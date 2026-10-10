package thaumcraft.world.rift;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.FocusCasting;
import thaumcraft.auromancy.media.FocusCloudEntity;
import thaumcraft.auromancy.media.FocusMediaModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.infusion.InfusionEffects;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.UUID;

/** Native weighted-event adapters, infection cascade and live Wisp combat/loot. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class RiftEventsGameTests {
    private RiftEventsGameTests() {}
    private static Vec3 center(GameTestHelper h) { return Vec3.atCenterOf(h.absolutePos(new BlockPos(3,45,3))); }
    private static FluxRiftEntity rift(GameTestHelper h) {
        var rift = (FluxRiftEntity)VisualEntitiesModule.EFFECTS.get("flux_rift").get().create(h.getLevel());
        rift.setPos(center(h)); rift.setRiftSeed(123); rift.setRiftSize(20); rift.setRiftStability(-20);
        h.getLevel().addFreshEntity(rift); return rift;
    }
    private static Cow cow(GameTestHelper h, Vec3 position) {
        var cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100); cow.setPos(position);
        h.getLevel().addFreshEntity(cow); return cow;
    }
    private static FakePlayer player(GameTestHelper h, Vec3 position) {
        var player = new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rift_event_qa"));
        player.setPos(position); player.setNoGravity(true); player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(player); return player;
    }
    private static WispEntity wisp(GameTestHelper h, Vec3 position) {
        var wisp = (WispEntity)VisualEntitiesModule.LIVING.get("wisp").get().create(h.getLevel());
        wisp.setPos(position); h.getLevel().addFreshEntity(wisp); return wisp;
    }
    private static void removePlayer(GameTestHelper h, ServerPlayer player) { player.discard(); h.getLevel().players().remove(player); }

    @GameTest(template="empty") public static void originalFiveWeightsKeepBlockedEcologySlotAndCloudCostQuirk(GameTestHelper h) {
        h.assertTrue(RiftEvents.EVENTS.equals(List.of(new RiftEvents.Entry(0,50,5,true),new RiftEvents.Entry(1,10,0,false),
                new RiftEvents.Entry(2,20,10,true),new RiftEvents.Entry(3,20,10,true),new RiftEvents.Entry(4,1,0,true))),"Original event cost or near-taint flags changed");
        int[] counts = new int[5];
        for (int slot=0; slot<101; slot++) counts[RiftEvents.choose((slot+.5)/101)]++;
        h.assertTrue(java.util.Arrays.equals(counts,new int[]{50,10,20,20,1}),"Unavailable ecology rerolled or original weight proportions changed");
        for (int sample : new int[]{0,3,4,31,60,120}) h.assertTrue(RiftEvents.cloudDurationSetting(sample)==30,"Original spinner exhaustion was changed to a lower clamp");
        for (int sample : new int[]{5,10,20,30}) h.assertTrue(RiftEvents.cloudDurationSetting(sample)==sample,"Valid original cloud duration was changed");
        h.succeed();
    }
    @GameTest(template="empty") public static void primeSeedSlotDoesNotSpawnACatalogFakeDiscardRiftOrPolluteAura(GameTestHelper h) {
        var rift=rift(h); int seedCount=h.getLevel().getEntitiesOfClass(thaumcraft.catalog.entities.VisualMobEntity.class,rift.getBoundingBox().inflate(32)).size();
        float flux=AuraManager.getFlux(h.getLevel(),rift.blockPosition());
        try {
            h.assertTrue(RiftEvents.execute(rift,1)==RiftEvents.Outcome.UNSUPPORTED && !rift.isRemoved()
                    && rift.getRiftStability()==-20 && rift.getRiftSize()==20 && !rift.getCollapse(),"Unavailable taint event applied original side effects without its ecology");
            h.assertTrue(AuraManager.getFlux(h.getLevel(),rift.blockPosition())==flux
                    && h.getLevel().getEntitiesOfClass(thaumcraft.catalog.entities.VisualMobEntity.class,rift.getBoundingBox().inflate(32)).size()==seedCount,"Unavailable event spawned a fake seed or polluted aura");
        } finally { rift.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void collapseEventKeepsZeroStabilityCostAndHasNoTaintGate(GameTestHelper h) {
        var rift=rift(h);
        try { h.assertTrue(RiftEvents.execute(rift,4)==RiftEvents.Outcome.APPLIED && rift.getCollapse()
                && rift.getRiftStability()==-20 && rift.getRiftSize()==20,"Collapse changed stability or contracted outside the entity tick"); }
        finally { rift.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void infectionEventUsesBoundingCubeExactDurationAndUncurableInitialInstance(GameTestHelper h) {
        var rift=rift(h); var near=cow(h,rift.position().add(10,10,10)); var far=cow(h,rift.position().add(0,20,0));
        try {
            h.assertTrue(RiftEvents.execute(rift,2)==RiftEvents.Outcome.APPLIED && rift.getRiftStability()==-10,"Successful infection did not add original ten stability");
            var effect=near.getEffect(InfusionEffects.INFECTIOUS_VIS_EXHAUST.get());
            h.assertTrue(effect!=null && effect.getDuration()==3000 && effect.getAmplifier()==2 && effect.getCurativeItems().isEmpty(),"Rift did not install the original uncurable3000/2 instance on a cube corner");
            h.assertTrue(!far.hasEffect(InfusionEffects.INFECTIOUS_VIS_EXHAUST.get()),"Event spread beyond its original rift bounds+16 cube");
            var saved=near.saveWithoutId(new CompoundTag()); var restored=EntityType.COW.create(h.getLevel()); restored.load(saved);
            effect=restored.getEffect(InfusionEffects.INFECTIOUS_VIS_EXHAUST.get());
            h.assertTrue(effect!=null && effect.getCurativeItems().isEmpty() && effect.getAmplifier()==2 && effect.getDuration()==3000,"Uncurable infection failed native effect persistence");
            restored.discard();
        } finally { near.discard(); far.discard(); rift.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void infectionWithoutTargetsAndRemovedRiftsCannotAddStability(GameTestHelper h) {
        var rift=rift(h);
        try { h.assertTrue(RiftEvents.execute(rift,2)==RiftEvents.Outcome.NO_TARGET && rift.getRiftStability()==-20,"Empty infection paid stability");
            rift.discard(); h.assertTrue(RiftEvents.execute(rift,4)==RiftEvents.Outcome.BLOCKED && !rift.getCollapse(),"Removed rift still executed a world event"); }
        finally { rift.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void phageSpreadsToFourBlockCubeWithLowerAmplifierAndOrdinaryDescendantCure(GameTestHelper h) {
        var carrier=cow(h,center(h)); var corner=cow(h,center(h).add(3.5,3.5,3.5)); var far=cow(h,center(h).add(0,8,0));
        var effect=InfusionEffects.INFECTIOUS_VIS_EXHAUST.get(); carrier.addEffect(new MobEffectInstance(effect,3000,2));
        try {
            h.assertTrue(effect.isDurationEffectTick(3000,2) && !effect.isDurationEffectTick(2999,2),"Infection cadence changed from duration%40");
            effect.applyEffectTick(carrier,2); var child=corner.getEffect(effect);
            h.assertTrue(child!=null && child.getDuration()==6000 && child.getAmplifier()==1 && !child.getCurativeItems().isEmpty(),"Phage descendants lost original6000/1 or milk cure");
            h.assertTrue(!far.hasEffect(effect) && carrier.getEffect(effect).getAmplifier()==2,"Phage ignored cubic range or reinfected its original carrier");
            effect.applyEffectTick(carrier,2); h.assertTrue(corner.getEffect(effect).getAmplifier()==1,"Already infected target received another tier");
        } finally { carrier.discard(); corner.discard(); far.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void zeroPhageTierSpreadsOrdinaryVisExhaustWithoutCreatingNegativeInfection(GameTestHelper h) {
        var carrier=cow(h,center(h)); var target=cow(h,center(h).add(2,0,0)); var effect=InfusionEffects.INFECTIOUS_VIS_EXHAUST.get();
        carrier.addEffect(new MobEffectInstance(effect,200,0));
        try { effect.applyEffectTick(carrier,0); var ordinary=target.getEffect(InfusionEffects.VIS_EXHAUST.get());
            h.assertTrue(ordinary!=null && ordinary.getDuration()==6000 && ordinary.getAmplifier()==0 && !target.hasEffect(effect),"Final phage tier did not become ordinary6000/0 Vis Exhaust");
        } finally { carrier.discard(); target.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void infectionPenaltyUsesMaximumTierRatherThanAddingBothIllnesses(GameTestHelper h) {
        var player=player(h,center(h));
        try {
            float base=FocusCasting.consumptionModifier(player);
            player.addEffect(new MobEffectInstance(InfusionEffects.INFECTIOUS_VIS_EXHAUST.get(),3000,2));
            h.assertTrue(Math.abs(FocusCasting.consumptionModifier(player)-base-.3F)<.0001,"Flux Phage did not raise actual caster price by30percent");
            player.addEffect(new MobEffectInstance(InfusionEffects.VIS_EXHAUST.get(),100,1));
            h.assertTrue(Math.abs(FocusCasting.consumptionModifier(player)-base-.3F)<.0001,"Two diseases summed instead of choosing the maximum");
            player.removeEffect(InfusionEffects.VIS_EXHAUST.get()); player.addEffect(new MobEffectInstance(InfusionEffects.VIS_EXHAUST.get(),100,4));
            h.assertTrue(Math.abs(FocusCasting.consumptionModifier(player)-base-.5F)<.0001,"Stronger ordinary Vis Exhaust lost its original precedence");
        } finally { removePlayer(h,player); } h.succeed();
    }
    @GameTest(template="empty") public static void nativeCloudEventIsFreeFixedFluxSuffixAndNeverAddsStability(GameTestHelper h) {
        var rift=rift(h); var player=player(h,rift.position().add(4,0,0)); player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));
        var knowledge=KnowledgeStore.get(player).save(); int xp=player.totalExperience; float vis=AuraManager.getVis(h.getLevel(),player.blockPosition());
        FocusCloudEntity cloud=null; Cow target=null;
        try {
            h.assertTrue(RiftEvents.execute(rift,3)==RiftEvents.Outcome.APPLIED && rift.getRiftStability()==-20,"Cloud event added its table cost despite never setting original didit");
            cloud=h.getLevel().getEntitiesOfClass(FocusCloudEntity.class,new AABB(player.position(),player.position()).inflate(4)).stream()
                    .filter(e->e.bindOwner(player)).findFirst().orElseThrow();
            h.assertTrue(cloud.power()==.5F && cloud.radius()>=1 && cloud.radius()<=3 && cloud.duration()>=10 && cloud.duration()<=20
                    && cloud.paidPlan().effect().key().equals("thaumcraft.FLUX") && cloud.paidPlan().effect().settings().get("power")==1,"World cloud has wrong root/medium/flux settings");
            h.assertTrue(player.totalExperience==xp && player.getInventory().getItem(0).getCount()==7 && knowledge.equals(KnowledgeStore.get(player).save())
                    && AuraManager.getVis(h.getLevel(),player.blockPosition())==vis,"Free world event charged item/XP/research/aura");
            target=cow(h,cloud.position().add(.5,0,0));
            for (int i=0;i<5;i++) { cloud.tickCount++; cloud.tick(); }
            MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true));
            h.assertTrue(target.getHealth()==98,"Native cloud did not execute actual half-power Flux damage");
            var saved=cloud.saveWithoutId(new CompoundTag()); var restored=FocusMediaModule.CLOUD.get().create(h.getLevel()); restored.load(saved);
            h.assertTrue(restored.bindOwner(player) && restored.power()==.5F && restored.duration()==cloud.duration()
                    && restored.paidPlan().executionId().equals(cloud.paidPlan().executionId()),"Free world continuation lost its fixed paid suffix on reload");
            restored.discard();
        } finally { if(cloud!=null)cloud.discard(); if(target!=null)target.discard(); removePlayer(h,player); rift.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void cloudWithoutPlayerDoesNotMintAContinuation(GameTestHelper h) {
        var rift=rift(h); var target=cow(h,rift.position().add(3,0,0));
        try { h.assertTrue(RiftEvents.execute(rift,3)==RiftEvents.Outcome.NO_TARGET && rift.getRiftStability()==-20
                && h.getLevel().getEntitiesOfClass(FocusCloudEntity.class,rift.getBoundingBox().inflate(16)).isEmpty(),"Non-player substituted for original closest player caster"); }
        finally { target.discard(); rift.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void wispRegistrationHasRealOriginalStatsAiAndCrystalDrop(GameTestHelper h) {
        var wisp=wisp(h,center(h)); wisp.setAspectType("vitium");
        try {
            h.assertTrue(!wisp.isNoAi() && wisp.isNoGravity() && wisp.getMaxHealth()==22 && wisp.getAttributeValue(Attributes.ATTACK_DAMAGE)==3
                    && wisp.getMaxSpawnClusterSize()==2 && wisp.removeWhenFarAway(200),"Wisp remained a persistent20HP NoAI catalogue mob");
            h.assertTrue(wisp.color()==Aspect.FLUX.getColor(),"Actual Wisp aspect type did not drive original renderer color");
            wisp.hurt(h.getLevel().damageSources().genericKill(),1000);
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(center(h),center(h)).inflate(2));
            var crystals=drops.stream().filter(e->AspectCrystalItem.crystalAspect(e.getItem())==Aspect.FLUX).toList();
            h.assertTrue(crystals.size()==1 && crystals.get(0).getItem().getCount()==1,"Real Wisp death did not supply exactly one tagged aspect crystal");
            crystals.forEach(ItemEntity::discard);
        } finally { wisp.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void oldVisualWispSaveMigratesToLiveAiAndRetainsOriginalType(GameTestHelper h) {
        var wisp=wisp(h,center(h)); var saved=wisp.saveWithoutId(new CompoundTag()); wisp.discard();
        saved.putBoolean("VisualOnly",true); saved.putBoolean("NoAI",true); saved.putBoolean("PersistenceRequired",true); saved.putString("Type","ignis");
        var restored=(WispEntity)VisualEntitiesModule.LIVING.get("wisp").get().create(h.getLevel()); restored.load(saved);
        try { h.assertTrue(!restored.isNoAi() && !restored.isPersistenceRequired() && restored.aspectType().equals("ignis")
                && restored.color()==Aspect.FIRE.getColor() && !restored.saveWithoutId(new CompoundTag()).contains("VisualOnly"),"Old catalogue marker kept Wisp inert or lost original Type"); }
        finally { restored.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void liveWispInitializesAnAspectFliesAndRetaliatesAgainstActualDamage(GameTestHelper h) {
        var wisp=wisp(h,center(h)); var target=cow(h,center(h).add(5,0,0));
        try {
            wisp.customServerAiStep(); h.assertTrue(Aspect.getAspect(wisp.aspectType())!=null && wisp.getDeltaMovement().lengthSqr()>0,"Live Wisp never initialized its primal/compound type or flight");
            wisp.hurt(h.getLevel().damageSources().mobAttack(target),1);
            h.assertTrue(wisp.getTarget()==target && wisp.getHealth()==21,"Native retaliation did not bind its real attacker");
        } finally { wisp.discard(); target.discard(); } h.succeed();
    }
    @GameTest(template="essentia_network") public static void wispZapChargesTwentyTicksAndUsesOriginalStationaryDamage(GameTestHelper h) {
        // Both actors and every terrain mutation fit inside this9x5x9 owned template.
        // The old empty3x3x3 fixture put the target outside its allocated terrain.
        for(int x=1;x<=7;x++)for(int y=1;y<=4;y++)for(int z=3;z<=5;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        var wisp=wisp(h,Vec3.atCenterOf(h.absolutePos(new BlockPos(2,1,4))));
        var target=cow(h,Vec3.atCenterOf(h.absolutePos(new BlockPos(6,1,4)))); wisp.setAspectType("aer"); wisp.setTarget(target);
        try {
            // This tests charge cadence with a continuously visible target, not the independent
            // original1/1000 target replacement. Load this bounded fixture ray explicitly;
            // production still refuses any visibility ray through an unloaded chunk.
            var ray=new AABB(wisp.getEyePosition(),target.getEyePosition());
            int x0=net.minecraft.util.Mth.floor(ray.minX)>>4,x1=net.minecraft.util.Mth.floor(ray.maxX)>>4;
            int z0=net.minecraft.util.Mth.floor(ray.minZ)>>4,z1=net.minecraft.util.Mth.floor(ray.maxZ)>>4;
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)h.getLevel().getChunk(x,z);
            h.assertTrue(RiftEvents.loaded(h.getLevel(),ray),"Charge fixture ray is not actually loaded");
            var wall=new BlockPos(4,2,4);h.setBlock(wall,Blocks.STONE);
            h.assertTrue(!wisp.hasLineOfSight(target),"Physical wall did not block native Wisp sight");
            wisp.getRandom().setSeed(5);wisp.customServerAiStep();
            h.assertTrue(wisp.attackCounter()==0&&target.getHealth()==100,"Wisp charged through a real solid wall");
            h.setBlock(wall,Blocks.AIR);
            h.assertTrue(wisp.hasLineOfSight(target),"Clearing the owned physical wall did not restore native visibility");
            for(int i=0;i<19;i++){
                // Seed5 nextInt1000=487 excludes rare target reacquisition, preserving all
                // runtime probabilities. Synchronous AI calls cannot interleave native ticks.
                wisp.getRandom().setSeed(5);wisp.customServerAiStep();
                h.assertTrue(wisp.getTarget()==target&&wisp.attackCounter()==i+1,
                        "Visible charge lost its target/counter at step"+(i+1)+": target="+(wisp.getTarget()==target)+", counter="+wisp.attackCounter());
                h.assertTrue(target.getHealth()==100,"Actual Wisp damage arrived before its twentieth charge: step"+(i+1)+", health="+target.getHealth());
            }
            // Seed5 after nextInt1000 gives a <.66 hit roll; the target is physically stationary.
            wisp.getRandom().setSeed(5); wisp.customServerAiStep();
            h.assertTrue(target.getHealth()==96 && wisp.attackCounter()>=-20 && wisp.attackCounter()<=-1,"Actual stationary Wisp zap changed4damage or negative reload counter");
        } finally { wisp.discard(); target.discard(); } h.succeed();
    }
    @GameTest(template="empty") public static void wispRejectsCreativeTargetAndCollisionBlockedRiftPlacement(GameTestHelper h) {
        var wisp=wisp(h,center(h)); var player=player(h,center(h).add(5,0,0)); player.getAbilities().invulnerable=true; wisp.setTarget(player);
        try { wisp.customServerAiStep(); h.assertTrue(wisp.getTarget()==null,"Wisp attacked original invulnerable-player exclusion");
            var pos=BlockPos.containing(center(h)); h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            try { h.assertTrue(!wisp.canSpawnFromRift(),"Rift Wisp spawn ignored native collision"); } finally { h.getLevel().removeBlock(pos,false); }
        } finally { wisp.discard(); removePlayer(h,player); } h.succeed();
    }
    @GameTest(template="empty") public static void ninthWispCannotSpawnInTheOriginalSixteenBlockPopulationCube(GameTestHelper h) {
        java.util.List<WispEntity> wisps=new java.util.ArrayList<>();
        try {
            for(int i=0;i<8;i++)wisps.add(wisp(h,center(h).add(i*1.2,0,0)));
            var ninth=(WispEntity)VisualEntitiesModule.LIVING.get("wisp").get().create(h.getLevel()); ninth.setPos(center(h).add(0,3,0));
            h.assertTrue(!ninth.canSpawnFromRift(),"Original eight-Wisp cap was removed"); ninth.discard();
        } finally { wisps.forEach(WispEntity::discard); } h.succeed();
    }
}
