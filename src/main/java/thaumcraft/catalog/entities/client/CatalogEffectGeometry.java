package thaumcraft.catalog.entities.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Render-only BETA26 geometry. No entities, collision, target steering, sounds or aura changes.
 *
 * Sources: EntityFluxRift.calcSteps, RenderFluxRift.doRender and bundled CoreGLE's
 * gen_polycone/extrusion_angle_join; projectile/RenderDart.renderArrow (the actual renderer
 * of EntityGolemDart); FXSwarm.renderParticle and FXDispatcher.swarmParticleFX;
 * projectile/RenderEldritchOrb.renderEntityAt for its twelve ray fans.
 *
 * Rift spine, radius animation, six-sided contour and angle joins preserve the original math.
 * The 1.20 end-portal shader is the explicit material adaptation; BETA26's GLSL 1.20
 * ender.frag, camera uniforms and goggles depth override are not recreated by this helper.
 */
public final class CatalogEffectGeometry {
    public static final ResourceLocation DART_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/projectiles/arrow.png");
    public static final ResourceLocation PARTICLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png");
    private static final int CONTOUR_SIDES = 6;
    private static final double DEGENERATE_TOLERANCE = 2.0E-6;

    private CatalogEffectGeometry() {}

    /** Immutable spine/width values, exposed so catalogue bounds and checks use the same geometry. */
    public record RiftPath(List<Vec3> points, List<Float> widths) {
        public RiftPath { points = List.copyOf(points); widths = List.copyOf(widths); }
    }

    /** Exact BETA26 calcSteps draw order, float girth subtraction, 0.2 steps and 0.1 guide endpoints. */
    public static RiftPath riftPath(int seed, int size) {
        List<Vec3> points = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        if (size <= 0) return new RiftPath(points, widths);
        Random random = new Random(seed);
        Vec3 right = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
        Vec3 left = right.scale(-1.0);
        Vec3 rightPosition = Vec3.ZERO;
        Vec3 leftPosition = Vec3.ZERO;
        int steps = Mth.ceil(size / 3.0F);
        float girth = size / 300.0F;
        float decrement = girth / steps;
        for (int step = 0; step < steps; step++) {
            girth -= decrement;
            right = right.xRot((float) (random.nextGaussian() * .33));
            right = right.yRot((float) (random.nextGaussian() * .33));
            rightPosition = rightPosition.add(right.scale(.2));
            points.add(rightPosition);
            widths.add(girth);
            left = left.xRot((float) (random.nextGaussian() * .33));
            left = left.yRot((float) (random.nextGaussian() * .33));
            leftPosition = leftPosition.add(left.scale(.2));
            points.add(0, leftPosition);
            widths.add(0, girth);
        }
        points.add(rightPosition.add(right.scale(.1)));
        widths.add(0.0F);
        points.add(0, leftPosition.add(left.scale(.1)));
        widths.add(0, 0.0F);
        return new RiftPath(points, widths);
    }

    /** Neutral stability is BETA26 stability zero, giving stab=1. Age is ticks plus partial tick. */
    public static void fluxRift(PoseStack pose, MultiBufferSource buffers, int seed, int size, float age) {
        fluxRift(pose, buffers, seed, size, age, 0.0F);
    }

    public static void fluxRift(PoseStack pose, MultiBufferSource buffers, int seed, int size,
                                float age, float stability) {
        RiftPath path = riftPath(seed, size);
        if (path.points().size() <= 2) return;
        float stab = Mth.clamp(1.0F - stability / 50.0F, 0.0F, 1.5F);
        for (int pass = 0; pass <= 3; pass++) {
            Vec3[] points = new Vec3[path.points().size()];
            double[] radii = new double[points.length];
            for (int index = 0; index < points.length; index++) {
                float phase = age;
                if (index > points.length / 2) phase -= index * 10;
                else if (index < points.length / 2) phase += index * 10;
                points[index] = path.points().get(index).add(
                        Math.sin(phase / 50.0F) * 0.10000000149011612 * stab,
                        Math.sin(phase / 60.0F) * 0.10000000149011612 * stab,
                        Math.sin(phase / 70.0F) * 0.10000000149011612 * stab);
                double pulse = 1.0 - Math.sin(phase / 8.0F) * 0.10000000149011612 * stab;
                radii[index] = path.widths().get(index) * pulse * (pass < 3 ? 1.25F + .5F * pass : 1.0F);
            }
            polycone(pose, buffers.getBuffer(EffectRenderTypes.RIFT[pass]), points, radii);
        }
    }

