package thaumcraft.world.trees;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.Thaumcraft;

@Mod.EventBusSubscriber(modid = Thaumcraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TreeClientEvents {
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null
                ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(), TreeModule.LEAVES_GREATWOOD.get());
        event.register((state, level, pos, tint) -> 0xFFFFFF, TreeModule.LEAVES_SILVERWOOD.get());
    }
    @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> FoliageColor.getDefaultColor(), TreeModule.LEAVES_GREATWOOD.get());
        event.register((stack, tint) -> 0xFFFFFF, TreeModule.LEAVES_SILVERWOOD.get());
    }
}
