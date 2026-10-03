package thaumcraft.essentia.transport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.catalog.entities.client.LegacyTubeValveBlockModel;
import thaumcraft.essentia.transport.*;

/** Original ring/rod geometry, closed-valve motion, one-way and buffer choke markers. */
public final class TubeRenderer implements BlockEntityRenderer<TubeBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/valve.png");
    private final LegacyTubeValveBlockModel model = new LegacyTubeValveBlockModel();
    public TubeRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(TubeBlockEntity tube, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String id = tube.id();
        if (id.equals("tube_valve")) valve(tube.facing(), tube.valveRotation(partial), pose, buffers, light, overlay);
        else if (id.equals("tube_oneway") && EssentiaTransportModule.neighbor(tube.getLevel(), tube.getBlockPos(), tube.facing().getOpposite()) != null)
            oneway(tube.facing(), pose, buffers, light, overlay);
        else if (tube instanceof TubeBufferBlockEntity buffer) for (Direction face : Direction.values()) {
            if (buffer.choke(face) == 0 || !buffer.sideOpen(face) || EssentiaTransportModule.neighbor(tube.getLevel(), tube.getBlockPos(), face) == null) continue;
            pose.pushPose(); pose.translate(.5, .5, .5); orient(pose, face.getOpposite());
            pose.scale(2, 1, 2); pose.translate(0, -.5, 0);
            model.renderParts(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay,
                    buffer.choke(face) == 2 ? 0xFF4D4D : 0x4D4DFF, "ValveRod");
            pose.popPose();
        }
    }
    public void renderPreview(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String id = ((TubeBlock) state.getBlock()).id();
        Direction facing = state.hasProperty(TubeBlock.FACING) ? state.getValue(TubeBlock.FACING) : Direction.NORTH;
        if (id.equals("tube_valve")) valve(facing, 0, pose, buffers, light, overlay);
        if (id.equals("tube_oneway")) oneway(facing, pose, buffers, light, overlay);
    }
    private void valve(Direction face, float rotation, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); pose.translate(.5, .5, .5); orient(pose, face);
        pose.mulPose(Axis.YP.rotationDegrees(-rotation * 1.5F));
        pose.translate(0, -.03 - rotation / 360F * .09F, 0);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderParts(pose, vertices, light, overlay, 0xFFFFFF, "ValveRing");
        pose.scale(.75F, 1, .75F); model.renderParts(pose, vertices, light, overlay, 0xFFFFFF, "ValveRod");
        pose.popPose();
    }
    private void oneway(Direction face, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); pose.translate(.5, .5, .5); orient(pose, face); pose.scale(2, 2, 2); pose.translate(0, -.32, 0);
        model.renderParts(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, 0x7380FF, "ValveRod");
        pose.popPose();
    }
    private static void orient(PoseStack pose, Direction direction) {
        if (direction.getStepY() == 0) pose.mulPose(Axis.YP.rotationDegrees(90));
        else { pose.mulPose(Axis.XN.rotationDegrees(90)); pose.mulPose(Axis.XP.rotationDegrees(90 * direction.getStepY())); }
        if (direction.getStepX() != 0) pose.mulPose(Axis.XP.rotationDegrees(90 * direction.getStepX()));
        else if (direction.getStepY() != 0) pose.mulPose(Axis.YP.rotationDegrees(90 * direction.getStepY()));
        else pose.mulPose(Axis.ZP.rotationDegrees(90 * direction.getStepZ()));
    }
}
