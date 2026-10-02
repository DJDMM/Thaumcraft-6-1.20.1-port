package thaumcraft.equipment.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/** ThaumcraftMaterials BETA26; arrays use the original feet/legs/chest/head order. */
public enum Tc6ArmorMaterials implements ArmorMaterial {
    THAUMIUM(25, new int[]{2, 5, 6, 2}, 25, SoundEvents.ARMOR_EQUIP_IRON, 1, "ingot_thaumium"),
    SPECIAL(25, new int[]{1, 2, 3, 1}, 25, SoundEvents.ARMOR_EQUIP_LEATHER, 1, null),
    VOID(10, new int[]{3, 6, 8, 3}, 10, SoundEvents.ARMOR_EQUIP_CHAIN, 1, "ingot_void"),
    VOID_ROBE(18, new int[]{4, 7, 9, 4}, 10, SoundEvents.ARMOR_EQUIP_LEATHER, 2, "ingot_void"),
    FORTRESS(40, new int[]{3, 6, 7, 3}, 25, SoundEvents.ARMOR_EQUIP_IRON, 3, "ingot_thaumium"),
    CULTIST_PLATE(18, new int[]{2, 5, 6, 2}, 13, SoundEvents.ARMOR_EQUIP_IRON, 0, "minecraft:iron_ingot"),
    CULTIST_ROBE(17, new int[]{2, 4, 5, 2}, 13, SoundEvents.ARMOR_EQUIP_CHAIN, 0, "minecraft:iron_ingot"),
    CULTIST_LEADER(30, new int[]{3, 6, 7, 3}, 20, SoundEvents.ARMOR_EQUIP_IRON, 1, "minecraft:iron_ingot");

    private final int durability, enchantability;
    private final int[] defense;
    private final SoundEvent equipSound;
    private final float toughness;
    private final String repair;

    Tc6ArmorMaterials(int durability, int[] defense, int enchantability, SoundEvent sound, float toughness, String repair) {
        this.durability = durability;
        this.defense = defense;
        this.enchantability = enchantability;
        this.equipSound = sound;
        this.toughness = toughness;
        this.repair = repair;
    }

    @Override public int getDurabilityForType(ArmorItem.Type type) {
        return durability * switch (type) { case BOOTS -> 13; case LEGGINGS -> 15; case CHESTPLATE -> 16; case HELMET -> 11; };
    }
    @Override public int getDefenseForType(ArmorItem.Type type) {
        return defense[switch (type) { case BOOTS -> 0; case LEGGINGS -> 1; case CHESTPLATE -> 2; case HELMET -> 3; }];
    }
    @Override public int getEnchantmentValue() { return enchantability; }
    @Override public SoundEvent getEquipSound() { return equipSound; }
    @Override public Ingredient getRepairIngredient() { return ingredient(repair); }
    @Override public String getName() { return "thaumcraft:" + name().toLowerCase(java.util.Locale.ROOT); }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return 0; }

    /** Resolve lazily: armor can be constructed before the repair item has been registered. */
    public static Ingredient ingredient(String name) {
        if (name == null) return Ingredient.EMPTY;
        var id = ResourceLocation.parse(name.contains(":") ? name : "thaumcraft:" + name);
        var item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? Ingredient.EMPTY : Ingredient.of(item);
    }
}
