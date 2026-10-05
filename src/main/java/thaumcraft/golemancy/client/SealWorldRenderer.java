package thaumcraft.golemancy.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.golemancy.seals.core.*;

/** Original hand-gated 16-block seals, rotating area markers and corner-only area boxes. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class SealWorldRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(16384));
    private static final ResourceLocation MIDDLE=id("textures/misc/seal_area.png"),CORNER=id("textures/misc/frame_corner.png");
    private static final Direction[][] FACES={{Direction.DOWN,Direction.NORTH,Direction.WEST},{Direction.UP,Direction.NORTH,Direction.WEST},{Direction.DOWN,Direction.NORTH,Direction.EAST},{Direction.UP,Direction.NORTH,Direction.EAST},{Direction.DOWN,Direction.SOUTH,Direction.EAST},{Direction.UP,Direction.SOUTH,Direction.EAST},{Direction.DOWN,Direction.SOUTH,Direction.WEST},{Direction.UP,Direction.SOUTH,Direction.WEST}};
    private static final int[][] ROTATIONS={{0,270,0},{270,180,270},{90,0,90},{180,90,180},{180,180,0},{90,270,270},{270,90,90},{0,0,180}};
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("thaumcraft",path);}
    public static boolean displays(ItemStack stack){var key=ForgeRegistries.ITEMS.getKey(stack.getItem());return !stack.isEmpty()&&key!=null&&key.getNamespace().equals("thaumcraft")&&(key.getPath().equals("golem")||key.getPath().equals("golem_bell")||key.getPath().startsWith("seal_"));}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||mc.player==null||mc.level==null||!displays(mc.player.getMainHandItem())&&!displays(mc.player.getOffhandItem()))return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();boolean through=mc.player.isShiftKeyDown();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        float age=mc.player.tickCount+event.getPartialTick();
        for(SealData seal:SealClientState.seals()){
            var pos=seal.position().pos();double distance=mc.player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);if(distance>256||!mc.level.hasChunkAt(pos))continue;
            float alpha=1-(float)(distance/256);boolean stopped=seal.redstone()&&(mc.level.hasNeighborSignal(pos)||mc.level.hasNeighborSignal(pos.relative(seal.position().face())));
            String icon=seal.type().substring(seal.type().indexOf(':')+1);if(icon.equals("provide"))icon="provider";
            var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id("items/seals/seal_"+icon));
            pose.pushPose();pose.translate(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);face(pose,seal.position().face());pose.translate(0,0,.55);
            quad(pose,TextureAtlas.LOCATION_BLOCKS,through,.5F,stopped?.5F:1,stopped?.5F:1,stopped?.5F:1,alpha,sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1());pose.popPose();
            float r,g,b;if(seal.color()>0){int color=DyeColor.byId(16-seal.color()).getTextColor();r=(color>>16&255)/255F;g=(color>>8&255)/255F;b=(color&255)/255F;}
            else{r=.7F+Mth.sin((age+pos.getX())/4)*.1F;g=.7F+Mth.sin((age+pos.getY())/5)*.1F;b=.7F+Mth.sin((age+pos.getZ())/6)*.1F;}
            pose.pushPose();pose.translate(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);face(pose,seal.position().face());pose.translate(0,0,.51);pose.mulPose(Axis.ZP.rotationDegrees(age%360));quad(pose,MIDDLE,through,.9F,r,g,b,alpha*.8F,0,0,1,1);pose.popPose();
            SealBehavior behavior=SealRegistry.behavior(seal.type());if(behavior==null||!behavior.hasArea())continue;
            var area=seal.bounds();double[][] corners={{area.minX,area.minY,area.minZ},{area.minX,area.maxY-1,area.minZ},{area.maxX-1,area.minY,area.minZ},{area.maxX-1,area.maxY-1,area.minZ},{area.maxX-1,area.minY,area.maxZ-1},{area.maxX-1,area.maxY-1,area.maxZ-1},{area.minX,area.minY,area.maxZ-1},{area.minX,area.maxY-1,area.maxZ-1}};
            for(int corner=0;corner<8;corner++)for(int side=0;side<3;side++){
                pose.pushPose();pose.translate(corners[corner][0]+.5,corners[corner][1]+.5,corners[corner][2]+.5);face(pose,FACES[corner][side]);pose.translate(0,0,.49);pose.mulPose(Axis.ZP.rotationDegrees(ROTATIONS[corner][side]-90));quad(pose,CORNER,through,1,r,g,b,alpha*.7F,0,0,1,1);pose.popPose();}
        }
        pose.popPose();BUFFERS.endBatch();
    }
    private static void face(PoseStack pose,Direction direction){switch(direction){case DOWN->pose.mulPose(Axis.XP.rotationDegrees(90));case UP->pose.mulPose(Axis.XN.rotationDegrees(90));case NORTH->pose.mulPose(Axis.YP.rotationDegrees(180));case EAST->pose.mulPose(Axis.YP.rotationDegrees(90));case WEST->pose.mulPose(Axis.YN.rotationDegrees(90));default->{}}}
    private static void quad(PoseStack pose,ResourceLocation texture,boolean through,float size,float r,float g,float b,float alpha,float u0,float v0,float u1,float v1){
        var vertices=BUFFERS.getBuffer(GolemancyRenderTypes.seal(texture,through));float half=size/2;
        vertex(vertices,pose,-half,-half,r,g,b,alpha,u0,v1);vertex(vertices,pose,half,-half,r,g,b,alpha,u1,v1);vertex(vertices,pose,half,half,r,g,b,alpha,u1,v0);vertex(vertices,pose,-half,half,r,g,b,alpha,u0,v0);
    }
    private static void vertex(VertexConsumer vertices,PoseStack pose,float x,float y,float r,float g,float b,float alpha,float u,float v){vertices.vertex(pose.last().pose(),x,y,0).color(r,g,b,alpha).uv(u,v).uv2(15728880).endVertex();}
}
