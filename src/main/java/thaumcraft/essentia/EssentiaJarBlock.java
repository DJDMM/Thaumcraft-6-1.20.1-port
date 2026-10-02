package thaumcraft.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import thaumcraft.catalog.CatalogModule;
import java.util.*;

public final class EssentiaJarBlock extends BaseEntityBlock {
    private final boolean voidJar;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);
    public EssentiaJarBlock(boolean voidJar) { super(Properties.copy(Blocks.GLASS).strength(.3F).noOcclusion()); this.voidJar = voidJar; }
    public boolean isVoid() { return voidJar; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EssentiaJarBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, EssentiaModule.JAR.get(), EssentiaJarBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof EssentiaJarBlockEntity jar)) return;
        jar.readItem(stack);
        if (placer != null) {
            var tag = jar.saveWithoutMetadata(); tag.putByte("facing", (byte)EssentiaJarBlockEntity.labelFacing(placer.getYRot()).get3DDataValue());
            jar.load(tag); jar.setChanged(); level.sendBlockUpdated(pos, state, state, 3);
        }
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EssentiaJarBlockEntity jar)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!jar.blocked() && held.is(CatalogModule.ENTRIES.get("jar_brace").get())) {
            if (!level.isClientSide && jar.installBrace()) { held.shrink(1); level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, .5F, 1F); }
        } else if (player.isShiftKeyDown() && jar.filter() != null && hit.getDirection() == jar.facing()) {
            if (!level.isClientSide && jar.removeLabel()) { popResource(level, pos.relative(hit.getDirection()), CatalogModule.stack("label_blank")); level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, .5F, 1F); }
        } else if (player.isShiftKeyDown() && held.isEmpty()) {
            if (!level.isClientSide) { jar.purge(); level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, .5F, 1F); }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var tile = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(tile instanceof EssentiaJarBlockEntity jar)) return List.of(new ItemStack(this));
        List<ItemStack> drops = new ArrayList<>(); drops.add(jar.asItemStack());
        if (jar.blocked()) drops.add(CatalogModule.stack("jar_brace"));
        return drops;
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EssentiaJarBlockEntity jar ? (jar.amount() * 14 / EssentiaJarBlockEntity.CAPACITY) + (jar.amount() > 0 ? 1 : 0) : 0;
    }
}
