package thaumcraft.equipment.cleansing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.research.KnowledgeStore;

/** Small server-authoritative operations, shared with game tests and the future warp-event system. */
public final class CleansingSupport {
    private CleansingSupport() {}

    public static boolean isProtected(LivingEntity entity) {
        return CleansingModule.WARP_WARD.isPresent() && entity.hasEffect(CleansingModule.WARP_WARD.get());
    }

    public static boolean isPurifyingFluid(BlockState state) { return state.getBlock() instanceof PurifyingFluidBlock; }

    public static int wardDuration(int permanentWarp) {
        int divisor = Math.max(1, (int) Math.sqrt(Math.max(0, permanentWarp)));
        return Math.min(32000, 200000 / divisor);
    }

    public static boolean grantWard(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!isPurifyingFluid(state) || !state.getFluidState().isSource() || isProtected(player)) return false;
        player.addEffect(new MobEffectInstance(CleansingModule.WARP_WARD.get(),
                wardDuration(KnowledgeStore.get(player).permanentWarp()), 0, true, true));
        player.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return true;
    }

    /** Kept exactly: BETA26 InternalMethodHandler assigns +current, rather than -current, on overdraw. */
    public static int originalNormalWarpDelta(int current, int removal) {
        return current - removal < 0 ? current : -removal;
    }

    /** One soap is consumed even in creative, temporary warp is wiped, permanent warp is untouched. */
    public static boolean finishSoap(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SanitySoapItem)) return false;
        int amount = 1 + (isProtected(player) ? 1 : 0)
                + (isPurifyingFluid(player.level().getBlockState(player.blockPosition())) ? 1 : 0);
        stack.shrink(1);
        var knowledge = KnowledgeStore.get(player);
        int normal = knowledge.normalWarp();
        int temporary = knowledge.temporaryWarp();
        if (normal > 0) KnowledgeStore.addNormalWarp(player, originalNormalWarpDelta(normal, amount));
        if (temporary > 0) KnowledgeStore.addTemporaryWarp(player, -temporary);
        CleansingNetwork.soapFinished(player);
        return true;
    }

    /** Expiring salt stacks convert precisely one vanilla source-water block; the event discards the whole stack. */
    public static boolean convertBath(Level level, BlockPos pos) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) return false;
        return level.setBlockAndUpdate(pos, CleansingModule.block().defaultBlockState());
    }
}
