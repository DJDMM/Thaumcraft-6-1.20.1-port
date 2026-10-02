package thaumcraft.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Server-owned progression. Views cannot be mutated by callers. */
public final class PlayerKnowledge {
    private final Set<String> scans = new LinkedHashSet<>();
    private final Set<String> creditedScans = new LinkedHashSet<>();
    private final Set<String> aspects = new LinkedHashSet<>();
    private final Set<String> research = new LinkedHashSet<>();
    private final Set<String> crafts = new LinkedHashSet<>();
    private final Map<String, Integer> stages = new LinkedHashMap<>();
    private final Map<KnowledgeType, Map<String, Integer>> knowledge = new EnumMap<>(KnowledgeType.class);
    private long celestialDay = -1;
    private int celestialMask;
    private int temporaryWarp;
    private int normalWarp;
    private int permanentWarp;
    private int warpCounter;

    public boolean hasCelestial(long day, int metadata) {
        return day >= 0 && metadata >= 0 && metadata < 13 && celestialDay == day
                && (celestialMask & (1 << metadata)) != 0;
    }
    public int temporaryWarp() { return temporaryWarp; }
    public int normalWarp() { return normalWarp; }
    public int permanentWarp() { return permanentWarp; }
    public int actualWarp() { return normalWarp + permanentWarp; }
    public int warpCounter() { return warpCounter; }

    boolean recordCelestial(long day, int metadata) {
        if (day < 0 || metadata < 0 || metadata >= 13 || hasCelestial(day, metadata)) return false;
        if (celestialDay != day) { celestialDay = day; celestialMask = 0; }
        celestialMask |= 1 << metadata;
        return true;
    }

    boolean addTemporaryWarp(int amount) {
        int next = (int) Math.max(0L, Math.min(500L, (long) temporaryWarp + amount));
        int counter = amount > 0 ? next + normalWarp + permanentWarp : warpCounter;
        if (next == temporaryWarp && counter == warpCounter) return false;
        temporaryWarp = next;
        warpCounter = counter;
        return true;
    }

    boolean addNormalWarp(int amount) { return addPersistentWarp(amount,false); }
    boolean addPermanentWarp(int amount) { return addPersistentWarp(amount,true); }
    private boolean addPersistentWarp(int amount,boolean permanent) {
        int old=permanent ? permanentWarp : normalWarp;
        int next=(int)Math.max(0L,Math.min(500L,(long)old+amount));
        int counter=amount>0 ? temporaryWarp + (permanent ? normalWarp : permanentWarp) + next : warpCounter;
        if(old==next && counter==warpCounter) return false;
        if(permanent) permanentWarp=next; else normalWarp=next;
        warpCounter=counter;
        return true;
    }

    public int scanCount() { return scans.size(); }
    public boolean hasScanned(String key) { return scans.contains(key); }
    public boolean knowsAspect(Aspect aspect) { return aspect != null && aspects.contains(aspect.getTag()); }
    public int researchStage(String key) { return stages.getOrDefault(key, 0); }
    public boolean hasCraft(String id) { return crafts.contains(id); }

    public int rawKnowledge(KnowledgeType type, String category) {
        Map<String, Integer> values = knowledge.get(type);
        return values == null ? 0 : values.getOrDefault(category, 0);
    }

    public int completedKnowledge(KnowledgeType type, String category) {
        return type == null ? 0 : rawKnowledge(type, category) / type.units();
    }

    /** Opened entries and event facts, with no PORT recipe aliases. */
    public boolean isResearchKnown(String raw) {
        String key = normalizedKey(raw);
        if (key == null) return false;
        if (key.isEmpty()) return true;
        int separator = key.indexOf('@');
        if (separator >= 0) return hasStageRequirement(key, separator);
        return researchStage(key) > 0 || research.contains(key) || isAspectFact(key);
    }

    /** Bare canonical keys require completion; KEY@N requires entry into stage N. */
    public boolean isResearchCompleteStrict(String raw) {
        String key = normalizedKey(raw);
        if (key == null) return false;
        if (key.isEmpty()) return true;
        int separator = key.indexOf('@');
        if (separator >= 0) return hasStageRequirement(key, separator);
        ResearchEntry entry = ResearchCatalog.get(key);
        if (entry != null && !entry.supported()) return researchStage(key) > entry.stages().size();
        return research.contains(key) || isAspectFact(key);
    }

