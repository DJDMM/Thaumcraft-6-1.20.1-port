package thaumcraft.essentia.production;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumcraft.world.aura.AuraManager;

/** Alembics accept one aspect from smelter columns but never pull or accept pipe input. */
public final class AlembicBlockEntity extends BlockEntity implements IAspectContainer, IEssentiaTransport {
    public static final int CAPACITY = 128;
    private Aspect aspect, filter;
    private int amount;
    private Direction labelFacing = Direction.DOWN;
    public AlembicBlockEntity(BlockPos pos, BlockState state) { super(EssentiaProductionModule.ALEMBIC.get(), pos, state); }
    public Aspect aspect() { return aspect; }
    public Aspect filter() { return filter; }
    public int amount() { return amount; }
    public Direction labelFacing() { return labelFacing; }
    private boolean mutable() { return level == null || !level.isClientSide; }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public boolean canAccept(Aspect type, int units) { return type != null && units > 0 && units <= CAPACITY - amount && (amount == 0 || type == aspect) && (filter == null || filter == type); }
    public boolean addExact(Aspect type, int units) { return mutable() && canAccept(type, units) && addToContainer(type, units) == 0; }
    public boolean take(Aspect type, int units) { return takeFromContainer(type, units); }
    public boolean applyLabel(Player player, Direction face, Aspect template) {
        if (!mutable() || !face.getAxis().isHorizontal() || filter != null || amount == 0 && template == null) return false;
        filter = amount > 0 ? aspect : template;
        labelFacing = face; changed(); return true;
    }
    public boolean removeLabel() { if (!mutable() || filter == null) return false; filter = null; labelFacing = Direction.DOWN; changed(); return true; }
    public boolean purge() {
        if (!mutable()) return false;
        if (level instanceof ServerLevel server) AuraManager.addFlux(server, worldPosition, amount);
        aspect = null; amount = 0; changed(); return true;
    }
    @Override public AspectList getAspects() { return aspect == null || amount <= 0 ? new AspectList() : new AspectList().add(aspect, amount); }
    @Override public void setAspects(AspectList list) {} // Original alembic setter is deliberately inert.
    @Override public int addToContainer(Aspect type, int units) {
        if (!mutable() || type == null || units <= 0 || filter != null && filter != type || amount > 0 && aspect != type) return units;
        int accepted = Math.min(units, CAPACITY - amount);
        if (accepted > 0) { aspect = type; amount += accepted; changed(); }
        return units - accepted;
    }
    @Override public boolean takeFromContainer(Aspect type, int units) {
        if (!mutable() || type == null || units <= 0 || aspect != type || amount < units) return false;
        amount -= units;
        if (amount == 0) aspect = null;
        changed(); return true;
    }
    @Override public boolean doesContainerContainAmount(Aspect type, int units) { return type != null && type == aspect && units >= 0 && amount >= units; }
    @Override public boolean doesContainerContain(AspectList list) { return amount > 0 && aspect != null && list.getAmount(aspect) > 0; }
    @Override public int containerContains(Aspect type) { return type != null && type == aspect ? amount : 0; }
    @Override public boolean doesContainerAccept(Aspect type) { return true; } // Original advisory method ignores filter; addToContainer enforces it.
    @Override public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean isConnectable(Direction face) { return face != null && face != Direction.DOWN && face != labelFacing; }
    @Override public boolean canInputFrom(Direction face) { return false; }
    @Override public boolean canOutputTo(Direction face) { return isConnectable(face); }
    @Override public void setSuction(Aspect type, int units) {}
    @Override public Aspect getSuctionType(Direction face) { return null; }
    @Override public int getSuctionAmount(Direction face) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getEssentiaType(Direction face) { return aspect; }
    @Override public int getEssentiaAmount(Direction face) { return amount; }
    @Override public int takeEssentia(Aspect type, int units, Direction face) { return canOutputTo(face) && takeFromContainer(type, units) ? units : 0; }
    @Override public int addEssentia(Aspect type, int units, Direction face) { return 0; }
    public static boolean processColumn(Level level, BlockPos base, Aspect type) {
        if (level == null || level.isClientSide || type == null) return false;
        // BETA26's bytecode scans the contiguous column without a five-block limit,
        // despite its book text. The world's build height supplies a finite bound.
        for (int y = base.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic)) break;
            if (alembic.amount > 0 && alembic.aspect == type && alembic.addToContainer(type, 1) == 0) return true;
        }
        for (int y = base.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic)) return false;
            if ((alembic.filter == null || alembic.filter == type) && alembic.addToContainer(type, 1) == 0) return true;
        }
        return false;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (aspect != null) tag.putString("aspect", aspect.getTag());
        if (filter != null) tag.putString("AspectFilter", filter.getTag());
        tag.putShort("amount", (short)amount); tag.putByte("facing", (byte)labelFacing.get3DDataValue());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); aspect = Aspect.getAspect(tag.getString("aspect")); filter = Aspect.getAspect(tag.getString("AspectFilter"));
        amount = aspect == null ? 0 : Math.max(0, Math.min(CAPACITY, tag.getInt("amount")));
        if (amount == 0) aspect = null;
        Direction face = Direction.from3DDataValue(tag.getByte("facing"));
        labelFacing = filter != null && face.getAxis().isHorizontal() ? face : Direction.DOWN;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
