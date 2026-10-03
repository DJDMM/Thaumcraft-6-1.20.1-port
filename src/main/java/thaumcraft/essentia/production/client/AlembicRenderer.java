package thaumcraft.essentia.production.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.essentia.production.AlembicBlockEntity;

/** BETA26 paper/aspect label and the two original ModelBoreBase nozzle cuboids. */
public final class AlembicRenderer implements BlockEntityRenderer<AlembicBlockEntity> {
    private static final ResourceLocation LABEL = texture("textures/models/label.png"), BORE = texture("textures/models/bore.png");
    private final ModelPart nozzle;
    public AlembicRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("Nozzle1", CubeListBuilder.create().texOffs(106,42).addBox(2.5F,-2,-2,5,4,4), PartPose.offset(0,8,0));
        root.addOrReplaceChild("Nozzle2", CubeListBuilder.create().texOffs(106,51).addBox(7,-2.5F,-2.5F,1,5,5), PartPose.offset(0,8,0));
        nozzle = LayerDefinition.create(mesh,128,64).bakeRoot();
    }
    @Override public void render(AlembicBlockEntity alembic, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (alembic.filter() != null) {
            pose.pushPose(); pose.translate(.5,.5,.5);
            pose.mulPose(Axis.YP.rotationDegrees(yawFromNorth(alembic.labelFacing())));
            quad(buffers.getBuffer(RenderType.text(LABEL)), pose, .22F,-.376F,0xFFFFFF,1,true);
            // Original drawTag(-8,-8), scale .02 and black/white printed ink.
            quad(buffers.getBuffer(RenderType.text(alembic.filter().getImage())), pose, .16F,-.377F,0x1A1A1A,.8F,false);
            pose.popPose();
        }
        if (alembic.getLevel() == null) return;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var neighbour = alembic.getBlockPos().relative(side);
            if (!alembic.canOutputTo(side) || !alembic.getLevel().hasChunkAt(neighbour)
                    || !(alembic.getLevel().getBlockEntity(neighbour) instanceof IEssentiaTransport transport)
                    || !transport.isConnectable(side.getOpposite()) || !transport.canInputFrom(side.getOpposite())) continue;
            pose.pushPose(); pose.translate(.5,0,.5);
            float yaw = switch(side) { case NORTH -> 90; case SOUTH -> 270; case WEST -> 180; default -> 0; };
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            nozzle.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(BORE)),light,overlay);
            pose.popPose();
        }
    }
    private static float yawFromNorth(Direction face) { return switch(face) { case SOUTH -> 180; case EAST -> -90; case WEST -> 90; default -> 0; }; }
    private static void quad(VertexConsumer vertices, PoseStack pose, float half, float z, int color, float alpha, boolean paper) {
        point(vertices,pose,half,half,z,0,0,color,alpha);
        point(vertices,pose,half,-half,z,paper?1:0,paper?0:1,color,alpha);
        point(vertices,pose,-half,-half,z,1,1,color,alpha);
        point(vertices,pose,-half,half,z,paper?0:1,paper?1:0,color,alpha);
        point(vertices,pose,-half,half,z,paper?0:1,paper?1:0,color,alpha);
        point(vertices,pose,-half,-half,z,1,1,color,alpha);
        point(vertices,pose,half,-half,z,paper?1:0,paper?0:1,color,alpha);
        point(vertices,pose,half,half,z,0,0,color,alpha);
    }
    private static void point(VertexConsumer vertices,PoseStack pose,float x,float y,float z,float u,float v,int color,float alpha) {
        vertices.vertex(pose.last().pose(),x,y,z).color(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,alpha).uv(u,v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft",path); }
}
