package thaumcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.world.plants.ForestFloraFeature;
import thaumcraft.world.plants.PlantModule;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class PlantGameTests {
    @GameTest(template = "empty")
    public static void plantSoilsLightSupportAndLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Block plant : new Block[]{PlantModule.SHIMMERLEAF.get(), PlantModule.CINDERPEARL.get(), PlantModule.VISHROOM.get()}) {
            level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
            helper.assertTrue(plant.defaultBlockState().canSurvive(level, pos), "Plant rejected original dirt substrate");
            helper.assertTrue(plant.defaultBlockState().getLightEmission(level, pos) == (plant == PlantModule.CINDERPEARL.get() ? 7 : 6), "Plant light differs from BETA26");
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(plant.defaultBlockState().canSurvive(level, pos) == (plant == PlantModule.VISHROOM.get()),
                    "Forge cave/plains/desert soil rules differ from TC6");
            level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(pos, plant.defaultBlockState());
            level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(level.getBlockState(pos).isAir(), "Unsupported plant remained in world");
            var drops = Block.getDrops(plant.defaultBlockState(), level, pos, null, null, ItemStack.EMPTY);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(plant.asItem()), "Plant does not drop its usable item");
        }
        level.setBlockAndUpdate(pos.below(), Blocks.SAND.defaultBlockState());
        helper.assertTrue(PlantModule.CINDERPEARL.get().defaultBlockState().canSurvive(level, pos), "Cinderpearl rejected desert sand");
        helper.assertTrue(!PlantModule.SHIMMERLEAF.get().defaultBlockState().canSurvive(level, pos), "Shimmerleaf accepted desert sand");
        for (Block soil : new Block[]{Blocks.TERRACOTTA, Blocks.RED_TERRACOTTA}) {
            level.setBlockAndUpdate(pos.below(), soil.defaultBlockState());
            helper.assertTrue(PlantModule.CINDERPEARL.get().defaultBlockState().canSurvive(level, pos), "Cinderpearl rejected terracotta");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void plantConversionsCraftRealOutputsAndAmbientLootUsesSilkTouch(GameTestHelper helper) {
        var level = helper.getLevel();
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 2, 2);
        for (Block plant : new Block[]{PlantModule.SHIMMERLEAF.get(), PlantModule.CINDERPEARL.get()}) {
            grid.clearContent();
            grid.setItem(3, new ItemStack(plant));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
            ItemStack result = recipe.assemble(grid, level.registryAccess());
            var expected = plant == PlantModule.CINDERPEARL.get() ? Items.BLAZE_POWDER
                    : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "quicksilver"));
            helper.assertTrue(result.is(expected) && result.getCount() == 1, "Plant conversion output incorrect");
        }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var state = PlantModule.GRASS_AMBIENT.get().defaultBlockState();
        var drops = Block.getDrops(state, level, pos, null, null, ItemStack.EMPTY);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.DIRT), "Ambient grass normal drop differs");
        ItemStack silk = new ItemStack(Items.DIAMOND_SHOVEL);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        drops = Block.getDrops(state, level, pos, null, null, silk);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(PlantModule.GRASS_AMBIENT.get().asItem()), "Ambient anchor cannot be harvested with silk touch");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void vishroomContactAffectsLivingEntitiesOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var pig = EntityType.PIG.create(level);
        var item = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.STICK));
        var state = PlantModule.VISHROOM.get().defaultBlockState();
        for (int i = 0; i < 100; i++) {
            state.getBlock().entityInside(state, level, pos, pig);
            state.getBlock().entityInside(state, level, pos, item);
        }
        var nausea = pig.getEffect(MobEffects.CONFUSION);
        helper.assertTrue(nausea != null && nausea.getDuration() == 200 && nausea.getAmplifier() == 0, "Vishroom contact did not apply original 200-tick nausea");
        helper.assertTrue(!item.isRemoved() && item.getItem().getCount() == 1, "Vishroom damaged a dropped item");
        helper.succeed();
    }
    @GameTest(template = "trees")
    public static void shimmerleafScatterRespectsTerrainAndObstructions(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(19, 2, 19));
        for (BlockPos soil : BlockPos.betweenClosed(center.offset(-8, -1, -8), center.offset(8, -1, 8)))
            level.setBlockAndUpdate(soil, Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(center, Blocks.OBSIDIAN.defaultBlockState());
        boolean placed = ForestFloraFeature.scatter(level, RandomSource.create(127), center, PlantModule.SHIMMERLEAF.get());
        helper.assertTrue(placed && level.getBlockState(center).is(Blocks.OBSIDIAN), "Scatter failed or destroyed an obstruction");
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            if (level.getBlockState(pos).is(PlantModule.SHIMMERLEAF.get())) {
                count++;
                helper.assertTrue(level.getBlockState(pos).canSurvive(level, pos), "Scatter left a plant without support");
            }
        }
        helper.assertTrue(count > 0 && count <= 18, "Scatter exceeded original attempt count");
        helper.succeed();
    }
}
