package thaumcraft.essentia.centrifuge.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.essentia.centrifuge.CentrifugeModule;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CentrifugeClientEvents {
    private CentrifugeClientEvents() {}
    @SubscribeEvent public static void registerCentrifugeRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (CentrifugeModule.CENTRIFUGE.isPresent())
            event.registerBlockEntityRenderer(CentrifugeModule.CENTRIFUGE.get(),CentrifugeRenderer::new);
    }
}
