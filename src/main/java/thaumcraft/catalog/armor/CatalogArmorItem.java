package thaumcraft.catalog.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import thaumcraft.api.items.IGoggles;
import thaumcraft.api.items.IRevealer;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.equipment.VisDiscountGear;
import thaumcraft.equipment.WarpingGear;
import thaumcraft.equipment.armor.Tc6ArmorMaterials;
import thaumcraft.equipment.armor.TravellerBootsItem;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** TC6 BETA26 armor mechanics with the existing original texture and geometry adapters. */
public class CatalogArmorItem extends ArmorItem implements VisDiscountGear, WarpingGear, IGoggles, IRevealer {
    private static final Set<String> SOURCES = Set.of("ItemThaumiumArmor", "ItemRobeArmor", "ItemVoidArmor",
            "ItemVoidRobeArmor", "ItemFortressArmor", "ItemCultistPlateArmor", "ItemCultistRobeArmor",
            "ItemCultistLeaderArmor", "ItemCultistBoots", "ItemGoggles", "ItemBootsTraveller");
    private final CatalogModule.Spec spec;

    public CatalogArmorItem(CatalogModule.Spec spec, Type type) {
        super(materialForSource(spec.sourceClass()), type, properties(spec.sourceClass()));
        this.spec = spec;
    }

    public CatalogModule.Spec spec() { return spec; }
    public boolean isFortress() { return spec.sourceClass().equals("ItemFortressArmor"); }
    public boolean isVoidRobe() { return spec.sourceClass().equals("ItemVoidRobeArmor"); }
    public boolean selfRepairs() { return isVoidRobe() || spec.sourceClass().equals("ItemVoidArmor"); }

    public static Item create(CatalogModule.Spec spec) {
        if (!SOURCES.contains(spec.sourceClass())) return null;
        Type type = spec.id().endsWith("helm") || spec.id().equals("goggles") ? Type.HELMET
                : spec.id().endsWith("chest") ? Type.CHESTPLATE : spec.id().endsWith("legs") ? Type.LEGGINGS : Type.BOOTS;
        if (spec.sourceClass().equals("ItemBootsTraveller")) return new TravellerBootsItem(spec);
        return spec.sourceClass().equals("ItemRobeArmor") || spec.sourceClass().equals("ItemVoidRobeArmor")
                ? new Dyed(spec, type) : new CatalogArmorItem(spec, type);
    }

    /** Entry point for EquipmentModule's four already-registered thaumium pieces. */
    public static CatalogArmorItem thaumium(Type type) {
        String suffix = switch (type) { case HELMET -> "helm"; case CHESTPLATE -> "chest"; case LEGGINGS -> "legs"; case BOOTS -> "boots"; };
        String id = "thaumium_" + suffix;
        return new CatalogArmorItem(new CatalogModule.Spec(id, id, 0, "item." + id + ".name", "ItemThaumiumArmor", 1), type);
    }

    public static ArmorMaterial materialForSource(String source) {
        return switch (source) {
            case "ItemThaumiumArmor" -> Tc6ArmorMaterials.THAUMIUM;
            case "ItemVoidArmor" -> Tc6ArmorMaterials.VOID;
            case "ItemVoidRobeArmor" -> Tc6ArmorMaterials.VOID_ROBE;
            case "ItemFortressArmor" -> Tc6ArmorMaterials.FORTRESS;
            case "ItemCultistPlateArmor" -> Tc6ArmorMaterials.CULTIST_PLATE;
            case "ItemCultistRobeArmor" -> Tc6ArmorMaterials.CULTIST_ROBE;
            case "ItemCultistLeaderArmor" -> Tc6ArmorMaterials.CULTIST_LEADER;
            case "ItemCultistBoots" -> ArmorMaterials.IRON;
            default -> Tc6ArmorMaterials.SPECIAL;
        };
    }

    private static Properties properties(String source) {
        Rarity rarity = switch (source) {
            case "ItemVoidRobeArmor" -> Rarity.EPIC;
            case "ItemFortressArmor", "ItemGoggles", "ItemBootsTraveller", "ItemCultistLeaderArmor" -> Rarity.RARE;
            default -> Rarity.UNCOMMON;
        };
        Properties properties = new Properties().rarity(rarity);
        if (source.equals("ItemGoggles") || source.equals("ItemBootsTraveller")) properties.durability(350);
        return properties;
    }

    @Override public boolean isValidRepairItem(ItemStack armor, ItemStack ingredient) {
        Ingredient extra = switch (spec.sourceClass()) {
            case "ItemRobeArmor" -> Tc6ArmorMaterials.ingredient("fabric");
            case "ItemGoggles" -> Tc6ArmorMaterials.ingredient("ingot_brass");
            case "ItemBootsTraveller" -> Ingredient.of(Items.LEATHER);
            default -> Ingredient.EMPTY;
        };
        return extra.test(ingredient) || super.isValidRepairItem(armor, ingredient);
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        if (!level.isClientSide && selfRepairs() && holder instanceof LivingEntity && holder.tickCount % 20 == 0 && stack.isDamaged())
            stack.setDamageValue(stack.getDamageValue() - 1);
    }

