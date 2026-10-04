package thaumcraft.auromancy.remaining.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.auromancy.remaining.RemainingEffectsModule;
import thaumcraft.auromancy.remaining.RiftHoleBlockEntity;

/** Same original end-portal texture/opaque-neighbor faces, through the modern star shader. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RiftHoleRenderer extends TheEndPortalRenderer<RiftHoleBlockEntity> {
    public RiftHoleRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    @Override protected float getOffsetUp() { return .999F; }
    @Override protected float getOffsetDown() { return .001F; }
    @SubscribeEvent public static void registerRiftHoleRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RemainingEffectsModule.HOLE.get(), RiftHoleRenderer::new);
        event.registerEntityRenderer(RemainingEffectsModule.SPECIAL_ITEM.get(), net.minecraft.client.renderer.entity.ItemEntityRenderer::new);
    }
}
