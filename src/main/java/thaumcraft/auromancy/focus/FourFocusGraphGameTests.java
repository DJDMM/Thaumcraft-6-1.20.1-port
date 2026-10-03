package thaumcraft.auromancy.focus;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.CatalogModule;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FourFocusGraphGameTests {
    @GameTest(template="empty") public static void boltFluxHealBreakCostsCrystalsCapacityAndNbtAreServerDerived(GameTestHelper h) {
        for(var row:List.of(new Object[]{FocusNodeRegistry.FLUX,8,"vitium"},new Object[]{FocusNodeRegistry.HEAL,9,"victus"},new Object[]{FocusNodeRegistry.BREAK,8,"perditio"})) {
            String effect=(String)row[0];var blank=CatalogModule.stack("focus_1");
            var r=FocusCompiler.compile(ElementalFocusGraphGameTests.graph(FocusNodeRegistry.BOLT,effect,Map.of(),Map.of()),blank,k->true);
            h.assertTrue(r.success()&&r.plan().complexity()==(int)row[1]&&r.plan().crystals().equals(Map.of("potentia",1,(String)row[2],1)),"New node inherited Fire costs: "+effect);
            var stack=FocusStacks.apply(blank,r.plan(),"TC6 "+effect);
            stack.getTag().getCompound("package").putInt("complexity",1);stack.getTag().putInt("color",0);
            var reread=FocusStacks.readPlan(stack).orElseThrow();h.assertTrue(reread.complexity()==r.plan().complexity()&&reread.color()==r.plan().color(),"Forged cost/color trusted");
            var invalid=stack.copy();invalid.getTag().getCompound("package").getList("nodes",Tag.TAG_COMPOUND).getCompound(2).putInt("setting.power",6);
            h.assertTrue(FocusStacks.readPlan(invalid).isEmpty(),"Forged new effect power accepted");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void allFourNewNodesRequireStrictOriginalResearch(GameTestHelper h) {
        for(String effect:List.of(FocusNodeRegistry.FLUX,FocusNodeRegistry.HEAL,FocusNodeRegistry.BREAK)) {
            var g=ElementalFocusGraphGameTests.graph(FocusNodeRegistry.BOLT,effect,Map.of(),Map.of());
            h.assertTrue(FocusCompiler.compile(g,CatalogModule.stack("focus_3"),k->k.equals("FOCUSBOLT")).error().equals("missing_research"),"Effect research bypass");
            h.assertTrue(FocusCompiler.compile(g,CatalogModule.stack("focus_3"),k->!k.equals("FOCUSBOLT")).error().equals("missing_research"),"Bolt research bypass");
        }h.succeed();
    }
    @GameTest(template="empty") public static void breakFortuneAndSilkSettingsPreserveOriginalFullCost(GameTestHelper h) {
        for(int silk=0;silk<=1;silk++)for(int fortune=0;fortune<=4;fortune++) {
            int expected=5+15+silk*4+(fortune==0?0:(fortune+1)*3);
            var result=FocusCompiler.compile(ElementalFocusGraphGameTests.graph(FocusNodeRegistry.BOLT,FocusNodeRegistry.BREAK,Map.of(),Map.of("power",5,"silk",silk,"fortune",fortune)),CatalogModule.stack("focus_3"),k->true);
            h.assertTrue(result.success()&&result.plan().complexity()==expected,"Break fortune/silk complexity drift");
            h.assertTrue(FocusCompiler.compile(result.plan().graph(),CatalogModule.stack("focus_1"),k->true).error().equals("complexity_limit"),"Small focus exceeded original15");
        }h.succeed();
    }
}
