package thaumcraft.scanning.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;
import thaumcraft.scanning.ThaumometerItem;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** BETA26 HudHandler meter and RenderEventHandler aspect billboards, using server snapshots only. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class ThaumometerClient {
    private static final ResourceLocation HUD = texture("textures/gui/hud.png");
    private static final DecimalFormat NUMBERS = new DecimalFormat("#######.#");
    private static final int MAX_SNAPSHOT_AGE = 30;
    private static final int ICON_LIGHT = 220 << 16;
    private static final MultiBufferSource.BufferSource WORLD_BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1024));
    private static final List<ThaumometerRune> RUNES = new ArrayList<>();
    private static final List<ThaumometerSparkle> SPARKLES = new ArrayList<>();
    @Nullable private static ScanningNetwork.Snapshot snapshot;
    @Nullable private static ClientLevel snapshotLevel;
    @Nullable private static LocalPlayer snapshotPlayer;
    @Nullable private static InteractionHand snapshotHand;
    @Nullable private static BlockState snapshotBlock;
    private static ItemStack snapshotItem = ItemStack.EMPTY;
    private static int receivedTick;
    private static float tagScale;
    private static float previousTagScale;
    @Nullable private static ThaumometerItem.TargetLocation scaleTarget;
    @Nullable private static ScanningNetwork.ScanResult lastScanResult;
    private static long hudRenderCount, worldRenderCount;

    private ThaumometerClient() {}

    /** Called on the client thread by the read-only server packet handler. */
    public static void receive(ScanningNetwork.Snapshot packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(packet.dimension())) return;
        if (snapshot != null && snapshotLevel == mc.level && packet.tick() < snapshot.tick()) return;
        snapshot = packet;
        snapshotLevel = mc.level;
        snapshotPlayer = mc.player;
        snapshotHand = ThaumometerItem.heldHand(mc.player);
        receivedTick = mc.player.tickCount;
        snapshotBlock = null;
        snapshotItem = ItemStack.EMPTY;
        if (packet.target() != null) {
            var location = packet.target().location();
            if (location.kind() == ThaumometerItem.TargetKind.BLOCK)
                snapshotBlock = mc.level.getBlockState(location.blockPos());
            else if (location.kind() == ThaumometerItem.TargetKind.HELD_ITEM && snapshotHand != null)
                snapshotItem = mc.player.getItemInHand(otherHand(snapshotHand)).copy();
            else if (location.kind() == ThaumometerItem.TargetKind.ENTITY
                    && mc.level.getEntity(location.entityId()) instanceof ItemEntity item)
                snapshotItem = item.getItem().copy();
        }
    }

    /** The original right-click feedback uses ten purple runes and the scan sound for repeated scans too. */
    public static void receiveScan(ScanningNetwork.ScanResult packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().location().equals(packet.dimension())
                || ThaumometerItem.heldHand(mc.player) == null) return;
        var hand = ThaumometerItem.heldHand(mc.player);
        var location = packet.target().location();
        if (!sameGeometry(location, ThaumometerItem.locateTarget(mc.player, hand))) return;
        lastScanResult = packet;
        mc.player.playSound(ScanningModule.SCAN_SOUND.get(), .5F, 1F);
        if (location.kind() == ThaumometerItem.TargetKind.HELD_ITEM) return;
        Vec3 center;
        int duration = 15;
        if (location.kind() == ThaumometerItem.TargetKind.ENTITY) {
            Entity entity = mc.level.getEntity(location.entityId());
            if (entity == null || entity.isRemoved()) return;
            center = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight() / 2F + .5, entity.getZ());
            duration = Math.max(1, (int) (entity.getBbHeight() * 15));
        } else center = Vec3.atCenterOf(location.blockPos()).add(0, .25, 0);
        // Bound retained effects even if a server sends unusually frequent scan replies.
        while (RUNES.size() > 90) RUNES.remove(0);
        for (int i = 0; i < 10; i++) RUNES.add(new ThaumometerRune(center, duration, mc.level.random));
    }

    @Nullable public static ScanningNetwork.Snapshot currentSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || snapshot == null || snapshotLevel != mc.level
                || snapshotPlayer != mc.player || !mc.level.dimension().location().equals(snapshot.dimension())
                || ThaumometerItem.heldHand(mc.player) == null) return null;
        int age = mc.player.tickCount - receivedTick;
        return age >= 0 && age <= MAX_SNAPSHOT_AGE ? snapshot : null;
    }

    @Nullable public static ScanningNetwork.Target currentTarget() {
        var current = currentSnapshot();
        if (current == null || current.target() == null) return null;
        Minecraft mc = Minecraft.getInstance();
        InteractionHand hand = ThaumometerItem.heldHand(mc.player);
        if (hand != snapshotHand) return null;
        var expected = current.target().location();
        if (!sameGeometry(expected, ThaumometerItem.locateTarget(mc.player, hand))) return null;
        if (expected.kind() == ThaumometerItem.TargetKind.BLOCK
                && !mc.level.getBlockState(expected.blockPos()).equals(snapshotBlock)) return null;
        if (expected.kind() == ThaumometerItem.TargetKind.HELD_ITEM) {
            ItemStack held = mc.player.getItemInHand(otherHand(hand));
            if (!ItemStack.isSameItemSameTags(held, snapshotItem) || !held.getHoverName().equals(current.target().name())) return null;
        } else if (expected.kind() == ThaumometerItem.TargetKind.ENTITY) {
            Entity entity = mc.level.getEntity(expected.entityId());
            if (entity == null || entity.isRemoved()) return null;
            if (entity instanceof ItemEntity item && !ItemStack.isSameItemSameTags(item.getItem(), snapshotItem)) return null;
        }
        return current.target();
    }

    public static void clear() {
        snapshot = null;
        snapshotLevel = null;
        snapshotPlayer = null;
        snapshotHand = null;
        snapshotBlock = null;
        snapshotItem = ItemStack.EMPTY;
        scaleTarget = null;
        tagScale = 0;
        previousTagScale = 0;
        lastScanResult = null;
        hudRenderCount = worldRenderCount = 0;
        RUNES.clear();
        SPARKLES.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void clonePlayer(ClientPlayerNetworkEvent.Clone event) { clear(); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || (snapshotLevel != null && snapshotLevel != mc.level)) { clear(); return; }
        if (mc.isPaused()) return;
        previousTagScale = tagScale;
        if (tagScale > 0) tagScale = Math.max(0, tagScale - .005F);
        var target = currentTarget();
        if (target == null) {
            tagScale = 0;
            previousTagScale = 0;
            scaleTarget = null;
        } else {
            if (!sameGeometry(scaleTarget, target.location())) { tagScale = 0; previousTagScale = 0; }
            scaleTarget = target.location();
            // Original per-frame easing, made tick-based so display refresh rate does not change the animation.
            if (tagScale < .5F) tagScale = Math.min(.5F, tagScale + .031F - tagScale / 10F);
        }
        RUNES.removeIf(ThaumometerRune::tick);
        SPARKLES.removeIf(ThaumometerSparkle::tick);
        if (visible() && target != null && !target.scanned() && mc.player.tickCount % 5 == 0)
            highlight(target);
    }

    private static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && !mc.options.hideGui && mc.screen == null && !mc.isPaused()
                && ThaumometerItem.heldHand(mc.player) != null;
    }

    @Nullable public static ScanningNetwork.Snapshot snapshotForSmokeTest() { return currentSnapshot(); }
    @Nullable public static ScanningNetwork.ScanResult scanResultForSmokeTest() { return lastScanResult; }
    public static boolean isVisibleForSmokeTest() { return visible() && currentSnapshot() != null; }
    public static long hudRenderCountForSmokeTest() { return hudRenderCount; }
    public static long worldRenderCountForSmokeTest() { return worldRenderCount; }

    private static void renderHud(GuiGraphics graphics, float partialTick, int width, int height) {
        var current = currentSnapshot();
        if (!visible() || current == null) return;
        ++hudRenderCount;
        graphics.pose().pushPose();
        graphics.pose().translate(0,thaumcraft.equipment.client.SanityHud.thaumometerOffset(),0);
        Minecraft mc = Minecraft.getInstance();
        float base = Mth.clamp(current.base() / 525F, 0, 1);
        float vis = Mth.clamp(current.vis() / 525F, 0, 1);
        float flux = Mth.clamp(current.flux() / 525F, 0, 1);
        if (vis + flux > 1) {
            float normalization = 1F / (vis + flux);
            base *= normalization; vis *= normalization; flux *= normalization;
        }
        float count = mc.player.tickCount + partialTick;
        float count2 = mc.player.tickCount / 3F + partialTick;
        float visStart = 10 + (1 - vis) * 64;
        float fluxStart = 10 + (1 - flux - vis) * 64;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            if (vis > 0) {
                hudQuad(graphics.pose(), 7, visStart, 8, vis * 64, 88, 56, 8, 64, .7F, .4F, .9F, 1);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                hudQuad(graphics.pose(), 7, visStart, 8, vis * 64, 96, 56 + count % 64, 8, vis * 64, 1, 1, 1, .5F);
                RenderSystem.defaultBlendFunc();
            }
            if (flux > 0) {
                hudQuad(graphics.pose(), 7, fluxStart, 8, flux * 64, 88, 56, 8, 64, .25F, .1F, .3F, 1);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                hudQuad(graphics.pose(), 7, fluxStart, 8, flux * 64, 104, 120 - count2 % 64, 8, flux * 64, .7F, .4F, 1, .5F);
                RenderSystem.defaultBlendFunc();
            }
            hudQuad(graphics.pose(), 3, 1, 16, 80, 72, 48, 16, 80, 1, 1, 1, 1);
            hudQuad(graphics.pose(), 4, 8 + (1 - base) * 64, 14, 5, 117, 61, 14, 5, 1, 1, 1, 1);
            if (mc.player.isShiftKeyDown()) {
                if (vis > 0) number(graphics, current.vis(), 18, visStart, 15641343);
                if (flux > 0) number(graphics, current.flux(), 18, fluxStart - 4, 11145659);
            }
            var target = currentTarget();
            if (target != null && target.location().kind() == ThaumometerItem.TargetKind.HELD_ITEM)
                heldAspects(graphics, target.aspects(), width, height);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void number(GuiGraphics graphics, float value, float x, float y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(.5F, .5F, 1);
        graphics.drawString(Minecraft.getInstance().font, NUMBERS.format(value), 0, 0, color, true);
        graphics.pose().popPose();
    }

    /** The port's other-hand scan has no world position: use the original coloured icons beside the crosshair. */
    private static void heldAspects(GuiGraphics graphics, List<ScanningNetwork.AspectAmount> aspects, int width, int height) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < aspects.size(); i++) {
            var value = aspects.get(i);
            Aspect aspect = Aspect.getAspect(value.tag());
            if (aspect == null) continue;
            int row = i / 5, columns = Math.min(5, aspects.size() - row * 5);
            int x = width / 2 + (i % 5) * 20 - columns * 10 + 2;
            int y = height / 2 + 22 + row * 22;
            int color = aspect.getColor();
            graphics.setColor(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, .75F);
            graphics.blit(aspect.getImage(), x, y, 0, 0, 16, 16, 16, 16);
            graphics.setColor(1, 1, 1, 1);
            String count = Integer.toString(value.amount());
            graphics.drawString(font, count, x + 16 - font.width(count), y + 10, 0xFFFFFF, true);
        }
    }

    @SubscribeEvent public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !visible()) return;
        ++worldRenderCount;
        Minecraft mc = Minecraft.getInstance();
        var target = currentTarget();
        if (target != null && target.location().kind() != ThaumometerItem.TargetKind.HELD_ITEM)
            if (target.location().kind() != ThaumometerItem.TargetKind.BLOCK
                    || !thaumcraft.equipment.armor.GogglesArmorClient.hasContainerPopup(target.location().blockPos()))
                worldAspects(event, target);
        for (ThaumometerRune rune : RUNES) rune.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick(), WORLD_BUFFERS);
        for (ThaumometerSparkle sparkle : SPARKLES)
            sparkle.render(event.getPoseStack(), event.getCamera(), event.getPartialTick(), WORLD_BUFFERS);
        WORLD_BUFFERS.endBatch();
    }

    private static void highlight(ScanningNetwork.Target target) {
        Minecraft mc = Minecraft.getInstance();
        var location = target.location();
        AABB bounds;
        if (location.kind() == ThaumometerItem.TargetKind.ENTITY) {
            Entity entity = mc.level.getEntity(location.entityId());
            if (entity == null) return;
            bounds = entity.getBoundingBox();
        } else if (location.kind() == ThaumometerItem.TargetKind.BLOCK) {
            var shape = mc.level.getBlockState(location.blockPos()).getShape(mc.level, location.blockPos());
            bounds = shape.isEmpty() ? new AABB(location.blockPos()) : shape.bounds().move(location.blockPos());
        } else return;
        // Match TC6's six-face trial count and Gaussian/clamped placement. The server supplies scannability.
        int trials = Math.min(32, Mth.ceil(bounds.getSize() * 2)) * 2;
        Vec3 center = bounds.getCenter();
        for (Direction face : Direction.values()) {
            for (int i = 0; i < trials; i++) {
                double x = Mth.clamp(.5 + face.getStepX() * .51 + mc.level.random.nextGaussian() * bounds.getXsize(), bounds.minX - center.x, bounds.maxX - center.x);
                double y = Mth.clamp(.5 + face.getStepY() * .51 + mc.level.random.nextGaussian() * bounds.getYsize(), bounds.minY - center.y, bounds.maxY - center.y);
                double z = Mth.clamp(.5 + face.getStepZ() * .51 + mc.level.random.nextGaussian() * bounds.getZsize(), bounds.minZ - center.z, bounds.maxZ - center.z);
                if (SPARKLES.size() >= 1024) SPARKLES.remove(0);
                SPARKLES.add(new ThaumometerSparkle(center.add(x, y, z), mc.level.random, mc.level.getMoonPhase()));
            }
        }
    }

    private static void worldAspects(RenderLevelStageEvent event, ScanningNetwork.Target target) {
        Minecraft mc = Minecraft.getInstance();
        var location = target.location();
        float scale = Math.max(.03F, Mth.lerp(event.getPartialTick(), previousTagScale, tagScale));
        Vec3 origin;
        if (location.kind() == ThaumometerItem.TargetKind.ENTITY) {
            Entity entity = mc.level.getEntity(location.entityId());
            if (entity == null) return;
            origin = entity.getPosition(event.getPartialTick()).add(0, entity.getBbHeight() + .5, 0);
        } else {
            var face = location.face();
            origin = Vec3.atCenterOf(location.blockPos()).add(face.getStepX() * (.5 + scale * 2),
                    face.getStepY() * (.5 + scale * 2), face.getStepZ() * (.5 + scale * 2));
        }
        Vec3 camera = event.getCamera().getPosition();
        float yaw = (float) (Mth.atan2(camera.x - origin.x, camera.z - origin.z) * Mth.RAD_TO_DEG);
        List<ScanningNetwork.AspectAmount> aspects = target.aspects();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        for (int i = 0; i < aspects.size(); i++) {
            var value = aspects.get(i);
            Aspect aspect = Aspect.getAspect(value.tag());
            if (aspect == null) continue;
            int row = i / 5, columns = Math.min(5, aspects.size() - row * 5);
            float x = (i % 5 - columns / 2F + .5F) * scale * 4 * scale;
            pose.pushPose();
            pose.translate(-x, row * scale * 1.05F, 0);
            pose.scale(scale, scale, scale);
            quad(WORLD_BUFFERS.getBuffer(RenderType.textSeeThrough(aspect.getImage())), pose, -.5F, -.5F, .5F, .5F,
                    0, 0, 1, 1, aspect.getColor(), .75F, ICON_LIGHT);
            pose.scale(.04F, -.04F, -.04F);
            String count = Integer.toString(value.amount());
            mc.font.drawInBatch(count, 14 - mc.font.width(count), 7, 0xFF111111, false,
                    pose.last().pose(), WORLD_BUFFERS, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            mc.font.drawInBatch(count, 13 - mc.font.width(count), 6, 0xFFFFFFFF, false,
                    pose.last().pose(), WORLD_BUFFERS, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    private static boolean sameGeometry(@Nullable ThaumometerItem.TargetLocation left, @Nullable ThaumometerItem.TargetLocation right) {
        if (left == null || right == null || left.kind() != right.kind()) return false;
        return switch (left.kind()) {
            case BLOCK -> left.blockPos().equals(right.blockPos()) && left.face() == right.face();
            case ENTITY -> left.entityId() == right.entityId();
            case HELD_ITEM -> true;
        };
    }

    private static void hudQuad(PoseStack pose, float x, float y, float width, float height, float u, float v,
                                float uvWidth, float uvHeight, float red, float green, float blue, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, HUD);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = pose.last().pose();
        buffer.vertex(matrix, x, y + height, 0).uv(u / 256, (v + uvHeight) / 256).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).uv((u + uvWidth) / 256, (v + uvHeight) / 256).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x + width, y, 0).uv((u + uvWidth) / 256, v / 256).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x, y, 0).uv(u / 256, v / 256).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    static void quad(VertexConsumer vertices, PoseStack pose, float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int color, float alpha, int light) {
        point(vertices, pose, x0, y1, u0, v0, color, alpha, light);
        point(vertices, pose, x0, y0, u0, v1, color, alpha, light);
        point(vertices, pose, x1, y0, u1, v1, color, alpha, light);
        point(vertices, pose, x1, y1, u1, v0, color, alpha, light);
    }

    private static void point(VertexConsumer vertices, PoseStack pose, float x, float y, float u, float v,
                              int color, float alpha, int light) {
        vertices.vertex(pose.last().pose(), x, y, 0)
                .color(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, alpha)
                .uv(u, v).uv2(light).endVertex();
    }

    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }

    @Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Overlays {
        private Overlays() {}
        @SubscribeEvent public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("thaumometer", (gui, graphics, partialTick, width, height) -> renderHud(graphics, partialTick, width, height));
        }
    }
}
