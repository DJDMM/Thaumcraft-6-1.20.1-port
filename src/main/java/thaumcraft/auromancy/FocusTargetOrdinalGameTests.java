package thaumcraft.auromancy;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.media.FocusPlanArea;
import thaumcraft.auromancy.projectile.FocusProjectileEntity;
import thaumcraft.auromancy.projectile.FocusProjectileImpacts;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.world.aura.AuraManager;

import java.util.*;

/** Observable harvest times distinguish target indices from incoming trajectory indices. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusTargetOrdinalGameTests {
    private record Fixture(ServerPlayer player, FocusPlan plan, Vec3 source) {}
    private record Rays(long seed, List<BlockPos> targets) {}
    private static FocusGraph.Node n(int id, int parent, List<Integer> children, String key, Map<String,Integer> settings) {
        return CompleteFocusGraphGameTests.n(id,parent,children,key,settings);
    }
    private static FocusGraph scatter(String medium, int forks, int cone) {
        return new FocusGraph(List.of(n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()),
                n(1,0,List.of(2),FocusNodeRegistry.SCATTER,Map.of("forks",forks,"cone",cone)),
                n(2,1,List.of(3),medium,Map.of()),n(3,2,List.of(),FocusNodeRegistry.BREAK,Map.of())));
    }
    private static Fixture fixture(GameTestHelper h, FocusGraph graph) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TC6TargetOrdinal"));
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,18,1))));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var compiled=FocusCompiler.compile(graph,CatalogModule.stack("focus_3"),ignored->true);
        h.assertTrue(compiled.success(),"Regression fixture did not compile: "+compiled.error());
        var wand=CatalogModule.stack("caster_basic");
        FocusSelection.setInstalled(wand,FocusStacks.apply(CatalogModule.stack("focus_3"),compiled.plan(),"Target ordinals"));
        for(String axis:List.of("x","y","z"))wand.getOrCreateTag().putInt("area"+axis,0);
        p.setItemInHand(InteractionHand.MAIN_HAND,wand);
        return new Fixture(p,compiled.plan(),p.getEyePosition().add(0,-.10000000149011612,0));
    }
    private static List<Vec3> directions(long seed, int forks, int cone) {
        var random=RandomSource.create(seed);var result=new ArrayList<Vec3>();double scale=.007499999832361937*cone;
        for(int i=0;i<forks;i++)result.add(new Vec3(random.nextGaussian()*scale,random.nextGaussian()*scale,
                1+random.nextGaussian()*scale).normalize());
        return result;
    }
    private static void fund(GameTestHelper h, BlockPos pos) { AuraManager.addVis(h.getLevel(),pos,100); }
    private static boolean stone(GameTestHelper h, BlockPos pos) { return h.getLevel().getBlockState(pos).is(Blocks.STONE); }
    private static void activeTicks(GameTestHelper h, int count) { for(int i=0;i<count;i++)FocusBreakQueue.process(h.getLevel()); }

    @GameTest(template="empty") public static void scatterTouchMissesDoNotDelayTheFirstActualBreakTarget(GameTestHelper h) {
        var f=fixture(h,scatter(FocusNodeRegistry.TOUCH,4,360));Rays chosen=null;
        // Select a deterministic last-fork hit, then replay the real Scatter and Touch code.
        for(long seed=0;seed<1000&&chosen==null;seed++) {
            var rays=directions(seed,4,360);BlockPos pos=BlockPos.containing(f.source().add(rays.get(3).scale(3)));
            var old=h.getLevel().getBlockState(pos);if(!old.isAir())continue;
            h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());boolean correct=true;
            for(int i=0;i<4;i++) {
                var target=FocusCasting.traceTouch(f.player(),f.source(),rays.get(i)).target();
                correct&=i==3?target instanceof BlockHitResult b&&b.getBlockPos().equals(pos):target==null;
            }
            if(correct)chosen=new Rays(seed,List.of(pos));else h.getLevel().setBlockAndUpdate(pos,old);
        }
        h.assertTrue(chosen!=null,"Could not construct three misses followed by one actual target");
        BlockPos pos=chosen.targets().get(0);fund(h,pos);f.player().getRandom().setSeed(chosen.seed());
        FocusExecution.resume(f.player(),f.plan(),1,null,f.source(),new Vec3(0,0,1));
        activeTicks(h,24);h.assertTrue(stone(h,pos),"Half-power Break completed before its 25th active tick");
        activeTicks(h,1);h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"First actual target inherited the missed forks' ordinal");h.succeed();
    }

    @GameTest(template="empty") public static void planAggregatesScatterTargetsBeforeAssigningBreakOrdinals(GameTestHelper h) {
        var f=fixture(h,scatter(FocusNodeRegistry.PLAN,2,60));Rays chosen=null;
        for(long seed=0;seed<1000&&chosen==null;seed++) {
            var rays=directions(seed,2,60);var a=BlockPos.containing(f.source().add(rays.get(0).scale(4)));
            var b=BlockPos.containing(f.source().add(rays.get(1).scale(4)));
            if(a.equals(b)||!h.getLevel().isEmptyBlock(a)||!h.getLevel().isEmptyBlock(b))continue;
            h.getLevel().setBlockAndUpdate(a,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(b,Blocks.STONE.defaultBlockState());
            var first=FocusPlanArea.targets(f.player(),f.source(),rays.get(0),0);
            var second=FocusPlanArea.targets(f.player(),f.source(),rays.get(1),0);
            if(first.size()==1&&second.size()==1&&first.get(0).getBlockPos().equals(a)&&second.get(0).getBlockPos().equals(b))
                chosen=new Rays(seed,List.of(a,b));
            else { h.getLevel().setBlockAndUpdate(a,Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(b,Blocks.AIR.defaultBlockState()); }
        }
        h.assertTrue(chosen!=null,"Could not construct two distinct one-block Plan trajectories");
        var first=chosen.targets().get(0);var second=chosen.targets().get(1);fund(h,first);fund(h,second);
        f.player().getRandom().setSeed(chosen.seed());FocusExecution.resume(f.player(),f.plan(),1,null,f.source(),new Vec3(0,0,1));
        activeTicks(h,13);h.assertTrue(h.getLevel().getBlockState(first).isAir()&&stone(h,second),"Plan restarted target ordinal for its second trajectory");
        activeTicks(h,3);h.assertTrue(stone(h,second),"Second aggregate Plan target lost its four-tick delay");
        activeTicks(h,1);h.assertTrue(h.getLevel().getBlockState(second).isAir(),"Second aggregate Plan target did not finish on tick 17");h.succeed();
    }

    @GameTest(template="empty") public static void restoredScatterProjectileResumesOneTargetAtOrdinalZero(GameTestHelper h) {
        var f=fixture(h,scatter(FocusNodeRegistry.PROJECTILE,4,10));
        BlockPos pos=BlockPos.containing(f.source().add(0,0,3));h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());fund(h,pos);
        h.assertTrue(FocusProjectileEntity.spawn(f.player(),f.plan(),3,f.source(),new Vec3(0,0,1),1,0,.5F,3),"Projectile spawn failed");
        var original=h.getLevel().getEntitiesOfClass(FocusProjectileEntity.class,new AABB(f.source(),f.source()).inflate(3))
                .stream().filter(e->e.getOwner()==f.player()).findFirst().orElseThrow();
        var saved=original.saveWithoutId(new CompoundTag());original.discard();saved.putInt("Ordinal",3);
        var restored=new FocusProjectileEntity(VisualEntitiesModule.FOCUS_PROJECTILE.get(),h.getLevel());restored.load(saved);restored.setOwner(f.player());
        h.assertTrue(restored.paidPlan()!=null&&restored.saveWithoutId(new CompoundTag()).getInt("Ordinal")==0,"Legacy paid save retained its Scatter fork ordinal");
        for(int i=0;i<40&&!restored.isRemoved();i++)restored.tick();
        h.assertTrue(restored.isRemoved()&&stone(h,pos),"Projectile failed to collide or mined before its detached END callback");
        FocusProjectileImpacts.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true));
        activeTicks(h,24);h.assertTrue(stone(h,pos),"Restored half-power suffix mined too early");
        activeTicks(h,1);h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Projectile's one-target suffix retained a fork delay");h.succeed();
    }

    @GameTest(template="empty") public static void explicitSingleTargetCallbackKeepsItsParentBatchOrdinal(GameTestHelper h) {
        var graph=new FocusGraph(List.of(n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()),
                n(1,0,List.of(2),FocusNodeRegistry.TOUCH,Map.of()),n(2,1,List.of(),FocusNodeRegistry.BREAK,Map.of())));
        var f=fixture(h,graph);BlockPos pos=BlockPos.containing(f.source().add(0,0,3));
        h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());fund(h,pos);
        FocusExecution.resume(f.player(),f.plan(),2,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false),f.source(),new Vec3(0,0,1),1,3);
        activeTicks(h,24);h.assertTrue(stone(h,pos),"Explicit detached parent ordinal was incorrectly reset");
        activeTicks(h,1);h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Explicit ordinal three did not preserve twelve delayed and thirteen active ticks");h.succeed();
    }
}
