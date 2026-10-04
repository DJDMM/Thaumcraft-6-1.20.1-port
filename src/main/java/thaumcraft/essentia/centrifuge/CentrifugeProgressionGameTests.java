package thaumcraft.essentia.centrifuge;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.*;
import thaumcraft.world.aura.AuraManager;
import java.util.List;
import java.util.UUID;

/** Canonical parent completion, exact theory debit and a real paid workbench transaction. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CentrifugeProgressionGameTests {
    private static final BlockPos CENTER=new BlockPos(4,2,4);
    private CentrifugeProgressionGameTests() {}
    private static ServerPlayer player(GameTestHelper helper) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"centrifuge_craft"));
        var position=helper.absolutePos(CENTER); player.setPos(position.getX()+.5,position.getY()+1,position.getZ()+.5);
        return player;
    }
    /** Parent stages are explicit QA fixtures; the production setter remains package-private. */
    private static void stage(PlayerKnowledge knowledge,String key,int value) {
        try {
            var setter=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);
            setter.setAccessible(true); setter.invoke(knowledge,key,value);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Cannot install research parent fixture",error); }
    }
    private static void complete(PlayerKnowledge knowledge,String key) { stage(knowledge,key,ResearchCatalog.get(key).stages().size()+1); }
    private static void result(GameTestHelper helper,ResearchProgression.Result actual,ResearchProgression.Result expected) {
        helper.assertTrue(actual==expected,"Expected "+expected+", got "+actual);
    }

    @GameTest(template="empty")
    public static void completedTubesThenExactly32AlchemyTheoryAdvanceOriginalTwoStages(GameTestHelper helper) {
        var player=player(helper); var knowledge=KnowledgeStore.get(player); var entry=ResearchCatalog.get("CENTRIFUGE");
        helper.assertTrue(ResearchProgression.isImplemented("CENTRIFUGE")&&entry.parents().equals(List.of("TUBES"))&&entry.stages().size()==2,
                "Centrifuge canonical support, parent or stage count changed");
        result(helper,ResearchProgression.advance(player,"CENTRIFUGE",0),ResearchProgression.Result.LOCKED);
        stage(knowledge,"TUBES",ResearchCatalog.get("TUBES").stages().size());
        result(helper,ResearchProgression.advance(player,"CENTRIFUGE",0),ResearchProgression.Result.LOCKED);
        complete(knowledge,"TUBES");
        result(helper,ResearchProgression.advance(player,"CENTRIFUGE",0),ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(player,KnowledgeType.THEORY,"ALCHEMY",31);
        var before=knowledge.save(); var inventory=new ListTag(); player.getInventory().save(inventory); int xp=player.totalExperience;
        result(helper,ResearchProgression.advance(player,"CENTRIFUGE",1),ResearchProgression.Result.MISSING_REQUIREMENTS);
        var afterInventory=new ListTag(); player.getInventory().save(afterInventory);
        helper.assertTrue(before.equals(knowledge.save())&&inventory.equals(afterInventory)&&xp==player.totalExperience,"31-raw rejection partially paid research");
        KnowledgeStore.addKnowledge(player,KnowledgeType.THEORY,"ALCHEMY",4);
        result(helper,ResearchProgression.advance(player,"CENTRIFUGE",1),ResearchProgression.Result.COMPLETE);
        helper.assertTrue(knowledge.researchStage("CENTRIFUGE")==3&&knowledge.rawKnowledge(KnowledgeType.THEORY,"ALCHEMY")==3,
                "Centrifuge did not debit exactly32 raw Alchemy theory");
        helper.assertTrue(knowledge.researchStage("CENTRIFUGE")==3&&knowledge.isResearchCompleteStrict("CENTRIFUGE")
                &&knowledge.permanentWarp()==0&&knowledge.normalWarp()==0,"Empty recipe stage added a cost/warp or failed completion");
        before=knowledge.save(); xp=player.totalExperience;
        result(helper,ResearchProgression.advance(player,"CENTRIFUGE",1),ResearchProgression.Result.STALE);
        helper.assertTrue(before.equals(knowledge.save())&&xp==player.totalExperience&&PlayerKnowledge.load(before).isResearchCompleteStrict("CENTRIFUGE"),
                "Replay consumed resources or save lost completion");
        result(helper,ResearchProgression.advance(player,"THAUMATORIUM",0),ResearchProgression.Result.LOCKED);
        helper.succeed();
    }

    @GameTest(template="essentia_network")
    public static void actualWorkbenchUsesBareResearchAndDebitsFiveIngredientsTwoCrystals100Vis(GameTestHelper helper) {
        helper.setBlock(CENTER,ArcaneModule.WORKBENCH.get());
        var bench=(ArcaneWorkbenchBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
        var player=player(helper); var knowledge=KnowledgeStore.get(player);
        bench.setItem(1,new ItemStack(CatalogBlocks.block("tube"),2)); bench.setItem(7,new ItemStack(CatalogBlocks.block("tube"),2));
        var resonator=CatalogModule.stack("morphic_resonator"); resonator.setCount(2); bench.setItem(3,resonator);
        bench.setItem(4,new ItemStack(CatalogBlocks.block("metal_alchemical"),2));
        var mechanism=CatalogModule.stack("mechanism_simple"); mechanism.setCount(2); bench.setItem(5,mechanism);
        bench.setItem(13,CatalogModule.aspectStack("crystal_essence",Aspect.ORDER,1));
        bench.setItem(14,CatalogModule.aspectStack("crystal_essence",Aspect.ENTROPY,1));
        // Two crystals remain two physical stacks, not merely server recipe metadata.
        helper.assertTrue(!bench.getItem(13).isEmpty()&&!bench.getItem(14).isEmpty(),"Crystal fixtures did not resolve registered original stacks");
        AuraManager.addVis(helper.getLevel(),bench.getBlockPos(),500);
        float vis=AuraManager.getVis(helper.getLevel(),bench.getBlockPos()); var before=bench.saveWithoutMetadata();
        helper.assertTrue(bench.findRecipe(player)==null&&bench.craft(player).isEmpty()
                &&before.equals(bench.saveWithoutMetadata())&&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==vis,"Absent research consumed physical ingredients/aura");
        complete(knowledge,"TUBES"); result(helper,ResearchProgression.advance(player,"CENTRIFUGE",0),ResearchProgression.Result.STARTED);
        ArcaneRecipe recipe=bench.findRecipe(player);
        helper.assertTrue(recipe!=null&&recipe.getId().equals(ResourceLocation.fromNamespaceAndPath("thaumcraft","arcane/centrifuge"))
                &&recipe.unlocked(player)&&knowledge.researchStage("CENTRIFUGE")==1,"Bare original recipe gate required an invented completed stage");
        AuraManager.drainVis(helper.getLevel(),bench.getBlockPos(),Float.MAX_VALUE,false); AuraManager.addVis(helper.getLevel(),bench.getBlockPos(),99);
        before=bench.saveWithoutMetadata();
        helper.assertTrue(bench.craft(player).isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==99,"99vis rejection partially paid");
        AuraManager.addVis(helper.getLevel(),bench.getBlockPos(),3);
        ItemStack output=bench.craft(player);
        helper.assertTrue(output.is(CatalogBlocks.block("centrifuge").asItem())&&output.getCount()==1
                &&output.getItem() instanceof CentrifugeBlockItem&&!output.hasTag()
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==2,"Paid manufacture changed price/output/item mechanics");
        for(int slot:new int[]{1,3,4,5,7})helper.assertTrue(bench.getItem(slot).getCount()==1,"Ingredient paid wrong count at slot="+slot);
        helper.assertTrue(bench.getItem(13).isEmpty()&&bench.getItem(14).isEmpty(),"Ordo/Perditio crystal payment omitted");
        before=bench.saveWithoutMetadata();
        helper.assertTrue(bench.craft(player).isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==2,"Repeat manufacture bypassed crystals/vis");
        helper.succeed();
    }
}
