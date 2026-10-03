package thaumcraft.infusion.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.catalog.entities.client.LegacyInfusionCubeBlockModel;
import thaumcraft.infusion.*;
import java.util.Random;

/** Original eight ModelCube pieces, startup rotations, instability jitter and luminous rune UVs. */
@Mod.EventBusSubscriber(modid="thaumcraft", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class InfusionMatrixRenderer implements BlockEntityRenderer<InfusionMatrixBlockEntity> {
    private final LegacyInfusionCubeBlockModel cube = new LegacyInfusionCubeBlockModel(), glow = new LegacyInfusionCubeBlockModel(32);
    public InfusionMatrixRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(InfusionModule.MATRIX.get(), InfusionMatrixRenderer::new);
    }
    @Override public void render(InfusionMatrixBlockEntity tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float ticks = tile.getLevel().getGameTime() + partial, startup = tile.startup();
        String pillar = InfusionStability.id(tile.getLevel(), tile.getBlockPos().offset(-1, -2, -1));
        String type = pillar.equals("pillar_ancient") ? "ancient" : pillar.equals("pillar_eldritch") ? "eldritch" : "normal";
        ResourceLocation texture = InfusionModule.id("textures/blocks/infuser_" + type + ".png");
        pose.pushPose(); pose.translate(.5, .5, .5); pose.mulPose(Axis.YP.rotationDegrees(ticks % 360 * startup));
        pose.mulPose(Axis.XP.rotationDegrees(35 * startup)); pose.mulPose(Axis.ZP.rotationDegrees(45 * startup));
        float jitter = Math.min(6, 1 + (tile.stability() < 0 ? -tile.stability() * .66F : 1) * Math.min(tile.craftCount(), 50) / 50F);
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int c = 0; c < 2; c++) {
            pose.pushPose();
            float dx = tile.active() ? Mth.sin((ticks + a * 10) / 15F) * .01F * startup * jitter : 0;
            float dy = tile.active() ? Mth.sin((ticks + b * 10) / 14F) * .01F * startup * jitter : 0;
            float dz = tile.active() ? Mth.sin((ticks + c * 10) / 13F) * .01F * startup * jitter : 0;
            pose.translate(dx + (a == 0 ? -.25 : .25), dy + (b == 0 ? -.25 : .25), dz + (c == 0 ? -.25 : .25));
            if (a > 0) pose.mulPose(Axis.XP.rotationDegrees(90)); if (b > 0) pose.mulPose(Axis.YP.rotationDegrees(90)); if (c > 0) pose.mulPose(Axis.ZP.rotationDegrees(90));
            pose.scale(.45F, .45F, .45F);
            cube.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay, 1, 1, 1, 1);
            if (tile.active()) glow.renderToBuffer(pose, buffers.getBuffer(InfusionRenderTypes.GLOW.apply(texture)), LightTexture.FULL_BRIGHT, overlay, .8F, .1F, 1,
                    (Mth.sin((ticks + a * 2 + b * 3 + c * 4) / 4F) * .1F + .2F) * startup);
            pose.popPose();
        }
        pose.popPose();
        if (tile.crafting()) halo(pose, buffers, tile.craftCount());
    }
    private static void halo(PoseStack pose, MultiBufferSource buffers, int count) {
        Random random = new Random(245); var vertices = buffers.getBuffer(RenderType.lightning());
        pose.pushPose(); pose.translate(.5, .5, .5); float scale = Math.min(count, 50) / 1000F;
        for (int i = 0; i < 20; i++) {
            for (int rotation = 0; rotation < 2; rotation++) { pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360)); pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360)); pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360 + (rotation == 1 ? count / 500F * 360 : 0))); }
            float height = (random.nextFloat() * 20 + 5) * scale, width = (random.nextFloat() * 2 + 1) * scale;
            var matrix = pose.last().pose();
            for (int edge = 0; edge < 3; edge++) {
                double angle = Math.PI * 2 * edge / 3, next = Math.PI * 2 * (edge + 1) / 3;
                vertices.vertex(matrix, 0, 0, 0).color(1F, 1F, 1F, Math.max(0, 1 - count / 500F)).endVertex();
                vertices.vertex(matrix, (float)Math.sin(angle) * width, height, (float)Math.cos(angle) * width).color(1F, 0F, 1F, 0F).endVertex();
                vertices.vertex(matrix, (float)Math.sin(next) * width, height, (float)Math.cos(next) * width).color(1F, 0F, 1F, 0F).endVertex();
                vertices.vertex(matrix, 0, 0, 0).color(1F, 1F, 1F, Math.max(0, 1 - count / 500F)).endVertex();
            }
        }
        pose.popPose();
    }
}
