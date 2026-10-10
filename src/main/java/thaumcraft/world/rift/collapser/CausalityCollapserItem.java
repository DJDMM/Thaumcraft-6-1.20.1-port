package thaumcraft.world.rift.collapser;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.catalog.entities.VisualEntitiesModule;

/** Original BETA26 consumable; throwing does not impose a new research or cooldown gate. */
public final class CausalityCollapserItem extends Item {
    public CausalityCollapserItem() { super(new Properties().stacksTo(16)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator() || held.getItem() != this || player.level() != level)
            return InteractionResultHolder.fail(held);
        if (level instanceof ServerLevel server) {
            if (!server.getServer().isSameThread()) return InteractionResultHolder.fail(held);
            var projectile = new CausalityCollapserEntity(VisualEntitiesModule.CAUSALITY_COLLAPSER.get(), level);
            projectile.setOwner(player);
            projectile.setPos(player.getX(), player.getEyeY() - .1, player.getZ());
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), -5F, .8F, 2F);
            // Preserve the native action, but avoid consuming an item when a Forge spawn callback rejects it.
            if (!server.addFreshEntity(projectile)) return InteractionResultHolder.fail(held);
            if (!player.getAbilities().instabuild) held.shrink(1);
            server.playSound(null, player.blockPosition(), SoundEvents.EGG_THROW, SoundSource.PLAYERS,
                    .3F, .4F / (player.getRandom().nextFloat() * .4F + .8F));
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
}
