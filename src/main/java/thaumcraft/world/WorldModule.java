package thaumcraft.world;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.Thaumcraft;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.feature.PrimalCrystalFeature;
import thaumcraft.world.feature.SingleOreFeature;

public final class WorldModule {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Thaumcraft.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Thaumcraft.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Thaumcraft.MOD_ID);
    public static final RegistryObject<Block> ORE_AMBER = ore("ore_amber", true);
    public static final RegistryObject<Block> ORE_CINNABAR = ore("ore_cinnabar", false);
    public static final RegistryObject<Block> ORE_QUARTZ = ore("ore_quartz", true);
    public static final Map<String, RegistryObject<Block>> CRYSTALS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Item>> VIS_CRYSTALS = new LinkedHashMap<>();
    public static final Map<String, Integer> CRYSTAL_COLORS = Map.of(
            "aer", 0xffff7e, "ignis", 0xff5a01, "aqua", 0x3cd4fc,
            "terra", 0x56c000, "ordo", 0xd5d4ec, "perditio", 0x70667d);

    static {
        for (String aspect : new String[]{"aer", "ignis", "aqua", "terra", "ordo", "perditio"}) {
            RegistryObject<Block> crystal = BLOCKS.register("crystal_" + aspect,
                    () -> new AmethystClusterBlock(7, 3,
                            BlockBehaviour.Properties.copy(Blocks.AMETHYST_CLUSTER)
                                    .strength(0.5F).sound(SoundType.AMETHYST).lightLevel(state -> 7)));
            CRYSTALS.put(aspect, crystal);
            ITEMS.register("crystal_" + aspect, () -> new BlockItem(crystal.get(), new Item.Properties()));
            VIS_CRYSTALS.put(aspect, ITEMS.register("vis_crystal_" + aspect, () -> new Item(new Item.Properties())));
        }
        FEATURES.register("ore_amber", () -> new SingleOreFeature(ORE_AMBER, true));
        FEATURES.register("ore_cinnabar", () -> new SingleOreFeature(ORE_CINNABAR, false));
        FEATURES.register("ore_quartz", () -> new SingleOreFeature(ORE_QUARTZ, false));
        FEATURES.register("primal_crystals", PrimalCrystalFeature::new);
    }

    private WorldModule() {}

    private static RegistryObject<Block> ore(String id, boolean xp) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new DropExperienceBlock(
                BlockBehaviour.Properties.copy(Blocks.IRON_ORE).strength(3.0F, 5.0F),
                UniformInt.of(xp ? 1 : 0, xp ? 4 : 0)));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
        MinecraftForge.EVENT_BUS.addListener(AuraManager::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(AuraManager::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(AuraManager::onLevelTick);
    }

}
