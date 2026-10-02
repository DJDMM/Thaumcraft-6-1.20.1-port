package thaumcraft.essentia.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.essentia.EssentiaJarBlockEntity;

/** Original blank/filled labels are templates; their stored aspect is not scan contents. */
public final class EssentiaLabelItem extends Item implements IEssentiaContainerItem {
    private final boolean filled;

    public EssentiaLabelItem(CatalogModule.Spec spec) {
        super(new Properties().stacksTo(spec.stackLimit()));
        filled = spec.id().equals("label_filled");
    }

    @Override
    public Component getName(ItemStack stack) {
        AspectList contents = getAspects(stack);
        return filled && contents != null
                ? Component.translatable(getDescriptionId(stack), contents.getAspects()[0].getName())
                : super.getName(stack);
    }

    @Override
    public boolean ignoreContainedAspects() { return true; }

    @Override
    public InteractionResult onItemUseFirst(ItemStack held, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || held.isEmpty() || context.getLevel().isClientSide)
            return InteractionResult.PASS;
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof EssentiaJarBlockEntity jar))
            return InteractionResult.PASS;

        AspectList contents = getAspects(held);
        Aspect template = contents == null ? null : contents.getAspects()[0];
        // The core preserves BETA26: an occupied jar uses its existing aspect,
        // an empty jar needs a template, and label facing derives from player yaw.
        if (jar.applyLabel(player, context.getClickedFace(), template)) {
            held.shrink(1); // BETA26 also consumes labels in creative.
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, .4F, 1F);
        }
        // Labelable blocks consume the interaction even if no label was applied.
        return InteractionResult.SUCCESS;
    }
}
