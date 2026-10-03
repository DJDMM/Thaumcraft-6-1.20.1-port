package thaumcraft.infusion;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Horizontal charge wiring. Deferred updates avoid modern neighbor recursion without loading chunks. */
public final class InfusionInlayBlock extends Block {
    public enum Connection implements StringRepresentable { NONE, SIDE, EXT; @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); } }
    public static final IntegerProperty CHARGE = InfusionPedestalBlock.CHARGE;
    public static final EnumProperty<Connection> NORTH = EnumProperty.create("north", Connection.class), EAST = EnumProperty.create("east", Connection.class),
            SOUTH = EnumProperty.create("south", Connection.class), WEST = EnumProperty.create("west", Connection.class);
    private static final List<Direction> DIRECTIONS = List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST);
    public InfusionInlayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0).setValue(NORTH, Connection.NONE).setValue(EAST, Connection.NONE).setValue(SOUTH, Connection.NONE).setValue(WEST, Connection.NONE));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CHARGE, NORTH, EAST, SOUTH, WEST); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(0, 0, 0, 16, 1, 16); }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP); }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos adjacent) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        var property = switch(direction) { case NORTH -> NORTH; case SOUTH -> SOUTH; case WEST -> WEST; case EAST -> EAST; default -> null; };
        return property == null ? state : state.setValue(property, neighbor.getBlock() instanceof InfusionStabilizerBlock ? Connection.EXT :
                neighbor.getBlock() instanceof InfusionInlayBlock || neighbor.getBlock() instanceof InfusionPedestalBlock ? Connection.SIDE : Connection.NONE);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) { if (!level.isClientSide) level.scheduleTick(pos, this, 1); }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) { if (!level.isClientSide) level.scheduleTick(pos, this, 1); }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { updateCharge(level, pos); }
    public static void updateCharge(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos); if (!state.hasProperty(CHARGE)) return;
        int source = 0, max = 0;
        for (Direction direction : DIRECTIONS) {
            BlockPos adjacent = pos.relative(direction); if (!level.hasChunkAt(adjacent)) continue;
            if (source == 0 && level.getBlockEntity(adjacent) instanceof InfusionStabilizerBlockEntity stabilizer) source = stabilizer.energy();
            var neighbor = level.getBlockState(adjacent);
            if (neighbor.hasProperty(CHARGE)) max = Math.max(max, neighbor.getValue(CHARGE) - 1);
        }
        int charge = Math.max(source, max); BlockState before = state;
        if (state.getBlock() instanceof InfusionInlayBlock) for (Direction direction : DIRECTIONS) {
            BlockPos adjacent = pos.relative(direction); if (!level.hasChunkAt(adjacent)) continue;
            state = state.getBlock().updateShape(state,direction,level.getBlockState(adjacent),level,pos,adjacent);
        }
        if (state.getValue(CHARGE) != charge || !state.equals(before)) {
            level.setBlock(pos, state.setValue(CHARGE, charge), 2);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }
    public static InfusionStabilizerBlockEntity find(Level level, BlockPos pos) { return find(level, pos, -1); }
    private static InfusionStabilizerBlockEntity find(Level level, BlockPos pos, int previous) {
        if (!level.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos); if (!state.hasProperty(CHARGE) || state.getValue(CHARGE) <= previous || state.getValue(CHARGE) <= 0) return null;
        for (Direction direction : DIRECTIONS) {
            BlockPos next = pos.relative(direction); if (!level.hasChunkAt(next)) continue;
            if (level.getBlockEntity(next) instanceof InfusionStabilizerBlockEntity source && source.energy() >= 5) return source;
            // Charge strictly increases to at most15, so recursion cannot cycle. A global visited
            // set would discard a valid alternate path after first reaching it at an equal charge.
            var result = find(level, next, state.getValue(CHARGE)); if (result != null) return result;
        }
        return null;
    }
}
