package thaumcraft.auromancy.focus;

import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/** Authoritative graph compiler. It performs no resource or stack mutation. */
public final class FocusCompiler {
    private FocusCompiler() {}
    /** success implies non-null plan and empty error. Failure has null plan and a stable error key. */
    public record Result(boolean success, FocusPlan plan, String error) {
        private static Result failure(String error) { return new Result(false, null, error); }
    }
    /**
     * The predicate is BETA26 knowsResearchStrict: bare keys COMPLETE, @N known at stage N.
     * Compiling a saved/paid gifted focus may pass research -> true instead.
     */
    public static Result compile(FocusGraph graph, ItemStack focus, Predicate<String> knowsStrict) {
        if (graph == null || knowsStrict == null) return Result.failure("missing_graph");
        int capacity = FocusStacks.maxComplexity(focus);
        if (capacity == 0 || focus.getCount() != 1) return Result.failure("invalid_focus");
        if (graph.nodes().isEmpty() || graph.nodes().size() > FocusGraph.MAX_NODES) return Result.failure("node_limit");
        Map<Integer, FocusGraph.Node> byId = new LinkedHashMap<>();
        Map<Integer, FocusNodeRegistry.Definition> definitions = new HashMap<>();
        Map<Integer, Map<String, Integer>> resolved = new HashMap<>();
        for (FocusGraph.Node node : graph.nodes()) {
            if (node.id() < 0 || node.id() >= FocusGraph.MAX_NODES || node.parent() < -1 || node.parent() >= FocusGraph.MAX_NODES
                    || node.x() < -FocusGraph.MAX_COORDINATE || node.x() > FocusGraph.MAX_COORDINATE
                    || node.y() < -FocusGraph.MAX_COORDINATE || node.y() > FocusGraph.MAX_COORDINATE)
                return Result.failure("node_bounds");
            if (byId.put(node.id(), node) != null) return Result.failure("duplicate_id");
            var definition = FocusNodeRegistry.get(node.key());
            if (definition == null) return Result.failure("unknown_node");
            definitions.put(node.id(), definition);
            Map<String, Integer> settings = new LinkedHashMap<>(definition.defaultSettings());
            for (var setting : node.settings().entrySet()) {
                var declared = definition.settings().get(setting.getKey());
                if (declared == null || !declared.accepts(setting.getValue())) return Result.failure("invalid_setting");
                settings.put(setting.getKey(), setting.getValue());
            }
            resolved.put(node.id(), Collections.unmodifiableMap(settings));
        }
        var root = byId.get(0);
        if (root == null || !root.key().equals(FocusNodeRegistry.ROOT) || root.parent() != -1) return Result.failure("invalid_root");
        for (FocusGraph.Node node : graph.nodes()) {
            if (node.id() != 0 && (node.parent() == -1 || node.key().equals(FocusNodeRegistry.ROOT))) return Result.failure("multiple_roots");
            Set<Integer> children = new HashSet<>();
            for (int child : node.children()) {
                if (!children.add(child)) return Result.failure("duplicate_child");
                var target = byId.get(child);
                if (target == null || target.parent() != node.id()) return Result.failure("parent_children_mismatch");
            }
            if (node.id() != 0) {
                var parent = byId.get(node.parent());
                if (parent == null || !parent.children().contains(node.id())) return Result.failure("parent_children_mismatch");
            }
        }
        // Iterative traversal never recurses into an attacker-controlled graph.
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(0);
        Set<Integer> visited = new HashSet<>(); List<FocusGraph.Node> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (!visited.add(id)) return Result.failure("cycle");
            FocusGraph.Node node = byId.get(id); ordered.add(node);
            for (int child : node.children()) queue.addLast(child);
        }
        if (visited.size() != byId.size()) return Result.failure("unreachable_node");
        for (FocusGraph.Node node : ordered) {
            var definition = definitions.get(node.id());
            if (!definition.runtimeSupported()) return Result.failure("unsupported_node");
            if (node.id() != 0) {
                var parent = definitions.get(node.parent());
                if (!parent.supplied().containsAll(definition.requiredSupply())) return Result.failure("missing_supply");
                if (!knowsStrict.test(definition.research())) return Result.failure("missing_research");
            }
            for (var setting : definition.settings().values()) if (setting.research() != null
                    && resolved.get(node.id()).get(setting.key()) != setting.defaultValue()
                    && !knowsStrict.test(setting.research())) return Result.failure("missing_setting_research");
        }
        if (ordered.size()<2) return Result.failure("unsupported_shape");
        Map<String,Integer> exclusives=new HashMap<>(); int media=0; boolean exclusiveMedium=false;
        for(var node:ordered){
            var definition=definitions.get(node.id());
            int expected=definition.type()==FocusNodeRegistry.Type.EFFECT?0:FocusNodeRegistry.isSplit(node.key())?2:1;
            if(node.children().size()!=expected)return Result.failure("unsupported_shape");
            if(definition.exclusive()&&exclusives.merge(node.key(),1,Integer::sum)>1)return Result.failure("exclusive_node");
            if(node.id()!=0&&definition.type()==FocusNodeRegistry.Type.MEDIUM){media++;exclusiveMedium|=definition.exclusive();}
        }
        // Original editor: Plan excludes every other non-root medium, Scatter is unique across the focus.
        if(exclusiveMedium&&media>1)return Result.failure("exclusive_medium");
        Map<String, Integer> occurrences = new HashMap<>(), crystals = new LinkedHashMap<>();
        List<FocusGraph.Node> normalized = new ArrayList<>(); int complexity = 0;
        for (FocusGraph.Node node : ordered) {
            var definition = definitions.get(node.id()); int occurrence = occurrences.merge(node.key(), 1, Integer::sum);
            complexity += (int) (definition.complexity(resolved.get(node.id())) * (.5F * (occurrence + 1)));
            if (definition.aspect() != null) crystals.merge(definition.aspect(), 1, Integer::sum);
            normalized.add(new FocusGraph.Node(node.id(), node.parent(), node.children(), node.x(), node.y(), node.key(), resolved.get(node.id())));
        }
        if (complexity <= 0 || complexity > capacity) return Result.failure("complexity_limit");
        int red=0,green=0,blue=0,effects=0;
        for(var node:ordered)if(definitions.get(node.id()).type()==FocusNodeRegistry.Type.EFFECT){
            int rgb=definitions.get(node.id()).color();red+=(rgb>>16)&255;green+=(rgb>>8)&255;blue+=rgb&255;effects++;
        }
        int color=0xFF000000|(red/effects)<<16|(green/effects)<<8|blue/effects;
        return new Result(true, new FocusPlan(new FocusGraph(normalized), complexity, capacity, crystals, color), "");
    }
}
