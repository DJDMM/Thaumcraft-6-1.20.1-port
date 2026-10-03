package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.arcane.ArcaneWorkbenchMenu;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;

import java.util.Map;
import java.util.UUID;

/** Frozen BETA26 inputs exercised via the paid server result slot, not recipe previews. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EssentiaArcaneGameTests {
    private EssentiaArcaneGameTests() {}

    @GameTest(template = "empty")
    public static void originalFilterAndAlembicRequireResearchAndPayExactWaterCrystals(GameTestHelper helper) {
        var bench = bench(helper); var player = player(helper, bench);
        fill(bench, new String[]{"GWG"}, Map.of('G', Items.GOLD_INGOT, 'W', item("plank_silverwood")));
        crystals(bench, "aqua", 2); setVis(helper, bench, 100);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(0).getCount() == 1, "Locked filter spent gold");
        complete(player, "BASEALCHEMY"); menu.broadcastChanges();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("filter")) && menu.getCarried().getCount() == 2
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 85 && bench.getItem(11).getCount() == 1,
                "Filter is not original two outputs /15 vis /one Aqua");
        fill(bench, new String[]{"WFW", "SBS", "WFW"}, Map.of('W', item("plank_greatwood"), 'F', item("filter"), 'S', item("plate_brass"), 'B', Items.BUCKET));
        crystals(bench, "aqua", 2); menu.setCarried(ItemStack.EMPTY); menu.broadcastChanges();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(4).is(Items.BUCKET), "Alembic opened before completed smelter research");
        complete(player, "ESSENTIASMELTER"); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("alembic")) && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 35
                && bench.getItem(11).getCount() == 1 && KnowledgeStore.get(player).hasCraft("thaumcraft:alembic"), "Alembic payment/craft fact incorrect");
        assertGridEmpty(helper, bench);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wardedJarAllowsColoredPanesAndVoidUpgradePreservesWholeIndependentTag(GameTestHelper helper) {
        var bench = bench(helper); var player = player(helper, bench);
        complete(player, "WARDEDJARS");
        fill(bench, new String[]{"GWG", "G G", "GGG"}, Map.of('G', Items.BLUE_STAINED_GLASS_PANE, 'W', Items.SPRUCE_SLAB));
        setVis(helper, bench, 100);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("jar_normal")) && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 95,
                "Original paneGlass/slabWood jar paid a different recipe");
        ItemStack filled = menu.getCarried().copy();
        new AspectList().add(Aspect.FIRE, 123).writeToNBT(filled.getOrCreateTag());
        filled.getOrCreateTag().putString("AspectFilter", "ignis");
        filled.getOrCreateTag().putString("CustomProof", "keep-the-entire-tag");
        var saved = filled.getTag().copy();
        fill(bench, new String[]{"J"}, Map.of('J', item("jar_normal")));
        bench.setItem(8, bench.removeItemNoUpdate(0));
        bench.setItem(8, filled); crystals(bench, "perditio", 2); menu.setCarried(ItemStack.EMPTY);
        setVis(helper, bench, 49); menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(0).getItem().getTag().equals(saved) && !menu.craftable(), "Void preview lost jar contents/filter or ignored vis");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(8).getTag().equals(saved) && bench.getItem(14).getCount() == 2,
                "Failed void upgrade spent a tagged jar or crystal");
        setVis(helper, bench, 50); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("jar_void")) && menu.getCarried().getTag().equals(saved)
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 0 && bench.getItem(14).getCount() == 1,
                "Void upgrade did not preserve whole tag/pay 50 vis +one Perditio");
        menu.getCarried().getTag().putInt("Mutated", 1);
        helper.assertTrue(!saved.contains("Mutated") && !filled.getTag().contains("Mutated"), "Void output aliases source NBT");
        assertGridEmpty(helper, bench);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tubesAndResonatorUseIronPlatesAndOriginalCountsWithoutCrystalCost(GameTestHelper helper) {
        var bench = bench(helper); var player = player(helper, bench);
        fill(bench, new String[]{" Q ", "IGI", " B "}, Map.of('Q', item("nugget_quicksilver"), 'I', item("plate_iron"), 'G', Items.RED_STAINED_GLASS, 'B', item("nugget_brass")));
        setVis(helper, bench, 100);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 100, "Locked tube spent resources");
        complete(player, "TUBES");
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i]), 7));
        menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("tube")) && menu.getCarried().getCount() == 8
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 90, "Original tube craft did not yield eight for 10 vis");
        for (int i = 0; i < 6; i++) helper.assertTrue(bench.getItem(9 + i).getCount() == 7, "Null AspectList tube spent a crystal");
        fill(bench, new String[]{"I I", "INI", " S "}, Map.of('I', item("plate_iron"), 'N', Items.QUARTZ, 'S', Items.STICK));
        menu.setCarried(ItemStack.EMPTY); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("resonator")) && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 40
                && KnowledgeStore.get(player).hasCraft("thaumcraft:resonator"), "Original resonator recipe/payment failed");
        assertGridEmpty(helper, bench); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allFourTubeConversionsAreTrulyShapelessAndAtomic(GameTestHelper helper) {
        var bench = bench(helper); var player = player(helper, bench); complete(player, "TUBES");
        String[] outputs = {"tube_valve", "tube_filter", "tube_restrict", "tube_oneway"};
        for (int i = 0; i < outputs.length; i++) {
            bench.clearContent(); bench.setItem(8, new ItemStack(item("tube")));
            if (i == 0) bench.setItem(0, new ItemStack(Items.LEVER));
            if (i == 1) bench.setItem(2, CatalogModule.stack("filter"));
            if (i == 2) crystals(bench, "terra", 2);
            if (i == 3) crystals(bench, "aqua", 2);
            setVis(helper, bench, 9); var menu = new ArcaneWorkbenchMenu(i + 1, player.getInventory(), bench);
            helper.assertTrue(bench.findRecipe(player) != null && bench.findRecipe(player).shapeless(), "Tube conversion is not shapeless: " + outputs[i]);
            var before = bench.saveWithoutMetadata(); menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                    && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 9, "Underfunded conversion mutated inputs");
            setVis(helper, bench, 10); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(item(outputs[i])) && menu.getCarried().getCount() == 1
                    && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 0, "Shapeless tube conversion failed: " + outputs[i]);
            assertGridEmpty(helper, bench);
            if (i == 2) helper.assertTrue(bench.getItem(12).getCount() == 1, "Restrictor did not consume one Terra");
            if (i == 3) helper.assertTrue(bench.getItem(11).getCount() == 1, "Directional tube did not consume one Aqua");
        }
        bench.clearContent(); bench.setItem(0, new ItemStack(item("tube"))); bench.setItem(8, new ItemStack(item("tube")));
        helper.assertTrue(bench.findRecipe(player) == null, "Single-input conversion accepted an additional tube");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bufferAndAlchemyConstructPayOriginalDistinctDeviceInputs(GameTestHelper helper) {
        var bench = bench(helper); var player = player(helper, bench); complete(player, "TUBES");
        fill(bench, new String[]{"PVP", "TWT", "PRP"}, Map.of('P', item("phial_empty"), 'V', item("tube_valve"), 'T', item("tube"), 'W', item("plate_iron"), 'R', item("tube_restrict")));
        setVis(helper, bench, 150); var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("tube_buffer")) && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 125,
                "Original buffer did not consume four empty phials/distinct tube types for25 vis");
        assertGridEmpty(helper, bench);
        fill(bench, new String[]{"IVI", "TWT", "IVI"}, Map.of('I', item("plate_iron"), 'V', item("tube_valve"), 'T', item("tube"), 'W', item("plank_greatwood")));
        crystals(bench, "aqua", 2); crystals(bench, "ordo", 2); crystals(bench, "perditio", 2);
        menu.setCarried(ItemStack.EMPTY); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item("metal_alchemical")) && menu.getCarried().getCount() == 2
                && AuraManager.getVis(helper.getLevel(), bench.getBlockPos()) == 50, "Construct recipe did not pay75 vis for two blocks");
        for (int slot : new int[]{11, 13, 14}) helper.assertTrue(bench.getItem(slot).getCount() == 1, "Construct crystal payment incorrect");
        assertGridEmpty(helper, bench); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allNineteenDeviceRecipesSynchronizeTheirOriginalCostsGatesAndDynamicFlags(GameTestHelper helper) {
        var registry = helper.getLevel().getRecipeManager();
        String[][] rows = {
                {"filter", "BASEALCHEMY", "15", "filter", "2", "aqua:1"},
                {"alembic", "ESSENTIASMELTER", "50", "alembic", "1", "aqua:1"},
                {"essentiasmelter", "ESSENTIASMELTER@2", "50", "smelter_basic", "1", "ignis:1"},
                {"wardedjar", "WARDEDJARS", "5", "jar_normal", "1", ""},
                {"jarvoid", "WARDEDJARS", "50", "jar_void", "1", "perditio:1"},
                {"tube", "TUBES", "10", "tube", "8", ""},
                {"resonator", "TUBES", "50", "resonator", "1", ""},
                {"tubevalve", "TUBES", "10", "tube_valve", "1", ""},
                {"tubefilter", "TUBES", "10", "tube_filter", "1", ""},
                {"tuberestrict", "TUBES", "10", "tube_restrict", "1", "terra:1"},
                {"tubeoneway", "TUBES", "10", "tube_oneway", "1", "aqua:1"},
                {"tubebuffer", "TUBES", "25", "tube_buffer", "1", ""},
                {"alchemicalconstruct", "TUBES", "75", "metal_alchemical", "2", "aqua:1,ordo:1,perditio:1"},
                {"essentiasmelterthaumium", "ESSENTIASMELTERTHAUMIUM", "250", "smelter_thaumium", "1", "ignis:2"},
                {"essentiasmeltervoid", "ESSENTIASMELTERVOID", "750", "smelter_void", "1", "ignis:3"},
                {"advalchemyconstruct", "ESSENTIASMELTERVOID@1", "200", "metal_alchemical_advanced", "1", "terra:1,ignis:1"},
                {"smelteraux", "IMPROVEDSMELTING", "100", "smelter_aux", "1", "aer:1,terra:1"},
                {"smeltervent", "IMPROVEDSMELTING2", "150", "smelter_vent", "1", "aer:1"},
                {"bellows", "BELLOWS", "25", "bellows", "1", "aer:1"}};
        var serializer = new ArcaneRecipe.Serializer();
        for (String[] row : rows) {
            var id = ResourceLocation.fromNamespaceAndPath("thaumcraft", "arcane/" + row[0]);
            helper.assertTrue(registry.byKey(id).orElse(null) instanceof ArcaneRecipe, "Missing original recipe " + id);
            var recipe = (ArcaneRecipe)registry.byKey(id).orElseThrow();
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            ArcaneRecipe loaded;
            try { serializer.toNetwork(bytes, recipe); loaded = serializer.fromNetwork(id, bytes); helper.assertTrue(!bytes.isReadable(), "Recipe left unread payload"); }
            finally { bytes.release(); }
            helper.assertTrue(loaded.research().equals(row[1]) && loaded.vis() == Integer.parseInt(row[2])
                    && loaded.getResultItem(helper.getLevel().registryAccess()).is(item(row[3]))
                    && loaded.getResultItem(helper.getLevel().registryAccess()).getCount() == Integer.parseInt(row[4])
                    && loaded.shapeless() == recipe.shapeless() && loaded.getIngredients().size() == recipe.getIngredients().size(),
                    "Recipe gate/count/cost/shape changed during sync: " + id);
            for (int i = 0; i < 6; i++) {
                int expected = 0;
                if (!row[5].isEmpty()) for (String value : row[5].split(",")) {
                    String[] fields = value.split(":"); if (fields[0].equals(ArcaneModule.PRIMALS[i])) expected = Integer.parseInt(fields[1]);
                }
                helper.assertTrue(loaded.crystalCost(i) == expected, "Original crystal cost changed: " + id + "/" + ArcaneModule.PRIMALS[i]);
            }
            if (row[0].equals("jarvoid")) {
                var jar = new ItemStack(item("jar_normal")); jar.getOrCreateTag().putString("Proof", "synced");
                var container = new net.minecraft.world.SimpleContainer(15); container.setItem(7, jar);
                helper.assertTrue(loaded.assemble(container, helper.getLevel().registryAccess()).getTag().equals(jar.getTag()), "Void NBT behavior lost in recipe sync");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void advancedRecipesRemainLockedWithoutInventedInfernalOrEldritchCompletion(GameTestHelper helper) {
        var bench = bench(helper); var player = player(helper, bench);
        for (String key : new String[]{"BASEALCHEMY", "ESSENTIASMELTER", "WARDEDJARS", "TUBES", "ESSENTIASMELTERTHAUMIUM"}) complete(player, key);
        for (String id : new String[]{"essentiasmeltervoid", "advalchemyconstruct", "smelteraux", "smeltervent", "bellows"}) {
            var recipe = (ArcaneRecipe)helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft", "arcane/" + id)).orElseThrow();
            helper.assertTrue(!recipe.unlocked(player), "Early progression opened original advanced gate: " + id);
        }
        helper.succeed();
    }

    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1)); helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        return (ArcaneWorkbenchBlockEntity)helper.getLevel().getBlockEntity(pos);
    }
    private static ServerPlayer player(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "essentia_arcane"));
        BlockPos pos = bench.getBlockPos(); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); return player;
    }
    private static void complete(ServerPlayer player, String key) { KnowledgeStore.get(player).setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1); }
    private static Item item(String id) { return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id)); }
    private static void fill(ArcaneWorkbenchBlockEntity bench, String[] pattern, Map<Character, Item> key) {
        bench.clearContent();
        for (int y = 0; y < pattern.length; y++) for (int x = 0; x < pattern[y].length(); x++) {
            char symbol = pattern[y].charAt(x); if (symbol != ' ') bench.setItem(x + y * 3, new ItemStack(key.get(symbol)));
        }
    }
    private static void crystals(ArcaneWorkbenchBlockEntity bench, String aspect, int count) {
        for (int i = 0; i < 6; i++) if (ArcaneModule.PRIMALS[i].equals(aspect)) bench.setItem(9 + i, AspectCrystalItem.create(Aspect.getAspect(aspect), count));
    }
    private static void setVis(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench, float amount) { AuraManager.drainVis(helper.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false); AuraManager.addVis(helper.getLevel(), bench.getBlockPos(), amount); }
    private static void assertGridEmpty(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench) { for (int slot = 0; slot < 9; slot++) helper.assertTrue(bench.getItem(slot).isEmpty(), "Unpaid ingredient remains in " + slot); }
}
