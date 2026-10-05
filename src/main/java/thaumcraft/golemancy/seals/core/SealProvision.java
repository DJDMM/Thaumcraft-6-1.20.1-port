package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.UUID;

/** Original 10-second request / 120-second linked-task deadlines expressed in server ticks. */
public final class SealProvision {
    private final SealPos seal;
    private final BlockPos pos;
    private final Direction side;
    private final UUID entity;
    private final ItemStack stack;
    private final int ui;
    private SealTask linkedTask;
    private boolean invalid;
    private long timeoutTick;
    public SealProvision(SealPos seal,BlockPos pos,Direction side,UUID entity,ItemStack stack,int ui,long now) {
        this.seal=seal;this.pos=pos==null?null:pos.immutable();this.side=side;this.entity=entity;this.stack=stack.copy();this.ui=ui;timeoutTick=now+200;
    }
    public SealPos seal() {return seal;}
    public BlockPos position() {return seal!=null?seal.pos():pos;}
    public Direction side() {return seal!=null?seal.face():side;}
    public UUID entityUUID() {return entity;}
    public ItemStack stack() {return stack.copy();}
    public int ui() {return ui;}
    public SealTask linkedTask() {return linkedTask;}
    public void linkedTask(SealTask task,long now) {linkedTask=task;timeoutTick=now+2400;if(task!=null)task.provision(this);}
    public boolean invalid() {return invalid;}
    public void invalid(boolean value) {invalid=value;}
    public long timeoutTick() {return timeoutTick;}
    @Override public boolean equals(Object other) {return other instanceof SealProvision p&&ui==p.ui&&Objects.equals(seal,p.seal)&&Objects.equals(pos,p.pos)&&side==p.side&&Objects.equals(entity,p.entity)&&ItemStack.matches(stack,p.stack);}
    @Override public int hashCode() {return Objects.hash(seal,pos,side,entity,ui,stack.getItem(),stack.getCount(),stack.getTag());}
}
