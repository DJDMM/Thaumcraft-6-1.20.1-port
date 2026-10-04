package thaumcraft.golemancy.press;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual press/menu/capabilities and paid typed draws; research/components are explicit fixtures. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemPressGameTests {
    private static final String TEMPLATE = "essentia_network";
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    private static final long BASIC = 0L;
    private GolemPressGameTests() {}
    private static GolemPressBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(CENTER, CatalogBlocks.block("golem_builder").defaultBlockState().setValue(GolemPressBlock.FACING, Direction.NORTH));
        return (GolemPressBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }
    private static ServerPlayer player(GameTestHelper helper, GolemPressBlockEntity tile) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "press_qa"));
        player.setPos(tile.getBlockPos().getX() + .5, tile.getBlockPos().getY() + .5, tile.getBlockPos().getZ() + 2);
        player.containerMenu = new GolemPressMenu(7, player.getInventory(), tile);
        return player;
    }
    private static void stage(ServerPlayer player, String key, int stage) {
        try { var setter = PlayerKnowledge.class.getDeclaredMethod("setResearchStage", String.class, int.class); setter.setAccessible(true); setter.invoke(KnowledgeStore.get(player), key, stage); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void allowed(ServerPlayer player) {
        for (String key : List.of("MATSTUDWOOD", "MINDCLOCKWORK")) stage(player, key, ResearchCatalog.get(key).stages().size() + 1);
    }
    private static GolemDesign design(long id) { return GolemDesign.parse(id).orElseThrow(); }
    private static void supply(ServerPlayer player, long id) {
        player.getInventory().clearContent();
        List<ItemStack> components = design(id).components();
        for (int slot = 0; slot < components.size(); slot++) player.getInventory().setItem(slot, components.get(slot).copy());
    }
    private static List<ItemStack> playerItems(ServerPlayer player) { return player.getInventory().items.stream().map(ItemStack::copy).toList(); }
    private static boolean same(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) if (!ItemStack.matches(first.get(index), second.get(index))) return false;
        return true;
    }
    private static void tick(GameTestHelper helper, GolemPressBlockEntity tile, int ticks) {
        for (int index = 0; index < ticks; index++) GolemPressBlockEntity.tick(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }
    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, Blocks.CHEST);
        return (ChestBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }
    private static AlembicBlockEntity source(GameTestHelper helper, BlockPos relative, Aspect aspect, int amount) {
        helper.setBlock(relative, CatalogBlocks.block("alembic"));
        var source = (AlembicBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(relative)); source.addToContainer(aspect, amount); return source;
    }
    private static void start(GameTestHelper helper, GolemPressBlockEntity tile, ServerPlayer player, long id) {
        helper.assertTrue(((GolemPressMenu)player.containerMenu).startDesign(id), "Actual scoped menu rejected original accessible design");
    }

    @GameTest(template = TEMPLATE)
    public static void pressRequiresCompletedPartResearchRealMenuAndValidUnrankedProperties(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); supply(player, BASIC);
        List<ItemStack> inventory = playerItems(player); CompoundTag before = tile.saveWithoutMetadata();
        stage(player, "MINDCLOCKWORK", 2); stage(player, "MATSTUDWOOD", 2);
        helper.assertTrue(!tile.startCraft(BASIC, player), "Entered Mind2 incorrectly satisfied original completed BASIC part gates");
        allowed(player);
        for (long malformed : new long[]{-1L, 6L << 56, 5L << 48, 5L << 40, 4L << 32, 4L << 24, 1L << 16, 1L, 1L << 8})
            helper.assertTrue(!tile.startCraft(malformed, player), "Malformed or ranked C2S design was manufactured: " + malformed);
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(!tile.startCraft(BASIC, player), "Start without matching physical menu consumed components");
        helper.assertTrue(before.equals(tile.saveWithoutMetadata()) && same(inventory, playerItems(player)), "Rejected start mutated original machine or inventory");
        helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressPreviewIsReadOnlyAndMissingLateComponentCannotPartiallyPay(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        var menu = (GolemPressMenu)player.containerMenu; CompoundTag before = tile.saveWithoutMetadata(); var inventory = playerItems(player);
        helper.assertTrue(menu.preview(player, 0, BASIC) && menu.owns().equals(List.of(true, true, true)), "Server preview did not derive all actual components");
        helper.assertTrue(before.equals(tile.saveWithoutMetadata()) && same(inventory, playerItems(player)), "Preview spent resources or started machine");
        player.getInventory().setItem(2, ItemStack.EMPTY); inventory = playerItems(player);
        helper.assertTrue(!menu.startDesign(BASIC) && !tile.busy() && tile.cost() == 0 && same(inventory, playerItems(player)), "Missing final mind partially consumed earlier planks/mechanisms");
        helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressReservesWholeAdjacentComponentsInOriginalDownFirstOrder(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        var below = chest(helper, CENTER.below()); var north = chest(helper, CENTER.north());
        var components = design(BASIC).components();
        for (int slot = 0; slot < components.size(); slot++) below.setItem(slot, components.get(slot).copy());
        north.setItem(0, components.get(0).copy()); var inventory = playerItems(player);
        start(helper, tile, player, BASIC);
        helper.assertTrue(below.isEmpty() && north.getItem(0).getCount() == 3 && same(inventory, playerItems(player))
                && tile.cost() == 8 && tile.maxCost() == 8 && tile.golemId() == BASIC, "Original DOWN-before-horizontal adjacent priority or exact8 Machina price changed");
        helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void partialChestUsesWholePlayerCountWithoutOriginalUnsafeOverdebit(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player);
        var chest = chest(helper, CENTER.west()); var components = design(BASIC).components();
        chest.setItem(0, components.get(0).copyWithCount(2)); chest.setItem(1, components.get(1).copy()); chest.setItem(2, components.get(2).copy());
        player.getInventory().setItem(0, components.get(0).copy());
        start(helper, tile, player, BASIC);
        helper.assertTrue(chest.getItem(0).getCount() == 2 && chest.getItem(1).isEmpty() && chest.getItem(2).isEmpty() && player.getInventory().isEmpty(),
                "Atomic adaptation overcharged a partial adjacent stack in addition to the whole player count");
        helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void partialChestAndPartialPlayerNeverAggregateOrPay(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        var chest = chest(helper, CENTER.west()); var planks = design(BASIC).components().get(0);
        chest.setItem(0, planks.copyWithCount(2)); player.getInventory().setItem(0, planks.copyWithCount(1)); var inventory = playerItems(player);
        helper.assertTrue(!tile.startCraft(BASIC, player) && chest.getItem(0).getCount() == 2 && same(inventory, playerItems(player)) && !tile.busy(),
                "Original whole-per-component source check was replaced with chest/player aggregation"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressFifthTickTypedDrawPaysExactlyEightAndCompletesSameTerminalTick(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        var source = source(helper, CENTER.west(), Aspect.MECHANISM, 10); start(helper, tile, player, BASIC);
        tick(helper, tile, 4); helper.assertTrue(tile.cost() == 8 && source.amount() == 10 && tile.isEmpty(), "Typed draw occurred before fifth tick");
        tick(helper, tile, 1); helper.assertTrue(tile.cost() == 7 && source.amount() == 9 && tile.getSuctionAmount(Direction.WEST) == 128, "Fifth tick failed exact one Machina debit");
        tick(helper, tile, 34); helper.assertTrue(tile.cost() == 1 && tile.isEmpty(), "Manufacture completed before eighth fifth-tick debit");
        tick(helper, tile, 1); ItemStack output = tile.getItem(0);
        helper.assertTrue(output.getCount() == 1 && ItemStack.isSameItemSameTags(output, GolemPressBlockEntity.outputStack(BASIC)) && output.getTag().contains("props", Tag.TAG_LONG)
                && source.amount() == 2 && !tile.busy() && tile.cost() == 0 && tile.maxCost() == 8 && tile.getSuctionAmount(null) == 0 && player.getInventory().isEmpty(),
                "Terminal draw did not emit precisely one original long-props output without second component charge");
        tick(helper, tile, 20); helper.assertTrue(tile.getItem(0).getCount() == 1 && source.amount() == 2, "Idle press repeated paid output or took essentia"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressRejectsWrongTypedPeersAndPreservesFirstConnectedNonOutputStop(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        var wrong = source(helper, CENTER.north(), Aspect.FIRE, 5); var upper = source(helper, CENTER.above(), Aspect.MECHANISM, 5);
        start(helper, tile, player, BASIC); tick(helper, tile, 5);
        helper.assertTrue(tile.cost() == 8 && wrong.amount() == 5 && upper.amount() == 5, "Wrong aspect or forbidden UP source was consumed");
        // A second press BELOW is not a connected peer: its UP face is forbidden.
        // Use the actual supported Thaumatorium UP input endpoint, with its physical crucible support.
        helper.setBlock(CENTER.below(2), thaumcraft.alchemy.AlchemyModule.CRUCIBLE.get());
        helper.setBlock(CENTER.below(), CatalogBlocks.block("thaumatorium"));
        var blocker = (thaumcraft.essentia.thaumatorium.ThaumatoriumBlockEntity)helper.getLevel().getBlockEntity(tile.getBlockPos().below());
        helper.assertTrue(blocker.isConnectable(Direction.UP) && !blocker.canOutputTo(Direction.UP)
                && tile.isConnectable(Direction.DOWN), "DOWN stop fixture does not supply the original connected non-output peer");
        var permitted = source(helper, CENTER.west(), Aspect.MECHANISM, 5);
        tick(helper, tile, 5); helper.assertTrue(tile.cost() == 8 && permitted.amount() == 5, "Original first DOWN connectable non-output peer did not stop the scan");
        helper.setBlock(CENTER.below(), Blocks.AIR); tick(helper, tile, 5);
        helper.assertTrue(tile.cost() == 7 && permitted.amount() == 4, "Horizontal typed draw failed after blocking DOWN peer was removed"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressOneUnitBufferIsTransientAndPaidCostResumesAcrossRealSave(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC); start(helper, tile, player, BASIC);
        helper.assertTrue(tile.addEssentia(Aspect.MECHANISM, 100, Direction.DOWN) == 1 && tile.addEssentia(Aspect.MECHANISM, 1, Direction.WEST) == 0, "Press accepted more than its original one-unit buffer");
        CompoundTag save = tile.saveWithoutMetadata();
        helper.assertTrue(!save.contains("bufferedEssentia") && !save.contains("ticks") && !save.contains("press"), "Transient buffer/animation/cadence was persisted");
        var restored = new GolemPressBlockEntity(tile.getBlockPos(), tile.getBlockState()); restored.load(save); helper.getLevel().setBlockEntity(restored);
        player.containerMenu = new GolemPressMenu(8, player.getInventory(), restored);
        tick(helper, restored, 5); helper.assertTrue(restored.cost() == 8 && restored.golemId() == BASIC && player.getInventory().isEmpty(), "Reload persisted uncredited buffer or charged component plan again");
        var source = source(helper, CENTER.west(), Aspect.MECHANISM, 8); tick(helper, restored, 40);
        helper.assertTrue(restored.getItem(0).getCount() == 1 && source.amount() == 0 && !restored.busy(), "Paid restored design did not finish after exactly eight new typed units"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void occupiedTerminalOutputPreservesOriginalZeroCostStallEvenAfterExtractionAndReload(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        var source = source(helper, CENTER.west(), Aspect.MECHANISM, 10); start(helper, tile, player, BASIC);
        ItemStack otherProps = GolemPressBlockEntity.outputStack(GolemDesign.create(0, 0, 0, 1, 0).orElseThrow().props());
        var outputHandler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(() -> new AssertionError("Press output endpoint missing"));
        helper.assertTrue(outputHandler.insertItem(0, otherProps.copy(), false).isEmpty(), "Original inventory API refused an external placer item while GUI is output-only");
        tick(helper, tile, 40);
        helper.assertTrue(tile.cost() == 0 && tile.golemId() == BASIC && tile.busy() && source.amount() == 2 && ItemStack.isSameItemSameTags(tile.getItem(0), otherProps), "Blocked terminal output invented retry or discarded paid design");
        tile.removeItem(0, 1); tick(helper, tile, 20); helper.assertTrue(tile.isEmpty() && tile.busy() && source.amount() == 2, "Clearing output retried original cost0 stalled job");
        CompoundTag save = tile.saveWithoutMetadata(); tile.load(save); tick(helper, tile, 20); supply(player, BASIC); var inventory = playerItems(player);
        helper.assertTrue(tile.isEmpty() && tile.golemId() == BASIC && !tile.startCraft(BASIC, player) && same(inventory, playerItems(player)) && source.amount() == 2,
                "Reload/replayed start lost or repaid terminal stalled job"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void matchingOutputStacksOnlyWithIdenticalWholePropsAndBusyReplayCannotPay(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC);
        tile.setItem(0, GolemPressBlockEntity.outputStack(BASIC).copyWithCount(2)); var source = source(helper, CENTER.west(), Aspect.MECHANISM, 8);
        var menu = (GolemPressMenu)player.containerMenu; helper.assertTrue(menu.startDesign(player, 0, BASIC), "Matching props stack prevented start");
        supply(player, BASIC); var inventory = playerItems(player);
        helper.assertTrue(!menu.startDesign(player, 0, BASIC) && !menu.startDesign(player, 1, BASIC) && same(inventory, playerItems(player)) && tile.cost() == 8,
                "Stale or busy valid-revision request consumed components again");
        tick(helper, tile, 40); helper.assertTrue(tile.getItem(0).getCount() == 3 && source.amount() == 0, "Identical props output did not grow by exactly one");
        long roller = GolemDesign.create(0, 0, 0, 1, 0).orElseThrow().props(); supply(player, roller); inventory = playerItems(player);
        helper.assertTrue(!menu.startDesign(roller) && same(inventory, playerItems(player)) && !tile.busy(), "Different props reused occupied output stack or paid components");
        tile.setItem(0, GolemPressBlockEntity.outputStack(BASIC).copyWithCount(64)); supply(player, BASIC);
        helper.assertTrue(!menu.startDesign(BASIC) && tile.getItem(0).getCount() == 64, "Full identical output accepted another process"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressMenuIsOutputOnlyWhileAutomationPreservesOriginalPlacerValidationAndSlotPositions(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); var menu = (GolemPressMenu)player.containerMenu;
        helper.assertTrue(menu.slots.size() == 37 && menu.slots.get(0).x == 160 && menu.slots.get(0).y == 104
                && menu.slots.get(1).x == 24 && menu.slots.get(1).y == 142 && menu.slots.get(28).y == 200
                && !menu.slots.get(0).mayPlace(GolemPressBlockEntity.outputStack(BASIC)), "Original output-only slot or player grid changed");
        for (Direction face : Direction.values()) {
            var cap = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElseThrow(() -> new AssertionError("Press inventory capability missing")); var stack = GolemPressBlockEntity.outputStack(BASIC);
            ItemStack foreign = new ItemStack(Items.STONE);
            helper.assertTrue(cap.insertItem(0, stack.copy(), true).isEmpty() && ItemStack.matches(cap.insertItem(0, foreign.copy(), false), foreign) && tile.isEmpty(),
                    "Original inventory API placer validation or read-only insertion simulation changed");
            helper.assertTrue(tile.isConnectable(face) == (face != Direction.UP) && tile.canInputFrom(face) == (face != Direction.UP)
                    && !tile.canOutputTo(face) && tile.getEssentiaType(face) == null && tile.getEssentiaAmount(face) == 0 && tile.takeEssentia(Aspect.MECHANISM, 1, face) == 0,
                    "Original transport input-only faces/public no-store contract changed");
        }
        tile.setItem(0, GolemPressBlockEntity.outputStack(BASIC).copyWithCount(2)); var handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(() -> new AssertionError("Press output capability missing"));
        helper.assertTrue(handler.extractItem(0, 1, false).getCount() == 1 && tile.getItem(0).getCount() == 1 && !menu.quickMoveStack(player, 0).isEmpty() && tile.isEmpty()
                && menu.quickMoveStack(player, 1).isEmpty(), "Output extraction/quick move failed or player storage inserted into press");
        var retained = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN); tile.invalidateCaps(); helper.assertTrue(!retained.isPresent(), "Destroyed capability remained usable"); tile.reviveCaps();
        helper.assertTrue(tile.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).isPresent(), "Revived press did not restore extraction endpoint"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressRejectsMalformedBufferInputAndCorruptSavedPaymentWithoutDebit(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC); start(helper, tile, player, BASIC);
        for (int amount : new int[]{0, -1, Integer.MIN_VALUE}) helper.assertTrue(tile.addEssentia(Aspect.MECHANISM, amount, Direction.WEST) == 0, "Nonpositive input manufactured a buffer unit");
        helper.assertTrue(tile.addEssentia(null, 1, Direction.WEST) == 0 && tile.addEssentia(Aspect.FIRE, 1, Direction.WEST) == 0
                && tile.addEssentia(Aspect.MECHANISM, 1, Direction.UP) == 0 && tile.addEssentia(Aspect.MECHANISM, 1, null) == 0, "Malformed/off-face input accepted buffer");
        tick(helper, tile, 5); helper.assertTrue(tile.cost() == 8, "Rejected input secretly debited progress");
        CompoundTag corrupt = tile.saveWithoutMetadata(); corrupt.putInt("cost", 9); tile.load(corrupt);
        helper.assertTrue(!tile.busy() && tile.cost() == 0 && tile.maxCost() == 0, "Overbudget saved paid plan was trusted");
        corrupt.putLong("golem", 1L); corrupt.putInt("cost", 8); corrupt.putInt("mcost", 8); tile.load(corrupt);
        helper.assertTrue(!tile.busy() && tile.isEmpty() && player.getInventory().isEmpty(), "Malformed saved props manufactured output or refunded fictional components"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressModernCallbackFailureRestoresAllEscrowAndReentryCannotReplacePlan(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player);
        var handler = new ItemStackHandler(3) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!simulate && slot == 0) helper.assertTrue(!tile.startCraft(BASIC, player), "Capability callback reentered start while payment was in progress");
                if (!simulate && slot == 1) return ItemStack.EMPTY;
                return super.extractItem(slot, amount, simulate);
            }
        };
        var components = design(BASIC).components(); for (int slot = 0; slot < components.size(); slot++) handler.setStackInSlot(slot, components.get(slot).copy());
        helper.setBlock(CENTER.west(), Blocks.CHEST); var mock = new CallbackChest(helper.absolutePos(CENTER.west()), Blocks.CHEST.defaultBlockState(), handler); helper.getLevel().setBlockEntity(mock);
        List<ItemStack> before = new ArrayList<>(); for (int slot = 0; slot < 3; slot++) before.add(handler.getStackInSlot(slot).copy());
        helper.assertTrue(!tile.startCraft(BASIC, player) && !tile.busy() && tile.cost() == 0 && tile.isEmpty(), "Failed extraction started an unpaid or partial machine plan");
        for (int slot = 0; slot < 3; slot++) helper.assertTrue(ItemStack.matches(before.get(slot), handler.getStackInSlot(slot)), "Failed second extraction did not restore exact prior escrow slot " + slot);
        helper.assertTrue(player.getInventory().isEmpty(), "Failed adjacent payment created player refunds or bypassed original source choice"); helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressPreviewSimulationCallbackCannotStartOrSpendComponents(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player);
        var handler = new ItemStackHandler(3) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (simulate) helper.assertTrue(!tile.startCraft(BASIC, player), "Read-only preview handler callback started paid manufacturing");
                return super.extractItem(slot, amount, simulate);
            }
        };
        var components = design(BASIC).components(); for (int slot = 0; slot < components.size(); slot++) handler.setStackInSlot(slot, components.get(slot).copy());
        helper.setBlock(CENTER.west(), Blocks.CHEST); helper.getLevel().setBlockEntity(new CallbackChest(helper.absolutePos(CENTER.west()), Blocks.CHEST.defaultBlockState(), handler));
        CompoundTag before = tile.saveWithoutMetadata(); var menu = (GolemPressMenu)player.containerMenu;
        helper.assertTrue(menu.preview(player, 0, BASIC) && menu.owns().equals(List.of(true, true, true)) && !tile.busy()
                && before.equals(tile.saveWithoutMetadata()) && player.getInventory().isEmpty(), "Guarded simulation mutated machine/player state");
        for (int slot = 0; slot < components.size(); slot++) helper.assertTrue(ItemStack.matches(components.get(slot), handler.getStackInSlot(slot)), "Preview consumed adjacent component " + slot);
        helper.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void pressRangeAndInheritedPhysicalStatsUsePinnedDeviceOverrides(GameTestHelper helper) {
        var tile = machine(helper); var player = player(helper, tile); allowed(player); supply(player, BASIC); var inventory = playerItems(player);
        player.setPos(tile.getBlockPos().getX() + 20, tile.getBlockPos().getY(), tile.getBlockPos().getZ());
        helper.assertTrue(!tile.startCraft(BASIC, player) && same(inventory, playerItems(player)), "Distant real menu started component debit");
        var state = tile.getBlockState();
        // Independent pinned constructor chain: BlockGolemBuilder -> BlockTCDevice -> BlockTCTile, hard2/setResistance20.
        helper.assertTrue(state.getDestroySpeed(helper.getLevel(), tile.getBlockPos()) == 2 && state.getBlock().getExplosionResistance() == 12
                && !state.requiresCorrectToolForDrops() && state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE,
                "Inherited BlockTCTile overrides were replaced by base BlockTC1.5 values or non-original tool gate");
        helper.succeed();
    }
    private static final class CallbackChest extends ChestBlockEntity {
        private final LazyOptional<IItemHandler> callback;
        CallbackChest(BlockPos pos, BlockState state, IItemHandler handler) { super(pos, state); callback = LazyOptional.of(() -> handler); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == ForgeCapabilities.ITEM_HANDLER ? callback.cast() : super.getCapability(capability, side);
        }
    }
}
