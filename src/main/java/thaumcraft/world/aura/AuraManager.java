package thaumcraft.world.aura;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;

/**
 * Server-thread aura API. The TC6 lunar regeneration, vis diffusion and flux diffusion
 * rules are retained. TC6's three biome coefficients are implemented; a complete
 * replacement for the old biome dictionary, rifts and taint remain unported.
 * Queries initialize loaded chunks lazily and never force-load an unloaded chunk.
 */
public final class AuraManager {
    private static final float[] PHASE_VIS = {0.25F, 0.15F, 0.10F, 0.05F, 0.0F, 0.05F, 0.10F, 0.15F};
    private static final float[] PHASE_MAX = {1.15F, 1.05F, 1.0F, 0.95F, 0.85F, 0.95F, 1.0F, 1.05F};
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private AuraManager() {}

    private static AuraChunk aura(ServerLevel level, BlockPos pos) {
        requireServerThread(level);
        ChunkPos chunkPos = new ChunkPos(pos);
        AuraSavedData data = AuraSavedData.get(level);
        AuraChunk aura = data.getChunk(chunkPos);
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk != null) {
            data.activeChunks.add(chunkPos.toLong());
            if (aura == null) aura = data.getOrCreate(chunkPos, generateBase(level, chunk));
        }
        return aura;
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Aura API must run on the server thread");
    }

    public static float getVis(ServerLevel level, BlockPos pos) {
        AuraChunk aura = aura(level, pos);
        return aura == null ? 0 : aura.getVis();
    }

    public static float getFlux(ServerLevel level, BlockPos pos) {
        AuraChunk aura = aura(level, pos);
        return aura == null ? 0 : aura.getFlux();
    }

    public static int getAuraBase(ServerLevel level, BlockPos pos) {
        AuraChunk aura = aura(level, pos);
        return aura == null ? 0 : aura.getBase();
    }

    public static float drainVis(ServerLevel level, BlockPos pos, float amount, boolean simulate) {
        if (!Float.isFinite(amount) || amount <= 0) return 0;
        AuraChunk aura = aura(level, pos);
        if (aura == null) return 0;
        float drained = Math.min(amount, aura.getVis());
        if (!simulate && drained > 0) {
            aura.setVis(aura.getVis() - drained);
            AuraSavedData.get(level).setDirty();
        }
        return drained;
    }

    public static float drainFlux(ServerLevel level, BlockPos pos, float amount, boolean simulate) {
        if (!Float.isFinite(amount) || amount <= 0) return 0;
        AuraChunk aura = aura(level, pos);
        if (aura == null) return 0;
        float drained = Math.min(amount, aura.getFlux());
        if (!simulate && drained > 0) {
            aura.setFlux(aura.getFlux() - drained);
            AuraSavedData.get(level).setDirty();
        }
        return drained;
    }

    public static void addVis(ServerLevel level, BlockPos pos, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        AuraChunk aura = aura(level, pos);
        if (aura != null) {
            aura.setVis(aura.getVis() + amount);
            AuraSavedData.get(level).setDirty();
        }
    }

    public static void addFlux(ServerLevel level, BlockPos pos, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        AuraChunk aura = aura(level, pos);
        if (aura != null) {
            aura.setFlux(aura.getFlux() + amount);
            AuraSavedData.get(level).setDirty();
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            // Chunk callbacks can originate during loading; serialize access onto the server.
            ChunkPos pos = chunk.getPos();
            level.getServer().execute(() -> {
                if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                    aura(level, pos.getMiddleBlockPosition(64));
                }
            });
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            long key = event.getChunk().getPos().toLong();
            level.getServer().execute(() -> AuraSavedData.get(level).activeChunks.remove(key));
        }
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level
                && level.getGameTime() % 20 == 0) tick(level);
    }

    public static void tick(ServerLevel level) {
        requireServerThread(level);
        AuraSavedData data = AuraSavedData.get(level);
        int phase = level.getMoonPhase();
        for (long key : new ArrayList<>(data.activeChunks)) {
            ChunkPos pos = new ChunkPos(key);
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                data.activeChunks.remove(key);
                continue;
            }
            AuraChunk current = data.getChunk(pos);
            if (current == null) continue;
            AuraChunk lowVis = null;
            AuraChunk lowFlux = null;
            int rotation = level.random.nextInt(4);
            for (int i = 0; i < 4; i++) {
                Direction dir = HORIZONTAL[(rotation + i) % 4];
                ChunkPos neighborPos = new ChunkPos(pos.x + dir.getStepX(), pos.z + dir.getStepZ());
                if (!data.activeChunks.contains(neighborPos.toLong())) continue;
                AuraChunk neighbor = data.getChunk(neighborPos);
                if (neighbor == null) continue;
                if (neighbor.getVis() + neighbor.getFlux() < neighbor.getBase() * PHASE_MAX[phase]
                        && (lowVis == null || neighbor.getVis() < lowVis.getVis())) lowVis = neighbor;
                if (lowFlux == null || neighbor.getFlux() < lowFlux.getFlux()) lowFlux = neighbor;
            }
            boolean changed = false;
            if (lowVis != null && current.getVis() > 0 && lowVis.getVis() / current.getVis() < 0.75F) {
                float transfer = Math.min(current.getVis() - lowVis.getVis(), 1.0F);
                current.setVis(current.getVis() - transfer);
                lowVis.setVis(lowVis.getVis() + transfer);
                changed = true;
            }
            if (lowFlux != null && current.getFlux() > Math.max(5.0F, current.getBase() / 10.0F)
                    && lowFlux.getFlux() < current.getFlux() / 1.75F) {
                float transfer = Math.min(current.getFlux() - lowFlux.getFlux(), 1.0F);
                current.setFlux(current.getFlux() - transfer);
                lowFlux.setFlux(lowFlux.getFlux() + transfer);
                changed = true;
            }
            if (regenerate(current, phase, level.random.nextFloat())) changed = true;
            if (changed) data.setDirty();
        }
    }

    /** One one-second TC6 lunar update, separated for deterministic validation. */
    public static boolean regenerate(AuraChunk aura, int moonPhase, float randomChance) {
        int phase = Math.floorMod(moonPhase, 8);
        float base = aura.getBase() * PHASE_MAX[phase];
        float gain = PHASE_VIS[phase];
        float pollution = 0.25F - gain;
        if (aura.getVis() + aura.getFlux() < base && gain > 0) {
            aura.setVis(aura.getVis() + Math.min(base - aura.getVis() - aura.getFlux(), gain));
            return true;
        }
        if (randomChance < 0.1F && pollution > 0 && aura.getVis() > base * 1.25F) {
            aura.setVis(aura.getVis() - pollution);
            aura.setFlux(aura.getFlux() + pollution);
            return true;
        }
        if (randomChance < 0.1F && pollution > 0 && aura.getVis() <= base * 0.1F && aura.getVis() >= aura.getFlux()) {
            aura.setFlux(aura.getFlux() + pollution);
            return true;
        }
        return false;
    }

    private static int generateBase(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        // Biome samples use noise biome access so aura creation cannot force neighbor chunks.
        float life = biomeModifier(level.getNoiseBiome(pos.x * 4 + 2, 16, pos.z * 4 + 2));
        for (Direction dir : HORIZONTAL) {
            life += biomeModifier(level.getNoiseBiome((pos.x + dir.getStepX()) * 4 + 2, 16,
                    (pos.z + dir.getStepZ()) * 4 + 2));
        }
        RandomSource random = RandomSource.create(level.getSeed() ^ pos.toLong() ^ 0x544841554D4CL);
        return Mth.clamp((int) (life / 5.0F * 500.0F * (1.0F + (float) random.nextGaussian() * 0.1F)), 0, 500);
    }

    public static float biomeModifier(Holder<Biome> biome) {
        // BETA26 averages every dictionary type: magical+forest, magical+spooky,
        // and magical+spooky+end. Do this before the generic modern biome tags.
        if (biome.is(thaumcraft.world.biome.BiomeModule.MAGICAL_FOREST)
                || biome.is(thaumcraft.world.biome.BiomeModule.EERIE)) return (0.75F + 0.5F) / 2;
        if (biome.is(thaumcraft.world.biome.BiomeModule.ELDRITCH)) return (0.75F + 0.5F + 0.125F) / 3;
        if (biome.is(BiomeTags.IS_NETHER) || biome.is(BiomeTags.IS_END)) return 0.125F;
        if (biome.is(Biomes.MUSHROOM_FIELDS)) return 0.75F;
        if (biome.is(BiomeTags.IS_JUNGLE)) return 0.6F;
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) return 0.5F;
        if (biome.is(BiomeTags.IS_RIVER)) return 0.4F;
        if (biome.is(BiomeTags.IS_OCEAN)) return 0.33F;
        if (biome.value().getBaseTemperature() < 0.2F || biome.value().getBaseTemperature() > 1.5F) return 0.25F;
        return 0.3F;
    }
}
