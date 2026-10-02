package thaumcraft.equipment.items;

import net.minecraft.world.item.Item;
import thaumcraft.catalog.CatalogModule;

public final class ItemMechanics {
    private ItemMechanics() {}
    public static Item create(CatalogModule.Spec spec) {
        Item cleansing=thaumcraft.equipment.cleansing.CleansingModule.create(spec);
        if(cleansing!=null) return cleansing;
        if(spec.sourceClass().equals("ItemChunksEdible") || spec.sourceClass().equals("ItemTripleMeatTreat") || spec.sourceClass().equals("ItemZombieBrain")) return new CatalogFoodItem(spec);
        if(spec.id().equals("primordial_pearl")) return new PrimordialPearlItem();
        if(spec.sourceClass().equals("ItemCurio")) return new CurioItem(spec);
        if(spec.sourceClass().equals("ItemLootBag")) return new LootBagItem(spec);
        if(spec.id().equals("sanity_checker")) return new SanityCheckerItem();
        return null;
    }
}
