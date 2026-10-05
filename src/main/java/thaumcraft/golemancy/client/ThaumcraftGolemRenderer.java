package thaumcraft.golemancy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import thaumcraft.catalog.entities.client.ObjMesh;
import thaumcraft.golemancy.entity.ThaumcraftGolemEntity;
import thaumcraft.golemancy.press.GolemDesign;
import java.util.*;

/** BETA26 OBJ attachments, limb rotations and carried-item transforms. Entity UVs retain 1-v. */
public final class ThaumcraftGolemRenderer extends EntityRenderer<ThaumcraftGolemEntity> {
    private record Grinder(float speed,float rotation,int tick) {}
    private final Map<UUID,Grinder> grinders=new HashMap<>();
    private int passTint=0xffffffff;
    private boolean translucent,through;
    public ThaumcraftGolemRenderer(EntityRendererProvider.Context context) {super(context);shadowRadius=.3F;}
    private static ResourceLocation texture(String path){return ResourceLocation.fromNamespaceAndPath("thaumcraft",path);}
    @Override public ResourceLocation getTextureLocation(ThaumcraftGolemEntity golem) {
        return texture("textures/entity/golems/mat_"+new String[]{"wood","iron","clay","brass","thaumium","void"}[golem.design().material().id()]+".png");
    }
    @Override public void render(ThaumcraftGolemEntity golem,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,golem.yBodyRotO,golem.yBodyRot)));
        var player=Minecraft.getInstance().player;
        try {
            if(!golem.isInvisible()||player!=null&&!golem.isInvisibleTo(player)) {
                translucent=golem.isInvisible();through=false;passTint=translucent?0x26ffffff:0xffffffff;
                renderParts(golem,partial,pose,buffers,light);
            }
            if(player!=null&&player.isShiftKeyDown()&&(SealWorldRenderer.displays(player.getMainHandItem())||SealWorldRenderer.displays(player.getOffhandItem()))) {
                var start=player.position().add(0,player.getBbHeight()/2,0);
                var end=golem.position().add(0,golem.getBbHeight()/2,0);
                var hit=golem.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){translucent=through=true;passTint=0x40404040;renderParts(golem,partial,pose,buffers,light);}
            }
        } finally {passTint=0xffffffff;translucent=through=false;}
        pose.popPose();
        super.render(golem,yaw,partial,pose,buffers,light);
        if(grinders.size()>1024)grinders.clear();
    }
    private void mesh(String name,PoseStack pose,MultiBufferSource buffers,ResourceLocation material,ResourceLocation detail,int light,String... groups){meshTinted(name,pose,buffers,material,detail,light,0xffffffff,groups);}
    private void meshTinted(String name,PoseStack pose,MultiBufferSource buffers,ResourceLocation material,ResourceLocation detail,int light,int color,String... groups){
        int tint=0;for(int shift:new int[]{0,8,16,24})tint|=((color>>>shift&255)*(passTint>>>shift&255)/255)<<shift;
        ObjMesh.get(name).renderStyled(pose,buffers,material,detail,light,tint,translucent?texture->GolemancyRenderTypes.golem(texture,through):net.minecraft.client.renderer.RenderType::entityCutoutNoCull,groups);
    }
    private void renderParts(ThaumcraftGolemEntity golem,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        GolemDesign design=golem.design();ResourceLocation material=getTextureLocation(golem);
        boolean holding=!golem.getMainHandItem().isEmpty();
        boolean wheeled=design.hasTrait(GolemDesign.Trait.WHEELED), flyer=design.hasTrait(GolemDesign.Trait.FLYER);
        float age=golem.tickCount+partial,limb=golem.walkAnimation.position(partial),amount=Math.min(1,golem.walkAnimation.speed(partial));
        double dx=golem.getX()-golem.xo,dz=golem.getZ()-golem.zo,speed=dx*dx+dz*dz;
        float rx=(float)Math.toDegrees(Mth.sin(age*.067F)*.03F),rz=(float)Math.toDegrees(Mth.cos(age*.09F)*.05F+.05F);
        float body=0,swing=golem.getAttackAnim(partial);
        if(swing>0)body=(float)Math.toDegrees(-Mth.sin(Mth.sqrt(swing)*Mth.TWO_PI)*.2F);
        pose.mulPose(Axis.YP.rotationDegrees(body));
        float lean=wheeled||flyer?75:25;
        pose.mulPose(Axis.XN.rotationDegrees((float)(speed*lean)));
        pose.mulPose(Axis.ZN.rotationDegrees((float)(speed*lean*.06*Mth.wrapDegrees(golem.getYRot()-golem.yRotO))));
        pose.pushPose();pose.translate(0,.5,0);
        mesh("golem_base",pose,buffers,material,null,light,"chest","waist");
        if(golem.getGolemColor()>0)meshTinted("golem_base",pose,buffers,material,null,light,
                0xff000000|DyeColor.byId(16-golem.getGolemColor()).getTextColor(),"flag");
        if(design.addon().id()==1)mesh("golem_armor",pose,buffers,material,null,light);
        if(design.addon().id()==3){mesh("golem_hauler",pose,buffers,material,texture("textures/entity/golems/golem_hauler.png"),light);
            var carrying=golem.getCarrying();if(carrying.size()>1&&!carrying.get(1).isEmpty()){
                pose.pushPose();pose.scale(.375F,.375F,.375F);pose.translate(0,.33,.825);
                if(!(carrying.get(1).getItem() instanceof BlockItem))pose.translate(0,0,-.25);
                item(golem,carrying.get(1),pose,buffers,light);pose.popPose();}}
        if(wheeled||flyer)part(golem,wheeled?"golem_legs_wheel":"golem_legs_floater",wheeled?"wheel":"floater",0,partial,pose,buffers,material,light);
        pose.popPose();
        pose.pushPose();pose.translate(0,.75,-.03125);
        pose.mulPose(Axis.YN.rotationDegrees(Mth.rotLerp(partial,golem.yHeadRotO,golem.yHeadRot)-Mth.rotLerp(partial,golem.yBodyRotO,golem.yBodyRot)));
        pose.mulPose(Axis.XN.rotationDegrees(Mth.lerp(partial,golem.xRotO,golem.getXRot())));
        String head=new String[]{"basic","smart","smart_armor","scout","scout_smart"}[design.head().id()];
        mesh("golem_head_"+head,pose,buffers,material,design.head().id()==0||design.head().id()==2?null:texture("textures/entity/golems/golem_head_other.png"),light);
        pose.popPose();
        for(int side:new int[]{1,-1}){
            float armX,armY=0,armZ;
            if(holding){armX=90-rz/2;armZ=side>0?-2:2;}
            else{armX=wheeled||flyer?rx*2*side:(float)Math.toDegrees(Mth.cos(limb*.6662F+(side>0?Mth.PI:0))*amount)+rx*side;armZ=(rz+2)*side;}
            if(side>0&&swing>0){float wiggle=(float)Math.toRadians(body);armZ=-(float)Math.toDegrees(Mth.sin(wiggle)*3);armX=(float)Math.toDegrees(-Mth.cos(wiggle)*5);armY=body;}
            if(design.arms().id()==4&&golem.isInCombat()){armX=90-golem.xRotO+armX/10;armY/=10;armZ/=10;}
            pose.pushPose();pose.translate(side*.20625,.6875,0);
            pose.mulPose(Axis.XP.rotationDegrees(armX));pose.mulPose(Axis.YP.rotationDegrees(armY+(side<0?180:0)));pose.mulPose(Axis.ZP.rotationDegrees(side*armZ));
            mesh("golem_base",pose,buffers,material,null,light,"arm");
            String arms=new String[]{"basic","fine","claws","breakers","darter"}[design.arms().id()];
            part(golem,"golem_arms_"+arms,arms,side,partial,pose,buffers,material,light);pose.popPose();
            if(!wheeled&&!flyer){pose.pushPose();pose.translate(side*.09375,.375,0);
                pose.mulPose(Axis.XP.rotationDegrees((float)Math.toDegrees(Mth.cos(limb*.6662F+(side<0?Mth.PI:0))*amount)));
                mesh(design.legs().id()==0?"golem_legs_walker":"golem_legs_climber",pose,buffers,material,design.legs().id()==0?null:texture("textures/blocks/base_metal.png"),light);pose.popPose();}
        }
        if(holding){pose.pushPose();pose.translate(0,.625,0);pose.mulPose(Axis.XP.rotationDegrees(90-rz*.5F));
            pose.mulPose(Axis.XN.rotationDegrees(90));pose.scale(.375F,.375F,.375F);pose.translate(0,.25,-1.5);
            if(!(golem.getMainHandItem().getItem() instanceof BlockItem))pose.translate(0,-.6,0);
            item(golem,golem.getMainHandItem(),pose,buffers,light);pose.popPose();}
    }
    private static void item(ThaumcraftGolemEntity golem,ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light){
        Minecraft.getInstance().getItemRenderer().renderStatic(golem,stack,ItemDisplayContext.HEAD,false,pose,buffers,golem.level(),light,OverlayTexture.NO_OVERLAY,golem.getId());
    }
    private void part(ThaumcraftGolemEntity golem,String meshName,String detailName,int side,float partial,PoseStack pose,MultiBufferSource buffers,ResourceLocation material,int light){
        ObjMesh mesh=ObjMesh.get(meshName);ResourceLocation detail=null;
        if(meshName.startsWith("golem_arms_")&&!List.of("basic","fine").contains(detailName))detail=texture("textures/entity/golems/golem_arms_"+detailName+".png");
        if(meshName.startsWith("golem_legs_"))detail=texture("textures/entity/golems/golem_legs_"+detailName+".png");
        for(String group:mesh.groups()){
            pose.pushPose();
            if(meshName.equals("golem_legs_wheel")&&group.equals("wheel")){pose.translate(0,-.375,0);pose.mulPose(Axis.XN.rotationDegrees(golem.wheelRotation()));}
            if(meshName.equals("golem_arms_claws")&&group.startsWith("claw")){float f=golem.getAttackAnim(partial)*4.1F;pose.translate(0,-.2,0);pose.mulPose((group.endsWith("1")?Axis.XP:Axis.XN).rotationDegrees(f*f));}
            if(meshName.equals("golem_arms_breakers")&&group.equals("grinder")){
                Grinder old=grinders.getOrDefault(golem.getUUID(),new Grinder(0,0,-1));
                if(old.tick()!=golem.tickCount){float f=Math.max(old.speed(),golem.getAttackAnim(partial)*20);old=new Grinder(f*.99F,old.rotation()+f,golem.tickCount);grinders.put(golem.getUUID(),old);}
                pose.translate(0,-.34,0);pose.mulPose((side<0?Axis.XN:Axis.XP).rotationDegrees((golem.tickCount+partial)/2+old.rotation()+(side<0?22:0)));
            }
            mesh(meshName,pose,buffers,material,detail,light,group);pose.popPose();
        }
    }
}