    /**
     * CoreGLE TUBE_JN_ANGLE | TUBE_NORM_PATH_EDGE (1026), closed six-point contour.
     * First/last points orient the end joins and are not themselves rendered segments.
     * Intersections are with the actual bisector planes, rather than independent cylinder rings.
     */
    private static void polycone(PoseStack pose, VertexConsumer vertices, Vec3[] points, double[] radii) {
        Vec3 firstDirection = nextPoint(points, 0).difference();
        Vec3 up = firstDirection.x == 0.0 && firstDirection.z == 0.0 ? new Vec3(1, 1, 1) : new Vec3(0, 1, 0);
        Vec3 initialDirection = points[1].subtract(points[0]);
        if (initialDirection.length() == 0.0) {
            for (int point = 1; point < points.length - 2; point++) {
                initialDirection = points[point + 1].subtract(points[point]);
                if (initialDirection.length() != 0.0) break;
            }
        }
        initialDirection = unit(initialDirection);
        up = perpendicular(up, initialDirection);
        if (up.lengthSqr() == 0.0) up = initialDirection;
        int index = 1;
        SpineStep step = nextPoint(points, index);
        int next = step.index();
        double length = step.difference().length();
        Vec3 frontBisector = bisector(points[0], points[1], points[next]);
        up = reflect(up, frontBisector);
        double[][] contour = new double[CONTOUR_SIDES][2];
        double sine = Math.sin(2 * Math.PI / CONTOUR_SIDES);
        double cosine = Math.cos(2 * Math.PI / CONTOUR_SIDES);
        contour[0][0] = 1;
        for (int side = 1; side < CONTOUR_SIDES; side++) {
            contour[side][0] = contour[side - 1][0] * cosine - contour[side - 1][1] * sine;
            contour[side][1] = contour[side - 1][0] * sine + contour[side - 1][1] * cosine;
        }
        while (next < points.length - 1) {
            SpineStep following = nextPoint(points, next);
            int after = following.index();
            Vec3 backBisector = bisector(points[index], points[next], points[after]);
            double[][] frame = viewDirection(points[next].subtract(points[index]), up);
            Vec3 localFrontNormal = local(frame, frontBisector);
            Vec3 localBackNormal = local(frame, backBisector);
            Vec3[] front = new Vec3[CONTOUR_SIDES];
            Vec3[] back = new Vec3[CONTOUR_SIDES];
            for (int side = 0; side < CONTOUR_SIDES; side++) {
                // GLE uses a constant start radius for each front intersection and end radius for each back intersection.
                Vec3 start = new Vec3(contour[side][0] * radii[next - 1], contour[side][1] * radii[next - 1], 0);
                Vec3 end = new Vec3(contour[side][0] * radii[next], contour[side][1] * radii[next], 0);
                front[side] = world(frame, points[index], planeIntersection(Vec3.ZERO, localFrontNormal,
                        start, start.add(0, 0, -length)));
                back[side] = world(frame, points[index], planeIntersection(new Vec3(0, 0, -length), localBackNormal,
                        end, end.add(0, 0, -length)));
            }
            for (int side = 0; side < CONTOUR_SIDES; side++) {
                int other = (side + 1) % CONTOUR_SIDES;
                // Same two triangles as the original front/back GL_TRIANGLE_STRIP.
                position(vertices, pose.last(), front[side]);
                position(vertices, pose.last(), back[side]);
                position(vertices, pose.last(), front[other]);
                position(vertices, pose.last(), front[other]);
                position(vertices, pose.last(), back[side]);
                position(vertices, pose.last(), back[other]);
            }
            index = next;
            next = after;
            length = following.difference().length();
            frontBisector = backBisector;
            up = reflect(up, frontBisector);
        }
    }

    private record SpineStep(int index, Vec3 difference) {}

    private static SpineStep nextPoint(Vec3[] points, int index) {
        int result = index;
        Vec3 difference;
        do {
            difference = points[result + 1].subtract(points[result]);
            double threshold = points[result + 1].add(points[result]).length() * DEGENERATE_TOLERANCE;
            result++;
            if (difference.length() > threshold) break;
        } while (result < points.length - 1);
        return new SpineStep(result, difference);
    }

