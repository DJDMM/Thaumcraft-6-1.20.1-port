package thaumcraft.equipment.armor;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.equipment.RechargeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server armor effects and the original traveller movement/jump/fall hooks. */
@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class ArmorEffects {
    private static final UUID SET_ARMOR = UUID.fromString("00642061-1bcd-4933-a035-618900006001");
    private static final UUID SET_TOUGHNESS = UUID.fromString("00642061-1bcd-4933-a035-618900006002");
    private static final Map<Player, Float> PREVIOUS_STEP = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private ArmorEffects() {}

    public static void enableTravellerStep(Player player) {
        PREVIOUS_STEP.putIfAbsent(player, player.maxUpStep());
        player.setMaxUpStep(1F);
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if ((player.isShiftKeyDown() || !TravellerBootsItem.wearing(player)) && PREVIOUS_STEP.containsKey(player))
            player.setMaxUpStep(PREVIOUS_STEP.remove(player));
        if (!player.level().isClientSide) updateFortressAttributes(player);
    }

    @SubscribeEvent public static void equipmentChanged(LivingEquipmentChangeEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof Player player) updateFortressAttributes(player);
    }

    public static void updateFortressAttributes(Player player) {
        var bonus = FortressArmorSupport.setBonus(player);
        modifier(player, Attributes.ARMOR, SET_ARMOR, bonus.armor(), "TC6 fortress armor");
        modifier(player, Attributes.ARMOR_TOUGHNESS, SET_TOUGHNESS, bonus.toughness(), "TC6 fortress toughness");
    }

    private static void modifier(Player player, Attribute attribute, UUID id, double amount, String name) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        var existing = instance.getModifier(id);
        if (existing != null && existing.getAmount() == amount) return;
        instance.removeModifier(id);
        if (amount > 0) instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && TravellerBootsItem.wearing(player)
                && RechargeSupport.getCharge(player.getItemBySlot(EquipmentSlot.FEET)) > 0)
            player.setDeltaMovement(player.getDeltaMovement().add(0, .2750000059604645, 0));
    }

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        float originalAmount = event.getAmount();
        if (event.getSource().getEntity() instanceof Player attacker && FortressArmorSupport.hasMask(attacker, 2)
                && attacker.getRandom().nextFloat() < originalAmount / 12F) attacker.heal(1F);
        if (event.getEntity() instanceof Player victim && event.getSource().getEntity() instanceof LivingEntity attacker
                && FortressArmorSupport.hasMask(victim, 1) && victim.getRandom().nextFloat() < originalAmount / 10F)
            attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));

        if (event.getEntity() instanceof Player player) {
            if (event.getSource().is(DamageTypes.FALL) && TravellerBootsItem.wearing(player)) {
                float amount = Math.max(0, event.getAmount() / 2F - 1F);
                event.setAmount(amount < 1F ? 0 : amount);
                if (amount < 1F) { event.setCanceled(true); return; }
            }
            updateFortressAttributes(player);
            applySpecialAbsorption(event, player);
        }
    }

    private record Piece(ItemStack stack, EquipmentSlot slot, FortressArmorSupport.Protection protection) {}

    private static void applySpecialAbsorption(LivingHurtEvent event, Player wearer) {
        // Pinned reference uses Forge 14.23.5.2860: ISpecialArmor's default handleUnblockableDamage is false.
        // Neither TC6 class overrides it, so the magic/on-fire branches cannot bypass vanilla's armor exemption.
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR) || event.getAmount() <= 0) return;
        List<Piece> pieces = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (FortressArmorSupport.special(stack))
                pieces.add(new Piece(stack, slot, FortressArmorSupport.protection(stack, event.getSource())));
        }
        double damage = event.getAmount();
        for (int priority = 1; priority >= 0; priority--) {
            double totalRatio = 0;
            for (Piece piece : pieces) if (piece.protection().priority() == priority) totalRatio += piece.protection().ratio();
            double divisor = Math.max(1, totalRatio), absorbedTotal = 0;
            for (Piece piece : pieces) {
                var protection = piece.protection();
                if (protection.priority() != priority) continue;
                double absorbed = Math.min(damage * protection.ratio() / divisor, Math.max(0, protection.absorbMax()));
                absorbedTotal += absorbed;
                if (absorbed > 0 && !event.getSource().is(DamageTypes.FALL))
                    piece.stack().hurtAndBreak((int)Math.max(1, absorbed), wearer, entity -> entity.broadcastBreakEvent(piece.slot()));
            }
            damage = Math.max(0, damage - absorbedTotal);
        }
        // Vanilla then performs its ordinary armor/toughness pass and durability pass on the residual damage.
        event.setAmount((float)damage);
    }
}
