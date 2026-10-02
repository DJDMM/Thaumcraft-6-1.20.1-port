package thaumcraft.equipment.cleansing;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** FXGeneric's unrotated, zero-initial-velocity bubble with its original final atlas frames. */
final class CleansingBubble extends Particle {
    private final float scale, randomMotion;
    private final int[] finalFrames;

    CleansingBubble(ClientLevel level, double x, double y, double z, boolean bath, float green, float blue) {
        super(level, x, y, z, 0, 0, 0);
        xd = yd = zd = 0;
        lifetime = (bath ? 10 : 15) + level.random.nextInt(10);
        scale = .3F + level.random.nextFloat() * .3F;
        randomMotion = bath ? .001F : .002F;
        gravity = bath ? -.01F : -.001F;
        rCol = 1; gCol = green; bCol = blue;
        alpha = bath ? .25F : 1;
        finalFrames = bath ? new int[]{65, 66} : new int[]{65, 66, 66};
        setSize(.1F, .1F);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) remove();
        yd -= .04 * gravity;
        move(xd, yd, zd);
        xd *= .9800000190734863; yd *= .9800000190734863; zd *= .9800000190734863;
        xd += level.random.nextGaussian() * randomMotion;
        yd += level.random.nextGaussian() * randomMotion;
        zd += level.random.nextGaussian() * randomMotion;
        if (onGround) { xd *= .699999988079071; zd *= .699999988079071; }
    }

    void draw(VertexConsumer vertices, Matrix4f pose, Camera camera, float partialTick) {
        int frame = age > lifetime - finalFrames.length ? finalFrames[Math.max(0, lifetime - age)] : 64;
        float u0 = (frame % 64) / 64F, u1 = u0 + 1 / 64F;
        float v0 = (frame / 64) / 64F, v1 = v0 + 1 / 64F;
        var eye = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, xo, x) - eye.x);
        float py = (float) (Mth.lerp(partialTick, yo, y) - eye.y);
        float pz = (float) (Mth.lerp(partialTick, zo, z) - eye.z);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        corner(vertices, pose, camera, -1, -1, px, py, pz, u1, v1, light);
        corner(vertices, pose, camera, -1, 1, px, py, pz, u1, v0, light);
        corner(vertices, pose, camera, 1, 1, px, py, pz, u0, v0, light);
        corner(vertices, pose, camera, 1, -1, px, py, pz, u0, v1, light);
    }

    private void corner(VertexConsumer vertices, Matrix4f pose, Camera camera, int sx, int sy,
                        float x, float y, float z, float u, float v, int light) {
        Vector3f corner = new Vector3f(sx, sy, 0).rotate(camera.rotation()).mul(.1F * scale).add(x, y, z);
        vertices.vertex(pose, corner.x(), corner.y(), corner.z()).color(rCol, gCol, bCol, alpha).uv(u, v).uv2(light).endVertex();
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    @Override public void render(VertexConsumer vertices, Camera camera, float partialTick) {
        draw(vertices, new Matrix4f(), camera, partialTick);
    }
}
