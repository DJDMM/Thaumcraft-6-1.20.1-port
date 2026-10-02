package thaumcraft.equipment.items;

import net.minecraft.world.item.*;

/** BETA26 reusable crafting reagent: damage 0..6 returns the next form, 7 is consumed. */
public final class PrimordialPearlItem extends Item {
    public PrimordialPearlItem() { super(new Properties().durability(8).rarity(Rarity.UNCOMMON)); }
    @Override public String getDescriptionId(ItemStack stack) {
        return "item.primordial_pearl."+(stack.getDamageValue()<3 ? "pearl" : stack.getDamageValue()<6 ? "nodule" : "mote")+".name";
    }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return stack.getDamageValue()<7; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if(!hasCraftingRemainingItem(stack)) return ItemStack.EMPTY;
        ItemStack remainder=stack.copy();remainder.setDamageValue(stack.getDamageValue()+1);return remainder;
    }
    @Override public boolean isValidRepairItem(ItemStack stack,ItemStack repair) { return false; }
    @Override public boolean isRepairable(ItemStack stack) { return false; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack,net.minecraft.world.item.enchantment.Enchantment enchantment) { return false; }
}
