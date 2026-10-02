package thaumcraft.test;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.world.biome.BiomeModule;
import thaumcraft.world.biome.MagicalForestTreesFeature;
import thaumcraft.world.trees.TreeBiomeRules;
import thaumcraft.world.trees.TreeModule;

/** Actual loaded placements and tree growth: rarity trials alone are not spawn probabilities. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TreeSpawnRateGameTests {
    private TreeSpawnRateGameTests() {}
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", path);
    }

    @GameTest(template = "empty")
    public static void registeredTreePlacementsKeepBeta26TrialsAndModernBiomeSupport(GameTestHelper helper) {
        var level = helper.getLevel();
        var placements = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        String[] names = {"greatwood_tree", "silverwood_tree", "magical_forest_greatwood", "magical_forest_silverwood"};
        String[] features = {"natural_greatwood_tree", "natural_silverwood_tree", "natural_greatwood_tree", "silverwood_tree"};
        int[] chances = {25, 80, 25, 160};
        for (int i = 0; i < names.length; i++) {
            var encoded = PlacedFeature.DIRECT_CODEC.encodeStart(ops, placements.get(id(names[i])))
                    .getOrThrow(false, message -> { throw new AssertionError(message); }).getAsJsonObject();
            helper.assertTrue(encoded.get("feature").getAsString().equals(id(features[i]).toString()),
                    "Wrong configured feature behind the loaded placement " + names[i]);
            int rarityCount = 0, actualChance = -1;
            for (var element : encoded.getAsJsonArray("placement")) {
                var modifier = element.getAsJsonObject();
                if (modifier.get("type").getAsString().equals("minecraft:rarity_filter")) {
                    rarityCount++;
                    actualChance = modifier.get("chance").getAsInt();
                }
            }
            helper.assertTrue(rarityCount == 1 && actualChance == chances[i],
                    "Wrong BETA26 chunk trial in actual loaded placement " + names[i]);
        }
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (var key : List.of(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.WINDSWEPT_FOREST,
                Biomes.SPARSE_JUNGLE, BiomeModule.MAGICAL_FOREST, BiomeModule.EERIE, BiomeModule.ELDRITCH))
            assertSupport(helper, biomes.getHolderOrThrow(key), 1F);
        for (var key : List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.TAIGA, Biomes.SNOWY_TAIGA,
                Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.SAVANNA,
                Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA, Biomes.SWAMP, Biomes.MANGROVE_SWAMP))
            assertSupport(helper, biomes.getHolderOrThrow(key), .2F);
        TagKey<Biome> greatwood = TagKey.create(Registries.BIOME, id("has_greatwood"));
        TagKey<Biome> silverwood = TagKey.create(Registries.BIOME, id("has_silverwood"));
        for (var key : List.of(Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.DESERT, Biomes.BADLANDS,
                Biomes.OCEAN, Biomes.RIVER, Biomes.THE_END, Biomes.NETHER_WASTES)) {
            var holder = biomes.getHolderOrThrow(key);
            assertSupport(helper, holder, 0F);
            helper.assertTrue(!holder.is(greatwood) && !holder.is(silverwood),
                    "An unsupported biome still receives natural-tree attempts: " + key.location());
        }
        helper.assertTrue(biomes.getHolderOrThrow(Biomes.TAIGA).is(silverwood)
                && biomes.getHolderOrThrow(Biomes.SAVANNA).is(silverwood)
                && biomes.getHolderOrThrow(Biomes.SPARSE_JUNGLE).is(silverwood),
                "Supported modern biomes never receive Silverwood's trial");
        helper.succeed();
    }

    private static void assertSupport(GameTestHelper helper, Holder<Biome> biome, float expected) {
        helper.assertTrue(TreeBiomeRules.greatwoodSupport(biome) == expected,
                "Wrong vegetation support for " + biome.unwrapKey().orElseThrow().location());
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void naturalGreatwoodGatesRealPlacementBeforeChoosingSpiderVariant(GameTestHelper helper) {
        BlockPos origin = origin(helper);
        prepareSoil(helper, origin);
        try (BiomeFixture fixture = new BiomeFixture(helper, origin)) {
            fixture.use(Biomes.PLAINS);
            FirstFloatRandom rejected = new FirstFloatRandom(.2F);
            helper.assertTrue(!place(helper, "natural_greatwood_tree", origin, rejected),
                    "Greatwood ignored the strict .2 support gate in Plains");
            helper.assertTrue(rejected.floatCalls == 1 && rejected.intCalls == 0,
                    "Rejected Greatwood consumed spider/tree RNG before its biome gate");
            assertUnchangedSoil(helper, origin);
            helper.assertTrue(place(helper, "natural_greatwood_tree", origin, new FirstFloatRandom(Math.nextDown(.2F))),
                    "Greatwood rejected a supported Plains candidate just below .2");
            helper.assertTrue(helper.getLevel().getBlockState(origin).is(TreeModule.LOG_GREATWOOD.get()),
                    "Passing the Plains gate did not generate an actual Greatwood trunk");
            clearTree(helper, origin);
            fixture.use(Biomes.FOREST);
            helper.assertTrue(place(helper, "natural_greatwood_tree", origin, new FirstFloatRandom(.999F)),
                    "Full forest support incorrectly applied the Plains gate");
            helper.assertTrue(helper.getLevel().getBlockState(origin).is(TreeModule.LOG_GREATWOOD.get()),
                    "Passing full forest support did not generate the actual tree");
        }
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void naturalSilverwoodUsesHalfSupportAndTheOriginalMagicalException(GameTestHelper helper) {
        BlockPos origin = origin(helper);
        prepareSoil(helper, origin);
        try (BiomeFixture fixture = new BiomeFixture(helper, origin)) {
            for (var key : List.of(Biomes.PLAINS, Biomes.FOREST, BiomeModule.MAGICAL_FOREST)) {
                fixture.use(key);
                float boundary = key.equals(Biomes.PLAINS) ? .1F : .5F;
                FirstFloatRandom rejected = new FirstFloatRandom(boundary);
                helper.assertTrue(!place(helper, "natural_silverwood_tree", origin, rejected),
                        "Silverwood ignored its strict half-support gate in " + key.location());
                helper.assertTrue(rejected.floatCalls == 1 && rejected.intCalls == 0,
                        "Rejected Silverwood consumed generator RNG before its biome gate");
                assertUnchangedSoil(helper, origin);
                helper.assertTrue(place(helper, "natural_silverwood_tree", origin,
                        new FirstFloatRandom(Math.nextDown(boundary))),
                        "Silverwood rejected a candidate below its gate in " + key.location());
                helper.assertTrue(helper.getLevel().getBlockState(origin).is(TreeModule.LOG_SILVERWOOD.get()),
                        "Passing Silverwood's gate did not grow a trunk");
                clearTree(helper, origin);
            }
            for (var key : List.of(BiomeModule.EERIE, BiomeModule.ELDRITCH)) {
                fixture.use(key);
                FirstFloatRandom magical = new FirstFloatRandom(.999F);
                helper.assertTrue(place(helper, "natural_silverwood_tree", origin, magical),
                        "BETA26 magical exception was lost for " + key.location());
                helper.assertTrue(magical.floatCalls == 1,
                        "Magical Silverwood skipped the original gate draw");
                clearTree(helper, origin);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void saplingsAndMagicalForestDecoratorKeepTheirIndependentGeneration(GameTestHelper helper) {
        BlockPos origin = origin(helper);
        prepareSoil(helper, origin);
        try (BiomeFixture fixture = new BiomeFixture(helper, origin)) {
            fixture.use(Biomes.PLAINS);
            var silver = (SaplingBlock) TreeModule.SAPLING_SILVERWOOD.get();
            helper.getLevel().setBlock(origin, silver.defaultBlockState(), 2);
            grow(helper, origin, silver);
            helper.assertTrue(helper.getLevel().getBlockState(origin).is(TreeModule.LOG_SILVERWOOD.get()),
                    "Player Silverwood sapling inherited the natural Plains probability gate");
            clearTree(helper, origin);
            var great = (SaplingBlock) TreeModule.SAPLING_GREATWOOD.get();
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
                helper.getLevel().setBlock(origin.offset(x, 0, z), great.defaultBlockState(), 2);
            grow(helper, origin, great);
            helper.assertTrue(helper.getLevel().getBlockState(origin).is(TreeModule.LOG_GREATWOOD.get()),
                    "Player's four Greatwood saplings inherited the natural Plains gate");
            helper.assertTrue(helper.getLevel().getBlockState(origin.below()).is(Blocks.DIRT)
                    && helper.getLevel().getBlockState(origin.below(2)).is(Blocks.DIRT),
                    "Growing player saplings generated a buried spider nest");
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(6, 9, 6)))
                helper.assertTrue(!helper.getLevel().getBlockState(pos).is(Blocks.COBWEB),
                        "Growing player saplings scattered spider webs");
            clearTree(helper, origin);
            helper.assertTrue(place(helper, "forest_silverwood", origin, new FirstFloatRandom(.999F)),
                    "The separate Magical Forest decorator inherited the global tree probability gate");
        }
        assertChoice(helper, new int[]{18}, new int[]{0}, MagicalForestTreesFeature.FOREST_SILVERWOOD);
        assertChoice(helper, new int[]{18, 12}, new int[]{1, 1}, MagicalForestTreesFeature.BIG_MAGIC_TREE);
        assertChoice(helper, new int[]{18, 12, 8}, new int[]{1, 0, 0}, TreeModule.SPIDER_GREATWOOD_TREE);
        assertChoice(helper, new int[]{18, 12, 8}, new int[]{1, 0, 1}, TreeModule.GREATWOOD_TREE);
        helper.succeed();
    }

    private static void assertChoice(GameTestHelper helper, int[] bounds, int[] values,
            ResourceKey<?> expected) {
        ChoiceRandom random = new ChoiceRandom(bounds, values);
        helper.assertTrue(MagicalForestTreesFeature.chooseTree(random).equals(expected)
                && random.index == bounds.length && random.floatCalls == 0,
                "Magical Forest's conditional 18/12/8 draws or outcome changed");
    }

    private static BlockPos origin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(19, 2, 19));
    }

    private static void prepareSoil(GameTestHelper helper, BlockPos origin) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = -2; y <= -1; y++)
            helper.getLevel().setBlock(origin.offset(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
    }

    private static void assertUnchangedSoil(GameTestHelper helper, BlockPos origin) {
        helper.assertTrue(helper.getLevel().getBlockState(origin).isAir()
                && helper.getLevel().getBlockState(origin.below()).is(Blocks.DIRT)
                && helper.getLevel().getBlockState(origin.below(2)).is(Blocks.DIRT),
                "Rejected natural tree changed the candidate or its ground");
    }

    private static void clearTree(GameTestHelper helper, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-14, 0, -14), origin.offset(14, 38, 14)))
            if (!helper.getLevel().getBlockState(pos).isAir())
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        prepareSoil(helper, origin);
    }

    private static boolean place(GameTestHelper helper, String name, BlockPos origin, RandomSource random) {
        var level = helper.getLevel();
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(id(name));
        helper.assertTrue(feature != null, "Missing registered configured feature " + name);
        return feature.place(level, level.getChunkSource().getGenerator(), random, origin);
    }

    private static void grow(GameTestHelper helper, BlockPos pos, SaplingBlock sapling) {
        for (int attempt = 0; attempt < 2 && helper.getLevel().getBlockState(pos).is(sapling); attempt++)
            sapling.advanceTree(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), new FirstFloatRandom(.999F));
    }

    /** Only the first float is scripted; the actual tree still receives seeded ordinary RNG. */
    private static class FirstFloatRandom extends LegacyRandomSource {
        private final float first;
        int floatCalls;
        int intCalls;
        FirstFloatRandom(float first) { super(127); this.first = first; }
        @Override public float nextFloat() {
            return floatCalls++ == 0 ? first : super.nextFloat();
        }
        @Override public int nextInt(int bound) { intCalls++; return super.nextInt(bound); }
    }

    private static final class ChoiceRandom extends FirstFloatRandom {
        private final int[] bounds;
        private final int[] values;
        private int index;
        ChoiceRandom(int[] bounds, int[] values) { super(.999F); this.bounds = bounds; this.values = values; }
        @Override public int nextInt(int bound) {
            if (index >= bounds.length || bound != bounds[index])
                throw new AssertionError("Unexpected Magical Forest RNG bound " + bound);
            return values[index++];
        }
    }

    /** Save and restore real loaded 3D chunk palettes, including fuzzy biome lookup neighbours. */
    private static final class BiomeFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<ChunkPalette> palettes = new ArrayList<>();
        BiomeFixture(GameTestHelper helper, BlockPos origin) {
            this.helper = helper;
            ChunkPos center = new ChunkPos(origin);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                LevelChunk chunk = helper.getLevel().getChunk(center.x + x, center.z + z);
                int minY = chunk.getMinBuildHeight() >> 2;
                List<Holder<Biome>> saved = new ArrayList<>();
                for (int y = minY; y < chunk.getMaxBuildHeight() >> 2; y++)
                    for (int qz = 0; qz < 4; qz++) for (int qx = 0; qx < 4; qx++)
                        saved.add(chunk.getNoiseBiome(chunk.getPos().x * 4 + qx, y, chunk.getPos().z * 4 + qz));
                palettes.add(new ChunkPalette(chunk, minY, List.copyOf(saved)));
            }
        }
        void use(ResourceKey<Biome> key) {
            Holder<Biome> holder = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
            for (ChunkPalette palette : palettes)
                palette.chunk.fillBiomesFromNoise((x, y, z, sampler) -> holder, Climate.empty());
        }
        @Override public void close() {
            for (ChunkPalette palette : palettes)
                palette.chunk.fillBiomesFromNoise((x, y, z, sampler) -> palette.saved.get(
                        (y - palette.minY) * 16 + Math.floorMod(z, 4) * 4 + Math.floorMod(x, 4)), Climate.empty());
        }
    }

    private record ChunkPalette(LevelChunk chunk, int minY, List<Holder<Biome>> saved) {}
}
