package thaumcraft.auromancy;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.projectile.FocusProjectileEntity;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;
import java.util.*;
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CompleteFocusExecutionGameTests {
    private static ServerPlayer player(GameTestHelper h){var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TC6Branches"));p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,1,1))));p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);return p;}
    @GameTest(template="empty") public static void splitTargetExecutesBothReducedEffectsOnSameEntityAndPaysOnce(GameTestHelper h){
        var p=player(h);var plan=FocusCompiler.compile(CompleteFocusGraphGameTests.split(),CatalogModule.stack("focus_3"),k->true).plan();
        var wand=CatalogModule.stack("caster_basic");FocusSelection.setInstalled(wand,FocusStacks.apply(CatalogModule.stack("focus_3"),plan,"Branches"));p.setItemInHand(InteractionHand.MAIN_HAND,wand);
        p.setHealth(20);AuraManager.drainVis(h.getLevel(),p.blockPosition(),Float.MAX_VALUE,false);AuraManager.addVis(h.getLevel(),p.blockPosition(),20);
        // Separate direct run verifies both effects with original .75 power and same-cast immunity reset.
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(p.position().add(0,0,3));h.getLevel().addFreshEntity(cow);
        FocusExecution.resume(p,plan,2,new EntityHitResult(cow),cow.position(),new Vec3(0,0,1));
        h.assertTrue(Math.abs(cow.getHealth()-4)<.001,"Two effects did not each damage3: "+cow.getHealth());cow.discard();
        var self=new FocusGraph(List.of(CompleteFocusGraphGameTests.n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()),CompleteFocusGraphGameTests.n(1,0,List.of(2,3),FocusNodeRegistry.SPLITTARGET,Map.of()),CompleteFocusGraphGameTests.n(2,1,List.of(),FocusNodeRegistry.FIRE,Map.of()),CompleteFocusGraphGameTests.n(3,1,List.of(),FocusNodeRegistry.FLUX,Map.of())));
        var paid=FocusCompiler.compile(self,CatalogModule.stack("focus_3"),k->true).plan();FocusSelection.setInstalled(wand,FocusStacks.apply(CatalogModule.stack("focus_3"),paid,"Self branches"));
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST,"Branched physical focus cast failed");
        h.assertTrue(Math.abs(20-AuraManager.getVis(h.getLevel(),p.blockPosition())-paid.castVis())<.001&&FocusCasting.onCooldown(p),"Split duplicated debit/cooldown");h.succeed();
    }
    @GameTest(template="empty") public static void scatterCreatesDeclaredForksAndProjectileCarriesReducedPowerThroughSave(GameTestHelper h){
        var p=player(h);var graph=new FocusGraph(List.of(CompleteFocusGraphGameTests.n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()),CompleteFocusGraphGameTests.n(1,0,List.of(2),FocusNodeRegistry.SCATTER,Map.of("forks",4,"cone",10)),CompleteFocusGraphGameTests.n(2,1,List.of(3),FocusNodeRegistry.PROJECTILE,Map.of()),CompleteFocusGraphGameTests.n(3,2,List.of(),FocusNodeRegistry.FIRE,Map.of())));
        var plan=FocusCompiler.compile(graph,CatalogModule.stack("focus_3"),k->true).plan();Vec3 start=p.position().add(0,4,0);
        FocusExecution.resume(p,plan,1,new EntityHitResult(p),start,new Vec3(0,0,1));
        var list=h.getLevel().getEntitiesOfClass(FocusProjectileEntity.class,new AABB(start,start).inflate(4)).stream().filter(e->e.getOwner()==p).toList();h.assertTrue(list.size()==4,"Scatter fork count "+list.size());
        for(var e:list){var tag=e.saveWithoutId(new net.minecraft.nbt.CompoundTag());h.assertTrue(tag.getFloat("Power")==.5F&&tag.hasUUID("Execution")&&tag.getUUID("Execution").equals(plan.executionId()),"Paid strength/identity lost");e.discard();}
        h.succeed();
    }
}
