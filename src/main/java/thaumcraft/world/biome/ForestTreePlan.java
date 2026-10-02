package thaumcraft.world.biome;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A bounded, per-placement plan; no mutable tree generator state is shared between chunks. */
final class ForestTreePlan {
    private final WorldGenLevel level;
    private final Block log;
    private final Block leaves;
    private final Map<BlockPos, BlockState> logs = new LinkedHashMap<>();
    private final Set<BlockPos> foliage = new LinkedHashSet<>();

    ForestTreePlan(WorldGenLevel level, Block log, Block leaves) {
        this.level = level;
        this.log = log;
        this.leaves = leaves;
    }
    boolean soil(BlockPos pos) { return Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos); }
    boolean replaceable(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }
    void log(BlockPos pos) { log(pos, Direction.Axis.Y); }
    void log(BlockPos pos, Direction.Axis axis) {
        logs.put(pos.immutable(), log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
    }
    void leaf(BlockPos pos) { if (replaceable(pos)) foliage.add(pos.immutable()); }
    private int length(BlockPos from, BlockPos to) {
        return Math.max(Math.abs(to.getY() - from.getY()),
                Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ())));
    }
    private BlockPos linePosition(BlockPos from, BlockPos to, int step, int length) {
        if (length == 0) return from;
        return BlockPos.containing(from.getX() + .5 + (to.getX() - from.getX()) * (float) step / length,
                from.getY() + .5 + (to.getY() - from.getY()) * (float) step / length,
                from.getZ() + .5 + (to.getZ() - from.getZ()) * (float) step / length);
    }
    int obstruction(BlockPos from, BlockPos to) {
        int length = length(from, to);
        for (int step = 0; step <= length; step++)
            if (!replaceable(linePosition(from, to, step, length))) return step;
        return -1;
    }
    boolean lineClear(BlockPos from, BlockPos to) { return obstruction(from, to) == -1; }
    void branch(BlockPos from, BlockPos to) {
        int length = length(from, to);
        for (int step = 0; step <= length; step++) {
            BlockPos pos = linePosition(from, to, step, length);
            int dx = Math.abs(pos.getX() - from.getX()), dz = Math.abs(pos.getZ() - from.getZ());
            Direction.Axis axis = Math.max(dx, dz) == 0 ? Direction.Axis.Y
                    : dx >= dz ? Direction.Axis.X : Direction.Axis.Z;
            log(pos, axis);
        }
    }
    boolean place() {
        if (logs.isEmpty() || logs.keySet().stream().anyMatch(pos -> !replaceable(pos))) return false;
        foliage.removeAll(logs.keySet());
        Map<BlockPos, Integer> distance = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.keySet().forEach(pos -> { distance.put(pos, 0); queue.add(pos); });
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            int next = distance.get(pos) + 1;
            if (next > 6) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (foliage.contains(neighbor) && !distance.containsKey(neighbor)) {
                    distance.put(neighbor, next);
                    queue.add(neighbor);
                }
            }
        }
        logs.forEach((pos, state) -> level.setBlock(pos, state, 2));
        for (BlockPos pos : foliage) level.setBlock(pos, leaves.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, distance.getOrDefault(pos, 7)), 2);
        return true;
    }
}
