package thaumcraft.alchemy;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class AlchemyModule {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "thaumcraft");
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "thaumcraft");
    public static final RegistryObject<net.minecraft.world.entity.EntityType<AlumentumProjectile>> ALUMENTUM_ENTITY = ENTITIES.register("alumentum", () -> net.minecraft.world.entity.EntityType.Builder.<AlumentumProjectile>of(AlumentumProjectile::new, net.minecraft.world.entity.MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("thaumcraft:alumentum"));
    public static final RegistryObject<Item> ALUMENTUM = ITEMS.register("alumentum", AlumentumItem::new);
    public static final RegistryObject<Block> CRUCIBLE = BLOCKS.register("crucible", CrucibleBlock::new);
    public static final RegistryObject<Block> NITOR = BLOCKS.register("nitor", () -> new Block(BlockBehaviour.Properties.copy(Blocks.GLOWSTONE).strength(0.2F).noCollission().noOcclusion().lightLevel(state -> 15)));
    public static final RegistryObject<Item> CRUCIBLE_ITEM = ITEMS.register("crucible", () -> new BlockItem(CRUCIBLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> NITOR_ITEM = ITEMS.register("nitor", () -> new BlockItem(NITOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> SALIS_MUNDUS = ITEMS.register("salis_mundus", () -> new SalisMundusItem(new Item.Properties()));
    public static final RegistryObject<BlockEntityType<CrucibleBlockEntity>> CRUCIBLE_TILE = TILES.register("crucible", () -> BlockEntityType.Builder.of(CrucibleBlockEntity::new, CRUCIBLE.get()).build(null));
    public static final RegistryObject<RecipeSerializer<SalisMundusRecipe>> SALIS_RECIPE = RECIPES.register("salis_mundus", () -> new SimpleCraftingRecipeSerializer<>(SalisMundusRecipe::new));
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); RECIPES.register(bus); ENTITIES.register(bus);
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new CrucibleRecipes()));
    }
}
