package thaumcraft.essentia.thaumatorium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;
import thaumcraft.essentia.thaumatorium.ThaumatoriumBlockEntity;

/** Original front output preview cycles one selected result per second; the original OBJ remains baked. */
public final class ThaumatoriumRenderer implements BlockEntityRenderer<ThaumatoriumBlockEntity> {
    public ThaumatoriumRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ThaumatoriumBlockEntity tile,float partialTicks,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(tile.getLevel()==null)return;var output=tile.cyclingOutput(tile.getLevel().getGameTime());if(output.isEmpty())return;
        output.setCount(1);var side=tile.facing();pose.pushPose();pose.translate(.5+side.getStepX()/1.99,1.125,.5+side.getStepZ()/1.99);
        pose.mulPose(Axis.YP.rotationDegrees(switch(side) {case EAST->90;case WEST->270;case NORTH->180;default->0;}));pose.scale(.75f,.75f,.75f);
        Minecraft.getInstance().getItemRenderer().renderStatic(output,ItemDisplayContext.GROUND,light,overlay,pose,buffers,tile.getLevel(),0);pose.popPose();
    }
}
