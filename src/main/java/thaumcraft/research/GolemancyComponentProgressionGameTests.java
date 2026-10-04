package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.scanning.*;

import java.util.List;
import java.util.UUID;

/** Natural scan commits and real book requests; predecessor stages/knowledge totals are explicit fixtures. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemancyComponentProgressionGameTests {
    private GolemancyComponentProgressionGameTests() {}

    @GameTest(template="essentia_network")
    public static void exactGolemScanFamiliesIncludeShulkerAndOwnedConstructsButNotItems(GameTestHelper h) {
        h.assertTrue(ResearchCatalog.entries().stream().filter(e -> ResearchProgression.isImplemented(e.key())).count()==51
                && ResearchProgression.isImplemented("MINDCLOCKWORK") && ResearchProgression.stageSupported("MINDCLOCKWORK",2),
                "Clockwork mind's complete original progression was omitted from the canonical inventory");
        for (var type : List.of(EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.SHULKER)) {
            h.assertTrue(AuromancyProgressionEvents.scanFacts(type.create(h.getLevel())).equals(List.of("f_golem")),
                    "Original EntityGolem subclass omitted: "+type);
        }
        for (String id : List.of("golem","turret_basic","turret_advanced","arcane_bore")) {
            var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id));
            h.assertTrue(type!=null && AuromancyProgressionEvents.scanFacts(type.create(h.getLevel())).equals(List.of("f_golem")),
                    "Original owned construct omitted: "+id);
        }
        h.assertTrue(AuromancyProgressionEvents.scanFacts(EntityType.ENDERMAN.create(h.getLevel())).isEmpty()
                && AuromancyProgressionEvents.scanFacts(new ItemStack(Items.IRON_GOLEM_SPAWN_EGG)).isEmpty()
                && !ResearchProgression.supportsProgression("CONTROLSEALS") && !ResearchProgression.supportsProgression("SEALCOLLECT"),
                "Unrelated entity/item or unported control seals unlocked");
        h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void actualIronGolemScanUnlocksCategoryWithAtomicKnowledgeAndSiblingPayment(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p);
        complete(state,"UNLOCKARTIFICE"); complete(state,"UNLOCKAUROMANCY"); state.setResearchStage("UNLOCKINFUSION",1);
        result(h,ResearchNetwork.processAdvance(p,"UNLOCKGOLEMANCY",0),ResearchProgression.Result.LOCKED);
        complete(state,"UNLOCKINFUSION");
        result(h,ResearchNetwork.processAdvance(p,"UNLOCKGOLEMANCY",0),ResearchProgression.Result.STARTED);
        setKnowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",20); setKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",20);
        missing(h,p,"UNLOCKGOLEMANCY",1);
        var golem=EntityType.IRON_GOLEM.create(h.getLevel()); golem.setNoAi(true); scanEntity(h,p,golem);
        h.assertTrue(state.isResearchCompleteStrict("f_golem") && !state.isResearchCompleteStrict("UNLOCKGOLEMANCY"),
                "Scan omitted f_golem or skipped the book payment");
        setKnowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",15); setKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",20);
        missing(h,p,"UNLOCKGOLEMANCY",1);
        setKnowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",19); setKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",15);
        missing(h,p,"UNLOCKGOLEMANCY",1);
        setKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",20); int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"UNLOCKGOLEMANCY",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("UNLOCKGOLEMANCY")==3 && state.researchStage("BASEGOLEMANCY")==2
                && state.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==3
                && state.rawKnowledge(KnowledgeType.OBSERVATION,"BASICS")==4 && p.totalExperience==xp+10
                && ResearchCategories.categoryUnlocked(state,"GOLEMANCY"),"16+16 payment, final-stage skip or sibling XP changed");
        var saved=state.save(); xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"UNLOCKGOLEMANCY",1),ResearchProgression.Result.STALE);
        h.assertTrue(saved.equals(state.save()) && xp==p.totalExperience
                && PlayerKnowledge.load(saved).isResearchCompleteStrict("BASEGOLEMANCY"),"Replay or save lost the completed sibling");
        h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void mindUsesRealScansAndCompletesOnlyAfterAtomicOriginalTheoryPayments(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p);
        complete(state,"UNLOCKGOLEMANCY"); complete(state,"BASEGOLEMANCY"); complete(state,"ESSENTIASMELTER"); state.setResearchStage("HEDGEALCHEMY",3);
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",0),ResearchProgression.Result.LOCKED);
        complete(state,"HEDGEALCHEMY");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",0),ResearchProgression.Result.NO_BOOK);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get())); int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",0),ResearchProgression.Result.STARTED);
        h.assertTrue(state.researchStage("MATSTUDWOOD")==2 && p.totalExperience==xp+10,
                "Original no-parent material sibling did not complete when the mind entry started");
        setKnowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",19); missing(h,p,"MINDCLOCKWORK",1);
        scanHeld(h,p,new ItemStack(Items.PAPER));
        h.assertTrue(state.isResearchCompleteStrict("!cognitio") && !state.isResearchCompleteStrict("!victus"),"Mind scan created Life proof");
        missing(h,p,"MINDCLOCKWORK",1); scanHeld(h,p,new ItemStack(Items.APPLE));
        h.assertTrue(state.isResearchCompleteStrict("!victus"),"Real apple scan omitted Life proof");
        setKnowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",15); missing(h,p,"MINDCLOCKWORK",1);
        setKnowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",19); xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",1),ResearchProgression.Result.ADVANCED);
        h.assertTrue(state.researchStage("MINDCLOCKWORK")==2 && state.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==3
                && p.totalExperience==xp+5 && state.knowsResearch("MINDCLOCKWORK@2") && !state.isResearchCompleteStrict("MINDCLOCKWORK"),
                "Mind stage2 did not pay16 or falsely completed the entire press research");
        var entry=ResearchCatalog.get("MINDCLOCKWORK");
        h.assertTrue(ResearchBookVisibility.visible(PlayerKnowledge.load(state.save()),entry,false)
                && ResearchBookVisibility.readableChapters(state,entry,false).equals(List.of(entry.stages().get(1))),
                "Entered mind stage2 vanished from the ordinary book or exposed the later press chapter");
        h.assertTrue(!ResearchProgression.canAdvance(state,entry),"Missing theories enabled the original second-stage payment");
        setKnowledge(p,KnowledgeType.THEORY,"ARTIFICE",31); setKnowledge(p,KnowledgeType.THEORY,"GOLEMANCY",40);
        missing(h,p,"MINDCLOCKWORK",2);
        setKnowledge(p,KnowledgeType.THEORY,"ARTIFICE",40); setKnowledge(p,KnowledgeType.THEORY,"GOLEMANCY",31);
        missing(h,p,"MINDCLOCKWORK",2);
        var saved=state.save(); xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",1),ResearchProgression.Result.STALE);
        h.assertTrue(saved.equals(state.save()) && xp==p.totalExperience && PlayerKnowledge.load(saved).researchStage("MINDCLOCKWORK")==2,
                "Stale first-stage request or reload crossed the unpaid theory boundary");
        setKnowledge(p,KnowledgeType.THEORY,"GOLEMANCY",40);
        h.assertTrue(ResearchProgression.canAdvance(state,entry,p.getInventory()),"Both complete theory costs did not enable advancement");
        xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",2),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("MINDCLOCKWORK")==4 && state.isResearchCompleteStrict("MINDCLOCKWORK")
                && state.rawKnowledge(KnowledgeType.THEORY,"ARTIFICE")==8 && state.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==8
                && state.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==3 && p.totalExperience==xp+5,
                "Final theory payment changed the two original32-unit costs, empty-stage skip, Observation remainder or XP");
        var chapters=ResearchBookVisibility.readableChapters(state,entry,false);
        h.assertTrue(chapters.equals(List.of(entry.stages().get(2))) && chapters.get(0).recipes().equals(List.of("thaumcraft:MindClockwork","thaumcraft:GolemPress"))
                && BookRecipeCatalog.originalStatus("thaumcraft:GolemPress").equals("blueprint")
                && thaumcraft.research.book.MultiblockCatalog.resolve("thaumcraft:GolemPress").orElseThrow().research().equals("MINDCLOCKWORK"),
                "Completion did not open only the original final chapter and its registered Golem Press blueprint");
        saved=state.save(); xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",2),ResearchProgression.Result.STALE);
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",4),ResearchProgression.Result.LOCKED);
        for(String unsupported:List.of("CONTROLSEALS","SEALCOLLECT","SEALSTORE","GOLEMDIRECT","MINDBIOTHAUMIC")) {
            result(h,ResearchNetwork.processAdvance(p,unsupported,0),ResearchProgression.Result.UNSUPPORTED);
            h.assertTrue(!state.isResearchKnown(unsupported),"Completed mind opened an unported sibling or descendant: "+unsupported);
        }
        h.assertTrue(saved.equals(state.save()) && xp==p.totalExperience && !ResearchProgression.canAdvance(state,entry)
                && PlayerKnowledge.load(saved).isResearchCompleteStrict("MINDCLOCKWORK"),"Completion replay, unsupported sibling request or reload mutated paid state");
        h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void hedgeStagesConsumeThreeObservationPaymentsAndModernPhysicalCraftProofs(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p);
        result(h,ResearchNetwork.processAdvance(p,"HEDGEALCHEMY",0),ResearchProgression.Result.LOCKED);
        complete(state,"BASEALCHEMY"); result(h,ResearchNetwork.processAdvance(p,"HEDGEALCHEMY",0),ResearchProgression.Result.STARTED);
        setKnowledge(p,KnowledgeType.OBSERVATION,"ALCHEMY",52); missing(h,p,"HEDGEALCHEMY",1);
        // Exercise the same server craft-credit operation used by real crucible output.
        KnowledgeStore.recordCraft(p,new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","tallow"))));
        h.assertTrue(!state.hasCraft("thaumcraft:leather"),"Fixture invented the shipped nonexistent leather craft");
        var rows=ResearchBookRequirements.rows(ResearchCatalog.get("HEDGEALCHEMY").stages().get(0),state,p.getInventory());
        h.assertTrue(rows.stream().filter(row->row.kind()==ResearchBookRequirements.Kind.CRAFT).count()==1,
                "Obsolete thaumcraft:leather remained a payable book proof");
        result(h,ResearchNetwork.processAdvance(p,"HEDGEALCHEMY",1),ResearchProgression.Result.ADVANCED);
        for (Item item:List.of(Items.GUNPOWDER,Items.SLIME_BALL,Items.GLOWSTONE_DUST)) KnowledgeStore.recordCraft(p,new ItemStack(item));
        missing(h,p,"HEDGEALCHEMY",2); KnowledgeStore.recordCraft(p,new ItemStack(Items.INK_SAC));
        result(h,ResearchNetwork.processAdvance(p,"HEDGEALCHEMY",2),ResearchProgression.Result.ADVANCED);
        for (Item item:List.of(Items.CLAY_BALL,Items.STRING,Items.LAVA_BUCKET)) KnowledgeStore.recordCraft(p,new ItemStack(item));
        missing(h,p,"HEDGEALCHEMY",3); KnowledgeStore.recordCraft(p,new ItemStack(Items.COBWEB));
        result(h,ResearchNetwork.processAdvance(p,"HEDGEALCHEMY",3),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("HEDGEALCHEMY")==5 && state.rawKnowledge(KnowledgeType.OBSERVATION,"ALCHEMY")==4
                && PlayerKnowledge.load(state.save()).isResearchCompleteStrict("HEDGEALCHEMY"),"Three16-unit payments or final-stage skip changed");
        var saved=state.save(); result(h,ResearchNetwork.processAdvance(p,"HEDGEALCHEMY",3),ResearchProgression.Result.STALE);
        h.assertTrue(saved.equals(state.save()),"Hedge replay paid twice"); h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void savedTallowAccessNeverSatisfiesCompletedHedgeParentOrNewProfileLesson(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"BASEGOLEMANCY"); complete(state,"ESSENTIASMELTER");
        result(h,ResearchNetwork.processDiscover(p,"PORT_TALLOW"),ResearchProgression.Result.UNSUPPORTED);
        state.discover("PORT_TALLOW"); var loaded=PlayerKnowledge.load(state.save());
        h.assertTrue(loaded.knowsResearch("HEDGEALCHEMY@1") && !loaded.isResearchKnown("HEDGEALCHEMY")
                && !loaded.isResearchCompleteStrict("HEDGEALCHEMY") && !ResearchProgression.canStart(loaded,"MINDCLOCKWORK"),
                "Legacy recipe alias turned into completed canonical parent");
        result(h,ResearchNetwork.processAdvance(p,"MINDCLOCKWORK",0),ResearchProgression.Result.LOCKED); h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"golem_components")); var at=h.absolutePos(new BlockPos(4,2,2));
        p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5); p.setYRot(0); p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get())); return p;
    }
    private static void scanEntity(GameTestHelper h,ServerPlayer p,Entity entity) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));
        entity.setPos(p.getX(),p.getY(),p.getZ()+2); entity.setNoGravity(true); h.assertTrue(h.getLevel().addFreshEntity(entity),"Cannot insert scan specimen");
        var before=KnowledgeStore.get(p).save(); var snapshot=ScanningNetwork.capture(p);
        h.assertTrue(snapshot.target()!=null && snapshot.target().location().entityId()==entity.getId() && before.equals(KnowledgeStore.get(p).save()),
                "Hover missed the actual golem or mutated knowledge");
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get()); ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        entity.discard(); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void scanHeld(GameTestHelper h,ServerPlayer p,ItemStack sample) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get())); p.setItemInHand(InteractionHand.OFF_HAND,sample); p.setShiftKeyDown(true);
        var before=KnowledgeStore.get(p).save(); ScanningNetwork.capture(p); h.assertTrue(before.equals(KnowledgeStore.get(p).save()),"Held hover created aspect facts");
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get()); ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void setKnowledge(ServerPlayer p,KnowledgeType type,String category,int amount) {
        KnowledgeStore.addKnowledge(p,type,category,amount-KnowledgeStore.get(p).rawKnowledge(type,category));
    }
    private static void complete(PlayerKnowledge state,String key) { state.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1); }
    private static void missing(GameTestHelper h,ServerPlayer p,String key,int stage) {
        var before=KnowledgeStore.get(p).save(); var inv=new ListTag(); p.getInventory().save(inv); int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,key,stage),ResearchProgression.Result.MISSING_REQUIREMENTS); var after=new ListTag(); p.getInventory().save(after);
        h.assertTrue(before.equals(KnowledgeStore.get(p).save()) && inv.equals(after) && xp==p.totalExperience,"Missing proof/resource partially paid "+key);
    }
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected) {
        h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);
    }
}
