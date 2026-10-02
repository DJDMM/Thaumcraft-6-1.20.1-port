package thaumcraft.world.trees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** BETA26's cave-spider spawner, fifty wood-adjacent web attempts and buried dungeon chest. */
public final class GreatwoodSpiderNest {
    private GreatwoodSpiderNest() {}

    /** Called only after successful placement of a spider-variant Greatwood. */
    public static void place(WorldGenLevel level, BlockPos origin, RandomSource random) {
        BlockPos spawnerPos = origin.below();
        BlockPos chestPos = origin.below(2);
        // Modern worldgen must stay within its writable region. Unlike the old generator,
        // it also leaves existing inventories and unbreakable blocks below the tree intact.
        if (!canReplaceGround(level, spawnerPos) || !canReplaceGround(level, chestPos)) return;
        if (!level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 2)) return;
        if (!(level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner)) return;
        spawner.setEntityId(EntityType.CAVE_SPIDER, random);
        spawner.setChanged();

        for (int attempt = 0; attempt < 50; attempt++) {
            int x = origin.getX() - 7 + random.nextInt(14);
            int y = origin.getY() + random.nextInt(10);
            int z = origin.getZ() - 7 + random.nextInt(14);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isOutsideBuildHeight(pos) && level.ensureCanWrite(pos)
                    && level.isEmptyBlock(pos) && touchesGreatwood(level, pos))
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
        }
        if (level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2)
                && level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, random.nextLong());
            chest.setChanged();
        }
    }

    private static boolean canReplaceGround(WorldGenLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.ensureCanWrite(pos)) return false;
        var state = level.getBlockState(pos);
        return !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0;
    }

    private static boolean touchesGreatwood(WorldGenLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            var state = level.getBlockState(pos.relative(direction));
            if (state.is(TreeModule.LOG_GREATWOOD.get()) || state.is(TreeModule.LEAVES_GREATWOOD.get())) return true;
        }
        return false;
    }
}
