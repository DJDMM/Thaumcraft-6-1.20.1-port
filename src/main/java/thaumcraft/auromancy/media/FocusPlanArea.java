package thaumcraft.auromancy.media;

import net.minecraft.core.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.focus.FocusStacks;
import java.util.*;

/** Original area radii/face offsets and connected exposed-surface planning. Maximum343 blocks. */
public final class FocusPlanArea {
    private FocusPlanArea(){}
    public static ItemStack caster(Player player){
        if(FocusSelection.isCaster(player.getMainHandItem()))return player.getMainHandItem();
        return FocusSelection.isCaster(player.getOffhandItem())?player.getOffhandItem():ItemStack.EMPTY;
    }
    public static int radius(ItemStack stack,String axis){
        String key="area"+axis;return stack.hasTag()&&stack.getTag().contains(key,Tag.TAG_INT)?Math.max(0,Math.min(3,stack.getTag().getInt(key))):3;
    }
    public static int dimension(ItemStack stack){return stack.hasTag()&&stack.getTag().contains("aread",Tag.TAG_INT)?Math.max(0,Math.min(3,stack.getTag().getInt("aread"))):0;}
    public static boolean cycle(ServerPlayer player,int mode){
        if(player==null||!player.getServer().isSameThread()||!player.isAlive()||player.isSpectator()||mode<0||mode>1)return false;
        ItemStack caster=caster(player);var plan=FocusStacks.readPlan(FocusSelection.installed(caster));
        if(plan.isEmpty()||plan.get().graph().nodes().stream().noneMatch(node->node.key().equals("thaumcraft.PLAN")))return false;
        int dimension=dimension(caster);var tag=caster.getOrCreateTag();
        if(mode==1)tag.putInt("aread",(dimension+1)%4);
        else{
            for(String axis:List.of("x","y","z")){
                if(dimension==0||dimension==1&&axis.equals("x")||dimension==2&&axis.equals("z")||dimension==3&&axis.equals("y"))tag.putInt("area"+axis,(radius(caster,axis)+1)%4);
            }
        }
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();return true;
    }
    public static List<BlockHitResult> targets(Player player,Vec3 source,Vec3 direction,int method){
        if(player==null||!PaidFocusContinuation.finite(source)||!PaidFocusContinuation.finite(direction)||direction.lengthSqr()<1e-12||method<0||method>1)return List.of();
        Vec3 end=source.add(direction.normalize().scale(16));
        if(!loadedRay(player.level(),source,end))return List.of();
        var hit=player.level().clip(new ClipContext(source,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK)return List.of();
        return blocks(player.level(),caster(player),hit.getBlockPos(),hit.getDirection(),method).stream()
                .map(pos->new BlockHitResult(Vec3.atCenterOf(pos),hit.getDirection(),pos,false)).toList();
    }
    public static List<BlockPos> blocks(Level level,ItemStack stack,BlockPos origin,Direction face,int method){
        if(stack.isEmpty()||face==null||method<0||method>1||!loaded(level,origin))return List.of();
        int x=radius(stack,"x"),y=radius(stack,"y"),z=radius(stack,"z");var result=new ArrayList<BlockPos>();
        if(method==0){
            BlockPos center=origin.offset(-x*face.getStepX(),-y*face.getStepY(),-z*face.getStepZ());
            for(BlockPos cursor:BlockPos.betweenClosed(center.offset(-x,-y,-z),center.offset(x,y,z)))
                if(loaded(level,cursor)&&!level.isEmptyBlock(cursor))result.add(cursor.immutable());
        }else{
            BlockState original=level.getBlockState(origin);var checked=new HashSet<BlockPos>();var pending=new ArrayDeque<BlockPos>();pending.add(origin);
            while(!pending.isEmpty()&&checked.size()<1024){
                BlockPos cursor=pending.removeFirst();if(!checked.add(cursor))continue;
                boolean inside=switch(face.getAxis()){
                    case Y->Math.abs(cursor.getX()-origin.getX())<=x&&Math.abs(cursor.getZ()-origin.getZ())<=z;
                    case Z->Math.abs(cursor.getX()-origin.getX())<=z&&Math.abs(cursor.getY()-origin.getY())<=x;
                    case X->Math.abs(cursor.getY()-origin.getY())<=x&&Math.abs(cursor.getZ()-origin.getZ())<=z;
                };
                if(!inside||!loaded(level,cursor)||level.isEmptyBlock(cursor)||level.getBlockState(cursor)!=original||!exposed(level,cursor))continue;
                result.add(cursor);
                for(Direction direction:Direction.values())if(direction.getAxis()!=face.getAxis())pending.addLast(cursor.relative(direction));
            }
        }
        result.sort(Comparator.comparingDouble(origin::distSqr));return List.copyOf(result);
    }
    private static boolean exposed(Level level,BlockPos pos){
        for(Direction direction:Direction.values()){BlockPos neighbour=pos.relative(direction);if(loaded(level,neighbour)&&!level.getBlockState(neighbour).canOcclude())return true;}return false;
    }
    private static boolean loaded(Level level,BlockPos pos){
        return level.isInWorldBounds(pos)&&(level instanceof ServerLevel server?server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null:level.hasChunkAt(pos));
    }
    private static boolean loadedRay(Level level,Vec3 source,Vec3 end){
        int minX=BlockPos.containing(Math.min(source.x,end.x),0,0).getX()>>4,maxX=BlockPos.containing(Math.max(source.x,end.x),0,0).getX()>>4;
        int minZ=BlockPos.containing(0,0,Math.min(source.z,end.z)).getZ()>>4,maxZ=BlockPos.containing(0,0,Math.max(source.z,end.z)).getZ()>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level instanceof ServerLevel server?server.getChunkSource().getChunkNow(x,z)==null:!level.hasChunk(x,z))return false;
        return true;
    }
}
