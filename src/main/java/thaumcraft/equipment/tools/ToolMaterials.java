package thaumcraft.equipment.tools;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Original ThaumcraftMaterials, ItemCrimsonBlade and ItemPrimalCrusher tool values. */
public final class ToolMaterials {
    public static final Tier THAUMIUM = tier("thaumium", 3, 500, 7, 2.5F, 22, "ingot_thaumium");
    public static final Tier VOID = tier("void", 4, 150, 8, 3, 10, "ingot_void");
    public static final Tier ELEMENTAL = tier("elemental", 3, 1500, 9, 3, 18, "ingot_thaumium");
    public static final Tier CRIMSON = tier("crimson", 4, 200, 8, 3.5F, 20, "ingot_void");
    public static final Tier PRIMAL = tier("primal", 5, 500, 8, 4, 20, "ingot_void");
    private static boolean registered;

    private ToolMaterials() {}

    private static Tier tier(String name, int level, int uses, float speed, float attack, int enchantment, String repair) {
        // A tier tag denotes blocks requiring that specific tier. Reusing NEEDS_DIAMOND_TOOL on a higher
        // custom tier makes every earlier tool incorrectly fail obsidian in Forge's sorted-tier scan.
        return new ForgeTier(level, uses, speed, attack, enchantment, net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK, id("needs_" + name + "_tool")),
                () -> Ingredient.of(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", repair))));
    }

    public static void register() {
        if (registered) return;
        registered = true;
        TierSortingRegistry.registerTier(THAUMIUM, id("thaumium"), List.of(Tiers.DIAMOND), List.of(Tiers.NETHERITE));
        TierSortingRegistry.registerTier(ELEMENTAL, id("elemental"), List.of(Tiers.DIAMOND), List.of(Tiers.NETHERITE));
        TierSortingRegistry.registerTier(VOID, id("void"), List.of(Tiers.NETHERITE), List.of());
        TierSortingRegistry.registerTier(CRIMSON, id("crimson_void"), List.of(Tiers.NETHERITE), List.of());
        TierSortingRegistry.registerTier(PRIMAL, id("primal_void"), List.of(VOID, CRIMSON), List.of());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
}
