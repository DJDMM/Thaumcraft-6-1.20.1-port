package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/** BETA26 AIGoto block/entity reservation, navigation, ten-tick retry and 1000-tick abandonment. */
public final class SealTaskGoal extends Goal {
    private final SealWorker worker;
    private SealTask task;
    private BlockPos adjacent,previous;
    private int counter=-1,cooldown,pause;
    private double minDistance=4;
    public SealTaskGoal(SealWorker worker){this.worker=worker;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    private SealService service(){return SealService.get((ServerLevel)worker.mob().level());}
    public SealTask task(){return task;}
    @Override public boolean canUse(){
        if(!(worker.mob().level() instanceof ServerLevel level)||worker.inCombat()||!worker.mob().isAlive())return false;
        if(cooldown>0){cooldown--;return false;}cooldown=5;
        // BETA26 installs AIGotoEntity before AIGotoBlock (priorities 3 and 4).
        // A provider's assigned entity delivery must therefore beat an eligible Store block
        // ticket; otherwise Store can take the cargo before it reaches the requesting player.
        // Select only when this goal is idle, retaining the original non-preemption behavior.
        SealService service=service();for(int type=1;type>=0;type--)for(SealTask candidate:service.sorted(worker,type)){
            BlockPos pos=candidate.position(level);if(pos==null)continue;
            BlockPos next=type==0?adjacentSpace(level,pos):null;minDistance=type==0?4:3.5+Math.pow(candidate.entity(level).getBbWidth()/2.0,2);
            if(!reachable(level,candidate,next)||!service.claim(candidate,worker))continue;
            task=candidate;adjacent=next;return true;
        }return false;
    }
    @Override public void start(){counter=0;pause=0;previous=null;moveTo();}
    @Override public boolean canContinueToUse(){return task!=null&&counter>=0&&counter<=1000&&service().task(task.id())==task&&task.reserved()&&service().eligible(task,worker);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    private void moveTo(){
        if(task==null||!(worker.mob().level() instanceof ServerLevel level))return;
        if(task.type()==1){Entity entity=task.entity(level);if(entity!=null)worker.mob().getNavigation().moveTo(entity,worker.moveSpeed());}
        else{BlockPos pos=adjacent==null?task.position(level):adjacent;if(pos!=null)worker.mob().getNavigation().moveTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,worker.moveSpeed());}
    }
    @Override public void tick(){
        if(task==null||!(worker.mob().level() instanceof ServerLevel level))return;
        BlockPos pos=task.position(level);if(pos==null){task.suspended(true);return;}
        if(task.type()==1){Entity target=task.entity(level);if(target==null){task.suspended(true);return;}worker.mob().getLookControl().setLookAt(target,10,worker.mob().getMaxHeadXRot());}
        else worker.mob().getLookControl().setLookAt(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,10,worker.mob().getMaxHeadXRot());
        if(pause--<=0){
            double distance=task.type()==0?worker.mob().distanceToSqr(Vec3.atCenterOf(adjacent==null?pos:adjacent)):worker.mob().distanceToSqr(task.entity(level));
            if(distance>minDistance){
                task.completed(false);counter++;
                if(counter%5==0){
                    if(previous!=null&&previous.equals(worker.mob().blockPosition())){
                        Vec3 random=worker.mob() instanceof net.minecraft.world.entity.PathfinderMob pathfinder?DefaultRandomPos.getPosTowards(pathfinder,6,4,Vec3.atCenterOf(pos),Math.PI/2):null;
                        if(random!=null&&level.hasChunkAt(BlockPos.containing(random)))worker.mob().getNavigation().moveTo(random.x+.5,random.y+.5,random.z+.5,worker.moveSpeed());
                    }else moveTo();previous=worker.mob().blockPosition();
                }
            }else{
                service().complete(task,worker);
                if(task.completed()){counter=0;pause=0;}else{pause=10;counter++;}counter--;
            }
        }
    }
    @Override public void stop(){if(task!=null)service().release(task,worker);worker.mob().getNavigation().stop();task=null;adjacent=null;counter=-1;}
    private BlockPos adjacentSpace(ServerLevel level,BlockPos pos){
        double distance=Double.MAX_VALUE;BlockPos closest=null;
        for(Direction direction:Direction.Plane.HORIZONTAL){BlockPos next=pos.relative(direction);if(!level.hasChunkAt(next)||!level.getBlockState(next).getCollisionShape(level,next).isEmpty())continue;
            double d=worker.mob().distanceToSqr(Vec3.atCenterOf(next));if(d<distance){distance=d;closest=next;}}
        return closest;
    }
    private boolean reachable(ServerLevel level,SealTask candidate,BlockPos next){
        BlockPos pos=candidate.position(level);if(pos==null)return false;
        if(candidate.type()==0&&worker.mob().distanceToSqr(Vec3.atCenterOf(pos))<minDistance)return true;
        if(candidate.type()==1&&worker.mob().distanceToSqr(candidate.entity(level))<minDistance)return true;
        Path path=candidate.type()==0?worker.mob().getNavigation().createPath(next==null?pos:next,0):worker.mob().getNavigation().createPath(candidate.entity(level),0);
        if(path==null||path.getEndNode()==null)return false;
        Node end=path.getEndNode();int x=end.x-pos.getX(),z=end.z-pos.getZ(),y=end.y-pos.getY();if(x==0&&z==0&&y==2)y--;
        return candidate.type()==0?x*x+y*y+z*z<2.25:x*x+z*z<minDistance;
    }
}
