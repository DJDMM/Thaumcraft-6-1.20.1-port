package thaumcraft.infusion;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.List;
/** Release effects shared by matrix harm, spell prices and actual rift hazards. */
public final class InfusionEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "thaumcraft");
    public static final RegistryObject<MobEffect> FLUX_TAINT = EFFECTS.register("flux_taint",thaumcraft.world.rift.FluxTaintEffect::new);
    public static final RegistryObject<MobEffect> VIS_EXHAUST = EFFECTS.register("vis_exhaust", () -> new MobEffect(MobEffectCategory.HARMFUL, 6702199) {
        @Override public List<ItemStack> getCurativeItems() { return List.of(); }
    });
    public static final RegistryObject<MobEffect> INFECTIOUS_VIS_EXHAUST = EFFECTS.register("infectious_vis_exhaust",
            thaumcraft.world.rift.InfectiousVisExhaustEffect::new);
    private InfusionEffects() {}
    public static void register(IEventBus bus) { EFFECTS.register(bus); }
}
