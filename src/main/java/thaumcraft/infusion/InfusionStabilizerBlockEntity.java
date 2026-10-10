package thaumcraft.infusion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.world.aura.AuraManager;
/** Release's self-charging fifteen-unit stabilizer; it is not a Forge Energy machine. */
public final class InfusionStabilizerBlockEntity extends BlockEntity {
    private int energy, ticks, delay;
    public InfusionStabilizerBlockEntity(BlockPos pos, BlockState state) { super(InfusionModule.STABILIZER.get(), pos, state); }
    public int energy() { return energy; }
    public static void tick(Level level, BlockPos pos, BlockState state, InfusionStabilizerBlockEntity tile) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        if (++tile.ticks % 20 == 0 && tile.energy < 15) {
            tile.energy++; AuraManager.addFlux(server, pos, .25F); tile.changed();
        }
        if (tile.energy > 0 && tile.delay <= 0 && tile.ticks % 5 == 0) {
            for (var rift : server.getEntitiesOfClass(thaumcraft.world.rift.FluxRiftEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(8))) {
                if (!rift.isAlive() || rift.getStability()==thaumcraft.world.rift.FluxRiftEntity.Stability.VERY_STABLE || !tile.mitigate(1)) continue;
                rift.addStability(); tile.delay += 5;
                if (tile.energy <= 0) break;
            }
        }
        if (tile.delay > 0) tile.delay--;
    }
    public boolean mitigate(int amount) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || amount <= 0 || energy < amount) return false;
        energy -= amount; changed(); return true;
    }
    private void changed() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putInt("energy", energy); tag.putInt("ticks", ticks); }
    @Override public void load(CompoundTag tag) { super.load(tag); energy = Mth.clamp(tag.getInt("energy"), 0, 15); ticks = Math.max(0, tag.getInt("ticks")); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
