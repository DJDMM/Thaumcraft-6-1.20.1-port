package thaumcraft.crafting.ingredients;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Forge's replacement for the removed HarvestDropsEvent, without a shared bootstrap dependency. */
@Mod.EventBusSubscriber(modid="thaumcraft", bus=Mod.EventBusSubscriber.Bus.MOD)
public final class RareEarthLootRegistration {
    private RareEarthLootRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                ResourceLocation.fromNamespaceAndPath("thaumcraft","rare_earth_ore"),
                () -> RareEarthOreLootModifier.CODEC);
    }
}
