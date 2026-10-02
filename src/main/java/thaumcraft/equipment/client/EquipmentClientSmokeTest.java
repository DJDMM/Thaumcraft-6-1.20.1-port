package thaumcraft.equipment.client;

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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.equipment.GearSupport;
import thaumcraft.equipment.RechargeSupport;
import thaumcraft.equipment.armor.GogglesArmorClient;
import thaumcraft.equipment.cleansing.CleansingModule;
import thaumcraft.equipment.recharge.RechargePedestalBlockEntity;
import thaumcraft.equipment.tools.ElementalShovelItem;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchNetwork;
import thaumcraft.world.aura.AuraManager;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.glfw.GLFW;

/** Opt-in integrated-server equipment use, inventory/BE synchronization and real world rendering. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class EquipmentClientSmokeTest {
    private static final String WORLD = "thaumcraft-equipment-smoke-" + System.currentTimeMillis();
    private static final String[] IMAGES = {"thaumium-worn", "fortress-mask-goggles", "void-robes-worn", "traveller-worn",
            "elemental-hoe-tilled", "thaumium-pick-mined", "pedestal-charged", "goggles-container", "sanity-85", "sanity-overflow",
            "purifying-bath", "soap-use"};
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static boolean started, stopped, prepared, scenePrepared, requested, captured, verified;
    private static int stage, totalTicks, sceneTicks, stableTicks, modeStep, modeTicks, survivalSyncTicks;
    private static long start;
    private static TutorialSteps previousTutorial;
    private static BlockPos feet, target;
    private static volatile CompoundTag sceneKnowledge;
    private static CompletableFuture<Void> work;

    private EquipmentClientSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.equipmentSmokeTest") || stopped) return;
        Minecraft mc = Minecraft.getInstance();
        if (start == 0) start = System.nanoTime();
        try {
            require(System.nanoTime() - start < 360_000_000_000L && ++totalTicks < 7200, "Equipment smoke timed out");
            if (!started) { startWorld(mc); return; }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            require(mc.getSingleplayerServer() != null && WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()), "Wrong equipment audit world");
            if (mc.screen != null) mc.setScreen(null); // Only this newly created harness world.
            mc.getToasts().clear();
            if (!finishWork()) return;
            if (!prepared) {
                feet = new BlockPos(mc.player.blockPosition().getX(), 160, mc.player.blockPosition().getZ());
                submit(mc, () -> prepareWorld(mc));
                prepared = true;
                return;
            }
            if (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SURVIVAL || mc.player.getAbilities().instabuild) {
                require(++survivalSyncTicks < 200, "Server survival mode did not synchronize: " + sceneDiagnostic(mc));
                return;
            }
            if (stage == IMAGES.length) { finish(mc); return; }
            if (stage == 4 && modeStep < 3) { checkModeKey(mc); return; }
            if (!scenePrepared) {
                submit(mc, () -> prepareScene(mc));
                scenePrepared = true;
                return;
            }
            setCamera(mc);
            require(++sceneTicks < 1000, "Equipment scene never became ready: " + IMAGES[stage] + "; " + sceneDiagnostic(mc));
            if (!ready(mc)) { stableTicks = 0; return; }
            if (++stableTicks < 30) return;
            if (!verified) {
                submit(mc, () -> verifyScene(mc));
                verified = true;
                return;
            }
            if (!captured) {
                capture(mc, IMAGES[stage]);
                captured = true;
                return;
            }
            if (SAVED.get() != stage + 1 || stableTicks < 40) return;
            stage++;
            scenePrepared = requested = captured = verified = false;
            sceneTicks = stableTicks = 0;
        } catch (Exception | AssertionError failure) { fail(mc, failure); }
    }

    private static void startWorld(Minecraft mc) {
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
        mc.options.fov().set(60);
        mc.options.hideGui = false;
        KeyMapping.releaseAll();
        mc.resizeDisplay();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        var settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
        LogUtils.getLogger().info("THAUMCRAFT_EQUIPMENT_SMOKE_WORLD: {}", WORLD);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0x54433610L, false, false), WorldPresets::createNormalWorldDimensions);
    }

    private static void prepareWorld(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 10; z++) {
            BlockPos floor = feet.offset(x, -1, z);
            level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 1; y <= 6; y++) level.setBlockAndUpdate(floor.above(y), Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        // This public player path sends CHANGE_GAME_MODE to the owning client as well as updating server abilities.
        require(player.setGameMode(GameType.SURVIVAL), "Server rejected fresh-world survival transition");
        player.getInventory().clearContent();
        teleport(player);
        sync(player);
        require(KnowledgeStore.get(player).scanCount() == 0, "Fresh equipment world already contains credited scans");
    }

    private static void prepareScene(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        player.getInventory().clearContent();
        for (EquipmentSlot slot : ARMOR) player.setItemSlot(slot, ItemStack.EMPTY);
        teleport(player);
        target = feet.south(3);
        // Restore only the small work area from the previous scene.
        for (int x = -1; x <= 1; x++) for (int z = 1; z <= 4; z++) {
            level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            level.setBlockAndUpdate(feet.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.offset(x, 1, z), Blocks.AIR.defaultBlockState());
        }
        switch (stage) {
            case 0 -> { equip(player, "thaumium", true); player.setItemInHand(InteractionHand.MAIN_HAND, stack("thaumium_sword")); }
            case 1 -> {
                equip(player, "fortress", false);
                var helm = player.getItemBySlot(EquipmentSlot.HEAD);
                helm.getOrCreateTag().putInt("mask", 2);
                helm.getOrCreateTag().putBoolean("goggles", true);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack("crimson_blade"));
            }
            case 2 -> { equip(player, "void_robe", false); player.setItemSlot(EquipmentSlot.FEET, stack("void_boots")); player.setItemInHand(InteractionHand.MAIN_HAND, stack("void_sword")); }
            case 3 -> {
                cloth(player);
                ItemStack boots = stack("traveller_boots");
                RechargeSupport.addCharge(boots, player, 10);
                player.setItemSlot(EquipmentSlot.FEET, boots);
            }
            case 4 -> {
                cloth(player); target = feet.below().south(2);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(target.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(target.east(2), Blocks.WATER.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack("elemental_hoe"));
            }
            case 5 -> {
                cloth(player); target = feet.above().south(2);
                level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack("thaumium_pick"));
            }
            case 6 -> {
                cloth(player);
                level.setBlockAndUpdate(target, CatalogBlocks.ENTRIES.get("recharge_pedestal").get().defaultBlockState());
                ItemStack boots = stack("traveller_boots"); RechargeSupport.addCharge(boots, player, 230);
                boots.getOrCreateTag().putInt("energy", 17);
                player.setItemInHand(InteractionHand.OFF_HAND, boots);
                AuraManager.drainVis(level, target, Float.MAX_VALUE, false); AuraManager.addVis(level, target, 20);
            }
            case 7 -> {
                cloth(player);
                level.setBlockAndUpdate(target, CatalogBlocks.ENTRIES.get("jar_normal").get().defaultBlockState());
                var jar = (EssentiaJarBlockEntity)level.getBlockEntity(target);
                require(jar != null && jar.addExact(Aspect.WATER, 250) && jar.applyLabel(player, Direction.NORTH, Aspect.WATER), "Actual goggles jar fixture failed");
            }
            case 8, 9 -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack("sanity_checker"));
                if (stage == 8) {
                    KnowledgeStore.addPermanentWarp(player, 30); KnowledgeStore.addNormalWarp(player, 45); KnowledgeStore.addTemporaryWarp(player, 10);
                } else { KnowledgeStore.addPermanentWarp(player, 40); KnowledgeStore.addNormalWarp(player, 30); KnowledgeStore.addTemporaryWarp(player, 10); }
                ResearchNetwork.sync(player);
            }
            case 10 -> {
                // Actual player collision grants Ward and consumes this first source bath.
                level.setBlockAndUpdate(feet, CleansingModule.block().defaultBlockState());
                target = feet.below().south(3);
                // A one-block pit exposes the fluid surface while keeping the bath away from the player.
                for (int x = -1; x <= 1; x++) for (int z = 2; z <= 4; z++) {
                    level.setBlockAndUpdate(feet.offset(x, -2, z), Blocks.STONE_BRICKS.defaultBlockState());
                    level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.AIR.defaultBlockState());
                }
                for (int x = -2; x <= 2; x++) for (int z = 1; z <= 5; z++)
                    if (Math.abs(x) == 2 || z == 1 || z == 5) level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CleansingModule.PURE_BUCKET.get()));
                // A second filled bucket remains visible after real C2S placement empties main hand.
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CleansingModule.PURE_BUCKET.get()));
            }
            case 11 -> {
                require(player.hasEffect(CleansingModule.WARP_WARD.get()), "Ward from the real previous bath expired unexpectedly");
                // Existing Ward preserves this bath source; soap now earns both original bonuses.
                level.setBlockAndUpdate(feet, CleansingModule.block().defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("sanity_soap"), 2));
                player.setItemInHand(InteractionHand.OFF_HAND, stack("sanity_checker"));
                ResearchNetwork.sync(player);
            }
            default -> throw new AssertionError("Unknown equipment scene");
        }
        sync(player);
        sceneKnowledge = KnowledgeStore.get(player).save();
    }

    private static void setCamera(Minecraft mc) {
        mc.options.setCameraType(stage < 4 ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        Vec3 aim = stage < 4 || (stage >= 8 && stage != 10) ? mc.player.getEyePosition().add(0, 0, 4) : Vec3.atCenterOf(target);
        if (stage == 10) aim = Vec3.atCenterOf(target.below()).add(0, .5, 0);
        Vec3 delta = aim.subtract(mc.player.getEyePosition());
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }

    private static void checkModeKey(Minecraft mc) {
        require(++modeTicks < 200, "Actual F input never synchronized shovel mode without swapping hands");
        if (modeStep == 0) {
            submit(mc, () -> {
                ServerPlayer player = serverPlayer(mc);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack("elemental_shovel"));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK, 3));
                sync(player);
            });
            modeStep = 1;
            return;
        }
        if (!mc.player.getMainHandItem().is(item("elemental_shovel")) || !mc.player.getOffhandItem().is(Items.STICK)) return;
        if (modeStep == 1) {
            // Real KeyboardHandler dispatch queues mapped clicks and fires Forge input in vanilla order.
            // The production handler must suppress the conflicting F swap before the next game tick.
            long window = mc.getWindow().getWindow();
            mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_F, 0, GLFW.GLFW_PRESS, 0);
            mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_F, 0, GLFW.GLFW_RELEASE, 0);
            modeStep = 2;
            return;
        }
        if (ElementalShovelItem.orientation(mc.player.getMainHandItem()) != 1) return;
        require(mc.player.getOffhandItem().getCount() == 3, "F mode consumed or changed the opposite hand");
        submit(mc, () -> {
            ServerPlayer player = serverPlayer(mc);
            require(player.getMainHandItem().is(item("elemental_shovel")) && ElementalShovelItem.orientation(player.getMainHandItem()) == 1
                    && player.getOffhandItem().is(Items.STICK) && player.getOffhandItem().getCount() == 3,
                    "Actual F dispatch swapped server hands or failed to update authoritative mode");
            LogUtils.getLogger().info("THAUMCRAFT_EQUIPMENT_SMOKE_MODE_KEY: F, server or=1, main shovel/offhand stick x3 unchanged");
        });
        modeStep = 3;
    }

    private static boolean ready(Minecraft mc) throws Exception {
        require(mc.gameMode != null, "No actual client interaction controller");
        ItemStack main = mc.player.getMainHandItem();
        switch (stage) {
            case 0 -> { return worn(mc, "thaumium", true) && main.is(item("thaumium_sword")); }
            case 1 -> {
                var helm = mc.player.getItemBySlot(EquipmentSlot.HEAD);
                return worn(mc, "fortress", false) && helm.hasTag() && helm.getTag().getInt("mask") == 2 && helm.getTag().getBoolean("goggles");
            }
            case 2 -> { return worn(mc, "void_robe", false) && mc.player.getItemBySlot(EquipmentSlot.FEET).is(item("void_boots")); }
            case 3 -> { return mc.player.getItemBySlot(EquipmentSlot.HEAD).is(item("goggles")) && mc.player.getItemBySlot(EquipmentSlot.FEET).is(item("traveller_boots"))
                    && RechargeSupport.getCharge(mc.player.getItemBySlot(EquipmentSlot.FEET)) > 0; }
            case 4 -> {
                if (!main.is(item("elemental_hoe")) || (!requested && !mc.level.getBlockState(target).is(Blocks.DIRT))) return false;
                if (!requested) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(target).add(0, .5, 0), Direction.UP, target, false));
                    requested = true;
                }
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!mc.level.getBlockState(target.offset(x, 0, z)).is(Blocks.FARMLAND)) return false;
                return main.getDamageValue() == 9;
            }
            case 5 -> {
                if (!main.is(item("thaumium_pick"))) return false;
                if (!requested) {
                    if (!mc.level.getBlockState(target).is(Blocks.STONE) || sceneTicks < 6
                            || !(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(target)) return false;
                    // Hold real attack input so Minecraft continues this survival break across ticks.
                    // Without it handleKeybinds stops/reset the manually started action each frame.
                    mc.options.keyAttack.setDown(true);
                    mc.gameMode.startDestroyBlock(target, Direction.NORTH); requested = true;
                }
                if (!mc.level.isEmptyBlock(target)) return false;
                mc.options.keyAttack.setDown(false);
                return main.getDamageValue() == 1;
            }
            case 6 -> {
                if (!(mc.level.getBlockEntity(target) instanceof RechargePedestalBlockEntity pedestal)
                        || !mc.player.getItemBySlot(EquipmentSlot.HEAD).is(item("goggles"))) return false;
                if (!requested) {
                    if (!mc.player.getOffhandItem().is(item("traveller_boots"))) return false;
                    mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, false));
                    requested = true;
                }
                return mc.player.getOffhandItem().isEmpty() && RechargeSupport.getCharge(pedestal.getItem(0)) == 240 && GogglesArmorClient.hasContainerPopup(target);
            }
            case 7 -> { return mc.level.getBlockEntity(target) instanceof EssentiaJarBlockEntity jar && jar.amount() == 250
                    && jar.aspect() == Aspect.WATER && jar.filter() == Aspect.WATER && GogglesArmorClient.hasContainerPopup(target); }
            case 8, 9 -> {
                if (!main.is(item("sanity_checker"))) return false;
                // Read the actual S2C receiver fields; never invoke receive() or invent a client snapshot.
                return sanityValue("permanent") == (stage == 8 ? 30 : 70) && sanityValue("normal") == (stage == 8 ? 45 : 75)
                        && sanityValue("temporary") == (stage == 8 ? 10 : 20);
            }
            case 10 -> {
                if (!mc.player.hasEffect(CleansingModule.WARP_WARD.get()) || sceneTicks < 6) return false;
                if (!requested) {
                    if (!main.is(CleansingModule.PURE_BUCKET.get()) || !(mc.hitResult instanceof BlockHitResult hit)
                            || !hit.getBlockPos().equals(target.below())) return false;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    requested = true;
                }
                if (!main.is(Items.BUCKET) || !mc.level.getFluidState(target).isSource()
                        || !mc.level.getFluidState(target).is(CleansingModule.PURE.get())
                        || !mc.player.getOffhandItem().is(CleansingModule.PURE_BUCKET.get())) return false;
                return mc.level.getFluidState(target.east()).is(CleansingModule.FLOWING_PURE.get())
                        && !mc.level.getFluidState(target.east()).isSource();
            }
            case 11 -> {
                if (!main.is(item("sanity_soap")) || !mc.player.hasEffect(CleansingModule.WARP_WARD.get())) return false;
                if (!requested) {
                    if (main.getCount() != 2 || !mc.player.getOffhandItem().is(item("sanity_checker"))) return false;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    mc.options.keyUse.setDown(true);
                    requested = true;
                }
                if (!captured && mc.player.isUsingItem() && mc.player.getUseItemRemainingTicks() <= 20
                        && mc.player.getUseItemRemainingTicks() > 4) {
                    // Capture real use bubbles/hand animation, then wait for actual server completion.
                    capture(mc, IMAGES[stage]); captured = true;
                }
                if (main.getCount() == 1) mc.options.keyUse.setDown(false);
                return captured && main.getCount() == 1 && !mc.player.isUsingItem() && sanityValue("permanent") == 70
                        && sanityValue("normal") == 72 && sanityValue("temporary") == 0;
            }
            default -> throw new AssertionError("Unknown equipment scene");
        }
    }

    private static void verifyScene(Minecraft mc) {
        ServerPlayer player = serverPlayer(mc);
        var level = player.serverLevel();
        CompoundTag expectedKnowledge = sceneKnowledge.copy();
        if (stage == 11) { expectedKnowledge.putInt("NormalWarp", 72); expectedKnowledge.putInt("TemporaryWarp", 0); }
        require(expectedKnowledge.equals(KnowledgeStore.get(player).save()), "Equipment action changed knowledge/warp beyond its exact original contract");
        switch (stage) {
            case 0 -> require(player.getArmorValue() == 15 && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 4, "Actual worn thaumium attributes missing");
            case 1 -> require(player.getArmorValue() == 22 && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 12, "Actual NBT fortress set attributes missing");
            case 2 -> require(Math.abs(GearSupport.getTotalVisDiscount(player) - .15F) < .001, "Void robe worn discount missing");
            case 3 -> {
                var boots = player.getItemBySlot(EquipmentSlot.FEET);
                require(RechargeSupport.getCharge(boots) == 9 && boots.hasTag() && boots.getTag().getInt("energy") > 0, "Actual server worn boot billing did not run");
            }
            case 4 -> {
                require(requested && player.getMainHandItem().getDamageValue() == 9, "Vanilla C2S elemental hoe did not pay nine durability");
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) require(level.getBlockState(target.offset(x, 0, z)).is(Blocks.FARMLAND), "Server did not actually till all nine blocks");
            }
            case 5 -> {
                require(requested && level.isEmptyBlock(target) && player.getMainHandItem().getDamageValue() == 1, "Vanilla C2S survival mining did not mutate server block/tool");
                int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(4)).stream().map(ItemEntity::getItem)
                        .filter(stack -> stack.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum();
                for (ItemStack stack : player.getInventory().items) if (stack.is(Items.COBBLESTONE)) drops += stack.getCount();
                require(drops == 1, "Actual mining did not conserve its single cobblestone drop");
            }
            case 6 -> {
                var pedestal = (RechargePedestalBlockEntity)level.getBlockEntity(target);
                require(requested && pedestal != null && player.getOffhandItem().isEmpty() && RechargeSupport.getCharge(pedestal.getItem(0)) == 240
                        && pedestal.getItem(0).getTag().getInt("energy") == 17, "Actual offhand/pedestal ticker lost charge or item NBT");
                // The ordinary integrated world continues lunar regeneration and neighbor diffusion.
                // Exact whole-vis conservation is tested synchronously by EquipmentGameTests.
                LogUtils.getLogger().info("THAUMCRAFT_EQUIPMENT_SMOKE_PEDESTAL: initial charge=230, client/server charge=240, actual aura={}", AuraManager.getVis(level, target));
            }
            case 7 -> {
                var jar = (EssentiaJarBlockEntity)level.getBlockEntity(target);
                require(jar != null && jar.amount() == 250 && jar.aspect() == Aspect.WATER && jar.filter() == Aspect.WATER, "Goggles display differed from authoritative contents");
            }
            case 8, 9 -> {
                var knowledge = KnowledgeStore.get(player);
                int total = knowledge.permanentWarp() + knowledge.normalWarp() + knowledge.temporaryWarp();
                require(total == (stage == 8 ? 85 : 165), "Sanity fixture did not retain raw server warp");
            }
            case 10 -> {
                require(requested && player.hasEffect(CleansingModule.WARP_WARD.get()) && level.getFluidState(feet).isEmpty()
                        && level.getFluidState(target).isSource() && level.getFluidState(target.east()).is(CleansingModule.FLOWING_PURE.get())
                        && player.getMainHandItem().is(Items.BUCKET) && player.getOffhandItem().is(CleansingModule.PURE_BUCKET.get()),
                        "Real bath collision, source/flowing fluid or C2S bucket payment/sync failed");
            }
            case 11 -> {
                require(requested && captured && player.hasEffect(CleansingModule.WARP_WARD.get()) && level.getFluidState(feet).isSource()
                        && player.getMainHandItem().is(item("sanity_soap")) && player.getMainHandItem().getCount() == 1 && !player.isUsingItem(),
                        "Actual 96-tick soap completion did not consume exactly one or preserve the protected bath");
            }
        }
        LogUtils.getLogger().info("THAUMCRAFT_EQUIPMENT_SMOKE_SYNC: scene={}, armor={}, toughness={}, discount={}, charge={}, scans={}", IMAGES[stage],
                player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), GearSupport.getTotalVisDiscount(player),
                RechargeSupport.getCharge(player.getItemBySlot(EquipmentSlot.FEET)), KnowledgeStore.get(player).scanCount());
    }

    private static int sanityValue(String fieldName) throws ReflectiveOperationException {
        var field = SanityHud.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static String sceneDiagnostic(Minecraft mc) {
        return "clientMode=" + (mc.gameMode == null ? "none" : mc.gameMode.getPlayerMode())
                + ", instabuild=" + mc.player.getAbilities().instabuild + ", main=" + mc.player.getMainHandItem()
                + ", damage=" + mc.player.getMainHandItem().getDamageValue() + ", target=" + target
                + ", state=" + (target == null ? "none" : mc.level.getBlockState(target))
                + ", hit=" + mc.hitResult + ", requested=" + requested;
    }

    private static void finish(Minecraft mc) {
        require(SAVED.get() == IMAGES.length, "Missing equipment PNGs");
        stopped = true;
        KeyMapping.releaseAll();
        mc.options.tutorialStep = previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_EQUIPMENT_CLIENT_SMOKE_OK: {} screenshots; actual worn equipment; vanilla C2S till/mining/offhand pedestal; server ticker and synchronized charge; goggles contents; sanity S2C 85/165; real bath Ward/source/flowing/bucket and 96-tick C2S soap; isolated world={}", SAVED.get(), WORLD);
        mc.getConnection().getConnection().disconnect(Component.literal("Equipment audit complete"));
        mc.clearLevel(new TitleScreen());
        mc.stop();
    }

    private static void teleport(ServerPlayer player) { player.connection.teleport(feet.getX() + .5, feet.getY(), feet.getZ() + .5, 0, 0); }
    private static void sync(ServerPlayer player) { player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); }
    private static void cloth(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, stack("goggles")); player.setItemSlot(EquipmentSlot.CHEST, stack("cloth_chest"));
        player.setItemSlot(EquipmentSlot.LEGS, stack("cloth_legs")); player.setItemSlot(EquipmentSlot.FEET, stack("cloth_boots"));
    }
    private static void equip(ServerPlayer player, String prefix, boolean boots) {
        player.setItemSlot(EquipmentSlot.HEAD, stack(prefix + "_helm")); player.setItemSlot(EquipmentSlot.CHEST, stack(prefix + "_chest"));
        player.setItemSlot(EquipmentSlot.LEGS, stack(prefix + "_legs")); if (boots) player.setItemSlot(EquipmentSlot.FEET, stack(prefix + "_boots"));
    }
    private static boolean worn(Minecraft mc, String prefix, boolean boots) {
        return mc.player.getItemBySlot(EquipmentSlot.HEAD).is(item(prefix + "_helm")) && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(item(prefix + "_chest"))
                && mc.player.getItemBySlot(EquipmentSlot.LEGS).is(item(prefix + "_legs")) && (!boots || mc.player.getItemBySlot(EquipmentSlot.FEET).is(item(prefix + "_boots")));
    }
    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
        require(item != null && item != Items.AIR, "Missing actual registered item " + id);
        return item;
    }
    private static ItemStack stack(String id) { return new ItemStack(item(id)); }
    private static ServerPlayer serverPlayer(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        require(player != null, "No matching integrated server player");
        return player;
    }
    private static void submit(Minecraft mc, Runnable command) {
        require(work == null, "Overlapping equipment server commands");
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
        work.join(); work = null;
        return true;
    }
    private static void capture(Minecraft mc, String scene) throws Exception {
        String name = "tc6-equipment-" + scene + ".png";
        File file = new File(new File(mc.gameDirectory, "screenshots"), name);
        Files.deleteIfExists(file.toPath());
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
            if (file.isFile() && file.length() > 0) {
                SAVED.incrementAndGet();
                LogUtils.getLogger().info("THAUMCRAFT_EQUIPMENT_SMOKE_IMAGE: {}", file.getAbsolutePath());
            } else mc.execute(() -> fail(mc, new AssertionError("Missing screenshot " + name)));
        });
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(Minecraft mc, Throwable failure) {
        if (stopped) return;
        stopped = true;
        KeyMapping.releaseAll();
        mc.options.hideGui = false;
        if (previousTutorial != null) mc.options.tutorialStep = previousTutorial;
        LogUtils.getLogger().error("THAUMCRAFT_EQUIPMENT_CLIENT_SMOKE_FAILED", failure);
        mc.stop();
    }
}
