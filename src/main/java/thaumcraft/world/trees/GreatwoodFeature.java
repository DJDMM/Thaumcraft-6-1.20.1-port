package thaumcraft.world.trees;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** TC6's two stacked branch crowns and 2x2 trunk, adapted to modern leaf distances. */
public final class GreatwoodFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean spiders;
    public GreatwoodFeature() { this(false); }
    public GreatwoodFeature(boolean spiders) { super(NoneFeatureConfiguration.CODEC); this.spiders = spiders; }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        TreePlan plan = new TreePlan(context.level(), TreeModule.LOG_GREATWOOD.get(), TreeModule.LEAVES_GREATWOOD.get());
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        int limit = 11 + random.nextInt(11);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos foot = origin.offset(x, 0, z);
            if (!plan.soil(foot)) return false;
            for (int y = 0; y < limit; y++) if (!plan.replaceable(foot.above(y))) {
                if (y < 6) return false;
                limit = y;
                break;
            }
        }
        int trunkHeight = (int) (limit * 0.618);
        crown(plan, origin, limit, 1.2, random);
        crown(plan, origin.above(trunkHeight), limit, 1.66, random);
        if (!plan.place()) return false;
        if (spiders) GreatwoodSpiderNest.place(context.level(), origin, random);
        return true;
    }

    private void crown(TreePlan plan, BlockPos base, int limit, double width, RandomSource random) {
        int height = Math.min(limit - 1, (int) (limit * 0.618));
        int nodesPerLayer = Math.max(1, (int) (1.382 + Math.pow(0.9 * limit / 13.0, 2)));
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(base.above(limit - 4), base.above(height)));
        for (int y = limit - 5; y >= limit * 0.3; y--) {
            double half = limit / 2.0;
            double layer = Math.sqrt(Math.max(0, half * half - (half - y) * (half - y))) * 0.5;
            for (int i = 0; i < nodesPerLayer; i++) {
                double radius = width * layer * (random.nextFloat() + 0.328);
                double angle = random.nextFloat() * Math.PI * 2;
                int x = Mth.floor(radius * Math.sin(angle) + 0.5);
                int z = Mth.floor(radius * Math.cos(angle) + 0.5);
                BlockPos node = base.offset(x, y, z);
                int startY = Math.min(height, (int) (y - Math.sqrt(x * x + z * z) * 0.38));
                BlockPos branchStart = base.above(startY);
                if (plan.lineClear(node, node.above(4)) && plan.lineClear(branchStart, node)) nodes.add(new Node(node, branchStart));
            }
        }
        for (Node node : nodes) {
            for (int dy = 0; dy < 4; dy++) {
                float radius = dy == 0 || dy == 3 ? 2 : 3;
                int size = (int) (radius + 0.618);
                for (int dx = -size; dx <= size; dx++) for (int dz = -size; dz <= size; dz++) {
                    if (Math.pow(Math.abs(dx) + 0.5, 2) + Math.pow(Math.abs(dz) + 0.5, 2) <= radius * radius)
                        plan.leaf(node.pos.offset(dx, dy, dz));
                }
            }
            if (node.start.getY() - base.getY() >= limit * 0.2) plan.branch(node.start, node.pos);
        }
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
            for (int y = 0; y <= height; y++) plan.log(base.offset(dx, y, dz));
    }
    private record Node(BlockPos pos, BlockPos start) {}
}
