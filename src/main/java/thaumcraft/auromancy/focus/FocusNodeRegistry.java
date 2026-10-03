package thaumcraft.auromancy.focus;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Immutable metadata for the 21 nodes registered by ConfigItems in TC6 BETA26. */
public final class FocusNodeRegistry {
    public static final String ROOT = "ROOT", TOUCH = "thaumcraft.TOUCH", FIRE = "thaumcraft.FIRE";
    public enum Type { MEDIUM, EFFECT, MOD }
    public enum Supply { TARGET, TRAJECTORY }

    /** Values are the original true setting values, not the spinner indices. */
    public record Setting(String key, String label, List<Integer> values, List<String> descriptions, String research) {
        public Setting {
            values = List.copyOf(values); descriptions = List.copyOf(descriptions);
            if (values.isEmpty() || values.size() != descriptions.size()) throw new IllegalArgumentException("setting values");
        }
        public int defaultValue() { return values.get(0); }
        public boolean accepts(int value) { return values.contains(value); }
    }

    public record Definition(String key, Type type, String research, String aspect, int color,
                             ResourceLocation icon, Map<String, Setting> settings, boolean runtimeSupported,
                             Set<Supply> requiredSupply, Set<Supply> supplied, boolean exclusive) {
        public Definition {
            settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
            requiredSupply = Set.copyOf(requiredSupply); supplied = Set.copyOf(supplied);
        }
        public Map<String, Integer> defaultSettings() {
            Map<String, Integer> result = new LinkedHashMap<>();
            settings.forEach((key, value) -> result.put(key, value.defaultValue()));
            return Collections.unmodifiableMap(result);
        }
        /** Reference-only nodes retain their exact BETA26 complexity calculation. */
        public int complexity(Map<String, Integer> settings) {
            int power = value(settings, "power"), duration = value(settings, "duration");
            int fortune = value(settings, "fortune"), silk = value(settings, "silk");
            return switch (key) {
                case ROOT -> 0;
                case TOUCH -> 2;
                case "thaumcraft.BOLT", "thaumcraft.SPLITTRAJECTORY" -> 5;
                case "thaumcraft.MINE", "thaumcraft.PLAN", "thaumcraft.SPLITTARGET" -> 4;
                case "thaumcraft.SPELLBAT" -> 8;
                case "thaumcraft.PROJECTILE" -> 4 + (value(settings, "speed") - 1) / 2
                        + switch (value(settings, "option")) { case 1 -> 3; case 2, 3 -> 5; default -> 0; };
                case "thaumcraft.CLOUD" -> 4 + value(settings, "radius") * 2 + duration / 5;
                case FIRE, "thaumcraft.FROST" -> duration + power * 2;
                case "thaumcraft.AIR" -> power * 2;
                case "thaumcraft.EARTH", "thaumcraft.FLUX" -> power * 3;
                case "thaumcraft.BREAK" -> power * 3 + silk * 4 + (fortune == 0 ? 0 : (fortune + 1) * 3);
                // The unusual precedence is confirmed in the official JAR, not a decompiler repair.
                case "thaumcraft.EXCHANGE" -> 5 + silk * 4 + fortune == 0 ? 0 : (fortune + 1) * 3;
                case "thaumcraft.RIFT" -> 3 + duration / 2 + value(settings, "depth") / 4;
                case "thaumcraft.CURSE" -> duration + power * 3;
                case "thaumcraft.HEAL" -> power * 4;
                case "thaumcraft.SCATTER" -> (int) Math.max(2F, 2F * (value(settings, "forks") - value(settings, "cone") / 45F));
                default -> throw new IllegalArgumentException("Unknown focus node");
            };
        }
        public float powerMultiplier(Map<String, Integer> settings) {
            return switch (key) {
                case "thaumcraft.CLOUD" -> .5F;
                case "thaumcraft.SPELLBAT" -> .33F;
                case "thaumcraft.SCATTER" -> 1F / (value(settings, "forks") / 2F);
                case "thaumcraft.SPLITTARGET", "thaumcraft.SPLITTRAJECTORY" -> .75F;
                default -> 1F;
            };
        }
        private int value(Map<String, Integer> settings, String key) {
            Setting definition = this.settings.get(key);
            return settings.getOrDefault(key, definition == null ? 0 : definition.defaultValue());
        }
    }

