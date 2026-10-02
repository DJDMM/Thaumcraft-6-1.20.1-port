package thaumcraft.world.feature;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** TC6 places isolated ore blocks rather than vanilla ore veins. */
public final class SingleOreFeature extends Feature<NoneFeatureConfiguration> {
    private final Supplier<Block> block;
    private final boolean nearSurface;

    public SingleOreFeature(Supplier<Block> block, boolean nearSurface) {
        super(NoneFeatureConfiguration.CODEC);
        this.block = block;
        this.nearSurface = nearSurface;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos pos = context.origin();
        if (nearSurface) {
            int top = context.level().getHeight(Heightmap.Types.OCEAN_FLOOR_WG, pos.getX(), pos.getZ());
            pos = new BlockPos(pos.getX(), top - 1 - context.random().nextInt(25), pos.getZ());
        }
        if (context.level().isOutsideBuildHeight(pos) || !context.level().getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD)) return false;
        context.level().setBlock(pos, block.get().defaultBlockState(), 2);
        return true;
    }
}
