package thaumcraft.auromancy.table;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.blocks.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.auromancy.focus.*;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocalUpgradeGameTests {
    @GameTest(template="empty") public static void everyElementalFocusCraftUsesRealCrystalsXpAuraAndPaidSave(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));level.setBlockAndUpdate(pos,CatalogBlocks.block("wand_workbench").defaultBlockState());
        var tile=(FocalManipulatorBlockEntity)level.getBlockEntity(pos);
        for(String key:List.of(FocusNodeRegistry.AIR,FocusNodeRegistry.FROST,FocusNodeRegistry.EARTH)){
            var p=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"elemental_table"));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);p.experienceLevel=20;
            var knowledge=thaumcraft.research.KnowledgeStore.get(p);
            for(String research:List.of("BASEAUROMANCY","FOCUSELEMENTAL"))try{var set=knowledge.getClass().getDeclaredMethod("setResearchStage",String.class,int.class);set.setAccessible(true);set.invoke(knowledge,research,thaumcraft.research.ResearchCatalog.get(research).stages().size()+1);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            var graph=thaumcraft.auromancy.focus.ElementalFocusGraphGameTests.graph(FocusNodeRegistry.TOUCH,key,Map.of(),Map.of());var blank=CatalogModule.stack("focus_1");var plan=FocusCompiler.compile(graph,blank,knowledge::isResearchCompleteStrict).plan();
            tile.setItem(0,blank);int slot=9;for(var entry:plan.crystals().entrySet())p.getInventory().setItem(slot++,thaumcraft.alchemy.AspectCrystalItem.create(thaumcraft.api.aspects.Aspect.getAspect(entry.getKey()),entry.getValue()));
            thaumcraft.world.aura.AuraManager.drainVis(level,pos,Float.MAX_VALUE,false);thaumcraft.world.aura.AuraManager.addVis(level,pos,100);
            h.assertTrue(tile.edit(p,tile.revision(),graph.save(),"Elemental")==FocalManipulatorResult.ACCEPTED&&tile.start(p,tile.revision())==FocalManipulatorResult.ACCEPTED,"Elemental table start failed "+key);
            h.assertTrue(p.experienceLevel==20-plan.xpLevels()&&p.getInventory().getItem(9).isEmpty()&&p.getInventory().getItem(10).isEmpty(),"Elemental crystals/XP not consumed "+key);
            var paid=tile.saveWithoutMetadata();tile.load(paid);
            for(int i=0;i<100&&tile.crafting();i++)FocalManipulatorBlockEntity.tick(level,pos,tile.getBlockState(),tile);
            h.assertTrue(!tile.crafting()&&FocusStacks.readPlan(tile.getItem(0)).orElseThrow().effect().key().equals(key)
                    &&Math.abs(thaumcraft.world.aura.AuraManager.getVis(level,pos)-(100-plan.craftVis()))<.001F&&p.experienceLevel==20-plan.xpLevels(),"Elemental saved craft payment/output drift "+key);
        }
        h.succeed();
    }
    private static void process(GameTestHelper h){for(int i=0;i<256;i++)LegacyFocalMigration.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true));}
    @GameTest(template="empty") public static void legacyVisualFocalTableUpgradesOnlyAtEndWithoutManufacturingItems(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(1,1,1));var level=h.getLevel();var state=CatalogBlocks.block("wand_workbench").defaultBlockState();level.setBlockAndUpdate(pos,state);
        var chunk=level.getChunkAt(pos);var legacy=new CatalogBlockEntity(pos,state);chunk.addAndRegisterBlockEntity(legacy);
        LegacyFocalMigration.load(new ChunkEvent.Load(chunk,false));LegacyFocalMigration.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.START,level,()->true));
        h.assertTrue(level.getBlockEntity(pos)==legacy,"Worker/START focal migration mutated world");process(h);
        h.assertTrue(level.getBlockEntity(pos) instanceof FocalManipulatorBlockEntity,"Legacy focal tile did not upgrade");var tile=(FocalManipulatorBlockEntity)level.getBlockEntity(pos);
        h.assertTrue(tile.isEmpty()&&!tile.crafting()&&tile.graph().nodes().isEmpty(),"Storage-free migration invented focus/payment");h.succeed();
    }
    @GameTest(template="empty") public static void modernFocalSaveRemainsUntouchedAndElementalPlansRestore(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(1,1,1));var level=h.getLevel();level.setBlockAndUpdate(pos,CatalogBlocks.block("wand_workbench").defaultBlockState());
        var tile=(FocalManipulatorBlockEntity)level.getBlockEntity(pos);var focus=CatalogModule.stack("focus_1");
        var graph=new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),new FocusGraph.Node(1,0,List.of(2),0,1,FocusNodeRegistry.PROJECTILE,Map.of()),new FocusGraph.Node(2,1,List.of(),0,2,FocusNodeRegistry.FROST,Map.of())));
        var plan=FocusCompiler.compile(graph,focus,k->true).plan();tile.setItem(0,FocusStacks.apply(focus,plan,"Saved frost"));var before=tile.saveWithoutMetadata();
        LegacyFocalMigration.load(new ChunkEvent.Load(level.getChunkAt(pos),false));process(h);
        h.assertTrue(level.getBlockEntity(pos)==tile&&before.equals(tile.saveWithoutMetadata()),"Migration replaced modern paid state");
        var loaded=new FocalManipulatorBlockEntity(pos,tile.getBlockState());loaded.load(before);
        h.assertTrue(FocusStacks.readPlan(loaded.getItem(0)).orElseThrow().effect().key().equals(FocusNodeRegistry.FROST),"Elemental focus missing on table reload");h.succeed();
    }
}
