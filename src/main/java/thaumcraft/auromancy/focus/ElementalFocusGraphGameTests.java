package thaumcraft.auromancy.focus;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class ElementalFocusGraphGameTests {
    public static FocusGraph graph(String medium,String effect,Map<String,Integer> mediumSettings,Map<String,Integer> effectSettings){
        return new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),
                new FocusGraph.Node(1,0,List.of(2),0,1,medium,mediumSettings),new FocusGraph.Node(2,1,List.of(),0,2,effect,effectSettings)));
    }
    private static ItemStack focus(int tier){return CatalogModule.stack("focus_"+tier);}
    @GameTest(template="empty") public static void elementalCostsAndColorsComeFromTerminalEffect(GameTestHelper h){
        for(var row:List.of(new Object[]{FocusNodeRegistry.AIR,4,16777086,"aer"},new Object[]{FocusNodeRegistry.FROST,6,14811135,"gelum"},new Object[]{FocusNodeRegistry.EARTH,5,5685248,"terra"})){
            String key=(String)row[0];var result=FocusCompiler.compile(graph(FocusNodeRegistry.TOUCH,key,Map.of(),Map.of()),focus(1),k->true);
            h.assertTrue(result.success()&&result.plan().complexity()==(int)row[1]&&result.plan().color()==(0xFF000000|(int)row[2])
                    &&result.plan().crystals().equals(Map.of("aversio",1,(String)row[3],1))&&result.plan().effect().key().equals(key),"Effect inherited Fire cache: "+key);
            h.assertTrue(result.plan().firePower()==0&&FocusStacks.readPlan(FocusStacks.apply(focus(1),result.plan(),"Gift")).orElseThrow().effect().key().equals(key),"Elemental gifted package changed");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void projectileStageTwoAllowsSpeedButCompleteResearchControlsOptions(GameTestHelper h){
        var saved=new CompoundTag();saved.putInt("Version",2);var stages=new CompoundTag();
        stages.putInt("BASEAUROMANCY",ResearchCatalog.get("BASEAUROMANCY").stages().size()+1);stages.putInt("FOCUSPROJECTILE",2);saved.put("ResearchStages",stages);
        var k=PlayerKnowledge.load(saved);
        var normal=graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FIRE,Map.of("speed",5,"option",0),Map.of());
        h.assertTrue(FocusCompiler.compile(normal,focus(1),k::isResearchCompleteStrict).success(),"Projectile speed gated behind final stage");
        for(int option=1;option<=3;option++)h.assertTrue(FocusCompiler.compile(graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FIRE,Map.of("speed",1,"option",option),Map.of()),focus(1),k::isResearchCompleteStrict).error().equals("missing_setting_research"),"Forged Projectile option at@2");
        stages.putInt("FOCUSPROJECTILE",ResearchCatalog.get("FOCUSPROJECTILE").stages().size()+1);k=PlayerKnowledge.load(saved);
        h.assertTrue(FocusCompiler.compile(graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FIRE,Map.of("option",2),Map.of()),focus(1),k::isResearchCompleteStrict).success(),"Complete research did not unlock seek");h.succeed();
    }
    @GameTest(template="empty") public static void projectileIntegerSpeedCostsAndCapacityAreAuthoritative(GameTestHelper h){
        for(int speed=1;speed<=5;speed++)for(int option=0;option<=3;option++){
            var plan=FocusCompiler.compile(graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FIRE,Map.of("speed",speed,"option",option),Map.of()),focus(3),k->true).plan();
            int price=6+(speed-1)/2+(option==0?0:option==1?3:5);
            h.assertTrue(plan.complexity()==price&&plan.craftVis()==price*10+10&&plan.castVis()==price/5F&&plan.crystals().equals(Map.of("motus",1,"ignis",1)),"Projectile price drift");
        }
        h.assertTrue(!FocusCompiler.compile(graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.FROST,Map.of("option",2,"speed",5),Map.of("power",5,"duration",10)),focus(2),k->true).success(),"Advanced focus exceeded25");h.succeed();
    }
    @GameTest(template="empty") public static void resumedDeliveryOrderSurvivesGraphAndPackageRoundtrip(GameTestHelper h){
        List<String> keys=List.of(FocusNodeRegistry.ROOT,FocusNodeRegistry.TOUCH,FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.TOUCH,FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.AIR);
        List<FocusGraph.Node> nodes=new ArrayList<>();
        for(int i=0;i<keys.size();i++)nodes.add(new FocusGraph.Node(i,i-1,i==keys.size()-1?List.of():List.of(i+1),0,i,keys.get(i),FocusNodeRegistry.get(keys.get(i)).defaultSettings()));
        var result=FocusCompiler.compile(new FocusGraph(nodes),focus(3),k->true);h.assertTrue(result.success()&&result.plan().complexity()==17,"Repeated media complexity/order");
        var plan=FocusStacks.readPlan(FocusStacks.apply(focus(3),result.plan(),"Nested delivery")).orElseThrow();
        h.assertTrue(plan.graph().nodes().stream().map(FocusGraph.Node::key).toList().equals(keys),"Serialized remainder reordered media");h.succeed();
    }
    @GameTest(template="empty") public static void allElementalSettingsRemainTypedBoundedAndResearchLocked(GameTestHelper h){
        for(String effect:List.of(FocusNodeRegistry.AIR,FocusNodeRegistry.FROST,FocusNodeRegistry.EARTH)){
            var g=graph(FocusNodeRegistry.TOUCH,effect,Map.of(),Map.of());
            h.assertTrue(FocusCompiler.compile(g,focus(3),k->k.equals("BASEAUROMANCY")).error().equals("missing_research"),"Elemental research bypass");
            var prepared=FocusStacks.apply(focus(3),FocusCompiler.compile(g,focus(3),k->true).plan(),"");
            prepared.getTag().getCompound("package").getList("nodes",Tag.TAG_COMPOUND).getCompound(2).putFloat("setting.power",1F);
            h.assertTrue(FocusStacks.readPlan(prepared).isEmpty(),"Elemental float setting accepted");
        }
        for(int[] row:List.of(new int[]{0,0},new int[]{6,0},new int[]{1,-1},new int[]{1,4}))h.assertTrue(!FocusCompiler.compile(graph(FocusNodeRegistry.PROJECTILE,FocusNodeRegistry.AIR,Map.of("speed",row[0],"option",row[1]),Map.of()),focus(3),k->true).success(),"Projectile forged setting accepted");h.succeed();
    }
}
