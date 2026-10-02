package thaumcraft.world.trees;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The worldgen-only 1/8 spider variant; the four-sapling feature remains ordinary. */
public final class NaturalGreatwoodFeature extends Feature<NoneFeatureConfiguration> {
    public NaturalGreatwoodFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!TreeBiomeRules.allowGreatwood(context.level().getBiome(context.origin()), context.random())) return false;
        var key = context.random().nextInt(8) == 0 ? TreeModule.SPIDER_GREATWOOD_TREE : TreeModule.GREATWOOD_TREE;
        return context.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(key)
                .value().place(context.level(), context.chunkGenerator(), context.random(), context.origin());
    }
}
