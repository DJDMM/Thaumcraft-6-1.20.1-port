package thaumcraft.auromancy.media;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.auromancy.focus.FocusPlan;
import java.util.*;

/** Stationary spherical BETA26 cloud; its paid suffix is copied for every pulse target. */
public final class FocusCloudEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(FocusCloudEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(FocusCloudEntity.class,EntityDataSerializers.INT);
    private static final Map<ServerLevel,Map<Long,Long>> COOLDOWNS=new IdentityHashMap<>();
    private PaidFocusContinuation continuation;
    private int duration=5;
    public FocusCloudEntity(EntityType<? extends FocusCloudEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(RADIUS,.5F);entityData.define(COLOR,0xFFFFFFFF);}
    public float radius(){return entityData.get(RADIUS);}public int color(){return entityData.get(COLOR);}public int duration(){return duration;}
    public FocusPlan paidPlan(){return continuation==null?null:continuation.plan;}
    public boolean bindOwner(ServerPlayer caster){return continuation!=null&&continuation.bind(caster);}
    public float power(){return continuation==null?0:continuation.power;}
    public int nextIndex(){return continuation==null?-1:continuation.nextIndex;}
    @Override public EntityDimensions getDimensions(Pose pose){return EntityDimensions.scalable(radius()*2,.5F);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(RADIUS))refreshDimensions();}
    public static boolean spawn(ServerPlayer caster,FocusPlan plan,int nextIndex,Vec3 source,int radius,int duration,float power,int ordinal){
        if(!FocusMedia.validSpawn(caster,plan,nextIndex,"thaumcraft.CLOUD",source,power,ordinal)||radius<1||radius>3||duration<5||duration>30)return false;
        var medium=PaidFocusContinuation.medium(plan,nextIndex,"thaumcraft.CLOUD");if(medium.settings().get("radius")!=radius||medium.settings().get("duration")!=duration)return false;
        var entity=FocusMediaModule.CLOUD.get().create(caster.serverLevel());if(entity==null)return false;
        entity.continuation=new PaidFocusContinuation(caster,plan,nextIndex,power,ordinal);entity.duration=duration;
        entity.entityData.set(RADIUS,(float)radius);entity.entityData.set(COLOR,plan.color());entity.refreshDimensions();entity.setPos(source);
        return caster.serverLevel().addFreshEntity(entity);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        var server=(ServerLevel)level();
        if(continuation==null||continuation.caster(server)==null||!PaidFocusContinuation.loaded(server,position())||tickCount>duration*20){discard();return;}
        if(tickCount%5!=0)return;
        float radius=radius();long now=System.currentTimeMillis();
        var cooldown=COOLDOWNS.computeIfAbsent(server,ignored->new HashMap<>());
        // Preserve the official BETA26 Integer lookup / Long insertion quirk for living targets.
        // Block entries use Long on both sides and really have a shared two-second cooldown.
        cooldown.entrySet().removeIf(entry->entry.getValue()<=now);
        int ordinal=0;
        for(Entity entity:server.getEntities(this,new AABB(position(),position()).inflate(radius),e->!e.isRemoved())){
            if(entity instanceof FocusCloudEntity other){Vec3 away=other.position().subtract(position()).scale(1/50D);other.move(MoverType.SELF,away);}
            if(!(entity instanceof LivingEntity)||!entity.isAlive())continue;
            if(cooldown.containsKey(entity.getId())&&cooldown.get(entity.getId())>now)continue;
            cooldown.put((long)entity.getId(),now+2000);
            Vec3 center=entity.position().add(0,entity.getBbHeight()/2,0),direction=center.subtract(position());
            FocusMediaCallbacks.enqueue(server,continuation,new EntityHitResult(entity,center),position(),direction,0,ordinal++);
        }
        for(int ray=0;ray<radius;ray++){
            Vec3 direction=new Vec3(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()).normalize();
            Vec3 end=position().add(direction.scale(radius));if(!PaidFocusContinuation.loaded(server,end))continue;
            var hit=server.clip(new ClipContext(position(),end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,this));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            long key=hit.getBlockPos().asLong();if(cooldown.getOrDefault(key,0L)>now)continue;
            cooldown.put(key,now+2000);FocusMediaCallbacks.enqueue(server,continuation,hit,position(),direction,0,ordinal++);
        }
    }
    static void clearCooldowns(ServerLevel level){COOLDOWNS.remove(level);}
    static void clearServerCooldowns(MinecraftServer server){COOLDOWNS.keySet().removeIf(level->level.getServer()==server);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putInt("Age",tickCount);tag.putInt("Duration",duration);tag.putFloat("Radius",radius());if(continuation!=null)tag.put("Paid",continuation.save());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        continuation=null;if(!tag.contains("Paid",Tag.TAG_COMPOUND)||!tag.contains("Age",Tag.TAG_INT)||!tag.contains("Duration",Tag.TAG_INT)||!tag.contains("Radius",Tag.TAG_FLOAT))return;
        int age=tag.getInt("Age"),duration=tag.getInt("Duration");float radius=tag.getFloat("Radius");
        if(age<0||age>600||duration<5||duration>30||!Float.isFinite(radius)||radius<1||radius>3)return;
        var paid=PaidFocusContinuation.read(tag.getCompound("Paid"),"thaumcraft.CLOUD");if(paid==null)return;
        var medium=PaidFocusContinuation.medium(paid.plan,paid.nextIndex,"thaumcraft.CLOUD");
        if(medium.settings().get("radius")!=radius||medium.settings().get("duration")!=duration)return;
        continuation=paid;this.duration=duration;tickCount=age;entityData.set(RADIUS,radius);entityData.set(COLOR,paid.plan.color());refreshDimensions();
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