    private static final Set<Supply> TARGET = Set.of(Supply.TARGET), TRAJECTORY = Set.of(Supply.TRAJECTORY);
    private static final Set<Supply> BOTH = Set.of(Supply.TARGET, Supply.TRAJECTORY);
    private static final Map<String, Definition> DEFINITIONS = create();
    private FocusNodeRegistry() {}
    public static Definition get(String key) { return DEFINITIONS.get(key); }
    public static Collection<Definition> all() { return DEFINITIONS.values(); }

    private static Map<String, Definition> create() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        add(definitions, ROOT, Type.MEDIUM, "BASEAUROMANCY", null, 10066329, "root", true, Set.of(), BOTH, false);
        add(definitions, TOUCH, Type.MEDIUM, "BASEAUROMANCY", "aversio", 11371909, "touch", true, TRAJECTORY, BOTH, false);
        add(definitions, "thaumcraft.BOLT", Type.MEDIUM, "FOCUSBOLT", "potentia", 11377029, "bolt", false, TRAJECTORY, BOTH, false);
        add(definitions, "thaumcraft.PROJECTILE", Type.MEDIUM, "FOCUSPROJECTILE@2", "motus", 11382149, "projectile", false, TRAJECTORY, BOTH, false,
                list("option", "focus.common.options", "FOCUSPROJECTILE", new int[]{0,1,2,3}, "focus.common.none", "focus.projectile.bouncy", "focus.projectile.seeking.hostile", "focus.projectile.seeking.friendly"),
                range("speed", "focus.projectile.speed", 1, 5));
        add(definitions, "thaumcraft.CLOUD", Type.MEDIUM, "FOCUSCLOUD", "alkimia", 10071429, "cloud", false, TRAJECTORY, TARGET, false,
                range("radius", "focus.common.radius", 1, 3), range("duration", "focus.common.duration", 5, 30));
        add(definitions, "thaumcraft.MINE", Type.MEDIUM, "FOCUSMINE", "vinculum", 8760709, "mine", false, TRAJECTORY, BOTH, false, targetSetting());
        add(definitions, "thaumcraft.PLAN", Type.MEDIUM, "FOCUSPLAN", "fabrico", 8760728, "plan", false, TRAJECTORY, TARGET, true,
                list("method", "focus.plan.method", null, new int[]{0,1}, "focus.plan.full", "focus.plan.surface"));
        add(definitions, "thaumcraft.SPELLBAT", Type.MEDIUM, "FOCUSSPELLBAT", "bestia", 8760748, "spellbat", false, TRAJECTORY, TARGET, false, targetSetting());
        add(definitions, FIRE, Type.EFFECT, "BASEAUROMANCY", "ignis", 16734721, "fire", true, TARGET, Set.of(), false,
                range("power", "focus.common.power", 1, 5), range("duration", "focus.fire.burn", 0, 5));
        add(definitions, "thaumcraft.FROST", Type.EFFECT, "FOCUSELEMENTAL", "gelum", 14811135, "frost", false, TARGET, Set.of(), false,
                range("power", "focus.common.power", 1, 5), range("duration", "focus.common.duration", 2, 10));
        add(definitions, "thaumcraft.AIR", Type.EFFECT, "FOCUSELEMENTAL", "aer", 16777086, "air", false, TARGET, Set.of(), false, range("power", "focus.common.power", 1, 5));
        add(definitions, "thaumcraft.EARTH", Type.EFFECT, "FOCUSELEMENTAL", "terra", 5685248, "earth", false, TARGET, Set.of(), false, range("power", "focus.common.power", 1, 5));
        add(definitions, "thaumcraft.FLUX", Type.EFFECT, "FOCUSFLUX", "vitium", 8388736, "flux", false, TARGET, Set.of(), false, range("power", "focus.common.power", 1, 5));
        add(definitions, "thaumcraft.BREAK", Type.EFFECT, "FOCUSBREAK", "perditio", 9063176, "break", false, TARGET, Set.of(), false,
                range("power", "focus.break.power", 1, 5), fortuneSetting(), silkSetting());
        add(definitions, "thaumcraft.RIFT", Type.EFFECT, "FOCUSRIFT", "alienis", 3084645, "rift", false, TARGET, Set.of(), false,
                list("depth", "focus.rift.depth", null, new int[]{8,16,24,32}, "8", "16", "24", "32"), range("duration", "focus.common.duration", 2, 10));
        add(definitions, "thaumcraft.EXCHANGE", Type.EFFECT, "FOCUSEXCHANGE", "permutatio", 5735255, "exchange", false, TARGET, Set.of(), false, fortuneSetting(), silkSetting());
        add(definitions, "thaumcraft.CURSE", Type.EFFECT, "FOCUSCURSE", "mortuus", 6946821, "curse", false, TARGET, Set.of(), false,
                range("power", "focus.common.power", 1, 5), range("duration", "focus.common.duration", 1, 10));
        add(definitions, "thaumcraft.HEAL", Type.EFFECT, "FOCUSHEAL", "victus", 14548997, "heal", false, TARGET, Set.of(), false, range("power", "focus.heal.power", 1, 5));
        add(definitions, "thaumcraft.SCATTER", Type.MOD, "FOCUSSCATTER", null, 10066329, "scatter", false, TRAJECTORY, TRAJECTORY, true,
                range("forks", "focus.scatter.forks", 2, 10), list("cone", "focus.scatter.cone", null, new int[]{10,30,60,90,180,270,360}, "10", "30", "60", "90", "180", "270", "360"));
        add(definitions, "thaumcraft.SPLITTARGET", Type.MOD, "FOCUSSPLIT", null, 10066329, "split_target", false, TARGET, TARGET, false);
        add(definitions, "thaumcraft.SPLITTRAJECTORY", Type.MOD, "FOCUSSPLIT", null, 10066329, "split_trajectory", false, TRAJECTORY, TRAJECTORY, false);
        return Collections.unmodifiableMap(definitions);
    }
    private static void add(Map<String, Definition> map, String key, Type type, String research, String aspect, int color,
                            String icon, boolean supported, Set<Supply> required, Set<Supply> supplies, boolean exclusive, Setting... settings) {
        Map<String, Setting> byKey = new LinkedHashMap<>();
        for (Setting setting : settings) byKey.put(setting.key(), setting);
        if (map.put(key, new Definition(key, type, research, aspect, color,
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/foci/" + icon + ".png"),
                byKey, supported, required, supplies, exclusive)) != null) throw new IllegalStateException("Duplicate focus node");
    }
    private static Setting range(String key, String label, int min, int max) {
        List<Integer> values = new ArrayList<>(); List<String> descriptions = new ArrayList<>();
        for (int value = min; value <= max; value++) { values.add(value); descriptions.add(Integer.toString(value)); }
        return new Setting(key, label, values, descriptions, null);
    }
    private static Setting list(String key, String label, String research, int[] values, String... descriptions) {
        return new Setting(key, label, Arrays.stream(values).boxed().toList(), List.of(descriptions), research);
    }
    private static Setting targetSetting() { return list("target", "focus.common.target", null, new int[]{0,1}, "focus.common.enemy", "focus.common.friend"); }
    private static Setting silkSetting() { return list("silk", "focus.common.silk", null, new int[]{0,1}, "focus.common.no", "focus.common.yes"); }
    private static Setting fortuneSetting() { return list("fortune", "focus.common.fortune", null, new int[]{0,1,2,3,4}, "focus.common.no", "I", "II", "III", "IV"); }
}
