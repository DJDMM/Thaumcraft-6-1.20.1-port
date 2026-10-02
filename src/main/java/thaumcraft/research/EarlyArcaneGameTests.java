package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.arcane.ArcaneWorkbenchMenu;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** The original five equipment recipes are exercised through the paid server result slot. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EarlyArcaneGameTests {
    private EarlyArcaneGameTests() {}

    @GameTest(template = "empty")
    public static void gogglesRequireResearchAndTwoPaidThaumometers(GameTestHelper helper) {
        var bench = bench(helper);
        var player = player(helper, bench);
        // The prerequisite itself still needs all six primals: an empty slot cannot mint a lens.
        KnowledgeStore.get(player).setResearchStage("FIRSTSTEPS", 2);
        thaumometerInputs(bench);
        setVis(helper, bench, 100);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        bench.removeItemNoUpdate(14);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(1).getCount() == 2
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 100,
                "Missing prerequisite crystal created a thaumometer or charged the attempt");
        bench.setItem(14, crystal(5, 2));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        ItemStack firstLens = menu.getCarried();
        helper.assertTrue(firstLens.is(ScanningModule.THAUMOMETER.get()), "First paid thaumometer failed");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        ItemStack secondLens = menu.getCarried();
        helper.assertTrue(secondLens.is(ScanningModule.THAUMOMETER.get()) && bench.isEmpty()
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 60,
                "The two lenses did not consume 40 vis, ingredients and two of each primal");
        menu.setCarried(ItemStack.EMPTY);
        gogglesInputs(bench, firstLens, secondLens);
        var before = bench.saveWithoutMetadata();
        menu.broadcastChanges();
        helper.assertTrue(!menu.hasRecipe(), "Goggles opened before UNLOCKARTIFICE");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata()),
                "Locked goggles consumed inputs");
        // An opened canonical entry, even with the same legacy fact, is not completion.
        KnowledgeStore.get(player).setResearchStage("UNLOCKARTIFICE", 1);
        KnowledgeStore.of(helper.getLevel()).recordFact(player.getUUID(), "UNLOCKARTIFICE");
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("UNLOCKARTIFICE"),
                "A bare fact bypassed canonical completion");
        complete(player, "UNLOCKARTIFICE");
        setVis(helper, bench, 49);
        menu.broadcastChanges();
        helper.assertTrue(menu.hasRecipe() && menu.requiredVis() == 50 && !menu.craftable(),
                "Goggles did not display the original 50 vis payment");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 49,
                "Underfunded goggles consumed lenses or vis");
        setVis(helper, bench, 60);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("goggles")) && bench.isEmpty()
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 10
                && KnowledgeStore.get(player).hasCraft("thaumcraft:goggles"),
                "Goggles were not paid for or did not record the real crafting event");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantedFabricAcceptsEveryOriginalWoolColorWithoutCrystals(GameTestHelper helper) {
        var bench = bench(helper);
        var player = player(helper, bench);
        fabricInputs(bench, Items.BLUE_WOOL);
        setVis(helper, bench, 100);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(4).is(Items.BLUE_WOOL),
                "Fabric bypassed its research gate");
        complete(player, "UNLOCKINFUSION");
        Item[] colors = {Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
                Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
                Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
                Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL};
        for (Item color : colors) {
            fabricInputs(bench, color);
            menu.setCarried(ItemStack.EMPTY);
            menu.broadcastChanges();
            helper.assertTrue(menu.craftable() && menu.requiredVis() == 5, "Original wildcard wool excluded " + color);
            assertNoCrystalCost(helper, bench.findRecipe(player));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(item("fabric")) && menu.getCarried().getCount() == 1
                    && bench.isEmpty(), "Fabric did not consume exactly four string and one wool");
        }
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 20
                && KnowledgeStore.get(player).hasCraft("thaumcraft:fabric"),
                "Sixteen fabric crafts did not pay 80 vis or record their craft");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void robeChestCraftsEightFabricWithOriginalVis(GameTestHelper helper) {
        paidRobe(helper, "cloth_chest", new int[]{0, 2, 3, 4, 5, 6, 7, 8}, ClickType.PICKUP);
    }

    @GameTest(template = "empty")
    public static void robeLeggingsShiftCraftSevenFabricWithOriginalVis(GameTestHelper helper) {
        paidRobe(helper, "cloth_legs", new int[]{0, 1, 2, 3, 5, 6, 8}, ClickType.QUICK_MOVE);
    }

    @GameTest(template = "empty")
    public static void robeBootsAllowTwoRowPatternOffsetAndDoNotSpendCrystals(GameTestHelper helper) {
        // A two-row vanilla-shaped recipe must also match the bottom two rows.
        paidRobe(helper, "cloth_boots", new int[]{3, 5, 6, 8}, ClickType.SWAP);
    }

    @GameTest(template = "empty")
    public static void originalWornDiscountPaysTheDisplayedIntegerCost(GameTestHelper helper) {
        var bench = bench(helper);
        var player = player(helper, bench);
        complete(player, "UNLOCKINFUSION");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item("goggles")));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(item("cloth_chest")));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(item("cloth_legs")));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(item("cloth_boots")));
        fabricInputs(bench, Items.BLACK_WOOL);
        setVis(helper, bench, 4);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        helper.assertTrue(menu.requiredVis() == 4 && menu.craftable(),
                "A 13 percent worn discount must truncate a 5 vis fabric payment to 4");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("fabric")) && bench.isEmpty()
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 0,
                "Fabric charged a different cost from the discounted preview");
        menu.setCarried(ItemStack.EMPTY);
        for (int slot : new int[]{0, 2, 3, 4, 5, 6, 7, 8}) bench.setItem(slot, new ItemStack(item("fabric")));
        setVis(helper, bench, 86);
        menu.broadcastChanges();
        helper.assertTrue(menu.requiredVis() == 87 && !menu.craftable(), "Discounted robe cost changed");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(0).getCount() == 1
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 86,
                "Underfunded discounted robe consumed resources");
        AuraManager.addVis(helper.getLevel(), bench.getBlockPos(), 1);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("cloth_chest")) && bench.isEmpty()
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 0,
                "Discounted robe did not pay exactly the displayed 87 vis");
        helper.succeed();
    }

    private static void paidRobe(GameTestHelper helper, String output, int[] fabricSlots, ClickType click) {
        var bench = bench(helper);
        var player = player(helper, bench);
        for (int slot : fabricSlots) bench.setItem(slot, new ItemStack(item("fabric")));
        // Null AspectList in the original recipe must not consume even installed crystals.
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, crystal(i, 7));
        setVis(helper, bench, 150);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.clicked(0, 0, click, player);
        helper.assertTrue(menu.getCarried().isEmpty() && !player.getInventory().contains(new ItemStack(item(output)))
                && bench.getItem(fabricSlots[0]).getCount() == 1, "Locked robe minted output or spent fabric");
        complete(player, "UNLOCKINFUSION");
        setVis(helper, bench, 99);
        menu.broadcastChanges();
        helper.assertTrue(menu.hasRecipe() && menu.requiredVis() == 100 && !menu.craftable(),
                "The robe did not require its original 100 vis");
        assertNoCrystalCost(helper, bench.findRecipe(player));
        menu.clicked(0, 0, click, player);
        for (int slot : fabricSlots) helper.assertTrue(bench.getItem(slot).getCount() == 1, "Failed robe consumed fabric");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 99, "Failed robe drained aura");
        setVis(helper, bench, 150);
        menu.clicked(0, 0, click, player);
        ItemStack crafted = click == ClickType.PICKUP ? menu.getCarried()
                : click == ClickType.SWAP ? player.getInventory().getItem(0)
                : player.getInventory().items.stream().filter(stack -> stack.is(item(output))).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(crafted.is(item(output)) && crafted.getCount() == 1,
                "Original robe did not reach the paid click destination");
        for (int slot = 0; slot < 9; slot++) helper.assertTrue(bench.getItem(slot).isEmpty(), "Robe left unpaid fabric in " + slot);
        for (int i = 0; i < 6; i++) helper.assertTrue(bench.getItem(9 + i).getCount() == 7, "Crystal-free robe spent a primal");
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 50
                && KnowledgeStore.get(player).hasCraft("thaumcraft:" + output),
                "Robe paid the wrong aura amount or omitted craft evidence");
        helper.succeed();
    }

    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        return (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ServerPlayer player(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "early_arcane"));
        BlockPos pos = bench.getBlockPos();
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return player;
    }

    private static void complete(ServerPlayer player, String key) {
        // This fixture isolates recipe transactions. Canonical payment/parents are tested separately.
        KnowledgeStore.get(player).setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1);
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
    }

    private static ItemStack crystal(int primal, int count) {
        return new ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[primal]).get(), count);
    }

    private static void setVis(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench, float amount) {
        AuraManager.drainVis(helper.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false);
        AuraManager.addVis(helper.getLevel(), bench.getBlockPos(), amount);
    }

    private static void thaumometerInputs(ArcaneWorkbenchBlockEntity bench) {
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT, 2));
        bench.setItem(4, new ItemStack(Items.GLASS_PANE, 2));
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, crystal(i, 2));
    }

    private static void gogglesInputs(ArcaneWorkbenchBlockEntity bench, ItemStack first, ItemStack second) {
        for (int slot : new int[]{0, 2, 3, 5}) bench.setItem(slot, new ItemStack(Items.LEATHER));
        for (int slot : new int[]{1, 7}) bench.setItem(slot, new ItemStack(item("ingot_brass")));
        bench.setItem(6, first);
        bench.setItem(8, second);
    }

    private static void fabricInputs(ArcaneWorkbenchBlockEntity bench, Item color) {
        bench.clearContent();
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.STRING));
        bench.setItem(4, new ItemStack(color));
    }

    private static void assertNoCrystalCost(GameTestHelper helper, ArcaneRecipe recipe) {
        helper.assertTrue(recipe != null, "Expected early equipment recipe was not loaded");
        for (int i = 0; i < 6; i++) helper.assertTrue(recipe.crystalCost(i) == 0, "BETA26 null crystal cost changed");
    }
}
