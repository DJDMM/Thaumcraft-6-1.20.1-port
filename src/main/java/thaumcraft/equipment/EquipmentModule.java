package thaumcraft.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.List;

/** The plain thaumium equipment retains TC6 durability, mining and armor values. */
public final class EquipmentModule {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final Tier THAUMIUM = thaumcraft.equipment.tools.ToolMaterials.THAUMIUM;
    public static final ArmorMaterial ARMOR = thaumcraft.equipment.armor.Tc6ArmorMaterials.THAUMIUM;
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"thaumcraft");
    static {
        for(String id:new String[]{"thaumium_pick","thaumium_shovel","thaumium_axe","thaumium_sword","thaumium_hoe"})
            ITEMS.register(id, () -> thaumcraft.equipment.tools.ToolItems.thaumium(id));
        for(String sound:new String[]{"learn","wind","zap","coins","craftstart","wandfail"})
            SOUNDS.register(sound, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft",sound)));
        armor("thaumium_helm", ArmorItem.Type.HELMET);
        armor("thaumium_chest", ArmorItem.Type.CHESTPLATE);
        armor("thaumium_legs", ArmorItem.Type.LEGGINGS);
        armor("thaumium_boots", ArmorItem.Type.BOOTS);
    }
    private static void armor(String id, ArmorItem.Type type) {
        ITEMS.register(id, () -> thaumcraft.catalog.armor.CatalogArmorItem.thaumium(type));
    }
    public static void register(IEventBus bus) {
        thaumcraft.equipment.tools.ToolItems.registerTiers();
        ITEMS.register(bus);
        SOUNDS.register(bus);
    }
}
