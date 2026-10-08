package thaumcraft.essentia.transfuser;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Existing essentia_input/output IDs and original baked models acquire working tiles. */
public final class EssentiaTransfuserModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    public static final RegistryObject<BlockEntityType<EssentiaTransfuserBlockEntity>> TRANSFUSER = TILES.register("essentia_transfuser",
            () -> BlockEntityType.Builder.of(EssentiaTransfuserBlockEntity::new,
                    CatalogBlocks.block("essentia_input"), CatalogBlocks.block("essentia_output")).build(null));
    private EssentiaTransfuserModule() {}
    public static boolean handlesBlock(String id) { return id.equals("essentia_input") || id.equals("essentia_output"); }
    public static Block createBlock(String id) {
        if (!handlesBlock(id)) throw new IllegalArgumentException("Not a BETA26 transfuser: " + id);
        // BlockEssentiaTransport overrides BlockTCTile: hardness1; resistance10 stores
        // an effective 6 in 1.12. No correct-tool gate: canHarvestBlock always returned true.
        return new EssentiaTransfuserBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(1, 6).sound(SoundType.METAL).noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false), id.equals("essentia_input"));
    }
    public static void register(IEventBus bus) { TILES.register(bus); }
}
