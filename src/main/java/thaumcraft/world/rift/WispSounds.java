package thaumcraft.world.rift;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Original BETA26 media, registered once alongside the operational Wisp. */
public final class WispSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"thaumcraft");
    static {
        for (String name : java.util.List.of("wisplive","wispdead"))
            SOUNDS.register(name,() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft",name)));
    }
    private WispSounds() {}
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
