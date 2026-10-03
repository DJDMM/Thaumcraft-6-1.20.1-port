package thaumcraft.infusion;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.List;
/** The two release effects used by matrix harm; champion/tainted entity special cases await their entity port. */
public final class InfusionEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "thaumcraft");
    public static final RegistryObject<MobEffect> FLUX_TAINT = EFFECTS.register("flux_taint", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x800080) {
        @Override public boolean isDurationEffectTick(int duration, int amplifier) { int interval = 40 >> Math.min(30, amplifier); return interval <= 0 || duration % interval == 0; }
        @Override public void applyEffectTick(LivingEntity target, int amplifier) { if (!target.isInvertedHealAndHarm()) target.hurt(target.damageSources().magic(), 1); }
    });
    public static final RegistryObject<MobEffect> VIS_EXHAUST = EFFECTS.register("vis_exhaust", () -> new MobEffect(MobEffectCategory.HARMFUL, 6702199) {
        @Override public List<ItemStack> getCurativeItems() { return List.of(); }
    });
    private InfusionEffects() {}
    public static void register(IEventBus bus) { EFFECTS.register(bus); }
}
