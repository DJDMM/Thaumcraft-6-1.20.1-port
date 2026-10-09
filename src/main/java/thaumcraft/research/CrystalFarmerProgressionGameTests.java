package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.infusion.InfusionMatrixBlockEntity;
import thaumcraft.infusion.InfusionModule;
import thaumcraft.infusion.InfusionPedestalBlockEntity;
import thaumcraft.infusion.InfusionRecipe;
import thaumcraft.infusion.InfusionRecipes;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;
import thaumcraft.scanning.ThaumometerItem;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.UUID;

/** Real scanner commits, native research payments and actual arcane/infusion outputs. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CrystalFarmerProgressionGameTests {
    private static final Aspect[] PRIMALS = {Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY};
    private static final String[] SUFFIXES = {"air", "fire", "water", "earth", "order", "entropy", "flux"};
    private static final Aspect[] ALL = {Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY, Aspect.FLUX};
    private CrystalFarmerProgressionGameTests() {}

    @GameTest(template="essentia_network")
    public static void actualNineBlockFamiliesCompleteHiddenOreOnceAndOnlyTheirOwnAddenda(GameTestHelper h) {
        for (String id : List.of("ore_amber", "ore_cinnabar", "crystal_aer", "crystal_ignis", "crystal_aqua",
                "crystal_terra", "crystal_ordo", "crystal_perditio", "crystal_vitium")) {
            var p=player(h); var k=KnowledgeStore.get(p); int xp=p.totalExperience;
            BlockState state=block(id).defaultBlockState();
            // The growth branch supplies real supporting stone. Reading/using the scanner leaves that block intact.
            BlockPos at=h.absolutePos(new BlockPos(4,3,5)); h.getLevel().setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(at,state); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get())); aim(p,Vec3.atCenterOf(at));
            CompoundTag before=k.save(); var hover=ScanningNetwork.capture(p);
            h.assertTrue(hover.target()!=null && !hover.target().scanned() && before.equals(k.save()),"HUD missed or acquired unscanned ore "+id);
            use(p); String fact=id.equals("ore_amber")?"!OREAMBER":id.equals("ore_cinnabar")?"!ORECINNABAR":"!ORECRYSTAL";
            h.assertTrue(k.isResearchCompleteStrict("ORE") && k.researchStage("ORE")==2 && k.isResearchCompleteStrict(fact)
                    && p.totalExperience==xp+5 && h.getLevel().getBlockState(at).equals(state),"Real block scan did not preserve original ORE/addendum/5XP "+id);
            for(String other:List.of("!OREAMBER","!ORECINNABAR","!ORECRYSTAL"))h.assertTrue(k.isResearchCompleteStrict(other)==other.equals(fact),"Ore families leaked an unrelated addendum "+id);
            before=k.save(); use(p); h.assertTrue(before.equals(k.save()) && p.totalExperience==xp+5,"Duplicate physical scan rewarded ORE twice");
            var loaded=PlayerKnowledge.load(before);h.assertTrue(loaded.isResearchCompleteStrict("ORE") && loaded.isResearchCompleteStrict(fact),"Ore scan failed persistence");
            h.assertTrue(k.researchStage("CRYSTALFARMER")==0 && k.researchStage("VISBATTERY")==0,"Ore scan granted paid farming/battery studies");
            h.getLevel().setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        }h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void scanBlockConstructorItemAndDroppedItemPathsRetainStrictNegativeFamilies(GameTestHelper h) {
        for(String id:List.of("ore_amber","ore_cinnabar","crystal_aer","crystal_ignis","crystal_aqua","crystal_terra","crystal_ordo","crystal_perditio","crystal_vitium")) {
            var p=player(h); held(h,p,new ItemStack(block(id)));h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict("ORE") && p.totalExperience==5,"ScanBlock's constructor item registration missing "+id);
        }
        var dropped=player(h);var entity=new ItemEntity(h.getLevel(),dropped.getX(),dropped.getY()+1,dropped.getZ()+3,new ItemStack(block("crystal_vitium")));
        entity.setNoGravity(true);entity.setDeltaMovement(Vec3.ZERO);h.assertTrue(h.getLevel().addFreshEntity(entity),"Cannot insert actual dropped crystal specimen");
        dropped.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));aim(dropped,entity.getBoundingBox().getCenter());
        var before=KnowledgeStore.get(dropped).save();var hover=ScanningNetwork.capture(dropped);
        h.assertTrue(hover.target()!=null && hover.target().location().entityId()==entity.getId() && before.equals(KnowledgeStore.get(dropped).save()),"Dropped-item HUD granted/missed fact");
        use(dropped);h.assertTrue(KnowledgeStore.get(dropped).isResearchCompleteStrict("ORE") && KnowledgeStore.get(dropped).isResearchCompleteStrict("!ORECRYSTAL") && entity.getItem().getCount()==1,"Actual dropped crystal scan consumed specimen or missed original proof");entity.discard();
        for(var specimen:List.of(new ItemStack(WorldModule.ORE_QUARTZ.get()),new ItemStack(Items.IRON_ORE),new ItemStack(Items.AMETHYST_CLUSTER),
                AspectCrystalItem.create(Aspect.AIR),new ItemStack(WorldModule.VIS_CRYSTALS.get("aer").get()),item("amber"),item("quicksilver"))) {
            var p=player(h);held(h,p,specimen);var k=KnowledgeStore.get(p);
            for(String fact:List.of("ORE","!OREAMBER","!ORECINNABAR","!ORECRYSTAL"))h.assertTrue(!k.isResearchKnown(fact),"Unrelated specimen substituted for ore block scan: "+specimen+" -> "+fact);
        }h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void hiddenOreCannotBeStartedByEmptyBookRequestAndFarmingNeedsCompletedInfusion(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);var ore=ResearchCatalog.get("ORE");
        h.assertTrue(ore.hasMeta("HIDDEN") && ore.parents().isEmpty() && ore.siblings().isEmpty() && ore.stages().size()==1
                && ResearchCatalog.entries().stream().filter(e->ResearchProgression.isImplemented(e.key())).count()==82,"Original hidden ORE/no-sibling graph or canonical census changed");
        result(h,ResearchNetwork.processAdvance(p,"ORE",0),ResearchProgression.Result.LOCKED);
        h.assertTrue(k.researchStage("ORE")==0 && p.totalExperience==0,"Forged root request granted canonical ore research");
        var farmer=ResearchCatalog.get("CRYSTALFARMER");h.assertTrue(farmer.parents().equals(List.of("ORE","INFUSION","!ORECRYSTAL")) && farmer.siblings().isEmpty() && farmer.stages().size()==2,"Original Farmer parents/stages changed");
        held(h,p,new ItemStack(block("ore_amber")));book(p);complete(k,"INFUSION");result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",0),ResearchProgression.Result.LOCKED);
        held(h,p,new ItemStack(block("crystal_aer")));book(p);k.setResearchStage("INFUSION",ResearchCatalog.get("INFUSION").stages().size());
        result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",0),ResearchProgression.Result.LOCKED);complete(k,"INFUSION");int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",0),ResearchProgression.Result.STARTED);h.assertTrue(k.researchStage("CRYSTALFARMER")==1 && p.totalExperience==xp+5,"Starting Farmer skipped its actual payment");
        result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",0),ResearchProgression.Result.LOCKED);
        h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void farmingPaysExactSixNbtCrystalsAnd16Plus16ObservationsAtomically(GameTestHelper h) {
        var p=farmer(h);var k=KnowledgeStore.get(p);KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",19);KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",15);
        for(int i=0;i<6;i++)p.getInventory().setItem(9+i,AspectCrystalItem.create(PRIMALS[i],2));
        unchangedRejection(h,p,"CRYSTALFARMER");KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",4);
        // Multi-aspect crystals, wrong contained amounts and offhand ownership do not satisfy original main-inventory obtain payment.
        var invalid=AspectCrystalItem.create(Aspect.AIR,2);new thaumcraft.api.aspects.AspectList().add(Aspect.AIR,1).add(Aspect.WATER,1).writeToNBT(invalid.getOrCreateTag());
        p.getInventory().setItem(9,invalid);unchangedRejection(h,p,"CRYSTALFARMER");
        invalid=AspectCrystalItem.create(Aspect.AIR,2);new thaumcraft.api.aspects.AspectList().add(Aspect.AIR,2).writeToNBT(invalid.getOrCreateTag());
        p.getInventory().setItem(9,invalid);unchangedRejection(h,p,"CRYSTALFARMER");
        p.getInventory().setItem(9,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,AspectCrystalItem.create(Aspect.AIR,2));unchangedRejection(h,p,"CRYSTALFARMER");
        invalid=AspectCrystalItem.create(Aspect.AIR,2);invalid.getOrCreateTag().putString("Extra","unexpected root key");
        p.getInventory().setItem(9,invalid);unchangedRejection(h,p,"CRYSTALFARMER");
        p.getInventory().setItem(9,AspectCrystalItem.create(Aspect.AIR,2));
        var e=ResearchCatalog.get("CRYSTALFARMER");h.assertTrue(ResearchBookRequirements.rows(e.stages().get(0),k,p.getInventory()).stream().allMatch(ResearchBookRequirements.Row::met),"Book disagrees with exact paid main inventory/NBT requirements");int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("CRYSTALFARMER")==3 && k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==3
                && k.rawKnowledge(KnowledgeType.OBSERVATION,"BASICS")==3 && p.totalExperience==xp+5 && p.getOffhandItem().getCount()==2
                && k.permanentWarp()==0 && k.normalWarp()==0,"Original Farmer16+16 debit/stage3/5XP/warp changed");
        for(int i=0;i<6;i++)h.assertTrue(p.getInventory().getItem(9+i).getCount()==1 && AspectCrystalItem.crystalAspect(p.getInventory().getItem(9+i))==PRIMALS[i],"Wrong crystal payment index "+i);
        var before=k.save();var items=inventory(p);result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",1),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(k.save()) && items.equals(inventory(p)) && p.totalExperience==xp+5 && PlayerKnowledge.load(before).isResearchCompleteStrict("CRYSTALFARMER"),"Replay/reload paid Farmer twice");noLate(h,k);h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void earlierPortPrimalIdsNormalizeForTheSamePaidCanonicalObtain(GameTestHelper h) {
        var p=farmer(h);var k=KnowledgeStore.get(p);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",19);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",19);
        for(int i=0;i<6;i++)p.getInventory().setItem(9+i,new ItemStack(WorldModule.VIS_CRYSTALS.get(PRIMALS[i].getTag()).get(),2));
        int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("CRYSTALFARMER")==3 && k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==3
                && k.rawKnowledge(KnowledgeType.OBSERVATION,"BASICS")==3 && p.totalExperience==xp+5,
                "Earlier-port primal normalization changed exact paid research costs");
        for(int i=0;i<6;i++)h.assertTrue(p.getInventory().getItem(9+i).is(WorldModule.VIS_CRYSTALS.get(PRIMALS[i].getTag()).get())
                && p.getInventory().getItem(9+i).getCount()==1,"Legacy primal payment failed index "+i);
        var before=k.save();var items=inventory(p);
        result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",1),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(k.save()) && items.equals(inventory(p)) && p.totalExperience==xp+5,
                "Legacy payment replay altered knowledge or inventory");
        noLate(h,k);h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void batteryRequiresBothCompletedStudiesAndPaysOnlyOriginalKnowledge(GameTestHelper h) {
        var p=farmer(h);var k=KnowledgeStore.get(p);var e=ResearchCatalog.get("VISBATTERY");
        h.assertTrue(e.parents().equals(List.of("RECHARGEPEDESTAL","CRYSTALFARMER")) && e.stages().size()==2 && e.siblings().isEmpty(),"Original battery graph changed");
        complete(k,"RECHARGEPEDESTAL");result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",0),ResearchProgression.Result.LOCKED);
        complete(k,"CRYSTALFARMER");k.setResearchStage("RECHARGEPEDESTAL",ResearchCatalog.get("RECHARGEPEDESTAL").stages().size());result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",0),ResearchProgression.Result.LOCKED);
        complete(k,"RECHARGEPEDESTAL");int xp=p.totalExperience;result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",0),ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",18);KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",15);unchangedRejection(h,p,"VISBATTERY");KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",3);
        var beforeItems=inventory(p);result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("VISBATTERY")==3 && k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==2 && k.rawKnowledge(KnowledgeType.OBSERVATION,"ARTIFICE")==2
                && beforeItems.equals(inventory(p)) && p.totalExperience==xp+10 && k.permanentWarp()==0 && k.normalWarp()==0,"Original battery16+16/no-item debit/10XP changed");
        var before=k.save();result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",1),ResearchProgression.Result.STALE);h.assertTrue(before.equals(k.save()) && p.totalExperience==xp+10 && PlayerKnowledge.load(before).isResearchCompleteStrict("VISBATTERY"),"Battery replay/reload changed payment");noLate(h,k);h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void nativeBatteryBenchCraftAtStartedGatePays50VisAndTwoOfAllSixCrystals(GameTestHelper h) {
        BlockPos at=h.absolutePos(new BlockPos(4,2,4));h.getLevel().setBlockAndUpdate(at,ArcaneModule.WORKBENCH.get().defaultBlockState());var b=(ArcaneWorkbenchBlockEntity)h.getLevel().getBlockEntity(at);
        var p=player(h);var k=KnowledgeStore.get(p);ArcaneRecipe r=h.getLevel().getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream().filter(recipe->recipe.getResultItem(h.getLevel().registryAccess()).is(CatalogBlocks.block("vis_battery").asItem())).findFirst().orElseThrow();
        h.assertTrue(r.vis()==50 && r.research().equals("VISBATTERY") && r.gridWidth()==3 && r.gridHeight()==3 && r.getIngredients().size()==9,"Original battery50vis/pattern/research changed");
        for(int i=0;i<9;i++){ItemStack wanted=item(i==4?"vis_resonator":"slab_arcane_stone");h.assertTrue(r.getIngredients().get(i).test(wanted),"Original eight slabs/single resonator changed");b.setItem(i,wanted.copyWithCount(2));}
        for(int i=0;i<6;i++){h.assertTrue(r.crystalCost(i)==2,"Original battery crystal amount changed "+i);b.setItem(9+i,AspectCrystalItem.create(PRIMALS[i],3));}
        AuraManager.drainVis(h.getLevel(),at,Float.MAX_VALUE,false);AuraManager.addVis(h.getLevel(),at,52);var before=b.saveWithoutMetadata();
        h.assertTrue(b.findRecipe(p)==null && b.craft(p).isEmpty() && before.equals(b.saveWithoutMetadata()),"Battery without research could craft");
        complete(k,"RECHARGEPEDESTAL");complete(k,"CRYSTALFARMER");result(h,ResearchNetwork.processAdvance(p,"VISBATTERY",0),ResearchProgression.Result.STARTED);
        h.assertTrue(b.findRecipe(p)==r && k.researchStage("VISBATTERY")==1,"Original bare gate gained an invented completion requirement");
        AuraManager.drainVis(h.getLevel(),at,3,false);before=b.saveWithoutMetadata();h.assertTrue(b.craft(p).isEmpty() && before.equals(b.saveWithoutMetadata()) && AuraManager.getVis(h.getLevel(),at)==49,"49vis rejection partially paid");AuraManager.addVis(h.getLevel(),at,3);
        b.setItem(14,AspectCrystalItem.create(Aspect.ENTROPY));before=b.saveWithoutMetadata();h.assertTrue(b.craft(p).isEmpty() && before.equals(b.saveWithoutMetadata()) && AuraManager.getVis(h.getLevel(),at)==52,"Single entropy crystal rejection partially paid");b.setItem(14,AspectCrystalItem.create(Aspect.ENTROPY,3));
        var output=b.craft(p);h.assertTrue(output.is(CatalogBlocks.block("vis_battery").asItem()) && output.getCount()==1 && !output.hasTag() && AuraManager.getVis(h.getLevel(),at)==2,"Actual battery output/aura debit changed");
        for(int i=0;i<15;i++)h.assertTrue(b.getItem(i).getCount()==1,"Exact component or crystal debit failed slot "+i);before=b.saveWithoutMetadata();h.assertTrue(b.craft(p).isEmpty() && before.equals(b.saveWithoutMetadata()),"Repeat craft bypassed insufficient crystals/vis");h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void sevenOriginalClusterPlansRetainExactPaymentOutputsAndBareStartedGate(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);var components=List.of(new ItemStack(Items.WHEAT_SEEDS),new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));
        for(int i=0;i<ALL.length;i++) {
            var r=cluster(h,i);var a=ALL[i];var input=AspectCrystalItem.create(a);
            h.assertTrue(r.research().equals("CRYSTALFARMER") && r.instability()==(a==Aspect.FLUX?4:0) && r.aspects().size()==3 && r.aspects().getAmount(a)==10
                    && r.aspects().getAmount(Aspect.CRYSTAL)==10 && r.aspects().getAmount(Aspect.TRAP)==5 && r.matchesItems(input,components),"Pinned infusion cost/ingredients changed "+SUFFIXES[i]);
            h.assertTrue(r.plan(k,input,components,p.getRandom()).isEmpty(),"Unstarted Farmer created cluster plan");
        }
        held(h,p,new ItemStack(block("crystal_aer")));book(p);complete(k,"INFUSION");result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",0),ResearchProgression.Result.STARTED);
        for(int i=0;i<ALL.length;i++) {
            var r=cluster(h,i);var input=AspectCrystalItem.create(ALL[i]);var plan=r.plan(k,input,components,p.getRandom()).orElseThrow();
            h.assertTrue(plan.output().is(block("crystal_"+ALL[i].getTag()).asItem()) && plan.output().getCount()==1 && !plan.output().hasTag() && !plan.preserveInput(),"Pinned cluster output changed "+SUFFIXES[i]);
            h.assertTrue(r.plan(k,AspectCrystalItem.create(Aspect.MAGIC),components,p.getRandom()).isEmpty() && r.plan(k,input,List.of(new ItemStack(Items.WHEAT),new ItemStack(AlchemyModule.SALIS_MUNDUS.get())),p.getRandom()).isEmpty(),"Foreign central crystal/component matched "+SUFFIXES[i]);
        }h.assertTrue(k.researchStage("CRYSTALFARMER")==1,"Bare original recipe gate silently completed Farmer");h.succeed();
    }

    @GameTest(template="essentia_production")
    public static void paidFarmerInfusesAllSevenNativeClusterOutputsWithoutDuplicates(GameTestHelper h) {
        var p=farmer(h);var k=KnowledgeStore.get(p);KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",16);KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"BASICS",16);for(int i=0;i<6;i++)p.getInventory().setItem(9+i,AspectCrystalItem.create(PRIMALS[i]));
        result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",1),ResearchProgression.Result.COMPLETE);
        BlockPos pos=h.absolutePos(new BlockPos(6,8,6));var level=h.getLevel();
        for(int i=0;i<ALL.length;i++) {
            for(BlockPos at:BlockPos.betweenClosed(pos.offset(-8,-7,-8),pos.offset(8,3,8)))level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos,CatalogBlocks.block("infusion_matrix").defaultBlockState());level.setBlockAndUpdate(pos.below(2),CatalogBlocks.block("pedestal_arcane").defaultBlockState());
            for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})level.setBlockAndUpdate(pos.offset(x,-2,z),CatalogBlocks.block("pillar_arcane").defaultBlockState());
            var matrix=(InfusionMatrixBlockEntity)level.getBlockEntity(pos);var tag=matrix.saveWithoutMetadata();tag.putFloat("stability",25);matrix.load(tag);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3.5);
            var center=(InfusionPedestalBlockEntity)level.getBlockEntity(pos.below(2));center.setItem(0,AspectCrystalItem.create(ALL[i]));
            var componentPositions=List.of(pos.offset(3,-2,0),pos.offset(-3,-2,0));var components=List.of(new ItemStack(Items.WHEAT_SEEDS),new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));
            for(int index=0;index<2;index++){level.setBlockAndUpdate(componentPositions.get(index),CatalogBlocks.block("pedestal_arcane").defaultBlockState());((InfusionPedestalBlockEntity)level.getBlockEntity(componentPositions.get(index))).setItem(0,components.get(index));}
            // Sixteen real color pairs give gain1.6, keeping the original Flux instability4 safe
            // without removing that instability or changing the original raw essentia cost.
            String[] colors={"white","orange","magenta","lightblue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black"};
            for(int ci=0;ci<16;ci++)for(int sign:new int[]{-1,1}){BlockPos cp=pos.offset(sign*(5+ci%4),-2,sign*(3+ci/4));level.setBlockAndUpdate(cp.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(cp,CatalogBlocks.block("candle_"+colors[ci]).defaultBlockState());}
            var costs=cluster(h,i).aspects();var jars=new java.util.ArrayList<EssentiaJarBlockEntity>();int ji=0;
            for(Aspect a:costs.getAspects()){BlockPos jp=pos.offset(ji++-3,-2,5);level.setBlockAndUpdate(jp,CatalogBlocks.block("jar_normal").defaultBlockState());var jar=(EssentiaJarBlockEntity)level.getBlockEntity(jp);h.assertTrue(jar.addExact(a,costs.getAmount(a)+7),"Cannot supply raw essentia fixture");jars.add(jar);}
            h.assertTrue(matrix.useCaster(p) && matrix.active() && !matrix.crafting() && matrix.useCaster(p) && matrix.crafting(),"Paid Farmer did not start actual cluster infusion "+SUFFIXES[i]);
            for(int tick=0;tick<500;tick++)InfusionMatrixBlockEntity.tick(level,pos,matrix.getBlockState(),matrix);
            h.assertTrue(!matrix.crafting() && center.getItem(0).is(block("crystal_"+ALL[i].getTag()).asItem()) && center.getItem(0).getCount()==1
                    && jars.stream().allMatch(jar->jar.amount()==7) && componentPositions.stream().allMatch(at->((InfusionPedestalBlockEntity)level.getBlockEntity(at)).getItem(0).isEmpty()),"Native seven-cluster output or actual exact payment failed "+SUFFIXES[i]);
            var before=center.saveWithoutMetadata();for(int tick=0;tick<100;tick++)InfusionMatrixBlockEntity.tick(level,pos,matrix.getBlockState(),matrix);h.assertTrue(before.equals(center.saveWithoutMetadata()) && jars.stream().allMatch(jar->jar.amount()==7),"Finished cluster repeated output or payment");
        }h.succeed();
    }

    @GameTest(template="empty")
    public static void bookRecipesRemainReadOnlyAndExposeAllAuditedClusterAndBatteryPrices(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);var before=k.save();
        var battery=BookRecipeCatalog.definitions("thaumcraft:VisBattery");h.assertTrue(battery.size()==1 && battery.get(0).vis()==50 && battery.get(0).research().equals("VISBATTERY") && battery.get(0).output().is(CatalogBlocks.block("vis_battery").asItem()),"Original battery book price/output missing");
        for(int c:battery.get(0).crystals())h.assertTrue(c==2,"Original book12crystal price changed");
        for(int i=0;i<ALL.length;i++){String suffix=SUFFIXES[i].substring(0,1).toUpperCase(java.util.Locale.ROOT)+SUFFIXES[i].substring(1);var rows=BookRecipeCatalog.definitions("thaumcraft:CrystalCluster"+suffix);h.assertTrue(rows.size()==1 && rows.get(0).research().equals("CRYSTALFARMER") && rows.get(0).output().is(block("crystal_"+ALL[i].getTag()).asItem()),"Original cluster book page absent "+suffix);}
        h.assertTrue(before.equals(k.save()) && p.totalExperience==0,"Read-only recipe archive granted studies");h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void ordinaryNetherWartAndQuartzProduceTheExactFluxCentralCrystal(GameTestHelper h) {
        var p=player(h);complete(KnowledgeStore.get(p),"BASEALCHEMY");
        h.assertTrue(thaumcraft.scanning.AspectRegistry.getAspects(new ItemStack(Items.GLASS)).getAmount(Aspect.CRYSTAL)==5
                && thaumcraft.scanning.AspectRegistry.getAspects(new ItemStack(Items.OAK_DOOR)).getAmount(Aspect.TRAP)==5,
                "Ordinary glass/wooden door no longer supply cluster Vitreus/Vinculum");
        // Only ordinary raw resources and preceding research are fixtures; no Vitium or finished crystal is injected.
        var grid=new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null,0){
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player ignored,int slot){return ItemStack.EMPTY;}
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player ignored){return true;}
        },3,3);grid.setItem(0,new ItemStack(Items.QUARTZ));
        var quartz=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow();
        ItemStack slivers=quartz.assemble(grid,h.getLevel().registryAccess());h.assertTrue(slivers.is(item("nugget_quartz").getItem()) && slivers.getCount()==9,"Ordinary quartz no longer supplies nine original slivers");grid.removeItem(0,1);h.assertTrue(grid.getItem(0).isEmpty(),"Quartz input fixture was not paid");
        BlockPos pos=h.absolutePos(new BlockPos(4,2,4));h.getLevel().setBlockAndUpdate(pos.below(),Blocks.MAGMA_BLOCK.defaultBlockState());h.getLevel().setBlockAndUpdate(pos,AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var c=(thaumcraft.alchemy.CrucibleBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(c.fillWater(),"Real crucible water fill failed");
        for(int i=0;i<180;i++)thaumcraft.alchemy.CrucibleBlockEntity.tick(h.getLevel(),pos,c.getBlockState(),c);
        var wart=new ItemStack(Items.NETHER_WART);h.assertTrue(c.consume(wart,p) && wart.isEmpty() && c.aspects().getAmount(Aspect.FLUX)==2 && c.water()==1000,"Actual Nether Wart dissolution did not provide its ordinary two Vitium");
        h.assertTrue(c.consume(slivers,p) && c.consume(slivers,p) && slivers.getCount()==7 && c.water()==900,"Mixed original wart aspects did not pay two actual crystallizations");
        // Equal-cost recipe selection first crystallizes Alkimia; the next real sliver obtains Vitium.
        var outputs=h.getLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2),e->e.getPersistentData().getBoolean("thaumcraft_crucible_output"));
        var flux=outputs.stream().filter(e->AspectCrystalItem.crystalAspect(e.getItem())==Aspect.FLUX).toList();
        h.assertTrue(outputs.size()==2 && flux.size()==1 && flux.get(0).getItem().getCount()==1 && c.aspects().getAmount(Aspect.FLUX)==0
                && cluster(h,6).matchesItems(flux.get(0).getItem(),List.of(new ItemStack(Items.WHEAT_SEEDS),new ItemStack(AlchemyModule.SALIS_MUNDUS.get())))
                && KnowledgeStore.get(p).hasCraft("thaumcraft:crystal_essence"),"Ordinary crafted Vitium central crystal failed exact flux cluster recipe/proof");
        outputs.forEach(ItemEntity::discard);noLate(h,KnowledgeStore.get(p));h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"crystal_study"));var at=h.absolutePos(new BlockPos(4,2,2));p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);p.setYRot(0);p.setXRot(0);book(p);return p;}
    private static ServerPlayer farmer(GameTestHelper h) {var p=player(h);held(h,p,new ItemStack(block("crystal_aer")));book(p);var k=KnowledgeStore.get(p);complete(k,"INFUSION");result(h,ResearchNetwork.processAdvance(p,"CRYSTALFARMER",0),ResearchProgression.Result.STARTED);for(String category:ResearchCategories.keys()){int raw=k.rawKnowledge(KnowledgeType.OBSERVATION,category);if(raw>0)KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,category,-raw);}return p;}
    private static net.minecraft.world.level.block.Block block(String id) {return id.equals("ore_amber")?WorldModule.ORE_AMBER.get():id.equals("ore_cinnabar")?WorldModule.ORE_CINNABAR.get():CatalogBlocks.block(id);}
    private static ItemStack item(String id) {return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft",id)));}
    private static InfusionRecipe cluster(GameTestHelper h,int i) {return (InfusionRecipe)h.getLevel().getRecipeManager().byKey(InfusionModule.id("infusion/crystalcluster"+SUFFIXES[i])).orElseThrow();}
    private static void book(ServerPlayer p) {p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));}
    private static void held(GameTestHelper h,ServerPlayer p,ItemStack specimen) {var before=specimen.copy();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));p.setItemInHand(InteractionHand.OFF_HAND,specimen);p.setShiftKeyDown(true);var state=KnowledgeStore.get(p).save();ScanningNetwork.capture(p);h.assertTrue(state.equals(KnowledgeStore.get(p).save()),"Held hover granted scan fact");use(p);h.assertTrue(ItemStack.matches(before,specimen),"Held scan changed specimen");p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);book(p);}
    private static void use(ServerPlayer p) {p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);}
    private static void aim(ServerPlayer p,Vec3 target) {Vec3 d=target.subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private static void complete(PlayerKnowledge k,String key) {k.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1);}
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected) {h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);}
    private static ListTag inventory(ServerPlayer p) {var tag=new ListTag();p.getInventory().save(tag);return tag;}
    private static void unchangedRejection(GameTestHelper h,ServerPlayer p,String key) {var k=KnowledgeStore.get(p);var before=k.save();var items=inventory(p);int xp=p.totalExperience;result(h,ResearchNetwork.processAdvance(p,key,1),ResearchProgression.Result.MISSING_REQUIREMENTS);h.assertTrue(before.equals(k.save()) && items.equals(inventory(p)) && xp==p.totalExperience,"Failed obtain/knowledge request partially paid "+key);}
    private static void noLate(GameTestHelper h,PlayerKnowledge k) {for(String key:List.of("FLUX","FLUXRIFT","RIFTCLOSER","BASEELDRITCH","MATSTUDVOID","MIRRORESSENTIA"))h.assertTrue(!ResearchProgression.supportsProgression(key) && k.researchStage(key)==0,"Farmer/battery unlocked unfinished late path "+key);}
}
