package thaumcraft.world.rift;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEffectEntity;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.aura.AuraManager;

/** Physical BETA26 rift, retaining the existing registry ID and catalogue render adapter. */
public final class FluxRiftEntity extends VisualEffectEntity {
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STABILITY = SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> COLLAPSE = SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.BOOLEAN);
    public List<Vec3> points = List.of();
    public List<Float> pointsWidth = List.of();
    private int maxSize;

    public FluxRiftEntity(EntityType<? extends VisualEffectEntity> type, Level level) {
        super(type, level);
        rebuildGeometry();
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SEED, 0); entityData.define(SIZE, 5);
        entityData.define(STABILITY, 0F); entityData.define(COLLAPSE, false);
    }
    public int getRiftSeed() { return entityData.get(SEED); }
    public net.minecraft.util.RandomSource getRandom() { return random; }
    public int getRiftSize() { return entityData.get(SIZE); }
    public float getRiftStability() { return entityData.get(STABILITY); }
    public boolean getCollapse() { return entityData.get(COLLAPSE); }
    public void setRiftSeed(int seed) { entityData.set(SEED, seed); }
    /** Bounded malformed-save geometry is a modern guard; original growth still stops at100. */
    public void setRiftSize(int size) { entityData.set(SIZE, Mth.clamp(size, 0, 1024)); }
    public void setRiftStability(float value) { if (Float.isFinite(value)) entityData.set(STABILITY, Mth.clamp(value, -100, 100)); }
    public void setCollapse(boolean value) { if (value) maxSize = getRiftSize(); entityData.set(COLLAPSE, value); }
    public void addStability() { setRiftStability(getRiftStability() + .125F); }
    @Override public int riftSeed() { return getRiftSeed(); }
    @Override public int riftSize() { return getRiftSize(); }
    public enum Stability { VERY_STABLE, STABLE, UNSTABLE, VERY_UNSTABLE }
    public Stability getStability() {
        float value = getRiftStability();
        return value > 50 ? Stability.VERY_STABLE : value >= 0 ? Stability.STABLE : value > -25 ? Stability.UNSTABLE : Stability.VERY_UNSTABLE;
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (points != null && (key.equals(SEED) || key.equals(SIZE))) rebuildGeometry();
    }
    @Override public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (points != null) rebuildGeometry();
    }
    private void rebuildGeometry() {
        var path = RiftGeometry.path(getRiftSeed(), getRiftSize());
        points = path.points(); pointsWidth = path.widths();
        if (points.isEmpty()) { setBoundingBox(new AABB(position(), position())); return; }
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
        // BETA26 intentionally initializes maxima to positive Double.MIN_VALUE.
        double x1 = Double.MIN_VALUE, y1 = Double.MIN_VALUE, z1 = Double.MIN_VALUE;
        for (Vec3 point : points) {
            x0 = Math.min(x0, point.x); y0 = Math.min(y0, point.y); z0 = Math.min(z0, point.z);
            x1 = Math.max(x1, point.x); y1 = Math.max(y1, point.y); z1 = Math.max(z1, point.z);
        }
        setBoundingBox(new AABB(getX()+x0, getY()+y0, getZ()+z0, getX()+x1, getY()+y1, getZ()+z1));
    }
    @Override public void move(MoverType type, Vec3 movement) {}
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isOnFire() { return false; }
    @Override public void setSecondsOnFire(int seconds) {}
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        maxSize = Mth.clamp(tag.getInt("MaxSize"), 0, 1024);
        setRiftSize(tag.contains("RiftSize") ? tag.getInt("RiftSize") : 5);
        setRiftSeed(tag.getInt("RiftSeed"));
        // Released bytecode reads an integer from the float Stability tag.
        setRiftStability(tag.getInt("Stability"));
        setCollapse(tag.getBoolean("collapse")); // also replaces the reward snapshot, as in BETA26
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("MaxSize", maxSize); tag.putInt("RiftSize", getRiftSize());
        tag.putInt("RiftSeed", getRiftSeed()); tag.putFloat("Stability", getRiftStability());
        tag.putBoolean("collapse", getCollapse());
        // Retain the previous catalogue cosmetic flag without a VisualOnly runtime marker.
        tag.putBoolean("Red", red());
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        if (getRiftSeed() == 0) setRiftSeed(random.nextInt());
        erodeSegment(server);
        if (points.size() < 3 && !getCollapse()) setCollapse(true);
        if (getCollapse()) {
            setRiftSize(getRiftSize()-1);
            if (random.nextBoolean()) AuraManager.addVis(server, blockPosition(), 1);
            else AuraManager.addFlux(server, blockPosition(), 1);
            if (random.nextInt(10)==0) server.explode(this, getX()+random.nextGaussian()*2,
                    getY()+random.nextGaussian()*2, getZ()+random.nextGaussian()*2, random.nextFloat()/2, Level.ExplosionInteraction.NONE);
            if (getRiftSize() <= 1) { completeCollapse(server); return; }
        }
        if (tickCount%120==0) setRiftStability(getRiftStability()-.2F);
        if (tickCount%600==getId()%600) {
            double cost = Math.sqrt(getRiftSize()*2);
            if (AuraManager.getFlux(server, blockPosition()) >= cost && getRiftSize()<100 && getStability()!=Stability.VERY_STABLE) {
                AuraManager.drainFlux(server, blockPosition(), (float)cost, false);
                setRiftSize(getRiftSize()+1);
            }
            if (getRiftStability()<0 && random.nextInt(1000)<Math.abs(getRiftStability())+getRiftSize()) RiftEvents.execute(this);
        }
        if (!isRemoved() && tickCount%300==0) {
            var sound=ForgeRegistries.SOUND_EVENTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","evilportal"));
            if (sound!=null) playSound(sound,(float)(.15000000596046448+random.nextGaussian()*.066),(float)(.75+random.nextGaussian()*.1));
        }
    }
    private void erodeSegment(ServerLevel server) {
        if (points.size()<2) return;
        int index=random.nextInt(points.size()-1);
        Vec3 a=points.get(index).add(position()), b=points.get(index+1).add(position());
        // Do not force-load terrain along a saved large spine.
        if (!server.hasChunkAt(BlockPos.containing(a)) || !server.hasChunkAt(BlockPos.containing(b))) return;
        // Original rayTraceBlocks(a,b,false) keeps selectable blocks even with no
        // physical collision box; flowers, torches and native crystals qualify.
        var hit=server.clip(new ClipContext(a,b,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,this));
        if (hit.getType()==HitResult.Type.BLOCK) {
            BlockPos pos=hit.getBlockPos(); var state=server.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(server,pos)>=0 && !state.getShape(server,pos).isEmpty()) {
                server.levelEvent(2001,pos,net.minecraft.world.level.block.Block.getId(state));
                server.removeBlock(pos,false); // no ordinary drops; BE contents follow native removal callbacks
            }
        }
        AABB box=new AABB(a.x-.5,a.y-.5,a.z-.5,a.x+.5,a.y+.5,a.z+.5);
        for (Entity target : server.getEntities(this,box,e->e.isAlive())) {
            if (target instanceof Player player && player.isCreative()) continue;
            target.hurt(server.damageSources().fellOutOfWorld(),2);
            if (target instanceof ItemEntity) target.discard();
        }
    }
    private void completeCollapse(ServerLevel server) {
        int count=(int)Math.sqrt(maxSize);
        if (random.nextInt(100)<count) {
            ItemStack pearl=CatalogModule.stack("primordial_pearl"); pearl.setDamageValue(4+random.nextInt(4)); spawnAtLocation(pearl);
        }
        var seed=ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","void_seed"));
        for (int i=0; i<count; i++) spawnAtLocation(new ItemStack(seed));
        server.sendParticles(ParticleTypes.PORTAL,getX(),getY(),getZ(),64,1,1,1,.15);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,new AABB(position(),position()).inflate(32),Entity::isAlive)) {
            // Intentional squared-distance divisor32 and switch fallthrough from released TC6.
            double factor=1-distanceToSqr(target)/32;
            if (getStability()==Stability.VERY_UNSTABLE) {
                int duration=(int)(factor*120);
                var taint=ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","flux_taint"));
                if (duration>0 && taint!=null) target.addEffect(new MobEffectInstance(taint,duration*20,0));
            }
            if (getStability()==Stability.VERY_UNSTABLE || getStability()==Stability.UNSTABLE) {
                int duration=(int)(factor*300);
                if (duration>0) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration*20,0));
            }
            if (getStability()!=Stability.VERY_STABLE && target instanceof ServerPlayer player) {
                int warp=(int)(factor*25);
                if (warp>0) { KnowledgeStore.addNormalWarp(player,warp); KnowledgeStore.addTemporaryWarp(player,warp); }
            }
        }
        discard();
    }
}
