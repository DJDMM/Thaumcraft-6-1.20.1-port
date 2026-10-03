package thaumcraft.essentia.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Mixed-aspect ten-unit buffer, per-arm choke and stronger-consumer reservation. */
public final class TubeBufferBlockEntity extends TubeBlockEntity implements IAspectContainer {
    public static final int CAPACITY = 10;
    private AspectList contents = new AspectList();
    private final byte[] chokedSides = new byte[6];
    private int bellows = -1, bufferTicks;
    public TubeBufferBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    public int choke(Direction face) { return face == null ? 0 : chokedSides[face.get3DDataValue()]; }
    public void cycleChoke(Direction face) {
        if (mutable() && face != null) { chokedSides[face.get3DDataValue()] = (byte) ((choke(face) + 1) % 3); changed(); }
    }
    public int bellowsCount() { return Math.max(0, bellows); }
    @Override public AspectList getAspects() { return contents.copy(); }
    @Override public void setAspects(AspectList list) {} // Original API does not replace the buffer.
    @Override public int addToContainer(Aspect type, int units) {
        if (!mutable() || type == null || units != 1 || contents.visSize() >= CAPACITY) return units;
        contents.add(type, 1); changed(); return 0;
    }
    @Override public boolean takeFromContainer(Aspect type, int units) {
        if (!mutable() || type == null || units <= 0 || contents.getAmount(type) < units) return false;
        contents.remove(type, units); changed(); return true;
    }
    @Override @Deprecated public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect type, int units) { return type != null && units >= 0 && contents.getAmount(type) >= units; }
    @Override @Deprecated public boolean doesContainerContain(AspectList list) { return false; }
    @Override public int containerContains(Aspect type) { return contents.getAmount(type); }
    @Override public boolean doesContainerAccept(Aspect type) { return type != null; }
    @Override public boolean isConnectable(Direction face) { return sideOpen(face); }
    @Override public void setSuction(Aspect type, int units) {}
    @Override public Aspect getSuctionType(Direction face) { return null; }
    @Override public int getSuctionAmount(Direction face) {
        if (face == null) return bellowsCount() <= 0 ? 1 : bellowsCount() * 32;
        return choke(face) == 2 ? 0 : bellows <= 0 || choke(face) == 1 ? 1 : bellows * 32;
    }
    @Override public Aspect getEssentiaType(Direction face) {
        Aspect[] aspects = contents.getAspects();
        return aspects.length == 0 ? null : aspects[level == null ? 0 : level.random.nextInt(aspects.length)];
    }
    @Override public int getEssentiaAmount(Direction face) { return contents.visSize(); }
    @Override public int addEssentia(Aspect type, int units, Direction face) {
        return canInputFrom(face) && units > 0 ? units - addToContainer(type, units) : 0;
    }
    @Override public int takeEssentia(Aspect type, int units, Direction face) {
        if (!mutable() || type == null || units <= 0 || !canOutputTo(face)) return 0;
        IEssentiaTransport requester = neighbor(face);
        int requestedSuction = requester == null ? 0 : requester.getSuctionAmount(face.getOpposite());
        for (Direction other : Direction.values()) {
            if (other == face || !canOutputTo(other)) continue;
            IEssentiaTransport competitor = neighbor(other);
            if (competitor == null) continue;
            int strength = competitor.getSuctionAmount(other.getOpposite());
            Aspect wanted = competitor.getSuctionType(other.getOpposite());
            if ((wanted == type || wanted == null) && requestedSuction < strength && getSuctionAmount(other) < strength) return 0;
        }
        int accepted = Math.min(units, contents.getAmount(type));
        return takeFromContainer(type, accepted) ? accepted : 0;
    }
    @Override protected void tick() {
        ++bufferTicks;
        if (bellows < 0 || bufferTicks % 20 == 0) updateBellows();
        if (!level.isClientSide && bufferTicks % 5 == 0 && contents.visSize() < CAPACITY) fillBuffer();
        if (!level.isClientSide && bufferTicks % 20 == 0) refreshConnections();
    }
    private void fillBuffer() {
        for (Direction face : Direction.values()) {
            if (!canInputFrom(face)) continue;
            IEssentiaTransport source = neighbor(face);
            if (source == null || source.getEssentiaAmount(face.getOpposite()) <= 0
                    || source.getSuctionAmount(face.getOpposite()) >= getSuctionAmount(face)
                    || getSuctionAmount(face) < source.getMinimumSuction()) continue;
            Aspect type = source.getEssentiaType(face.getOpposite());
            if (type != null && source.takeEssentia(type, 1, face.getOpposite()) == 1) addToContainer(type, 1);
            return;
        }
    }
    /** BETA26 counts only enabled bellows pointing toward the buffer, not arbitrary neighbors. */
    private void updateBellows() {
        int found = 0;
        if (level != null) for (Direction face : Direction.values()) {
            BlockPos pos = worldPosition.relative(face);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (key == null || !key.toString().equals("thaumcraft:bellows")) continue;
            var facingProperty = state.getBlock().getStateDefinition().getProperty("facing");
            var enabledProperty = state.getBlock().getStateDefinition().getProperty("enabled");
            if (facingProperty != null && enabledProperty != null
                    && String.valueOf(state.getValue(facingProperty)).equals(face.getOpposite().getName())
                    && String.valueOf(state.getValue(enabledProperty)).equals("true") && !level.hasNeighborSignal(pos)) ++found;
        }
        if (bellows != found) { bellows = found; if (!level.isClientSide) changed(); }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); contents.writeToNBT(tag); tag.putByteArray("choke", chokedSides);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        AspectList loaded = new AspectList(); loaded.readFromNBT(tag); contents = new AspectList();
        int free = CAPACITY;
        for (Aspect type : loaded.getAspects()) {
            int units = Mth.clamp(loaded.getAmount(type), 0, free);
            if (type != null && units > 0) { contents.add(type, units); free -= units; }
        }
        byte[] choke = tag.getByteArray("choke");
        for (int i = 0; i < 6; i++) chokedSides[i] = choke.length == 6 ? (byte) Mth.clamp(choke[i], 0, 2) : 0;
        bellows = -1;
    }
}
