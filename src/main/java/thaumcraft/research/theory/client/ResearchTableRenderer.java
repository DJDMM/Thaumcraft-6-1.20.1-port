package thaumcraft.research.theory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.entities.client.LegacyResearchTableBlockModel;
import thaumcraft.research.theory.ResearchTableBlock;
import thaumcraft.research.theory.ResearchTableBlockEntity;
import thaumcraft.research.theory.TheoryModule;

/** Original BETA26 table attachments; its JSON supplies the original wooden body. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ResearchTableRenderer implements BlockEntityRenderer<ResearchTableBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/research_table_model.png");
    private static final ResourceLocation QUILL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/quill.png");
    private final LegacyResearchTableBlockModel model = new LegacyResearchTableBlockModel();

    public ResearchTableRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TheoryModule.TABLE_TILE.get(), ResearchTableRenderer::new);
    }

    @Override public void render(ResearchTableBlockEntity table, float partialTicks, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        renderAttachments(table.getBlockState().getValue(ResearchTableBlock.FACING),
                table.getItem(ResearchTableBlockEntity.INK), table.session() != null, pose, buffers, light, overlay);
    }

    /** Pure rendering entry point also used by the isolated visual fixture. */
    public void renderAttachments(Direction facing, ItemStack tools, boolean theoryPresent, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, 1, .5);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(yaw(facing)));
        if (theoryPresent) {
            var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(MODEL));
            model.renderParts(pose, vertices, light, overlay, 0xFFFFFF, "ScrollTube");
            pose.pushPose();
            pose.scale(1.2F, 1.2F, 1.2F);
            model.renderParts(pose, vertices, light, overlay, Aspect.ALCHEMY.getColor(), "ScrollRibbon");
            pose.popPose();
        }
        // Exhausted scribing tools still render in the original: this is an item-type test.
        if (!tools.isEmpty() && tools.is(TheoryModule.SCRIBING_TOOLS.get())) {
            model.renderParts(pose, buffers.getBuffer(RenderType.entityTranslucent(MODEL)), light, overlay, 0xFFFFFF, "Inkwell");
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(180));
            pose.translate(-.5, .1, .125);
            pose.mulPose(Axis.YP.rotationDegrees(60));
            pose.scale(.5F, .5F, .5F);
            renderQuill(pose, buffers.getBuffer(RenderType.entityTranslucent(QUILL)), light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    public static float yaw(Direction facing) {
        return switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    /** UtilsFX.renderTextureIn3D's 16-column/row extrusion, with original mirrored U and 1/16 depth. */
    private static void renderQuill(PoseStack pose, VertexConsumer out, int light, int overlay) {
        float depth = .0625F;
        quad(out, pose, light, overlay, 0, 0, 1,
                0, 0, 0, 1, 1,  1, 0, 0, 0, 1,  1, 1, 0, 0, 0,  0, 1, 0, 1, 0);
        quad(out, pose, light, overlay, 0, 0, -1,
                0, 1, -depth, 1, 0,  1, 1, -depth, 0, 0,  1, 0, -depth, 0, 1,  0, 0, -depth, 1, 1);
        for (int column = 0; column < 16; column++) {
            float x = column / 16F, next = x + 1 / 16F, u = 1 - x - .5F / 16;
            quad(out, pose, light, overlay, -1, 0, 0,
                    x, 0, -depth, u, 1,  x, 0, 0, u, 1,  x, 1, 0, u, 0,  x, 1, -depth, u, 0);
            quad(out, pose, light, overlay, 1, 0, 0,
                    next, 1, -depth, u, 0,  next, 1, 0, u, 0,  next, 0, 0, u, 1,  next, 0, -depth, u, 1);
        }
        for (int row = 0; row < 16; row++) {
            float y = row / 16F, next = y + 1 / 16F, v = 1 - y - .5F / 16;
            quad(out, pose, light, overlay, 0, 1, 0,
                    0, next, 0, 1, v,  1, next, 0, 0, v,  1, next, -depth, 0, v,  0, next, -depth, 1, v);
            quad(out, pose, light, overlay, 0, -1, 0,
                    1, y, 0, 0, v,  0, y, 0, 1, v,  0, y, -depth, 1, v,  1, y, -depth, 0, v);
        }
    }

    private static void quad(VertexConsumer out, PoseStack pose, int light, int overlay,
                             float nx, float ny, float nz, float... vertices) {
        for (int i = 0; i < vertices.length; i += 5)
            out.vertex(pose.last().pose(), vertices[i], vertices[i + 1], vertices[i + 2])
                    .color(255, 255, 255, 255).uv(vertices[i + 3], vertices[i + 4]).overlayCoords(overlay)
                    .uv2(light).normal(pose.last().normal(), nx, ny, nz).endVertex();
    }
}
