package thaumcraft.api.aspects;

/** TC6 aspect container contract; amounts are essentia units, not fluid millibuckets. */
public interface IAspectContainer {
    AspectList getAspects();
    void setAspects(AspectList aspects);
    boolean doesContainerAccept(Aspect aspect);
    int addToContainer(Aspect aspect, int amount);
    boolean takeFromContainer(Aspect aspect, int amount);
    @Deprecated boolean takeFromContainer(AspectList aspects);
    boolean doesContainerContainAmount(Aspect aspect, int amount);
    @Deprecated boolean doesContainerContain(AspectList aspects);
    int containerContains(Aspect aspect);
}
