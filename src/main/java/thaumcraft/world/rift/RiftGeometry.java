package thaumcraft.world.rift;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Common BETA26 calcSteps; physics and rendering use the same deterministic spine. */
public final class RiftGeometry {
    private RiftGeometry() {}
    public record Path(List<Vec3> points, List<Float> widths) {
        public Path { points = List.copyOf(points); widths = List.copyOf(widths); }
    }
    public static Path path(int seed, int size) {
        List<Vec3> points = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        if (size <= 0) return new Path(points, widths);
        Random random = new Random(seed);
        Vec3 right = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
        Vec3 left = right.scale(-1), rightPosition = Vec3.ZERO, leftPosition = Vec3.ZERO;
        int steps = Mth.ceil(size / 3.0F);
        float girth = size / 300.0F, decrement = girth / steps;
        for (int step = 0; step < steps; step++) {
            girth -= decrement;
            right = right.xRot((float) (random.nextGaussian() * .33)).yRot((float) (random.nextGaussian() * .33));
            rightPosition = rightPosition.add(right.scale(.2));
            points.add(rightPosition); widths.add(girth);
            left = left.xRot((float) (random.nextGaussian() * .33)).yRot((float) (random.nextGaussian() * .33));
            leftPosition = leftPosition.add(left.scale(.2));
            points.add(0, leftPosition); widths.add(0, girth);
        }
        points.add(rightPosition.add(right.scale(.1))); widths.add(0F);
        points.add(0, leftPosition.add(left.scale(.1))); widths.add(0, 0F);
        return new Path(points, widths);
    }
}
