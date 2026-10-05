package thaumcraft.golemancy.seals.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.catalog.CatalogItem;
import thaumcraft.catalog.CatalogModule;
import javax.annotation.Nullable;
import java.util.List;

/** Flattened item IDs preserve original metadata forms and their existing model bindings. */
public final class ItemSealPlacer extends CatalogItem {
    public ItemSealPlacer(CatalogModule.Spec spec){super(spec);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){
        String type=SealRegistry.keyForMetadata(spec().legacyMetadata());
        if(type==null||context.getPlayer()==null||context.getPlayer().isShiftKeyDown())return InteractionResult.PASS;
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        return context.getPlayer() instanceof ServerPlayer player&&SealRegistry.place(player,new SealPos(context.getClickedPos(),context.getClickedFace()),type,stack)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override public boolean doesSneakBypassUse(ItemStack stack,net.minecraft.world.level.LevelReader level,net.minecraft.core.BlockPos pos,net.minecraft.world.entity.player.Player player){return true;}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> tooltip,TooltipFlag flag){
        if(flag.isAdvanced())tooltip.add(Component.translatable("thaumcraft.catalog.original_variant",spec().legacyItem(),spec().legacyMetadata()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
