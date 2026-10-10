package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.*;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.aura.AuraManager;
import java.util.List;
import java.util.UUID;

/** Actual authoritative book payments and physical bench crafting, without supplied outputs. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class EssentiaTransfuserProgressionGameTests {
    private static final BlockPos BENCH = new BlockPos(4,2,4);
    private EssentiaTransfuserProgressionGameTests() {}
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"transfuser_qa"));
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5,3,4.5)));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));return p;
    }
    private static void complete(PlayerKnowledge k,String key) { k.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1); }
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected) {
        h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);
    }
    @GameTest(template="empty")
    public static void transportResearchPaysBothCategoriesOnceAndPreservesLateGates(GameTestHelper h) {
        var p=player(h);var k=KnowledgeStore.get(p);var e=ResearchCatalog.get("ESSENTIATRANSPORT");
        h.assertTrue(e.parents().equals(List.of("THAUMATORIUM","INFUSION"))&&e.stages().size()==2
                &&ResearchCatalog.entries().stream().filter(entry->ResearchProgression.isImplemented(entry.key())).count()==85,"Original parents/stages or canonical census changed");
        result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",0),ResearchProgression.Result.LOCKED);
        complete(k,"THAUMATORIUM");k.setResearchStage("INFUSION",ResearchCatalog.get("INFUSION").stages().size());
        result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",0),ResearchProgression.Result.LOCKED);
        complete(k,"INFUSION");result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",0),ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"ALCHEMY",19);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",15);
        var before=k.save();var inventory=new ListTag();p.getInventory().save(inventory);int xp=p.totalExperience;
        result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",1),ResearchProgression.Result.MISSING_REQUIREMENTS);
        var after=new ListTag();p.getInventory().save(after);
        h.assertTrue(before.equals(k.save())&&inventory.equals(after)&&xp==p.totalExperience,"Partial observation rejection paid research");
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",4);
        h.assertTrue(ResearchBookRequirements.rows(e.stages().get(0),k,p.getInventory()).stream().allMatch(ResearchBookRequirements.Row::met),"Book does not agree with original16+16 payment");
        result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",1),ResearchProgression.Result.COMPLETE);
        h.assertTrue(k.researchStage("ESSENTIATRANSPORT")==3&&k.rawKnowledge(KnowledgeType.OBSERVATION,"ALCHEMY")==3
                &&k.rawKnowledge(KnowledgeType.OBSERVATION,"ARTIFICE")==3&&p.totalExperience==xp+5&&k.permanentWarp()==0&&k.normalWarp()==0,"Original two-stage debit/XP/warp changed");
        before=k.save();result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",1),ResearchProgression.Result.STALE);
        h.assertTrue(before.equals(k.save())&&p.totalExperience==xp+5&&PlayerKnowledge.load(before).isResearchCompleteStrict("ESSENTIATRANSPORT"),"Replay or reload changed payment/completion");
        for(String key:List.of("BASEELDRITCH","MATSTUDVOID","MIRRORESSENTIA"))h.assertTrue(!ResearchProgression.supportsProgression(key),"Transport unlocked unrelated late research "+key);
        h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void bothOriginalTransfusersCraftAtStartedStageFor100VisAndAirWater(GameTestHelper h) {
        for(String id:List.of("essentia_input","essentia_output")) {
            h.setBlock(BENCH,ArcaneModule.WORKBENCH.get());var b=(ArcaneWorkbenchBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(BENCH));b.clearContent();
            var p=player(h);var k=KnowledgeStore.get(p);
            ArcaneRecipe recipe=h.getLevel().getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream()
                    .filter(r->r.getResultItem(h.getLevel().registryAccess()).is(CatalogBlocks.block(id).asItem())).findFirst().orElseThrow();
            h.assertTrue(recipe.vis()==100&&recipe.crystalCost(0)==1&&recipe.crystalCost(2)==1&&recipe.gridWidth()==3&&recipe.gridHeight()==2,"Original100vis/Aer1/Aqua1 trimmed pattern changed "+id);
            for(int i=0;i<recipe.getIngredients().size();i++) {
                var ingredient=recipe.getIngredients().get(i);h.assertTrue(!ingredient.isEmpty()&&ingredient.getItems().length>0,"Original plate ingredient unresolved "+id);
                b.setItem(i,ingredient.getItems()[0].copyWithCount(2));
            }
            b.setItem(9,AspectCrystalItem.create(Aspect.AIR));b.setItem(11,AspectCrystalItem.create(Aspect.WATER));
            AuraManager.drainVis(h.getLevel(),b.getBlockPos(),Float.MAX_VALUE,false);AuraManager.addVis(h.getLevel(),b.getBlockPos(),102);
            var before=b.saveWithoutMetadata();h.assertTrue(b.findRecipe(p)==null&&b.craft(p).isEmpty()&&before.equals(b.saveWithoutMetadata()),"Absent research yielded a finished device");
            complete(k,"THAUMATORIUM");complete(k,"INFUSION");result(h,ResearchNetwork.processAdvance(p,"ESSENTIATRANSPORT",0),ResearchProgression.Result.STARTED);
            h.assertTrue(b.findRecipe(p)==recipe&&k.researchStage("ESSENTIATRANSPORT")==1,"Bare original gate gained an invented completed-stage condition");
            AuraManager.drainVis(h.getLevel(),b.getBlockPos(),3,false);before=b.saveWithoutMetadata();
            h.assertTrue(b.craft(p).isEmpty()&&before.equals(b.saveWithoutMetadata())&&AuraManager.getVis(h.getLevel(),b.getBlockPos())==99,"99vis rejection partially consumed recipe");
            AuraManager.addVis(h.getLevel(),b.getBlockPos(),3);var output=b.craft(p);
            h.assertTrue(output.is(CatalogBlocks.block(id).asItem())&&output.getCount()==1&&!output.hasTag()
                    &&AuraManager.getVis(h.getLevel(),b.getBlockPos())==2,"Actual device crafting changed output/aura "+id);
            for(int i=0;i<6;i++)h.assertTrue(b.getItem(i).getCount()==1,"Original plate/device/metal ingredient overpaid at "+i);
            h.assertTrue(b.getItem(9).isEmpty()&&b.getItem(11).isEmpty(),"Air/water crystals were not consumed");
            before=b.saveWithoutMetadata();h.assertTrue(b.craft(p).isEmpty()&&before.equals(b.saveWithoutMetadata()),"Repeat craft bypassed missing crystals/vis");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void bothBookReferencesShowOriginalIngredientsAndDoNotGrantResearch(GameTestHelper h) {
        var k=KnowledgeStore.get(player(h));
        for(String suffix:List.of("In","Out")) {
            var defs=BookRecipeCatalog.definitions("thaumcraft:EssentiaTransport"+suffix);
            h.assertTrue(defs.size()==1,"Missing original book recipe "+suffix);var r=defs.get(0);
            h.assertTrue(r.vis()==100&&r.research().equals("ESSENTIATRANSPORT")&&r.crystals()[0]==1&&r.crystals()[2]==1
                    &&r.output().is(CatalogBlocks.block(suffix.equals("In")?"essentia_input":"essentia_output").asItem()),"Book output/price/gate differs "+suffix);
        }
        h.assertTrue(k.researchStage("ESSENTIATRANSPORT")==0,"Read-only archive granted transport research");h.succeed();
    }
}
