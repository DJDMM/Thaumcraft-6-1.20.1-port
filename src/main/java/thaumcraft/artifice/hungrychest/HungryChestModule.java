package thaumcraft.artifice.hungrychest;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

public final class HungryChestModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"thaumcraft");
    public static final RegistryObject<BlockEntityType<HungryChestBlockEntity>> HUNGRY_CHEST=TILES.register("hungry_chest",
            ()->BlockEntityType.Builder.of(HungryChestBlockEntity::new,CatalogBlocks.block("hungry_chest")).build(null));
    private HungryChestModule() {}
    public static boolean handlesBlock(String id) {return id.equals("hungry_chest");}
    public static Block createBlock() {return new HungryChestBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2.5F,2.5F).sound(SoundType.WOOD)
            .noOcclusion().isValidSpawn((state,level,pos,type)->false).isSuffocating((state,level,pos)->false).isViewBlocking((state,level,pos)->false));}
    public static BlockItem createBlockItem(Block block) {return new HungryChestBlockItem(block,new Item.Properties());}
    public static void register(IEventBus bus) {TILES.register(bus);}
}
