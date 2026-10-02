package thaumcraft.world.trees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** TC6 cross-shaped trunk, buttress roots and rounded crown. */
public final class SilverwoodFeature extends Feature<NoneFeatureConfiguration> {
    public SilverwoodFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        TreePlan plan = new TreePlan(context.level(), TreeModule.LOG_SILVERWOOD.get(), TreeModule.LEAVES_SILVERWOOD.get());
        BlockPos base = context.origin();
        var random = context.random();
        int height = 7 + random.nextInt(4);
        if (!plan.soil(base)) return false;
        for (int y = 0; y < height; y++) {
            plan.log(base.above(y));
            for (Direction direction : Direction.Plane.HORIZONTAL) plan.log(base.above(y).relative(direction));
        }
        plan.log(base.above(height));
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            plan.log(base.offset(x, 0, z));
            if (random.nextInt(3) != 0) plan.log(base.offset(x, 1, z));
            plan.log(base.offset(x, height - 4, z));
            if (random.nextInt(3) == 0) plan.log(base.offset(x, height - 5, z));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            plan.log(base.relative(direction, 2), direction.getAxis());
            plan.log(base.above(height - 4).relative(direction, 2), direction.getAxis());
            BlockPos root = base.below().relative(direction, 2);
            if (plan.replaceable(root)) plan.log(root);
        }
        int top = height + 3 + random.nextInt(3);
        for (int y = height - 5; y <= top; y++) {
            int centerY = Mth.clamp(y, height - 3, height);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                int dy = y - centerY;
                if (x * x + dy * dy + z * z < 10 + random.nextInt(8)) plan.leaf(base.offset(x, y, z));
            }
        }
        if (!plan.place()) return false;
        if (context.level() instanceof net.minecraft.server.level.WorldGenRegion)
            thaumcraft.world.plants.ForestFloraFeature.scatter(context.level(), random, base,
                    thaumcraft.world.plants.PlantModule.SHIMMERLEAF.get());
        return true;
    }
}
