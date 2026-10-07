package thaumcraft.golemancy.jar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class BrainJarBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);
    public BrainJarBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BrainJarBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BrainJarModule.BRAIN_JAR.get(), BrainJarBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BrainJarBlockEntity jar) jar.readItem(stack);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BrainJarBlockEntity jar)) return InteractionResult.PASS;
        jar.releaseXp();
        if (level.isClientSide) level.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                BrainJarModule.JAR.get(), SoundSource.BLOCKS, .2F, 1, false);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BrainJarBlockEntity jar
                ? jar.asItemStack() : new ItemStack(this));
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BrainJarBlockEntity jar)) return 0;
        return Mth.clamp(Mth.floor(jar.xp() / (float)BrainJarBlockEntity.CAPACITY * 14) + (jar.xp() > 0 ? 1 : 0), 0, 15);
    }
    @Override public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BrainJarBlockEntity ? 5 : 0;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BrainJarBlockEntity jar && jar.xp() >= BrainJarBlockEntity.CAPACITY)
            // Modern native coloured particle; original FXDispatcher green spark is documented.
            level.addParticle(ParticleTypes.ENTITY_EFFECT, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5,
                    .2 + random.nextFloat() * .2, 1, .3 + random.nextFloat() * .2);
    }
}
