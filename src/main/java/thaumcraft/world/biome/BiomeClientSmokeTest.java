package thaumcraft.world.biome;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.aura.AuraSavedData;
import thaumcraft.world.plants.PlantModule;
import thaumcraft.world.trees.TreeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in actual noise-world, renderer and disk save/reload validation. Never opens an existing user world. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class BiomeClientSmokeTest {
    private static final long SEED = 0x54433642494F4D45L;
    private static final String WORLD = "thaumcraft-biome-smoke-" + Long.toUnsignedString(System.currentTimeMillis());
    private static final String WORLD_TITLE = "Thaumcraft biome smoke " + WORLD;
    private static final List<String> BIOMES = List.of("magical_forest", "eerie", "eldritch");
    private static final AtomicInteger saved = new AtomicInteger();
    private static Phase phase = Phase.TITLE;
    private static long startedAt;
    private static int sceneIndex, sceneTicks, lightIndex;
    private static boolean reloaded, stopped, sceneCaptured;
    private static CompletableFuture<?> operation;
    private static Plan plan;
    private static IntegratedServer initialServer;
    private static long colourBaseline, distanceBaseline;
    private static Map<ChunkPos, AuraSnapshot> savedAuras;
    private static boolean optionsCaptured, previousPauseOnLostFocus, previousHideGui;
    private static int previousRenderDistance, previousSimulationDistance, previousBiomeBlendRadius;
    private static CloudStatus previousCloudStatus;

    private enum Phase { TITLE, JOIN, PREPARE, TELEPORT, VIEW, SAVE, REJOIN, VERIFY }
    private record Scene(String name, ResourceKey<Biome> biome, BlockPos view, BlockPos tintProbe, boolean natural) {}
    private record Plan(List<Scene> scenes, Map<ChunkPos, List<ResourceLocation>> palettes,
                        int trees, int customBlocks, int ambientGrass, int oakBranches, int tallOakTrunks, BlockPos parking) {}
    private record AuraSnapshot(int base, float vis, float flux) {}

    private BiomeClientSmokeTest() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
    private static ResourceKey<Biome> biome(String path) { return ResourceKey.create(Registries.BIOME, id(path)); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.biomeSmokeTest") || stopped) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (startedAt == 0) startedAt = System.nanoTime();
        if (System.nanoTime() - startedAt > 600_000_000_000L) {
            fail(minecraft, "Timed out during " + phase, null); return;
        }
        try {
            if (inWorld(minecraft) && minecraft.screen instanceof PauseScreen
                    && WORLD_TITLE.equals(minecraft.getSingleplayerServer().getWorldData().getLevelName()))
                minecraft.setScreen(null);
            switch (phase) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    require(minecraft.level == null && minecraft.player == null, "Smoke must start at the title screen");
                    require(!new File(new File(minecraft.gameDirectory, "saves"), WORLD).exists(), "Smoke world name already exists");
                    previousPauseOnLostFocus = minecraft.options.pauseOnLostFocus;
                    previousHideGui = minecraft.options.hideGui;
                    previousRenderDistance = minecraft.options.renderDistance().get();
                    previousSimulationDistance = minecraft.options.simulationDistance().get();
                    previousBiomeBlendRadius = minecraft.options.biomeBlendRadius().get();
                    previousCloudStatus = minecraft.options.cloudStatus().get();
                    optionsCaptured = true;
                    minecraft.options.pauseOnLostFocus = false;
                    minecraft.options.renderDistance().set(6);
                    minecraft.options.simulationDistance().set(5);
                    minecraft.options.biomeBlendRadius().set(2);
                    minecraft.options.cloudStatus().set(CloudStatus.OFF);
                    minecraft.options.hideGui = true;
                    GameRules rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                    rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                    LevelSettings settings = new LevelSettings(WORLD_TITLE, GameType.CREATIVE, false,
                            Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                    phase = Phase.JOIN;
                    LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_WORLD: {} seed={} preset=minecraft:normal", WORLD, SEED);
                    minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, false, false),
                            WorldPresets::createNormalWorldDimensions);
                }
                case JOIN -> {
                    if (!inWorld(minecraft)) return;
                    initialServer = minecraft.getSingleplayerServer();
                    operation = initialServer.submit(() -> prepare(initialServer));
                    phase = Phase.PREPARE;
                }
                case PREPARE -> {
                    if (!operation.isDone()) return;
                    plan = (Plan) operation.join();
                    sceneIndex = 0;
                    teleport(minecraft);
                }
                case TELEPORT -> {
                    if (!operation.isDone()) return;
                    operation.join();
                    var observations = BiomeClientEvents.observation();
                    colourBaseline = observations.colourFrames(); distanceBaseline = observations.distanceFrames();
                    sceneTicks = 0; sceneCaptured = false;
                    phase = Phase.VIEW;
                }
                case VIEW -> view(minecraft);
                case SAVE -> {
                    if (!operation.isDone()) return;
                    operation.join();
                    minecraft.getConnection().getConnection().disconnect(Component.literal("Biome smoke save/reload"));
                    minecraft.clearLevel(new TitleScreen());
                    reloaded = true;
                    phase = Phase.REJOIN;
                }
                case REJOIN -> {
                    if (minecraft.level == null && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                        if (!initialServer.isStopped()) return;
                        savedAuras = readSavedAuras(minecraft);
                        phase = Phase.VERIFY;
                        operation = null;
                        minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
                    }
                }
                case VERIFY -> {
                    // This confirmation belongs exclusively to the new smoke world's experimental registry data.
                    if (minecraft.screen instanceof ConfirmScreen confirmation) {
                        for (var child : confirmation.children()) if (child instanceof Button button
                                && (button.getMessage().equals(CommonComponents.GUI_PROCEED)
                                || button.getMessage().equals(CommonComponents.GUI_YES))) {
                            button.onPress(); return;
                        }
                    }
                    if (!inWorld(minecraft)) return;
                    if (operation == null) {
                        IntegratedServer server = minecraft.getSingleplayerServer();
                        require(server != initialServer, "Reload reused the original integrated server");
                        initialServer = null;
                        operation = server.submit(() -> verifyReload(server));
                    }
                    if (!operation.isDone()) return;
                    operation.join();
                    sceneIndex = 0;
                    teleport(minecraft);
                }
            }
        } catch (RuntimeException | AssertionError failure) { fail(minecraft, "Biome smoke failed during " + phase, failure); }
    }

    private static boolean inWorld(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null
                && minecraft.getConnection() != null && minecraft.getOverlay() == null;
    }

    private static Plan prepare(IntegratedServer server) {
        ServerLevel level = server.overworld();
        require(level.getSeed() == SEED, "Wrong smoke seed");
        require(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator, "Normal preset did not create a noise generator");
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        require(generator.getBiomeSource().possibleBiomes().stream().anyMatch(holder -> holder.is(biome("magical_forest"))),
                "TerraBlender did not inject Magical Forest into the actual normal biome source");
        require(generator.getBiomeSource().possibleBiomes().stream().noneMatch(holder -> holder.is(biome("eerie")) || holder.is(biome("eldritch"))),
                "Conversion-only biomes entered normal natural generation");
        var found = generator.getBiomeSource().findBiomeHorizontal(0, 64, 0, 8192, 64,
                holder -> holder.is(biome("magical_forest")), RandomSource.create(SEED), true,
                level.getChunkSource().randomState().sampler());
        require(found != null, "Magical Forest was not found within 8192 blocks in actual normal noise generation");
        ChunkPos naturalChunk = new ChunkPos(found.getFirst());
        int[] trees = {0}, custom = {0}, ambientGrass = {0}, oakBranches = {0}, tallOakTrunks = {0};
        Map<ChunkPos, List<ResourceLocation>> palettes = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            LevelChunk chunk = level.getChunk(naturalChunk.x + x, naturalChunk.z + z);
            require(chunk.getStatus().isOrAfter(net.minecraft.world.level.chunk.ChunkStatus.FULL), "Natural chunk did not reach FULL");
            chunk.findBlocks(state -> state.is(BlockTags.LOGS) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("thaumcraft"),
                    (pos, state) -> {
                        // Count decoration only in actual Magical Forest surface cells, never neighboring vanilla woods or ores.
                        if (pos.getY() < 50 || !level.getBiome(pos).is(BiomeModule.MAGICAL_FOREST)) return;
                        if (state.is(BlockTags.LOGS)) trees[0]++;
                        if (state.is(PlantModule.GRASS_AMBIENT.get())) { ambientGrass[0]++; custom[0]++; }
                        else if (state.is(PlantModule.VISHROOM.get()) || state.is(PlantModule.SHIMMERLEAF.get())
                                || state.is(TreeModule.LOG_GREATWOOD.get()) || state.is(TreeModule.LOG_SILVERWOOD.get())
                                || state.is(TreeModule.LEAVES_GREATWOOD.get()) || state.is(TreeModule.LEAVES_SILVERWOOD.get())) custom[0]++;
                        if (state.is(Blocks.OAK_LOG)) {
                            if (state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) oakBranches[0]++;
                            if (!level.getBlockState(pos.below()).is(Blocks.OAK_LOG)) {
                                int height = 0;
                                while (height < 24 && level.getBlockState(pos.above(height)).is(Blocks.OAK_LOG)) height++;
                                if (height >= 8) tallOakTrunks[0]++;
                            }
                        }
                    });
            palettes.put(chunk.getPos(), palette(chunk));
        }
        LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_DECORATION: onlyMagicalForest FULL chunks=25 treeBlocks={} customSurfaceBlocks={} ambientGrass={} oakBranches={} tallOakTrunks={}",
                trees[0], custom[0], ambientGrass[0], oakBranches[0], tallOakTrunks[0]);
        require(trees[0] > 10 && tallOakTrunks[0] > 0 && oakBranches[0] > 0,
                "Natural Magical Forest lacks tall branched oak decoration");
        require(ambientGrass[0] > 0 && custom[0] > 0,
                "Natural Magical Forest lacks its real ambient-grass/custom surface decoration");
        BlockPos naturalSurface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getFirst());
        require(level.getBiome(naturalSurface).is(biome("magical_forest")), "Located noise biome was absent in generated surface palette");
        List<Scene> scenes = new ArrayList<>();
        scenes.add(new Scene("natural-magical-forest", biome("magical_forest"), naturalSurface.above(7), naturalSurface.below(), true));
        for (int index = 0; index < BIOMES.size(); index++) {
            String name = BIOMES.get(index);
            Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome(name));
            int x = naturalChunk.getMinBlockX() + 1024 + index * 160 + 8;
            int z = naturalChunk.getMinBlockZ() + 8;
            ChunkPos centre = new ChunkPos(new BlockPos(x, 192, z));
            List<ChunkAccess> changed = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = level.getChunk(centre.x + dx, centre.z + dz);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> holder, Climate.empty());
                chunk.setUnsaved(true);
                changed.add(chunk);
                palettes.put(chunk.getPos(), palette(chunk));
            }
            buildTintGarden(level, new BlockPos(x, 192, z));
            level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
            BlockPos view = new BlockPos(x, 195, z + 18);
            scenes.add(new Scene(name, biome(name), view, new BlockPos(x, 191, z), false));
            palettes.put(centre, palette(level.getChunk(centre.x, centre.z)));
            // Capture the camera's adjacent chunk as well: the view is deliberately off the centre line.
            ChunkPos cameraChunk = new ChunkPos(view);
            palettes.put(cameraChunk, palette(level.getChunk(cameraChunk.x, cameraChunk.z)));
            float expected = name.equals("eldritch") ? (0.75F + 0.5F + 0.125F) / 3.0F : 0.625F;
            require(Math.abs(AuraManager.biomeModifier(holder) - expected) < 0.000001F, "Wrong custom aura coefficient for " + name);
        }
        level.setDayTime(1000);
        level.setWeatherParameters(0, 6000, false, false);
        LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_NATURAL: position={} FULL chunks=25 onlyMagicalForest treeBlocks={} customSurfaceBlocks={} ambientGrass={} oakBranches={} tallOakTrunks={}",
                naturalSurface, trees[0], custom[0], ambientGrass[0], oakBranches[0], tallOakTrunks[0]);
        BlockPos parking = new BlockPos(naturalChunk.getMinBlockX() + 8192, 240, naturalChunk.getMinBlockZ() + 8192);
        return new Plan(List.copyOf(scenes), Map.copyOf(palettes), trees[0], custom[0], ambientGrass[0],
                oakBranches[0], tallOakTrunks[0], parking);
    }

    private static void buildTintGarden(ServerLevel level, BlockPos base) {
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
            level.setBlock(base.offset(x, -2, z), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int y = 0; y < 15; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        for (int x = -9; x <= -3; x++) for (int z = 3; z <= 9; z++)
            level.setBlock(base.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 2);
        for (int x : new int[]{-13, 12}) {
            for (int y = 0; y < 5; y++) level.setBlock(base.offset(x, y, -7), Blocks.OAK_LOG.defaultBlockState(), 2);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 3; y <= 5; y++)
                if (Math.abs(dx) + Math.abs(dz) < 4)
                    level.setBlock(base.offset(x + dx, y, -7 + dz), Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
        }
        for (int x = -18; x <= 18; x += 6) for (int z = -14; z <= 12; z += 6)
            level.setBlock(base.offset(x, 0, z), Blocks.GRASS.defaultBlockState(), 2);
        // Explicit renderer fixtures, including neutral Silverwood foliage and separately planted flora.
        for (int wood = 0; wood < 2; wood++) {
            int x = wood == 0 ? -6 : 5;
            var log = wood == 0 ? TreeModule.LOG_GREATWOOD.get() : TreeModule.LOG_SILVERWOOD.get();
            var leaves = wood == 0 ? TreeModule.LEAVES_GREATWOOD.get() : TreeModule.LEAVES_SILVERWOOD.get();
            for (int y = 0; y < 4; y++) level.setBlock(base.offset(x, y, -9), log.defaultBlockState(), 2);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 3; y <= 5; y++)
                if (Math.abs(dx) + Math.abs(dz) < 4) level.setBlock(base.offset(x + dx, y, -9 + dz),
                        leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
        }
        for (int x = -17; x <= -11; x += 2) for (int z = 5; z <= 11; z += 2) {
            level.setBlock(base.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(base.offset(x, 0, z), PlantModule.SHIMMERLEAF.get().defaultBlockState(), 2);
        }
        for (int x = 11; x <= 17; x += 2) for (int z = 5; z <= 11; z += 2) {
            level.setBlock(base.offset(x, -1, z), Blocks.SAND.defaultBlockState(), 2);
            level.setBlock(base.offset(x, 0, z), PlantModule.CINDERPEARL.get().defaultBlockState(), 2);
        }
        for (int x = -2; x <= 2; x += 2) for (int z = 8; z <= 12; z += 2) {
            level.setBlock(base.offset(x, -1, z), PlantModule.GRASS_AMBIENT.get().defaultBlockState(), 2);
            level.setBlock(base.offset(x, 0, z), PlantModule.VISHROOM.get().defaultBlockState(), 2);
        }
    }

    private static List<ResourceLocation> palette(ChunkAccess chunk) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int y = chunk.getMinBuildHeight() >> 2; y < chunk.getMaxBuildHeight() >> 2; y++)
            for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                result.add(chunk.getNoiseBiome(chunk.getPos().x * 4 + x, y, chunk.getPos().z * 4 + z)
                        .unwrapKey().orElseThrow().location());
        return List.copyOf(result);
    }

    private static boolean verifyReload(IntegratedServer server) {
        ServerLevel level = server.overworld();
        require(level.getSeed() == SEED && level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
                "Reload lost normal noise settings or seed");
        // Read the restored store before loading the distant fixture chunks, so ticking cannot alter the comparison.
        AuraSavedData restoredAuras = AuraSavedData.get(level);
        for (var entry : savedAuras.entrySet()) {
            var actual = restoredAuras.getChunk(entry.getKey());
            AuraSnapshot expected = entry.getValue();
            require(actual != null && actual.getBase() == expected.base()
                    && Float.compare(actual.getVis(), expected.vis()) == 0
                    && Float.compare(actual.getFlux(), expected.flux()) == 0,
                    "Aura base/vis/flux changed on fresh-server disk reload: " + entry.getKey());
        }
        for (var entry : plan.palettes().entrySet()) {
            ChunkPos pos = entry.getKey();
            require(entry.getValue().equals(palette(level.getChunk(pos.x, pos.z))), "Biome palette changed on disk reload: " + pos);
        }
        for (Scene scene : plan.scenes()) {
            require(level.getBiome(scene.view()).is(scene.biome()), "Saved camera biome changed: " + scene.name());
            if (!scene.natural()) require(level.getBlockState(scene.tintProbe()).is(Blocks.GRASS_BLOCK), "Saved tint garden terrain was lost");
        }
        LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_RELOAD: fresh integrated server; {} complete chunk palettes and {} aura base/vis/flux records unchanged",
                plan.palettes().size(), savedAuras.size());
        return true;
    }

    private static Map<ChunkPos, AuraSnapshot> readSavedAuras(Minecraft minecraft) {
        File file = new File(new File(new File(new File(minecraft.gameDirectory, "saves"), WORLD), "data"), "thaumcraft_aura.dat");
        try {
            AuraSavedData disk = AuraSavedData.load(NbtIo.readCompressed(file).getCompound("data"));
            Map<ChunkPos, AuraSnapshot> result = new LinkedHashMap<>();
            for (ChunkPos pos : plan.palettes().keySet()) {
                var aura = disk.getChunk(pos);
                require(aura != null, "Own smoke save lacks aura for tested chunk: " + pos);
                result.put(pos, new AuraSnapshot(aura.getBase(), aura.getVis(), aura.getFlux()));
            }
            return Map.copyOf(result);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read aura from own newly created smoke world", failure); }
    }

    private static void teleport(Minecraft minecraft) {
        Scene scene = plan.scenes().get(sceneIndex);
        IntegratedServer server = minecraft.getSingleplayerServer();
        operation = server.submit(() -> {
            var player = server.getPlayerList().getPlayers().stream().findFirst().orElseThrow();
            player.setGameMode(GameType.SPECTATOR);
            server.overworld().setDayTime(lightIndex == 0 ? 6000 : 18000);
            BlockPos pos = scene.view();
            player.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0F, scene.natural() ? 24.0F : 14.0F);
            return true;
        });
        phase = Phase.TELEPORT;
    }

    private static void view(Minecraft minecraft) {
        Scene scene = plan.scenes().get(sceneIndex);
        if (!inWorld(minecraft) || !minecraft.level.isLoaded(scene.view())
                || minecraft.player.position().distanceToSqr(scene.view().getX() + 0.5, scene.view().getY(), scene.view().getZ() + 0.5) > 4
                || !minecraft.level.getBiome(scene.view()).is(scene.biome())) { sceneTicks = 0; return; }
        if (++sceneTicks < 80) return;
        if (!sceneCaptured) {
            var fog = BiomeClientEvents.observation();
            require(fog.colourFrames() > colourBaseline && fog.distanceFrames() > distanceBaseline && fog.finite(), "Renderer did not produce valid biome fog");
            require(scene.biome().location().equals(fog.colourBiome()) && scene.biome().location().equals(fog.distanceBiome()), "Fog came from the wrong camera biome");
            Biome value = minecraft.level.getBiome(scene.tintProbe()).value();
            int grass = minecraft.getBlockColors().getColor(Blocks.GRASS_BLOCK.defaultBlockState(), minecraft.level, scene.tintProbe(), 0);
            int foliage = minecraft.getBlockColors().getColor(Blocks.OAK_LEAVES.defaultBlockState(), minecraft.level, scene.tintProbe(), 0);
            int greatwoodFoliage = minecraft.getBlockColors().getColor(TreeModule.LEAVES_GREATWOOD.get().defaultBlockState(),
                    minecraft.level, scene.tintProbe(), 0);
            int silverwoodFoliage = minecraft.getBlockColors().getColor(TreeModule.LEAVES_SILVERWOOD.get().defaultBlockState(),
                    minecraft.level, scene.tintProbe(), 0);
            int water = BiomeColors.getAverageWaterColor(minecraft.level, scene.tintProbe());
            int expectedGrass = value.getGrassColor(scene.tintProbe().getX(), scene.tintProbe().getZ());
            int expectedFoliage = value.getFoliageColor();
            int averageGrass = BiomeColors.getAverageGrassColor(minecraft.level, scene.tintProbe());
            int averageFoliage = BiomeColors.getAverageFoliageColor(minecraft.level, scene.tintProbe());
            int freshGrass = minecraft.level.calculateBlockTint(scene.tintProbe(), BiomeColors.GRASS_COLOR_RESOLVER);
            int freshFoliage = minecraft.level.calculateBlockTint(scene.tintProbe(), BiomeColors.FOLIAGE_COLOR_RESOLVER);
            LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_TINT: scene={} cameraBiome={} probeBiome={} rendererGrass={} biomeGrassRaw={} biomeGrassRGB={} averageGrass={} freshGrass={} rendererFoliage={} biomeFoliageRaw={} biomeFoliageRGB={} averageFoliage={} freshFoliage={} blendRadius={}",
                    scene.name(), minecraft.level.getBiome(scene.view()).unwrapKey().orElseThrow().location(),
                    minecraft.level.getBiome(scene.tintProbe()).unwrapKey().orElseThrow().location(), grass,
                    expectedGrass, expectedGrass & 0xFFFFFF, averageGrass, freshGrass, foliage,
                    expectedFoliage, expectedFoliage & 0xFFFFFF, averageFoliage, freshFoliage,
                    minecraft.options.biomeBlendRadius().get());
            require((grass & 0xFFFFFF) == (averageGrass & 0xFFFFFF)
                    && (foliage & 0xFFFFFF) == (averageFoliage & 0xFFFFFF), "Block renderer ignores actual biome blending");
            require((averageGrass & 0xFFFFFF) == (freshGrass & 0xFFFFFF)
                    && (averageFoliage & 0xFFFFFF) == (freshFoliage & 0xFFFFFF), "Biome tint cache differs from fresh palette sampling");
            require((greatwoodFoliage & 0xFFFFFF) == BiomeColors.getAverageFoliageColor(minecraft.level, scene.tintProbe()),
                    "Greatwood renderer ignores biome foliage tint");
            require((silverwoodFoliage & 0xFFFFFF) == 0xFFFFFF, "Silverwood renderer no longer retains its neutral BETA26 tint");
            if (!scene.natural()) {
                // Colormap pixels preserve ARGB alpha; the blended renderer returns RGB. Compare every RGB bit.
                require((grass & 0xFFFFFF) == (expectedGrass & 0xFFFFFF), "Grass renderer ignores the saved biome tint");
                require((foliage & 0xFFFFFF) == (expectedFoliage & 0xFFFFFF), "Foliage renderer ignores the saved biome tint");
                require(water == value.getWaterColor(), "Water renderer ignores the saved biome tint");
            }
            String filename = "thaumcraft-biome-" + scene.name() + (lightIndex == 0 ? "-day" : "-night")
                    + (reloaded ? "-reloaded-" : "-generated-") + WORLD + ".png";
            LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_RENDER: {} grass={} oakFoliage={} greatwoodFoliage={} silverwoodFoliage={} water={} sky={} fogRGB={},{},{} near={} far={}",
                    filename, grass & 0xFFFFFF, foliage & 0xFFFFFF, greatwoodFoliage & 0xFFFFFF, silverwoodFoliage & 0xFFFFFF,
                    water, value.getSkyColor(), fog.red(), fog.green(), fog.blue(), fog.near(), fog.far());
            capture(minecraft, filename);
            sceneCaptured = true;
        }
        if (saved.get() < (reloaded ? 8 : 0) + sceneIndex * 2 + lightIndex + 1) return;
        if (lightIndex == 0) { lightIndex = 1; teleport(minecraft); return; }
        lightIndex = 0;
        if (++sceneIndex < plan.scenes().size()) { teleport(minecraft); return; }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (!reloaded) {
            operation = server.submit(() -> {
                // Reload at a remote parking point, keeping every compared aura chunk inactive until verification.
                var player = server.getPlayerList().getPlayers().stream().findFirst().orElseThrow();
                BlockPos parking = plan.parking();
                player.teleportTo(server.overworld(), parking.getX() + .5, parking.getY(), parking.getZ() + .5, 0, 0);
                server.saveEverything(true, true, true);
                return true;
            });
            phase = Phase.SAVE;
        } else {
            require(saved.get() == 16, "Day/night and generated/reloaded screenshot matrix was incomplete");
            stopped = true;
            LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_OK: {} day/night screenshots saved; minecraft:normal actual noise world; naturally generated Magical Forest treeBlocks={} customSurfaceBlocks={} ambientGrass={} oakBranches={} tallOakTrunks={}; {} complete three-dimensional chunk palettes and aura records saved/reloaded; actual grass/oak/greatwood/silverwood/water/fog rendering; world={}",
                    saved.get(), plan.trees(), plan.customBlocks(), plan.ambientGrass(), plan.oakBranches(), plan.tallOakTrunks(),
                    plan.palettes().size(), WORLD);
            minecraft.getConnection().getConnection().disconnect(Component.literal("Biome smoke complete"));
            minecraft.clearLevel(new TitleScreen());
            restoreOptions(minecraft);
            minecraft.stop();
        }
    }

    private static void capture(Minecraft minecraft, String filename) {
        File output = new File(new File(minecraft.gameDirectory, "screenshots"), filename);
        try { Files.deleteIfExists(output.toPath()); }
        catch (IOException failure) { throw new IllegalStateException("Cannot replace own smoke screenshot " + filename, failure); }
        Screenshot.grab(minecraft.gameDirectory, filename, minecraft.getMainRenderTarget(), message -> {
            if (output.isFile() && output.length() > 0) {
                int count = saved.incrementAndGet();
                LogUtils.getLogger().info("THAUMCRAFT_BIOME_CLIENT_SMOKE_IMAGE: {} ({}/16)", filename, count);
            } else minecraft.execute(() -> fail(minecraft, "Screenshot not written: " + filename, null));
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void restoreOptions(Minecraft minecraft) {
        if (!optionsCaptured) return;
        minecraft.options.pauseOnLostFocus = previousPauseOnLostFocus;
        minecraft.options.hideGui = previousHideGui;
        minecraft.options.renderDistance().set(previousRenderDistance);
        minecraft.options.simulationDistance().set(previousSimulationDistance);
        minecraft.options.biomeBlendRadius().set(previousBiomeBlendRadius);
        minecraft.options.cloudStatus().set(previousCloudStatus);
        optionsCaptured = false;
    }
    private static void fail(Minecraft minecraft, String message, Throwable failure) {
        if (stopped) return;
        stopped = true;
        LogUtils.getLogger().error("THAUMCRAFT_BIOME_CLIENT_SMOKE_FAILED: " + message, failure);
        restoreOptions(minecraft);
        minecraft.stop();
    }
}
