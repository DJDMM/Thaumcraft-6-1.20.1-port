package thaumcraft.world.rift;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Original BETA26 ambient rift sound; audio is copied byte for byte from the pinned JAR. */
public final class RiftSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<SoundEvent> EVIL_PORTAL = SOUNDS.register("evilportal", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "evilportal")));
    private RiftSounds() {}
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
