package thaumcraft.auromancy.focus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.*;

/** Server-derived immutable values. No player/world reference and no shared ItemStack or NBT. */
public final class FocusPlan {
    private final FocusGraph graph;
    private UUID executionId=UUID.randomUUID();
    private final int complexity, maxComplexity, color, firePower, fireDuration;
    private final Map<String, Integer> crystals;
    FocusPlan(FocusGraph graph, int complexity, int maxComplexity, Map<String, Integer> crystals, int color) {
        this.graph = graph; this.complexity = complexity; this.maxComplexity = maxComplexity;
        this.crystals = Collections.unmodifiableMap(new LinkedHashMap<>(crystals)); this.color = color;
        var effect=effect();
        firePower=effect.key().equals(FocusNodeRegistry.FIRE)?effect.settings().get("power"):0;
        fireDuration=effect.key().equals(FocusNodeRegistry.FIRE)?effect.settings().get("duration"):0;
    }
    public FocusGraph graph() { return graph; }
    public UUID executionId(){return executionId;}
    public FocusPlan withExecutionId(UUID id){var copy=new FocusPlan(graph,complexity,maxComplexity,crystals,color);copy.executionId=Objects.requireNonNull(id);return copy;}
    public int indexOf(int id){for(int i=0;i<graph.nodes().size();i++)if(graph.nodes().get(i).id()==id)return i;return -1;}
    public FocusGraph.Node node(int id){int i=indexOf(id);return i<0?null:graph.nodes().get(i);}
    public List<FocusGraph.Node> effects(){return graph.nodes().stream().filter(n->FocusNodeRegistry.get(n.key()).type()==FocusNodeRegistry.Type.EFFECT).toList();}
    public FocusGraph.Node effect(){return effects().get(0);}
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
        result.put("nodes", chain(0));return result;
    }
    private ListTag chain(int first){
        ListTag nodes=new ListTag();int id=first;
        for(int count=0;count<FocusGraph.MAX_NODES;count++){
            var node=node(id);var definition=FocusNodeRegistry.get(node.key());var tag=new CompoundTag();
            tag.putString("type",definition.type().name());tag.putString("key",node.key());
            node.settings().forEach((key,value)->tag.putInt("setting."+key,value));
            if(FocusNodeRegistry.isSplit(node.key())){
                var branches=new ListTag();
                for(int child:node.children()){
                    var branch=new CompoundTag();branch.putInt("index",0);branch.putFloat("power",1);branch.putInt("complexity",0);
                    branch.put("nodes",chain(child));branches.add(branch);
                }
                var wrapper=new CompoundTag();wrapper.put("packages",branches);tag.put("packages",wrapper);
            }
            nodes.add(tag);if(node.children().size()!=1)break;id=node.children().get(0);
        }
        return nodes;
    }
    public int sortingHash() {
        StringBuilder result = new StringBuilder();
        int id=0;
        for (int count=0;count<FocusGraph.MAX_NODES;count++) {
            FocusGraph.Node node=node(id);result.append(node.key());
            node.settings().forEach((key, value) -> result.append(value));
            if(node.children().size()!=1)break;id=node.children().get(0);
        }
        return result.toString().hashCode();
    }
}
