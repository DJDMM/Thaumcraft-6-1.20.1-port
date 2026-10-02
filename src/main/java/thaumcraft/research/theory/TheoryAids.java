package thaumcraft.research.theory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All fourteen registered BETA26 aid definitions; repeated cards are finite pool weights. */
public final class TheoryAids {
    public static final String BOOKSHELF = "BOOKSHELF";
    public static final String BRAIN_IN_A_JAR = "BRAIN_IN_A_JAR";
    public static final String GLYPHED_STONE = "GLYPHED_STONE";
    public static final String PORTAL_END = "PORTAL_END";
    public static final String PORTAL_NETHER = "PORTAL_NETHER";
    public static final String PORTAL_CRIMSON = "PORTAL_CRIMSON";
    public static final String BASIC_ALCHEMY = "BASIC_ALCHEMY";
    public static final String BASIC_ARTIFICE = "BASIC_ARTIFICE";
    public static final String BASIC_INFUSION = "BASIC_INFUSION";
    public static final String BASIC_AUROMANCY = "BASIC_AUROMANCY";
    public static final String BASIC_GOLEMANCY = "BASIC_GOLEMANCY";
    public static final String BASIC_ELDRITCH = "BASIC_ELDRITCH";
    public static final String ENCHANTMENT_TABLE = "ENCHANTMENT_TABLE";
    public static final String BEACON = "BEACON";
    private static final ResourceLocation CRIMSON_ENTITY = id("cultist_portal_lesser");
    private static final Map<String, List<String>> POOLS;

    static {
        Map<String, List<String>> pools = new LinkedHashMap<>();
        // ConfigResearch.initTheorycraft's order, rather than the old HashMap order.
        pools.put(BOOKSHELF, List.of("balance", "notation", "notation", "study", "study", "study"));
        pools.put(BRAIN_IN_A_JAR, List.of("dark_whispers"));
        pools.put(GLYPHED_STONE, List.of("glyphs"));
        pools.put(PORTAL_END, List.of("portal"));
        pools.put(PORTAL_NETHER, List.of("portal"));
        pools.put(PORTAL_CRIMSON, List.of("portal"));
        pools.put(BASIC_ALCHEMY, List.of("concentrate", "reactions", "synthesis"));
        pools.put(BASIC_ARTIFICE, List.of("calibrate", "tinker", "mind_over_matter"));
        pools.put(BASIC_INFUSION, List.of("measure", "channel", "infuse"));
        pools.put(BASIC_AUROMANCY, List.of("focus", "awareness", "spellbinding"));
        pools.put(BASIC_GOLEMANCY, List.of("sculpting", "scripting", "synergy"));
        // BETA26 registers this aid but never initializes BlocksTC.eldritch. Its pool
        // also names unregistered CardTruth: retain that inert token, not a new card.
        pools.put(BASIC_ELDRITCH, List.of("realization", "revelation", "truth"));
        pools.put(ENCHANTMENT_TABLE, List.of("enchantment"));
        pools.put(BEACON, List.of("beacon"));
        POOLS = Collections.unmodifiableMap(pools);
    }

    private TheoryAids() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
    public static Set<String> keys() { return POOLS.keySet(); }
    public static boolean contains(String key) { return POOLS.containsKey(key); }
    public static List<String> cards(String key) { return POOLS.getOrDefault(key, List.of()); }

    /** Null means an entity aid or original dormant Eldritch aid, not an air-block aid. */
    public static Block block(String key) {
        return switch (key) {
            case BOOKSHELF -> Blocks.BOOKSHELF;
            case BRAIN_IN_A_JAR -> catalogBlock("jar_brain");
            case GLYPHED_STONE -> catalogBlock("stone_ancient_glyphed");
            case PORTAL_END -> Blocks.END_PORTAL;
            case PORTAL_NETHER -> Blocks.NETHER_PORTAL;
            case BASIC_ALCHEMY -> catalogBlock("crucible");
            case BASIC_ARTIFICE -> catalogBlock("arcane_workbench");
            case BASIC_INFUSION -> catalogBlock("infusion_matrix");
            case BASIC_AUROMANCY -> catalogBlock("wand_workbench");
            case BASIC_GOLEMANCY -> catalogBlock("golem_builder");
            case ENCHANTMENT_TABLE -> Blocks.ENCHANTING_TABLE;
            case BEACON -> Blocks.BEACON;
            default -> null;
        };
    }

    private static Block catalogBlock(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(id(path));
        return block == Blocks.AIR ? null : block;
    }

    /** Powers, device states, adjacent bookshelves and research unlocks are not conditions. */
    public static boolean matches(String key, BlockState state) {
        Block block = block(key);
        return state != null && block != null && state.is(block);
    }

    /** Catalogue entities share Java classes; preserve original lesser-portal identity by type. */
    public static boolean matches(String key, Entity entity) {
        return PORTAL_CRIMSON.equals(key) && entity != null
                && CRIMSON_ENTITY.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    /** BETA26 block box is 9×3×9; entity range is a separate AABB, not a sphere. No chunk loads. */
    public static Set<String> find(Level level, BlockPos table) {
        if (level == null || table == null) return Set.of();
        Set<String> detected = new LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(table.offset(-4, -1, -4), table.offset(4, 1, 4))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            for (String aid : keys()) if (matches(aid, state)) detected.add(aid);
        }
        Vec3 centre = Vec3.atCenterOf(table);
        AABB bounds = new AABB(centre, centre).inflate(5);
        for (Entity entity : level.getEntities((Entity)null, bounds))
            if (matches(PORTAL_CRIMSON, entity)) detected.add(PORTAL_CRIMSON);
        // Stable registry order makes serialization/UI independent of scan traversal order.
        Set<String> found = new LinkedHashSet<>();
        for (String aid : keys()) if (detected.contains(aid)) found.add(aid);
        return Collections.unmodifiableSet(found);
    }
}
