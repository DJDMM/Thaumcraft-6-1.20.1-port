package thaumcraft.catalog.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** Summonable visual effect; does not cast spells, drop items or alter terrain. */
public class VisualEffectEntity extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(VisualEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(VisualEffectEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> RIFT_SIZE = SynchedEntityData.defineId(VisualEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RIFT_SEED = SynchedEntityData.defineId(VisualEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RED = SynchedEntityData.defineId(VisualEffectEntity.class, EntityDataSerializers.BOOLEAN);
    public VisualEffectEntity(EntityType<? extends VisualEffectEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        if(spec().id().equals("bottle_taint"))entityData.set(ITEM,new ItemStack(ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","bottle_taint"))));
    }
    public VisualEntitySpec spec() { return VisualEntitySpec.byId(ForgeRegistries.ENTITY_TYPES.getKey(getType()).getPath()); }
    public int color() { return entityData.get(COLOR); }
    public ItemStack visualItem() { return entityData.get(ITEM); }
    public int riftSize() {return entityData.get(RIFT_SIZE);}
    public int riftSeed() {return entityData.get(RIFT_SEED);}
    public boolean red() {return entityData.get(RED);}
    @Override protected void defineSynchedData() {
        entityData.define(COLOR, 0x9966FF);
        entityData.define(ITEM, new ItemStack(Items.GLASS_BOTTLE));
        entityData.define(RIFT_SIZE,60);entityData.define(RIFT_SEED,187);entityData.define(RED,false);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Color")) entityData.set(COLOR, tag.getInt("Color") & 0xFFFFFF);
        if (tag.contains("Item", 10)) entityData.set(ITEM, ItemStack.of(tag.getCompound("Item")));
        if(tag.contains("RiftSize"))entityData.set(RIFT_SIZE,net.minecraft.util.Mth.clamp(tag.getInt("RiftSize"),1,300));
        if(tag.contains("RiftSeed"))entityData.set(RIFT_SEED,tag.getInt("RiftSeed"));
        entityData.set(RED,tag.getBoolean("Red"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Color", color());
        tag.put("Item", visualItem().save(new CompoundTag()));
        tag.putBoolean("VisualOnly", true);
        tag.putInt("RiftSize",riftSize());tag.putInt("RiftSeed",riftSeed());tag.putBoolean("Red",red());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
