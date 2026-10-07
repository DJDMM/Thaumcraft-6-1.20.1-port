package thaumcraft.artifice.hungrychest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.artifice.hungrychest.*;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class HungryChestRenderer implements BlockEntityRenderer<HungryChestBlockEntity> {
    private final ModelPart bottom,lid,lock;
    public HungryChestRenderer(BlockEntityRendererProvider.Context context) {this(context.bakeLayer(ModelLayers.CHEST));}
    public HungryChestRenderer(ModelPart root) {bottom=root.getChild("bottom");lid=root.getChild("lid");lock=root.getChild("lock");}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {event.registerBlockEntityRenderer(HungryChestModule.HUNGRY_CHEST.get(),HungryChestRenderer::new);}
    @Override public void render(HungryChestBlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        draw(tile.getBlockState().getValue(HungryChestBlock.FACING).toYRot(),tile.getOpenNess(partial),pose,buffers,light,overlay);
    }
    public void draw(float yaw,float open,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(-yaw));pose.translate(-.5,-.5,-.5);
        // Modern native chest layer has the same 64x64 single-chest UV layout as ModelChest.
        float eased=1-(1-open)*(1-open)*(1-open);lid.xRot=-(eased*(float)Math.PI/2);lock.xRot=lid.xRot;
        var vertices=buffers.getBuffer(RenderType.entityCutout(ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/models/chesthungry.png")));
        bottom.render(pose,vertices,light,overlay);lid.render(pose,vertices,light,overlay);lock.render(pose,vertices,light,overlay);pose.popPose();
    }
}
