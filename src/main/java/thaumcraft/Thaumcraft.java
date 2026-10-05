package thaumcraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(Thaumcraft.MOD_ID)
public final class Thaumcraft {
    public static final String MOD_ID = "thaumcraft";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    static {
        for (String name : new String[]{"amber", "quicksilver", "fabric", "tallow", "void_seed",
                "ingot_thaumium", "ingot_void", "ingot_brass",
                "nugget_thaumium", "nugget_void", "nugget_brass", "nugget_quicksilver",
                "plate_brass", "plate_iron", "plate_thaumium", "plate_void"}) {
            ITEMS.register(name, () -> new Item(new Item.Properties()));
        }
        for (String name : new String[]{"metal_thaumium", "metal_void", "metal_brass"}) {
            block(name, () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(4.0F, 10.0F)));
        }
        block("stone_arcane", () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS).strength(2.0F, 10.0F)));
        block("stone_arcane_brick", () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS).strength(2.0F, 10.0F)));
        TABS.register("thaumcraft", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.thaumcraft"))
                .icon(() -> new ItemStack(ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "ingot_thaumium"))))
                .displayItems((parameters, output) -> {
                    ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.research.ResearchModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.scanning.ScanningModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.world.WorldModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.alchemy.AlchemyModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.equipment.EquipmentModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.arcane.ArcaneModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.research.theory.TheoryModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.research.celestial.CelestialModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.world.trees.TreeModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.world.plants.PlantModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                })
                .build());
        TABS.register("catalog", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.thaumcraft.catalog"))
                .icon(() -> thaumcraft.catalog.CatalogModule.stack("caster_basic"))
                .displayItems((parameters, output) -> {
                    thaumcraft.catalog.CatalogModule.acceptItems(output);
                    thaumcraft.catalog.blocks.CatalogBlocks.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.catalog.entities.VisualEntitiesModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    thaumcraft.equipment.cleansing.CleansingModule.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                }).build());
    }

    private static void block(String name, java.util.function.Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public Thaumcraft(FMLJavaModLoadingContext context) {
        var bus = context.getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        thaumcraft.research.ResearchModule.register(bus);
        thaumcraft.scanning.ScanningModule.register(bus);
        thaumcraft.world.WorldModule.register(bus);
        thaumcraft.alchemy.AlchemyModule.register(bus);
        thaumcraft.equipment.EquipmentModule.register(bus);
        thaumcraft.arcane.ArcaneModule.register(bus);
        thaumcraft.research.theory.TheoryModule.register(bus);
        thaumcraft.research.celestial.CelestialModule.register(bus);
        thaumcraft.world.trees.TreeModule.register(bus);
        thaumcraft.world.plants.PlantModule.register(bus);
        thaumcraft.world.biome.BiomeModule.register(bus);
        thaumcraft.catalog.CatalogModule.register(bus);
        thaumcraft.catalog.blocks.CatalogBlocks.register(bus);
        thaumcraft.equipment.recharge.RechargeModule.register(bus);
        thaumcraft.equipment.cleansing.CleansingModule.register(bus);
        thaumcraft.essentia.EssentiaModule.register(bus);
        thaumcraft.essentia.production.EssentiaProductionModule.register(bus);
        thaumcraft.essentia.transport.EssentiaTransportModule.register(bus);
        thaumcraft.essentia.centrifuge.CentrifugeModule.register(bus);
        thaumcraft.essentia.thaumatorium.ThaumatoriumModule.register(bus);
        thaumcraft.golemancy.press.GolemPressRegistry.register(bus);
        thaumcraft.golemancy.entity.GolemEntitySounds.register(bus);
        thaumcraft.golemancy.seals.behavior.SealBehaviors.register();
        thaumcraft.golemancy.seals.core.SealRegistry.register(bus);
        thaumcraft.catalog.entities.VisualEntitiesModule.register(bus);
        thaumcraft.infusion.InfusionModule.register(bus);
        thaumcraft.auromancy.AuromancySounds.register(bus);
        thaumcraft.auromancy.media.FocusMediaModule.register(bus);
        thaumcraft.auromancy.remaining.RemainingEffectsModule.register(bus);
        thaumcraft.auromancy.table.FocalManipulatorModule.register(bus);
    }
}
