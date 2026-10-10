package thaumcraft.catalog.entities;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.auromancy.projectile.FocusProjectileEntity;
import thaumcraft.golemancy.entity.ThaumcraftGolemEntity;

/** Original registrations: catalogue mobs and effects, with operational projectiles retaining their IDs. */
public final class VisualEntitiesModule {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final Map<String, RegistryObject<EntityType<VisualMobEntity>>> LIVING = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<EntityType<VisualEffectEntity>>> EFFECTS = new LinkedHashMap<>();
    public static final RegistryObject<EntityType<ThaumcraftGolemEntity>> GOLEM = TYPES.register("golem", () ->
            EntityType.Builder.<ThaumcraftGolemEntity>of(ThaumcraftGolemEntity::new,MobCategory.MISC).sized(.4F,.9F)
                    .clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(true).build("thaumcraft:golem"));
    public static final RegistryObject<EntityType<thaumcraft.golemancy.entity.GolemDartEntity>> GOLEM_DART=TYPES.register("golem_dart",()->
            EntityType.Builder.<thaumcraft.golemancy.entity.GolemDartEntity>of(thaumcraft.golemancy.entity.GolemDartEntity::new,MobCategory.MISC).sized(.2F,.2F)
                    .clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(false).build("thaumcraft:golem_dart"));
    public static final RegistryObject<EntityType<thaumcraft.golemancy.entity.GolemOrbEntity>> GOLEM_ORB=TYPES.register("golem_orb",()->
            EntityType.Builder.<thaumcraft.golemancy.entity.GolemOrbEntity>of(thaumcraft.golemancy.entity.GolemOrbEntity::new,MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(true).build("thaumcraft:golem_orb"));
    public static final RegistryObject<EntityType<FocusProjectileEntity>> FOCUS_PROJECTILE = TYPES.register("focus_projectile", () ->
            EntityType.Builder.<FocusProjectileEntity>of(FocusProjectileEntity::new,MobCategory.MISC).sized(.15F,.15F)
                    .clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(true).build("thaumcraft:focus_projectile"));
    public static final RegistryObject<EntityType<thaumcraft.world.rift.collapser.CausalityCollapserEntity>> CAUSALITY_COLLAPSER = TYPES.register("causality_collapser", () ->
            EntityType.Builder.<thaumcraft.world.rift.collapser.CausalityCollapserEntity>of(thaumcraft.world.rift.collapser.CausalityCollapserEntity::new,MobCategory.MISC)
                    .sized(.25F,.25F).clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(true).build("thaumcraft:causality_collapser"));

    static {
        ITEMS.register("golem_spawn_egg", () -> new ForgeSpawnEggItem(GOLEM,6842578,8421504,new Item.Properties()));
        for (VisualEntitySpec spec : VisualEntitySpec.ALL) {
            if (java.util.Set.of("alumentum","focus_projectile","golem","golem_dart","golem_orb","causality_collapser").contains(spec.id())) continue;
            if (spec.living()) {
                var type = TYPES.register(spec.id(), () -> EntityType.Builder.<VisualMobEntity>of(
                        spec.id().equals("wisp") ? thaumcraft.world.rift.WispEntity::new : VisualMobEntity::new,
                        spec.id().equals("wisp") ? MobCategory.MONSTER : MobCategory.MISC).sized(spec.width(), spec.height())
                        .clientTrackingRange(Math.max(1, spec.trackingBlocks() / 16))
                        .updateInterval(spec.updateInterval()).setShouldReceiveVelocityUpdates(spec.trackVelocity())
                        .build("thaumcraft:" + spec.id()));
                LIVING.put(spec.id(), type);
                ITEMS.register(spec.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(type,
                        spec.eggBase(), spec.eggSpots(), new Item.Properties()));
            } else {
                var type = TYPES.register(spec.id(), () -> EntityType.Builder.<VisualEffectEntity>of(
                        (entityType, level) -> spec.id().equals("flux_rift")
                                ? new thaumcraft.world.rift.FluxRiftEntity(entityType, level) : new VisualEffectEntity(entityType, level), MobCategory.MISC).sized(spec.width(), spec.height())
                        .clientTrackingRange(Math.max(1, spec.trackingBlocks() / 16))
                        .updateInterval(spec.updateInterval()).setShouldReceiveVelocityUpdates(spec.trackVelocity())
                        .build("thaumcraft:" + spec.id()));
                EFFECTS.put(spec.id(), type);
            }
        }
    }

    private VisualEntitiesModule() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        ITEMS.register(bus);
        thaumcraft.world.rift.WispSounds.register(bus);
        bus.addListener(VisualEntitiesModule::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        var attributes = VisualMobEntity.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 0).build();
        LIVING.forEach((id,type) -> event.put(type.get(), id.equals("wisp")
                ? thaumcraft.world.rift.WispEntity.attributes().build() : attributes));
        event.put(GOLEM.get(),ThaumcraftGolemEntity.attributes().build());
    }
}
