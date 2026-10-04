package thaumcraft.essentia.centrifuge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Keeps the original placed-block/item ID; the working tile has its own save ID. */
public final class CentrifugeModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE = TILES.register("essentia_centrifuge",
            () -> BlockEntityType.Builder.of(CentrifugeBlockEntity::new, CatalogBlocks.block("centrifuge")).build(null));
    public static final RegistryObject<SoundEvent> PUMP = SOUNDS.register("pump", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "pump")));
    private CentrifugeModule() {}
    public static boolean handlesBlock(String id) { return id.equals("centrifuge"); }
    public static Block createBlock() {
        // BlockTCTile: hardness2/resistance20; Material.WOOD and SoundType.WOOD.
        return new CentrifugeBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2,20)
                .sound(SoundType.WOOD).noOcclusion().isValidSpawn((state,level,pos,type)->false)
                .isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos)->false));
    }
    public static void register(IEventBus bus) { TILES.register(bus); SOUNDS.register(bus); }
}
