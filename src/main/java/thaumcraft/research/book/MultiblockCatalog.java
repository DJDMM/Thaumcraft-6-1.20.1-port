package thaumcraft.research.book;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The six book-only BluePrint registrations in the official TC6 BETA26 binary. */
public final class MultiblockCatalog {
    public record BlockSpec(ResourceLocation block, Map<String, String> properties) {
        public BlockSpec { properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties)); }
        public BlockState state() {
            if (!BuiltInRegistries.BLOCK.containsKey(block)) throw new IllegalStateException("Missing blueprint block " + block);
            BlockState state = BuiltInRegistries.BLOCK.get(block).defaultBlockState();
            for (var entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null) throw new IllegalStateException("Missing blueprint property " + block + ":" + entry.getKey());
                state = apply(state, property, entry.getValue());
            }
            return state;
        }
        private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
            return state.setValue(property, property.getValue(value)
                    .orElseThrow(() -> new IllegalStateException("Invalid blueprint property " + property.getName() + "=" + value)));
        }
    }

    /** Position uses modern bottom-up Y; a null result retains its source, as in BlueprintBlockAccess. */
    public record Part(BlockPos pos, BlockSpec source, BlockSpec result) {
        public Part { pos = pos.immutable(); Objects.requireNonNull(source); }
    }
    public record Material(ResourceLocation item, int count) {
        public Material { if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid blueprint material count"); }
        public ItemStack stack() {
            if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalStateException("Missing blueprint material " + item);
            return new ItemStack(BuiltInRegistries.ITEM.get(item), count);
        }
    }
    public record Blueprint(ResourceLocation id, String research, String title, int width, int height, int depth,
                            List<Part> parts, List<Material> materials, ResourceLocation display) {
        public Blueprint {
            if (width < 1 || height < 1 || depth < 1 || width > 16 || height > 16 || depth > 16)
                throw new IllegalArgumentException("Invalid blueprint dimensions");
            parts = List.copyOf(parts); materials = List.copyOf(materials);
        }
        /** This is a construction reference; opening it never enables an unported DustTrigger. */
        public boolean mechanicsImplemented() { return false; }
        public MultiblockPreview preview(int removedTopLayers) { return new MultiblockPreview(this, false, removedTopLayers); }
        /** BETA26 bookmark target uses three clockwise horizontal rotations, without rotating stored facings. */
        public MultiblockPreview targetPreview() { return new MultiblockPreview(this, true, 0); }
        public List<ItemStack> materialStacks() { return materials.stream().map(Material::stack).toList(); }
        public Optional<ItemStack> displayStack() {
            return display == null ? Optional.empty() : Optional.of(new Material(display, 1).stack());
        }
    }

    private static final class Loaded { private static final List<Blueprint> VALUES = load(); }
    private MultiblockCatalog() { }
    public static List<Blueprint> all() { return Loaded.VALUES; }
    /** Original book links include uppercase Thaumatorium/GolemPress paths. Modern IDs normalize them. */
    public static Optional<Blueprint> resolve(String original) {
        String normalized = original.contains(":") ? original.toLowerCase(Locale.ROOT) : "thaumcraft:" + original.toLowerCase(Locale.ROOT);
        return all().stream().filter(blueprint -> blueprint.id().toString().equals(normalized)).findFirst();
    }

    private static List<Blueprint> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MultiblockCatalog.class.getResourceAsStream(
                "/assets/thaumcraft/research/multiblocks.json")), StandardCharsets.UTF_8)) {
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            if (!document.get("layer_order").getAsString().equals("top_to_bottom")
                    || document.get("target_horizontal_rotation").getAsInt() != 3)
                throw new IllegalStateException("Unexpected BETA26 blueprint coordinate convention");
            List<Blueprint> result = new ArrayList<>();
            for (var element : document.getAsJsonArray("blueprints")) {
                JsonObject row = element.getAsJsonObject();
                var layers = row.getAsJsonArray("layers");
                int height = layers.size(), width = layers.get(0).getAsJsonArray().size();
                int depth = layers.get(0).getAsJsonArray().get(0).getAsJsonArray().size();
                List<Part> parts = new ArrayList<>();
                for (int y = 0; y < height; y++) {
                    var plane = layers.get(y).getAsJsonArray();
                    if (plane.size() != width) throw new IllegalStateException("Ragged blueprint X dimension");
                    for (int x = 0; x < width; x++) {
                        var line = plane.get(x).getAsJsonArray();
                        if (line.size() != depth) throw new IllegalStateException("Ragged blueprint Z dimension");
                        for (int z = 0; z < depth; z++) if (!line.get(z).isJsonNull()) {
                            JsonObject part = line.get(z).getAsJsonObject();
                            parts.add(new Part(new BlockPos(x, height - y - 1, z), blockSpec(part.getAsJsonObject("source")),
                                    part.has("result") ? blockSpec(part.getAsJsonObject("result")) : null));
                        }
                    }
                }
                List<Material> materials = new ArrayList<>();
                row.getAsJsonArray("materials").forEach(value -> materials.add(new Material(
                        ResourceLocation.parse(value.getAsJsonObject().get("item").getAsString()),
                        value.getAsJsonObject().get("count").getAsInt())));
                Blueprint blueprint = new Blueprint(ResourceLocation.parse(row.get("id").getAsString()),
                        row.get("research").getAsString(), row.get("title").getAsString(), width, height, depth,
                        parts, materials, row.has("display") ? ResourceLocation.parse(row.get("display").getAsString()) : null);
                if (result.stream().anyMatch(existing -> existing.id().equals(blueprint.id())))
                    throw new IllegalStateException("Duplicate blueprint " + blueprint.id());
                result.add(blueprint);
            }
            return List.copyOf(result);
        } catch (Exception exception) { throw new IllegalStateException("Invalid BETA26 book blueprints", exception); }
    }
    private static BlockSpec blockSpec(JsonObject row) {
        Map<String, String> properties = new LinkedHashMap<>();
        row.getAsJsonObject("properties").entrySet().forEach(entry -> properties.put(entry.getKey(), entry.getValue().getAsString()));
        return new BlockSpec(ResourceLocation.parse(row.get("block").getAsString()), properties);
    }
}
