package thaumcraft.world.biome;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Observes the actual renderer for the opt-in biome smoke, without changing fog.
 * BETA26's RenderEventHandler density callback is a warp effect, not a biome effect;
 * biome grass, foliage, water, sky and fog colours are provided by the biome data.
 */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class BiomeClientEvents {
    private static ResourceLocation colourBiome, distanceBiome;
    private static float red, green, blue, near, far;
    private static FogType medium;
    private static long colourFrames, distanceFrames;

    private BiomeClientEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void colours(ViewportEvent.ComputeFogColor event) {
        if (!Boolean.getBoolean("thaumcraft.biomeSmokeTest")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        colourBiome = minecraft.level.getBiome(BlockPos.containing(event.getCamera().getPosition()))
                .unwrapKey().map(key -> key.location()).orElse(null);
        red = event.getRed(); green = event.getGreen(); blue = event.getBlue();
        colourFrames++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void distances(ViewportEvent.RenderFog event) {
        if (!Boolean.getBoolean("thaumcraft.biomeSmokeTest")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        distanceBiome = minecraft.level.getBiome(BlockPos.containing(event.getCamera().getPosition()))
                .unwrapKey().map(key -> key.location()).orElse(null);
        near = event.getNearPlaneDistance(); far = event.getFarPlaneDistance();
        medium = event.getType(); distanceFrames++;
    }

    static Observation observation() {
        return new Observation(colourBiome, distanceBiome, red, green, blue, near, far, medium, colourFrames, distanceFrames);
    }

    record Observation(ResourceLocation colourBiome, ResourceLocation distanceBiome,
                       float red, float green, float blue, float near, float far,
                       FogType medium, long colourFrames, long distanceFrames) {
        boolean finite() {
            return Float.isFinite(red) && Float.isFinite(green) && Float.isFinite(blue)
                    && red >= 0 && red <= 1 && green >= 0 && green <= 1 && blue >= 0 && blue <= 1
                    && Float.isFinite(near) && Float.isFinite(far) && far > near && medium == FogType.NONE;
        }
    }
}
