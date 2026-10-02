package thaumcraft.test;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.arcane.*;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchProgression;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ArcaneGameTests {
    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper helper, int count) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        ArcaneWorkbenchBlockEntity bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT, count));
        bench.setItem(4, new ItemStack(Items.GLASS_PANE, count));
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, new ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get(), count));
        setVis(helper, bench, 100);
        return bench;
    }
    private static ServerPlayer player(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "arcane_test"));
        BlockPos p = bench.getBlockPos(); player.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        ResearchProgression.advance(player, "FIRSTSTEPS", 0);
        KnowledgeStore.recordCraft(player, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        ResearchProgression.advance(player, "FIRSTSTEPS", 1);
        return player;
    }
    private static void setVis(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench, float vis) {
        AuraManager.drainVis(helper.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false);
        AuraManager.addVis(helper.getLevel(), bench.getBlockPos(), vis);
    }

    @GameTest(template = "empty")
    public static void arcanePickupIsAtomicAndStalePreviewsCannotDuplicate(GameTestHelper helper) {
        var bench = bench(helper, 2); var player = player(helper, bench);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        helper.assertTrue(menu.getSlot(0).getItem().is(ScanningModule.THAUMOMETER.get()), "Datapack thaumometer recipe not loaded");
        helper.assertTrue(menu.getSlot(0).remove(1).isEmpty(), "Preview directly extractable");
        menu.clicked(0, 0, ClickType.CLONE, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Clone extracted unpaid output");
        ItemStack crystal = bench.removeItem(14, 2);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(1).getCount() == 2, "Missing crystal consumed inputs");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 100, "Failed craft drained vis");
        bench.setItem(14, crystal); setVis(helper, bench, 19);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(14).getCount() == 2, "Insufficient vis consumed crystals");
        setVis(helper, bench, 100);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ScanningModule.THAUMOMETER.get()) && menu.getCarried().getCount() == 1, "Pickup failed");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 80, "Craft did not spend exactly 20 vis");
        for (int slot : new int[]{1, 3, 4, 5, 7, 9, 10, 11, 12, 13, 14}) helper.assertTrue(bench.getItem(slot).getCount() == 1, "Wrong ingredient consumption in slot " + slot);
        menu.setCarried(ItemStack.EMPTY);
        var otherPlayer = player(helper, bench);
        var stale = new ArcaneWorkbenchMenu(2, otherPlayer.getInventory(), bench);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ScanningModule.THAUMOMETER.get()), "Second craft failed");
        stale.clicked(0, 0, ClickType.PICKUP, otherPlayer);
        helper.assertTrue(stale.getCarried().isEmpty() && bench.isEmpty(), "Two viewers duplicated a stale recipe");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 60, "Stale preview drained vis");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arcaneShiftClickRequiresSpaceAndRoutesCrystals(GameTestHelper helper) {
        var bench = bench(helper, 1); var player = player(helper, bench);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "Shift-click crafted with full inventory");
        helper.assertTrue(bench.getItem(1).getCount() == 1 && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 100, "Full inventory consumed resources");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().getItem(0).is(ScanningModule.THAUMOMETER.get()) && bench.isEmpty(), "Shift result did not reach inventory");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 80, "Shift-click consumed wrong vis");
        player.getInventory().setItem(10, new ItemStack(WorldModule.VIS_CRYSTALS.get("aer").get(), 5));
        menu.quickMoveStack(player, 17);
        helper.assertTrue(bench.getItem(9).getCount() == 5 && player.getInventory().getItem(10).isEmpty(), "Shift-click failed to route primal crystal");
        helper.assertTrue(!menu.getSlot(11).mayPlace(bench.getItem(9)), "Ignis slot accepted Aer crystal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arcaneInventoryPersistsAndBreakDropsNoPreview(GameTestHelper helper) {
        var bench = bench(helper, 2); var player = player(helper, bench);
        var saved = bench.saveWithoutMetadata();
        bench.clearContent(); bench.load(saved);
        helper.assertTrue(bench.getItem(1).getCount() == 2 && bench.getItem(14).getCount() == 2, "Workbench NBT lost contents");
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.removed(player);
        helper.assertTrue(bench.getItem(1).getCount() == 2 && bench.getItem(14).getCount() == 2, "Closing menu removed contents");
        BlockPos pos = bench.getBlockPos();
        helper.getLevel().destroyBlock(pos, true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        int gold = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum();
        int glass = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.GLASS_PANE)).mapToInt(ItemStack::getCount).sum();
        int workbenches = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(ArcaneModule.WORKBENCH_ITEM.get())).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(gold == 8 && glass == 2 && workbenches == 1, "Breaking did not drop exactly the workbench and ingredients");
        for (String primal : ArcaneModule.PRIMALS) {
            int count = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(WorldModule.VIS_CRYSTALS.get(primal).get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(count == 2, "Breaking lost crystal " + primal);
        }
        helper.assertTrue(drops.stream().noneMatch(entity -> entity.getItem().is(ScanningModule.THAUMOMETER.get())), "Breaking dropped unpaid preview");
        helper.assertTrue(!menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(), "Broken bench retained usable menu");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arcaneRecipeNetworkRoundTripAndSwap(GameTestHelper helper) {
        var bench = bench(helper, 1); var player = player(helper, bench);
        var recipe = bench.findRecipe(player);
        helper.assertTrue(recipe != null && recipe.vis() == 20, "Original vis cost changed");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ArcaneModule.RECIPE_SERIALIZER.get().toNetwork(buffer, recipe);
            ArcaneRecipe decoded = ArcaneModule.RECIPE_SERIALIZER.get().fromNetwork(recipe.getId(), buffer);
            helper.assertTrue(decoded.matches(bench, helper.getLevel()) && decoded.vis() == 20 && decoded.hasCrystals(bench) && decoded.unlocked(player), "Recipe sync changed shape or costs");
            var locked = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "locked_arcane"));
            helper.assertTrue(!decoded.unlocked(locked), "Recipe sync lost its research gate");
            for (int i = 0; i < 6; i++) helper.assertTrue(decoded.crystalCost(i) == 1, "Wrong original primal cost");
        } finally { buffer.release(); }
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        BlockPos pos = bench.getBlockPos();
        player.setPos(pos.getX() + 50, pos.getY(), pos.getZ());
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(1).getCount() == 1, "Distant menu crafted");
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT));
        menu.clicked(0, 0, ClickType.SWAP, player);
        helper.assertTrue(bench.getItem(1).getCount() == 1 && player.getInventory().getItem(0).is(Items.DIRT), "Swap overwrote occupied hotbar");
        menu.clicked(0, 1, ClickType.SWAP, player);
        helper.assertTrue(player.getInventory().getItem(1).is(ScanningModule.THAUMOMETER.get()) && bench.isEmpty(), "Hotbar swap failed to transact");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 80, "Swap wrong cost");
        helper.succeed();
    }
}
