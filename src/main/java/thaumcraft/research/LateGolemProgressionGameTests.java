package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.golemancy.press.GolemDesign;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;

import java.util.List;
import java.util.UUID;

/** Original late Golemancy/Artifice costs paid through the authoritative book/scanner paths. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class LateGolemProgressionGameTests {
    private LateGolemProgressionGameTests() {}

    @GameTest(template="essentia_network")
    public static void hungryChestPaysExactlyOneOriginalDictionaryChestHopperAnd16Observations(GameTestHelper h) {
        for (var item:List.of(Items.CHEST,Items.TRAPPED_CHEST,Items.ENDER_CHEST)) {
            var p=player(h);var k=KnowledgeStore.get(p);
            result(h,ResearchNetwork.processAdvance(p,"HUNGRYCHEST",0),ResearchProgression.Result.LOCKED);
            complete(k,"BASEARTIFICE");result(h,ResearchNetwork.processAdvance(p,"HUNGRYCHEST",0),ResearchProgression.Result.STARTED);
            knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",20);
            var chest=new ItemStack(item,2);chest.setHoverName(net.minecraft.network.chat.Component.literal("Paid specimen"));
            p.getInventory().setItem(9,chest);missing(h,p,"HUNGRYCHEST",1);
            p.getInventory().setItem(10,new ItemStack(Items.HOPPER,2));
            var rows=ResearchBookRequirements.rows(ResearchCatalog.get("HUNGRYCHEST").stages().get(0),k,p.getInventory());
            h.assertTrue(rows.stream().filter(r->r.kind()==ResearchBookRequirements.Kind.ITEM).count()==2
                    &&rows.stream().allMatch(ResearchBookRequirements.Row::met),"Book disagreed with physical dictionary/Hopper payment");
            knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",15);missing(h,p,"HUNGRYCHEST",1);
            knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",20);int xp=p.totalExperience;
            result(h,ResearchNetwork.processAdvance(p,"HUNGRYCHEST",1),ResearchProgression.Result.COMPLETE);
            h.assertTrue(p.getInventory().getItem(9).getCount()==1&&p.getInventory().getItem(10).getCount()==1
                    &&k.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==4&&p.totalExperience==xp+5
                    &&k.researchStage("HUNGRYCHEST")==3,"Original Hungry Chest costs/final skip changed");
            var save=k.save();result(h,ResearchNetwork.processAdvance(p,"HUNGRYCHEST",1),ResearchProgression.Result.STALE);
            h.assertTrue(save.equals(k.save())&&p.getInventory().getItem(9).getCount()==1,"Research replay consumed a second chest");
        }h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void hungryChestDoesNotUseOffhandOrAnOrdinaryContainerAsDictionaryProof(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);complete(k,"BASEARTIFICE");
        result(h,ResearchNetwork.processAdvance(p,"HUNGRYCHEST",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",16);
        p.getInventory().setItem(9,new ItemStack(Items.BARREL));p.getInventory().setItem(10,new ItemStack(Items.HOPPER));
        missing(h,p,"HUNGRYCHEST",1);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.CHEST));missing(h,p,"HUNGRYCHEST",1);
        h.assertTrue(!ResearchBookRequirements.matchesItem(new ItemStack(Items.BARREL),"oredict:chest",new ItemStack(Items.CHEST)),"Every container was treated as an original chest");h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void levitatorKeepsCompletedHungryChestEnteredMetallurgyThreeAndPaidFlightObservation(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);complete(k,"HUNGRYCHEST");k.setResearchStage("METALLURGY",2);
        result(h,ResearchNetwork.processAdvance(p,"LEVITATOR",0),ResearchProgression.Result.LOCKED);
        k.setResearchStage("METALLURGY",3);result(h,ResearchNetwork.processAdvance(p,"LEVITATOR",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",20);missing(h,p,"LEVITATOR",1);
        scanEntity(h,p,EntityType.BAT.create(h.getLevel()));
        h.assertTrue(k.isResearchCompleteStrict("f_FLY")&&k.isResearchCompleteStrict("!volatus"),"Real bat scan omitted native Flight aspect or flying-entity proof");
        knowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",15);missing(h,p,"LEVITATOR",1);
        knowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",20);int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"LEVITATOR",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("LEVITATOR")==3&&k.rawKnowledge(KnowledgeType.OBSERVATION,"ARTIFICE")==4&&p.totalExperience==xp+5,"Levitator's 16 raw Artifice observations/final skip changed");h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void flyingLegsRequireBothCompletedParentsRealFlyingScanAndExactly32Theory(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);complete(k,"GOLEMCLIMBER");k.setResearchStage("LEVITATOR",2);
        result(h,ResearchNetwork.processAdvance(p,"GOLEMFLYER",0),ResearchProgression.Result.LOCKED);
        complete(k,"LEVITATOR");result(h,ResearchNetwork.processAdvance(p,"GOLEMFLYER",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",40);missing(h,p,"GOLEMFLYER",1);
        scanEntity(h,p,EntityType.PARROT.create(h.getLevel()));knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",31);missing(h,p,"GOLEMFLYER",1);
        knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",40);result(h,ResearchNetwork.processAdvance(p,"GOLEMFLYER",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("GOLEMFLYER")==3&&k.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==8,"Flyer lost original theory cost/final chapter skip");
        complete(k,"MINDCLOCKWORK");complete(k,"MATSTUDWOOD");var design=GolemDesign.create(0,0,0,3,0).orElseThrow();
        h.assertTrue(design.canManufacture(k)&&GolemDesign.choices(GolemDesign.Category.LEGS,k).stream().anyMatch(part->part.id()==3)
                    &&!GolemDesign.create(5,0,0,3,0).orElseThrow().canManufacture(k),"Completed flying legs bypassed the independent Void gate");
        h.assertTrue(design.components().stream().anyMatch(stack->ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals("thaumcraft:levitator")),"Flyer substituted an invented levitation ingredient");
        h.assertTrue(PlayerKnowledge.load(k.save()).isResearchCompleteStrict("GOLEMFLYER"),"Completed Flyer did not survive save");h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void brainJarRequiresOriginalParentsActualBrainProofBothKnowledgeKindsAndConsumesOneBrain(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);complete(k,"BASEGOLEMANCY");complete(k,"WARDEDJARS");
        result(h,ResearchNetwork.processAdvance(p,"JARBRAIN",0),ResearchProgression.Result.LOCKED);complete(k,"INFUSION");
        result(h,ResearchNetwork.processAdvance(p,"JARBRAIN",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",20);knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",40);
        var brain=CatalogModule.stack("brain");brain.setCount(2);p.getInventory().setItem(9,brain);missing(h,p,"JARBRAIN",1);
        scanHeld(h,p,brain.copy());knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",15);missing(h,p,"JARBRAIN",1);
        knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",20);knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",31);missing(h,p,"JARBRAIN",1);
        knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",40);int permanent=k.permanentWarp(),normal=k.normalWarp(),xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"JARBRAIN",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==4&&k.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==8
                &&p.getInventory().getItem(9).getCount()==1&&k.permanentWarp()==permanent+2&&k.normalWarp()==normal+1
                &&p.totalExperience==xp+5&&k.researchStage("JARBRAIN")==3,"Original Jar Brain 16+32, one brain, 3 warp or final skip changed");
        h.assertTrue(PlayerKnowledge.load(k.save()).isResearchCompleteStrict("JARBRAIN"),"Paid Jar Brain knowledge did not survive save");h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void flyingScanRosterIsOriginalAndHoverEggsNewFlyingMobsDoNotGrantIt(GameTestHelper h) {
        for (var type:List.of(EntityType.BAT,EntityType.PARROT,EntityType.GHAST,EntityType.BLAZE)) {
            var p=player(h);scanEntity(h,p,type.create(h.getLevel()));
            h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict("f_FLY"),"Original vanilla flying entity omitted "+type);
        }
        for (String id:List.of("fire_bat","taint_swarm","wisp")) {
            var p=player(h);var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id));
            h.assertTrue(type!=null,"Missing original flying type "+id);scanEntity(h,p,type.create(h.getLevel()));
            h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict("f_FLY"),"Original TC6 flying entity omitted "+id);
        }
        for (var type:List.of(EntityType.PHANTOM,EntityType.ALLAY,EntityType.CHICKEN)) {
            var entity=type.create(h.getLevel());h.assertTrue(!GolemancyProgressionEvents.scanFacts(entity).contains("f_FLY"),"Modern/unregistered flying mob invented proof "+type);entity.discard();
        }
        h.assertTrue(!GolemancyProgressionEvents.scanFacts(new ItemStack(Items.BAT_SPAWN_EGG)).contains("f_FLY"),"Spawn egg substituted a real flying entity scan");h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"late_golem"));var at=h.absolutePos(new BlockPos(4,1,1));p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);book(p);return p;}
    private static void book(ServerPlayer p) {p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);}
    private static void complete(PlayerKnowledge k,String key) {k.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1);}
    private static void knowledge(ServerPlayer p,KnowledgeType type,String category,int raw) {KnowledgeStore.addKnowledge(p,type,category,raw-KnowledgeStore.get(p).rawKnowledge(type,category));}
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected) {h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);}
    private static void missing(GameTestHelper h,ServerPlayer p,String key,int stage) {var before=KnowledgeStore.get(p).save();var inv=new ListTag();p.getInventory().save(inv);int xp=p.totalExperience;result(h,ResearchNetwork.processAdvance(p,key,stage),ResearchProgression.Result.MISSING_REQUIREMENTS);var after=new ListTag();p.getInventory().save(after);h.assertTrue(before.equals(KnowledgeStore.get(p).save())&&inv.equals(after)&&xp==p.totalExperience,"Failed late-device payment mutated knowledge/items/XP "+key);}
    private static void scanHeld(GameTestHelper h,ServerPlayer p,ItemStack specimen) {p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));p.setItemInHand(InteractionHand.OFF_HAND,specimen);p.setShiftKeyDown(true);use(p);h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict("f_BRAIN"),"Actual held scan did not record brain proof");book(p);}
    private static void scanEntity(GameTestHelper h,ServerPlayer p,Entity entity) {
        h.assertTrue(entity!=null,"Missing scan entity");entity.setPos(p.getX(),p.getY(),p.getZ()+3);entity.setNoGravity(true);if(entity instanceof Mob mob)mob.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(entity),"Cannot spawn scan entity");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));
        var offset=entity.getBoundingBox().getCenter().subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(offset.x,offset.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(offset.y,Math.sqrt(offset.x*offset.x+offset.z*offset.z))));
        var before=KnowledgeStore.get(p).save();var snapshot=ScanningNetwork.capture(p);h.assertTrue(snapshot.target()!=null&&snapshot.target().location().entityId()==entity.getId()&&before.equals(KnowledgeStore.get(p).save()),"Hover acquired flying knowledge or missed the real entity");use(p);entity.discard();book(p);
    }
    private static void use(ServerPlayer p) {p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);}
}
