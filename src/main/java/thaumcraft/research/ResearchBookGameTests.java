package thaumcraft.research;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.catalog.CatalogModule;

import java.util.List;

/** Read-only book rules checked without constructing client screens or granting gameplay progress. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ResearchBookGameTests {
    private static ResearchEntry.Stage stage(String... requirements) {
        return new ResearchEntry.Stage("",List.of(requirements),List.of(),List.of(),0);
    }

    @GameTest(template="empty") public static void hiddenParentlessNodesAndTheirChildrenNeedActualKnowledge(GameTestHelper helper) {
        PlayerKnowledge knowledge=new PlayerKnowledge();
        var types=ResearchCatalog.get("KNOWLEDGETYPES");
        var theory=ResearchCatalog.get("THEORYRESEARCH");
        helper.assertTrue(!ResearchBookVisibility.visible(knowledge,types,false)
                && !ResearchBookVisibility.visible(knowledge,theory,false),"Hidden root/child leaked before opening");
        knowledge.setResearchStage(types.key(),types.stages().size()+1);
        helper.assertTrue(ResearchBookVisibility.visible(knowledge,types,false)
                && ResearchBookVisibility.visible(knowledge,theory,false),"Actual completed hidden prerequisite did not reveal its child");
        helper.assertTrue(!ResearchBookVisibility.visible(knowledge,ResearchCatalog.get("INFUSIONANCIENT"),false)
                && ResearchBookVisibility.visible(knowledge,ResearchCatalog.get("INFUSIONANCIENT"),true),"Unsupported mechanic leaked into progression or disappeared from reference");
        helper.succeed();
    }
    @GameTest(template="empty") public static void chapterLookupNeverRevealsFutureRecipesAndStrictAddendaStayLocked(GameTestHelper helper) {
        PlayerKnowledge knowledge=new PlayerKnowledge();
        var smelter=ResearchCatalog.get("ESSENTIASMELTER");
        helper.assertTrue(ResearchBookVisibility.readableChapters(knowledge,smelter,false).isEmpty(),"Unstarted entry exposed recipes");
        knowledge.setResearchStage(smelter.key(),2);
        helper.assertTrue(ResearchBookVisibility.readableChapters(knowledge,smelter,false).equals(List.of(smelter.stages().get(1))),"A later stage was searchable");
        var ore=ResearchCatalog.get("ORE");
        knowledge.setResearchStage(ore.key(),ore.stages().size()+1);
        helper.assertTrue(ResearchBookVisibility.readableChapters(knowledge,ore,false).size()==1,"Unseen ore addenda leaked");
        knowledge.discover("!OREAMBER");
        helper.assertTrue(ResearchBookVisibility.readableChapters(knowledge,ore,false).stream().anyMatch(s->s.requiredResearch().contains("!OREAMBER")),"Unlocked amber addendum missing");
        helper.assertTrue(ResearchBookVisibility.readableChapters(knowledge,ore,true).size()==ore.stages().size()+ore.addenda().size(),"Reference discarded chapters");
        helper.succeed();
    }
    @GameTest(template="empty") public static void displayedItemsReserveSharedMainInventoryAndNeverSpendIt(GameTestHelper helper) {
        var player=FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.DIAMOND,10));
        var before=player.getInventory().save(new net.minecraft.nbt.ListTag());
        var rows=ResearchBookRequirements.rows(stage("required_item: minecraft:diamond,minecraft:diamond"),new PlayerKnowledge(),player.getInventory());
        helper.assertTrue(rows.size()==2 && rows.get(0).met() && !rows.get(1).met()
                && rows.get(0).available()==1 && rows.get(1).available()==0,"Requirement preview duplicated a shared stack or paid from offhand");
        rows.get(0).item().setCount(64);
        helper.assertTrue(rows.get(0).item().getCount()==1 && before.equals(player.getInventory().save(new net.minecraft.nbt.ListTag())),"Book preview mutated inventory or aliased its templates");
        helper.succeed();
    }
    @GameTest(template="empty") public static void displayedSampleCountsRespectWholeNbtAndPrimalCompatibility(GameTestHelper helper) {
        var player=FakePlayerFactory.getMinecraft(helper.getLevel());player.getInventory().clearContent();
        String descriptor="thaumcraft:phial;1;1;{Aspects:[{amount:10,key:'vitium'}]}";
        ItemStack bad=CatalogModule.stack("phial_filled");
        try {bad.setTag(TagParser.parseTag("{Aspects:[{amount:1,key:'vitium'}]}"));}catch(Exception error){throw new IllegalStateException(error);}
        player.getInventory().setItem(0,bad);
        var required=stage("required_item: "+descriptor);
        helper.assertTrue(!ResearchBookRequirements.rows(required,new PlayerKnowledge(),player.getInventory()).get(0).met(),"Partial phial displayed as paid");
        player.getInventory().setItem(1,ResearchBookRequirements.displayItem(descriptor));
        helper.assertTrue(ResearchBookRequirements.rows(required,new PlayerKnowledge(),player.getInventory()).get(0).met(),"Exact sample was not recognized");
        player.getInventory().setItem(2,new ItemStack(thaumcraft.world.WorldModule.VIS_CRYSTALS.get("aer").get()));
        helper.assertTrue(ResearchBookRequirements.rows(stage("required_item: thaumcraft:crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}"),
                new PlayerKnowledge(),player.getInventory()).get(0).met(),"Earlier-port primal crystal compatibility disappeared");
        helper.succeed();
    }
    @GameTest(template="empty") public static void displayedKnowledgeCraftAndEventsUseStrictFactsAndRawRemainders(GameTestHelper helper) {
        var knowledge=new PlayerKnowledge();knowledge.addKnowledge(KnowledgeType.THEORY,"ALCHEMY",63);
        knowledge.recordCraft("thaumcraft:smelter_basic");knowledge.discoverAspect(Aspect.AIR);
        CompoundTag before=knowledge.save();
        var rows=ResearchBookRequirements.rows(stage("required_knowledge: THEORY;ALCHEMY;2","required_craft: thaumcraft:smelter_basic",
                "required_research: !aer","required_knowledgeauram: OBSERVATION;ARTIFICE;1"),knowledge,null);
        helper.assertTrue(rows.get(0).required()==64 && rows.get(0).available()==63 && !rows.get(0).met()
                && rows.get(1).met() && rows.get(2).met() && rows.size()==3,
                "Raw remainder, craft/event proof or ignored BETA26 field was misrepresented");
        helper.assertTrue(before.equals(knowledge.save()),"Requirement read granted or spent knowledge");helper.succeed();
    }
    @GameTest(template="empty") public static void originalMapRequirementUsesFilledMapNotEmptyMap(GameTestHelper helper) {
        var player=FakePlayerFactory.getMinecraft(helper.getLevel());player.getInventory().clearContent();
        player.getInventory().setItem(0,new ItemStack(Items.MAP));
        var stage=stage("required_item: minecraft:map");
        var rows=ResearchBookRequirements.rows(stage,new PlayerKnowledge(),player.getInventory());
        helper.assertTrue(rows.get(0).item().is(Items.FILLED_MAP)&&!rows.get(0).met(),"Empty map substituted for the original filled map");
        player.getInventory().setItem(1,new ItemStack(Items.FILLED_MAP));
        helper.assertTrue(ResearchBookRequirements.rows(stage,new PlayerKnowledge(),player.getInventory()).get(0).met(),"Filled map was not recognized");
        helper.succeed();
    }
    @GameTest(template="empty") public static void enchantmentRequirementsUseMinimumLevelAndMalformedOriginalStaysReference(GameTestHelper helper) {
        var player=FakePlayerFactory.getMinecraft(helper.getLevel());player.getInventory().clearContent();
        ItemStack tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.enchant(Enchantments.BLOCK_FORTUNE,2);
        player.getInventory().setItem(0,tool);
        var rows=ResearchBookRequirements.rows(stage("required_item: thaumcraft:enchanted_placeholder;1;0;{ench:[{id:35s,lvl:1s}]},"
                +"thaumcraft:enchanted_placeholder;1;0;{ench:[{id:35s,lvl:3s}]},"
                +"minecraft:thaumcraft:enchanted_placeholder;1;0;{ench:[{id:16s,lvl:1s}]}"),new PlayerKnowledge(),player.getInventory());
        helper.assertTrue(rows.size()==2 && rows.get(0).met() && !rows.get(1).met(),
                "Enchant threshold or malformed original descriptor became an invented payment");helper.succeed();
    }
}
