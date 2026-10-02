package thaumcraft.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.*;
import thaumcraft.world.aura.AuraManager;

/** BETA26 normal/void jar storage, filters, source access and top-side suction. */
public final class EssentiaJarBlockEntity extends BlockEntity implements IAspectSource, IEssentiaTransport {
    public static final int CAPACITY = 250;
    private Aspect aspect, filter;
    private int amount, ticks;
    private Direction facing = Direction.NORTH;
    private boolean blocked;

    public EssentiaJarBlockEntity(BlockPos pos, BlockState state) { super(EssentiaModule.JAR.get(), pos, state); }
    public Aspect aspect() { return aspect; }
    public Aspect filter() { return filter; }
    public int amount() { return amount; }
    public Direction facing() { return facing; }
    public boolean blocked() { return blocked; }
    public boolean isVoid() { return getBlockState().getBlock() instanceof EssentiaJarBlock jar && jar.isVoid(); }
    private boolean mutable() { return level == null || !level.isClientSide; }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public static Direction labelFacing(float yaw) {
        return switch (Mth.floor(yaw * 4F / 360F + .5F) & 3) {
            case 0 -> Direction.NORTH; case 1 -> Direction.EAST; case 2 -> Direction.SOUTH; default -> Direction.WEST;
        };
    }
    public boolean canAccept(Aspect type, int units) {
        return type != null && units > 0 && units <= CAPACITY - amount && doesContainerAccept(type)
                && (amount == 0 || aspect == type);
    }
    /** Manual phial transfer is exact even for void jars. */
    public boolean addExact(Aspect type, int units) {
        if (!mutable() || !canAccept(type, units)) return false;
        return addToContainer(type, units) == 0;
    }
    public boolean take(Aspect type, int units) { return takeFromContainer(type, units); }
    public boolean installBrace() {
        if (!mutable() || blocked) return false;
        blocked = true; changed(); return true;
    }
    public boolean applyLabel(Player player, Direction clickedFace, @Nullable Aspect labelAspect) {
        if (!mutable() || filter != null || (amount == 0 && labelAspect == null)) return false;
        if (amount == 0) aspect = labelAspect;
        filter = aspect;
        facing = labelFacing(player.getYRot());
        changed(); return true;
    }
    public boolean removeLabel() {
        if (!mutable() || filter == null) return false;
        filter = null; changed(); return true;
    }
    public boolean purge() {
        if (!mutable()) return false;
        if (level instanceof ServerLevel server && amount > 0) AuraManager.addFlux(server, worldPosition, amount);
        if (filter == null) aspect = null;
        amount = 0; changed(); return true;
    }
    @Override public boolean isBlocked() { return blocked; }
    @Override public AspectList getAspects() {
        return amount > 0 && aspect != null ? new AspectList().add(aspect, amount) : new AspectList();
    }
    @Override public void setAspects(AspectList list) {
        if (!mutable() || list == null || list.size() == 0) return;
        Aspect type = list.getAspectsSortedByAmount()[0];
        if (type == null || list.getAmount(type) <= 0) return;
        aspect = type; amount = Math.min(CAPACITY, list.getAmount(type)); changed();
    }
    @Override public boolean doesContainerAccept(Aspect type) { return type != null && (filter == null || filter == type); }
    /** Low-level TC6 API deliberately checks stored type, not label; callers must check acceptance. */
    @Override public int addToContainer(Aspect type, int units) {
        if (!mutable() || type == null || units <= 0 || (amount > 0 && aspect != type)) return units;
        aspect = type;
        int added = Math.min(units, CAPACITY - amount);
        boolean overflow = isVoid() && units > added;
        amount += added;
        if (overflow && level instanceof ServerLevel server && level.random.nextInt(250) == 0)
            AuraManager.addFlux(server, worldPosition, 1);
        if (added > 0) changed();
        return isVoid() ? 0 : units - added;
    }
    @Override public boolean takeFromContainer(Aspect type, int units) {
        if (!mutable() || type == null || units <= 0 || type != aspect || amount < units) return false;
        amount -= units;
        if (amount == 0) aspect = null;
        changed(); return true;
    }
    @Override @Deprecated public boolean takeFromContainer(AspectList list) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect type, int units) { return type != null && type == aspect && units >= 0 && amount >= units; }
    @Override @Deprecated public boolean doesContainerContain(AspectList list) {
        // BETA26 reports true for any matching contained entry, despite the interface's 'all' wording.
        for (Aspect type : list.getAspects()) if (type == aspect && amount > 0) return true;
        return false;
    }
    @Override public int containerContains(Aspect type) { return type != null && type == aspect ? amount : 0; }
    @Override public boolean isConnectable(Direction face) { return face == Direction.UP; }
    @Override public boolean canInputFrom(Direction face) { return face == Direction.UP; }
    @Override public boolean canOutputTo(Direction face) { return face == Direction.UP; }
    @Override public void setSuction(Aspect type, int units) {}
    @Override public Aspect getSuctionType(Direction face) { return filter != null ? filter : aspect; }
    @Override public int getSuctionAmount(Direction face) {
        if (isVoid()) return filter != null && amount < CAPACITY ? 48 : 32;
        return amount >= CAPACITY ? 0 : filter != null ? 64 : 32;
    }
    @Override public int getMinimumSuction() { return filter != null ? (isVoid() ? 48 : 64) : 32; }
    @Override public int takeEssentia(Aspect type, int units, Direction face) { return canOutputTo(face) && takeFromContainer(type, units) ? units : 0; }
    @Override public int addEssentia(Aspect type, int units, Direction face) { return canInputFrom(face) && units > 0 ? units - addToContainer(type, units) : 0; }
    @Override public Aspect getEssentiaType(Direction face) { return aspect; }
    @Override public int getEssentiaAmount(Direction face) { return amount; }
    public static void tick(Level level, BlockPos pos, BlockState state, EssentiaJarBlockEntity jar) {
        if (level.isClientSide || ++jar.ticks % 5 != 0 || (!jar.isVoid() && jar.amount >= CAPACITY) || !level.hasChunkAt(pos.above())) return;
        if (!(level.getBlockEntity(pos.above()) instanceof IEssentiaTransport source) || !source.isConnectable(Direction.DOWN) || !source.canOutputTo(Direction.DOWN)) return;
        Aspect requested = jar.filter != null ? jar.filter : jar.aspect;
        int suction = jar.getSuctionAmount(Direction.UP);
        if (requested == null && source.getEssentiaAmount(Direction.DOWN) > 0 && source.getSuctionAmount(Direction.DOWN) < suction
                && suction >= source.getMinimumSuction()) requested = source.getEssentiaType(Direction.DOWN);
        if (requested != null && source.getSuctionAmount(Direction.DOWN) < suction && source.takeEssentia(requested, 1, Direction.DOWN) == 1)
            jar.addToContainer(requested, 1);
    }
    public ItemStack asItemStack() {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        if (amount > 0 && aspect != null) getAspects().writeToNBT(stack.getOrCreateTag());
        if (filter != null) stack.getOrCreateTag().putString("AspectFilter", filter.getTag());
        return stack;
    }
    public void readItem(ItemStack stack) {
        if (!mutable()) return;
        aspect = null; amount = 0; filter = null; blocked = false;
        if (stack.hasTag()) {
            AspectList list = new AspectList(); list.readFromNBT(stack.getTag());
            if (list.size() > 0) {
                Aspect type = list.getAspectsSortedByAmount()[0];
                int units = list.getAmount(type);
                if (type != null && units > 0) { aspect = type; amount = Math.min(CAPACITY, units); }
            }
            filter = Aspect.getAspect(stack.getTag().getString("AspectFilter"));
        }
        changed();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (aspect != null) tag.putString("Aspect", aspect.getTag());
        if (filter != null) tag.putString("AspectFilter", filter.getTag());
        tag.putShort("Amount", (short)amount); tag.putByte("facing", (byte)facing.get3DDataValue()); tag.putBoolean("blocked", blocked);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        aspect = Aspect.getAspect(tag.getString("Aspect")); filter = Aspect.getAspect(tag.getString("AspectFilter"));
        amount = aspect == null ? 0 : Mth.clamp(tag.getInt("Amount"), 0, CAPACITY);
        Direction read = Direction.from3DDataValue(tag.getByte("facing"));
        facing = read.getAxis().isHorizontal() ? read : Direction.NORTH;
        blocked = tag.getBoolean("blocked");
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
