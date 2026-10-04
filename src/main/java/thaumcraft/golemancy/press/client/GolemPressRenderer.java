package thaumcraft.golemancy.press.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.catalog.entities.client.ObjMesh;
import thaumcraft.golemancy.press.GolemPressBlock;
import thaumcraft.golemancy.press.GolemPressBlockEntity;

/** Original Wavefront groups/UVs and sin(press)*.625 stroke, including the lava basin. */
public final class GolemPressRenderer implements BlockEntityRenderer<GolemPressBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/blocks/golembuilder.png");
    public GolemPressRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(GolemPressBlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();pose.translate(.5,0,.5);
        var facing=tile.getBlockState().getValue(GolemPressBlock.FACING);
        pose.mulPose(Axis.YP.rotationDegrees(switch(facing) {case EAST->270;case WEST->90;case SOUTH->180;default->0;}));
        var mesh=ObjMesh.getBlock("golembuilder");mesh.render(pose,buffers,TEXTURE,null,light,"table","anvil","box");
        pose.pushPose();pose.translate(0,-Math.sin(Math.toRadians(tile.pressAngle()))*.625,0);
        mesh.render(pose,buffers,TEXTURE,null,light,"press");pose.popPose();
        var lava=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath("minecraft","block/lava_still"));
        var vertex=buffers.getBuffer(RenderType.cutoutMipped());
        // UtilsFX draws a 0..1 quad, then scales .625; its origin is a corner, not the centre.
        float[][] points={{-.3125f,.625f,1.3125f},{.3125f,.625f,1.3125f},{.3125f,.625f,.6875f},{-.3125f,.625f,.6875f}};
        float[] us={lava.getU1(),lava.getU0(),lava.getU0(),lava.getU1()},vs={lava.getV1(),lava.getV1(),lava.getV0(),lava.getV0()};
        for(int i=0;i<4;i++)vertex.vertex(pose.last().pose(),points[i][0],points[i][1],points[i][2]).color(255,255,255,255).uv(us[i],vs[i]).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,1,0).endVertex();
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(GolemPressBlockEntity tile) {return true;}
}
