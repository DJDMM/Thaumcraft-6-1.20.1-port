package thaumcraft.world.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;
import thaumcraft.Thaumcraft;

/** BETA26's three registered biomes; only Magical Forest belongs in natural generation. */
public final class BiomeModule {
    public static final ResourceKey<Biome> MAGICAL_FOREST = biome("magical_forest");
    public static final ResourceKey<Biome> EERIE = biome("eerie");
    public static final ResourceKey<Biome> ELDRITCH = biome("eldritch");
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, Thaumcraft.MOD_ID);

    static {
        FEATURES.register("big_magic_tree", BigMagicTreeFeature::new);
        FEATURES.register("magical_forest_trees", MagicalForestTreesFeature::new);
        FEATURES.register("forest_silverwood", ForestSilverwoodFeature::new);
        FEATURES.register("magical_forest_mushrooms", ForestMushroomsFeature::new);
    }

    private BiomeModule() {}
    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, name));
    }

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
        bus.addListener(BiomeModule::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Regions.register(new ThaumcraftOverworldRegion());
            SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD,
                    Thaumcraft.MOD_ID, surfaceRules());
        });
    }

    /** Restricted to these biomes and the terrain surface, leaving caves and bedrock intact. */
    public static SurfaceRules.RuleSource surfaceRules() {
        var dirt = SurfaceRules.state(Blocks.DIRT.defaultBlockState());
        var grass = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.waterBlockCheck(-1, 0),
                        SurfaceRules.state(Blocks.GRASS_BLOCK.defaultBlockState())), dirt);
        return SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(ELDRITCH),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dirt)),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(MAGICAL_FOREST, EERIE), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, grass),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dirt)))));
    }
}
