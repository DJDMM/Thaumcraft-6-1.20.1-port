package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Released research costs and actual scanner commits; knowledge/old parent setup is an explicit fixture. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class RemainingFocusProgressionGameTests {
    @GameTest(template = "empty")
    public static void everyRemainingEntryKeepsOriginalParentsAndCurrentCanonicalScope(GameTestHelper h) {
        Map<String,List<String>> parents = Map.ofEntries(
                Map.entry("FOCUSCURSE", List.of("FOCUSFLUX", "!Pechwand")),
                Map.entry("FOCUSEXCHANGE", List.of("FOCUSFLUX")),
                Map.entry("FOCUSRIFT", List.of("FOCUSBREAK", "FOCUSEXCHANGE")),
                Map.entry("FOCUSPLAN", List.of("FOCUSEXCHANGE", "FOCUSPROJECTILE")),
                Map.entry("FOCUSMINE", List.of("FOCUSPROJECTILE")),
                Map.entry("FOCUSSPELLBAT", List.of("FOCUSMINE", "f_BAT", "!Firebat")),
                Map.entry("FOCUSCLOUD", List.of("FOCUSMINE", "!DRAGONBREATH")),
                Map.entry("FOCUSSCATTER", List.of("FOCUSBOLT", "FOCUSPROJECTILE")),
                Map.entry("FOCUSSPLIT", List.of("FOCUSSCATTER")),
                Map.entry("FOCUSADVANCED", List.of("BASEAUROMANCY", "INFUSION")),
                Map.entry("FOCUSGREATER", List.of("FOCUSADVANCED", "PRIMPEARL")));
        var p = player(h); var state = KnowledgeStore.get(p);
        for (var entry : parents.entrySet()) {
            h.assertTrue(ResearchProgression.isImplemented(entry.getKey()) && ResearchCatalog.get(entry.getKey()).parents().equals(entry.getValue()),
                    "Changed a released parent or omitted supported entry " + entry.getKey());
            result(h, ResearchNetwork.processAdvance(p, entry.getKey(), 0), ResearchProgression.Result.LOCKED);
            h.assertTrue(state.researchStage(entry.getKey()) == 0, "Missing parent opened " + entry.getKey());
        }
        h.assertTrue(ResearchCatalog.entries().stream().filter(e -> ResearchProgression.isImplemented(e.key())).count() == 51,
                "Canonical inventory must include completed clockwork mind progression");
        for (String key : List.of("FORTRESSMASK", "INFUSIONENCHANTMENT", "RUNICSHIELDING", "INFUSIONELDRITCH"))
            result(h, ResearchNetwork.processAdvance(p, key, 0), ResearchProgression.Result.UNSUPPORTED);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pechWandAndDragonBreathRequireActualScansAndPreservePhysicalItems(GameTestHelper h) {
        var p = player(h); var state = KnowledgeStore.get(p); complete(state, "FOCUSFLUX"); complete(state, "FOCUSMINE");
        ItemStack wand = CatalogModule.stack("pech_wand"); wand.setHoverName(Component.literal("Pech's proof"));
        p.getInventory().setItem(4, wand.copy()); p.getInventory().setItem(5, new ItemStack(Items.DRAGON_BREATH));
        result(h, ResearchNetwork.processAdvance(p, "FOCUSCURSE", 0), ResearchProgression.Result.LOCKED);
        result(h, ResearchNetwork.processAdvance(p, "FOCUSCLOUD", 0), ResearchProgression.Result.LOCKED);
        hoverHeld(h, p, wand); h.assertTrue(!state.knowsResearch("!Pechwand"), "Pech wand hover manufactured its proof");
        scanHeld(p, wand);
        hoverHeld(h, p, new ItemStack(Items.DRAGON_BREATH));
        h.assertTrue(!state.knowsResearch("!DRAGONBREATH"), "Dragon breath hover manufactured its proof");
        scanHeld(p, new ItemStack(Items.DRAGON_BREATH));
        h.assertTrue(state.knowsResearch("!Pechwand") && state.knowsResearch("!DRAGONBREATH")
                        && p.getInventory().getItem(4).getCount() == 1 && p.getInventory().getItem(5).getCount() == 1,
                "Actual item scan omitted proof or consumed a research sample");
        result(h, ResearchNetwork.processAdvance(p, "FOCUSCURSE", 0), ResearchProgression.Result.STARTED);
        result(h, ResearchNetwork.processAdvance(p, "FOCUSCLOUD", 0), ResearchProgression.Result.STARTED);
        h.assertTrue(state.researchStage("FOCUSCURSE") == 1 && state.researchStage("FOCUSCLOUD") == 1,
                "Scanning a parent proof completed its descendant research");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allThreePearlFormsCompleteOnlyTheirScanEntryAndAwardExactlyFiveXPOnce(GameTestHelper h) {
        for (int damage : new int[]{0,3,7}) {
            var p = player(h); var state = KnowledgeStore.get(p);
            result(h, ResearchNetwork.processAdvance(p, "PRIMPEARL", 0), ResearchProgression.Result.LOCKED);
            h.assertTrue(!ResearchProgression.canStart(state, "PRIMPEARL"), "Arbitrary empty-root book request bypassed pearl scan");
            scanHeld(p, new ItemStack(Items.ENDER_PEARL));
            h.assertTrue(!state.isResearchCompleteStrict("PRIMPEARL"), "Vanilla ender pearl impersonated primordial pearl");
            ItemStack pearl = CatalogModule.stack("primordial_pearl"); pearl.setDamageValue(damage);
            pearl.setHoverName(Component.literal("Stage " + damage)); int xp = p.totalExperience;
            hoverHeld(h, p, pearl); h.assertTrue(state.researchStage("PRIMPEARL") == 0, "Pearl hover granted the hidden entry");
            scanHeld(p, pearl);
            h.assertTrue(state.researchStage("PRIMPEARL") == 2 && state.isResearchCompleteStrict("PRIMPEARL")
                            && p.totalExperience == xp + 5 && pearl.getDamageValue() == damage && pearl.getCount() == 1,
                    "Wildcard pearl scan did not complete the released no-cost entry once");
            var before = state.save(); xp = p.totalExperience; scanHeld(p, pearl);
            h.assertTrue(before.equals(state.save()) && p.totalExperience == xp && PlayerKnowledge.load(before).isResearchCompleteStrict("PRIMPEARL"),
                    "Repeat scan paid XP twice or lost canonical completion on reload");
            h.assertTrue(state.researchStage("FOCUSGREATER") == 0 && !state.hasCraft("thaumcraft:focus_3"),
                    "Pearl scan directly granted greater-focus manufacture");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBatRayScanCannotReplaceFirebatAndFirebatCommitsBothReleasedFacts(GameTestHelper h) {
        var p = player(h); var state = KnowledgeStore.get(p); complete(state, "FOCUSMINE");
        Bat bat = new Bat(EntityType.BAT, h.getLevel()); bat.setNoAi(true); scanEntity(h, p, bat);
        h.assertTrue(state.knowsResearch("f_BAT") && state.knowsResearch("!bestia") && !state.knowsResearch("!Firebat"),
                "Vanilla bat omitted its facts or impersonated FireBat");
        result(h, ResearchNetwork.processAdvance(p, "FOCUSSPELLBAT", 0), ResearchProgression.Result.LOCKED);
        var fire = VisualEntitiesModule.LIVING.get("fire_bat").get().create(h.getLevel()); scanEntity(h, p, fire);
        h.assertTrue(state.knowsResearch("!Firebat") && state.knowsResearch("f_BAT"), "Actual FireBat scan failed its dual registration");
        result(h, ResearchNetwork.processAdvance(p, "FOCUSSPELLBAT", 0), ResearchProgression.Result.STARTED);
        var fresh = player(h); scanEntity(h, fresh, VisualEntitiesModule.LIVING.get("fire_bat").get().create(h.getLevel()));
        h.assertTrue(KnowledgeStore.get(fresh).knowsResearch("!Firebat") && KnowledgeStore.get(fresh).knowsResearch("f_BAT"),
                "Fresh FireBat scan only granted the first matching proof");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void distantRemovedAndWrongEntitySpecimensNeverBecomeFirebatEvidence(GameTestHelper h) {
        var p = player(h); position(h, p); var state = KnowledgeStore.get(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        var far = VisualEntitiesModule.LIVING.get("fire_bat").get().create(h.getLevel());
        far.setPos(p.getX(), p.getEyeY()-.125, p.getZ()+12); h.getLevel().addFreshEntity(far);
        ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(!state.knowsResearch("!Firebat"), "Out-of-range specimen bypassed server scanner geometry"); far.discard();
        h.assertTrue(!AuromancyProgressionEvents.recordScannedFact(p, far), "Removed specimen granted a scan proof");
        var pech = VisualEntitiesModule.LIVING.get("pech").get().create(h.getLevel());
        h.assertTrue(AuromancyProgressionEvents.scanFacts(pech).isEmpty(), "Pech body substituted for its wand or FireBat");
        var spectator = new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"spectator_focus")) {
            @Override public boolean isSpectator() { return true; }
        };
        h.assertTrue(!AuromancyProgressionEvents.recordScannedFact(spectator, new ItemStack(Items.DRAGON_BREATH)), "Spectator committed a research fact");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cursePaysExactTheoryAndFinalWarpAfterRealPechWandAndDeathAspectScans(GameTestHelper h) {
        var p = player(h); var state = KnowledgeStore.get(p); complete(state, "FOCUSFLUX");
        scanHeld(p, CatalogModule.stack("pech_wand"));
        result(h, ResearchNetwork.processAdvance(p, "FOCUSCURSE", 0), ResearchProgression.Result.STARTED);
        knowledge(p, KnowledgeType.THEORY, "AUROMANCY", 35); missing(h, p, "FOCUSCURSE", 1);
        scanHeld(p, new ItemStack(Items.BONE));
        h.assertTrue(state.knowsResearch("!mortuus"), "Bone scan did not discover original Mortuus");
        knowledge(p, KnowledgeType.THEORY, "AUROMANCY", 31); missing(h, p, "FOCUSCURSE", 1);
        knowledge(p, KnowledgeType.THEORY, "AUROMANCY", 35);
        result(h, ResearchNetwork.processAdvance(p, "FOCUSCURSE", 1), ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("FOCUSCURSE") == 3 && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY") == 3
                        && state.permanentWarp() == 1 && state.normalWarp() == 1 && state.temporaryWarp() == 0,
                "Curse changed32raw cost, empty conclusion or2warp split");
        replay(h, p, "FOCUSCURSE", 1); h.succeed();
    }

    @GameTest(template = "empty")
    public static void exchangeAndPlanUseActualExchangeAndCraftAspectScansAndStrictOriginalParents(GameTestHelper h) {
        var p = player(h); var state = KnowledgeStore.get(p); complete(state, "FOCUSFLUX");
        result(h, ResearchNetwork.processAdvance(p,"FOCUSEXCHANGE",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35); missing(h,p,"FOCUSEXCHANGE",1);
        scanHeld(p,new ItemStack(Items.HOPPER)); knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSEXCHANGE",1),ResearchProgression.Result.COMPLETE);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSPLAN",0),ResearchProgression.Result.LOCKED);
        state.setResearchStage("FOCUSPROJECTILE",2);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSPLAN",0),ResearchProgression.Result.LOCKED);
        complete(state,"FOCUSPROJECTILE"); result(h,ResearchNetwork.processAdvance(p,"FOCUSPLAN",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35); missing(h,p,"FOCUSPLAN",1);
        scanHeld(p,new ItemStack(Items.CRAFTING_TABLE)); knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSPLAN",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("FOCUSEXCHANGE")==3 && state.researchStage("FOCUSPLAN")==3
                        && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3,
                "Exchange/Plan changed original32raw payments or final prose skipping"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void riftPaysEldritchObservationAndFinalDepartingStageWarpAtomically(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"FOCUSBREAK"); complete(state,"FOCUSEXCHANGE");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSRIFT",0),ResearchProgression.Result.STARTED);
        h.assertTrue(state.permanentWarp()==0 && state.normalWarp()==0,"Opening Rift prematurely awarded departing-stage warp");
        scanHeld(p,new ItemStack(Items.HOPPER)); knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        knowledge(p,KnowledgeType.OBSERVATION,"ELDRITCH",15); missing(h,p,"FOCUSRIFT",1);
        knowledge(p,KnowledgeType.OBSERVATION,"ELDRITCH",19);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSRIFT",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("FOCUSRIFT")==3 && state.rawKnowledge(KnowledgeType.OBSERVATION,"ELDRITCH")==3
                        && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3 && state.permanentWarp()==1 && state.normalWarp()==1,
                "Rift failed16raw Eldritch,32raw Auromancy or audited2warp departing stage");
        replay(h,p,"FOCUSRIFT",1); h.succeed();
    }

    @GameTest(template = "empty")
    public static void mineConsumesOneMainInventoryHookAndTwoSeparateTheoryCategoriesAtomically(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"FOCUSPROJECTILE");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSMINE",0),ResearchProgression.Result.STARTED);
        scanHeld(p,new ItemStack(Items.TRIPWIRE_HOOK)); knowledge(p,KnowledgeType.THEORY,"ARTIFICE",35);
        knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35); p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TRIPWIRE_HOOK));
        missing(h,p,"FOCUSMINE",1); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        p.getInventory().setItem(7,new ItemStack(Items.TRIPWIRE_HOOK,2)); knowledge(p,KnowledgeType.THEORY,"ARTIFICE",31);
        missing(h,p,"FOCUSMINE",1); knowledge(p,KnowledgeType.THEORY,"ARTIFICE",35);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSMINE",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("FOCUSMINE")==3 && p.getInventory().getItem(7).getCount()==1
                        && state.rawKnowledge(KnowledgeType.THEORY,"ARTIFICE")==3 && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3,
                "Mine changed hook cost/main inventory scope or either32raw theory category"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void cloudConsumesOneDragonBreathAfterScanAndTwoTheoryPaymentsAreAtomic(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"FOCUSMINE"); scanHeld(p,new ItemStack(Items.DRAGON_BREATH));
        result(h,ResearchNetwork.processAdvance(p,"FOCUSCLOUD",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.THEORY,"ALCHEMY",35); knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        missing(h,p,"FOCUSCLOUD",1); p.getInventory().setItem(7,new ItemStack(Items.DRAGON_BREATH,2));
        knowledge(p,KnowledgeType.THEORY,"ALCHEMY",31); missing(h,p,"FOCUSCLOUD",1);
        knowledge(p,KnowledgeType.THEORY,"ALCHEMY",35);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSCLOUD",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("FOCUSCLOUD")==3 && p.getInventory().getItem(7).getCount()==1
                        && state.rawKnowledge(KnowledgeType.THEORY,"ALCHEMY")==3 && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3,
                "Cloud changed dragonbreath cost or either32raw theory category"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void spellBatRequiresActualSpecimenScansBothTheoryCategoriesAndFinalWarp(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"FOCUSMINE");
        scanEntity(h,p,new Bat(EntityType.BAT,h.getLevel()));
        result(h,ResearchNetwork.processAdvance(p,"FOCUSSPELLBAT",0),ResearchProgression.Result.LOCKED);
        scanEntity(h,p,VisualEntitiesModule.LIVING.get("fire_bat").get().create(h.getLevel()));
        result(h,ResearchNetwork.processAdvance(p,"FOCUSSPELLBAT",0),ResearchProgression.Result.STARTED);
        knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35); knowledge(p,KnowledgeType.THEORY,"ELDRITCH",31);
        missing(h,p,"FOCUSSPELLBAT",1); knowledge(p,KnowledgeType.THEORY,"ELDRITCH",35);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSSPELLBAT",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.researchStage("FOCUSSPELLBAT")==3 && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3
                        && state.rawKnowledge(KnowledgeType.THEORY,"ELDRITCH")==3 && state.permanentWarp()==1 && state.normalWarp()==1,
                "SpellBat changed both32raw costs or skipped final2warp"); replay(h,p,"FOCUSSPELLBAT",1); h.succeed();
    }

    @GameTest(template = "empty")
    public static void scatterAndSplitRequireFullParentsAndPay16ObservationPlus32TheoryEach(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"FOCUSBOLT"); state.setResearchStage("FOCUSPROJECTILE",2);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSSCATTER",0),ResearchProgression.Result.LOCKED);
        complete(state,"FOCUSPROJECTILE"); result(h,ResearchNetwork.processAdvance(p,"FOCUSSCATTER",0),ResearchProgression.Result.STARTED);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSSPLIT",0),ResearchProgression.Result.LOCKED);
        for(String key:List.of("FOCUSSCATTER","FOCUSSPLIT")) {
            if(key.equals("FOCUSSPLIT")) result(h,ResearchNetwork.processAdvance(p,key,0),ResearchProgression.Result.STARTED);
            knowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",15); knowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
            missing(h,p,key,1); knowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",19);
            result(h,ResearchNetwork.processAdvance(p,key,1),ResearchProgression.Result.COMPLETE);
            h.assertTrue(state.researchStage(key)==3 && state.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==3
                            && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3,"Changed exact modifier payments: "+key);
            replay(h,p,key,1);
        }
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"remaining_focus")) {
            @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
            @Override public void displayClientMessage(Component message,boolean actionBar) { }
        };
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get())); return p;
    }
    private static void position(GameTestHelper h,ServerPlayer p) {
        var start=h.absolutePos(new BlockPos(1,2,1)); p.setPos(start.getX()+.5,start.getY(),start.getZ()+.5); p.setYRot(0); p.setXRot(0);
    }
    private static void scanEntity(GameTestHelper h,ServerPlayer p,Entity entity) {
        position(h,p); p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));
        entity.setPos(p.getX(),p.getEyeY()-.125,p.getZ()+2); entity.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(entity),"Cannot insert original scan specimen");
        var before=KnowledgeStore.get(p).save(); var snapshot=ScanningNetwork.capture(p);
        h.assertTrue(snapshot.target()!=null && snapshot.target().location().entityId()==entity.getId() && before.equals(KnowledgeStore.get(p).save()),
                "Specimen hover missed server target or granted facts");
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get()); ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(AuromancyProgressionEvents.scanFacts(entity).stream().allMatch(KnowledgeStore.get(p)::isResearchKnown),"Actual scan omitted "+AuromancyProgressionEvents.scanFacts(entity)+" entity="+entity.getType()+" alive="+p.isAlive()+" state="+KnowledgeStore.get(p).save());
        entity.discard(); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void hoverHeld(GameTestHelper h,ServerPlayer p,ItemStack sample) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get())); p.setItemInHand(InteractionHand.OFF_HAND,sample); p.setShiftKeyDown(true);
        var before=KnowledgeStore.get(p).save(); ScanningNetwork.capture(p); h.assertTrue(before.equals(KnowledgeStore.get(p).save()),"Held-item hover mutated knowledge");
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void scanHeld(ServerPlayer p,ItemStack sample) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get())); p.setItemInHand(InteractionHand.OFF_HAND,sample); p.setShiftKeyDown(true);
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get()); ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void complete(PlayerKnowledge state,String key) { state.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1); }
    private static void knowledge(ServerPlayer p,KnowledgeType type,String category,int raw) {
        KnowledgeStore.addKnowledge(p,type,category,raw-KnowledgeStore.get(p).rawKnowledge(type,category));
    }
    private static void missing(GameTestHelper h,ServerPlayer p,String key,int stage) {
        var before=KnowledgeStore.get(p).save(); var inventory=new ListTag(); p.getInventory().save(inventory); int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,key,stage),ResearchProgression.Result.MISSING_REQUIREMENTS);
        var after=new ListTag(); p.getInventory().save(after);
        h.assertTrue(before.equals(KnowledgeStore.get(p).save()) && inventory.equals(after) && xp==p.totalExperience,"Missing requirement partially paid "+key);
    }
    private static void replay(GameTestHelper h,ServerPlayer p,String key,int stage) {
        var before=KnowledgeStore.get(p).save(); int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,key,stage),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(KnowledgeStore.get(p).save()) && xp==p.totalExperience && PlayerKnowledge.load(before).isResearchCompleteStrict(key),
                "Replay changed warp/resources or save lost completed "+key);
    }
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result wanted) { h.assertTrue(actual==wanted,"Expected "+wanted+", got "+actual); }
}
