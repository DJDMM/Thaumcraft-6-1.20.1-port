package thaumcraft.golemancy.levitator;

import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import thaumcraft.world.aura.AuraManager;

/** Audited BETA26 counter scan, integer energy buffer and per-entity debit. */
public final class LevitatorBlockEntity extends BlockEntity {
    private static final int[] RANGES={4,8,16,32};
    private int range=1,rangeActual,counter,vis;
    private Direction lastFacing;
    public LevitatorBlockEntity(BlockPos pos,BlockState state) {super(LevitatorModule.LEVITATOR.get(),pos,state);}
    public int rangeIndex() {return range;}
    public int configuredRange() {return RANGES[range];}
    public int actualRange() {return rangeActual;}
    public int energy() {return vis;}
    public int scanCounter() {return counter;}
    public boolean active() {return rangeActual>0&&vis>0&&getBlockState().getValue(LevitatorBlock.ENABLED);}
    public int getCost() {return configuredRange()*2;}
    private void changed() {setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    public void increaseRange(Player player) {
        if(level==null||level.isClientSide||isRemoved())return;
        rangeActual=0;range=(range+1)%RANGES.length;changed();
        player.sendSystemMessage(Component.translatable("tc.levitator",configuredRange(),getCost()));
    }
    public AABB field(Direction facing) {
        return new AABB(worldPosition.getX()-(facing.getStepX()<0?rangeActual:0),worldPosition.getY()-(facing.getStepY()<0?rangeActual:0),worldPosition.getZ()-(facing.getStepZ()<0?rangeActual:0),
                worldPosition.getX()+1+(facing.getStepX()>0?rangeActual:0),worldPosition.getY()+1+(facing.getStepY()>0?rangeActual:0),worldPosition.getZ()+1+(facing.getStepZ()>0?rangeActual:0));
    }
    public static void tick(Level level,BlockPos pos,BlockState state,LevitatorBlockEntity tile) {
        if(tile.isRemoved()||!state.is(tile.getBlockState().getBlock())||!level.hasChunkAt(pos))return;
        Direction facing=state.getValue(LevitatorBlock.FACING);
        // Rotation/unload cannot reuse a beam that was verified along another loaded axis.
        if(tile.lastFacing!=facing) {tile.lastFacing=facing;tile.rangeActual=0;tile.counter=0;}
        if(tile.rangeActual>tile.configuredRange())tile.rangeActual=0;
        int distance=1+Math.floorMod(tile.counter,tile.configuredRange());
        BlockPos sample=pos.relative(facing,distance);
        if(!level.hasChunkAt(sample)||level.isOutsideBuildHeight(sample)) {tile.rangeActual=Math.min(tile.rangeActual,distance-1);tile.counter=0;}
        else {
            if(level.getBlockState(sample).isSolidRender(level,sample)) {if(distance<tile.rangeActual)tile.rangeActual=distance;tile.counter=-1;}
            else if(distance>tile.rangeActual)tile.rangeActual=distance;
            ++tile.counter;
        }
        if(level instanceof ServerLevel server&&tile.vis<10) {
            // javap: fadd occurs before f2i, including the original negative final-lift debt.
            tile.vis=(int)(tile.vis+AuraManager.drainVis(server,pos,1,false)*1200F);tile.changed();
        }
        if(tile.rangeActual<=0||tile.vis<=0||!state.getValue(LevitatorBlock.ENABLED))return;
        for(int distanceCheck=1;distanceCheck<=tile.rangeActual;distanceCheck++)if(!level.hasChunkAt(pos.relative(facing,distanceCheck))) {tile.rangeActual=distanceCheck-1;return;}
        boolean lifted=false;
        for(Entity entity:level.getEntities((Entity)null,tile.field(facing))) {
            if(entity.isRemoved()||!(entity instanceof ItemEntity)&&!entity.isPushable()&&!(entity instanceof Horse))continue;
            lifted=true;tile.drawAt(entity);tile.draw(facing,.6F);
            Vec3 motion=entity.getDeltaMovement();
            if(entity.isShiftKeyDown()&&facing==Direction.UP) {if(motion.y<0)motion=new Vec3(motion.x,motion.y*.8999999761581421,motion.z);}
            else {
                motion=motion.add(.1F*facing.getStepX(),.1F*facing.getStepY(),.1F*facing.getStepZ());
                if(facing.getAxis()!=Direction.Axis.Y&&!entity.onGround())motion=new Vec3(motion.x,(motion.y<0?motion.y*.8999999761581421:motion.y)+.07999999821186066,motion.z);
                motion=new Vec3(clamp(motion.x),clamp(motion.y),clamp(motion.z));
            }
            entity.setDeltaMovement(motion);entity.fallDistance=0;if(!level.isClientSide)entity.hurtMarked=true;
            tile.vis-=tile.getCost();if(tile.vis<=0)break;
        }
        tile.draw(facing,.1F);
        if(lifted&&!level.isClientSide&&tile.counter%20==0)tile.setChanged();
    }
    private static double clamp(double value) {return Math.max(-.3499999940395355,Math.min(.3499999940395355,value));}
    private void draw(Direction facing,float chance) {
        if(level.isClientSide&&level.random.nextFloat()<chance)level.addParticle(ParticleTypes.END_ROD,worldPosition.getX()+.25+level.random.nextFloat()*.5,worldPosition.getY()+.25+level.random.nextFloat()*.5,worldPosition.getZ()+.25+level.random.nextFloat()*.5,
                facing.getStepX()/50.0,facing.getStepY()/50.0,facing.getStepZ()/50.0);
    }
    private void drawAt(Entity entity) {
        if(level.isClientSide&&level.random.nextFloat()<.1F)level.addParticle(ParticleTypes.END_ROD,entity.getX()+(level.random.nextFloat()-level.random.nextFloat())*entity.getBbWidth(),entity.getY()+level.random.nextFloat()*entity.getBbHeight(),entity.getZ()+(level.random.nextFloat()-level.random.nextFloat())*entity.getBbWidth(),
                (level.random.nextFloat()-level.random.nextFloat())*.01,(level.random.nextFloat()-level.random.nextFloat())*.01,(level.random.nextFloat()-level.random.nextFloat())*.01);
    }
    @Override protected void saveAdditional(CompoundTag tag) {super.saveAdditional(tag);tag.putByte("range",(byte)range);tag.putInt("vis",vis);}
    @Override public void load(CompoundTag tag) {
        super.load(tag);range=tag.contains("range")?Math.max(0,Math.min(3,tag.getByte("range"))):1;vis=Math.max(-63,Math.min(1209,tag.getInt("vis")));
        rangeActual=0;counter=0;lastFacing=null;
    }
    @Override public CompoundTag getUpdateTag() {return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {return ClientboundBlockEntityDataPacket.create(this);}
    private void readSync(CompoundTag tag) {
        int nextRange=tag.contains("range")?Math.max(0,Math.min(3,tag.getByte("range"))):1;
        if(nextRange!=range)rangeActual=0;range=nextRange;vis=Math.max(-63,Math.min(1209,tag.getInt("vis")));
    }
    @Override public void handleUpdateTag(CompoundTag tag) {readSync(tag);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet) {if(packet.getTag()!=null)readSync(packet.getTag());}
}
