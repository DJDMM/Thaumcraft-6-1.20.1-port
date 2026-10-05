package thaumcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraChunk;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.aura.AuraSavedData;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class WorldGameTests {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", path);
    }

    @GameTest(template = "empty")
    public static void auraPersistsAcrossSaveLoad(GameTestHelper helper) {
        AuraSavedData initial = new AuraSavedData();
        initial.getOrCreate(new ChunkPos(-23, 41), 287);
        initial.getOrCreate(new ChunkPos(23, -41), 156);
        CompoundTag tag = initial.save(new CompoundTag());
        var entries = tag.getList("Chunks", 10);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (entry.getInt("X") == -23) {
                entry.putFloat("Vis", 83.125F);
                entry.putFloat("Flux", 42.75F);
            }
        }
        AuraSavedData restored = AuraSavedData.load(tag);
        AuraChunk aura = restored.getChunk(new ChunkPos(-23, 41));
        helper.assertTrue(aura != null && aura.getBase() == 287, "Aura base or signed chunk coordinates lost");
        helper.assertTrue(aura.getVis() == 83.125F && aura.getFlux() == 42.75F, "Aura amounts lost on reload");
        helper.assertTrue(restored.getChunk(new ChunkPos(23, -41)).getBase() == 156, "Chunk keys collided");
        helper.assertTrue(restored.getOrCreate(new ChunkPos(-23, 41), 500) == aura, "Chunk reload regenerated existing aura");
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("Base", -1);
        malformed.putFloat("Vis", Float.NaN);
        malformed.putFloat("Flux", -99);
        AuraChunk clamped = AuraChunk.load(malformed);
        helper.assertTrue(clamped.getBase() == 0 && clamped.getVis() == 0 && clamped.getFlux() == 0, "Corrupt aura was not clamped");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void auraDrainIsBoundedAndSimulationIsReadOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        float originalVis = AuraManager.getVis(level, pos);
        float originalFlux = AuraManager.getFlux(level, pos);
        try {
            AuraManager.drainVis(level, pos, Float.MAX_VALUE, false);
            AuraManager.drainFlux(level, pos, Float.MAX_VALUE, false);
            AuraManager.addVis(level, pos, 10);
            AuraManager.addFlux(level, pos, 4);
            helper.assertTrue(AuraManager.drainVis(level, pos, 40, true) == 10, "Simulation does not cap to available vis");
            helper.assertTrue(AuraManager.getVis(level, pos) == 10, "Simulation consumed vis");
            helper.assertTrue(AuraManager.drainVis(level, pos, 7, false) == 7, "Requested vis not drained");
            helper.assertTrue(AuraManager.drainVis(level, pos, 20, false) == 3, "Vis overdraft was allowed");
            helper.assertTrue(AuraManager.drainFlux(level, pos, 20, true) == 4 && AuraManager.getFlux(level, pos) == 4, "Flux simulation mutated aura");
            AuraManager.addVis(level, pos, Float.NaN);
            AuraManager.addFlux(level, pos, -10);
            helper.assertTrue(AuraManager.getVis(level, pos) == 0 && AuraManager.getFlux(level, pos) == 4, "Invalid aura input changed state");
            AuraSavedData restored = AuraSavedData.load(AuraSavedData.get(level).save(new CompoundTag()));
            helper.assertTrue(restored.getChunk(new ChunkPos(pos)).getFlux() == 4, "Manager updates are not persisted");
        } finally {
            AuraManager.drainVis(level, pos, Float.MAX_VALUE, false);
            AuraManager.drainFlux(level, pos, Float.MAX_VALUE, false);
            AuraManager.addVis(level, pos, originalVis);
            AuraManager.addFlux(level, pos, originalFlux);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lunarRegenerationRespectsFluxAndMoon(GameTestHelper helper) {
        AuraChunk clean = new AuraChunk(200, 100, 0);
        AuraManager.regenerate(clean, 0, 0.5F);
        helper.assertTrue(clean.getVis() == 100.25F, "Full moon regeneration differs from TC6");
        AuraChunk polluted = new AuraChunk(200, 100, 200);
        AuraManager.regenerate(polluted, 0, 0.5F);
        helper.assertTrue(polluted.getVis() == 100, "Flux did not block regeneration above capacity");
        AuraChunk newMoon = new AuraChunk(200, 100, 0);
        AuraManager.regenerate(newMoon, 4, 0.5F);
        helper.assertTrue(newMoon.getVis() == 100, "New moon should not regenerate vis");
        AuraChunk oversaturated = new AuraChunk(200, 300, 0);
        AuraManager.regenerate(oversaturated, 4, 0.0F);
        helper.assertTrue(oversaturated.getVis() == 299.75F && oversaturated.getFlux() == 0.25F, "Excess vis does not become flux");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void worldOresHaveLootAndPlacedFeatures(GameTestHelper helper) {
        var level = helper.getLevel();
        var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPick.enchant(Enchantments.SILK_TOUCH, 1);
        for (var ore : new net.minecraft.world.level.block.Block[]{WorldModule.ORE_AMBER.get(), WorldModule.ORE_CINNABAR.get(), WorldModule.ORE_QUARTZ.get()}) {
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            level.setBlockAndUpdate(pos, ore.defaultBlockState());
            helper.assertTrue(ore.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "Ore lacks mining tags");
            helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ore.defaultBlockState()), "Ore cannot be mined");
            var drops = Block.getDrops(ore.defaultBlockState(), level, pos, null, null, silkPick);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(ore.asItem()), "Silk touch does not return ore");
        }
        for (String name : new String[]{"ore_amber", "ore_cinnabar", "ore_quartz", "primal_crystals"}) {
            helper.assertTrue(configured.containsKey(id(name)) && placed.containsKey(id(name)), "Missing worldgen feature: " + name);
            var plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).value();
            boolean injected = plains.getGenerationSettings().features().stream().flatMap(set -> set.stream())
                    .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(id(name))).orElse(false));
            helper.assertTrue(injected, "Biome modifier did not inject " + name);
        }
        BlockPos quartzPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(quartzPos, Blocks.STONE.defaultBlockState());
        helper.assertTrue(configured.get(id("ore_quartz")).place(level, level.getChunkSource().getGenerator(), RandomSource.create(17), quartzPos), "Ore feature did not replace stone");
        helper.assertTrue(level.getBlockState(quartzPos).is(WorldModule.ORE_QUARTZ.get()), "Wrong block generated");
        var quartzDrops = Block.getDrops(level.getBlockState(quartzPos), level, quartzPos, null, null, new ItemStack(Items.IRON_PICKAXE));
        // Since 0.20 this original ore also has its audited non-Silk 5% rare-earth bonus.
        var rareEarth=thaumcraft.catalog.CatalogModule.stack("nugget_rareearth");
        helper.assertTrue(quartzDrops.stream().filter(s->s.is(Items.QUARTZ)).mapToInt(ItemStack::getCount).sum()==1
                && quartzDrops.stream().filter(s->s.is(rareEarth.getItem())).mapToInt(ItemStack::getCount).sum()<=1
                && quartzDrops.stream().allMatch(s->s.is(Items.QUARTZ)||s.is(rareEarth.getItem())),
                "Quartz ore lost its one primary quartz or produced an unrelated/duplicate bonus: "+quartzDrops);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void primalCrystalsGenerateAndDropVis(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            level.setBlockAndUpdate(origin.offset(x, -2, z), Blocks.STONE.defaultBlockState());
            for (int y = -1; y <= 1; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(id("primal_crystals"));
        helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(9), origin), "Crystal feature generated nothing beside exposed stone");
        for (String aspect : WorldModule.CRYSTALS.keySet()) {
            Block crystal = WorldModule.CRYSTALS.get(aspect).get();
            var drops = Block.getDrops(crystal.defaultBlockState(), level, origin, null, null, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(WorldModule.VIS_CRYSTALS.get(aspect).get())
                    && drops.get(0).getCount() >= 1 && drops.get(0).getCount() <= 3, "Incorrect primal crystal drop: " + aspect);
        }
        helper.succeed();
    }
}
