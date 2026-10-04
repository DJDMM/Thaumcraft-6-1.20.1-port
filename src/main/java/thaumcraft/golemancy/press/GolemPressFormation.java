package thaumcraft.golemancy.press;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.KnowledgeStore;
import java.util.*;

/** Pinned 2x2x2 blueprint; null cells are wildcards, not invented air requirements. */
public final class GolemPressFormation {
    public record Part(BlockPos pos,String id) {}
    public record Layout(Direction facing,List<Part> parts) {
        public BlockPos anchor() {return parts.stream().filter(p->p.id().equals("golem_builder")).findFirst().orElseThrow().pos();}
    }
    private static final Set<BlockPos> DISMANTLING=new HashSet<>();
    private GolemPressFormation() {}
    public static List<Part> parts(BlockPos corner,Direction facing) {
        // source array[y][x][z], y=0 top, y=1 bottom; Matrix.Rotate90DegRight.
        int[][] coordinates={{1,1,0},{0,0,0},{0,0,1},{1,0,0},{1,0,1}};
        String[] ids={"placeholder_bars","placeholder_cauldron","placeholder_anvil","golem_builder","placeholder_table"};
        List<Part> result=new ArrayList<>();int turns=3-facing.get2DDataValue();
        for(int i=0;i<coordinates.length;i++) {int x=coordinates[i][0],z=coordinates[i][2];
            for(int rotation=0;rotation<turns;rotation++) {int oldX=x;x=z;z=1-oldX;}
            result.add(new Part(corner.offset(x,coordinates[i][1],z),ids[i]));}
        return List.copyOf(result);
    }
    public static Layout match(Level level,BlockPos clicked) {
        for(int y=-2;y<=0;y++)for(int x=-2;x<=0;x++)for(int z=-2;z<=0;z++) {
            BlockPos corner=clicked.offset(x,y,z);
            for(Direction facing:List.of(Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST)) {
                List<Part> parts=parts(corner,facing);boolean matches=true;
                for(var part:parts) {
                    if(!level.hasChunkAt(part.pos())) {matches=false;break;}
                    BlockState state=level.getBlockState(part.pos());
                    if(part.id().equals("golem_builder")) {
                        if(!state.equals(Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP))) {matches=false;break;}
                    } else if(!matchesSource(part.id(),state)) {matches=false;break;}
                }
                if(matches)return new Layout(facing,parts);
            }
        }
        return null;
    }
    private static boolean matchesSource(String id,BlockState state) {
        // 1.12 held water levels/anvil damage in metadata of the same source block.
        if(id.equals("placeholder_cauldron"))return state.is(Blocks.CAULDRON)||state.is(Blocks.WATER_CAULDRON);
        if(id.equals("placeholder_anvil"))return state.is(Blocks.ANVIL)||state.is(Blocks.CHIPPED_ANVIL)||state.is(Blocks.DAMAGED_ANVIL);
        return state.is(GolemPressPlaceholderBlock.original(id));
    }
    public static InteractionResult use(UseOnContext context) {
        Level level=context.getLevel();Layout layout=match(level,context.getClickedPos());if(layout==null)return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player)||!player.mayBuild()||player.isSpectator()
                ||!KnowledgeStore.get(player).isResearchKnown("MINDCLOCKWORK"))return InteractionResult.FAIL;
        for(var part:layout.parts())if(!level.mayInteract(player,part.pos())||!player.mayUseItemAt(part.pos(),context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        List<BlockSnapshot> before=new ArrayList<>();for(var part:layout.parts())before.add(BlockSnapshot.create(level.dimension(),level,part.pos()));
        boolean capturing=level.captureBlockSnapshots,committed=false;int start=level.capturedBlockSnapshots.size();level.captureBlockSnapshots=true;
        try {
            for(var part:layout.parts()) {var state=CatalogBlocks.block(part.id()).defaultBlockState();
                if(part.id().equals("golem_builder"))state=state.setValue(GolemPressBlock.FACING,layout.facing());
                if(!level.setBlock(part.pos(),state,3))return InteractionResult.FAIL;}
            if(ForgeEventFactory.onMultiBlockPlace(player,before,context.getClickedFace()))return InteractionResult.FAIL;
            committed=true;level.captureBlockSnapshots=false;
            for(var snapshot:before) {var state=level.getBlockState(snapshot.getPos());state.onPlace(level,snapshot.getPos(),snapshot.getReplacedBlock(),false);
                level.markAndNotifyBlock(snapshot.getPos(),level.getChunkAt(snapshot.getPos()),snapshot.getReplacedBlock(),state,3,512);}
            if(!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
            level.playSound(null,layout.anchor(),net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1);
            // Original shared DustTrigger emits infernal-furnace crafting regardless of blueprint.
            // Formation does not fabricate a golem item craft proof or complete MINDCLOCKWORK.
            return InteractionResult.CONSUME;
        } finally {
            if(!committed) {boolean restoring=level.restoringBlockSnapshots;level.restoringBlockSnapshots=true;
                try {for(int i=before.size()-1;i>=0;i--)before.get(i).restore(true,false);}finally {level.restoringBlockSnapshots=restoring;}}
            if(level.capturedBlockSnapshots.size()>start)level.capturedBlockSnapshots.subList(start,level.capturedBlockSnapshots.size()).clear();level.captureBlockSnapshots=capturing;
        }
    }
    public static BlockPos findAnchor(Level level,BlockPos member) {
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++) {BlockPos pos=member.offset(x,y,z);
            if(level.hasChunkAt(pos)&&level.getBlockState(pos).is(CatalogBlocks.block("golem_builder")))return pos;}
        return null;
    }
    public static void destroy(Level level,BlockPos anchor,BlockPos broken) {
        if(level.isClientSide||level.restoringBlockSnapshots||!DISMANTLING.add(anchor.immutable()))return;
        try {
            for(int x=-1;x<=1;x++)for(int y=0;y<=1;y++)for(int z=-1;z<=1;z++) {BlockPos pos=anchor.offset(x,y,z);
                if(pos.equals(broken)||!level.hasChunkAt(pos))continue;
                var block=level.getBlockState(pos).getBlock();
                for(String id:List.of("placeholder_bars","placeholder_cauldron","placeholder_anvil","placeholder_table"))
                    if(block==CatalogBlocks.block(id))level.setBlockAndUpdate(pos,GolemPressPlaceholderBlock.original(id).defaultBlockState());
            }
            if(!anchor.equals(broken))level.setBlockAndUpdate(anchor,Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP));
        } finally {DISMANTLING.remove(anchor);}
    }
}
