package thaumcraft.golemancy.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Existing pinned sound assets; scan is already owned by ScanningModule. */
public final class GolemEntitySounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<SoundEvent> CLACK = sound("clack");
    // EssentiaTransportModule owns the original shared tool sound.
    public static final RegistryObject<SoundEvent> TOOL = RegistryObject.create(ResourceLocation.fromNamespaceAndPath("thaumcraft", "tool"), ForgeRegistries.SOUND_EVENTS);
    // EquipmentModule already owns this shared ID.
    public static final RegistryObject<SoundEvent> ZAP = RegistryObject.create(ResourceLocation.fromNamespaceAndPath("thaumcraft", "zap"), ForgeRegistries.SOUND_EVENTS);
    public static final RegistryObject<SoundEvent> SHOCK = sound("shock");
    private static RegistryObject<SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", name))); }
    private GolemEntitySounds() {}
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
