package thaumcraft.auromancy.remaining;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Reuses the two original catalogue block IDs rather than registering replacements. */
public final class RemainingEffectsModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<BlockEntityType<RiftHoleBlockEntity>> HOLE = TILES.register("hole", () ->
            BlockEntityType.Builder.of(RiftHoleBlockEntity::new, CatalogBlocks.block("hole")).build(null));
    public static final RegistryObject<EntityType<FocusExchangeItemEntity>> SPECIAL_ITEM = ENTITIES.register("focus_exchange_item", () ->
            EntityType.Builder.<FocusExchangeItemEntity>of(FocusExchangeItemEntity::new, MobCategory.MISC).sized(.25F,.25F)
                    .clientTrackingRange(4).updateInterval(20).setShouldReceiveVelocityUpdates(true).build("thaumcraft:focus_exchange_item"));
    public static final RegistryObject<SoundEvent> JACOBS = SOUNDS.register("jacobs", () -> SoundEvent.createVariableRangeEvent(id("jacobs")));
    private RemainingEffectsModule() {}
    public static boolean handlesBlock(String id) { return id.equals("hole") || id.equals("effect_sap"); }
    public static Block createBlock(String id, BlockBehaviour.Properties ignored) {
        return switch (id) {
            case "hole" -> new RiftHoleBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(-1, 6_000_000)
                    .sound(SoundType.WOOL).noCollission().noOcclusion().lightLevel(state -> 10).noLootTable());
            case "effect_sap" -> new CurseSapBlock(BlockBehaviour.Properties.copy(Blocks.AIR).replaceable().noCollission()
                    .noOcclusion().randomTicks().strength(0, 999).lightLevel(state -> 7).noLootTable());
            default -> throw new IllegalArgumentException(id);
        };
    }
    public static void register(IEventBus bus) { TILES.register(bus); ENTITIES.register(bus); SOUNDS.register(bus); }
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
}
