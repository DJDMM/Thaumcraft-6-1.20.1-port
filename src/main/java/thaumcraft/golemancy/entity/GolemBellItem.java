package thaumcraft.golemancy.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import thaumcraft.golemancy.seals.core.*;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.catalog.CatalogItem;
import thaumcraft.catalog.CatalogModule;
import java.util.List;
import java.util.Objects;

/** The bell delegates physical seal/menu/logistics actions to the authoritative seal module. */
public final class GolemBellItem extends CatalogItem {
    public interface SealActions {
        InteractionResult useOn(UseOnContext context);
        InteractionResult useAir(ServerPlayer player, InteractionHand hand);
    }
    private static SealActions actions = new SealActions() {
        public InteractionResult useOn(UseOnContext context) { return SealRegistry.useBell(context); }
        public InteractionResult useAir(ServerPlayer player, InteractionHand hand) { return originalAirUse(player, hand); }
    };
    public static void setSealActions(SealActions value) { actions = Objects.requireNonNull(value); }
    public GolemBellItem(CatalogModule.Spec spec) { super(spec); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() == null || context.getPlayer().isSpectator()) return InteractionResult.FAIL;
        context.getPlayer().swing(context.getHand());
        return actions == null ? InteractionResult.PASS : actions.useOn(context);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.swing(hand);
        if (level.isClientSide) player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .6F, 1 + level.random.nextFloat() * .1F);
        else if (player instanceof ServerPlayer serverPlayer && !player.isSpectator() && actions != null) {
            InteractionResult action = actions.useAir(serverPlayer, hand);
            if (action != InteractionResult.PASS) return new InteractionResultHolder<>(action, player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {}
    /** Pinned five-block cube/face selection; stale or unloaded seals cannot be opened. */
    private static SealData raySeal(ServerPlayer player) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(5));
        SealData found = null; double nearest = Double.MAX_VALUE;
        for (SealData seal : SealService.get(player.serverLevel()).seals()) {
            BlockPos pos = seal.position().pos();
            if (!player.serverLevel().hasChunkAt(pos) || pos.distToCenterSqr(start) > 49) continue;
            var hit = new AABB(pos).clip(start, end); if (hit.isEmpty()) continue;
            Vec3 point = hit.get(); Direction face = null;
            if (Math.abs(point.x - pos.getX()) < 1e-6) face = Direction.WEST;
            else if (Math.abs(point.x - pos.getX() - 1) < 1e-6) face = Direction.EAST;
            else if (Math.abs(point.y - pos.getY()) < 1e-6) face = Direction.DOWN;
            else if (Math.abs(point.y - pos.getY() - 1) < 1e-6) face = Direction.UP;
            else if (Math.abs(point.z - pos.getZ()) < 1e-6) face = Direction.NORTH;
            else if (Math.abs(point.z - pos.getZ() - 1) < 1e-6) face = Direction.SOUTH;
            double distance = start.distanceToSqr(point);
            if (face == seal.position().face() && distance < nearest) { found = seal; nearest = distance; }
        }
        return found;
    }
    private static InteractionResult originalAirUse(ServerPlayer player, InteractionHand hand) {
        HitResult hit = player.pick(5, 0, false);
        if (hit.getType() != HitResult.Type.MISS) {
            SealData seal = raySeal(player);
            if (seal != null) {
                var pos = seal.position(); Vec3 point = Vec3.atCenterOf(pos.pos()).add(Vec3.atLowerCornerOf(pos.face().getNormal()).scale(.5));
                SealRegistry.useBell(new UseOnContext(player, hand, new BlockHitResult(point, pos.face(), pos.pos(), false)));
            }
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown() && KnowledgeStore.get(player).isResearchKnown("GOLEMLOGISTICS")) {
            SealRegistry.openLogistics(player, null, null);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
