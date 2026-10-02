package thaumcraft.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aspects.*;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.research.ResearchEvents;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class CrucibleBlockEntity extends BlockEntity {
    private int water, heat;
    private int idle = -100;
    private AspectList aspects = new AspectList();
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        public int getTanks() { return 1; }
        public FluidStack getFluidInTank(int tank) { return tank == 0 && water > 0 ? new FluidStack(Fluids.WATER, water) : FluidStack.EMPTY; }
        public int getTankCapacity(int tank) { return tank == 0 ? 1000 : 0; }
        public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && stack.getFluid() == Fluids.WATER; }
        public int fill(FluidStack stack, FluidAction action) {
            if (!isFluidValid(0, stack)) return 0;
            int accepted = Math.min(1000 - water, stack.getAmount());
            if (action.execute() && accepted > 0) { water += accepted; changed(); }
            return accepted;
        }
        public FluidStack drain(FluidStack stack, FluidAction action) { return stack.getFluid() == Fluids.WATER ? drain(stack.getAmount(), action) : FluidStack.EMPTY; }
        public FluidStack drain(int maximum, FluidAction action) {
            int amount = Math.min(water, Math.max(0, maximum));
            if (amount == 0) return FluidStack.EMPTY;
            if (action.execute()) { water -= amount; changed(); }
            return new FluidStack(Fluids.WATER, amount);
        }
    };
    private LazyOptional<IFluidHandler> fluid = LazyOptional.of(() -> fluidHandler);
    public CrucibleBlockEntity(BlockPos pos, BlockState state) { super(AlchemyModule.CRUCIBLE_TILE.get(), pos, state); }
    public int water() { return water; }
    public int heat() { return heat; }
    public AspectList aspects() { return aspects.copy(); }
    public boolean fillWater() {
        if (water == 1000) return false;
        water = 1000; changed(); return true;
    }
    public void empty() {
        discardContents();
        changed();
    }
    public void discardContents() {
        if (level instanceof ServerLevel server) AuraManager.addFlux(server, worldPosition, aspects.visSize() * 0.25F + aspects.getAmount(Aspect.FLUX) * 0.75F);
        water = 0; aspects = new AspectList(); setChanged();
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            var state = getBlockState().setValue(CrucibleBlock.FILLED, water > 0).setValue(CrucibleBlock.BOILING, water > 0 && heat > 150);
            if (state != getBlockState()) level.setBlock(worldPosition, state, 3);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    /** Atomic server operation: catalyst and aspects are consumed only after all requirements match. */
    public boolean consume(ItemStack input, ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || input.isEmpty() || water <= 0 || heat <= 150) return false;
        // BETA26 permits its last craft with any water and FluidTank.drain removes up to 50 mB.
        var recipe = CrucibleRecipes.find(input, aspects, player);
        if (recipe != null) {
            ItemEntity result = new ItemEntity(server, worldPosition.getX() + 0.5, worldPosition.getY() + 1.15, worldPosition.getZ() + 0.5, recipe.output().copy());
            result.getPersistentData().putBoolean("thaumcraft_crucible_output", true);
            result.setNoGravity(true);
            result.setDeltaMovement(0, 0.015, 0); result.setDefaultPickUpDelay();
            if (!server.addFreshEntity(result)) return false;
            aspects.remove(recipe.cost()); water = Math.max(0, water - 50); input.shrink(1); idle = -250;
            ResearchEvents.recordCraft(player, recipe.output());
        } else {
            AspectList dissolved = AspectRegistry.getAspects(input);
            if (dissolved == null || dissolved.visSize() <= 0) return false;
            aspects.add(dissolved); input.shrink(1); idle = -150;
        }
        changed(); return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState below = level.getBlockState(pos.below());
        boolean hot = below.is(Blocks.LAVA) || below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.MAGMA_BLOCK) || below.is(AlchemyModule.NITOR.get());
        int oldHeat = crucible.heat;
        crucible.heat = Math.max(0, Math.min(200, crucible.heat + (hot && crucible.water > 0 ? 1 : -1)));
        if ((oldHeat > 150) != (crucible.heat > 150)) crucible.changed();
        if (oldHeat != crucible.heat) crucible.setChanged();
        crucible.idle++;
        // These are two independent BETA26 spills: overflow must not postpone idle decay.
        if (crucible.aspects.visSize() > 500) crucible.spillRandom(server);
        if (crucible.idle >= 100) {
            crucible.spillRandom(server);
            crucible.idle = 0;
        }
        if (crucible.water > 0 && crucible.heat > 150 && level.getGameTime() % 5 == 0) {
            AABB mouth = new AABB(pos.getX() + 0.15, pos.getY() + 0.25, pos.getZ() + 0.15, pos.getX() + 0.85, pos.getY() + 1.1, pos.getZ() + 0.85);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mouth)) {
                if (entity.getPersistentData().getBoolean("thaumcraft_crucible_output")) continue;
                ServerPlayer player = entity.getOwner() instanceof ServerPlayer owner ? owner : null;
                if (crucible.consume(entity.getItem(), player)) {
                    if (entity.getItem().isEmpty()) entity.discard(); else entity.setItem(entity.getItem().copy());
                }
            }
        }
    }
    private void spillRandom(ServerLevel server) {
        Aspect[] choices = aspects.getAspects();
        if (choices.length == 0) return;
        Aspect aspect = choices[server.random.nextInt(choices.length)];
        aspects.remove(aspect, 1);
        AuraManager.addFlux(server, worldPosition, aspect == Aspect.FLUX ? 1F : 0.25F);
        changed();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putInt("Water", water); tag.putInt("Heat", heat); tag.putInt("Idle", idle); aspects.writeToNBT(tag);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); water = Math.max(0, Math.min(1000, tag.getInt("Water"))); heat = Math.max(0, Math.min(200, tag.getInt("Heat"))); idle = tag.getInt("Idle"); aspects.readFromNBT(tag);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.FLUID_HANDLER ? fluid.cast() : super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); fluid.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); fluid = LazyOptional.of(() -> fluidHandler); }
}
