package thaumcraft.auromancy.remaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import java.util.UUID;

/** BETA26 TileHole chain/countdown, with portable registry/state NBT instead of numeric IDs. */
public final class RiftHoleBlockEntity extends TheEndPortalBlockEntity {
    private BlockState previous = Blocks.AIR.defaultBlockState();
    private short countdown, maximum = 120;
    private byte count;
    private Direction direction;
    private UUID casterId;
    private ServerPlayer originalCaster;
    public RiftHoleBlockEntity(BlockPos pos, BlockState state) { super(RemainingEffectsModule.HOLE.get(), pos, state); }
    void configure(BlockState previous, int maximum, byte count, Direction direction, ServerPlayer caster) {
        this.previous = previous; this.maximum = (short)maximum; this.count = count; this.direction = direction;
        this.casterId = caster.getUUID(); this.originalCaster = caster; this.countdown = 0; setChanged();
    }
    public BlockState previousState() { return previous; }
    public int countdown() { return countdown; }
    public int maximum() { return maximum; }
    public int remainingSegments() { return count; }
    public Direction passageDirection() { return direction; }
    public static void tick(Level level, BlockPos pos, BlockState state, RiftHoleBlockEntity hole) {
        if (hole.isRemoved() || level.getBlockEntity(pos) != hole || !(state.getBlock() instanceof RiftHoleBlock)) return;
        if (level.isClientSide) { hole.sparkles(); return; }
        if (!(level instanceof ServerLevel server)) return;
        if (hole.countdown == 0 && hole.count > 1 && hole.direction != null) {
            // Ownership is solely modern protection metadata, not an extra payment or recipe gate.
            ServerPlayer caster = hole.originalCaster != null ? hole.originalCaster
                    : hole.casterId == null ? null : server.getServer().getPlayerList().getPlayer(hole.casterId);
            if (caster != null && caster.serverLevel() == server && caster.isAlive() && !caster.isSpectator()) {
                for (int a = 0; a < 9; a++) if (a / 3 != 1 || a % 3 != 1) {
                    int u = -1 + a / 3, v = -1 + a % 3;
                    BlockPos neighbor = switch (hole.direction.getAxis()) {
                        case Y -> pos.offset(u, 0, v);
                        case Z -> pos.offset(u, v, 0);
                        case X -> pos.offset(0, u, v);
                    };
                    RiftPassage.create(server, caster, neighbor, null, (byte)1, hole.maximum);
                }
                if (!RiftPassage.create(server, caster, pos.relative(hole.direction.getOpposite()), hole.direction,
                        (byte)(hole.count - 1), hole.maximum)) hole.count = 0;
            } else hole.count = 0;
        }
        ++hole.countdown;
        if (hole.countdown % 20 == 0) hole.setChanged();
        if (hole.countdown >= hole.maximum && server.getBlockEntity(pos) == hole)
            server.setBlock(pos, hole.previous, 3);
    }
    @Override public boolean shouldRenderFace(Direction face) {
        if (level == null) return false;
        BlockPos adjacent = worldPosition.relative(face);
        BlockState state = level.getBlockState(adjacent);
        return !(state.getBlock() instanceof RiftHoleBlock) && state.isSolidRender(level, adjacent);
    }
    private void sparkles() {
        var particle = new DustParticleOptions(new Vector3f(.25F, .25F, 1F), 1);
        for (int a = 0; a < 2; a++) for (Direction d1 : Direction.values()) {
            BlockPos adjacent = worldPosition.relative(d1);
            BlockState s1 = level.getBlockState(adjacent);
            if (s1.getBlock() instanceof RiftHoleBlock || s1.isSolidRender(level, adjacent)) continue;
            for (Direction d2 : Direction.values()) if (d1.getAxis() != d2.getAxis()) {
                BlockPos side = worldPosition.relative(d2), corner = adjacent.relative(d2);
                if (!level.getBlockState(side).isSolidRender(level, side) && !level.getBlockState(corner).isSolidRender(level, corner)) continue;
                double x = .5 * d1.getStepX(), y = .5 * d1.getStepY(), z = .5 * d1.getStepZ();
                if (x == 0) x = .5 * d2.getStepX(); if (y == 0) y = .5 * d2.getStepY(); if (z == 0) z = .5 * d2.getStepZ();
                x = x == 0 ? level.random.nextFloat() : x + .5;
                y = y == 0 ? level.random.nextFloat() : y + .5;
                z = z == 0 ? level.random.nextFloat() : z + .5;
                level.addParticle(particle, worldPosition.getX() + x, worldPosition.getY() + y, worldPosition.getZ() + z, 0, 0, 0);
            }
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("oldState", NbtUtils.writeBlockState(previous));
        tag.putShort("countdown", countdown); tag.putShort("countdownmax", maximum); tag.putByte("count", count);
        tag.putByte("direction", (byte)(direction == null ? -1 : direction.get3DDataValue()));
        if (casterId != null) tag.putUUID("caster", casterId);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        previous = tag.contains("oldState", Tag.TAG_COMPOUND) ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("oldState")) : Blocks.AIR.defaultBlockState();
        // Corrupt self-memory cannot create an immortal passage or duplicate block entities.
        if (previous.getBlock() instanceof RiftHoleBlock || previous.hasBlockEntity()) previous = Blocks.AIR.defaultBlockState();
        maximum = (short)Math.max(1, Math.min(200, tag.getShort("countdownmax")));
        countdown = (short)Math.max(0, Math.min(maximum, tag.getShort("countdown")));
        count = tag.getByte("count"); int face = tag.getByte("direction");
        direction = face >= 0 && face < 6 ? Direction.from3DDataValue(face) : null;
        casterId = tag.hasUUID("caster") ? tag.getUUID("caster") : null;
        originalCaster = null;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
