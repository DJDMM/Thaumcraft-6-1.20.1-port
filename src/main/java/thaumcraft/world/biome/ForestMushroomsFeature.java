package thaumcraft.world.biome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** BETA26's 4x4 grid, each cell with a separate 1/40 giant-mushroom attempt. */
public final class ForestMushroomsFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<ConfiguredFeature<?, ?>> BROWN = mushroom("huge_brown_mushroom");
    private static final ResourceKey<ConfiguredFeature<?, ?>> RED = mushroom("huge_red_mushroom");
    public ForestMushroomsFeature() { super(NoneFeatureConfiguration.CODEC); }
    private static ResourceKey<ConfiguredFeature<?, ?>> mushroom(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath("minecraft", path));
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var registry = context.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var random = context.random();
        boolean placed = false;
        for (int cx = 0; cx < 4; cx++) for (int cz = 0; cz < 4; cz++) {
            if (random.nextInt(40) != 0) continue;
            int x = context.origin().getX() + cx * 4 + 1 + random.nextInt(3);
            int z = context.origin().getZ() + cz * 4 + 1 + random.nextInt(3);
            BlockPos pos = new BlockPos(x, context.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            var feature = registry.getHolderOrThrow(random.nextBoolean() ? BROWN : RED).value();
            placed |= feature.place(context.level(), context.chunkGenerator(), random, pos);
        }
        return placed;
    }
}
