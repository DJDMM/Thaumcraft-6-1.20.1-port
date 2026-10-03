package thaumcraft.infusion;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import thaumcraft.catalog.blocks.CatalogBlocks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionPedestalGameTests {
    @GameTest(template = "empty")
    public static void everyVariantKeepsAllOriginalChargeStatesAndSingleInventory(GameTestHelper helper) {
        int index = 0;
        for (String id : new String[]{"pedestal_arcane", "pedestal_ancient", "pedestal_eldritch"}) {
            BlockPos pos = helper.absolutePos(new BlockPos(++index, 1, 1));
            var block = CatalogBlocks.block(id);
            helper.assertTrue(block instanceof InfusionPedestalBlock, "Visual pedestal survived working registration: " + id);
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            var pedestal = (InfusionPedestalBlockEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(pedestal.getContainerSize() == 1 && pedestal.getMaxStackSize() == 1, "Inventory limit changed");
            for (int charge = 0; charge < 16; charge++)
                helper.assertTrue(block.defaultBlockState().setValue(InfusionPedestalBlock.CHARGE, charge)
                        .getValue(InfusionPedestalBlock.CHARGE) == charge, "Charge state is absent");
            helper.assertTrue(pedestal.getRenderBoundingBox().equals(new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1)), "Held item render bounds are not two blocks high");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryOwnsExactNbtAndRemovalSyncsAnEmptySlot(GameTestHelper helper) {
        var pedestal = pedestal(helper, new BlockPos(1, 1, 1));
        ItemStack source = tagged("ownership", 7);
        CompoundTag original = source.getTag().copy();
        pedestal.setItem(0, source);
        helper.assertTrue(source.getCount() == 7 && pedestal.getItem(0).getCount() == 1
                && pedestal.getItem(0).getTag().equals(original), "Insertion changed input count/NBT");
        source.getOrCreateTag().putString("proof", "mutated-source");
        helper.assertTrue(pedestal.getItem(0).getTag().equals(original), "Input and pedestal share mutable NBT");
        var reloaded = new InfusionPedestalBlockEntity(pedestal.getBlockPos(), pedestal.getBlockState());
        reloaded.load(pedestal.getUpdateTag());
        helper.assertTrue(ItemStack.isSameItemSameTags(reloaded.getItem(0), pedestal.getItem(0)), "Round trip changed exact item NBT");
        ItemStack removed = pedestal.removeItemNoUpdate(0);
        helper.assertTrue(removed.getCount() == 1 && removed.getTag().equals(original), "Output lost original NBT");
        reloaded.load(pedestal.getUpdateTag());
        helper.assertTrue(pedestal.isEmpty() && reloaded.isEmpty(), "Empty update left stale client contents");
        helper.assertTrue(pedestal.getUpdatePacket() != null, "Real BlockEntity update packet is absent");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistedInvalidSlotsAndOversizedCountsAreDiscarded(GameTestHelper helper) {
        var pedestal = pedestal(helper, new BlockPos(1, 1, 1));
        CompoundTag oversized = tagged("oversized", 2).save(new CompoundTag());
        oversized.putByte("Slot", (byte) 0);
        loadEntry(pedestal, oversized);
        helper.assertTrue(pedestal.isEmpty(), "Oversized saved input entered the recipe inventory");
        CompoundTag wrongSlot = tagged("wrong-slot", 1).save(new CompoundTag());
        wrongSlot.putByte("Slot", (byte) 1);
        loadEntry(pedestal, wrongSlot);
        helper.assertTrue(pedestal.isEmpty(), "Invalid saved slot was accepted");
        CompoundTag wrongCountType = tagged("wrong-type", 1).save(new CompoundTag());
        wrongCountType.putByte("Slot", (byte) 0); wrongCountType.putInt("Count", 1);
        loadEntry(pedestal, wrongCountType);
        helper.assertTrue(pedestal.isEmpty(), "Malformed count type was accepted");
        CompoundTag valid = tagged("valid", 1).save(new CompoundTag()); valid.putByte("Slot", (byte) 0);
        loadEntry(pedestal, valid);
        helper.assertTrue(!pedestal.isEmpty() && pedestal.getItem(0).getTag().getString("proof").equals("valid"), "Valid saved item was discarded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSixFacesAndUnsidedAutomationRespectOneItemAndSimulation(GameTestHelper helper) {
        var pedestal = pedestal(helper, new BlockPos(1, 1, 1));
        for (Direction side : Direction.values()) automation(helper, pedestal, side);
        automation(helper, pedestal, null);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void interactingOffhandUsesOriginalMainHandGateAndCreativeConsumption(GameTestHelper helper) {
        var pedestal = pedestal(helper, new BlockPos(1, 1, 1));
        var player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, tagged("offhand", 3));
        helper.assertTrue(use(pedestal, player, InteractionHand.OFF_HAND) == InteractionResult.PASS
                && pedestal.isEmpty() && player.getOffhandItem().getCount() == 3, "Empty main hand incorrectly allowed offhand insertion");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(use(pedestal, player, InteractionHand.OFF_HAND) == InteractionResult.CONSUME
                && player.getOffhandItem().getCount() == 2 && player.getMainHandItem().getCount() == 1
                && pedestal.getItem(0).getTag().getString("proof").equals("offhand"), "Interacting hand did not supply exact item");
        pedestal.clearContent();
        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, tagged("creative", 2));
        use(pedestal, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && !pedestal.isEmpty(), "Original creative pedestal cost was skipped");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingItemDropsAtPlayerWithoutReplacingOrAddingToInventory(GameTestHelper helper) {
        var pedestal = pedestal(helper, new BlockPos(1, 1, 1));
        var player = player(helper);
        BlockPos playerPos = helper.absolutePos(new BlockPos(3, 1, 1));
        player.setPos(playerPos.getX() + .5, playerPos.getY(), playerPos.getZ() + .5);
        String proof = UUID.randomUUID().toString();
        pedestal.setItem(0, tagged(proof, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 4));
        use(pedestal, player, InteractionHand.MAIN_HAND);
        var drops = drops(helper, player.getBoundingBox().inflate(2), proof);
        helper.assertTrue(pedestal.isEmpty() && drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                "Pickup did not clear one exact drop");
        ItemEntity drop = drops.get(0);
        helper.assertTrue(Math.abs(drop.getX() - player.getX()) < .000001
                && Math.abs(drop.getZ() - player.getZ()) < .000001
                && Math.abs(drop.getY() - player.getY() - player.getEyeHeight() / 2) < .000001,
                "Item was dropped at pedestal instead of original player coordinates");
        helper.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 4
                && !player.getInventory().contains(new ItemStack(Items.FEATHER)), "Taking replaced the item or silently inserted it into inventory");
        drop.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargeUpdatesKeepInventoryAndBreakingDropsItExactlyOnce(GameTestHelper helper) {
        var pedestal = pedestal(helper, new BlockPos(1, 1, 1));
        BlockPos pos = pedestal.getBlockPos();
        String proof = UUID.randomUUID().toString();
        pedestal.setItem(0, tagged(proof, 1));
        helper.getLevel().setBlock(pos, pedestal.getBlockState().setValue(InfusionPedestalBlock.CHARGE, 2), 2);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == pedestal && !pedestal.isEmpty()
                && drops(helper, new AABB(pos).inflate(2), proof).isEmpty(), "A charge-only change dropped or replaced inventory");
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var drops = drops(helper, new AABB(pos).inflate(2), proof);
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "Breaking duplicated or lost contents");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void symmetryUsesOccupancyAndCapabilityLifecycleCannotMutateOffThread(GameTestHelper helper) throws InterruptedException {
        var first = pedestal(helper, new BlockPos(1, 1, 1));
        var second = pedestal(helper, new BlockPos(3, 1, 1));
        first.setItem(0, tagged("guarded", 1));
        helper.assertTrue(InfusionPedestalBlock.hasSymmetryPenalty(helper.getLevel(), first.getBlockPos(), second.getBlockPos()), "One occupied pedestal lost symmetry penalty");
        second.setItem(0, new ItemStack(Items.DIAMOND));
        helper.assertTrue(!InfusionPedestalBlock.hasSymmetryPenalty(helper.getLevel(), first.getBlockPos(), second.getBlockPos()), "Different items wrongly penalize occupied symmetry");
        var oldCapability = first.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
        IItemHandler handler = oldCapability.orElseThrow(IllegalStateException::new);
        AtomicBoolean safe = new AtomicBoolean();
        Thread attempt = new Thread(() -> {
            first.setItem(0, new ItemStack(Items.DIRT));
            boolean removedNothing = first.removeItem(0, 1).isEmpty() && handler.extractItem(0, 1, false).isEmpty();
            first.clearContent();
            safe.set(removedNothing && first.getItem(0).getTag().getString("proof").equals("guarded"));
        }, "infusion-pedestal-authority-test");
        attempt.start(); attempt.join(2000);
        helper.assertTrue(!attempt.isAlive() && safe.get(), "Off-thread container/capability mutation was accepted");
        first.invalidateCaps();
        helper.assertTrue(!oldCapability.isPresent(), "Invalidated handler remained usable");
        first.reviveCaps();
        helper.assertTrue(first.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(), "Handler did not revive");
        helper.succeed();
    }

    private static void automation(GameTestHelper helper, InfusionPedestalBlockEntity pedestal, Direction side) {
        IItemHandler handler = pedestal.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(IllegalStateException::new);
        ItemStack input = tagged("automation", 3);
        helper.assertTrue(handler.insertItem(0, input, true).getCount() == 2 && pedestal.isEmpty(), "Simulated insertion mutated slot");
        helper.assertTrue(handler.insertItem(0, input, false).getCount() == 2 && input.getCount() == 3
                && pedestal.getItem(0).getCount() == 1, "One-item insertion limit/input ownership changed");
        ItemStack exposed = handler.getStackInSlot(0); exposed.getOrCreateTag().putString("proof", "capability-alias");
        helper.assertTrue(pedestal.getItem(0).getTag().getString("proof").equals("automation"), "Capability exposes mutable internal NBT");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1, "Occupied slot accepted a replacement");
        helper.assertTrue(handler.extractItem(0, 64, true).getCount() == 1 && !pedestal.isEmpty(), "Simulated extraction mutated slot");
        ItemStack output = handler.extractItem(0, 64, false);
        helper.assertTrue(output.getCount() == 1 && output.getTag().getString("proof").equals("automation") && pedestal.isEmpty(), "Extraction lost exact NBT or failed to clear");
    }
    private static InfusionPedestalBlockEntity pedestal(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlockAndUpdate(pos, CatalogBlocks.block("pedestal_arcane").defaultBlockState());
        return (InfusionPedestalBlockEntity) helper.getLevel().getBlockEntity(pos);
    }
    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "pedestal_test"));
    }
    private static ItemStack tagged(String proof, int count) {
        ItemStack stack = new ItemStack(Items.FEATHER, count);
        stack.getOrCreateTag().putString("proof", proof);
        var nested = new CompoundTag(); nested.putInt("nested", 9); stack.getOrCreateTag().put("component", nested);
        return stack;
    }
    private static InteractionResult use(InfusionPedestalBlockEntity pedestal, ServerPlayer player, InteractionHand hand) {
        BlockPos pos = pedestal.getBlockPos();
        return pedestal.getBlockState().use(player.serverLevel(), player, hand,
                new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
    }
    private static void loadEntry(InfusionPedestalBlockEntity pedestal, CompoundTag entry) {
        ListTag items = new ListTag(); items.add(entry);
        CompoundTag root = new CompoundTag(); root.put("Items", items); pedestal.load(root);
    }
    private static java.util.List<ItemEntity> drops(GameTestHelper helper, AABB box, String proof) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box,
                entity -> entity.getItem().hasTag() && entity.getItem().getTag().getString("proof").equals(proof));
    }
}
