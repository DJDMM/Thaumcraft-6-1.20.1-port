package thaumcraft.arcane;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class ArcaneModule {
    public static final String[] PRIMALS = {"aer", "ignis", "aqua", "terra", "ordo", "perditio"};
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "thaumcraft");
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, "thaumcraft");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "thaumcraft");
    public static final RegistryObject<Block> WORKBENCH = BLOCKS.register("arcane_workbench", ArcaneWorkbenchBlock::new);
    public static final RegistryObject<Item> WORKBENCH_ITEM = ITEMS.register("arcane_workbench", () -> new BlockItem(WORKBENCH.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ArcaneWorkbenchBlockEntity>> WORKBENCH_TILE = TILES.register("arcane_workbench", () -> BlockEntityType.Builder.of(ArcaneWorkbenchBlockEntity::new, WORKBENCH.get()).build(null));
    public static final RegistryObject<MenuType<ArcaneWorkbenchMenu>> MENU = MENUS.register("arcane_workbench", () -> IForgeMenuType.create(ArcaneWorkbenchMenu::new));
    public static final RegistryObject<RecipeType<ArcaneRecipe>> RECIPE_TYPE = TYPES.register("arcane_shaped", () -> new RecipeType<>() { public String toString() { return "thaumcraft:arcane_shaped"; } });
    public static final RegistryObject<RecipeSerializer<ArcaneRecipe>> RECIPE_SERIALIZER = SERIALIZERS.register("arcane_shaped", ArcaneRecipe.Serializer::new);

    private ArcaneModule() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); MENUS.register(bus); TYPES.register(bus); SERIALIZERS.register(bus);
    }
}
