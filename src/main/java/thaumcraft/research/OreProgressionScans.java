package thaumcraft.research;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/** Pinned ConfigResearch ScanBlock families, including its constructor's matching ScanItem registrations. */
public final class OreProgressionScans {
    private static final Set<String> CRYSTALS = Set.of("crystal_aer", "crystal_ignis", "crystal_aqua",
            "crystal_terra", "crystal_ordo", "crystal_perditio", "crystal_vitium");
    private OreProgressionScans() {}

    /** Predicate only: HUD evaluation must never complete ORE or grant an addendum. */
    public static List<String> scanFacts(@Nullable Object specimen) {
        if (specimen instanceof ItemEntity entity) specimen = entity.getItem();
        ResourceLocation id;
        if (specimen instanceof BlockState state) id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        else if (specimen instanceof ItemStack stack && !stack.isEmpty()) id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        else return List.of();
        if (id == null || !id.getNamespace().equals("thaumcraft")) return List.of();
        String fact = switch (id.getPath()) {
            case "ore_amber" -> "!OREAMBER";
            case "ore_cinnabar" -> "!ORECINNABAR";
            default -> CRYSTALS.contains(id.getPath()) ? "!ORECRYSTAL" : null;
        };
        // ConfigResearch registers the canonical ORE ScanBlock before the three addendum families.
        // Ore quartz, loose crystal_essence and the old vis_crystal_* aliases never belong to it.
        return fact == null ? List.of() : List.of("ORE", fact);
    }
}
