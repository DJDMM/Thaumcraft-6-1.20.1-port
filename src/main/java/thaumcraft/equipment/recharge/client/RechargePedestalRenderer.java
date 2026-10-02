package thaumcraft.equipment.recharge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.equipment.recharge.*;

@Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RechargePedestalRenderer implements BlockEntityRenderer<RechargePedestalBlockEntity> {
    public RechargePedestalRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(RechargeModule.PEDESTAL.get(),RechargePedestalRenderer::new); }
    @Override public void render(RechargePedestalBlockEntity pedestal,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var stack=pedestal.getItem(0);
        if(stack.isEmpty()) return;
        float ticks=(Minecraft.getInstance().getCameraEntity()==null ? 0 : Minecraft.getInstance().getCameraEntity().tickCount)+partial;
        pose.pushPose();
        pose.translate(.5,.75,.5);pose.scale(1.5F,1.5F,1.5F);
        pose.mulPose(Axis.YP.rotationDegrees(ticks%360));
        // EntityItem's origin offset, then the model's GROUND transform exactly once.
        var model=Minecraft.getInstance().getItemRenderer().getModel(stack,pedestal.getLevel(),null,0);
        pose.translate(0,.1F+.25F*model.getTransforms().ground.scale.y(),0);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.GROUND,light,overlay,pose,buffers,pedestal.getLevel(),0);
        pose.popPose();
    }
}