    @Override public void onArmorTick(ItemStack stack, Level level, Player wearer) {
        // Keep both original entry points. Forge ticks worn stacks through inventoryTick and onArmorTick.
        // BETA26 therefore repairs carried gear by 1 and worn player gear by up to 2 every twenty ticks.
        if (!level.isClientSide && selfRepairs() && wearer.tickCount % 20 == 0 && stack.isDamaged())
            stack.setDamageValue(stack.getDamageValue() - 1);
    }

    @Override public int getWarp(ItemStack stack, LivingEntity wearer) {
        return switch (spec.sourceClass()) {
            case "ItemVoidRobeArmor" -> 3;
            case "ItemVoidArmor", "ItemCultistRobeArmor", "ItemCultistBoots" -> 1;
            default -> 0;
        };
    }
    @Override public int getVisDiscount(ItemStack stack, Player wearer) {
        return switch (spec.sourceClass()) {
            case "ItemGoggles", "ItemVoidRobeArmor" -> 5;
            case "ItemRobeArmor" -> getType() == Type.BOOTS ? 2 : 3;
            case "ItemCultistRobeArmor", "ItemCultistBoots" -> 1;
            default -> 0;
        };
    }
    @Override public boolean showNodes(ItemStack stack, LivingEntity wearer) { return reveals(stack); }
    @Override public boolean showIngamePopups(ItemStack stack, LivingEntity wearer) { return reveals(stack); }
    private boolean reveals(ItemStack stack) {
        return spec.sourceClass().equals("ItemGoggles") || (isVoidRobe() && getType() == Type.HELMET)
                || (isFortress() && stack.hasTag() && stack.getTag().contains("goggles"));
    }

    @Override public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String overlay) {
        String name = switch (spec.sourceClass()) {
            case "ItemThaumiumArmor" -> "thaumium_" + (slot == EquipmentSlot.LEGS ? 2 : 1);
            case "ItemVoidArmor" -> "void_" + (slot == EquipmentSlot.LEGS ? 2 : 1);
            case "ItemRobeArmor" -> "robes_" + (slot == EquipmentSlot.LEGS ? 2 : 1) + (overlay == null ? "" : "_overlay");
            case "ItemVoidRobeArmor" -> "void_robe_armor" + (overlay == null ? "_overlay" : "");
            case "ItemFortressArmor" -> "fortress_armor";
            case "ItemCultistPlateArmor" -> entity instanceof thaumcraft.catalog.entities.VisualMobEntity mob
                    && mob.spec().id().equals("inhabited_zombie") ? "zombie_plate_armor" : "cultist_plate_armor";
            case "ItemCultistRobeArmor" -> "cultist_robe_armor";
            case "ItemCultistLeaderArmor" -> "cultist_leader_armor";
            case "ItemBootsTraveller" -> "bootstraveler";
            case "ItemCultistBoots" -> "cultistboots";
            default -> "goggles";
        };
        return "thaumcraft:textures/entity/armor/" + name + ".png";
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new thaumcraft.catalog.armor.client.CatalogArmorExtension(this));
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (isFortress() && stack.hasTag()) {
            if (stack.getTag().contains("goggles")) tooltip.add(Component.translatable("item.thaumcraft.goggles").withStyle(ChatFormatting.DARK_PURPLE));
            if (stack.getTag().contains("mask")) {
                int mask = stack.getTag().getInt("mask");
                String fallback = switch (mask) { case 0 -> "Grinning Devil"; case 1 -> "Angry Ghost"; case 2 -> "Sipping Fiend"; default -> "Mask " + mask; };
                tooltip.add(Component.translatableWithFallback("item.fortress_helm.mask." + mask, fallback).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static final class Dyed extends CatalogArmorItem implements DyeableLeatherItem {
        Dyed(CatalogModule.Spec spec, Type type) { super(spec, type); }
        @Override public boolean hasCustomColor(ItemStack stack) { return true; }
        @Override public int getColor(ItemStack stack) {
            return stack.hasTag() && stack.getTag().getCompound("display").contains("color")
                    ? stack.getTag().getCompound("display").getInt("color") : 6961280;
        }
        @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
            var state = context.getLevel().getBlockState(context.getClickedPos());
            if (state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) > 0) {
                if (!context.getLevel().isClientSide) {
                    clearColor(stack);
                    LayeredCauldronBlock.lowerFillLevel(state, context.getLevel(), context.getClickedPos());
                }
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            }
            return InteractionResult.PASS;
        }
    }
}
