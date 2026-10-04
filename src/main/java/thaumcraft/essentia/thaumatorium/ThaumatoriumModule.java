package thaumcraft.essentia.thaumatorium;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Existing original IDs acquire working tiles, rather than a second visual block. */
public final class ThaumatoriumModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"thaumcraft");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"thaumcraft");
    public static final RegistryObject<BlockEntityType<ThaumatoriumBlockEntity>> BASE=TILES.register("essentia_thaumatorium",()->BlockEntityType.Builder.of(ThaumatoriumBlockEntity::new,CatalogBlocks.block("thaumatorium")).build(null));
    public static final RegistryObject<BlockEntityType<ThaumatoriumTopBlockEntity>> TOP=TILES.register("essentia_thaumatorium_top",()->BlockEntityType.Builder.of(ThaumatoriumTopBlockEntity::new,CatalogBlocks.block("thaumatorium_top")).build(null));
    public static final RegistryObject<MenuType<ThaumatoriumMenu>> MENU=MENUS.register("thaumatorium",()->IForgeMenuType.create(ThaumatoriumMenu::new));
    private ThaumatoriumModule() {}
    public static boolean handlesBlock(String id) { return id.equals("thaumatorium")||id.equals("thaumatorium_top")||id.equals("brain_box"); }
    public static Block createBlock(String id) {
        // 1.12 setResistance(r) stored r*3 and getExplosionResistance returned that/5.
        // Modern strength(h,r) stores the effective resistance directly: original20/10 become12/6.
        // BlockTCTile/BrainBox.canHarvestBlock always returns true; copying IRON_BLOCK
        // would inherit requiresCorrectToolForDrops and wrongly suppress hand/stick loot.
        var properties=BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(2,12).sound(SoundType.METAL).noOcclusion();
        return id.equals("brain_box")?new BrainBoxBlock(properties.strength(1,6)):new ThaumatoriumBlock(properties,id.equals("thaumatorium_top"));
    }
    public static void register(IEventBus bus) { TILES.register(bus);MENUS.register(bus);ThaumatoriumNetwork.register(); }
}
