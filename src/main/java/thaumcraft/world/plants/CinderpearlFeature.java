package thaumcraft.world.plants;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class CinderpearlFeature extends Feature<NoneFeatureConfiguration> {
    public CinderpearlFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        var pos = context.origin();
        if (!level.getBlockState(pos.below()).is(Blocks.SAND) || level.getBiome(pos).value().getBaseTemperature() <= 1.0F) return false;
        return ForestFloraFeature.scatter(level, context.random(), pos, PlantModule.CINDERPEARL.get());
    }
}
