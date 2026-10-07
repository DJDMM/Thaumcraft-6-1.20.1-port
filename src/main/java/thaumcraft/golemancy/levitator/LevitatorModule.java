package thaumcraft.golemancy.levitator;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** The catalogue ID and baked BETA26 models remain unchanged. */
public final class LevitatorModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"thaumcraft");
    public static final RegistryObject<BlockEntityType<LevitatorBlockEntity>> LEVITATOR=TILES.register("arcane_levitator",
            ()->BlockEntityType.Builder.of(LevitatorBlockEntity::new,CatalogBlocks.block("levitator")).build(null));
    private LevitatorModule() {}
    public static boolean handlesBlock(String id) {return id.equals("levitator");}
    public static Block createBlock() {
        // BlockTC -> BlockTCTile overrides: hardness2, effective resistance12, no tool gate.
        return new LevitatorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2,12).sound(SoundType.WOOD)
                .noOcclusion().isValidSpawn((state,level,pos,type)->false).isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos)->false));
    }
    public static void register(IEventBus bus) {TILES.register(bus);}
}
