package thaumcraft.client.theory;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.research.theory.ResearchTableMenu;
import thaumcraft.research.theory.TheoryModule;
import thaumcraft.research.theory.TheoryNetwork;
import thaumcraft.research.theory.TheorySession;
import thaumcraft.research.ResearchCategories;
import thaumcraft.research.celestial.CelestialModule;
import thaumcraft.research.celestial.CelestialVariant;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in Russian layout and request-state checks; never creates a world or sends packets. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class TheoryClientSmokeTest {
    private static final List<String> SCENES = List.of("idle", "two-cards", "three-cards", "complete", "no-ink", "no-paper", "pending",
            "notes-owned", "notes-missing", "xp-owned", "xp-missing", "aid-cards", "large-knowledge", "note-tooltip", "card-tooltip");
    private static final UUID OWNER = UUID.fromString("33dc0316-6d0a-4bee-a281-8a43be4ad901");
    private static final int EXPECTED = SCENES.size() * 3;
    private static final AtomicInteger saved = new AtomicInteger();
    private static int totalTicks, sceneTicks, sceneIndex, scaleIndex, requests;
    private static boolean languageRequested, started, stopped;
    private static ResearchTablePreviewScreen preview;
    private static Set<String> lastAids = Set.of();

    private TheoryClientSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.theorySmokeTest") || stopped) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (++totalTicks > 2200) { fail(minecraft, "Timed out waiting for theory screenshots", null); return; }
        try {
            if (!started) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (!languageRequested) {
                    languageRequested = true;
                    if (!minecraft.options.languageCode.equals("ru_ru")) {
                        minecraft.options.languageCode = "ru_ru";
                        minecraft.getLanguageManager().setSelected("ru_ru");
                        minecraft.reloadResourcePacks();
                        return;
                    }
                }
                require(minecraft.player == null && minecraft.level == null, "Theory preview unexpectedly has a world");
                require(minecraft.getWindow().getWidth() == 1600 && minecraft.getWindow().getHeight() == 1000,
                        "Theory smoke requires a 1600x1000 framebuffer");
                setScale(minecraft, 2);
                exerciseRequests(minecraft);
                openScene(minecraft);
                started = true;
            }
            if (++sceneTicks == 15) capture(minecraft, "thaumcraft-theory-" + SCENES.get(sceneIndex) + "-gui" + (2 + scaleIndex) + ".png");
            if (sceneTicks < 30) return;
            if (++sceneIndex == SCENES.size()) {
                sceneIndex = 0;
                scaleIndex++;
                if (scaleIndex == 3) {
                    if (saved.get() != EXPECTED) { sceneIndex = SCENES.size() - 1; scaleIndex = 2; sceneTicks = 29; return; }
                    stopped = true;
                    LogUtils.getLogger().info("THAUMCRAFT_THEORY_CLIENT_SMOKE_OK: {} Russian scenes saved; scale 2/3/4; 13 note variants, exact consumed notes, XP, three aids, large knowledge, tooltips, pending, revision, resource and ownership guards; {} local requests; no world or network", saved.get(), requests);
                    minecraft.stop();
                    return;
                }
                setScale(minecraft, 2 + scaleIndex);
            }
            openScene(minecraft);
            sceneTicks = 0;
        } catch (RuntimeException | AssertionError failure) { fail(minecraft, "Theory visual smoke failed at scene " + sceneIndex + ", scale " + (2 + scaleIndex), failure); }
    }

    private static void openScene(Minecraft minecraft) {
        preview = createPreview(SCENES.get(sceneIndex));
        minecraft.setScreen(preview);
        require(preview.contents.layoutFitsForSmokeTest(), "Table and category panel do not fit the scaled display");
        require(preview.contents.slotHitboxesForSmokeTest(), "Scale conversion lost an inventory slot hitbox");
        if (SCENES.get(sceneIndex).equals("idle")) {
            require(preview.contents.clickAidForSmokeTest(TheorySession.BOOKSHELF), "Scaled bookshelf selector was not clickable");
            require(preview.contents.clickAidForSmokeTest(TheorySession.ENCHANTMENT_TABLE), "Scaled enchanting selector was not clickable");
            require(preview.contents.clickAidForSmokeTest(TheorySession.BEACON), "Scaled beacon selector was not clickable");
            require(preview.contents.selectedAidsForSmokeTest().size() == 3, "All three research aids were not selected");
            int before = requests;
            require(preview.contents.clickActionForSmokeTest(TheoryNetwork.Action.START, -1), "Scaled start button did not handle a mouse click");
            require(requests == before + 1 && preview.contents.pendingForSmokeTest(), "Scaled button click did not submit exactly once");
            require(lastAids.equals(Set.of(TheorySession.BOOKSHELF, TheorySession.ENCHANTMENT_TABLE, TheorySession.BEACON)), "Start lost an aid selection");
            CompoundTag ack = state("idle", 42);
            ack.putString("Result", "ACCEPTED");
            preview.contents.applyForSmokeTest(ack);
        }
        if (SCENES.get(sceneIndex).equals("pending")) {
            preview.contents.actionForSmokeTest(TheoryNetwork.Action.SELECT, 0);
            require(preview.contents.pendingForSmokeTest(), "Preview request did not enter pending state");
        }
        if (SCENES.get(sceneIndex).equals("note-tooltip")) preview.hoverForSmokeTest(true);
        if (SCENES.get(sceneIndex).equals("card-tooltip")) preview.hoverForSmokeTest(false);
        if (List.of("notes-owned", "xp-owned", "aid-cards").contains(SCENES.get(sceneIndex))) {
            int before = requests;
            require(preview.contents.clickActionForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Scaled requirement card did not handle a mouse click");
            require(requests == before + 1, "Scaled requirement card did not submit exactly once");
            CompoundTag ack = state(SCENES.get(sceneIndex), 42); ack.putString("Result", "ACCEPTED");
            preview.contents.applyForSmokeTest(ack);
        }
    }

    private static ResearchTablePreviewScreen createPreview(String scene) {
        Inventory inventory = new Inventory(null);
        inventory.setItem(0, new ItemStack(TheoryModule.WOOD_TABLE_ITEM.get()));
        inventory.setItem(1, new ItemStack(Items.INK_SAC, 4));
        inventory.setItem(2, new ItemStack(Items.FEATHER, 8));
        inventory.setItem(9, new ItemStack(Items.PAPER, 32));
        inventory.setItem(10, new ItemStack(Items.GLASS_BOTTLE, 3));
        if (List.of("notes-owned", "aid-cards", "note-tooltip", "card-tooltip").contains(scene)) {
            inventory.setItem(11, CelestialModule.note(0));
            inventory.setItem(12, CelestialModule.note(5));
        } else if (scene.equals("notes-missing")) {
            inventory.setItem(11, CelestialModule.note(0));
            inventory.setItem(12, CelestialModule.note(6));
        }
        ResearchTableMenu menu = ResearchTableMenu.preview(71, inventory, state(scene, 41));
        ItemStack ink = new ItemStack(TheoryModule.SCRIBING_TOOLS.get());
        ink.setDamageValue(scene.equals("no-ink") ? ink.getMaxDamage() : 24);
        menu.getSlot(0).set(ink);
        menu.getSlot(1).set(scene.equals("no-paper") ? ItemStack.EMPTY : new ItemStack(Items.PAPER, 16));
        ResearchTableScreen screen = new ResearchTableScreen(menu, inventory, net.minecraft.network.chat.Component.translatable("container.thaumcraft.research_table"));
        screen.senderForSmokeTest((menuId, revision, action, index, aids) -> {
            require(menuId == 71 && revision == screen.getMenu().revision(), "Request lost the menu ID or expected revision");
            lastAids = Set.copyOf(aids);
            requests++;
        });
        return new ResearchTablePreviewScreen(screen);
    }

    private static void exerciseRequests(Minecraft minecraft) {
        ResearchTablePreviewScreen check = createPreview("idle");
        minecraft.setScreen(check);
        ResearchTableScreen screen = check.contents;
        int before = requests;
        require(screen.availableForSmokeTest(TheoryNetwork.Action.START, -1), "Supplied idle table did not enable start");
        screen.actionForSmokeTest(TheoryNetwork.Action.START, -1);
        screen.actionForSmokeTest(TheoryNetwork.Action.START, -1);
        require(requests == before + 1 && screen.pendingForSmokeTest(), "A duplicate start request was sent");
        screen.applyForSmokeTest(state("idle", 42));
        require(screen.pendingForSmokeTest(), "An ordinary snapshot cleared pending before acknowledgement");
        screen.actionForSmokeTest(TheoryNetwork.Action.START, -1);
        require(requests == before + 1, "A snapshot permitted a second pending request");
        CompoundTag acknowledgement = state("idle", 43);
        acknowledgement.putString("Result", "STALE");
        screen.applyForSmokeTest(acknowledgement);
        require(!screen.pendingForSmokeTest(), "Explicit stale acknowledgement did not clear pending");
        CompoundTag locked = state("idle", 44);
        locked.putBoolean("CanUse", false);
        screen.applyForSmokeTest(locked);
        for (TheoryNetwork.Action action : TheoryNetwork.Action.values()) {
            require(!screen.availableForSmokeTest(action, 0), "A spectator or wrong owner could submit " + action);
            screen.actionForSmokeTest(action, 0);
        }
        require(requests == before + 1, "Locked table sent a request");

        check = createPreview("two-cards");
        minecraft.setScreen(check);
        screen = check.contents;
        require(!screen.availableForSmokeTest(TheoryNetwork.Action.DRAW, -1), "Existing choices permitted another draw");
        require(!screen.availableForSmokeTest(TheoryNetwork.Action.SELECT, 3), "An invalid card index was enabled");
        screen.actionForSmokeTest(TheoryNetwork.Action.SELECT, 1);
        screen.actionForSmokeTest(TheoryNetwork.Action.SELECT, 1);
        require(requests == before + 2, "A card click was sent more than once");
        acknowledgement = state("no-paper", 45);
        acknowledgement.putString("Result", "ACCEPTED");
        screen.applyForSmokeTest(acknowledgement);
        require(!screen.pendingForSmokeTest(), "Card acknowledgement did not clear pending");

        check = createPreview("no-ink"); minecraft.setScreen(check);
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Exhausted ink allowed selection");
        check = createPreview("no-paper"); minecraft.setScreen(check);
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.DRAW, -1), "Missing paper allowed drawing");
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.DRAW_BONUS, -1), "Missing paper allowed a bonus draw");
        check = createPreview("complete"); minecraft.setScreen(check);
        require(check.contents.availableForSmokeTest(TheoryNetwork.Action.FINISH, -1), "Finished theory did not enable payment");
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.DRAW, -1), "Completed theory allowed another draw");
        exerciseCostsAndAids(minecraft);
        LogUtils.getLogger().info("THAUMCRAFT_THEORY_CLIENT_SMOKE_REQUESTS_OK: duplicate clicks, explicit acknowledgement, expected revision, exhausted ink, missing paper, ownership, completion, exact notes, XP, three research aids");
    }

    private static void exerciseCostsAndAids(Minecraft minecraft) {
        ResearchTablePreviewScreen check = createPreview("notes-missing"); minecraft.setScreen(check);
        require(check.contents.getMenu().session().choices().get(0).requiredItems().size() == 2, "Celestial offer lost its exact requirements");
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Wrong lunar phase satisfied a celestial card");
        check.contents.getMenu().playerInventory().offhand.set(0, CelestialModule.note(5));
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Offhand note satisfied main-inventory payment");
        ItemStack namedNote = CelestialModule.note(5);
        namedNote.setHoverName(net.minecraft.network.chat.Component.literal("Named exact note"));
        check.contents.getMenu().playerInventory().setItem(12, namedNote);
        require(check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Exact note with extra NBT did not satisfy the BETA26 payment filter");
        int before = requests;
        check.contents.actionForSmokeTest(TheoryNetwork.Action.SELECT, 0);
        check.contents.actionForSmokeTest(TheoryNetwork.Action.SELECT, 0);
        require(requests == before + 1, "Celestial card submitted a duplicate request");

        check = createPreview("xp-missing"); minecraft.setScreen(check);
        require(check.contents.getMenu().playerExperienceLevel() == 4, "Prepared XP state did not arrive");
        require(!check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Four XP levels paid a five-level enchantment card");
        CompoundTag enough = state("xp-owned", 50); check.contents.applyForSmokeTest(enough);
        require(check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 0), "Five XP levels did not enable enchantment");
        require(check.contents.availableForSmokeTest(TheoryNetwork.Action.SELECT, 1), "Negative-cost beacon offer was disabled");

        check = createPreview("idle"); minecraft.setScreen(check);
        for (String aid : List.of(TheorySession.BOOKSHELF, TheorySession.ENCHANTMENT_TABLE, TheorySession.BEACON)) require(check.contents.clickAidForSmokeTest(aid), "Aid selector did not respond");
        require(check.contents.selectedAidsForSmokeTest().size() == 3, "Aid selectors lost a selection");
        require(check.contents.clickAidForSmokeTest(TheorySession.BEACON), "Aid selector did not toggle off");
        require(check.contents.selectedAidsForSmokeTest().size() == 2, "Repeated aid click duplicated its cost");
        require(check.contents.clickAidForSmokeTest(TheorySession.BEACON), "Aid selector did not toggle on");
        check.contents.actionForSmokeTest(TheoryNetwork.Action.START, -1);
        require(lastAids.size() == 3, "Start request lost the selected aids");
        for (String aid : lastAids) require(!check.contents.clickAidForSmokeTest(aid), "Pending request permitted aid changes");

        check = createPreview("large-knowledge"); minecraft.setScreen(check);
        var largeKnowledge = check.contents.getMenu().playerKnowledge();
        long total = ResearchCategories.keys().stream().mapToLong(key -> largeKnowledge.rawKnowledge(thaumcraft.research.KnowledgeType.THEORY, key)).sum();
        require(total == 7L * Integer.MAX_VALUE, "Large raw theory balance overflowed");
        Set<net.minecraft.world.item.Item> notes = new java.util.HashSet<>();
        for (CelestialVariant variant : CelestialVariant.values()) {
            ItemStack stack = CelestialModule.note(variant.metadata());
            require(!stack.isEmpty() && notes.add(stack.getItem()), "A celestial variant was empty or duplicated");
        }
        require(notes.size() == 13, "Celestial note catalogue lost a variant");
    }

    private static CompoundTag state(String scene, long revision) {
        CompoundTag state = new CompoundTag();
        state.putLong("Revision", revision);
        state.putBoolean("CanUse", true);
        state.putInt("InspirationAvailable", 7);
        state.putInt("ExperienceLevel", scene.equals("xp-missing") ? 4 : 5);
        ListTag aids = new ListTag();
        for (String aid : List.of(TheorySession.BOOKSHELF, TheorySession.ENCHANTMENT_TABLE, TheorySession.BEACON)) aids.add(StringTag.valueOf(aid));
        state.put("Aids", aids);
        CompoundTag knowledge = new CompoundTag();
        knowledge.putInt("Version", 2);
        CompoundTag research = new CompoundTag();
        research.putInt("FIRSTSTEPS", 4); research.putInt("UNLOCKALCHEMY", 4); research.putInt("UNLOCKAUROMANCY", 3);
        knowledge.put("ResearchStages", research);
        CompoundTag theory = new CompoundTag(); theory.putInt("BASICS", 23); theory.putInt("ALCHEMY", 15);
        if (scene.equals("large-knowledge")) for (String category : ResearchCategories.keys()) theory.putInt(category, Integer.MAX_VALUE);
        CompoundTag observation = new CompoundTag(); observation.putInt("BASICS", 23); observation.putInt("ALCHEMY", 32);
        CompoundTag balances = new CompoundTag(); balances.put("THEORY", theory); balances.put("OBSERVATION", observation);
        knowledge.put("Knowledge", balances);
        state.put("Knowledge", knowledge);
        if (scene.equals("idle")) return state;
        CompoundTag session = new CompoundTag();
        session.putInt("Version", 1); session.putUUID("Owner", OWNER);
        session.putInt("Inspiration", scene.equals("complete") ? 0 : 4);
        session.putInt("InspirationStart", 7); session.putInt("BonusDraws", 1);
        session.putInt("PenaltyStart", 0); session.putInt("PlacedCards", 4); session.putLong("RandomSeed", 42);
        session.put("Aids", aids.copy());
        CompoundTag totals = new CompoundTag();
        totals.putInt("BASICS", scene.equals("complete") ? 142 : 34);
        totals.putInt("ALCHEMY", scene.equals("complete") ? 89 : 72);
        totals.putInt("AUROMANCY", scene.equals("complete") ? 61 : 18);
        if (scene.equals("large-knowledge")) for (String category : ResearchCategories.keys()) totals.putInt(category, 200 + ResearchCategories.keys().indexOf(category));
        session.put("Totals", totals);
        ListTag cards = new ListTag();
        if (List.of("two-cards", "no-ink", "pending").contains(scene)) {
            cards.add(card("experimentation", null, false, 0));
            cards.add(card("ponder", null, false, 0));
        } else if (scene.equals("three-cards")) {
            cards.add(card("study", "ALCHEMY", true, 0));
            CompoundTag notation = card("notation", null, true, 0);
            notation.putString("SourceCategory", "AUROMANCY"); notation.putString("TargetCategory", "ALCHEMY");
            cards.add(notation);
            cards.add(card("inspired", "ALCHEMY", false, 46));
        } else if (List.of("notes-owned", "notes-missing", "aid-cards", "note-tooltip", "card-tooltip").contains(scene)) {
            CompoundTag celestial = card("celestial", "ALCHEMY", false, 0);
            celestial.putInt("md1", 0); celestial.putInt("md2", 5);
            cards.add(celestial);
            if (scene.equals("aid-cards")) cards.add(card("enchantment", null, true, 0));
            cards.add(card("beacon", null, true, 0));
        } else if (List.of("xp-owned", "xp-missing").contains(scene)) {
            cards.add(card("enchantment", null, true, 0)); cards.add(card("beacon", null, true, 0));
        }
        session.put("Choices", cards);
        session.put("LastCard", card("study", "BASICS", true, 0));
        state.put("Session", session);
        return state;
    }

    private static CompoundTag card(String id, String category, boolean fromAid, int amount) {
        CompoundTag card = new CompoundTag();
        card.putString("Id", id); card.putLong("Seed", 29); card.putBoolean("FromAid", fromAid); card.putInt("Amount", amount);
        if (category != null) card.putString("Category", category);
        return card;
    }

    private static void setScale(Minecraft minecraft, int scale) {
        minecraft.options.guiScale().set(scale);
        minecraft.resizeDisplay();
        LogUtils.getLogger().info("THAUMCRAFT_THEORY_CLIENT_SMOKE_SCALE: requested={}, actual={}, framebuffer={}x{}, gui={}x{}", scale,
                minecraft.getWindow().getGuiScale(), minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(),
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static void capture(Minecraft minecraft, String filename) {
        require(minecraft.screen == preview, "The theory preview was replaced");
        File output = new File(new File(minecraft.gameDirectory, "screenshots"), filename);
        try { Files.deleteIfExists(output.toPath()); }
        catch (IOException failure) { fail(minecraft, "Cannot replace " + filename, failure); return; }
        Screenshot.grab(minecraft.gameDirectory, filename, minecraft.getMainRenderTarget(), message -> {
            if (output.isFile() && output.length() > 0) {
                LogUtils.getLogger().info("THAUMCRAFT_THEORY_CLIENT_SMOKE_IMAGE: {} ({}/{})", filename, saved.incrementAndGet(), EXPECTED);
            } else minecraft.execute(() -> fail(minecraft, "Screenshot not written: " + filename + "; " + message.getString(), null));
        });
    }

    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void fail(Minecraft minecraft, String message, Throwable failure) {
        if (stopped) return;
        stopped = true;
        LogUtils.getLogger().error("THAUMCRAFT_THEORY_CLIENT_SMOKE_FAILED: " + message, failure);
        minecraft.stop();
    }
}
