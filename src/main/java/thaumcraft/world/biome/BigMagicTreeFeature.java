package thaumcraft.world.biome;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** TC6's large oak: 11..21 blocks, attenuation .6618, width 1.25, four leaf layers. */
public final class BigMagicTreeFeature extends Feature<NoneFeatureConfiguration> {
    public BigMagicTreeFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var plan = new ForestTreePlan(context.level(), Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        BlockPos base = context.origin();
        var random = new LegacyRandomSource(context.random().nextLong());
        int limit = 11 + random.nextInt(11);
        if (!plan.soil(base)) return false;
        int obstruction = plan.obstruction(base, base.above(limit - 1));
        if (obstruction >= 0) {
            if (obstruction < 6) return false;
            limit = obstruction;
        }
        int height = Math.min(limit - 1, (int) (limit * .6618));
        int nodesPerLayer = Math.max(1, (int) (1.382 + Math.pow(.9 * limit / 13, 2)));
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(base.above(limit - 4), base.above(height)));
        for (int layer = limit - 4; layer >= 0; layer--) {
            if (layer < limit * .3f) continue;
            float half = limit / 2f;
            float offset = half - layer;
            float size = Math.abs(offset) >= half ? 0 : Mth.sqrt(half * half - offset * offset) * .5f;
            for (int i = 0; i < nodesPerLayer; i++) {
                double radius = 1.25 * size * (random.nextFloat() + .328);
                double angle = random.nextFloat() * 2f * Math.PI;
                BlockPos node = base.offset(Mth.floor(radius * Math.sin(angle) + .5), layer - 1,
                        Mth.floor(radius * Math.cos(angle) + .5));
                int dx = base.getX() - node.getX(), dz = base.getZ() - node.getZ();
                double branchY = node.getY() - Math.sqrt(dx * dx + dz * dz) * .381;
                BlockPos start = new BlockPos(base.getX(),
                        branchY > base.getY() + height ? base.getY() + height : (int) branchY, base.getZ());
                if (plan.lineClear(node, node.above(4)) && plan.lineClear(start, node)) nodes.add(new Node(node, start));
            }
        }
        for (Node node : nodes) for (int dy = 0; dy < 4; dy++) {
            float radius = dy == 0 || dy == 3 ? 2 : 3;
            int size = (int) (radius + .618);
            for (int dx = -size; dx <= size; dx++) for (int dz = -size; dz <= size; dz++)
                if (Math.pow(Math.abs(dx) + .5, 2) + Math.pow(Math.abs(dz) + .5, 2) <= radius * radius)
                    plan.leaf(node.pos.offset(dx, dy, dz));
        }
        plan.branch(base, base.above(height));
        for (Node node : nodes)
            if (node.start.getY() - base.getY() >= limit * .2) plan.branch(node.start, node.pos);
        return plan.place();
    }
    private record Node(BlockPos pos, BlockPos start) {}
}
