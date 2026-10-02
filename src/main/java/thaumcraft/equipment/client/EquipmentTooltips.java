package thaumcraft.equipment.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.items.*;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.equipment.*;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class EquipmentTooltips {
    private EquipmentTooltips() {}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var stack=event.getItemStack();
        if(stack.isEmpty()) return;
        int runic=stack.hasTag() ? stack.getTag().getByte("TC.RUNIC") : 0;
        if(runic>0) event.getToolTip().add(Component.translatable("item.runic.charge").append(" +"+runic).withStyle(ChatFormatting.GOLD));
        int warp=GearSupport.getFinalWarp(stack,event.getEntity());
        if(warp>0) event.getToolTip().add(Component.translatable("item.warping").append(" "+warp).withStyle(ChatFormatting.DARK_PURPLE));
        if(stack.getItem() instanceof IVisDiscountGear gear) {
            int discount=gear.getVisDiscount(stack,event.getEntity());
            if(discount>0) event.getToolTip().add(Component.translatable("tc.visdiscount").append(": "+discount+"%").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if(stack.getItem() instanceof IRechargable) event.getToolTip().add(Component.translatable("tc.charge").append(" "+RechargeSupport.getCharge(stack)).withStyle(ChatFormatting.YELLOW));
        for(var enchantment:EnumInfusionEnchantment.getInfusionEnchantments(stack)) {
            var label=Component.translatable("enchantment.infusion."+enchantment.name());
            if(enchantment.maxLevel>1) label.append(" ").append(Component.translatable("enchantment.level."+EnumInfusionEnchantment.getInfusionEnchantmentLevel(stack,enchantment)));
            event.getToolTip().add(Math.min(1,event.getToolTip().size()),label.withStyle(ChatFormatting.GOLD));
        }
    }
}
