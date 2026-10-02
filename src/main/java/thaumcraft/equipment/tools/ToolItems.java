package thaumcraft.equipment.tools;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.equipment.WarpingGear;

import javax.annotation.Nullable;

/** Separate original tool tiers and behaviours. Raw /give stacks retain their original unenchanted state. */
public final class ToolItems {
    private ToolItems() {}

    @Nullable public static Item create(CatalogModule.Spec spec) {
        return switch (spec.id()) {
            case "void_pick" -> new Pick(ToolMaterials.VOID, 1);
            case "void_shovel" -> new Shovel(ToolMaterials.VOID, 1);
            case "void_axe" -> new Axe(ToolMaterials.VOID, 1);
            case "void_sword" -> new Sword(ToolMaterials.VOID, 1);
            case "void_hoe" -> new Hoe(ToolMaterials.VOID, 1);
            case "crimson_blade" -> new Sword(ToolMaterials.CRIMSON, 2);
            case "elemental_pick" -> new ElementalPickaxeItem();
            case "elemental_shovel" -> new ElementalShovelItem();
            case "elemental_axe" -> new ElementalAxeItem();
            case "elemental_sword" -> new ElementalSwordItem();
            case "elemental_hoe" -> new ElementalHoeItem();
            case "primal_crusher" -> new PrimalCrusherItem();
            default -> null;
        };
    }

    public static Item thaumium(String id) {
        return switch (id) {
            case "thaumium_pick" -> new Pick(ToolMaterials.THAUMIUM, 0);
            case "thaumium_shovel" -> new Shovel(ToolMaterials.THAUMIUM, 0);
            case "thaumium_axe" -> new Axe(ToolMaterials.THAUMIUM, 0);
            case "thaumium_sword" -> new Sword(ToolMaterials.THAUMIUM, 0);
            case "thaumium_hoe" -> new Hoe(ToolMaterials.THAUMIUM, 0);
            default -> throw new IllegalArgumentException("Unknown thaumium tool " + id);
        };
    }

    public static void registerTiers() { ToolMaterials.register(); }

    /** Called for creative/crafted outputs, not on ordinary inventory ticks or NBT loading. */
    public static ItemStack initializeStack(ItemStack stack) {
        if (stack.isEmpty() || stack.hasTag() && stack.getTag().contains("infench", Tag.TAG_LIST)) return stack;
        if (stack.getItem() instanceof ElementalAxeItem) {
            add(stack, EnumInfusionEnchantment.BURROWING, 1); add(stack, EnumInfusionEnchantment.COLLECTOR, 1);
        } else if (stack.getItem() instanceof ElementalPickaxeItem) {
            add(stack, EnumInfusionEnchantment.REFINING, 1); add(stack, EnumInfusionEnchantment.SOUNDING, 2);
        } else if (stack.getItem() instanceof ElementalSwordItem) add(stack, EnumInfusionEnchantment.ARCING, 2);
        else if (stack.getItem() instanceof ElementalShovelItem) add(stack, EnumInfusionEnchantment.DESTRUCTIVE, 1);
        else if (stack.getItem() instanceof PrimalCrusherItem) {
            add(stack, EnumInfusionEnchantment.DESTRUCTIVE, 1); add(stack, EnumInfusionEnchantment.REFINING, 1);
        }
        return stack;
    }

    private static void add(ItemStack stack, EnumInfusionEnchantment enchantment, int level) {
        EnumInfusionEnchantment.addInfusionEnchantment(stack, enchantment, level);
    }

    public static class Pick extends PickaxeItem implements WarpingGear {
        private final int warp;
        public Pick(Tier tier, int warp) { super(tier, 1, -2.8F, new Properties()); this.warp = warp; }
        @Override public int getWarp(ItemStack stack, LivingEntity wearer) { return warp; }
        @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (warp > 0) ToolSupport.selfRepair(stack, level, entity);
        }
        @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            if (warp > 0) ToolSupport.sap(player, entity, 80, false); return false;
        }
        @Override public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) { return ToolMining.start(stack, pos, player); }
    }

    public static class Shovel extends ShovelItem implements WarpingGear {
        private final int warp;
        public Shovel(Tier tier, int warp) { super(tier, 1.5F, -3F, new Properties()); this.warp = warp; }
        @Override public int getWarp(ItemStack stack, LivingEntity wearer) { return warp; }
        @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (warp > 0) ToolSupport.selfRepair(stack, level, entity);
        }
        @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            if (warp > 0) ToolSupport.sap(player, entity, 80, false); return false;
        }
        @Override public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) { return ToolMining.start(stack, pos, player); }
    }

    public static class Axe extends AxeItem implements WarpingGear {
        private final int warp;
        public Axe(Tier tier, int warp) { super(tier, 8 - tier.getAttackDamageBonus(), -3F, new Properties()); this.warp = warp; }
        @Override public int getWarp(ItemStack stack, LivingEntity wearer) { return warp; }
        @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (warp > 0) ToolSupport.selfRepair(stack, level, entity);
        }
        @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            if (warp > 0) ToolSupport.sap(player, entity, 80, false); return false;
        }
        @Override public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) { return ToolMining.start(stack, pos, player); }
    }

    public static class Sword extends SwordItem implements WarpingGear {
        private final int warp;
        public Sword(Tier tier, int warp) { super(tier, 3, -2.4F, new Properties().rarity(warp == 2 ? Rarity.EPIC : Rarity.COMMON)); this.warp = warp; }
        @Override public int getWarp(ItemStack stack, LivingEntity wearer) { return warp; }
        @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (warp > 0) ToolSupport.selfRepair(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (warp > 0) ToolSupport.sap(attacker, target, 60, warp == 2);
            return super.hurtEnemy(stack, target, attacker);
        }
    }

    public static class Hoe extends HoeItem implements WarpingGear {
        private final int warp;
        private final Multimap<Attribute, AttributeModifier> attributes;
        public Hoe(Tier tier, int warp) {
            super(tier, 0, tier.getAttackDamageBonus() - 3, new Properties()); this.warp = warp;
            // 1.12 ItemHoe has zero weapon damage and speed=material damage+1, not the modern tier-level formula.
            attributes = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                    .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 0, AttributeModifier.Operation.ADDITION))
                    .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", tier.getAttackDamageBonus() - 3, AttributeModifier.Operation.ADDITION)).build();
        }
        @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
            return slot == EquipmentSlot.MAINHAND ? attributes : super.getDefaultAttributeModifiers(slot);
        }
        @Override public int getWarp(ItemStack stack, LivingEntity wearer) { return warp; }
        @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (warp > 0) ToolSupport.selfRepair(stack, level, entity);
        }
        @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
            if (warp > 0) ToolSupport.sap(player, entity, 80, false); return false;
        }
    }
}
