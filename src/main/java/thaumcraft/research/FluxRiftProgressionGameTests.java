package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.rift.FluxRiftEntity;

import java.util.List;
import java.util.UUID;

/** Canonical rift studies use real scanner objects and the server book payment path. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FluxRiftProgressionGameTests {
    @GameTest(template="essentia_network")
    public static void actualRiftHoverIsPureAndNativeScanPaysBothFactsOnlyOnce(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); var rift=rift(h,new BlockPos(4,4,6));
        scanner(p); aim(p,rift.getBoundingBox().getCenter()); var before=k.save(); int xp=p.totalExperience;
        var target=ScanningNetwork.capture(p).target();
        h.assertTrue(target!=null && target.location().entityId()==rift.getId() && !target.scanned()
                && before.equals(k.save()),"Real unscanned rift hover missed its target or wrote knowledge");
        scan(p);
        h.assertTrue(k.isResearchCompleteStrict("f_toomuchflux") && k.isResearchCompleteStrict("!FluxRift")
                && k.researchStage("FLUX")==0 && k.researchStage("FLUXRIFT")==0 && k.researchStage("RIFTCLOSER")==0
                && p.totalExperience==xp && !rift.getCollapse(),"Rift scan invented a paid research, collapse or XP");
        var once=k.save(); scan(p);
        h.assertTrue(once.equals(k.save()) && ScanningNetwork.capture(p).target().scanned(),"Repeat scan repaid a rift fact");
        var restored=PlayerKnowledge.load(once);
        h.assertTrue(restored.isResearchCompleteStrict("!FluxRift") && restored.isResearchCompleteStrict("f_toomuchflux"),"Actual rift facts failed save/load");
        rift.discard(); h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void heldCollapserAndDistantOrRemovedRiftsCannotSupplyTheScanProof(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); scanner(p); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.OFF_HAND,CatalogModule.stack("causality_collapser")); scan(p);
        h.assertTrue(!k.isResearchKnown("!FluxRift") && !k.isResearchKnown("f_toomuchflux"),"Held collapser substituted for the original ScanEntity");
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var rift=rift(h,new BlockPos(4,4,25)); aim(p,rift.getBoundingBox().getCenter()); scan(p);
        h.assertTrue(!k.isResearchKnown("!FluxRift") && !k.isResearchKnown("f_toomuchflux"),"Out-of-range world entity granted rift scan proof");
        rift.discard(); var before=k.save();
        h.assertTrue(!AuromancyProgressionEvents.recordScannedFact(p,rift) && before.equals(k.save()),"Removed rift granted a discovery"); h.succeed();
    }

    @GameTest(template="empty")
    public static void hiddenFluxCannotBeForgedFromAnEmptyBookRequestOrARiftFactAlone(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); book(p); var before=k.save();
        result(h,ResearchNetwork.processAdvance(p,"FLUX",0),ResearchProgression.Result.LOCKED);
        h.assertTrue(before.equals(k.save()) && p.totalExperience==0 && !ResearchBookVisibility.visible(k,ResearchCatalog.get("FLUX"),false),"Empty hidden-root request opened FLUX");
        KnowledgeStore.recordFact(p,"f_toomuchflux"); KnowledgeStore.recordFact(p,"!FluxRift");
        result(h,ResearchNetwork.processAdvance(p,"FLUX",0),ResearchProgression.Result.LOCKED);
        result(h,ResearchNetwork.processAdvance(p,"FLUXRIFT",0),ResearchProgression.Result.LOCKED);
        h.assertTrue(k.researchStage("FLUX")==0 && k.researchStage("FLUXRIFT")==0 && p.totalExperience==0,"Facts bypassed the actual aura discovery or completed FLUX parent"); h.succeed();
    }

    @GameTest(template="empty")
    public static void actualHeldAuraEventRetainsFiveXpThenRiftFactCompletesItsEmptyConclusion(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); scanner(p);
        AuraManager.drainVis(h.getLevel(),p.blockPosition(),Float.MAX_VALUE,false);
        AuraManager.drainFlux(h.getLevel(),p.blockPosition(),Float.MAX_VALUE,false);
        AuraManager.addVis(h.getLevel(),p.blockPosition(),10); AuraManager.addFlux(h.getLevel(),p.blockPosition(),11);
        p.tickCount=20; MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(k.researchStage("FLUX")==1 && p.totalExperience==5 && !k.isResearchCompleteStrict("f_toomuchflux"),"Held aura event skipped its original five-XP stage or invented a rift fact");
        book(p); var before=k.save();
        result(h,ResearchNetwork.processAdvance(p,"FLUX",1),ResearchProgression.Result.MISSING_REQUIREMENTS);
        h.assertTrue(before.equals(k.save()) && p.totalExperience==5,"Missing world proof partially advanced FLUX");
        KnowledgeStore.recordFact(p,"f_toomuchflux");
        result(h,ResearchNetwork.processAdvance(p,"FLUX",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("FLUX")==3 && p.totalExperience==10 && k.isResearchCompleteStrict("FLUX"),"FLUX failed to skip its empty final stage or charged incorrect XP");
        before=k.save(); result(h,ResearchNetwork.processAdvance(p,"FLUX",1),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(k.save()) && p.totalExperience==10 && !KnowledgeStore.startFluxResearch(p),"Replay restarted/paid FLUX"); h.succeed();
    }

    @GameTest(template="empty")
    public static void fluxRiftRequiresBothCompletedFluxAndScanBeforeItsOneEmptyStage(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); book(p);
        h.assertTrue(ResearchCatalog.get("FLUXRIFT").parents().equals(List.of("FLUX","!FluxRift"))
                && ResearchCatalog.get("FLUXRIFT").stages().size()==1,"Original FLUXRIFT parents/empty stage changed");
        k.setResearchStage("FLUX",1); KnowledgeStore.recordFact(p,"!FluxRift");
        result(h,ResearchNetwork.processAdvance(p,"FLUXRIFT",0),ResearchProgression.Result.LOCKED);
        complete(k,"FLUX"); result(h,ResearchNetwork.processAdvance(p,"FLUXRIFT",0),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("FLUXRIFT")==2 && p.totalExperience==5 && PlayerKnowledge.load(k.save()).isResearchCompleteStrict("FLUXRIFT"),"One empty rift stage changed its completion/XP/persistence");
        var before=k.save(); result(h,ResearchNetwork.processAdvance(p,"FLUXRIFT",0),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(k.save()) && p.totalExperience==5,"Replayed rift request rewarded again");
        var missing=player(h); book(missing); complete(KnowledgeStore.get(missing),"FLUX");
        result(h,ResearchNetwork.processAdvance(missing,"FLUXRIFT",0),ResearchProgression.Result.LOCKED); h.succeed();
    }

    @GameTest(template="empty")
    public static void closerRequiresCompletedBatteryInfusionAndRiftWithoutInventingEldritch(GameTestHelper h) {
        h.assertTrue(ResearchCatalog.get("RIFTCLOSER").parents().equals(List.of("FLUXRIFT","INFUSION","VISBATTERY")),"RIFTCLOSER lost its VISBATTERY prerequisite");
        for(String omitted:List.of("FLUXRIFT","INFUSION","VISBATTERY")) {
            var p=player(h); book(p); var k=KnowledgeStore.get(p);
            for(String parent:List.of("FLUXRIFT","INFUSION","VISBATTERY")) if(!parent.equals(omitted)) complete(k,parent);
            k.setResearchStage(omitted,ResearchCatalog.get(omitted).stages().size());
            result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",0),ResearchProgression.Result.LOCKED);
            complete(k,omitted); result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",0),ResearchProgression.Result.STARTED);
            h.assertTrue(k.researchStage("RIFTCLOSER")==1 && p.totalExperience==5,"Closer start skipped paid research requirements");
            for(String late:List.of("UNLOCKELDRITCH","BASEELDRITCH","MATSTUDVOID","VOIDSIPHON"))
                h.assertTrue(!ResearchProgression.supportsProgression(late) && k.researchStage(late)==0,"Rift branch fabricated later Eldritch gate "+late);
        } h.succeed();
    }

    @GameTest(template="empty")
    public static void closerPaysExact16ObservationAnd64Plus32TheoryAtomicallyWithReplayProtection(GameTestHelper h) {
        var p=closer(h); var k=KnowledgeStore.get(p);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",19);
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"ALCHEMY",63);
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,3)); var before=k.save(); var inventory=new ListTag(); p.getInventory().save(inventory);
        result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",1),ResearchProgression.Result.MISSING_REQUIREMENTS);
        var afterInventory=new ListTag(); p.getInventory().save(afterInventory);
        h.assertTrue(before.equals(k.save()) && inventory.equals(afterInventory) && p.totalExperience==5,"Short theory request partially paid closer");
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"ALCHEMY",4);
        h.assertTrue(ResearchBookRequirements.rows(ResearchCatalog.get("RIFTCLOSER").stages().get(0),k,p.getInventory()).stream().allMatch(ResearchBookRequirements.Row::met),"Book preview disagrees with actual original closer costs");
        result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("RIFTCLOSER")==3 && k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==3
                && k.rawKnowledge(KnowledgeType.THEORY,"ALCHEMY")==3 && k.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3
                && p.totalExperience==10 && p.getInventory().getItem(9).getCount()==3 && k.actualWarp()==0,
                "Closer changed16/64/32 costs, stage3,10XP total, inventory or warp");
        before=k.save(); result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",1),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(k.save()) && p.totalExperience==10 && PlayerKnowledge.load(before).isResearchCompleteStrict("RIFTCLOSER"),"Closer replay/persistence paid twice"); h.succeed();
    }

    @GameTest(template="empty")
    public static void noBookAndSpectatorRequestsCannotPayTheNewBranch(GameTestHelper h) {
        var p=closer(h); var k=KnowledgeStore.get(p); p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); var before=k.save();
        result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",1),ResearchProgression.Result.NO_BOOK);
        book(p); ((AuditPlayer)p).spectator=true;
        result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",1),ResearchProgression.Result.LOCKED);
        h.assertTrue(before.equals(k.save()) && p.totalExperience==5,"Unheld/spectator book advanced closer"); h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void heldVoidSeedHoverAndPossessionDoNotGrantTheActualScanFactOrEldritchGate(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); var seed=voidSeed(); seed.setCount(2);
        h.assertTrue(!seed.isEmpty() && AuromancyProgressionEvents.scanFacts(seed).equals(List.of("f_VOIDSEED")),"Void Seed fixture did not resolve its real main-registry specimen");
        p.getInventory().setItem(9,seed.copy());
        h.assertTrue(!k.isResearchKnown("f_VOIDSEED"),"Possessing a Void Seed manufactured its scan proof");
        scanner(p); p.setShiftKeyDown(true); p.setItemInHand(InteractionHand.OFF_HAND,seed);
        var before=k.save(); var target=ScanningNetwork.capture(p).target();
        h.assertTrue(before.equals(k.save()),"Void Seed hover changed server knowledge");
        h.assertTrue(target!=null && !target.scanned(),"Actual unscanned held Void Seed was missing from the HUD target");
        scan(p);
        h.assertTrue(k.isResearchCompleteStrict("f_VOIDSEED") && seed.getCount()==2 && p.totalExperience==0,
                "Actual held seed scan failed the original fact or consumed/rewarded the specimen");
        var once=k.save(); scan(p);
        h.assertTrue(once.equals(k.save()) && PlayerKnowledge.load(once).isResearchCompleteStrict("f_VOIDSEED"),"Void Seed repeat/reload lost or repaid the proof");
        for(String late:List.of("UNLOCKELDRITCH","BASEELDRITCH","MATSTUDVOID","VOIDSIPHON"))
            h.assertTrue(k.researchStage(late)==0 && !k.isResearchCompleteStrict(late),"Scanning seed fabricated the later canonical gate "+late);
        h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void actualDroppedVoidSeedQualifiesWhileOrdinaryEnderPearlDoesNot(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); scanner(p); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.ENDER_PEARL)); scan(p);
        h.assertTrue(!k.isResearchKnown("f_VOIDSEED"),"Ordinary Ender Pearl substituted for original Void Seed ScanItem");
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var at=h.absolutePos(new BlockPos(4,4,5));
        var specimen=voidSeed(); h.assertTrue(!specimen.isEmpty(),"Dropped Void Seed fixture did not resolve its real main-registry specimen");
        var drop=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,specimen);
        drop.setNoGravity(true); drop.setDeltaMovement(Vec3.ZERO); h.assertTrue(h.getLevel().addFreshEntity(drop),"Cannot insert native dropped Void Seed");
        aim(p,drop.getBoundingBox().getCenter()); var before=k.save(); var target=ScanningNetwork.capture(p).target();
        h.assertTrue(before.equals(k.save()),"Dropped seed hover changed server knowledge");
        h.assertTrue(target!=null && target.location().entityId()==drop.getId(),"Native dropped seed was missing from the HUD target");
        scan(p); h.assertTrue(k.isResearchCompleteStrict("f_VOIDSEED") && drop.isAlive() && drop.getItem().getCount()==1
                && p.totalExperience==0 && k.researchStage("UNLOCKELDRITCH")==0,"Native dropped seed scan consumed the item or granted the paid late research");
        drop.discard(); h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p=new AuditPlayer(h);
        var at=h.absolutePos(new BlockPos(4,3,1)); p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5); book(p); return p;
    }
    private static ServerPlayer closer(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); for(String parent:List.of("FLUXRIFT","INFUSION","VISBATTERY")) complete(k,parent);
        result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",0),ResearchProgression.Result.STARTED); return p;
    }
    private static FluxRiftEntity rift(GameTestHelper h,BlockPos relative) {
        var rift=(FluxRiftEntity)VisualEntitiesModule.EFFECTS.get("flux_rift").get().create(h.getLevel());
        var at=h.absolutePos(relative); rift.setPos(at.getX()+.5,at.getY(),at.getZ()+.5); rift.setRiftSize(6); rift.setRiftStability(100);
        h.assertTrue(h.getLevel().addFreshEntity(rift),"Could not insert operational world rift fixture"); return rift;
    }
    private static void scanner(ServerPlayer p) {p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));}
    private static void book(ServerPlayer p) {p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));}
    private static void scan(ServerPlayer p) {ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);}
    private static void aim(ServerPlayer p,Vec3 target) {var d=target.subtract(p.getEyePosition()); p.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z))); p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private static void complete(PlayerKnowledge k,String key) {k.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1);}
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected) {h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);}
    private static ItemStack voidSeed() {return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","void_seed")));}
    private static final class AuditPlayer extends FakePlayer {
        boolean spectator;
        AuditPlayer(GameTestHelper h) {super(h.getLevel(),new GameProfile(UUID.randomUUID(),"rift_study"));}
        @Override public boolean isSpectator() {return spectator;}
    }
}
