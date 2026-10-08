package thaumcraft.essentia.transfuser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.essentia.airborne.AirborneEssentiaManager;
import thaumcraft.infusion.InfusionMatrixBlockEntity;

/** BETA26's two stateless pumps. Actual transfers always use the physical rear peer. */
public final class EssentiaTransfuserBlockEntity extends BlockEntity implements IEssentiaTransport {
    public static final int RANGE = 16;
    private int ticks;
    private boolean transferring;

    public EssentiaTransfuserBlockEntity(BlockPos pos, BlockState state) {
        super(EssentiaTransfuserModule.TRANSFUSER.get(), pos, state);
    }
    public Direction facing() { return getBlockState().getValue(EssentiaTransfuserBlock.FACING); }
    public boolean isFilling() { return getBlockState().getBlock() instanceof EssentiaTransfuserBlock block && block.isFilling(); }
    @Override public boolean isConnectable(Direction face) { return face != null && face == facing().getOpposite(); }
    @Override public boolean canInputFrom(Direction face) { return isFilling() && isConnectable(face); }
    @Override public boolean canOutputTo(Direction face) { return !isFilling() && isConnectable(face); }
    @Override public void setSuction(Aspect aspect, int amount) {}
    @Override public int getMinimumSuction() { return 0; }
    @Override public @Nullable Aspect getSuctionType(Direction face) { return null; }
    @Override public int getSuctionAmount(Direction face) { return isFilling() ? 128 : 0; }
    @Override public @Nullable Aspect getEssentiaType(Direction face) { return null; }
    @Override public int getEssentiaAmount(Direction face) { return 0; }
    @Override public int takeEssentia(Aspect aspect, int amount, Direction face) { return 0; }
    // The original public API reports acceptance without storing anything. Real pumps
    // actively transact with their rear peer; no simulated buffer is added to this API.
    @Override public int addEssentia(Aspect aspect, int amount, Direction face) { return Math.max(0, amount); }

    public static void tick(Level level, BlockPos pos, BlockState state, EssentiaTransfuserBlockEntity tile) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || tile.isRemoved()
                || tile.transferring || !(state.getBlock() instanceof EssentiaTransfuserBlock)) return;
        var ownChunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (ownChunk == null || ownChunk.getBlockEntity(pos) != tile || tile.getLevel() != server) return;
        if (++tile.ticks % 5 != 0) return;
        Direction front = tile.facing();
        BlockPos peerPos = pos.relative(front.getOpposite());
        if (server.isOutsideBuildHeight(peerPos)) return;
        var peerChunk = server.getChunkSource().getChunkNow(peerPos.getX() >> 4, peerPos.getZ() >> 4);
        if (peerChunk == null) return;
        BlockEntity peer = peerChunk.getBlockEntity(peerPos);
        if (peer == null || peer.isRemoved() || !(peer instanceof IEssentiaTransport transport)) return;
        tile.transferring = true;
        try {
            if (!transport.isConnectable(front)) return;
            if (tile.isFilling()) {
                if (!transport.canOutputTo(front) || transport.getEssentiaAmount(front) <= 0
                        || transport.getSuctionAmount(front) >= 128 || transport.getMinimumSuction() > 128) return;
                // TubeBuffer selects a random held aspect on each query, like BETA26.
                Aspect held = transport.getEssentiaType(front);
                if (held == null) return;
                AirborneEssentiaManager.transferToSources(tile, peer, held, front, front, RANGE, 5)
                        .ifPresent(transfer -> InfusionMatrixBlockEntity.trail(server, transfer.source(), transfer.target(), transfer.aspect().getColor()));
            } else {
                if (!transport.canInputFrom(front) || transport.getSuctionAmount(front) <= 0
                        || transport.getSuctionType(front) == null) return;
                AirborneEssentiaManager.transferFromSources(tile, peer, transport.getSuctionType(front), front, front, RANGE, 5)
                        .ifPresent(transfer -> InfusionMatrixBlockEntity.trail(server, transfer.source(), transfer.target(), transfer.aspect().getColor()));
            }
        } finally {
            tile.transferring = false;
        }
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        // Neither count nor essence is persistent in TileEssentiaInput/Output.
        ticks = 0;
        transferring = false;
    }
    @Override public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel) AirborneEssentiaManager.forgetConsumer(this);
    }
}
