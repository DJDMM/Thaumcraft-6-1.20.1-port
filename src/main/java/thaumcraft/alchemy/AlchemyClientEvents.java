package thaumcraft.alchemy;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AlchemyClientEvents {
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AlchemyModule.ALUMENTUM_ENTITY.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level == null || pos == null ? 0x3f76e4 : BiomeColors.getAverageWaterColor(level, pos), AlchemyModule.CRUCIBLE.get());
    }
}
