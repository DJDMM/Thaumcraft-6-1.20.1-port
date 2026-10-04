package thaumcraft.alchemy.hedge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** The BETA26 candle is always alight, has no collision, and drops when its support disappears. */
public final class TallowCandleBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 8, 10);
    public TallowCandleBlock(String id) {
        // BlockTC first raises old internal resistance to1.5*5; the later candle hardness does not lower it.
        super(Properties.of().mapColor(color(id).getMapColor()).strength(.1F, 1.5F)
                .sound(SoundType.WOOL).noCollission().noOcclusion().lightLevel(state -> 14));
    }
    private static DyeColor color(String id) {
        String color = id.substring("candle_".length());
        return DyeColor.byName(switch (color) { case "silver" -> "light_gray"; case "lightblue" -> "light_blue"; default -> color; }, DyeColor.WHITE);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        return canSurvive(defaultBlockState(), context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos other, boolean moving) {
        if (!level.isClientSide && !canSurvive(state, level, pos)) {
            Block.dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(new ItemStack(this)); }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5, 0, 0, 0);
        level.addParticle(ParticleTypes.FLAME, pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5, 0, 0, 0);
    }
}
