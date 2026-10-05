package thaumcraft.golemancy.seals.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import thaumcraft.golemancy.seals.core.*;

import java.util.*;

/** All sixteen seal definitions registered by pinned ConfigItems.initSeals in 6.1.BETA26. */
public final class SealBehaviors {
    private SealBehaviors() {}
    private static final List<String> KEYS=List.of("pickup","pickup_advanced","fill","fill_advanced","empty","empty_advanced",
            "harvest","butcher","guard","guard_advanced","lumber","breaker","use","provider","stock","breaker_advanced");
    private static final List<SealBehavior> ALL=KEYS.stream().map(Beta26::new).map(x->(SealBehavior)x).toList();
    public static List<SealBehavior> all(){return ALL;}
    public static void register(){for(SealBehavior behavior:ALL)if(SealRegistry.behavior(behavior.key())==null)SealRegistry.registerBehavior(behavior);}
    public static SealBehavior get(String key){String full=key.contains(":")?key:"thaumcraft:"+key;return ALL.stream().filter(b->b.key().equals(full)).findFirst().orElse(null);}
    /** Replant instructions are the one original persisted behavior cache; tasks/scan counters are transient. */
    public static void writeCustom(SealData seal,CompoundTag tag){if(seal.type().equals("thaumcraft:harvest")&&seal.toggle("prep"))tag.put("replant",seal.runtime().getList("replant",Tag.TAG_COMPOUND).copy());}
    public static void readCustom(SealData seal,CompoundTag tag){if(seal.type().equals("thaumcraft:harvest")){
        ListTag clean=new ListTag(),raw=tag.getList("replant",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(raw.size(),4096);i++){CompoundTag entry=raw.getCompound(i);int face=entry.getByte("taskface");if(face<0||face>=6||ItemStack.of(entry).isEmpty())continue;CompoundTag copy=entry.copy();copy.remove("Task");clean.add(copy);}seal.runtime().put("replant",clean);
    }}

