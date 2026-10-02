package thaumcraft.catalog.entities.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.util.Mth;
import thaumcraft.catalog.entities.VisualMobEntity;

/** Original BETA26 cuboids baked into 1.20 ModelParts, with original idle motion. */
public abstract class LegacyVisualModel extends EntityModel<VisualMobEntity> {
    protected int textureWidth = 64, textureHeight = 32;
    final List<LegacyPart> nodes = new ArrayList<>();
    private final Map<String, LegacyPart> named = new LinkedHashMap<>();
    private VisualMobEntity currentEntity;
    private float currentAge;

    protected final void setRotation(LegacyPart part, float x, float y, float z) {
        part.rotateAngleX = x; part.rotateAngleY = y; part.rotateAngleZ = z;
    }
    protected final void bake() {
        for (LegacyPart node : nodes) if (node.parent == null) node.bake();
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType() == LegacyPart.class) {
                try { field.setAccessible(true); var node = (LegacyPart)field.get(this); if (node != null) named.put(field.getName(), node); }
                catch (ReflectiveOperationException ex) { throw new IllegalStateException("Missing original model part", ex); }
            }
        }
    }
    private void rotation(String name, float x, float y, float z) {
        var part = named.get(name); if (part != null) setRotation(part, x, y, z);
    }
    private void visible(String name, boolean visible) {
        var part = named.get(name); if (part != null) part.visible = visible;
    }

    @Override public void setupAnim(VisualMobEntity entity, float swing, float amount, float age, float yaw, float pitch) {
        currentEntity = entity; currentAge = age;
        nodes.forEach(LegacyPart::reset);
        float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
        if (this instanceof LegacyPechModel) {
            rotation("bipedHead", pr, yr, 0);
            rotation("Jowls", pr + .2617994f + Mth.cos(swing * .6662f) * amount * .25f, yr, 0);
            rotation("bipedRightLeg", Mth.cos(swing * .6662f) * 1.4f * amount, 0, 0);
            rotation("bipedLeftLeg", Mth.cos(swing * .6662f + Mth.PI) * 1.4f * amount, 0, 0);
            rotation("bipedRightArm", Mth.cos(swing * .6662f + Mth.PI) * amount + Mth.sin(age * .067f) * .05f, 0, Mth.cos(age * .09f) * .05f + .05f);
            rotation("bipedLeftArm", Mth.cos(swing * .6662f) * amount - Mth.sin(age * .067f) * .05f, 0, -Mth.cos(age * .09f) * .05f - .05f);
            rotation("LowerPack", .3013602f, Mth.cos(swing * .6662f) * amount * .25f, Mth.cos(swing * .6662f) * amount * .25f);
        } else if (this instanceof LegacyFireBatModel) {
            rotation("batHead", pr, yr, 0);
            rotation("batBody", .7853982f + Mth.cos(age * .1f) * .15f, 0, 0);
            float flap = Mth.cos(age * 1.3f) * Mth.PI * .25f;
            rotation("batRightWing", 0, flap, 0); rotation("batLeftWing", 0, -flap, 0);
            rotation("batOuterRightWing", 0, flap * .5f, 0); rotation("batOuterLeftWing", 0, -flap * .5f, 0);
        } else if (this instanceof LegacyCrossbowModel) {
            rotation("crossbow", pr, yr, 0);
        } else if (this instanceof LegacyArcaneBoreModel) {
            rotation("base", pr, yr, 0);
        } else if (this instanceof LegacyEldritchCrabModel) {
            visible("TailHelm", entity.hasHelm()); visible("TailBare", !entity.hasHelm());
            for (String side : List.of("R", "L")) for (String end : List.of("F", "R")) for (int i = 0; i < 2; i++) {
                boolean right = side.equals("R"); boolean front = end.equals("F");
                float motion = -Mth.cos(swing * .6662f * 2 + (front ? Mth.PI : 0)) * .4f * amount;
                rotation(side + end + "Leg" + i, 0, (front ? -.2094395f : .2094395f) * (right ? 1 : -1) + motion * (right ? 1 : -1), .4363323f * (right ? 1 : -1) + motion * (right ? 1 : -1));
            }
            rotation("RClaw2", .3141593f - Mth.sin(age / 4) * .25f, 0, 0);
            rotation("LClaw2", .3141593f + Mth.sin(age / 4.1f) * .25f, 0, 0);
            rotation("RClaw1", Mth.sin(age / 4) * .125f, 0, 0);
            rotation("LClaw1", -Mth.sin(age / 4.1f) * .125f, 0, 0);
        } else if (this instanceof LegacyEldritchGolemModel) {
            visible("Head", !entity.headless()); visible("Head2", entity.headless());
            rotation("Head", pr / 2, yr / 4, 0); rotation("Head2", pr, yr, 0);
            rotation("LegR", Mth.cos(swing * .4662f) * 1.4f * amount, 0, 0);
            rotation("LegL", Mth.cos(swing * .4662f + Mth.PI) * 1.4f * amount, 0, 0);
            rotation("ArmR", Mth.cos(swing * .4f + Mth.PI) * amount, 0, .1047198f);
            rotation("ArmL", Mth.cos(swing * .4f) * amount, 0, -.1047198f);
        } else if (this instanceof LegacyEldritchGuardianModel) {
            rotation("Head", pr, yr, 0);
            // Model fields are the original uppercase parts, not vanilla humanoid placeholders.
            rotation("ArmL", Mth.sin(age / 10) * .05f, 0, 0);
            rotation("ArmR", -Mth.sin(age / 10) * .05f, 0, 0);
        } else if (this instanceof LegacyTaintacleModel taintacle) {
            for (int k = 0; k < taintacle.tents.length - 1; k++) {
                var node = taintacle.tents[k];
                node.scale = 1;
                node.rotateAngleX = .15f * .1f * Mth.sin(age * .1f - k / 2f);
                node.rotateAngleZ = Mth.sin(age * .15f - k / 2f);
            }
            taintacle.orb.scale = 1;
        } else if (this instanceof LegacyTaintSeedModel seed) {
            for (int k = 0; k < seed.tents.length - 1; k++) {
                var node = seed.tents[k];
                node.scale = 1;
                node.rotateAngleX = .1f / .2625f * Mth.sin(age * .06f - k / 2f) / 5;
                node.rotateAngleZ = .1f / .2625f * Mth.sin(age * .05f - k / 2f) / 5;
            }
        }
        nodes.forEach(LegacyPart::update);
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        pose.pushPose();
        if (currentEntity != null && this instanceof LegacyTaintacleModel) {
            pose.translate(0, currentEntity.getBbHeight() == 3 ? .6 : 1.2, 0);
            float scale = currentEntity.getBbHeight() / 3;
            pose.scale(scale, scale, scale);
        } else if (currentEntity != null && this instanceof LegacyTaintSeedModel) {
            pose.translate(0, currentEntity.getBbHeight() == 3 ? .6 : 1.2, 0);
            float scale = currentEntity.getBbHeight() / 2;
            pose.scale(scale, scale, scale);
        }
        int[] segment={0};
        for (LegacyPart node : nodes) if (node.parent == null && node.part != null) {
            if(this instanceof LegacyTaintSeedModel || this instanceof LegacyTaintacleModel)
                renderTentacle(node,pose,vertices,light,overlay,red,green,blue,alpha,segment);
            else node.part.render(pose, vertices, light, overlay, red, green, blue, alpha);
        }
        pose.popPose();
    }
    private void renderTentacle(LegacyPart node,PoseStack pose,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a,int[] segment) {
        if(!node.visible)return;
        pose.pushPose();node.part.translateAndRotate(pose);
        for(var cube:node.cubes)cube.compile(pose.last(),vertices,light,overlay,r,g,b,a);
        float x=.88f,y=.88f;
        if(this instanceof LegacyTaintSeedModel) {
            float q=Mth.PI*(segment[0]++%8)/7, scale=1.6f-Mth.sin(q), pulse=Mth.sin(currentAge/12-q)*.33f;
            x=scale+pulse*pulse;y=scale*.9f;
        }
        for(var child:node.children) {
            pose.pushPose();pose.scale(x,y,x);renderTentacle(child,pose,vertices,light,overlay,r,g,b,a,segment);pose.popPose();
        }
        pose.popPose();
    }
    public void renderParts(PoseStack pose,VertexConsumer vertices,int light,int overlay,int rgb,String... names) {
        for(String name:names) {var part=named.get(name);if(part!=null)part.part.render(pose,vertices,light,overlay,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1);}
    }
    public int cuboidCount() { return nodes.stream().mapToInt(node -> node.cubes.size()).sum(); }
}
