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

/** All 43 original registrations, currently a visual catalogue without TC6 AI. */
public final class VisualEntitiesModule {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final Map<String, RegistryObject<EntityType<VisualMobEntity>>> LIVING = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<EntityType<VisualEffectEntity>>> EFFECTS = new LinkedHashMap<>();

    static {
        for (VisualEntitySpec spec : VisualEntitySpec.ALL) {
            if (spec.id().equals("alumentum")) continue; // Existing working projectile owns this registry ID.
            if (spec.living()) {
                var type = TYPES.register(spec.id(), () -> EntityType.Builder.<VisualMobEntity>of(
                        VisualMobEntity::new, MobCategory.MISC).sized(spec.width(), spec.height())
                        .clientTrackingRange(Math.max(1, spec.trackingBlocks() / 16))
                        .updateInterval(spec.updateInterval()).setShouldReceiveVelocityUpdates(spec.trackVelocity())
                        .build("thaumcraft:" + spec.id()));
                LIVING.put(spec.id(), type);
                ITEMS.register(spec.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(type,
                        spec.eggBase(), spec.eggSpots(), new Item.Properties()));
            } else {
                var type = TYPES.register(spec.id(), () -> EntityType.Builder.<VisualEffectEntity>of(
                        VisualEffectEntity::new, MobCategory.MISC).sized(spec.width(), spec.height())
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
        bus.addListener(VisualEntitiesModule::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        var attributes = VisualMobEntity.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 0).build();
        LIVING.values().forEach(type -> event.put(type.get(), attributes));
    }
}
