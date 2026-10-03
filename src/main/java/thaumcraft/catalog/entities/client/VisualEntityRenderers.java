package thaumcraft.catalog.entities.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.entities.*;
import thaumcraft.auromancy.projectile.FocusProjectileRenderer;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class VisualEntityRenderers {
    private VisualEntityRenderers() {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        VisualEntitiesModule.LIVING.forEach((id,type) -> event.registerEntityRenderer(type.get(),context -> {
            var spec=VisualEntitySpec.byId(id);
            if(spec.model().startsWith("obj_")) return new ObjRenderer(context);
            if(java.util.List.of("wisp","swarm","portal").contains(spec.model())) return new SpriteRenderer<VisualMobEntity>(context);
            if(spec.model().equals("humanoid") || spec.model().equals("zombie")) return new CultistRenderer(context,spec);
            return new Mob(context,spec);
        }));
        VisualEntitiesModule.EFFECTS.forEach((id,type) -> event.registerEntityRenderer(type.get(),SpriteRenderer<VisualEffectEntity>::new));
        event.registerEntityRenderer(VisualEntitiesModule.FOCUS_PROJECTILE.get(),FocusProjectileRenderer::new);
    }
    public static ResourceLocation texture(String path) {
        return path.contains(":")?ResourceLocation.parse(path):ResourceLocation.fromNamespaceAndPath("thaumcraft",path);
    }
    private static EntityModel<VisualMobEntity> model(EntityRendererProvider.Context context,VisualEntitySpec spec) {
        return switch(spec.model()) {
            case "ArcaneBore" -> new LegacyArcaneBoreModel();
            case "Crossbow" -> new LegacyCrossbowModel();
            case "EldritchCrab" -> new LegacyEldritchCrabModel();
            case "EldritchGolem" -> new LegacyEldritchGolemModel();
            case "EldritchGuardian" -> new LegacyEldritchGuardianModel();
            case "FireBat" -> new LegacyFireBatModel();
            case "Pech" -> new LegacyPechModel();
            case "TaintSeed" -> new LegacyTaintSeedModel();
            case "Taintacle14" -> new LegacyTaintacleModel(14,false);
            case "Taintacle10" -> new LegacyTaintacleModel(10,false);
            case "Taintacle6" -> new LegacyTaintacleModel(6,false);
            case "spider" -> new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER));
            case "slime" -> new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME));
            case "silverfish" -> new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH));
            default -> new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE));
        };
    }
    private static final class Mob extends MobRenderer<VisualMobEntity,EntityModel<VisualMobEntity>> {
        Mob(EntityRendererProvider.Context context,VisualEntitySpec spec) {
            super(context,model(context,spec),.35f);
            if(spec.model().equals("slime")) addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<VisualMobEntity,EntityModel<VisualMobEntity>>(this) {
                final SlimeModel<VisualMobEntity> shell=new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME_OUTER));
                @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,VisualMobEntity entity,float swing,float amount,float partial,float age,float yaw,float pitch) {
                    getParentModel().copyPropertiesTo(shell);shell.prepareMobModel(entity,swing,amount,partial);shell.setupAnim(entity,swing,amount,age,yaw,pitch);
                    shell.renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),light,OverlayTexture.NO_OVERLAY,1,1,1,1);
                }
            });
        }
        @Override public ResourceLocation getTextureLocation(VisualMobEntity entity) {
            if(entity.spec().id().equals("pech")) return texture("textures/entity/pech_"+new String[]{"forage","thaum","stalker"}[Math.floorMod(entity.variant(),3)]+".png");
            return texture(entity.spec().texture());
        }
        @Override protected void scale(VisualMobEntity entity,PoseStack pose,float partial) {
            float scale=switch(entity.spec().id()) {case "eldritch_golem" -> 1.7f;case "eldritch_crab" -> .8f;case "mind_spider" -> .6f;case "taint_crawler" -> .7f;case "fire_bat","spell_bat" -> .35f;case "thaumic_slime" -> 2;default -> 1; };
            pose.scale(scale,scale,scale);
        }
    }
    private static final class CultistRenderer extends HumanoidMobRenderer<VisualMobEntity,HumanoidModel<VisualMobEntity>> {
        CultistRenderer(EntityRendererProvider.Context context,VisualEntitySpec spec) {
            super(context,new HumanoidModel<>(context.bakeLayer(spec.model().equals("zombie")?ModelLayers.ZOMBIE:ModelLayers.PLAYER)),.4f);
            addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),context.getModelManager()));
        }
        @Override public ResourceLocation getTextureLocation(VisualMobEntity entity) { return texture(entity.spec().texture()); }
        @Override protected void scale(VisualMobEntity entity,PoseStack pose,float partial) { if(entity.spec().id().equals("cultist_leader")) pose.scale(1.15f,1.15f,1.15f); }
    }
    public static void golem(PoseStack pose,MultiBufferSource buffers,int light,int material,int head,int arms,int legs,int addon) {
        ResourceLocation mat=texture("textures/entity/golems/mat_"+new String[]{"wood","iron","clay","brass","thaumium","void"}[Math.floorMod(material,6)]+".png");
        pose.pushPose();pose.translate(0,.5,0);ObjMesh.get("golem_base").render(pose,buffers,mat,null,light,"chest","waist");
        if(addon==1) ObjMesh.get("golem_armor").render(pose,buffers,mat,null,light);
        if(addon==3) ObjMesh.get("golem_hauler").render(pose,buffers,mat,texture("textures/entity/golems/golem_hauler.png"),light);
        if(legs==1 || legs==3) ObjMesh.get(legs==1?"golem_legs_wheel":"golem_legs_floater").render(pose,buffers,mat,texture("textures/entity/golems/golem_legs_"+(legs==1?"wheel":"floater")+".png"),light);
        pose.popPose();
        pose.pushPose();pose.translate(0,.75,-.03125);
        ObjMesh.get("golem_base").render(pose,buffers,mat,null,light,"head");
        ObjMesh.get("golem_head_"+new String[]{"basic","smart","smart_armor","scout","scout_smart"}[Math.floorMod(head,5)]).render(pose,buffers,mat,head==0 || head==2?null:texture("textures/entity/golems/golem_head_other.png"),light);pose.popPose();
        for(int side:new int[]{1,-1}) {
            pose.pushPose();pose.translate(side*.20625,.6875,0);if(side<0) pose.mulPose(Axis.YP.rotationDegrees(180));
            ObjMesh.get("golem_base").render(pose,buffers,mat,null,light,"arm");
            String a=new String[]{"basic","fine","claws","breakers","darter"}[Math.floorMod(arms,5)];
            ObjMesh.get("golem_arms_"+a).render(pose,buffers,mat,arms<2?null:texture("textures/entity/golems/golem_arms_"+a+".png"),light);pose.popPose();
            if(legs==0 || legs==2) {
                pose.pushPose();pose.translate(side*.09375,.375,0);
                ObjMesh.get(legs==0?"golem_legs_walker":"golem_legs_climber").render(pose,buffers,mat,legs==0?null:texture("textures/blocks/base_metal.png"),light);pose.popPose();
            }
        }
    }
    private static final class ObjRenderer extends EntityRenderer<VisualMobEntity> {
        ObjRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=.3f; }
        @Override public ResourceLocation getTextureLocation(VisualMobEntity entity) { return texture(entity.spec().texture()); }
        @Override public void render(VisualMobEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            if(entity.spec().model().equals("obj_golem")) golem(pose,buffers,light,entity.material(),entity.headPart(),entity.armsPart(),entity.legsPart(),entity.addonPart());
            else { pose.translate(0,.75,0);ObjMesh.get("crossbow_advanced").render(pose,buffers,getTextureLocation(entity),null,light,"legs","mech","box","shield","brain","loader");pose.translate(0,0,.375);ObjMesh.get("crossbow_advanced").render(pose,buffers,getTextureLocation(entity),null,light,"bow1","bow2"); }
            pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
        }
    }
    private static final class SpriteRenderer<T extends Entity> extends EntityRenderer<T> {
        SpriteRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(T entity) { var spec=entity instanceof VisualMobEntity m?m.spec():((VisualEffectEntity)entity).spec();return texture(spec.texture().isEmpty()?"textures/misc/particles.png":spec.texture()); }
        @Override public void render(T entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            var spec=entity instanceof VisualMobEntity m?m.spec():((VisualEffectEntity)entity).spec();String model=spec.model();
            if(model.equals("none") || model.equals("cloud")) return; // Original renderers have no geometry; spell particles belong to mechanics.
            pose.pushPose();
            if(entity instanceof VisualEffectEntity effect && java.util.List.of("item","bottle").contains(model)) {
                pose.translate(0,.2,0);pose.scale(.5f,.5f,.5f);
                net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(effect.visualItem(),ItemDisplayContext.GROUND,light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
            } else if(model.equals("block")) {
                pose.translate(-.5,0,-.5);
                net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(thaumcraft.catalog.blocks.CatalogBlocks.block("taint_rock").defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            } else if(model.equals("Grappler") || model.equals("mine")) {
                var mesh=new LegacyGrapplerModel();pose.mulPose(Axis.YP.rotationDegrees(yaw-90));pose.mulPose(Axis.ZP.rotationDegrees(entity.getXRot()));
                float pulse=model.equals("mine")?((entity.tickCount+partial)%8)/8:0;
                mesh.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))),light,OverlayTexture.NO_OVERLAY,1,1-pulse,1-pulse,1);
            } else if(model.equals("rift")) {
                var effect=(VisualEffectEntity)entity;
                CatalogEffectGeometry.fluxRift(pose,buffers,effect.riftSeed(),effect.riftSize(),entity.tickCount+partial);
            } else if(model.equals("dart")) {
                CatalogEffectGeometry.dart(pose,buffers,light,yaw,entity.getXRot());
            } else if(model.equals("swarm")) {
                CatalogEffectGeometry.swarm(pose,buffers,entity.getId(),entity.tickCount+partial);
            } else {
                if(model.equals("eldritch_orb"))CatalogEffectGeometry.eldritchOrbRays(pose,buffers,entity.tickCount+partial);
                pose.translate(0,model.equals("portal")?entity.getBbHeight()/2:0,0);
                pose.mulPose(entityRenderDispatcher.cameraOrientation());pose.mulPose(Axis.YP.rotationDegrees(180));
                int color=entity instanceof VisualMobEntity m?m.color():((VisualEffectEntity)entity).color();
                if(model.equals("wisp")) {
                    frame(pose,buffers,"textures/misc/particles.png",64,512+entity.tickCount%16,.4f,0xFFFFFF,1);
                    frame(pose,buffers,"textures/misc/particles.png",64,320+entity.tickCount%16,.75f,0xFFFFFF,.25f);
                    frame(pose,buffers,"textures/misc/auranodes.png",32,800+entity.tickCount%16,.75f,color,.5f);
                } else if(model.equals("portal")) {
                    float u=(15-entity.tickCount%16)/16f;
                    float sx=spec.id().endsWith("greater")?1.3f:1.25f;
                    quad(buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),pose.last(),sx,1.4f,u,0,u+1/16f,1,0xFFFFFF,1);
                } else if(model.equals("eldritch_orb")) {
                    frame(pose,buffers,"textures/misc/particles.png",64,192+entity.tickCount%13,.375f,0xFFFFFF,1);
                } else if(model.equals("golem_orb")) {
                    float size=.5f*(1.2f+net.minecraft.util.Mth.sin(entity.tickCount/5f)*.2f);
                    frame(pose,buffers,"textures/misc/particles.png",32,(((VisualEffectEntity)entity).red()?6:7)*32+1+entity.tickCount%6,size,0xFFFFFF,.8f);
                }
            }
            pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
        }
        private static void frame(PoseStack pose,MultiBufferSource buffers,String tex,int grid,int frame,float size,int color,float alpha) {
            float u=(frame%grid)/(float)grid,v=(frame/grid)/(float)grid;
            quad(buffers.getBuffer(RenderType.entityTranslucent(texture(tex))),pose.last(),size,size,u,v,u+1f/grid,v+1f/grid,color,alpha);
        }
        private static void quad(VertexConsumer v,PoseStack.Pose p,float sx,float sy,float u0,float v0,float u1,float v1,int color,float alpha) {
            point(v,p,-sx,-sy,u0,v1,color,alpha);point(v,p,sx,-sy,u1,v1,color,alpha);point(v,p,sx,sy,u1,v0,color,alpha);point(v,p,-sx,sy,u0,v0,color,alpha);
        }
        private static void point(VertexConsumer v,PoseStack.Pose p,float x,float y,float u,float w,int color,float alpha) { v.vertex(p.pose(),x,y,0).color((color>>16)&255,(color>>8)&255,color&255,(int)(alpha*255)).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.normal(),0,0,1).endVertex(); }
    }
}
