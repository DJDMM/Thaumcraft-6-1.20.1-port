package thaumcraft.research;

import java.util.ArrayList;
import java.util.List;

/** Read-only book facts. The archive never contributes to server progression or notification state. */
public final class ResearchBookState {
    private ResearchBookState() {}

    /** BETA26 shows addenda only after completion and strict completion of every listed prerequisite. */
    public static int availableAddendaMask(PlayerKnowledge knowledge, ResearchEntry entry) {
        if (knowledge == null || entry == null || entry.supported()
                || !knowledge.isResearchCompleteStrict(entry.key())) return 0;
        int mask = 0;
        for (int i = 0; i < Math.min(31, entry.addenda().size()); i++) {
            if (entry.addenda().get(i).requiredResearch().stream().allMatch(knowledge::isResearchCompleteStrict)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static List<ResearchEntry.Stage> visibleAddenda(PlayerKnowledge knowledge, ResearchEntry entry) {
        int mask = availableAddendaMask(knowledge, entry);
        List<ResearchEntry.Stage> result = new ArrayList<>();
        if (entry != null) for (int i = 0; i < Math.min(31, entry.addenda().size()); i++) {
            if ((mask & (1 << i)) != 0) result.add(entry.addenda().get(i));
        }
        return List.copyOf(result);
    }
}
