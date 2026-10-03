package thaumcraft.research;

import java.util.ArrayList;
import java.util.List;

/** Snapshot differences describe notifications only; they never infer or award progression. */
public final class ResearchBookNotifications {
    public enum Kind { RESEARCH, PAGE }
    public record Notice(ResearchEntry entry, Kind kind) {}
    private ResearchBookNotifications() {}

    public static List<Notice> between(PlayerKnowledge previous, PlayerKnowledge current) {
        if (previous == null || current == null) return List.of();
        List<Notice> notices = new ArrayList<>();
        for (ResearchEntry entry : ResearchCatalog.entries()) {
            if (entry.supported()) continue;
            boolean before = previous.isResearchCompleteStrict(entry.key());
            boolean after = current.isResearchCompleteStrict(entry.key());
            if (!before && after) notices.add(new Notice(entry, Kind.RESEARCH));
            // Completion already has its own toast. PAGE means an additional discovery on an existing entry.
            if (before && (ResearchBookState.availableAddendaMask(current, entry)
                    & ~ResearchBookState.availableAddendaMask(previous, entry)) != 0) {
                notices.add(new Notice(entry, Kind.PAGE));
            }
        }
        return List.copyOf(notices);
    }
}
