package thaumcraft.catalog.blocks.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.blocks.*;
import thaumcraft.catalog.entities.client.*;

/** Original neutral hardware details. No invented operational effects or contents. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CatalogBlockRenderer implements BlockEntityRenderer<CatalogBlockEntity> {
    private final LegacyVisualModel banner=new LegacyBannerBlockModel(),brain=new LegacyBrainBlockModel(),
            valve=new LegacyTubeValveBlockModel(),centrifuge=new LegacyCentrifugeBlockModel(),jar=new LegacyJarBlockModel(),cube=new LegacyInfusionCubeBlockModel();
    public CatalogBlockRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CatalogBlocks.VISUAL_TILE.get(),CatalogBlockRenderer::new);
    }
    public static int color(String id) {
        String name=id.substring(id.indexOf('_')+1);
        name=name.equals("lightblue")?"light_blue":name.equals("silver")?"light_gray":name;
        float[] rgb=DyeColor.byName(name,DyeColor.WHITE).getTextureDiffuseColors();
        return ((int)(rgb[0]*255)<<16)|((int)(rgb[1]*255)<<8)|(int)(rgb[2]*255);
    }
    private static String value(BlockState state,String name,String fallback) {
        var property=state.getBlock().getStateDefinition().getProperty(name);
        return property==null ? fallback : String.valueOf(state.getValue(property));
    }
    private static Direction facing(BlockState state) {
        var direction=Direction.byName(value(state,"facing","north"));
        return direction==null ? Direction.NORTH : direction;
    }
    @Override public void render(CatalogBlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        renderVisual(tile.getBlockState(),tile.getLevel(),tile.getBlockPos(),pose,buffers,light,overlay);
    }
    public void renderVisual(String id,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        renderVisual(CatalogBlocks.block(id).defaultBlockState(),null,null,pose,buffers,light,overlay);
    }
    public void renderVisual(BlockState state,BlockGetter level,BlockPos pos,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        pose.pushPose();
        if(id.startsWith("banner_")) {
            pose.translate(.5,1.5,.5);
            pose.mulPose(Axis.XP.rotationDegrees(180));
            // BlockBannerTCItem's original 16-way floor / cardinal wall orientation.
            pose.mulPose(Axis.YP.rotationDegrees(180+Integer.parseInt(value(state,"rotation","0"))*22.5f));
            boolean wall=Boolean.parseBoolean(value(state,"wall","false"));
            int color=id.equals("banner_crimson_cult")?0xFFFFFF:color(id);
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/models/banner_"+(id.equals("banner_crimson_cult")?"cultist":"blank")+".png")));
            if(wall)pose.translate(0,1,-.4125);
            else banner.renderParts(pose,vertices,light,overlay,0xFFFFFF,"Pole");
            banner.renderParts(pose,vertices,light,overlay,0xFFFFFF,"Beam");
            banner.renderParts(pose,vertices,light,overlay,color,"Banner","B1","B2");
        } else if(id.equals("jar_brain")) {
            pose.pushPose();
            pose.translate(.5,.81,.5);
            pose.mulPose(Axis.XP.rotationDegrees(180));pose.mulPose(Axis.YP.rotationDegrees(-90));pose.scale(.4f,.4f,.4f);
            render(brain,pose,buffers,"brain2",light,overlay,0xFFFFFF);
            pose.popPose();
            pose.translate(.5,.01,.5);pose.mulPose(Axis.XP.rotationDegrees(180));
            jar.renderParts(pose,buffers.getBuffer(RenderType.entityTranslucent(texture("textures/models/jarbrine.png"))),light,overlay,0xFFFFFF,"Brine");
        } else if(id.equals("centrifuge")) {
            pose.translate(.5,.5,.5);render(centrifuge,pose,buffers,"centrifuge",light,overlay,0xFFFFFF);
        } else if(id.equals("infusion_matrix")) {
            pose.translate(.5,.5,.5);
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/blocks/infuser_normal.png")));
            for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int c=0;c<2;c++) {
                pose.pushPose();pose.translate((a==0?-1:1)*.25,(b==0?-1:1)*.25,(c==0?-1:1)*.25);
                if(a>0)pose.mulPose(Axis.XP.rotationDegrees(90));
                if(b>0)pose.mulPose(Axis.YP.rotationDegrees(90));
                if(c>0)pose.mulPose(Axis.ZP.rotationDegrees(90));
                pose.scale(.45f,.45f,.45f);cube.renderToBuffer(pose,vertices,light,overlay,1,1,1,1);pose.popPose();
            }
        } else if(id.equals("tube_valve")) {
            pose.translate(.5,.5,.5);orientTube(pose,facing(state));pose.translate(0,-.03,0);
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/models/valve.png")));
            valve.renderParts(pose,vertices,light,overlay,0xFFFFFF,"ValveRing");
            pose.scale(.75f,1,.75f);valve.renderParts(pose,vertices,light,overlay,0xFFFFFF,"ValveRod");
        } else if(id.equals("tube_oneway")) {
            Direction facing=facing(state);
            // Original upstream-connected marker. Item previews have no world and show the same rod.
            boolean incoming=level==null || pos==null || level.getBlockState(pos.relative(facing.getOpposite())).getBlock() instanceof CatalogBlock pipe && pipe.catalogId().startsWith("tube");
            if(incoming) {
                pose.translate(.5,.5,.5);orientTube(pose,facing);pose.scale(2,2,2);pose.translate(0,-.32,0);
                valve.renderParts(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/models/valve.png"))),light,overlay,0x7380FF,"ValveRod");
            }
        } else if(id.equals("pattern_crafter")) {
            renderPattern(state,pose,buffers,light,overlay);
        } else if(id.startsWith("nitor_")) {
            pose.translate(.5,.5,.5);pose.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            var vertices=buffers.getBuffer(RenderType.entityTranslucent(texture("textures/misc/particles.png")));
            int c=color(id);float u=0,v=5/64f,du=1/64f;
            spritePoint(vertices,pose.last(),-.28f,-.28f,u,v+du,c);spritePoint(vertices,pose.last(),.28f,-.28f,u+du,v+du,c);
            spritePoint(vertices,pose.last(),.28f,.28f,u+du,v,c);spritePoint(vertices,pose.last(),-.28f,.28f,u,v,c);
        }
        pose.popPose();
    }
    private static void orientTube(PoseStack pose,Direction direction) {
        if(direction.getStepY()==0)pose.mulPose(Axis.YP.rotationDegrees(90));
        else {pose.mulPose(Axis.XN.rotationDegrees(90));pose.mulPose(Axis.XP.rotationDegrees(90*direction.getStepY()));}
        if(direction.getStepX()!=0)pose.mulPose(Axis.XP.rotationDegrees(90*direction.getStepX()));
        else if(direction.getStepY()!=0)pose.mulPose(Axis.YP.rotationDegrees(90*direction.getStepY()));
        else pose.mulPose(Axis.ZP.rotationDegrees(90*direction.getStepZ()));
    }
    private static void renderPattern(BlockState state,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.translate(.5,.75,.5);
        float rotation=switch(facing(state)){case EAST -> 90;case WEST -> 270;case NORTH -> 180;default -> 0;};
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(90));pose.translate(0,0,-.5001);
        var panel=buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/blocks/pattern_crafter_modes.png")));
        // TC6 renderQuadCentered(10,1,frame=0,scale=.5): original rotated UV order.
        vertex(panel,pose.last(),-.25f,.25f,0,.1f,1,light,overlay,0,0,-1);
        vertex(panel,pose.last(),.25f,.25f,0,.1f,0,light,overlay,0,0,-1);
        vertex(panel,pose.last(),.25f,-.25f,0,0,0,light,overlay,0,0,-1);
        vertex(panel,pose.last(),-.25f,-.25f,0,0,1,light,overlay,0,0,-1);
        pose.popPose();
        var gear=buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/misc/gear_brass.png")));
        for(float x:new float[]{-.2f,.2f}) {
            pose.pushPose();pose.translate(x,-.40625,.05);pose.scale(.5f,.5f,1);pose.translate(-.5,-.5,0);
            extrudedSprite(gear,pose.last(),light,overlay,.1f);pose.popPose();
        }
    }
    /** Original UtilsFX.renderTextureIn3D(1,0,0,1,16,16,.1) using modern vertices. */
    private static void extrudedSprite(VertexConsumer vertices,PoseStack.Pose pose,int light,int overlay,float thickness) {
        vertex(vertices,pose,0,0,0,1,1,light,overlay,0,0,1);vertex(vertices,pose,1,0,0,0,1,light,overlay,0,0,1);
        vertex(vertices,pose,1,1,0,0,0,light,overlay,0,0,1);vertex(vertices,pose,0,1,0,1,0,light,overlay,0,0,1);
        vertex(vertices,pose,0,1,-thickness,1,0,light,overlay,0,0,-1);vertex(vertices,pose,1,1,-thickness,0,0,light,overlay,0,0,-1);
        vertex(vertices,pose,1,0,-thickness,0,1,light,overlay,0,0,-1);vertex(vertices,pose,0,0,-thickness,1,1,light,overlay,0,0,-1);
        for(int i=0;i<16;i++) {
            float f=i/16f,next=f+1/16f,uv=1-f-1/32f;
            vertex(vertices,pose,f,0,-thickness,uv,1,light,overlay,-1,0,0);vertex(vertices,pose,f,0,0,uv,1,light,overlay,-1,0,0);
            vertex(vertices,pose,f,1,0,uv,0,light,overlay,-1,0,0);vertex(vertices,pose,f,1,-thickness,uv,0,light,overlay,-1,0,0);
            vertex(vertices,pose,next,1,-thickness,uv,0,light,overlay,1,0,0);vertex(vertices,pose,next,1,0,uv,0,light,overlay,1,0,0);
            vertex(vertices,pose,next,0,0,uv,1,light,overlay,1,0,0);vertex(vertices,pose,next,0,-thickness,uv,1,light,overlay,1,0,0);
            vertex(vertices,pose,0,next,0,1,uv,light,overlay,0,1,0);vertex(vertices,pose,1,next,0,0,uv,light,overlay,0,1,0);
            vertex(vertices,pose,1,next,-thickness,0,uv,light,overlay,0,1,0);vertex(vertices,pose,0,next,-thickness,1,uv,light,overlay,0,1,0);
            vertex(vertices,pose,1,f,0,0,uv,light,overlay,0,-1,0);vertex(vertices,pose,0,f,0,1,uv,light,overlay,0,-1,0);
            vertex(vertices,pose,0,f,-thickness,1,uv,light,overlay,0,-1,0);vertex(vertices,pose,1,f,-thickness,0,uv,light,overlay,0,-1,0);
        }
    }
    private static ResourceLocation texture(String path) {return ResourceLocation.fromNamespaceAndPath("thaumcraft",path);}
    private static void render(LegacyVisualModel model,PoseStack pose,MultiBufferSource buffers,String texture,int light,int overlay,int color) {
        model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture("textures/models/"+texture+".png"))),light,overlay,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose p,float x,float y,float z,float u,float w,int light,int overlay,float nx,float ny,float nz) {
        v.vertex(p.pose(),x,y,z).color(255,255,255,255).uv(u,w).overlayCoords(overlay).uv2(light).normal(p.normal(),nx,ny,nz).endVertex();
    }
    private static void spritePoint(VertexConsumer v,PoseStack.Pose p,float x,float y,float u,float w,int c) {
        v.vertex(p.pose(),x,y,0).color((c>>16)&255,(c>>8)&255,c&255,255).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.normal(),0,0,1).endVertex();
    }
}
