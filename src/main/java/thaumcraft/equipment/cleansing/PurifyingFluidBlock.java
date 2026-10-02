package thaumcraft.equipment.cleansing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

/** Actual source/flowing fluid, with the original source-only, one-bath Ward grant. */
public final class PurifyingFluidBlock extends LiquidBlock {
    public PurifyingFluidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) { super(fluid, properties); }

    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        double factor = 1 - state.getFluidState().getAmount() / 16.0;
        var motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x * factor, motion.y, motion.z * factor);
        if (entity instanceof ServerPlayer player) CleansingSupport.grantWard(player, pos, state);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CleansingClient.fluidBubble(state, level, pos, random));
    }
}
