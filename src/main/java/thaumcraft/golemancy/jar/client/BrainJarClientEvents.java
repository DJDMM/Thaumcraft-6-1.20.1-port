package thaumcraft.golemancy.jar.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.golemancy.jar.BrainJarModule;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class BrainJarClientEvents {
    private BrainJarClientEvents() {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BrainJarModule.BRAIN_JAR.get(),BrainJarRenderer::new);
    }
}
