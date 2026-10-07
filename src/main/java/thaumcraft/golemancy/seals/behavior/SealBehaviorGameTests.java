package thaumcraft.golemancy.seals.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.golemancy.entity.ThaumcraftGolemEntity;
import thaumcraft.golemancy.press.GolemDesign;
import thaumcraft.golemancy.seals.core.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Actual capabilities, paid carrying, Forge interactions and transient ticket/provision flows. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class SealBehaviorGameTests {
    private SealBehaviorGameTests() {}
    @GameTest(template="essentia_network")
    public static void allSixteenOriginalDefinitionsHaveExactTraitsFiltersAndDefaultToggles(GameTestHelper h) {
        h.assertTrue(SealBehaviors.all().stream().map(SealBehavior::key).toList().equals(SealRegistry.KEYS),"BETA26 sixteen seal order changed");
        for(String key:SealRegistry.KEYS)h.assertTrue(SealRegistry.behavior(key)!=null,"Runtime seal omitted: "+key);
        h.assertTrue(b("use").requiredTraits().equals(Set.of("DEFT","SMART"))&&b("harvest").requiredTraits().equals(Set.of("DEFT","SMART"))
                &&b("lumber").requiredTraits().equals(Set.of("BREAKER","SMART"))&&b("butcher").requiredTraits().equals(Set.of("FIGHTER","SMART")),"Required original tags changed");
        for(String key:List.of("empty","fill","pickup","stock","provider"))h.assertTrue(b(key).forbiddenTraits().equals(Set.of("CLUMSY")),"Clumsy restriction lost "+key);
        for(String key:List.of("empty_advanced","fill_advanced","pickup_advanced"))h.assertTrue(b(key).filterSlots()==9&&b(key).requiredTraits().equals(Set.of("SMART")),"Advanced filters or smart gate lost");
        h.assertTrue(b("guard_advanced").requiredTraits().equals(Set.of("FIGHTER","SMART"))&&b("breaker_advanced").requiredTraits().equals(Set.of("BREAKER","SMART"))
                &&b("guard").toggleDefaults().equals(java.util.Map.of("pmob",true,"panimal",false,"pplayer",false))&&b("harvest").toggleDefaults().get("prep"),"Advanced combat/mining or default flags changed");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void emptyPaysSidedInventoryIntoActualCarryingAndAdvancedCycleLeavesOne(GameTestHelper h) {
        ChestBlockEntity chest=chest(h,new BlockPos(2,1,2));chest.setItem(0,new ItemStack(Items.APPLE,5));chest.setItem(1,new ItemStack(Items.CARROT,6));
        var golem=golem(h,0,1,0);var seal=seal(h,"empty_advanced",new BlockPos(2,1,2));seal.blacklist(false);seal.filter(0,new ItemStack(Items.APPLE));seal.filter(1,new ItemStack(Items.CARROT));seal.toggle("pcycle",true);seal.toggle("pleave",true);
        var service=SealService.get(h.getLevel());b("empty_advanced").tick(h.getLevel(),seal,service);var first=latest(service,seal);h.assertTrue(first.lifespan()==5,"Empty ticket duration changed");
        finish(h,seal,golem,first);h.assertTrue(chest.getItem(0).getCount()==1&&golem.getCarrying().get(0).getCount()==4,"Leave one did not debit exact source and carry4");
        golem.dropItem(new ItemStack(Items.APPLE,4));seal.runtime().putInt("Delay",20);b("empty_advanced").tick(h.getLevel(),seal,service);finish(h,seal,golem,latest(service,seal));
        h.assertTrue(chest.getItem(1).getCount()==1&&golem.getCarrying().get(0).is(Items.CARROT)&&golem.getCarrying().get(0).getCount()==5,"Advanced cycle did not move to second filter");cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void fillLimiterCountsRealDestinationAndExistingOnlyRefusesAnEmptyContainer(GameTestHelper h) {
        var chest=chest(h,new BlockPos(2,1,2));chest.setItem(0,new ItemStack(Items.APPLE,4));var golem=golem(h,0,1,0);golem.holdItem(new ItemStack(Items.APPLE,6));
        var seal=seal(h,"fill_advanced",new BlockPos(2,1,2));seal.blacklist(false);seal.filter(0,new ItemStack(Items.APPLE));seal.filterSize(0,7);seal.toggle("pexist",true);var service=SealService.get(h.getLevel());
        b("fill_advanced").tick(h.getLevel(),seal,service);finish(h,seal,golem,latest(service,seal));
        h.assertTrue(chest.getItem(0).getCount()==7&&golem.getCarrying().get(0).getCount()==3&&golem.rankXp()==1,"Fill limiter duplicated or ignored existing inventory");
        chest.clearContent();var task=SealTask.block(seal.position(),seal.position().pos());h.assertTrue(!b("fill_advanced").canPerform(h.getLevel(),seal,golem,task),"Existing-only filled absent stack");cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void pickupAdvancedLeavesUncarriedRemainderOnTheRealGroundEntity(GameTestHelper h) {
        var seal=seal(h,"pickup_advanced",new BlockPos(2,1,2));var golem=golem(h,0,1,0);golem.holdItem(new ItemStack(Items.APPLE,50));
        ItemEntity item=new ItemEntity(h.getLevel(),seal.position().pos().getX()+.5,seal.position().pos().getY()+1.1,seal.position().pos().getZ()+.5,new ItemStack(Items.APPLE,30));item.setOnGround(true);item.setNoPickUpDelay();item.setNoGravity(true);h.getLevel().addFreshEntity(item);
        b("pickup_advanced").tick(h.getLevel(),seal,SealService.get(h.getLevel()));finish(h,seal,golem,latest(SealService.get(h.getLevel()),seal));
        h.assertTrue(golem.getCarrying().get(0).getCount()==64&&item.isAlive()&&item.getItem().getCount()==16,"Pickup lost or duplicated partial carry remainder");
        golem.dropItem(new ItemStack(Items.APPLE,64));seal.runtime().putInt("Delay",5);b("pickup_advanced").tick(h.getLevel(),seal,SealService.get(h.getLevel()));finish(h,seal,golem,latest(SealService.get(h.getLevel()),seal));
        h.assertTrue(item.isRemoved()&&golem.getCarrying().get(0).getCount()==16,"Exhausted ground item survived second pickup");cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void stockAndProviderPerformTwoPaidTasksWithNoFreeDestinationItems(GameTestHelper h) {
        var source=chest(h,new BlockPos(2,1,2));source.setItem(0,new ItemStack(Items.APPLE,9));var target=chest(h,new BlockPos(6,1,2));target.setItem(0,new ItemStack(Items.APPLE,2));
        var provider=seal(h,"provider",new BlockPos(2,1,2));var stock=seal(h,"stock",new BlockPos(6,1,2));stock.filter(0,new ItemStack(Items.APPLE));stock.filterSize(0,7);
        var golem=golem(h,0,0,0);var service=SealService.get(h.getLevel());b("stock").tick(h.getLevel(),stock,service);h.assertTrue(service.provisions().stream().anyMatch(p->stock.position().pos().equals(p.position())&&p.stack().getCount()==5),"Stock failed to request missing5");
        b("provider").tick(h.getLevel(),provider,service);SealTask fetch=latest(service,provider);finish(h,provider,golem,fetch);
        h.assertTrue(source.getItem(0).getCount()==4&&target.getItem(0).getCount()==2&&golem.getCarrying().get(0).getCount()==5,"Fetch did not pay source before travel");
        SealTask delivery=latest(service,provider);h.assertTrue(delivery.data()==2&&delivery.golemUUID().equals(golem.getUUID()),"Delivery was not reserved for the paid carrier");finish(h,provider,golem,delivery);
        h.assertTrue(target.getItem(0).getCount()==7&&golem.getCarrying().get(0).isEmpty()&&source.getItem(0).getCount()==4,"Stock delivery duplicated or lost paid items");cleanup(h,provider,golem);service.remove(stock.position(),true);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void providerSingleAndLeaveOneNeverExtractTheFinalStoredItem(GameTestHelper h) {
        var source=chest(h,new BlockPos(2,1,2));source.setItem(0,new ItemStack(Items.APPLE,2));chest(h,new BlockPos(6,1,2));var provider=seal(h,"provider",new BlockPos(2,1,2));provider.toggle("psing",true);provider.toggle("pleave",true);var golem=golem(h,0,0,0);var service=SealService.get(h.getLevel());
        service.requestProvision(h.absolutePos(new BlockPos(6,1,2)),Direction.UP,new ItemStack(Items.APPLE,3),19);b("provider").tick(h.getLevel(),provider,service);finish(h,provider,golem,latest(service,provider));
        h.assertTrue(source.getItem(0).getCount()==1&&golem.getCarrying().get(0).getCount()==1,"Provider single/leave-one changed");var delivery=latest(service,provider);finish(h,provider,golem,delivery);
        provider.runtime().putInt("Delay",20);long count=service.tasks().stream().filter(t->!t.suspended()).count();b("provider").tick(h.getLevel(),provider,service);
        h.assertTrue(source.getItem(0).getCount()==1&&service.tasks().stream().filter(t->!t.suspended()).count()==count,"Provider extracted the last item");cleanup(h,provider,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void useRunsActualLeverInteractionAndReturnsBorrowedItems(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(pos,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR),3);
        var seal=seal(h,"use",new BlockPos(2,1,2));seal.toggle("pemptyhand",true);var golem=golem(h,3,1,1);var service=SealService.get(h.getLevel());
        b("use").tick(h.getLevel(),seal,service);finish(h,seal,golem,latest(service,seal));h.assertTrue(h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED),"Use seal did not call vanilla block interaction");
        seal.toggle("pemptyhand",false);golem.holdItem(new ItemStack(Items.APPLE,5));seal.runtime().putInt("Delay",5);b("use").tick(h.getLevel(),seal,service);finish(h,seal,golem,latest(service,seal));
        h.assertTrue(!h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED)&&golem.getCarrying().get(0).getCount()==5,"Use lost borrowed stack or skipped second lever click");
        seal.toggle("pemptyhand",true);seal.runtime().putInt("Delay",10);b("use").tick(h.getLevel(),seal,service);finish(h,seal,golem,latest(service,seal));
        h.assertTrue(golem.getCarrying().get(0).isEmpty()&&h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED),"Confirmed BETA26 empty-hand discards-selected-stack quirk was silently refunded");cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void breakerAdvancedHasSlowerSilkTicksAndForgeCancellationProtectsTheBlock(GameTestHelper h) {
        var seal=seal(h,"breaker_advanced",new BlockPos(2,1,2));seal.toggle("psilk",true);var golem=golem(h,1,1,3);BlockPos target=h.absolutePos(new BlockPos(3,1,2));h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),3);
        var task=SealTask.block(seal.position(),target).data(15);var behavior=b("breaker_advanced");h.assertTrue(!behavior.onCompletion(h.getLevel(),seal,golem,task)&&task.data()==8,"Silk mining must decrement7 and continue");
        h.assertTrue(!behavior.onCompletion(h.getLevel(),seal,golem,task)&&task.data()==1,"Silk second mining step changed");h.assertTrue(behavior.onCompletion(h.getLevel(),seal,golem,task)&&h.getLevel().isEmptyBlock(target),"Silk terminal mining failed");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(target).inflate(.9)).stream().anyMatch(e->e.getItem().is(Items.STONE)),"Silk seal dropped cobblestone instead of stone");
        h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),3);CancelBreak cancel=new CancelBreak(h.getLevel(),target);MinecraftForge.EVENT_BUS.register(cancel);
        try{behavior.onCompletion(h.getLevel(),seal,golem,SealTask.block(seal.position(),target).data(1));h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.STONE),"Forge break veto bypassed");}finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
        cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void breakerRetainsOriginalAndWhitelistRatherThanNormalItemOrMatching(GameTestHelper h) {
        var seal=seal(h,"breaker_advanced",new BlockPos(2,1,2));seal.blacklist(false);seal.filter(0,new ItemStack(Items.STONE));seal.filter(1,new ItemStack(Items.DIRT));var golem=golem(h,1,1,3);BlockPos pos=h.absolutePos(new BlockPos(3,1,2));h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!b("breaker_advanced").canPerform(h.getLevel(),seal,golem,SealTask.block(seal.position(),pos)),"Pinned AND whitelist silently changed to OR");seal.filter(1,ItemStack.EMPTY);
        h.assertTrue(b("breaker_advanced").canPerform(h.getLevel(),seal,golem,SealTask.block(seal.position(),pos)),"Single matching whitelist refused valid stone");cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void lumberBreaksTheFurthestMatchingLogThenReturnsForThePaidBase(GameTestHelper h) {
        var seal=seal(h,"lumber",new BlockPos(2,1,2));var golem=golem(h,1,1,3);BlockPos base=h.absolutePos(new BlockPos(3,1,2));h.getLevel().setBlock(base,Blocks.OAK_LOG.defaultBlockState(),3);h.getLevel().setBlock(base.above(),Blocks.OAK_LOG.defaultBlockState(),3);h.getLevel().setBlock(base.above(2),Blocks.OAK_LOG.defaultBlockState(),3);h.getLevel().setBlock(base.east(),Blocks.BIRCH_LOG.defaultBlockState(),3);
        var task=SealTask.block(seal.position(),base);h.assertTrue(!b("lumber").onCompletion(h.getLevel(),seal,golem,task)&&h.getLevel().isEmptyBlock(base.above(2))&&h.getLevel().getBlockState(base).is(Blocks.OAK_LOG),"Lumber removed base before furthest matching log");
        b("lumber").onCompletion(h.getLevel(),seal,golem,task);b("lumber").onCompletion(h.getLevel(),seal,golem,task);
        h.assertTrue(h.getLevel().isEmptyBlock(base)&&h.getLevel().getBlockState(base.east()).is(Blocks.BIRCH_LOG)&&golem.rankXp()==3,"Lumber crossed species or lost three work XP");cleanup(h,seal,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void harvestPersistsItsSeedInstructionButReplantRequiresOneActualCarriedSeed(GameTestHelper h) {
        var seal=seal(h,"harvest",new BlockPos(2,1,2));var golem=golem(h,3,1,1);BlockPos crop=h.absolutePos(new BlockPos(3,1,2));
        h.getLevel().setBlock(crop.above(2),Blocks.GLOWSTONE.defaultBlockState(),3);
        // Real seed placement checks wheat survival; new structure lighting is asynchronous.
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getRawBrightness(crop,0)>=8,"Physical crop lamp has not propagated enough light for seed placement"))
                .thenExecute(()->{
                    h.getLevel().setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);
                    h.getLevel().setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);
                    h.assertTrue(h.getLevel().getBlockState(crop).canSurvive(h.getLevel(),crop),"Wheat fixture lacks valid light or farmland");
                    var task=SealTask.block(seal.position(),crop);b("harvest").onCompletion(h.getLevel(),seal,golem,task);h.assertTrue(h.getLevel().isEmptyBlock(crop),"Harvest did not remove grown wheat");
                    CompoundTag saved=seal.save();h.assertTrue(saved.getList("replant",Tag.TAG_COMPOUND).size()==1,"Paid harvest replant instructions were not persisted");SealData loaded=SealData.load(saved);
                    h.assertTrue(loaded!=null&&loaded.runtime().getList("replant",Tag.TAG_COMPOUND).size()==1&&!loaded.runtime().getList("replant",Tag.TAG_COMPOUND).getCompound(0).contains("Task"),"Reload retained live ticket or lost seed descriptor");
                    SealTask next=latest(SealService.get(h.getLevel()),seal);h.assertTrue(!b("harvest").canPerform(h.getLevel(),seal,golem,next)&&h.getLevel().isEmptyBlock(crop),"Replant created free seeds");golem.holdItem(new ItemStack(Items.WHEAT_SEEDS,2));finish(h,seal,golem,next);
                    h.assertTrue(h.getLevel().getBlockState(crop).is(Blocks.WHEAT)&&h.getLevel().getBlockState(crop).getValue(CropBlock.AGE)==0&&golem.getCarrying().get(0).getCount()==1,"Actual seed debit/replant failed: block="+h.getLevel().getBlockState(crop)+" carried="+golem.getCarrying());cleanup(h,seal,golem);
                }).thenSucceed();
    }
    @GameTest(template="essentia_network")
    public static void guardAndButcherAssignActualTargetsAndKeepTwoAdultAnimals(GameTestHelper h) {
        var guard=seal(h,"guard",new BlockPos(2,1,2));var golem=golem(h,0,0,0);golem.setProps(GolemDesign.create(0,0,0,0,2).orElseThrow().props());
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setNoAi(true);zombie.setNoGravity(true);zombie.setPos(guard.position().pos().getX()+.5,guard.position().pos().getY()+1,guard.position().pos().getZ()+.5);h.getLevel().addFreshEntity(zombie);
        b("guard").tick(h.getLevel(),guard,SealService.get(h.getLevel()));var ticket=latest(SealService.get(h.getLevel()),guard);b("guard").onStarted(h.getLevel(),guard,golem,ticket);
        h.assertTrue(golem.getTarget()==zombie&&ticket.suspended(),"Guard did not set real attack target");golem.setTarget(null);zombie.discard();SealService.get(h.getLevel()).remove(guard.position(),true);
        var butcher=seal(h,"butcher",new BlockPos(2,1,2));golem.setProps(GolemDesign.create(0,1,2,0,0).orElseThrow().props());java.util.ArrayList<net.minecraft.world.entity.animal.Cow> cows=new java.util.ArrayList<>();
        for(int i=0;i<2;i++){var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(butcher.position().pos().getX()+.3+i*.3,butcher.position().pos().getY()+1,butcher.position().pos().getZ()+.5);h.getLevel().addFreshEntity(cow);cows.add(cow);}
        b("butcher").tick(h.getLevel(),butcher,SealService.get(h.getLevel()));h.assertTrue(SealService.get(h.getLevel()).tasks().stream().noneMatch(t->butcher.position().equals(t.sealPosition())&&!t.suspended()),"Butcher killed one of only two adults");
        var calf=EntityType.COW.create(h.getLevel());calf.setAge(-24000);calf.setNoAi(true);calf.setNoGravity(true);calf.setPos(cows.get(0).getX(),cows.get(0).getY(),cows.get(0).getZ());h.getLevel().addFreshEntity(calf);
        butcher.runtime().putInt("Delay",200);b("butcher").tick(h.getLevel(),butcher,SealService.get(h.getLevel()));h.assertTrue(!butcher.runtime().getBoolean("Wait"),"Decompiler artifact counted a calf as the third adult");
        calf.setAge(0);butcher.runtime().putInt("Delay",400);b("butcher").tick(h.getLevel(),butcher,SealService.get(h.getLevel()));var kill=latest(SealService.get(h.getLevel()),butcher);b("butcher").onStarted(h.getLevel(),butcher,golem,kill);
        h.assertTrue(golem.getTarget()!=null&&!golem.getTarget().isBaby()&&!butcher.runtime().getBoolean("Wait"),"Third adult did not permit butcher work");for(var cow:cows)cow.discard();calf.discard();cleanup(h,butcher,golem);h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void extractCallbackFailureRestoresTheExactSourceBeforeAnyCarryingPayment(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
        ItemStackHandler broken=new ItemStackHandler(2){@Override public ItemStack extractItem(int slot,int amount,boolean simulate){ItemStack result=super.extractItem(slot,amount,simulate);if(!simulate)throw new IllegalStateException("fixture callback after extraction");return result;}};
        broken.setStackInSlot(0,new ItemStack(Items.APPLE,2));broken.setStackInSlot(1,new ItemStack(Items.APPLE,3));h.getLevel().setBlockEntity(new CapabilityChest(pos,broken));var golem=golem(h,0,0,0);
        h.assertTrue(SealInventory.take(h.getLevel(),new SealPos(pos,Direction.UP),golem,new ItemStack(Items.APPLE),5)==0&&broken.getStackInSlot(0).getCount()==2&&broken.getStackInSlot(1).getCount()==3&&golem.getCarrying().get(0).isEmpty(),"Callback failure lost or duplicated partial extraction");golem.discard();h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void insertionSimulationAndCommitCallbacksCannotCreateFreeItemsOrConsumeTheCarrier(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var golem=golem(h,0,0,0);golem.holdItem(new ItemStack(Items.APPLE,5));
        ItemStackHandler simulation=new ItemStackHandler(1){@Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){if(simulate)setStackInSlot(slot,new ItemStack(Items.DIAMOND));return super.insertItem(slot,stack,simulate);}};h.getLevel().setBlockEntity(new CapabilityChest(pos,simulation));
        h.assertTrue(SealInventory.room(simulation,new ItemStack(Items.APPLE,5))==0&&simulation.getStackInSlot(0).isEmpty()&&golem.getCarrying().get(0).getCount()==5,"Insertion preview callback mutated physical inventory");
        ItemStackHandler commit=new ItemStackHandler(1){@Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){ItemStack remainder=super.insertItem(slot,stack,simulate);if(!simulate)throw new IllegalStateException("fixture callback after insertion");return remainder;}};h.getLevel().setBlockEntity(new CapabilityChest(pos,commit));
        h.assertTrue(SealInventory.deliver(h.getLevel(),new SealPos(pos,Direction.UP),golem,new ItemStack(Items.APPLE,5),5)==0&&commit.getStackInSlot(0).isEmpty()&&golem.getCarrying().get(0).getCount()==5,"Commit callback lost carrier or duplicated inventory");golem.discard();h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void harvestIncludesOriginalModConfigFruitWartAndStackedFamiliesButNeverStems(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(3,1,2));
        for(var block:List.of(Blocks.MELON,Blocks.PUMPKIN)){h.getLevel().setBlock(pos,block.defaultBlockState(),3);h.assertTrue(SealWorld.grown(h.getLevel(),pos),"Original explicitly registered fruit omitted");}
        h.getLevel().setBlock(pos.below(),Blocks.SOUL_SAND.defaultBlockState(),3);h.getLevel().setBlock(pos,Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,2),3);h.assertTrue(!SealWorld.grown(h.getLevel(),pos),"Immature wart selected");h.getLevel().setBlock(pos,Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,3),3);h.assertTrue(SealWorld.grown(h.getLevel(),pos)&&SealWorld.seed(h.getLevel().getBlockState(pos)).is(Items.NETHER_WART),"Mature wart or its actual seed omitted");
        h.getLevel().setBlock(pos.below(),Blocks.SAND.defaultBlockState(),3);h.getLevel().setBlock(pos,Blocks.CACTUS.defaultBlockState(),3);h.assertTrue(!SealWorld.grown(h.getLevel(),pos),"Stacked crop base was selected");h.getLevel().setBlock(pos.below(),Blocks.CACTUS.defaultBlockState(),3);h.assertTrue(SealWorld.grown(h.getLevel(),pos),"Second cactus block omitted");
        h.getLevel().setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);h.getLevel().setBlock(pos,Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,7),3);h.assertTrue(!SealWorld.grown(h.getLevel(),pos),"Mature stem was harvested instead of fruit");h.succeed();
    }
    private static SealBehavior b(String key){return SealBehaviors.get(key);}
    private static SealData seal(GameTestHelper h,String type,BlockPos pos){SealBehaviors.register();BlockPos actual=h.absolutePos(pos);if(h.getLevel().isEmptyBlock(actual))h.getLevel().setBlock(actual,Blocks.STONE.defaultBlockState(),3);SealData seal=new SealData(new SealPos(actual,Direction.UP),"thaumcraft:"+type,UUID.randomUUID());h.assertTrue(SealService.get(h.getLevel()).add(seal),"Fixture cannot install seal");return seal;}
    private static ChestBlockEntity chest(GameTestHelper h,BlockPos pos){BlockPos actual=h.absolutePos(pos);h.getLevel().setBlock(actual,Blocks.CHEST.defaultBlockState(),3);ChestBlockEntity tile=(ChestBlockEntity)h.getLevel().getBlockEntity(actual);h.assertTrue(tile!=null,"Missing real fixture chest");return tile;}
    private static ThaumcraftGolemEntity golem(GameTestHelper h,int material,int head,int arms){var entity=VisualEntitiesModule.GOLEM.get().create(h.getLevel());entity.setProps(GolemDesign.create(material,head,arms,0,0).orElseThrow().props());entity.setOwnerId(UUID.randomUUID());entity.setValidSpawn();entity.setNoAi(true);entity.setNoGravity(true);BlockPos at=h.absolutePos(new BlockPos(4,1,3));entity.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);entity.setHome(at);h.assertTrue(h.getLevel().addFreshEntity(entity),"Cannot add operational fixture golem");return entity;}
    private static SealTask latest(SealService service,SealData seal){return service.tasks().stream().filter(t->t.sealPosition().equals(seal.position())&&!t.suspended()&&!t.completed()).reduce((a,b)->b).orElseThrow();}
    private static void finish(GameTestHelper h,SealData seal,ThaumcraftGolemEntity golem,SealTask task){h.assertTrue(b(seal.type()).canPerform(h.getLevel(),seal,golem,task),"Paid ticket not performable: "+seal.type()+" data="+task.data());h.assertTrue(b(seal.type()).onCompletion(h.getLevel(),seal,golem,task),"One-step ticket did not complete");}
    private static void cleanup(GameTestHelper h,SealData seal,ThaumcraftGolemEntity golem){SealService.get(h.getLevel()).remove(seal.position(),true);golem.discard();}
    private static final class CancelBreak {private final net.minecraft.server.level.ServerLevel level;private final BlockPos pos;private CancelBreak(net.minecraft.server.level.ServerLevel level,BlockPos pos){this.level=level;this.pos=pos;}@SubscribeEvent public void cancel(BlockEvent.BreakEvent event){if(event.getLevel()==level&&event.getPos().equals(pos))event.setCanceled(true);}}
    private static final class CapabilityChest extends ChestBlockEntity {
        private final LazyOptional<net.minecraftforge.items.IItemHandler> endpoint;
        private CapabilityChest(BlockPos pos,ItemStackHandler handler){super(pos,Blocks.CHEST.defaultBlockState());endpoint=LazyOptional.of(()->handler);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){return capability==ForgeCapabilities.ITEM_HANDLER?endpoint.cast():super.getCapability(capability,side);}
    }
}
