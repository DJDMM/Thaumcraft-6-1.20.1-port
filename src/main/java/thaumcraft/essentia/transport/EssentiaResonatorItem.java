package thaumcraft.essentia.transport;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import thaumcraft.api.aspects.*;

/** BETA26 resonator diagnostics use the clicked arm and actual server transport state. */
public final class EssentiaResonatorItem extends Item {
    public EssentiaResonatorItem() { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }
    @Override public boolean isFoil(ItemStack stack) { return stack.getTag() != null; }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof IEssentiaTransport transport)) return InteractionResult.FAIL;
        if (context.getPlayer() == null) return InteractionResult.FAIL;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        Direction face = context.getClickedFace();
        if (transport instanceof TubeBlockEntity tube) {
            Direction arm = TubeBlock.armHit(tube, new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), face, context.getClickedPos(), false));
            if (arm != null) face = arm;
        }
        var player = context.getPlayer();
        if (transport instanceof TubeBufferBlockEntity buffer) {
            AspectList list = buffer.getAspects();
            for (Aspect type : list.getAspectsSortedByName()) player.sendSystemMessage(Component.translatable("tc.resonator1", list.getAmount(type), type.getName()));
        } else if (transport.getEssentiaType(face) != null) {
            player.sendSystemMessage(Component.translatable("tc.resonator1", transport.getEssentiaAmount(face), transport.getEssentiaType(face).getName()));
        }
        Aspect type = transport.getSuctionType(face);
        player.sendSystemMessage(Component.translatable("tc.resonator2", transport.getSuctionAmount(face), type == null ? Component.translatable("tc.resonator3") : type.getName()));
        context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, .5F, 1.9F + context.getLevel().random.nextFloat() * .1F);
        return InteractionResult.SUCCESS;
    }
}
