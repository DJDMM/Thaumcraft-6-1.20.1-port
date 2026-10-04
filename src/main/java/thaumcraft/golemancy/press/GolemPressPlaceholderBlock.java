package thaumcraft.golemancy.press;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.catalog.blocks.CatalogBlocks;
import java.util.List;

/** Invisible paid multiblock members retain their original collision, light and dismantling drops. */
public final class GolemPressPlaceholderBlock extends Block {
    private final String id;
    public GolemPressPlaceholderBlock(String id) {
        super(BlockBehaviour.Properties.of().strength(2.5f,2.5f).sound(SoundType.STONE).noOcclusion()
                .lightLevel(s->id.equals("placeholder_cauldron")?13:0).pushReaction(PushReaction.BLOCK));this.id=id;
    }
    public static Block original(String id) {
        return switch(id) {case "placeholder_bars"->Blocks.IRON_BARS;case "placeholder_anvil"->Blocks.ANVIL;
            case "placeholder_cauldron"->Blocks.CAULDRON;case "placeholder_table"->CatalogBlocks.block("table_stone");default->null;};
    }
    @Override public RenderShape getRenderShape(BlockState state) {return RenderShape.INVISIBLE;}
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder context) {return List.of(new ItemStack(original(id)));}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(level.isClientSide)return InteractionResult.SUCCESS;
        BlockPos anchor=GolemPressFormation.findAnchor(level,pos);
        if(anchor!=null&&player instanceof ServerPlayer server&&level.getBlockEntity(anchor) instanceof GolemPressBlockEntity press) {
            thaumcraft.research.ResearchNetwork.sync(server);
            NetworkHooks.openScreen(server,press,anchor);
        }
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving) {
        if(!state.is(replacement.getBlock())&&!level.isClientSide&&!level.restoringBlockSnapshots) {
            BlockPos anchor=GolemPressFormation.findAnchor(level,pos);
            if(anchor!=null)GolemPressFormation.destroy(level,anchor,pos);
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
}
