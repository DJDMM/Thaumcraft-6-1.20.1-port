package thaumcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.trees.TreeModule;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TreeGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
    private static BlockPos prepareSoil(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(19, 2, 19));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(origin.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
        return origin;
    }
    private static void grow(GameTestHelper helper, BlockPos pos, SaplingBlock sapling) {
        for (int i = 0; i < 2 && helper.getLevel().getBlockState(pos).is(sapling); i++)
            sapling.advanceTree(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), RandomSource.create(127));
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void greatwoodRequiresFourSaplingsAndRestoresBlockedGrowth(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = prepareSoil(helper);
        SaplingBlock sapling = (SaplingBlock) TreeModule.SAPLING_GREATWOOD.get();
        level.setBlockAndUpdate(origin, sapling.defaultBlockState());
        grow(helper, origin, sapling);
        helper.assertTrue(level.getBlockState(origin).is(sapling), "A single greatwood sapling grew");
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) level.setBlockAndUpdate(origin.offset(x, 0, z), sapling.defaultBlockState());
        level.setBlockAndUpdate(origin.above(), Blocks.OBSIDIAN.defaultBlockState());
        grow(helper, origin, sapling);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            helper.assertTrue(level.getBlockState(origin.offset(x, 0, z)).is(sapling), "Blocked tree consumed a sapling");
        helper.assertTrue(level.getBlockState(origin.above()).is(Blocks.OBSIDIAN), "Tree replaced an obstruction");
        level.setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
        grow(helper, origin, sapling);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            helper.assertTrue(level.getBlockState(origin.offset(x, 0, z)).is(TreeModule.LOG_GREATWOOD.get()), "Missing greatwood 2x2 trunk");
        int logs = 0, leaves = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, 0, -12), origin.offset(12, 38, 12))) {
            if (level.getBlockState(pos).is(TreeModule.LOG_GREATWOOD.get())) logs++;
            if (level.getBlockState(pos).is(TreeModule.LEAVES_GREATWOOD.get())) leaves++;
        }
        helper.assertTrue(logs > 60 && leaves > 100, "Greatwood branches/crowns were not generated");
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void silverwoodGrowsCrossTrunkAndSupportedCrown(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = prepareSoil(helper);
        SaplingBlock sapling = (SaplingBlock) TreeModule.SAPLING_SILVERWOOD.get();
        level.setBlockAndUpdate(origin, sapling.defaultBlockState());
        grow(helper, origin, sapling);
        for (BlockPos pos : new BlockPos[]{origin, origin.north(), origin.south(), origin.east(), origin.west()})
            helper.assertTrue(level.getBlockState(pos.above(2)).is(TreeModule.LOG_SILVERWOOD.get()), "Silverwood cross-shaped trunk missing");
        int leaves = 0, supported = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, 0, -5), origin.offset(5, 16, 5))) {
            var state = level.getBlockState(pos);
            if (state.is(TreeModule.LEAVES_SILVERWOOD.get())) {
                leaves++;
                if (state.getValue(LeavesBlock.DISTANCE) < 7) supported++;
                helper.assertTrue(!state.getValue(LeavesBlock.PERSISTENT), "Grown leaves must be decayable");
            }
        }
        helper.assertTrue(leaves > 100 && supported > leaves * 0.9, "Silverwood crown has incorrect leaf distances");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woodLootTagsRecipesAndNaturalGenerationAreRegistered(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (String wood : new String[]{"greatwood", "silverwood"}) {
            var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
            var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            helper.assertTrue(configured.containsKey(id(wood + "_tree")) && placed.containsKey(id(wood + "_tree")), "Tree feature missing");
            var forest = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST).value();
            helper.assertTrue(forest.getGenerationSettings().features().stream().flatMap(set -> set.stream())
                    .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(id(wood + "_tree"))).orElse(false)), "Tree biome modifier missing");
            for (String recipe : new String[]{"plank_" + wood, "slab_" + wood, "stairs_" + wood, "charcoal_from_" + wood})
                helper.assertTrue(level.getRecipeManager().byKey(id(recipe)).isPresent(), "Wood recipe missing: " + recipe);
        }
        for (Block log : new Block[]{TreeModule.LOG_GREATWOOD.get(), TreeModule.LOG_SILVERWOOD.get()}) {
            helper.assertTrue(log.defaultBlockState().is(BlockTags.LOGS) && new ItemStack(log).is(ItemTags.LOGS_THAT_BURN), "Missing log tags");
            var drops = Block.getDrops(log.defaultBlockState(), level, pos, null, null, ItemStack.EMPTY);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(log.asItem()), "Log does not drop itself");
        }
        for (Block slab : new Block[]{TreeModule.SLAB_GREATWOOD.get(), TreeModule.SLAB_SILVERWOOD.get()}) {
            var drops = Block.getDrops(slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), level, pos, null, null, ItemStack.EMPTY);
            helper.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 2 && drops.get(0).is(slab.asItem()), "Double slab does not drop two slabs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leavesDropWithShearsAndSilkTouchAndDecayNaturally(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemStack silk = new ItemStack(Items.DIAMOND_HOE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        for (Block leaves : new Block[]{TreeModule.LEAVES_GREATWOOD.get(), TreeModule.LEAVES_SILVERWOOD.get()}) {
            for (ItemStack tool : new ItemStack[]{new ItemStack(Items.SHEARS), silk}) {
                var drops = Block.getDrops(leaves.defaultBlockState(), level, pos, null, null, tool);
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(leaves.asItem()), "Leaves do not respect shears/silk touch");
            }
            var unsupported = leaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, 7).setValue(LeavesBlock.PERSISTENT, false);
            level.setBlockAndUpdate(pos, unsupported);
            leaves.randomTick(unsupported, level, pos, RandomSource.create(1));
            helper.assertTrue(level.getBlockState(pos).isAir(), "Unsupported leaves did not decay");
            var persistent = unsupported.setValue(LeavesBlock.PERSISTENT, true);
            level.setBlockAndUpdate(pos, persistent);
            leaves.randomTick(persistent, level, pos, RandomSource.create(1));
            helper.assertTrue(level.getBlockState(pos).is(leaves), "Player-placed leaves decayed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalSilverwoodLeavesRestoreVisButPlacedLeavesDoNot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        float originalVis = AuraManager.getVis(level, pos);
        var leaves = TreeModule.LEAVES_SILVERWOOD.get();
        var natural = leaves.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, false);
        try {
            AuraManager.drainVis(level, pos, Float.MAX_VALUE, false);
            AuraManager.addVis(level, pos, 10);
            level.setBlockAndUpdate(pos, natural);
            leaves.randomTick(natural, level, pos, RandomSource.create(1));
            helper.assertTrue(Math.abs(AuraManager.getVis(level, pos) - 10.01F) < 0.0001F, "Natural silverwood did not restore 0.01 vis");
            var persistent = natural.setValue(LeavesBlock.PERSISTENT, true);
            level.setBlockAndUpdate(pos, persistent);
            leaves.randomTick(persistent, level, pos, RandomSource.create(1));
            helper.assertTrue(Math.abs(AuraManager.getVis(level, pos) - 10.01F) < 0.0001F, "Player leaves farmed free vis");
            AuraManager.addVis(level, pos, AuraManager.getAuraBase(level, pos));
            float full = AuraManager.getVis(level, pos);
            leaves.randomTick(natural, level, pos, RandomSource.create(1));
            helper.assertTrue(AuraManager.getVis(level, pos) == full, "Silverwood restored vis above the chunk's base");
        } finally {
            AuraManager.drainVis(level, pos, Float.MAX_VALUE, false);
            AuraManager.addVis(level, pos, originalVis);
        }
        helper.succeed();
    }
}
