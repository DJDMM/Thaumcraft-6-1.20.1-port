package thaumcraft.essentia.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;

import java.util.List;

/** The existing six catalogue IDs now implement BETA26's sided suction API. */
public final class EssentiaTransportModule {
    public static final List<String> IDS = List.of("tube", "tube_filter", "tube_restrict", "tube_oneway", "tube_valve", "tube_buffer");
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> CREAK = sound("creak"), SQUEEK = sound("squeek"), TOOL = sound("tool"), KEY = sound("key");
    public static final RegistryObject<BlockEntityType<TubeBlockEntity>> TUBE = TILES.register("essentia_tube",
            () -> BlockEntityType.Builder.of(TubeBlockEntity::create, IDS.stream()
                    .map(id -> CatalogBlocks.ENTRIES.get(id).get()).toArray(Block[]::new)).build(null));
    private EssentiaTransportModule() {}
    private static RegistryObject<net.minecraft.sounds.SoundEvent> sound(String key) {
        return SOUNDS.register(key, () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", key)));
    }
    public static void register(IEventBus bus) { TILES.register(bus); SOUNDS.register(bus); }
    public static boolean handlesBlock(String id) { return IDS.contains(id); }
    public static Block createBlock(String id, BlockBehaviour.Properties properties) {
        return TubeBlock.create(id, properties.strength(.5F, 5F).sound(net.minecraft.world.level.block.SoundType.METAL).noOcclusion());
    }
    public static BlockItem createBlockItem(String id, Block block) { return new TubeBlockItem(block); }
    public static Item createItem(CatalogModule.Spec spec) {
        return spec.id().equals("resonator") ? new EssentiaResonatorItem() : null;
    }

    /** Checks the adjacent opposing face without loading chunks; callers check their own face. */
    public static IEssentiaTransport neighbor(Level level, BlockPos pos, Direction direction) {
        if (level == null || direction == null || !level.hasChunkAt(pos.relative(direction))) return null;
        if (level.getBlockEntity(pos.relative(direction)) instanceof IEssentiaTransport transport
                && transport.isConnectable(direction.getOpposite())) return transport;
        return null;
    }
}
