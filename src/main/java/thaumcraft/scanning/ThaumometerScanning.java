package thaumcraft.scanning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.research.AuromancyProgressionEvents;
import thaumcraft.research.KnowledgeStore;

import javax.annotation.Nullable;

/** BETA26 scan commit; callers first reconstruct and validate the actual server target. */
public final class ThaumometerScanning {
    public static final int INVENTORY_LIMIT = 100;

    private ThaumometerScanning() {}

    /** Generic Observation and independent registered scan proofs can both match one specimen. */
    public static boolean scanObject(ServerPlayer player, @Nullable Object specimen) {
        if (!validPlayer(player) || specimen == null) return false;
        if (specimen instanceof Entity entity && (entity.isRemoved() || entity.level() != player.level())) return false;
        Object object = specimen instanceof ItemEntity item ? item.getItem().copy() : specimen;
        AspectList aspects;
        String key;
        if (object instanceof ItemStack stack) {
            if (stack.isEmpty()) return false;
            var target = ThaumometerItem.itemTarget(stack, Vec3.ZERO);
            aspects = target.aspects();
            key = target.key();
        } else if (object instanceof Entity entity) {
            aspects = AspectRegistry.getAspects(entity);
            key = ThaumometerItem.scanKey("entity:" + ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), aspects);
        } else if (object instanceof BlockState state) {
            aspects = AspectRegistry.getAspects(state);
            String identity = state.getBlock().asItem() == Items.AIR
                    ? "block:" + ForgeRegistries.BLOCKS.getKey(state.getBlock())
                    : "item:" + ForgeRegistries.ITEMS.getKey(state.getBlock().asItem());
            key = ThaumometerItem.scanKey(identity, aspects);
        } else return false;
        boolean changed = aspects.size() > 0 && KnowledgeStore.recordScan(player, key, aspects);
        // A known generic identity must not suppress new independent ScanItem/ScanEntity proofs.
        return AuromancyProgressionEvents.recordScannedFact(player, object) || changed;
    }

    /** The original always asks the inventory's UP face, regardless of the clicked block face. */
    public static ContentsResult scanContents(ServerPlayer player, BlockPos pos) {
        if (!validPlayer(player) || pos == null || !player.serverLevel().hasChunkAt(pos)) return ContentsResult.EMPTY;
        var blockEntity = player.serverLevel().getBlockEntity(pos);
        if (blockEntity == null || blockEntity.isRemoved()) return ContentsResult.EMPTY;
        // Forge's ChestBlockEntity capability already wraps the native combined
        // double-chest container. Retain TC6's hook-first, vanilla-inventory fallback.
        var handler = VanillaInventoryCodeHooks.getItemHandler(player.serverLevel(),
                pos.getX(), pos.getY(), pos.getZ(), Direction.UP);
        if (handler.isPresent()) return scanContents(player, handler.get().getLeft());
        if (blockEntity instanceof WorldlyContainer container)
            return scanContents(player, new SidedInvWrapper(container, Direction.UP));
        if (blockEntity instanceof Container container) return scanContents(player, new InvWrapper(container));
        return ContentsResult.EMPTY;
    }

    /** Traversal is read-only. Empty slots and already-known contents do not change the limit semantics. */
    static ContentsResult scanContents(ServerPlayer player, IItemHandler handler) {
        if (!validPlayer(player) || handler == null) return ContentsResult.EMPTY;
        int scanned = 0;
        boolean changed = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) continue;
            changed |= scanObject(player, stack.copy());
            if (++scanned >= INVENTORY_LIMIT) return new ContentsResult(scanned, changed, true);
        }
        return new ContentsResult(scanned, changed, false);
    }

    private static boolean validPlayer(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator()
                && player.serverLevel().getServer().isSameThread();
    }

    public record ContentsResult(int scanned, boolean changed, boolean capped) {
        public static final ContentsResult EMPTY = new ContentsResult(0, false, false);
    }
}
