package thaumcraft.essentia.thaumatorium;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.*;
import java.util.*;

/** Original three-block Salis blueprint: crucible retained, two constructs become facing machine halves. */
public final class ThaumatoriumFormation {
    private ThaumatoriumFormation() {}
    public static InteractionResult use(UseOnContext context) {
        Level level=context.getLevel();BlockPos base=findBase(level,context.getClickedPos());
        if(base==null)return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player)||!player.mayBuild()||player.isSpectator()||!KnowledgeStore.get(player).isResearchKnown("THAUMATORIUM"))return InteractionResult.FAIL;
        for(BlockPos pos:List.of(context.getClickedPos(),base,base.above()))
            if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        Direction facing=player.getDirection().getOpposite();
        Map<BlockPos,BlockState> replacements=new LinkedHashMap<>();
        replacements.put(base,CatalogBlocks.block("thaumatorium").defaultBlockState().setValue(ThaumatoriumBlock.FACING,facing));
        replacements.put(base.above(),CatalogBlocks.block("thaumatorium_top").defaultBlockState().setValue(ThaumatoriumBlock.FACING,facing));
        List<BlockSnapshot> before=new ArrayList<>();for(BlockPos pos:replacements.keySet())before.add(BlockSnapshot.create(level.dimension(),level,pos));
        boolean capturing=level.captureBlockSnapshots,committed=false;int start=level.capturedBlockSnapshots.size();level.captureBlockSnapshots=true;
        try {
            for(var replacement:replacements.entrySet())if(!level.setBlock(replacement.getKey(),replacement.getValue(),3))return InteractionResult.FAIL;
            if(ForgeEventFactory.onMultiBlockPlace(player,before,context.getClickedFace()))return InteractionResult.FAIL;
            committed=true;level.captureBlockSnapshots=false;
            for(var snapshot:before) {var state=level.getBlockState(snapshot.getPos());state.onPlace(level,snapshot.getPos(),snapshot.getReplacedBlock(),false);level.markAndNotifyBlock(snapshot.getPos(),level.getChunkAt(snapshot.getPos()),snapshot.getReplacedBlock(),state,3,512);}
            if(!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
            level.playSound(null,base,net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1);
            ResearchEvents.recordCraft(player,new net.minecraft.world.item.ItemStack(CatalogBlocks.block("thaumatorium")));
            return InteractionResult.CONSUME;
        } finally {
            if(!committed) {boolean restoring=level.restoringBlockSnapshots;level.restoringBlockSnapshots=true;try {for(int index=before.size()-1;index>=0;index--)before.get(index).restore(true,false);}finally {level.restoringBlockSnapshots=restoring;}}
            if(level.capturedBlockSnapshots.size()>start)level.capturedBlockSnapshots.subList(start,level.capturedBlockSnapshots.size()).clear();level.captureBlockSnapshots=capturing;
        }
    }
    private static BlockPos findBase(Level level,BlockPos clicked) {
        for(int offset=-1;offset<=1;offset++) {
            BlockPos base=clicked.above(offset);
            if(level.hasChunkAt(base.below())&&level.hasChunkAt(base.above())&&level.getBlockState(base.below()).is(AlchemyModule.CRUCIBLE.get())
                    &&level.getBlockState(base).is(CatalogBlocks.block("metal_alchemical"))&&level.getBlockState(base.above()).is(CatalogBlocks.block("metal_alchemical")))return base;
        }
        return null;
    }
}
