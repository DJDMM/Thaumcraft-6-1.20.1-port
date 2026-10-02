package thaumcraft.research;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TC6 BETA26 ConfigResearch weights; closed tabs still receive observation knowledge. */
public final class ResearchCategories {
    private static final Map<String, Map<Aspect, Integer>> WEIGHTS;
    private static final List<String> KEYS;

    static {
        Map<String, Map<Aspect, Integer>> weights = new LinkedHashMap<>();
        weights.put("BASICS", formula(Aspect.PLANT, 5, Aspect.ORDER, 5, Aspect.ENTROPY, 5,
                Aspect.AIR, 5, Aspect.FIRE, 5, Aspect.EARTH, 3, Aspect.WATER, 5));
        weights.put("AUROMANCY", formula(Aspect.AURA, 20, Aspect.MAGIC, 20, Aspect.FLUX, 15,
                Aspect.CRYSTAL, 5, Aspect.COLD, 5, Aspect.AIR, 5));
        weights.put("ALCHEMY", formula(Aspect.ALCHEMY, 30, Aspect.FLUX, 10, Aspect.MAGIC, 10,
                Aspect.LIFE, 5, Aspect.AVERSION, 5, Aspect.DESIRE, 5, Aspect.WATER, 5));
        weights.put("ARTIFICE", formula(Aspect.MECHANISM, 10, Aspect.CRAFT, 10, Aspect.METAL, 10,
                Aspect.TOOL, 10, Aspect.ENERGY, 10, Aspect.LIGHT, 5, Aspect.FLIGHT, 5,
                Aspect.TRAP, 5, Aspect.FIRE, 5));
        weights.put("INFUSION", formula(Aspect.MAGIC, 30, Aspect.PROTECT, 10, Aspect.TOOL, 10,
                Aspect.FLUX, 5, Aspect.CRAFT, 5, Aspect.SOUL, 5, Aspect.EARTH, 3));
        weights.put("GOLEMANCY", formula(Aspect.MAN, 20, Aspect.MOTION, 10, Aspect.MIND, 10,
                Aspect.MECHANISM, 10, Aspect.EXCHANGE, 5, Aspect.SENSES, 5, Aspect.BEAST, 5, Aspect.ORDER, 5));
        weights.put("ELDRITCH", formula(Aspect.ELDRITCH, 20, Aspect.DARKNESS, 10, Aspect.MAGIC, 5,
                Aspect.MIND, 5, Aspect.VOID, 5, Aspect.DEATH, 5, Aspect.UNDEAD, 5, Aspect.ENTROPY, 5));
        WEIGHTS = Collections.unmodifiableMap(weights);
        KEYS = List.copyOf(weights.keySet());
    }

    private ResearchCategories() {}

    public static List<String> keys() { return KEYS; }

    public static boolean contains(String category) { return WEIGHTS.containsKey(category); }

    public static boolean categoryUnlocked(PlayerKnowledge knowledge, String category) {
        return contains(category) && (category.equals("BASICS")
                || knowledge.isResearchCompleteStrict("UNLOCK" + category));
    }

    /** ceil(sqrt(sum(amount * weight / 10))), with multiplication promoted before it can overflow. */
    public static int observationGain(String category, AspectList aspects) {
        Map<Aspect, Integer> formula = WEIGHTS.get(category);
        if (formula == null || aspects == null) return 0;
        double total = 0;
        for (var weight : formula.entrySet()) {
            int amount = aspects.getAmount(weight.getKey());
            if (amount > 0) total += (double) amount * (weight.getValue() / 10d);
        }
        return (int) Math.ceil(Math.sqrt(total));
    }

    private static Map<Aspect, Integer> formula(Object... values) {
        Map<Aspect, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((Aspect) values[i], (Integer) values[i + 1]);
        return Collections.unmodifiableMap(result);
    }
}
