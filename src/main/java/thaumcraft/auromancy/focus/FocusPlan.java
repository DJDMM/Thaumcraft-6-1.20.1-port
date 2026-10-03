package thaumcraft.auromancy.focus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.*;

/** Server-derived immutable values. No player/world reference and no shared ItemStack or NBT. */
public final class FocusPlan {
    private final FocusGraph graph;
    private final int complexity, maxComplexity, color, firePower, fireDuration;
    private final Map<String, Integer> crystals;
    FocusPlan(FocusGraph graph, int complexity, int maxComplexity, Map<String, Integer> crystals, int color) {
        this.graph = graph; this.complexity = complexity; this.maxComplexity = maxComplexity;
        this.crystals = Collections.unmodifiableMap(new LinkedHashMap<>(crystals)); this.color = color;
        FocusGraph.Node fire = graph.nodes().stream().filter(node -> node.key().equals(FocusNodeRegistry.FIRE)).findFirst().orElseThrow();
        firePower = fire.settings().get("power"); fireDuration = fire.settings().get("duration");
    }
    public FocusGraph graph() { return graph; }
    public int complexity() { return complexity; }
    public int maxComplexity() { return maxComplexity; }
    public float craftVis() { return complexity * 10 + maxComplexity / 5; }
    public int xpLevels() { return (int) Math.max(1L, Math.round(Math.sqrt(complexity))); }
    public Map<String, Integer> crystals() { return crystals; }
    public float castVis() { return complexity / 5F; }
    public int cooldownTicks() { return Math.max(5, (complexity / 5) * (complexity / 4)); }
    /** java.awt.Color.getRGB in the original also sets the opaque alpha byte. */
    public int color() { return color; }
    public int firePower() { return firePower; }
    public int fireDuration() { return fireDuration; }
    /** Original package fields, fresh every call; execution identity is created by the actual cast. */
    public CompoundTag packageNbt() {
        CompoundTag result = new CompoundTag();
        result.putInt("index", 0); result.putFloat("power", 1F); result.putInt("complexity", complexity);
        ListTag nodes = new ListTag();
        for (FocusGraph.Node node : graph.nodes()) {
            CompoundTag tag = new CompoundTag(); var definition = FocusNodeRegistry.get(node.key());
            tag.putString("type", definition.type().name()); tag.putString("key", node.key());
            node.settings().forEach((key, value) -> tag.putInt("setting." + key, value));
            nodes.add(tag);
        }
        result.put("nodes", nodes); return result;
    }
    public int sortingHash() {
        StringBuilder result = new StringBuilder();
        for (FocusGraph.Node node : graph.nodes()) {
            result.append(node.key());
            node.settings().forEach((key, value) -> result.append(value));
        }
        return result.toString().hashCode();
    }
}
