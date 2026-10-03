package thaumcraft.research.book;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Detached, immutable preview access: no Level, chunks, block entities, timers or world writes. */
public final class MultiblockPreview implements BlockGetter {
    private final MultiblockCatalog.Blueprint blueprint;
    private final Map<BlockPos, BlockState> states;
    private final int removedTopLayers;
    private final boolean target;

    MultiblockPreview(MultiblockCatalog.Blueprint blueprint, boolean target, int removedTopLayers) {
        this.blueprint = blueprint; this.target = target;
        this.removedTopLayers = Math.max(0, Math.min(blueprint.height(), removedTopLayers));
        Map<BlockPos, BlockState> assembled = new LinkedHashMap<>();
        for (var part : blueprint.parts()) {
            BlockPos pos = part.pos();
            if (target) pos = new BlockPos(blueprint.depth() - pos.getZ() - 1, pos.getY(), pos.getX());
            if (pos.getY() >= blueprint.height() - this.removedTopLayers) continue;
            BlockState state = (target && part.result() != null ? part.result() : part.source()).state();
            if (!state.isAir()) assembled.put(pos, state);
        }
        // In the release book the detached BlockAccess provides neighbours for the vanilla bars model.
        // Compute those neighbours from this blueprint alone rather than consulting the active world.
        Map<BlockPos, BlockState> connected = new LinkedHashMap<>();
        for (var entry : assembled.entrySet()) {
            BlockState state = entry.getValue();
            if (state.getBlock() instanceof IronBarsBlock) for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockState neighbour = assembled.getOrDefault(entry.getKey().relative(side), Blocks.AIR.defaultBlockState());
                var property = switch (side) {
                    case NORTH -> BlockStateProperties.NORTH; case SOUTH -> BlockStateProperties.SOUTH;
                    case WEST -> BlockStateProperties.WEST; case EAST -> BlockStateProperties.EAST;
                    default -> throw new IllegalStateException("Nonhorizontal bars side");
                };
                state = state.setValue(property, neighbour.getBlock() instanceof IronBarsBlock || neighbour.isSolid());
            }
            connected.put(entry.getKey(), state);
        }
        states = Collections.unmodifiableMap(connected);
    }

    public MultiblockCatalog.Blueprint blueprint() { return blueprint; }
    public Map<BlockPos, BlockState> states() { return states; }
    public int removedTopLayers() { return removedTopLayers; }
    public boolean target() { return target; }
    public int width() { return target ? blueprint.depth() : blueprint.width(); }
    public int depth() { return target ? blueprint.width() : blueprint.depth(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return blueprint.height(); }
    @Override public int getMinBuildHeight() { return 0; }
}
