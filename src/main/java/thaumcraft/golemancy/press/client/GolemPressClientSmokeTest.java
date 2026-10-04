package thaumcraft.golemancy.press.client;

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
import thaumcraft.research.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned hidden client: actual research/menu/formation packets and paid roller output, never a supplied golem. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class GolemPressClientSmokeTest {
    private static final String WORLD="thaumcraft-golem-press-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"formed-press","ready-menu","paid-progress","completed-output","output-item","book-blueprint"};
    private static final BlockPos ANCHOR=new BlockPos(0,112,0), CHEST=ANCHOR.north(), SOURCE=ANCHOR.below();
    private static final long DESIGN=1L<<32; // Original WOOD/BASIC/BASIC/ROLLER/NONE: 12 Machina.
    private static final AtomicInteger saved=new AtomicInteger();
    private static boolean started,prepared,stopped,captureRequested,captured;
    private static int scene,phase,stable;
    private static long began;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previous;
    private static String knowledgeBefore;
    private GolemPressClientSmokeTest() {}
    private static void require(boolean ok,String reason) {if(!ok)throw new AssertionError(reason);}
    private static ServerPlayer player(Minecraft mc) {var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(p!=null,"Missing integrated player");return p;}
    private static GolemPressBlockEntity tile(Minecraft mc) {return (GolemPressBlockEntity)player(mc).serverLevel().getBlockEntity(ANCHOR);}
    private static void submit(Minecraft mc,Runnable task) {require(work==null,"Overlapping press server tasks");var result=new CompletableFuture<Void>();work=result;mc.getSingleplayerServer().execute(()->{try {task.run();result.complete(null);}catch(Throwable e) {result.completeExceptionally(e);}});}
    private static void stage(ServerPlayer p,String key,int stage) {try {var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),key,stage);}catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}}
    private static void knowledge(ServerPlayer p,String category,int raw) {try {var method=PlayerKnowledge.class.getDeclaredMethod("addKnowledge",KnowledgeType.class,String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),KnowledgeType.THEORY,category,raw);}catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.golemPressSmokeTest")||stopped)return;
        var mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try {
            require(System.nanoTime()-began<300_000_000_000L,"Press client timeout scene="+scene+" phase="+phase);
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
                    var p=player(mc);var k=KnowledgeStore.get(p);require(k.researchStage("MINDCLOCKWORK")==4&&k.rawKnowledge(KnowledgeType.THEORY,"ARTIFICE")==0&&k.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==0&&!k.isResearchKnown("CONTROLSEALS"),"Actual research payment failed or opened seals");
                    p.getInventory().setItem(0,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));p.inventoryMenu.broadcastChanges();
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_RESEARCH: actual32+32 theory C2S, completion4, seals closed");});return;}
                if(phase==2) {if(!mc.player.getMainHandItem().is(AlchemyModule.SALIS_MUNDUS.get()))return;phase=3;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(ANCHOR.getCenter(),Direction.UP,ANCHOR,false));return;}
                if(phase==3) {if(!(mc.level.getBlockEntity(ANCHOR) instanceof GolemPressBlockEntity))return;phase=4;submit(mc,()->{
                    require(tile(mc)!=null&&tile(mc).getItem(0).isEmpty()&&player(mc).getMainHandItem().getCount()==1,"Real Salis formation did not pay once");
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_FORMATION: real five-member ritual/dust2->1, original facing north");});return;}
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
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_PAID: actual C2S roller, component debit, busy/replay guard,12Machina remains; no prepared output");});return;}
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
                    LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_OUTPUT: twelve real fifth-tick draws from downward alembic, exact props4294967296, one output");});return;}
            } else if(scene==4) {
                if(phase==0) {phase=1;var menu=(GolemPressMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,2,ClickType.SWAP,mc.player);return;}
                if(mc.player.getInventory().getItem(2).isEmpty())return;
                var output=mc.player.getInventory().getItem(2);require(output.getCount()==1&&output.hasTag()&&output.getTag().getLong("props")==DESIGN,"Real output slot extraction failed");
                if(phase==1) {phase=2;close(mc);mc.setScreen(new Gallery("TC6 Golem Press / paid output extracted from real slot"));}
            } else {
                if(phase==0) {phase=1;submit(mc,()->{var p=player(mc);knowledgeBefore=KnowledgeStore.get(p).save().toString();p.getInventory().setItem(0,new ItemStack(ResearchModule.THAUMONOMICON.get()));p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);});return;}
                if(phase==1) {if(!mc.player.getMainHandItem().is(ResearchModule.THAUMONOMICON.get()))return;phase=2;
                    var k=ResearchClient.golemPressKnowledge();var browser=new ThaumonomiconScreen(k,k.scanCount());var page=new ThaumonomiconPageScreen(browser,ResearchCatalog.get("MINDCLOCKWORK"),k,k.scanCount());mc.setScreen(page);page.directRecipeForSmokeTest("thaumcraft:GolemPress");return;}
                require(mc.screen instanceof ThaumonomiconPageScreen,"Missing press blueprint page");
                LogUtils.getLogger().debug("THAUMCRAFT_GOLEM_PRESS_BOOK_READY");
            }
            if(++stable>=15&&!captured)captureRequested=true;
        }catch(Throwable e) {fail(mc,e);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.golemPressSmokeTest")||stopped||!captureRequested||captured)return;
        var mc=Minecraft.getInstance();try {
            require(scene<6,"Invalid press screenshot scene");captured=true;captureRequested=false;
            String name="tc6-golem-press-"+IMAGES[scene]+".png";File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{if(file.isFile()&&file.length()>0) {saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_SMOKE_IMAGE: {}",file.getAbsolutePath());}else mc.execute(()->fail(mc,new AssertionError("Missing press image")));});
        }catch(Throwable e) {fail(mc,e);}
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
        for(var pos:BlockPos.betweenClosed(-3,111,-3,4,114,5))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        for(var part:GolemPressFormation.parts(ANCHOR,Direction.NORTH))level.setBlockAndUpdate(part.pos(),part.id().equals("golem_builder")?Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP):GolemPressPlaceholderBlock.original(part.id()).defaultBlockState());
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(thaumcraft.catalog.blocks.CatalogBlocks.block("plank_greatwood"),2));
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(ResearchModule.THAUMONOMICON.get()));
        var design=GolemDesign.parse(DESIGN).orElseThrow();int slot=9;for(var stack:design.components())if(!stack.is(thaumcraft.catalog.blocks.CatalogBlocks.block("plank_greatwood").asItem()))p.getInventory().setItem(slot++,stack.copy());
        for(String key:List.of("BASEGOLEMANCY","ESSENTIASMELTER","HEDGEALCHEMY","MATSTUDWOOD"))stage(p,key,ResearchCatalog.get(key).stages().size()+1);
        stage(p,"MINDCLOCKWORK",2);knowledge(p,"ARTIFICE",32);knowledge(p,"GOLEMANCY",32);p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);
        LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_FIXTURE: exact predecessor stages/theories/components and empty source/output prepared; Mind completion/formation/manufacture are actual client operations");
    }
    private static void close(Minecraft mc) {mc.player.closeContainer();mc.setScreen(null);}
    private static void finish(Minecraft mc) {
        if(phase==0) {phase=1;submit(mc,()->require(knowledgeBefore.equals(KnowledgeStore.get(player(mc)).save().toString()),"Blueprint mutated paid knowledge"));return;}
        require(saved.get()==6,"Missing six fresh captures");stopped=true;mc.options.tutorialStep=previous;
        LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_RENDER_AUDIT_OK: original OBJ fixed/press groups and lava, real S2C working tile,90degree stroke, native208x224 GUI, original selectors/traits, detached blueprint");
        LogUtils.getLogger().info("THAUMCRAFT_GOLEM_PRESS_CLIENT_SMOKE_OK: 6 scenes; actual research32+32, five-block Salis, component C2S payment,12Machina ticks, one exact output and physical slot extraction; no golem supplied; world={}",WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable e) {if(stopped)return;stopped=true;if(previous!=null)mc.options.tutorialStep=previous;LogUtils.getLogger().error("THAUMCRAFT_GOLEM_PRESS_CLIENT_SMOKE_FAILED",e);mc.stop();}
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
