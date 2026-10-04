package thaumcraft.auromancy.remaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Original zero-lifespan virtual swapper: physical inventory, target aura and pickup loot. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FocusExchangeQueue {
    private static final int MAX_PENDING = 4096;
    private record Swap(ServerPlayer caster, BlockPos pos, BlockState source, ItemStack target, boolean silk, int fortune, float vis) {}
    private static final Map<ServerLevel, ArrayDeque<Swap>> PENDING = new IdentityHashMap<>();
    private static final Set<ServerLevel> PROCESSING = Collections.newSetFromMap(new IdentityHashMap<>());
    private FocusExchangeQueue() {}
    public static void enqueue(ServerLevel level, ServerPlayer caster, BlockPos pos, BlockState source, ItemStack target,
                               boolean silk, int fortune) {
        if (!level.getServer().isSameThread() || caster.serverLevel() != level || !RiftPassage.loaded(level, pos)
                || target == null || target.isEmpty() || target.getCount() != 1 || fortune < 0 || fortune > 4) return;
        var pending = PENDING.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        if (pending.size() < MAX_PENDING) pending.addLast(new Swap(caster, pos.immutable(), source, target.copy(), silk, fortune,
                .25F + (silk ? .25F : 0) + fortune * .1F));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) process(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        PENDING.keySet().removeIf(level -> level.getServer() == event.getServer());
    }
    public static void process(ServerLevel level) {
        if (!level.getServer().isSameThread() || !PROCESSING.add(level)) return;
        try {
            // Third-party placement/loot callbacks can enqueue work without reentering this batch.
            var batch = PENDING.remove(level); if (batch == null) return;
            for (var swap : batch) apply(level, swap);
        } finally { PROCESSING.remove(level); }
    }
    private static boolean valid(ServerLevel level, Swap swap) {
        var caster = swap.caster(); var pos = swap.pos();
        return caster.serverLevel() == level && caster.isAlive() && !caster.isSpectator() && RiftPassage.mayChange(level, caster, pos)
                && level.getBlockState(pos) == swap.source() && !swap.source().isAir()
                && Float.isFinite(swap.source().getDestroySpeed(level, pos)) && swap.source().getDestroySpeed(level, pos) >= 0
                && AuraManager.drainVis(level, pos, swap.vis(), true) >= swap.vis();
    }
    private static int slot(ServerPlayer player, ItemStack target) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            var stack = player.getInventory().items.get(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, target)) return i;
        }
        return -1;
    }
    private static void apply(ServerLevel level, Swap swap) {
        if (!valid(level, swap)) return;
        var caster = swap.caster(); var pos = swap.pos(); var source = swap.source(); var selected = swap.target();
        // BETA26 isItemEqual ignores NBT here: swapping the same block item never consumes either.
        if (selected.is(source.getBlock().asItem())) return;
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (ForgeEventFactory.onBlockPlace(caster, snapshot, Direction.UP) || !valid(level, swap)) return;
        int inventorySlot = caster.isCreative() ? -1 : slot(caster, selected);
        if (!caster.isCreative() && inventorySlot < 0) return;
        var tool = CatalogModule.stack("enchanted_placeholder");
        if (swap.silk()) tool.enchant(Enchantments.SILK_TOUCH, 1);
        if (swap.fortune() > 0) tool.enchant(Enchantments.BLOCK_FORTUNE, swap.fortune());
        List<ItemStack> drops = caster.isCreative() ? List.of() : Block.getDrops(source, level, pos, level.getBlockEntity(pos), caster, tool);
        if (!valid(level, swap) || !caster.isCreative() && (slot(caster, selected) != inventorySlot
                || !ItemStack.isSameItemSameTags(caster.getInventory().getItem(inventorySlot), selected))) return;
        var replacement = selected.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        // Original replacement uses metadata/default orientation, not placement context or held NBT.
        ItemStack payment = caster.isCreative() ? ItemStack.EMPTY : caster.getInventory().removeItem(inventorySlot, 1);
        if (!caster.isCreative() && payment.isEmpty()) return;
        float drained = caster.isCreative() ? 0 : AuraManager.drainVis(level, pos, swap.vis(), false);
        if (!caster.isCreative() && drained < swap.vis()) {
            if (drained > 0) AuraManager.addVis(level, pos, drained);
            refund(level, caster, pos, payment); return;
        }
        if (!level.setBlock(pos, replacement, 3)) {
            if (drained > 0) AuraManager.addVis(level, pos, drained);
            refund(level, caster, pos, payment); return;
        }
        if (!(selected.getItem() instanceof BlockItem)) {
            // Original nonblock picks become a stationary EntitySpecialItem.
            var entity = new FocusExchangeItemEntity(RemainingEffectsModule.SPECIAL_ITEM.get(), level);
            entity.setPos(pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5); entity.setItem(selected.copy());
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); level.addFreshEntity(entity);
        }
        for (var drop : drops) refund(level, caster, pos, drop.copy());
        caster.getInventory().setChanged(); caster.inventoryMenu.broadcastChanges();
        if (caster.containerMenu != caster.inventoryMenu) caster.containerMenu.broadcastChanges();
        RemainingFocusEffects.particles(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), 8038177, 32);
    }
    private static void refund(ServerLevel level, ServerPlayer caster, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        caster.getInventory().add(stack);
        if (!stack.isEmpty()) {
            var entity = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
            entity.setDefaultPickUpDelay(); level.addFreshEntity(entity);
        }
    }
}
