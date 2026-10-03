package thaumcraft.auromancy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.world.aura.AuraManager;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Original delay-zero Earth breaker, processed after projectile impact continuations at END. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EarthBreakerQueue {
    private static final float COST = .1F;
    private static final int MAX_PENDING = 4096;
    private record Breaker(ServerPlayer player, BlockPos pos, BlockState state) {}
    private static final Map<ServerLevel, ArrayDeque<Breaker>> PENDING = new IdentityHashMap<>();
    private EarthBreakerQueue() {}

    static void enqueue(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!level.getServer().isSameThread() || player.serverLevel() != level || !FocusEffects.loaded(level, pos)) return;
        var queue = PENDING.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        if (queue.size() < MAX_PENDING) queue.addLast(new Breaker(player, pos.immutable(), state));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) process(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { PENDING.keySet().removeIf(level -> level.getServer() == event.getServer()); }

    static void process(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        var queue = PENDING.remove(level);
        if (queue == null) return;
        for (Breaker operation : queue) {
            var player = operation.player(); var pos = operation.pos(); var state = operation.state();
            if (player.serverLevel() != level || !player.isAlive() || player.isSpectator() || !FocusEffects.mayChange(level, player, pos)
                    || level.getBlockState(pos) != state || state.getDestroySpeed(level, pos) < 0 || state.isAir()
                    || AuraManager.drainVis(level, pos, COST, true) < COST) continue;
            harvest(level, player, pos, state);
            // BETA26 charges after calling harvest, even when a Forge break listener cancelled it.
            AuraManager.drainVis(level, pos, COST, false);
        }
    }

    private static boolean harvest(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        int xp = ForgeHooks.onBlockBreakEvent(level, player.gameMode.getGameModeForPlayer(), player, pos);
        if (xp < 0 || level.getBlockState(pos) != state) return false;
        if (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) return false;
        var blockEntity = level.getBlockEntity(pos);
        var mainHand = player.getMainHandItem().copy();
        boolean creative = player.isCreative();
        level.levelEvent(player, 2001, pos, Block.getId(state));
        // alwaysDrop=true bypasses tool tier. Actual tool enchantments remain in the loot context.
        boolean removed = state.onDestroyedByPlayer(level, pos, player, !creative, level.getFluidState(pos));
        if (!removed) return false;
        state.getBlock().destroy(level, pos, state);
        if (!creative) {
            state.getBlock().playerDestroy(level, player, pos, state, blockEntity, mainHand);
            if (xp > 0) state.getBlock().popExperience(level, pos, xp);
        }
        // Neither the caster nor the actual main-hand tool is damaged by this harvest helper.
        return true;
    }
}
