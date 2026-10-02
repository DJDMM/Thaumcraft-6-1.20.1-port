package thaumcraft.research.celestial;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CelestialModule {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final Map<CelestialVariant, RegistryObject<Item>> NOTES;
    static {
        Map<CelestialVariant, RegistryObject<Item>> notes = new EnumMap<>(CelestialVariant.class);
        for (CelestialVariant variant : CelestialVariant.values()) {
            notes.put(variant, ITEMS.register(variant.itemId(), () -> new CelestialNoteItem(variant)));
        }
        NOTES = Collections.unmodifiableMap(notes);
    }
    private CelestialModule() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    public static ItemStack note(int metadata) {
        CelestialVariant variant = CelestialVariant.byMetadata(metadata);
        return variant == null ? ItemStack.EMPTY : new ItemStack(NOTES.get(variant).get());
    }
    public static int metadata(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CelestialNoteItem note ? note.variant().metadata() : -1;
    }
}
