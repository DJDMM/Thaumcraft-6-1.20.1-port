package thaumcraft.equipment.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.ArrayList;
import java.util.List;

/** Original nine-block builder and three orientations; inventory and placements change only on the server. */
public final class ElementalShovelItem extends ToolItems.Shovel {
    public ElementalShovelItem() { super(ToolMaterials.ELEMENTAL, 0); }
    @Override public Rarity getRarity(ItemStack stack) { return Rarity.RARE; }

    public static int orientation(ItemStack stack) { return stack.hasTag() ? Math.floorMod(stack.getTag().getByte("or"), 3) : 0; }
    public static void cycleOrientation(ItemStack stack) { stack.getOrCreateTag().putByte("or", (byte)((orientation(stack) + 1) % 3)); }

    public static List<BlockPos> positions(ItemStack stack, BlockPos origin, Direction face, Player player) {
        List<BlockPos> result = new ArrayList<>(9);
        int o = orientation(stack), yaw = Mth.floor(player.getYRot() * 4F / 360F + .5) & 3;
        for (int aa = -1; aa <= 1; aa++) for (int bb = -1; bb <= 1; bb++) {
            int x = 0, y = 0, z = 0;
            if (o == 1 || o == 2 && face.getAxis() == Direction.Axis.Y) {
                y = bb;
                if (face.getAxis() == Direction.Axis.Y) { if (yaw == 0 || yaw == 2) x = aa; else z = aa; }
                else if (face.getAxis() == Direction.Axis.Z) z = aa;
                else x = aa;
            } else if (o == 2) { x = aa; z = bb; }
            else if (face.getAxis() == Direction.Axis.Y) { x = aa; z = bb; }
            else if (face.getAxis() == Direction.Axis.Z) { x = aa; y = bb; }
            else { z = aa; y = bb; }
            result.add(origin.relative(face).offset(x, y, z));
        }
        return result;
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getLevel().getBlockEntity(context.getClickedPos()) != null) return InteractionResult.PASS;
        BlockState template = context.getLevel().getBlockState(context.getClickedPos());
        if (template.isAir() || !(template.getBlock().asItem() instanceof BlockItem) || template.hasBlockEntity()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        ServerLevel level = (ServerLevel)context.getLevel();
        if (!level.mayInteract(player, context.getClickedPos())) return InteractionResult.FAIL;
        boolean placed = false;
        for (BlockPos pos : positions(stack, context.getClickedPos(), context.getClickedFace(), player)) {
            if (stack.isEmpty()) break;
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, context.getClickedFace(), stack) || !level.getBlockState(pos).canBeReplaced()
                    || level.getBlockEntity(pos) != null) continue;
            BlockState state = template;
            int slot = matchingSlot(player, template.getBlock().asItem());
            if (!player.getAbilities().instabuild && slot < 0 && template.is(Blocks.GRASS_BLOCK)) {
                state = Blocks.DIRT.defaultBlockState(); slot = matchingSlot(player, Items.DIRT);
            }
            if (!player.getAbilities().instabuild && slot < 0 || !state.canSurvive(level, pos)
                    || !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.of(player))) continue;
            BlockSnapshot previous = BlockSnapshot.create(level.dimension(), level, pos);
            if (!level.setBlock(pos, state, 3)) continue;
            // Each target is protected individually. Never pay for a placement canceled by Forge.
            if (ForgeEventFactory.onBlockPlace(player, previous, context.getClickedFace())) { previous.restore(true, false); continue; }
            if (!player.getAbilities().instabuild) player.getInventory().getItem(slot).shrink(1);
            ToolSupport.damage(stack, player, context.getHand(), 1);
            level.playSound(null, pos, state.getSoundType(level, pos, player).getBreakSound(), SoundSource.BLOCKS,
                    .6F, .9F + player.getRandom().nextFloat() * .2F);
            ToolSupport.particles(level, pos);
            placed = true;
        }
        if (placed) { player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); player.swing(context.getHand(), true); }
        // TC6 returns FAIL even after building. SUCCESS prevents modern second-hand/GUI fallthrough after a real placement.
        return placed ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static int matchingSlot(Player player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (!player.getInventory().getItem(i).isEmpty() && player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }
}
