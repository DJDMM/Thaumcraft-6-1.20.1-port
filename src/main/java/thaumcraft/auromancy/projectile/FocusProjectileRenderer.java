package thaumcraft.auromancy.projectile;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import java.util.Map;
import java.util.WeakHashMap;

/** Original fire-mote atlas cell, with native effect trails instead of a visible projectile item. */
public final class FocusProjectileRenderer extends EntityRenderer<FocusProjectileEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/particles.png");
    private final Map<FocusProjectileEntity,Integer> emitted=new WeakHashMap<>();
    public FocusProjectileRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius=.1F; }
    @Override public ResourceLocation getTextureLocation(FocusProjectileEntity entity) { return TEXTURE; }
    @Override public void render(FocusProjectileEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if (entity.effectKey().isEmpty()) return; // Neutral legacy catalogue entity has no execution appearance.
        int color=entity.color();
        pose.pushPose(); pose.translate(0,entity.getBbHeight()/2,0); pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer vertices=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        // FXFireMote: cell7 in the64x64 atlas; .1*scale7 half size and alpha .5.
        vertex(vertices,pose.last(),-.7F,-.7F,7/64F,1/64F,color);
        vertex(vertices,pose.last(), .7F,-.7F,8/64F,1/64F,color);
        vertex(vertices,pose.last(), .7F, .7F,8/64F,0,color);
        vertex(vertices,pose.last(),-.7F, .7F,7/64F,0,color);
        pose.popPose();
        if (emitted.getOrDefault(entity,-1)!=entity.tickCount) {
            emitted.put(entity,entity.tickCount);
            var random=entity.level().random;
            double x=entity.xo+(entity.getX()-entity.xo)*partial, y=entity.yo+(entity.getY()-entity.yo)*partial+entity.getBbHeight()/2, z=entity.zo+(entity.getZ()-entity.zo)*partial;
            entity.level().addParticle(new DustParticleOptions(new Vector3f(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F),.7F),x,y,z,
                    .0125F*(random.nextFloat()-.5F),.0125F*(random.nextFloat()-.5F),.0125F*(random.nextFloat()-.5F));
            ParticleOptions effect=switch(entity.effectKey()) {
                case FocusNodeRegistry.AIR -> ParticleTypes.CLOUD;
                case FocusNodeRegistry.FROST -> ParticleTypes.SNOWFLAKE;
                case FocusNodeRegistry.EARTH -> new BlockParticleOption(ParticleTypes.BLOCK,Blocks.DIRT.defaultBlockState());
                default -> ParticleTypes.FLAME;
            };
            entity.level().addParticle(effect,x+random.nextGaussian()*.10000000149011612,y+random.nextGaussian()*.10000000149011612,z+random.nextGaussian()*.10000000149011612,
                    random.nextGaussian()*.009999999776482582,random.nextGaussian()*.009999999776482582,random.nextGaussian()*.009999999776482582);
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    private static void vertex(VertexConsumer vertices,PoseStack.Pose pose,float x,float y,float u,float v,int color) {
        vertices.vertex(pose.pose(),x,y,0).color((color>>16)&255,(color>>8)&255,color&255,128).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0,0,1).endVertex();
    }
}
