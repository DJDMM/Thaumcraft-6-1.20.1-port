package thaumcraft.auromancy.focus;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.CatalogModule;
import java.util.*;
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CompleteFocusGraphGameTests {
    public static FocusGraph.Node n(int id,int parent,List<Integer> children,String key,Map<String,Integer> settings){return new FocusGraph.Node(id,parent,children,0,id,key,settings);}
    public static FocusGraph split(){return new FocusGraph(List.of(n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()),n(1,0,List.of(2),FocusNodeRegistry.TOUCH,Map.of()),
        n(2,1,List.of(3,4),FocusNodeRegistry.SPLITTARGET,Map.of()),n(3,2,List.of(),FocusNodeRegistry.FIRE,Map.of()),n(4,2,List.of(),FocusNodeRegistry.FLUX,Map.of())));}
    @GameTest(template="empty") public static void originalNestedBranchesRoundtripDeriveCostsAndMeanColor(GameTestHelper h){
        var blank=CatalogModule.stack("focus_3");var r=FocusCompiler.compile(split(),blank,k->true);h.assertTrue(r.success(),r.error());
        var p=r.plan();h.assertTrue(p.complexity()==11&&p.effects().size()==2&&p.crystals().equals(Map.of("aversio",1,"ignis",1,"vitium",1)),"Split lost branch costs/crystals");
        int a=FocusNodeRegistry.get(FocusNodeRegistry.FIRE).color(),b=FocusNodeRegistry.get(FocusNodeRegistry.FLUX).color();
        int mean=0xff000000|(((a>>16&255)+(b>>16&255))/2)<<16|(((a>>8&255)+(b>>8&255))/2)<<8|((a&255)+(b&255))/2;
        h.assertTrue(p.color()==mean,"Effect average color drift");var saved=FocusStacks.apply(blank,p,"branches");
        var entry=saved.getTag().getCompound("package").getList("nodes",10).getCompound(2);
        var branches=entry.getCompound("packages").getList("packages",10);h.assertTrue(branches.size()==2,"Original nested package wrapper missing");
        var loaded=FocusStacks.readPlan(saved).orElseThrow();h.assertTrue(loaded.complexity()==11&&loaded.effects().size()==2&&loaded.color()==mean,"Paid tree changed on reload");
        branches.getCompound(0).putInt("complexity",0);branches.getCompound(1).putFloat("power",99);
        h.assertTrue(FocusStacks.readPlan(saved).orElseThrow().complexity()==11,"Untrusted branch scalar price used");
        branches.getCompound(1).getList("nodes",10).getCompound(0).putInt("setting.power",6);
        h.assertTrue(FocusStacks.readPlan(saved).isEmpty(),"Nested settings bypassed validation");h.succeed();
    }
    @GameTest(template="empty") public static void everyRemainingNodeUsesRealShapesResearchAndCapacity(GameTestHelper h){
        for(String key:List.of(FocusNodeRegistry.CURSE,FocusNodeRegistry.EXCHANGE,FocusNodeRegistry.RIFT,FocusNodeRegistry.CLOUD,FocusNodeRegistry.MINE,FocusNodeRegistry.PLAN,FocusNodeRegistry.SPELLBAT,FocusNodeRegistry.SCATTER,FocusNodeRegistry.SPLITTARGET,FocusNodeRegistry.SPLITTRAJECTORY)){
            List<FocusGraph.Node> nodes=new ArrayList<>();nodes.add(n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()));
            if(FocusNodeRegistry.isSplit(key)){
                nodes.add(n(1,0,List.of(2,3),key,Map.of()));
                if(key.equals(FocusNodeRegistry.SPLITTRAJECTORY)){
                    nodes.add(n(2,1,List.of(4),FocusNodeRegistry.TOUCH,Map.of()));nodes.add(n(3,1,List.of(5),FocusNodeRegistry.BOLT,Map.of()));nodes.add(n(4,2,List.of(),FocusNodeRegistry.FIRE,Map.of()));nodes.add(n(5,3,List.of(),FocusNodeRegistry.FLUX,Map.of()));
                }else{nodes.add(n(2,1,List.of(),FocusNodeRegistry.FIRE,Map.of()));nodes.add(n(3,1,List.of(),FocusNodeRegistry.HEAL,Map.of()));}
            }else if(FocusNodeRegistry.get(key).type()==FocusNodeRegistry.Type.EFFECT)nodes.add(n(1,0,List.of(),key,Map.of()));
            else if(key.equals(FocusNodeRegistry.SCATTER)){nodes.add(n(1,0,List.of(2),key,Map.of()));nodes.add(n(2,1,List.of(3),FocusNodeRegistry.BOLT,Map.of()));nodes.add(n(3,2,List.of(),FocusNodeRegistry.FIRE,Map.of()));}
            else{nodes.add(n(1,0,List.of(2),key,Map.of()));nodes.add(n(2,1,List.of(),FocusNodeRegistry.FIRE,Map.of()));}
            var graph=new FocusGraph(nodes);var r=FocusCompiler.compile(graph,CatalogModule.stack("focus_3"),k->true);h.assertTrue(r.success(),key+": "+r.error());
            String gate=FocusNodeRegistry.get(key).research();h.assertTrue(FocusCompiler.compile(graph,CatalogModule.stack("focus_3"),k->!k.equals(gate)).error().equals("missing_research"),"Missing canonical gate "+key);
            h.assertTrue(FocusStacks.readPlan(FocusStacks.apply(CatalogModule.stack("focus_3"),r.plan(),key)).isPresent(),"Saved node failed "+key);
        }h.succeed();
    }
    @GameTest(template="empty") public static void exclusiveNodesWrongBranchCountAndOversizedNestedPackagesAreRejected(GameTestHelper h){
        var repeated=new FocusGraph(List.of(n(0,-1,List.of(1),FocusNodeRegistry.ROOT,Map.of()),n(1,0,List.of(2),FocusNodeRegistry.SCATTER,Map.of()),n(2,1,List.of(3),FocusNodeRegistry.SCATTER,Map.of()),n(3,2,List.of(4),FocusNodeRegistry.TOUCH,Map.of()),n(4,3,List.of(),FocusNodeRegistry.FIRE,Map.of())));
        h.assertTrue(FocusCompiler.compile(repeated,CatalogModule.stack("focus_3"),k->true).error().equals("exclusive_node"),"Repeated Scatter accepted");
        var incompatible=ElementalFocusGraphGameTests.graph(FocusNodeRegistry.PLAN,FocusNodeRegistry.FIRE,Map.of(),Map.of());var nodes=new ArrayList<>(incompatible.nodes());
        nodes.set(1,n(1,0,List.of(3),FocusNodeRegistry.TOUCH,Map.of()));nodes.set(2,n(2,3,List.of(),FocusNodeRegistry.FIRE,Map.of()));nodes.add(n(3,1,List.of(2),FocusNodeRegistry.PLAN,Map.of()));
        h.assertTrue(FocusCompiler.compile(new FocusGraph(nodes),CatalogModule.stack("focus_3"),k->true).error().equals("exclusive_medium"),"Plan combined with another medium");
        var plan=FocusCompiler.compile(split(),CatalogModule.stack("focus_3"),k->true).plan();var saved=FocusStacks.apply(CatalogModule.stack("focus_3"),plan,"");
        var branches=saved.getTag().getCompound("package").getList("nodes",10).getCompound(2).getCompound("packages").getList("packages",10);branches.add(branches.getCompound(0).copy());
        h.assertTrue(FocusStacks.readPlan(saved).isEmpty(),"Third split branch accepted");h.succeed();
    }
}
