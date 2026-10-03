package thaumcraft.auromancy;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusBoltGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TC6Bolt"));
        var pos=h.absolutePos(new BlockPos(2,2,2));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.setYRot(0);p.setXRot(-90);p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        for(int y=0;y<22;y++)h.getLevel().setBlockAndUpdate(pos.above(y),Blocks.AIR.defaultBlockState());return p;
    }
    private static Cow cow(ServerPlayer p,double height) {
        var cow=EntityType.COW.create(p.serverLevel());cow.setNoAi(true);cow.setNoGravity(true);
        cow.setPos(p.getX(),p.getY()+height,p.getZ());p.serverLevel().addFreshEntity(cow);return cow;
    }
    private static FocusPlan plan(String effect) {
        var graph=ElementalFocusGraphGameTests.graph(FocusNodeRegistry.BOLT,effect,Map.of(),Map.of());
        var result=FocusCompiler.compile(graph,CatalogModule.stack("focus_1"),k->true);
        if(!result.success())throw new AssertionError(result.error());return result.plan();
    }
    @GameTest(template="empty") public static void boltReachesPastTouchAndReturnsEntityTrajectoryAtSixteenBlocks(GameTestHelper h) {
        var p=player(h);var cow=cow(p,12);var start=p.getEyePosition().add(0,-.10000000149011612,0);var look=new Vec3(0,1,0);
        h.assertTrue(FocusCasting.traceTouch(p,start,look).target()==null,"Touch acquired a distant Bolt target");
        var ray=FocusBoltMedium.trace(p,plan(FocusNodeRegistry.FLUX),start,look);
        h.assertTrue(ray.target() instanceof EntityHitResult hit&&hit.getEntity()==cow,"Bolt missed twelve-block entity");
        h.assertTrue(Math.abs(ray.trajectory().distanceTo(start)-start.distanceTo(cow.position()))<.00001,"Bolt continued from box intersection instead of entity position distance");
        cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void boltMissContinuesExactlySixteenAndNeverTargetsBeyondRange(GameTestHelper h) {
        var p=player(h);var cow=cow(p,20);var start=p.getEyePosition().add(0,-.10000000149011612,0);
        var ray=FocusBoltMedium.trace(p,plan(FocusNodeRegistry.FIRE),start,new Vec3(0,7,0));
        h.assertTrue(ray.target()==null&&Math.abs(ray.trajectory().y-start.y-16)<.00001,"Bolt range used reach or unnormalised direction");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void boltHonorsEntityEyeOcclusionAndStopsAtTheWall(GameTestHelper h) {
        var p=player(h);var cow=cow(p,12);var wall=p.blockPosition().above(6);p.serverLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        var ray=FocusBoltMedium.trace(p,plan(FocusNodeRegistry.HEAL),p.getEyePosition().add(0,-.10000000149011612,0),new Vec3(0,1,0));
        h.assertTrue(ray.target() instanceof BlockHitResult hit&&hit.getBlockPos().equals(wall),"Bolt hit entity through solid occlusion");
        h.assertTrue(Math.abs(ray.trajectory().y-wall.getY())<.00001,"Bolt visual endpoint passed wall");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void realBoltFluxCastPaysOnceAndUsesServerTarget(GameTestHelper h) {
        var p=player(h);var cow=cow(p,12);var caster=CatalogModule.stack("caster_basic");var plan=plan(FocusNodeRegistry.FLUX);
        FocusSelection.setInstalled(caster,FocusStacks.apply(CatalogModule.stack("focus_1"),plan,"Bolt Flux"));p.setItemInHand(InteractionHand.MAIN_HAND,caster);
        AuraManager.drainVis(p.serverLevel(),p.blockPosition(),Float.MAX_VALUE,false);AuraManager.addVis(p.serverLevel(),p.blockPosition(),10);
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST&&cow.getHealth()==6,"Bolt did not execute paid Flux at distant entity");
        h.assertTrue(Math.abs(AuraManager.getVis(p.serverLevel(),p.blockPosition())-8.4F)<.001,"Bolt continuation took wrong/repeated debit");
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.COOLDOWN,"Bolt bypassed common cooldown");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void boltContinuationCanResumeAnAlreadyPaidDeliveryWithoutRecasting(GameTestHelper h) {
        var p=player(h);var cow=cow(p,12);cow.setHealth(3);
        AuraManager.drainVis(p.serverLevel(),p.blockPosition(),Float.MAX_VALUE,false);
        FocusExecution.resume(p,plan(FocusNodeRegistry.HEAL),1,null,p.getEyePosition().add(0,-.10000000149011612,0),new Vec3(0,1,0));
        h.assertTrue(cow.getHealth()==4,"Bolt suffix did not heal1 at finalPower1");
        h.assertTrue(AuraManager.getVis(p.serverLevel(),p.blockPosition())==0&&!FocusCasting.onCooldown(p),"Detached continuation started another cast/payment");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void boltVisualPacketRejectsNonfiniteOversizedOrNegativeWidth(GameTestHelper h) {
        h.assertTrue(FocusBoltNetwork.valid(Vec3.ZERO,new Vec3(0,16,0),.66F),"Original Bolt payload rejected");
        for(Vec3 v:List.of(new Vec3(Double.NaN,0,0),new Vec3(Double.POSITIVE_INFINITY,0,0),new Vec3(33,0,0)))
            h.assertTrue(!FocusBoltNetwork.valid(Vec3.ZERO,v,.66F),"Malformed Bolt path accepted");
        for(float width:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,-1,5})h.assertTrue(!FocusBoltNetwork.valid(Vec3.ZERO,new Vec3(0,16,0),width),"Malformed Bolt width accepted");h.succeed();
    }
}
