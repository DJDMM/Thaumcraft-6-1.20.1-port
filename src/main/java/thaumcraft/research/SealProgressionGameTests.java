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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.golemancy.press.GolemDesign;

import java.util.List;
import java.util.UUID;

/** Original archived stage requirements become working payments; precursor stages/facts are explicit fixtures. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class SealProgressionGameTests {
    private SealProgressionGameTests() {}
    private static final List<String> ADDED=List.of("CONTROLSEALS","SEALCOLLECT","SEALSTORE","SEALEMPTY","SEALPROVIDE","SEALSTOCK","SEALGUARD","SEALBUTCHER","SEALUSE","SEALHARVEST","SEALBREAK","SEALLUMBER","MINDBIOTHAUMIC","MATSTUDIRON","MATSTUDCLAY","MATSTUDBRASS","MATSTUDTHAUMIUM","GOLEMBREAKER","GOLEMCOMBATADV","GOLEMDIRECT","GOLEMLOGISTICS","GOLEMCLIMBER","GOLEMVISION");
    @GameTest(template="essentia_network")
    public static void canonicalCensusAndUnportedParentsStayClosed(GameTestHelper h) {
        h.assertTrue(ResearchCatalog.entries().stream().filter(e->ResearchProgression.isImplemented(e.key())).count()==82,"Canonical census should contain preserved79 plus Ore, CrystalFarmer and VisBattery");
        for(String key:ADDED)h.assertTrue(ResearchProgression.supportsProgression(key),"Working research omitted "+key);
        for(String key:List.of("HUNGRYCHEST","GOLEMFLYER","LEVITATOR","JARBRAIN"))h.assertTrue(ResearchProgression.supportsProgression(key),"Working device branch omitted "+key);
        for(String key:List.of("MATSTUDVOID","BASEELDRITCH"))h.assertTrue(!ResearchProgression.supportsProgression(key),"Unsupported late branch silently unlocked "+key);
        h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void paidMindCompletionRecursivelyCompletesOnlyOriginalEmptyControlSiblings(GameTestHelper h) {
        var player=player(h);var state=KnowledgeStore.get(player);for(String key:List.of("BASEGOLEMANCY","ESSENTIASMELTER","HEDGEALCHEMY","MATSTUDWOOD"))complete(state,key);
        state.setResearchStage("MINDCLOCKWORK",2);knowledge(player,KnowledgeType.THEORY,"ARTIFICE",32);knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",31);
        missing(h,player,"MINDCLOCKWORK",2);h.assertTrue(state.researchStage("CONTROLSEALS")==0,"Missing theory accidentally revealed seals");knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",32);int xp=player.totalExperience;
        result(h,ResearchNetwork.processAdvance(player,"MINDCLOCKWORK",2),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("MINDCLOCKWORK")==4&&player.totalExperience==xp+20,"Mind completion or three recursive sibling XP changed");
        for(String key:List.of("CONTROLSEALS","SEALCOLLECT","SEALSTORE"))h.assertTrue(state.researchStage(key)==2&&state.isResearchCompleteStrict(key),"Original eligible empty sibling not recursively completed "+key);
        h.assertTrue(state.researchStage("SEALEMPTY")==0&&state.researchStage("SEALGUARD")==0&&state.researchStage("MINDBIOTHAUMIC")==0,"Paid seal paths were freely granted");var save=state.save();
        result(h,ResearchNetwork.processAdvance(player,"MINDCLOCKWORK",2),ResearchProgression.Result.STALE);h.assertTrue(save.equals(state.save()),"Replay of Mind changed sibling research");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void controlSealsCannotBypassCompletedMindWithStartedBareKnowledge(GameTestHelper h) {
        var player=player(h);var state=KnowledgeStore.get(player);state.setResearchStage("MINDCLOCKWORK",2);result(h,ResearchNetwork.processAdvance(player,"CONTROLSEALS",0),ResearchProgression.Result.LOCKED);
        h.assertTrue(!ResearchProgression.canStart(state,"CONTROLSEALS")&&state.researchStage("SEALCOLLECT")==0,"Bare mind recipe access replaced strict control parent");
        complete(state,"MINDCLOCKWORK");int xp=player.totalExperience;result(h,ResearchNetwork.processAdvance(player,"CONTROLSEALS",0),ResearchProgression.Result.COMPLETE);
        h.assertTrue(player.totalExperience==xp+15&&state.isResearchCompleteStrict("SEALCOLLECT")&&state.isResearchCompleteStrict("SEALSTORE"),"Control's free but gated original sibling chain changed");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void allNinePaidSealPathsRequireTheirOriginalAspectFactAndExactly32Theory(GameTestHelper h) {
        for(String key:List.of("SEALEMPTY","SEALPROVIDE","SEALGUARD","SEALBUTCHER","SEALUSE","SEALHARVEST","SEALBREAK","SEALLUMBER")){
            var player=player(h);var state=KnowledgeStore.get(player);fixtureParents(state,key);result(h,ResearchNetwork.processAdvance(player,key,0),ResearchProgression.Result.STARTED);
            knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",40);missing(h,player,key,1);
            String fact=switch(key){case "SEALEMPTY"->"!vacuos";case "SEALPROVIDE"->"!desiderium";case "SEALGUARD"->"!mortuus";case "SEALBUTCHER"->"!bestia";case "SEALUSE"->"!instrumentum";case "SEALHARVEST","SEALLUMBER"->"!herba";default->"!perditio";};
            state.discover(fact);knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",31);missing(h,player,key,1);knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",40);int xp=player.totalExperience;
            result(h,ResearchNetwork.processAdvance(player,key,1),ResearchProgression.Result.COMPLETE);h.assertTrue(state.researchStage(key)==3&&state.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==8&&player.totalExperience==xp+5,"Original32 theory/final skip changed "+key);
            h.assertTrue(PlayerKnowledge.load(state.save()).isResearchCompleteStrict(key),"Paid stage did not survive save "+key);
        }
        var p=player(h);var s=KnowledgeStore.get(p);fixtureParents(s,"SEALSTOCK");result(h,ResearchNetwork.processAdvance(p,"SEALSTOCK",0),ResearchProgression.Result.STARTED);knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",31);missing(h,p,"SEALSTOCK",1);knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",32);result(h,ResearchNetwork.processAdvance(p,"SEALSTOCK",1),ResearchProgression.Result.COMPLETE);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void biothaumicMindRequiresBrainFactBothKnowledgeKindsAndOriginalPermanentWarp(GameTestHelper h) {
        var player=player(h);var state=KnowledgeStore.get(player);complete(state,"MINDCLOCKWORK");result(h,ResearchNetwork.processAdvance(player,"MINDBIOTHAUMIC",0),ResearchProgression.Result.LOCKED);complete(state,"INFUSION");result(h,ResearchNetwork.processAdvance(player,"MINDBIOTHAUMIC",0),ResearchProgression.Result.STARTED);
        knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",40);knowledge(player,KnowledgeType.OBSERVATION,"ARTIFICE",20);missing(h,player,"MINDBIOTHAUMIC",1);state.discover("f_BRAIN");knowledge(player,KnowledgeType.OBSERVATION,"ARTIFICE",15);missing(h,player,"MINDBIOTHAUMIC",1);
        knowledge(player,KnowledgeType.OBSERVATION,"ARTIFICE",20);int permanent=state.permanentWarp(),normal=state.normalWarp();result(h,ResearchNetwork.processAdvance(player,"MINDBIOTHAUMIC",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==8&&state.rawKnowledge(KnowledgeType.OBSERVATION,"ARTIFICE")==4&&state.permanentWarp()==permanent+2&&state.normalWarp()==normal+1,"32+16 biothaumic payment/3 warp changed");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void materialStudiesRequireExactOriginalScanFactsAndMetallurgyStages(GameTestHelper h) {
        var player=player(h);var state=KnowledgeStore.get(player);complete(state,"MATSTUDWOOD");
        for(String key:List.of("MATSTUDIRON","MATSTUDCLAY")){result(h,ResearchNetwork.processAdvance(player,key,0),ResearchProgression.Result.STARTED);missing(h,player,key,1);state.discover(key.equals("MATSTUDIRON")?"f_MATIRON":"f_MATCLAY");result(h,ResearchNetwork.processAdvance(player,key,1),ResearchProgression.Result.COMPLETE);}
        state.setResearchStage("METALLURGY",1);result(h,ResearchNetwork.processAdvance(player,"MATSTUDBRASS",0),ResearchProgression.Result.LOCKED);state.setResearchStage("METALLURGY",2);result(h,ResearchNetwork.processAdvance(player,"MATSTUDBRASS",0),ResearchProgression.Result.STARTED);missing(h,player,"MATSTUDBRASS",1);state.discover("f_MATBRASS");result(h,ResearchNetwork.processAdvance(player,"MATSTUDBRASS",1),ResearchProgression.Result.COMPLETE);
        result(h,ResearchNetwork.processAdvance(player,"MATSTUDTHAUMIUM",0),ResearchProgression.Result.LOCKED);state.setResearchStage("METALLURGY",3);result(h,ResearchNetwork.processAdvance(player,"MATSTUDTHAUMIUM",0),ResearchProgression.Result.STARTED);missing(h,player,"MATSTUDTHAUMIUM",1);state.discover("f_MATTHAUMIUM");result(h,ResearchNetwork.processAdvance(player,"MATSTUDTHAUMIUM",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(GolemDesign.choices(GolemDesign.Category.MATERIAL,state).size()==5&&GolemDesign.choices(GolemDesign.Category.ARMS,state).stream().anyMatch(p->p.key().equals("FINE"))&&!ResearchProgression.canStart(state,"MATSTUDVOID"),"Five material/Fine arms access or closed Void parent changed");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void originalPartLessonsPayPhysicalItemsAndDoNotAcceptMissingFacts(GameTestHelper h) {
        for(String key:List.of("GOLEMBREAKER","GOLEMCOMBATADV","GOLEMCLIMBER","GOLEMVISION")){
            var player=player(h);var state=KnowledgeStore.get(player);fixtureParents(state,key);result(h,ResearchNetwork.processAdvance(player,key,0),ResearchProgression.Result.STARTED);knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",32);missing(h,player,key,1);
            switch(key){case "GOLEMBREAKER"->{player.getInventory().setItem(9,new ItemStack(Items.DIAMOND));player.getInventory().setItem(10,new ItemStack(Items.PISTON));}
                case "GOLEMCOMBATADV"->{player.getInventory().setItem(9,new ItemStack(Items.ARROW));player.getInventory().setItem(10,new ItemStack(Items.SHEARS));state.discover("!aversio");missing(h,player,key,1);state.discover("f_DISPENSER");}
                case "GOLEMCLIMBER"->{player.getInventory().setItem(9,new ItemStack(Items.FLINT));missing(h,player,key,1);state.discover("f_SPIDER");}
                case "GOLEMVISION"->{player.getInventory().setItem(9,new ItemStack(Items.FERMENTED_SPIDER_EYE));missing(h,player,key,1);state.discover("!sensus");}}
            result(h,ResearchNetwork.processAdvance(player,key,1),ResearchProgression.Result.COMPLETE);h.assertTrue(player.getInventory().getItem(9).isEmpty()&&player.getInventory().getItem(10).isEmpty()&&state.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==0,"Part lesson did not atomically debit original item costs "+key);
        }h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void logisticsRequiresCompletedProviderDirectAndPaysFilledMapEnderEye16Plus16(GameTestHelper h) {
        var player=player(h);var state=KnowledgeStore.get(player);complete(state,"MINDCLOCKWORK");result(h,ResearchNetwork.processAdvance(player,"GOLEMDIRECT",0),ResearchProgression.Result.STARTED);knowledge(player,KnowledgeType.THEORY,"GOLEMANCY",32);result(h,ResearchNetwork.processAdvance(player,"GOLEMDIRECT",1),ResearchProgression.Result.COMPLETE);
        state.setResearchStage("SEALPROVIDE",2);result(h,ResearchNetwork.processAdvance(player,"GOLEMLOGISTICS",0),ResearchProgression.Result.LOCKED);complete(state,"SEALPROVIDE");result(h,ResearchNetwork.processAdvance(player,"GOLEMLOGISTICS",0),ResearchProgression.Result.STARTED);
        knowledge(player,KnowledgeType.OBSERVATION,"BASICS",16);knowledge(player,KnowledgeType.OBSERVATION,"GOLEMANCY",16);player.getInventory().setItem(9,new ItemStack(Items.MAP));player.getInventory().setItem(10,new ItemStack(Items.ENDER_EYE));missing(h,player,"GOLEMLOGISTICS",1);
        player.getInventory().setItem(9,new ItemStack(Items.FILLED_MAP));result(h,ResearchNetwork.processAdvance(player,"GOLEMLOGISTICS",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(player.getInventory().getItem(9).isEmpty()&&player.getInventory().getItem(10).isEmpty()&&state.rawKnowledge(KnowledgeType.OBSERVATION,"BASICS")==0&&state.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==0,"Original filled map/eye or16+16 costs changed");h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"seal_progression"));BlockPos at=h.absolutePos(new BlockPos(4,1,3));p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));return p;}
    private static void complete(PlayerKnowledge state,String key){state.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1);}
    private static void fixtureParents(PlayerKnowledge state,String key){for(String parent:ResearchCatalog.get(key).parents()){String raw=parent.startsWith("~")?parent.substring(1):parent;int at=raw.indexOf('@');if(at>=0)state.setResearchStage(raw.substring(0,at),Integer.parseInt(raw.substring(at+1)));else complete(state,raw);}}
    private static void knowledge(ServerPlayer p,KnowledgeType type,String category,int value){KnowledgeStore.addKnowledge(p,type,category,value-KnowledgeStore.get(p).rawKnowledge(type,category));}
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected){h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);}
    private static void missing(GameTestHelper h,ServerPlayer p,String key,int stage){var before=KnowledgeStore.get(p).save();var inventory=new ListTag();p.getInventory().save(inventory);int xp=p.totalExperience;result(h,ResearchNetwork.processAdvance(p,key,stage),ResearchProgression.Result.MISSING_REQUIREMENTS);var after=new ListTag();p.getInventory().save(after);h.assertTrue(before.equals(KnowledgeStore.get(p).save())&&inventory.equals(after)&&xp==p.totalExperience,"Failed seal/part payment mutated state "+key);}
}
