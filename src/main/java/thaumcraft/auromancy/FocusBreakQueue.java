package thaumcraft.auromancy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Original per-world BreakData progression, detached at END after projectile continuations. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FocusBreakQueue {
    private static final int MAX_PENDING = 4096;
    private static final Map<ServerLevel, ArrayDeque<Breaker>> PENDING = new IdentityHashMap<>();
    private static final Set<ServerLevel> PROCESSING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final class Breaker {
        final ServerPlayer player;
        final BlockPos pos;
        final BlockState state;
        final float strength, maximum, vis;
        final boolean silk;
        final int fortune;
        float remaining;
        int delay;
        Breaker(ServerPlayer player, BlockPos pos, BlockState state, float strength, float durability,
                int delay, boolean silk, int fortune, float vis) {
            this.player = player; this.pos = pos.immutable(); this.state = state;
            this.strength = strength; this.maximum = durability; this.remaining = durability;
            this.delay = delay; this.silk = silk; this.fortune = fortune; this.vis = vis;
        }
    }
    private FocusBreakQueue() {}

    static void enqueue(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, float strength,
                        float durability, int delay, boolean silk, int fortune, float vis) {
        if (!level.getServer().isSameThread() || player.serverLevel() != level || !FocusEffects.loaded(level, pos)
                || !Float.isFinite(strength) || strength <= 0 || !Float.isFinite(durability) || durability < 0
                || delay < 0 || fortune < 0 || fortune > 4 || !Float.isFinite(vis) || vis <= 0) return;
        var queue = PENDING.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        if (queue.size() < MAX_PENDING) queue.addLast(new Breaker(player, pos, state, strength, durability, delay, silk, fortune, vis));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) process(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) clear(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for (ServerLevel level : PENDING.keySet().toArray(ServerLevel[]::new))
            if (level.getServer() == event.getServer()) clear(level);
    }

    static void process(ServerLevel level) {
        if (!level.getServer().isSameThread() || !PROCESSING.add(level)) return;
        try {
            var current = PENDING.remove(level);
            if (current == null) return;
            var next = new ArrayDeque<Breaker>();
            PENDING.put(level, next);
            for (Breaker operation : current) {
                var player = operation.player; var pos = operation.pos; var state = operation.state;
                if (player.serverLevel() != level || !player.isAlive() || player.isSpectator()
                        || !FocusEffects.loaded(level, pos)) { clearProgress(level, operation); continue; }
                // RunnableEntry stores the target unchanged until activation; temporary state
                // changes/permission loss during its positive delay do not cancel original work.
                if (operation.delay > 0) { operation.delay--; retain(level, next, operation); continue; }
                if (!FocusEffects.mayChange(level, player, pos) || level.getBlockState(pos) != state
                        || state.isAir() || !Float.isFinite(state.getDestroySpeed(level, pos))
                        || state.getDestroySpeed(level, pos) < 0) { clearProgress(level, operation); continue; }
                // Original insufficient aura discards progress rather than pausing or partially paying.
                if (AuraManager.drainVis(level, pos, operation.vis, true) < operation.vis) {
                    clearProgress(level, operation); continue;
                }
                int stage = (int)((1F - operation.remaining / operation.maximum) * 10F);
                level.destroyBlockProgress(pos.hashCode(), pos, stage);
                operation.remaining -= operation.strength;
                if (operation.remaining <= 0) {
                    harvest(level, operation);
                    clearProgress(level, operation);
                    // BETA26 debits even when Forge vetoed harvest or its callback changed the state.
                    AuraManager.drainVis(level, pos, operation.vis, false);
                } else retain(level, next, operation);
            }
            if (next.isEmpty() && PENDING.get(level) == next) PENDING.remove(level);
        } finally { PROCESSING.remove(level); }
    }

    private static void retain(ServerLevel level, ArrayDeque<Breaker> next, Breaker operation) {
        if (PENDING.get(level) == next && next.size() < MAX_PENDING) next.addLast(operation);
        else clearProgress(level, operation);
    }
    private static void clearProgress(ServerLevel level, Breaker operation) {
        level.destroyBlockProgress(operation.pos.hashCode(), operation.pos, -1);
    }
    private static void clear(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        var pending = PENDING.remove(level);
        if (pending != null) pending.forEach(operation -> clearProgress(level, operation));
    }

    private static boolean harvest(ServerLevel level, Breaker operation) {
        var player = operation.player; var pos = operation.pos; var state = operation.state;
        int xp = ForgeHooks.onBlockBreakEvent(level, player.gameMode.getGameModeForPlayer(), player, pos);
        if (xp < 0 || level.getBlockState(pos) != state) return false;
        if (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) return false;
        var blockEntity = level.getBlockEntity(pos);
        boolean creative = player.isCreative();
        ItemStack lootTool = lootTool(player, operation.silk, operation.fortune);
        level.levelEvent(player, 2001, pos, Block.getId(state));
        boolean removed = state.onDestroyedByPlayer(level, pos, player, !creative, level.getFluidState(pos));
        if (!removed) return false;
        state.getBlock().destroy(level, pos, state);
        if (!creative) {
            state.getBlock().playerDestroy(level, player, pos, state, blockEntity, lootTool);
            if (xp > 0) state.getBlock().popExperience(level, pos, xp);
        }
        return true;
    }

    /** Original alwaysDrop=true override chooses a placeholder, while keeping main-hand enchantments. */
    static ItemStack lootTool(ServerPlayer player, boolean silk, int fortune) {
        var mainHand = player.getMainHandItem();
        ItemStack fake = mainHand.copy();
        if (silk || fortune > EnchantmentHelper.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE, player)) {
            fake = CatalogModule.stack("enchanted_placeholder");
            var enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(mainHand));
            if (silk) enchantments.put(Enchantments.SILK_TOUCH, 1);
            int effectiveFortune = Math.max(fortune, enchantments.getOrDefault(Enchantments.BLOCK_FORTUNE, 0));
            if (effectiveFortune > 0) enchantments.put(Enchantments.BLOCK_FORTUNE, effectiveFortune);
            EnchantmentHelper.setEnchantments(enchantments, fake);
        }
        // No mineBlock, no actual-held stack enchantment changes and no tool/caster durability cost.
        return fake;
    }
}