    private static Vec3 bisector(Vec3 before, Vec3 centre, Vec3 after) {
        Vec3 incoming = centre.subtract(before), outgoing = after.subtract(centre);
        double incomingLength = incoming.length(), outgoingLength = outgoing.length();
        if (incomingLength <= DEGENERATE_TOLERANCE * outgoingLength) return unit(outgoing);
        if (outgoingLength <= DEGENERATE_TOLERANCE * incomingLength) return unit(incoming);
        incoming = incoming.scale(1.0 / incomingLength);
        outgoing = outgoing.scale(1.0 / outgoingLength);
        double dot = outgoing.dot(incoming);
        if (dot >= .999998 || dot <= -.999998) return incoming;
        return unit(new Vec3(dot * (outgoing.x + incoming.x) - outgoing.x - incoming.x,
                dot * (outgoing.y + incoming.y) - outgoing.y - incoming.y,
                dot * (outgoing.z + incoming.z) - outgoing.z - incoming.z));
    }

    private static Vec3 planeIntersection(Vec3 planePoint, Vec3 normal, Vec3 first, Vec3 second) {
        double denominator = first.subtract(second).dot(normal);
        if (denominator == 0.0) return Vec3.ZERO; // CoreGLE INNERSECT returns its zero-initialized result in this case.
        double t = planePoint.subtract(second).dot(normal) / denominator;
        return first.scale(t).add(second.scale(1.0 - t));
    }

    // CoreGLE's row-major uview_direction_d. Retaining its arithmetic avoids a different seam twist.
    private static double[][] viewDirection(Vec3 direction, Vec3 up) {
        Vec3 axis = unit(direction);
        double length = direction.length();
        double[][] rotateY = length != 0.0 ? new double[][] {
                {-axis.z, 0, Math.sqrt(1.0 - axis.z * axis.z)}, {0, 1, 0},
                {-Math.sqrt(1.0 - axis.z * axis.z), 0, -axis.z}} : identity();
        double xyLength = Math.sqrt(direction.x * direction.x + direction.y * direction.y);
        double[][] rotation = xyLength != 0.0 ? multiply(rotateY,
                rotateZ(direction.x / xyLength, direction.y / xyLength)) : rotateY;
        Vec3 projection = perpendicular(up, axis);
        if (projection.lengthSqr() != 0.0) {
            projection = unit(projection);
            double cosine = projection.dot(row(rotation, 1));
            double sine = projection.dot(row(rotation, 0));
            rotation = multiply(rotateZ(cosine, -sine), rotation);
        }
        return rotation;
    }

