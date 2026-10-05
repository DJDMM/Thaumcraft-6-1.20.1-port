package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** BETA26 tasks are transient tickets: 300 seconds, +120 on reservation, +1 on completion. */
public final class SealTask {
    private static final AtomicInteger NEXT=new AtomicInteger(1);
    private final int id=NEXT.getAndUpdate(i->i==Integer.MAX_VALUE?1:i+1);
    private final SealPos seal;
    private final BlockPos block;
    private final UUID entity;
    private UUID golem;
    private int priority, lifespan=300, data;
    private boolean reserved,suspended,completed;
    private SealProvision provision;
    private final CompoundTag payload=new CompoundTag();
    private SealTask(SealPos seal,BlockPos block,UUID entity) {this.seal=seal;this.block=block==null?null:block.immutable();this.entity=entity;}
    public static SealTask block(SealPos seal,BlockPos pos) {return new SealTask(seal,pos,null);}
    public static SealTask entity(SealPos seal,Entity entity) {return new SealTask(seal,null,entity.getUUID());}
    public int id() {return id;}
    public SealPos sealPosition() {return seal;}
    public int type() {return entity==null?0:1;}
    public Entity entity(ServerLevel level) {return entity==null?null:level.getEntity(entity);}
    public UUID entityUUID() {return entity;}
    public BlockPos position(ServerLevel level) {Entity e=entity(level);return entity==null?block:e==null?null:e.blockPosition();}
    public BlockPos position() {return block;}
    public UUID golemUUID() {return golem;}
    public SealTask golemUUID(UUID value) {golem=value;return this;}
    public int priority() {return priority;}
    public SealTask priority(int value) {priority=Math.max(-5,Math.min(5,value));return this;}
    public int lifespan() {return lifespan;}
    public SealTask lifespan(int value) {lifespan=Math.max(0,Math.min(32767,value));return this;}
    public int data() {return data;}
    public SealTask data(int value) {data=value;return this;}
    public boolean reserved() {return reserved;}
    public SealTask reserved(boolean value) {reserved=value;lifespan=Math.min(32767,lifespan+120);return this;}
    public boolean suspended() {return suspended;}
    public SealTask suspended(boolean value) {if(value)provision=null;suspended=value;return this;}
    public boolean completed() {return completed;}
    public SealTask completed(boolean value) {completed=value;lifespan=Math.min(32767,lifespan+1);return this;}
    public SealProvision provision() {return provision;}
    public SealTask provision(SealProvision value) {provision=value;return this;}
    /** Mutable transient data is confined to the server-thread behavior callback, never network-decoded. */
    public CompoundTag payload() {return payload;}
}
