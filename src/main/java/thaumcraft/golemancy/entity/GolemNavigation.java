package thaumcraft.golemancy.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
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
        Air(Mob mob, Level level) { super(mob, level); setCanFloat(true); }
        @Override protected Path createPath(Set<BlockPos> targets, int padding, boolean head, int distance, float range) {
            return loaded(mob, targets, padding, range) ? super.createPath(targets, padding, head, distance, range) : null;
        }
    }
}
