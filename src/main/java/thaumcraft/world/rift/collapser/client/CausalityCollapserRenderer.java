package thaumcraft.world.rift.collapser.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.world.rift.collapser.CausalityCollapserEntity;

/** BETA26 has no projectile mesh; its trail is emitted by the client entity tick. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CausalityCollapserRenderer extends EntityRenderer<CausalityCollapserEntity> {
    public CausalityCollapserRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(CausalityCollapserEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png");
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VisualEntitiesModule.CAUSALITY_COLLAPSER.get(), CausalityCollapserRenderer::new);
    }
}
