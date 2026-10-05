package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Objects;

/** A seal belongs to one face of a real block, never to an invisible substitute block. */
public record SealPos(BlockPos pos, Direction face) {
    public SealPos { pos=Objects.requireNonNull(pos).immutable(); Objects.requireNonNull(face); }
}
