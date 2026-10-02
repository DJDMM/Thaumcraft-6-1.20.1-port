package thaumcraft.equipment.tools;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.TierSortingRegistry;

/** Original primal tier and both pickaxe/shovel capabilities, not a reskinned diamond pickaxe. */
public final class PrimalCrusherItem extends ToolItems.Pick {
    public PrimalCrusherItem() { super(ToolMaterials.PRIMAL, 2); }
    @Override public Rarity getRarity(ItemStack stack) { return Rarity.COMMON; }
    @Override public boolean onLeftClickEntity(ItemStack stack, net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) { return false; }
    @Override public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || ToolMining.isTaint(state) ? ToolMaterials.PRIMAL.getSpeed() : 1F;
    }
    @Override public boolean isCorrectToolForDrops(BlockState state) {
        return (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || ToolMining.isTaint(state))
                && TierSortingRegistry.isCorrectTierForDrops(ToolMaterials.PRIMAL, state);
    }
    @Override public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action) || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action);
    }
    @Override public float getAttackDamage() { return 7.5F; }
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot) {
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        return com.google.common.collect.ImmutableMultimap.<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>builder()
                .put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Tool modifier", 7.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION))
                .put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_SPEED_UUID, "Tool modifier", -2.8, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION)).build();
    }
}
