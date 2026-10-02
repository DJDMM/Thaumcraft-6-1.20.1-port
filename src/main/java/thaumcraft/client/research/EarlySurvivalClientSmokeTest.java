package thaumcraft.client.research;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchModule;
import thaumcraft.research.ResearchNetwork;
import thaumcraft.research.ResearchProgression;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated real-world recipe rendering and S2C knowledge checks; prerequisite facts are a test fixture. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class EarlySurvivalClientSmokeTest {
    private static final String WORLD = "thaumcraft-early-survival-smoke-" + System.currentTimeMillis();
    private static final String[] IMAGES = {"alchemy-graph", "thaumium-locked-archive", "brass-stage-one", "thaumium-stage-two",
            "enchanted-fabric", "goggles", "thaumometer-crystals", "aspect-crystal", "salis-mundus"};
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static boolean started, stopped, setup, submitted, opened, captured;
    private static int scene, ticks, stableTicks;
    private static long began;
    private static CompletableFuture<Void> work;
    private static volatile CompoundTag received;
    private static ThaumonomiconScreen browser;
    private static String unchanged;
    private static TutorialSteps previousTutorial;

    private EarlySurvivalClientSmokeTest() {}
    static void snapshot(CompoundTag data) {
        if (Boolean.getBoolean("thaumcraft.earlySurvivalSmokeTest")) received = data.copy();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.earlySurvivalSmokeTest") || stopped) return;
        Minecraft mc = Minecraft.getInstance();
        if (began == 0) began = System.nanoTime();
        try {
            require(++ticks < 6000 && System.nanoTime() - began < 300_000_000_000L, "Early survival UI smoke timed out");
            if (!started) { startWorld(mc); return; }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            require(mc.getSingleplayerServer() != null && WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()), "Wrong UI smoke world");
            if (work != null) {
                if (!work.isDone()) return;
                work.join(); work = null;
            }
            if (!setup) { submit(mc, () -> prepare(mc)); setup = true; return; }
            if (scene == IMAGES.length) {
                require(SAVED.get() == IMAGES.length, "Missing recipe screenshots");
                stopped = true;
                if (previousTutorial != null) mc.options.tutorialStep = previousTutorial;
                LogUtils.getLogger().info("THAUMCRAFT_EARLY_SURVIVAL_CLIENT_SMOKE_OK: {} screenshots; actual integrated recipe manager; S2C crucible recipes and paid stages; archive read only; ingredients, vis, six crystals and locks", SAVED.get());
                mc.stop(); return;
            }
            if (!submitted) { received = null; submit(mc, () -> prepareScene(mc)); submitted = true; return; }
            if (received == null || BookRecipeViews.previewCount() < 2) return;
            PlayerKnowledge authoritative = PlayerKnowledge.load(received);
            if (authoritative.researchStage("METALLURGY") != (scene < 3 ? 1 : 2)
                    || scene >= 4 && !authoritative.isResearchCompleteStrict("UNLOCKINFUSION")
                    || scene >= 5 && !authoritative.isResearchCompleteStrict("UNLOCKARTIFICE")) return;
            if (!opened) { openScene(mc, authoritative); opened = true; return; }
            if (++stableTicks < 30) return;
            require(unchanged.equals(PlayerKnowledge.load(received).save().toString()), "Viewing a recipe mutated authoritative knowledge");
            if (!captured) { capture(mc); captured = true; return; }
            if (SAVED.get() != scene + 1 || stableTicks < 40) return;
            scene++; submitted = opened = captured = false; stableTicks = 0;
        } catch (Exception | AssertionError failure) { fail(mc, failure); }
    }

    private static void startWorld(Minecraft mc) {
        if (mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false; mc.options.save(); mc.setScreen(new TitleScreen()); return;
        }
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        started = true; previousTutorial = mc.options.tutorialStep;
        mc.options.tutorialStep = TutorialSteps.NONE; mc.getTutorial().stop(); mc.getToasts().clear();
        mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(3); mc.options.simulationDistance().set(5);
        mc.options.guiScale().set(2); mc.options.cloudStatus().set(CloudStatus.OFF); mc.resizeDisplay();
        GameRules rules = new GameRules(); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null); rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        var settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        LogUtils.getLogger().info("THAUMCRAFT_EARLY_SURVIVAL_SMOKE_WORLD: {}", WORLD);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0x54433611L, false, false), WorldPresets::createNormalWorldDimensions);
    }

    private static net.minecraft.server.level.ServerPlayer player(Minecraft mc) {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        require(player != null, "Missing integrated player"); return player;
    }
    private static void prepare(Minecraft mc) {
        var player = player(mc);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        player.setInvulnerable(true);
        // Seed prior, separately tested starter actions. All new stages below use the actual server payment path.
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        for (String category : List.of("BASICS", "ALCHEMY", "ARTIFICE", "INFUSION")) {
            KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, category, 160);
            KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, category, 160);
        }
        KnowledgeStore.of(player.serverLevel()).recordCraft(player.getUUID(), "thaumcraft:arcane_workbench");
        KnowledgeStore.of(player.serverLevel()).recordCraft(player.getUUID(), "thaumcraft:thaumometer");
        KnowledgeStore.of(player.serverLevel()).recordCraft(player.getUUID(), "thaumcraft:crucible");
        KnowledgeStore.of(player.serverLevel()).recordCraft(player.getUUID(), "thaumcraft:nitor");
        complete(player, "FIRSTSTEPS"); complete(player, "UNLOCKALCHEMY"); complete(player, "BASEALCHEMY");
        require(ResearchProgression.advance(player, "METALLURGY", 0) == ResearchProgression.Result.STARTED, "Cannot start metallurgy");
    }
    private static void complete(net.minecraft.server.level.ServerPlayer player, String key) {
        for (int attempt = 0; attempt < 10 && !KnowledgeStore.get(player).isResearchCompleteStrict(key); attempt++) {
            var result = ResearchProgression.advance(player, key, KnowledgeStore.get(player).researchStage(key));
            require(List.of(ResearchProgression.Result.STARTED, ResearchProgression.Result.ADVANCED, ResearchProgression.Result.COMPLETE).contains(result), "Cannot complete fixture " + key + ": " + result);
        }
        require(KnowledgeStore.get(player).isResearchCompleteStrict(key), "Fixture did not complete " + key);
    }
    private static void prepareScene(Minecraft mc) {
        var player = player(mc);
        if (scene == 3) {
            KnowledgeStore.of(player.serverLevel()).recordCraft(player.getUUID(), "thaumcraft:ingot_brass");
            int before = KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY");
            require(ResearchProgression.advance(player, "METALLURGY", 1) == ResearchProgression.Result.ADVANCED, "Metallurgy payment failed");
            require(KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == before - 16, "Metallurgy did not spend one observation");
        }
        if (scene == 4) {
            for (Aspect aspect : List.of(Aspect.AURA, Aspect.MAGIC)) KnowledgeStore.discoverAspect(player, aspect);
            complete(player, "UNLOCKINFUSION"); complete(player, "BASEINFUSION");
        }
        if (scene == 5) {
            for (Aspect aspect : List.of(Aspect.SENSES, Aspect.MECHANISM)) KnowledgeStore.discoverAspect(player, aspect);
            complete(player, "UNLOCKARTIFICE"); complete(player, "BASEARTIFICE");
        }
        ResearchNetwork.sync(player);
    }

    private static void openScene(Minecraft mc, PlayerKnowledge knowledge) {
        unchanged = knowledge.save().toString();
        browser = new ThaumonomiconScreen(knowledge, knowledge.scanCount()); mc.setScreen(browser);
        if (scene == 0) {
            browser.selectCategoryForSmokeTest("ALCHEMY");
            require(browser.entriesForSmokeTest().contains("METALLURGY"), "Metallurgy node missing"); return;
        }
        String key = switch (scene) { case 1, 2, 3 -> "METALLURGY"; case 4 -> "UNLOCKINFUSION"; case 5 -> "UNLOCKARTIFICE"; case 6, 8 -> "FIRSTSTEPS"; default -> "BASEALCHEMY"; };
        if (scene == 1) browser.archiveForSmokeTest(true);
        browser.selectForSmokeTest(key);
        require(mc.screen instanceof ThaumonomiconPageScreen, "Book did not open " + key);
        var page = (ThaumonomiconPageScreen)mc.screen;
        if (scene == 1) { page.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0); page.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0); }
        String output = switch (scene) { case 1, 3 -> "ingot_thaumium"; case 2 -> "ingot_brass"; case 4 -> "fabric"; case 5 -> "goggles"; case 6 -> "thaumometer"; case 8 -> "salis_mundus"; default -> "crystal_essence"; };
        page.showRecipeForSmokeTest(output);
        var view = page.recipesForSmokeTest().stream().filter(v -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(v.output().getItem()).getPath().equals(output)).findFirst().orElseThrow();
        require(view.unlocked(knowledge) == (scene != 1), "Incorrect research lock for " + output);
        require(!view.ingredients().isEmpty() && view.ingredients().stream().anyMatch(i -> i.getItems().length > 0), "No actual ingredients for " + output);
        if (scene == 4) require(view.vis() == 5 && page.recipesForSmokeTest().stream().filter(v -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(v.output().getItem()).getPath().startsWith("cloth_")).count() == 3, "Fabric/robe recipes missing");
        if (scene == 5) require(view.vis() == 50, "Goggles cost differs from BETA26");
        if (scene == 6) { require(view.vis() == 20, "Thaumometer vis differs"); for (int cost : view.crystals()) require(cost == 1, "Missing primal crystal cost"); }
        if (scene == 7) require(view.aspects().visSize() == 2, "Crystal recipe cost differs");
        if (scene == 8) {
            require(view.kind().equals("salis") && view.ingredients().size() == 6, "Missing actual custom Salis preview");
            require(view.ingredients().get(0).test(new ItemStack(Items.FLINT)) && view.ingredients().get(1).test(new ItemStack(Items.BOWL))
                    && view.ingredients().get(2).test(new ItemStack(Items.REDSTONE)), "Wrong Salis mundane ingredients");
            java.util.Set<Aspect> samples = new java.util.HashSet<>();
            for (int i = 3; i < 6; i++) samples.add(thaumcraft.alchemy.AspectCrystalItem.crystalAspect(view.ingredients().get(i).getItems()[0]));
            require(samples.size() == 3 && !samples.contains(null) && samples.stream().anyMatch(a -> !a.isPrimal()), "Salis preview restricted or duplicated aspects");
            require(mc.level.getRecipeManager().getRecipes().stream().anyMatch(recipe -> recipe instanceof thaumcraft.alchemy.SalisMundusRecipe
                    && recipe.getId().equals(view.id())), "Preview invented a missing custom recipe");
        }
        if (scene == 1) require(!page.availableForSmokeTest(), "Archive enabled progression payment");
    }

    private static void submit(Minecraft mc, Runnable task) {
        require(work == null, "Overlapping UI server tasks"); work = new CompletableFuture<>();
        var result = work;
        mc.getSingleplayerServer().execute(() -> { try { task.run(); result.complete(null); } catch (Throwable error) { result.completeExceptionally(error); } });
    }
    private static void capture(Minecraft mc) throws Exception {
        String name = "tc6-early-survival-" + IMAGES[scene] + ".png";
        File file = new File(new File(mc.gameDirectory, "screenshots"), name); Files.deleteIfExists(file.toPath());
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
            if (file.isFile() && file.length() > 0) { SAVED.incrementAndGet(); LogUtils.getLogger().info("THAUMCRAFT_EARLY_SURVIVAL_SMOKE_IMAGE: {}", file.getAbsolutePath()); }
            else mc.execute(() -> fail(mc, new AssertionError("Missing screenshot " + name)));
        });
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(Minecraft mc, Throwable failure) {
        if (stopped) return; stopped = true;
        if (previousTutorial != null) mc.options.tutorialStep = previousTutorial;
        LogUtils.getLogger().error("THAUMCRAFT_EARLY_SURVIVAL_CLIENT_SMOKE_FAILED", failure); mc.stop();
    }
}
