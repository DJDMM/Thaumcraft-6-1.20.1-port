package thaumcraft.world.trees;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Global generation gate; saplings and the forest's own decorator use their ungated features. */
public final class NaturalSilverwoodFeature extends Feature<NoneFeatureConfiguration> {
    public NaturalSilverwoodFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TreeBiomeRules.allowSilverwood(context.level().getBiome(context.origin()), context.random())) return false;
        return context.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(TreeModule.SILVERWOOD_TREE).value()
                .place(context.level(), context.chunkGenerator(), context.random(), context.origin());
    }
}
