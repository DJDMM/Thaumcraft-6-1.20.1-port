package thaumcraft.essentia.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.*;
import thaumcraft.world.aura.AuraManager;

/** Same connection-state names/models as the catalogue, backed by actual transport tiles. */
public final class TubeBlock extends Block implements EntityBlock {
    private static final ThreadLocal<String> CONSTRUCTING = new ThreadLocal<>();
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final BooleanProperty[] CONNECTIONS = {BlockStateProperties.DOWN, BlockStateProperties.UP,
            BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST};
    private final String id;
    public static TubeBlock create(String id, Properties properties) {
        CONSTRUCTING.set(id);
        try { return new TubeBlock(id, properties); } finally { CONSTRUCTING.remove(); }
    }
    private TubeBlock(String id, Properties properties) {
        super(properties); this.id = id;
        BlockState state = defaultBlockState();
        for (BooleanProperty property : CONNECTIONS) state = state.setValue(property, false);
        if (state.hasProperty(FACING)) state = state.setValue(FACING, Direction.NORTH);
        registerDefaultState(state);
    }
    public String id() { return id; }
    public static BooleanProperty connection(Direction face) { return CONNECTIONS[face.get3DDataValue()]; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS);
        String id = CONSTRUCTING.get();
        if ("tube_valve".equals(id) || "tube_oneway".equals(id)) builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.hasProperty(FACING) ? state.setValue(FACING, context.getNearestLookingDirection().getOpposite()) : state;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof TubeBlockEntity tube) {
            tube.setFacing(state.hasProperty(FACING) ? state.getValue(FACING) : Direction.getNearest(placer.getLookAngle().x,
                    placer.getLookAngle().y, placer.getLookAngle().z).getOpposite());
        }
        level.scheduleTick(pos, this, 1);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof TubeBlockEntity tube) tube.refreshConnections();
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override public BlockState updateShape(BlockState state, Direction face, BlockState adjacent, LevelAccessor level, BlockPos pos, BlockPos next) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
        return state;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return TubeBlockEntity.create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == EssentiaTransportModule.TUBE.get() ? (world, pos, blockState, entity) -> TubeBlockEntity.tick(world, pos, blockState, (TubeBlockEntity) entity) : null;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int event, int data) {
        return level.getBlockEntity(pos) instanceof TubeBlockEntity tube && tube.triggerEvent(event, data);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape result = id.equals("tube_buffer") ? box(4, 4, 4, 12, 12, 12) : box(5, 5, 5, 11, 11, 11);
        TubeBlockEntity tube = level.getBlockEntity(pos) instanceof TubeBlockEntity candidate ? candidate : null;
        for (Direction face : Direction.values()) {
            // Closed arms remain traceable for reopening with the caster, as in the original raytracer.
            if (!(tube != null ? tube.hasTransport(face) : state.getValue(connection(face)))) continue;
            result = Shapes.or(result, switch (face) {
                case DOWN -> box(6, 0, 6, 10, 6, 10); case UP -> box(6, 10, 6, 10, 16, 10);
                case NORTH -> box(6, 6, 0, 10, 10, 6); case SOUTH -> box(6, 6, 10, 10, 10, 16);
                case WEST -> box(0, 6, 6, 6, 10, 10); case EAST -> box(10, 6, 6, 16, 10, 10);
            });
        }
        return result;
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TubeBufferBlockEntity buffer)) return 0;
        int amount = buffer.getEssentiaAmount(null);
        return net.minecraft.util.Mth.floor(amount / 10F * 14F) + (amount > 0 ? 1 : 0);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof TubeBlockEntity tube) {
            int amount = tube.getEssentiaAmount(Direction.UP);
            if (amount > 0 && level instanceof ServerLevel server) AuraManager.addFlux(server, pos, amount);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    public static boolean isCaster(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.getNamespace().equals("thaumcraft") && (key.getPath().equals("caster_basic") || key.getPath().equals("caster_gauntlet"));
    }
    /** Original cuboid sub-hit expressed through the actual hit point, with no global input. */
    public static Direction armHit(TubeBlockEntity tube, BlockHitResult hit) {
        Vec3 point = hit.getLocation().subtract(Vec3.atLowerCornerOf(tube.getBlockPos()));
        double min = tube instanceof TubeBufferBlockEntity ? .25 : .375, max = 1 - min;
        Direction face = point.y < min ? Direction.DOWN : point.y > max ? Direction.UP : point.z < min ? Direction.NORTH
                : point.z > max ? Direction.SOUTH : point.x < min ? Direction.WEST : point.x > max ? Direction.EAST : null;
        return face != null && tube.hasTransport(face) ? face : null;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TubeBlockEntity tube)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (isCaster(held)) {
            Direction arm = armHit(tube, hit);
            if (arm == null && tube instanceof TubeBufferBlockEntity) return InteractionResult.PASS;
            if (!level.isClientSide) {
                if (arm != null && player.isShiftKeyDown() && tube instanceof TubeBufferBlockEntity buffer) buffer.cycleChoke(arm);
                else if (arm != null) tube.toggleSide(arm);
                else tube.rotateFacing();
                boolean choked = arm != null && player.isShiftKeyDown() && tube instanceof TubeBufferBlockEntity;
                level.playSound(null, pos, choked ? EssentiaTransportModule.SQUEEK.get() : EssentiaTransportModule.TOOL.get(), SoundSource.BLOCKS,
                        choked ? .6F : .5F, (choked ? 2F : .9F) + level.random.nextFloat() * .2F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (tube instanceof TubeFilterBlockEntity filter) {
            if (player.isShiftKeyDown() && filter.filter() != null) {
                if (!level.isClientSide) { filter.setFilter(null); level.playSound(null, pos, EssentiaTransportModule.KEY.get(), SoundSource.BLOCKS, 1F, 1F); }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (filter.filter() == null && held.getItem() instanceof IEssentiaContainerItem container) {
                AspectList aspects = container.getAspects(held);
                if (!level.isClientSide && aspects != null && aspects.size() > 0) {
                    filter.setFilter(aspects.getAspects()[0]); level.playSound(null, pos, EssentiaTransportModule.KEY.get(), SoundSource.BLOCKS, 1F, 1F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide); // Template is not consumed.
            }
        }
        if (id.equals("tube_valve") && !(held.getItem() instanceof EssentiaResonatorItem) && held.getItem() != asItem()) {
            if (!level.isClientSide) { tube.toggleFlow(); level.playSound(null, pos, EssentiaTransportModule.SQUEEK.get(), SoundSource.BLOCKS, .7F, .9F + level.random.nextFloat() * .2F); }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
