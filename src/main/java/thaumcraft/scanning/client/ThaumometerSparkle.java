package thaumcraft.scanning.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** BETA26 scanHighlight's delayed cyan FXGeneric sparkles, with the original looping atlas frames. */
final class ThaumometerSparkle {
    private final RandomSource random;
    private final int lifetime, baseFrame, color;
    private final float scale;
    private final float[] alphaKeys;
    private final double windX, windZ;
    private int delay, age;
    private Vec3 previous, position, velocity = Vec3.ZERO;

    ThaumometerSparkle(Vec3 start, RandomSource random, int moonPhase) {
        this.random = random;
        previous = position = start;
        int red = 16 + random.nextInt(17), green = 132 + random.nextInt(34), blue = 223 + random.nextInt(17);
        color = red << 16 | green << 8 | blue;
        scale = .4F + (float) random.nextGaussian() * .1F;
        delay = random.nextInt(10);
        baseFrame = random.nextFloat() < .2F ? 320 : 512;
        lifetime = 16 + random.nextInt(4);
        alphaKeys = new float[6 + random.nextInt(lifetime / 3)];
        for (int i = 1; i < alphaKeys.length - 1; i++) alphaKeys[i] = random.nextFloat();
        double windAngle = moonPhase * (40 + random.nextInt(10)) * Mth.DEG_TO_RAD;
        windX = Math.cos(windAngle) * .00005;
        windZ = -Math.sin(windAngle) * .00005;
    }

    boolean tick() {
        if (delay > 0) { --delay; return false; }
        previous = position;
        position = position.add(velocity);
        velocity = velocity.add(random.nextGaussian() * .0005 + windX,
                random.nextGaussian() * .001, random.nextGaussian() * .0005 + windZ);
        return ++age > lifetime;
    }

    void render(PoseStack pose, Camera camera, float partialTick, MultiBufferSource buffers) {
        if (delay > 0 || age >= lifetime) return;
        float progress = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        float key = progress * (alphaKeys.length - 1);
        int keyIndex = Math.min(alphaKeys.length - 2, (int) key);
        float alpha = Mth.lerp(key - keyIndex, alphaKeys[keyIndex], alphaKeys[keyIndex + 1]);
        float halfSize = .1F * scale * (1 + progress);
        int frame = baseFrame + age % 16;
        float u = frame % 64 / 64F, v = frame / 64 / 64F;
        Vec3 point = previous.lerp(position, partialTick).subtract(camera.getPosition());
        pose.pushPose();
        pose.translate(point.x, point.y, point.z);
        pose.mulPose(camera.rotation());
        ThaumometerClient.quad(buffers.getBuffer(ThaumometerRenderTypes.SPARKLE), pose,
                -halfSize, -halfSize, halfSize, halfSize, u, v, u + 1 / 64F, v + 1 / 64F,
                color, alpha, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
