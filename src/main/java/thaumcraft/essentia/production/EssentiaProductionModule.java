package thaumcraft.essentia.production;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

import java.util.Set;

/** Functional replacements retain the original catalogue registry IDs and resources. */
public final class EssentiaProductionModule {
    private static final Set<String> BLOCK_IDS = Set.of("smelter_basic", "smelter_thaumium", "smelter_void",
            "alembic", "smelter_aux", "smelter_vent", "bellows");
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "thaumcraft");
    public static final RegistryObject<BlockEntityType<SmelterBlockEntity>> SMELTER = TILES.register("essentia_smelter",
            () -> BlockEntityType.Builder.of(SmelterBlockEntity::new, block("smelter_basic"), block("smelter_thaumium"), block("smelter_void")).build(null));
    public static final RegistryObject<BlockEntityType<AlembicBlockEntity>> ALEMBIC = TILES.register("essentia_alembic",
            () -> BlockEntityType.Builder.of(AlembicBlockEntity::new, block("alembic")).build(null));
    public static final RegistryObject<MenuType<SmelterMenu>> SMELTER_MENU = MENUS.register("essentia_smelter", () -> IForgeMenuType.create(SmelterMenu::new));
    private EssentiaProductionModule() {}
    private static Block block(String id) { return CatalogBlocks.ENTRIES.get(id).get(); }
    public static boolean handlesBlock(String id) { return BLOCK_IDS.contains(id); }
    public static Block createBlock(String id, BlockBehaviour.Properties properties) {
        return switch (id) {
            case "smelter_basic" -> new SmelterBlock(properties, 0);
            case "smelter_thaumium" -> new SmelterBlock(properties, 1);
            case "smelter_void" -> new SmelterBlock(properties, 2);
            case "alembic" -> new AlembicBlock(properties);
            case "smelter_aux" -> new SmelterAttachmentBlock(properties, false);
            case "smelter_vent" -> new SmelterAttachmentBlock(properties, true);
            case "bellows" -> new SmelterBellowsBlock(properties);
            default -> throw new IllegalArgumentException("Unknown essentia production block: " + id);
        };
    }
    public static Item createBlockItem(String id, Block block) { return new BlockItem(block, new Item.Properties()); }
    public static void register(IEventBus bus) { TILES.register(bus); MENUS.register(bus); }
}
