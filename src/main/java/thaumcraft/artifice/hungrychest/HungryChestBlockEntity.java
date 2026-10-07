package thaumcraft.artifice.hungrychest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;

/** Native chest storage/menu/capability, with adjacency explicitly disabled. */
public final class HungryChestBlockEntity extends ChestBlockEntity {
    private boolean inserting;
    private final ContainerOpenersCounter users=new ContainerOpenersCounter() {
        @Override protected void onOpen(Level level,BlockPos pos,BlockState state) {sound(level,pos,SoundEvents.CHEST_OPEN);}
        @Override protected void onClose(Level level,BlockPos pos,BlockState state) {sound(level,pos,SoundEvents.CHEST_CLOSE);}
        @Override protected void openerCountChanged(Level level,BlockPos pos,BlockState state,int oldCount,int newCount) {signalOpenCount(level,pos,state,oldCount,newCount);}
        @Override protected boolean isOwnContainer(Player player) {return player.containerMenu instanceof ChestMenu menu&&menu.getContainer()==HungryChestBlockEntity.this;}
    };
    public HungryChestBlockEntity(BlockPos pos,BlockState state) {super(HungryChestModule.HUNGRY_CHEST.get(),pos,state);}
    @Override protected Component getDefaultName() {return Component.translatable("block.thaumcraft.hungry_chest");}
    private static void sound(Level level,BlockPos pos,SoundEvent event) {level.playSound(null,pos,event,SoundSource.BLOCKS,.5F,level.random.nextFloat()*.1F+.9F);}
    @Override public void startOpen(Player player) {if(!isRemoved()&&!player.isSpectator()&&level!=null&&!level.isClientSide)users.incrementOpeners(player,level,worldPosition,getBlockState());}
    @Override public void stopOpen(Player player) {if(!isRemoved()&&!player.isSpectator()&&level!=null&&!level.isClientSide)users.decrementOpeners(player,level,worldPosition,getBlockState());}
    @Override public void recheckOpen() {if(!isRemoved()&&level!=null&&!level.isClientSide)users.recheckOpeners(level,worldPosition,getBlockState());}
    @Override protected void signalOpenCount(Level level,BlockPos pos,BlockState state,int oldCount,int newCount) {
        super.signalOpenCount(level,pos,state,oldCount,newCount);level.updateNeighborsAt(pos,state.getBlock());level.updateNeighborsAt(pos.below(),state.getBlock());
    }
    public int openerCount() {return users.getOpenerCount();}
    public void consume(ItemEntity entity) {
        if(inserting||isRemoved()||level==null||level.isClientSide||entity.isRemoved()||entity.getItem().isEmpty())return;
        inserting=true;
        try {
            ItemStack original=entity.getItem().copy();
            var handler=getCapability(ForgeCapabilities.ITEM_HANDLER,net.minecraft.core.Direction.UP).resolve().orElse(null);
            if(handler==null)return;
            ItemStack leftover=ItemHandlerHelper.insertItem(handler,original.copy(),false);
            if(leftover.isEmpty()||leftover.getCount()!=original.getCount())entity.playSound(SoundEvents.GENERIC_EAT,.25F,(level.random.nextFloat()-level.random.nextFloat())*.2F+1);
            if(leftover.isEmpty())entity.discard();else entity.setItem(leftover);
            setChanged();level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        } finally {inserting=false;}
    }
}
