package thaumcraft.research;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.CatalogModule;

/** Full book data is valid without installing future crafting operations or awarding research. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class BookRecipeCatalogGameTests {
    private BookRecipeCatalogGameTests() {}

    @GameTest(template = "empty")
    public static void allOriginalRecipeKeysAreClassifiedIncludingOriginalMissingAndBlueprints(GameTestHelper helper) {
        helper.assertTrue(BookRecipeCatalog.referencedKeys().size() == 203, "The book recipe-key inventory is incomplete");
        int missing = 0, blueprint = 0;
        for (String key : BookRecipeCatalog.referencedKeys()) {
            String status = BookRecipeCatalog.originalStatus(key);
            if (status.equals("original_unregistered")) missing++;
            else if (status.equals("blueprint")) blueprint++;
            else helper.assertTrue(!BookRecipeCatalog.definitions(key).isEmpty(), "Missing display recipe/group " + key);
        }
        helper.assertTrue(missing == 4 && blueprint == 6, "Original absent recipe names were guessed into gameplay");
        for (String key : new String[]{"JarLabelEssence", "arcane_brick", "arcane_stone", "nitorcolor"})
            helper.assertTrue(BookRecipeCatalog.originalStatus("thaumcraft:" + key).equals("original_unregistered"), "Original null recipe identity changed: " + key);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void all375DisplaysResolveRegisteredItemsAndTypedNbt(GameTestHelper helper) {
        helper.assertTrue(BookRecipeCatalog.recipeCount() == 375, "Finite original book variants are incomplete");
        for (var definition : BookRecipeCatalog.allDefinitions()) {
            helper.assertTrue(!definition.output().isEmpty(), "Unregistered reference output " + definition.id());
            var ingredients = definition.ingredients();
            helper.assertTrue(!ingredients.isEmpty(), "Missing ingredients " + definition.id());
            definition.aspects(); definition.crystals();
            for (var ingredient : ingredients) for (ItemStack stack : ingredient.getItems())
                helper.assertTrue(!stack.isEmpty(), "Invalid display ingredient " + definition.id());
            if (definition.kind().equals("arcane") || definition.kind().equals("crafting") || definition.kind().equals("salis"))
                helper.assertTrue(definition.width() * definition.height() >= ingredients.size()
                                && definition.width() <= 3 && definition.height() <= 3,
                        "Original grid dimensions were lost: " + definition.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lateArcaneCostsAndGatesRemainOriginalReadOnly(GameTestHelper helper) {
        var recipe = get("EssentiaSmelterVoid");
        helper.assertTrue(recipe.vis() == 750 && recipe.crystals()[1] == 3 && recipe.research().equals("ESSENTIASMELTERVOID"), "Void smelter reference costs changed");
        var matrix = get("InfusionMatrix");
        helper.assertTrue(matrix.vis() == 150 && matrix.research().equals("INFUSION@2")
                && java.util.Arrays.stream(matrix.crystals()).allMatch(value -> value == 1), "Original infusion matrix gate/cost changed");
        var player = new PlayerKnowledge(); var before = player.save();
        BookRecipeCatalog.allDefinitions().forEach(definition -> { definition.output(); definition.ingredients(); });
        helper.assertTrue(player.save().equals(before) && !player.knowsResearch("INFUSION"), "Reading the recipe catalogue awarded research");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(recipe.id()).isEmpty()
                        || helper.getLevel().getRecipeManager().byKey(recipe.id()).get() instanceof thaumcraft.arcane.ArcaneRecipe,
                "A reference display was registered as a gameplay operation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void infusionOutputsPreserveOriginalMasksPotionAndEnchantTags(GameTestHelper helper) {
        ItemStack helm = get("HelmGoggles").output();
        helper.assertTrue(helm.getTag().contains("goggles", Tag.TAG_BYTE) && helm.getTag().getByte("goggles") == 1, "Goggles mutation lost original byte NBT");
        ItemStack mask = get("MaskAngryGhost").output();
        helper.assertTrue(mask.getTag().contains("mask", Tag.TAG_INT) && mask.getTag().getInt("mask") == 1, "Mask mutation lost original integer NBT");
        var life = get("VerdantHeartLife");
        helper.assertTrue(life.output().getTag().contains("type", Tag.TAG_BYTE) && life.output().getTag().getByte("type") == 1, "Verdant heart type changed");
        ItemStack potion = life.ingredients().get(3).getItems()[0];
        helper.assertTrue(potion.is(Items.POTION) && potion.getTag().getString("Potion").equals("minecraft:strong_healing"), "Original exact healing potion was flattened");
        var axe = get("ElementalAxe").output().getTag().getList("infench", Tag.TAG_COMPOUND);
        helper.assertTrue(axe.size() == 2 && axe.getCompound(0).getShort("id") == 0 && axe.getCompound(1).getShort("id") == 2,
                "Original default elemental axe enchants changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dynamicRunicExamplesUseReleaseCostsAndIndependentNbt(GameTestHelper helper) {
        int[] costs = {40,60,100}; int[] instability = {5,5,6};
        for (int level = 0; level < 3; level++) {
            var recipe = get("RunicArmorFake" + level);
            helper.assertTrue(recipe.aspects().getAmount(Aspect.PROTECT) == costs[level]
                    && recipe.aspects().getAmount(Aspect.CRYSTAL) == costs[level] / 2
                    && recipe.instability() == instability[level] && recipe.ingredients().size() == level + 3,
                    "Dynamic runic cost/amber example incorrect for " + level);
            ItemStack central = recipe.ingredients().get(0).getItems()[0];
            helper.assertTrue((central.hasTag() ? central.getTag().getByte("TC.RUNIC") : 0) == level
                    && recipe.output().getTag().getByte("TC.RUNIC") == level+1, "Runic central/output charge example changed");
            recipe.output().getTag().putByte("TC.RUNIC", (byte)99);
            helper.assertTrue(recipe.output().getTag().getByte("TC.RUNIC") == level+1, "Book output exposed the shared reference NBT");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalGroupsAndNbtCrystalsAreCompleteAndDistinct(GameTestHelper helper) {
        helper.assertTrue(BookRecipeCatalog.definitions("thaumcraft:viscrystalgroup").size() == 37
                        && BookRecipeCatalog.definitions("thaumcraft:Banners").size() == 16
                        && BookRecipeCatalog.definitions("thaumcraft:tallowcandles").size() == 17
                        && BookRecipeCatalog.definitions("thaumcraft:inkwell").size() == 3,
                "Original group variants were collapsed");
        java.util.Set<Aspect> aspects = new java.util.HashSet<>();
        for (var recipe : BookRecipeCatalog.definitions("thaumcraft:viscrystalgroup")) {
            Aspect aspect = CatalogModule.containedAspect(recipe.output());
            helper.assertTrue(aspect != null && recipe.aspects().getAmount(aspect) == 2, "Crystal output/cost aspect mismatch");
            aspects.add(aspect);
        }
        helper.assertTrue(aspects.size() == 37, "NBT-aware crystal variants were duplicated");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseHedgeDifferencesAndPhialRequirementsAreExact(GameTestHelper helper) {
        var clay = get("hedge_clay");
        helper.assertTrue(clay.aspects().size() == 1 && clay.aspects().getAmount(Aspect.WATER) == 5, "Clay minus dirt cost was guessed");
        var web = get("hedge_web");
        helper.assertTrue(web.aspects().size() == 1 && web.aspects().getAmount(Aspect.TRAP) == 5, "Web minus string cost was guessed");
        ItemStack catalyst = get("BottleTaint").ingredients().get(0).getItems()[0];
        helper.assertTrue(CatalogModule.containedAspect(catalyst) == Aspect.FLUX
                && catalyst.getTag().getList("Aspects", Tag.TAG_COMPOUND).getCompound(0).getInt("amount") == 10,
                "Bottle of taint's exact full flux phial NBT was lost");
        helper.succeed();
    }
    private static BookRecipeCatalog.Definition get(String path) { return BookRecipeCatalog.definitions("thaumcraft:"+path).get(0); }
}
