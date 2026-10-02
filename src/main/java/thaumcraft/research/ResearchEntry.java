package thaumcraft.research;

import java.util.List;

public record ResearchEntry(String key, String title, String category, List<String> parents,
                            List<Stage> stages, boolean supported, int scans, int aspects,
                            int column, int row, List<String> icons, List<String> meta,
                            List<String> siblings, List<Stage> addenda) {
    public ResearchEntry {
        parents = List.copyOf(parents);
        stages = List.copyOf(stages);
        icons = List.copyOf(icons);
        meta = List.copyOf(meta);
        siblings = List.copyOf(siblings);
        addenda = List.copyOf(addenda);
    }

    public ResearchEntry(String key, String title, String category, List<String> parents,
                         List<Stage> stages, boolean supported, int scans, int aspects) {
        this(key, title, category, parents, stages, supported, scans, aspects,
                0, 0, List.of(), List.of(), List.of(), List.of());
    }

    public record Stage(String text, List<String> requirements, List<String> recipes,
                        List<String> requiredResearch, int warp) {
        public Stage {
            requirements = List.copyOf(requirements);
            recipes = List.copyOf(recipes);
            requiredResearch = List.copyOf(requiredResearch);
        }

        public Stage(String text, List<String> requirements) {
            this(text, requirements, List.of(), List.of(), 0);
        }
    }

    public boolean hasMeta(String flag) { return meta.contains(flag); }

    /** Legacy prerequisites are retained for display, never interpreted as an automatic unlock. */
    public boolean canDiscover(PlayerKnowledge knowledge) {
        return supported && !knowledge.knowsResearch(key)
                && knowledge.scanCount() >= scans && knowledge.discoveredAspects().size() >= aspects
                && parents.stream().allMatch(knowledge::knowsResearch);
    }
}
