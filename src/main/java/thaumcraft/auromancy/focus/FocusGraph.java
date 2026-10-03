package thaumcraft.auromancy.focus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Detached editor graph. Validation and payment decisions belong to FocusCompiler. */
public final class FocusGraph {
    public static final int MAX_NODES = 32, MAX_DEPTH = 32, MAX_COORDINATE = 4096;
    public record Node(int id, int parent, List<Integer> children, int x, int y, String key, Map<String, Integer> settings) {
        public Node {
            Objects.requireNonNull(key); children = List.copyOf(children);
            settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
            if (children.size() > MAX_NODES || settings.size() > 16 || key.length() > 64)
                throw new IllegalArgumentException("focus graph bounds");
            for (String setting : settings.keySet()) if (setting.isEmpty() || setting.length() > 32)
                throw new IllegalArgumentException("focus setting key bounds");
        }
        public Node(int id, int parent, int[] children, int x, int y, String key, Map<String, Integer> settings) {
            this(id, parent, Arrays.stream(children).boxed().toList(), x, y, key, settings);
        }
    }
    private final List<Node> nodes;
    public FocusGraph(List<Node> nodes) {
        if (nodes.size() > MAX_NODES) throw new IllegalArgumentException("focus node limit");
        this.nodes = List.copyOf(nodes);
    }
    public List<Node> nodes() { return nodes; }
    public static FocusGraph touchFire(int power, int duration) {
        return new FocusGraph(List.of(
                new Node(0, -1, List.of(1), 0, 0, FocusNodeRegistry.ROOT, Map.of()),
                new Node(1, 0, List.of(2), 0, 1, FocusNodeRegistry.TOUCH, Map.of()),
                new Node(2, 1, List.of(), 0, 2, FocusNodeRegistry.FIRE, Map.of("power", power, "duration", duration))));
    }
    /** ROOT already supplies the caster as its TARGET; a medium is optional in original TC6. */
    public static FocusGraph selfFire(int power, int duration) {
        return new FocusGraph(List.of(
                new Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),
                new Node(1,0,List.of(),0,1,FocusNodeRegistry.FIRE,Map.of("power",power,"duration",duration))));
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); ListTag list = new ListTag();
        Map<String, Integer> occurrences = new HashMap<>();
        for (Node node : nodes) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", node.id()); entry.putInt("parent", node.parent());
            entry.putIntArray("children", node.children().stream().mapToInt(Integer::intValue).toArray());
            entry.putInt("x", node.x()); entry.putInt("y", node.y()); entry.putString("key", node.key());
            var definition = FocusNodeRegistry.get(node.key());
            entry.putBoolean("target", definition != null && definition.supplied().contains(FocusNodeRegistry.Supply.TARGET));
            entry.putBoolean("trajectory", definition != null && definition.supplied().contains(FocusNodeRegistry.Supply.TRAJECTORY));
            int count = occurrences.merge(node.key(), 1, Integer::sum);
            entry.putFloat("complexity", .5F * (count + 1));
            node.settings().forEach((key, value) -> entry.putInt("setting." + key, value));
            list.add(entry);
        }
        tag.put("nodes", list); return tag;
    }
    /** Bounded parser; malformed tags throw, so a caller cannot mistake them for an empty graph. */
    public static FocusGraph read(CompoundTag tag) {
        if (!tag.contains("nodes", Tag.TAG_LIST)) throw new IllegalArgumentException("focus nodes tag");
        Tag raw = tag.get("nodes");
        if (!(raw instanceof ListTag list) || list.size() > MAX_NODES || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("focus nodes type or limit");
        List<Node> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            for (String field : List.of("id", "parent", "x", "y")) if (!entry.contains(field, Tag.TAG_INT))
                throw new IllegalArgumentException("focus integer " + field);
            if (!entry.contains("children", Tag.TAG_INT_ARRAY) || !entry.contains("key", Tag.TAG_STRING))
                throw new IllegalArgumentException("focus children or key type");
            int[] children = entry.getIntArray("children");
            if (children.length > MAX_NODES) throw new IllegalArgumentException("focus children limit");
            int x = entry.getInt("x"), y = entry.getInt("y");
            if (x < -MAX_COORDINATE || x > MAX_COORDINATE || y < -MAX_COORDINATE || y > MAX_COORDINATE)
                throw new IllegalArgumentException("focus coordinate limit");
            result.add(new Node(entry.getInt("id"), entry.getInt("parent"), children, x, y,
                    entry.getString("key"), readSettings(entry)));
        }
        return new FocusGraph(result);
    }
    static Map<String, Integer> readSettings(CompoundTag entry) {
        Map<String, Integer> settings = new LinkedHashMap<>();
        for (String key : entry.getAllKeys()) if (key.startsWith("setting.")) {
            if (!entry.contains(key, Tag.TAG_INT) || key.length() > 40)
                throw new IllegalArgumentException("focus setting type or key");
            settings.put(key.substring(8), entry.getInt(key));
        }
        if (settings.size() > 16) throw new IllegalArgumentException("focus setting limit");
        return settings;
    }
}