    private static final class Beta26 implements SealBehavior {
        private final String path,base;
        private final boolean advanced;
        private Beta26(String path){this.path=path;advanced=path.endsWith("_advanced");base=advanced?path.substring(0,path.length()-9):path;}
        @Override public String key(){return "thaumcraft:"+path;}
        @Override public int filterSlots(){return switch(base){case "stock","provider"->9;case "empty","fill","pickup","breaker"->advanced?9:1;case "use"->1;default->0;};}
        @Override public boolean hasStacksizeLimiters(){return base.equals("stock")||base.equals("fill");}
        @Override public boolean hasArea(){return Set.of("pickup","harvest","butcher","guard","lumber","breaker").contains(base);}
        @Override public List<Integer> categories(){return switch(base){
            case "pickup"->advanced?List.of(2,1,3,0,4):List.of(2,1,0,4);
            case "fill","empty"->advanced?List.of(1,3,0,4):List.of(1,0,4);
            case "breaker"->List.of(2,1,3,0,4);case "harvest"->List.of(2,3,0,4);
            case "guard"->advanced?List.of(2,3,0,4):List.of(2,0,4);
            case "lumber","butcher"->List.of(2,0,4);default->List.of(1,3,0,4);};}
        @Override public Map<String,Boolean> toggleDefaults(){LinkedHashMap<String,Boolean> map=new LinkedHashMap<>();
            if(Set.of("empty","fill","pickup","use","stock","provider").contains(base)){map.put("pmeta",true);map.put("pnbt",true);map.put("pore",false);map.put("pmod",false);}
            switch(base){
                case "empty"->{map.put("pcycle",false);map.put("pleave",false);}
                case "fill"->map.put("pexist",false);
                case "use"->{map.put("pleft",false);map.put("pempty",false);map.put("pemptyhand",false);map.put("psneak",false);map.put("ppro",false);}
                case "provider"->{map.put("psing",false);map.put("pleave",false);}
                case "breaker"->{map.put("pmeta",true);if(advanced)map.put("psilk",false);}
                case "harvest"->{map.put("prep",true);map.put("ppro",false);}
                case "guard"->{map.put("pmob",true);map.put("panimal",false);map.put("pplayer",false);}
            }return Collections.unmodifiableMap(map);
        }
        @Override public Set<String> requiredTraits(){return switch(base){
            case "guard"->advanced?Set.of("FIGHTER","SMART"):Set.of("FIGHTER");
            case "butcher"->Set.of("FIGHTER","SMART");case "harvest","use"->Set.of("DEFT","SMART");
            case "lumber"->Set.of("BREAKER","SMART");case "breaker"->advanced?Set.of("BREAKER","SMART"):Set.of("BREAKER");
            default->advanced?Set.of("SMART"):Set.of();};}
        @Override public Set<String> forbiddenTraits(){return Set.of("empty","fill","pickup","stock","provider").contains(base)?Set.of("CLUMSY"):Set.of();}
        @Override public boolean canPlace(ServerLevel level,SealPos position){return SealWorld.loaded(level,position.pos())&&switch(base){
            case "empty","provider","stock"->SealInventory.handler(level,position)!=null;
            case "use"->true;default->!level.isEmptyBlock(position.pos());};}
        @Override public void tick(ServerLevel level,SealData seal,SealService service){
            int delay=seal.runtime().getInt("Delay");seal.runtime().putInt("Delay",delay==Integer.MAX_VALUE?0:delay+1);
            switch(base){
                case "empty"->{if(delay%20==0){ItemStack stack=SealInventory.first(SealInventory.handler(level,seal.position()),s->matchesCycle(seal,s),seal.toggle("pleave"));
                    if(!stack.isEmpty())add(service,seal,SealTask.block(seal.position(),seal.position().pos()).lifespan(5),stack);}}
                case "fill"->{if(delay%20==0){SealTask old=service.task(seal.runtime().getInt("Watched"));if(old==null||old.reserved()||old.suspended()||old.completed())watched(service,seal);}}
                case "pickup"->{if(delay%5==0)for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,seal.bounds())){
                    if(item.onGround()&&!item.hasPickUpDelay()&&!item.getItem().isEmpty()&&seal.matches(item.getItem())&&!hasEntity(service,seal,item)){
                        add(service,seal,SealTask.entity(seal.position(),item),null);break;}}}
                case "stock"->{if(delay%20==0)stock(level,seal,service);}
                case "provider"->{if(delay%20==0)provide(level,seal,service);}
                case "use"->{if(delay%5==0&&seal.toggle("pempty")==level.isEmptyBlock(seal.position().pos())){
                    SealTask old=service.task(seal.runtime().getInt("Watched"));if(old==null||old.suspended()||old.completed())watched(service,seal);}}
                case "guard"->{if(delay%20==0)for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,seal.bounds()))if(guardTarget(level,seal,target)&&!hasEntity(service,seal,target))add(service,seal,SealTask.entity(seal.position(),target).lifespan(10),null);}
                case "butcher"->{if(delay%200==0&&!seal.runtime().getBoolean("Wait"))for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,seal.bounds())){
                    if(!butcherTarget(target))continue;long count=level.getEntitiesOfClass(LivingEntity.class,seal.bounds(),e->target.getClass().isInstance(e)&&butcherTarget(e)).size();
                    if(count>2){add(service,seal,SealTask.entity(seal.position(),target).lifespan(10),null);seal.runtime().putBoolean("Wait",true);break;}}}
                case "breaker","lumber"->{BlockPos pos=seal.posInArea(delay+1);if(!hasBlock(service,seal,pos)&&validBlock(level,seal,pos)){
                    SealTask task=SealTask.block(seal.position(),pos);if(base.equals("breaker"))task.data((int)(level.getBlockState(pos).getDestroySpeed(level,pos)*10));add(service,seal,task,null);}}
                case "harvest"->{if(delay%100==0)pruneReplants(seal,service);if(delay%5==0){int count=seal.runtime().getInt("Count");seal.runtime().putInt("Count",count==Integer.MAX_VALUE?0:count+1);BlockPos pos=seal.posInArea(count);
                    if(!hasBlock(service,seal,pos)&&SealWorld.grown(level,pos))add(service,seal,SealTask.block(seal.position(),pos),null);
                    else if(seal.toggle("prep")&&SealWorld.loaded(level,pos)&&level.isEmptyBlock(pos)){CompoundTag replant=replant(seal,pos);if(replant!=null&&!hasBlock(service,seal,pos)){
                        SealTask task=SealTask.block(seal.position(),pos).lifespan(300);task.payload().put("Replant",replant.copy());add(service,seal,task,null);replant.putInt("Task",task.id());}}}}
            }
        }
        @Override public boolean canPerform(ServerLevel level,SealData seal,SealWorker golem,SealTask task){
            BlockPos pos=task.position(level);if(pos==null||!SealWorld.loaded(level,pos))return false;
            switch(base){
                case "empty"->{ItemStack cached=ItemStack.of(task.payload().getCompound("Stack"));return !cached.isEmpty()&&golem.canCarry(cached,true)&&SealInventory.count(SealInventory.handler(level,seal.position()),s->ItemStack.isSameItemSameTags(cached,s))>(seal.toggle("pleave")?1:0);}
                case "pickup"->{return task.entity(level) instanceof ItemEntity item&&item.isAlive()&&golem.canCarry(item.getItem(),true)&&seal.matches(item.getItem());}
                case "fill"->{ItemStack carried=carried(seal,golem);if(carried.isEmpty())return false;IItemHandler inv=SealInventory.handler(level,seal.position());
                    if(inv==null)return amountAllowed(level,seal,carried)>0;
                    return (!seal.toggle("pexist")||SealInventory.count(inv,s->seal.matchesFilter(carried,s))>0)&&amountAllowed(level,seal,carried)>0&&SealInventory.room(inv,carried)>0;}
                case "stock"->{return false;}
                case "provider"->{SealProvision request=task.provision();if(request==null||request.invalid())return false;
                    Entity target=request.entityUUID()==null?null:level.getEntity(request.entityUUID());BlockPos destination=target==null?request.position():target.blockPosition();
                    if(destination==null||!SealWorld.loaded(level,destination)||!golem.withinHome(destination))return false;
                    SealData targetSeal=request.seal()==null?null:SealService.get(level).seal(request.seal());if(targetSeal!=null&&!SealService.get(level).tagsValid(targetSeal,golem))return false;
                    ItemStack wanted=request.stack();boolean holds=golem.carrying().stream().anyMatch(s->strict(wanted,s));
                    return task.data()==0?!holds&&golem.canCarry(wanted,true):holds;}
                case "use"->{if(seal.toggle("pemptyhand"))return true;ItemStack held=carried(seal,golem);if(!held.isEmpty())return true;
                    if(seal.toggle("ppro")&&!seal.blacklist()&&!seal.filter(0).isEmpty()){ItemStack wanted=seal.filter(0);if(!seal.toggle("pmeta"))wanted.setDamageValue(32767);SealService.get(level).requestProvision(seal,wanted);}return false;}
                case "breaker","lumber"->{return validBlock(level,seal,pos);}
                case "harvest"->{if(task.payload().contains("Replant",Tag.TAG_COMPOUND)){ItemStack seed=ItemStack.of(task.payload().getCompound("Replant"));boolean holds=golem.carrying().stream().anyMatch(s->ItemStack.isSameItemSameTags(seed,s));
                        if(!holds&&seal.toggle("ppro"))SealService.get(level).requestProvision(seal,seed.copyWithCount(1));return holds&&level.isEmptyBlock(pos);}return SealWorld.grown(level,pos);}
                case "guard"->{return task.entity(level) instanceof LivingEntity target&&target.isAlive()&&guardTarget(level,seal,target)&&!golem.mob().isAlliedTo(target);}
                case "butcher"->{return task.entity(level) instanceof LivingEntity target&&butcherTarget(target)&&!golem.mob().isAlliedTo(target);}
            }return false;
        }
        @Override public void onStarted(ServerLevel level,SealData seal,SealWorker golem,SealTask task){
            if(base.equals("fill")&&!SealService.get(level).stopped(seal))watched(SealService.get(level),seal);
            if((base.equals("guard")||base.equals("butcher"))&&task.entity(level) instanceof LivingEntity target){
                if(!golem.mob().isAlliedTo(target)&&(base.equals("guard")?guardTarget(level,seal,target):butcherTarget(target))){golem.mob().setTarget(target);golem.addRankXp(1);}
                task.suspended(true);seal.runtime().putBoolean("Wait",false);
            }
        }
        @Override public boolean onCompletion(ServerLevel level,SealData seal,SealWorker golem,SealTask task){
            switch(base){
                case "empty"->{ItemStack template=ItemStack.of(task.payload().getCompound("Stack"));int amount=template.getCount();
                    if(seal.toggle("pleave"))amount=Math.min(amount,Math.max(0,SealInventory.count(SealInventory.handler(level,seal.position()),s->ItemStack.isSameItemSameTags(template,s))-1));
                    if(SealInventory.take(level,seal.position(),golem,template,amount)>0)golem.swingArm();seal.runtime().putInt("FilterInc",seal.runtime().getInt("FilterInc")+1);}
                case "pickup"->{if(task.entity(level) instanceof ItemEntity item&&item.isAlive()&&seal.matches(item.getItem())){
                    ItemStack remainder=golem.holdItem(item.getItem().copy());if(remainder.isEmpty())item.discard();else item.setItem(remainder);golem.swingArm();}}
                case "fill"->{ItemStack stack=carried(seal,golem);int limit=amountAllowed(level,seal,stack);
                    if(limit>0){if(SealInventory.handler(level,seal.position())==null){ItemStack out=golem.dropItem(stack.copyWithCount(Math.min(limit,stack.getCount())));SealWorld.eject(level,seal.position().pos().relative(seal.position().face()),out);}
                        else SealInventory.deliver(level,seal.position(),golem,stack,limit);golem.addRankXp(1);golem.swingArm();}}
                case "provider"->{return finishProvision(level,seal,golem,task);}
                case "use"->{if(seal.toggle("pempty")==level.isEmptyBlock(task.position())){
                    ItemStack click=golem.carrying().isEmpty()?ItemStack.EMPTY:golem.carrying().get(0);if(!seal.filter(0).isEmpty())click=carried(seal,golem);
                    if(!click.isEmpty()||seal.toggle("pemptyhand")){
                        // Released SealUse removes the selected stack even when empty-hand overrides it.
                        // Preserve this confirmed destructive toggle quirk, instead of silently refunding it.
                        if(seal.toggle("pemptyhand")&&!click.isEmpty())golem.dropItem(click);
                        SealWorld.click(level,golem,task.position(),seal.position().face(),click,seal.toggle("pemptyhand"),seal.toggle("psneak"),!seal.toggle("pleft"));
                    }}}
                case "breaker"->{if(validBlock(level,seal,task.position())){BlockState state=level.getBlockState(task.position());boolean silk=seal.toggle("psilk");int speed=silk?7:21;
                    golem.swingArm();task.payload().putInt("Breaker",golem.mob().getId());if(task.data()>speed){task.lifespan(Math.max(10,task.lifespan()));task.data(task.data()-speed);float hardness=state.getDestroySpeed(level,task.position())*10;
                        int progress=hardness<=0?9:(int)(9*(1-task.data()/hardness));level.destroyBlockProgress(golem.mob().getId(),task.position(),Math.max(0,Math.min(9,progress)));return false;}
                    level.destroyBlockProgress(golem.mob().getId(),task.position(),-1);SealWorld.harvest(level,golem,task.position(),silk);golem.addRankXp(1);}}
                case "lumber"->{if(validBlock(level,seal,task.position())){golem.swingArm();BlockPos chosen=SealWorld.furthestLog(level,task.position());
                    if(SealWorld.harvest(level,golem,chosen,false)){task.lifespan(Math.max(10,task.lifespan()));golem.addRankXp(1);return false;}}}
                case "harvest"->{harvest(level,seal,golem,task);}
                case "butcher","guard"->seal.runtime().putBoolean("Wait",false);
            }task.suspended(true);return true;
        }
        @Override public void onSuspension(ServerLevel level,SealData seal,SealTask task){if(base.equals("butcher"))seal.runtime().putBoolean("Wait",false);
            if(base.equals("breaker")&&task.position()!=null&&task.payload().contains("Breaker",Tag.TAG_INT))level.destroyBlockProgress(task.payload().getInt("Breaker"),task.position(),-1);
            if(base.equals("provider")&&task.provision()!=null){task.provision().linkedTask(null,level.getGameTime());task.provision(null);}}
        @Override public void onRemoval(ServerLevel level,SealData seal){seal.runtime().putBoolean("Wait",false);}
        private boolean validBlock(ServerLevel level,SealData seal,BlockPos pos){if(!SealWorld.loaded(level,pos))return false;BlockState state=level.getBlockState(pos);
            if(base.equals("lumber"))return state.is(BlockTags.LOGS)&&state.getDestroySpeed(level,pos)>=0;
            if(state.isAir()||state.getDestroySpeed(level,pos)<0)return false;
            ItemStack item=new ItemStack(state.getBlock());
            // Pinned SealBreaker is an AND whitelist, unlike the normal item seals' OR whitelist.
            for(ItemStack filter:seal.filters())if(!filter.isEmpty()){
                ItemStack withoutNbt=filter.copy();withoutNbt.setTag(null);
                boolean match=seal.matchesFilter(withoutNbt,item);
                if(seal.blacklist()?match:!match)return false;
            }return true;
        }
        private boolean matchesCycle(SealData seal,ItemStack candidate){if(!(advanced&&seal.toggle("pcycle")&&!seal.blacklist()))return seal.matches(candidate);
            List<ItemStack> active=seal.filters().stream().filter(s->!s.isEmpty()).toList();return active.isEmpty()?seal.matches(candidate):seal.matchesFilter(active.get(Math.floorMod(seal.runtime().getInt("FilterInc"),active.size())),candidate);}
        private void stock(ServerLevel level,SealData seal,SealService service){IItemHandler inv=SealInventory.handler(level,seal.position());if(inv==null)return;
            for(int slot=0;slot<seal.filters().size();slot++){ItemStack template=seal.filter(slot);if(template.isEmpty())continue;int missing=seal.filterSize(slot)-SealInventory.count(inv,s->seal.matchesFilter(template,s));
                if(missing>0){ItemStack requested=template.copyWithCount(Math.min(template.getMaxStackSize(),missing));int room=SealInventory.room(inv,requested);if(room>0)service.requestProvision(seal.position().pos(),seal.position().face(),requested.copyWithCount(room),0);}}}
        private void provide(ServerLevel level,SealData seal,SealService service){IItemHandler inv=SealInventory.handler(level,seal.position());if(inv==null)return;
            for(SealProvision request:service.provisions()){if(request.invalid()||request.linkedTask()!=null)continue;
                Entity target=request.entityUUID()==null?null:level.getEntity(request.entityUUID());BlockPos pos=target==null?request.position():target.blockPosition();
                if(pos==null||!SealWorld.loaded(level,pos)||seal.position().pos().distSqr(pos)>=4096||!seal.matches(request.stack())
                        ||SealInventory.count(inv,s->strict(request.stack(),s))<=(seal.toggle("pleave")?1:0))continue;
                SealData destination=request.seal()==null?null:service.seal(request.seal());SealTask task=SealTask.block(seal.position(),seal.position().pos()).priority(destination==null?5:destination.priority()).lifespan(destination==null?31000:10);
                service.addTask(task);request.linkedTask(task,level.getGameTime());break;
            }}
        private boolean finishProvision(ServerLevel level,SealData seal,SealWorker golem,SealTask task){SealProvision request=task.provision();if(request==null){task.suspended(true);return true;}
            ItemStack wanted=request.stack();
            if(task.data()==0){int limit=seal.toggle("psing")?1:wanted.getCount();if(seal.toggle("pleave"))limit=Math.min(limit,Math.max(0,SealInventory.count(SealInventory.handler(level,seal.position()),s->strict(wanted,s))-1));
                // Requests with ignored damage still select actual physical item identity for carrying.
                ItemStack physical=SealInventory.first(SealInventory.handler(level,seal.position()),s->strict(wanted,s),seal.toggle("pleave"));
                int fetched=SealInventory.take(level,seal.position(),golem,physical,limit);
                if(fetched>0){golem.addRankXp(1);golem.swingArm();Entity target=request.entityUUID()==null?null:level.getEntity(request.entityUUID());SealTask next=target!=null?SealTask.entity(seal.position(),target):request.position()!=null&&request.seal()==null?SealTask.block(seal.position(),request.position()):null;
                    if(next!=null){next.priority(task.priority()).data(target!=null?1:2).lifespan(31000).golemUUID(golem.mob().getUUID());SealService.get(level).addTask(next);request.linkedTask(next,level.getGameTime());}}
                // Suspended tickets clear provision; detach before so the new delivery link survives.
                task.provision(null);task.suspended(true);return true;
            }
            ItemStack held=golem.carrying().stream().filter(s->strict(wanted,s)).findFirst().orElse(ItemStack.EMPTY);
            int delivered=0;
            if(!held.isEmpty()){if(task.data()==1){ItemStack stack=golem.dropItem(held.copyWithCount(Math.min(held.getCount(),wanted.getCount())));Entity target=task.entity(level);if(target!=null){SealWorld.eject(level,target.blockPosition(),stack);delivered=stack.getCount();}else{ItemStack back=golem.holdItem(stack);if(!back.isEmpty())golem.mob().spawnAtLocation(back);}}
                else if(request.position()!=null&&request.side()!=null){SealPos destination=new SealPos(request.position(),request.side());
                    if(SealInventory.handler(level,destination)!=null)delivered=SealInventory.deliver(level,destination,golem,held,Math.min(held.getCount(),wanted.getCount()));
                    else{ItemStack out=golem.dropItem(held.copyWithCount(Math.min(held.getCount(),wanted.getCount())));SealWorld.eject(level,request.position().relative(request.side()),out);delivered=out.getCount();}}}
            if(delivered<wanted.getCount()){ItemStack missing=wanted.copyWithCount(wanted.getCount()-delivered);Entity target=request.entityUUID()==null?null:level.getEntity(request.entityUUID());
                if(target!=null)SealService.get(level).requestProvision(target,missing,request.ui());else if(request.position()!=null&&request.side()!=null)SealService.get(level).requestProvision(request.position(),request.side(),missing,request.ui());}
            request.invalid(true);golem.swingArm();task.suspended(true);return true;
        }
        private int amountAllowed(ServerLevel level,SealData seal,ItemStack stack){if(stack.isEmpty())return 0;if(seal.blacklist())return stack.getCount();int desired=0;
            for(int i=0;i<seal.filters().size();i++)if(seal.matchesFilter(seal.filter(i),stack)){desired=seal.filterSize(i);break;}
            if(desired<=0)return stack.getCount();IItemHandler inv=SealInventory.handler(level,seal.position());int existing=inv==null?level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(seal.position().pos()).inflate(1.5)).stream().map(ItemEntity::getItem).filter(s->seal.matchesFilter(stack,s)).mapToInt(ItemStack::getCount).sum():SealInventory.count(inv,s->seal.matchesFilter(stack,s));
            return Math.min(stack.getCount(),Math.max(0,desired-existing));}
        private void harvest(ServerLevel level,SealData seal,SealWorker golem,SealTask task){BlockPos pos=task.position();if(task.payload().contains("Replant",Tag.TAG_COMPOUND)){
                CompoundTag ri=task.payload().getCompound("Replant");int face=ri.getByte("taskface");if(face>=0&&face<6)SealWorld.replant(level,golem,pos,Direction.values()[face],ItemStack.of(ri),ri.getBoolean("farmland"));return;}
            if(!SealWorld.grown(level,pos))return;BlockState state=level.getBlockState(pos);
            SealWorld.click(level,golem,pos,seal.position().face(),ItemStack.EMPTY,true,false,true);
            if(!SealWorld.grown(level,pos))return;
            if(SealWorld.harvest(level,golem,pos,false)){golem.addRankXp(1);golem.swingArm();if(seal.toggle("prep")){
                ItemStack seed=SealWorld.seed(state);if(seed.isEmpty())return;Direction face=state.is(Blocks.COCOA)?state.getValue(CocoaBlock.FACING):Direction.DOWN;
                CompoundTag ri=seed.copyWithCount(1).save(new CompoundTag());ri.putLong("taskloc",pos.asLong());ri.putByte("taskface",(byte)face.ordinal());ri.putBoolean("farmland",level.getBlockState(pos.below()).is(Blocks.FARMLAND));
                ListTag list=seal.runtime().getList("replant",Tag.TAG_COMPOUND);list.removeIf(t->((CompoundTag)t).getLong("taskloc")==pos.asLong());list.add(ri);seal.runtime().put("replant",list);
                SealTask next=SealTask.block(seal.position(),pos).priority(task.priority()).lifespan(300);next.payload().put("Replant",ri.copy());SealService.get(level).addTask(next);ri.putInt("Task",next.id());SealService.get(level).setDirty();
            }}
        }
    }
    private static boolean strict(ItemStack required,ItemStack candidate){return !required.isEmpty()&&!candidate.isEmpty()&&required.getItem()==candidate.getItem()&&(required.getDamageValue()==32767||candidate.getDamageValue()==32767||required.getDamageValue()==candidate.getDamageValue())&&Objects.equals(required.getTag(),candidate.getTag());}
    private static ItemStack carried(SealData seal,SealWorker golem){return golem.carrying().stream().filter(seal::matches).findFirst().orElse(ItemStack.EMPTY);}
    private static void add(SealService service,SealData seal,SealTask task,ItemStack stack){task.priority(seal.priority());if(stack!=null)task.payload().put("Stack",stack.save(new CompoundTag()));service.addTask(task);}
    private static void watched(SealService service,SealData seal){SealTask task=SealTask.block(seal.position(),seal.position().pos()).priority(seal.priority());service.addTask(task);seal.runtime().putInt("Watched",task.id());}
    private static boolean hasEntity(SealService service,SealData seal,Entity target){return service.tasks().stream().anyMatch(t->!t.suspended()&&!t.completed()&&seal.position().equals(t.sealPosition())&&target.getUUID().equals(t.entityUUID()));}
    private static boolean hasBlock(SealService service,SealData seal,BlockPos pos){return service.tasks().stream().anyMatch(t->!t.suspended()&&!t.completed()&&seal.position().equals(t.sealPosition())&&pos.equals(t.position()));}
    private static boolean guardTarget(ServerLevel level,SealData seal,LivingEntity target){return target.isAlive()&&((seal.toggle("pmob")&&target instanceof Enemy)||(seal.toggle("panimal")&&target instanceof Animal)||(seal.toggle("pplayer")&&level.getServer().isPvpAllowed()&&target instanceof Player player&&!player.isSpectator()&&!player.isCreative()));}
    public static boolean butcherTarget(LivingEntity target){return target.isAlive()&&target instanceof Animal animal&&!animal.isBaby()&&!(target instanceof Enemy)&&!(target instanceof AbstractGolem)&&(!(target instanceof TamableAnimal tame)||!tame.isTame());}
    private static CompoundTag replant(SealData seal,BlockPos pos){ListTag list=seal.runtime().getList("replant",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++)if(list.getCompound(i).getLong("taskloc")==pos.asLong())return list.getCompound(i);return null;}
    private static void pruneReplants(SealData seal,SealService service){ListTag list=seal.runtime().getList("replant",Tag.TAG_COMPOUND);list.removeIf(raw->{CompoundTag ri=(CompoundTag)raw;BlockPos pos=BlockPos.of(ri.getLong("taskloc"));if(seal.bounds().contains(Vec3.atCenterOf(pos)))return false;SealTask task=service.task(ri.getInt("Task"));if(task!=null)task.suspended(true);return true;});}
}
