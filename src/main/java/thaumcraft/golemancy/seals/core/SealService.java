package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Dimension-owned persistent seals and transient BETA26 task/provision queues. */
public final class SealService extends SavedData {
    private final LinkedHashMap<SealPos,SealData> seals=new LinkedHashMap<>();
    private final LinkedHashMap<Integer,SealTask> tasks=new LinkedHashMap<>();
    private final Map<Integer,UUID> claims=new HashMap<>();
    private final Set<Integer> completing=new HashSet<>();
    private final ArrayList<SealProvision> provisions=new ArrayList<>();
    private final Set<SealPos> removalGuard=new HashSet<>();
    private ServerLevel level;
    private long lastTick=Long.MIN_VALUE;
    public static SealService get(ServerLevel level) {
        SealService service=level.getDataStorage().computeIfAbsent(SealService::load,SealService::new,"thaumcraft_seals");service.level=level;return service;
    }
    public static SealService load(CompoundTag tag) {
        SealService service=new SealService();ListTag list=tag.getList("Seals",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(list.size(),100000);i++) {SealData seal=SealData.load(list.getCompound(i));if(seal!=null)service.seals.putIfAbsent(seal.position(),seal);}return service;
    }
    @Override public CompoundTag save(CompoundTag tag) {tag.putInt("Version",1);ListTag list=new ListTag();for(SealData seal:seals.values())list.add(seal.save());tag.put("Seals",list);return tag;}
    public SealData seal(SealPos pos){return seals.get(pos);}
    /** Objects are server-owned; this immutable collection cannot alter the index. */
    public List<SealData> seals(){return List.copyOf(seals.values());}
    public List<SealData> inRange(BlockPos center,int range){return seals.values().stream().filter(s->s.position().pos().distSqr(center)<=range*(double)range).toList();}
    public boolean add(SealData seal) {
        if(seal==null||SealRegistry.behavior(seal.type())==null||seals.size()>=100000||removalGuard.contains(seal.position())||seals.putIfAbsent(seal.position(),seal)!=null)return false;
        setDirty();if(level!=null)SealNetwork.sync(level,seal);return true;
    }
    public boolean remove(SealPos pos,boolean quiet) {
        if(pos==null||!removalGuard.add(pos))return false;
        try {
            SealData seal=seals.remove(pos);if(seal==null)return false;
            suspend(pos);for(SealProvision request:provisions)if(pos.equals(request.seal()))request.invalid(true);
            SealBehavior behavior=SealRegistry.behavior(seal.type());
            if(level!=null&&behavior!=null){try{behavior.onRemoval(level,seal);}catch(RuntimeException ignored){/* The index and payment stay removed even if an addon callback fails. */}}
            if(!quiet&&level!=null&&!level.restoringBlockSnapshots){ItemStack drop=SealRegistry.stack(seal.type());if(!drop.isEmpty()){
                Vec3 center=Vec3.atCenterOf(pos.pos()).add(pos.face().getStepX()/1.7,pos.face().getStepY()/1.7,pos.face().getStepZ()/1.7);
                ItemEntity entity=new ItemEntity(level,center.x,center.y,center.z,drop);entity.setDefaultPickUpDelay();level.addFreshEntity(entity);
            }}
            setDirty();if(level!=null)SealNetwork.remove(level,pos);return true;
        } finally {removalGuard.remove(pos);}
    }
    public void changed(SealData seal) {if(seal!=null&&seals.get(seal.position())==seal){seal.changed();setDirty();if(level!=null)SealNetwork.sync(level,seal);}}
    public boolean canConfigure(ServerPlayer player,SealData seal) {return player!=null&&level==player.level()&&player.isAlive()&&!player.isSpectator()&&seal!=null&&seals.get(seal.position())==seal&&level.hasChunkAt(seal.position().pos())&&player.distanceToSqr(Vec3.atCenterOf(seal.position().pos()))<=64&&level.mayInteract(player,seal.position().pos());}
    public void addTask(SealTask task) {
        if(task==null||task.sealPosition()==null||!seals.containsKey(task.sealPosition()))return;
        // Original TASK_LIMIT evicts the oldest arbitrary ticket; a deterministic FIFO avoids leaked reservations.
        if(tasks.size()>=10000){SealTask old=tasks.values().iterator().next();tasks.remove(old.id());claims.remove(old.id());suspensionCallback(old);}
        tasks.putIfAbsent(task.id(),task);
    }
    public SealTask task(int id){return tasks.get(id);}
    public List<SealTask> tasks(){return List.copyOf(tasks.values());}
    public void suspend(SealPos pos){for(SealTask task:tasks.values())if(pos.equals(task.sealPosition()))task.suspended(true);}
    public boolean tagsValid(SealData seal,SealWorker worker) {
        if(seal==null||worker==null)return false;SealBehavior behavior=SealRegistry.behavior(seal.type());if(behavior==null)return false;
        if(seal.locked()&&!seal.owner().equals(worker.ownerId()))return false;
        if(worker.color()>0&&seal.color()>0&&worker.color()!=seal.color())return false;
        return worker.traits().containsAll(behavior.requiredTraits())&&Collections.disjoint(worker.traits(),behavior.forbiddenTraits());
    }
    public boolean eligible(SealTask task,SealWorker worker) {
        if(level==null||task==null||worker==null||task.suspended()||task.completed()||task.lifespan()<=0||worker.mob().isRemoved()||!worker.mob().isAlive()||worker.mob().level()!=level||worker.inCombat())return false;
        if(task.golemUUID()!=null&&!task.golemUUID().equals(worker.mob().getUUID()))return false;
        BlockPos pos=task.position(level);if(pos==null||!level.hasChunkAt(pos)||!worker.withinHome(pos))return false;
        if(task.type()==1){Entity entity=task.entity(level);if(entity==null||!entity.isAlive()){task.suspended(true);return false;}}
        SealData seal=seals.get(task.sealPosition());if(seal==null||!level.hasChunkAt(seal.position().pos())||stopped(seal)||!tagsValid(seal,worker))return false;
        SealBehavior behavior=SealRegistry.behavior(seal.type());
        try{return behavior.canPerform(level,seal,worker,task)&&seals.get(seal.position())==seal&&!task.suspended();}catch(RuntimeException invalid){task.suspended(true);return false;}
    }
    public List<SealTask> sorted(SealWorker worker,int type) {
        if(level==null)return List.of();List<SealTask> out=new ArrayList<>();for(SealTask task:tasks.values())if(!task.reserved()&&task.type()==type&&eligible(task,worker))out.add(task);
        // Original task distance is squared block-center distance, priority subtracts 256 per level.
        out.sort(Comparator.comparingDouble(t->worker.mob().distanceToSqr(Vec3.atCenterOf(t.position(level)))-t.priority()*256.0));return out;
    }
    public boolean claim(SealTask task,SealWorker worker) {
        if(task==null||tasks.get(task.id())!=task||task.reserved()||!eligible(task,worker))return false;
        task.reserved(true);claims.put(task.id(),worker.mob().getUUID());SealData seal=seals.get(task.sealPosition());try{SealRegistry.behavior(seal.type()).onStarted(level,seal,worker,task);}catch(RuntimeException invalid){task.suspended(true);task.reserved(false);claims.remove(task.id());return false;}
        boolean valid=tasks.get(task.id())==task&&seals.get(task.sealPosition())==seal;
        if(!valid){task.reserved(false);claims.remove(task.id());}return valid;
    }
    public boolean complete(SealTask task,SealWorker worker) {
        if(task==null||tasks.get(task.id())!=task||!task.reserved()||task.completed()||task.suspended()||worker==null||!worker.mob().getUUID().equals(claims.get(task.id()))||!eligible(task,worker)||!withinCompletionRange(task,worker)||!completing.add(task.id()))return false;
        SealData seal=seals.get(task.sealPosition());try{boolean done=SealRegistry.behavior(seal.type()).onCompletion(level,seal,worker,task);task.completed(done);return done;}catch(RuntimeException invalid){task.suspended(true);return false;}finally{completing.remove(task.id());}
    }
    public void release(SealTask task) {if(task==null)return;if(task.completed()&&!task.suspended())task.suspended(true);task.reserved(false);claims.remove(task.id());}
    public void release(SealTask task,SealWorker worker){if(task!=null&&worker!=null&&worker.mob().getUUID().equals(claims.get(task.id())))release(task);}
    private boolean withinCompletionRange(SealTask task,SealWorker worker){
        // Block AI can finish two blocks from a cardinal adjacent stand position: at most three
        // from the source center. Entity AI uses the original width-dependent squared threshold.
        if(task.type()==0)return worker.mob().distanceToSqr(task.position(level).getCenter())<=9;
        Entity entity=task.entity(level);return entity!=null&&worker.mob().distanceToSqr(entity)<=3.5+Math.pow(entity.getBbWidth()/2.0,2);
    }
    public boolean stopped(SealData seal){return seal.redstone()&&(level.hasNeighborSignal(seal.position().pos())||level.hasNeighborSignal(seal.position().pos().relative(seal.position().face())));}
    public SealProvision requestProvision(SealData target,ItemStack stack){return requestProvision(target,stack,0);}
    public SealProvision requestProvision(SealData target,ItemStack stack,int ui){return target==null?null:requestProvision(new SealProvision(target.position(),null,null,null,stack,ui,now()));}
    public SealProvision requestProvision(BlockPos pos,Direction side,ItemStack stack,int ui){return pos==null||side==null?null:requestProvision(new SealProvision(null,pos,side,null,stack,ui,now()));}
    public SealProvision requestProvision(Entity entity,ItemStack stack,int ui){return entity==null||entity.level()!=level?null:requestProvision(new SealProvision(null,null,null,entity.getUUID(),stack,ui,now()));}
    private SealProvision requestProvision(SealProvision request) {
        if(request.stack().isEmpty()||request.stack().getCount()>request.stack().getMaxStackSize())return null;
        for(SealProvision old:provisions)if(!old.invalid()&&old.equals(request))return old;
        if(provisions.size()>=1000)provisions.remove(0);provisions.add(request);return request;
    }
    public List<SealProvision> provisions(){return List.copyOf(provisions);}
    public long now(){return level==null?0:level.getGameTime();}
    public void cleanupTasks() {
        List<SealTask> snapshot=List.copyOf(tasks.values());for(SealTask ticket:snapshot){
            if(tasks.get(ticket.id())!=ticket)continue;
            if(!ticket.suspended()&&ticket.lifespan()>0)ticket.lifespan(ticket.lifespan()-1);
            else {tasks.remove(ticket.id());claims.remove(ticket.id());suspensionCallback(ticket);}
        }
    }
    private void suspensionCallback(SealTask task){SealData seal=seals.get(task.sealPosition());SealBehavior behavior=seal==null?null:SealRegistry.behavior(seal.type());if(behavior!=null&&level!=null)try{behavior.onSuspension(level,seal,task);}catch(RuntimeException ignored){} }
    public void tick() {
        if(level==null||lastTick==now())return;lastTick=now();if(now()%20==0)cleanupTasks();
        for(SealData seal:List.copyOf(seals.values())){
            if(seals.get(seal.position())!=seal||!level.hasChunkAt(seal.position().pos()))continue;
            SealBehavior behavior=SealRegistry.behavior(seal.type());
            try{
                if(behavior==null||(now()%20==0&&!behavior.canPlace(level,seal.position()))){remove(seal.position(),false);continue;}
                if(stopped(seal)){if(!seal.runtime().getBoolean("Stopped"))suspend(seal.position());seal.runtime().putBoolean("Stopped",true);continue;}
                seal.runtime().putBoolean("Stopped",false);behavior.tick(level,seal,this);
            }catch(RuntimeException invalid){remove(seal.position(),false);}
        }
        // Provision cleanup is performed by provider behavior in BETA26; bounded stale requests also
        // expire here so dimensions without a currently loaded provider do not retain entity UUIDs forever.
        provisions.removeIf(p->p.invalid()||p.timeoutTick()<now()||(p.linkedTask()!=null&&(p.linkedTask().suspended()||p.linkedTask().completed())));
    }
}
