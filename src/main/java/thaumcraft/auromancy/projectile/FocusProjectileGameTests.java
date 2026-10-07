package thaumcraft.auromancy.projectile;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.auromancy.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.world.aura.AuraManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusProjectileGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TC6Projectile"));
        Vec3 pos=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,1,1))); player.setPos(pos);
        player.setYRot(0); player.setXRot(0); player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return player;
    }
    private static FocusPlan plan(int speed,int option) {
        var graph=ElementalFocusGraphGameTests.graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FIRE,Map.of("speed",speed,"option",option),Map.of("power",1,"duration",0));
        var result=FocusCompiler.compile(graph,CatalogModule.stack("focus_3"),key->true);
        if (!result.success()) throw new AssertionError(result.error()); return result.plan();
    }
    private static FocusProjectileEntity projectile(GameTestHelper h,ServerPlayer caster,int speed,int option) {
        Vec3 source=caster.position().add(0,4,0); var paid=plan(speed,option);
        h.assertTrue(FocusProjectileEntity.spawn(caster,paid,2,source,new Vec3(0,0,1),speed,option),"Spawn rejected valid paid continuation");
        return ownedProjectile(h,caster,paid,2,source,4,"Initial paid spawn");
    }
    private static FocusProjectileEntity ownedProjectile(GameTestHelper h,ServerPlayer caster,FocusPlan paid,int next,Vec3 source,int radius,String phase) {
        // Concurrent GameTests share a ServerLevel and inflated boxes can reach
        // another fixture. Select this caster's exact paid execution, not merely
        // the globally newest projectile, and give a useful phase on failure.
        var nearby=h.getLevel().getEntitiesOfClass(FocusProjectileEntity.class,new AABB(source,source).inflate(radius));
        var owned=nearby.stream().filter(e->!e.isRemoved() && e.getOwner()==caster && e.paidPlan()!=null
                && e.paidPlan().executionId().equals(paid.executionId()) && e.nextIndex()==next).toList();
        h.assertTrue(owned.size()==1,phase+" expected one owned continuation at index"+next+", found="+owned.size()
                +", pending="+FocusProjectileImpacts.pending(h.getLevel())+", casterAlive="+caster.isAlive()+", source="+source
                +", sourceLoaded="+(h.getLevel().getChunkSource().getChunkNow(BlockPos.containing(source).getX()>>4,BlockPos.containing(source).getZ()>>4)!=null)
                +", nearby="+nearby.stream().map(e->e.getId()+"/owner"+e.ownerEntityId()+"/next"+e.nextIndex()+"/pos"+e.position()).toList());
        return owned.get(0);
    }
    private static void advance(FocusProjectileEntity entity) { entity.setOldPosAndRot(); entity.tickCount++; entity.tick(); }
    private static void end(GameTestHelper h) { MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true)); }
    private static void close(GameTestHelper h,double actual,double expected,String message) { h.assertTrue(Math.abs(actual-expected)<1e-6,message+": "+actual+" != "+expected); }

    @GameTest(template="empty") public static void movingCastResumesAtEndAndPaysAuraAndSharedCooldownOnce(GameTestHelper h) {
        var caster=player(h); var paid=plan(5,0);
        var wand=CatalogModule.stack("caster_basic"); FocusSelection.setInstalled(wand,FocusStacks.apply(CatalogModule.stack("focus_3"),paid,"Gifted projectile"));
        caster.setItemInHand(InteractionHand.MAIN_HAND,wand);
        AuraManager.drainVis(h.getLevel(),caster.blockPosition(),Float.MAX_VALUE,false); AuraManager.addVis(h.getLevel(),caster.blockPosition(),10);
        var cow=EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setPos(caster.position().add(0,0,4)); h.getLevel().addFreshEntity(cow);
        h.assertTrue(FocusCasting.cast(caster,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST,"Real focus cast failed");
        float after=AuraManager.getVis(h.getLevel(),caster.blockPosition()); close(h,after,10-paid.castVis(),"Initial fractional aura debit");
        var flying=h.getLevel().getEntitiesOfClass(FocusProjectileEntity.class,caster.getBoundingBox().inflate(8)).stream().max(Comparator.comparingInt(Entity::getId)).orElseThrow();
        float health=cow.getHealth();
        for (int i=0;i<8 && !flying.isRemoved();i++) advance(flying);
        h.assertTrue(flying.isRemoved() && cow.getHealth()==health && FocusProjectileImpacts.pending(h.getLevel())>0,"Hit did not defer its effect until END");
        MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.START,h.getLevel(),()->true));
        close(h,cow.getHealth(),health,"Continuation executed at START");
        var replacement=CatalogModule.stack("caster_basic"); FocusSelection.setInstalled(replacement,FocusStacks.apply(CatalogModule.stack("focus_3"),paid,"Swap")); caster.setItemInHand(InteractionHand.MAIN_HAND,replacement);
        end(h); close(h,cow.getHealth(),health-4,"Moving projectile did not execute terminal Fire");
        close(h,AuraManager.getVis(h.getLevel(),caster.blockPosition()),after,"Impact charged aura twice");
        h.assertTrue(FocusCasting.cast(caster,InteractionHand.MAIN_HAND)==FocusCasting.Result.COOLDOWN,"Impact or caster swap reset shared cooldown");
        advance(flying); end(h); close(h,cow.getHealth(),health-4,"Removed projectile replayed continuation");
        cow.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void directSpeedAndAirGravityUseOriginalFloats(GameTestHelper h) {
        var caster=player(h); var normal=projectile(h,caster,5,0);
        close(h,normal.getDeltaMovement().length(),5/3F,"Speed scale or inherited player velocity");
        normal.setDeltaMovement(1,0,0); Vec3 before=normal.position(); advance(normal);
        close(h,normal.getX(),before.x+1,"Move before drag"); close(h,normal.getDeltaMovement().x,.99F,"Air drag"); close(h,normal.getDeltaMovement().y,-.01F,"Normal gravity after drag"); normal.discard();
        var seeking=projectile(h,caster,1,2); seeking.setDeltaMovement(1,0,0); advance(seeking);
        close(h,seeking.getDeltaMovement().y,-.005F,"Seeking gravity"); seeking.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void waterUsesPointEightDragBeforeGravity(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,1,0); BlockPos water=BlockPos.containing(entity.position());
        for (BlockPos pos:BlockPos.betweenClosed(water.offset(-1,-1,-1),water.offset(1,1,1))) h.getLevel().setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState());
        entity.setPos(Vec3.atCenterOf(water)); entity.setDeltaMovement(.3,0,0); advance(entity);
        h.assertTrue(entity.isInWater(),"Water fixture did not enter water"); close(h,entity.getDeltaMovement().x,.3*.8F,"Water drag"); close(h,entity.getDeltaMovement().y,-.01F,"Water gravity"); entity.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void bounceDoubleDampsVerticalAndIgnoresEmptyCollisionShape(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,3,1); BlockPos block=BlockPos.containing(entity.position());
        int pendingBefore=FocusProjectileImpacts.pending(h.getLevel());
        h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState()); Vec3 original=entity.position(); entity.setDeltaMovement(.5,1,.5);
        entity.onHit(new BlockHitResult(original,Direction.UP,block,false));
        close(h,entity.getDeltaMovement().x,.45,"Horizontal bounce damping"); close(h,entity.getDeltaMovement().y,-.81,"Vertical double damping"); close(h,entity.getDeltaMovement().z,.45,"Horizontal bounce damping Z");
        h.assertTrue(FocusProjectileImpacts.pending(h.getLevel())==pendingBefore,"Bouncing block executed remaining Fire");
        entity.setPos(original); entity.setDeltaMovement(.5,0,0); h.getLevel().setBlockAndUpdate(block,Blocks.TORCH.defaultBlockState());
        entity.onHit(new BlockHitResult(original,Direction.EAST,block,false)); close(h,entity.getDeltaMovement().x,.5,"Empty collision shape reflected");
        h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState()); entity.setDeltaMovement(.1,0,0); entity.onHit(new BlockHitResult(original,Direction.EAST,block,false));
        h.assertTrue(entity.isRemoved() && FocusProjectileImpacts.pending(h.getLevel())==pendingBefore,"Low-speed bounce did not die harmlessly"); h.succeed();
    }
    @GameTest(template="empty") public static void savedPlanIsDetachedTypedAndLegacyAppearancesAreHarmless(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,3,2); var saved=entity.saveWithoutId(new CompoundTag()); entity.discard();
        var restored=VisualEntitiesModule.FOCUS_PROJECTILE.get().create(h.getLevel()); restored.load(saved); restored.setOwner(caster);
        h.assertTrue(restored.paidPlan()!=null && restored.nextIndex()==2 && restored.special()==2 && restored.ownerEntityId()==caster.getId(),"Paid continuation restore failed");
        saved.getCompound("PaidGraph").getList("nodes",Tag.TAG_COMPOUND).getCompound(2).putInt("setting.power",5);
        h.assertTrue(restored.paidPlan().effect().settings().get("power")==1,"Saved graph aliases execution plan");
        for (int mutation=0;mutation<5;mutation++) {
            CompoundTag bad=entity.saveWithoutId(new CompoundTag());
            if(mutation==0)bad.putFloat("Next",2); else if(mutation==1)bad.putInt("Next",1); else if(mutation==2)bad.putInt("Capacity",16); else if(mutation==3)bad.putInt("Special",1); else bad.remove("Owner");
            var rejected=VisualEntitiesModule.FOCUS_PROJECTILE.get().create(h.getLevel()); rejected.load(bad); rejected.setOwner(caster); advance(rejected);
            h.assertTrue(rejected.isRemoved() && rejected.paidPlan()==null,"Malformed saved continuation became spell: "+mutation);
        }
        var legacy=VisualEntitiesModule.FOCUS_PROJECTILE.get().create(h.getLevel()); var appearance=new CompoundTag(); appearance.putBoolean("VisualOnly",true); legacy.readAdditionalSaveData(appearance); advance(legacy);
        h.assertTrue(legacy.isRemoved(),"Old visual-only entity gained runtime"); restored.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void ownerLossLifetimeAndInvalidSpawnNeverExecute(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,1,0); entity.setDeltaMovement(Vec3.ZERO); entity.setNoGravity(true); entity.tickCount=1199;
        advance(entity); h.assertTrue(!entity.isRemoved(),"Expired at1200 instead of1201"); advance(entity); h.assertTrue(entity.isRemoved(),"Lifetime exceeded1200");
        var missing=projectile(h,caster,1,0); missing.setOwner(null);
        var cleared=missing.saveWithoutId(new CompoundTag()); h.assertTrue(!cleared.hasUUID("Owner"),"Cleared owner revived in save");
        var reloaded=VisualEntitiesModule.FOCUS_PROJECTILE.get().create(h.getLevel()); reloaded.load(cleared); advance(reloaded);
        h.assertTrue(reloaded.isRemoved()&&reloaded.paidPlan()==null,"Cleared owner revived a paid continuation on reload");
        Vec3 before=missing.position(); advance(missing);
        h.assertTrue(missing.isRemoved() && missing.position().equals(before),"Missing owner moved or executed");
        h.assertTrue(!FocusProjectileEntity.spawn(caster,plan(1,0),1,caster.position(),new Vec3(0,0,1),1,0)
                && !FocusProjectileEntity.spawn(caster,plan(1,0),2,new Vec3(Double.NaN,0,0),new Vec3(0,0,1),1,0)
                && !FocusProjectileEntity.spawn(caster,plan(1,0),2,caster.position(),new Vec3(1e308,0,1e308),1,0)
                && !FocusProjectileEntity.spawn(caster,plan(1,0),2,caster.position(),Vec3.ZERO,1,0),"Forged continuation or nonfinite trajectory spawned"); h.succeed();
    }
    @GameTest(template="empty") public static void hostileSeekIncludesNearestPassiveAndRetainsOriginalRadianCone(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,1,2);
        // The tiny template clears only its own low volume; the surrounding generated terrain can reach this old +32 height.
        entity.setPos(entity.getX(),250,entity.getZ()); entity.setNoGravity(true); entity.setDeltaMovement(0,0,.3); Vec3 origin=entity.position();
        var cow=EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setPos(origin.add(1,-.7,4)); h.getLevel().addFreshEntity(cow);
        var zombie=EntityType.ZOMBIE.create(h.getLevel()); zombie.setNoAi(true); zombie.setPos(origin.add(0,-.8,7)); h.getLevel().addFreshEntity(zombie);
        h.assertTrue(!FocusProjectileEntity.friendly(caster,cow) && FocusProjectileEntity.friendly(caster,caster),"Passive treated as friend or caster as hostile");
        entity.tickCount=4; advance(entity); h.assertTrue(entity.seekingTarget()==cow && entity.getDeltaMovement().x>0,"Hostile seek missed nearest passive in cone: target="+entity.seekingTarget()+" cow="+cow+" ticks="+entity.tickCount+" removed="+entity.isRemoved()+" cone="+entity.inCone(cow)+" motion="+entity.getDeltaMovement()+" position="+entity.position());
        cow.setPos(entity.position().add(8,-.57,8)); h.assertTrue(entity.inCone(cow),"Radian cone wrongly rejected45 degrees");
        cow.setPos(entity.position().add(14,-.57,8)); h.assertTrue(!entity.inCone(cow),"Cone confused radians with a broad angle");
        cow.setPos(entity.position().add(0,0,-3)); entity.tickCount=9; advance(entity);
        h.assertTrue(entity.seekingTarget()==null,"Held target outside cone was not cleared at fifth tick");
        cow.discard(); zombie.discard(); entity.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void forgeImpactVetoKeepsMovingAndNeverQueuesEffect(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,1,0); entity.setNoGravity(true); entity.setDeltaMovement(0,0,1);
        var cow=EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setPos(entity.position().add(0,-.7,.9)); h.getLevel().addFreshEntity(cow);
        AtomicInteger seen=new AtomicInteger(); Consumer<ProjectileImpactEvent> veto=event->{ if(event.getProjectile()==entity) { seen.incrementAndGet(); event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY); } };
        Vec3 before=entity.position(); MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,veto);
        try { advance(entity); } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(seen.get()==1 && !entity.isRemoved() && entity.getZ()>before.z && FocusProjectileImpacts.pending(h.getLevel())==0 && cow.getHealth()==cow.getMaxHealth(),"Cancelled impact stopped movement or executed effect");
        cow.discard(); entity.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void queuedImpactRejectsRemovedTargetsAndNestedProjectileResumesOnlySuffix(GameTestHelper h) {
        var caster=player(h); var entity=projectile(h,caster,1,0); var cow=EntityType.COW.create(h.getLevel()); cow.setPos(entity.position()); h.getLevel().addFreshEntity(cow);
        entity.onHit(new EntityHitResult(cow)); cow.discard(); end(h); h.assertTrue(FocusProjectileImpacts.pending(h.getLevel())==0,"Removed-target continuation remained queued");
        List<String> keys=List.of(FocusNodeRegistry.ROOT,FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FIRE); List<FocusGraph.Node> nodes=new ArrayList<>();
        for(int i=0;i<keys.size();i++)nodes.add(new FocusGraph.Node(i,i-1,i==keys.size()-1?List.of():List.of(i+1),0,i,keys.get(i),FocusNodeRegistry.get(keys.get(i)).defaultSettings()));
        var paid=FocusCompiler.compile(new FocusGraph(nodes),CatalogModule.stack("focus_3"),k->true).plan(); Vec3 source=caster.position().add(0,4,0);
        h.assertTrue(FocusProjectileEntity.spawn(caster,paid,2,source,new Vec3(0,0,1),1,0),"Nested initial spawn failed");
        var first=ownedProjectile(h,caster,paid,2,source,4,"Nested initial spawn"); first.setOldPosAndRot();
        var victim=EntityType.COW.create(h.getLevel()); victim.setNoAi(true); victim.setPos(first.position()); h.getLevel().addFreshEntity(victim); float health=victim.getHealth(); first.onHit(new EntityHitResult(victim)); end(h);
        var second=ownedProjectile(h,caster,paid,3,source,8,"Nested END continuation");
        h.assertTrue(second.nextIndex()==3 && victim.getHealth()==health,"Intermediary ran terminal effect or restarted root"); second.setOldPosAndRot(); second.onHit(new EntityHitResult(victim)); end(h);
        close(h,victim.getHealth(),health-4,"Nested suffix did not execute once"); victim.discard(); h.succeed();
    }
}