    private static double[][] identity() { return new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}; }
    private static double[][] rotateZ(double cosine, double sine) {
        return new double[][] {{cosine, sine, 0}, {-sine, cosine, 0}, {0, 0, 1}};
    }
    private static double[][] multiply(double[][] first, double[][] second) {
        double[][] result = new double[3][3];
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++)
            result[row][column] = first[row][0] * second[0][column] + first[row][1] * second[1][column]
                    + first[row][2] * second[2][column];
        return result;
    }
    private static Vec3 row(double[][] matrix, int row) { return new Vec3(matrix[row][0], matrix[row][1], matrix[row][2]); }
    private static Vec3 local(double[][] matrix, Vec3 value) {
        return new Vec3(row(matrix, 0).dot(value), row(matrix, 1).dot(value), row(matrix, 2).dot(value));
    }
    private static Vec3 world(double[][] matrix, Vec3 origin, Vec3 value) {
        return origin.add(matrix[0][0] * value.x + matrix[1][0] * value.y + matrix[2][0] * value.z,
                matrix[0][1] * value.x + matrix[1][1] * value.y + matrix[2][1] * value.z,
                matrix[0][2] * value.x + matrix[1][2] * value.y + matrix[2][2] * value.z);
    }
    private static Vec3 unit(Vec3 value) { double length = value.length(); return length == 0 ? Vec3.ZERO : value.scale(1.0 / length); }
    private static Vec3 perpendicular(Vec3 value, Vec3 normal) { return value.subtract(normal.scale(value.dot(normal))); }
    private static Vec3 reflect(Vec3 value, Vec3 normal) { return value.subtract(normal.scale(2.0 * value.dot(normal))); }
    private static void position(VertexConsumer vertices, PoseStack.Pose pose, Vec3 position) {
        vertices.vertex(pose.pose(), (float) position.x, (float) position.y, (float) position.z).endVertex();
    }

    public static void dart(PoseStack pose, MultiBufferSource buffers, int light, float yaw, float pitch) {
        dart(pose, buffers, light, yaw, pitch, 0.0F, 0.0F);
    }

    /** Yaw/pitch already interpolated by the caller; shake is arrowShake and partialTick is the render fraction. */
    public static void dart(PoseStack pose, MultiBufferSource buffers, int light, float yaw, float pitch,
                            float shake, float partialTick) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(pitch));
        float remainingShake = shake - partialTick;
        if (remainingShake > 0.0F)
            pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(remainingShake * 3.0F) * remainingShake));
        pose.mulPose(Axis.XP.rotationDegrees(45));
        pose.scale(.033F, .033F, .033F);
        pose.translate(-4, 0, 0);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(DART_TEXTURE));
        textured(vertices, pose.last(), -7, -2, -2, 0, 5 / 32.0F, 1, 0, 0, light);
        textured(vertices, pose.last(), -7, -2, 2, 5 / 32.0F, 5 / 32.0F, 1, 0, 0, light);
        textured(vertices, pose.last(), -7, 2, 2, 5 / 32.0F, 10 / 32.0F, 1, 0, 0, light);
        textured(vertices, pose.last(), -7, 2, -2, 0, 10 / 32.0F, 1, 0, 0, light);
        textured(vertices, pose.last(), -7, 2, -2, 0, 5 / 32.0F, -1, 0, 0, light);
        textured(vertices, pose.last(), -7, 2, 2, 5 / 32.0F, 5 / 32.0F, -1, 0, 0, light);
        textured(vertices, pose.last(), -7, -2, 2, 5 / 32.0F, 10 / 32.0F, -1, 0, 0, light);
        textured(vertices, pose.last(), -7, -2, -2, 0, 10 / 32.0F, -1, 0, 0, light);
        for (int fin = 0; fin < 4; fin++) {
            pose.mulPose(Axis.XP.rotationDegrees(90));
            textured(vertices, pose.last(), -8, -2, 0, 0, 0, 0, 0, 1, light);
            textured(vertices, pose.last(), 8, -2, 0, .5F, 0, 0, 0, 1, light);
            textured(vertices, pose.last(), 8, 2, 0, .5F, 5 / 32.0F, 0, 0, 1, light);
            textured(vertices, pose.last(), -8, 2, 0, 0, 5 / 32.0F, 0, 0, 1, light);
        }
        pose.popPose();
    }

    private static void textured(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
                                  float u, float v, float nx, float ny, float nz, int light) {
        vertices.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    /**
     * Thirty neutral, alive FXSwarm sprites. Seeded spawn positions/colours follow FXDispatcher;
     * layout is held at those spawn positions, deliberately omitting target-seeking particle AI.
     * The eight-frame UV animation and sine size bob are the original renderParticle formulas.
     */
    public static void swarm(PoseStack pose, MultiBufferSource buffers, int seed, float age) {
        Random spawn = new Random(seed);
        VertexConsumer vertices = buffers.getBuffer(EffectRenderTypes.SWARM);
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (int insect = 0; insect < 30; insect++) {
            float x = (spawn.nextFloat() - spawn.nextFloat()) * 2.0F;
            float y = (spawn.nextFloat() - spawn.nextFloat()) * 2.0F;
            float z = (spawn.nextFloat() - spawn.nextFloat()) * 2.0F;
            float red = .8F + spawn.nextFloat() * .2F;
            float green = spawn.nextFloat() * .4F;
            float blue = 1.0F - spawn.nextFloat() * .2F;
            float scale = new Random((long) seed * 31 + insect).nextFloat() * .5F + 1.0F;
            int particleAge = Math.max(0, (int) age - insect);
            float halfSize = .1F * scale * (Mth.sin(particleAge / 3.0F) * .25F + 1.0F);
            float u0 = (7 + particleAge % 8) / 64.0F, u1 = u0 + .015625F;
            float v0 = .0625F, v1 = v0 + .015625F;
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(camera);
            // FXSwarm vertex alpha is trans=(50-deathtimer)/50, which is one for this alive catalogue state.
            swarmVertex(vertices, pose.last(), -halfSize, -halfSize, u1, v1, red, green, blue);
            swarmVertex(vertices, pose.last(), -halfSize, halfSize, u1, v0, red, green, blue);
            swarmVertex(vertices, pose.last(), halfSize, halfSize, u0, v0, red, green, blue);
            swarmVertex(vertices, pose.last(), halfSize, -halfSize, u0, v1, red, green, blue);
            pose.popPose();
        }
    }

    private static void swarmVertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y,
                                    float u, float v, float red, float green, float blue) {
        // Legacy lightmap(j,k) writes (0,240) for the fixed i=240 in FXSwarm.
        vertices.vertex(pose.pose(), x, y, 0).color(red, green, blue, 1.0F).uv(u, v).uv2(0, 240).endVertex();
    }

    /**
     * The twelve cumulative, seed-187 triangle fans of RenderEldritchOrb, excluding its sprite.
     * BETA26 uses integer ticks here, ignoring render partial tick. The odd float colour call
     * at fan tips is confirmed by the pinned JAR: (64,64,64,255) is passed to the FLOAT overload,
     * so its byte-packed values are (192,192,192,1), not an intended dark opaque gray.
     */
    public static void eldritchOrbRays(PoseStack pose, MultiBufferSource buffers, float age) {
        int ticks = Math.max(0, (int) age);
        Random random = new Random(187L);
        float spin = ticks / 80.0F;
        float growthDivisor = 30.0F / (Math.min(ticks, 10) / 10.0F);
        VertexConsumer vertices = buffers.getBuffer(EffectRenderTypes.ORB_RAYS);
        pose.pushPose();
        for (int ray = 0; ray < 12; ray++) {
            pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F + spin * 360.0F));
            float length = (random.nextFloat() * 20.0F + 5.0F) / growthDivisor;
            float width = (random.nextFloat() * 2.0F + 1.0F) / growthDivisor;
            Vec3 first = new Vec3(-.866 * width, length, -.5F * width);
            Vec3 second = new Vec3(.866 * width, length, -.5F * width);
            Vec3 third = new Vec3(0, length, width);
            orbRayVertex(vertices, pose.last(), Vec3.ZERO, true);
            orbRayVertex(vertices, pose.last(), first, false);
            orbRayVertex(vertices, pose.last(), second, false);
            orbRayVertex(vertices, pose.last(), Vec3.ZERO, true);
            orbRayVertex(vertices, pose.last(), second, false);
            orbRayVertex(vertices, pose.last(), third, false);
            orbRayVertex(vertices, pose.last(), Vec3.ZERO, true);
            orbRayVertex(vertices, pose.last(), third, false);
            orbRayVertex(vertices, pose.last(), first, false);
        }
        pose.popPose();
    }

    private static void orbRayVertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, boolean centre) {
        vertices.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(centre ? 255 : 192, centre ? 255 : 192, centre ? 255 : 192, centre ? 255 : 1).endVertex();
    }

    /** Protected render-state access is kept in this file; no global GL state escapes a draw. */
    private static final class EffectRenderTypes extends RenderType {
        private EffectRenderTypes() {
            super("tc_catalog_effect_state", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {});
        }
        private static final RenderType[] RIFT = {
                rift(0), rift(1), rift(2), rift(3)
        };
        private static final RenderType SWARM = create("tc_catalog_swarm", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                        .setTextureState(new TextureStateShard(PARTICLE_TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        private static final RenderType ORB_RAYS = create("tc_catalog_eldritch_orb_rays", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES, 4096, false, false, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

        private static RenderType rift(int pass) {
            return create("tc_catalog_flux_rift_" + pass, DefaultVertexFormat.POSITION,
                    VertexFormat.Mode.TRIANGLES, 8192, false, false, CompositeState.builder()
                            .setShaderState(RENDERTYPE_END_PORTAL_SHADER)
                            .setTextureState(MultiTextureStateShard.builder()
                                    .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                                    .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false).build())
                            .setTransparencyState(pass < 3 ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setWriteMaskState(pass < 3 ? COLOR_WRITE : COLOR_DEPTH_WRITE)
                            .createCompositeState(false));
        }
    }
}
