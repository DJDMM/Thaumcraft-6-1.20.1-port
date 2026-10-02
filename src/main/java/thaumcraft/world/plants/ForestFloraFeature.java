package thaumcraft.world.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Runs after the forest trees, retaining TC6's wood-adjacent vishrooms. */
public final class ForestFloraFeature extends Feature<NoneFeatureConfiguration> {
    public ForestFloraFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        var random = context.random();
        var base = context.origin();
        boolean placed = false;
        for (int i = 0; i < 3; i++) {
            BlockPos ground = surface(level, base.getX() + 4 + random.nextInt(8), base.getZ() + 4 + random.nextInt(8)).below();
            if (level.ensureCanWrite(ground) && level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
                level.setBlock(ground, PlantModule.GRASS_AMBIENT.get().defaultBlockState(), 2);
                placed = true;
                break;
            }
        }
        for (int i = 0; i < 8; i++) {
            BlockPos pos = surface(level, base.getX() + random.nextInt(16), base.getZ() + random.nextInt(16));
            if (level.ensureCanWrite(pos) && level.isEmptyBlock(pos)
                    && (level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
                    || level.getBlockState(pos.below()).is(PlantModule.GRASS_AMBIENT.get())) && nearWood(level, pos)
                    && PlantModule.VISHROOM.get().defaultBlockState().canSurvive(level, pos)) {
                level.setBlock(pos, PlantModule.VISHROOM.get().defaultBlockState(), 2);
                placed = true;
            }
        }
        return placed;
    }
    private static BlockPos surface(WorldGenLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        // Find the ground beneath vegetation and the branched tree canopy.
        BlockPos pos = new BlockPos(x, y, z);
        while (pos.getY() > level.getMinBuildHeight() + 1) {
            var soil = level.getBlockState(pos.below());
            if (soil.is(Blocks.GRASS_BLOCK) || soil.is(PlantModule.GRASS_AMBIENT.get())) return pos;
            if (!soil.canBeReplaced() && !soil.is(BlockTags.LEAVES) && !soil.is(BlockTags.LOGS)) break;
            pos = pos.below();
        }
        return pos;
    }
    private static boolean nearWood(WorldGenLevel level, BlockPos pos) {
        for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
            if (!neighbor.equals(pos) && level.getBlockState(neighbor).is(BlockTags.LOGS)) return true;
        return false;
    }
    public static boolean scatter(WorldGenLevel level, RandomSource random, BlockPos center, Block plant) {
        boolean placed = false;
        for (int i = 0; i < 18; i++) {
            BlockPos pos = center.offset(random.nextInt(8) - random.nextInt(8), random.nextInt(4) - random.nextInt(4),
                    random.nextInt(8) - random.nextInt(8));
            if (!level.ensureCanWrite(pos) || !level.isEmptyBlock(pos)) continue;
            var soil = level.getBlockState(pos.below());
            var state = plant.defaultBlockState();
            if ((soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.SAND) || soil.is(PlantModule.GRASS_AMBIENT.get())) && state.canSurvive(level, pos)) {
                level.setBlock(pos, state, 2);
                placed = true;
            }
        }
        return placed;
    }
}
