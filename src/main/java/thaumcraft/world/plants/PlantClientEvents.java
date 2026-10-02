package thaumcraft.world.plants;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.Thaumcraft;

@Mod.EventBusSubscriber(modid = Thaumcraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlantClientEvents {
    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 0 ? level != null && pos != null
                ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.get(.5, 1) : -1, PlantModule.GRASS_AMBIENT.get());
    }
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? GrassColor.get(.5, 1) : -1, PlantModule.GRASS_AMBIENT.get());
    }
}
