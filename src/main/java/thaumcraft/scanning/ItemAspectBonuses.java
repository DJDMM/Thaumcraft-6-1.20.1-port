package thaumcraft.scanning;

import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/** Equipment and enchantment contributions ported from TC6 getBonusTags. */
final class ItemAspectBonuses {
    private ItemAspectBonuses() {}

    static AspectList apply(ItemStack stack, AspectList result) {
        Item item = stack.getItem();
        if (item instanceof thaumcraft.api.aspects.IEssentiaContainerItem container && !container.ignoreContainedAspects()) {
            AspectList contents = container.getAspects(stack);
            result = contents == null ? new AspectList() : contents.copy();
        }
        if (item instanceof ArmorItem armor) result.merge(Aspect.PROTECT, armor.getDefense() * 4);
        else if (item instanceof SwordItem sword) result.merge(Aspect.AVERSION, (int) (sword.getDamage() + 1) * 4);
        else if (item instanceof BowItem) result.merge(Aspect.AVERSION, 10).merge(Aspect.FLIGHT, 5);
        else if (item instanceof HoeItem || item instanceof ShearsItem) {
            int durability = stack.getMaxDamage();
            result.merge(Aspect.TOOL, durability <= Tiers.WOOD.getUses() ? 4 : durability <= Tiers.STONE.getUses() ? 8 : durability <= Tiers.IRON.getUses() ? 12 : 16);
        } else if (item instanceof DiggerItem digger) result.merge(Aspect.TOOL, (digger.getTier().getLevel() + 1) * 4);
        if (item instanceof DyeItem) result.merge(Aspect.SENSES, 5);

        int magic = 0;
        for (var entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            int level = Math.max(0, Math.min(255, entry.getValue())) * 3;
            Enchantment enchantment = entry.getKey();
            var id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            if (id != null && id.getNamespace().equals("minecraft")) {
                switch (id.getPath()) {
                    case "aqua_affinity", "depth_strider" -> result.merge(Aspect.WATER, level);
                    case "bane_of_arthropods" -> result.merge(Aspect.BEAST, level / 2).merge(Aspect.AVERSION, level / 2);
                    case "blast_protection" -> result.merge(Aspect.PROTECT, level / 2).merge(Aspect.ENTROPY, level / 2);
                    case "efficiency" -> result.merge(Aspect.TOOL, level);
                    case "feather_falling" -> result.merge(Aspect.FLIGHT, level);
                    case "fire_aspect" -> result.merge(Aspect.FIRE, level / 2).merge(Aspect.AVERSION, level / 2);
                    case "fire_protection" -> result.merge(Aspect.PROTECT, level / 2).merge(Aspect.FIRE, level / 2);
                    case "flame" -> result.merge(Aspect.FIRE, level);
                    case "fortune", "looting", "luck_of_the_sea" -> result.merge(Aspect.DESIRE, level);
                    case "infinity", "mending" -> result.merge(Aspect.CRAFT, level);
                    case "knockback", "punch", "respiration" -> result.merge(Aspect.AIR, level);
                    case "power", "sharpness", "thorns" -> result.merge(Aspect.AVERSION, level);
                    case "projectile_protection", "protection" -> result.merge(Aspect.PROTECT, level);
                    case "silk_touch" -> result.merge(Aspect.EXCHANGE, level);
                    case "smite" -> result.merge(Aspect.UNDEAD, level / 2).merge(Aspect.AVERSION, level / 2);
                    case "unbreaking" -> result.merge(Aspect.EARTH, level);
                    case "lure" -> result.merge(Aspect.BEAST, level);
                    case "frost_walker" -> result.merge(Aspect.COLD, level);
                    default -> { } // Post-1.12 enchantments only receive the common magic contribution.
                }
            }
            magic += level + switch (enchantment.getRarity()) {
                case COMMON -> 0;
                case UNCOMMON -> 2;
                case RARE -> 4;
                case VERY_RARE -> 6;
            };
        }
        if (magic > 0) result.merge(Aspect.MAGIC, Math.min(500, magic));
        for (Aspect aspect : result.getAspects()) {
            int amount = result.getAmount(aspect);
            if (amount <= 0) result.remove(aspect);
            else if (amount > 500) { result.remove(aspect); result.add(aspect, 500); }
        }
        return result;
    }
}
