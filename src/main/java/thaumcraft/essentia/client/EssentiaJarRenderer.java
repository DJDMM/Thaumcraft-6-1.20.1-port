package thaumcraft.essentia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.catalog.entities.client.LegacyJarBlockModel;
import thaumcraft.essentia.EssentiaJarBlockEntity;

/** BETA26 jar contents and attachments; the transparent block model supplies the glass. */
public final class EssentiaJarRenderer implements BlockEntityRenderer<EssentiaJarBlockEntity> {
    private static final ResourceLocation LABEL = texture("textures/models/label.png");
    private static final ResourceLocation BRACE = texture("textures/models/jarbrine.png");
    private static final ResourceLocation LIQUID = texture("blocks/animatedglow");
    // RenderCubes receives brightness=200, then emits lightmap(bright>>16, bright&65535).
    // Keep those actual (0,200) UV2 coordinates rather than substituting full-bright light.
    private static final int LIQUID_LIGHT = 200 << 16;
    private static final float MIN = .25F, MAX = .75F, BOTTOM = .0625F;
    private final LegacyJarBlockModel model = new LegacyJarBlockModel();

    public EssentiaJarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(EssentiaJarBlockEntity jar, float partialTicks, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        renderLid(jar, pose, buffers, light, overlay);
        if (jar.filter() != null) renderLabel(jar, pose, buffers);
        if (jar.amount() > 0) renderLiquid(jar, pose, buffers);
    }

