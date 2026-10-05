package thaumcraft.golemancy.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.golemancy.entity.*;

public final class GolemProjectileRenderers {
    public static final class Dart extends ArrowRenderer<GolemDartEntity>{
        public Dart(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(GolemDartEntity dart){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/projectiles/arrow.png");}
    }
    public static final class Orb extends EntityRenderer<GolemOrbEntity>{
        public Orb(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(GolemOrbEntity orb){return ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/particles.png");}
        @Override public void render(GolemOrbEntity orb,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            pose.pushPose();pose.mulPose(entityRenderDispatcher.cameraOrientation());float scale=1+Mth.sin(orb.tickCount/5F)*.2F+.2F;pose.scale(scale,scale,scale);
            float u=(1+orb.tickCount%6)/32F,v=orb.red()?.1875F:.21875F;VertexConsumer vertices=buffers.getBuffer(GolemancyRenderTypes.ORB);
            vertex(vertices,pose,-.5F,-.5F,u,v+1/32F);vertex(vertices,pose,.5F,-.5F,u+1/32F,v+1/32F);vertex(vertices,pose,.5F,.5F,u+1/32F,v);vertex(vertices,pose,-.5F,.5F,u,v);
            pose.popPose();super.render(orb,yaw,partial,pose,buffers,light);
        }
        private static void vertex(VertexConsumer vertices,PoseStack pose,float x,float y,float u,float v){vertices.vertex(pose.last().pose(),x,y,0).color(1F,1F,1F,1F).uv(u,v).uv2(220).endVertex();}
    }
    private GolemProjectileRenderers(){}
}
