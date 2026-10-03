package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ResearchBookKnowledgeGameTests {
    private static void complete(PlayerKnowledge knowledge, String key) {
        ResearchEntry entry = ResearchCatalog.get(key);
        knowledge.setResearchStage(key, entry.stages().size() + 1);
    }

    @GameTest(template = "empty")
    public static void readAcknowledgmentChangesOnlyBookmarksAndPersistsPerPlayer(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore();
        UUID id = new UUID(414L, 1L);
        PlayerKnowledge knowledge = store.get(id);
        complete(knowledge, "FIRSTSTEPS");
        store.recordScan(id, "test:book_read_scan", new AspectList().add(Aspect.AIR, 8));
        store.recordCraft(id, "thaumcraft:thaumometer");
        int stage = knowledge.researchStage("FIRSTSTEPS");
        CompoundTag before = knowledge.save();
        store.setDirty(false);
        helper.assertTrue(knowledge.hasUnreadResearch("FIRSTSTEPS"), "Completed entry has no original new-research marker");
        helper.assertTrue(store.recordBookRead(id, "FIRSTSTEPS", stage, 0) && store.isDirty(), "Read acknowledgment did not persist");
        CompoundTag after = knowledge.save(); before.remove("BookRead"); after.remove("BookRead");
        helper.assertTrue(before.equals(after), "Viewing awarded or consumed research, scans, aspects, craft proof, knowledge or warp");
        helper.assertTrue(!knowledge.hasUnreadResearch("FIRSTSTEPS"), "Acknowledged marker remained unread");
        store.setDirty(false);
        helper.assertTrue(!store.recordBookRead(id, "FIRSTSTEPS", stage, 0) && !store.isDirty(), "Duplicate read dirtied or changed the save");
        KnowledgeStore loaded = KnowledgeStore.load(store.save(new CompoundTag()));
        helper.assertTrue(!loaded.get(id).hasUnreadResearch("FIRSTSTEPS") && loaded.get(id).readResearchStage("FIRSTSTEPS") == stage,
                "Read state was lost after a SavedData round trip");
        helper.assertTrue(loaded.get(new UUID(414L, 2L)).readResearchStage("FIRSTSTEPS") == 0, "Bookmarks leaked to another player");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryStageProgressDoesNotPretendToBeCompletedResearch(GameTestHelper helper) {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.setResearchStage("FIRSTSTEPS", 1);
        helper.assertTrue(!knowledge.hasUnreadResearch("FIRSTSTEPS"), "Starting ordinary research emitted a completion marker");
        helper.assertTrue(knowledge.recordBookRead("FIRSTSTEPS", 1, 0), "Known unfinished page could not be marked read");
        knowledge.setResearchStage("FIRSTSTEPS", 2);
        helper.assertTrue(!knowledge.hasUnreadResearch("FIRSTSTEPS"), "An intermediate stage emitted a completion marker");
        complete(knowledge, "FIRSTSTEPS");
        helper.assertTrue(knowledge.hasUnreadResearch("FIRSTSTEPS"), "Earlier page acknowledgment swallowed the later completion");
        CompoundTag before = knowledge.save();
        helper.assertTrue(!knowledge.recordBookRead("FIRSTSTEPS", 2, 0) && before.equals(knowledge.save()), "Stale stage acknowledgment consumed a newer discovery");
        helper.assertTrue(!knowledge.recordBookRead("UNKNOWN_ENTRY", 1, 0)
                && !knowledge.recordBookRead("UNLOCKALCHEMY", 1, 0)
                && !knowledge.recordBookRead("PORT_START", 0, 0), "A view created unknown or legacy progression");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void addendaRequireCompletedBaseAndStrictPrerequisites(GameTestHelper helper) {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        ResearchEntry smelter = ResearchCatalog.get("ESSENTIASMELTER");
        complete(knowledge, "BELLOWS");
        helper.assertTrue(ResearchBookState.visibleAddenda(knowledge, smelter).isEmpty(), "Addendum bypassed unfinished base research");
        complete(knowledge, "ESSENTIASMELTER");
        helper.assertTrue(ResearchBookState.availableAddendaMask(knowledge, smelter) == 1
                && ResearchBookState.visibleAddenda(knowledge, smelter).size() == 1, "Strictly unlocked original bellows addendum disappeared");
        helper.assertTrue(knowledge.hasUnreadPage("ESSENTIASMELTER"), "New addendum did not mark its completed base page");
        helper.assertTrue(knowledge.recordBookRead("ESSENTIASMELTER", knowledge.researchStage("ESSENTIASMELTER"), 1), "Available addendum was not acknowledged");
        helper.assertTrue(!knowledge.hasUnreadPage("ESSENTIASMELTER"), "Read addendum stayed new");
        PlayerKnowledge legacy = new PlayerKnowledge();
        complete(legacy, "ESSENTIASMELTER");
        legacy.discover("PORT_ALCHEMY"); legacy.discover("PORT_NITOR");
        helper.assertTrue(ResearchBookState.visibleAddenda(legacy, smelter).isEmpty(), "Recipe alias granted a canonical addendum");
        CompoundTag before = legacy.save();
        helper.assertTrue(!legacy.recordBookRead("ESSENTIASMELTER", legacy.researchStage("ESSENTIASMELTER"), 1)
                && before.equals(legacy.save()), "Client acknowledged an unavailable addendum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void readMaskDoesNotClearAnAddendumUnlockedAfterTheView(GameTestHelper helper) {
        KnowledgeStore store = new KnowledgeStore(); UUID id = new UUID(414L, 3L);
        PlayerKnowledge knowledge = store.get(id); ResearchEntry ore = ResearchCatalog.get("ORE");
        complete(knowledge, "ORE"); knowledge.discover("!OREAMBER");
        int viewed = ResearchBookState.availableAddendaMask(knowledge, ore);
        helper.assertTrue(viewed == 1, "Original ore addenda indices changed");
        knowledge.discover("!ORECINNABAR");
        helper.assertTrue(store.recordBookRead(id, "ORE", knowledge.researchStage("ORE"), viewed), "Valid old visible mask rejected");
        helper.assertTrue(knowledge.hasUnreadPage("ORE") && knowledge.readAddendaMask("ORE") == 1,
                "Reading the amber page silently consumed the newly discovered cinnabar page");
        CompoundTag before = knowledge.save(); store.setDirty(false);
        helper.assertTrue(!store.recordBookRead(id, "ORE", knowledge.researchStage("ORE"), 7)
                && before.equals(knowledge.save()) && !store.isDirty(), "Unavailable crystal page caused partial read payment");
        helper.assertTrue(store.recordBookRead(id, "ORE", knowledge.researchStage("ORE"), 3) && !knowledge.hasUnreadPage("ORE"), "Exact visible mask did not acknowledge both pages");
        helper.assertTrue(PlayerKnowledge.load(knowledge.save()).readAddendaMask("ORE") == 3, "Addendum bitmask changed across serialization");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyReadMigrationIsSilentAndKeepsLaterDiscoveriesNew(GameTestHelper helper) {
        PlayerKnowledge old = new PlayerKnowledge(); complete(old, "ORE"); old.discover("!OREAMBER");
        old.setResearchStage("FIRSTSTEPS", 2); old.discoverAspect(Aspect.AIR);
        old.addKnowledge(KnowledgeType.THEORY, "BASICS", 37);
        CompoundTag tag = old.save(); tag.remove("BookRead");
        PlayerKnowledge loaded = PlayerKnowledge.load(tag);
        helper.assertTrue(!loaded.hasUnreadResearch("ORE") && !loaded.hasUnreadPage("ORE")
                && loaded.readResearchStage("FIRSTSTEPS") == 2, "Migration marked the whole existing book unread");
        CompoundTag after = loaded.save(); after.remove("BookRead");
        helper.assertTrue(tag.equals(after), "Silent read migration changed any previous gameplay fact");
        complete(loaded, "FIRSTSTEPS"); loaded.discover("!ORECINNABAR");
        helper.assertTrue(loaded.hasUnreadResearch("FIRSTSTEPS") && loaded.hasUnreadPage("ORE"), "Migration swallowed future research or addenda");
        PlayerKnowledge again = PlayerKnowledge.load(loaded.save());
        helper.assertTrue(again.hasUnreadResearch("FIRSTSTEPS") && again.hasUnreadPage("ORE"), "New unread state disappeared after restart");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedBookDataCannotHideFutureResearchOrUnavailablePages(GameTestHelper helper) {
        PlayerKnowledge source = new PlayerKnowledge(); complete(source, "ORE"); source.discover("!OREAMBER");
        CompoundTag tag = source.save(); CompoundTag book = tag.getCompound("BookRead");
        CompoundTag seen = book.getCompound("Stages");
        seen.putInt("ORE", Integer.MAX_VALUE); seen.putInt("FIRSTSTEPS", 4); seen.putInt("UNKNOWN", 999);
        seen.putString("ESSENTIASMELTER", "99");
        CompoundTag masks = book.getCompound("Addenda"); masks.putInt("ORE", -1); masks.putInt("ESSENTIASMELTER", 1);
        PlayerKnowledge loaded = PlayerKnowledge.load(tag);
        helper.assertTrue(loaded.readResearchStage("ORE") == 0 && loaded.hasUnreadResearch("ORE")
                && loaded.readResearchStage("FIRSTSTEPS") == 0 && loaded.readResearchStage("UNKNOWN") == 0,
                "Forged future-stage read facts hid unread research");
        helper.assertTrue(loaded.readAddendaMask("ORE") == 1 && loaded.readAddendaMask("ESSENTIASMELTER") == 0,
                "Malformed NBT acknowledged unavailable addenda");
        loaded.discover("!ORECINNABAR");
        helper.assertTrue(loaded.hasUnreadPage("ORE"), "Negative old mask pre-acknowledged a future addendum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstSnapshotAndReadOnlyChangesDoNotCreateNotifications(GameTestHelper helper) {
        PlayerKnowledge before = new PlayerKnowledge(); complete(before, "ORE");
        helper.assertTrue(ResearchBookNotifications.between(null, before).isEmpty(), "First snapshot generated every old discovery as new");
        before.recordBookRead("ORE", before.researchStage("ORE"), 0);
        PlayerKnowledge after = PlayerKnowledge.load(before.save());
        after.addKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY", 20); after.discoverAspect(Aspect.MAGIC);
        helper.assertTrue(ResearchBookNotifications.between(before, after).isEmpty(), "Balance or aspect updates generated research toasts");
        after.setResearchStage("FIRSTSTEPS", 1);
        helper.assertTrue(ResearchBookNotifications.between(before, after).isEmpty(), "Ordinary unfinished stage generated a completion toast");
        complete(after, "FIRSTSTEPS"); after.discover("!OREAMBER");
        var notices = ResearchBookNotifications.between(before, after);
        helper.assertTrue(notices.size() == 2
                && notices.stream().anyMatch(n -> n.kind() == ResearchBookNotifications.Kind.RESEARCH && n.entry().key().equals("FIRSTSTEPS"))
                && notices.stream().anyMatch(n -> n.kind() == ResearchBookNotifications.Kind.PAGE && n.entry().key().equals("ORE")),
                "Completed research and additional pages did not produce their separate original notices");
        helper.assertTrue(ResearchBookNotifications.between(after, PlayerKnowledge.load(after.save())).isEmpty(), "Repeated authoritative snapshot repeated notifications");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void readPacketRequiresHeldBookAndCannotForgeProgress(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "book_read_test"));
        PlayerKnowledge knowledge = KnowledgeStore.get(player); complete(knowledge, "FIRSTSTEPS");
        int stage = knowledge.researchStage("FIRSTSTEPS"); CompoundTag before = knowledge.save();
        helper.assertTrue(!ResearchNetwork.processRead(player, "FIRSTSTEPS", stage, 0) && before.equals(knowledge.save()), "Read packet ignored the held-book context");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        helper.assertTrue(!ResearchNetwork.processRead(player, "FIRSTSTEPS", stage + 1, 0)
                && !ResearchNetwork.processRead(player, "FIRSTSTEPS", stage, Integer.MAX_VALUE)
                && !ResearchNetwork.processRead(player, "UNLOCKALCHEMY", 1, 0), "Client forged a future view or unavailable entry");
        helper.assertTrue(before.equals(knowledge.save()), "Rejected packet mutated player facts");
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        ResearchNetwork.Read.encode(new ResearchNetwork.Read("FIRSTSTEPS", stage, 0), bytes);
        ResearchNetwork.Read packet = ResearchNetwork.Read.decode(bytes);
        helper.assertTrue(packet.key().equals("FIRSTSTEPS") && packet.expectedStage() == stage && packet.addendumMask() == 0, "Book packet round trip changed its race guard");
        bytes.release();
        helper.assertTrue(ResearchNetwork.processRead(player, packet.key(), packet.expectedStage(), packet.addendumMask())
                && !knowledge.hasUnreadResearch("FIRSTSTEPS"), "Held offhand book could not acknowledge its visible page");
        helper.assertTrue(!ResearchNetwork.processRead(player, packet.key(), packet.expectedStage(), packet.addendumMask()), "Packet replay changed a read bookmark");
        helper.succeed();
    }
}