    /** Recipe access retains 0.2/0.3 unlocks, independently of canonical research completion. */
    public boolean knowsResearch(String raw) {
        String key = normalizedKey(raw);
        if (key == null) return false;
        if (isResearchCompleteStrict(key)) return true;
        if (key.equals("PORT_ALCHEMY") && isResearchCompleteStrict("BASEALCHEMY")) return true;
        String legacy = switch (key) {
            case "FIRSTSTEPS@2" -> "PORT_START";
            case "BASEALCHEMY" -> "PORT_ALCHEMY";
            case "ALUMENTUM" -> "PORT_ALUMENTUM";
            case "UNLOCKALCHEMY@3" -> "PORT_NITOR";
            case "METALLURGY@1" -> "PORT_BRASS";
            case "METALLURGY@2" -> "PORT_THAUMIUM";
            case "HEDGEALCHEMY@1" -> "PORT_TALLOW";
            default -> null;
        };
        return legacy != null && research.contains(legacy);
    }
    public Set<String> scanKeys() { return Collections.unmodifiableSet(scans); }
    public Set<String> researchKeys() {
        Set<String> result = new LinkedHashSet<>(research);
        result.addAll(stages.keySet());
        return Collections.unmodifiableSet(result);
    }
    public Set<String> discoveredAspects() { return Collections.unmodifiableSet(aspects); }

    boolean recordScan(String key, AspectList found) {
        if (key == null || key.isBlank() || key.length() > 256 || found == null || found.size() == 0) return false;
        boolean valid = false;
        for (Aspect aspect : found.getAspects()) {
            if (aspect != null && found.getAmount(aspect) > 0) valid = true;
        }
        if (!valid) return false;
        if (creditedScans.contains(key)) return false;
        Map<String, Integer> rewards = new LinkedHashMap<>();
        for (String category : ResearchCategories.keys()) {
            int amount = ResearchCategories.observationGain(category, found);
            if ((long) rawKnowledge(KnowledgeType.OBSERVATION, category) + amount > Integer.MAX_VALUE) return false;
            rewards.put(category, amount);
        }
        scans.add(key);
        creditedScans.add(key);
        for (Aspect aspect : found.getAspects()) {
            if (aspect != null && found.getAmount(aspect) > 0) aspects.add(aspect.getTag());
        }
        rewards.forEach((category, amount) -> addKnowledge(KnowledgeType.OBSERVATION, category, amount));
        return true;
    }

    boolean discover(String key) { return validKey(key) && research.add(key); }
    boolean discoverAspect(Aspect aspect) { return aspect != null && aspects.add(aspect.getTag()); }

    boolean recordCraft(String id) {
        return validKey(id) && id.indexOf(':') > 0 && ResourceLocation.tryParse(id) != null && crafts.add(id);
    }

    /** Negative deltas spend raw knowledge; an invalid debit or overflowing credit changes nothing. */
    boolean addKnowledge(KnowledgeType type, String category, int amount) {
        if (type == null || !ResearchCategories.contains(category) || amount == 0) return false;
        long next = (long) rawKnowledge(type, category) + amount;
        if (next < 0 || next > Integer.MAX_VALUE) return false;
        knowledge.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(category, (int) next);
        return true;
    }

