package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.table.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.aura.AuraManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server transactions, save cadence and real container actions, independent of the GUI preview. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocalManipulatorGameTests {
    private record Fixture(GameTestHelper helper, BlockPos pos, FocalManipulatorBlockEntity table,
                           ServerPlayer player, FocalManipulatorMenu menu) {}
    private static Fixture table(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2,1,2)); var level = h.getLevel();
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, CatalogBlocks.block("wand_workbench").defaultBlockState());
        var table = (FocalManipulatorBlockEntity)level.getBlockEntity(pos);
        var player = new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"focal_table"));
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5); player.experienceLevel = 5;
        KnowledgeStore.get(player).setResearchStage("BASEAUROMANCY",ResearchCatalog.get("BASEAUROMANCY").stages().size()+1);
        var menu = new FocalManipulatorMenu(1,player.getInventory(),table); player.containerMenu = menu;
        table.setItem(0,focus(1));
        return new Fixture(h,pos,table,player,menu);
    }
    private static ItemStack focus(int tier) { return CatalogModule.stack("focus_"+tier); }
    private static void crystals(Fixture f) {
        f.player.getInventory().setItem(9,AspectCrystalItem.create(Aspect.AVERSION,3));
        f.player.getInventory().setItem(10,AspectCrystalItem.create(Aspect.FIRE,3));
    }
    private static void edit(Fixture f, FocusGraph graph, String name) {
        result(f.helper,f.table.edit(f.player,f.table.revision(),graph.save(),name),FocalManipulatorResult.ACCEPTED);
    }
    private static void start(Fixture f) {
        result(f.helper,f.table.start(f.player,f.table.revision()),FocalManipulatorResult.ACCEPTED);
    }
    private static void ticks(Fixture f,int count) {
        for(int i=0;i<count;i++) FocalManipulatorBlockEntity.tick(f.helper.getLevel(),f.pos,f.table.getBlockState(),f.table);
    }
    private static void aura(Fixture f,BlockPos pos,float vis) {
        AuraManager.drainVis(f.helper.getLevel(),pos,32766,false);
        AuraManager.addVis(f.helper.getLevel(),pos,vis);
    }
    private static void result(GameTestHelper h,FocalManipulatorResult actual,FocalManipulatorResult expected) {
        h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);
    }

    @GameTest(template="empty") public static void workingTableOwnsSingleFocusNbtAndAllAutomationFaces(GameTestHelper h) {
        Fixture f=table(h); h.assertTrue(f.table.getType()==FocalManipulatorModule.TABLE.get()
                && f.table.getContainerSize()==1 && f.table.getMaxStackSize()==1
                && f.table.getRenderBoundingBox().equals(new AABB(f.pos.getX(),f.pos.getY(),f.pos.getZ(),f.pos.getX()+1,f.pos.getY()+2,f.pos.getZ()+1)),
                "Working table/type/inventory/render bounds missing");
        ItemStack input=focus(1); input.getOrCreateTag().putString("marker","owned"); f.table.setItem(0,input);
        input.getOrCreateTag().putString("marker","caller");
        ItemStack exposed=f.table.getItem(0); exposed.getOrCreateTag().putString("marker","read_alias"); exposed.shrink(1);
        h.assertTrue(f.table.getItem(0).getTag().getString("marker").equals("owned")&&f.table.getItem(0).getCount()==1,
                "Container reads/input expose mutable table state");
        var stored=f.table.saveWithoutMetadata();var reloaded=new FocalManipulatorBlockEntity(f.pos,f.table.getBlockState());reloaded.load(stored);
        stored.getCompound("Focus").getCompound("tag").putString("marker","saved_alias");
        f.menu.setState(f.table.clientSnapshot(f.player,null));f.menu.focus().getOrCreateTag().putString("marker","snapshot_alias");
        h.assertTrue(reloaded.getItem(0).getTag().getString("marker").equals("owned")&&f.menu.focus().getTag().getString("marker").equals("owned"),
                "ItemStack.of retained a saved/snapshot NBT alias");
        for(Direction side:Direction.values()) automation(h,f,side);
        automation(h,f,null); h.assertTrue(f.table.getUpdatePacket()!=null,"Real client update packet absent"); h.succeed();
    }
    private static void automation(GameTestHelper h,Fixture f,Direction side) {
        f.table.clearContent(); var handler=f.table.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow(IllegalStateException::new);
        var input=focus(1); input.setCount(3); input.getOrCreateTag().putString("marker","capability");
        h.assertTrue(handler.insertItem(0,input,true).getCount()==2&&f.table.isEmpty(),"Simulated insert mutated table");
        h.assertTrue(handler.insertItem(0,input,false).getCount()==2&&input.getCount()==3&&f.table.getItem(0).getCount()==1,
                "Automation inserted multiple foci/changed source");
        handler.getStackInSlot(0).getOrCreateTag().putString("marker","alias");
        h.assertTrue(f.table.getItem(0).getTag().getString("marker").equals("capability")
                &&handler.insertItem(0,new ItemStack(Items.DIAMOND),false).is(Items.DIAMOND),"Capability aliased NBT/accepted nonfocus");
        h.assertTrue(handler.extractItem(0,64,true).getCount()==1&&!f.table.isEmpty(),"Simulated extract mutated table");
        h.assertTrue(handler.extractItem(0,64,false).getTag().getString("marker").equals("capability")&&f.table.isEmpty(),"Extract lost one exact focus");
    }

    @GameTest(template="empty") public static void resourcePreflightReservesMainInventoryAndNeverChargesForMissingCrystals(GameTestHelper h) {
        Fixture f=table(h); edit(f,FocusGraph.touchFire(1,0),"Preflight");
        f.player.getInventory().setItem(9,AspectCrystalItem.create(Aspect.AVERSION,2));
        f.player.getInventory().offhand.set(0,AspectCrystalItem.create(Aspect.FIRE,3));
        f.player.getInventory().armor.set(0,AspectCrystalItem.create(Aspect.FIRE,3));
        long revision=f.table.revision(); var before=f.table.saveWithoutMetadata();
        result(h,f.table.start(f.player,revision),FocalManipulatorResult.MISSING_CRYSTALS);
        h.assertTrue(f.player.experienceLevel==5&&f.player.getInventory().getItem(9).getCount()==2
                &&before.equals(f.table.saveWithoutMetadata()),"Missing main-inventory crystal partially charged XP/state");
        var wrongAmount=AspectCrystalItem.create(Aspect.FIRE,3);
        ((AspectCrystalItem)wrongAmount.getItem()).setAspects(wrongAmount,new AspectList().add(Aspect.FIRE,2));
        f.player.getInventory().setItem(10,wrongAmount);
        result(h,f.table.start(f.player,revision),FocalManipulatorResult.MISSING_CRYSTALS);
        h.assertTrue(f.player.experienceLevel==5&&wrongAmount.getCount()==3,"Noncanonical Aspects amount entered payment");
        var valid=AspectCrystalItem.create(Aspect.FIRE,3);valid.getOrCreateTag().putString("custom","allowed_extra");
        f.player.getInventory().setItem(10,valid);start(f);
        h.assertTrue(f.player.experienceLevel==3&&f.player.getInventory().getItem(9).getCount()==1
                &&f.player.getInventory().getItem(10).getCount()==2&&f.player.getInventory().getItem(10).getTag().getString("custom").equals("allowed_extra")
                &&f.player.getInventory().offhand.get(0).getCount()==3&&f.player.getInventory().armor.get(0).getCount()==3,
                "Exact XP/two aspect debits/relaxed extra NBT/main-only reservations changed");h.succeed();
    }

    @GameTest(template="empty") public static void missingXpAndCreativeModeKeepOriginalSeparateCrystalCost(GameTestHelper h) {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"");f.player.experienceLevel=1;
        result(h,f.table.start(f.player,f.table.revision()),FocalManipulatorResult.MISSING_XP);
        h.assertTrue(f.player.experienceLevel==1&&f.player.getInventory().getItem(9).getCount()==3&&!f.table.crafting(),"Missing XP consumed crystals");
        f.player.getAbilities().instabuild=true; f.player.experienceLevel=0;f.player.getInventory().setItem(10,ItemStack.EMPTY);
        result(h,f.table.start(f.player,f.table.revision()),FocalManipulatorResult.MISSING_CRYSTALS);
        f.player.getInventory().setItem(10,AspectCrystalItem.create(Aspect.FIRE,3));start(f);
        h.assertTrue(f.player.experienceLevel==0&&f.player.getInventory().getItem(9).getCount()==2&&f.player.getInventory().getItem(10).getCount()==2,
                "Creative skipped original crystals or lost XP");h.succeed();
    }

    @GameTest(template="empty") public static void craftUsesTwentyTickTwentyVisCadenceAndFinishesExactlyOnce(GameTestHelper h) {
        Fixture f=table(h);crystals(f);var input=focus(1);input.getOrCreateTag().putString("marker","keep_after_payment");f.table.setItem(0,input);
        edit(f,FocusGraph.touchFire(1,0),"\u00a7Paid\n Focus");aura(f,f.pos,100);start(f);var started=f.table.clientSnapshot(f.player,null);ticks(f,19);
        h.assertTrue(f.table.remainingVis()==43&&AuraManager.getVis(h.getLevel(),f.pos)==100&&FocusStacks.readPlan(f.table.getItem(0)).isEmpty()
                &&started.equals(f.table.clientSnapshot(f.player,null)),"Focus consumed aura/completed before twentieth tick or synced an idle cadence every tick");
        ticks(f,1);h.assertTrue(f.table.remainingVis()==23&&AuraManager.getVis(h.getLevel(),f.pos)==80,"First debit is not20");
        ticks(f,20);h.assertTrue(f.table.remainingVis()==3&&AuraManager.getVis(h.getLevel(),f.pos)==60,"Second debit is not20");
        ticks(f,20);var output=f.table.getItem(0);var plan=FocusStacks.readPlan(output).orElseThrow();
        h.assertTrue(!f.table.crafting()&&f.table.graph().nodes().isEmpty()&&AuraManager.getVis(h.getLevel(),f.pos)==57
                &&plan.complexity()==4&&output.getHoverName().getString().equals("Paid Focus")&&output.getTag().getString("marker").equals("keep_after_payment")
                &&f.player.experienceLevel==3&&f.player.getInventory().getItem(9).getCount()==2,"Completion changed total43/NBT/name/payment");
        var finished=f.table.saveWithoutMetadata();ticks(f,100);
        h.assertTrue(ItemStack.matches(output,f.table.getItem(0))&&AuraManager.getVis(h.getLevel(),f.pos)==57
                &&!f.table.crafting()&&finished.getCompound("Focus").equals(f.table.saveWithoutMetadata().getCompound("Focus")),"Completed focus charged/wrote twice");h.succeed();
    }

    @GameTest(template="empty") public static void paidSaveReloadRetainsRemainingAuraAndExactCadenceWithoutPayingAgain(GameTestHelper h) {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"Saved");aura(f,f.pos,100);start(f);ticks(f,27);
        var saved=f.table.saveWithoutMetadata();var reloaded=new FocalManipulatorBlockEntity(f.pos,f.table.getBlockState());reloaded.load(saved);
        h.getLevel().getChunkAt(f.pos).addAndRegisterBlockEntity(reloaded);
        Fixture resumed=new Fixture(h,f.pos,reloaded,f.player,new FocalManipulatorMenu(2,f.player.getInventory(),reloaded));
        h.assertTrue(saved.equals(reloaded.saveWithoutMetadata())&&reloaded.remainingVis()==23,"Paid save changed graph/input/cadence/remaining vis");
        ticks(resumed,12);h.assertTrue(reloaded.remainingVis()==23&&AuraManager.getVis(h.getLevel(),f.pos)==80,"Reload reset/executed twenty-tick cadence early");
        ticks(resumed,1);h.assertTrue(reloaded.remainingVis()==3&&AuraManager.getVis(h.getLevel(),f.pos)==60,"Reload missed exact next cadence");
        ticks(resumed,20);h.assertTrue(!reloaded.crafting()&&FocusStacks.readPlan(reloaded.getItem(0)).isPresent()
                &&AuraManager.getVis(h.getLevel(),f.pos)==57&&f.player.experienceLevel==3&&f.player.getInventory().getItem(9).getCount()==2,
                "Reload paid XP/crystals/vis twice or lost completion");h.succeed();
    }

    @GameTest(template="empty") public static void absentAuraPausesPaidCraftAndPartialAuraNeverCreatesPrematureOutput(GameTestHelper h) {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"Paused");aura(f,f.pos,0);start(f);ticks(f,40);
        h.assertTrue(f.table.crafting()&&f.table.remainingVis()==43&&FocusStacks.readPlan(f.table.getItem(0)).isEmpty()&&f.player.experienceLevel==3,
                "No aura created output/refunded initial payment");
        AuraManager.addVis(h.getLevel(),f.pos,10);ticks(f,20);
        h.assertTrue(f.table.remainingVis()==33&&AuraManager.getVis(h.getLevel(),f.pos)==0&&FocusStacks.readPlan(f.table.getItem(0)).isEmpty(),"Partial aura debited/completed wrongly");
        AuraManager.addVis(h.getLevel(),f.pos,100);ticks(f,40);
        h.assertTrue(!f.table.crafting()&&FocusStacks.readPlan(f.table.getItem(0)).isPresent()&&AuraManager.getVis(h.getLevel(),f.pos)==67,
                "Paused focus could not resume exact remaining33");h.succeed();
    }

    @GameTest(template="empty") public static void shiftClickExtractionCancelsPaidCraftImmediatelyAndMovesOneExactInput(GameTestHelper h) {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"Cancel");aura(f,f.pos,100);start(f);
        var before=f.table.getItem(0);var moved=f.menu.quickMoveStack(f.player,0);
        h.assertTrue(ItemStack.matches(moved,before)&&f.table.isEmpty()&&!f.table.crafting()&&f.table.graph().nodes().isEmpty()
                &&f.player.getInventory().items.stream().filter(FocusStacks::isFocus).mapToInt(ItemStack::getCount).sum()==1,
                "Shift-click retained a paid plan/duplicated original focus");
        ticks(f,100);h.assertTrue(AuraManager.getVis(h.getLevel(),f.pos)==100&&f.player.experienceLevel==3
                &&f.player.getInventory().items.stream().filter(FocusStacks::isFocus).allMatch(stack->FocusStacks.readPlan(stack).isEmpty()),
                "Canceled shift-click manufactured a package or refunded/debited payment");h.succeed();
    }

    @GameTest(template="empty") public static void replacingPaidInputCancelsWithoutOverwritingReplacementOrConsumingAura(GameTestHelper h) {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"Old");aura(f,f.pos,100);start(f);
        var replacement=focus(2);replacement.getOrCreateTag().putString("marker","new_input");f.table.setItem(0,replacement);ticks(f,100);
        h.assertTrue(!f.table.crafting()&&ItemStack.matches(replacement,f.table.getItem(0))&&FocusStacks.readPlan(f.table.getItem(0)).isEmpty()
                &&AuraManager.getVis(h.getLevel(),f.pos)==100&&f.player.experienceLevel==3,"Old paid plan replaced/debited new input");h.succeed();
    }

    @GameTest(template="empty") public static void revisionedRequestsRejectStaleWrongMenuPositionAndDistantActionsWithoutPayment(GameTestHelper h) {
        Fixture f=table(h);crystals(f);long old=f.table.revision();edit(f,FocusGraph.touchFire(1,0),"Request");
        result(h,FocalManipulatorNetwork.process(f.menu,f.player,f.pos,old,FocalManipulatorNetwork.Action.START,new CompoundTag(),""),FocalManipulatorResult.STALE);
        result(h,FocalManipulatorNetwork.process(f.menu,f.player,f.pos.offset(1,0,0),f.table.revision(),FocalManipulatorNetwork.Action.START,new CompoundTag(),""),FocalManipulatorResult.LOCKED);
        var another=new FocalManipulatorMenu(2,f.player.getInventory(),f.table);
        result(h,FocalManipulatorNetwork.process(another,f.player,f.pos,f.table.revision(),FocalManipulatorNetwork.Action.START,new CompoundTag(),""),FocalManipulatorResult.LOCKED);
        f.player.setPos(f.pos.getX()+20,f.pos.getY(),f.pos.getZ());var before=f.table.saveWithoutMetadata();
        result(h,FocalManipulatorNetwork.process(f.menu,f.player,f.pos,f.table.revision(),FocalManipulatorNetwork.Action.START,new CompoundTag(),""),FocalManipulatorResult.LOCKED);
        f.menu.clicked(0,0,ClickType.PICKUP,f.player);f.menu.quickMoveStack(f.player,0);
        h.assertTrue(before.equals(f.table.saveWithoutMetadata())&&f.menu.getCarried().isEmpty()&&f.player.experienceLevel==5
                &&f.player.getInventory().getItem(9).getCount()==3,"Rejected/distant request mutated table/payment");h.succeed();
    }

    @GameTest(template="empty") public static void startedBasicResearchEmptySocketsAndUnsupportedNodesCannotStartPaidCraft(GameTestHelper h) {
        Fixture f=table(h);crystals(f);KnowledgeStore.get(f.player).setResearchStage("BASEAUROMANCY",1);
        result(h,f.table.edit(f.player,f.table.revision(),FocusGraph.touchFire(1,0).save(),""),FocalManipulatorResult.MISSING_RESEARCH);
        KnowledgeStore.get(f.player).setResearchStage("BASEAUROMANCY",ResearchCatalog.get("BASEAUROMANCY").stages().size()+1);
        var socket=new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,"ROOT",Map.of()),new FocusGraph.Node(1,0,List.of(),0,1,"",Map.of())));
        edit(f,socket,"");result(h,f.table.start(f.player,f.table.revision()),FocalManipulatorResult.INVALID);
        var unsupported=new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,"ROOT",Map.of()),
                new FocusGraph.Node(1,0,List.of(),0,1,"thaumcraft.CLOUD",Map.of())));
        result(h,f.table.edit(f.player,f.table.revision(),unsupported.save(),""),FocalManipulatorResult.UNSUPPORTED);
        var malformed=FocusGraph.touchFire(1,0).save();malformed.getList("nodes",Tag.TAG_COMPOUND).getCompound(2).putInt("setting.power",99);
        result(h,f.table.edit(f.player,f.table.revision(),malformed,""),FocalManipulatorResult.INVALID);
        h.assertTrue(!f.table.crafting()&&f.player.experienceLevel==5&&f.player.getInventory().getItem(9).getCount()==3
                &&f.table.graph().nodes().equals(socket.nodes()),"Rejected socket/research/late/malformed graph changed paid/editor state");h.succeed();
    }

    @GameTest(template="empty") public static void selfTargetAndRepeatedTouchCraftOriginalDuplicateCostsThroughRealTable(GameTestHelper h) {
        Fixture f=table(h);crystals(f);aura(f,f.pos,300);edit(f,FocusGraph.selfFire(1,0),"Self");start(f);ticks(f,40);
        var self=FocusStacks.readPlan(f.table.getItem(0)).orElseThrow();
        h.assertTrue(self.complexity()==2&&AuraManager.getVis(h.getLevel(),f.pos)==277&&f.player.experienceLevel==4
                &&f.player.getInventory().getItem(9).getCount()==3&&f.player.getInventory().getItem(10).getCount()==2,"Self-target invented a medium/aversio cost");
        var repeated=new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,"ROOT",Map.of()),
                new FocusGraph.Node(1,0,List.of(2),0,1,FocusNodeRegistry.TOUCH,Map.of()),new FocusGraph.Node(2,1,List.of(3),0,2,FocusNodeRegistry.TOUCH,Map.of()),
                new FocusGraph.Node(3,2,List.of(),0,3,FocusNodeRegistry.FIRE,Map.of("power",1,"duration",0))));
        edit(f,repeated,"Repeated");start(f);ticks(f,80);var plan=FocusStacks.readPlan(f.table.getItem(0)).orElseThrow();
        h.assertTrue(plan.complexity()==7&&AuraManager.getVis(h.getLevel(),f.pos)==204&&f.player.experienceLevel==1
                &&f.player.getInventory().getItem(9).getCount()==1&&f.player.getInventory().getItem(10).getCount()==1,"Repeated Touch lost73vis/3XP/two Aversion");h.succeed();
    }

    @GameTest(template="empty") public static void paidLoadRejectsTamperedPendingPriceInputGraphAndOversizedSavedFocus(GameTestHelper h) {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"Load");start(f);var saved=f.table.saveWithoutMetadata();
        List<CompoundTag> invalid=new ArrayList<>();
        for(float amount:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,44}) {var tag=saved.copy();tag.putFloat("RemainingVis",amount);invalid.add(tag);}
        var wrongPriceType=saved.copy();wrongPriceType.putInt("RemainingVis",1);invalid.add(wrongPriceType);
        var wrongInput=saved.copy();wrongInput.getCompound("PaidInput").getCompound("tag").putString("marker","not_current");invalid.add(wrongInput);
        // Blank input does not initially have a compound, so install one explicitly.
        var inputTag=wrongInput.getCompound("PaidInput");var custom=new CompoundTag();custom.putString("marker","not_current");inputTag.put("tag",custom);
        var wrongGraph=saved.copy();wrongGraph.getCompound("PaidGraph").getList("nodes",Tag.TAG_COMPOUND).getCompound(2).putInt("setting.power",99);invalid.add(wrongGraph);
        var wrongPaidCount=saved.copy();wrongPaidCount.getCompound("PaidInput").putInt("Count",1);invalid.add(wrongPaidCount);
        for(var tag:invalid) {var reloaded=new FocalManipulatorBlockEntity(f.pos,f.table.getBlockState());reloaded.load(tag);
            h.assertTrue(!reloaded.crafting()&&reloaded.remainingVis()==0&&FocusStacks.readPlan(reloaded.getItem(0)).isEmpty(),"Malformed persisted payment became executable");}
        for(boolean numericWrongType:new boolean[]{false,true}) {var tag=saved.copy();if(numericWrongType)tag.getCompound("Focus").putInt("Count",1);else tag.getCompound("Focus").putByte("Count",(byte)2);
            var reloaded=new FocalManipulatorBlockEntity(f.pos,f.table.getBlockState());reloaded.load(tag);h.assertTrue(reloaded.isEmpty()&&!reloaded.crafting(),"Oversized/wrong-type saved focus normalized into paid output");}
        h.assertTrue(f.player.experienceLevel==3&&f.player.getInventory().getItem(9).getCount()==2,"Reload fixture changed initial paid resources");h.succeed();
    }

    @GameTest(template="empty") public static void chargerSpreadsEachDebitEquallyAcrossNineLoadedChunks(GameTestHelper h) {
        Fixture f=table(h);List<BlockPos> positions=new ArrayList<>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {var pos=f.pos.offset(x*16,0,z*16);h.getLevel().getChunkAt(pos);aura(f,pos,9);positions.add(pos);}
        h.getLevel().setBlockAndUpdate(f.pos.above(),CatalogBlocks.block("arcane_workbench_charger").defaultBlockState());
        h.assertTrue(f.table.spendAura(9)==9&&positions.stream().allMatch(pos->AuraManager.getVis(h.getLevel(),pos)==8),"Charger did not debit1 in each of nine chunks");
        h.getLevel().setBlockAndUpdate(f.pos.above(),Blocks.AIR.defaultBlockState());
        h.assertTrue(f.table.spendAura(9)==8&&AuraManager.getVis(h.getLevel(),f.pos)==0
                &&positions.stream().filter(pos->!pos.equals(f.pos)).allMatch(pos->AuraManager.getVis(h.getLevel(),pos)==8),"No-charger table pulled neighbor vis");h.succeed();
    }

    @GameTest(template="empty") public static void authorityGuardsWorkerThreadAndCapabilityInvalidation(GameTestHelper h) throws InterruptedException {
        Fixture f=table(h);crystals(f);edit(f,FocusGraph.touchFire(1,0),"Thread");var before=f.table.saveWithoutMetadata();
        var old=f.table.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP);var handler=old.orElseThrow(IllegalStateException::new);
        AtomicBoolean guarded=new AtomicBoolean();Thread worker=new Thread(()->{
            var status=f.table.start(f.player,f.table.revision());f.table.setItem(0,focus(2));f.menu.clicked(0,0,ClickType.PICKUP,f.player);
            guarded.set(status==FocalManipulatorResult.LOCKED&&f.table.removeItem(0,1).isEmpty()&&handler.extractItem(0,1,false).isEmpty()
                    &&handler.insertItem(0,focus(2),false).getCount()==1);
        },"focal-authority-test");worker.start();worker.join(2000);
        h.assertTrue(!worker.isAlive()&&guarded.get()&&before.equals(f.table.saveWithoutMetadata())&&f.player.experienceLevel==5,"Off-thread menu/table/capability action mutated state");
        f.table.invalidateCaps();h.assertTrue(!old.isPresent(),"Removed table exposed live capability");f.table.reviveCaps();
        h.assertTrue(f.table.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).isPresent(),"Table capability did not revive");h.succeed();
    }

    @GameTest(template="empty") public static void breakingPaidTableDropsOneOriginalInputWithoutCreatingCompletedFocus(GameTestHelper h) {
        Fixture f=table(h);crystals(f);var input=focus(1);String proof=UUID.randomUUID().toString();input.getOrCreateTag().putString("proof",proof);f.table.setItem(0,input);
        edit(f,FocusGraph.touchFire(1,0),"Break");aura(f,f.pos,100);start(f);h.getLevel().setBlockAndUpdate(f.pos,Blocks.AIR.defaultBlockState());
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.pos).inflate(3),entity->entity.getItem().hasTag()&&entity.getItem().getTag().getString("proof").equals(proof));
        h.assertTrue(drops.size()==1&&ItemStack.matches(drops.get(0).getItem(),input)&&FocusStacks.readPlan(drops.get(0).getItem()).isEmpty()
                &&AuraManager.getVis(h.getLevel(),f.pos)==100&&f.player.experienceLevel==3,"Breaking duplicated/manufactured/refunded the paid input");
        drops.forEach(ItemEntity::discard);h.succeed();
    }
}
