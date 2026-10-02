package thaumcraft.catalog.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.registries.ForgeRegistries;

/** Persistent visual NPC. No goals, natural spawns, loot or TC6 progression. */
public final class VisualMobEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MATERIAL = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEAD = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ARMS = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEGS = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ADDON = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HELM = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HEADLESS = SynchedEntityData.defineId(VisualMobEntity.class, EntityDataSerializers.BOOLEAN);

    public VisualMobEntity(EntityType<? extends VisualMobEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
        if (spec().model().equals("wisp") || spec().model().equals("swarm") || spec().model().equals("portal")) setNoGravity(true);
        equipVisualArmor();
    }

    public VisualEntitySpec spec() {
        return VisualEntitySpec.byId(ForgeRegistries.ENTITY_TYPES.getKey(getType()).getPath());
    }

    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(MATERIAL, 0);
        entityData.define(HEAD, 0);
        entityData.define(ARMS, 0);
        entityData.define(LEGS, 0);
        entityData.define(ADDON, 0);
        entityData.define(COLOR, 0xFFFF7E);
        entityData.define(HELM, true);
        entityData.define(HEADLESS, false);
    }
    public int variant() { return entityData.get(VARIANT); }
    private void equipVisualArmor() {
        String prefix=switch(spec().id()) {case "cultist_knight","inhabited_zombie" -> "crimson_plate";case "cultist_cleric" -> "crimson_robe";case "cultist_leader" -> "crimson_praetor";default -> "";};
        if(prefix.isEmpty())return;
        for(String part:java.util.List.of("helm","chest","legs")) {
            var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",prefix+"_"+part));
            if(item instanceof net.minecraft.world.item.ArmorItem armor)setItemSlot(armor.getEquipmentSlot(),new net.minecraft.world.item.ItemStack(item));
        }
        var boots=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","crimson_boots"));
        if(boots!=null && !spec().id().equals("inhabited_zombie"))setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,new net.minecraft.world.item.ItemStack(boots));
    }
    public int material() { return entityData.get(MATERIAL); }
    public int headPart() { return entityData.get(HEAD); }
    public int armsPart() { return entityData.get(ARMS); }
    public int legsPart() { return entityData.get(LEGS); }
    public int addonPart() { return entityData.get(ADDON); }
    public int color() { return entityData.get(COLOR); }
    public boolean hasHelm() { return entityData.get(HELM); }
    public boolean headless() { return entityData.get(HEADLESS); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putInt("Material", material());
        tag.putInt("GolemHead", headPart());
        tag.putInt("GolemArms", armsPart());
        tag.putInt("GolemLegs", legsPart());
        tag.putInt("GolemAddon", addonPart());
        tag.putInt("Color", color());
        tag.putBoolean("Helm", hasHelm());
        tag.putBoolean("Headless", headless());
        tag.putBoolean("VisualOnly", true);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, Math.max(0, tag.contains("PechType") ? tag.getInt("PechType") : tag.getInt("Variant")));
        entityData.set(MATERIAL, Math.floorMod(tag.getInt("Material"), 6));
        entityData.set(HEAD, Math.floorMod(tag.getInt("GolemHead"), 5));
        entityData.set(ARMS, Math.floorMod(tag.getInt("GolemArms"), 5));
        entityData.set(LEGS, Math.floorMod(tag.getInt("GolemLegs"), 4));
        entityData.set(ADDON, Math.floorMod(tag.getInt("GolemAddon"), 4));
        if (tag.contains("Color")) entityData.set(COLOR, tag.getInt("Color") & 0xFFFFFF);
        if (tag.contains("Helm")) entityData.set(HELM, tag.getBoolean("Helm"));
        entityData.set(HEADLESS, tag.getBoolean("Headless"));
        setNoAi(true);
        setPersistenceRequired();
    }
    @Override protected ResourceLocation getDefaultLootTable() { return BuiltInLootTables.EMPTY; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
}
