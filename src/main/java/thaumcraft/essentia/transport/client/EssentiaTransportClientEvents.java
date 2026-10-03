package thaumcraft.essentia.transport.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.transport.*;

@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EssentiaTransportClientEvents {
    private EssentiaTransportClientEvents() {}
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EssentiaTransportModule.TUBE.get(), TubeRenderer::new);
    }
    @SubscribeEvent public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, index) -> index == 1 && level != null && pos != null
                && level.getBlockEntity(pos) instanceof TubeFilterBlockEntity filter && filter.filter() != null
                ? filter.filter().getColor() : 0xFFFFFF, CatalogBlocks.block("tube_filter"));
    }
}
