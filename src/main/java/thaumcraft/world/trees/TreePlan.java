package thaumcraft.world.trees;

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

/** Plan first so an obstructed tree never consumes saplings or damages a building. */
final class TreePlan {
    final WorldGenLevel level;
    final Block log;
    final Block leaves;
    final Map<BlockPos, BlockState> logs = new LinkedHashMap<>();
    final Set<BlockPos> foliage = new LinkedHashSet<>();

    TreePlan(WorldGenLevel level, Block log, Block leaves) { this.level = level; this.log = log; this.leaves = leaves; }
    boolean soil(BlockPos pos) { return Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos); }
    boolean replaceable(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }
    void log(BlockPos pos) { log(pos, Direction.Axis.Y); }
    void log(BlockPos pos, Direction.Axis axis) { logs.put(pos.immutable(), log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis)); }
    void leaf(BlockPos pos) { if (replaceable(pos)) foliage.add(pos.immutable()); }
    void branch(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        int length = Math.max(Math.abs(dy), Math.max(Math.abs(dx), Math.abs(dz)));
        Direction.Axis axis = Math.max(Math.abs(dx), Math.abs(dz)) == 0 ? Direction.Axis.Y
                : Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        if (length == 0) { log(from); return; }
        for (int step = 0; step <= length; step++) log(from.offset(
                Math.round((float) dx * step / length), Math.round((float) dy * step / length), Math.round((float) dz * step / length)), axis);
    }
    boolean lineClear(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        int length = Math.max(Math.abs(dy), Math.max(Math.abs(dx), Math.abs(dz)));
        for (int step = 0; step <= length; step++) {
            BlockPos pos = length == 0 ? from : from.offset(Math.round((float) dx * step / length),
                    Math.round((float) dy * step / length), Math.round((float) dz * step / length));
            if (!replaceable(pos) && !logs.containsKey(pos)) return false;
        }
        return true;
    }
    boolean place() {
        if (logs.isEmpty() || logs.keySet().stream().anyMatch(pos -> !replaceable(pos))) return false;
        foliage.removeAll(logs.keySet());
        // Initialize modern leaf distances; otherwise freshly generated TC6 crowns decay immediately.
        Map<BlockPos, Integer> distance = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.keySet().forEach(pos -> { distance.put(pos, 0); queue.add(pos); });
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            int next = distance.get(pos) + 1;
            if (next > 6) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (foliage.contains(neighbor) && !distance.containsKey(neighbor)) { distance.put(neighbor, next); queue.add(neighbor); }
            }
        }
        logs.forEach((pos, state) -> level.setBlock(pos, state, 2));
        for (BlockPos pos : foliage) level.setBlock(pos, leaves.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, distance.getOrDefault(pos, 7)), 2);
        return true;
    }
}
