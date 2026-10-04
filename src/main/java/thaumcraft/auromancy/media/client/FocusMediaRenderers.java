package thaumcraft.auromancy.media.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import thaumcraft.auromancy.media.*;
import thaumcraft.catalog.entities.client.LegacyGrapplerModel;
import java.util.*;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FocusMediaRenderers {
    private FocusMediaRenderers(){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(FocusMediaModule.CLOUD.get(),Cloud::new);event.registerEntityRenderer(FocusMediaModule.MINE.get(),Mine::new);event.registerEntityRenderer(FocusMediaModule.SPELL_BAT.get(),Bat::new);
    }
    private static ResourceLocation texture(String file){return ResourceLocation.fromNamespaceAndPath("thaumcraft",file);}
    private static DustParticleOptions dust(int color,float size){return new DustParticleOptions(new Vector3f(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F),size);}
    private static final class Cloud extends EntityRenderer<FocusCloudEntity>{
        private final Map<FocusCloudEntity,Integer> emitted=new WeakHashMap<>();
        Cloud(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(FocusCloudEntity entity){return texture("textures/misc/particles.png");}
        @Override public void render(FocusCloudEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            if(emitted.getOrDefault(entity,-1)!=entity.tickCount){
                emitted.put(entity,entity.tickCount);var random=entity.level().random;float radius=entity.radius();
                for(int i=0;i<radius;i++)for(int cloud=0;cloud<2;cloud++){
                    double scale=cloud==0?.85:1;
                    entity.level().addParticle(dust(entity.color(),cloud==0?2:1),entity.getX()+random.nextGaussian()*radius/2*scale,
                            entity.getY()+random.nextGaussian()*radius/2*scale,entity.getZ()+random.nextGaussian()*radius/2*scale,
                            random.nextGaussian()*.01,random.nextGaussian()*.01,random.nextGaussian()*.01);
                }
            }
            super.render(entity,yaw,partial,pose,buffers,light);
        }
    }
    private static final class Mine extends EntityRenderer<FocusMineEntity>{
        private final LegacyGrapplerModel model=new LegacyGrapplerModel();private final Map<FocusMineEntity,Integer> emitted=new WeakHashMap<>();
        Mine(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
        @Override public ResourceLocation getTextureLocation(FocusMineEntity entity){return texture("textures/entity/mine.png");}
        @Override public void render(FocusMineEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(yaw-90));pose.mulPose(Axis.ZP.rotationDegrees(entity.getXRot()));
            float pulse=(entity.counter()+partial)%8/8F;
            model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),61680,OverlayTexture.NO_OVERLAY,1,1-pulse,1-pulse,1);pose.popPose();
            if(entity.armed()&&entity.counter()<=0&&entity.tickCount%5==0&&emitted.getOrDefault(entity,-1)!=entity.tickCount){
                emitted.put(entity,entity.tickCount);var random=entity.level().random;
                entity.level().addParticle(dust(entity.color(),.7F),entity.getX()+random.nextGaussian()*.1,entity.getY()+random.nextGaussian()*.1,entity.getZ()+random.nextGaussian()*.1,
                        random.nextGaussian()*.01,random.nextGaussian()*.01,random.nextGaussian()*.01);
            }
            super.render(entity,yaw,partial,pose,buffers,light);
        }
    }
    private static final class Bat extends MobRenderer<SpellBatEntity,SpellBatModel>{
        private final Map<SpellBatEntity,Integer> emitted=new WeakHashMap<>();
        Bat(EntityRendererProvider.Context context){super(context,new SpellBatModel(),.25F);}
        @Override public ResourceLocation getTextureLocation(SpellBatEntity entity){return texture("textures/entity/spellbat.png");}
        @Override protected void scale(SpellBatEntity entity,PoseStack pose,float partial){pose.scale(.35F,.35F,.35F);}
        @Override protected void setupRotations(SpellBatEntity entity,PoseStack pose,float age,float yaw,float partial){pose.translate(0,-.1,0);super.setupRotations(entity,pose,age,yaw,partial);}
        @Override protected int getBlockLightLevel(SpellBatEntity entity,net.minecraft.core.BlockPos pos){return 15;}
        @Override public void render(SpellBatEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
            if(emitted.getOrDefault(entity,-1)!=entity.tickCount){emitted.put(entity,entity.tickCount);var random=entity.level().random;
                entity.level().addParticle(dust(entity.color(),.7F),entity.getX()+random.nextGaussian()*.125,entity.getY()+entity.getBbHeight()/2+random.nextGaussian()*.125,entity.getZ()+random.nextGaussian()*.125,0,0,0);
            }
            super.render(entity,yaw,partial,pose,buffers,15728880);
        }
    }
}
