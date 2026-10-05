package thaumcraft.golemancy.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.golemancy.press.GolemDesign;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.golemancy.seals.core.SealWorker;
import thaumcraft.golemancy.seals.core.SealTaskGoal;

import javax.annotation.Nullable;
import java.util.*;

/** Operational BETA26 construct. Seal tasks use the server-only controller below. */
public final class ThaumcraftGolemEntity extends PathfinderMob implements RangedAttackMob, SealWorker {
    private static final EntityDataAccessor<Integer> PROPS_HIGH = SynchedEntityData.defineId(ThaumcraftGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PROPS_LOW = SynchedEntityData.defineId(ThaumcraftGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ThaumcraftGolemEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(ThaumcraftGolemEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> COLOR = SynchedEntityData.defineId(ThaumcraftGolemEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(ThaumcraftGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean validSpawn, firstRun = true, collecting;
    private int rankXp;
    private BlockPos home;
    private final List<Goal> activeGoals = new ArrayList<>();
    private final List<Goal> activeTargets = new ArrayList<>();
    private float wheelRotation;
    private SealTaskGoal sealGoal;

    /** Installed once by the seal module; no client packet can assign a task. */
    public interface TaskController {
        void tick(ThaumcraftGolemEntity golem);
        void release(ThaumcraftGolemEntity golem);
        default boolean hasTask(ThaumcraftGolemEntity golem) { return false; }
    }
    private static TaskController controller = new TaskController() {
        public void tick(ThaumcraftGolemEntity golem) {}
        public void release(ThaumcraftGolemEntity golem) {}
    };
    public static void setTaskController(TaskController taskController) { controller = Objects.requireNonNull(taskController); }
    public boolean hasTask() { return sealGoal != null && sealGoal.task() != null || controller.hasTask(this); }
    public void releaseTask() {
        if (!level().isClientSide) {
            if (sealGoal != null && sealGoal.task() != null) sealGoal.stop();
            controller.release(this);
        }
    }

    public ThaumcraftGolemEntity(EntityType<? extends ThaumcraftGolemEntity> type, Level level) {
        super(type, level);
        xpReward = 5;
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setDropChance(EquipmentSlot.MAINHAND, 0); setDropChance(EquipmentSlot.OFFHAND, 0);
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ARMOR, 0);
    }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PROPS_HIGH, 0); entityData.define(PROPS_LOW, 0);
        entityData.define(OWNER, Optional.empty()); entityData.define(FLAGS, (byte)0);
        entityData.define(COLOR, (byte)0); entityData.define(CLIMBING, false);
    }
    public long props() { return ((long)entityData.get(PROPS_HIGH) << 32) | Integer.toUnsignedLong(entityData.get(PROPS_LOW)); }
    public GolemDesign design() { return GolemDesign.parse(props()).orElseGet(() -> GolemDesign.parse(0).orElseThrow()); }
    public GolemDesign getProperties() { return design(); }
    public boolean setProps(long props) {
        if (collecting || GolemDesign.parse(props).isEmpty()) return false;
        entityData.set(PROPS_HIGH, (int)(props >>> 32)); entityData.set(PROPS_LOW, (int)props);
        refreshAttributesAndGoals();
        return true;
    }
    public void setProperties(GolemDesign design) { setProps(design.props()); }
    @Nullable public UUID getOwnerId() { return entityData.get(OWNER).orElse(null); }
    public void setOwnerId(@Nullable UUID owner) { entityData.set(OWNER, Optional.ofNullable(owner)); }
    public boolean isOwned() { return getOwnerId() != null; }
    @Nullable public Player getOwnerEntity() { return getOwnerId() == null ? null : level().getPlayerByUUID(getOwnerId()); }
    public boolean isOwner(Entity player) { return player != null && getOwnerId() != null && getOwnerId().equals(player.getUUID()); }
    public void setValidSpawn() { validSpawn = true; }
    public boolean isValidSpawn() { return validSpawn; }
    public int rankXp() { return rankXp; }
    public void setRankXp(int xp) { rankXp = Math.max(0, Math.min(xp, 1_000_000)); }
    public byte getFlags() { return entityData.get(FLAGS); }
    public void setFlags(byte flags) { entityData.set(FLAGS, (byte)(flags & 10)); }
    public byte getGolemColor() { return entityData.get(COLOR); }
    public void setGolemColor(byte color) { entityData.set(COLOR, (byte)Mth.clamp(color, 0, 16)); }
    public boolean isFollowingOwner() { return (getFlags() & 2) != 0; }
    public boolean isInCombat() { return (getFlags() & 8) != 0; }
    public void setFollowingOwner(boolean following) {
        releaseTask();
        setFlags((byte)(following ? getFlags() | 2 : getFlags() & ~2));
        if (following) clearRestriction();
        else setHome(blockPosition());
        refreshAttributesAndGoals();
    }
    public BlockPos homePosition() { return home == null ? blockPosition() : home; }
    public boolean hasHome() { return home != null; }
    public int homeRadius() { return design().hasTrait(GolemDesign.Trait.SCOUT) ? 48 : 32; }
    public void setHome(BlockPos position) { home = position.immutable(); restrictTo(home, homeRadius()); }
    public float getGolemMoveSpeed() { return design().moveSpeed(); }
    public float wheelRotation() { return wheelRotation; }
    @Override public Mob mob() { return this; }
    @Override public UUID ownerId() { return getOwnerId(); }
    @Override public int color() { return getGolemColor(); }
    @Override public Set<String> traits() { return design().traits().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    @Override public boolean withinHome(BlockPos pos) { return !hasHome() || home.distSqr(pos) < (double)homeRadius() * homeRadius(); }
    @Override public double moveSpeed() { return getGolemMoveSpeed(); }
    @Override public boolean inCombat() { return isInCombat(); }
    @Override public List<ItemStack> carrying() { return getCarrying(); }
    @Override public void swingArm() { swing(InteractionHand.MAIN_HAND); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override protected ResourceLocation getDefaultLootTable() { return BuiltInLootTables.EMPTY; }
    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions size) { return .7F; }
    @Override public boolean onClimbable() { return entityData.get(CLIMBING); }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !design().hasTrait(GolemDesign.Trait.FLYER) && !design().hasTrait(GolemDesign.Trait.CLIMBER)
                && super.causeFallDamage(distance, multiplier, source);
    }
    @Override public boolean isAlliedTo(Entity entity) {
        if (isOwner(entity)) return true;
        if (entity instanceof ThaumcraftGolemEntity other && isOwned() && Objects.equals(getOwnerId(), other.getOwnerId())) return true;
        Player owner = getOwnerEntity();
        return owner != null && owner.isAlliedTo(entity) || super.isAlliedTo(entity);
    }
    @Override public net.minecraft.world.scores.Team getTeam() { Player owner = getOwnerEntity(); return owner == null ? super.getTeam() : owner.getTeam(); }
    @Override protected SoundEvent getAmbientSound() { return GolemEntitySounds.CLACK.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource damage) { return GolemEntitySounds.CLACK.get(); }
    @Override protected SoundEvent getDeathSound() { return GolemEntitySounds.TOOL.get(); }
    @Override public int getAmbientSoundInterval() { return 240; }
    public boolean canAttackTarget(LivingEntity target) {
        return target != null && target.isAlive() && target.level() == level() && !isAlliedTo(target)
                && !(target instanceof Player player && (player.isSpectator() || player.isCreative()
                    || level().getServer() != null && !level().getServer().isPvpAllowed()));
    }
    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(canAttackTarget(target) ? target : null);
        setFlags((byte)(getTarget() != null ? getFlags() | 8 : getFlags() & ~8));
    }
    private void addGoal(int priority, Goal goal) { goalSelector.addGoal(priority, goal); activeGoals.add(goal); }
    private void addTarget(int priority, Goal goal) { targetSelector.addGoal(priority, goal); activeTargets.add(goal); }
    /** Attributes are recomputed only from detached validated property data. */
    public void refreshAttributesAndGoals() {
        releaseTask();
        GolemDesign design = design();
        // BETA26 updateEntityAttributes reapplies the saved home even after detachHome on follow.
        if (home == null) home = blockPosition().immutable();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(design.health());
        getAttribute(Attributes.ARMOR).setBaseValue(design.armor());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(design.attackDamage());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(design.hasTrait(GolemDesign.Trait.SCOUT) ? 56 : 40);
        setMaxUpStep(design.hasTrait(GolemDesign.Trait.WHEELED) ? .5F : .6F);
        setNoGravity(design.hasTrait(GolemDesign.Trait.FLYER));
        if (home != null) restrictTo(home, homeRadius());
        for (Goal goal : activeGoals) goalSelector.removeGoal(goal);
        for (Goal goal : activeTargets) targetSelector.removeGoal(goal);
        activeGoals.clear(); activeTargets.clear();
        getNavigation().stop();
        if (design.hasTrait(GolemDesign.Trait.FLYER)) {
            navigation = new GolemNavigation.Air(this, level());
            moveControl = new GolemFlyingMoveControl(this);
        } else {
            navigation = design.hasTrait(GolemDesign.Trait.CLIMBER) ? new GolemNavigation.Climber(this, level()) : new GolemNavigation.Ground(this, level());
            moveControl = new MoveControl(this);
        }
        sealGoal = null;
        if (isFollowingOwner()) addGoal(4, new FollowOwnerGoal(this));
        else { sealGoal = new SealTaskGoal(this); addGoal(3, sealGoal); addGoal(5, new HomeGoal(this)); }
        addGoal(8, new LookAtPlayerGoal(this, Player.class, 8)); addGoal(9, new RandomLookAroundGoal(this));
        if (design.hasTrait(GolemDesign.Trait.FIGHTER)) {
            if (navigation instanceof GroundPathNavigation) addGoal(0, new FloatGoal(this));
            if (design.hasTrait(GolemDesign.Trait.RANGED)) addGoal(1, new DartAttackGoal(this));
            addGoal(2, new MeleeAttackGoal(this, 1.15, false));
            if (isFollowingOwner()) { addTarget(1, new OwnerDefenceGoal(this, true)); addTarget(2, new OwnerDefenceGoal(this, false)); }
            addTarget(3, new HurtByTargetGoal(this));
        } else setTarget(null);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }
    @Override public void tick() {
        super.tick();
        GolemDesign design = design();
        if (!level().isClientSide) {
            if (!validSpawn) { discard(); return; }
            if (firstRun) { firstRun = false; if (hasHome() && !blockPosition().equals(home)) recoverHome(); }
            if (getTarget() != null && (!canAttackTarget(getTarget()) || design.hasTrait(GolemDesign.Trait.RANGED) && distanceToSqr(getTarget()) > 1024)) setTarget(null);
            if (tickCount % (design.hasTrait(GolemDesign.Trait.REPAIR) ? 40 : 100) == 0) heal(1);
            entityData.set(CLIMBING, design.hasTrait(GolemDesign.Trait.CLIMBER) && horizontalCollision);
            if (!isFollowingOwner() && isAlive() && !collecting) controller.tick(this);
        } else {
            if (design.hasTrait(GolemDesign.Trait.WHEELED)) {
                double dx = getX() - xo, dz = getZ() - zo;
                double distance = Math.sqrt(dx * dx + (getY() - yo) * (getY() - yo) + dz * dz);
                float travel = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90;
                wheelRotation += (float)(distance / 1.571 * (360 - (getYRot() - travel)));
                if (wheelRotation > 360) wheelRotation -= 360;
            }
            if (design.hasTrait(GolemDesign.Trait.FLYER) && (!onGround() || tickCount % 5 == 0))
                level().addParticle(ParticleTypes.END_ROD, getX(), getY() + .1, getZ(), random.nextGaussian() / 100, -.1, random.nextGaussian() / 100);
        }
    }
    /** Pinned upward search is retained, bounded to loaded cells and build height. */
    public boolean recoverHome() {
        if (!hasHome() || !level().hasChunkAt(home)) return false;
        Vec3 old = position();
        BlockPos candidate = home;
        boolean found = false;
        while (candidate.getY() < level().getMaxBuildHeight() - 1 && level().hasChunkAt(candidate.above())) {
            if (level().getBlockState(candidate.above()).blocksMotion()) { found = true; break; }
            candidate = candidate.above();
        }
        if (!found) return false;
        setPos(candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5);
        if (!level().noCollision(this, getBoundingBox())) { setPos(old); return false; }
        getNavigation().stop(); return true;
    }
    @Override public boolean hurt(DamageSource source, float damage) {
        if (!Float.isFinite(damage) || damage <= 0 || collecting) return false;
        GolemDesign design = design();
        if (source.is(DamageTypeTags.IS_FIRE) && design.hasTrait(GolemDesign.Trait.FIREPROOF) || source.is(DamageTypes.CACTUS)) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION) && design.hasTrait(GolemDesign.Trait.BLASTPROOF)) damage = Math.min(getMaxHealth() / 2, damage * .3F);
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) recoverHome();
        return super.hurt(source, damage);
    }
    @Override public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living) || !design().hasTrait(GolemDesign.Trait.FIGHTER) || !canAttackTarget(living)) return false;
        boolean hit = super.doHurtTarget(target);
        if (hit && design().hasTrait(GolemDesign.Trait.DEFT) && getOwnerEntity() != null) living.setLastHurtByPlayer(getOwnerEntity());
        if (hit && !living.isAlive() && living instanceof Mob) addRankXp(8);
        return hit;
    }
    @Override public void performRangedAttack(LivingEntity target, float range) {
        if (level().isClientSide || !design().hasTrait(GolemDesign.Trait.RANGED) || !canAttackTarget(target)
                || !Float.isFinite(range) || !level().hasChunkAt(blockPosition())) return;
        // BETA26 EntityGolemDart is otherwise the vanilla arrow, including arrow pickup.
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "golem_dart"));
        if (type == null || !(type.create(level()) instanceof GolemDartEntity arrow)) return;
        arrow.setOwner(this); arrow.setPos(getX(), getEyeY() - .1, getZ());
        arrow.setBaseDamage(getAttributeValue(Attributes.ATTACK_DAMAGE) / 3 + range + random.nextGaussian() * .25);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double dy = target.getBoundingBox().minY + target.getEyeHeight() + range * range - arrow.getY();
        arrow.shoot(dx, dy, dz, 1.6F, 3);
        if (level().addFreshEntity(arrow)) playSound(SoundEvents.ARROW_SHOOT, 1, 1 / (random.nextFloat() * .4F + .8F));
    }
    public void addRankXp(int xp) {
        if (level().isClientSide || xp <= 0 || !design().hasTrait(GolemDesign.Trait.SMART) || design().rank() >= 10) return;
        rankXp = (int)Math.min(1_000_000L, (long)rankXp + xp);
        int rank = design().rank(), threshold = (rank + 1) * (rank + 1) * 1000;
        if (rankXp >= threshold) {
            rankXp -= threshold;
            // Original advances at most one rank per award, retaining excess XP.
            long props = (props() & ~(255L << 16)) | ((long)(rank + 1) << 16);
            entityData.set(PROPS_HIGH, (int)(props >>> 32)); entityData.set(PROPS_LOW, (int)props);
            level().broadcastEntityEvent(this, (byte)9); playSound(SoundEvents.PLAYER_LEVELUP, .25F, 1);
            // Original setProperties does not refresh attributes until a subsequent rebuild/load.
        }
    }
    public int carrySlots() { return design().hasTrait(GolemDesign.Trait.HAULER) ? 2 : 1; }
    private EquipmentSlot carrySlot(int slot) { return slot == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND; }
    public List<ItemStack> getCarrying() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < carrySlots(); slot++) items.add(getItemBySlot(carrySlot(slot)).copy());
        return List.copyOf(items);
    }
    public int canCarryAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int capacity = 0;
        for (int slot = 0; slot < carrySlots(); slot++) {
            ItemStack held = getItemBySlot(carrySlot(slot));
            // BETA26 empty ItemStack max stack is64, independent of the offered item.
            if (held.isEmpty()) capacity += 64;
            else if (ItemStack.isSameItemSameTags(held, stack)) capacity += held.getMaxStackSize() - held.getCount();
        }
        return capacity;
    }
    public boolean canCarry(ItemStack stack, boolean partial) { int capacity = canCarryAmount(stack); return capacity > 0 && (partial || capacity >= stack.getCount()); }
    public boolean isCarrying(ItemStack stack) { return stack != null && !stack.isEmpty() && getCarrying().stream().anyMatch(item -> !item.isEmpty() && ItemStack.isSameItemSameTags(item, stack)); }
    /** Detached return values; callers cannot mutate an equipped stack by alias. */
    public ItemStack holdItem(ItemStack offered) {
        if (offered == null || offered.isEmpty() || offered.getCount() <= 0 || collecting || !isAlive()) return offered == null ? ItemStack.EMPTY : offered.copy();
        ItemStack remainder = offered.copy();
        for (int slot = 0; slot < carrySlots(); slot++) {
            EquipmentSlot equipment = carrySlot(slot); ItemStack held = getItemBySlot(equipment);
            if (held.isEmpty()) { setItemSlot(equipment, remainder.copy()); return ItemStack.EMPTY; }
            if (ItemStack.isSameItemSameTags(held, remainder)) {
                int count = Math.min(remainder.getCount(), held.getMaxStackSize() - held.getCount());
                if (count > 0) { held.grow(count); remainder.shrink(count); }
                if (remainder.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return remainder;
    }
    public ItemStack dropItem(@Nullable ItemStack requested) {
        if (collecting) return ItemStack.EMPTY;
        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < carrySlots(); slot++) {
            EquipmentSlot equipment = carrySlot(slot); ItemStack held = getItemBySlot(equipment);
            if (held.isEmpty()) continue;
            if (requested == null || requested.isEmpty()) { out = held.copy(); setItemSlot(equipment, ItemStack.EMPTY); }
            else if (ItemStack.isSameItemSameTags(held, requested)) {
                out = held.copyWithCount(Math.min(held.getCount(), requested.getCount())); held.shrink(out.getCount());
                if (held.isEmpty()) setItemSlot(equipment, ItemStack.EMPTY);
            }
            if (!out.isEmpty()) break;
        }
        if (carrySlots() == 2 && getMainHandItem().isEmpty() && !getOffhandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, getOffhandItem().copy()); setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        return out;
    }
    public void dropCarried() {
        List<ItemStack> stacks = getCarrying();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        for (ItemStack stack : stacks) if (!stack.isEmpty()) spawnAtLocation(stack, .25F);
    }
    /** Owner-only pickup is available with any hand, as in the pinned entity interaction. */
    public boolean collect(Player player, InteractionHand hand) {
        if (level().isClientSide || collecting || !isAlive() || !isOwner(player) || player.level() != level()
                || player.distanceToSqr(this) > 36 || player.isSpectator()) return false;
        ItemStack placer = CatalogModule.stack("golem");
        if (placer.isEmpty()) return false;
        placer.getOrCreateTag().putLong("props", props()); placer.getOrCreateTag().putInt("xp", rankXp);
        List<ItemStack> carried = getCarrying(); List<ItemEntity> outputs = new ArrayList<>();
        collecting = true; releaseTask(); getNavigation().stop();
        try {
            // Native spawnAtLocation ignores a Forge-canceled addFreshEntity result.
            // Reserve detached carried stacks until every physical output was accepted.
            for (ItemStack stack : carried) if (!stack.isEmpty()) {
                ItemEntity output = new ItemEntity(level(), getX(), getY() + .25, getZ(), stack.copy()); output.setDefaultPickUpDelay();
                if (!level().addFreshEntity(output) || output.isRemoved()) { outputs.forEach(Entity::discard); return false; }
                outputs.add(output);
            }
            ItemEntity output = new ItemEntity(level(), getX(), getY() + .5, getZ(), placer); output.setDefaultPickUpDelay();
            if (!level().addFreshEntity(output) || output.isRemoved()) { outputs.forEach(Entity::discard); return false; }
            outputs.add(output);
            List<ItemStack> current = getCarrying();
            for (int slot = 0; slot < carried.size(); slot++) if (!ItemStack.matches(carried.get(slot), current.get(slot))) { outputs.forEach(Entity::discard); return false; }
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            playSound(GolemEntitySounds.ZAP.get(), 1, 1); discard(); player.swing(hand); return true;
        } finally { if (!isRemoved()) collecting = false; }
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || player.getItemInHand(hand).is(Items.NAME_TAG)) return InteractionResult.PASS;
        if (!isOwner(player)) {
            if (!level().isClientSide && !player.isShiftKeyDown()) player.displayClientMessage(Component.translatable("tc.notowned"), true);
            return player.isShiftKeyDown() ? InteractionResult.PASS : InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) return collect(player, hand) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof GolemBellItem && player instanceof ServerPlayer serverPlayer
                && KnowledgeStore.get(serverPlayer).isResearchKnown("GOLEMDIRECT")) {
            setFollowingOwner(!isFollowingOwner());
            player.displayClientMessage(Component.translatable(isFollowingOwner() ? "golem.follow" : "golem.stay", ""), true);
            level().broadcastEntityEvent(this, (byte)(isFollowingOwner() ? 5 : 8));
            player.swing(hand); playSound(thaumcraft.scanning.ScanningModule.SCAN_SOUND.get(), 1, 1);
        } else if (stack.getItem() instanceof DyeItem dye) {
            setGolemColor((byte)(16 - dye.getDyeColor().getId())); stack.shrink(1);
            player.swing(hand); playSound(GolemEntitySounds.ZAP.get(), 1, 1);
        }
        return InteractionResult.CONSUME;
    }
    @Override public void die(DamageSource source) {
        if (!level().isClientSide && !collecting) { releaseTask(); dropCarried(); }
        super.die(source);
    }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (ItemStack component : design().components()) if (random.nextFloat() < .3F + looting * .15F) {
            ItemStack drop = component.copy(); drop.shrink(random.nextInt(drop.getCount())); spawnAtLocation(drop, .25F);
        }
    }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide) releaseTask();
        super.remove(reason);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("props", props()); tag.putInt("rankXP", rankXp); tag.putByte("gflags", getFlags()); tag.putByte("color", getGolemColor());
        tag.putBoolean("v", validSpawn); tag.putBoolean("HasHome", hasHome());
        if (hasHome()) tag.putLong("homepos", home.asLong());
        if (getOwnerId() != null) tag.putString("OwnerUUID", getOwnerId().toString());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Existing NoAI catalogue saves are visual fixtures, never silently promoted into owned workers.
        validSpawn = tag.getBoolean("v") && !tag.getBoolean("VisualOnly");
        long props = tag.contains("props", Tag.TAG_LONG) ? tag.getLong("props") : 0;
        if (GolemDesign.parse(props).isEmpty()) { props = 0; validSpawn = false; }
        entityData.set(PROPS_HIGH, (int)(props >>> 32)); entityData.set(PROPS_LOW, (int)props);
        setFlags(tag.getByte("gflags")); setGolemColor(tag.getByte("color")); setRankXp(tag.getInt("rankXP"));
        try { setOwnerId(tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : UUID.fromString(tag.getString("OwnerUUID"))); }
        catch (IllegalArgumentException failure) { setOwnerId(null); }
        home = tag.contains("homepos") && (!tag.contains("HasHome") || tag.getBoolean("HasHome")) ? BlockPos.of(tag.getLong("homepos")) : null;
        setNoAi(false); refreshAttributesAndGoals(); firstRun = true;
    }
    @Override public void handleEntityEvent(byte event) {
        if (event >= 5 && event <= 9) {
            for (int count = 0; count < (event == 9 ? 5 : 1); count++) level().addParticle(event == 9 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ENCHANT,
                    getX(), getY() + getBbHeight() + .1, getZ(), 0, .02, 0);
        } else super.handleEntityEvent(event);
    }

    private static final class HomeGoal extends Goal {
        private final ThaumcraftGolemEntity golem; private int idle = 10;
        private Vec3 destination;
        HomeGoal(ThaumcraftGolemEntity golem) { this.golem = golem; setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
        public boolean canUse() {
            if (idle-- > 0) return false;
            idle = 50;
            if(!golem.hasHome()||golem.hasTask()||golem.isFollowingOwner()||golem.getTarget()!=null||!golem.level().hasChunkAt(golem.homePosition()))return false;
            double distance=golem.distanceToSqr(Vec3.atCenterOf(golem.homePosition()));if(distance<5)return false;
            BlockPos home=golem.homePosition();
            destination=distance<=1024?new Vec3(home.getX(),home.getY(),home.getZ()):net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(golem,16,7,new Vec3(home.getX(),home.getY(),home.getZ()),Math.PI/2);
            return destination!=null&&golem.level().hasChunkAt(BlockPos.containing(destination));
        }
        public void start() { if(destination!=null)golem.getNavigation().moveTo(destination.x,destination.y,destination.z,golem.getGolemMoveSpeed()); }
        public boolean canContinueToUse() { return !golem.hasTask() && !golem.getNavigation().isDone() && golem.distanceToSqr(Vec3.atCenterOf(golem.homePosition())) > 3; }
        public void stop() { idle = 50; golem.getNavigation().stop(); }
    }
    private static final class FollowOwnerGoal extends Goal {
        private final ThaumcraftGolemEntity golem; private Player owner; private int recalc; private float water;
        FollowOwnerGoal(ThaumcraftGolemEntity golem) { this.golem = golem; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        public boolean requiresUpdateEveryTick() { return true; }
        public boolean canUse() { owner = golem.getOwnerEntity(); return golem.isFollowingOwner() && owner != null && !owner.isSpectator() && golem.distanceToSqr(owner) >= 100; }
        public boolean canContinueToUse() { return golem.isFollowingOwner() && owner != null && owner.isAlive() && !owner.isSpectator() && !golem.getNavigation().isDone() && golem.distanceToSqr(owner) > 4; }
        public void start() { recalc = 0; water = golem.getPathfindingMalus(BlockPathTypes.WATER); golem.setPathfindingMalus(BlockPathTypes.WATER, 0); }
        public void stop() { owner = null; golem.getNavigation().stop(); golem.setPathfindingMalus(BlockPathTypes.WATER, water); }
        public void tick() {
            if (owner == null) return;
            golem.getLookControl().setLookAt(owner, 10, golem.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (!golem.getNavigation().moveTo(owner, 1) && !golem.isLeashed() && golem.distanceToSqr(owner) >= 144) {
                BlockPos origin = owner.blockPosition();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    BlockPos position = origin.offset(x, 0, z);
                    if (!golem.level().hasChunkAt(position) || !golem.level().getBlockState(position.below()).isSolidRender(golem.level(), position.below())) continue;
                    Vec3 old = golem.position(); golem.setPos(Vec3.atBottomCenterOf(position));
                    if (golem.level().noCollision(golem, golem.getBoundingBox())) { golem.getNavigation().stop(); return; }
                    golem.setPos(old);
                }
            }
        }
    }
    private static final class OwnerDefenceGoal extends Goal {
        private final ThaumcraftGolemEntity golem; private final boolean attacker; private int timestamp; private LivingEntity target;
        OwnerDefenceGoal(ThaumcraftGolemEntity golem, boolean attacker) { this.golem = golem; this.attacker = attacker; setFlags(EnumSet.of(Flag.TARGET)); }
        public boolean canUse() {
            Player owner = golem.getOwnerEntity(); if (owner == null || !golem.isFollowingOwner()) return false;
            target = attacker ? owner.getLastHurtByMob() : owner.getLastHurtMob();
            int time = attacker ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
            return time != timestamp && golem.canAttackTarget(target);
        }
        public void start() { Player owner = golem.getOwnerEntity(); golem.setTarget(target); if (owner != null) timestamp = attacker ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp(); }
    }
    private static final class DartAttackGoal extends Goal {
        private final ThaumcraftGolemEntity golem; private int attackTime = -1, seeTime;
        DartAttackGoal(ThaumcraftGolemEntity golem) { this.golem = golem; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        public boolean requiresUpdateEveryTick() { return true; }
        public boolean canUse() { return golem.getTarget() != null; }
        public boolean canContinueToUse() { return canUse() || !golem.getNavigation().isDone(); }
        public void stop() { seeTime = 0; attackTime = -1; }
        public void tick() {
            LivingEntity target = golem.getTarget(); if (target == null) return;
            double distance = golem.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ());
            boolean visible = golem.getSensing().hasLineOfSight(target);
            seeTime = visible ? seeTime + 1 : 0;
            if (distance <= 256 && seeTime >= 20) golem.getNavigation().stop(); else golem.getNavigation().moveTo(target, 1);
            golem.getLookControl().setLookAt(target, 10, 30);
            if (--attackTime == 0) {
                if (distance > 256 || !visible) return;
                float range = (float)Math.sqrt(distance) / 16;
                golem.performRangedAttack(target, Mth.clamp(range, .1F, 1)); attackTime = Mth.floor(range * 5 + 20);
            } else if (attackTime < 0) attackTime = Mth.floor(Math.sqrt(distance) / 16 * 5 + 20);
        }
    }
    private static final class GolemFlyingMoveControl extends MoveControl {
        private final ThaumcraftGolemEntity golem;
        GolemFlyingMoveControl(ThaumcraftGolemEntity golem) { super(golem); this.golem = golem; }
        public void tick() {
            if (operation != Operation.MOVE_TO) return;
            Vec3 delta = new Vec3(wantedX - golem.getX(), wantedY - golem.getY(), wantedZ - golem.getZ()); double distance = delta.length();
            if (distance < golem.getBoundingBox().getSize()) { operation = Operation.WAIT; golem.setDeltaMovement(golem.getDeltaMovement().scale(.5)); }
            else {
                golem.setDeltaMovement(golem.getDeltaMovement().add(delta.x / distance * .033 * speedModifier, delta.y / distance * .0125 * speedModifier, delta.z / distance * .033 * speedModifier));
                Vec3 facing = golem.getTarget() == null ? golem.getDeltaMovement() : golem.getTarget().position().subtract(golem.position());
                golem.setYRot(-(float)Math.toDegrees(Math.atan2(facing.x, facing.z))); golem.yBodyRot = golem.getYRot();
            }
        }
    }
}
