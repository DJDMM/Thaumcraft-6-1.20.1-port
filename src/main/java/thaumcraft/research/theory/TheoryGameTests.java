package thaumcraft.research.theory;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchCategories;
import thaumcraft.research.ResearchProgression;
import thaumcraft.scanning.ScanningModule;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TheoryGameTests {
    private static ServerPlayer player(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "theory_test"));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static InteractionResult convert(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static ItemStack markedTools(int damage) {
        ItemStack tools = new ItemStack(TheoryModule.SCRIBING_TOOLS.get());
        tools.setDamageValue(damage);
        tools.setHoverName(Component.literal("Persistent research tools"));
        tools.getOrCreateTag().putString("TheoryTestMarker", "keep");
        return tools;
    }

    /** Prepare an already-saved canonical session, rather than bypassing private runtime state. */
    private static CompoundTag sessionTag(ServerPlayer player, int inspiration, Map<String, Integer> totals) {
        CompoundTag saved = TheorySession.create(player.getUUID(), KnowledgeStore.get(player), Set.of(), RandomSource.create(41)).save();
        saved.putInt("Inspiration", inspiration);
        CompoundTag progress = new CompoundTag();
        totals.forEach(progress::putInt);
        saved.put("Totals", progress);
        return saved;
    }

    private static CompoundTag card(String id, String category) {
        CompoundTag saved = new CompoundTag();
        saved.putString("Id", id);
        saved.putLong("Seed", 12345L);
        saved.putBoolean("FromAid", false);
        saved.putInt("Amount", 0);
        if (category != null) saved.putString("Category", category);
        return saved;
    }

    private static ResearchTableBlockEntity table(GameTestHelper helper, BlockPos pos, int inkDamage, int paper) {
        helper.getLevel().setBlockAndUpdate(pos, TheoryModule.TABLE.get().defaultBlockState());
        ResearchTableBlockEntity table = (ResearchTableBlockEntity) helper.getLevel().getBlockEntity(pos);
        table.setItem(0, markedTools(inkDamage));
        table.setItem(1, paper > 0 ? new ItemStack(Items.PAPER, paper) : ItemStack.EMPTY);
        return table;
    }

    private static void result(GameTestHelper helper, TheoryResult actual, TheoryResult expected) {
        helper.assertTrue(actual == expected, "Expected theory result " + expected + ", got " + actual);
    }

    @GameTest(template = "empty")
    public static void sessionOwnerControlsEveryInventoryClickAndCanRefillAndResume(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer owner = player(helper, pos), other = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 99, 7);
        result(helper, table.start(owner, table.revision(), Set.of()), TheoryResult.ACCEPTED);
        CompoundTag before = table.saveWithoutMetadata();
        CompoundTag sessionBefore = table.session().save();
        ResearchTableMenu otherMenu = new ResearchTableMenu(21, other.getInventory(), table);
        other.containerMenu = otherMenu;
        ItemStack replacement = markedTools(1);
        other.getInventory().setItem(0, replacement.copy());
        other.getInventory().setItem(1, new ItemStack(Items.PAPER, 8));
        for (int slot = 0; slot < 2; slot++) {
            otherMenu.clicked(slot, 0, ClickType.PICKUP, other);
            helper.assertTrue(otherMenu.getCarried().isEmpty() && table.saveWithoutMetadata().equals(before),
                    "Another player removed an owned supply with normal pickup");
            helper.assertTrue(otherMenu.quickMoveStack(other, slot).isEmpty() && table.saveWithoutMetadata().equals(before),
                    "Another player shifted an owned supply into their inventory");
            otherMenu.clicked(slot, slot, ClickType.SWAP, other);
            helper.assertTrue(table.saveWithoutMetadata().equals(before)
                            && ItemStack.matches(other.getInventory().getItem(0), replacement)
                            && other.getInventory().getItem(1).getCount() == 8,
                    "Another player's number key replaced tools or paper in the owned table");
        }
        otherMenu.setCarried(replacement.copy());
        otherMenu.clicked(0, 0, ClickType.PICKUP, other);
        helper.assertTrue(ItemStack.matches(otherMenu.getCarried(), replacement) && table.saveWithoutMetadata().equals(before),
                "Another player replaced owned tools with a cursor stack");
        otherMenu.setCarried(new ItemStack(Items.PAPER, 64));
        otherMenu.clicked(1, 0, ClickType.PICKUP, other);
        helper.assertTrue(otherMenu.getCarried().getCount() == 64 && table.saveWithoutMetadata().equals(before),
                "Another player replaced or added to owned paper with a cursor stack");
        otherMenu.setCarried(new ItemStack(Items.PAPER));
        // Target a player inventory slot: parent PICKUP_ALL scans the entire menu, including table slots.
        otherMenu.clicked(2, 0, ClickType.PICKUP_ALL, other);
        helper.assertTrue(otherMenu.getCarried().getCount() == 1 && other.getInventory().getItem(1).getCount() == 8
                        && table.saveWithoutMetadata().equals(before), "Collect-all bypassed the session owner's table inventory lock");
        otherMenu.setCarried(ItemStack.EMPTY);

        ResearchTableMenu ownerMenu = new ResearchTableMenu(22, owner.getInventory(), table);
        owner.containerMenu = ownerMenu;
        ownerMenu.clicked(0, 0, ClickType.PICKUP, owner);
        helper.assertTrue(table.getItem(0).isEmpty() && ownerMenu.getCarried().is(TheoryModule.SCRIBING_TOOLS.get())
                        && ownerMenu.getCarried().getDamageValue() == 99, "Session owner could not retrieve tools for refilling");
        BlockPos craftingPos = pos.east();
        BlockState previousCraftingBlock = helper.getLevel().getBlockState(craftingPos);
        helper.getLevel().setBlockAndUpdate(craftingPos, Blocks.CRAFTING_TABLE.defaultBlockState());
        try {
            CraftingMenu crafting = new CraftingMenu(23, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), craftingPos));
            owner.containerMenu = crafting;
            crafting.setCarried(ownerMenu.getCarried()); ownerMenu.setCarried(ItemStack.EMPTY);
            crafting.clicked(1, 0, ClickType.PICKUP, owner);
            crafting.getSlot(2).set(new ItemStack(Items.BLACK_DYE));
            crafting.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(crafting.getCarried().is(TheoryModule.SCRIBING_TOOLS.get()) && crafting.getCarried().getDamageValue() == 0
                            && crafting.getCarried().getTag().getString("TheoryTestMarker").equals("keep"),
                    "Session owner's retrieved tools could not refill through the real crafting result");
            owner.containerMenu = ownerMenu;
            ownerMenu.setCarried(crafting.getCarried()); crafting.setCarried(ItemStack.EMPTY);
            ownerMenu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(ownerMenu.getCarried().isEmpty() && table.getItem(0).getDamageValue() == 0, "Session owner could not return refilled tools");
        } finally { helper.getLevel().setBlockAndUpdate(craftingPos, previousCraftingBlock); }
        helper.assertTrue(ownerMenu.quickMoveStack(owner, 1).is(Items.PAPER) && table.getItem(1).isEmpty(), "Session owner could not retrieve paper by shift-click");
        int paperSlot = -1;
        for (int slot = ResearchTableMenu.PLAYER_START; slot < ResearchTableMenu.PLAYER_END; slot++) {
            if (ownerMenu.getSlot(slot).getItem().is(Items.PAPER)) { paperSlot = slot; break; }
        }
        helper.assertTrue(paperSlot >= 0 && ownerMenu.quickMoveStack(owner, paperSlot).is(Items.PAPER)
                        && table.getItem(1).getCount() == 7 && table.revision() == 1 && table.session().save().equals(sessionBefore),
                "Owner supply retrieval/return reset the active theory or lost paper");
        result(helper, table.draw(owner, table.revision(), false), TheoryResult.ACCEPTED);
        result(helper, table.select(owner, table.revision(), 0), TheoryResult.ACCEPTED);
        helper.assertTrue(table.getItem(0).getDamageValue() == 1 && table.getItem(1).getCount() == 6,
                "Refilled owned theory could not resume with exact paper and ink payment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingActiveTheoryDropsTableAndRemainingSuppliesOnceWithoutRewards(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer owner = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 50, 8);
        result(helper, table.start(owner, table.revision(), Set.of()), TheoryResult.ACCEPTED);
        result(helper, table.draw(owner, table.revision(), false), TheoryResult.ACCEPTED);
        result(helper, table.select(owner, table.revision(), 0), TheoryResult.ACCEPTED);
        helper.assertTrue(!table.session().complete() && !table.session().totals().isEmpty(), "Break fixture has no live theory progress");
        helper.assertTrue(helper.getLevel().destroyBlock(pos, true) && helper.getLevel().getBlockState(pos).isAir()
                        && helper.getLevel().getBlockEntity(pos) == null, "Active research table did not break");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        int tables = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(TheoryModule.TABLE_ITEM.get())).mapToInt(ItemStack::getCount).sum();
        int tools = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(TheoryModule.SCRIBING_TOOLS.get())).mapToInt(ItemStack::getCount).sum();
        int paper = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.PAPER)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(tables == 1 && tools == 1 && paper == 7, "Breaking active theory duplicated or lost the table and remaining supplies");
        ItemStack droppedTools = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(TheoryModule.SCRIBING_TOOLS.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(droppedTools.getDamageValue() == 51 && droppedTools.getTag().getString("TheoryTestMarker").equals("keep"),
                "Breaking changed spent ink or stripped scribing tools NBT");
        helper.assertTrue(!helper.getLevel().destroyBlock(pos, true), "Breaking the same absent table succeeded twice");
        result(helper, table.finish(owner, table.revision()), TheoryResult.LOCKED);
        for (String category : ResearchCategories.keys()) {
            helper.assertTrue(KnowledgeStore.get(owner).rawKnowledge(KnowledgeType.THEORY, category) == 0,
                    "Breaking an unfinished theory delivered knowledge in " + category);
        }
        helper.getLevel().setBlockAndUpdate(pos, TheoryModule.TABLE.get().defaultBlockState());
        ResearchTableBlockEntity replacement = (ResearchTableBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(replacement.session() == null && replacement.isEmpty(), "Replacement table inherited a destroyed session or supplies");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream().map(ItemEntity::getItem).mapToInt(ItemStack::getCount).sum() == 9,
                "Repeated break/replacement produced an extra drop");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realBookshelfTheoryCompletesThroughLowInspirationAfterReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        // The full 33-card pool changes rejected draws and therefore later session RNG.
        // Seed 8 now spends 1, 1, 2 (4 -> 3 -> 2 -> 0), missing this fixture's one-point phase.
        // Seed 0 offers Study, then Inspired, then Study: a genuine 4 -> 3 -> 1 -> 0 lifecycle.
        player.getRandom().setSeed(0L);
        ResearchTableBlockEntity table = table(helper, pos, 0, 16);
        BlockPos bookshelf = pos.east();
        BlockState oldShelfState = helper.getLevel().getBlockState(bookshelf);
        helper.getLevel().setBlockAndUpdate(bookshelf, Blocks.BOOKSHELF.defaultBlockState());
        try {
            result(helper, table.start(player, table.revision(), Set.of(TheorySession.BOOKSHELF)), TheoryResult.ACCEPTED);
            int selections = 0;
            boolean reachedOneInspiration = false;
            while (!table.session().complete() && selections < 16) {
                reachedOneInspiration |= table.session().inspiration() == 1;
                result(helper, table.draw(player, table.revision(), table.session().bonusDraws() > 0), TheoryResult.ACCEPTED);
                helper.assertTrue(!table.session().choices().isEmpty(), "Real low-inspiration theory stalled without any selectable card");
                int index = -1;
                int highestCost = 0;
                for (int choice = 0; choice < table.session().choices().size(); choice++) {
                    TheoryCard card = table.session().choices().get(choice);
                    // Study first leaves odd inspiration, deliberately exercising the final one-point draw.
                    if (selections == 0 && card.id().equals("study")) { index = choice; break; }
                    if (selections > 0 && card.cost() > highestCost) { index = choice; highestCost = card.cost(); }
                }
                helper.assertTrue(index >= 0, "Real theory offered no card that can spend its remaining inspiration");
                if (selections == 0) {
                    TheorySession restoredEffect = TheorySession.load(table.session().save());
                    result(helper, table.select(player, table.revision(), index), TheoryResult.ACCEPTED);
                    helper.assertTrue(restoredEffect.select(player, index) && restoredEffect.save().equals(table.session().save()),
                            "Restored card effect or subsequent RNG state changed the seeded Study result");
                    CompoundTag saved = table.saveWithoutMetadata();
                    ResearchTableBlockEntity reloaded = new ResearchTableBlockEntity(pos, helper.getLevel().getBlockState(pos));
                    reloaded.load(saved); helper.getLevel().setBlockEntity(reloaded); table = reloaded;
                    helper.assertTrue(table.saveWithoutMetadata().equals(saved), "Mid-theory table reload changed its live state");
                } else {
                    result(helper, table.select(player, table.revision(), index), TheoryResult.ACCEPTED);
                }
                selections++;
            }
            helper.assertTrue(table.session().complete() && reachedOneInspiration && selections < 16,
                    "Actual start/draw/select lifecycle did not complete after its one-inspiration phase"
                            + " (inspiration=" + table.session().inspiration() + ", selections=" + selections
                            + ", reachedOne=" + reachedOneInspiration + ")");
            Map<String, Integer> expectedRewards = table.session().rewards();
            result(helper, table.finish(player, table.revision()), TheoryResult.ACCEPTED);
            helper.assertTrue(table.session() == null && table.getItem(1).getCount() == 16 - selections
                            && table.getItem(0).getDamageValue() == selections && table.revision() == 2L + selections * 2L,
                    "Live complete/finish lifecycle lost exact paper, ink, or revision accounting");
            expectedRewards.forEach((category, amount) -> helper.assertTrue(KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, category) == amount,
                    "Actual completed theory did not deliver its percentage reward in " + category));
        } finally { helper.getLevel().setBlockAndUpdate(bookshelf, oldShelfState); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tableResourceFailuresAreAtomicAndLastInkRetainsTools(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 99, 0);
        CompoundTag empty = table.saveWithoutMetadata();
        result(helper, table.start(player, table.revision(), Set.of()), TheoryResult.MISSING_RESOURCES);
        helper.assertTrue(table.saveWithoutMetadata().equals(empty), "Failed start consumed ink or created a session");
        table.setItem(1, new ItemStack(Items.PAPER, 2));
        result(helper, table.start(player, table.revision(), Set.of()), TheoryResult.ACCEPTED);
        helper.assertTrue(table.revision() == 1 && table.getItem(0).getDamageValue() == 99 && table.getItem(1).getCount() == 2
                        && !KnowledgeStore.get(player).isResearchKnown("THEORYRESEARCH"), "Starting charged supplies or required theory research");
        result(helper, table.select(player, table.revision(), 0), TheoryResult.INVALID);
        result(helper, table.draw(player, table.revision(), false), TheoryResult.ACCEPTED);
        helper.assertTrue(table.getItem(1).getCount() == 1 && table.getItem(0).getDamageValue() == 99 && table.revision() == 2,
                "Drawing did not spend exactly one paper and zero ink");
        long drawnRevision = table.revision();
        result(helper, table.select(player, drawnRevision, 0), TheoryResult.ACCEPTED);
        helper.assertTrue(table.getItem(0).is(TheoryModule.SCRIBING_TOOLS.get()) && table.getItem(0).getCount() == 1
                        && table.getItem(0).getDamageValue() == 100 && table.getItem(1).getCount() == 1 && table.revision() == 3,
                "Selection spent paper, broke exhausted tools, or missed its single ink cost");
        CompoundTag selected = table.saveWithoutMetadata();
        result(helper, table.select(player, drawnRevision, 0), TheoryResult.STALE);
        helper.assertTrue(table.saveWithoutMetadata().equals(selected), "Replayed selection paid or rewarded again");
        result(helper, table.draw(player, table.revision(), false), TheoryResult.ACCEPTED);
        CompoundTag withoutInk = table.saveWithoutMetadata();
        result(helper, table.select(player, table.revision(), 0), TheoryResult.MISSING_RESOURCES);
        helper.assertTrue(table.saveWithoutMetadata().equals(withoutInk) && table.getItem(1).isEmpty(), "Missing-ink activation partially changed session/resources");
        result(helper, table.scrap(player, table.revision()), TheoryResult.ACCEPTED);
        helper.assertTrue(table.session() == null && table.getItem(0).getDamageValue() == 100
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, "BASICS") == 0, "Scrap destroyed tools or awarded a theory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void restoredTableKeepsOwnerChoicesHistoryAndRevisionGuards(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer owner = player(helper, pos), other = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 0, 4);
        result(helper, table.start(owner, table.revision(), Set.of()), TheoryResult.ACCEPTED);
        result(helper, table.draw(owner, table.revision(), false), TheoryResult.ACCEPTED);
        result(helper, table.select(owner, table.revision(), 0), TheoryResult.ACCEPTED);
        result(helper, table.draw(owner, table.revision(), false), TheoryResult.ACCEPTED);
        CompoundTag saved = table.saveWithoutMetadata();
        ResearchTableBlockEntity restored = new ResearchTableBlockEntity(pos, helper.getLevel().getBlockState(pos));
        restored.load(saved);
        helper.getLevel().setBlockEntity(restored);
        helper.assertTrue(restored.saveWithoutMetadata().equals(saved) && restored.revision() == 4
                        && restored.session().owner().equals(owner.getUUID()) && restored.session().lastCard() != null,
                "Reload changed choices, initialized targets, owner, card history, resources, or revision");
        result(helper, restored.start(other, restored.revision(), Set.of()), TheoryResult.LOCKED);
        result(helper, restored.draw(other, restored.revision(), false), TheoryResult.LOCKED);
        result(helper, restored.select(other, restored.revision(), 0), TheoryResult.LOCKED);
        result(helper, restored.finish(other, restored.revision()), TheoryResult.LOCKED);
        result(helper, restored.scrap(other, restored.revision()), TheoryResult.LOCKED);
        owner.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        result(helper, restored.select(owner, restored.revision(), 0), TheoryResult.LOCKED);
        owner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        result(helper, restored.select(owner, restored.revision() - 1, 0), TheoryResult.STALE);
        helper.assertTrue(restored.saveWithoutMetadata().equals(saved), "Rejected owner/context/revision request changed a restored session");
        result(helper, restored.select(owner, restored.revision(), 0), TheoryResult.ACCEPTED);
        helper.assertTrue(restored.revision() == 5 && restored.getItem(0).getDamageValue() == 2, "Restored current choice could not commit once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingInkCannotDebitRestoredAnalyzeObservation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        prepareAlchemyKnowledge(player);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 19);
        ResearchTableBlockEntity table = table(helper, pos, 100, 2);
        CompoundTag session = sessionTag(player, 5, Map.of());
        ListTag choices = new ListTag(); choices.add(card("analyze", "ALCHEMY")); session.put("Choices", choices);
        CompoundTag saved = table.saveWithoutMetadata(); saved.put("Session", session); table.load(saved);
        CompoundTag before = table.saveWithoutMetadata();
        result(helper, table.select(player, table.revision(), 0), TheoryResult.MISSING_RESOURCES);
        helper.assertTrue(table.saveWithoutMetadata().equals(before)
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 19,
                "Missing ink activated Analyze or debited its Observation");
        table.setItem(0, markedTools(0));
        result(helper, table.select(player, table.revision(), 0), TheoryResult.ACCEPTED);
        helper.assertTrue(table.getItem(0).getDamageValue() == 1 && table.getItem(1).getCount() == 2
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 3,
                "Paid Analyze did not debit exactly one ink and 16 raw Observation units");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finishAwardsOnceAndOverflowRejectsEveryCategory(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 0, 1);
        result(helper, table.start(player, table.revision(), Set.of()), TheoryResult.ACCEPTED);
        CompoundTag saved = table.saveWithoutMetadata();
        saved.put("Session", sessionTag(player, 0, Map.of("ALCHEMY", 50, "ARTIFICE", 50, "BASICS", 50))); table.load(saved);
        long beforeFinish = table.revision();
        result(helper, table.scrap(player, beforeFinish), TheoryResult.INVALID);
        result(helper, table.finish(player, beforeFinish), TheoryResult.ACCEPTED);
        helper.assertTrue(table.session() == null && table.revision() == beforeFinish + 1
                        && table.getItem(0).getDamageValue() == 0 && table.getItem(1).getCount() == 1
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 16
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, "ARTIFICE") == 10
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, "BASICS") == 10,
                "Finish did not deliver original raw rewards exactly once without charging supplies");
        result(helper, table.finish(player, beforeFinish), TheoryResult.STALE);
        result(helper, table.finish(player, table.revision()), TheoryResult.NO_SESSION);
        helper.assertTrue(KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 16, "Repeated finish duplicated theory knowledge");
        ServerPlayer overflowing = player(helper, pos);
        KnowledgeStore.addKnowledge(overflowing, KnowledgeType.THEORY, "BASICS", Integer.MAX_VALUE - 20);
        result(helper, table.start(overflowing, table.revision(), Set.of()), TheoryResult.ACCEPTED);
        saved = table.saveWithoutMetadata();
        saved.put("Session", sessionTag(overflowing, 0, Map.of("ALCHEMY", 100, "BASICS", 100))); table.load(saved);
        CompoundTag beforeOverflow = table.saveWithoutMetadata();
        result(helper, table.finish(overflowing, table.revision()), TheoryResult.OVERFLOW);
        helper.assertTrue(table.saveWithoutMetadata().equals(beforeOverflow)
                        && KnowledgeStore.get(overflowing).rawKnowledge(KnowledgeType.THEORY, "BASICS") == Integer.MAX_VALUE - 20
                        && KnowledgeStore.get(overflowing).rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 0,
                "Later-category overflow cleared the theory or partially awarded its earlier category");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookshelfSearchIncludesBoxCornersAndDeduplicatesItsAid(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 0, 2);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (BlockPos inRange : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            if (helper.getLevel().getBlockState(inRange).is(Blocks.BOOKSHELF)) {
                previous.put(inRange.immutable(), helper.getLevel().getBlockState(inRange));
                helper.getLevel().setBlockAndUpdate(inRange, Blocks.AIR.defaultBlockState());
            }
        }
        for (BlockPos location : List.of(pos.offset(4, 1, 4), pos.offset(-4, -1, -4), pos.offset(5, 0, 0), pos.offset(0, 2, 0))) {
            previous.putIfAbsent(location, helper.getLevel().getBlockState(location));
        }
        try {
            helper.getLevel().setBlockAndUpdate(pos.offset(5, 0, 0), Blocks.BOOKSHELF.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos.offset(0, 2, 0), Blocks.BOOKSHELF.defaultBlockState());
            helper.assertTrue(table.checkSurroundingAids().isEmpty(), "Bookshelf outside the 9x3x9 box became an aid");
            CompoundTag before = table.saveWithoutMetadata();
            result(helper, table.start(player, table.revision(), Set.of(TheorySession.BOOKSHELF)), TheoryResult.INVALID);
            helper.assertTrue(table.saveWithoutMetadata().equals(before), "Unavailable selected aid charged inspiration or supplies");
            helper.getLevel().setBlockAndUpdate(pos.offset(4, 1, 4), Blocks.BOOKSHELF.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos.offset(-4, -1, -4), Blocks.BOOKSHELF.defaultBlockState());
            helper.assertTrue(table.checkSurroundingAids().equals(Set.of(TheorySession.BOOKSHELF)), "Inclusive box corners failed or duplicated the Bookshelf key");
            result(helper, table.start(player, table.revision(), Set.of("UNKNOWN_AID")), TheoryResult.INVALID);
            result(helper, table.start(player, table.revision(), Set.of(TheorySession.BOOKSHELF)), TheoryResult.ACCEPTED);
            helper.assertTrue(table.session().inspiration() == 4 && table.session().aidCardsRemaining().size() == 6
                            && table.getItem(1).getCount() == 2 && table.getItem(0).getDamageValue() == 0,
                    "Duplicate bookshelves charged twice or duplicated their finite pool");
        } finally { previous.forEach((location, state) -> helper.getLevel().setBlockAndUpdate(location, state)); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theoryPacketsRejectClosedMenusAndStaleRequests(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        ResearchTableBlockEntity table = table(helper, pos, 0, 2);
        ResearchTableMenu menu = new ResearchTableMenu(17, player.getInventory(), table);
        CompoundTag before = table.saveWithoutMetadata();
        result(helper, TheoryNetwork.process(menu, player, TheoryNetwork.Action.START, 0, -1, Set.of()), TheoryResult.LOCKED);
        helper.assertTrue(table.saveWithoutMetadata().equals(before), "Closed-menu start request changed the table");
        player.containerMenu = menu;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TheoryNetwork.Request request = new TheoryNetwork.Request(17, 0, TheoryNetwork.Action.START, -1, Set.of());
            TheoryNetwork.Request.encode(request, buffer);
            TheoryNetwork.Request decoded = TheoryNetwork.Request.decode(buffer);
            helper.assertTrue(decoded.equals(request) && buffer.readableBytes() == 0, "Theory packet changed its menu/revision/action");
            result(helper, TheoryNetwork.process(menu, player, decoded.action(), decoded.revision(), decoded.cardIndex(), decoded.aids()), TheoryResult.ACCEPTED);
            CompoundTag started = table.saveWithoutMetadata();
            result(helper, TheoryNetwork.process(menu, player, decoded.action(), decoded.revision(), decoded.cardIndex(), decoded.aids()), TheoryResult.STALE);
            helper.assertTrue(table.saveWithoutMetadata().equals(started), "Replayed start replaced the existing theory");
        } finally { buffer.release(); }
        player.containerMenu = player.inventoryMenu;
        before = table.saveWithoutMetadata();
        result(helper, TheoryNetwork.process(menu, player, TheoryNetwork.Action.DRAW, table.revision(), -1, Set.of()), TheoryResult.LOCKED);
        helper.assertTrue(table.saveWithoutMetadata().equals(before), "Closed-menu draw consumed paper or created choices");
        helper.succeed();
    }

    private static void prepareAlchemyKnowledge(ServerPlayer player) {
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        ResearchProgression.advance(player, "FIRSTSTEPS", 0);
        KnowledgeStore.recordCraft(player, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        ResearchProgression.advance(player, "FIRSTSTEPS", 1);
        KnowledgeStore.recordCraft(player, new ItemStack(ScanningModule.THAUMOMETER.get()));
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "BASICS", 32);
        ResearchProgression.advance(player, "FIRSTSTEPS", 2);
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 16);
        ResearchProgression.advance(player, "UNLOCKALCHEMY", 0);
        ResearchProgression.advance(player, "UNLOCKALCHEMY", 1);
        KnowledgeStore.recordCraft(player, new ItemStack(AlchemyModule.CRUCIBLE_ITEM.get()));
        ResearchProgression.advance(player, "UNLOCKALCHEMY", 2);
        KnowledgeStore.recordCraft(player, new ItemStack(AlchemyModule.NITOR.get()));
        ResearchProgression.advance(player, "UNLOCKALCHEMY", 3);
    }

    @GameTest(template = "empty")
    public static void analyzePreservesBinaryEligibilityAndChecksObservationBeforeDebit(GameTestHelper helper) {
        ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        prepareAlchemyKnowledge(player);
        helper.assertTrue(KnowledgeStore.get(player).isResearchCompleteStrict("UNLOCKALCHEMY"), "Analyze fixture did not unlock Alchemy");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 15);
        CompoundTag saved = sessionTag(player, 5, Map.of());
        ListTag choices = new ListTag(); choices.add(card("analyze", "ALCHEMY")); saved.put("Choices", choices);
        TheorySession session = TheorySession.load(saved);
        helper.assertTrue(!session.select(player, 0) && session.save().equals(saved)
                        && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 15,
                "Analyze spent a fractional Observation or partially changed the session");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "ALCHEMY", 4);
        helper.assertTrue(TheoryCard.initialize("analyze", 42L, false, session, KnowledgeStore.get(player)) == null,
                "Named category observations bypassed the verified BETA26 Analyze initialization bug");
        helper.assertTrue(session.select(player, 0) && KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "ALCHEMY") == 3
                        && session.inspiration() == 3 && session.totals().get("BASICS") == 5
                        && session.totals().get("ALCHEMY") >= 25 && session.totals().get("ALCHEMY") <= 50,
                "Restored initialized Analyze did not spend exactly 16 raw Observations and award its original ranges");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void genericAndBookshelfDrawsRespectAidOnlyCardsAndFiniteWeight(GameTestHelper helper) {
        ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        TheorySession normal = TheorySession.create(player.getUUID(), KnowledgeStore.get(player), Set.of(), RandomSource.create(12));
        helper.assertTrue(normal.inspiration() == 5 && normal.draw(player, false), "Fresh unaided session did not draw");
        helper.assertTrue(normal.choices().size() == 1 && normal.choices().get(0).id().equals("experimentation")
                        && !normal.choices().get(0).fromAid(), "Fresh nine-card pool fabricated an ineligible ordinary offer");
        CompoundTag ordinaryBefore = normal.save();
        helper.assertTrue(!normal.draw(player, true) && normal.save().equals(ordinaryBefore), "Drawing over existing choices changed the session");
        TheorySession aided = TheorySession.create(player.getUUID(), KnowledgeStore.get(player), Set.of(TheorySession.BOOKSHELF), RandomSource.create(12));
        helper.assertTrue(aided.inspirationStart() == 5 && aided.inspiration() == 4
                        && aided.aidCardsRemaining().equals(List.of("balance", "notation", "notation", "study", "study", "study")),
                "Bookshelf inspiration or finite 1:2:3 weight changed");
        helper.assertTrue(aided.draw(player, false) && aided.choices().size() == 2, "Bookshelf could not add Study to a fresh draw");
        TheoryCard study = aided.choices().stream().filter(choice -> choice.id().equals("study")).findFirst().orElse(null);
        helper.assertTrue(study != null && study.aidOnly() && study.fromAid() && study.category().equals("BASICS")
                        && aided.aidCardsRemaining().equals(List.of("balance", "notation", "notation", "study", "study")),
                "Study was ordinary, targeted a locked category, or failed to consume one weighted aid occurrence");
        helper.assertTrue(TheorySession.load(aided.save()).save().equals(aided.save()), "Saving rerolled initialized aid targets or seeds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inspirationUsesCompletedCanonicalMetadataAndRoundedCatalogTotal(GameTestHelper helper) {
        helper.assertTrue(TheorySession.availableInspiration(new PlayerKnowledge()) == 5, "Initial inspiration changed");
        var spiky = ResearchCatalog.entries().stream().filter(entry -> !entry.supported() && entry.hasMeta("SPIKY") && !entry.hasMeta("HIDDEN")).findFirst().orElseThrow();
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", 2);
        CompoundTag stages = new CompoundTag();
        stages.putInt(spiky.key(), Math.max(1, spiky.stages().size()));
        saved.put("ResearchStages", stages);
        helper.assertTrue(TheorySession.availableInspiration(PlayerKnowledge.load(saved)) == 5, "Incomplete SPIKY research increased inspiration");
        stages.putInt(spiky.key(), spiky.stages().size() + 1);
        helper.assertTrue(TheorySession.availableInspiration(PlayerKnowledge.load(saved)) == 6, "Completed SPIKY half-point did not round up");
        ListTag facts = new ListTag();
        facts.add(StringTag.valueOf("!gotdream"));
        facts.add(StringTag.valueOf("!gotthaumonomicon"));
        saved.put("Research", facts);
        helper.assertTrue(TheorySession.availableInspiration(PlayerKnowledge.load(saved)) == 6, "Event facts fabricated metadata inspiration");
        for (var entry : ResearchCatalog.entries()) {
            if (!entry.supported()) stages.putInt(entry.key(), entry.stages().size() + 1);
        }
        helper.assertTrue(TheorySession.availableInspiration(PlayerKnowledge.load(saved)) == 14, "Pinned catalogue's 10 SPIKY and 43 HIDDEN entries did not round to 14 inspiration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finishPreviewUsesAlphabeticTiesRawRoundingAndPenaltyPrefix(GameTestHelper helper) {
        ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        CompoundTag saved = sessionTag(player, 0, Map.of("BASICS", 50, "ARTIFICE", 50, "ALCHEMY", 50));
        ListTag blocked = new ListTag();
        blocked.add(StringTag.valueOf("ALCHEMY"));
        saved.put("Blocked", blocked);
        TheorySession session = TheorySession.load(saved);
        helper.assertTrue(session.complete() && List.copyOf(session.rewards().keySet()).equals(List.of("ALCHEMY", "ARTIFICE", "BASICS")),
                "Equal percentages lost the original alphabetical order");
        helper.assertTrue(session.rewards().equals(Map.of("ALCHEMY", 16, "ARTIFICE", 10, "BASICS", 10)),
                "Theory preview used whole points, erased blocked totals, or rounded the penalty incorrectly");
        saved.putInt("PenaltyStart", 1);
        helper.assertTrue(TheorySession.load(saved).rewards().equals(Map.of("ALCHEMY", 16, "ARTIFICE", 16, "BASICS", 10)),
                "Balance penalty prefix did not exempt its additional category");
        CompoundTag tiny = sessionTag(player, 0, Map.of("ALCHEMY", 1, "BASICS", 1));
        helper.assertTrue(TheorySession.load(tiny).rewards().equals(Map.of("ALCHEMY", 0, "BASICS", 1)), "Tiny top/penalized theory rounding changed");
        CompoundTag large = sessionTag(player, 0, Map.of("ALCHEMY", 150, "BASICS", 100));
        helper.assertTrue(TheorySession.load(large).rewards().equals(Map.of("ALCHEMY", 48, "BASICS", 21)), "Percentage above 100 was clamped or penalty changed");
        helper.assertTrue(KnowledgeStore.get(player).rawKnowledge(KnowledgeType.THEORY, "ALCHEMY") == 0,
                "Preview itself delivered theory knowledge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rethinkCapsRefundAndRestoredDrawsDoNotReroll(GameTestHelper helper) {
        ServerPlayer player = player(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
        CompoundTag saved = sessionTag(player, 5, Map.of("BASICS", 20, "ALCHEMY", 5, "ARTIFICE", 3));
        ListTag choices = new ListTag(); choices.add(card("rethink", null)); saved.put("Choices", choices);
        TheorySession session = TheorySession.load(saved);
        helper.assertTrue(session.select(player, 0) && session.inspiration() == 5 && session.bonusDraws() == 1
                        && session.placedCards() == 1 && session.lastCard().id().equals("rethink") && session.choices().isEmpty(),
                "Negative inspiration cost exceeded its cap or lost selected-card history");
        helper.assertTrue(session.totals().get("ALCHEMY") == 1 && !session.totals().containsKey("ARTIFICE")
                        && session.totals().get("BASICS") >= 18 && session.totals().get("BASICS") <= 27,
                "Rethink did not remove ten alphabetically distributed progress points");
        CompoundTag after = session.save();
        helper.assertTrue(!session.select(player, 0) && session.save().equals(after), "Repeated selection activated the same card twice");
        TheorySession restored = TheorySession.load(after);
        helper.assertTrue(restored.save().equals(after) && session.draw(player, true) && restored.draw(player, true)
                        && restored.save().equals(session.save()) && session.bonusDraws() == 0 && session.choices().size() == 3,
                "Restoring the session changed the next seeded bonus draw or spent more than one bonus credit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woodTableConversionRecordsOnlyCommittedCraftAndPreservesTools(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos), bystander = player(helper, pos);
        player.getInventory().add(new ItemStack(TheoryModule.TABLE_ITEM.get()));
        helper.assertTrue(!KnowledgeStore.get(player).hasCraft("thaumcraft:research_table"), "Owning a research table fabricated craft proof");
        helper.getLevel().setBlockAndUpdate(pos, TheoryModule.WOOD_TABLE.get().defaultBlockState());
        ItemStack tools = markedTools(100);
        player.setItemInHand(InteractionHand.MAIN_HAND, tools.copy());
        java.util.concurrent.atomic.AtomicInteger cancelled = new java.util.concurrent.atomic.AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getLevel() == helper.getLevel() && event.getPos().equals(pos) && event.getEntity() == player) {
                cancelled.incrementAndGet();
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(cancel);
        try {
            helper.assertTrue(!convert(player, pos).consumesAction() && cancelled.get() == 1, "Cancelled table placement reported success or skipped its Forge hook");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(TheoryModule.WOOD_TABLE.get())
                            && ItemStack.matches(player.getMainHandItem(), tools)
                            && !KnowledgeStore.get(player).hasCraft("thaumcraft:research_table"),
                    "Cancelled conversion consumed table/tools or credited a craft");
        } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        helper.assertTrue(convert(player, pos).consumesAction(), "Wooden table did not convert without prior research");
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(TheoryModule.TABLE.get())
                        && player.getMainHandItem().isEmpty(), "Committed conversion did not transfer tools to the table");
        ResearchTableBlockEntity table = (ResearchTableBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(ItemStack.matches(table.getItem(0), tools), "Conversion lost exhausted tools or their NBT");
        helper.assertTrue(KnowledgeStore.get(player).hasCraft("thaumcraft:research_table")
                        && !KnowledgeStore.get(bystander).hasCraft("thaumcraft:research_table"), "Conversion credited the wrong player");
        helper.assertTrue(!KnowledgeStore.get(player).isResearchCompleteStrict("THEORYRESEARCH"), "Conversion itself completed theory research");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exhaustedScribingToolsRefillThroughCommittedVanillaRecipe(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = player(helper, pos);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        ItemStack exhausted = markedTools(100);
        menu.getSlot(1).set(exhausted.copy());
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Tools refilled without an ink ingredient");
        menu.getSlot(2).set(new ItemStack(Items.BLACK_DYE));
        ItemStack preview = menu.getSlot(0).getItem();
        helper.assertTrue(preview.is(TheoryModule.SCRIBING_TOOLS.get()) && preview.getDamageValue() == 0
                        && preview.getHoverName().equals(exhausted.getHoverName())
                        && preview.getTag().getString("TheoryTestMarker").equals("keep"), "Refill preview lost tool name/NBT or retained exhausted damage");
        helper.assertTrue(!KnowledgeStore.get(player).hasCraft("thaumcraft:scribing_tools")
                        && menu.getSlot(1).getItem().getDamageValue() == 100, "Recipe preview consumed tools or wrote craft proof");
        menu.getSlot(3).set(new ItemStack(Items.FEATHER));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Refill accepted an unrelated extra ingredient");
        menu.getSlot(3).set(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(TheoryModule.SCRIBING_TOOLS.get()) && menu.getCarried().getDamageValue() == 0
                        && menu.getCarried().getTag().getString("TheoryTestMarker").equals("keep")
                        && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Refill did not consume exactly tools plus one ink ingredient");
        helper.assertTrue(KnowledgeStore.get(player).hasCraft("thaumcraft:scribing_tools"), "Committed refill result did not produce scribing tool craft proof");
        helper.succeed();
    }
}
