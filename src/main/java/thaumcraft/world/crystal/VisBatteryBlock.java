package thaumcraft.world.crystal;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import thaumcraft.world.aura.AuraManager;

/** BETA26's full-cube, ten-unit local aura battery; storage lives in its block state. */
public final class VisBatteryBlock extends Block {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 10);
    public static final int CAPACITY = 10;

    public VisBatteryBlock() {
        // Vanilla 1.12 setHardness(.5) raised internal resistance to .5*5; its getter divided by5.
        super(Properties.of().mapColor(MapColor.STONE).strength(.5F, .5F).sound(SoundType.STONE)
                .randomTicks().lightLevel(state -> state.getValue(CHARGE)));
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tick(state, level, pos, random);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Scheduled/random updates never force a chunk, mutate a replacement block or run off-thread.
        if (!level.getServer().isSameThread() || !level.hasChunkAt(pos)
                || level.getBlockState(pos) != state || !state.is(this)) return;
        int charge = state.getValue(CHARGE);
        if (level.hasNeighborSignal(pos)) {
            if (charge > 0) release(state, level, pos, charge, 5);
            return;
        }
        float vis = AuraManager.getVis(level, pos);
        int base = AuraManager.getAuraBase(level, pos);
        // The original comparisons promote the float amount to double and are strictly >/<.
        if (charge < CAPACITY && vis > base * .9D && vis > 1.0F) {
            float paid = AuraManager.drainVis(level, pos, 1.0F, false);
            if (paid != 1.0F) {
                if (paid > 0) AuraManager.addVis(level, pos, paid);
                return;
            }
            if (level.setBlock(pos, state.setValue(CHARGE, charge + 1), Block.UPDATE_ALL)) {
                level.scheduleTick(pos, this, 100 + random.nextInt(100));
            } else AuraManager.addVis(level, pos, paid);
        } else if (charge > 0 && vis < base * .75D) {
            release(state, level, pos, charge, 20 + random.nextInt(20));
        }
    }

    private void release(BlockState state, ServerLevel level, BlockPos pos, int charge, int delay) {
        if (!level.setBlock(pos, state.setValue(CHARGE, charge - 1), Block.UPDATE_ALL)) return;
        AuraManager.addVis(level, pos, 1.0F);
        level.scheduleTick(pos, this, delay);
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos neighbor, boolean moving) {
        if (!level.isClientSide && level.hasChunkAt(pos) && level.getBlockState(pos).is(this)
                && level.hasNeighborSignal(pos)) level.scheduleTick(pos, this, 1);
    }

    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(CHARGE);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // damageDropped was always0: breaking or Silk Touch does not carry stored vis into the item.
        return List.of(new ItemStack(this));
    }
}
