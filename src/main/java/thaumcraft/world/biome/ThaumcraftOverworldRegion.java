package thaumcraft.world.biome;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils.Temperature;
import terrablender.api.Region;
import terrablender.api.RegionType;
import thaumcraft.Thaumcraft;

/** Modern climate adaptation of TC6's warm/cool biome-list entry with default weight five. */
public final class ThaumcraftOverworldRegion extends Region {
    public ThaumcraftOverworldRegion() {
        super(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, "overworld"), RegionType.OVERWORLD, 5);
    }

    @Override public void addBiomes(Registry<Biome> registry,
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        // A complete vanilla palette keeps this weighted region from filling every
        // climate slot with Magical Forest. Eerie and Outer Lands are never added.
        addModifiedVanillaOverworldBiomes(pair -> {
            var biome = pair.getSecond();
            if (!biome.equals(Biomes.FOREST) && !biome.equals(Biomes.BIRCH_FOREST)) {
                mapper.accept(pair);
                return;
            }
            var point = pair.getFirst();
            // Split broad vanilla temperature intervals rather than changing their
            // neutral/frozen/hot portions along with the two TC6 climate groups.
            for (var band : new Temperature[] {Temperature.ICY, Temperature.COOL,
                    Temperature.NEUTRAL, Temperature.WARM, Temperature.HOT}) {
                long min = Math.max(point.temperature().min(), band.parameter().min());
                long max = Math.min(point.temperature().max(), band.parameter().max());
                if (min >= max) continue;
                var parameters = new Climate.ParameterPoint(new Climate.Parameter(min, max),
                        point.humidity(), point.continentalness(), point.erosion(),
                        point.depth(), point.weirdness(), point.offset());
                mapper.accept(Pair.of(parameters,
                        band == Temperature.WARM || band == Temperature.COOL
                                ? BiomeModule.MAGICAL_FOREST : biome));
            }
        }, builder -> {});
    }
}
