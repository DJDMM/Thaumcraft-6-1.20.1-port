package thaumcraft.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.TagParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Implemented TC6 stages. Display strings are never used to decide a payment. */
public final class ResearchProgression {
    private static final Set<String> IMPLEMENTED = Set.of("FIRSTSTEPS", "KNOWLEDGETYPES", "THEORYRESEARCH", "CELESTIALSCANNING",
            "UNLOCKALCHEMY", "BASEALCHEMY", "ALUMENTUM", "METALLURGY", "UNLOCKARTIFICE", "BASEARTIFICE",
            "UNLOCKINFUSION", "BASEINFUSION", "ESSENTIASMELTER", "WARDEDJARS", "TUBES",
            "UNLOCKAUROMANCY", "INFUSION", "INFUSIONBOOST",
            "BASEAUROMANCY", "RECHARGEPEDESTAL", "BOOTSTRAVELLER", "ELEMENTALTOOLS", "ARMORFORTRESS",
            "FOCUSELEMENTAL", "FOCUSPROJECTILE");
    private static final Set<String> REMAINING_LESSONS = Set.of("PORT_TALLOW");
    private static final Set<String> OLD_PROFILE_LESSONS = Set.of("PORT_START", "PORT_SCAN", "PORT_ALCHEMY",
            "PORT_NITOR", "PORT_ALUMENTUM", "PORT_BRASS", "PORT_THAUMIUM");
    private static final Map<String, List<Requirements>> REQUIREMENTS = load();

    private ResearchProgression() {}
    public enum Result { STARTED, ADVANCED, COMPLETE, STALE, MISSING_REQUIREMENTS, LOCKED, UNSUPPORTED, NO_BOOK }
    public static boolean isImplemented(String key) { return IMPLEMENTED.contains(key); }
    public static int stage(PlayerKnowledge knowledge, String key) { return knowledge.researchStage(key); }
    public static boolean isComplete(PlayerKnowledge knowledge, String key) { return knowledge.isResearchCompleteStrict(key); }
    public static boolean legacyLessonAvailable(PlayerKnowledge knowledge, String key) {
        ResearchEntry entry = ResearchCatalog.get(key);
        return entry != null && entry.supported() && (REMAINING_LESSONS.contains(key)
                || knowledge.researchKeys().stream().anyMatch(OLD_PROFILE_LESSONS::contains));
    }
    public static boolean canStart(PlayerKnowledge knowledge, String key) {
        ResearchEntry entry = ResearchCatalog.get(key);
        return isImplemented(key) && entry != null && stage(knowledge, key) == 0 && parentsMet(knowledge, entry);
    }
    private static boolean parentsMet(PlayerKnowledge knowledge, ResearchEntry entry) {
        return entry.parents().stream().allMatch(raw -> knowledge.isResearchCompleteStrict(raw.startsWith("~") ? raw.substring(1) : raw));
    }
    public static boolean canAdvance(PlayerKnowledge knowledge, ResearchEntry entry) {
        return canAdvance(knowledge, entry, null);
    }
    /** Client inventory is an optional preview only; advance always checks real server inventory. */
    public static boolean canAdvance(PlayerKnowledge knowledge, ResearchEntry entry, net.minecraft.world.entity.player.Inventory inventory) {
        if (!isImplemented(entry.key()) || isComplete(knowledge, entry.key()) || !parentsMet(knowledge, entry)) return false;
        int current = stage(knowledge, entry.key());
        if (current == 0) return true;
        List<Requirements> list = REQUIREMENTS.get(entry.key());
        return current <= list.size() && list.get(current - 1).met(knowledge)
                && (inventory == null || inventoryPlan(inventory, list.get(current - 1).obtain()) != null);
    }
    /** Detached, canonical NBT templates for book display; never a payment instruction. */
    public static List<ItemStack> requiredItems(String key, int stage) {
        List<Requirements> stages = REQUIREMENTS.get(key);
        return stages == null || stage < 1 || stage > stages.size() ? List.of()
                : stages.get(stage - 1).obtain().stream().map(Obtain::template).toList();
    }

