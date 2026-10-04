package thaumcraft.auromancy.media;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.auromancy.focus.FocusPlan;
import java.util.Comparator;

/** Original manual bat flight and five one-health paid attacks, not a visual/NoAI catalogue mob. */
public final class SpellBatEntity extends FlyingMob {
    private static final EntityDataAccessor<Boolean> FRIENDLY=SynchedEntityData.defineId(SpellBatEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(SpellBatEntity.class,EntityDataSerializers.INT);
    private PaidFocusContinuation continuation;private int attackTime;private BlockPos flightTarget;
    public SpellBatEntity(EntityType<? extends SpellBatEntity> type,Level level){super(type,level);setNoGravity(true);setPersistenceRequired();}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(FRIENDLY,false);entityData.define(COLOR,0xFFFFFFFF);}
    public boolean friendly(){return entityData.get(FRIENDLY);}public int color(){return entityData.get(COLOR);}public int attackTime(){return attackTime;}
    public FocusPlan paidPlan(){return continuation==null?null:continuation.plan;}
    public boolean bindOwner(ServerPlayer caster){return continuation!=null&&continuation.bind(caster);}
    public float power(){return continuation==null?0:continuation.power;}
    public int nextIndex(){return continuation==null?-1:continuation.nextIndex;}
    public ServerPlayer owner(){return level() instanceof ServerLevel server&&continuation!=null?continuation.caster(server):null;}
    public static boolean spawn(ServerPlayer caster,FocusPlan plan,int index,Vec3 source,boolean friendly,float power,int ordinal){
        if(!FocusMedia.validSpawn(caster,plan,index,"thaumcraft.SPELLBAT",source,power,ordinal))return false;
        if((PaidFocusContinuation.medium(plan,index,"thaumcraft.SPELLBAT").settings().get("target")==1)!=friendly)return false;
        var entity=FocusMediaModule.SPELL_BAT.get().create(caster.serverLevel());if(entity==null)return false;
        entity.continuation=new PaidFocusContinuation(caster,plan,index,power,ordinal);entity.entityData.set(FRIENDLY,friendly);entity.entityData.set(COLOR,plan.color());
        entity.setPos(source);entity.setHealth(5);return caster.serverLevel().addFreshEntity(entity);
    }
    @Override public void tick(){
        if(!level().isClientSide&&(continuation==null||owner()==null||tickCount>600||!PaidFocusContinuation.loaded((ServerLevel)level(),position()))){discard();return;}
        super.tick();setDeltaMovement(getDeltaMovement().multiply(1,.6000000238418579,1));
    }
    @Override protected void registerGoals(){} // BETA26 bat has custom updateAITasks, no vanilla hostile/pathfinding goals.
    @Override protected void customServerAiStep(){
        if(attackTime>0)attackTime--;
        LivingEntity target=getTarget();
        if(target!=null&&(!target.isAlive()||target.isRemoved()||target.level()!=level())){setTarget(null);target=null;}
        Vec3 destination;
        if(target==null){
            if(flightTarget!=null&&(!level().isEmptyBlock(flightTarget)||flightTarget.getY()<level().getMinBuildHeight()+1))flightTarget=null;
            if(flightTarget==null||random.nextInt(30)==0||flightTarget.distToCenterSqr(position())<4)
                flightTarget=new BlockPos((int)getX()+random.nextInt(7)-random.nextInt(7),(int)getY()+random.nextInt(6)-2,(int)getZ()+random.nextInt(7)-random.nextInt(7));
            destination=new Vec3(flightTarget.getX()+.5,flightTarget.getY()+.1,flightTarget.getZ()+.5);
        }else destination=target.position().add(0,target.getEyeHeight()*.66F,0);
        if(PaidFocusContinuation.loaded((ServerLevel)level(),destination)){
            Vec3 delta=destination.subtract(position()),motion=getDeltaMovement();
            setDeltaMovement(motion.add((Math.signum(delta.x)*.5-motion.x)*.10000000149011612,
                    (Math.signum(delta.y)*.699999988079071-motion.y)*.10000000149011612,(Math.signum(delta.z)*.5-motion.z)*.10000000149011612));
            Vec3 move=getDeltaMovement();float angle=(float)(Math.atan2(move.z,move.x)*180/Math.PI)-90;setYRot(getYRot()+Mth.wrapDegrees(angle-getYRot()));
            yBodyRot=getYRot();yHeadRot=getYRot();
        }else setDeltaMovement(Vec3.ZERO);
        if(target==null)setTarget(findTarget());
        else if(hasLineOfSight(target))attack(target);
        if(!friendly()&&getTarget() instanceof Player player&&player.getAbilities().invulnerable)setTarget(null);
    }
    LivingEntity findTarget(){
        ServerPlayer owner=owner();
        return level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(12),e->e!=this&&e.isAlive()&&!e.isRemoved()
                        &&PaidFocusContinuation.friendly(owner,e)==friendly()&&(friendly()||!isAlliedTo(e)))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }
    void attack(LivingEntity target){
        if(attackTime>0||distanceTo(target)>=Math.max(2.5F,target.getBbWidth()*1.1F)||target.getBoundingBox().maxY<=getBoundingBox().minY||target.getBoundingBox().minY>=getBoundingBox().maxY)return;
        attackTime=40;Vec3 center=target.position().add(0,target.getBbHeight()/2,0);
        FocusMediaCallbacks.enqueue((ServerLevel)level(),continuation,new EntityHitResult(target,center),position(),center.subtract(position()),0,0);
        setHealth(getHealth()-1);playSound(SoundEvents.BAT_HURT,.5F,.9F+random.nextFloat()*.2F);
        if(getHealth()<=0)die(damageSources().generic());
    }
    @Override public void travel(Vec3 input){move(MoverType.SELF,getDeltaMovement());}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isIgnoringBlockTriggers(){return true;}
    @Override protected boolean shouldDropLoot(){return false;}
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.BAT_AMBIENT;}
    @Override protected SoundEvent getHurtSound(DamageSource damage){return SoundEvents.BAT_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.BAT_DEATH;}
    @Override protected float getSoundVolume(){return .1F;}
    @Override public Team getTeam(){ServerPlayer owner=owner();return owner==null?super.getTeam():owner.getTeam();}
    @Override public boolean isAlliedTo(Entity entity){ServerPlayer owner=owner();return owner==null?super.isAlliedTo(entity):entity==owner||owner.isAlliedTo(entity)||entity.isAlliedTo(owner);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("Age",tickCount);tag.putBoolean("Friendly",friendly());if(continuation!=null)tag.put("Paid",continuation.save());}
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);continuation=null;attackTime=0;flightTarget=null;
        if(!tag.contains("Paid",Tag.TAG_COMPOUND)||!tag.contains("Age",Tag.TAG_INT)||!tag.contains("Friendly",Tag.TAG_BYTE))return;
        int age=tag.getInt("Age");if(age<0||age>600)return;
        var paid=PaidFocusContinuation.read(tag.getCompound("Paid"),"thaumcraft.SPELLBAT");if(paid==null)return;
        boolean friendly=tag.getBoolean("Friendly");if((PaidFocusContinuation.medium(paid.plan,paid.nextIndex,"thaumcraft.SPELLBAT").settings().get("target")==1)!=friendly)return;
        continuation=paid;tickCount=age;entityData.set(FRIENDLY,friendly);entityData.set(COLOR,paid.plan.color());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
