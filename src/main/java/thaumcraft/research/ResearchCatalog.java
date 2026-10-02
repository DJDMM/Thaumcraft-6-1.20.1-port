package thaumcraft.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bundled, versioned catalogue shared by client and server; imported records are explicitly archival. */
public final class ResearchCatalog {
    private static final Map<String, ResearchEntry> ENTRIES = load();

    public static ResearchEntry get(String key) { return ENTRIES.get(key); }
    public static List<ResearchEntry> entries() { return List.copyOf(ENTRIES.values()); }
    public static List<String> categories() {
        return List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH", "PORT");
    }

    /** Only for drawing graph links: this is not a prerequisite or progression check. */
    public static String graphParentKey(String parent) {
        String key = parent.startsWith("~") ? parent.substring(1) : parent;
        int stage = key.indexOf('@');
        return stage < 0 ? key : key.substring(0, stage);
    }

    public static List<ResearchEntry> graphParents(ResearchEntry entry) {
        return entry.parents().stream().map(ResearchCatalog::graphParentKey)
                .map(ResearchCatalog::get).filter(Objects::nonNull).distinct().toList();
    }

    private static Map<String, ResearchEntry> load() {
        Map<String, ResearchEntry> entries = new LinkedHashMap<>();
        try (InputStream in = ResearchCatalog.class.getResourceAsStream("/assets/thaumcraft/research/catalog.json")) {
            if (in == null) throw new IllegalStateException("Missing Thaumcraft research catalogue");
            for (JsonElement element : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject json = element.getAsJsonObject();
                ResearchEntry entry = new ResearchEntry(json.get("key").getAsString(), json.get("title").getAsString(),
                        json.get("category").getAsString(), strings(json, "parents"), stages(json, "stages"),
                        json.get("supported").getAsBoolean(), number(json, "scans"), number(json, "aspects"),
                        number(json, "column"), number(json, "row"), strings(json, "icons"), strings(json, "meta"),
                        strings(json, "siblings"), stages(json, "addenda"));
                if (entries.putIfAbsent(entry.key(), entry) != null) throw new IllegalStateException("Duplicate research " + entry.key());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read Thaumcraft research", e);
        }
        return Collections.unmodifiableMap(entries);
    }

    private static int number(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static List<ResearchEntry.Stage> stages(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        List<ResearchEntry.Stage> result = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(key)) {
            JsonObject stage = element.getAsJsonObject();
            result.add(new ResearchEntry.Stage(stage.get("text").getAsString(), strings(stage, "requirements"),
                    strings(stage, "recipes"), strings(stage, "requiredResearch"), number(stage, "warp")));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        List<String> result = new ArrayList<>();
        object.getAsJsonArray(key).forEach(value -> result.add(value.getAsString()));
        return List.copyOf(result);
    }
}
