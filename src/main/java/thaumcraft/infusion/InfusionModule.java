package thaumcraft.infusion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;
import java.util.List;

public final class InfusionModule {
    public static final List<String> PEDESTALS = List.of("pedestal_arcane", "pedestal_ancient", "pedestal_eldritch");
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, "thaumcraft");
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "thaumcraft");
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<RecipeType<InfusionRecipe>> RECIPE_TYPE = TYPES.register("infusion", () -> RecipeType.simple(id("infusion")));
    public static final RegistryObject<RecipeSerializer<InfusionRecipe>> RECIPE_SERIALIZER = SERIALIZERS.register("infusion", InfusionRecipe.Serializer::new);
    public static final RegistryObject<BlockEntityType<InfusionPedestalBlockEntity>> PEDESTAL = TILES.register("infusion_pedestal", () ->
            BlockEntityType.Builder.of(InfusionPedestalBlockEntity::new, PEDESTALS.stream().map(CatalogBlocks::block).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<InfusionMatrixBlockEntity>> MATRIX = TILES.register("infusion_matrix", () ->
            BlockEntityType.Builder.of(InfusionMatrixBlockEntity::new, CatalogBlocks.block("infusion_matrix")).build(null));
    public static final RegistryObject<BlockEntityType<InfusionStabilizerBlockEntity>> STABILIZER = TILES.register("infusion_stabilizer", () ->
            BlockEntityType.Builder.of(InfusionStabilizerBlockEntity::new, CatalogBlocks.block("stabilizer")).build(null));
    public static final RegistryObject<SoundEvent> START = RegistryObject.create(id("craftstart"), ForgeRegistries.SOUND_EVENTS), FAIL = sound("craftfail"), WAND = sound("wand"),
            LOOP = sound("infuser"), INFUSER_START = sound("infuserstart");
    private InfusionModule() {}
    public static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", value); }
    private static RegistryObject<SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name))); }
    public static boolean handlesBlock(String id) { return id.equals("infusion_matrix") || PEDESTALS.contains(id) || id.equals("stabilizer") || id.equals("inlay"); }
    public static Block createBlock(String id, BlockBehaviour.Properties properties) {
        if (PEDESTALS.contains(id)) return new InfusionPedestalBlock(properties);
        return switch (id) {
            case "infusion_matrix" -> new InfusionMatrixBlock(properties.strength(3));
            case "stabilizer" -> new InfusionStabilizerBlock(properties);
            case "inlay" -> new InfusionInlayBlock(properties.noCollission().lightLevel(s -> 1));
            default -> throw new IllegalArgumentException(id);
        };
    }
    public static void register(IEventBus bus) {
        TILES.register(bus); TYPES.register(bus); SERIALIZERS.register(bus); SOUNDS.register(bus);
        InfusionEffects.register(bus);
    }
}
