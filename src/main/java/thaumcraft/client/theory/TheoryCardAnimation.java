package thaumcraft.client.theory;

import thaumcraft.research.theory.TheoryCard;
import thaumcraft.research.theory.TheorySession;

import java.util.List;

/** Detached, cosmetic BETA26 card motions. This class never alters the server session. */
final class TheoryCardAnimation {
    enum Phase { NONE, DEAL, OFFER, RESOLVE }
    private List<TheoryCard> cards = List.of();
    private String offered = "";
    private Phase phase = Phase.NONE;
    private float age;
    private final float[] hover = new float[3];
    private int selected = -1, previousPlaced;

    void sync(TheorySession session) {
        if (session == null) { clear(); previousPlaced = 0; return; }
        String choices = session.choices().stream().map(c -> c.save().toString()).reduce("", (a, b) -> a + b);
        if (!choices.isEmpty() && !choices.equals(offered)) {
            cards = List.copyOf(session.choices()); offered = choices; age = 0; phase = Phase.DEAL;
            selected = -1; java.util.Arrays.fill(hover, 0);
        } else if (choices.isEmpty() && !cards.isEmpty() && phase != Phase.RESOLVE) {
            // A successful SELECT has already paid on the server and persisted lastCard.
            // Rejected requests leave the offers intact; an arbitrary clear must not animate success.
            TheoryCard last = session.lastCard();
            selected = -1;
            if (last != null && session.placedCards() > previousPlaced) for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).save().equals(last.save())) { selected = i; break; }
            }
            if (selected >= 0) { age = 0; phase = Phase.RESOLVE; }
            else clear();
        }
        previousPlaced = session.placedCards();
    }
    void advance(float seconds, float x, float y) {
        age += seconds;
        if (phase == Phase.DEAL && age >= .65F + .14F * Math.max(0, cards.size() - 1)) phase = Phase.OFFER;
        if (phase == Phase.RESOLVE && age >= .55F) { clear(); return; }
        for (int i = 0; i < cards.size(); i++) {
            int width = cards.size() > 2 ? 80 : 116;
            int left = cards.size() > 2 ? 7 + i * 81 : cards.size() == 1 ? 69 : 10 + i * 120;
            float target = phase == Phase.OFFER && x >= left && x < left + width && y >= 44 && y < 142 ? 1 : 0;
            hover[i] += (target - hover[i]) * Math.min(1, seconds * 12);
        }
    }
    private void clear() { cards = List.of(); offered = ""; phase = Phase.NONE; age = 0; selected = -1; java.util.Arrays.fill(hover, 0); }
    Phase phase() { return phase; }
    List<TheoryCard> cards() { return cards; }
    boolean busy() { return phase == Phase.DEAL || phase == Phase.RESOLVE; }
    boolean selected(int index) { return index == selected; }
    float hover(int index) { return hover[index]; }
    float deal(int index) { return phase == Phase.DEAL ? ease(Math.max(0, Math.min(1, (age - .14F * (cards.size() - index - 1)) / .65F))) : 1; }
    float resolution() { return phase == Phase.RESOLVE ? ease(Math.min(1, age / .55F)) : 0; }
    private static float ease(float value) { return 1 - (1 - value) * (1 - value) * (1 - value); }
}
