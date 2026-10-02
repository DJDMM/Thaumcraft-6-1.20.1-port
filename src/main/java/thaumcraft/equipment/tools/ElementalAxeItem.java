package thaumcraft.equipment.tools;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The BETA26 held-use item magnet. It attracts ordinary entities, preserving normal pickup/overflow. */
public final class ElementalAxeItem extends ToolItems.Axe {
    public ElementalAxeItem() { super(ToolMaterials.ELEMENTAL, 0); }
    @Override public Rarity getRarity(ItemStack stack) { return Rarity.RARE; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand); return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide) return;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, user.getBoundingBox().inflate(10),
                entity -> entity.isAlive() && entity.distanceToSqr(user) < 100)) {
            Vec3 offset = item.position().subtract(user.position()).add(0, user.getBbHeight() / 2, 0);
            // Original divides by the distance. Zero distance is explicitly hardened against NaN velocity.
            if (offset.lengthSqr() < 1.0E-8) continue;
            Vec3 movement = item.getDeltaMovement().subtract(offset.normalize().scale(.3)).add(0, .1, 0);
            item.setDeltaMovement(clamp(movement.x), clamp(movement.y), clamp(movement.z));
            item.hasImpulse = true;
            item.hurtMarked = true;
        }
    }
    private static double clamp(double value) { return Math.max(-.25, Math.min(.25, value)); }
}
