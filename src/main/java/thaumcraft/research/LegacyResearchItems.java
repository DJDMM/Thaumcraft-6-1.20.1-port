package thaumcraft.research;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Explicit BETA26 item metadata mapping, shared by stage requirements and their display. */
public final class LegacyResearchItems {
    private static final Map<String, ResourceLocation> ITEMS = load();

    private LegacyResearchItems() {}

    public static ResourceLocation resolve(String legacyId, int metadata) {
        ResourceLocation id = ResourceLocation.tryParse(legacyId);
        if (id == null || metadata < 0) throw new IllegalArgumentException("Invalid legacy item " + legacyId);
        // BETA26's dye defaults to metadata0 (ink sac), and web is the old cobweb ID.
        // Keep canonical craft proofs and book rows aligned with the physical modern output.
        if (metadata == 0 && id.toString().equals("minecraft:dye")) return ResourceLocation.fromNamespaceAndPath("minecraft", "ink_sac");
        if (metadata == 0 && id.toString().equals("minecraft:web")) return ResourceLocation.fromNamespaceAndPath("minecraft", "cobweb");
        // 1.12 map is the filled map; its empty-map ID was empty_map.
        if (metadata == 0 && id.toString().equals("minecraft:map")) return ResourceLocation.fromNamespaceAndPath("minecraft", "filled_map");
        if (metadata == 0 && id.toString().equals("minecraft:empty_map")) return ResourceLocation.fromNamespaceAndPath("minecraft", "map");
        // The original yellow nitor is the already playable 0.2 nitor block/item.
        if (id.toString().equals("thaumcraft:nitor")) {
            if (metadata == 4) return id;
            throw new IllegalArgumentException("Unimplemented nitor variant " + metadata);
        }
        ResourceLocation mapped = ITEMS.get(id + ";" + metadata);
        if (mapped != null) return mapped;
        if (metadata == 0) return id;
        throw new IllegalArgumentException("Unmapped legacy item " + id + ";" + metadata);
    }

    /** Parse id;count;metadata. Count is handled by obtain requirements, not by item identity. */
    public static ResourceLocation resolve(String raw) {
        String[] fields = raw.split(";", 4);
        int metadata = fields.length > 2 ? Integer.parseInt(fields[2]) : 0;
        // Plain nitor in the modern project already identifies its single implemented color.
        if (fields.length < 3 && fields[0].equals("thaumcraft:nitor")) metadata = 4;
        return resolve(fields[0], metadata);
    }

    private static Map<String, ResourceLocation> load() {
        String path = "/assets/thaumcraft/catalog/items.json";
        try (var stream = LegacyResearchItems.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing pinned item metadata manifest");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, ResourceLocation> result = new HashMap<>();
            for (var element : json.getAsJsonArray("items")) {
                var row = element.getAsJsonObject();
                String old = "thaumcraft:" + row.get("legacy_item").getAsString() + ";" + row.get("legacy_metadata").getAsInt();
                ResourceLocation modern = ResourceLocation.fromNamespaceAndPath("thaumcraft", row.get("id").getAsString());
                ResourceLocation duplicate = result.putIfAbsent(old, modern);
                if (duplicate != null && !duplicate.equals(modern)) throw new IllegalStateException("Ambiguous legacy item " + old);
            }
            return Map.copyOf(result);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid pinned item metadata manifest", failure);
        }
    }
}
