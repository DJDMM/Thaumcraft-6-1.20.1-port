package thaumcraft.auromancy.media;

import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.auromancy.focus.FocusPlan;

/** Gravity-placed mine: forty armed ticks, friendly/hostile cube, one delayed paid detonation. */
public final class FocusMineEntity extends net.minecraft.world.entity.projectile.Projectile {
    private static final EntityDataAccessor<Boolean> ARMED=SynchedEntityData.defineId(FocusMineEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COUNTER=SynchedEntityData.defineId(FocusMineEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(FocusMineEntity.class,EntityDataSerializers.INT);
    private PaidFocusContinuation continuation;private boolean friendly;
    public FocusMineEntity(EntityType<? extends FocusMineEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){entityData.define(ARMED,false);entityData.define(COUNTER,40);entityData.define(COLOR,0xFFFFFFFF);}
    public boolean armed(){return entityData.get(ARMED);}public int counter(){return entityData.get(COUNTER);}public int color(){return entityData.get(COLOR);}
    public boolean friendly(){return friendly;}public FocusPlan paidPlan(){return continuation==null?null:continuation.plan;}
    public boolean bindOwner(ServerPlayer caster){return continuation!=null&&continuation.bind(caster);}
    public float power(){return continuation==null?0:continuation.power;}
    public int nextIndex(){return continuation==null?-1:continuation.nextIndex;}
    public static boolean spawn(ServerPlayer caster,FocusPlan plan,int index,Vec3 source,Vec3 direction,boolean friendly,float power,int ordinal){
        if(!FocusMedia.validSpawn(caster,plan,index,"thaumcraft.MINE",source,power,ordinal)||!PaidFocusContinuation.finite(direction)||!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()<1e-12)return false;
        if((PaidFocusContinuation.medium(plan,index,"thaumcraft.MINE").settings().get("target")==1)!=friendly)return false;
        var entity=FocusMediaModule.MINE.get().create(caster.serverLevel());if(entity==null)return false;
        entity.continuation=new PaidFocusContinuation(caster,plan,index,power,ordinal);entity.friendly=friendly;entity.setOwner(caster);entity.setPos(source);
        entity.entityData.set(COLOR,plan.color());entity.setDeltaMovement(Vec3.ZERO);
        return caster.serverLevel().addFreshEntity(entity);
    }
    public void arm(){entityData.set(ARMED,true);}
    @Override public void tick(){
        super.tick();
        ServerLevel server=level() instanceof ServerLevel s?s:null;
        if(server!=null&&(continuation==null||continuation.caster(server)==null||tickCount>1200||!PaidFocusContinuation.loaded(server,position()))){discard();return;}
        {
            Vec3 motion=getDeltaMovement(),end=position().add(motion);
            if(server!=null&&!PaidFocusContinuation.loaded(server,end)){discard();return;}
            HitResult hit=level().clip(new ClipContext(position(),end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,this));
            Vec3 rayEnd=hit.getType()==HitResult.Type.MISS?end:hit.getLocation();double distance=position().distanceToSqr(rayEnd);
            for(Entity entity:level().getEntities(this,getBoundingBox().expandTowards(motion).inflate(1),e->!e.isRemoved()&&e.isPickable())){
                if(continuation!=null&&entity.getUUID().equals(continuation.owner)&&tickCount<5)continue;
                var intersection=entity.getBoundingBox().inflate(.3).clip(position(),rayEnd);
                if(intersection.isPresent()&&position().distanceToSqr(intersection.get())<=distance){hit=new EntityHitResult(entity,intersection.get());distance=position().distanceToSqr(intersection.get());}
            }
            if(hit.getType()!=HitResult.Type.MISS){
                boolean canceled=ForgeEventFactory.onProjectileImpact(this,hit);
                // Impact listeners may remove the projectile instead of canceling it.
                if(isRemoved())return;
                if(!canceled&&server!=null)arm();
            }
            // Original EntityThrowable advances and applies drag/gravity even after an arming impact.
            setPos(end);setDeltaMovement(motion.scale(isInWater()?.8F:.99F).add(0,isNoGravity()?0:-.01F,0));
            if(!level().noCollision(this,getBoundingBox())){
                // Modern replacement for old pushOutOfBlocks, including its quarter-speed damping.
                moveTowardsClosestSpace(getX(),getY(),getZ());setDeltaMovement(getDeltaMovement().scale(.25));
            }
        }
        if(server==null||isRemoved()||!armed())return;
        if(counter()>0)entityData.set(COUNTER,counter()-1);
        if(counter()>0||tickCount%5!=0)return;
        int delay=0;
        ServerPlayer owner=continuation.caster(server);
        for(LivingEntity entity:server.getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(1),e->!e.isRemoved()&&e.isAlive())){
            if(PaidFocusContinuation.friendly(owner,entity)!=friendly)continue;
            Vec3 center=entity.position().add(0,entity.getBbHeight()/2,0);
            FocusMediaCallbacks.enqueue(server,continuation,new EntityHitResult(entity,center),position(),center.subtract(position()),delay,0);delay++;
        }
        if(delay>0)discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putInt("Age",tickCount);tag.putBoolean("Armed",armed());tag.putBoolean("Friendly",friendly);if(continuation!=null)tag.put("Paid",continuation.save());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        continuation=null;if(!tag.contains("Paid",Tag.TAG_COMPOUND)||!tag.contains("Armed",Tag.TAG_BYTE)||!tag.contains("Friendly",Tag.TAG_BYTE)||!tag.contains("Age",Tag.TAG_INT))return;
        int age=tag.getInt("Age");if(age<0||age>1200)return;
        var paid=PaidFocusContinuation.read(tag.getCompound("Paid"),"thaumcraft.MINE");if(paid==null)return;
        boolean friendly=tag.getBoolean("Friendly");if((PaidFocusContinuation.medium(paid.plan,paid.nextIndex,"thaumcraft.MINE").settings().get("target")==1)!=friendly)return;
        continuation=paid;this.friendly=friendly;tickCount=age;entityData.set(ARMED,tag.getBoolean("Armed"));
        // Original BETA26 deliberately makes a saved armed mine immediately live after reload.
        entityData.set(COUNTER,armed()?0:40);entityData.set(COLOR,paid.plan.color());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
