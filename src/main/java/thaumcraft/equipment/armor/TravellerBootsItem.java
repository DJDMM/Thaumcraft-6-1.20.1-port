package thaumcraft.equipment.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.armor.CatalogArmorItem;
import thaumcraft.equipment.RechargeSupport;
import thaumcraft.equipment.RechargeableGear;

/** BETA26 traveller boots: the stored energy counter is a billing timer, not a source of movement charge. */
public final class TravellerBootsItem extends CatalogArmorItem implements RechargeableGear {
    public TravellerBootsItem(CatalogModule.Spec spec) { super(spec, Type.BOOTS); }

    @Override public int getMaxCharge(ItemStack stack, LivingEntity wearer) { return 240; }
    @Override public EnumChargeDisplay showInHud(ItemStack stack, LivingEntity wearer) { return EnumChargeDisplay.PERIODIC; }

    @Override public void onArmorTick(ItemStack stack, Level level, Player player) {
        boolean chargedAtStart = RechargeSupport.getCharge(stack) > 0;
        if (!level.isClientSide && player.tickCount % 20 == 0) {
            int energy = stack.hasTag() ? stack.getTag().getInt("energy") : 0;
            if (energy > 0) --energy;
            else if (RechargeSupport.consumeCharge(stack, player, 1)) energy = 60;
            stack.getOrCreateTag().putInt("energy", energy);
        }
        if (!chargedAtStart || player.getAbilities().flying || player.zza <= 0) return;
        if (!player.isShiftKeyDown()) ArmorEffects.enableTravellerStep(player);
        if (player.onGround()) player.moveRelative(player.isInWater() ? .0125F : .05F, new Vec3(0, 0, 1));
        else {
            if (player.isInWater()) player.moveRelative(.025F, new Vec3(0, 0, 1));
            else {
                // jumpMovementFactor was removed. Supply the difference from the modern normal/sprinting air input.
                float vanillaAirControl = player.isSprinting() ? .026F : .02F;
                player.moveRelative(.05F - vanillaAirControl, new Vec3(player.xxa, 0, player.zza));
            }
        }
    }

    public static boolean wearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof TravellerBootsItem;
    }
}
