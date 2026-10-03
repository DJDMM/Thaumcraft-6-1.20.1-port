package thaumcraft.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.Aspect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** UUID-keyed overworld data survives death, dimension changes and server restart. */
public final class KnowledgeStore extends SavedData {
    private final Map<UUID, PlayerKnowledge> players = new HashMap<>();

    public static KnowledgeStore of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                KnowledgeStore::load, KnowledgeStore::new, "thaumcraft_knowledge");
    }

    public static PlayerKnowledge get(ServerPlayer player) {
        return of(player.serverLevel()).get(player.getUUID());
    }

    public PlayerKnowledge get(UUID player) {
        return players.computeIfAbsent(player, id -> new PlayerKnowledge());
    }

    public static boolean recordScan(ServerPlayer player, String key, AspectList aspects) {
        boolean changed = of(player.serverLevel()).recordScan(player.getUUID(), key, aspects);
        if (changed) ResearchNetwork.sync(player);
        return changed;
    }

    /** Also exposed for deterministic server tests and integrations without an online player. */
    public boolean recordScan(UUID player, String key, AspectList aspects) {
        if (!get(player).recordScan(key, aspects)) return false;
        setDirty();
        return true;
    }

    /** Successful server craft events, including dust transformations and crucible output. */
    public static boolean recordCraft(ServerPlayer player, ItemStack result) {
        if (result == null || result.isEmpty()) return false;
        var id = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (id == null) return false;
        boolean changed = of(player.serverLevel()).recordCraft(player.getUUID(), id.toString());
        if (changed) ResearchNetwork.sync(player);
        return changed;
    }

    public boolean recordCraft(UUID player, String id) {
        if (!get(player).recordCraft(id)) return false;
        setDirty();
        return true;
    }

    public static boolean recordFact(ServerPlayer player, String fact) {
        boolean changed = of(player.serverLevel()).recordFact(player.getUUID(), fact);
        if (changed) ResearchNetwork.sync(player);
        return changed;
    }

    public boolean recordFact(UUID player, String fact) {
        if (!get(player).discover(fact)) return false;
        setDirty();
        return true;
    }

    /** The only book-write operation records viewed server facts; it cannot create research or knowledge. */
    public boolean recordBookRead(UUID player, String key, int expectedStage, int addendumMask) {
        if (!get(player).recordBookRead(key, expectedStage, addendumMask)) return false;
        setDirty();
        return true;
    }

    public static boolean addKnowledge(ServerPlayer player, KnowledgeType type, String category, int amount) {
        boolean changed = of(player.serverLevel()).addKnowledge(player.getUUID(), type, category, amount);
        if (changed) ResearchNetwork.sync(player);
        return changed;
    }

    public boolean addKnowledge(UUID player, KnowledgeType type, String category, int amount) {
        if (!get(player).addKnowledge(type, category, amount)) return false;
        setDirty();
        return true;
    }

    public static boolean recordCelestial(ServerPlayer player, long day, int metadata) {
        boolean changed = of(player.serverLevel()).recordCelestial(player.getUUID(), day, metadata);
        if (changed) ResearchNetwork.sync(player);
        return changed;
    }

    public boolean recordCelestial(UUID player, long day, int metadata) {
        if (!get(player).recordCelestial(day, metadata)) return false;
        setDirty();
        return true;
    }

    public static boolean addTemporaryWarp(ServerPlayer player, int amount) {
        boolean changed = of(player.serverLevel()).addTemporaryWarp(player.getUUID(), amount);
        if (changed) ResearchNetwork.sync(player);
        return changed;
    }

    public boolean addTemporaryWarp(UUID player, int amount) {
        if (!get(player).addTemporaryWarp(amount)) return false;
        setDirty();
        return true;
    }

    public static boolean addNormalWarp(ServerPlayer player,int amount) {
        boolean changed=of(player.serverLevel()).addNormalWarp(player.getUUID(),amount);
        if(changed) { discoverWarp(player);ResearchNetwork.sync(player); }
        return changed;
    }
    public boolean addNormalWarp(UUID player,int amount) {
        if(!get(player).addNormalWarp(amount)) return false;
        setDirty();return true;
    }
    public static boolean addPermanentWarp(ServerPlayer player,int amount) {
        boolean changed=of(player.serverLevel()).addPermanentWarp(player.getUUID(),amount);
        if(changed) { discoverWarp(player);ResearchNetwork.sync(player); }
        return changed;
    }
    public boolean addPermanentWarp(UUID player,int amount) {
        if(!get(player).addPermanentWarp(amount)) return false;
        setDirty();return true;
    }

    /** Only BETA26's item-triggered event entries; ordinary research still requires its stages. */
    public static boolean completeCrimsonRites(ServerPlayer player) {
        KnowledgeStore store=of(player.serverLevel());
        PlayerKnowledge knowledge=store.get(player.getUUID());
        ResearchEntry entry=ResearchCatalog.get("CrimsonRites");
        if(entry==null || knowledge.isResearchCompleteStrict("CrimsonRites")) return false;
        knowledge.setResearchStage("CrimsonRites",entry.stages().size()+1);
        store.setDirty();ResearchNetwork.sync(player);return true;
    }

    private static void discoverWarp(ServerPlayer player) {
        KnowledgeStore store=of(player.serverLevel());PlayerKnowledge knowledge=store.get(player.getUUID());
        ResearchEntry entry=ResearchCatalog.get("WARP");
        if(entry==null || !knowledge.isResearchCompleteStrict("FIRSTSTEPS") || knowledge.isResearchCompleteStrict("WARP")) return;
        knowledge.setResearchStage("WARP",entry.stages().size()+1);store.setDirty();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("research.WARP.warn"),true);
    }

    public static boolean discoverResearch(ServerPlayer player, String key) {
        return of(player.serverLevel()).discoverResearch(player.getUUID(), key);
    }

    public static boolean discoverAspect(ServerPlayer player, Aspect aspect) {
        KnowledgeStore store = of(player.serverLevel());
        if (!store.get(player.getUUID()).discoverAspect(aspect)) return false;
        store.setDirty();
        ResearchNetwork.sync(player);
        return true;
    }

    public boolean discoverResearch(UUID player, String key) {
        ResearchEntry entry = ResearchCatalog.get(key);
        PlayerKnowledge knowledge = get(player);
        if (entry == null || !entry.canDiscover(knowledge) || !knowledge.discover(key)) return false;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        players.forEach((uuid, knowledge) -> {
            CompoundTag player = knowledge.save();
            player.putUUID("Player", uuid);
            list.add(player);
        });
        tag.put("Players", list);
        return tag;
    }

    public static KnowledgeStore load(CompoundTag tag) {
        KnowledgeStore result = new KnowledgeStore();
        if (tag == null) return result;
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag player = list.getCompound(i);
            if (player.hasUUID("Player")) result.players.put(player.getUUID("Player"), PlayerKnowledge.load(player));
        }
        return result;
    }
}
