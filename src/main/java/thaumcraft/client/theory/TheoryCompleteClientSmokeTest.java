package thaumcraft.client.theory;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.celestial.CelestialModule;
import thaumcraft.research.theory.ResearchTableBlockEntity;
import thaumcraft.research.theory.ResearchTableMenu;
import thaumcraft.research.theory.TheoryAids;
import thaumcraft.research.theory.TheoryCard;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.theory.TheoryModule;
import thaumcraft.research.theory.TheoryNetwork;
import thaumcraft.research.theory.TheorySession;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual C2S/S2C table transactions in an isolated integrated world.
 * Late knowledge and exact offers are explicit fixtures, not a claimed survival playthrough. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class TheoryCompleteClientSmokeTest {
    // The pinned base catalogue has 10 SPIKY and 43 HIDDEN entries:
    // round(5 + 10 * .5 + 43 * .1) = 14, below the production cap of 15.
    private static final int LATE_FIXTURE_INSPIRATION = 14;
    private static final long IDLE_READINESS_TIMEOUT = 10_000_000_000L;
    private static final String WORLD = "thaumcraft-theory-complete-smoke-" + System.currentTimeMillis();
    private static final String[] IMAGES = {"idle-aids", "idle-aids-scale4", "dealing", "offers-hover", "xp-missing", "xp-paid",
            "notes-missing", "notes-paid", "no-ink", "no-paper", "owner-locked", "stale", "complete", "synthesis-wrong", "synthesis-paid", "infuse-wrong", "infuse-paid", "dark-whispers-offer", "dark-whispers-paid", "scripting-offer", "scripting-paid", "block-active"};
    private static final BlockPos TABLE = new BlockPos(0, 112, 0);
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static volatile CompoundTag received;
    private static volatile long expectedRevision;
    private static volatile String unchanged;
    private static boolean started, stopped, setup, prepared, opened, captured;
    private static int scene, phase, sceneTicks, ticks;
    private static int idleReadyTicks;
    private static long idleReadinessBegan;
    private static long began;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previousTutorial;
    private static Map<String, Integer> finishRewards;
    private static volatile TheoryCard extraCard;
    private static volatile int priorNormalWarp;
    private static boolean paidVerified;
    private static int paidResolutionBaseline, paidPlacedBaseline;
    private static long paidRequestRevision, paidRequestBegan;
    private static CompoundTag paidSelectedCard;
    private static boolean paidFrameReady;
    private static volatile List<ItemStack> paidInventory;
    private static volatile ItemStack paidInk, paidPaper;
    private static volatile int paidLevel, paidTotalExperience;
    private static volatile float paidExperienceProgress;
    private static boolean renderAudited;
    private static int blockSyncedTicks;
    private static Map<String, Integer> finishBalances;

    private TheoryCompleteClientSmokeTest() {}
    static void snapshot(int menuId, CompoundTag state) {
        if (Boolean.getBoolean("thaumcraft.theoryCompleteSmokeTest")) {
            CompoundTag previous = received; received = state.copy();
            // Handler sends an acknowledgement followed by a periodic snapshot. Preserve
            // the result at the same revision, without treating an unrelated snapshot as ACK.
            if (!received.contains("Result") && previous != null && previous.contains("Result")
                    && previous.getLong("Revision") == received.getLong("Revision")) received.putString("Result", previous.getString("Result"));
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.theoryCompleteSmokeTest") || stopped) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (began == 0) began = System.nanoTime();
            require(++ticks < 9000 && System.nanoTime() - began < 420_000_000_000L, "Complete table client smoke timed out");
            if (!started) { startWorld(mc); return; }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            require(mc.getSingleplayerServer() != null && WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()), "Wrong smoke world");
            if (work != null) { if (!work.isDone()) return; work.join(); work = null; }
            if (!setup) { submit(mc, () -> prepareWorld(mc)); setup = true; return; }
            if (scene == IMAGES.length) {
                require(SAVED.get() == IMAGES.length, "Missing complete table screenshots");
                require(renderAudited, "Complete table client smoke omitted the actual renderer audit");
                stopped = true; mc.options.tutorialStep = previousTutorial;
                LogUtils.getLogger().info("THAUMCRAFT_THEORY_COMPLETE_CLIENT_SMOKE_OK: {} scenes; actual integrated server C2S/S2C START/DRAW/SELECT/SCRAP/FINISH; 14 aid definitions, 13 original active aids; scales2/4; card deal/hover/paid resolution; exact consumed celestial notes and XP; Synthesis wrong NBT and compound output; Infuse ingredient/phial payment; Dark Whispers XP/normal Warp; Scripting extra callbacks; paper/ink/owner/stale guards; real synced BER attachments", SAVED.get());
                mc.stop(); return;
            }
            sceneTicks++;
            if (scene == 21) { blockScene(mc); return; }
            if (!opened) {
                if (!(mc.screen instanceof ResearchTableScreen)) {
                    if (mc.screen != null) mc.setScreen(null);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.UP, TABLE, false));
                    return;
                }
                opened = true;
            }
            var screen = screen(mc);
            require(screen.layoutFitsForSmokeTest() && screen.slotHitboxesForSmokeTest(), "Scaled table lost its layout or slot hitboxes");
            if (scene < 4) { starterScenes(mc, screen); return; }
            if (!prepared) { received = null; submit(mc, () -> prepareScene(mc)); prepared = true; return; }
            if (received == null || phase == 0 && screen.getMenu().revision() != expectedRevision) return;
            if (isPaidScene()) { paidScene(mc, screen); return; }
            if (scene == 17 || scene == 19) { offerScene(mc, screen); return; }
            if (scene == 12) { completionScene(mc, screen); return; }
            guardedScene(mc, screen);
        } catch (Throwable failure) { fail(mc, failure); }
    }

    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.theoryCompleteSmokeTest")
                || stopped || !isPaidScene() || phase == 0 || captured) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!(mc.screen instanceof ResearchTableScreen screen) || !currentPaidResponse(screen)
                    || screen.resolvingAnimationsForSmokeTest() <= paidResolutionBaseline) return;
            require(screen.animationBusyForSmokeTest(), "Current paid resolution ended before its synchronized image: " + clientState(mc));
            // This runs after the real GUI and vanilla HUD have rendered. A client tick
            // may receive S2C/slot/XP packets before rendering, so capture only after an
            // entire frame contains this payment's response and synchronized resources.
            if (paidInventory != null && paidClientSynchronized(mc, screen) && !paidFrameReady) {
                paidFrameReady = true;
                LogUtils.getLogger().info("THAUMCRAFT_THEORY_COMPLETE_PAID_FRAME_READY: scene={}, revision={}, resolution={} (baseline={}), vanillaXP={}/{}/{}",
                        scene, screen.getMenu().revision(), screen.resolvingAnimationsForSmokeTest(), paidResolutionBaseline,
                        mc.player.experienceLevel, mc.player.totalExperience, mc.player.experienceProgress);
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static boolean isPaidScene() { return scene == 5 || scene == 7 || scene == 14 || scene == 16 || scene == 18 || scene == 20; }

    private static void startWorld(Minecraft mc) {
        if (mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false; mc.options.save(); mc.setScreen(new TitleScreen()); return;
        }
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        started = true; previousTutorial = mc.options.tutorialStep; mc.options.tutorialStep = TutorialSteps.NONE;
        mc.getTutorial().stop(); mc.getToasts().clear(); mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(3); mc.options.simulationDistance().set(5); mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.guiScale().set(2); mc.resizeDisplay();
        GameRules rules = new GameRules(); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null); rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        var settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        LogUtils.getLogger().info("THAUMCRAFT_THEORY_COMPLETE_SMOKE_WORLD: {}", WORLD);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0x54433612L, false, false), WorldPresets::createNormalWorldDimensions);
    }
    private static ServerPlayer player(Minecraft mc) {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        require(player != null, "Missing integrated server player"); return player;
    }
    private static ResearchTableBlockEntity table(Minecraft mc) {
        return (ResearchTableBlockEntity)player(mc).serverLevel().getBlockEntity(TABLE);
    }
    private static ResearchTableScreen screen(Minecraft mc) {
        require(mc.screen instanceof ResearchTableScreen, "Research table was replaced"); return (ResearchTableScreen)mc.screen;
    }
    private static void prepareWorld(Minecraft mc) {
        var player = player(mc); var level = player.serverLevel(); player.setInvulnerable(true);
        player.teleportTo(level, .5, 112, 3.5, 180, 20);
        for (BlockPos pos : BlockPos.betweenClosed(TABLE.offset(-5, -1, -5), TABLE.offset(5, 3, 5)))
            level.setBlock(pos, pos.getY() == 111 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(TABLE, TheoryModule.TABLE.get().defaultBlockState(), 3);
        table(mc).setItem(0, new ItemStack(TheoryModule.SCRIBING_TOOLS.get())); table(mc).setItem(1, new ItemStack(Items.PAPER, 16));
        int[][] positions = {{-4,-4},{-2,-4},{0,-4},{2,-4},{4,-4},{-4,-2},{4,-2},{-4,0},{4,0},{-4,2},{4,2},{-4,4},{4,4}};
        int index = 0;
        for (String key : TheoryAids.keys()) {
            var block = TheoryAids.block(key); if (block == null) continue;
            level.setBlock(TABLE.offset(positions[index][0], 0, positions[index++][1]), block.defaultBlockState(), 2);
        }
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "cultist_portal_lesser"));
        require(type != null, "Crimson portal entity type absent"); var portal = type.create(level);
        require(portal != null, "Cannot create crimson aid fixture"); portal.moveTo(2.5, 112, 2.5); portal.setNoGravity(true); level.addFreshEntity(portal);
        require(TheoryAids.keys().size() == 14 && TheoryAids.block(TheoryAids.BASIC_ELDRITCH) == null, "BETA26 dormant aid was invented");
        require(table(mc).checkSurroundingAids().size() == 13, "Original active aid search did not find all 13 types");
        // Deliberate late-research fixture, restricted to this opt-in isolated world.
        // Archived entries are seeded too; this is not evidence that their progression
        // or mechanics are implemented. The package-private mutator remains unavailable
        // to normal gameplay, and production inspiration still uses strict completion.
        var knowledge = KnowledgeStore.get(player);
        try {
            var setter = PlayerKnowledge.class.getDeclaredMethod("setResearchStage", String.class, int.class); setter.setAccessible(true);
            for (var entry : ResearchCatalog.entries()) if (!entry.supported()) setter.invoke(knowledge, entry.key(), entry.stages().size() + 1);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot seed late research UI fixture", error); }
        int spiky = 0, hidden = 0;
        for (var entry : ResearchCatalog.entries()) {
            if (entry.supported()) continue;
            require(knowledge.isResearchCompleteStrict(entry.key()), "Late fixture did not strictly complete " + entry.key());
            if (entry.hasMeta("SPIKY")) spiky++;
            if (entry.hasMeta("HIDDEN")) hidden++;
        }
        int inspiration = TheorySession.availableInspiration(knowledge);
        require(spiky == 10 && hidden == 43, "Pinned late-fixture metadata changed: SPIKY=" + spiky + ", HIDDEN=" + hidden);
        require(inspiration == LATE_FIXTURE_INSPIRATION, "Late fixture inspiration was " + inspiration + ", expected " + LATE_FIXTURE_INSPIRATION);
        LogUtils.getLogger().info("THAUMCRAFT_THEORY_COMPLETE_LATE_FIXTURE: strictly seeded canonical stages; SPIKY={}, HIDDEN={}, inspiration={}; fixture only, not a survival progression pass", spiky, hidden, inspiration);
        KnowledgeStore.of(level).setDirty(); expectedRevision = table(mc).revision();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges();
    }

    private static void starterScenes(Minecraft mc, ResearchTableScreen screen) throws Exception {
        if (scene == 0 || scene == 1) {
            if (scene == 1 && phase == 0) { mc.options.guiScale().set(4); mc.resizeDisplay(); phase++; sceneTicks = 0; return; }
            if (!idleReadyForCapture(screen)) return;
            capture(mc); if (SAVED.get() == scene + 1) { next(); if (scene == 2) { mc.options.guiScale().set(2); mc.resizeDisplay(); } } return;
        }
        if (scene == 2) {
            if (phase == 0) {
                for (String aid : screen.getMenu().availableAids()) require(screen.clickAidForSmokeTest(aid), "Aid selector not clickable: " + aid);
                require(screen.selectedAidsForSmokeTest().size() == 13, "Aid click grid lost selections");
                require(screen.clickActionForSmokeTest(TheoryNetwork.Action.START, -1), "Start did not send C2S"); phase++; return;
            }
            if (phase == 1) {
                if (screen.pendingForSmokeTest() || screen.getMenu().session() == null) return;
                require(screen.getMenu().session().inspirationStart() == LATE_FIXTURE_INSPIRATION
                        && screen.getMenu().session().inspiration() == 1 && screen.getMenu().session().aids().size() == 13,
                        "S2C lost paid aid cost: " + clientState(mc));
                require(screen.clickActionForSmokeTest(TheoryNetwork.Action.DRAW, -1), "Draw did not send C2S"); phase++; return;
            }
            if (screen.drawnAnimationsForSmokeTest() < 1 || !screen.animationBusyForSmokeTest()) return;
            require(screen.getMenu().getSlot(1).getItem().getCount() == 15, "Actual draw did not consume paper");
            capture(mc); if (SAVED.get() == scene + 1) next(); return;
        }
        if (phase >= 2) { if (!screen.pendingForSmokeTest() && screen.getMenu().session() == null) next(); return; }
        if (screen.animationBusyForSmokeTest() || screen.getMenu().session() == null || screen.getMenu().session().choices().isEmpty()) return;
        if (phase == 0) { screen.pointerForSmokeTest(screen.cardPointForSmokeTest(0)); phase++; sceneTicks = 0; return; }
        if (phase == 1) {
            if (sceneTicks < 10) return;
            require(screen.hoverRaisedForSmokeTest(0), "Hover did not lift a dealt card"); capture(mc);
            if (SAVED.get() != scene + 1) return;
            screen.pointerForSmokeTest(null); require(screen.clickActionForSmokeTest(TheoryNetwork.Action.SCRAP, -1), "Scrap did not send C2S"); phase++; return;
        }
        if (!screen.pendingForSmokeTest() && screen.getMenu().session() == null) next();
    }

    private static boolean idleReadyForCapture(ResearchTableScreen screen) {
        if (idleReadinessBegan == 0) idleReadinessBegan = System.nanoTime();
        int aids = screen.getMenu().availableAids().size();
        int icons = screen.aidIconsForSmokeTest();
        int inspiration = screen.getMenu().inspirationAvailable();
        // ClientTick END may follow the first S2C snapshot but precede render(),
        // which refreshes the cached aid-button visibility. Await that visible state
        // rather than forcing controls to update or changing the production screen.
        if (aids != 13 || icons != 13 || inspiration != LATE_FIXTURE_INSPIRATION) {
            idleReadyTicks = 0;
            require(System.nanoTime() - idleReadinessBegan < IDLE_READINESS_TIMEOUT,
                    "Idle controls did not become ready: expected aids=13, icons=13, inspiration="
                            + LATE_FIXTURE_INSPIRATION + "; " + clientState(Minecraft.getInstance()));
            return false;
        }
        // Settle after readiness, not after world/menu opening, so screenshots at
        // both GUI scales capture a rendered desk with the authoritative state.
        if (++idleReadyTicks < 20) return false;
        require(aids == 13 && icons == 13 && inspiration == LATE_FIXTURE_INSPIRATION,
                "Idle aid icons or inspiration changed before capture: " + clientState(Minecraft.getInstance()));
        return true;
    }

    private static CompoundTag card(String id, boolean aid) {
        CompoundTag card = new CompoundTag(); card.putString("Id", id); card.putLong("Seed", 29); card.putBoolean("FromAid", aid);
        if (id.equals("celestial")) { card.putString("Category", "BASICS"); card.putInt("md1", 0); card.putInt("md2", 5); }
        if (id.equals("study")) card.putString("Category", "BASICS"); return card;
    }
    private static CompoundTag session(UUID owner, int inspiration, String id) {
        CompoundTag session = new CompoundTag(); session.putInt("Version", 1); session.putUUID("Owner", owner);
        session.putInt("InspirationStart", LATE_FIXTURE_INSPIRATION); session.putInt("Inspiration", inspiration); session.putLong("RandomSeed", 726);
        ListTag aids = new ListTag(); aids.add(StringTag.valueOf(TheoryAids.ENCHANTMENT_TABLE)); session.put("Aids", aids);
        session.put("AidCards", new ListTag()); session.put("Blocked", new ListTag());
        CompoundTag totals = new CompoundTag(); totals.putInt("BASICS", 100); session.put("Totals", totals);
        ListTag choices = new ListTag(); if (id != null) choices.add(card(id, id.equals("enchantment") || id.equals("study")));
        session.put("Choices", choices); return session;
    }
    private static void prepareScene(Minecraft mc) {
        var player = player(mc); var table = table(mc); player.getInventory().clearContent();
        var tools = new ItemStack(TheoryModule.SCRIBING_TOOLS.get()); tools.setDamageValue(scene == 8 ? tools.getMaxDamage() : scene == 19 || scene == 20 ? 99 : 0);
        table.setItem(0, tools); table.setItem(1, scene == 9 ? ItemStack.EMPTY : new ItemStack(Items.PAPER, scene == 19 || scene == 20 ? 1 : 16));
        player.experienceLevel = scene == 4 ? 4 : scene == 5 ? 5 : scene == 17 || scene == 18 ? 20 : 0; player.totalExperience = 0; player.experienceProgress = 0;
        player.connection.send(new ClientboundSetExperiencePacket(0, 0, player.experienceLevel));
        if (scene == 6 || scene == 7) { player.getInventory().setItem(10, CelestialModule.note(0)); player.getInventory().setItem(11, CelestialModule.note(scene == 6 ? 6 : 5)); }
        UUID owner = scene == 10 ? UUID.fromString("e3b95f50-f356-4fb0-b872-575fd04b2dc9") : player.getUUID();
        String id = scene == 4 || scene == 5 ? "enchantment" : scene == 6 || scene == 7 ? "celestial" : scene == 9 || scene == 12 || scene >= 13 ? null : "study";
        var session = session(owner, scene == 7 ? 1 : scene == 12 ? 0 : 3, id);
        if (scene >= 13 && scene <= 20) {
            String cardId = scene <= 14 ? "synthesis" : scene <= 16 ? "infuse" : scene <= 18 ? "dark_whispers" : "scripting";
            extraCard = TheoryCard.initialize(cardId, 29, cardId.equals("dark_whispers"), TheorySession.load(session), KnowledgeStore.get(player), player.getInventory(), player.experienceLevel);
            require(extraCard != null && TheoryCard.load(extraCard.save()) != null, "New card fixture failed initialization: " + cardId);
            ListTag offers = new ListTag(); offers.add(extraCard.save()); session.put("Choices", offers);
            if (cardId.equals("dark_whispers")) { ListTag aids = new ListTag(); aids.add(StringTag.valueOf(TheoryAids.BRAIN_IN_A_JAR)); session.put("Aids", aids); }
            var requirements = extraCard.requiredItems();
            for (int i = 0; i < requirements.size(); i++) player.getInventory().setItem(10 + i, requirements.get(i).stack());
            if (scene == 13) { Aspect wrong = extraCard.aspect("aspect1") == Aspect.ORDER ? Aspect.AIR : Aspect.ORDER; player.getInventory().setItem(10, AspectCrystalItem.create(wrong)); }
            if (scene == 15) player.getInventory().setItem(11, CatalogModule.aspectStack("phial_filled", Aspect.AIR, 10));
            priorNormalWarp = KnowledgeStore.get(player).normalWarp();
        }
        if (scene == 12) { session.getCompound("Totals").putInt("ALCHEMY", 60); session.getCompound("Totals").putInt("AUROMANCY", 45); }
        var tag = table.saveWithoutMetadata(); tag.put("Session", session); tag.putLong("TheoryRevision", table.revision() + 1);
        table.load(tag); table.setChanged(); expectedRevision = table.revision(); unchanged = table.saveWithoutMetadata().toString();
        require(table.session() != null, "Seeded original offer was rejected at scene " + scene);
        if (scene == 12) {
            finishRewards = Map.copyOf(table.session().rewards());
            var balance = new java.util.LinkedHashMap<String, Integer>(); finishRewards.keySet().forEach(k -> balance.put(k, KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, k)));
            finishBalances = Map.copyOf(balance);
        }
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges(); player.inventoryMenu.broadcastChanges();
        // Fixture-only setup-status reset: clear the preceding case's message using
        // a real server-generated S2C snapshot, without fabricating payment or rewards.
        var preparedSnapshot = table.clientSnapshot(player, null);
        preparedSnapshot.putString("Result", "");
        TheoryNetwork.send(player, player.containerMenu.containerId, preparedSnapshot);
    }
    private static void guardedScene(Minecraft mc, ResearchTableScreen screen) throws Exception {
        if (screen.animationBusyForSmokeTest()) return;
        TheoryNetwork.Action action = scene == 9 ? TheoryNetwork.Action.DRAW : TheoryNetwork.Action.SELECT;
        int index = scene == 9 ? -1 : 0;
        String result = scene == 8 || scene == 9 ? "MISSING_RESOURCES" : scene == 10 ? "LOCKED" : scene == 11 ? "STALE" : "INVALID";
        if (phase == 0) {
            if (scene != 11) require(!screen.availableForSmokeTest(action, index), "Missing resource/owner guard enabled an action at scene " + scene);
            received = null;
            TheoryNetwork.request(screen.getMenu().containerId, screen.getMenu().revision() - (scene == 11 ? 1 : 0), action, index, Set.of()); phase++; return;
        }
        if (received == null || !result.equals(received.getString("Result"))) return;
        if (phase == 1) { submit(mc, () -> require(unchanged.equals(table(mc).saveWithoutMetadata().toString()), "Rejected packet changed the table at scene " + scene)); phase++; return; }
        if (sceneTicks < 30) return; capture(mc); if (SAVED.get() == scene + 1) next();
    }
    private static void paidScene(Minecraft mc, ResearchTableScreen screen) throws Exception {
        if (phase == 0) {
            if (screen.animationBusyForSmokeTest()) return;
            require(screen.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Supplied original requirements did not enable select");
            paidResolutionBaseline = screen.resolvingAnimationsForSmokeTest();
            paidPlacedBaseline = screen.getMenu().session().placedCards();
            paidRequestRevision = screen.getMenu().revision(); paidRequestBegan = System.nanoTime();
            paidSelectedCard = screen.getMenu().session().choices().get(0).save();
            paidInventory = null; paidFrameReady = false;
            require(screen.clickActionForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Select did not send C2S"); phase++; return;
        }
        if (captured) { if (SAVED.get() == scene + 1 && !screen.animationBusyForSmokeTest()) next(); return; }
        require(System.nanoTime() - paidRequestBegan < IDLE_READINESS_TIMEOUT, "Current paid response/frame did not settle: " + clientState(mc));
        if (!currentPaidResponse(screen) || screen.resolvingAnimationsForSmokeTest() <= paidResolutionBaseline) return;
        require(screen.resolvingAnimationsForSmokeTest() == paidResolutionBaseline + 1, "SELECT produced an unexpected resolution count: " + clientState(mc));
        require(screen.animationBusyForSmokeTest(), "Current paid resolution ended before capture: " + clientState(mc));
        if (!paidVerified) { submit(mc, () -> verifyPaidPaymentAndSnapshot(mc)); paidVerified = true; return; }
        if (!paidFrameReady || !paidClientSynchronized(mc, screen)) return;
        require(screen.getMenu().getSlot(0).getItem().getDamageValue() == (scene == 20 ? 100 : 1), "Select paid wrong ink amount");
        if (scene >= 14) {
            if (scene == 18) require(screen.getMenu().playerExperienceLevel() == 0 && screen.getMenu().playerKnowledge().normalWarp() == priorNormalWarp + 4, "Dark Whispers did not sync its XP/normal Warp payment");
            if (scene == 20) require(screen.getMenu().getSlot(1).getItem().isEmpty() && screen.getMenu().session().totals().getOrDefault("GOLEMANCY", 0) == 25, "Scripting extra table callbacks or gain did not sync");
        }
        if (scene == 5) require(screen.getMenu().playerExperienceLevel() == 0, "Five XP levels were not spent before animation");
        if (scene == 7) {
            require(screen.getMenu().session().complete(), "Exact celestial selection did not exhaust inspiration");
            require(mc.player.getInventory().getItem(10).isEmpty() && mc.player.getInventory().getItem(11).isEmpty(), "Both exact notes were not consumed before animation");
        }
        capture(mc); if (SAVED.get() == scene + 1 && !screen.animationBusyForSmokeTest()) next();
    }

    private static boolean currentPaidResponse(ResearchTableScreen screen) {
        var menu = screen.getMenu(); var session = menu.session();
        return !screen.pendingForSmokeTest() && "ACCEPTED".equals(menu.status())
                && menu.revision() == paidRequestRevision + 1 && session != null && session.lastCard() != null
                && session.placedCards() == paidPlacedBaseline + 1 && session.lastCard().save().equals(paidSelectedCard);
    }

    private static void verifyPaidPaymentAndSnapshot(Minecraft mc) {
        var player = player(mc); var table = table(mc);
        require(table.revision() == paidRequestRevision + 1 && table.session() != null
                        && table.session().lastCard() != null && table.session().lastCard().save().equals(paidSelectedCard),
                "Authoritative SELECT did not resolve the current paid card");
        require(table.getItem(0).getDamageValue() == (scene == 20 ? 100 : 1), "Authoritative SELECT paid wrong ink amount");
        if (scene == 5) require(player.experienceLevel == 0, "Authoritative Enchantment did not spend five XP levels");
        if (scene == 7) {
            require(table.session().complete(), "Authoritative celestial selection did not exhaust inspiration");
            require(player.getInventory().getItem(10).isEmpty() && player.getInventory().getItem(11).isEmpty(),
                    "Authoritative celestial selection did not consume both exact notes");
        }
        if (scene >= 14) verifyNewCardPayment(mc);
        // Read the already verified server state; never assign client resources or XP.
        paidInk = table.getItem(0).copy(); paidPaper = table.getItem(1).copy();
        paidLevel = player.experienceLevel; paidTotalExperience = player.totalExperience; paidExperienceProgress = player.experienceProgress;
        paidInventory = player.getInventory().items.stream().map(ItemStack::copy).toList();
    }

    private static boolean paidClientSynchronized(Minecraft mc, ResearchTableScreen screen) {
        List<ItemStack> expected = paidInventory;
        if (expected == null || mc.player == null || !currentPaidResponse(screen)) return false;
        var menu = screen.getMenu();
        if (mc.player.experienceLevel != paidLevel || mc.player.totalExperience != paidTotalExperience
                || mc.player.experienceProgress != paidExperienceProgress || menu.playerExperienceLevel() != paidLevel
                || !ItemStack.matches(menu.getSlot(0).getItem(), paidInk) || !ItemStack.matches(menu.getSlot(1).getItem(), paidPaper)
                || mc.player.getInventory().items.size() != expected.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            if (!ItemStack.matches(mc.player.getInventory().getItem(index), expected.get(index))) return false;
            int menuIndex = ResearchTableMenu.PLAYER_START + (index >= 9 ? index - 9 : 27 + index);
            if (!ItemStack.matches(menu.getSlot(menuIndex).getItem(), expected.get(index))) return false;
        }
        return true;
    }
    private static void verifyNewCardPayment(Minecraft mc) {
        var table = table(mc); var player = player(mc);
        require(table.session().lastCard().save().equals(extraCard.save()), "S2C resolved a different initialized new card");
        if (scene == 14) {
            require(table.session().totals().getOrDefault("ALCHEMY", 0) == 40, "Synthesis did not grant 40 Alchemy");
            require(player.getInventory().getItem(10).isEmpty() && player.getInventory().getItem(11).isEmpty(), "Synthesis did not consume both component crystals");
            require(player.getInventory().items.stream().anyMatch(stack -> AspectCrystalItem.crystalAspect(stack) == extraCard.aspect("aspect3") && stack.getCount() == 1), "Synthesis did not deliver the actual compound crystal");
        }
        if (scene == 16) {
            require(player.getInventory().getItem(10).isEmpty() && player.getInventory().getItem(11).isEmpty(), "Infuse did not consume ingredient and exact NBT phial");
            require(table.session().totals().getOrDefault("INFUSION", 0) == extraCard.value(), "Infuse delivered wrong parameter value");
        }
        if (scene == 18) require(player.experienceLevel == 0 && player.totalExperience == 0 && player.experienceProgress == 0
                && KnowledgeStore.get(player).normalWarp() == priorNormalWarp + 4, "Dark Whispers did not reset XP and apply normal Warp");
        if (scene == 20) require(table.getItem(0).getDamageValue() == 100 && table.getItem(1).isEmpty(), "Scripting did not clamp the two ink callbacks and consume extra paper");
    }
    private static void offerScene(Minecraft mc, ResearchTableScreen screen) throws Exception {
        if (screen.animationBusyForSmokeTest()) return;
        require(screen.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "New full-deck offer unavailable");
        if (phase == 0) { screen.pointerForSmokeTest(screen.cardPointForSmokeTest(0)); phase++; sceneTicks = 0; return; }
        if (sceneTicks < 15) return; capture(mc); if (SAVED.get() == scene + 1) { screen.pointerForSmokeTest(null); next(); }
    }
    private static void completionScene(Minecraft mc, ResearchTableScreen screen) throws Exception {
        if (phase == 0) {
            require(screen.getMenu().session().complete() && screen.availableForSmokeTest(TheoryNetwork.Action.FINISH, -1), "Completed session did not enable Finish");
            if (sceneTicks < 20) return; capture(mc); if (SAVED.get() != scene + 1) return;
            require(screen.clickActionForSmokeTest(TheoryNetwork.Action.FINISH, -1), "Finish did not send C2S"); phase++; return;
        }
        if (screen.pendingForSmokeTest() || screen.getMenu().session() != null) return;
        if (phase == 1) {
            submit(mc, () -> { require(table(mc).session() == null, "Finish did not clear authoritative session"); finishRewards.forEach((key, amount) -> require(KnowledgeStore.get(player(mc)).rawKnowledge(KnowledgeType.THEORY, key) == finishBalances.get(key) + amount, "Finish paid wrong original reward: " + key)); });
            phase++; return;
        }
        next();
    }
    private static void blockScene(Minecraft mc) throws Exception {
        if (!prepared) { submit(mc, () -> prepareScene(mc)); prepared = true; return; }
        if (phase == 0) { if (mc.screen instanceof ResearchTableScreen screen) screen.onClose(); else mc.setScreen(null); phase++; sceneTicks = 0; return; }
        mc.player.setYRot(180); mc.player.setXRot(20);
        if (!(mc.level.getBlockEntity(TABLE) instanceof ResearchTableBlockEntity table)
                || table.revision() != expectedRevision || table.session() == null
                || !table.getItem(0).is(TheoryModule.SCRIBING_TOOLS.get()) || table.getItem(0).getDamageValue() != 0) {
            blockSyncedTicks = 0; return;
        }
        require(mc.getBlockEntityRenderDispatcher().getRenderer(table) instanceof thaumcraft.research.theory.client.ResearchTableRenderer, "Original table renderer is not installed");
        if (++blockSyncedTicks < 40) return;
        if (!renderAudited) {
            String audit = thaumcraft.research.theory.client.ResearchTableRenderAudit.verify(table);
            renderAudited = true;
            LogUtils.getLogger().info("THAUMCRAFT_THEORY_TABLE_RENDER_AUDIT_OK: {}; actual synced table={}, revision={}", audit, TABLE, table.revision());
        }
        capture(mc); if (SAVED.get() == scene + 1) next();
    }
    private static void capture(Minecraft mc) throws Exception {
        if (captured) return; captured = true; String name = "tc6-theory-complete-" + IMAGES[scene] + ".png";
        var file = new File(new File(mc.gameDirectory, "screenshots"), name); Files.deleteIfExists(file.toPath());
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
            if (file.isFile() && file.length() > 0) { SAVED.incrementAndGet(); LogUtils.getLogger().info("THAUMCRAFT_THEORY_COMPLETE_SMOKE_IMAGE: {}", file.getAbsolutePath()); }
            else mc.execute(() -> fail(mc, new AssertionError("Screenshot absent: " + name)));
        });
    }
    private static void next() { scene++; phase = sceneTicks = idleReadyTicks = 0; idleReadinessBegan = 0; prepared = captured = paidVerified = paidFrameReady = false; paidInventory = null; }
    private static void submit(Minecraft mc, Runnable task) {
        require(work == null, "Overlapping table server tasks"); work = new CompletableFuture<>(); var result = work;
        mc.getSingleplayerServer().execute(() -> { try { task.run(); result.complete(null); } catch (Throwable error) { result.completeExceptionally(error); } });
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String clientState(Minecraft mc) {
        String blockState = mc.level != null && mc.level.getBlockEntity(TABLE) instanceof ResearchTableBlockEntity table
                ? ", clientTableRevision=" + table.revision() + ", clientTableActive=" + (table.session() != null)
                + ", clientTableInk=" + table.getItem(0) + ", blockSyncedTicks=" + blockSyncedTicks
                : ", clientTable=absent";
        if (!(mc.screen instanceof ResearchTableScreen screen)) return "screen=" + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()) + blockState;
        var menu = screen.getMenu(); var session = menu.session();
        return "menu=" + menu.containerId + ", revision=" + menu.revision() + ", expectedRevision=" + expectedRevision
                + ", aids=" + menu.availableAids() + ", aidCount=" + menu.availableAids().size()
                + ", visibleIcons=" + screen.aidIconsForSmokeTest() + ", inspirationAvailable=" + menu.inspirationAvailable()
                + ", selectedAids=" + screen.selectedAidsForSmokeTest()
                + ", canUse=" + menu.canUse() + ", pending=" + screen.pendingForSmokeTest()
                + ", status=" + menu.status() + ", resolving=" + screen.resolvingAnimationsForSmokeTest() + ", paidResolutionBaseline=" + paidResolutionBaseline
                + ", paidRequestRevision=" + paidRequestRevision + ", paidFrameReady=" + paidFrameReady
                + ", vanillaXP=" + (mc.player == null ? "absent" : mc.player.experienceLevel + "/" + mc.player.totalExperience + "/" + mc.player.experienceProgress)
                + ", expectedPaidXP=" + paidLevel + "/" + paidTotalExperience + "/" + paidExperienceProgress
                + ", ink=" + menu.getSlot(0).getItem() + ", paper=" + menu.getSlot(1).getItem()
                + ", session=" + (session == null ? "null" : "start=" + session.inspirationStart()
                + "/remaining=" + session.inspiration() + "/aids=" + session.aids()
                + "/offers=" + session.choices().size() + "/placed=" + session.placedCards()) + blockState;
    }
    private static void fail(Minecraft mc, Throwable failure) {
        if (stopped) return; stopped = true; if (previousTutorial != null) mc.options.tutorialStep = previousTutorial;
        LogUtils.getLogger().error("THAUMCRAFT_THEORY_COMPLETE_CLIENT_SMOKE_FAILED: scene=" + scene + ", phase=" + phase
                + ", sceneTicks=" + sceneTicks + ", idleReadyTicks=" + idleReadyTicks + "; " + clientState(mc), failure); mc.stop();
    }
}
