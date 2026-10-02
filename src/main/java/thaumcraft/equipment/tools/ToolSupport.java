package thaumcraft.equipment.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import net.minecraftforge.registries.ForgeRegistries;

final class ToolSupport {
    private ToolSupport() {}

    static int enchantment(ItemStack stack, EnumInfusionEnchantment enchantment) {
        return Math.max(0, Math.min(enchantment.maxLevel, EnumInfusionEnchantment.getInfusionEnchantmentLevel(stack, enchantment)));
    }

    static void selfRepair(ItemStack stack, Level level, Entity wearer) {
        if (!level.isClientSide && wearer instanceof LivingEntity && wearer.tickCount % 20 == 0 && stack.isDamaged())
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
    }

    static boolean canHarm(LivingEntity attacker, Entity target) {
        if (target instanceof Player && attacker.getServer() != null && !attacker.getServer().isPvpAllowed()) return false;
        return !(attacker instanceof Player player && target instanceof Player other) || player.canHarmPlayer(other);
    }

    static void sap(LivingEntity attacker, Entity target, int duration, boolean hunger) {
        if (attacker.level().isClientSide || !(target instanceof LivingEntity living) || !canHarm(attacker, target)) return;
        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration));
        if (hunger) living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120));
    }

    static void damage(ItemStack stack, LivingEntity wearer, InteractionHand hand, int amount) {
        if (!wearer.level().isClientSide) stack.hurtAndBreak(amount, wearer, entity -> entity.broadcastBreakEvent(hand));
    }

    static void particles(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.POOF, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                4, .15, .15, .15, .015);
    }

    static void wind(LivingEntity entity, float volume) {
        SoundEvent wind = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "wind"));
        entity.level().playSound(null, entity.blockPosition(), wind == null ? SoundEvents.ELYTRA_FLYING : wind,
                SoundSource.PLAYERS, volume, .9F + entity.getRandom().nextFloat() * .2F);
    }
}
