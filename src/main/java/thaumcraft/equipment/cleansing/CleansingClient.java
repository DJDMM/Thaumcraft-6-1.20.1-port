package thaumcraft.equipment.cleansing;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/** Original soap and bath FXGeneric particles, rendered in one client batch with world-change cleanup. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class CleansingClient {
    private static final List<CleansingBubble> BUBBLES = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1024));
    private static ClientLevel world;
    private CleansingClient() {}

    private static boolean selectWorld(ClientLevel level) {
        if (world != level) { BUBBLES.clear(); world = level; }
        return level != null;
    }

    public static void soapUsing(LivingEntity player) {
        if (!(player.level() instanceof ClientLevel level) || !selectWorld(level)) return;
        if (level.random.nextFloat() < .2F) level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHORUS_FLOWER_DEATH, SoundSource.PLAYERS, .1F, 1.5F + level.random.nextFloat() * .2F, false);
        for (int i = 0; i < 10; ++i) BUBBLES.add(new CleansingBubble(level,
                player.getX() - .5 + level.random.nextFloat(), player.getBoundingBox().minY + level.random.nextFloat() * player.getBbHeight(),
                player.getZ() - .5 + level.random.nextFloat(), false, .8F, .9F));
    }

    public static void soapFinished(CleansingNetwork.SoapFinished packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(packet.dimension()) || !selectWorld(level)
                || !Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())
                || !Float.isFinite(packet.height()) || packet.height() <= 0 || packet.height() > 16) return;
        for (int i = 0; i < 40; ++i) BUBBLES.add(new CleansingBubble(level,
                packet.x() - .5 + level.random.nextFloat() * 1.5, packet.y() + level.random.nextFloat() * packet.height(),
                packet.z() - .5 + level.random.nextFloat() * 1.5, false, .7F, .9F));
    }

    public static void fluidBubble(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (!(world instanceof ClientLevel level) || !selectWorld(level)) return;
        if (random.nextInt(10) == 0) {
            // Modern falling LEVEL values use their fluid amount: the original Classic only had quanta 0..7.
            float surface = state.getValue(LiquidBlock.LEVEL) < 8
                    ? .125F * (8 - state.getValue(LiquidBlock.LEVEL)) : 1;
            BUBBLES.add(new CleansingBubble(level, pos.getX() + random.nextFloat(), pos.getY() + surface,
                    pos.getZ() + random.nextFloat(), true, 1, 1));
        }
        if (random.nextInt(50) == 0) level.playLocalSound(pos.getX() + random.nextFloat(), pos.getY() + .5,
                pos.getZ() + random.nextFloat(), SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                .1F + random.nextFloat() * .1F, .9F + random.nextFloat() * .15F, false);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!selectWorld(mc.level) || mc.isPaused()) return;
        BUBBLES.removeIf(bubble -> { bubble.tick(); return !bubble.isAlive(); });
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BUBBLES.isEmpty()
                || world != Minecraft.getInstance().level) return;
        var vertices = BUFFERS.getBuffer(CleansingRenderTypes.BUBBLE);
        var pose = event.getPoseStack().last().pose();
        for (CleansingBubble bubble : BUBBLES) bubble.draw(vertices, pose, event.getCamera(), event.getPartialTick());
        BUFFERS.endBatch();
    }
}
