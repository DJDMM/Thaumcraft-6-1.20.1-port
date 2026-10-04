package thaumcraft.auromancy.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import java.util.Comparator;

/** BETA26 intermediary: a paid immutable plan, resumed only by an authoritative collision. */
public final class FocusProjectileEntity extends Projectile {
    private static final EntityDataAccessor<Integer> SPECIAL = SynchedEntityData.defineId(FocusProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FocusProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(FocusProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> EFFECT = SynchedEntityData.defineId(FocusProjectileEntity.class, EntityDataSerializers.STRING);
    private FocusPlan plan;
    private int nextIndex,ordinal;
    private float power=1F;
    private Entity ignoredEntity;
    private int ignoreTime;
    private LivingEntity target;
    private boolean ownerCleared;

    public FocusProjectileEntity(EntityType<? extends FocusProjectileEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() {
        entityData.define(SPECIAL, 0); entityData.define(OWNER_ID, 0);
        entityData.define(COLOR, 0xFFFFFFFF); entityData.define(EFFECT, "");
    }
    public int special() { return entityData.get(SPECIAL); }
    public int color() { return entityData.get(COLOR); }
    public int ownerEntityId() { return entityData.get(OWNER_ID); }
    public String effectKey() { return entityData.get(EFFECT); }
    public FocusPlan paidPlan() { return plan; }
    public int nextIndex() { return nextIndex; }
    LivingEntity seekingTarget() { return target; }
    @Override public void setOwner(Entity owner) {
        ownerCleared=owner==null;
        super.setOwner(owner);
        entityData.set(OWNER_ID, owner == null ? 0 : owner.getId());
    }
    @Override public Entity getOwner() {
        if(ownerCleared)return null;
        if (level().isClientSide) {
            Entity owner = level().getEntity(ownerEntityId());
            if (owner instanceof LivingEntity) return owner;
        }
        return super.getOwner();
    }

    public static boolean spawn(ServerPlayer caster,FocusPlan plan,int nextIndex,Vec3 source,Vec3 direction,int speed,int option){return spawn(caster,plan,nextIndex,source,direction,speed,option,1F,0);}
    public static boolean spawn(ServerPlayer caster, FocusPlan plan, int nextIndex, Vec3 source, Vec3 direction, int speed, int option,float power,int ordinal) {
        if (!Float.isFinite(power)||power<=0||power>16||ordinal<0||ordinal>4096||caster == null || caster.getServer() == null || !caster.getServer().isSameThread()
                || !validContinuation(plan, nextIndex, speed, option) || !finite(source) || !finite(direction)
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-12) return false;
        Vec3 unit = direction.normalize();
        Vec3 position = source.add(unit.scale(caster.getBbWidth() * 2.1));
        if (!finite(position) || !caster.serverLevel().hasChunkAt(BlockPos.containing(position))) return false;
        var entity = new FocusProjectileEntity(VisualEntitiesModule.FOCUS_PROJECTILE.get(), caster.serverLevel());
        // Each impact resumes a new one-target array; its BETA26 effect ordinal is zero,
        // regardless of which Scatter trajectory created this intermediary.
        entity.plan = plan; entity.nextIndex = nextIndex;entity.power=power;entity.ordinal=0;
        entity.entityData.set(SPECIAL, option); entity.entityData.set(COLOR, plan.color());
        entity.entityData.set(EFFECT, plan.effect().key()); entity.setOwner(caster); entity.ignoredEntity = caster;
        entity.setPos(position);
        // EntityThrowable used a float square root and direct shoot, with no inherited caster velocity.
        float length = (float)Math.sqrt(unit.lengthSqr());
        Vec3 motion=unit.scale((speed / 3F) / length); entity.setDeltaMovement(motion);
        entity.setYRot((float)(Math.atan2(motion.x,motion.z)*180/Math.PI));
        entity.setXRot((float)(Math.atan2(motion.y,Math.sqrt(motion.x*motion.x+motion.z*motion.z))*180/Math.PI));
        entity.yRotO=entity.getYRot(); entity.xRotO=entity.getXRot();
        return caster.serverLevel().addFreshEntity(entity);
    }
    private static boolean validContinuation(FocusPlan plan, int index, int speed, int option) {
        if (plan == null || index < 1 || index >= plan.graph().nodes().size() || speed < 1 || speed > 5 || option < 0 || option > 3) return false;
        var medium = plan.node(plan.graph().nodes().get(index).parent());
        if(medium==null)return false;
        return medium.key().equals(FocusNodeRegistry.PROJECTILE)
                && Integer.valueOf(speed).equals(medium.settings().get("speed"))
                && Integer.valueOf(option).equals(medium.settings().get("option"));
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private boolean loadedRay(Vec3 start, Vec3 end) {
        if (!(level() instanceof ServerLevel server)) return true;
        int x0 = BlockPos.containing(Math.min(start.x, end.x), 0, 0).getX() >> 4;
        int x1 = BlockPos.containing(Math.max(start.x, end.x), 0, 0).getX() >> 4;
        int z0 = BlockPos.containing(0, 0, Math.min(start.z, end.z)).getZ() >> 4;
        int z1 = BlockPos.containing(0, 0, Math.max(start.z, end.z)).getZ() >> 4;
        for (int x=x0; x<=x1; x++) for (int z=z0; z<=z1; z++) if (server.getChunkSource().getChunkNow(x,z)==null) return false;
        return true;
    }

    @Override public void tick() {
        if (isRemoved()) return;
        Entity owner = getOwner();
        // Old catalogue entities have no paid graph. Never turn their appearance NBT into a spell.
        if (!level().isClientSide && (plan == null || !(owner instanceof ServerPlayer) || owner.isRemoved() || owner.level()!=level())) { discard(); return; }
        if (!finite(position()) || !finite(getDeltaMovement()) || getDeltaMovement().lengthSqr()>64) { discard(); return; }
        if (!level().isClientSide && ownerEntityId()!=owner.getId()) entityData.set(OWNER_ID, owner.getId());
        super.tick();
        Vec3 old = position(), end = old.add(getDeltaMovement());
        if (!loadedRay(old,end)) { if (!level().isClientSide) discard(); return; }
        HitResult hit = collision(old,end);
        if (hit.getType()!=HitResult.Type.MISS) {
            if (hit instanceof BlockHitResult block && level().getBlockState(block.getBlockPos()).is(Blocks.NETHER_PORTAL)) handleInsidePortal(block.getBlockPos());
            else if (!ForgeEventFactory.onProjectileImpact(this,hit)) onHit(hit);
        }
        // Vanilla1.12 moved even after onImpact; bounce first rewinds and then changes this motion.
        Vec3 motion = getDeltaMovement();
        setPos(position().add(motion)); updateRotation();
        float drag = .99F;
        if (isInWater()) {
            for (int i=0;i<4;i++) level().addParticle(ParticleTypes.BUBBLE, getX()-motion.x*.25, getY()-motion.y*.25, getZ()-motion.z*.25, motion.x,motion.y,motion.z);
            drag = .8F;
        }
        Vec3 slowed = motion.scale(drag);
        setDeltaMovement(isNoGravity() ? slowed : slowed.add(0, -(special()>1 ? .005F : .01F), 0));
        if (tickCount>1200) { discard(); return; }
        if (!isRemoved()) seek();
    }
    /** Outline ray + the temporary vanilla1.12 ignoreEntity rule, not modern leftOwner filtering. */
    private HitResult collision(Vec3 start, Vec3 end) {
        HitResult block = level().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,this));
        if (block.getType()!=HitResult.Type.MISS) end = block.getLocation();
        Entity nearest = null; double distance = 0; boolean ignoredSeen = false;
        for (Entity candidate : level().getEntities(this,getBoundingBox().expandTowards(getDeltaMovement()).inflate(1),
                e -> !e.isRemoved() && !e.isSpectator() && e.isPickable())) {
            if (candidate==ignoredEntity) ignoredSeen = true;
            else if (getOwner()!=null && tickCount<2 && ignoredEntity==null) { ignoredEntity=candidate; ignoredSeen=true; }
            else {
                ignoredSeen=false; // Intentional original last-candidate quirk, not an OR accumulation.
                var intercept=candidate.getBoundingBox().inflate(.30000001192092896).clip(start,end);
                if (intercept.isPresent()) {
                    double value=start.distanceToSqr(intercept.get());
                    if (value<distance || distance==0) { nearest=candidate; distance=value; }
                }
            }
        }
        if (ignoredEntity!=null) {
            if (ignoredSeen) ignoreTime=2;
            else if (ignoreTime--<=0) ignoredEntity=null;
        }
        return nearest==null ? block : new EntityHitResult(nearest,position());
    }

    @Override protected void onHit(HitResult hit) {
        if (isRemoved()) return;
        if (special()==1 && hit instanceof BlockHitResult block) {
            if (level().getBlockState(block.getBlockPos()).getCollisionShape(level(),block.getBlockPos()).isEmpty()) return;
            Vec3 motion=getDeltaMovement(); setPos(position().subtract(motion));
            Direction.Axis axis=block.getDirection().getAxis();
            motion=new Vec3(motion.x*(axis==Direction.Axis.X?-1:1),motion.y*(axis==Direction.Axis.Y?-.9:1),motion.z*(axis==Direction.Axis.Z?-1:1)).scale(.9);
            float length=(float)Math.sqrt(motion.lengthSqr());
            if (length>0) setPos(position().subtract(motion.scale(.05000000074505806/length)));
            setDeltaMovement(motion);
            if (!level().isClientSide) { playSound(SoundEvents.LEASH_KNOT_PLACE,.25F,1); if (motion.length()<.2) discard(); }
            return;
        }
        if (!level().isClientSide && getOwner() instanceof ServerPlayer caster && plan!=null) {
            HitResult actual=hit instanceof EntityHitResult e ? new EntityHitResult(e.getEntity(),position()) : hit;
            Vec3 source=new Vec3(xo,yo,zo), direction=getDeltaMovement().normalize();
            // Discard first: callbacks and nested media cannot execute this continuation a second time.
            discard(); FocusProjectileImpacts.enqueue((ServerLevel)level(),caster,plan,nextIndex,actual,source,direction,power,ordinal);
        }
    }

    static boolean friendly(Entity owner, Entity candidate) {
        if (owner==null || candidate==null) return false;
        if (owner==candidate || owner.hasIndirectPassenger(candidate) || candidate.hasIndirectPassenger(owner)
                || (owner.getTeam()!=null && owner.getTeam().isAlliedTo(candidate.getTeam()))) return true;
        if (candidate instanceof OwnableEntity ownable && ownable.getOwner()==owner) return true;
        return !owner.level().isClientSide && candidate instanceof Player && owner.getServer()!=null && !owner.getServer().isPvpAllowed();
    }
    boolean inCone(Entity candidate) {
        Vec3 direction=getDeltaMovement().normalize();
        Vec3 delta=new Vec3(candidate.getX(),candidate.getBoundingBox().minY+candidate.getBbHeight()/2,candidate.getZ()).subtract(position().add(0,getBbHeight()*.85F,0));
        double projection=delta.dot(direction);
        return projection/delta.length()>Math.cos(1.75F/2) && projection<16;
    }
    private boolean visible(Entity candidate) {
        Vec3 start=position().add(0,getBbHeight()/2,0), end=candidate.position().add(0,candidate.getBbHeight()/2,0);
        return loadedRay(start,end) && level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private void seek() {
        if (target==null && tickCount%5==0 && special()>1) {
            var candidates=level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(16),e -> !e.isRemoved() && !e.isSpectator());
            candidates.sort(Comparator.comparingDouble(this::distanceToSqr));
            for (LivingEntity candidate:candidates) if (inCone(candidate) && visible(candidate) && friendly(getOwner(),candidate)==(special()==3)) { target=candidate; break; }
        }
        if (target!=null) {
            Vec3 desired=new Vec3(target.getX()-getX(),target.getBoundingBox().minY+target.getBbHeight()*.6-getY(),target.getZ()-getZ()).normalize();
            Vec3 motion=getDeltaMovement(); setDeltaMovement(motion.normalize().add(desired.scale(.275)).normalize().scale(motion.length()));
            if (tickCount%5==0 && (target.isRemoved() || !inCone(target) || !visible(target))) target=null;
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Modern Projectile.setOwner(null) leaves its cached UUID; an explicitly cleared owner must not revive on reload.
        if(ownerCleared)tag.remove("Owner");
        if (plan!=null) { tag.put("PaidGraph",plan.graph().save()); tag.putInt("Capacity",plan.maxComplexity()); tag.putInt("Next",nextIndex); tag.putInt("Special",special());tag.putFloat("Power",power);tag.putInt("Ordinal",ordinal);tag.putUUID("Execution",plan.executionId()); }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); plan=null; nextIndex=0; target=null; ignoredEntity=null; ignoreTime=0; ownerCleared=false;
        if (!tag.contains("PaidGraph",Tag.TAG_COMPOUND) || !tag.contains("Capacity",Tag.TAG_INT)
                || !tag.contains("Next",Tag.TAG_INT) || !tag.contains("Special",Tag.TAG_INT) || !tag.hasUUID("Owner")) return;
        try {
            int capacity=tag.getInt("Capacity"), tier=switch(capacity) {case 15->1; case 25->2; case 50->3; default->0;};
            if (tier==0) return;
            var result=FocusCompiler.compile(FocusGraph.read(tag.getCompound("PaidGraph")),CatalogModule.stack("focus_"+tier),ignored->true);
            if (!result.success()) return;
            FocusPlan restored=result.plan();
            if(tag.hasUUID("Execution"))restored=restored.withExecutionId(tag.getUUID("Execution"));
            power=tag.contains("Power")?tag.getFloat("Power"):1F;int storedOrdinal=tag.getInt("Ordinal");
            if(!Float.isFinite(power)||power<=0||power>16||storedOrdinal<0||storedOrdinal>4096)return;
            ordinal=0; // Old paid saves may contain a fork ordinal; impact still has one target.
            int index=tag.getInt("Next"), option=tag.getInt("Special");
            if (index<1 || index>=restored.graph().nodes().size()) return;
            int speed=restored.node(restored.graph().nodes().get(index).parent()).settings().getOrDefault("speed",0);
            if (!validContinuation(restored,index,speed,option)) return;
            plan=restored; nextIndex=index; entityData.set(SPECIAL,option); entityData.set(COLOR,plan.color()); entityData.set(EFFECT,plan.effect().key());
        } catch (IllegalArgumentException ignored) { /* Invalid and old appearances remain harmless. */ }
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
