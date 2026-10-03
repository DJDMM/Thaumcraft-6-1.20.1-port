package thaumcraft.auromancy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
public final class AuromancySounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"thaumcraft");
    public static final RegistryObject<SoundEvent> TICKS=SOUNDS.register("ticks",()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft","ticks")));
    private AuromancySounds(){}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
}
