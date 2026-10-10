package thaumcraft.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All ConfigItems BETA26 forms, without replacing earlier implemented items. */
public final class CatalogModule {
    public record Spec(String id, String legacyItem, int legacyMetadata, String legacyNameKey, String sourceClass, int stackLimit) {}
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Thaumcraft.MOD_ID);
    public static final List<Spec> SPECS = loadSpecs();
    public static final Map<String, RegistryObject<Item>> ENTRIES;

    static {
        Set<ResourceLocation> existing = new HashSet<>();
        for (DeferredRegister<Item> module : List.of(Thaumcraft.ITEMS,
                thaumcraft.research.ResearchModule.ITEMS, thaumcraft.scanning.ScanningModule.ITEMS,
                thaumcraft.world.WorldModule.ITEMS, thaumcraft.alchemy.AlchemyModule.ITEMS,
                thaumcraft.equipment.EquipmentModule.ITEMS, thaumcraft.arcane.ArcaneModule.ITEMS,
                thaumcraft.research.theory.TheoryModule.ITEMS, thaumcraft.research.celestial.CelestialModule.ITEMS,
                thaumcraft.world.trees.TreeModule.ITEMS, thaumcraft.world.plants.PlantModule.ITEMS)) {
            module.getEntries().forEach(entry -> existing.add(entry.getId()));
        }
        Map<String, RegistryObject<Item>> entries = new LinkedHashMap<>();
        for (Spec spec : SPECS) {
            if (!existing.contains(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, spec.id()))) {
                entries.put(spec.id(), ITEMS.register(spec.id(), () -> createItem(spec)));
            }
        }
        ENTRIES = Collections.unmodifiableMap(entries);
    }

    private CatalogModule() {}

    /** Kept in one place so armor and special render adapters can be added independently. */
    private static Item createItem(Spec spec) {
        if (spec.id().equals("causality_collapser")) return new thaumcraft.world.rift.collapser.CausalityCollapserItem();
        if (spec.id().equals("golem")) return new thaumcraft.golemancy.entity.GolemPlacerItem(spec);
        if (spec.id().equals("golem_bell")) return new thaumcraft.golemancy.entity.GolemBellItem(spec);
        if (spec.legacyItem().equals("seal")) return new thaumcraft.golemancy.seals.core.ItemSealPlacer(spec);
        if (spec.id().equals("vis_resonator")) return new thaumcraft.infusion.InfusionUtilityItem(spec);
        if (spec.id().equals("caster_basic") || spec.id().equals("caster_gauntlet")) return new thaumcraft.auromancy.CasterItem(spec);
        if (List.of("focus_1", "focus_2", "focus_3").contains(spec.id())) return new thaumcraft.auromancy.FocusItem(spec);
        if (spec.id().equals("crystal_essence")) return new thaumcraft.alchemy.AspectCrystalItem(spec);
        if (spec.id().equals("phial_empty") || spec.id().equals("phial_filled")) return new thaumcraft.essentia.item.EssentiaPhialItem(spec);
        if (spec.id().equals("label_blank") || spec.id().equals("label_filled")) return new thaumcraft.essentia.item.EssentiaLabelItem(spec);
        Item transport = thaumcraft.essentia.transport.EssentiaTransportModule.createItem(spec);
        if (transport != null) return transport;
        Item mechanic=thaumcraft.equipment.items.ItemMechanics.create(spec);
        if(mechanic!=null) return mechanic;
        Item tool=thaumcraft.equipment.tools.ToolItems.create(spec);
        if(tool!=null) return tool;
        Item armor=thaumcraft.catalog.armor.CatalogArmorItem.create(spec);
        return armor==null?new CatalogItem(spec):armor;
    }

    private static List<Spec> loadSpecs() {
        var stream = CatalogModule.class.getResourceAsStream("/assets/thaumcraft/catalog/items.json");
        if (stream == null) throw new IllegalStateException("Missing pinned TC6 item inventory");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();
            List<Spec> result = new ArrayList<>();
            manifest.getAsJsonArray("items").forEach(element -> {
                var row = element.getAsJsonObject();
                result.add(new Spec(row.get("id").getAsString(), row.get("legacy_item").getAsString(),
                        row.get("legacy_metadata").getAsInt(), row.get("legacy_name_key").getAsString(),
                        row.get("source_class").getAsString(), row.get("stack_limit").getAsInt()));
            });
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid pinned TC6 item inventory", exception);
        }
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    public static ItemStack stack(String id) {
        RegistryObject<Item> entry = ENTRIES.get(id);
        return entry == null ? ItemStack.EMPTY : new ItemStack(entry.get());
    }

    public static Aspect containedAspect(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        ListTag aspects = tag.getList("Aspects", Tag.TAG_COMPOUND);
        return aspects.isEmpty() ? null : Aspect.getAspect(aspects.getCompound(0).getString("key"));
    }

    public static ItemStack aspectStack(String id, Aspect aspect, int amount) {
        ItemStack stack = stack(id);
        if (!stack.isEmpty()) new AspectList().add(aspect, amount).writeToNBT(stack.getOrCreateTag());
        return stack;
    }

    /** Mirrors the BETA26 jar display predicate, whose values range from 0 through 4. */
    public static int jarFillLevel(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        AspectList aspects = new AspectList();
        aspects.readFromNBT(stack.getTag());
        if (aspects.size() == 0) return 0;
        double remaining = 1.0 - aspects.visSize() / 250.0;
        if (remaining == 1.0) return 0;
        if (remaining >= 0.75) return 1;
        if (remaining >= 0.5) return 2;
        if (remaining >= 0.25) return 3;
        return 4;
    }

    private static ItemStack registeredStack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, id));
        return item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack jarSample(String id, Aspect aspect, int amount) {
        ItemStack sample = registeredStack(id);
        if (!sample.isEmpty()) new AspectList().add(aspect, amount).writeToNBT(sample.getOrCreateTag());
        return sample;
    }

    /** BETA26 ByteBuffer was big endian: material index 0 is the most significant byte. */
    public static int legacyGolemByte(long properties, int index) {
        if (index < 0 || index > 7) throw new IllegalArgumentException("Invalid golem byte index " + index);
        return (int)((properties >>> (56 - index * 8)) & 0xFFL);
    }

    public static int golemItemColor(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("props", Tag.TAG_LONG)) return 0xFFFFFF;
        int material = legacyGolemByte(stack.getTag().getLong("props"), 0);
        // ConfigGolems/GolemProperties BETA26 order: wood, iron, clay, brass, thaumium, void.
        int[] colors = {5059370, 16777215, 13071447, 15638812, 5257074, 1445161};
        return material < colors.length ? colors[material] : 0xFFFFFF;
    }

    private static long golemProperties(int material, int head, int arms) {
        return ((long)material << 56) | ((long)head << 48) | ((long)arms << 40);
    }

    /** Finite original metadata roster and representative NBT forms for visual inspection. */
    public static void acceptItems(CreativeModeTab.Output output) {
        for (ItemStack stack : sampleStacks()) output.accept(stack);
    }

    public static List<ItemStack> sampleStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (var entry : ENTRIES.entrySet()) {
            String id = entry.getKey();
            if (id.equals("crystal_essence") || id.equals("phial_filled") || id.equals("label_filled")) {
                int amount = id.equals("phial_filled") ? 10 : 1;
                for (Aspect aspect : Aspect.aspects.values()) result.add(aspectStack(id, aspect, amount));
            } else if (id.equals("primordial_pearl")) {
                for (int damage : new int[]{0, 3, 6}) {
                    ItemStack sample = new ItemStack(entry.getValue().get());
                    sample.setDamageValue(damage);
                    result.add(sample);
                }
            } else if (id.equals("verdant_charm")) {
                for (int type = 0; type <= 2; type++) {
                    ItemStack sample = new ItemStack(entry.getValue().get());
                    sample.getOrCreateTag().putByte("type", (byte)type);
                    result.add(sample);
                }
            } else if (id.equals("grapple_gun")) {
                for (int loaded = 0; loaded <= 1; loaded++) {
                    ItemStack sample = new ItemStack(entry.getValue().get());
                    sample.getOrCreateTag().putByte("loaded", (byte)loaded);
                    result.add(sample);
                }
            } else if (id.equals("golem")) {
                // ConfigItems' four original creative presets. Each material/head/arms index
                // occupies a big-endian byte, as BETA26's ByteBuffer helpers confirm.
                // Extra material samples expose the complete six-entry tint roster.
                for (long properties : new long[]{0L, golemProperties(0, 1, 1), golemProperties(1, 1, 2),
                        golemProperties(4, 1, 3), golemProperties(2, 0, 0), golemProperties(3, 0, 0), golemProperties(5, 0, 0)}) {
                    ItemStack sample = new ItemStack(entry.getValue().get());
                    sample.getOrCreateTag().putLong("props", properties);
                    result.add(sample);
                }
            } else if (id.startsWith("cloth_") || id.startsWith("void_robe_")) {
                result.add(new ItemStack(entry.getValue().get()));
                for (int color : new int[]{0xB02E26, 0x3C44AA}) {
                    ItemStack sample = new ItemStack(entry.getValue().get());
                    sample.getOrCreateTagElement("display").putInt("color", color);
                    result.add(sample);
                }
            } else if (id.equals("fortress_helm")) {
                // The original infusion outputs use goggles byte 1 and mask int 0..2.
                // Absence of mask differs from mask 0 (Grinning Devil).
                for (boolean goggles : new boolean[]{false, true}) {
                    for (int mask = -1; mask <= 2; mask++) {
                        ItemStack sample = new ItemStack(entry.getValue().get());
                        if (goggles) sample.getOrCreateTag().putByte("goggles", (byte)1);
                        if (mask >= 0) sample.getOrCreateTag().putInt("mask", mask);
                        result.add(sample);
                    }
                }
            } else if (id.equals("caster_basic")) {
                result.add(new ItemStack(entry.getValue().get()));
                ItemStack installed = new ItemStack(entry.getValue().get());
                ItemStack focus = stack("focus_1");
                focus.getOrCreateTag().putInt("color", 16734721); // original FocusEffectFire color
                installed.getOrCreateTag().put("focus", focus.save(new CompoundTag()));
                result.add(installed);
            } else if (List.of("focus_1", "focus_2", "focus_3").contains(id)) {
                result.add(new ItemStack(entry.getValue().get()));
                for (int color : new int[]{16734721, 14811135, 5685248}) {
                    ItemStack sample = new ItemStack(entry.getValue().get());
                    sample.getOrCreateTag().putInt("color", color); // fire, frost, earth from ConfigItems.init
                    result.add(sample);
                }
            } else {
                result.add(new ItemStack(entry.getValue().get()));
            }
        }
        // These are block items, so their plain forms remain owned by CatalogBlocks.
        // Tagged samples cover every original fill boundary and four aspect colors.
        for (String id : new String[]{"jar_normal", "jar_void"}) {
            for (Aspect aspect : new Aspect[]{Aspect.AIR, Aspect.FIRE}) {
                for (int amount : new int[]{0, 1, 62, 63, 125, 126, 187, 188, 250}) {
                    ItemStack sample = jarSample(id, aspect, amount);
                    if (!sample.isEmpty()) result.add(sample);
                }
            }
            for (Aspect aspect : new Aspect[]{Aspect.WATER, Aspect.EARTH}) {
                ItemStack sample = jarSample(id, aspect, 250);
                if (!sample.isEmpty()) result.add(sample);
            }
        }
        // ProxyBlock originally selected the *_on item model for metadata 1.
        // Damage preserves that former metadata as visual data in the modern item.
        for (String id : new String[]{"mirror", "mirror_essentia"}) {
            for (int linked = 0; linked <= 1; linked++) {
                ItemStack sample = registeredStack(id);
                if (sample.isEmpty()) continue;
                sample.setDamageValue(linked);
                if (linked == 1) {
                    sample.getOrCreateTag().putInt("linkX", 0);
                    sample.getOrCreateTag().putInt("linkY", 64);
                    sample.getOrCreateTag().putInt("linkZ", 0);
                    sample.getOrCreateTag().putInt("linkDim", 0);
                }
                result.add(sample);
            }
        }
        result.forEach(thaumcraft.equipment.tools.ToolItems::initializeStack);
        return List.copyOf(result);
    }
}
