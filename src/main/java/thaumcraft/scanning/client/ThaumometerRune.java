package thaumcraft.scanning.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Original FXBlockRunes atlas cells, offsets, colour, lifetime, fade and downward acceleration. */
final class ThaumometerRune {
    private final Vec3 start;
    private final int lifetime, rotation, index, color;
    private final float offsetX, offsetY, size;
    private int age;
    private double previousY, y, velocityY;

    ThaumometerRune(Vec3 center, int duration, RandomSource random) {
        start = center;
        lifetime = Math.max(1, 3 * duration);
        rotation = random.nextInt(4) * 90;
        index = random.nextInt(16);
        offsetX = random.nextFloat() * .2F;
        offsetY = -.3F + random.nextFloat() * .6F;
        size = .3F * (float) (1 + random.nextGaussian() * .1);
        int red = (int) ((.3F + random.nextFloat() * .7F) * 255);
        int blue = (int) ((.3F + random.nextFloat() * .7F) * 255);
        color = red << 16 | blue;
    }

    /** @return whether this effect expired. */
    boolean tick() {
        previousY = y;
        velocityY -= .04 * .03;
        y += velocityY;
        return ++age > lifetime;
    }

    void render(PoseStack pose, Vec3 camera, float partialTick, MultiBufferSource buffers) {
        float time = age + partialTick;
        float threshold = lifetime / 5F;
        float alpha = time <= threshold ? time / threshold : (lifetime - time) / lifetime;
        if (alpha <= 0) return;
        pose.pushPose();
        pose.translate(start.x - camera.x, start.y - camera.y + previousY + (y - previousY) * partialTick, start.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.mulPose(Axis.ZP.rotationDegrees(90));
        pose.translate(offsetX, offsetY, -.51);
        float u0 = index / 64F, u1 = u0 + 1 / 64F;
        float v0 = 6 / 64F, v1 = v0 + 1 / 64F;
        // FXBlockRunes uses a quarter-turned mapping in ParticleEngine's additive layer.
        // NO_CULL makes one quad visible from either side; a reversed copy would double the blend.
        var vertices = buffers.getBuffer(ThaumometerRenderTypes.SPARKLE);
        runePoint(vertices, pose, -size / 2, size / 2, u1, v1, alpha / 2);
        runePoint(vertices, pose, size / 2, size / 2, u1, v0, alpha / 2);
        runePoint(vertices, pose, size / 2, -size / 2, u0, v0, alpha / 2);
        runePoint(vertices, pose, -size / 2, -size / 2, u0, v1, alpha / 2);
        pose.popPose();
    }

    private void runePoint(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack pose,
                           float x, float y, float u, float v, float alpha) {
        vertices.vertex(pose.last().pose(), x, y, 0)
                .color(((color >> 16) & 255) / 255F, 0, (color & 255) / 255F, alpha)
                .uv(u, v).uv2(240 << 16).endVertex();
    }
}
