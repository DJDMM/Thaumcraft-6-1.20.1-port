package thaumcraft.equipment.armor;

import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.catalog.armor.CatalogArmorItem;

/** Pure BETA26 contracts also usable by damage/warp integrations and server tests. */
public final class FortressArmorSupport {
    private FortressArmorSupport() {}

    public record SetBonus(int pieces, int masks, int armor, int toughness) {}
    public record Protection(int priority, double ratio, int absorbMax) {}

    public static SetBonus setBonus(LivingEntity wearer) {
        if (!(wearer instanceof Player)) return new SetBonus(0, 0, 0, 0);
        int pieces = 0, masks = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (stack.getItem() instanceof CatalogArmorItem armor && armor.isFortress()) {
                pieces++;
                if (stack.hasTag() && stack.getTag().contains("mask")) masks++;
            }
        }
        // Original getProperties repeats the first-piece and every present-mask bonus for each fortress piece.
        return new SetBonus(pieces, masks, pieces * ((pieces > 0 ? 1 : 0) + masks), pieces);
    }

    public static int armorDisplay(Player player) {
        SetBonus bonus = setBonus(player);
        return bonus.pieces() == 0 ? 0 : 1 + bonus.masks();
    }

    public static boolean special(ItemStack stack) {
        return stack.getItem() instanceof CatalogArmorItem armor && (armor.isFortress() || armor.isVoidRobe());
    }

    public static Protection protection(ItemStack stack, DamageSource source) {
        if (!(stack.getItem() instanceof CatalogArmorItem armor) || !special(stack)) return new Protection(0, 0, 0);
        int priority = 0;
        double ratio = armor.getDefense() / 25D;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            priority = 1;
            ratio = armor.getDefense() / 35D;
        } else if (armor.isFortress() && (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION))) {
            priority = 1;
            ratio = armor.getDefense() / 20D;
        } else if (source.is(DamageTypeTags.BYPASSES_ARMOR)) ratio = 0;
        return new Protection(priority, ratio, stack.getMaxDamage() + 1 - stack.getDamageValue());
    }

    public static boolean hasMask(Player wearer, int mask) {
        ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof CatalogArmorItem armor && armor.isFortress()
                && head.hasTag() && head.getTag().contains("mask") && head.getTag().getInt("mask") == mask;
    }

    /** Original WarpEvents mask 0 subtracts 2..5 from event severity, not permanent/temporary warp. */
    public static int reduceWarpEventSeverity(Player wearer, int severity, RandomSource random) {
        return hasMask(wearer, 0) ? severity - 2 - random.nextInt(4) : severity;
    }
}
