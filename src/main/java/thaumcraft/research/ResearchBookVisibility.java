package thaumcraft.research;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Original hidden/parent visibility, restricted to the port's implemented progression. */
public final class ResearchBookVisibility {
    private ResearchBookVisibility() {}

    public static boolean visible(PlayerKnowledge knowledge, ResearchEntry entry, boolean archive) {
        if (entry == null) return false;
        if (archive) return true;
        if (entry.supported()) return ResearchProgression.legacyLessonAvailable(knowledge, entry.key());
        return ResearchProgression.isImplemented(entry.key())
                && ResearchCategories.categoryUnlocked(knowledge, entry.category())
                && visibleWithinCategory(knowledge, entry, new HashSet<>());
    }

    private static boolean visibleWithinCategory(PlayerKnowledge knowledge, ResearchEntry entry, Set<String> visiting) {
        if (entry.hasMeta("AUTOUNLOCK") || entry.icons().isEmpty()) return false;
        if (knowledge.isResearchKnown(entry.key())) return true;
        if (!visiting.add(entry.key())) return false;
        try {
            if (entry.hasMeta("HIDDEN") && (entry.parents().isEmpty()
                    || !entry.parents().stream().allMatch(knowledge::isResearchCompleteStrict))) return false;
            for (String raw : entry.parents()) {
                ResearchEntry parent = ResearchCatalog.get(ResearchCatalog.graphParentKey(raw));
                if (parent != null && !visibleWithinCategory(knowledge, parent, visiting)) return false;
            }
            return true;
        } finally { visiting.remove(entry.key()); }
    }

    /** Recipes/search never expose later stages; completed pages include only unlocked addenda. */
    public static List<ResearchEntry.Stage> readableChapters(PlayerKnowledge knowledge, ResearchEntry entry, boolean archive) {
        if (archive || entry.supported()) {
            var chapters = new java.util.ArrayList<>(entry.stages());
            if (archive) chapters.addAll(entry.addenda());
            return List.copyOf(chapters);
        }
        int stage = knowledge.researchStage(entry.key());
        if (stage <= 0 || entry.stages().isEmpty()) return List.of();
        var chapters = new java.util.ArrayList<ResearchEntry.Stage>();
        chapters.add(entry.stages().get(Math.min(stage, entry.stages().size()) - 1));
        if (knowledge.isResearchCompleteStrict(entry.key())) entry.addenda().stream()
                .filter(addendum -> addendum.requiredResearch().stream().allMatch(knowledge::isResearchCompleteStrict))
                .forEach(chapters::add);
        return List.copyOf(chapters);
    }
}
