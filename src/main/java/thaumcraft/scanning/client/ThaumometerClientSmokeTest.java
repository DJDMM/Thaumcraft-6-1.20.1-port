package thaumcraft.scanning.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;
import thaumcraft.scanning.ThaumometerItem;
import thaumcraft.world.aura.AuraManager;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** First-person HUD receives actual server packets and scans through vanilla interactions. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class ThaumometerClientSmokeTest {
    private static final String WORLD = "thaumcraft-thaumometer-smoke-" + System.currentTimeMillis();
    private static final String[] IMAGES = {"unknown-block", "scanned-block", "unknown-entity", "scanned-entity", "offhand-sneak", "overflow-sneak", "unknown-container", "scanned-container", "repeated-container"};
    private static final int EXPECTED_IMAGES = IMAGES.length + 2;
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static boolean started, stopped, prepared, captured, requested;
    private static int stage, stableTicks, totalTicks, finishStep;
    private static long start;
    private static long sceneHudStart = -1, sceneWorldStart = -1, hiddenHudStart, hiddenWorldStart;
    private static TutorialSteps previousTutorial;
    private static BlockPos feet, targetBlock;
    private static volatile int entityId = -1;
    private static volatile CompoundTag hoverKnowledge;
    private static volatile int completedScans;
    private static volatile int hoverExperience;
    private static ItemStack[] containerContents;
    private static CompletableFuture<Void> work;

    private ThaumometerClientSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.thaumometerSmokeTest") || stopped) return;
        Minecraft mc = Minecraft.getInstance();
        if (start == 0) start = System.nanoTime();
        try {
            require(System.nanoTime() - start < 300_000_000_000L && ++totalTicks < 6000, "Thaumometer client audit timed out");
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.options.onboardAccessibility = false;
                    mc.options.save();
                    mc.setScreen(new TitleScreen());
                    return;
                }
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                started = true;
                previousTutorial = mc.options.tutorialStep;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.getTutorial().stop();
                mc.getToasts().clear();
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(3);
                mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(2);
                mc.options.cloudStatus().set(CloudStatus.OFF);
                mc.options.fov().set(70);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                KeyMapping.releaseAll();
                mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                var settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_SMOKE_WORLD: {}", WORLD);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0x544336L, false, false), WorldPresets::createNormalWorldDimensions);
                return;
            }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            require(mc.getSingleplayerServer() != null && mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD), "Wrong thaumometer audit world");
            if (mc.screen != null) mc.setScreen(null); // Own world only: close the initial joining/pause screen.
            mc.getToasts().clear();
            if (!prepared) {
                if (work == null) {
                    feet = new BlockPos(mc.player.blockPosition().getX(), 160, mc.player.blockPosition().getZ());
                    targetBlock = feet.above().south(4);
                    submit(mc, () -> prepareWorld(mc));
                    return;
                }
                if (!finishWork()) return;
                prepared = true;
                mc.player.setYRot(0);
                mc.player.setXRot(0);
                mc.player.yRotO = 0;
                mc.player.xRotO = 0;
            }
            if (stage >= IMAGES.length) { finishChecks(mc); return; }
            if (work != null && !finishWork()) return;
            ScanningNetwork.Snapshot packet = ThaumometerClient.currentSnapshot();
            if (!matchesScene(mc, packet)) {
                stableTicks = 0;
                sceneHudStart = sceneWorldStart = -1;
                return;
            }
            require(packet.base() > 0 && packet.vis() > 0 && packet.flux() > 0, "Gauge lacks live vis/flux/base values");
            require(ThaumometerClient.isVisibleForSmokeTest(), "Valid held scanner did not enable the HUD renderer");
            if (sceneHudStart < 0) {
                sceneHudStart = ThaumometerClient.hudRenderCountForSmokeTest();
                sceneWorldStart = ThaumometerClient.worldRenderCountForSmokeTest();
            }
            if (++stableTicks < 25) return;
            if (!captured) {
                require(ThaumometerClient.hudRenderCountForSmokeTest() > sceneHudStart
                        && ThaumometerClient.worldRenderCountForSmokeTest() > sceneWorldStart,
                        "Standard scene did not actually render both HUD and world overlays");
                if (stage == 1 || stage == 3 || stage == 7 || stage == 8) {
                    var result = ThaumometerClient.scanResultForSmokeTest();
                    require(result != null && result.dimension().equals(packet.dimension()) && result.discovered() == (stage != 8)
                            && result.target().aspects().equals(packet.target().aspects()),
                            "Scanned scene lacked its real server scan-result packet");
                    var location = result.target().location();
                    require(stage != 3 ? location.kind() == ThaumometerItem.TargetKind.BLOCK && targetBlock.equals(location.blockPos())
                            : location.kind() == ThaumometerItem.TargetKind.ENTITY && location.entityId() == entityId,
                            "Real scan reply described a different target");
                }
                submit(mc, () -> verifyHover(mc, packet));
                captured = true;
                capture(mc, IMAGES[stage]);
                return;
            }
            if (SAVED.get() < stage + 1 || stableTicks < 40) return;
            int completedStage = stage++;
            captured = false;
            stableTicks = 0;
            sceneHudStart = sceneWorldStart = -1;
            if (completedStage == 0 || completedStage == 2 || completedStage == 6 || completedStage == 7) {
                var hand = ThaumometerItem.heldHand(mc.player);
                require(hand != null && mc.gameMode != null, "Cannot dispatch actual scan interaction");
                // The air-use vanilla packet makes the server reconstruct the aimed
                // target, rather than trusting a target supplied by this harness.
                mc.gameMode.useItem(mc.player, hand);
                requested = true;
            } else if (completedStage == 1) {
                submit(mc, () -> prepareEntity(mc));
            } else if (completedStage == 3) {
                submit(mc, () -> prepareOffhand(mc));
                mc.options.keyShift.setDown(true);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
            } else if (completedStage == 4) {
                submit(mc, () -> prepareOverflow(mc));
            } else if (completedStage == 5) {
                submit(mc, () -> prepareContainer(mc));
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
            }
        } catch (Exception | AssertionError failure) { fail(mc, failure); }
    }

    private static boolean matchesScene(Minecraft mc, ScanningNetwork.Snapshot packet) {
        if (packet == null || !packet.dimension().equals(mc.level.dimension().location()) || packet.target() == null) return false;
        var target = packet.target();
        if (ThaumometerClient.currentTarget() == null) return false;
        var location = target.location();
        if (stage <= 1 && (location.kind() != ThaumometerItem.TargetKind.BLOCK || !targetBlock.equals(location.blockPos()))) return false;
        if (stage >= 2 && stage <= 3 && (location.kind() != ThaumometerItem.TargetKind.ENTITY || location.entityId() != entityId)) return false;
        if (stage >= 4 && stage <= 5 && (location.kind() != ThaumometerItem.TargetKind.HELD_ITEM || !mc.player.isShiftKeyDown()
                || ThaumometerItem.heldHand(mc.player) != InteractionHand.OFF_HAND)) return false;
        if (stage >= 4 && stage <= 5 && mc.options.guiScale().get() != 3) return false;
        if (stage >= 6 && (location.kind() != ThaumometerItem.TargetKind.BLOCK || !targetBlock.equals(location.blockPos())
                || !mc.player.isShiftKeyDown() || ThaumometerItem.heldHand(mc.player) != InteractionHand.MAIN_HAND
                || !mc.player.getOffhandItem().isEmpty() || mc.options.guiScale().get() != 2)) return false;
        if (stage == 5 && (packet.vis() < 425 || packet.flux() < 325 || packet.vis() + packet.flux() <= 525)) return false;
        boolean scanned = stage == 1 || stage == 3 || stage == 7 || stage == 8;
        if ((stage < 4 || stage >= 6) && target.scanned() != scanned) return false;
        if (target.aspects().isEmpty()) return false; // Original entity icons are visible before scanning.
        if (scanned && !requested) return false;
        if (stage == 8 && (ThaumometerClient.scanResultForSmokeTest() == null
                || ThaumometerClient.scanResultForSmokeTest().discovered())) return false;
        return true;
    }

    private static void prepareWorld(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 12; z++) {
            BlockPos floor = feet.offset(x, -1, z);
            level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(floor.above(y + 1), Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);
        level.setBlockAndUpdate(targetBlock, Blocks.COAL_BLOCK.defaultBlockState());
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        player.connection.teleport(feet.getX() + .5, feet.getY(), feet.getZ() + .5, 0, 0);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        require(KnowledgeStore.get(player).scanCount() == 0, "Fresh isolated player already had scans");
        require(KnowledgeStore.get(player).discoveredAspects().isEmpty()
                && !KnowledgeStore.get(player).isResearchKnown("BASEALCHEMY"),
                "Ungated first scans were accidentally given aspect or alchemy prerequisites");
        AuraManager.drainVis(level, feet, Float.MAX_VALUE, false);
        AuraManager.drainFlux(level, feet, Float.MAX_VALUE, false);
        AuraManager.addVis(level, feet, 174);
        // A small positive flux value keeps the normal meter visible without
        // triggering the separate native twenty-tick FLUX research check.
        AuraManager.addFlux(level, feet, Math.max(1, Math.min(30, AuraManager.getAuraBase(level, feet) / 6)));
        hoverKnowledge = KnowledgeStore.get(player).save();
        hoverExperience = player.totalExperience;
        ScanningNetwork.sendHud(player);
    }

    private static void prepareEntity(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        require(KnowledgeStore.get(player).scanCount() == 1, "Real block scan did not credit exactly once");
        completedScans = 1;
        level.setBlockAndUpdate(targetBlock, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(targetBlock.below(), Blocks.STONE.defaultBlockState());
        Cow cow = EntityType.COW.create(level);
        require(cow != null, "Could not create a real cow scan target");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(targetBlock.getX() + .5, targetBlock.getY(), targetBlock.getZ() + .5);
        cow.setYRot(180);
        require(level.addFreshEntity(cow), "Real cow target did not spawn");
        entityId = cow.getId();
        player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        hoverKnowledge = KnowledgeStore.get(player).save();
        hoverExperience = player.totalExperience;
        requested = false;
        ScanningNetwork.sendHud(player);
    }

    private static void prepareOffhand(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        require(KnowledgeStore.get(player).scanCount() == 2, "Real entity scan did not credit exactly once");
        completedScans = 2;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.REDSTONE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        player.setShiftKeyDown(true);
        requested = false;
        hoverKnowledge = KnowledgeStore.get(player).save();
        hoverExperience = player.totalExperience;
        ScanningNetwork.sendHud(player);
    }

    private static void prepareOverflow(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        AuraManager.drainVis(level, pos, Float.MAX_VALUE, false);
        AuraManager.drainFlux(level, pos, Float.MAX_VALUE, false);
        AuraManager.addVis(level, pos, 450);
        AuraManager.addFlux(level, pos, 350);
        var actual = ScanningNetwork.capture(player);
        require(actual.vis() == 450 && actual.flux() == 350, "Server overflow fixture failed to retain raw aura amounts");
        hoverKnowledge = KnowledgeStore.get(player).save();
        hoverExperience = player.totalExperience;
        ScanningNetwork.sendHud(player);
        LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_SMOKE_OVERFLOW: server vis={}, flux={}, sum={}", actual.vis(), actual.flux(), actual.vis() + actual.flux());
    }

    private static void prepareContainer(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        require(KnowledgeStore.get(player).scanCount() == 2, "Overflow aura discovery credited an object scan");
        require(KnowledgeStore.get(player).researchStage("FLUX") == 1, "Native held-scanner aura check did not start FLUX");
        require(player.totalExperience == hoverExperience + 5, "Native FLUX discovery did not award exactly five experience once");
        var cow = level.getEntity(entityId);
        if (cow != null) cow.discard();
        level.setBlockAndUpdate(targetBlock, Blocks.CHEST.defaultBlockState());
        require(level.getBlockEntity(targetBlock) instanceof ChestBlockEntity, "Native chest fixture did not create its inventory");
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(targetBlock);
        // The coal block is already known. Repeated diamonds must yield only
        // one discovery, and a distant nonempty slot must still be visited.
        chest.setItem(0, new ItemStack(Items.COAL_BLOCK, 64));
        chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
        chest.setItem(3, new ItemStack(Items.DIAMOND, 64));
        chest.setItem(26, new ItemStack(Items.DIAMOND, 3));
        chest.setChanged();
        containerContents = new ItemStack[chest.getContainerSize()];
        for (int i = 0; i < containerContents.length; i++) containerContents[i] = chest.getItem(i).copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        requested = false;
        hoverKnowledge = KnowledgeStore.get(player).save();
        hoverExperience = player.totalExperience;
        ScanningNetwork.sendHud(player);
    }

    private static void verifyContainer(ServerPlayer player) {
        require(player.serverLevel().getBlockEntity(targetBlock) instanceof ChestBlockEntity, "Scanned container vanished");
        ChestBlockEntity chest = (ChestBlockEntity) player.serverLevel().getBlockEntity(targetBlock);
        require(containerContents != null && chest.getContainerSize() == containerContents.length, "Container slot count changed during scans");
        for (int i = 0; i < containerContents.length; i++)
            require(ItemStack.matches(chest.getItem(i), containerContents[i]), "Scanning changed physical container contents in slot " + i);
        require(player.containerMenu == player.inventoryMenu, "Scanner interaction opened the chest menu");
    }

    private static void verifyHover(Minecraft mc, ScanningNetwork.Snapshot displayed) {
        ServerPlayer player = serverPlayer(mc);
        var authoritative = ScanningNetwork.capture(player);
        require(authoritative.target() != null && displayed.target().scanned() == authoritative.target().scanned()
                && displayed.target().location().kind() == authoritative.target().location().kind()
                && displayed.target().location().entityId() == authoritative.target().location().entityId()
                && java.util.Objects.equals(displayed.target().location().blockPos(), authoritative.target().location().blockPos())
                && displayed.target().aspects().equals(authoritative.target().aspects()), "Client displayed target composition/status from a different source");
        require(displayed.base() == authoritative.base() && Math.abs(displayed.vis() - authoritative.vis()) < 15
                && Math.abs(displayed.flux() - authoritative.flux()) < 15, "Client gauge did not match the current server chunk");
        if (stage == 0 || stage == 2 || stage == 4 || stage == 6 || stage == 8) {
            require(hoverKnowledge.equals(KnowledgeStore.get(player).save()), "Hover credited a discovery, aspect or observation");
            require(player.totalExperience == hoverExperience, "Hover or repeated container scan credited additional experience");
            require(!player.getCooldowns().isOnCooldown(ScanningModule.THAUMOMETER.get()), "Hover created scan cooldown");
        }
        if (stage == 5) {
            // High-flux holding may discover FLUX; it must not silently grant
            // scans, aspects, observation knowledge or any other research.
            CompoundTag expected = hoverKnowledge.copy();
            expected.getCompound("ResearchStages").putInt("FLUX", 1);
            require(KnowledgeStore.get(player).researchStage("FLUX") == 1
                    && expected.equals(KnowledgeStore.get(player).save()), "Overflow hover changed more than original FLUX discovery");
            require(player.totalExperience == hoverExperience + 5, "Original FLUX event did not award exactly five experience once");
            require(!player.getCooldowns().isOnCooldown(ScanningModule.THAUMOMETER.get()), "Aura discovery created scan cooldown");
            LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_FLUX_DISCOVERY_OK: native twenty-tick held scanner update; FLUX stage1; exact five experience once; object scans, aspects and Observation unchanged");
        }
        if (stage >= 6) verifyContainer(player);
        if (stage == 7) {
            require(KnowledgeStore.get(player).scanCount() == 5, "Actual chest interaction failed to scan chest, log and one diamond composition exactly once");
            completedScans = 5;
            hoverKnowledge = KnowledgeStore.get(player).save();
            hoverExperience = player.totalExperience;
            LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_CONTAINER_SCAN_OK: actual C2S air-use; native 27-slot capability; 4 nonempty slots, known coal block and repeated diamond counted once; exact inventory retained");
        }
        if (stage == 8) LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_REPEAT_SCAN_OK: actual second C2S container interaction; no additional knowledge or observations; exact inventory retained");
        LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_SMOKE_SYNC: scene={}, scanned={}, aspects={}, base={}, vis={}, flux={}, chunk={}",
                IMAGES[stage], authoritative.target().scanned(), authoritative.target().aspects(), authoritative.base(), authoritative.vis(), authoritative.flux(), new ChunkPos(player.blockPosition()));
    }

    private static void finishChecks(Minecraft mc) throws Exception {
        if (finishStep == 0) {
            finishStep = 1;
            mc.options.keyShift.setDown(false);
            mc.options.hideGui = true;
            hiddenHudStart = ThaumometerClient.hudRenderCountForSmokeTest();
            hiddenWorldStart = ThaumometerClient.worldRenderCountForSmokeTest();
            stableTicks = 0;
            captured = false;
            return;
        }
        if (finishStep == 1) {
            require(!ThaumometerClient.isVisibleForSmokeTest(), "F1 left the thaumometer renderer visible");
            require(ThaumometerClient.hudRenderCountForSmokeTest() == hiddenHudStart
                    && ThaumometerClient.worldRenderCountForSmokeTest() == hiddenWorldStart,
                    "HUD or world overlays continued drawing while F1 hid the GUI");
            if (++stableTicks < 15) return;
            if (!captured) {
                capture(mc, "hidden-gui");
                captured = true;
                return;
            }
            if (SAVED.get() < IMAGES.length + 1 || stableTicks < 30) return;
            mc.options.hideGui = false;
            submit(mc, () -> {
                ServerPlayer player = serverPlayer(mc);
                require(KnowledgeStore.get(player).scanCount() == 5 && completedScans == 5, "Hover/scan pipeline gave additional scan credits");
                require(player.totalExperience == hoverExperience, "Repeated high-flux holding paid FLUX experience again");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                var empty = ScanningNetwork.capture(player);
                require(empty.target() == null && empty.base() == 0 && empty.vis() == 0 && empty.flux() == 0, "No-scanner capture retained HUD state");
            });
            finishStep = 2;
            stableTicks = 0;
            captured = false;
            return;
        }
        if (!finishWork() || ThaumometerItem.heldHand(mc.player) != null) return;
        require(ThaumometerClient.currentSnapshot() == null && ThaumometerClient.currentTarget() == null, "Client retained scanner HUD state after both hands were cleared");
        require(!ThaumometerClient.isVisibleForSmokeTest(), "No-scanner client still enabled the HUD renderer");
        if (stableTicks == 0) {
            hiddenHudStart = ThaumometerClient.hudRenderCountForSmokeTest();
            hiddenWorldStart = ThaumometerClient.worldRenderCountForSmokeTest();
        }
        require(ThaumometerClient.hudRenderCountForSmokeTest() == hiddenHudStart
                && ThaumometerClient.worldRenderCountForSmokeTest() == hiddenWorldStart,
                "HUD or world overlays continued drawing without a scanner");
        if (++stableTicks < 20) return;
        if (!captured) {
            capture(mc, "no-scanner");
            captured = true;
            return;
        }
        if (SAVED.get() < EXPECTED_IMAGES || stableTicks < 30) return;
        require(SAVED.get() == EXPECTED_IMAGES, "Not all first-person screenshots were saved");
        stopped = true;
        mc.options.tutorialStep = previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_RENDER_AUDIT_OK: {} current first-person captures; native block/entity/container world and meter rendered through actual HUD/world counters; F1 and both-hand removal suppress both renderers", SAVED.get());
        LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_CLIENT_SMOKE_OK: {} scenes; actual block/entity/container scan packets and repeated container interaction; aura/current chunk, raw overflow and native FLUX discovery; hover grants no object knowledge; exact physical chest inventory retained; offhand/sneak and GUI scales 2/3; hidden GUI and hand removal; isolated world={}", SAVED.get(), WORLD);
        mc.getConnection().getConnection().disconnect(Component.literal("Thaumometer audit complete"));
        mc.clearLevel(new TitleScreen());
        mc.stop();
    }

    private static ServerPlayer serverPlayer(Minecraft mc) {
        UUID uuid = mc.player.getUUID();
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
        require(player != null, "Integrated server has no matching audit player");
        return player;
    }

    private static void submit(Minecraft mc, Runnable command) {
        require(work == null, "Overlapping smoke server commands");
        work = new CompletableFuture<>();
        CompletableFuture<Void> result = work;
        mc.getSingleplayerServer().execute(() -> {
            try { command.run(); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
    }

    private static boolean finishWork() {
        if (work == null) return true;
        if (!work.isDone()) return false;
        work.join();
        work = null;
        return true;
    }

    private static void capture(Minecraft mc, String scene) throws Exception {
        String name = "tc6-thaumometer-" + scene + ".png";
        File file = new File(new File(mc.gameDirectory, "screenshots"), name);
        Files.deleteIfExists(file.toPath()); // Only this harness's named outputs.
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
            if (file.isFile() && file.length() > 0) {
                SAVED.incrementAndGet();
                LogUtils.getLogger().info("THAUMCRAFT_THAUMOMETER_SMOKE_IMAGE: {}", file.getAbsolutePath());
            } else mc.execute(() -> fail(mc, new AssertionError("Missing screenshot " + name)));
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fail(Minecraft mc, Throwable failure) {
        if (stopped) return;
        stopped = true;
        mc.options.keyShift.setDown(false);
        mc.options.hideGui = false;
        if (previousTutorial != null) mc.options.tutorialStep = previousTutorial;
        LogUtils.getLogger().error("THAUMCRAFT_THAUMOMETER_CLIENT_SMOKE_FAILED", failure);
        mc.stop();
    }
}
