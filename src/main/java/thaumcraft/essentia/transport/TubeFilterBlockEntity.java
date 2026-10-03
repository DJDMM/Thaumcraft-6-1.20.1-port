package thaumcraft.essentia.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;

/** The filter's aspect is a suction template; the raw one-unit tube API remains unfiltered. */
public final class TubeFilterBlockEntity extends TubeBlockEntity implements IAspectContainer {
    private Aspect filter;
    public TubeFilterBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    public Aspect filter() { return filter; }
    public void setFilter(Aspect type) { if (mutable()) { filter = type; changed(); } }
    @Override public AspectList getAspects() { return filter == null ? null : new AspectList().add(filter, -1); }
    @Override public void setAspects(AspectList list) {}
    @Override public boolean doesContainerAccept(Aspect type) { return false; }
    @Override public int addToContainer(Aspect type, int units) { return 0; }
    @Override public boolean takeFromContainer(Aspect type, int units) { return false; }
    @Override @Deprecated public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect type, int units) { return false; }
    @Override @Deprecated public boolean doesContainerContain(AspectList list) { return false; }
    @Override public int containerContains(Aspect type) { return 0; }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (filter != null) tag.putString("AspectFilter", filter.getTag());
    }
    @Override public void load(CompoundTag tag) { super.load(tag); filter = Aspect.getAspect(tag.getString("AspectFilter")); }
}
