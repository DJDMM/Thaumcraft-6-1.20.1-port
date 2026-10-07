package thaumcraft.golemancy.jar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.catalog.entities.client.LegacyBrainBlockModel;
import thaumcraft.catalog.entities.client.LegacyJarBlockModel;
import thaumcraft.golemancy.jar.BrainJarBlockEntity;

/** Original brain cuboids, brine and target yaw; the placed block supplies transparent glass. */
public final class BrainJarRenderer implements BlockEntityRenderer<BrainJarBlockEntity> {
    private static final ResourceLocation BRAIN = texture("brain2"), BRINE = texture("jarbrine");
    private final LegacyBrainBlockModel brain = new LegacyBrainBlockModel();
    private final LegacyJarBlockModel jar = new LegacyJarBlockModel();
    public BrainJarRenderer(BlockEntityRendererProvider.Context context) {}
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/" + name + ".png"); }
    @Override public void render(BrainJarBlockEntity tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var player = Minecraft.getInstance().player;
        float bob = player == null ? 0 : Mth.sin(player.tickCount / 14F) * .03F + .03F;
        renderContents(tile.rotation(partial), bob, pose, buffers, light, overlay);
    }
    public void renderContents(float rotation, float bob, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, .01, .5); pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.pushPose(); pose.translate(0, -.8 + bob, 0);
        pose.mulPose(Axis.YP.rotation(rotation)); pose.mulPose(Axis.YP.rotationDegrees(-90)); pose.scale(.4F, .4F, .4F);
        brain.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(BRAIN)), light, overlay, 1, 1, 1, 1);
        pose.popPose();
        jar.renderParts(pose, buffers.getBuffer(RenderType.entityTranslucent(BRINE)), light, overlay, 0xFFFFFF, "Brine");
        pose.popPose();
    }
}
