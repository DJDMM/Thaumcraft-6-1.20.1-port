package thaumcraft.essentia.centrifuge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;

/** BETA26 has one input and one output, with no saved processing countdown. */
public final class CentrifugeBlockEntity extends BlockEntity implements IAspectContainer, IEssentiaTransport {
    private Aspect input, output;
    private int idleCount, process;
    private float rotation, rotationSpeed;
    public CentrifugeBlockEntity(BlockPos pos, BlockState state) { super(CentrifugeModule.CENTRIFUGE.get(),pos,state); }
    public Aspect input() { return input; }
    public Aspect output() { return output; }
    public int processTicks() { return process; }
    public float rotationDegrees() { return rotation; }
    public float rotationSpeed() { return rotationSpeed; }
    private boolean mutable() { return level == null || !level.isClientSide; }
    private boolean powered() { return level != null && level.hasNeighborSignal(worldPosition); }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity tile) {
        if (level.isClientSide) { tile.animate(); return; }
        if (tile.powered()) return;
        if (tile.output == null && tile.input == null && ++tile.idleCount % 5 == 0) tile.drawEssentia();
        if (tile.process > 0) --tile.process;
        if (tile.output == null && tile.input != null && tile.process == 0) {
            Aspect[] components = tile.input.getComponents();
            // Invalid addon aspect definitions cannot create an unbounded/invalid machine state.
            if (components == null || components.length != 2 || components[0] == null || components[1] == null) return;
            tile.output = components[level.random.nextInt(2)];
            tile.input = null;
            tile.changed();
        }
    }
    private void animate() {
        if (input != null && !powered() && rotationSpeed < 20) rotationSpeed += 2;
        if ((input == null || powered()) && rotationSpeed > 0) rotationSpeed -= .5F;
        int previous = (int)rotation;
        rotation += rotationSpeed;
        if (rotation % 180 <= 20 && previous % 180 >= 160 && rotationSpeed > 0)
            level.playLocalSound(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,
                    CentrifugeModule.PUMP.get(), SoundSource.BLOCKS,1,1,false);
    }
    private void drawEssentia() {
        BlockPos below = worldPosition.below();
        if (level == null || !level.hasChunkAt(below)
                || !(level.getBlockEntity(below) instanceof IEssentiaTransport source)
                || !source.isConnectable(Direction.UP) || !source.canOutputTo(Direction.UP)) return;
        int suction = getSuctionAmount(Direction.DOWN);
        if (source.getEssentiaAmount(Direction.UP) <= 0 || source.getSuctionAmount(Direction.UP) >= suction
                || source.getMinimumSuction() > suction) return;
        Aspect type = source.getEssentiaType(Direction.UP);
        if (validCompound(type) && source.getSuctionAmount(Direction.UP) < getSuctionAmount(Direction.DOWN)
                && source.takeEssentia(type,1,Direction.UP) == 1) acceptInput(type);
    }
    private static boolean validCompound(Aspect type) {
        return type != null && !type.isPrimal() && type.getComponents()[0] != null && type.getComponents()[1] != null;
    }
    private void acceptInput(Aspect type) { input=type; process=39; changed(); }

    @Override public AspectList getAspects() { return output == null ? new AspectList() : new AspectList().add(output,1); }
    @Override public void setAspects(AspectList aspects) {} // Original inert setter.
    @Override public boolean doesContainerAccept(Aspect type) { return true; }
    @Override public int addToContainer(Aspect type, int amount) {
        if (mutable() && type != null && amount > 0 && output == null) { output=type; changed(); return amount-1; }
        return amount;
    }
    @Override public boolean takeFromContainer(Aspect type, int amount) {
        // BETA26 clears its single output irrespective of the positive requested quantity.
        if (!mutable() || type == null || amount <= 0 || type != output) return false;
        output=null; changed(); return true;
    }
    @Override public boolean takeFromContainer(AspectList aspects) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect type, int amount) { return type != null && amount == 1 && type == output; }
    @Override public boolean doesContainerContain(AspectList aspects) {
        if (aspects == null || output == null) return false;
        for (Aspect type : aspects.getAspects()) if (type == output) return true;
        return false;
    }
    @Override public int containerContains(Aspect type) { return type != null && type == output ? 1 : 0; }
    @Override public boolean isConnectable(Direction face) { return face == Direction.UP || face == Direction.DOWN; }
    @Override public boolean canInputFrom(Direction face) { return face == Direction.DOWN; }
    @Override public boolean canOutputTo(Direction face) { return face == Direction.UP; }
    @Override public void setSuction(Aspect type, int amount) {}
    @Override public Aspect getSuctionType(Direction face) { return null; }
    @Override public int getSuctionAmount(Direction face) { return face == Direction.DOWN ? powered() ? 0 : input == null ? 128 : 64 : 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getEssentiaType(Direction face) { return output; }
    @Override public int getEssentiaAmount(Direction face) { return output == null ? 0 : 1; }
    @Override public int takeEssentia(Aspect type, int amount, Direction face) {
        return canOutputTo(face) && takeFromContainer(type,amount) ? amount : 0;
    }
    @Override public int addEssentia(Aspect type, int amount, Direction face) {
        // The original low-level method ignores face and takes exactly one, even for amount>1.
        if (!mutable() || amount <= 0 || input != null || !validCompound(type)) return 0;
        acceptInput(type); return 1;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (input != null) tag.putString("aspectIn",input.getTag());
        if (output != null) tag.putString("aspectOut",output.getTag());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        Aspect candidate = Aspect.getAspect(tag.getString("aspectIn"));
        input = validCompound(candidate) ? candidate : null;
        output = Aspect.getAspect(tag.getString("aspectOut"));
        // Do not save/recover process, idleCount, rotation or rotationSpeed: BETA26 omits them.
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
