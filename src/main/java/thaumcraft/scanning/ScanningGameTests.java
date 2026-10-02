package thaumcraft.scanning;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.research.KnowledgeStore;

import java.util.UUID;
import java.util.List;
import java.util.HashSet;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ScanningGameTests {
    @GameTest(template = "empty")
    public static void originalDefinitionsAndSurvivalFuelsAreAvailable(GameTestHelper helper) {
        helper.assertTrue(AspectRegistry.definitionCount() >= 300, "Bundled aspect definitions did not load");
        assertAmount(helper, Items.COAL, Aspect.FIRE, 10);
        assertAmount(helper, Items.CHARCOAL, Aspect.ENERGY, 10);
        assertAmount(helper, Items.GLOWSTONE_DUST, Aspect.LIGHT, 10);
        assertAmount(helper, Items.WHEAT, Aspect.LIFE, 5);
        assertAmount(helper, Items.OAK_LOG, Aspect.PLANT, 20);
        var trees = new net.minecraft.world.level.block.Block[]{
                thaumcraft.world.trees.TreeModule.LOG_GREATWOOD.get(),
                thaumcraft.world.trees.TreeModule.LOG_SILVERWOOD.get(),
                thaumcraft.world.trees.TreeModule.SAPLING_GREATWOOD.get(),
                thaumcraft.world.trees.TreeModule.SAPLING_SILVERWOOD.get()};
        for (int i = 0; i < trees.length; i++) {
            AspectList treeAspects = AspectRegistry.getAspects(new ItemStack(trees[i]));
            Aspect special = i % 2 == 0 ? Aspect.LIFE : Aspect.AURA;
            Aspect excluded = i % 2 == 0 ? Aspect.AURA : Aspect.LIFE;
            helper.assertTrue(treeAspects.size() == 2 && treeAspects.getAmount(Aspect.PLANT) == (i < 2 ? 20 : 15)
                            && treeAspects.getAmount(special) == 5 && treeAspects.getAmount(excluded) == 0,
                    "Magical wood lost its original aspects to a vanilla tag fallback: " + trees[i]);
        }
        helper.assertTrue(AspectRegistry.getAspects(new ItemStack(Items.WOODEN_SHOVEL)).getAmount(Aspect.TOOL) >= 4,
                "Early-game source of Instrumentum missing");
        helper.assertTrue(AspectRegistry.getAspects(new ItemStack(Items.REDSTONE)).getAmount(Aspect.ENERGY) > 0,
                "Redstone aspect tag missing");
        helper.assertTrue(AspectRegistry.getAspects(Blocks.FIRE.defaultBlockState()).getAmount(Aspect.FIRE) == 20,
                "Non-item fire block cannot be scanned");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingDerivationUsesYieldAndReturnsIndependentCopies(GameTestHelper helper) {
        assertAmount(helper, Items.OAK_PLANKS, Aspect.PLANT, 3);
        assertAmount(helper, Items.STICK, Aspect.PLANT, 1);
        assertAmount(helper, Items.TORCH, Aspect.LIGHT, 5);
        assertAmount(helper, Items.TORCH, Aspect.FIRE, 1);
        AspectList first = AspectRegistry.getAspects(new ItemStack(Items.COAL));
        first.remove(Aspect.FIRE);
        first.add(Aspect.MAGIC, 400);
        AspectList again = AspectRegistry.getAspects(new ItemStack(Items.COAL, 64));
        helper.assertTrue(again.getAmount(Aspect.FIRE) == 10 && again.getAmount(Aspect.MAGIC) == 0,
                "A caller or stack count mutated cached per-item aspects");
        helper.assertTrue(AspectRegistry.getAspects(ItemStack.EMPTY).size() == 0, "Empty stack has aspects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void countDamageAndCustomNbtCannotFarmDiscoveries(GameTestHelper helper) {
        var store = new KnowledgeStore();
        UUID player = UUID.randomUUID();
        ItemStack coal = new ItemStack(Items.COAL);
        var first = ThaumometerItem.itemTarget(coal, Vec3.ZERO);
        helper.assertTrue(store.recordScan(player, first.key(), first.aspects()), "First discovery rejected");
        coal.setCount(64);
        coal.setHoverName(Component.literal("Different label"));
        coal.getOrCreateTag().putString("Unrelated", "arbitrary data");
        var changed = ThaumometerItem.itemTarget(coal, Vec3.ZERO);
        helper.assertTrue(first.key().equals(changed.key()) && !store.recordScan(player, changed.key(), changed.aspects()),
                "Count, rename or arbitrary NBT gave duplicate discovery");
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        var undamaged = ThaumometerItem.itemTarget(sword, Vec3.ZERO);
        store.recordScan(player, undamaged.key(), undamaged.aspects());
        sword.setDamageValue(100);
        var damaged = ThaumometerItem.itemTarget(sword, Vec3.ZERO);
        helper.assertTrue(undamaged.key().equals(damaged.key()) && !store.recordScan(player, damaged.key(), damaged.aspects()),
                "Tool damage gave duplicate discovery");
        sword.enchant(Enchantments.SHARPNESS, 1);
        var enchanted = ThaumometerItem.itemTarget(sword, Vec3.ZERO);
        helper.assertTrue(enchanted.aspects().getAmount(Aspect.MAGIC) > 0 && store.recordScan(player, enchanted.key(), enchanted.aspects()),
                "Meaningful enchantment variant could not be discovered");
        helper.assertTrue(store.get(player).scanCount() == 3, "Unexpected discovery count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void largeAspectVariantsRemainDistinctAndPersistent(GameTestHelper helper) {
        AspectList many = new AspectList();
        for (Aspect aspect : Aspect.aspects.values()) many.add(aspect, 500);
        String first = ThaumometerItem.scanKey("item:minecraft:enchanted_book", many);
        helper.assertTrue(first.length() <= 256, "Composed discovery key exceeds the knowledge-store limit");
        AspectList reverse = new AspectList();
        Aspect[] ordered = many.getAspectsSortedByName();
        for (int i = ordered.length - 1; i >= 0; i--) reverse.add(ordered[i], 500);
        helper.assertTrue(first.equals(ThaumometerItem.scanKey("item:minecraft:enchanted_book", reverse)), "Aspect order changes discovery identity");
        many.remove(Aspect.AIR, 1);
        String second = ThaumometerItem.scanKey("item:minecraft:enchanted_book", many);
        helper.assertTrue(!first.equals(second), "Different large variants collapsed to one discovery");
        UUID player = UUID.randomUUID();
        KnowledgeStore store = new KnowledgeStore();
        helper.assertTrue(store.recordScan(player, first, reverse), "Large variant discovery rejected");
        KnowledgeStore restored = KnowledgeStore.load(store.save(new CompoundTag()));
        helper.assertTrue(!restored.recordScan(player, first, reverse) && restored.recordScan(player, second, many),
                "Hashed variants lost identity through NBT persistence");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void targetComesFromServerRayAndOppositeHand(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "scanner_test"));
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 0));
        player.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        player.setYRot(0); player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL, 64));
        player.setShiftKeyDown(true);
        var held = ThaumometerItem.findTarget(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(held != null && held.key().startsWith("item:minecraft:coal"), "Sneak scan ignored server offhand stack");
        player.setShiftKeyDown(false);
        BlockPos wall = start.above().south(2);
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        var block = ThaumometerItem.findTarget(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(block != null && block.key().startsWith("item:minecraft:stone"), "Server ray did not select blocking stone");
        level.removeBlock(wall, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cyclicRecipesDoNotCreateAspectsOrOverflowTheStack(GameTestHelper helper) {
        RecipeManager manager = new RecipeManager();
        manager.replaceRecipes(List.of(
                new ShapelessRecipe(ResourceLocation.fromNamespaceAndPath("thaumcraft", "test_cycle_a"), "", CraftingBookCategory.MISC,
                        new ItemStack(Items.BRICK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.NETHER_BRICK))),
                new ShapelessRecipe(ResourceLocation.fromNamespaceAndPath("thaumcraft", "test_cycle_b"), "", CraftingBookCategory.MISC,
                        new ItemStack(Items.NETHER_BRICK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.BRICK)))));
        AspectRegistry.Snapshot data = new AspectRegistry.Snapshot(manager, helper.getLevel().registryAccess());
        AspectRegistry.ResolutionBudget budget = new AspectRegistry.ResolutionBudget();
        var path = new HashSet<ResourceLocation>();
        helper.assertTrue(data.resolve(new ItemStack(Items.BRICK), path, 0, budget).size() == 0,
                "Circular recipes manufactured aspects without a known input");
        helper.assertTrue(path.isEmpty() && !budget.exhausted, "Cycle was not stopped promptly or contaminated the next resolution");
        helper.succeed();
    }

    private static void assertAmount(GameTestHelper helper, net.minecraft.world.item.Item item, Aspect aspect, int expected) {
        int actual = AspectRegistry.getAspects(new ItemStack(item)).getAmount(aspect);
        helper.assertTrue(actual == expected, item + " expected " + expected + " " + aspect.getTag() + ", got " + actual);
    }
}