    private void renderLid(EssentiaJarBlockEntity jar, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
        boolean connected = jar.getLevel() != null && jar.getLevel().hasChunkAt(jar.getBlockPos().above())
                && jar.getLevel().getBlockEntity(jar.getBlockPos().above()) instanceof IEssentiaTransport source
                && source.isConnectable(Direction.DOWN);
        if (!jar.blocked() && !connected) return;
        pose.pushPose();
        pose.translate(.5, .01, .5);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(BRACE));
        if (jar.blocked()) {
            pose.pushPose();
            pose.scale(1.001F, 1.001F, 1.001F);
            model.renderParts(pose, vertices, light, overlay, 0xFFFFFF, "Lid");
            pose.popPose();
        }
        if (connected) {
            pose.scale(.9F, 1, .9F);
            model.renderParts(pose, vertices, light, overlay, 0xFFFFFF, "LidExtension");
        }
        pose.popPose();
    }

    private static void renderLabel(EssentiaJarBlockEntity jar, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(.5, .41, .5);
        // The original label sits at local +Z under a 180-degree X flip. Here the quad
        // starts facing world NORTH, so EAST/WEST yaw signs are inverted accordingly.
        float yaw = switch (jar.facing()) {
            case SOUTH -> 180;
            case EAST -> -90;
            case WEST -> 90;
            default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        // Original optional graphics.crooked rotation is intentionally omitted.
        labelQuad(buffers.getBuffer(RenderType.text(LABEL)), pose, .25F, -.315F, 0xFFFFFF, 1, true);
        Aspect filter = jar.filter();
        // UtilsFX.drawTag(x,y,aspect) uses bw=true: dark ink, 80% alpha, not aspect colour.
        labelQuad(buffers.getBuffer(RenderType.text(filter.getImage())), pose, 8 * .021F, -.316F,
                0x1A1A1A, .8F, false);
        pose.popPose();
    }

    private static void labelQuad(VertexConsumer vertices, PoseStack pose, float halfSize, float z,
                                  int color, float alpha, boolean paper) {
        // The paper keeps renderQuadCentered's original rotated UVs; the printed icon is upright.
        point(vertices, pose, halfSize, halfSize, z, 0, 0, color, alpha, LightTexture.FULL_BRIGHT);
        point(vertices, pose, halfSize, -halfSize, z, paper ? 1 : 0, paper ? 0 : 1,
                color, alpha, LightTexture.FULL_BRIGHT);
        point(vertices, pose, -halfSize, -halfSize, z, 1, 1, color, alpha, LightTexture.FULL_BRIGHT);
        point(vertices, pose, -halfSize, halfSize, z, paper ? 0 : 1, paper ? 1 : 0,
                color, alpha, LightTexture.FULL_BRIGHT);
        // TC6 disables culling for the attachments. RenderType.text culls, so emit the
        // reverse face as well; the paper can still be seen through the opposite glass wall.
        point(vertices, pose, -halfSize, halfSize, z, paper ? 0 : 1, paper ? 1 : 0,
                color, alpha, LightTexture.FULL_BRIGHT);
        point(vertices, pose, -halfSize, -halfSize, z, 1, 1, color, alpha, LightTexture.FULL_BRIGHT);
        point(vertices, pose, halfSize, -halfSize, z, paper ? 1 : 0, paper ? 0 : 1,
                color, alpha, LightTexture.FULL_BRIGHT);
        point(vertices, pose, halfSize, halfSize, z, 0, 0, color, alpha, LightTexture.FULL_BRIGHT);
    }

    private static void renderLiquid(EssentiaJarBlockEntity jar, PoseStack pose, MultiBufferSource buffers) {
        float top = BOTTOM + jar.amount() / 250F * .625F;
        int color = jar.aspect() == null ? 0 : jar.aspect().getColor();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(LIQUID);
        // The text shader is an unshaded POSITION_COLOR_TEX_LIGHTMAP pass. It matches the
        // original GL_LIGHTING-disabled fluid while retaining its lightmap and alpha/depth tests.
        VertexConsumer vertices = buffers.getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
        float u0 = sprite.getU(4), u1 = sprite.getU(12);
        float v0 = sprite.getV(4), v1 = sprite.getV(12);
        float vTop = sprite.getV((1 - top) * 16), vBottom = sprite.getV(15);
        pose.pushPose();
        // Original TileJarRenderer's root offset remains after its two cancelling X flips.
        pose.translate(0, .01, 0);
        // Original RenderCubes face order and bounds-relative UVs, without invented ripples.
        liquidPoint(vertices, pose, MIN, BOTTOM, MAX, u0, v1, color);
        liquidPoint(vertices, pose, MIN, BOTTOM, MIN, u0, v0, color);
        liquidPoint(vertices, pose, MAX, BOTTOM, MIN, u1, v0, color);
        liquidPoint(vertices, pose, MAX, BOTTOM, MAX, u1, v1, color);

        liquidPoint(vertices, pose, MAX, top, MAX, u1, v1, color);
        liquidPoint(vertices, pose, MAX, top, MIN, u1, v0, color);
        liquidPoint(vertices, pose, MIN, top, MIN, u0, v0, color);
        liquidPoint(vertices, pose, MIN, top, MAX, u0, v1, color);

        liquidPoint(vertices, pose, MIN, top, MIN, u1, vTop, color);
        liquidPoint(vertices, pose, MAX, top, MIN, u0, vTop, color);
        liquidPoint(vertices, pose, MAX, BOTTOM, MIN, u0, vBottom, color);
        liquidPoint(vertices, pose, MIN, BOTTOM, MIN, u1, vBottom, color);

        liquidPoint(vertices, pose, MIN, top, MAX, u0, vTop, color);
        liquidPoint(vertices, pose, MIN, BOTTOM, MAX, u0, vBottom, color);
        liquidPoint(vertices, pose, MAX, BOTTOM, MAX, u1, vBottom, color);
        liquidPoint(vertices, pose, MAX, top, MAX, u1, vTop, color);

        liquidPoint(vertices, pose, MIN, top, MAX, u1, vTop, color);
        liquidPoint(vertices, pose, MIN, top, MIN, u0, vTop, color);
        liquidPoint(vertices, pose, MIN, BOTTOM, MIN, u0, vBottom, color);
        liquidPoint(vertices, pose, MIN, BOTTOM, MAX, u1, vBottom, color);

        liquidPoint(vertices, pose, MAX, BOTTOM, MAX, u0, vBottom, color);
        liquidPoint(vertices, pose, MAX, BOTTOM, MIN, u1, vBottom, color);
        liquidPoint(vertices, pose, MAX, top, MIN, u1, vTop, color);
        liquidPoint(vertices, pose, MAX, top, MAX, u0, vTop, color);
        pose.popPose();
    }

    private static void liquidPoint(VertexConsumer vertices, PoseStack pose, float x, float y, float z,
                                    float u, float v, int color) {
        point(vertices, pose, x, y, z, u, v, color, 1, LIQUID_LIGHT);
    }

    private static void point(VertexConsumer vertices, PoseStack pose, float x, float y, float z,
                               float u, float v, int color, float alpha, int light) {
        vertices.vertex(pose.last().pose(), x, y, z)
                .color(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, alpha)
                .uv(u, v).uv2(light).endVertex();
    }

    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
}
