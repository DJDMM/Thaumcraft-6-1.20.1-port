package thaumcraft.auromancy.media;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Paid spell entities deliberately use distinct IDs from old harmless catalogue appearances. */
public final class FocusMediaModule {
    public static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"thaumcraft");
    public static final RegistryObject<EntityType<FocusCloudEntity>> CLOUD=TYPES.register("focus_cloud_spell",()->
            EntityType.Builder.<FocusCloudEntity>of(FocusCloudEntity::new,MobCategory.MISC).sized(.15F,.15F).clientTrackingRange(4).updateInterval(5).build("thaumcraft:focus_cloud_spell"));
    public static final RegistryObject<EntityType<FocusMineEntity>> MINE=TYPES.register("focus_mine_spell",()->
            EntityType.Builder.<FocusMineEntity>of(FocusMineEntity::new,MobCategory.MISC).sized(.15F,.15F).clientTrackingRange(4).updateInterval(5).setShouldReceiveVelocityUpdates(true).build("thaumcraft:focus_mine_spell"));
    public static final RegistryObject<EntityType<SpellBatEntity>> SPELL_BAT=TYPES.register("spell_bat_spell",()->
            EntityType.Builder.<SpellBatEntity>of(SpellBatEntity::new,MobCategory.MISC).sized(.5F,.9F).clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(true).build("thaumcraft:spell_bat_spell"));
    private FocusMediaModule(){}
    public static void register(IEventBus bus){TYPES.register(bus);bus.addListener(FocusMediaModule::attributes);}
    private static void attributes(EntityAttributeCreationEvent event){
        event.put(SPELL_BAT.get(),SpellBatEntity.createMobAttributes().add(Attributes.MAX_HEALTH,5).add(Attributes.ATTACK_DAMAGE,1).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,12).build());
    }
}
