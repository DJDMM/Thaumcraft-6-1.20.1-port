package thaumcraft.world.rift;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import thaumcraft.infusion.InfusionEffects;

/** BETA26 Flux Phage: a forty-duration-tick, four-block cubic infection cascade. */
public final class InfectiousVisExhaustEffect extends MobEffect {
    public InfectiousVisExhaustEffect() { super(MobEffectCategory.HARMFUL, 6706551); }
    @Override public boolean isDurationEffectTick(int duration, int amplifier) { return duration % 40 == 0; }
    @Override public String getDescriptionId() { return "potion.infvisexhaust"; }
    @Override public void applyEffectTick(LivingEntity carrier, int amplifier) {
        if (carrier.level().isClientSide || !carrier.isAlive() || carrier.isRemoved()
                || carrier.getServer() == null || !carrier.getServer().isSameThread()) return;
        for (LivingEntity nearby : carrier.level().getEntitiesOfClass(LivingEntity.class,
                carrier.getBoundingBox().inflate(4), e -> e.isAlive() && !e.isRemoved())) {
            if (nearby.hasEffect(this)) continue;
            // Original descendants use ordinary potion curatives; only the initiating rift
            // instance explicitly clears its curative list.
            nearby.addEffect(amplifier > 0 ? new MobEffectInstance(this, 6000, amplifier - 1, false, true)
                    : new MobEffectInstance(InfusionEffects.VIS_EXHAUST.get(), 6000, 0, false, true));
        }
    }
}