    boolean setResearchStage(String key, int stage) {
        ResearchEntry entry = ResearchCatalog.get(key);
        if (entry == null || stage <= 0 || stage > entry.stages().size() + 1 || researchStage(key) == stage) return false;
        stages.put(key, stage);
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.put("Scans", writeStrings(scans));
        tag.put("CreditedScans", writeStrings(creditedScans));
        tag.put("Aspects", writeStrings(aspects));
        tag.put("Research", writeStrings(research));
        tag.put("Crafts", writeStrings(crafts));
        CompoundTag stageData = new CompoundTag();
        stages.forEach(stageData::putInt);
        tag.put("ResearchStages", stageData);
        CompoundTag rawData = new CompoundTag();
        knowledge.forEach((type, values) -> {
            CompoundTag categories = new CompoundTag();
            values.forEach(categories::putInt);
            rawData.put(type.name(), categories);
        });
        tag.put("Knowledge", rawData);
        if (celestialDay >= 0) {
            tag.putLong("CelestialDay", celestialDay);
            tag.putInt("CelestialMask", celestialMask);
        }
        tag.putInt("TemporaryWarp", temporaryWarp);
        tag.putInt("NormalWarp", normalWarp);
        tag.putInt("PermanentWarp", permanentWarp);
        tag.putInt("WarpCounter", warpCounter);
        return tag;
    }

    public static PlayerKnowledge load(CompoundTag tag) {
        PlayerKnowledge result = new PlayerKnowledge();
        if (tag == null) return result;
        readStrings(tag, "Scans", result.scans);
        readStrings(tag, "Aspects", result.aspects);
        result.aspects.removeIf(aspect -> Aspect.getAspect(aspect) == null);
        readStrings(tag, "Research", result.research);
        if (tag.contains("Version", Tag.TAG_INT) && tag.getInt("Version") >= 2) {
            readStrings(tag, "CreditedScans", result.creditedScans);
            result.creditedScans.retainAll(result.scans);
            Set<String> crafts = new LinkedHashSet<>();
            readStrings(tag, "Crafts", crafts);
            crafts.forEach(result::recordCraft);
            CompoundTag stages = tag.getCompound("ResearchStages");
            for (String key : stages.getAllKeys()) {
                if (stages.contains(key, Tag.TAG_INT)) result.setResearchStage(key, stages.getInt(key));
            }
            CompoundTag raw = tag.getCompound("Knowledge");
            for (KnowledgeType type : KnowledgeType.values()) {
                CompoundTag categories = raw.getCompound(type.name());
                for (String category : ResearchCategories.keys()) {
                    if (!categories.contains(category, Tag.TAG_INT)) continue;
                    int amount = categories.getInt(category);
                    if (amount > 0) result.addKnowledge(type, category, amount);
                }
            }
        }
        if (tag.contains("CelestialDay", Tag.TAG_LONG) && tag.getLong("CelestialDay") >= 0
                && tag.contains("CelestialMask", Tag.TAG_INT) && tag.getInt("CelestialMask") >= 0
                && tag.getInt("CelestialMask") <= 8191) {
            result.celestialDay = tag.getLong("CelestialDay");
            result.celestialMask = tag.getInt("CelestialMask");
        }
        if (tag.contains("TemporaryWarp", Tag.TAG_INT)) result.temporaryWarp = Math.max(0, Math.min(500, tag.getInt("TemporaryWarp")));
        if (tag.contains("NormalWarp", Tag.TAG_INT)) result.normalWarp = Math.max(0, Math.min(500, tag.getInt("NormalWarp")));
        if (tag.contains("PermanentWarp", Tag.TAG_INT)) result.permanentWarp = Math.max(0, Math.min(500, tag.getInt("PermanentWarp")));
        int counterCap=tag.contains("NormalWarp",Tag.TAG_INT) || tag.contains("PermanentWarp",Tag.TAG_INT) ? 1500 : 500;
        if (tag.contains("WarpCounter", Tag.TAG_INT)) result.warpCounter = Math.max(0, Math.min(counterCap, tag.getInt("WarpCounter")));
        return result;
    }

    private boolean hasStageRequirement(String key, int separator) {
        if (separator == 0) return false;
        try {
            int required = Integer.parseInt(key.substring(separator + 1));
            return required > 0 && researchStage(key.substring(0, separator)) >= required;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isAspectFact(String key) {
        return key.startsWith("!") && aspects.contains(key.substring(1));
    }

    private static String normalizedKey(String key) {
        return key != null && key.startsWith("~") ? key.substring(1) : key;
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 256;
    }

    private static ListTag writeStrings(Set<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    private static void readStrings(CompoundTag tag, String key, Set<String> target) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isBlank() && value.length() <= 256) target.add(value);
        }
    }
}
