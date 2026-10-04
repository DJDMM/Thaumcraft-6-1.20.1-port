package thaumcraft.auromancy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.catalog.CatalogItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.auromancy.focus.FocusStacks;
import java.util.List;

/** Block callbacks remain on their working devices; only unconsumed uses cast a focus. */
public final class CasterItem extends CatalogItem {
    public CasterItem(CatalogModule.Spec spec) { super(spec); }
    @Override public net.minecraft.world.item.Rarity getRarity(ItemStack stack) { return net.minecraft.world.item.Rarity.UNCOMMON; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if(player.isShiftKeyDown()&&thaumcraft.auromancy.remaining.FocusBlockPicker.isPicker(held))return InteractionResultHolder.pass(held);
        if (level.isClientSide) {
            var plan = FocusStacks.readPlan(FocusSelection.installed(held));
            if (plan.isEmpty() || player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(held);
            player.getCooldowns().addCooldown(this, plan.get().cooldownTicks());
            return InteractionResultHolder.success(held);
        }
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.pass(held);
        return switch (FocusCasting.cast(server, hand)) {
            case CAST -> InteractionResultHolder.success(held);
            case NO_VIS -> InteractionResultHolder.fail(held);
            default -> InteractionResultHolder.pass(held);
        };
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack,net.minecraft.world.item.context.UseOnContext context){
        var player=context.getPlayer();if(player==null||!player.isShiftKeyDown()||!thaumcraft.auromancy.remaining.FocusBlockPicker.isPicker(stack))return InteractionResult.PASS;
        // Functioning device callbacks and rituals keep priority and must not become Exchange samples.
        var block=context.getLevel().getBlockState(context.getClickedPos()).getBlock();
        if(block instanceof thaumcraft.essentia.transport.TubeBlock||block instanceof thaumcraft.infusion.InfusionMatrixBlock
                ||context.getLevel().getBlockEntity(context.getClickedPos())!=null)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server&&thaumcraft.auromancy.remaining.FocusBlockPicker.pick(server,context.getHand(),context.getClickedPos()))return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // The vanilla interact packet identifies an entity, but does not make it an authoritative spell target.
        return use(player.level(), player, hand).getResult();
    }

    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return oldStack.getItem()!=newStack.getItem() || !ItemStack.matches(FocusSelection.installed(oldStack), FocusSelection.installed(newStack));
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.thaumcraft.caster").withStyle(ChatFormatting.GRAY));
        ItemStack focus = FocusSelection.installed(stack);
        if (!focus.isEmpty()) {
            tooltip.add(focus.getHoverName().copy().withStyle(ChatFormatting.GREEN));
            FocusItem.addFocusTooltip(focus, tooltip);
        }
    }
}
