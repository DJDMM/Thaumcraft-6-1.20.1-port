package thaumcraft.auromancy.media;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.auromancy.FocusExecution;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;
import java.util.*;
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusMediaGameTests {
    private static ServerPlayer player(GameTestHelper h){var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TC6Media"));p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,1,1))));p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);return p;}
    private static FocusPlan plan(String medium,Map<String,Integer> settings){return FocusCompiler.compile(ElementalFocusGraphGameTests.graph(medium,FocusNodeRegistry.FLUX,settings,Map.of()),CatalogModule.stack("focus_3"),k->true).plan();}
    private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h,Vec3 pos){var c=EntityType.COW.create(h.getLevel());c.setNoAi(true);c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);c.setHealth(100);c.setPos(pos);h.getLevel().addFreshEntity(c);return c;}
    private static void end(GameTestHelper h){MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true));}
    @GameTest(template="empty") public static void cloudPulsesEveryFiveTicksAtHalfPowerAndRestoresPaidOwnerIdentity(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.CLOUD,Map.of("radius",2,"duration",5));var c=cow(h,p.position().add(0,3,0));
        float aura=AuraManager.getVis(h.getLevel(),p.blockPosition());
        Set<Long> activeChunks;
        try{
            var field=thaumcraft.world.aura.AuraSavedData.class.getDeclaredField("activeChunks");field.setAccessible(true);
            @SuppressWarnings("unchecked") var chunks=(Set<Long>)field.get(thaumcraft.world.aura.AuraSavedData.get(h.getLevel()));
            activeChunks=chunks;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot isolate cloud aura fixture",failure);}
        long chunkKey=new net.minecraft.world.level.ChunkPos(p.blockPosition()).toLong();
        // Real END delivery also invokes lunar regeneration/diffusion when gameTime % 20 == 0.
        // Pause only this loaded fixture chunk for the synchronous no-repayment check;
        // leave the real event, game time and cloud callbacks unchanged, then restore membership.
        boolean wasActive=activeChunks.remove(chunkKey);
        try{
            FocusExecution.resume(p,plan,1,null,c.position(),new Vec3(0,0,1));
            var cloud=h.getLevel().getEntitiesOfClass(FocusCloudEntity.class,c.getBoundingBox().inflate(2)).stream().filter(e->e.paidPlan()!=null&&e.paidPlan().executionId().equals(plan.executionId())).findFirst().orElseThrow();
            h.assertTrue(cloud.power()==.5F,"Cloud multiplied strength twice");
            for(int i=0;i<5;i++){cloud.tickCount++;cloud.tick();}h.assertTrue(c.getHealth()==100&&FocusMediaCallbacks.pending(h.getLevel())>0,"Cloud bypassed detached END");end(h);
            h.assertTrue(c.getHealth()==98,"Cloud damage must2");for(int i=0;i<5;i++){cloud.tickCount++;cloud.tick();}end(h);h.assertTrue(c.getHealth()==96,"Original Integer/Long living cooldown quirk changed");
            var save=cloud.saveWithoutId(new CompoundTag());cloud.discard();var loaded=FocusMediaModule.CLOUD.get().create(h.getLevel());loaded.load(save);h.assertTrue(loaded.bindOwner(p)&&loaded.power()==.5F&&loaded.paidPlan().executionId().equals(plan.executionId())&&loaded.radius()==2,"Cloud save changed paid continuation");loaded.discard();
            h.assertTrue(Math.abs(aura-AuraManager.getVis(h.getLevel(),p.blockPosition()))<.001,"Cloud paid cast twice");
        }finally{
            if(wasActive)activeChunks.add(chunkKey);else activeChunks.remove(chunkKey);
            c.discard();
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void armedMineWaitsFortyTicksThenDetonatesOnceAndReloadIsImmediatelyLive(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.MINE,Map.of("target",0));Vec3 pos=p.position().add(0,4,0);var c=cow(h,pos);
        h.assertTrue(FocusMineEntity.spawn(p,plan,2,pos,new Vec3(0,0,1),false,1,0),"Mine spawn failed");
        var mine=h.getLevel().getEntitiesOfClass(FocusMineEntity.class,new AABB(pos,pos).inflate(2)).stream().filter(e->e.paidPlan().executionId().equals(plan.executionId())).findFirst().orElseThrow();mine.arm();mine.setNoGravity(true);
        for(int i=0;i<39;i++)mine.tick();end(h);h.assertTrue(c.getHealth()==100&&!mine.isRemoved()&&mine.counter()==1,"Mine armed too early");
        var save=mine.saveWithoutId(new CompoundTag());var loaded=FocusMediaModule.MINE.get().create(h.getLevel());loaded.load(save);h.assertTrue(loaded.bindOwner(p)&&loaded.armed()&&loaded.counter()==0,"Original reload mine counter changed");loaded.discard();
        mine.tick();h.assertTrue(mine.isRemoved(),"Mine did not consume one detonation");end(h);h.assertTrue(c.getHealth()==96,"Mine wrong suffix");end(h);h.assertTrue(c.getHealth()==96,"Mine detonated twice");c.discard();h.succeed();
    }
    @GameTest(template="empty") public static void spellBatOwnsTargetingConsumesHealthAndCarriesPoint33Power(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.SPELLBAT,Map.of("target",0));Vec3 pos=p.position().add(0,4,0);var c=cow(h,pos.add(1,0,0));
        FocusExecution.resume(p,plan,1,null,pos,new Vec3(0,0,1));var bat=h.getLevel().getEntitiesOfClass(SpellBatEntity.class,new AABB(pos,pos).inflate(2)).stream().filter(e->e.owner()==p).findFirst().orElseThrow();
        h.assertTrue(bat.power()==.33F&&bat.getHealth()==5&&bat.findTarget()==c,"Bat owner/target/strength failed");bat.attack(c);end(h);
        h.assertTrue(bat.getHealth()==4&&bat.attackTime()==40&&Math.abs(c.getHealth()-98.68)<.001,"Bat attack wrong strength/health/cooldown");bat.attack(c);end(h);h.assertTrue(bat.getHealth()==4,"Bat bypassed40tick delay");
        var save=bat.saveWithoutId(new CompoundTag());bat.discard();var loaded=FocusMediaModule.SPELL_BAT.get().create(h.getLevel());loaded.load(save);h.assertTrue(loaded.bindOwner(p)&&loaded.power()==.33F&&loaded.getHealth()==4&&loaded.attackTime()==0,"Original save bat state failed");loaded.discard();c.discard();h.succeed();
    }
    @GameTest(template="empty") public static void architectDimensionsFaceOffsetAndExposedConnectedSurfaceStayBounded(GameTestHelper h){
        var p=player(h);var wand=CatalogModule.stack("caster_basic");var plan=plan(FocusNodeRegistry.PLAN,Map.of());thaumcraft.auromancy.FocusSelection.setInstalled(wand,FocusStacks.apply(CatalogModule.stack("focus_3"),plan,"Plan"));p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wand);
        h.assertTrue(FocusPlanArea.radius(wand,"x")==3&&FocusPlanArea.cycle(p,0)&&FocusPlanArea.radius(wand,"x")==0&&FocusPlanArea.radius(wand,"z")==0,"Area default/cycle failed");
        FocusPlanArea.cycle(p,1);FocusPlanArea.cycle(p,0);h.assertTrue(FocusPlanArea.dimension(wand)==1&&FocusPlanArea.radius(wand,"x")==1&&FocusPlanArea.radius(wand,"y")==0,"Axis cycle affected wrong radius");
        wand.getOrCreateTag().putInt("areax",1);wand.getOrCreateTag().putInt("areay",0);wand.getOrCreateTag().putInt("areaz",0);
        BlockPos center=h.absolutePos(new BlockPos(1,4,1));for(int x=-2;x<=2;x++)h.getLevel().setBlockAndUpdate(center.offset(x,0,0),Blocks.STONE.defaultBlockState());
        var full=FocusPlanArea.blocks(h.getLevel(),wand,center,Direction.EAST,0);h.assertTrue(full.size()==3&&full.contains(center.offset(-2,0,0))&&!full.contains(center.offset(1,0,0)),"Full region face offset wrong");
        h.succeed();
    }
    @GameTest(template="empty") public static void forgedPaidSaveOrWrongOwnerCannotExecuteIntermediary(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.CLOUD,Map.of());var paid=new PaidFocusContinuation(p,plan,2,.5F,0);var tag=paid.save();tag.putFloat("Power",Float.NaN);
        h.assertTrue(PaidFocusContinuation.read(tag,FocusNodeRegistry.CLOUD)==null,"NaN continuation accepted");tag=paid.save();tag.putInt("Next",1);h.assertTrue(PaidFocusContinuation.read(tag,FocusNodeRegistry.CLOUD)==null,"Wrong continuation parent accepted");
        var loaded=PaidFocusContinuation.read(paid.save(),FocusNodeRegistry.CLOUD);var stranger=player(h);h.assertTrue(loaded!=null&&!loaded.bind(stranger)&&loaded.bind(p),"Paid owner can be hijacked");h.succeed();
    }
    @GameTest(template="empty") public static void spellBatDoesNotActivateActualPressurePlate(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.SPELLBAT,Map.of());var pos=h.absolutePos(new BlockPos(3,4,3));
        h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(pos,Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
        SpellBatEntity.spawn(p,plan,2,Vec3.atBottomCenterOf(pos),false,.33F,0);
        var bat=h.getLevel().getEntitiesOfClass(SpellBatEntity.class,new AABB(pos).inflate(1)).stream().filter(e->e.owner()==p).findFirst().orElseThrow();
        var state=h.getLevel().getBlockState(pos);state.entityInside(h.getLevel(),pos,bat);
        h.assertTrue(!h.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Spell bat activated pressure plate");
        var control=cow(h,Vec3.atBottomCenterOf(pos));h.getLevel().getBlockState(pos).entityInside(h.getLevel(),pos,control);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),"Plate control did not activate");
        bat.discard();control.discard();h.succeed();
    }
    @GameTest(template="empty") public static void armedMineKeepsMotionAndGravityAndForgeCanVetoArming(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.MINE,Map.of());Vec3 pos=p.position().add(0,4,0);
        FocusMineEntity.spawn(p,plan,2,pos,new Vec3(0,0,1),false,1,0);
        var mine=h.getLevel().getEntitiesOfClass(FocusMineEntity.class,new AABB(pos,pos).inflate(1)).stream().filter(e->e.paidPlan().executionId().equals(plan.executionId())).findFirst().orElseThrow();
        mine.arm();mine.setDeltaMovement(.25,0,0);mine.tick();
        h.assertTrue(Math.abs(mine.getX()-pos.x-.25)<.001&&Math.abs(mine.getDeltaMovement().y+.01)<.001,"Armed mine froze inherited movement/gravity");
        mine.discard();var start=h.absolutePos(new BlockPos(2,5,2));h.getLevel().setBlockAndUpdate(start.below(),Blocks.STONE.defaultBlockState());
        FocusMineEntity.spawn(p,plan,2,Vec3.atCenterOf(start),new Vec3(0,-1,0),false,1,0);
        var falling=h.getLevel().getEntitiesOfClass(FocusMineEntity.class,new AABB(start).inflate(1)).stream().filter(e->!e.isRemoved()).findFirst().orElseThrow();falling.setDeltaMovement(0,-1,0);
        var veto=(java.util.function.Consumer<net.minecraftforge.event.entity.ProjectileImpactEvent>)(e->{if(e.getProjectile()==falling)e.setCanceled(true);});
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,veto);
        try{falling.tick();h.assertTrue(!falling.armed(),"Forge impact veto did not protect mine arming");}
        finally{MinecraftForge.EVENT_BUS.unregister(veto);falling.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void impactListenerRemovedLiveMineCannotQueueItsPaidDetonation(GameTestHelper h){
        var p=player(h);var plan=plan(FocusNodeRegistry.MINE,Map.of());var start=h.absolutePos(new BlockPos(3,5,3));
        Vec3 pos=Vec3.atCenterOf(start);h.getLevel().setBlockAndUpdate(start.below(),Blocks.STONE.defaultBlockState());
        var target=cow(h,pos.add(.75,-1,0));
        FocusMineEntity.spawn(p,plan,2,pos,new Vec3(0,-1,0),false,1,0);
        var original=h.getLevel().getEntitiesOfClass(FocusMineEntity.class,new AABB(start).inflate(1)).stream().filter(e->e.paidPlan().executionId().equals(plan.executionId())).findFirst().orElseThrow();
        // ServerLevel advances entity age before tick(); this direct fixture must do so explicitly.
        original.arm();var save=original.saveWithoutId(new CompoundTag());save.putInt("Age",5);original.discard();
        var mine=FocusMediaModule.MINE.get().create(h.getLevel());mine.load(save);h.assertTrue(mine.bindOwner(p)&&mine.armed()&&mine.counter()==0&&mine.tickCount==5,"Live saved mine fixture did not restore on a trigger tick");
        mine.setNoGravity(true);mine.setDeltaMovement(0,-1,0);h.getLevel().addFreshEntity(mine);
        var impacts=new java.util.concurrent.atomic.AtomicInteger();int pending=FocusMediaCallbacks.pending(h.getLevel());
        var listener=(java.util.function.Consumer<net.minecraftforge.event.entity.ProjectileImpactEvent>)(e->{if(e.getProjectile()==mine){impacts.incrementAndGet();mine.discard();}});
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,listener);
        try{mine.tick();h.assertTrue(impacts.get()==1&&mine.isRemoved(),"Real impact listener did not remove live mine");
            h.assertTrue(FocusMediaCallbacks.pending(h.getLevel())==pending,"Removed mine queued paid continuation");end(h);h.assertTrue(target.getHealth()==100,"Removed mine dealt detached damage");}
        finally{MinecraftForge.EVENT_BUS.unregister(listener);mine.discard();}
        var control=FocusMediaModule.MINE.get().create(h.getLevel());control.load(save);h.assertTrue(control.bindOwner(p),"Control owner did not bind");
        control.setNoGravity(true);control.setDeltaMovement(0,-1,0);h.getLevel().addFreshEntity(control);control.tick();
        h.assertTrue(control.isRemoved()&&FocusMediaCallbacks.pending(h.getLevel())>0,"Same live impact geometry did not trigger the unremoved control mine");
        end(h);h.assertTrue(target.getHealth()==96,"Control did not deal the expected paid damage");control.discard();target.discard();h.succeed();
    }
}
