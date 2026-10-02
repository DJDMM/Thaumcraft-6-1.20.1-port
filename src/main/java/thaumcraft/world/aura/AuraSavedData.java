package thaumcraft.world.aura;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** One persisted aura store per dimension; chunk keys never collide between dimensions. */
public final class AuraSavedData extends SavedData {
    private final Map<Long, AuraChunk> chunks = new HashMap<>();
    final Set<Long> activeChunks = new HashSet<>();

    public static AuraSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AuraSavedData::load, AuraSavedData::new, "thaumcraft_aura");
    }

    public AuraChunk getChunk(ChunkPos pos) { return chunks.get(pos.toLong()); }

    public AuraChunk getOrCreate(ChunkPos pos, int base) {
        AuraChunk found = chunks.get(pos.toLong());
        if (found != null) return found;
        AuraChunk created = new AuraChunk(base, base, 0.0F);
        chunks.put(pos.toLong(), created);
        setDirty();
        return created;
    }

    public static AuraSavedData load(CompoundTag tag) {
        AuraSavedData data = new AuraSavedData();
        ListTag list = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.chunks.put(ChunkPos.asLong(entry.getInt("X"), entry.getInt("Z")), AuraChunk.load(entry));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 1);
        ListTag list = new ListTag();
        chunks.forEach((key, aura) -> {
            ChunkPos pos = new ChunkPos(key);
            CompoundTag entry = aura.save();
            entry.putInt("X", pos.x);
            entry.putInt("Z", pos.z);
            list.add(entry);
        });
        tag.put("Chunks", list);
        return tag;
    }
}
