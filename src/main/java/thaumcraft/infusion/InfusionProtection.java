package thaumcraft.infusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
public final class InfusionProtection {
    private InfusionProtection() {}
    public static BlockPos find(Level level, BlockPos pos) {
        var source = InfusionInlayBlock.find(level, pos); return source == null ? null : source.getBlockPos();
    }
}
