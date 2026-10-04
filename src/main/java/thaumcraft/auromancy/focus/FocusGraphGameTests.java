package thaumcraft.auromancy.focus;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;

import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusGraphGameTests {
    @GameTest(template="empty") public static void touchFireUsesOriginalPricesAndOriginalTypedPackage(GameTestHelper h) {
        var compiled = FocusCompiler.compile(FocusGraph.touchFire(1, 0), focus(1), key -> key.equals("BASEAUROMANCY"));
        h.assertTrue(compiled.success() && compiled.error().isEmpty() && compiled.plan() != null, "Basic graph did not compile");
        var plan = compiled.plan();
        h.assertTrue(plan.complexity() == 4 && plan.maxComplexity() == 15 && plan.craftVis() == 43F
                && plan.xpLevels() == 2 && plan.castVis() == .8F && plan.cooldownTicks() == 5,
                "BETA26 integer/float costs changed");
        h.assertTrue(plan.crystals().equals(Map.of("aversio",1,"ignis",1)) && plan.firePower() == 1
                && plan.fireDuration() == 0 && plan.color() == (0xFF000000 | 16734721), "Crystal/settings/color mismatch");
        var tag = plan.packageNbt(); var nodes = tag.getList("nodes", Tag.TAG_COMPOUND);
        h.assertTrue(tag.contains("power",Tag.TAG_FLOAT) && tag.getFloat("power") == 1F && tag.getInt("complexity") == 4
                && tag.getInt("index") == 0 && nodes.size() == 3 && nodes.getCompound(0).getString("key").equals("ROOT")
                && nodes.getCompound(0).getString("type").equals("MEDIUM")
                && nodes.getCompound(2).getString("type").equals("EFFECT")
                && nodes.getCompound(2).contains("setting.power",Tag.TAG_INT), "Original package NBT shape changed");
        h.succeed();
    }
    @GameTest(template="empty") public static void allThreeTiersEnforceCapacityAndIntegerCooldown(GameTestHelper h) {
        h.assertTrue(FocusStacks.maxComplexity(focus(1)) == 15 && FocusStacks.maxComplexity(focus(2)) == 25
                && FocusStacks.maxComplexity(focus(3)) == 50 && !FocusStacks.isFocus(new ItemStack(Items.DIAMOND)), "Focus tier capacities changed");
        h.assertTrue(!FocusCompiler.compile(FocusGraph.touchFire(5,5),focus(1),key->true).success(), "Lesser focus accepted17 complexity");
        var edge = FocusCompiler.compile(FocusGraph.touchFire(5,3),focus(1),key->true).plan();
        var advanced = FocusCompiler.compile(FocusGraph.touchFire(5,5),focus(2),key->true).plan();
        var greater = FocusCompiler.compile(FocusGraph.touchFire(5,5),focus(3),key->true).plan();
        h.assertTrue(edge.complexity() == 15 && edge.craftVis() == 153 && edge.cooldownTicks() == 9
                && advanced.complexity() == 17 && advanced.craftVis() == 175 && advanced.cooldownTicks() == 12
                && greater.craftVis() == 180 && greater.xpLevels() == 4, "Tier/cooldown integer divisions changed");
        h.assertTrue(FocusCompiler.compile(FocusGraph.touchFire(5,0),focus(1),key->true).plan().cooldownTicks() == 6,
                "Integer cooldown boundary12 became5"); h.succeed();
    }
    @GameTest(template="empty") public static void nodeCraftingNeedsCompletedBareResearchRatherThanStartedResearch(GameTestHelper h) {
        PlayerKnowledge knowledge = knowledge(1);
        h.assertTrue(knowledge.isResearchKnown("BASEAUROMANCY")
                && !FocusCompiler.compile(FocusGraph.touchFire(1,0),focus(1),knowledge::isResearchCompleteStrict).success(),
                "Started bare research bypassed original knowsResearchStrict");
        PlayerKnowledge complete = knowledge(ResearchCatalog.get("BASEAUROMANCY").stages().size()+1);
        h.assertTrue(FocusCompiler.compile(FocusGraph.touchFire(1,0),focus(1),complete::isResearchCompleteStrict).success(),
                "Completed basic auromancy cannot craft focus"); h.succeed();
    }
    @GameTest(template="empty") public static void savedFocusIsGiftableAndNeverTrustsCachedPricesOrPower(GameTestHelper h) {
        var input = focus(1); var plan = FocusCompiler.compile(FocusGraph.touchFire(3,2),input,key->true).plan();
        var saved = FocusStacks.apply(input,plan,"Gift"); var pkg = saved.getTag().getCompound("package");
        pkg.putInt("complexity",-1000); pkg.putFloat("power",Float.POSITIVE_INFINITY); pkg.putInt("index",1000);
        saved.getTag().putInt("color",0); saved.getTag().putInt("srt",0);
        var before = saved.save(new CompoundTag()); var loaded = FocusStacks.readPlan(saved).orElseThrow();
        h.assertTrue(loaded.complexity() == 10 && loaded.castVis() == 2F && loaded.craftVis() == 103F
                && loaded.color() == plan.color() && loaded.firePower() == 3 && loaded.fireDuration() == 2
                && before.equals(saved.save(new CompoundTag())), "Read used attacker-controlled payment/power/color or changed saved focus");
        h.assertTrue(!input.hasTag(), "Applying a plan changed the source focus"); h.succeed();
    }
    @GameTest(template="empty") public static void detachedPlanAndOriginalGraphTagsCannotBeMutatedThroughCallerReferences(GameTestHelper h) {
        var settings = new LinkedHashMap<String,Integer>(); settings.put("power",2); settings.put("duration",1);
        var nodes = new ArrayList<>(FocusGraph.touchFire(2,1).nodes());
        var fire = nodes.get(2); nodes.set(2,new FocusGraph.Node(2,1,List.of(),0,2,fire.key(),settings));
        var graph = new FocusGraph(nodes); var plan = FocusCompiler.compile(graph,focus(1),key->true).plan();
        settings.put("power",5); nodes.clear(); var first = plan.packageNbt();
        first.getList("nodes",Tag.TAG_COMPOUND).getCompound(2).putInt("setting.power",5);
        var serialized = graph.save(); serialized.getList("nodes",Tag.TAG_COMPOUND).getCompound(1).putInt("id",29);
        h.assertTrue(plan.firePower() == 2 && plan.complexity() == 7
                && plan.packageNbt().getList("nodes",Tag.TAG_COMPOUND).getCompound(2).getInt("setting.power") == 2
                && graph.nodes().size() == 3 && graph.nodes().get(1).id() == 1, "Caller retained graph/plan/tag aliases");
        var roundtrip = FocusGraph.read(graph.save());
        h.assertTrue(roundtrip.nodes().equals(graph.nodes()) && FocusCompiler.compile(roundtrip,focus(1),key->true).success(),
                "Original graph fields/settings failed roundtrip"); h.succeed();
    }
    @GameTest(template="empty") public static void malformedCyclesOrExtraNodesAreRejectedWithoutChangingFocus(GameTestHelper h) {
        var focus = focus(1); focus.getOrCreateTag().putString("owner_marker","keep"); var before = focus.save(new CompoundTag());
        List<FocusGraph> bad = List.of(
                new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,2,List.of(2),FocusNodeRegistry.TOUCH),node(2,1,List.of(1),FocusNodeRegistry.FIRE))),
                new FocusGraph(List.of(node(0,-1,List.of(),"ROOT"),node(1,2,List.of(2),FocusNodeRegistry.TOUCH),node(2,1,List.of(1),FocusNodeRegistry.FIRE))),
                new FocusGraph(List.of(node(0,-1,List.of(1,1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.TOUCH),node(2,1,List.of(),FocusNodeRegistry.FIRE))),
                new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.TOUCH),node(1,1,List.of(),FocusNodeRegistry.FIRE))),
                new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.TOUCH),node(2,-1,List.of(),FocusNodeRegistry.FIRE))),
                new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.TOUCH),node(2,1,List.of(3),FocusNodeRegistry.FIRE),node(3,2,List.of(),FocusNodeRegistry.FIRE))));
        for (var graph : bad) { var result = FocusCompiler.compile(graph,focus,key->true);
            h.assertTrue(!result.success() && result.plan() == null && !result.error().isEmpty(), "Malformed/cyclic/extra graph compiled"); }
        h.assertTrue(before.equals(focus.save(new CompoundTag())), "Invalid graph mutated an inventory stack"); h.succeed();
    }
    @GameTest(template="empty") public static void completeRegistryStillRejectsInvalidSupplyAndShapes(GameTestHelper h) {
        h.assertTrue(FocusNodeRegistry.all().size() == 21
                && FocusNodeRegistry.all().stream().filter(FocusNodeRegistry.Definition::runtimeSupported).count() == 21,
                "Original registered-node/runtime inventory changed");
        for (var definition : FocusNodeRegistry.all()) if (!definition.runtimeSupported()) {
            var graph = new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.TOUCH),
                    new FocusGraph.Node(2,1,List.of(),0,2,definition.key(),definition.defaultSettings())));
            h.assertTrue(!FocusCompiler.compile(graph,focus(3),key->true).success(), "Reference-only node became runtime: "+definition.key());
        }
        var projectile = FocusNodeRegistry.get("thaumcraft.PROJECTILE");
        h.assertTrue(projectile.research().equals("FOCUSPROJECTILE@2") && projectile.settings().get("option").research().equals("FOCUSPROJECTILE")
                && projectile.complexity(Map.of("speed",5,"option",2)) == 11
                && FocusNodeRegistry.get("thaumcraft.SPELLBAT").powerMultiplier(Map.of()) == .33F, "Late original metadata changed");
        // Verified original bytecode bug: Exchange default complexity is3 and silk does not add4.
        h.assertTrue(FocusNodeRegistry.get("thaumcraft.EXCHANGE").complexity(Map.of("silk",0,"fortune",0)) == 3
                && FocusNodeRegistry.get("thaumcraft.EXCHANGE").complexity(Map.of("silk",1,"fortune",0)) == 3,
                "Pinned Exchange precedence silently repaired"); h.succeed();
    }
    @GameTest(template="empty") public static void outOfRangeOrUnknownSettingsAndMissingTargetSupplyAreRejected(GameTestHelper h) {
        for (int[] settings : List.of(new int[]{0,0},new int[]{6,0},new int[]{1,-1},new int[]{1,6}))
            h.assertTrue(!FocusCompiler.compile(FocusGraph.touchFire(settings[0],settings[1]),focus(3),key->true).success(), "Invalid fire setting accepted");
        var nodes = new ArrayList<>(FocusGraph.touchFire(1,0).nodes()); var fire = nodes.get(2);
        nodes.set(2,new FocusGraph.Node(2,1,List.of(),0,2,fire.key(),Map.of("power",1,"duration",0,"forged",0)));
        h.assertTrue(!FocusCompiler.compile(new FocusGraph(nodes),focus(1),key->true).success(), "Unknown setting ignored");
        h.assertTrue(!FocusCompiler.compile(new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.FIRE),
                node(2,1,List.of(),FocusNodeRegistry.TOUCH))),focus(1),key->true).success(), "Effect supplied a trajectory after output"); h.succeed();
    }
    @GameTest(template="empty") public static void savedPackageRejectsWrongTypesNestedPackagesAndInvalidSettings(GameTestHelper h) {
        var base = FocusStacks.apply(focus(1),FocusCompiler.compile(FocusGraph.touchFire(1,0),focus(1),key->true).plan(),"Test");
        var wrongType = base.copy(); savedNode(wrongType,2).putString("type","PACKAGE");
        var wrongSetting = base.copy(); savedNode(wrongSetting,2).putString("setting.power","5");
        var nested = base.copy(); savedNode(nested,2).put("packages",new CompoundTag());
        var tooStrong = base.copy(); savedNode(tooStrong,2).putInt("setting.power",5); savedNode(tooStrong,2).putInt("setting.duration",5);
        var extra = base.copy(); extra.getTag().getCompound("package").getList("nodes",Tag.TAG_COMPOUND).add(savedNode(extra,2).copy());
        for (var invalid : List.of(wrongType,wrongSetting,nested,tooStrong,extra))
            h.assertTrue(FocusStacks.readPlan(invalid).isEmpty(), "Malformed/mispriced saved focus became executable"); h.succeed();
    }
    @GameTest(template="empty") public static void graphParserBoundsListsChildrenTypesAndCoordinates(GameTestHelper h) {
        var wrongType = new CompoundTag(); var strings = new ListTag(); strings.add(StringTag.valueOf("ROOT")); wrongType.put("nodes",strings);
        var tooMany = FocusGraph.touchFire(1,0).save(); var nodes = new ListTag();
        for (int i=0;i<33;i++) nodes.add(FocusGraph.touchFire(1,0).save().getList("nodes",Tag.TAG_COMPOUND).getCompound(0)); tooMany.put("nodes",nodes);
        var tooManyChildren = FocusGraph.touchFire(1,0).save(); tooManyChildren.getList("nodes",Tag.TAG_COMPOUND).getCompound(0).putIntArray("children",new int[33]);
        var wrongCoordinate = FocusGraph.touchFire(1,0).save(); wrongCoordinate.getList("nodes",Tag.TAG_COMPOUND).getCompound(0).putInt("x",Integer.MIN_VALUE);
        var wrongId = FocusGraph.touchFire(1,0).save(); wrongId.getList("nodes",Tag.TAG_COMPOUND).getCompound(0).putByte("id",(byte)0);
        for (var invalid : List.of(wrongType,tooMany,tooManyChildren,wrongCoordinate,wrongId)) {
            boolean rejected=false; try { FocusGraph.read(invalid); } catch (IllegalArgumentException expected) { rejected=true; }
            h.assertTrue(rejected,"Graph parser accepted malformed/unbounded tags");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void applyingRecompiledPlanPreservesUnrelatedTagsAndRequiresARealSingleFocus(GameTestHelper h) {
        var input = focus(2); input.getOrCreateTag().putString("marker","preserve"); var original = input.save(new CompoundTag());
        var plan = FocusCompiler.compile(FocusGraph.touchFire(5,5),input,key->true).plan();
        var output = FocusStacks.apply(input,plan,"\u00a7bad\nName");
        h.assertTrue(output.getTag().getString("marker").equals("preserve") && output.getHoverName().getString().equals("badName")
                && output.getTag().contains("color",Tag.TAG_INT) && output.getTag().contains("srt",Tag.TAG_INT)
                && original.equals(input.save(new CompoundTag())), "Plan replaced unrelated NBT/source stack or kept controls");
        boolean lowerRejected=false; try { FocusStacks.apply(focus(1),plan,"Too strong"); } catch (IllegalArgumentException expected) { lowerRejected=true; }
        var multiple = focus(2); multiple.setCount(2);
        h.assertTrue(lowerRejected && !FocusCompiler.compile(plan.graph(),multiple,key->true).success()
                && FocusStacks.readPlan(new ItemStack(Items.DIAMOND)).isEmpty(), "Applying plan bypassed focus/capacity/count validation"); h.succeed();
    }
    @GameTest(template="empty") public static void absentSavedSettingsUseDeclaredOriginalDefaults(GameTestHelper h) {
        var saved = FocusStacks.apply(focus(1),FocusCompiler.compile(FocusGraph.touchFire(1,0),focus(1),key->true).plan(),"");
        savedNode(saved,2).remove("setting.power"); savedNode(saved,2).remove("setting.duration");
        var read = FocusStacks.readPlan(saved).orElseThrow();
        h.assertTrue(read.firePower() == 1 && read.fireDuration() == 0 && read.complexity() == 4 && !saved.hasCustomHoverName(),
                "Absent settings did not resolve to original range/list defaults"); h.succeed();
    }
    @GameTest(template="empty") public static void originalBaseNodesAllowSelfTargetAndRepeatedTouchWithDuplicateComplexity(GameTestHelper h) {
        var self=FocusCompiler.compile(FocusGraph.selfFire(1,0),focus(1),key->true);
        h.assertTrue(self.success() && self.plan().complexity()==2 && self.plan().craftVis()==23F
                && self.plan().crystals().equals(Map.of("ignis",1)),"ROOT's original self target required an invented Touch");
        var repeated=new FocusGraph(List.of(node(0,-1,List.of(1),"ROOT"),node(1,0,List.of(2),FocusNodeRegistry.TOUCH),
                node(2,1,List.of(3),FocusNodeRegistry.TOUCH),node(3,2,List.of(),FocusNodeRegistry.FIRE)));
        var compiled=FocusCompiler.compile(repeated,focus(1),key->true);
        h.assertTrue(compiled.success() && compiled.plan().complexity()==7 && compiled.plan().craftVis()==73F
                && compiled.plan().crystals().equals(Map.of("aversio",2,"ignis",1))
                && compiled.plan().graph().nodes().get(2).key().equals(FocusNodeRegistry.TOUCH),"Repeated Touch lost original2+3 complexity/aspect payment");
        for(var plan:List.of(self.plan(),compiled.plan())) {
            var saved=FocusStacks.apply(focus(1),plan,"");var loaded=FocusStacks.readPlan(saved).orElseThrow();
            h.assertTrue(loaded.graph().nodes().size()==plan.graph().nodes().size()&&loaded.complexity()==plan.complexity(),"Nondefault legal base chain failed package roundtrip");
        }
        h.succeed();
    }
    private static ItemStack focus(int tier) { return CatalogModule.stack("focus_"+tier); }
    private static PlayerKnowledge knowledge(int stage) {
        var tag=new CompoundTag();tag.putInt("Version",2);var stages=new CompoundTag();
        stages.putInt("BASEAUROMANCY",stage);tag.put("ResearchStages",stages);return PlayerKnowledge.load(tag);
    }
    private static FocusGraph.Node node(int id,int parent,List<Integer> children,String key) {
        var definition=FocusNodeRegistry.get(key);
        return new FocusGraph.Node(id,parent,children,0,id,key,definition==null?Map.of():definition.defaultSettings());
    }
    private static CompoundTag savedNode(ItemStack stack,int index) {
        return stack.getTag().getCompound("package").getList("nodes",Tag.TAG_COMPOUND).getCompound(index);
    }
}
