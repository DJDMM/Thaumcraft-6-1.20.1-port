package thaumcraft.auromancy.media.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import thaumcraft.auromancy.media.SpellBatEntity;

/** Original ModelFireBat cuboids, UVs, pivots and flight flapping; spellbat translucent colour. */
public final class SpellBatModel extends EntityModel<SpellBatEntity> {
    private final ModelPart head,body,right,left,outerRight,outerLeft;
    private int color=0xFFFFFF;
    public SpellBatModel(){
        super(RenderType::entityTranslucent);var root=layer().bakeRoot();head=root.getChild("head");body=root.getChild("body");
        right=body.getChild("right");left=body.getChild("left");outerRight=right.getChild("outer");outerLeft=left.getChild("outer");
    }
    private static LayerDefinition layer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3,-3,-3,6,6,6),PartPose.ZERO);
        head.addOrReplaceChild("ear_right",CubeListBuilder.create().texOffs(24,0).addBox(-4,-6,-2,3,4,1),PartPose.ZERO);
        head.addOrReplaceChild("ear_left",CubeListBuilder.create().texOffs(24,0).mirror().addBox(1,-6,-2,3,4,1),PartPose.ZERO);
        var body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,16).addBox(-3,4,-3,6,12,6).texOffs(0,34).addBox(-5,16,0,10,6,1),PartPose.ZERO);
        var right=body.addOrReplaceChild("right",CubeListBuilder.create().texOffs(42,0).addBox(-12,1,1.5F,10,16,1),PartPose.ZERO);
        right.addOrReplaceChild("outer",CubeListBuilder.create().texOffs(24,16).addBox(-8,1,0,8,12,1),PartPose.offset(-12,1,1.5F));
        var left=body.addOrReplaceChild("left",CubeListBuilder.create().texOffs(42,0).mirror().addBox(2,1,1.5F,10,16,1),PartPose.ZERO);
        left.addOrReplaceChild("outer",CubeListBuilder.create().texOffs(24,16).mirror().addBox(0,1,0,8,12,1),PartPose.offset(12,1,1.5F));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(SpellBatEntity entity,float swing,float amount,float age,float yaw,float pitch){
        color=entity.color();head.xRot=pitch*Mth.DEG_TO_RAD;head.yRot=yaw*Mth.DEG_TO_RAD;
        body.xRot=.7853982F+Mth.cos(age*.1F)*.15F;right.yRot=Mth.cos(age*1.3F)*Mth.PI*.25F;left.yRot=-right.yRot;
        outerRight.yRot=right.yRot*.5F;outerLeft.yRot=-outerRight.yRot;
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer vertices,int light,int overlay,float red,float green,float blue,float alpha){
        float r=((color>>16)&255)/255F,g=((color>>8)&255)/255F,b=(color&255)/255F;
        head.render(pose,vertices,15728880,overlay,r,g,b,.5F*alpha);body.render(pose,vertices,15728880,overlay,r,g,b,.5F*alpha);
    }
}