    /** Expected stage prevents duplicate, delayed or replayed requests from paying the next stage. */
    public static Result advance(ServerPlayer player, String key, int expectedStage) {
        if (!isImplemented(key)) return Result.UNSUPPORTED;
        if (player.isSpectator() || !player.isAlive() || !player.serverLevel().getServer().isSameThread()) return Result.LOCKED;
        KnowledgeStore store = KnowledgeStore.of(player.serverLevel());
        PlayerKnowledge knowledge = store.get(player.getUUID());
        ResearchEntry entry = ResearchCatalog.get(key);
        int current = stage(knowledge, key);
        if (current != expectedStage) return Result.STALE;
        if (isComplete(knowledge, key) || !parentsMet(knowledge, entry)) return Result.LOCKED;
        List<Requirements> stages = REQUIREMENTS.get(key);
        if (current == 0) {
            int next = stages.size() == 1 && stages.get(0).empty() ? 2 : 1;
            knowledge.setResearchStage(key, next);
            int siblingExperience = revealSiblings(knowledge, entry);
            store.setDirty();
            player.giveExperiencePoints(5 + siblingExperience);
            return next > stages.size() ? Result.COMPLETE : Result.STARTED;
        }
        Requirements requirements = stages.get(current - 1);
        if (!requirements.met(knowledge)) return Result.MISSING_REQUIREMENTS;
        Map<Integer, Integer> plan = inventoryPlan(player.getInventory(), requirements.obtain());
        if (plan == null) return Result.MISSING_REQUIREMENTS;
        // Every resource and fact has been checked. All mutations below run on the server thread.
        for (KnowledgeCost cost : requirements.knowledge()) knowledge.addKnowledge(cost.type(), cost.category(), -cost.raw());
        plan.forEach((slot, count) -> player.getInventory().getItem(slot).shrink(count));
        if (!plan.isEmpty()) player.getInventory().setChanged();
        int next = current + 1;
        if (next == stages.size() && stages.get(next - 1).empty()) next++;
        knowledge.setResearchStage(key, next);
        int siblingExperience = revealSiblings(knowledge, entry);
        store.setDirty();
        player.giveExperiencePoints(5 + siblingExperience);
        return next > stages.size() ? Result.COMPLETE : Result.ADVANCED;
    }

    private static int revealSiblings(PlayerKnowledge knowledge, ResearchEntry entry) {
        int experience = 0;
        for (String raw : entry.siblings()) {
            if (raw.startsWith("!")) knowledge.discover(raw);
            else if (isImplemented(raw) && stage(knowledge, raw) == 0) {
                ResearchEntry sibling = ResearchCatalog.get(raw);
                List<Requirements> req = REQUIREMENTS.get(raw);
                if (parentsMet(knowledge, sibling) && req.size() == 1 && req.get(0).empty()
                        && knowledge.setResearchStage(raw, 2)) experience += 5;
            }
        }
        return experience;
    }

