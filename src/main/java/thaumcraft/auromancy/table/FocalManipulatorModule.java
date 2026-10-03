package thaumcraft.auromancy.table;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Operational table replaces the existing catalogue block without changing its saved ID. */
public final class FocalManipulatorModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "thaumcraft");
    public static final RegistryObject<BlockEntityType<FocalManipulatorBlockEntity>> TABLE = TILES.register("focal_manipulator", () ->
            BlockEntityType.Builder.of(FocalManipulatorBlockEntity::new, CatalogBlocks.block("wand_workbench")).build(null));
    public static final RegistryObject<MenuType<FocalManipulatorMenu>> MENU = MENUS.register("focal_manipulator", () -> IForgeMenuType.create(FocalManipulatorMenu::new));
    private FocalManipulatorModule() {}
    public static boolean handlesBlock(String id) { return "wand_workbench".equals(id); }
    public static Block createBlock(BlockBehaviour.Properties properties) { return new FocalManipulatorBlock(properties); }
    public static void register(IEventBus bus) { TILES.register(bus); MENUS.register(bus); FocalManipulatorNetwork.register(); }
}
