package thaumcraft.golemancy.seals.core;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.golemancy.entity.ThaumcraftGolemEntity;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import java.util.*;

/** Physical seal/menu fixtures. Direct queue tests pause the real golem AI; behavior suites test actual travel. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class SealCoreGameTests {
    private static final String TEMPLATE="essentia_network";
    private static final BlockPos CENTER=new BlockPos(4,2,4);
    private SealCoreGameTests(){}
    private static FakePlayer player(GameTestHelper helper){FakePlayer player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"seal_qa"));player.setGameMode(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(helper.absolutePos(CENTER)).add(0,0,2));return player;}
    private static SealData seal(GameTestHelper helper,FakePlayer player,BlockPos relative,String type){helper.setBlock(relative,Blocks.CHEST);SealData seal=new SealData(new SealPos(helper.absolutePos(relative),Direction.UP),"thaumcraft:"+type,player.getUUID());seal.locked(true);helper.assertTrue(SealService.get(helper.getLevel()).add(seal),"Real seal face registration failed");return seal;}
    private static ThaumcraftGolemEntity golem(GameTestHelper helper,FakePlayer player){ThaumcraftGolemEntity golem=VisualEntitiesModule.GOLEM.get().create(helper.getLevel());golem.setProps(0);golem.setOwnerId(player.getUUID());golem.setValidSpawn();golem.setHome(helper.absolutePos(CENTER));golem.setPos(Vec3.atCenterOf(helper.absolutePos(CENTER)));golem.setNoAi(true);helper.getLevel().addFreshEntity(golem);return golem;}
    private static void cleanup(GameTestHelper helper,FakePlayer player){SealService service=SealService.get(helper.getLevel());for(SealData seal:service.seals())if(seal.owner().equals(player.getUUID()))service.remove(seal.position(),true);}
    private static void done(GameTestHelper helper,FakePlayer player){cleanup(helper,player);helper.succeed();}
    private static void research(ServerPlayer player,String key,int stage){try{var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(player),key,stage);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static UseOnContext context(FakePlayer player,SealPos position){return new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(position.pos().getCenter(),position.face(),position.pos(),false));}

    @GameTest(template=TEMPLATE)
    public static void originalSixteenSealTypesAndBlankFlattenedItemsRemainStable(GameTestHelper helper){
        helper.assertTrue(SealRegistry.behaviors().size()==16&&SealRegistry.KEYS.size()==16,"BETA26 contains sixteen registered types, not seventeen types");
        helper.assertTrue(SealRegistry.keyForMetadata(0)==null&&SealRegistry.keyForMetadata(17)==null,"Blank/out-of-range became a seal type");
        for(int i=1;i<=16;i++){String key=SealRegistry.keyForMetadata(i);ItemStack item=SealRegistry.stack(key);helper.assertTrue(!item.isEmpty()&&item.getItem() instanceof ItemSealPlacer&&((ItemSealPlacer)item.getItem()).spec().legacyMetadata()==i,"Original order/model item metadata moved at "+i);}
        var player=player(helper);helper.setBlock(CENTER,Blocks.STONE);player.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("seal_blank"));
        helper.assertTrue(player.getMainHandItem().getItem().onItemUseFirst(player.getMainHandItem(),context(player,new SealPos(helper.absolutePos(CENTER),Direction.UP)))==net.minecraft.world.InteractionResult.PASS,"Blank seal item should pass");done(helper,player);
    }
    @GameTest(template=TEMPLATE)
    public static void sealPlacementPaysOncePreservesRealBlockAndRejectsInvalidEndpoints(GameTestHelper helper){
        var player=player(helper);helper.setBlock(CENTER,Blocks.CHEST);SealPos pos=new SealPos(helper.absolutePos(CENTER),Direction.UP);ItemStack held=CatalogModule.stack("seal_empty");held.setCount(3);player.setItemInHand(InteractionHand.MAIN_HAND,held);
        helper.assertTrue(SealRegistry.place(player,pos,"thaumcraft:empty",held)&&held.getCount()==2,"Physical seal placement did not pay exactly one item");
        helper.assertTrue(!SealRegistry.place(player,pos,"thaumcraft:empty",held)&&held.getCount()==2,"Duplicate face paid again");
        helper.assertTrue(helper.getLevel().getBlockState(pos.pos()).is(Blocks.CHEST)&&helper.getLevel().getBlockEntity(pos.pos()) instanceof ChestBlockEntity,"Seal replaced/lost its ordinary source inventory");
        helper.assertTrue(!SealRegistry.place(player,new SealPos(pos.pos().above(),Direction.UP),"thaumcraft:empty",held)&&held.getCount()==2,"Empty seal accepted a nonexistent item endpoint");
        helper.assertTrue(SealService.get(helper.getLevel()).seal(pos).owner().equals(player.getUUID()),"Placement lost original owner UUID");
        player.setGameMode(GameType.CREATIVE);helper.assertTrue(SealRegistry.place(player,new SealPos(pos.pos(),Direction.NORTH),"thaumcraft:empty",held)&&held.getCount()==2,"Creative placement consumed a seal");done(helper,player);
    }
    @GameTest(template=TEMPLATE)
    public static void sealGhostFiltersNeverTakeCursorItemsAndRejectStaleOrRemotePackets(GameTestHelper helper){
        var player=player(helper);SealData seal=seal(helper,player,CENTER,"empty_advanced");SealMenu menu=new SealMenu(21,player.getInventory(),seal);player.containerMenu=menu;
        ItemStack cursor=new ItemStack(Items.DIAMOND,41);cursor.getOrCreateTag().putString("Marker","exact ghost");menu.setCarried(cursor);CompoundTag before=cursor.save(new CompoundTag());int revision=menu.revision();
        helper.assertTrue(SealNetwork.process(player,new SealNetwork.Filter(menu.containerId,revision,0,0,false)),"Scoped ghost cursor copy rejected");
        helper.assertTrue(seal.filter(0).getCount()==1&&seal.filter(0).getTag().getString("Marker").equals("exact ghost")&&menu.getCarried().save(new CompoundTag()).equals(before),"Ghost copied ownership/count incorrectly or consumed cursor");
        helper.assertTrue(!SealNetwork.process(player,new SealNetwork.Filter(menu.containerId,revision,1,0,false))&&seal.filter(1).isEmpty(),"Stale revision edited another filter");
        menu.clicked(0,1,ClickType.PICKUP,player);helper.assertTrue(seal.filter(0).isEmpty()&&menu.getCarried().save(new CompoundTag()).equals(before),"Right click cleared actual cursor contents");
        player.setPos(player.position().add(20,0,0));helper.assertTrue(!SealNetwork.process(player,new SealNetwork.Button(menu.containerId,menu.revision(),81))&&seal.priority()==0,"Remote menu packet changed seal");done(helper,player);
    }
    @GameTest(template=TEMPLATE)
    public static void sealOwnerLockRedstoneAndOriginalPriorityColorAreaBoundsAreServerChecked(GameTestHelper helper){
        var owner=player(helper);SealData seal=seal(helper,owner,CENTER,"pickup_advanced");SealMenu menu=new SealMenu(22,owner.getInventory(),seal);owner.containerMenu=menu;
        for(int i=0;i<12;i++)menu.button(owner,menu.revision(),81);for(int i=0;i<20;i++)menu.button(owner,menu.revision(),83);for(int i=0;i<12;i++)menu.button(owner,menu.revision(),91);
        helper.assertTrue(seal.priority()==5&&seal.color()==16&&seal.area().getY()==8,"Original +/-5, 0..16 and 1..8 limits changed");
        int index=menu.categories().indexOf(0);menu.button(owner,menu.revision(),index);helper.assertTrue(menu.button(owner,menu.revision(),26)&&menu.button(owner,menu.revision(),27)&&!seal.locked()&&seal.redstone(),"Owner configuration rejected");
        var other=player(helper);SealMenu foreign=new SealMenu(23,other.getInventory(),seal);other.containerMenu=foreign;foreign.button(other,foreign.revision(),foreign.categories().indexOf(0));
        helper.assertTrue(foreign.button(other,foreign.revision(),80)&&seal.priority()==4,"Original non-owner priority editing was lost");
        helper.assertTrue(!foreign.button(other,foreign.revision(),25)&&!foreign.button(other,foreign.revision(),28)&&!seal.locked()&&seal.redstone(),"Non-owner changed owner-only lock/redstone");
        done(helper,owner);
    }
    @GameTest(template=TEMPLATE)
    public static void filterMetadataNbtModTagsAndEmptyBlacklistSemanticsMatchOriginal(GameTestHelper helper){
        var owner=player(helper);SealData seal=seal(helper,owner,CENTER,"empty_advanced");ItemStack worn=new ItemStack(Items.IRON_PICKAXE);worn.setDamageValue(4);worn.getOrCreateTag().putString("Owner","one");seal.filter(0,worn);
        helper.assertTrue(!seal.matches(worn)&&seal.matches(new ItemStack(Items.STONE)),"Blacklist inclusion/empty handling changed");seal.blacklist(false);
        ItemStack other=worn.copy();other.setDamageValue(5);helper.assertTrue(!seal.matches(other),"Default metadata gate ignored wear");seal.toggle("pmeta",false);helper.assertTrue(seal.matches(other),"Ignoring legacy damage still compared modern Damage NBT");
        other.getOrCreateTag().putString("Owner","two");helper.assertTrue(!seal.matches(other),"Default exact NBT became relaxed");seal.toggle("pnbt",false);helper.assertTrue(seal.matches(other),"Ignore NBT did not work");
        seal.filter(0,CatalogModule.stack("seal_pickup"));seal.toggle("pnbt",true);helper.assertTrue(seal.matches(CatalogModule.stack("seal_empty")),"Flattened original metadata variants lost ignore-meta matching");seal.toggle("pmeta",true);helper.assertTrue(!seal.matches(CatalogModule.stack("seal_empty")),"Default metadata mixed separate seal forms");
        seal.toggle("pmod",true);helper.assertTrue(seal.matches(CatalogModule.stack("focus_1"))&&!seal.matches(new ItemStack(Items.STONE)),"Mod filter did not use registry domain");
        seal.toggle("pmod",false);seal.filter(0,new ItemStack(Items.IRON_ORE));seal.toggle("pore",true);helper.assertTrue(seal.matches(new ItemStack(Items.DEEPSLATE_IRON_ORE))&&!seal.matches(new ItemStack(Items.GOLD_ORE)),"Forge material-specific ore tags did not adapt OreDictionary groups");
        for(int i=0;i<seal.filters().size();i++)seal.filter(i,ItemStack.EMPTY);helper.assertTrue(!seal.matches(new ItemStack(Items.STONE)),"Empty whitelist accepted everything");seal.blacklist(true);helper.assertTrue(seal.matches(new ItemStack(Items.STONE)),"Empty blacklist rejected everything");done(helper,owner);
    }
    @GameTest(template=TEMPLATE)
    public static void savedSealRoundTripKeepsDetachedSettingsButDropsTransientQueues(GameTestHelper helper){
        var owner=player(helper);SealData seal=seal(helper,owner,CENTER,"stock");seal.priority(-4);seal.color(9);seal.redstone(true);seal.blacklist(false);ItemStack tagged=new ItemStack(Items.DIAMOND,64);tagged.getOrCreateTag().putString("Label","original");seal.filter(0,tagged);seal.filterSize(0,32768);seal.toggle("pnbt",false);seal.runtime().putInt("Delay",77);
        SealService service=SealService.get(helper.getLevel());service.addTask(SealTask.block(seal.position(),seal.position().pos()));service.requestProvision(seal,new ItemStack(Items.DIAMOND,3));CompoundTag saved=service.save(new CompoundTag());SealService restored=SealService.load(saved);SealData copy=restored.seal(seal.position());
        helper.assertTrue(copy!=null&&copy.owner().equals(owner.getUUID())&&copy.priority()==-4&&copy.color()==9&&copy.redstone()&&copy.locked()&&!copy.blacklist()&&copy.filterSize(0)==32768&&!copy.toggle("pnbt"),"Original owner/settings/large ghost limit were lost");
        helper.assertTrue(restored.tasks().isEmpty()&&restored.provisions().isEmpty()&&!copy.runtime().contains("Delay"),"Transient task/counter state was improperly saved");
        ItemStack returned=copy.filters().get(0);returned.getOrCreateTag().putString("Label","changed");helper.assertTrue(copy.filter(0).getTag().getString("Label").equals("original"),"Filter snapshot shares mutable NBT");
        CompoundTag bad=seal.save();bad.putByte("face",(byte)127);helper.assertTrue(SealData.load(bad)==null,"Malformed facing crashed/created a seal");done(helper,owner);
    }
    @GameTest(template=TEMPLATE)
    public static void tasksUseOriginalPriorityDistanceExclusiveClaimsColorAndOwnerTraits(GameTestHelper helper){
        var owner=player(helper);SealData near=seal(helper,owner,new BlockPos(3,2,4),"fill"),far=seal(helper,owner,new BlockPos(7,2,4),"fill");ThaumcraftGolemEntity first=golem(helper,owner),second=golem(helper,owner);first.holdItem(new ItemStack(Items.STONE,4));second.holdItem(new ItemStack(Items.STONE,4));
        SealService service=SealService.get(helper.getLevel());SealTask low=SealTask.block(near.position(),near.position().pos()).priority(0),high=SealTask.block(far.position(),far.position().pos()).priority(1);service.addTask(low);service.addTask(high);
        helper.assertTrue(service.sorted(first,0).get(0)==high,"Priority no longer subtracts 256 from squared distance");helper.assertTrue(service.claim(high,first)&&high.lifespan()==420&&!service.claim(high,second)&&!service.complete(high,second),"Reservation lifetime/exclusive worker claim failed");
        far.color(3);first.setGolemColor((byte)2);helper.assertTrue(!service.eligible(high,first),"Different nonzero colors performed the same task");first.setGolemColor((byte)0);helper.assertTrue(service.eligible(high,first),"Color zero stopped being wildcard");first.setOwnerId(UUID.randomUUID());helper.assertTrue(!service.eligible(high,first),"Locked seal ignored golem owner");
        first.discard();second.discard();done(helper,owner);
    }
    @GameTest(template=TEMPLATE)
    public static void taskCleanupHasOriginalSecondDeadlineAndRedstoneSuspendsActiveWork(GameTestHelper helper){
        var owner=player(helper);SealData seal=seal(helper,owner,CENTER,"fill");SealService service=SealService.get(helper.getLevel());SealTask task=SealTask.block(seal.position(),seal.position().pos()).lifespan(1);service.addTask(task);service.cleanupTasks();helper.assertTrue(service.task(task.id())==task&&task.lifespan()==0,"Deadline removed its last positive second early");service.cleanupTasks();helper.assertTrue(service.task(task.id())==null,"Expired task survived cleanup");
        SealTask active=SealTask.block(seal.position(),seal.position().pos());service.addTask(active);seal.redstone(true);helper.setBlock(CENTER.above(),Blocks.REDSTONE_BLOCK);
        helper.runAtTickTime(2,()->{helper.assertTrue(active.suspended()&&service.stopped(seal),"Power on attached/front block did not stop and suspend tasks");done(helper,owner);});
    }
    @GameTest(template=TEMPLATE)
    public static void supportLossDropsOriginalSealOnceAndSuspendsItsTickets(GameTestHelper helper){
        var owner=player(helper);SealData seal=seal(helper,owner,CENTER,"empty");SealService service=SealService.get(helper.getLevel());SealTask task=SealTask.block(seal.position(),seal.position().pos());service.addTask(task);
        helper.setBlock(CENTER,Blocks.AIR);
        helper.runAtTickTime(42,()->{
            helper.assertTrue(service.seal(seal.position())==null&&(task.suspended()||service.task(task.id())==null),"Lost inventory face retained a seal/task");
            var item=SealRegistry.stack("thaumcraft:empty").getItem();int count=helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(seal.position().pos()).inflate(3),e->e.getItem().getItem()==item).stream().mapToInt(e->e.getItem().getCount()).sum();helper.assertTrue(count==1,"Support loss duplicated/lost the original seal item: "+count);
            helper.assertTrue(!service.remove(seal.position(),false),"Second removal was accepted");done(helper,owner);
        });
    }
    @GameTest(template=TEMPLATE)
    public static void bellLogisticsListsOwnedProviderInventoryAndCreatesDetachedRealDeliveryRequests(GameTestHelper helper){
        var owner=player(helper);research(owner,"GOLEMLOGISTICS",1);SealData seal=seal(helper,owner,CENTER,"provider");ChestBlockEntity chest=(ChestBlockEntity)helper.getLevel().getBlockEntity(seal.position().pos());chest.setItem(0,new ItemStack(Items.DIAMOND,64));chest.setItem(1,new ItemStack(Items.DIAMOND,64));
        var foreign=player(helper);SealData other=seal(helper,foreign,new BlockPos(6,2,4),"provider");((ChestBlockEntity)helper.getLevel().getBlockEntity(other.position().pos())).setItem(0,new ItemStack(Items.EMERALD,64));
        SealLogisticsMenu menu=new SealLogisticsMenu(27,owner.getInventory(),null,null);owner.containerMenu=menu;
        helper.assertTrue(menu.slots.size()==81&&menu.entries().get(0).getItem()==Items.DIAMOND&&menu.entries().get(0).getCount()==128&&menu.entries().stream().noneMatch(s->s.getItem()==Items.EMERALD),"Catalogue included foreign providers/lost large count/added player slots");int revision=menu.revision();
        helper.assertTrue(SealNetwork.process(owner,new SealNetwork.Request(menu.containerId,revision,0,65)),"Real catalogue request rejected");
        List<SealProvision> requests=SealService.get(helper.getLevel()).provisions().stream().filter(p->owner.getUUID().equals(p.entityUUID())).toList();helper.assertTrue(requests.size()==2&&requests.stream().mapToInt(p->p.stack().getCount()).sum()==65&&chest.getItem(0).getCount()==64&&chest.getItem(1).getCount()==64,"Request directly debited inventory or failed exact stack splitting");
        helper.assertTrue(!SealNetwork.process(owner,new SealNetwork.Request(menu.containerId,revision-1,0,1))&&!SealNetwork.process(owner,new SealNetwork.Request(menu.containerId,menu.revision(),0,129)),"Stale/oversize request bypassed available catalogue");
        ItemStack detached=requests.get(0).stack();detached.setCount(1);helper.assertTrue(requests.get(0).stack().getCount()==64,"Provision stack shared mutable request data");
        for(int slot=2;slot<6;slot++)chest.setItem(slot,new ItemStack(Items.DIAMOND,64));menu.refresh(owner);
        helper.assertTrue(menu.entries().get(0).getCount()==384&&menu.revision()!=revision,"Large catalogue count changed by 256 without advancing its revision");
        helper.assertTrue(!SealNetwork.process(owner,new SealNetwork.Request(menu.containerId,revision,0,1)),"Old large-count catalogue revision created a delivery request");
        cleanup(helper,foreign);done(helper,owner);
    }
    @GameTest(template=TEMPLATE)
    public static void providerPlayerDeliveryOutranksStoreAndPaysExactlyOnce(GameTestHelper helper){
        var owner=player(helper);research(owner,"GOLEMLOGISTICS",1);
        SealData provider=seal(helper,owner,CENTER.west(),"provider"),store=seal(helper,owner,CENTER.east(),"fill");
        store.priority(5);ChestBlockEntity source=(ChestBlockEntity)helper.getLevel().getBlockEntity(provider.position().pos());
        ChestBlockEntity destination=(ChestBlockEntity)helper.getLevel().getBlockEntity(store.position().pos());ItemStack apples=new ItemStack(Items.APPLE,6);
        // Pending requests are dimension-wide in TC6. Isolate this physical family from other
        // concurrently running provider tests without changing any behavior or task predicates.
        apples.getOrCreateTag().putUUID("SealDeliveryRegression",owner.getUUID());source.setItem(0,apples);
        ThaumcraftGolemEntity golem=golem(helper,owner);owner.setPos(golem.position().add(0,0,1));SealService service=SealService.get(helper.getLevel());
        try {
            // The requester must resolve through the actual world UUID/player index. No fake cargo
            // is injected: the physical logistics request creates the original provider pickup ticket.
            helper.getLevel().addNewPlayer(owner);helper.assertTrue(helper.getLevel().getEntity(owner.getUUID())==owner,"Player recipient was not tracked by the world");
            SealLogisticsMenu menu=new SealLogisticsMenu(28,owner.getInventory(),null,null);owner.containerMenu=menu;
            helper.assertTrue(SealNetwork.process(owner,new SealNetwork.Request(menu.containerId,menu.revision(),0,2)),"Actual player catalogue request was rejected");
            SealRegistry.behavior(store.type()).tick(helper.getLevel(),store,service);
            SealRegistry.behavior(provider.type()).tick(helper.getLevel(),provider,service);
            SealTask pickup=service.tasks().stream().filter(t->provider.position().equals(t.sealPosition())&&t.type()==0&&t.data()==0).findFirst().orElseThrow();
            helper.assertTrue(service.claim(pickup,golem)&&service.complete(pickup,golem),"Provider did not claim/extract its actual source inventory");service.release(pickup,golem);
            SealProvision request=service.provisions().stream().filter(p->owner.getUUID().equals(p.entityUUID())).findFirst().orElseThrow();SealTask delivery=request.linkedTask();
            helper.assertTrue(source.getItem(0).getCount()==4&&golem.carrying().stream().mapToInt(ItemStack::getCount).sum()==2
                    &&delivery!=null&&delivery.type()==1&&delivery.data()==1&&delivery.provision()==request&&golem.getUUID().equals(delivery.golemUUID()),"Pickup lost, duplicated or detached the assigned player delivery");
            helper.assertTrue(service.sorted(golem,0).stream().anyMatch(t->store.position().equals(t.sealPosition())&&t.priority()==5),"Regression did not contain a competing eligible highest-priority Store ticket");
            SealTaskGoal goal=new SealTaskGoal(golem);helper.assertTrue(goal.canUse()&&goal.task()==delivery,"Store stole cargo before the assigned entity delivery");
            goal.start();goal.tick();helper.assertTrue(delivery.completed()&&request.invalid(),"Actual player delivery did not complete and invalidate its request");
            // carrying() preserves hand-slot positions, including empty ItemStacks.
            helper.assertTrue(destination.isEmpty()&&golem.carrying().stream().allMatch(ItemStack::isEmpty),"Actual completion did not deliver exclusively to the requesting player");
            helper.assertTrue(!service.complete(delivery,golem),"A repeated delivery completion duplicated cargo");goal.stop();
            var delivered=helper.getLevel().getEntitiesOfClass(ItemEntity.class,owner.getBoundingBox().inflate(1),e->ItemStack.isSameItemSameTags(e.getItem(),apples));
            helper.assertTrue(delivered.stream().mapToInt(e->e.getItem().getCount()).sum()==2,"Delivery spawned an incorrect amount or at an unrelated target");
            for(ItemEntity item:delivered)item.playerTouch(owner);
            helper.assertTrue(owner.getInventory().countItem(Items.APPLE)==2&&source.getItem(0).getCount()==4&&destination.isEmpty(),"Player pickup did not conserve the six physical source apples");
        } finally {golem.discard();cleanup(helper,owner);helper.getLevel().removePlayerImmediately(owner,Entity.RemovalReason.DISCARDED);}
        helper.succeed();
    }
}
