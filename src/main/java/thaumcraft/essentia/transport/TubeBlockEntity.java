package thaumcraft.essentia.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;

/** Local asynchronous BETA26 suction propagation, not a graph-wide fluid push. */
public class TubeBlockEntity extends BlockEntity implements IEssentiaTransport {
    protected final boolean[] openSides = {true, true, true, true, true, true};
    protected Aspect essentiaType, suctionType;
    protected int essentiaAmount, suction, count, venting;
    protected Direction facing = Direction.NORTH;
    private boolean allowFlow = true, wasPowered;
    private int ventColor = 0xAAAAAA;
    private float rotation, previousRotation;

    public TubeBlockEntity(BlockPos pos, BlockState state) {
        super(EssentiaTransportModule.TUBE.get(), pos, state);
        // A previously visual-only block has orientation in its blockstate, but no tube NBT.
        if (state.hasProperty(TubeBlock.FACING)) facing = state.getValue(TubeBlock.FACING);
    }
    public static TubeBlockEntity create(BlockPos pos, BlockState state) {
        return switch (((TubeBlock) state.getBlock()).id()) {
            case "tube_buffer" -> new TubeBufferBlockEntity(pos, state);
            case "tube_filter" -> new TubeFilterBlockEntity(pos, state);
            default -> new TubeBlockEntity(pos, state);
        };
    }
    public String id() { return ((TubeBlock) getBlockState().getBlock()).id(); }
    public Direction facing() { return facing; }
    public boolean sideOpen(Direction face) { return face != null && openSides[face.get3DDataValue()]; }
    public boolean allowFlow() { return allowFlow; }
    public int ventingTicks() { return venting; }
    public int ventColor() { return ventColor; }
    public float valveRotation(float partial) { return Mth.lerp(partial, previousRotation, rotation); }
    protected boolean mutable() { return level == null || !level.isClientSide; }
    protected void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    public void setFacing(Direction direction) {
        if (!mutable() || direction == null) return;
        facing = direction; changed(); refreshConnections();
    }
    public void toggleFlow() {
        if (!mutable() || !id().equals("tube_valve")) return;
        allowFlow = !allowFlow; changed();
    }
    /** Original caster closes the matching arm of an adjoining tube as well. */
    public void toggleSide(Direction face) {
        if (!mutable() || face == null) return;
        openSides[face.get3DDataValue()] = !sideOpen(face);
        if (level != null && level.hasChunkAt(worldPosition.relative(face))
                && level.getBlockEntity(worldPosition.relative(face)) instanceof TubeBlockEntity adjacent) {
            adjacent.openSides[face.getOpposite().get3DDataValue()] = sideOpen(face);
            adjacent.changed(); adjacent.refreshConnections();
        }
        changed(); refreshConnections();
    }
    public void rotateFacing() {
        if (!mutable()) return;
        int start = facing.get3DDataValue();
        for (int i = start + 1; i < 20; i++) {
            Direction next = Direction.from3DDataValue(i % 6);
            if (id().equals("tube_valve") ? !hasTransport(next) : hasTransport(next.getOpposite()) && isConnectable(next.getOpposite())) {
                setFacing(next); break;
            }
        }
    }
    public boolean hasTransport(Direction face) {
        return level != null && level.hasChunkAt(worldPosition.relative(face))
                && level.getBlockEntity(worldPosition.relative(face)) instanceof IEssentiaTransport;
    }
    protected IEssentiaTransport neighbor(Direction direction) {
        return EssentiaTransportModule.neighbor(level, worldPosition, direction);
    }
    public void refreshConnections() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof TubeBlock block)) return;
        BlockState state = getBlockState(), updated = state;
        for (Direction face : Direction.values()) updated = updated.setValue(TubeBlock.connection(face), isConnectable(face) && neighbor(face) != null);
        if (state.hasProperty(TubeBlock.FACING)) updated = updated.setValue(TubeBlock.FACING, facing);
        if (updated != state) level.setBlock(worldPosition, updated, 3);
    }

    @Override public boolean isConnectable(Direction face) { return sideOpen(face) && (!id().equals("tube_valve") || face != facing); }
    // In BETA26 valve/oneway override graph propagation, not these low-level accessors.
    @Override public boolean canInputFrom(Direction face) { return sideOpen(face); }
    @Override public boolean canOutputTo(Direction face) { return sideOpen(face); }
    @Override public void setSuction(Aspect type, int units) {
        if (mutable() && (!id().equals("tube_valve") || allowFlow)) {
            suctionType = type; suction = Math.max(0, units);
        }
    }
    @Override public Aspect getSuctionType(Direction face) { return suctionType; }
    @Override public int getSuctionAmount(Direction face) { return suction; }
    @Override public Aspect getEssentiaType(Direction face) { return essentiaType; }
    @Override public int getEssentiaAmount(Direction face) { return essentiaAmount; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public int addEssentia(Aspect type, int units, Direction face) {
        if (!mutable() || type == null || units <= 0 || !canInputFrom(face) || essentiaAmount != 0) return 0;
        essentiaType = type; essentiaAmount = 1; changed(); return 1;
    }
    @Override public int takeEssentia(Aspect type, int units, Direction face) {
        if (!mutable() || type == null || units <= 0 || !canOutputTo(face) || essentiaAmount <= 0 || type != essentiaType) return 0;
        essentiaAmount = 0; essentiaType = null; changed(); return 1;
    }

    protected void calculateSuction() {
        Aspect filter = this instanceof TubeFilterBlockEntity filtered ? filtered.filter() : null;
        boolean restricted = id().equals("tube_restrict"), directional = id().equals("tube_oneway");
        suction = 0; suctionType = null;
        for (Direction face : Direction.values()) {
            if ((directional && facing != face.getOpposite()) || !isConnectable(face)) continue;
            IEssentiaTransport source = neighbor(face);
            if (source == null) continue;
            Aspect wanted = source.getSuctionType(face.getOpposite());
            if (filter != null && wanted != null && wanted != filter) continue;
            if (filter == null && essentiaAmount > 0 && wanted != null && essentiaType != wanted) continue;
            if (filter != null && essentiaAmount > 0 && essentiaType != null && wanted != null && essentiaType != wanted) continue;
            int strength = source.getSuctionAmount(face.getOpposite());
            // Keep the comparison before division, including restrict's direction-order quirk.
            if (strength > 0 && strength > (long) suction + 1) setSuction(wanted == null ? filter : wanted, restricted ? strength / 2 : strength - 1);
        }
    }
    protected void checkVenting() {
        if (suction <= 0) return;
        for (Direction face : Direction.values()) {
            if (!isConnectable(face)) continue;
            IEssentiaTransport source = neighbor(face);
            if (source == null || source instanceof TubeFilterBlockEntity) continue;
            int other = source.getSuctionAmount(face.getOpposite());
            if ((other == suction || other == suction - 1) && suctionType != source.getSuctionType(face.getOpposite())) {
                // Rival suction stalls for 40 ticks. It does not delete the held unit or add flux.
                venting = 40; ventColor = suctionType == null ? 0xAAAAAA : suctionType.getColor();
                level.blockEvent(worldPosition, getBlockState().getBlock(), 1, ventColor);
                break;
            }
        }
    }
    protected void equalize() {
        if (essentiaAmount > 0) return;
        boolean directional = id().equals("tube_oneway");
        for (Direction face : Direction.values()) {
            if ((directional && facing == face.getOpposite()) || !isConnectable(face)) continue;
            IEssentiaTransport source = neighbor(face);
            if (source == null || !source.canOutputTo(face.getOpposite())) continue;
            Aspect held = source.getEssentiaType(face.getOpposite());
            if (suctionType != null && held != null && suctionType != held) continue;
            if (suction <= source.getSuctionAmount(face.getOpposite()) || suction < source.getMinimumSuction()) continue;
            Aspect wanted = suctionType != null ? suctionType : held != null ? held : source.getEssentiaType(null);
            if (wanted == null) continue;
            if (source.takeEssentia(wanted, 1, face.getOpposite()) == 1 && addEssentia(wanted, 1, face) == 1) {
                if (level.random.nextInt(100) == 0) level.blockEvent(worldPosition, getBlockState().getBlock(), 0, 0);
                return;
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TubeBlockEntity tube) { tube.tick(); }
    protected void tick() {
        if (venting > 0) --venting;
        if (level.isClientSide) {
            previousRotation = rotation;
            rotation = Mth.clamp(rotation + (allowFlow ? -20F : 20F), 0F, 360F);
            if (venting > 0) {
                double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
                level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(
                        ((ventColor >> 16) & 255) / 255F, ((ventColor >> 8) & 255) / 255F, (ventColor & 255) / 255F), .6F),
                        x, y, z, (level.random.nextDouble() - .5) / 5, (level.random.nextDouble() - .5) / 5, (level.random.nextDouble() - .5) / 5);
            }
            return;
        }
        if (count == 0) count = level.random.nextInt(10);
        if (id().equals("tube_valve") && count % 5 == 0) {
            boolean powered = level.hasNeighborSignal(worldPosition);
            if (wasPowered && !powered && !allowFlow || !wasPowered && powered && allowFlow) {
                allowFlow = !allowFlow; changed();
                level.playSound(null, worldPosition, EssentiaTransportModule.SQUEEK.get(), SoundSource.BLOCKS, .7F, .9F + level.random.nextFloat() * .2F);
            }
            wasPowered = powered;
        }
        if (venting <= 0) {
            Aspect previousType = suctionType; int previous = suction;
            if (++count % 2 == 0) { calculateSuction(); checkVenting(); if (essentiaAmount == 0) essentiaType = null; }
            if (count % 5 == 0 && suction > 0) equalize();
            if (previous != suction || previousType != suctionType) changed();
        }
        if (count % 20 == 0) refreshConnections();
    }
    @Override public boolean triggerEvent(int id, int data) {
        if (id == 0) {
            if (level != null && level.isClientSide) level.playLocalSound(worldPosition.getX() + .5, worldPosition.getY() + .5,
                    worldPosition.getZ() + .5, EssentiaTransportModule.CREAK.get(), SoundSource.AMBIENT, 1F, 1.3F + level.random.nextFloat() * .2F, false);
            return true;
        }
        if (id == 1) {
            if (level != null && level.isClientSide) {
                if (venting <= 0) level.playLocalSound(worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, .1F, 1F, false);
                venting = 50; ventColor = data;
            }
            return true;
        }
        return super.triggerEvent(id, data);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (essentiaType != null) tag.putString("type", essentiaType.getTag());
        if (suctionType != null) tag.putString("stype", suctionType.getTag());
        tag.putInt("amount", essentiaAmount); tag.putInt("samount", suction);
        tag.putInt("side", facing.get3DDataValue());
        byte[] sides = new byte[6];
        for (int i = 0; i < 6; i++) sides[i] = (byte) (openSides[i] ? 1 : 0);
        tag.putByteArray("open", sides); tag.putBoolean("flow", allowFlow); tag.putBoolean("hadpower", wasPowered);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        essentiaType = Aspect.getAspect(tag.getString("type")); suctionType = Aspect.getAspect(tag.getString("stype"));
        essentiaAmount = essentiaType == null ? 0 : Mth.clamp(tag.getInt("amount"), 0, 1);
        suction = Mth.clamp(tag.getInt("samount"), 0, Integer.MAX_VALUE);
        if (tag.contains("side")) facing = Direction.from3DDataValue(Mth.clamp(tag.getInt("side"), 0, 5));
        byte[] sides = tag.getByteArray("open");
        for (int i = 0; i < 6; i++) openSides[i] = sides.length != 6 || sides[i] == 1;
        allowFlow = !tag.contains("flow") || tag.getBoolean("flow"); wasPowered = tag.getBoolean("hadpower");
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onLoad() { super.onLoad(); refreshConnections(); }
}
