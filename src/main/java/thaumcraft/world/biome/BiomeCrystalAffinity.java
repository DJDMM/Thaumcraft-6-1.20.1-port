package thaumcraft.world.biome;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

/** The BETA26 dictionary-type bias for the three TC6 biomes. */
public final class BiomeCrystalAffinity {
    private static final String[] PRIMALS = {"aer", "ignis", "aqua", "terra", "ordo", "perditio"};
    private BiomeCrystalAffinity() {}
    public static String choose(Holder<Biome> biome, RandomSource random) {
        String ordinary = PRIMALS[random.nextInt(PRIMALS.length)];
        String[] affinity;
        if (biome.is(BiomeModule.MAGICAL_FOREST)) affinity = new String[]{"ordo", "terra"};
        else if (biome.is(BiomeModule.EERIE)) affinity = new String[]{"ordo", "ignis"};
        else if (biome.is(BiomeModule.ELDRITCH)) affinity = new String[]{"ordo", "ignis", "aer"};
        else return ordinary;
        return random.nextInt(3) == 0 ? affinity[random.nextInt(affinity.length)] : ordinary;
    }
}
