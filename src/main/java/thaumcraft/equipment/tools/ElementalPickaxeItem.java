package thaumcraft.equipment.tools;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

/** BETA26 elemental pick: fire on left click; SOUNDING is the original infusion-enchantment action. */
public final class ElementalPickaxeItem extends ToolItems.Pick {
    public ElementalPickaxeItem() { super(ToolMaterials.ELEMENTAL, 0); }
    @Override public Rarity getRarity(ItemStack stack) { return Rarity.RARE; }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (!player.level().isClientSide && ToolSupport.canHarm(player, target)) target.setSecondsOnFire(2);
        return false;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        int rank = ToolSupport.enchantment(stack, EnumInfusionEnchantment.SOUNDING);
        if (player == null || !player.isShiftKeyDown() || rank == 0) return InteractionResult.PASS;
        if (!context.getLevel().mayInteract(player, context.getClickedPos())
                || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer serverPlayer) {
            ToolSupport.damage(stack, player, context.getHand(), 5);
            var sound = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "wandfail"));
            context.getLevel().playSound(null, context.getClickedPos(), sound == null ? SoundEvents.AMETHYST_BLOCK_CHIME : sound,
                    SoundSource.BLOCKS, .2F, .2F + player.getRandom().nextFloat() * .2F);
            ToolNetwork.sendSounding(serverPlayer, context.getClickedPos(), rank);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
