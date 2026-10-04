package thaumcraft.golemancy.press;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;

/** Detached reservations prevent the original simulate/consume double debit and partial component loss. */
final class GolemComponentPayment {
    private record Source(BlockEntity tile, BlockPos position, IItemHandlerModifiable handler, boolean player) {
        boolean valid(Level level, ServerPlayer owner) {
            return player ? owner.isAlive() && owner.level() == level
                    : level.hasChunkAt(position) && !tile.isRemoved() && level.getBlockEntity(position) == tile;
        }
    }
    private record Reservation(Source source, int slot, ItemStack before, int count) {}
    private static final class Candidate {
        final Source source;
        final int slot;
        final ItemStack before;
        int reserved;
        Candidate(Source source, int slot) { this.source = source; this.slot = slot; before = source.handler().getStackInSlot(slot).copy(); }
        int remaining() { return Math.max(0, before.getCount() - reserved); }
    }
    private final Level level;
    private final ServerPlayer player;
    private final List<Reservation> reservations;
    private GolemComponentPayment(Level level, ServerPlayer player, List<Reservation> reservations) {
        this.level = level; this.player = player; this.reservations = List.copyOf(reservations);
    }
    private static List<Candidate> candidates(Level level, BlockPos anchor, ServerPlayer player) {
        List<Candidate> result = new ArrayList<>();
        // EnumFacing.VALUES order was DOWN, UP, NORTH, SOUTH, WEST, EAST; UP is excluded.
        for (Direction face : Direction.values()) if (face != Direction.UP) {
            BlockPos pos = anchor.relative(face);
            if (!level.hasChunkAt(pos)) continue;
            BlockEntity tile = level.getBlockEntity(pos);
            if (tile == null || tile.isRemoved()) continue;
            IItemHandler handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite()).orElse(null);
            // A mutable endpoint is required to restore the exact escrow if another extraction callback invalidates it.
            if (!(handler instanceof IItemHandlerModifiable mutable) || handler.getSlots() > 256) continue;
            Source source = new Source(tile, pos.immutable(), mutable, false);
            for (int slot = 0; slot < handler.getSlots(); slot++) result.add(new Candidate(source, slot));
        }
        Source owner = new Source(null, player.blockPosition(), new PlayerMainInvWrapper(player.getInventory()), true);
        for (int slot = 0; slot < owner.handler().getSlots(); slot++) result.add(new Candidate(owner, slot));
        return result;
    }
    static GolemComponentPayment reserve(Level level, BlockPos anchor, ServerPlayer player, List<ItemStack> components) {
        if (components == null || components.isEmpty() || components.size() > 32) return null;
        List<Candidate> candidates = candidates(level, anchor, player);
        List<Candidate> ordered = new ArrayList<>();
        for (ItemStack component : components) {
            if (component.isEmpty() || component.getCount() <= 0 || component.getCount() > 1024) return null;
            // BETA26 requires an entire component count in adjacent storage OR in the player's main inventory.
            // Do not aggregate a partial chest with a partial player stack, and do not overcharge both sources.
            boolean fromPlayer = count(candidates, component, false) < component.getCount();
            if (fromPlayer && count(candidates, component, true) < component.getCount()) return null;
            int remaining = component.getCount();
            for (Candidate candidate : candidates) {
                if (candidate.source.player() != fromPlayer || candidate.remaining() == 0 || !matches(component, candidate.before, fromPlayer)) continue;
                int take = Math.min(remaining, candidate.remaining());
                ItemStack simulated = candidate.source.handler().extractItem(candidate.slot, candidate.reserved + take, true);
                if (!ItemStack.isSameItemSameTags(simulated, candidate.before) || simulated.getCount() < candidate.reserved + take) continue;
                if (candidate.reserved == 0) ordered.add(candidate);
                candidate.reserved += take; remaining -= take;
                if (remaining == 0) break;
            }
            if (remaining != 0) return null;
        }
        List<Reservation> reservations = ordered.stream()
                .map(candidate -> new Reservation(candidate.source, candidate.slot, candidate.before.copy(), candidate.reserved)).toList();
        return new GolemComponentPayment(level, player, reservations);
    }
    private static int count(List<Candidate> candidates, ItemStack component, boolean player) {
        int count = 0;
        for (Candidate candidate : candidates) if (candidate.source.player() == player && candidate.remaining() > 0 && matches(component, candidate.before, player)) {
            ItemStack extracted = candidate.source.handler().extractItem(candidate.slot, candidate.before.getCount(), true);
            if (ItemStack.isSameItemSameTags(candidate.before, extracted)) count += Math.max(0, extracted.getCount() - candidate.reserved);
        }
        return count;
    }
    static boolean[] available(Level level, BlockPos anchor, ServerPlayer player, List<ItemStack> components) {
        boolean[] result = new boolean[components.size()];
        for (int index = 0; index < result.length; index++)
            result[index] = reserve(level, anchor, player, List.of(components.get(index))) != null;
        return result;
    }
    boolean commit(java.util.function.BooleanSupplier guard) {
        if (!guard.getAsBoolean()) return false;
        for (Reservation reservation : reservations) {
            if (!reservation.source.valid(level, player) || !ItemStack.matches(reservation.before, reservation.source.handler().getStackInSlot(reservation.slot))) return false;
            ItemStack simulated = reservation.source.handler().extractItem(reservation.slot, reservation.count, true);
            if (simulated.getCount() != reservation.count || !ItemStack.isSameItemSameTags(simulated, reservation.before)) return false;
        }
        List<Reservation> paid = new ArrayList<>();
        try {
            for (Reservation reservation : reservations) {
                if (!guard.getAsBoolean() || !reservation.source.valid(level, player) || !ItemStack.matches(reservation.before, reservation.source.handler().getStackInSlot(reservation.slot))) {
                    rollback(paid); return false;
                }
                paid.add(reservation);
                ItemStack removed = reservation.source.handler().extractItem(reservation.slot, reservation.count, false);
                ItemStack expected = reservation.before.copy(); expected.shrink(reservation.count);
                if (removed.getCount() != reservation.count || !ItemStack.isSameItemSameTags(removed, reservation.before)
                        || !ItemStack.matches(expected, reservation.source.handler().getStackInSlot(reservation.slot))) {
                    rollback(paid); return false;
                }
            }
            if (!guard.getAsBoolean()) { rollback(paid); return false; }
        } catch (RuntimeException failure) { rollback(paid); return false; }
        player.getInventory().setChanged();
        return true;
    }
    private void rollback(List<Reservation> paid) {
        for (int index = paid.size() - 1; index >= 0; index--) {
            Reservation reservation = paid.get(index);
            reservation.source.handler().setStackInSlot(reservation.slot, reservation.before.copy());
        }
        player.getInventory().setChanged();
    }
    /** Original BASEORE matching first ignores NBT on a known ore equivalent, then uses exact item/NBT. */
    static boolean matches(ItemStack required, ItemStack candidate, boolean player) {
        if (required.isEmpty() || candidate.isEmpty()) return false;
        TagKey<Item> ore = oreTag(required);
        if (ore != null && (candidate.is(ore) || candidate.is(required.getItem()))) return true;
        if (!candidate.is(required.getItem()) || required.getDamageValue() != candidate.getDamageValue()) return false;
        if (player && !required.hasTag()) return true;
        return ItemStack.isSameItemSameTags(required, candidate);
    }
    private static TagKey<Item> oreTag(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        String path = id.getPath(), tag = null;
        if (id.getNamespace().equals("thaumcraft") && (path.equals("plank_greatwood") || path.equals("plank_silverwood"))) tag = "minecraft:planks";
        else if (id.getNamespace().equals("thaumcraft") && path.startsWith("plate_")) tag = "forge:plates/" + path.substring(6);
        else if (id.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"))) tag = "forge:gems/diamond";
        else if (id.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "leather"))) tag = "forge:leather";
        return tag == null ? null : TagKey.create(Registries.ITEM, new ResourceLocation(tag));
    }
}
