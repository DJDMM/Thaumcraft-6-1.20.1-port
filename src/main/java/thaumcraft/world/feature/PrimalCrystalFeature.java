package thaumcraft.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.world.WorldModule;

/** Small primal crystal groups attached to exposed underground stone. */
public final class PrimalCrystalFeature extends Feature<NoneFeatureConfiguration> {
    public PrimalCrystalFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        String aspect = thaumcraft.world.biome.BiomeCrystalAffinity.choose(
                context.level().getBiome(new BlockPos(context.origin().getX(), 64, context.origin().getZ())), context.random());
        Block crystal = WorldModule.CRYSTALS.get(aspect).get();
        boolean placed = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (context.random().nextInt(3) == 0) continue;
                    BlockPos pos = context.origin().offset(x, y, z);
                    if (!context.level().isEmptyBlock(pos)) continue;
                    int start = context.random().nextInt(6);
                    Direction[] directions = Direction.values();
                    for (int face = 0; face < 6; face++) {
                        Direction outward = directions[(start + face) % 6];
                        BlockPos support = pos.relative(outward.getOpposite());
                        if (!context.level().getBlockState(support).is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                        BlockState state = crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, outward);
                        if (state.canSurvive(context.level(), pos)) {
                            context.level().setBlock(pos, state, 2);
                            placed = true;
                            break;
                        }
                    }
                }
            }
        }
        return placed;
    }
}
