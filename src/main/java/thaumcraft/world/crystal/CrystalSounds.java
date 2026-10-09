package thaumcraft.world.crystal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Original SoundsTC.CRYSTAL: volume .5, pitch1 and the same crystal sample on every action. */
public final class CrystalSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<SoundEvent> CRYSTAL = SOUNDS.register("crystal", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "crystal")));
    public static final ForgeSoundType TYPE = new ForgeSoundType(.5F, 1, CRYSTAL, CRYSTAL, CRYSTAL, CRYSTAL, CRYSTAL);
    private CrystalSounds() {}
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
