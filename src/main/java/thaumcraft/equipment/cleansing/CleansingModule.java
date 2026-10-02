package thaumcraft.equipment.cleansing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.catalog.CatalogModule;

/** Operational replacements for the existing TC6 catalog entries. Blocks/items are wired by their owners. */
public final class CleansingModule {
    public static final ResourceLocation PURE_ID = ResourceLocation.fromNamespaceAndPath("thaumcraft", "purifying_fluid");
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, "thaumcraft");
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, "thaumcraft");
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final RegistryObject<FluidType> PURE_TYPE = FLUID_TYPES.register("purifying_fluid", PurifyingFluidType::new);
    public static final RegistryObject<FlowingFluid> PURE = FLUIDS.register("purifying_fluid", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING_PURE = FLUIDS.register("flowing_purifying_fluid", () -> new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<MobEffect> WARP_WARD = EFFECTS.register("warp_ward", WarpWardEffect::new);
    /** Modern replacement for Forge 1.12's universal fluid bucket, not an extra original ConfigItems entry. */
    public static final RegistryObject<Item> PURE_BUCKET = ITEMS.register("purifying_fluid_bucket", () ->
            new BucketItem(PURE, new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET).rarity(Rarity.RARE)));

    private CleansingModule() {}

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(PURE_TYPE, PURE, FLOWING_PURE)
                .block(CleansingModule::block).bucket(PURE_BUCKET)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5).explosionResistance(100);
    }

    public static LiquidBlock block() {
        Block block = ForgeRegistries.BLOCKS.getValue(PURE_ID);
        if (!(block instanceof PurifyingFluidBlock fluid))
            throw new IllegalStateException("purifying_fluid must use CleansingModule.createPurifyingBlock()");
        return fluid;
    }

    /** CatalogBlocks.create: replace the generic block for purifying_fluid with this factory. */
    public static Block createPurifyingBlock() {
        return new PurifyingFluidBlock(PURE, BlockBehaviour.Properties.copy(Blocks.WATER)
                .mapColor(MapColor.COLOR_LIGHT_GRAY).lightLevel(state -> 5).noLootTable());
    }

    /** CatalogModule.createItem: call before falling back to CatalogItem. Returns null for unrelated entries. */
    public static Item create(CatalogModule.Spec spec) {
        return switch (spec.id()) {
            case "sanity_soap" -> new SanitySoapItem(new Item.Properties().stacksTo(spec.stackLimit()));
            case "bath_salts" -> new BathSaltsItem(new Item.Properties().stacksTo(spec.stackLimit()));
            default -> null;
        };
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        EFFECTS.register(bus);
        ITEMS.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                net.minecraft.world.level.block.DispenserBlock.registerBehavior(PURE_BUCKET.get(),
                        net.minecraftforge.fluids.DispenseFluidContainer.getInstance())));
        CleansingNetwork.register();
    }
}
