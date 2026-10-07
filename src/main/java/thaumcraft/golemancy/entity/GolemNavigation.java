package thaumcraft.golemancy.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/** Native navigation adapters reject an unloaded PathNavigationRegion before it requests chunks. */
final class GolemNavigation {
    private GolemNavigation() {}
    private static boolean loaded(Mob mob, Set<BlockPos> targets, int padding, float range) {
        if (!Float.isFinite(range) || range < 0 || range > 128 || padding < 0 || padding > 32 || targets.isEmpty()) return false;
        Level level = mob.level();
        for (BlockPos target : targets) if (!level.hasChunkAt(target)) return false;
        int radius = (int)(range + padding), x = mob.blockPosition().getX(), z = mob.blockPosition().getZ();
        for (int chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++)
            for (int chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++)
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) return false;
        return true;
    }
    static final class Ground extends GroundPathNavigation {
        Ground(Mob mob, Level level) { super(mob, level); setCanPassDoors(true); }
        @Override public Path createPath(BlockPos target, int distance) { return level.hasChunkAt(target) ? super.createPath(target, distance) : null; }
        @Override protected Path createPath(Set<BlockPos> targets, int padding, boolean head, int distance, float range) {
            return loaded(mob, targets, padding, range) ? super.createPath(targets, padding, head, distance, range) : null;
        }
    }
    static final class Climber extends WallClimberNavigation {
        Climber(Mob mob, Level level) { super(mob, level); setCanPassDoors(true); }
        @Override public Path createPath(BlockPos target, int distance) { return level.hasChunkAt(target) ? super.createPath(target, distance) : null; }
        @Override public boolean moveTo(Entity target, double speed) {
            // WallClimberNavigation otherwise moves directly toward an unreachable/unloaded target.
            return loaded(mob, Set.of(target.blockPosition()), 16, (float)mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE))
                    && super.moveTo(target, speed);
        }
        @Override protected Path createPath(Set<BlockPos> targets, int padding, boolean head, int distance, float range) {
            return loaded(mob, targets, padding, range) ? super.createPath(targets, padding, head, distance, range) : null;
        }
    }
    static final class Air extends FlyingPathNavigation {
        Air(Mob mob, Level level) { super(mob, level); }
        @Override protected PathFinder createPathFinder(int maxNodes) {
            nodeEvaluator = new FlightNodes();
            return new PathFinder(nodeEvaluator, maxNodes);
        }
        @Override protected boolean canUpdatePath() { return true; }
        @Override protected Vec3 getTempMobPos() { return mob.position().add(0, mob.getBbHeight() * .5, 0); }
        @Override public Path createPath(BlockPos target, int distance) { return level.hasChunkAt(target) ? super.createPath(target, distance) : null; }
        @Override protected Path createPath(Set<BlockPos> targets, int padding, boolean head, int distance, float range) {
            return loaded(mob, targets, padding, range) ? super.createPath(targets, padding, head, distance, range) : null;
        }
        @Override protected void followThePath() {
            if (path == null || path.isDone()) return;
            Vec3 current = getTempMobPos();
            if (current.distanceToSqr(path.getNextEntityPos(mob)) < mob.getBbWidth() * mob.getBbWidth()) path.advance();
            for (int i = Math.min(path.getNextNodeIndex() + 6, path.getNodeCount() - 1); i > path.getNextNodeIndex(); i--) {
                Vec3 next = path.getEntityPosAtNode(mob, i);
                if (next.distanceToSqr(current) <= 36 && canMoveDirectly(current, next)) { path.setNextNodeIndex(i); break; }
            }
            doStuckDetection(current);
        }
        @Override protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
            return level.clip(new ClipContext(from, to.add(0, mob.getBbHeight() * .5, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
        }
        @Override public boolean isStableDestination(BlockPos target) {
            return level.hasChunkAt(target) && level.getBlockState(target).getCollisionShape(level, target).isEmpty();
        }
    }

    /** BETA26 FlightNodeProcessor visits only six face neighbors, independent of ground/water cost. */
    static final class FlightNodes extends NodeEvaluator {
        @Override public Node getStart() {
            return getNode(Mth.floor(mob.getBoundingBox().minX), Mth.floor(mob.getBoundingBox().minY + .5), Mth.floor(mob.getBoundingBox().minZ));
        }
        @Override public Target getGoal(double x, double y, double z) {
            // Modern PathFinder passes integral BlockPos rather than the legacy target center.
            return getTargetFromNode(getNode(Mth.floor(x + .5 - mob.getBbWidth() / 2), Mth.floor(y + .5), Mth.floor(z + .5 - mob.getBbWidth() / 2)));
        }
        @Override public int getNeighbors(Node[] neighbors, Node current) {
            int count = 0;
            for (Direction direction : Direction.values()) {
                int x = current.x + direction.getStepX(), y = current.y + direction.getStepY(), z = current.z + direction.getStepZ();
                if (!free(x, y, z)) continue;
                Node next = getNode(x, y, z);
                if (!next.closed) neighbors[count++] = next;
            }
            return count;
        }
        private boolean free(int x, int y, int z) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int a = x; a < x + entityWidth; a++) for (int b = y; b < y + entityHeight; b++) for (int c = z; c < z + entityDepth; c++) {
                pos.set(a, b, c);
                if (level.isOutsideBuildHeight(pos) || !mob.level().hasChunkAt(pos)) return false;
                var state = level.getBlockState(pos);
                // Legacy isPassable includes fluids/plants and open doors; modern AIR excludes fluids.
                if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()
                        && !state.isPathfindable(level, pos, PathComputationType.AIR)) return false;
            }
            return true;
        }
        @Override public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob) { return BlockPathTypes.WATER; }
        @Override public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) { return BlockPathTypes.WATER; }
    }
}
