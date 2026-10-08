package thaumcraft.essentia.transfuser.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.*;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.client.research.*;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.essentia.transfuser.EssentiaTransfuserBlock;
import thaumcraft.essentia.transfuser.EssentiaTransfuserBlockEntity;
import thaumcraft.research.*;
import thaumcraft.world.aura.AuraManager;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned hidden client: paid book/bench C2S and ordinary tile transfers, with no finished output fixtures. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class EssentiaTransfuserClientSmokeTest {
    private static final String WORLD = "thaumcraft-essentia-transfuser-smoke-" + System.currentTimeMillis();
    private static final String KEY = "ESSENTIATRANSPORT";
    private static final String[] IMAGES = {"paid-research", "input-recipe", "input-placed", "air-insert-active",
            "air-insert-complete", "output-recipe", "output-placed", "air-drain-active", "pipeline-complete", "original-six-facings"};
    private static final BlockPos BENCH = new BlockPos(-6, 112, 0), SOURCE = new BlockPos(0, 112, 0),
            INPUT = SOURCE.above(), MID = new BlockPos(4, 114, 0), COLLECTOR = new BlockPos(8, 113, 0), OUTPUT = COLLECTOR.above();
    private static final AtomicInteger saved = new AtomicInteger();
    private static boolean started, prepared, stopped, captureRequested, captured;
    private static int scene, phase, stable, wait, expectedStage = -1, obsAlchemyBefore, obsArtificeBefore, xpBefore, placementReadyTicks;
    private static float auraBefore;
    private static long began;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previous;
    private EssentiaTransfuserClientSmokeTest() {}

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static ServerPlayer player(Minecraft mc) {
        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        require(p != null, "Missing integrated transfuser player"); return p;
    }
    private static void submit(Minecraft mc, Runnable task) {
        require(work == null, "Overlapping transfuser server tasks");
        var future = new CompletableFuture<Void>(); work = future;
        mc.getSingleplayerServer().execute(() -> { try { task.run(); future.complete(null); } catch (Throwable e) { future.completeExceptionally(e); } });
    }
    private static void stage(ServerPlayer p, String key) {
        try { var method = PlayerKnowledge.class.getDeclaredMethod("setResearchStage", String.class, int.class);
            method.setAccessible(true); method.invoke(KnowledgeStore.get(p), key, ResearchCatalog.get(key).stages().size() + 1);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void knowledge(ServerPlayer p, String category, int raw) {
        try { var method = PlayerKnowledge.class.getDeclaredMethod("addKnowledge", KnowledgeType.class, String.class, int.class);
            method.setAccessible(true); method.invoke(KnowledgeStore.get(p), KnowledgeType.OBSERVATION, category, raw);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static ItemStack item(String id) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
        require(item != null && item != Items.AIR, "Missing transfuser item " + id); return new ItemStack(item);
    }
    private static void held(ServerPlayer p, ItemStack stack) {
        p.getInventory().selected = 0; p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.inventoryMenu.broadcastChanges();
    }
    private static void close(Minecraft mc) { mc.player.closeContainer(); mc.setScreen(null); }
    private static void click(Minecraft mc, BlockPos pos, Direction face) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(pos.getCenter(), face, pos, false));
    }
    private static void lookDown(Minecraft mc) {
        mc.player.setXRot(80);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), 80, mc.player.onGround()));
    }
    private static void shift(Minecraft mc, boolean value) {
        mc.player.input.shiftKeyDown = value; mc.player.setShiftKeyDown(value);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
                value ? net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                        : net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
    }
    private static void view(Minecraft mc) { if (!(mc.screen instanceof Gallery gallery) || gallery.renderedScene != scene) mc.setScreen(new Gallery()); }
    private static ArcaneWorkbenchBlockEntity bench(Minecraft mc) { return (ArcaneWorkbenchBlockEntity) player(mc).serverLevel().getBlockEntity(BENCH); }
    private static AlembicBlockEntity source(Minecraft mc) { return (AlembicBlockEntity) player(mc).serverLevel().getBlockEntity(SOURCE); }
    private static EssentiaJarBlockEntity jar(Minecraft mc, BlockPos pos) { return (EssentiaJarBlockEntity) player(mc).serverLevel().getBlockEntity(pos); }
    private static int clientAmount(Minecraft mc, BlockPos pos) {
        var tile = mc.level.getBlockEntity(pos);
        return tile instanceof AlembicBlockEntity a ? a.amount() : tile instanceof EssentiaJarBlockEntity j ? j.amount() : -1;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.essentiaTransfuserSmokeTest") || stopped) return;
        var mc = Minecraft.getInstance(); if (began == 0) began = System.nanoTime();
        try {
            require(System.nanoTime() - began < 900_000_000_000L, "Transfuser timeout scene=" + scene + " phase=" + phase);
            if (!started) { start(mc); return; }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null || mc.screen instanceof ReceivingLevelScreen) return;
            require(mc.getSingleplayerServer() != null && WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()), "Wrong transfuser world");
            mc.getToasts().clear();
            if (++wait % 100 == 0) LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_HEARTBEAT: scene={} phase={} source={} mid={} collector={} screen={} selected={} hand={}",
                    scene, phase, clientAmount(mc, SOURCE), clientAmount(mc, MID), clientAmount(mc, COLLECTOR),
                    mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), mc.player.getInventory().selected, mc.player.getMainHandItem());
            if (work != null) { if (!work.isDone()) return; work.join(); work = null; }
            if (!prepared) { prepared = true; submit(mc, () -> prepare(mc)); return; }
            if (captured) {
                if (saved.get() == scene + 1 && ++stable >= (scene == 3 || scene == 7 ? 8 : 20)) { scene++; phase = stable = wait = 0; captured = captureRequested = false; }
                return;
            }
            if (scene == IMAGES.length) { finish(mc); return; }
            if (!advance(mc)) return;
            if (++stable >= (scene == 3 || scene == 7 ? 3 : 15) && !captured) captureRequested = true;
        } catch (Throwable e) { fail(mc, e); }
    }

    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.essentiaTransfuserSmokeTest") || stopped || !captureRequested || captured) return;
        var mc = Minecraft.getInstance();
        try {
            require(scene < IMAGES.length, "Invalid transfuser image scene"); captured = true; captureRequested = false;
            String name = "tc6-essentia-transfuser-" + IMAGES[scene] + ".png";
            var file = new File(new File(mc.gameDirectory, "screenshots"), name); Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
                if (file.isFile() && file.length() > 0) { saved.incrementAndGet(); LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_SMOKE_IMAGE: {}", file.getAbsolutePath()); }
                else mc.execute(() -> fail(mc, new AssertionError("Missing transfuser image")));
            });
        } catch (Throwable e) { fail(mc, e); }
    }

    private static boolean advance(Minecraft mc) {
        if (scene == 0) return research(mc);
        if (scene == 1 || scene == 5) return recipe(mc, scene == 1 ? "essentia_input" : "essentia_output");
        if (scene == 2 || scene == 6) return placement(mc, scene == 2);
        if (scene == 3) {
            if (phase == 0) { phase = 1; close(mc); submit(mc, () -> {
                // Stage the explicit raw essentia fixture only after the placed-device
                // image, so native ticks cannot finish before the active scene starts.
                require(source(mc).amount() == 0 && jar(mc, MID).amount() == 0 && source(mc).addExact(Aspect.FIRE, 24), "Deferred24Ignis source fixture failed");
                player(mc).connection.teleport(2.5, 114, 4.5, 180, 15);
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_SOURCE_FIXTURE: raw24Ignis added to actual source Alembic only after paid input placement, before native insertion");
            }); return false; }
            int mid = clientAmount(mc, MID);
            if (mid <= 0 || mid >= 24) return false;
            if (phase == 1) { phase = 2; submit(mc, () -> {
                require(source(mc).amount() + jar(mc, MID).amount() == 24 && jar(mc, COLLECTOR).amount() == 0, "Air insertion duplicated or lost Ignis");
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_AIR_INSERT: actual paid UP input; ordinary fifth-tick sourceAlembic->air->jar; source={} mid={}", source(mc).amount(), jar(mc, MID).amount());
            }); return false; }
            // Native world rendering keeps the real packet-driven air trail visible.
            return mc.screen == null && mc.player.distanceToSqr(2.5, 114, 4.5) < .25;
        }
        if (scene == 4) {
            if (clientAmount(mc, SOURCE) != 0 || clientAmount(mc, MID) != 24) return false;
            if (phase == 0) { phase = 1; submit(mc, () -> {
                require(source(mc).amount() == 0 && jar(mc, MID).amount() == 24 && jar(mc, MID).aspect() == Aspect.FIRE && jar(mc, COLLECTOR).amount() == 0, "First complete air transfer wrong");
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_AIR_INSERT_COMPLETE: exactly24Ignis moved, source0/mid24/collector0; output device not yet crafted or placed");
            }); return false; }
            view(mc); return true;
        }
        if (scene == 7) {
            if (phase == 0) { phase = 1; close(mc); submit(mc, () -> {
                require(source(mc).amount() == 0 && jar(mc, MID).amount() == 24 && jar(mc, COLLECTOR).amount() == 0
                        && jar(mc, COLLECTOR).applyLabel(player(mc), Direction.NORTH, Aspect.FIRE), "Deferred collector Ignis label fixture failed");
                player(mc).connection.teleport(6.5, 115, 4.5, 180, 25);
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_LABEL_FIXTURE: empty rear collector receives explicit Ignis filter after paid output placement; real positive typed suction activates ordinary output ticks");
            }); return false; }
            int collector = clientAmount(mc, COLLECTOR);
            if (collector <= 0 || collector >= 24) return false;
            if (phase == 1) { phase = 2; submit(mc, () -> {
                require(source(mc).amount() == 0 && jar(mc, MID).amount() + jar(mc, COLLECTOR).amount() == 24, "Air drain duplicated or lost Ignis");
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_AIR_DRAIN: actual paid UP output; ordinary fifth-tick airJar->rear DOWN consumerJar; mid={} collector={}", jar(mc, MID).amount(), jar(mc, COLLECTOR).amount());
            }); return false; }
            return mc.screen == null && mc.player.distanceToSqr(6.5, 115, 4.5) < .25;
        }
        if (scene == 8) {
            if (clientAmount(mc, SOURCE) != 0 || clientAmount(mc, MID) != 0 || clientAmount(mc, COLLECTOR) != 24) return false;
            if (phase == 0) { phase = 1; submit(mc, () -> {
                var p = player(mc);
                require(source(mc).amount() == 0 && jar(mc, MID).amount() == 0 && jar(mc, COLLECTOR).amount() == 24 && jar(mc, COLLECTOR).aspect() == Aspect.FIRE, "Complete pipeline lost typed24Ignis");
                require(p.getInventory().getItem(2).isEmpty() && p.getInventory().getItem(3).isEmpty(), "Paid device placement left duplicate items");
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_PIPELINE_COMPLETE: source0/mid0/collector24Ignis; two actually paid placed devices, native ticks only; no direct transfer/tick calls");
            }); return false; }
            view(mc); return true;
        }
        if (scene == 9) { view(mc); return true; }
        return false;
    }

    private static boolean research(Minecraft mc) {
        if (phase == 0) {
            phase = 1; submit(mc, () -> {
                var p = player(mc); knowledge(p, "ALCHEMY", 16); knowledge(p, "ARTIFICE", 16);
                var k = KnowledgeStore.get(p); obsAlchemyBefore = k.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY");
                obsArtificeBefore = k.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE"); xpBefore = p.totalExperience;
                held(p, new ItemStack(ResearchModule.THAUMONOMICON.get())); ResearchNetwork.sync(p);
            }); return false;
        }
        if (phase == 1) {
            if (!mc.player.getMainHandItem().is(ResearchModule.THAUMONOMICON.get())) return false;
            var k = ResearchClient.golemPressKnowledge();
            if (!k.isResearchCompleteStrict(KEY)) {
                int current = k.researchStage(KEY); if (current == expectedStage) return false;
                expectedStage = current; ResearchNetwork.requestAdvance(KEY, current); return false;
            }
            phase = 2; submit(mc, () -> {
                var p = player(mc); var kServer = KnowledgeStore.get(p);
                require(kServer.isResearchCompleteStrict(KEY) && kServer.rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == obsAlchemyBefore - 16
                        && kServer.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == obsArtificeBefore - 16 && p.totalExperience == xpBefore + 10,
                        "Actual EssentiaTransport C2S research did not pay16+16 observations/award5XP each for start and completion");
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_RESEARCH_PAID: actual ESSENTIATRANSPORT C2S,16AlchemyObs+16ArtificeObs,10XP total(start5+completion5); predecessorTHAUMATORIUM/INFUSION fixture");
            }); return false;
        }
        if (!(mc.screen instanceof ThaumonomiconPageScreen)) {
            var k = ResearchClient.golemPressKnowledge(); var book = new ThaumonomiconScreen(k, k.scanCount());
            mc.setScreen(new ThaumonomiconPageScreen(book, ResearchCatalog.get(KEY), k, k.scanCount()));
        }
        return true;
    }

    private static boolean recipe(Minecraft mc, String output) {
        if (phase == 0) {
            phase = 1; close(mc); submit(mc, () -> {
                var p = player(mc); p.connection.teleport(-5.5, 112, 2.5, 180, 20); held(p, ItemStack.EMPTY); prepareArcane(mc, output);
            }); return false;
        }
        if (phase == 1) { if (!mc.player.getMainHandItem().isEmpty()) return false; phase = 2; click(mc, BENCH, Direction.UP); return false; }
        if (!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu) || !menu.getSlot(0).getItem().is(item(output).getItem()) || !menu.craftable()) return false;
        require(menu.requiredVis() == 100 && menu.crystalCost(0) == 1 && menu.crystalCost(2) == 1, "Original transfuser100vis/Aer1/Aqua1 absent");
        return true;
    }

    private static boolean placement(Minecraft mc, boolean filling) {
        String id = filling ? "essentia_input" : "essentia_output"; int slot = filling ? 2 : 3;
        BlockPos position = filling ? INPUT : OUTPUT, support = position.below();
        if (phase == 0) {
            placementReadyTicks = 0;
            phase = 1; var menu = (ArcaneWorkbenchMenu) mc.player.containerMenu;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 0, slot, ClickType.SWAP, mc.player); return false;
        }
        if (phase == 1) {
            if (!mc.player.getInventory().getItem(slot).is(item(id).getItem())) return false;
            phase = 2; close(mc); submit(mc, () -> {
                var p = player(mc); require(bench(mc).isEmpty(), "Craft did not consume original components/crystals: " + id);
                require(AuraManager.getVis(p.serverLevel(), BENCH) == auraBefore - 100, "Craft did not pay exact100vis: " + id);
                p.getInventory().selected = slot; p.connection.teleport(position.getX() + .5, position.getY(), position.getZ() + 2.5, 180, 80); p.inventoryMenu.broadcastChanges();
                LogUtils.getLogger().info(filling ? "THAUMCRAFT_ESSENTIA_TRANSFUSER_INPUT_PAID: actual bench C2S,100vis/Aer1/Aqua1,all original ingredients consumed; one output" :
                        "THAUMCRAFT_ESSENTIA_TRANSFUSER_OUTPUT_PAID: actual bench C2S,100vis/Aer1/Aqua1,all original ingredients consumed; one output");
            }); return false;
        }
        mc.player.getInventory().selected = slot;
        if (phase == 2) {
            // A server-side teleport completes its future before the client has necessarily
            // received/acknowledged it. An old-position click may be rejected even though the
            // newly crafted hotbar item has already synchronized. Wait for both, then send an
            // explicit carried-slot packet before the native use request on the same channel.
            double dx = mc.player.getX() - (position.getX() + .5), dz = mc.player.getZ() - (position.getZ() + 2.5);
            boolean ready = mc.player.getInventory().selected == slot && mc.player.getMainHandItem().is(item(id).getItem())
                    && mc.player.getMainHandItem().getCount() == 1 && dx * dx + dz * dz < .25
                    && Math.abs(mc.player.getY() - position.getY()) < 3 && mc.player.distanceToSqr(support.getCenter()) <= 16;
            if (!ready) { placementReadyTicks = 0; return false; }
            if (++placementReadyTicks < 3) return false;
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
            LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_PLACEMENT_READY: {} clientteleportsynchronized,pos={},selected={},hand={},supportDistanceSq={}",
                    id, mc.player.position(), slot, mc.player.getMainHandItem(), mc.player.distanceToSqr(support.getCenter()));
            // Both native Alembic and Jar consume ordinary block activation. Real secondary
            // use bypasses that activation, permitting the paid BlockItem to place above them.
            phase = 3; shift(mc, true); lookDown(mc); click(mc, support, Direction.UP); return false;
        }
        if (!(mc.level.getBlockEntity(position) instanceof EssentiaTransfuserBlockEntity)) return false;
        if (phase == 3) {
            shift(mc, false);
            phase = 4; submit(mc, () -> {
                var p = player(mc); require(p.getInventory().getItem(slot).isEmpty(), "Placement failed to consume actual paid item: " + id);
                require(p.serverLevel().getBlockState(position).getValue(EssentiaTransfuserBlock.FACING) == Direction.UP, "Actual placed device front did not faceUP");
                require(filling ? p.serverLevel().getBlockEntity(support) instanceof AlembicBlockEntity
                        : p.serverLevel().getBlockEntity(support) instanceof EssentiaJarBlockEntity, "Missing rear physical endpoint");
                held(p, ItemStack.EMPTY);
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_PLACEMENT: {} actual paid BlockItem C2S,faceUP,rearDOWN,position={}", id, position);
            }); return false;
        }
        view(mc); return true;
    }

    private static void prepareArcane(Minecraft mc, String output) {
        var p = player(mc); var level = p.serverLevel(); level.setBlockAndUpdate(BENCH, ArcaneModule.WORKBENCH.get().defaultBlockState()); bench(mc).clearContent();
        var recipe = level.getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream()
                .filter(r -> r.getResultItem(level.registryAccess()).is(item(output).getItem())).findFirst().orElseThrow();
        require(recipe.gridWidth() == 3 && recipe.gridHeight() == 2, "Original leading blank row must trim to3x2");
        for (int i = 0; i < 6; i++) { var ingredient = recipe.getIngredients().get(i);
            if (!ingredient.isEmpty()) { require(ingredient.getItems().length > 0, "Empty recipe input tag"); bench(mc).setItem(i, ingredient.getItems()[0].copyWithCount(1)); }
        }
        for (int i = 0; i < 6; i++) if (recipe.crystalCost(i) > 0) bench(mc).setItem(9 + i, AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i]), recipe.crystalCost(i)));
        // Full moon makes phaseFlux zero. Explicit high-flux neighbouring aura fixtures
        // exclude vis-regeneration/diffusion candidates before and after the real debit.
        // Equal low-flux neighbours alone would refill a paid bench from neighbouring chunks.
        level.setDayTime(6000); var chunk = new net.minecraft.world.level.ChunkPos(BENCH);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var anchor = new net.minecraft.world.level.ChunkPos(chunk.x + dx, chunk.z + dz).getMiddleBlockPosition(112);
            AuraManager.drainVis(level, anchor, Float.MAX_VALUE, false); AuraManager.addVis(level, anchor, 250);
            AuraManager.drainFlux(level, anchor, Float.MAX_VALUE, false); AuraManager.addFlux(level, anchor, 1000);
        }
        auraBefore = AuraManager.getVis(level, BENCH);
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_ARCANE_FIXTURE: {} original physical input grid/crystals,aura250/fullmoon/loadedneighboursflux1000 isolate exact100vis debit; no finished output supplied", output);
    }

    private static void prepare(Minecraft mc) {
        var p = player(mc); var level = p.serverLevel(); p.setInvulnerable(true); p.connection.teleport(-5.5, 112, 2.5, 180, 20);
        for (var pos : BlockPos.betweenClosed(-9, 111, -5, 11, 119, 5)) level.setBlockAndUpdate(pos, pos.getY() == 111 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(x, z);
        level.setBlockAndUpdate(new BlockPos(2, 113, 4), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(6, 114, 4), Blocks.STONE.defaultBlockState());
        p.getInventory().clearContent(); for (String key : List.of("THAUMATORIUM", "INFUSION")) stage(p, key);
        level.setBlockAndUpdate(SOURCE, CatalogBlocks.block("alembic").defaultBlockState());
        level.setBlockAndUpdate(MID.below(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(MID, CatalogBlocks.block("jar_normal").defaultBlockState());
        level.setBlockAndUpdate(COLLECTOR.below(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(COLLECTOR, CatalogBlocks.block("jar_normal").defaultBlockState());
        require(source(mc).amount() == 0 && jar(mc, MID).amount() == 0 && jar(mc, COLLECTOR).amount() == 0, "Fixture seeded destination contents");
        p.inventoryMenu.broadcastChanges(); ResearchNetwork.sync(p);
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_FIXTURE: owned isolated world; precedingTHAUMATORIUM/INFUSION,arena/loadedregion/emptyphysicalendpoints are explicitfixtures; rawAlembic24Ignis and collectorIgnislabel are deferred until their active scenes; ESSENTIATRANSPORT and both finished devices absent");
    }
    private static void start(Minecraft mc) {
        if (mc.screen instanceof AccessibilityOnboardingScreen) { mc.options.onboardAccessibility = false; mc.options.save(); mc.setScreen(new TitleScreen()); return; }
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        started = true; previous = mc.options.tutorialStep; mc.options.tutorialStep = TutorialSteps.NONE; mc.getTutorial().stop();
        mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(3); mc.options.simulationDistance().set(5); mc.options.guiScale().set(2); mc.resizeDisplay();
        var rules = new GameRules(); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null); rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(0x54433625L, false, false), WorldPresets::createNormalWorldDimensions);
    }
    private static void finish(Minecraft mc) {
        require(saved.get() == IMAGES.length, "Missing ten fresh transfuser captures"); stopped = true; mc.options.tutorialStep = previous;
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_RENDER_AUDIT_OK: native paid bench/book,actual synchronized source/jars/two devices,two active native-world trail scenes,original six-face baked states; gallery orientation variants are read-only render previews of paid devices; screenshots={}", saved.get());
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_TRANSFUSER_CLIENT_SMOKE_OK: 10 scenes; real ESSENTIATRANSPORT research C2S/16+16Obs/10XP(start5+completion5),two100vis/Aer1/Aqua1 bench crafts,actual UP placements,ordinary fifth-tick source24->airjar24->rearcollector24; predecessor/inputs/aura/endpoints explicitfixtures; world={}", WORLD); mc.stop();
    }
    private static void fail(Minecraft mc, Throwable e) {
        if (stopped) return; stopped = true; if (previous != null) mc.options.tutorialStep = previous;
        LogUtils.getLogger().error("THAUMCRAFT_ESSENTIA_TRANSFUSER_CLIENT_SMOKE_FAILED", e); mc.stop();
    }

    private static final class Gallery extends Screen {
        private final int renderedScene;
        Gallery() { super(Component.literal("Thaumcraft 6 / " + IMAGES[scene])); renderedScene = scene; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics gui, int x, int y, float partial) {
            var mc = Minecraft.getInstance();
            try {
                gui.fill(0, 0, width, height, 0xff18202b); gui.drawCenteredString(font, title, width / 2, 15, 0xffefdbac); gui.flush(); Lighting.setupFor3DItems();
                if (renderedScene == 9) {
                    Direction[] faces = Direction.values();
                    for (int i = 0; i < faces.length; i++) {
                        int cx = width / 2 + (i % 3 - 1) * 140, cy = height / 2 - 50 + (i / 3) * 125;
                        for (int device = 0; device < 2; device++) {
                            gui.pose().pushPose(); gui.pose().translate(cx + (device == 0 ? -30 : 30), cy, 200); gui.pose().scale(47, -47, 47);
                            gui.pose().mulPose(Axis.XP.rotationDegrees(22)); gui.pose().mulPose(Axis.YP.rotationDegrees(145));
                            var paidState = mc.level.getBlockState(device == 0 ? INPUT : OUTPUT);
                            require(paidState.getBlock() instanceof EssentiaTransfuserBlock, "Paid device absent from orientation gallery");
                            gui.pose().translate(-.5, 0, -.5);
                            mc.getBlockRenderer().renderSingleBlock(paidState.setValue(EssentiaTransfuserBlock.FACING, faces[i]), gui.pose(), gui.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                            gui.flush(); gui.pose().popPose();
                        }
                        gui.drawCenteredString(font, faces[i].getName() + " / in + out", cx, cy + 35, 0xffd4d9df);
                    }
                    gui.drawCenteredString(font, "Read-only baked orientation previews from the two actual paid blocks", width / 2, height - 52, 0xffd4d9df);
                } else {
                    gui.pose().pushPose(); gui.pose().translate(width / 2, height / 2 + 75, 200); gui.pose().scale(44, -44, 44);
                    gui.pose().mulPose(Axis.XP.rotationDegrees(24)); gui.pose().mulPose(Axis.YP.rotationDegrees(145));
                    for (BlockPos pos : List.of(SOURCE, INPUT, MID, OUTPUT, COLLECTOR)) {
                        if (mc.level.getBlockState(pos).isAir()) continue;
                        gui.pose().pushPose(); gui.pose().translate(pos.getX() - 4, pos.getY() - SOURCE.getY(), pos.getZ());
                        block(gui, pos); gui.pose().popPose();
                    }
                    gui.flush(); gui.pose().popPose();
                    gui.drawCenteredString(font, "Ignis: source " + clientAmount(mc, SOURCE) + " / air jar " + clientAmount(mc, MID) + " / rear collector " + clientAmount(mc, COLLECTOR), width / 2, height - 60, 0xffffff);
                    gui.drawCenteredString(font, "Input front UP, source below / output front UP, consumer below", width / 2, height - 45, 0xffd4d9df);
                }
                gui.flush(); gui.drawCenteredString(font, "Actual integrated-server paid outputs and ordinary five-tick transfers", width / 2, height - 22, 0xffd4d9df);
            } catch (Throwable e) { fail(mc, e); }
        }
        private static void block(GuiGraphics gui, BlockPos pos) {
            var mc = Minecraft.getInstance(); var state = mc.level.getBlockState(pos);
            require(!state.isAir(), "Missing live transfuser gallery block " + pos);
            if (state.getRenderShape() == RenderShape.MODEL) mc.getBlockRenderer().renderSingleBlock(state, gui.pose(), gui.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
            var tile = mc.level.getBlockEntity(pos);
            if (tile != null && mc.getBlockEntityRenderDispatcher().getRenderer(tile) != null)
                require(!mc.getBlockEntityRenderDispatcher().renderItem(tile, gui.pose(), gui.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY), "Missing live transfuser endpoint renderer " + pos);
        }
    }
}
