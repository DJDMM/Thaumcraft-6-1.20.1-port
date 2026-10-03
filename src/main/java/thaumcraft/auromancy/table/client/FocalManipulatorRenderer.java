package thaumcraft.auromancy.table.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.table.*;

import java.util.Random;

/** Original rotating focus and paid aspect crystals; only vanilla particle sprites are adapted. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FocalManipulatorRenderer implements BlockEntityRenderer<FocalManipulatorBlockEntity> {
    public FocalManipulatorRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FocalManipulatorModule.TABLE.get(), FocalManipulatorRenderer::new);
    }
    @Override public void render(FocalManipulatorBlockEntity table, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        float ticks = (mc.getCameraEntity() == null ? 0 : mc.getCameraEntity().tickCount)+partial;
        ItemStack focus = table.getItem(0);
        if (!focus.isEmpty()) {
            pose.pushPose(); pose.translate(.5,.8,.5); pose.mulPose(Axis.YP.rotationDegrees(ticks%360));
            // Age-zero EntityItem from the release renderer uses hoverStart=sin(t/14)*.2+.2.
            float hover = Mth.sin(ticks/14)*.2F+.2F;
            renderItem(focus,hover,table,pose,buffers,light,overlay); pose.popPose();
        }
        var aspects = table.orbitCrystals(); int count = aspects.size();
        if (count == 0) return;
        float step = 360/count; int index = 0;
        for (String key : aspects.keySet()) {
            Aspect aspect = Aspect.getAspect(key); if (aspect == null) continue;
            float angle = (ticks%720)/2+step*index, bob = Mth.sin((ticks+index*10)/12)*.02F+.02F;
            int color = aspect.getColor(); float r = ((color>>16)&255)/255F,g=((color>>8)&255)/255F,b=(color&255)/255F;
            pose.pushPose(); pose.translate(.5,1.05,.5); pose.mulPose(Axis.YP.rotationDegrees(angle)); pose.translate(0,bob,.4); pose.scale(.5F,.5F,.5F);
            renderRay(angle,index,bob,r,g,b,ticks,pose,buffers);
            renderRay(angle,(index+1)*5,bob,r,g,b,ticks,pose,buffers);
            renderItem(AspectCrystalItem.create(aspect),0,table,pose,buffers,light,overlay);
            pose.popPose(); index++;
        }
    }
    private static void renderItem(ItemStack stack, float hover, FocalManipulatorBlockEntity table, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance(); var model = mc.getItemRenderer().getModel(stack,table.getLevel(),null,0);
        pose.translate(0,Mth.sin(hover)*.1F+.1F+.25F*model.getTransforms().ground.scale.y(),0);
        mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.GROUND,light,overlay,pose,buffers,table.getLevel(),0);
    }
    private static void renderRay(float angle, int number, float lift, float r, float g, float b, float ticks, PoseStack pose, MultiBufferSource buffers) {
        Random random = new Random(187L+number*number);
        float pan = Mth.sin((ticks+number*10)/15)*15, aperture = Mth.sin((ticks+number*10)/14)*2;
        float height = (random.nextFloat()*20+10)/30*(Math.min(ticks,10)/10), width = (random.nextFloat()*4+6+aperture)/30*(Math.min(ticks,10)/10);
        pose.pushPose(); pose.translate(0,.475F+lift,0); pose.mulPose(Axis.XN.rotationDegrees(90)); pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat()*360)); pose.mulPose(Axis.XP.rotationDegrees(pan));
        Matrix4f matrix = pose.last().pose(); VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        float[][] edge = {{-.8F*width,height,-.5F*width},{.8F*width,height,-.5F*width},{0,height,width}};
        // Triangle-fan geometry becomes three independent transparent triangles in the modern QUADS stream.
        for (int i = 0; i < 3; i++) {
            out.vertex(matrix,0,0,0).color(r,g,b,.66F).endVertex();
            out.vertex(matrix,edge[i][0],edge[i][1],edge[i][2]).color(r,g,b,0).endVertex();
            float[] next = edge[(i+1)%3]; out.vertex(matrix,next[0],next[1],next[2]).color(r,g,b,0).endVertex();
            out.vertex(matrix,next[0],next[1],next[2]).color(r,g,b,0).endVertex();
        }
        pose.popPose();
    }
}
