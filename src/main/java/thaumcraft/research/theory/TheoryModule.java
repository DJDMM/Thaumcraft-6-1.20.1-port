package thaumcraft.research.theory;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class TheoryModule {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "thaumcraft");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "thaumcraft");
    public static final RegistryObject<Block> WOOD_TABLE = BLOCKS.register("table_wood", WoodTableBlock::new);
    public static final RegistryObject<Item> WOOD_TABLE_ITEM = ITEMS.register("table_wood", () -> new BlockItem(WOOD_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Block> TABLE = BLOCKS.register("research_table", ResearchTableBlock::new);
    public static final RegistryObject<Item> TABLE_ITEM = ITEMS.register("research_table", () -> new BlockItem(TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SCRIBING_TOOLS = ITEMS.register("scribing_tools", ScribingToolsItem::new);
    public static final RegistryObject<BlockEntityType<ResearchTableBlockEntity>> TABLE_TILE = TILES.register("research_table", () -> BlockEntityType.Builder.of(ResearchTableBlockEntity::new, TABLE.get()).build(null));
    public static final RegistryObject<MenuType<ResearchTableMenu>> MENU = MENUS.register("research_table", () -> IForgeMenuType.create(ResearchTableMenu::new));
    public static final RegistryObject<RecipeSerializer<ScribingRefillRecipe>> REFILL = SERIALIZERS.register("scribing_refill", () -> new SimpleCraftingRecipeSerializer<>(ScribingRefillRecipe::new));

    private TheoryModule() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); MENUS.register(bus); SERIALIZERS.register(bus);
        TheoryNetwork.register();
    }
}
