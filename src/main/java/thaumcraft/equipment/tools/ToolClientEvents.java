package thaumcraft.equipment.tools;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Client-only original ore ripples and shovel construction preview; no client changes to blocks/inventory. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class ToolClientEvents {
    private static final KeyMapping MODE = new KeyMapping("key.thaumcraft.tool_mode", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F, "key.categories.thaumcraft");
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static ResourceLocation dimension;
    private ToolClientEvents() {}

    @Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(MODE); }
    }

    /**
     * Modern input adaptation: TC6's configurable F action shares vanilla's offhand-swap default. Forge emits
     * InputEvent.Key after KeyboardHandler queues clicks, before Minecraft.handleKeybinds consumes swap clicks.
     * Handle one physical press here, draining the conflicting swap only for an active main-hand shovel action.
     * Repeats are drained without another cycle; rebinding either action and ordinary item swaps keep working.
     */
    @SubscribeEvent public static void key(InputEvent.Key event) {
        handleModeInput(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction());
    }

    /** KeyMapping's controls screen also permits a mouse binding; its post event has the same queued-click order. */
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Post event) {
        handleModeInput(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction());
    }

    private static void handleModeInput(InputConstants.Key key, int action) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null || minecraft.isPaused()
                || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !(minecraft.player.getMainHandItem().getItem() instanceof ElementalShovelItem)
                || !MODE.isActiveAndMatches(key)) return;
        KeyMapping swap = minecraft.options.keySwapOffhand;
        if (MODE.getKey().equals(swap.getKey()) && swap.isActiveAndMatches(key)) {
            while (swap.consumeClick()) {} // Discard only this shared active binding's queued swap, before vanilla sees it.
        }
        boolean pending = MODE.consumeClick();
        while (MODE.consumeClick()) {} // Clear repeat/stale clicks; never cycle again from the later tick handler.
        if (pending && action == GLFW.GLFW_PRESS) ToolNetwork.cycleMode();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        while (MODE.consumeClick()) {} // Clear clicks made outside the active shovel context instead of replaying them later.
        if (minecraft.level == null || dimension != null && !dimension.equals(minecraft.level.dimension().location())) { RIPPLES.clear(); dimension = null; }
        if (!minecraft.isPaused()) RIPPLES.removeIf(Ripple::tick);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { RIPPLES.clear(); dimension = null; }

    public static void sounding(ToolNetwork.Sounding packet) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        dimension = packet.dimension();
        int range = 4 + packet.rank() * 4;
        Set<BlockPos> ores = new LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(packet.pos().offset(-range, -range, -range), packet.pos().offset(range, range, range)))
            if (level.hasChunkAt(pos) && ToolMining.isOre(level.getBlockState(pos))) ores.add(pos.immutable());
        while (!ores.isEmpty()) {
            BlockPos start = ores.iterator().next();
            BlockState state = level.getBlockState(start);
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            ores.remove(start); queue.add(start);
            double x = 0, y = 0, z = 0; int count = 0;
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                x += pos.getX() + .5; y += pos.getY() + .5; z += pos.getZ() + .5; count++;
                for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
                    if (ores.contains(candidate) && level.getBlockState(candidate).equals(state)) {
                        BlockPos next = candidate.immutable(); ores.remove(next); queue.addLast(next);
                    }
            }
            Vec3 center = new Vec3(x / count, y / count, z / count);
            // Bounded particle cache prevents repeated packets accumulating indefinitely; original grouping is unchanged.
            if (RIPPLES.size() >= 4096) RIPPLES.remove(0);
            RIPPLES.add(new Ripple(center, color(state), (int)(Math.sqrt(packet.pos().distToCenterSqr(center.x, center.y, center.z)) * 3)));
        }
    }

    private static int color(BlockState state) {
        String[] names = {"iron", "coal", "redstone", "gold", "lapis", "diamond", "emerald", "quartz", "silver", "tin", "copper", "amber", "cinnabar"};
        int[] colors = {14200723, 1052688, 16711680, 16576075, 1328572, 6155509, 1564002, 15064789, 14342653, 15724539, 16620629, 16626469, 10159368};
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        for (int i = 0; i < names.length; i++) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("forge", "ores/" + names[i]));
            if (state.is(tag) || id != null && id.getNamespace().equals("thaumcraft") && id.getPath().equals("ore_" + names[i])) return colors[i];
        }
        return 12632256;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || minecraft.level == null || minecraft.player == null
                || minecraft.options.hideGui || minecraft.isPaused()) return;
        var buffers = minecraft.renderBuffers().bufferSource();
        for (Ripple ripple : RIPPLES) ripple.render(event.getPoseStack(), event.getCamera(), event.getPartialTick(), buffers);
        buffers.endBatch(ToolRenderTypes.ADDITIVE); buffers.endBatch(ToolRenderTypes.ALPHA);
        if (minecraft.screen == null && minecraft.player.isShiftKeyDown()
                && minecraft.player.getMainHandItem().getItem() instanceof ElementalShovelItem
                && minecraft.hitResult instanceof BlockHitResult hit && minecraft.level.getBlockEntity(hit.getBlockPos()) == null) {
            PoseStack pose = event.getPoseStack();
            Vec3 camera = event.getCamera().getPosition();
            pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (BlockPos pos : ElementalShovelItem.positions(minecraft.player.getMainHandItem(), hit.getBlockPos(), hit.getDirection(), minecraft.player))
                if (minecraft.level.getBlockState(pos).canBeReplaced()) LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(.002), .5F, .75F, 1F, .7F);
            pose.popPose(); buffers.endBatch(RenderType.lines());
        }
    }

    private static final class Ripple {
        final Vec3 position; final int color; final boolean dark;
        int delay, age;
        Ripple(Vec3 position, int color, int delay) {
            this.position = position; this.color = color; this.delay = delay;
            dark = (((color >> 16) & 255) + ((color >> 8) & 255) + (color & 255)) / (255F * 3) < .25F;
        }
        boolean tick() { if (delay > 0) { delay--; return false; } return ++age > 44; }
        void render(PoseStack pose, net.minecraft.client.Camera camera, float partial, MultiBufferSource buffers) {
            if (delay > 0 || age >= 44) return;
            float key = Mth.clamp((age + partial) / 44F, 0, 1) * 3;
            float[] alphaKeys = {0, 1, .8F, 0};
            int i = Math.min(2, (int)key);
            float alpha = Mth.lerp(key - i, alphaKeys[i], alphaKeys[i + 1]);
            int frame = 240 + age % 15;
            float u = frame % 16 / 16F, v = frame / 16 / 16F;
            Vec3 relative = position.subtract(camera.getPosition());
            pose.pushPose(); pose.translate(relative.x, relative.y, relative.z); pose.mulPose(camera.rotation());
            VertexConsumer vertices = buffers.getBuffer(dark ? ToolRenderTypes.ALPHA : ToolRenderTypes.ADDITIVE);
            point(vertices, pose, -.9F, -.9F, u, v + 1 / 16F, alpha);
            point(vertices, pose, .9F, -.9F, u + 1 / 16F, v + 1 / 16F, alpha);
            point(vertices, pose, .9F, .9F, u + 1 / 16F, v, alpha);
            point(vertices, pose, -.9F, .9F, u, v, alpha);
            pose.popPose();
        }
        void point(VertexConsumer vertices, PoseStack pose, float x, float y, float u, float v, float alpha) {
            vertices.vertex(pose.last().pose(), x, y, 0).color(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                    (color & 255) / 255F, alpha).uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
        }
    }
}
