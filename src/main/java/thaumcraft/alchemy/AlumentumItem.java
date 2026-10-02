package thaumcraft.alchemy;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.*;

public final class AlumentumItem extends Item {
    public AlumentumItem() { super(new Properties()); }
    @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) { return 4800; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            var projectile = new AlumentumProjectile(AlchemyModule.ALUMENTUM_ENTITY.get(), level);
            projectile.setOwner(player);
            projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), -5F, 0.75F, 2F);
            level.addFreshEntity(projectile);
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.getCooldowns().addCooldown(this, 5);
            level.playSound(null, player.blockPosition(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.3F, 0.5F);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
}
