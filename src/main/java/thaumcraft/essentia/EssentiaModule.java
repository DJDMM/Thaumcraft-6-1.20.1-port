package thaumcraft.essentia;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

public final class EssentiaModule {
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "thaumcraft");
    public static final RegistryObject<SimpleCraftingRecipeSerializer<EssentiaLabelRecipe>> LABEL_RECIPE = RECIPES.register("essentia_label", () -> new SimpleCraftingRecipeSerializer<>(EssentiaLabelRecipe::new));
    public static final RegistryObject<BlockEntityType<EssentiaJarBlockEntity>> JAR = TILES.register("essentia_jar",
            () -> BlockEntityType.Builder.of(EssentiaJarBlockEntity::new,
                    CatalogBlocks.ENTRIES.get("jar_normal").get(), CatalogBlocks.ENTRIES.get("jar_void").get()).build(null));
    private EssentiaModule() {}
    public static void register(IEventBus bus) { TILES.register(bus); RECIPES.register(bus); }
}
