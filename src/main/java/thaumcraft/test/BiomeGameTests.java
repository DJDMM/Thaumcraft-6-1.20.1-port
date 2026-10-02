package thaumcraft.test;

import com.mojang.serialization.JsonOps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import terrablender.util.LevelUtils;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.biome.BiomeModule;

/** Registry/codec, real chunk serialization and normal-preset noise-source regression tests. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class BiomeGameTests {
    private static final List<ResourceKey<Biome>> BIOMES =
            List.of(BiomeModule.MAGICAL_FOREST, BiomeModule.EERIE, BiomeModule.ELDRITCH);

    private BiomeGameTests() {}
    private static ResourceLocation id(String path) { return new ResourceLocation("thaumcraft", path); }

    @GameTest(template = "empty")
    public static void biomeCodecsPreserveBeta26ClimatePaletteAuraAndVanillaSpawns(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var registry = access.registryOrThrow(Registries.BIOME);
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        float[] downfall = {.4F, .5F, .2F};
        // Outer Lands inherited a white multiplier in 1.12; the modern equivalent retains vanilla water.
        int[] water = {0x0077EE, 0x2E535F, 4159204};
        int[] sky = {0x78A7FF, 0x222299, 0};
        for (int i = 0; i < BIOMES.size(); i++) {
            Holder<Biome> holder = registry.getHolderOrThrow(BIOMES.get(i));
            var encoded = Biome.DIRECT_CODEC.encodeStart(ops, holder.value()).getOrThrow(false,
                    message -> { throw new AssertionError("Biome encoding failed: " + message); });
            Biome decoded = Biome.DIRECT_CODEC.parse(ops, encoded).getOrThrow(false,
                    message -> { throw new AssertionError("Biome decoding failed: " + message); });
            helper.assertTrue(decoded.getBaseTemperature() == .8F
                    && decoded.getModifiedClimateSettings().downfall() == downfall[i]
                    && decoded.hasPrecipitation() == (i != 1), "BETA26 climate changed for " + BIOMES.get(i));
            helper.assertTrue(decoded.getWaterColor() == water[i] && decoded.getSkyColor() == sky[i],
                    "BETA26 water/sky palette changed for " + BIOMES.get(i));
            var effects = decoded.getSpecialEffects();
            if (i < 2) {
                int grass = i == 0 ? 0x55FF81 : 0x404840;
                int foliage = i == 0 ? 0x66FFC5 : 0x404840;
                helper.assertTrue(effects.getGrassColorOverride().orElse(-1) == grass
                        && effects.getFoliageColorOverride().orElse(-1) == foliage,
                        "BETA26 grass/foliage overrides changed for " + BIOMES.get(i));
            } else helper.assertTrue(effects.getGrassColorOverride().isEmpty()
                    && effects.getFoliageColorOverride().isEmpty(), "Outer Lands invented a grass/foliage override");
            helper.assertTrue(decoded.getAmbientParticle().isEmpty() && decoded.getAmbientLoop().isEmpty()
                    && decoded.getAmbientAdditions().isEmpty(), "Biome invented a permanent ambient effect");
            float expectedAura = i == 2 ? 11F / 24F : .625F;
            helper.assertTrue(Math.abs(AuraManager.biomeModifier(holder) - expectedAura) < .000001F,
                    "BETA26 dictionary average changed for " + BIOMES.get(i));
            Map<MobCategory, List<String>> expected = vanillaSpawns(i);
            for (MobCategory category : MobCategory.values()) {
                List<String> actual = decoded.getMobSettings().getMobs(category).unwrap().stream()
                        .map(spawn -> BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type) + ":"
                                + spawn.getWeight().asInt() + ":" + spawn.minCount + ":" + spawn.maxCount)
                        .sorted().toList();
                helper.assertTrue(actual.equals(expected.getOrDefault(category, List.of()).stream().sorted().toList()),
                        "Vanilla spawn entries, duplicate weights or groups changed in " + BIOMES.get(i)
                                + " / " + category + ": " + actual);
            }
        }
        Holder<Biome> forest = registry.getHolderOrThrow(BiomeModule.MAGICAL_FOREST);
        helper.assertTrue(forest.is(BiomeTags.IS_OVERWORLD) && forest.is(BiomeTags.IS_FOREST),
                "Magical Forest lacks the modern overworld/forest adaptation tags");
        // The legacy magical/spooky types use mod-owned tags; they do not make Eerie natural.
        TagKey<Biome> magical = TagKey.create(Registries.BIOME, id("magical"));
        TagKey<Biome> spooky = TagKey.create(Registries.BIOME, id("spooky"));
        for (ResourceKey<Biome> key : BIOMES)
            helper.assertTrue(registry.getHolderOrThrow(key).is(magical), "Missing magical type: " + key);
        helper.assertTrue(!forest.is(spooky) && registry.getHolderOrThrow(BiomeModule.EERIE).is(spooky)
                && registry.getHolderOrThrow(BiomeModule.ELDRITCH).is(spooky), "Spooky type membership changed");
        helper.succeed();
    }

    private static Map<MobCategory, List<String>> vanillaSpawns(int biome) {
        Map<MobCategory, List<String>> result = new EnumMap<>(MobCategory.class);
        if (biome == 2) return result; // All original categories were cleared; TC mobs are not ported yet.
        List<String> monsters = new ArrayList<>(List.of(
                "minecraft:spider:100:4:4", "minecraft:zombie:95:4:4", "minecraft:zombie_villager:5:1:1",
                "minecraft:skeleton:100:4:4", "minecraft:creeper:100:4:4", "minecraft:slime:100:4:4",
                "minecraft:enderman:10:1:4", "minecraft:witch:5:1:1"));
        monsters.add("minecraft:witch:" + (biome == 0 ? 3 : 8) + ":1:1");
        monsters.add("minecraft:enderman:" + (biome == 0 ? 3 : 4) + ":1:1");
        if (biome == 0) monsters.add("minecraft:vex:1:1:1");
        result.put(MobCategory.MONSTER, monsters);
        result.put(MobCategory.CREATURE, biome == 0 ? List.of(
                "minecraft:sheep:12:4:4", "minecraft:pig:10:4:4", "minecraft:chicken:10:4:4",
                "minecraft:cow:8:4:4", "minecraft:wolf:2:1:3", "minecraft:horse:2:1:3")
                : List.of("minecraft:bat:3:1:1"));
        result.put(MobCategory.AMBIENT, List.of("minecraft:bat:10:8:8"));
        result.put(MobCategory.WATER_CREATURE, List.of("minecraft:squid:10:4:4"));
        return result;
    }

    @GameTest(template = "empty")
    public static void allThreeBiomesSurviveCompleteThreeDimensionalChunkNbt(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder<Biome>> holders = BIOMES.stream().map(registry::getHolderOrThrow).map(holder -> (Holder<Biome>) holder).toList();
        ChunkPos pos = new ChunkPos(-137, 211);
        // Detached FULL chunk: no existing test or player-world chunk is modified.
        LevelChunk original = new LevelChunk(level, pos);
        original.fillBiomesFromNoise((x, y, z, sampler) -> holders.get(Math.floorMod(x + 2 * z + y, 3)), Climate.empty());
        BlockPos marker = pos.getMiddleBlockPosition(96);
        original.setBlockState(marker, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), false);
        List<ResourceLocation> expected = palette(original);
        helper.assertTrue(expected.stream().distinct().count() == 3, "Fixture did not contain all three biomes");
        helper.assertTrue(!original.getNoiseBiome(pos.x * 4, 16, pos.z * 4)
                .equals(original.getNoiseBiome(pos.x * 4, 17, pos.z * 4)), "Fixture collapsed to a 2D X/Z biome array");
        var encoded = ChunkSerializer.write(level, original);
        helper.assertTrue(encoded.getString("Status").equals(BuiltInRegistries.CHUNK_STATUS.getKey(ChunkStatus.FULL).toString())
                && encoded.getList("sections", Tag.TAG_COMPOUND).size() >= level.getSectionsCount(),
                "Serializer did not write a complete FULL chunk with every vertical section");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(encoded, bytes);
        var decodedTag = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        ChunkAccess restored = ChunkSerializer.read(level, level.getPoiManager(), pos, decodedTag);
        helper.assertTrue(restored.getStatus() == ChunkStatus.FULL && restored.getPos().equals(pos),
                "FULL chunk status or signed coordinates were lost");
        helper.assertTrue(expected.equals(palette(restored)), "A complete 3D biome palette changed after actual ChunkSerializer/NBT roundtrip");
        helper.assertTrue(restored.getBlockState(marker).is(Blocks.MOSSY_COBBLESTONE), "Chunk terrain marker was lost");
        helper.succeed();
    }

    private static List<ResourceLocation> palette(ChunkAccess chunk) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int y = chunk.getMinBuildHeight() >> 2; y < chunk.getMaxBuildHeight() >> 2; y++)
            for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                result.add(chunk.getNoiseBiome(chunk.getPos().x * 4 + x, y, chunk.getPos().z * 4 + z)
                        .unwrapKey().orElseThrow().location());
        return List.copyOf(result);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void normalOverworldNoiseSourceNaturallySamplesOnlyMagicalForest(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var overworld = WorldPresets.getNormalOverworld(access);
        var generator = overworld.generator();
        helper.assertTrue(generator instanceof NoiseBasedChunkGenerator, "Normal preset is not a noise generator");
        // A flat GameTest server never starts this normal-preset stem. Use TerraBlender's actual
        // server-start initialization hook, including region noise, rather than a synthetic biome list.
        LevelUtils.initializeBiomes(access, overworld.type(), LevelStem.OVERWORLD, generator, 32L);
        var source = generator.getBiomeSource();
        var sampler = RandomState.create(access.registryOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD), access.lookupOrThrow(Registries.NOISE), 32L).sampler();
        helper.assertTrue(source.possibleBiomes().stream().anyMatch(holder -> holder.is(BiomeModule.MAGICAL_FOREST)),
                "Actual normal-preset biome source lacks Magical Forest");
        helper.assertTrue(source.possibleBiomes().stream().noneMatch(holder -> holder.is(BiomeModule.EERIE)
                || holder.is(BiomeModule.ELDRITCH)), "Conversion-only biomes entered natural overworld generation");
        int forests = 0;
        // Use the normal generator's real climate sampler, even though the GameTest world itself is flat.
        for (int x = -1024; x <= 1024; x += 16) for (int z = -1024; z <= 1024; z += 16) {
            Holder<Biome> sample = source.getNoiseBiome(x, 16, z, sampler);
            if (sample.is(BiomeModule.MAGICAL_FOREST)) forests++;
            helper.assertTrue(!sample.is(BiomeModule.EERIE) && !sample.is(BiomeModule.ELDRITCH),
                    "Conversion-only biome sampled naturally at quart coordinates " + x + ",16," + z);
        }
        helper.assertTrue(forests > 0, "Magical Forest was registered but not actually sampled in normal world noise");
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void magicalForestUsesRegisteredDecorationAndBranchedBigOak(GameTestHelper helper) {
        var level = helper.getLevel();
        var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var forest = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(BiomeModule.MAGICAL_FOREST).value();
        for (String name : new String[]{"magical_forest_trees", "forest_silverwood", "big_magic_tree", "magical_forest_mushrooms"})
            helper.assertTrue(configured.containsKey(id(name)), "Missing real configured forest feature: " + name);
        helper.assertTrue(placed.containsKey(id("magical_forest_trees"))
                && forest.getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(id("magical_forest_trees"))).orElse(false)),
                "Forest's actual generation settings do not reference its registered tree decorator");
        BlockPos origin = helper.absolutePos(new BlockPos(19, 2, 19));
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 0; y < 26; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(configured.get(id("big_magic_tree")).place(level, level.getChunkSource().getGenerator(),
                RandomSource.create(32), origin), "Registered big-oak feature failed on clear soil");
        int logs = 0, leaves = 0, highest = 0, branches = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-10, 0, -10), origin.offset(10, 25, 10))) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.OAK_LOG)) {
                logs++;
                highest = Math.max(highest, pos.getY() - origin.getY());
                if (pos.getX() != origin.getX() || pos.getZ() != origin.getZ()) branches++;
            }
            if (state.is(Blocks.OAK_LEAVES)) leaves++;
        }
        helper.assertTrue(logs > 15 && leaves > 100 && highest >= 7 && branches > 0,
                "Magical Forest tree lost the BETA26 large oak height, crown or branches");
        helper.succeed();
    }
}
