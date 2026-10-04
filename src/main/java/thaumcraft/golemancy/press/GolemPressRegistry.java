package thaumcraft.golemancy.press;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Existing BETA26 block IDs become operational; no duplicate catalogue anchor. */
public final class GolemPressRegistry {
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"thaumcraft");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"thaumcraft");
    public static final RegistryObject<BlockEntityType<GolemPressBlockEntity>> PRESS_BE=TILES.register("golem_press",()->BlockEntityType.Builder.of(GolemPressBlockEntity::new,CatalogBlocks.block("golem_builder")).build(null));
    public static final RegistryObject<MenuType<GolemPressMenu>> PRESS_MENU=MENUS.register("golem_press",()->IForgeMenuType.create(GolemPressMenu::new));
    private GolemPressRegistry() {}
    public static boolean handlesBlock(String id) {return java.util.Set.of("golem_builder","placeholder_bars","placeholder_anvil","placeholder_cauldron","placeholder_table").contains(id);}
    public static Block createBlock(String id) {return id.equals("golem_builder")?new GolemPressBlock():new GolemPressPlaceholderBlock(id);}
    public static void register(IEventBus bus) {TILES.register(bus);MENUS.register(bus);GolemPressNetwork.register();}
}
