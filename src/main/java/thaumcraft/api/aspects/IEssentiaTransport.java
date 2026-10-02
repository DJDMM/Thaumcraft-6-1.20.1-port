package thaumcraft.api.aspects;

import net.minecraft.core.Direction;

/** Original TC6 suction transport API, adapted from EnumFacing to Direction. */
public interface IEssentiaTransport {
    boolean isConnectable(Direction face);
    boolean canInputFrom(Direction face);
    boolean canOutputTo(Direction face);
    void setSuction(Aspect aspect, int amount);
    Aspect getSuctionType(Direction face);
    int getSuctionAmount(Direction face);
    int takeEssentia(Aspect aspect, int amount, Direction face);
    int addEssentia(Aspect aspect, int amount, Direction face);
    Aspect getEssentiaType(Direction face);
    int getEssentiaAmount(Direction face);
    int getMinimumSuction();
}
