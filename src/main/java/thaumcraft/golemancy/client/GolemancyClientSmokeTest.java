package thaumcraft.golemancy.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.client.research.*;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.golemancy.press.*;
import thaumcraft.golemancy.press.client.GolemPressScreen;
import thaumcraft.golemancy.entity.*;
import thaumcraft.golemancy.seals.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import thaumcraft.research.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned hidden client: actual research/menu/formation packets and paid roller output, never a supplied golem. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class GolemancyClientSmokeTest {
    private static final String WORLD="thaumcraft-golemancy-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"formed-press","ready-menu","paid-progress","completed-output","output-item","book-blueprint","deployed","seal-area","seal-filter","seal-priority","collect-carrying","store-delivered","provider-logistics","logistics-delivered","advanced-options","traits-gallery","following","picked-up"};
    private static final BlockPos ANCHOR=new BlockPos(0,112,0), CHEST=ANCHOR.north(), SOURCE=ANCHOR.below();
    private static final long DESIGN=1L<<32; // Original WOOD/BASIC/BASIC/ROLLER/NONE: 12 Machina.
    private static final AtomicInteger saved=new AtomicInteger();
    private static boolean started,prepared,stopped,captureRequested,captured;
    private static int scene,phase,stable;
    private static long began;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previous;
    private static String knowledgeBefore;
    private static int golemId;
    private static final BlockPos PICKUP=new BlockPos(4,111,0),DESTINATION=new BlockPos(7,112,0),PROVIDER=new BlockPos(5,112,3);
    private static final SealPos COLLECT=new SealPos(PICKUP,Direction.UP),STORE=new SealPos(DESTINATION,Direction.NORTH),PROVIDE=new SealPos(PROVIDER,Direction.NORTH);
    private GolemancyClientSmokeTest() {}
    private static void require(boolean ok,String reason) {if(!ok)throw new AssertionError(reason);}
    private static ServerPlayer player(Minecraft mc) {var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(p!=null,"Missing integrated player");return p;}
    private static GolemPressBlockEntity tile(Minecraft mc) {return (GolemPressBlockEntity)player(mc).serverLevel().getBlockEntity(ANCHOR);}
    private static void submit(Minecraft mc,Runnable task) {require(work==null,"Overlapping press server tasks");var result=new CompletableFuture<Void>();work=result;mc.getSingleplayerServer().execute(()->{try {task.run();result.complete(null);}catch(Throwable e) {result.completeExceptionally(e);}});}
    private static void stage(ServerPlayer p,String key,int stage) {try {var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),key,stage);}catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}}
    private static void knowledge(ServerPlayer p,String category,int raw) {try {var method=PlayerKnowledge.class.getDeclaredMethod("addKnowledge",KnowledgeType.class,String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),KnowledgeType.THEORY,category,raw);}catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.golemancySmokeTest")||stopped)return;
        var mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try {
            require(System.nanoTime()-began<900_000_000_000L,"Press client timeout scene="+scene+" phase="+phase);
            if(!started) {start(mc);return;}
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null||mc.screen instanceof ReceivingLevelScreen)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong press world");mc.getToasts().clear();
            if(work!=null) {if(!work.isDone())return;work.join();work=null;}
            if(!prepared) {prepared=true;submit(mc,()->prepare(mc));return;}
            if(captured) {if(saved.get()==scene+1&&++stable>=20) {scene++;phase=stable=0;captured=captureRequested=false;}return;}
            if(scene==IMAGES.length) {finish(mc);return;}
            if(scene==0) {
                if(phase==0) {if(ResearchClient.golemPressKnowledge().researchStage("MINDCLOCKWORK")!=2)return;phase=1;ResearchNetwork.requestAdvance("MINDCLOCKWORK",2);return;}
                if(phase==1) {if(!ResearchClient.golemPressKnowledge().isResearchCompleteStrict("MINDCLOCKWORK"))return;phase=2;submit(mc,()->{
                    var p=player(mc);var k=KnowledgeStore.get(p);require(k.researchStage("MINDCLOCKWORK")==4&&k.rawKnowledge(KnowledgeType.THEORY,"ARTIFICE")==0&&k.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==0&&k.isResearchCompleteStrict("CONTROLSEALS")&&k.isResearchCompleteStrict("SEALCOLLECT")&&k.isResearchCompleteStrict("SEALSTORE"),"Actual research payment or eligible seal siblings failed");
                    p.getInventory().setItem(0,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));p.inventoryMenu.broadcastChanges();
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_RESEARCH: actual32+32 theory C2S, completion4, three eligible seal siblings complete");});return;}
                if(phase==2) {if(!mc.player.getMainHandItem().is(AlchemyModule.SALIS_MUNDUS.get()))return;phase=3;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(ANCHOR.getCenter(),Direction.UP,ANCHOR,false));return;}
                if(phase==3) {if(!(mc.level.getBlockEntity(ANCHOR) instanceof GolemPressBlockEntity))return;phase=4;submit(mc,()->{
                    require(tile(mc)!=null&&tile(mc).getItem(0).isEmpty()&&player(mc).getMainHandItem().getCount()==1,"Real Salis formation did not pay once");
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_FORMATION: real five-member ritual/dust2->1, original facing north");});return;}
                if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery("TC6 Golem Press / actual Salis formation"));
            } else if(scene==1) {
                if(phase==0) {phase=1;close(mc);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(ANCHOR.getCenter(),Direction.NORTH,ANCHOR,false));return;}
                if(!(mc.screen instanceof GolemPressScreen screen)||!(mc.player.containerMenu instanceof GolemPressMenu menu))return;
                if(phase==1) {if(screen.design()==null)return;phase=2;screen.selectForSmokeTest(DESIGN);return;}
                if(!screen.canCreate()||menu.checkedDesignId()!=DESIGN)return;
                require(menu.slots.size()==37&&menu.getSlot(0).getItem().isEmpty()&&screen.design().essentiaCost()==12,"Original container/design costs wrong");
            } else if(scene==2) {
                if(!(mc.screen instanceof GolemPressScreen screen)||!(mc.player.containerMenu instanceof GolemPressMenu menu))return;
                if(phase==0) {phase=1;require(screen.canCreate(),"Create lost available components");screen.create();return;}
                if(menu.cost()!=12)return;
                if(phase==1) {phase=2;submit(mc,()->{
                    var t=tile(mc);var p=player(mc);require(t.golemId()==DESIGN&&t.cost()==12&&t.getItem(0).isEmpty(),"Press start did not become paid12 job");
                    var chest=(ChestBlockEntity)p.serverLevel().getBlockEntity(CHEST);require(chest.isEmpty(),"Adjacent plank component not paid");
                    for(int slot=9;slot<15;slot++)require(p.getInventory().getItem(slot).isEmpty(),"Player component not paid");
                    require(!GolemPressNetwork.process(p,new GolemPressNetwork.Request(p.containerMenu.containerId,0,DESIGN,true)),"Old request paid again");
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_PAID: actual C2S roller, component debit, busy/replay guard,12Machina remains; no prepared output");});return;}
                var client=mc.level.getBlockEntity(ANCHOR);if(!(client instanceof GolemPressBlockEntity t)||t.pressAngle()<90)return;
                require(menu.maxCost()==12&&menu.cost()==12,"Client changed paid job without essentia");
            } else if(scene==3) {
                if(phase==0) {phase=1;submit(mc,()->{
                    var level=player(mc).serverLevel();level.setBlockAndUpdate(SOURCE,thaumcraft.catalog.blocks.CatalogBlocks.block("alembic").defaultBlockState());
                    var source=(AlembicBlockEntity)level.getBlockEntity(SOURCE);require(source.addExact(Aspect.MECHANISM,12),"Machina fixture refused");});return;}
                if(!(mc.player.containerMenu instanceof GolemPressMenu menu)||menu.getSlot(0).getItem().isEmpty()||menu.cost()!=0)return;
                var result=menu.getSlot(0).getItem();require(result.getCount()==1&&result.is(CatalogModule.stack("golem").getItem())&&result.hasTag()&&result.getTag().getLong("props")==DESIGN,"Paid output lost original props");
                if(phase==1) {phase=2;submit(mc,()->{var t=tile(mc);var source=(AlembicBlockEntity)player(mc).serverLevel().getBlockEntity(SOURCE);
                    require(t.golemId()==-1&&t.cost()==0&&source.amount()==0&&t.getItem(0).getCount()==1,"Final twelve-unit payment/output retirement wrong");
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_OUTPUT: twelve real fifth-tick draws from downward alembic, exact props4294967296, one output");});return;}
            } else if(scene==4) {
                if(phase==0) {phase=1;var menu=(GolemPressMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,2,ClickType.SWAP,mc.player);return;}
                if(mc.player.getInventory().getItem(2).isEmpty())return;
                var output=mc.player.getInventory().getItem(2);require(output.getCount()==1&&output.hasTag()&&output.getTag().getLong("props")==DESIGN,"Real output slot extraction failed");
                if(phase==1) {phase=2;close(mc);mc.setScreen(new Gallery("TC6 Golem Press / paid output extracted from real slot"));}
            } else if(scene==5) {
                if(phase==0) {phase=1;submit(mc,()->{var p=player(mc);knowledgeBefore=KnowledgeStore.get(p).save().toString();p.getInventory().setItem(0,new ItemStack(ResearchModule.THAUMONOMICON.get()));p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);});return;}
                if(phase==1) {if(!mc.player.getMainHandItem().is(ResearchModule.THAUMONOMICON.get()))return;phase=2;
                    var k=ResearchClient.golemPressKnowledge();var browser=new ThaumonomiconScreen(k,k.scanCount());var page=new ThaumonomiconPageScreen(browser,ResearchCatalog.get("MINDCLOCKWORK"),k,k.scanCount());mc.setScreen(page);page.directRecipeForSmokeTest("thaumcraft:GolemPress");return;}
                require(mc.screen instanceof ThaumonomiconPageScreen,"Missing press blueprint page");
                LogUtils.getLogger().debug("THAUMCRAFT_GOLEMANCY_BOOK_READY");
            }
            if(scene>=6&&!additional(mc))return;
            if(++stable>=15&&!captured)captureRequested=true;
        }catch(Throwable e) {fail(mc,e);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.golemancySmokeTest")||stopped||!captureRequested||captured)return;
        var mc=Minecraft.getInstance();try {
            require(scene<IMAGES.length,"Invalid golemancy screenshot scene");captured=true;captureRequested=false;
            String name="tc6-golemancy-"+IMAGES[scene]+".png";File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{if(file.isFile()&&file.length()>0) {saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_SMOKE_IMAGE: {}",file.getAbsolutePath());}else mc.execute(()->fail(mc,new AssertionError("Missing press image")));});
        }catch(Throwable e) {fail(mc,e);}
    }
    private static ThaumcraftGolemEntity serverGolem(Minecraft mc){Entity e=player(mc).serverLevel().getEntity(golemId);require(e instanceof ThaumcraftGolemEntity,"Missing deployed paid golem");return (ThaumcraftGolemEntity)e;}
    private static ThaumcraftGolemEntity clientGolem(Minecraft mc){Entity e=mc.level.getEntity(golemId);return e instanceof ThaumcraftGolemEntity golem?golem:null;}
    private static SealData seal(Minecraft mc,SealPos pos){return SealService.get(player(mc).serverLevel()).seal(pos);}
    private static void held(ServerPlayer player,ItemStack stack){player.getInventory().selected=0;player.getInventory().setItem(0,stack);player.inventoryMenu.broadcastChanges();}
    private static void click(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),face,pos,false));}
    private static void shift(Minecraft mc,boolean value){mc.player.input.shiftKeyDown=value;mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void view(Minecraft mc,String title){if(!(mc.screen instanceof GolemGallery))mc.setScreen(new GolemGallery(title));}
    private static boolean additional(Minecraft mc){
        if(scene==6){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);require(knowledgeBefore.equals(KnowledgeStore.get(p).save().toString()),"Read-only blueprint mutated research");p.getInventory().selected=2;p.connection.teleport(2.5,112,3.5,180,20);p.inventoryMenu.broadcastChanges();});return false;}
            mc.player.getInventory().selected=2;
            if(phase==1){if(mc.player.getMainHandItem().isEmpty())return false;require(mc.player.getMainHandItem().getTag().getLong("props")==DESIGN,"Placement used a replacement output");phase=2;click(mc,new BlockPos(2,111,0),Direction.UP);return false;}
            if(phase==2){if(!mc.player.getMainHandItem().isEmpty())return false;phase=3;submit(mc,()->{var p=player(mc);var list=p.serverLevel().getEntitiesOfClass(ThaumcraftGolemEntity.class,new AABB(-1,111,-2,6,116,3));require(list.size()==1,"Placement did not produce exactly one entity");var golem=list.get(0);golemId=golem.getId();require(golem.props()==DESIGN&&golem.isOwner(p)&&golem.homePosition().equals(new BlockPos(2,112,0))&&golem.isValidSpawn()&&!golem.isNoAi(),"Real placer lost ownership/home/props/AI");LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_PLACEMENT: one actual paid press item consumed by C2S, owner/home and original props preserved");});return false;}
            if(clientGolem(mc)==null)return false;view(mc,"TC6 / real press output deployed as an owned working golem");return true;
        }
        if(scene==7){
            if(phase==0){phase=1;close(mc);mc.player.getInventory().selected=0;submit(mc,()->{var p=player(mc);p.connection.teleport(4.5,112,3.5,180,35);held(p,CatalogModule.stack("seal_pickup").copyWithCount(2));});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().is(CatalogModule.stack("seal_pickup").getItem()))return false;phase=2;click(mc,PICKUP,Direction.UP);return false;}
            if(phase==2){if(mc.player.getMainHandItem().getCount()!=1)return false;phase=3;submit(mc,()->{require(seal(mc,COLLECT)!=null,"Actual seal placement absent");held(player(mc),CatalogModule.stack("golem_bell"));});return false;}
            if(phase==3){if(!mc.player.getMainHandItem().is(CatalogModule.stack("golem_bell").getItem()))return false;phase=4;click(mc,PICKUP,Direction.UP);return false;}
            if(!(mc.screen instanceof SealScreen)||!(mc.player.containerMenu instanceof SealMenu menu)||menu.category()!=2)return false;
            require(menu.seal().area().equals(new BlockPos(3,1,3)),"Original UP area defaults changed");require(SealClientState.seals().stream().anyMatch(s->s.position().equals(COLLECT)),"Placed seal did not sync to world renderer");return true;
        }
        if(scene==8){
            if(!(mc.player.containerMenu instanceof SealMenu menu)||!(mc.screen instanceof SealScreen screen))return false;
            if(phase==0){phase=1;submit(mc,()->{var p=player(mc);p.getInventory().setItem(9,new ItemStack(Items.DIAMOND,5));p.containerMenu.broadcastChanges();});return false;}
            if(phase==1){phase=2;screen.action(1);return false;}
            if(menu.category()!=1)return false;
            if(phase==2){if(!menu.getSlot(1).getItem().is(Items.DIAMOND))return false;phase=3;mc.gameMode.handleInventoryMouseClick(menu.containerId,1,0,ClickType.PICKUP,mc.player);return false;}
            if(phase==3){if(menu.getCarried().getCount()!=5)return false;phase=4;SealNetwork.ghost(menu,0,0,false);return false;}
            if(phase==4){if(!menu.seal().filter(0).is(Items.DIAMOND))return false;require(menu.getCarried().getCount()==5,"Ghost filter consumed physical diamonds");phase=5;screen.action(21);return false;}
            if(menu.seal().blacklist())return false;
            if(phase==5){phase=6;mc.gameMode.handleInventoryMouseClick(menu.containerId,1,0,ClickType.PICKUP,mc.player);return false;}
            if(!menu.getCarried().isEmpty())return false;require(menu.getSlot(1).getItem().getCount()==5,"Returned filter cursor lost real items");return true;
        }
        if(scene==9){
            if(!(mc.player.containerMenu instanceof SealMenu menu)||!(mc.screen instanceof SealScreen screen))return false;
            if(phase==0){phase=1;screen.action(2);return false;}if(menu.category()!=0)return false;
            if(phase==1){phase=2;screen.action(81);return false;}if(menu.seal().priority()!=1)return false;
            if(phase==2){phase=3;screen.action(83);return false;}if(menu.seal().color()!=1)return false;
            if(phase==3){phase=4;screen.action(25);return false;}if(!menu.seal().locked())return false;
            if(phase==4){phase=5;screen.action(27);return false;}return menu.seal().redstone();
        }
        if(scene==10){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);require(seal(mc,COLLECT).priority()==1&&seal(mc,COLLECT).color()==1&&seal(mc,COLLECT).locked()&&seal(mc,COLLECT).redstone(),"C2S seal configuration did not persist");ItemEntity item=new ItemEntity(p.serverLevel(),4.5,112.1,.5,new ItemStack(Items.DIAMOND,3));item.setPickUpDelay(0);p.serverLevel().addFreshEntity(item);});return false;}
            var golem=clientGolem(mc);if(golem==null||!golem.getMainHandItem().is(Items.DIAMOND)||golem.getMainHandItem().getCount()!=3)return false;view(mc,"TC6 / real seal task AI picked up three dropped diamonds");return true;
        }
        if(scene==11){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);p.serverLevel().setBlockAndUpdate(DESTINATION,Blocks.CHEST.defaultBlockState());p.connection.teleport(7.5,112,3.5,180,20);held(p,CatalogModule.stack("seal_fill").copyWithCount(2));});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().is(CatalogModule.stack("seal_fill").getItem()))return false;phase=2;click(mc,DESTINATION,Direction.NORTH);return false;}
            if(phase==2){if(mc.player.getMainHandItem().getCount()!=1)return false;phase=3;submit(mc,()->require(seal(mc,STORE)!=null,"Actual Store seal placement failed"));return false;}
            if(phase==3){phase=4;submit(mc,()->{var chest=(ChestBlockEntity)player(mc).serverLevel().getBlockEntity(DESTINATION);if(chest.getItem(0).is(Items.DIAMOND)&&chest.getItem(0).getCount()==3){phase=5;require(serverGolem(mc).carrying().stream().allMatch(ItemStack::isEmpty),"Delivery duplicated golem cargo");LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_DELIVERY: actual pickup navigation and Store task deposited three diamonds into real chest");}});return false;}
            if(phase==4){phase=3;return false;}if(phase<5)return false;view(mc,"TC6 / three collected diamonds delivered into the actual chest");return true;
        }
        if(scene==12){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);p.serverLevel().setBlockAndUpdate(PROVIDER,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)p.serverLevel().getBlockEntity(PROVIDER)).setItem(0,new ItemStack(Items.APPLE,6));p.connection.teleport(5.5,112,6.5,180,20);stage(p,"GOLEMLOGISTICS",ResearchCatalog.get("GOLEMLOGISTICS").stages().size()+1);held(p,CatalogModule.stack("seal_provider").copyWithCount(2));ResearchNetwork.sync(p);});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().is(CatalogModule.stack("seal_provider").getItem()))return false;phase=2;click(mc,PROVIDER,Direction.NORTH);return false;}
            if(phase==2){if(mc.player.getMainHandItem().getCount()!=1)return false;phase=3;submit(mc,()->{require(seal(mc,PROVIDE)!=null,"Provider seal absent");held(player(mc),CatalogModule.stack("golem_bell"));});return false;}
            if(phase==3){if(!mc.player.getMainHandItem().is(CatalogModule.stack("golem_bell").getItem()))return false;phase=4;shift(mc,true);mc.player.setXRot(-80);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),-80,mc.player.onGround()));mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return false;}
            if(!(mc.player.containerMenu instanceof SealLogisticsMenu menu)||!(mc.screen instanceof SealLogisticsScreen screen))return false;
            if(phase==4){phase=5;shift(mc,false);}if(!menu.getSlot(0).getItem().is(Items.APPLE)||menu.getSlot(0).getItem().getCount()!=6)return false;screen.select(0);screen.amount(2);return true;
        }
        if(scene==13){
            if(phase==0){phase=1;require(mc.screen instanceof SealLogisticsScreen,"Logistics screen lost before request");((SealLogisticsScreen)mc.screen).request();return false;}
            if(phase==1){phase=2;close(mc);submit(mc,()->player(mc).getInventory().removeItem(9,64));return false;}
            if(phase==2){phase=3;submit(mc,()->{var p=player(mc);int apples=0;for(ItemStack stack:p.getInventory().items)if(stack.is(Items.APPLE))apples+=stack.getCount();var source=(ChestBlockEntity)p.serverLevel().getBlockEntity(PROVIDER);if(apples==2&&source.getItem(0).getCount()==4){phase=4;require(serverGolem(mc).carrying().stream().allMatch(ItemStack::isEmpty),"Logistics delivery retained cargo");LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_LOGISTICS: actual C2S request for2, owned provider6->4, real AI delivered2 to player, no direct inventory transfer");}});return false;}
            if(phase==3){phase=2;return false;}if(phase<4)return false;view(mc,"TC6 / provider request delivered two apples to the actual player");return true;
        }
        if(scene==14){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);var old=seal(mc,COLLECT);SealService.get(p.serverLevel()).remove(COLLECT,true);SealData advanced=new SealData(COLLECT,"thaumcraft:pickup_advanced",p.getUUID());advanced.filter(0,new ItemStack(Items.DIAMOND));advanced.blacklist(false);SealService.get(p.serverLevel()).add(advanced);p.connection.teleport(4.5,112,3.5,180,35);held(p,CatalogModule.stack("golem_bell"));});return false;}
            if(phase==1){phase=2;click(mc,PICKUP,Direction.UP);return false;}if(!(mc.screen instanceof SealScreen screen)||!(mc.player.containerMenu instanceof SealMenu menu))return false;
            if(phase==2){phase=3;screen.action(2);return false;}return menu.category()==3&&menu.filterSlots()==9;
        }
        if(scene==15){
            if(phase==0){phase=1;close(mc);mc.setScreen(new GolemGallery("TC6 / original material and animated attachment variants",true));}
            return true;
        }
        if(scene==16){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);stage(p,"GOLEMDIRECT",ResearchCatalog.get("GOLEMDIRECT").stages().size()+1);var golem=serverGolem(mc);p.connection.teleport(golem.getX(),112,golem.getZ()+2,180,10);held(p,CatalogModule.stack("golem_bell"));});return false;}
            var golem=clientGolem(mc);if(golem==null)return false;
            if(phase==1){phase=2;mc.gameMode.interact(mc.player,golem,InteractionHand.MAIN_HAND);return false;}if(!golem.isFollowingOwner())return false;view(mc,"TC6 / owner bell command switched the manufactured golem to follow mode");return true;
        }
        if(scene==17){
            if(phase==0){phase=1;close(mc);var golem=clientGolem(mc);if(golem==null)return false;shift(mc,true);mc.player.connection.send(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(golem,true,InteractionHand.MAIN_HAND));return false;}
            if(clientGolem(mc)!=null)return false;
            if(phase==1){phase=2;shift(mc,false);submit(mc,()->{var p=player(mc);var drops=p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(-4,111,-4,12,118,9)).stream().filter(e->e.getItem().is(CatalogModule.stack("golem").getItem())).toList();require(drops.size()==1&&drops.get(0).getItem().getTag().getLong("props")==DESIGN&&drops.get(0).getItem().getCount()==1,"Owner pickup lost/duplicated paid construct props");LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_PICKUP: one owner-only original props item returned, entity removed and tasks released");});return false;}
            view(mc,"TC6 / owner picked up the exact manufactured golem");return true;
        }
        return false;
    }
    private static final class GolemGallery extends Screen {
        private final boolean variants;
        private final List<ThaumcraftGolemEntity> previews=new ArrayList<>();
        GolemGallery(String title){this(title,false);}
        GolemGallery(String title,boolean variants){super(Component.literal(title));this.variants=variants;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics gui,int x,int y,float partial){
            var mc=Minecraft.getInstance();gui.fill(0,0,width,height,0xff18202b);gui.drawCenteredString(font,title,width/2,15,0xffefdbac);gui.flush();Lighting.setupFor3DItems();
            if(variants){if(previews.isEmpty())for(int mat=0;mat<6;mat++){var golem=thaumcraft.catalog.entities.VisualEntitiesModule.GOLEM.get().create(mc.level);golem.setProps(GolemDesign.create(mat,mat%5,mat%5,mat%4,mat%4).orElseThrow().props());golem.setGolemColor((byte)(mat+1));golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND));previews.add(golem);}
                for(int i=0;i<previews.size();i++){var golem=previews.get(i);golem.tickCount=mc.player.tickCount;draw(gui,golem,100+(i%3)*240,200+(i/3)*200,105,partial);gui.drawCenteredString(font,golem.design().material().key(),100+(i%3)*240,225+(i/3)*200,0xffffff);}}
            else{var golem=clientGolem(mc);if(golem!=null)draw(gui,golem,width/2,height/2+100,210,partial);
                if(scene>=11){gui.renderItem(new ItemStack(Items.DIAMOND,3),width/2-80,height-75);gui.drawString(font,"Delivered: 3",width/2-55,height-71,0xffffff);}
                if(scene>=13){gui.renderItem(new ItemStack(Items.APPLE,2),width/2+15,height-75);gui.drawString(font,"Requested: 2",width/2+40,height-71,0xffffff);}}
            gui.flush();gui.drawCenteredString(font,"Real server golem / owner and seal state synchronized to this client",width/2,height-25,0xffd4d9df);
        }
        private static void draw(GuiGraphics gui,ThaumcraftGolemEntity golem,int x,int y,float scale,float partial){gui.pose().pushPose();gui.pose().translate(x,y,200);gui.pose().scale(scale,-scale,scale);gui.pose().mulPose(Axis.XP.rotationDegrees(18));gui.pose().mulPose(Axis.YP.rotationDegrees(150));Minecraft.getInstance().getEntityRenderDispatcher().render(golem,0,0,0,0,partial,gui.pose(),gui.bufferSource(),15728880);gui.flush();gui.pose().popPose();}
    }

    private static void start(Minecraft mc) {
        if(mc.screen instanceof AccessibilityOnboardingScreen) {mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;started=true;previous=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(0x54433622L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static void prepare(Minecraft mc) {
        var p=player(mc);var level=p.serverLevel();p.setInvulnerable(true);p.connection.teleport(.5,112,3.5,180,20);
        for(var pos:BlockPos.betweenClosed(-4,111,-4,12,116,8))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        for(var part:GolemPressFormation.parts(ANCHOR,Direction.NORTH))level.setBlockAndUpdate(part.pos(),part.id().equals("golem_builder")?Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP):GolemPressPlaceholderBlock.original(part.id()).defaultBlockState());
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(thaumcraft.catalog.blocks.CatalogBlocks.block("plank_greatwood"),2));
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(ResearchModule.THAUMONOMICON.get()));
        var design=GolemDesign.parse(DESIGN).orElseThrow();int slot=9;for(var stack:design.components())if(!stack.is(thaumcraft.catalog.blocks.CatalogBlocks.block("plank_greatwood").asItem()))p.getInventory().setItem(slot++,stack.copy());
        for(String key:List.of("BASEGOLEMANCY","ESSENTIASMELTER","HEDGEALCHEMY","MATSTUDWOOD"))stage(p,key,ResearchCatalog.get(key).stages().size()+1);
        stage(p,"MINDCLOCKWORK",2);knowledge(p,"ARTIFICE",32);knowledge(p,"GOLEMANCY",32);p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);
        LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_FIXTURE: exact predecessor stages/theories/components and empty source/output prepared; Mind completion/formation/manufacture are actual client operations");
    }
    private static void close(Minecraft mc) {mc.player.closeContainer();mc.setScreen(null);}
    private static void finish(Minecraft mc) {
        require(saved.get()==IMAGES.length,"Missing eighteen fresh captures");stopped=true;mc.options.tutorialStep=previous;
        LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_RENDER_AUDIT_OK: original OBJ fixed/press groups and lava, real S2C working tile,90degree stroke, native208x224 GUI, original selectors/traits, detached blueprint");
        LogUtils.getLogger().info("THAUMCRAFT_GOLEMANCY_CLIENT_SMOKE_OK: 18 scenes; actual research32+32, five-block Salis, component C2S payment,12Machina ticks, one exact output and physical slot extraction; no golem supplied; world={}",WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable e) {if(stopped)return;stopped=true;if(previous!=null)mc.options.tutorialStep=previous;LogUtils.getLogger().error("THAUMCRAFT_GOLEMANCY_CLIENT_SMOKE_FAILED",e);mc.stop();}
    private static final class Gallery extends Screen {
        Gallery(String title) {super(Component.literal(title));}
        @Override public boolean isPauseScreen() {return false;}
        @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial) {
            var mc=Minecraft.getInstance();try {
                gui.fill(0,0,width,height,0xff18202b);gui.drawCenteredString(font,title,width/2,15,0xffefdbac);gui.flush();
                var be=mc.level.getBlockEntity(ANCHOR);require(be instanceof GolemPressBlockEntity,"Missing visible working press");
                gui.pose().pushPose();gui.pose().translate(width/2,height/2+20,200);gui.pose().scale(110,-110,110);gui.pose().mulPose(Axis.XP.rotationDegrees(25));gui.pose().mulPose(Axis.YP.rotationDegrees(145));gui.pose().translate(-1,-.9,-1);Lighting.setupFor3DItems();
                require(!mc.getBlockEntityRenderDispatcher().renderItem(be,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Press BER missing");gui.flush();gui.pose().popPose();
                var output=mc.player.getInventory().getItem(2);if(!output.isEmpty()) {gui.pose().pushPose();gui.pose().translate(width/2-16,height-85,0);gui.pose().scale(2,2,1);gui.renderItem(output,0,0);gui.pose().popPose();}
                gui.drawCenteredString(font,"Original five-member construction / paid factory output",width/2,height-35,0xffd4d9df);
            }catch(Throwable e) {fail(mc,e);}
        }
    }
}
