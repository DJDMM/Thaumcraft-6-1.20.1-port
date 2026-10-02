package thaumcraft.world.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.Thaumcraft;
import thaumcraft.world.trees.TreeModule;

/** Preserves the two conditional integer draws in BiomeGenMagicalForest.getRandomTreeFeature. */
public final class MagicalForestTreesFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<ConfiguredFeature<?, ?>> BIG_MAGIC_TREE = key("big_magic_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FOREST_SILVERWOOD = key("forest_silverwood");
    public MagicalForestTreesFeature() { super(NoneFeatureConfiguration.CODEC); }
    private static ResourceKey<ConfiguredFeature<?, ?>> key(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, path));
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> chooseTree(RandomSource random) {
        if (random.nextInt(18) == 0) return FOREST_SILVERWOOD;
        if (random.nextInt(12) == 0) {
            return random.nextInt(8) == 0 ? TreeModule.SPIDER_GREATWOOD_TREE : TreeModule.GREATWOOD_TREE;
        }
        return BIG_MAGIC_TREE;
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var feature = context.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(chooseTree(context.random())).value();
        return feature.place(context.level(), context.chunkGenerator(), context.random(), context.origin());
    }
}
