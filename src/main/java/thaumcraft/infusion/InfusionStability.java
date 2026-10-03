package thaumcraft.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import java.util.*;

/** The release's pair contributions and per-block diminishing returns, including zero-base pedestals. */
public final class InfusionStability {
    public record Surroundings(List<BlockPos> pedestals, List<BlockPos> problems, float gain, float cost, int delay) {}
    private InfusionStability() {}
    public static String id(Level level, BlockPos pos) { return level.hasChunkAt(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath() : ""; }
    public static boolean pillar(Level level, BlockPos pos) { return level.hasChunkAt(pos) && Set.of("pillar_arcane", "pillar_ancient", "pillar_eldritch").contains(id(level, pos)); }
    public static boolean valid(Level level, BlockPos matrix) {
        if (!level.hasChunkAt(matrix.below(2)) || !(level.getBlockState(matrix.below(2)).getBlock() instanceof InfusionPedestalBlock)) return false;
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) if (!pillar(level, matrix.offset(x, -2, z))) return false;
        return true;
    }
    private static float amount(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        String id = id(level, pos);
        if (block instanceof AbstractSkullBlock || id.startsWith("candle_") || id.equals("pedestal_eldritch")) return .1F;
        if (id.equals("stabilizer")) return .25F;
        if (id.equals("inlay")) return .025F;
        return 0;
    }
    private static boolean stabilizer(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock(); String id = id(level, pos);
        return block instanceof AbstractSkullBlock || block instanceof InfusionPedestalBlock || id.startsWith("candle_") || id.equals("inlay") || id.equals("stabilizer");
    }
    private static Object identity(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        // 1.12 had one skull block for all metadata and wall/floor forms.
        return block instanceof AbstractSkullBlock ? AbstractSkullBlock.class : block;
    }
    public static Surroundings scan(Level level, BlockPos matrix) {
        List<BlockPos> pedestals = new ArrayList<>(), problems = new ArrayList<>(); Set<Long> positions = new HashSet<>();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int yy = -3; yy <= 7; yy++) {
            if (x == 0 && z == 0) continue;
            BlockPos pos = matrix.offset(x, -yy, z);
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockState(pos).getBlock() instanceof InfusionPedestalBlock) pedestals.add(pos);
            if (stabilizer(level, pos)) positions.add(pos.asLong());
        }
        Map<Object, Integer> counts = new HashMap<>(); float gain = 0;
        while (!positions.isEmpty()) {
            BlockPos first = BlockPos.of(positions.iterator().next());
            BlockPos opposite = new BlockPos(2 * matrix.getX() - first.getX(), first.getY(), 2 * matrix.getZ() - first.getZ());
            float a = amount(level, first), b = level.hasChunkAt(opposite) ? amount(level, opposite) : 0;
            if (level.hasChunkAt(opposite) && identity(level, first) == identity(level, opposite) && a == b) {
                if (level.getBlockState(first).getBlock() instanceof InfusionPedestalBlock && InfusionPedestalBlock.hasSymmetryPenalty(level, first, opposite)) {
                    gain -= .1F; problems.add(first);
                } else {
                    Object key = identity(level, first); int prior = counts.getOrDefault(key, 0);
                    gain += a * (float)Math.pow(.75, prior); counts.put(key, prior + 1);
                }
            } else { gain -= Math.max(a, b); problems.add(first); }
            positions.remove(first.asLong()); positions.remove(opposite.asLong());
        }
        float cost = 1; int cycle = 10; boolean ancient = true, eldritch = true;
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            ancient &= id(level, matrix.offset(x, -2, z)).equals("pillar_ancient");
            eldritch &= id(level, matrix.offset(x, -2, z)).equals("pillar_eldritch");
            String boost = id(level, matrix.offset(x, -3, z));
            if (boost.equals("matrix_speed")) { cycle--; cost += .01F; }
            if (boost.equals("matrix_cost")) { cycle++; cost -= .02F; }
        }
        if (ancient) { cycle--; cost -= .1F; gain -= .1F; }
        if (eldritch) { cycle -= 3; cost += .05F; gain += .2F; }
        for (BlockPos pos : pedestals) {
            if (id(level, pos).equals("pedestal_ancient")) cost -= .01F;
            if (id(level, pos).equals("pedestal_eldritch")) cost += .0025F;
        }
        return new Surroundings(List.copyOf(pedestals), List.copyOf(problems), gain, Math.max(.5F, cost), Math.max(1, cycle / 2));
    }
}
