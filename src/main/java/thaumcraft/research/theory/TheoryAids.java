package thaumcraft.research.theory;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The three BETA26 aid pools currently implemented; duplicate entries are deliberate weights. */
public final class TheoryAids {
    public static final String BOOKSHELF = "BOOKSHELF";
    public static final String ENCHANTMENT_TABLE = "ENCHANTMENT_TABLE";
    public static final String BEACON = "BEACON";
    private static final Map<String, List<String>> POOLS;

    static {
        Map<String, List<String>> pools = new LinkedHashMap<>();
        pools.put(BOOKSHELF, List.of("balance", "notation", "notation", "study", "study", "study"));
        pools.put(ENCHANTMENT_TABLE, List.of("enchantment"));
        pools.put(BEACON, List.of("beacon"));
        POOLS = Collections.unmodifiableMap(pools);
    }

    private TheoryAids() {}
    public static Set<String> keys() { return POOLS.keySet(); }
    public static boolean contains(String key) { return POOLS.containsKey(key); }
    public static List<String> cards(String key) { return POOLS.getOrDefault(key, List.of()); }
    public static Block block(String key) {
        return switch (key) {
            case BOOKSHELF -> Blocks.BOOKSHELF;
            case ENCHANTMENT_TABLE -> Blocks.ENCHANTING_TABLE;
            case BEACON -> Blocks.BEACON;
            default -> null;
        };
    }

    /** Beacon power and enchanting-table bookshelves are not conditions in the original aids. */
    public static boolean matches(String key, BlockState state) {
        Block block = block(key);
        return state != null && block != null && state.is(block);
    }
}
