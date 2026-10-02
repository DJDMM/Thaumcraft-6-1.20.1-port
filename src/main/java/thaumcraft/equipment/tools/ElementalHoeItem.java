package thaumcraft.equipment.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/** Original 3x3 tilling, single-block Shift bypass, and bonemeal when no block was tilled. */
public final class ElementalHoeItem extends ToolItems.Hoe {
    public ElementalHoeItem() { super(ToolMaterials.ELEMENTAL, 0); }
    @Override public Rarity getRarity(ItemStack stack) { return Rarity.RARE; }
    @Override public int getEnchantmentValue() { return 5; }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS; // ordinary modern HoeItem handles one block
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        ServerLevel level = (ServerLevel)context.getLevel();
        boolean did = false;
        if (context.getClickedFace() != Direction.DOWN) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (stack.isEmpty()) break;
                BlockPos pos = context.getClickedPos().offset(x, 0, z);
                if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || !player.mayUseItemAt(pos, context.getClickedFace(), stack) || !level.isEmptyBlock(pos.above())
                        || level.getBlockEntity(pos) != null) continue;
                Vec3 hit = context.getClickLocation().add(x, 0, z);
                UseOnContext at = new UseOnContext(level, player, context.getHand(), stack,
                        new BlockHitResult(hit, context.getClickedFace(), pos, context.isInside()));
                BlockState result = level.getBlockState(pos).getToolModifiedState(at, ToolActions.HOE_TILL, false);
                if (result == null || result == level.getBlockState(pos)) continue;
                BlockSnapshot previous = BlockSnapshot.create(level.dimension(), level, pos);
                if (!level.setBlock(pos, result, 3)) continue;
                if (ForgeEventFactory.onBlockPlace(player, previous, context.getClickedFace())) { previous.restore(true, false); continue; }
                ToolSupport.damage(stack, player, context.getHand(), 1);
                level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1F, 1F);
                ToolSupport.particles(level, pos);
                did = true;
            }
        }
        BlockPos pos = context.getClickedPos();
        if (!did && !stack.isEmpty() && level.mayInteract(player, pos) && player.mayUseItemAt(pos, context.getClickedFace(), stack)
                && BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, player)) {
            ToolSupport.damage(stack, player, context.getHand(), 3);
            level.levelEvent(2005, pos, 0);
        }
        return InteractionResult.CONSUME;
    }
}
