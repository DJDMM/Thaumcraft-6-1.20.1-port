package thaumcraft.research;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** BETA26 ConfigResearch facts and the original, ungated natural Brain sources. */
@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class GolemancyProgressionEvents {
    private GolemancyProgressionEvents() {}

    private static final Set<String> BRAINY_ZOMBIES = Set.of("thaumcraft:brainy_zombie", "thaumcraft:giant_brainy_zombie");
    private static final List<TagKey<Item>> IRON = materialTags("iron", true);
    private static final List<TagKey<Item>> BRASS = materialTags("brass", false);
    private static final List<TagKey<Item>> THAUMIUM = materialTags("thaumium", false);
    private static final Set<net.minecraft.world.level.block.Block> HARDENED_CLAY = Set.of(
            Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
            Blocks.MAGENTA_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
            Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
            Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA,
            Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA,
            Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA);

    /** Pure predicates; items/blocks/entities stay distinct as in ScanItem/ScanBlock/ScanMaterial. */
    public static List<String> scanFacts(@Nullable Object scanned) {
        if (scanned instanceof ItemEntity item) scanned = item.getItem();
        Set<String> facts = new LinkedHashSet<>();
        if (scanned instanceof BlockState state) {
            if (state.is(Blocks.DISPENSER)) facts.add("f_DISPENSER");
            // Material.CLAY was removed in modern Minecraft. Only the original vanilla
            // material/block families are mapped; glazed terracotta/mud are not substitutes.
            if (state.is(Blocks.CLAY) || HARDENED_CLAY.contains(state.getBlock())) facts.add("f_MATCLAY");
            addMaterialFacts(new ItemStack(state.getBlock()), facts);
        } else if (scanned instanceof ItemStack stack && !stack.isEmpty()) {
            if (stack.is(Items.CLAY_BALL)) facts.add("f_MATCLAY");
            if (stack.is(Items.DISPENSER)) facts.add("f_DISPENSER");
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.toString().equals("thaumcraft:brain")) facts.add("f_BRAIN");
            addMaterialFacts(stack, facts);
        } else if (scanned instanceof Entity entity) {
            if (entity instanceof Spider) facts.add("f_SPIDER");
            if (brainy(entity)) facts.add("f_BRAIN");
        }
        return List.copyOf(facts);
    }

    private static void addMaterialFacts(ItemStack stack, Set<String> facts) {
        if (IRON.stream().anyMatch(stack::is)) facts.add("f_MATIRON");
        if (BRASS.stream().anyMatch(stack::is)) facts.add("f_MATBRASS");
        if (THAUMIUM.stream().anyMatch(stack::is)) facts.add("f_MATTHAUMIUM");
    }

    private static List<TagKey<Item>> materialTags(String metal, boolean ore) {
        var paths = new java.util.ArrayList<>(List.of("ingots/" + metal, "storage_blocks/" + metal, "plates/" + metal));
        if (ore) paths.add(0, "ores/" + metal);
        return paths.stream().map(path -> TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("forge", path))).toList();
    }

    private static boolean brainy(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && BRAINY_ZOMBIES.contains(id.toString());
    }

    @SubscribeEvent public static void livingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        boolean brainy = brainy(entity);
        // The modern Drowned did not exist among BETA26's EntityZombie subclasses.
        // Zombie, Husk, ZombieVillager and the renamed ZombiePigman retain their branch.
        if (!brainy && (!(entity instanceof Zombie) || entity instanceof Drowned || !event.isRecentlyHit())) return;
        int roll = entity.level().random.nextInt(10) - event.getLootingLevel();
        // EntityEvents: ordinary zombies <1. EntityBrainyZombie.dropLoot: <=4,
        // including its Giant subclass, without a recently-hit or FakePlayer exclusion.
        if (brainy ? roll <= 4 : roll < 1) {
            double y = entity.getY() + (brainy ? 1.5 : entity.getEyeHeight());
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), y, entity.getZ(), CatalogModule.stack("brain")));
        }
    }
}
