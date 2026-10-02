package thaumcraft.equipment.cleansing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class SanitySoapItem extends Item {
    public SanitySoapItem(Properties properties) { super(properties); }
    @Override public int getUseDuration(ItemStack stack) { return 100; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        // Modern stopUsingItem does NOT call releaseUsing. releaseUsingItem is the old stopActiveHand equivalent.
        if (getUseDuration(stack) - remaining > 95) entity.releaseUsingItem();
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CleansingClient.soapUsing(entity));
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (getUseDuration(stack) - remaining > 95 && entity instanceof ServerPlayer player)
            CleansingSupport.finishSoap(player, stack);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Normally unreachable: onUseTick releases at remaining=4. Covers forced modern finish calls.
        if (entity instanceof ServerPlayer player) CleansingSupport.finishSoap(player, stack);
        return stack;
    }
}
