package thaumcraft.catalog.blocks;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.Thaumcraft;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Complete ConfigBlocks roster. Earlier working blocks retain their registrations. */
public final class CatalogBlocks {
    public record Spec(String id, boolean item, Map<String,List<String>> properties, Map<String,String> defaults) {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "thaumcraft");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    public static final List<Spec> SPECS = load();
    public static final Map<String,RegistryObject<Block>> ENTRIES = new LinkedHashMap<>();
    static {
        Set<ResourceLocation> existing = new HashSet<>();
        for (DeferredRegister<Block> module : List.of(Thaumcraft.BLOCKS, thaumcraft.world.WorldModule.BLOCKS,
                thaumcraft.alchemy.AlchemyModule.BLOCKS, thaumcraft.arcane.ArcaneModule.BLOCKS,
                thaumcraft.research.theory.TheoryModule.BLOCKS, thaumcraft.world.trees.TreeModule.BLOCKS,
                thaumcraft.world.plants.PlantModule.BLOCKS)) module.getEntries().forEach(e -> existing.add(e.getId()));
        for (Spec spec : SPECS) if (!existing.contains(ResourceLocation.fromNamespaceAndPath("thaumcraft",spec.id()))) {
            RegistryObject<Block> block = BLOCKS.register(spec.id(), () -> create(spec));
            ENTRIES.put(spec.id(),block);
            if (spec.item()) ITEMS.register(spec.id(), () -> isEssentiaJar(spec.id())
                    ? new thaumcraft.essentia.EssentiaJarItem(block.get())
                    : thaumcraft.essentia.production.EssentiaProductionModule.handlesBlock(spec.id())
                    ? thaumcraft.essentia.production.EssentiaProductionModule.createBlockItem(spec.id(), block.get())
                    : thaumcraft.essentia.transport.EssentiaTransportModule.handlesBlock(spec.id())
                    ? thaumcraft.essentia.transport.EssentiaTransportModule.createBlockItem(spec.id(), block.get())
                    : thaumcraft.infusion.InfusionModule.handlesBlock(spec.id())
                    ? new thaumcraft.infusion.InfusionBlockItem(block.get())
                    : thaumcraft.essentia.centrifuge.CentrifugeModule.handlesBlock(spec.id())
                    ? new thaumcraft.essentia.centrifuge.CentrifugeBlockItem(block.get())
                    : thaumcraft.essentia.thaumatorium.ThaumatoriumModule.handlesBlock(spec.id())
                    ? new BlockItem(block.get(),new Item.Properties())
                    : spec.id().equals("recharge_pedestal") || thaumcraft.auromancy.table.FocalManipulatorModule.handlesBlock(spec.id())
                    ? new BlockItem(block.get(),new Item.Properties()) : new CatalogBlockItem(block.get()));
        }
    }
    public static final RegistryObject<BlockEntityType<CatalogBlockEntity>> VISUAL_TILE = TILES.register("catalog_visual",
            () -> BlockEntityType.Builder.of(CatalogBlockEntity::new, ENTRIES.entrySet().stream()
                    .filter(e -> special(e.getKey())).map(e -> e.getValue().get()).toArray(Block[]::new)).build(null));
    private CatalogBlocks() {}
    private static boolean isEssentiaJar(String id) { return id.equals("jar_normal") || id.equals("jar_void"); }
    public static boolean special(String id) { return id.startsWith("banner_") || id.startsWith("nitor_") || List.of("jar_brain","centrifuge","pattern_crafter","infusion_matrix").contains(id); }
    private static Block create(Spec spec) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.copy(Blocks.STONE).strength(2).noOcclusion();
        String id=spec.id();
        if(thaumcraft.auromancy.remaining.RemainingEffectsModule.handlesBlock(id))return thaumcraft.auromancy.remaining.RemainingEffectsModule.createBlock(id,props);
        if (isEssentiaJar(id)) return new thaumcraft.essentia.EssentiaJarBlock(id.equals("jar_void"));
        if (thaumcraft.auromancy.table.FocalManipulatorModule.handlesBlock(id)) return thaumcraft.auromancy.table.FocalManipulatorModule.createBlock(props);
        if (thaumcraft.infusion.InfusionModule.handlesBlock(id)) return thaumcraft.infusion.InfusionModule.createBlock(id, props);
        if (thaumcraft.essentia.production.EssentiaProductionModule.handlesBlock(id)) return thaumcraft.essentia.production.EssentiaProductionModule.createBlock(id, props);
        if (thaumcraft.essentia.transport.EssentiaTransportModule.handlesBlock(id)) return thaumcraft.essentia.transport.EssentiaTransportModule.createBlock(id, props);
        if (thaumcraft.essentia.centrifuge.CentrifugeModule.handlesBlock(id)) return thaumcraft.essentia.centrifuge.CentrifugeModule.createBlock();
        if (thaumcraft.essentia.thaumatorium.ThaumatoriumModule.handlesBlock(id)) return thaumcraft.essentia.thaumatorium.ThaumatoriumModule.createBlock(id);
        if (id.equals("recharge_pedestal")) return new thaumcraft.equipment.recharge.RechargePedestalBlock(props);
        if (id.equals("purifying_fluid")) return thaumcraft.equipment.cleansing.CleansingModule.createPurifyingBlock();
        if (id.startsWith("slab_") && !id.startsWith("slab_double_")) return new SlabBlock(props);
        if (id.startsWith("stairs_")) {
            String base=switch(id) { case "stairs_arcane" -> "stone_arcane"; case "stairs_arcane_brick" -> "stone_arcane_brick"; default -> "stone_ancient"; };
            return new StairBlock(() -> ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",base)).defaultBlockState(),props);
        }
        if (id.equals("activator_rail")) return new PoweredRailBlock(BlockBehaviour.Properties.copy(Blocks.ACTIVATOR_RAIL),false) {};
        if (id.startsWith("nitor_")) props.noCollission().lightLevel(s -> 15);
        if (id.startsWith("candle_")) props.lightLevel(s -> 12);
        return CatalogBlock.create(spec,props);
    }
    public static Block block(String id) { return ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id)); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); }
    private static List<Spec> load() {
        try (var reader=new InputStreamReader(Objects.requireNonNull(CatalogBlocks.class.getResourceAsStream("/assets/thaumcraft/catalog/blocks.json")),StandardCharsets.UTF_8)) {
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            List<Spec> result=new ArrayList<>();
            for (var value:json.getAsJsonArray("blocks")) {
                var row=value.getAsJsonObject(); Map<String,List<String>> properties=new LinkedHashMap<>(); Map<String,String> defaults=new LinkedHashMap<>();
                row.getAsJsonObject("properties").entrySet().forEach(e -> { List<String> values=new ArrayList<>(); e.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString())); properties.put(e.getKey(),List.copyOf(values)); });
                row.getAsJsonObject("default_values").entrySet().forEach(e -> defaults.put(e.getKey(),e.getValue().getAsString()));
                result.add(new Spec(row.get("id").getAsString(),row.get("item").getAsBoolean(),Collections.unmodifiableMap(properties),Collections.unmodifiableMap(defaults)));
            }
            return List.copyOf(result);
        } catch (Exception e) { throw new IllegalStateException("Invalid pinned ConfigBlocks inventory",e); }
    }
}
