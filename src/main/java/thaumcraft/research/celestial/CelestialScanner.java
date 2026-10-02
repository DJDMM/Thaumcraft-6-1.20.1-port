package thaumcraft.research.celestial;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.theory.TheoryModule;

/** BETA26 ScanSky: called only after the thaumometer finds no object target. */
public final class CelestialScanner {
    public enum Result { NOT_APPLICABLE, CREATED, ALREADY_RECORDED, MISSING_RESOURCES, DELIVERY_FAILED, LOCKED }
    private CelestialScanner() {}

    public static Result scan(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || !player.serverLevel().getServer().isSameThread()) return Result.LOCKED;
        var level = player.serverLevel();
        if (!level.dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD)
                || !level.canSeeSky(player.blockPosition().above())
                || !KnowledgeStore.get(player).isResearchCompleteStrict("CELESTIALSCANNING")) return Result.NOT_APPLICABLE;
        int metadata = variantForAngles(player.getYRot(), player.getXRot(), level.getTimeOfDay(0), level.getMoonPhase());
        if (metadata < 0) return Result.NOT_APPLICABLE;
        long day = level.getGameTime() / 24000L;
        if (day < 0) return Result.LOCKED;
        if (KnowledgeStore.get(player).hasCelestial(day, metadata)) {
            player.displayClientMessage(Component.translatable("tc.celestial.fail.1", ""), true);
            return Result.ALREADY_RECORDED;
        }
        Inventory inventory = player.getInventory();
        int paperSlot = -1;
        boolean tools = false;
        // Original InventoryUtils iterates mainInventory only, with wildcard tool damage and relaxed NBT.
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.is(TheoryModule.SCRIBING_TOOLS.get())) tools = true;
            if (paperSlot < 0 && stack.is(Items.PAPER)) paperSlot = slot;
        }
        if (!tools || paperSlot < 0) {
            player.displayClientMessage(Component.translatable("tc.celestial.fail.2", ""), true);
            return Result.MISSING_RESOURCES;
        }
        ItemStack note = CelestialModule.note(metadata);
        int destination = destination(inventory, paperSlot, note);
        ItemEntity dropped = null;
        if (destination < 0) {
            // Preflight the real entity-join hook before charging paper or claiming the daily quota.
            dropped = new ItemEntity(level, player.getX(), player.getEyeY() - 0.3, player.getZ(), note.copy());
            dropped.setDefaultPickUpDelay();
            dropped.setThrower(player.getUUID());
            if (!level.addFreshEntity(dropped)) {
                player.displayClientMessage(Component.translatable("message.thaumcraft.celestial.delivery_failed"), true);
                return Result.DELIVERY_FAILED;
            }
            // Entity-join handlers run synchronously and may revoke eligibility before payment.
            if (!player.isAlive() || player.isSpectator() || player.serverLevel() != level
                    || !KnowledgeStore.get(player).isResearchCompleteStrict("CELESTIALSCANNING")
                    || dropped.isRemoved() || !ItemStack.matches(dropped.getItem(), note)
                    || !inventory.items.get(paperSlot).is(Items.PAPER) || !hasTools(inventory)
                    || KnowledgeStore.get(player).hasCelestial(day, metadata)) {
                dropped.discard();
                return Result.LOCKED;
            }
        }
        if (!KnowledgeStore.recordCelestial(player, day, metadata)) {
            if (dropped != null) dropped.discard();
            return Result.LOCKED;
        }
        inventory.items.get(paperSlot).shrink(1);
        if (destination >= 0) {
            ItemStack target = inventory.getItem(destination);
            if (target.isEmpty()) inventory.setItem(destination, note);
            else target.grow(1);
        }
        inventory.setChanged();
        player.displayClientMessage(Component.translatable("message.thaumcraft.celestial.created", note.getHoverName()), true);
        return Result.CREATED;
    }

    private static boolean hasTools(Inventory inventory) {
        return inventory.items.stream().anyMatch(stack -> stack.is(TheoryModule.SCRIBING_TOOLS.get()));
    }

    /** Vanilla 1.12 insertion order: selected stack, matching offhand, main stack, empty main slot. */
    private static int destination(Inventory inventory, int paperSlot, ItemStack note) {
        if (canMerge(inventory.getSelected(), note)) return inventory.selected;
        if (canMerge(inventory.getItem(40), note)) return 40;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (canMerge(inventory.items.get(slot), note)) return slot;
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty() || slot == paperSlot && stack.getCount() == 1) return slot;
        }
        return -1;
    }

    private static boolean canMerge(ItemStack target, ItemStack note) {
        return ItemStack.isSameItemSameTags(target, note) && target.getCount() < target.getMaxStackSize();
    }

    /** Literal binary boundaries: integer truncation, signed remainder, yaw <10 and pitch <7. */
    static int variantForAngles(float rotationYaw, float rotationPitch, float celestialAngle, int moonPhase) {
        if (!Float.isFinite(rotationYaw) || !Float.isFinite(rotationPitch) || !Float.isFinite(celestialAngle)
                || rotationPitch > 0 || moonPhase < 0 || moonPhase > 7) return -1;
        int yaw = (int) (rotationYaw + 90.0F) % 360;
        int pitch = (int) Math.abs(rotationPitch);
        int angle = (int) (((double) celestialAngle + 0.25D) * 360.0D) % 360;
        boolean night = angle > 180;
        if (night) angle -= 180;
        boolean yawInRange = angle > 90 ? Math.abs(Math.abs(yaw) - 180) < 10 : Math.abs(yaw) < 10;
        boolean pitchInRange = angle > 90 ? Math.abs(180 - angle - pitch) < 7 : Math.abs(angle - pitch) < 7;
        if (yawInRange && pitchInRange) return night ? 5 + moonPhase : 0;
        // 1.12 Entity.getAdjustedHorizontalFacing is exactly getHorizontalFacing for a player.
        Direction facing = Direction.from2DDataValue(Mth.floor((double) (rotationYaw * 4.0F / 360.0F) + 0.5D) & 3);
        return night ? starMetadata(facing) : -1;
    }

    static int starMetadata(Direction facing) {
        return switch (facing) {
            case NORTH -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            case EAST -> 4;
            default -> -1;
        };
    }
}
