package thaumcraft.world.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.world.aura.AuraManager;

/** BETA26 BlockCrystal growth and degeneration; facing/waterlogged retain earlier port saves. */
public final class PrimalCrystalClusterBlock extends AmethystClusterBlock {
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 3);
    public static final IntegerProperty GENERATION = IntegerProperty.create("gen", 1, 4);
    public static final TagKey<Block> ROCK_SUPPORT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "crystal_support"));
    private final Aspect aspect;

    public PrimalCrystalClusterBlock(Aspect aspect) {
        super(8, 0, Properties.of().mapColor(net.minecraft.world.level.material.MapColor.NONE)
                .strength(.25F, .25F).sound(CrystalSounds.TYPE).noCollission().noOcclusion()
                .randomTicks().lightLevel(state -> 1));
        if (aspect == null || (!aspect.isPrimal() && aspect != Aspect.FLUX))
            throw new IllegalArgumentException("Original world crystals have six primal forms and Flux only");
        this.aspect = aspect;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(WATERLOGGED, false)
                .setValue(SIZE, 0).setValue(GENERATION, 1));
    }

    public Aspect aspect() { return aspect; }
    @Override public void onProjectileHit(Level level, BlockState state, net.minecraft.world.phys.BlockHitResult hit,
            net.minecraft.world.entity.projectile.Projectile projectile) {
        // The original glass-material Block has no AmethystBlock hit/chime callback.
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIZE, GENERATION);
    }

    /** 1.12's X26/Y12/Z26 packed position, including its signed Java remainder. */
    public static long originalPositionLong(BlockPos pos) {
        return ((long)pos.getX() & 0x3ffffffL) << 38
                | ((long)pos.getY() & 0xfffL) << 26 | ((long)pos.getZ() & 0x3ffffffL);
    }
    public static long growthLimit(BlockPos pos, int generation) {
        return 5L - generation + originalPositionLong(pos) % 3L;
    }

    public static boolean attachedToRock(BlockGetter level, BlockPos pos, Direction towardSupport) {
        BlockPos neighbor = pos.relative(towardSupport);
        if (level instanceof LevelReader reader && !reader.hasChunkAt(neighbor)) return false;
        var state = level.getBlockState(neighbor);
        return state.is(ROCK_SUPPORT) && state.isFaceSturdy(level, neighbor, towardSupport.getOpposite());
    }
    public static int supportMask(BlockGetter level, BlockPos pos) {
        int mask = 0;
        for (Direction dir : Direction.values()) if (attachedToRock(level, pos, dir)) mask |= 1 << dir.ordinal();
        return mask;
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return supportMask(level, pos) != 0;
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        // BETA26 has no liquid placement. A WATERLOGGED property is retained only for old port saves.
        if (!context.getLevel().getFluidState(context.getClickedPos()).isEmpty()) return null;
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos other) {
        // Original neighborChanged handles every rock face and the one physical drop. The
        // inherited amethyst single-facing survival path must not remove a supported cluster.
        return state;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos other, boolean moving) {
        if (!level.isClientSide && level.getBlockState(pos).is(this) && !canSurvive(state, level, pos)) {
            Block.dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = supportMask(level, pos);
        if (Integer.bitCount(mask) != 1) return Shapes.block();
        return switch (Direction.values()[Integer.numberOfTrailingZeros(mask)]) {
            case DOWN -> Block.box(0, 0, 0, 16, 8, 16);
            case UP -> Block.box(0, 8, 0, 16, 16, 16);
            case NORTH -> Block.box(0, 0, 0, 16, 16, 8);
            case SOUTH -> Block.box(0, 0, 8, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, 8, 16, 16);
            case EAST -> Block.box(8, 0, 0, 16, 16, 16);
        };
    }

    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getServer().isSameThread() || !level.hasChunkAt(pos) || !level.getBlockState(pos).equals(state)) return;
        int generation = state.getValue(GENERATION);
        if (random.nextInt(3 + generation) != 0) return;
        int growth = state.getValue(SIZE);
        float stored = aspect == Aspect.FLUX ? AuraManager.getFlux(level, pos) : AuraManager.getVis(level, pos);
        if (stored <= 10) {
            if (growth > 0) {
                if (level.setBlockAndUpdate(pos, state.setValue(SIZE, growth - 1))) returnAura(level, pos);
            } else if (touchesSameCrystal(level, pos)) {
                if (level.removeBlock(pos, false)) returnAura(level, pos);
            }
        } else if (stored > AuraManager.getAuraBase(level, pos) + 10) {
            if (growth < 3 && growth < growthLimit(pos, generation)) {
                if (drainAura(level, pos) > 0) {
                    if (!level.setBlockAndUpdate(pos, state.setValue(SIZE, growth + 1))) returnAura(level, pos);
                }
            } else if (generation < 4) {
                BlockPos target = spreadCrystal(level, pos);
                if (target != null && drainAura(level, pos) > 0) {
                    int childGeneration = generation + (random.nextInt(6) == 0 ? 0 : 1);
                    BlockState child = defaultBlockState().setValue(GENERATION, childGeneration);
                    if (!level.setBlockAndUpdate(target, child)) returnAura(level, pos);
                }
            }
        }
    }

    private float drainAura(ServerLevel level, BlockPos pos) {
        return aspect == Aspect.FLUX ? AuraManager.drainFlux(level, pos, 10, false)
                : AuraManager.drainVis(level, pos, 10, false);
    }
    private void returnAura(ServerLevel level, BlockPos pos) {
        if (aspect == Aspect.FLUX) AuraManager.addFlux(level, pos, 10);
        else AuraManager.addVis(level, pos, 10);
    }
    private boolean touchesSameCrystal(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos other = pos.relative(dir);
            if (level.hasChunkAt(other) && level.getBlockState(other).is(this)) return true;
        }
        return false;
    }
    @Nullable public static BlockPos spreadCrystal(ServerLevel level, BlockPos pos) {
        // Original uses world.rand for target/1-in-16; the updateTick random supplies
        // the separate activation/1-in-6 generation-preservation rolls.
        BlockPos target = pos.offset(level.random.nextInt(3) - 1, level.random.nextInt(3) - 1,
                level.random.nextInt(3) - 1);
        if (target.equals(pos) || !level.hasChunkAt(target) || level.isOutsideBuildHeight(target)) return null;
        var state = level.getBlockState(target);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced())
                && level.random.nextInt(16) == 0 && supportMask(level, target) != 0 ? target : null;
    }
}
