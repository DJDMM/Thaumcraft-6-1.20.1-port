package thaumcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.world.trees.TreeModule;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class SpiderGreatwoodGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }

    private static BlockPos prepareSoil(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(19, 2, 19));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = -2; y <= -1; y++)
            helper.getLevel().setBlockAndUpdate(origin.offset(x, y, z), Blocks.DIRT.defaultBlockState());
        return origin;
    }

    private static boolean placeSpiderTree(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(id("spider_greatwood_tree"));
        helper.assertTrue(feature != null, "Spider Greatwood configured feature is missing");
        return feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(127), origin);
    }

    private static String spawnEntity(CompoundTag tag) {
        return tag.getCompound("SpawnData").getCompound("entity").getString("id");
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void configuredSpiderGreatwoodCreatesNestAndPreservesItsBlockEntityData(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = prepareSoil(helper);
        helper.assertTrue(placeSpiderTree(helper, origin), "Spider Greatwood did not grow on clear soil");
        helper.assertTrue(level.getBlockState(origin).is(TreeModule.LOG_GREATWOOD.get()), "Nest was not placed below a real Greatwood");
        BlockPos spawnerPos = origin.below(), chestPos = origin.below(2);
        helper.assertTrue(level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity, "Missing buried cave-spider spawner");
        helper.assertTrue(level.getBlockEntity(chestPos) instanceof ChestBlockEntity, "Missing buried loot chest");
        var spawner = (SpawnerBlockEntity) level.getBlockEntity(spawnerPos);
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        CompoundTag spawnerSaved = spawner.saveWithFullMetadata();
        CompoundTag chestSaved = chest.saveWithFullMetadata();
        helper.assertTrue(spawnEntity(spawnerSaved).equals("minecraft:cave_spider"), "Nest spawner has the wrong entity type");
        helper.assertTrue(chestSaved.getString("LootTable").equals(BuiltInLootTables.SIMPLE_DUNGEON.toString()), "Chest has the wrong loot table");
        helper.assertTrue(chestSaved.contains("LootTableSeed", 4), "Chest did not receive a persistent loot seed");

        int webs = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(6, 9, 6))) {
            if (!level.getBlockState(pos).is(Blocks.COBWEB)) continue;
            webs++;
            boolean woodNeighbor = false;
            for (Direction direction : Direction.values()) {
                var state = level.getBlockState(pos.relative(direction));
                woodNeighbor |= state.is(TreeModule.LOG_GREATWOOD.get()) || state.is(TreeModule.LEAVES_GREATWOOD.get());
            }
            helper.assertTrue(woodNeighbor, "Nest web is not face-adjacent to Greatwood log/leaves");
        }
        helper.assertTrue(webs > 0 && webs <= 50, "Nest did not respect the fifty web attempts");

        // Recreate the actual level block entities, then load their saved data. This
        // catches lost entity IDs or unopened loot tables without unpacking the loot.
        level.setBlock(spawnerPos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(chestPos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        var restoredSpawner = (SpawnerBlockEntity) level.getBlockEntity(spawnerPos);
        var restoredChest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        helper.assertTrue(restoredSpawner != spawner && restoredChest != chest, "Save/load reused the old block entities");
        restoredSpawner.load(spawnerSaved);
        restoredChest.load(chestSaved);
        helper.assertTrue(spawnEntity(restoredSpawner.saveWithFullMetadata()).equals("minecraft:cave_spider"), "Spawner entity type changed on NBT reload");
        CompoundTag restoredLoot = restoredChest.saveWithFullMetadata();
        helper.assertTrue(restoredLoot.getString("LootTable").equals(chestSaved.getString("LootTable"))
                && restoredLoot.getLong("LootTableSeed") == chestSaved.getLong("LootTableSeed"), "Chest loot table or seed changed on NBT reload");
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void blockedSpiderGreatwoodLeavesGroundFoliageAndExistingBlockEntitiesUnchanged(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = prepareSoil(helper);
        BlockPos leafPos = origin.offset(5, 4, 5);
        var leaf = TreeModule.LEAVES_GREATWOOD.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        level.setBlockAndUpdate(leafPos, leaf);
        level.setBlockAndUpdate(origin.above(), Blocks.OBSIDIAN.defaultBlockState());
        BlockPos spawnerPos = origin.offset(4, -1, 4), chestPos = origin.below(2);
        level.setBlockAndUpdate(spawnerPos, Blocks.SPAWNER.defaultBlockState());
        var spawner = (SpawnerBlockEntity) level.getBlockEntity(spawnerPos);
        spawner.setEntityId(EntityType.SHEEP, RandomSource.create(17));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        CompoundTag spawnerSaved = spawner.saveWithFullMetadata(), chestSaved = chest.saveWithFullMetadata();

        helper.assertTrue(!placeSpiderTree(helper, origin), "Spider Greatwood grew through obsidian");
        helper.assertTrue(level.getBlockState(origin).isAir() && level.getBlockState(origin.below()).is(Blocks.DIRT), "Failed tree placed logs or a nest spawner");
        helper.assertTrue(level.getBlockState(origin.above()).is(Blocks.OBSIDIAN) && level.getBlockState(leafPos).equals(leaf), "Failed tree changed obstruction or existing leaves");
        helper.assertTrue(spawnerSaved.equals(level.getBlockEntity(spawnerPos).saveWithFullMetadata()), "Failed tree changed an existing spawner");
        helper.assertTrue(chestSaved.equals(level.getBlockEntity(chestPos).saveWithFullMetadata()), "Failed tree changed the buried inventory");
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(6, 9, 6)))
            helper.assertTrue(!level.getBlockState(pos).is(Blocks.COBWEB), "Failed tree scattered a web");
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void playerGrownGreatwoodDoesNotCreateASpiderNest(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = prepareSoil(helper);
        var sapling = (SaplingBlock) TreeModule.SAPLING_GREATWOOD.get();
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            level.setBlockAndUpdate(origin.offset(x, 0, z), sapling.defaultBlockState());
        for (int attempt = 0; attempt < 2 && level.getBlockState(origin).is(sapling); attempt++)
            sapling.advanceTree(level, origin, level.getBlockState(origin), RandomSource.create(127));
        helper.assertTrue(level.getBlockState(origin).is(TreeModule.LOG_GREATWOOD.get()), "Four player saplings did not produce Greatwood");
        helper.assertTrue(level.getBlockState(origin.below()).is(Blocks.DIRT) && level.getBlockState(origin.below(2)).is(Blocks.DIRT), "Player saplings created a buried spider nest");
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(6, 9, 6)))
            helper.assertTrue(!level.getBlockState(pos).is(Blocks.COBWEB), "Player saplings scattered spider webs");
        helper.succeed();
    }

    @GameTest(template = "trees", timeoutTicks = 100)
    public static void successfulSpiderTreePreservesUndergroundBedrock(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = prepareSoil(helper);
        level.setBlockAndUpdate(origin.below(2), Blocks.BEDROCK.defaultBlockState());
        helper.assertTrue(placeSpiderTree(helper, origin), "Underground bedrock prevented the clear Greatwood crown");
        helper.assertTrue(level.getBlockState(origin).is(TreeModule.LOG_GREATWOOD.get()), "Bedrock case did not produce the real tree");
        helper.assertTrue(level.getBlockState(origin.below(2)).is(Blocks.BEDROCK)
                && level.getBlockState(origin.below()).is(Blocks.DIRT), "Nest replaced bedrock or partly placed its spawner");
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-7, 0, -7), origin.offset(6, 9, 6)))
            helper.assertTrue(!level.getBlockState(pos).is(Blocks.COBWEB), "Skipped nest still scattered webs");
        helper.succeed();
    }
}
