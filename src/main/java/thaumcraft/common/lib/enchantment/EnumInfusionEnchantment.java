package thaumcraft.common.lib.enchantment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** BETA26 ordinal IDs and infench NBT. The infusion recipe system is separate. */
public enum EnumInfusionEnchantment {
    COLLECTOR(Set.of("axe", "pickaxe", "shovel", "weapon"),1,"INFUSIONENCHANTMENT"),
    DESTRUCTIVE(Set.of("axe", "pickaxe", "shovel"),1,"INFUSIONENCHANTMENT"),
    BURROWING(Set.of("axe", "pickaxe"),1,"INFUSIONENCHANTMENT"),
    SOUNDING(Set.of("pickaxe"),4,"INFUSIONENCHANTMENT"),
    REFINING(Set.of("pickaxe"),4,"INFUSIONENCHANTMENT"),
    ARCING(Set.of("weapon"),4,"INFUSIONENCHANTMENT"),
    ESSENCE(Set.of("weapon"),5,"INFUSIONENCHANTMENT"),
    VISBATTERY(Set.of("chargable"),3,"?"), VISCHARGE(Set.of("chargable"),1,"?"),
    SWIFT(Set.of("boots"),4,"IEARMOR"), AGILE(Set.of("legs"),1,"IEARMOR"),
    INFESTED(Set.of("chest"),1,"IETAINT"),
    LAMPLIGHT(Set.of("axe", "pickaxe", "shovel"),1,"INFUSIONENCHANTMENT");
    public final Set<String> toolClasses;
    public final int maxLevel;
    public final String research;
    EnumInfusionEnchantment(Set<String> classes,int level,String research) { toolClasses=classes;maxLevel=level;this.research=research; }
    public static ListTag getInfusionEnchantmentTagList(ItemStack stack) {
        return stack == null || stack.isEmpty() || !stack.hasTag() ? null : stack.getTag().getList("infench", Tag.TAG_COMPOUND);
    }
    public static List<EnumInfusionEnchantment> getInfusionEnchantments(ItemStack stack) {
        List<EnumInfusionEnchantment> result = new ArrayList<>();
        ListTag list = getInfusionEnchantmentTagList(stack);
        if (list != null) for (int i=0;i<list.size();i++) {
            int id=list.getCompound(i).getShort("id");
            if (id>=0 && id<values().length) result.add(values()[id]);
        }
        return result;
    }
    public static int getInfusionEnchantmentLevel(ItemStack stack, EnumInfusionEnchantment enchantment) {
        ListTag list=getInfusionEnchantmentTagList(stack);
        if(list!=null) for(int i=0;i<list.size();i++) {
            CompoundTag row=list.getCompound(i);
            if(row.getShort("id")==enchantment.ordinal()) return row.getShort("lvl");
        }
        return 0;
    }
    public static void addInfusionEnchantment(ItemStack stack, EnumInfusionEnchantment enchantment,int level) {
        if(stack==null || stack.isEmpty() || level<=0 || level>enchantment.maxLevel) return;
        ListTag list=getInfusionEnchantmentTagList(stack);
        if(list==null) list=new ListTag();
        for(int i=0;i<list.size();i++) {
            CompoundTag row=list.getCompound(i);
            if(row.getShort("id")==enchantment.ordinal()) {
                if(level>row.getShort("lvl")) row.putShort("lvl",(short)level);
                stack.getOrCreateTag().put("infench",list); return;
            }
        }
        CompoundTag row=new CompoundTag(); row.putShort("id",(short)enchantment.ordinal()); row.putShort("lvl",(short)level);
        list.add(row); stack.getOrCreateTag().put("infench",list);
    }
}
