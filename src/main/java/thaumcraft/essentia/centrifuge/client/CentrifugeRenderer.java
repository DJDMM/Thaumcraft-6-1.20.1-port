package thaumcraft.essentia.centrifuge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.essentia.centrifuge.CentrifugeBlockEntity;

/** ModelCentrifuge's six cuboids, with independently rotating rotor. */
public final class CentrifugeRenderer implements BlockEntityRenderer<CentrifugeBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/models/centrifuge.png");
    private final ModelPart fixed, rotor;
    public CentrifugeRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition fixedMesh=new MeshDefinition();
        PartDefinition root=fixedMesh.getRoot();
        root.addOrReplaceChild("Top",CubeListBuilder.create().texOffs(20,16).addBox(-4,-8,-4,8,4,8),PartPose.ZERO);
        root.addOrReplaceChild("Bottom",CubeListBuilder.create().texOffs(20,16).addBox(-4,4,-4,8,4,8),PartPose.ZERO);
        fixed=LayerDefinition.create(fixedMesh,64,32).bakeRoot();
        MeshDefinition rotorMesh=new MeshDefinition();
        PartDefinition spin=rotorMesh.getRoot();
        // 1.12 addBox constructs ModelBox before ModelCentrifuge assigns mirror=true;
        // its effective box UVs use the original false value.
        spin.addOrReplaceChild("Crossbar",CubeListBuilder.create().texOffs(16,0).addBox(-4,-1,-1,8,2,2),PartPose.ZERO);
        spin.addOrReplaceChild("Dingus1",CubeListBuilder.create().texOffs(0,16).addBox(4,-3,-2,4,6,4),PartPose.ZERO);
        spin.addOrReplaceChild("Dingus2",CubeListBuilder.create().texOffs(0,16).addBox(-8,-3,-2,4,6,4),PartPose.ZERO);
        spin.addOrReplaceChild("Core",CubeListBuilder.create().texOffs(0,0).addBox(-1.5F,-4,-1.5F,3,8,3),PartPose.ZERO);
        rotor=LayerDefinition.create(rotorMesh,64,32).bakeRoot();
    }
    @Override public void render(CentrifugeBlockEntity tile,float partialTicks,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        renderGeometry(tile.rotationDegrees(),pose,buffers,light,overlay);
    }
    public void renderGeometry(float rotation,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose(); pose.translate(.5,.5,.5);
        var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        fixed.render(pose,vertices,light,overlay);
        // Original renderer uses rotation directly and ignores partialTicks.
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        rotor.render(pose,vertices,light,overlay); pose.popPose();
    }
}
