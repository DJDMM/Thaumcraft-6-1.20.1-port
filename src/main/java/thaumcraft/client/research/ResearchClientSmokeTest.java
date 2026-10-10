package thaumcraft.client.research;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicitly opted-in, world-free checks. Requests use a local sink and never send network packets. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class ResearchClientSmokeTest {
    private static final String MODE = System.getProperty("thaumcraft.clientSmokeMode", "progression").strip().toLowerCase(java.util.Locale.ROOT);
    private static final int EXPECTED_SCREENSHOTS = 13;
    private static final AtomicInteger saved = new AtomicInteger();
    private static int totalTicks, ticks;
    private static boolean started, stopped;
    private static int progressionRequests;
    private static ThaumonomiconScreen browser;
    private static String preservedView;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.clientSmokeTest") || stopped) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (++totalTicks >= 2600) { fail(minecraft, "Timed out before all screenshots and navigation checks completed", null); return; }
        try {
            if (!started) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                require(List.of("all", "progression").contains(MODE), "The historical archive smoke mode was removed; use ThaumonomiconComplete for supplied layout coverage");
                setScale(minecraft, 2);
                browser = new ThaumonomiconScreen(PlayerKnowledge.load(prepared(0, 0, 0, 0, 23, 23)), 10);
                minecraft.setScreen(browser);
                started = true;
            }
            ticks++;
            progressionTick(minecraft);
        } catch (RuntimeException | AssertionError failure) {
            fail(minecraft, "Visual smoke step " + ticks + " failed", failure);
        }
    }

    private static void progressionTick(Minecraft minecraft) {
        switch (ticks) {
            case 15 -> {
                require(browser.categoriesForSmokeTest().equals(List.of("BASICS")), "An unfinished category was visible");
                browser.searchForSmokeTest("");
                require(browser.searchResultsForSmokeTest().stream().allMatch(key -> thaumcraft.research.ResearchProgression.isImplemented(key)),
                        "Normal search exposed an unsupported entry or development lesson");
                browser.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            case 30 -> capture(minecraft, "thaumcraft-research-basics.png", ThaumonomiconScreen.class);
            case 45 -> {
                browser.selectForSmokeTest("ORE");
                require(minecraft.screen == browser, "Normal mode opened an unseen hidden ore root");
                browser.clickForSmokeTest("FIRSTSTEPS");
                require(page(minecraft).chaptersForSmokeTest().isEmpty(), "An unstarted entry exposed a future stage");
            }
            case 60 -> capture(minecraft, "thaumcraft-research-start.png", ThaumonomiconPageScreen.class);
            case 75 -> {
                ThaumonomiconPageScreen page = page(minecraft);
                page.senderForSmokeTest((key, stage) -> {
                    require(key.equals("FIRSTSTEPS") && stage == 0, "Start request had the wrong expected stage");
                    progressionRequests++;
                });
                page.clickActionForSmokeTest();
                page.clickActionForSmokeTest();
                require(progressionRequests == 1 && page.pendingForSmokeTest(), "A double click submitted the start twice");
                sync(prepared(0, 0, 0, 0, 23, 23), null);
                require(page.pendingForSmokeTest(), "An ordinary snapshot cleared the pending request");
                page.clickActionForSmokeTest();
                require(progressionRequests == 1, "A delayed response allowed another request");
            }
            case 90 -> capture(minecraft, "thaumcraft-research-pending.png", ThaumonomiconPageScreen.class);
            case 100 -> {
                sync(prepared(1, 0, 0, 0, 23, 23), "STARTED");
                require(!page(minecraft).pendingForSmokeTest(), "The acknowledgement did not clear pending");
                require(page(minecraft).chaptersForSmokeTest().equals(List.of("research.FIRSTSTEPS.stage.1")), "Stage 1 exposed future text");
            }
            case 110 -> capture(minecraft, "thaumcraft-research-firststeps-stage1.png", ThaumonomiconPageScreen.class);
            case 125 -> {
                sync(prepared(2, 0, 0, 0, 23, 23), "ADVANCED");
                ThaumonomiconPageScreen page = page(minecraft);
                page.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                require(page.chaptersForSmokeTest().equals(List.of("research.FIRSTSTEPS.stage.2")), "Chapter navigation exposed a future stage");
                require(page.chapterLabelForSmokeTest().equals(Component.translatable("thaumcraft.book.stage", 2, 3).getString()), "Stage 2 had the wrong label");
                require(page.costForSmokeTest().contains("16"), "The current knowledge cost was missing");
                lastSpread(page);
            }
            case 145 -> capture(minecraft, "thaumcraft-research-firststeps-stage2.png", ThaumonomiconPageScreen.class);
            case 160 -> {
                ThaumonomiconPageScreen page = page(minecraft);
                for (int i = 0; i < 100; i++) page.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                require(page.spreadForSmokeTest() == page.spreadCountForSmokeTest() - 1, "Spread navigation did not clamp");
                sync(prepared(1, 0, 0, 0, 23, 23), null);
                require(page.spreadForSmokeTest() == 0 && page.chaptersForSmokeTest().equals(List.of("research.FIRSTSTEPS.stage.1")),
                        "A stage snapshot did not clamp the displayed chapter and spread");
                sync(prepared(2, 0, 0, 0, 23, 23), null);
                page.senderForSmokeTest((key, stage) -> {
                    require(key.equals("FIRSTSTEPS") && stage == 2, "Advance request had the wrong expected stage");
                    progressionRequests++;
                });
                page.clickActionForSmokeTest();
                page.clickActionForSmokeTest();
                require(progressionRequests == 2 && page.pendingForSmokeTest(), "Cached controls submitted a stage twice");
                sync(prepared(4, 0, 0, 0, 7, 23), null);
                require(page.pendingForSmokeTest(), "A completion snapshot without an acknowledgement cleared pending");
                page.clickActionForSmokeTest();
                require(progressionRequests == 2, "A cached callback submitted the next stage");
                sync(prepared(4, 0, 0, 0, 7, 23), "COMPLETE");
                require(!page.pendingForSmokeTest() && page.chaptersForSmokeTest().equals(List.of("research.FIRSTSTEPS.stage.3")),
                        "Completion did not reveal the final stage");
            }
            case 180 -> capture(minecraft, "thaumcraft-research-firststeps-complete.png", ThaumonomiconPageScreen.class);
            case 195 -> {
                page(minecraft).onClose();
                sync(prepared(4, 1, 0, 0, 23, 23), null);
                require(browser.categoriesForSmokeTest().equals(List.of("BASICS")), "Alchemy opened before UNLOCKALCHEMY completed");
                browser.selectForSmokeTest("UNLOCKALCHEMY");
                require(page(minecraft).costForSmokeTest().contains("16"), "Alchemy unlock did not display its observation payment");
            }
            case 215 -> capture(minecraft, "thaumcraft-research-unlockalchemy.png", ThaumonomiconPageScreen.class);
            case 230 -> {
                sync(prepared(4, 2, 0, 0, 7, 7), "ADVANCED");
                require(page(minecraft).chaptersForSmokeTest().equals(List.of("research.UNLOCKALCHEMY.stage.2")), "Alchemy stage 2 was not isolated");
                sync(prepared(4, 3, 0, 0, 7, 7), "ADVANCED");
                require(page(minecraft).chaptersForSmokeTest().equals(List.of("research.UNLOCKALCHEMY.stage.3")), "Nitor stage was not isolated");
            }
            case 245 -> {
                sync(prepared(4, 4, 2, 0, 7, 7), "COMPLETE");
                page(minecraft).onClose();
                require(browser.categoriesForSmokeTest().equals(List.of("BASICS", "ALCHEMY")), "Alchemy did not open on completion");
                browser.selectCategoryForSmokeTest("ALCHEMY");
                require(browser.entriesForSmokeTest().contains("BASEALCHEMY") && browser.entriesForSmokeTest().stream().allMatch(key -> thaumcraft.research.ResearchProgression.isImplemented(key)), "Alchemy exposed unsupported nodes");
            }
            case 265 -> capture(minecraft, "thaumcraft-research-alchemy.png", ThaumonomiconScreen.class);
            case 280 -> {
                sync(prepared(4, 4, 2, 1, 7, 7), null);
                browser.selectForSmokeTest("ALUMENTUM");
                require(!page(minecraft).availableForSmokeTest(), "Insufficient observation allowed payment");
                require(page(minecraft).chaptersForSmokeTest().equals(List.of("research.ALUMENTUM.stage.1")), "Unpaid alumentum exposed its final recipe");
            }
            case 300 -> capture(minecraft, "thaumcraft-research-alumentum-cost.png", ThaumonomiconPageScreen.class);
            case 315 -> {
                sync(prepared(4, 4, 2, 1, 23, 7), null);
                require(page(minecraft).availableForSmokeTest(), "Sufficient observation did not enable the current stage");
                page(minecraft).onClose();
                var before=browser.entriesForSmokeTest();
                browser.selectCategoryForSmokeTest("PORT");
                require(browser.entriesForSmokeTest().equals(before), "Removed practice category was selectable");
                browser.selectForSmokeTest("ALUMENTUM");
                require(page(minecraft).availableForSmokeTest(), "Sufficient alchemy knowledge did not enable canonical payment");
            }
            case 350 -> capture(minecraft, "thaumcraft-research-alumentum-ready.png", ThaumonomiconPageScreen.class);
            case 365 -> {
                ThaumonomiconPageScreen page = page(minecraft);
                page.senderForSmokeTest((key, stage) -> { require(key.equals("ALUMENTUM") && stage == 1, "Wrong canonical alumentum request"); progressionRequests++; });
                page.clickActionForSmokeTest();
                page.clickActionForSmokeTest();
                require(progressionRequests == 3 && page.pendingForSmokeTest(), "Canonical research submitted twice");
                sync(prepared(4, 4, 2, 3, 7, 7), "COMPLETE");
                require(!page.pendingForSmokeTest(), "Canonical acknowledgement did not clear pending");
                page.onClose();
                require(browser.entriesForSmokeTest().stream().noneMatch(key -> key.startsWith("PORT_")), "Normal book exposed development lessons");
                LogUtils.getLogger().info("THAUMCRAFT_CLIENT_SMOKE_PROGRESSION_OK: current stages, knowledge costs, closed categories, delayed acknowledgement, duplicate clicks and removed practice routes");
            }
            case 380 -> {
                browser.selectCategoryForSmokeTest("BASICS");
                sync(celestialPrepared(1, 15), null);
                browser.selectForSmokeTest("CELESTIALSCANNING");
                require(!page(minecraft).availableForSmokeTest(), "Missing third celestial observation enabled payment");
                require(page(minecraft).costForSmokeTest().contains("16"), "Celestial payment was absent");
                lastSpread(page(minecraft));
            }
            case 400 -> capture(minecraft, "thaumcraft-research-celestial-cost.png", ThaumonomiconPageScreen.class);
            case 415 -> {
                sync(celestialPrepared(1, 16), null);
                ThaumonomiconPageScreen page = page(minecraft);
                require(page.availableForSmokeTest(), "All three celestial observations did not enable payment");
                page.senderForSmokeTest((key, stage) -> {
                    require(key.equals("CELESTIALSCANNING") && stage == 1, "Wrong celestial expected stage");
                    progressionRequests++;
                });
            }
            case 420 -> {
                // Let the newly enabled action render before exercising its real mouse hitbox.
                ThaumonomiconPageScreen page = page(minecraft);
                page.clickActionForSmokeTest(); page.clickActionForSmokeTest();
                require(progressionRequests == 4 && page.pendingForSmokeTest(), "Celestial request count=" + progressionRequests + ", pending=" + page.pendingForSmokeTest());
            }
            case 435 -> capture(minecraft, "thaumcraft-research-celestial-pending.png", ThaumonomiconPageScreen.class);
            case 440 -> {
                sync(celestialPrepared(2, 0), null);
                require(page(minecraft).pendingForSmokeTest(), "Unacknowledged celestial snapshot cleared pending");
            }
            case 445 -> {
                sync(celestialPrepared(2, 0), "COMPLETE");
                require(!page(minecraft).pendingForSmokeTest() && !page(minecraft).availableForSmokeTest(), "Celestial completion remained payable");
            }
            case 455 -> capture(minecraft, "thaumcraft-research-celestial-complete.png", ThaumonomiconPageScreen.class);
            case 470 -> {
                require(saved.get() == EXPECTED_SCREENSHOTS, "Progression screenshots did not finish saving");
                finish(minecraft);
            }
            default -> { }
        }
    }

    private static CompoundTag prepared(int first, int unlock, int base, int alumentum, int basicsRaw, int alchemyRaw, String... legacy) {
        CompoundTag state = new CompoundTag();
        state.putInt("Version", 2);
        state.putInt("ScanCount", 10);
        ListTag research = new ListTag();
        research.add(StringTag.valueOf("!gotthaumonomicon"));
        for (String key : legacy) research.add(StringTag.valueOf(key));
        state.put("Research", research);
        ListTag aspects = new ListTag();
        for (String aspect : new String[]{"aer", "terra", "ignis", "aqua", "ordo", "perditio"}) aspects.add(StringTag.valueOf(aspect));
        state.put("Aspects", aspects);
        ListTag crafts = new ListTag();
        for (String craft : new String[]{"thaumcraft:arcane_workbench", "thaumcraft:thaumometer", "thaumcraft:crucible", "thaumcraft:nitor"})
            crafts.add(StringTag.valueOf(craft));
        state.put("Crafts", crafts);
        CompoundTag stages = new CompoundTag();
        if (first > 0) stages.putInt("FIRSTSTEPS", first);
        if (first > 0) stages.putInt("KNOWLEDGETYPES", 2);
        if (unlock > 0) stages.putInt("UNLOCKALCHEMY", unlock);
        if (base > 0) stages.putInt("BASEALCHEMY", base);
        if (alumentum > 0) stages.putInt("ALUMENTUM", alumentum);
        state.put("ResearchStages", stages);
        CompoundTag observation = new CompoundTag();
        observation.putInt("BASICS", basicsRaw);
        observation.putInt("ALCHEMY", alchemyRaw);
        CompoundTag knowledge = new CompoundTag();
        knowledge.put("OBSERVATION", observation);
        state.put("Knowledge", knowledge);
        return state;
    }

    private static CompoundTag celestialPrepared(int stage, int auromancyRaw) {
        CompoundTag state = prepared(4, 4, 2, 1, stage == 2 ? 7 : 23, 7);
        state.getCompound("ResearchStages").putInt("THEORYRESEARCH", 3);
        state.getCompound("ResearchStages").putInt("CELESTIALSCANNING", stage);
        CompoundTag observation = state.getCompound("Knowledge").getCompound("OBSERVATION");
        observation.putInt("ARTIFICE", stage == 2 ? 0 : 16);
        observation.putInt("AUROMANCY", auromancyRaw);
        return state;
    }

    private static void sync(CompoundTag state, String result) {
        if (result != null) state.putString("ProgressResult", result);
        ResearchClient.receive(state, false);
    }

    private static void lastSpread(ThaumonomiconPageScreen page) {
        for (int i = 0; i < page.spreadCountForSmokeTest(); i++) page.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
    }

    private static ThaumonomiconPageScreen page(Minecraft minecraft) {
        expect(minecraft, ThaumonomiconPageScreen.class);
        return (ThaumonomiconPageScreen) minecraft.screen;
    }

    private static void finish(Minecraft minecraft) {
        stopped = true;
        LogUtils.getLogger().info("THAUMCRAFT_CLIENT_SMOKE_OK: mode={}; {} normal progression screenshots saved; {} simulated requests",
                MODE, saved.get(), progressionRequests);
        minecraft.stop();
    }

    private static void exerciseMap(Minecraft minecraft) {
        expect(minecraft, ThaumonomiconScreen.class);
        browser.selectCategoryForSmokeTest("BASICS");
        String initial = browser.stateForSmokeTest();
        double x = minecraft.getWindow().getGuiScaledWidth() / 2.0;
        double y = minecraft.getWindow().getGuiScaledHeight() / 2.0;
        browser.mouseScrolled(x, y, -1);
        require(!initial.equals(browser.stateForSmokeTest()), "Map scroll did not change zoom");
        String zoomed = browser.stateForSmokeTest();
        browser.mouseClicked(x, y, 0);
        browser.mouseDragged(x + 26, y + 15, 0, 26, 15);
        browser.mouseReleased(x + 26, y + 15, 0);
        require(minecraft.screen == browser, "A map drag accidentally opened a node");
        require(!zoomed.equals(browser.stateForSmokeTest()), "Map drag did not change its center");
        preservedView = browser.stateForSmokeTest();
        browser.clickForSmokeTest("FIRSTSTEPS");
        expect(minecraft, ThaumonomiconPageScreen.class);
    }

    private static void setScale(Minecraft minecraft, int scale) {
        minecraft.options.guiScale().set(scale);
        minecraft.resizeDisplay();
        LogUtils.getLogger().info("THAUMCRAFT_CLIENT_SMOKE_SCALE: requested={}, actual={}, framebuffer={}x{}, gui={}x{}",
                scale, minecraft.getWindow().getGuiScale(), minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(),
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static void expect(Minecraft minecraft, Class<? extends Screen> type) {
        require(type.isInstance(minecraft.screen), "Expected " + type.getSimpleName() + ", got "
                + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName()));
    }

    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }

    private static void capture(Minecraft minecraft, String filename, Class<? extends Screen> expectedScreen) {
        expect(minecraft, expectedScreen);
        File output = new File(new File(minecraft.gameDirectory, "screenshots"), filename);
        try {
            // Only remove this harness's own named artifact, so an old image cannot falsely pass the check.
            Files.deleteIfExists(output.toPath());
        } catch (IOException failure) {
            fail(minecraft, "Cannot replace smoke screenshot " + filename, failure); return;
        }
        Screenshot.grab(minecraft.gameDirectory, filename, minecraft.getMainRenderTarget(), message -> {
            if (output.isFile() && output.length() > 0) {
                saved.incrementAndGet();
                LogUtils.getLogger().info("THAUMCRAFT_CLIENT_SMOKE_IMAGE: {} ({}/{})", filename, saved.get(), EXPECTED_SCREENSHOTS);
            } else minecraft.execute(() -> fail(minecraft, "Screenshot not written: " + filename + "; " + message.getString(), null));
        });
    }

    private static void fail(Minecraft minecraft, String message, Throwable failure) {
        if (stopped) return;
        stopped = true;
        LogUtils.getLogger().error("THAUMCRAFT_CLIENT_SMOKE_FAILED: " + message, failure);
        minecraft.stop();
    }
}
