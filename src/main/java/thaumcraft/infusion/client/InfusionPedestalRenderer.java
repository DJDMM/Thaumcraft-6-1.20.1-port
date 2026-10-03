package thaumcraft.infusion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.infusion.InfusionModule;
import thaumcraft.infusion.InfusionPedestalBlockEntity;

@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InfusionPedestalRenderer implements BlockEntityRenderer<InfusionPedestalBlockEntity> {
    public InfusionPedestalRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(InfusionModule.PEDESTAL.get(), InfusionPedestalRenderer::new);
    }

    @Override public void render(InfusionPedestalBlockEntity pedestal, float partial, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        var stack = pedestal.getItem(0);
        if (stack.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        float ticks = (minecraft.getCameraEntity() == null ? 0 : minecraft.getCameraEntity().tickCount) + partial;
        var model = minecraft.getItemRenderer().getModel(stack, pedestal.getLevel(), null, 0);
        pose.pushPose();
        pose.translate(.5, .75, .5);
        pose.scale(1.25F, 1.25F, 1.25F);
        pose.mulPose(Axis.YP.rotationDegrees(ticks % 360));
        // Release creates a fresh age-zero EntityItem and renders it with partial0: no animated bob.
        // Its constant origin offset applies before the model's GROUND transform exactly once.
        pose.translate(0, .1F + .25F * model.getTransforms().ground.scale.y(), 0);
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, overlay,
                pose, buffers, pedestal.getLevel(), 0);
        pose.popPose();
    }
}
