package thaumcraft.world.trees;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import thaumcraft.world.biome.BiomeModule;

/** Explicit modern adaptation of BETA26's biome-type support gate, before attempting a tree. */
public final class TreeBiomeRules {
    private static final TagKey<Biome> FULL = tag("tree_support/full");
    private static final TagKey<Biome> LUSH = tag("tree_support/lush");
    private static final TagKey<Biome> REDUCED = tag("tree_support/reduced");
    private static final TagKey<Biome> MAGICAL = tag("magical");

    private TreeBiomeRules() {}
    private static TagKey<Biome> tag(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thaumcraft", path));
    }

    public static float greatwoodSupport(Holder<Biome> biome) {
        if (biome.is(MAGICAL) || biome.is(FULL)) return 1.0F;
        if (biome.is(LUSH)) return 0.5F;
        if (biome.is(REDUCED)) return 0.2F;
        return 0.0F;
    }

    public static boolean allowGreatwood(Holder<Biome> biome, RandomSource random) {
        return greatwoodSupport(biome) > random.nextFloat();
    }

    public static boolean allowSilverwood(Holder<Biome> biome, RandomSource random) {
        // The original consumes this draw even when a later magical exception succeeds.
        return greatwoodSupport(biome) / 2.0F > random.nextFloat()
                || biome.is(MAGICAL) && !biome.is(BiomeModule.MAGICAL_FOREST);
    }
}
