package thaumcraft.essentia.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.essentia.EssentiaModule;

@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EssentiaClientEvents {
    private EssentiaClientEvents() {}

    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EssentiaModule.JAR.get(), EssentiaJarRenderer::new);
    }
}
