package thaumcraft.equipment.armor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

import javax.annotation.Nullable;

/** BETA26 goggles reveal a container's synchronized contents; they do not enable the thaumometer aura meter. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class GogglesArmorClient {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1024));
    @Nullable private static BlockPos scaleTarget;
    private static float scale;
    private GogglesArmorClient() {}

    private record Container(BlockPos pos, Direction face, AspectList aspects) {}

    @Nullable private static Container container() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null || mc.isPaused()
                || !mc.player.isAlive() || mc.player.isSpectator() || !GogglesArmorSupport.hasGoggles(mc.player)
                || !(mc.hitResult instanceof BlockHitResult hit)) return null;
        var blockEntity = mc.level.getBlockEntity(hit.getBlockPos());
        if (!(blockEntity instanceof IAspectContainer holder)) return null;
        AspectList aspects = holder.getAspects();
        return aspects == null || aspects.size() == 0 ? null : new Container(hit.getBlockPos(), hit.getDirection(), aspects);
    }

    /** Scanner's generic block-composition overlay can defer to this original goggles contents popup. */
    public static boolean hasContainerPopup(BlockPos pos) {
        Container target = container();
        return target != null && target.pos().equals(pos);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        Container target = container();
        if (target == null) { scaleTarget = null; scale = 0; return; }
        if (!target.pos().equals(scaleTarget)) scale = 0;
        scaleTarget = target.pos();
        scale = Math.max(0, scale - .005F);
        if (scale < .3F) scale += .031F - scale / 10F;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Container target = container();
        if (target == null || scale <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean spaceAbove = mc.level.isEmptyBlock(target.pos().above());
        Direction face = spaceAbove ? Direction.UP : target.face();
        Vec3 origin = Vec3.atCenterOf(target.pos()).add(0, spaceAbove ? .4 : 0, 0)
                .add(face.getStepX() * scale * 2, face.getStepY() * scale * 2, face.getStepZ() * scale * 2);
        Vec3 camera = event.getCamera().getPosition();
        float yaw = (float)Math.toDegrees(Math.atan2(camera.x - origin.x, camera.z - origin.z));
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        var aspects = target.aspects().getAspects();
        for (int i = 0; i < aspects.length; i++) {
            var aspect = aspects[i];
            int row = i / 5, columns = Math.min(5, aspects.length - row * 5);
            float x = (i % 5 - columns / 2F + .5F) * scale * 4 * scale;
            pose.pushPose();
            pose.translate(-x, row * scale * 1.05F, 0);
            pose.scale(scale, scale, scale);
            var vertices = BUFFERS.getBuffer(RenderType.textSeeThrough(aspect.getImage()));
            int color = aspect.getColor();
            point(vertices, pose, -.5F, .5F, 0, 0, color);
            point(vertices, pose, -.5F, -.5F, 0, 1, color);
            point(vertices, pose, .5F, -.5F, 1, 1, color);
            point(vertices, pose, .5F, .5F, 1, 0, color);
            pose.scale(.04F, -.04F, -.04F);
            String count = Integer.toString(target.aspects().getAmount(aspect));
            mc.font.drawInBatch(count, 14 - mc.font.width(count), 7, 0xFF111111, false,
                    pose.last().pose(), BUFFERS, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            mc.font.drawInBatch(count, 13 - mc.font.width(count), 6, 0xFFFFFFFF, false,
                    pose.last().pose(), BUFFERS, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
        BUFFERS.endBatch();
    }

    private static void point(VertexConsumer vertices, PoseStack pose, float x, float y, float u, float v, int color) {
        vertices.vertex(pose.last().pose(), x, y, 0)
                .color(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, .75F)
                .uv(u, v).uv2(220 << 16).endVertex();
    }
}
