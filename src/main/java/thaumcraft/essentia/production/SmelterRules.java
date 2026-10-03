package thaumcraft.essentia.production;

import net.minecraft.util.RandomSource;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/** Small deterministic BETA26 formulas shared by the actual device and its audit tests. */
public final class SmelterRules {
    public record LossResult(AspectList retained, int lost) {}
    private SmelterRules() {}
    public static float efficiency(int tier) { return tier == 1 ? .9F : tier == 2 ? .95F : .8F; }
    public static int interval(int tier, boolean alumentum) {
        int speed = tier == 1 ? 10 : 15;
        // javap confirms int((double)speed *0.8), not the decompiler's speed *= (int)0.8.
        return alumentum ? (int)(speed * .8D) : speed;
    }
    public static int cookTicks(int rawAspects, int bellows) { return Math.max(1, (int)(rawAspects * 2 * (1F - .125F * Math.max(0, Math.min(3, bellows))))); }
    public static LossResult rollLoss(AspectList input, int tier, RandomSource random) {
        AspectList retained = new AspectList(); int lost = 0;
        for (Aspect aspect : input.getAspects()) {
            int original = input.getAmount(aspect), left = original;
            float threshold = efficiency(tier) * (aspect == Aspect.FLUX ? .66F : 1F);
            for (int i = 0; i < original; i++) if (random.nextFloat() > threshold) { left--; lost++; }
            if (left > 0) retained.add(aspect, left);
        }
        return new LossResult(retained, lost);
    }
}
