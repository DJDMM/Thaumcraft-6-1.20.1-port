package thaumcraft.equipment.cleansing;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** TC6 Ward has no tick action or attributes; the warp-event caller checks its presence. */
public final class WarpWardEffect extends MobEffect {
    public WarpWardEffect() { super(MobEffectCategory.BENEFICIAL, 14742263); }

    @Override public boolean isDurationEffectTick(int duration, int amplifier) { return false; }
}