    private static Map<Integer, Integer> inventoryPlan(net.minecraft.world.entity.player.Inventory inventory, List<Obtain> obtain) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Obtain cost : obtain) {
            int remaining = cost.count();
            // BETA26's obtain payment searches mainInventory, never worn/offhand items.
            for (int slot = 0; slot < inventory.items.size() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!cost.matches(stack)) continue;
                int available = stack.getCount() - result.getOrDefault(slot, 0);
                int take = Math.min(Math.max(0, available), remaining);
                if (take > 0) { result.merge(slot, take, Integer::sum); remaining -= take; }
            }
            if (remaining > 0) return null;
        }
        return result;
    }

    private static Map<String, List<Requirements>> load() {
        Map<String, List<Requirements>> result = new HashMap<>();
        for (String filename : List.of("basics", "alchemy", "auromancy", "artifice", "infusion")) {
            String path = "/data/thaumcraft/legacy_research/" + filename + ".json";
            try (var stream = ResearchProgression.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing original research " + path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement element : json.getAsJsonArray("entries")) {
                    JsonObject entry = element.getAsJsonObject();
                    String key = entry.get("key").getAsString();
                    if (!isImplemented(key)) continue;
                    List<Requirements> stages = new ArrayList<>();
                    for (JsonElement stage : entry.getAsJsonArray("stages")) stages.add(parse(stage.getAsJsonObject()));
                    if (stages.size() != ResearchCatalog.get(key).stages().size()) throw new IllegalStateException("Stage catalogue mismatch " + key);
                    result.put(key, List.copyOf(stages));
                }
            } catch (Exception failure) { throw new IllegalStateException("Invalid TC6 stage data " + path, failure); }
        }
        if (!result.keySet().equals(IMPLEMENTED)) throw new IllegalStateException("Incomplete TC6 initial stage data");
        return Map.copyOf(result);
    }

    private static Requirements parse(JsonObject stage) {
        List<KnowledgeCost> knowledge = new ArrayList<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String raw : strings(stage, "required_knowledge")) {
            String[] parts = raw.split(";");
            if (parts.length != 3 || !ResearchCategories.keys().contains(parts[1])) throw new IllegalArgumentException("Invalid knowledge " + raw);
            KnowledgeType type = KnowledgeType.valueOf(parts[0]);
            int units = Integer.parseInt(parts[2]);
            if (units <= 0) throw new IllegalArgumentException("Non-positive knowledge cost");
            totals.merge(type.name() + ";" + parts[1], Math.multiplyExact(units, type.units()), Math::addExact);
        }
        totals.forEach((key, raw) -> {
            String[] parts = key.split(";");
            knowledge.add(new KnowledgeCost(KnowledgeType.valueOf(parts[0]), parts[1], raw));
        });
        List<String> craft = strings(stage, "required_craft").stream().map(ResearchProgression::craftId).toList();
        List<Obtain> obtain = new ArrayList<>();
        for (String raw : strings(stage, "required_item")) {
            // BETA26 drops invalid IDs (e.g. minecraft:thaumcraft:enchanted_placeholder).
            // Do not repair them into costs the released research never charged.
            if (ResourceLocation.tryParse(raw.split(";", 2)[0]) != null) obtain.add(Obtain.parse(raw));
        }
        for (String key : stage.keySet()) if (key.startsWith("required_") &&
                !Set.of("required_knowledge", "required_craft", "required_item", "required_research").contains(key))
            throw new IllegalArgumentException("Unsupported requirement " + key);
        return new Requirements(List.copyOf(knowledge), craft, strings(stage, "required_research"), List.copyOf(obtain));
    }

    private static String craftId(String raw) {
        String[] parts = raw.split(";", 4);
        if (parts.length > 3 || parts.length > 1 && Integer.parseInt(parts[1]) <= 0)
            throw new IllegalArgumentException("Unsupported craft requirement " + raw);
        return LegacyResearchItems.resolve(raw).toString();
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> result = new ArrayList<>();
        json.getAsJsonArray(key).forEach(value -> result.add(value.getAsString()));
        return List.copyOf(result);
    }

    private record KnowledgeCost(KnowledgeType type, String category, int raw) {}
    private record Requirements(List<KnowledgeCost> knowledge, List<String> craft, List<String> research, List<Obtain> obtain) {
        boolean empty() { return knowledge.isEmpty() && craft.isEmpty() && research.isEmpty() && obtain.isEmpty(); }
        boolean met(PlayerKnowledge state) {
            return craft.stream().allMatch(state::hasCraft) && research.stream().allMatch(state::isResearchCompleteStrict)
                    && knowledge.stream().allMatch(cost -> state.rawKnowledge(cost.type(), cost.category()) >= cost.raw());
        }
    }
    private record Obtain(ResourceLocation item, int count, net.minecraft.nbt.CompoundTag nbt) {
        static Obtain parse(String raw) {
            try {
                String[] parts = raw.split(";", 4);
                ResourceLocation id = LegacyResearchItems.resolve(raw);
                int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                if (count <= 0) throw new IllegalArgumentException("Invalid obtain count " + raw);
                return new Obtain(id, count, parts.length > 3 ? TagParser.parseTag(parts[3]) : null);
            } catch (Exception failure) { throw new IllegalArgumentException("Invalid obtain " + raw, failure); }
        }
        ItemStack template() {
            ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(item), count);
            if (nbt != null) template.setTag(nbt.copy());
            return template;
        }
        boolean matches(ItemStack stack) {
            // Share the audited found-to-template relaxed comparison with theory cards.
            // In particular Aspects is one complete list, not a subset of a mixed phial.
            return thaumcraft.research.theory.TheoryCard.matchesRequirement(stack, template());
        }
    }
}
