package thaumcraft.auromancy.remaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.auromancy.FocusEffects;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** The original portable-hole blacklist and delayed segment creation, with no forced chunk loading. */
public final class RiftPassage {
    private RiftPassage() {}
    public static boolean blacklisted(BlockState state) {
        var block = state.getBlock(); var id = ForgeRegistries.BLOCKS.getKey(block);
        return block instanceof BedBlock || block instanceof DoorBlock || block instanceof PistonBaseBlock
                || block instanceof PistonHeadBlock || block instanceof MovingPistonBlock
                || id != null && id.equals(RemainingEffectsModule.id("infernal_furnace"));
    }
    static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    static boolean mayChange(ServerLevel level, ServerPlayer caster, BlockPos pos) {
        return loaded(level, pos) && level.mayInteract(caster, pos)
                && !caster.blockActionRestricted(level, pos, caster.gameMode.getGameModeForPlayer());
    }
    public static boolean create(ServerLevel level, ServerPlayer caster, BlockPos pos, Direction side, byte count, int maximum) {
        if (level == null || caster == null || !level.getServer().isSameThread() || caster.serverLevel() != level
                || !caster.isAlive() || caster.isSpectator() || maximum < 1 || maximum > 200 || !mayChange(level, caster, pos)) return false;
        BlockState previous = level.getBlockState(pos); float hardness = previous.getDestroySpeed(level, pos);
        if (level.getBlockEntity(pos) != null || previous.hasBlockEntity() || blacklisted(previous) || previous.is(Blocks.BEDROCK)
                || previous.getBlock() instanceof RiftHoleBlock || !Float.isFinite(hardness) || hardness < 0
                || !previous.isAir() && previous.canBeReplaced()) return false;
        // Original old canPlaceBlockAt is a replaceability check for ordinary blocks. Modern
        // canBeReplaced retains the refusal of water/replaceable plants and acceptance of solid walls.
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (ForgeEventFactory.onBlockPlace(caster, snapshot, side == null ? Direction.UP : side)
                || !mayChange(level, caster, pos) || level.getBlockState(pos) != previous || level.getBlockEntity(pos) != null) return false;
        var hole = CatalogBlocks.block("hole").defaultBlockState();
        if (!level.setBlock(pos, hole, 3)) return false;
        if (!(level.getBlockEntity(pos) instanceof RiftHoleBlockEntity memory)) {
            // Never leave an unrestorable hole if another mod displaced the expected BE.
            if (level.getBlockState(pos) == hole) level.setBlock(pos, previous, 3);
            return false;
        }
        memory.configure(previous, maximum, count, side, caster);
        level.sendBlockUpdated(pos, hole, hole, 3);
        return true;
    }
}
