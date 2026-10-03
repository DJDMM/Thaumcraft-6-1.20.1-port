package thaumcraft.client.research;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.*;
import thaumcraft.essentia.production.client.SmelterScreen;
import thaumcraft.essentia.transport.*;
import thaumcraft.research.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in, owned integrated world: real menu packets, production ticks, S2C and actual renderers. */
@Mod.EventBusSubscriber(modid="thaumcraft", value=Dist.CLIENT)
public final class EssentiaProductionClientSmokeTest {
    private static final String WORLD="thaumcraft-essentia-production-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"smelter-idle","smelter-burning","production-network","machines",
            "six-tubes","tube-controls","alembic-labels","smelter-recipe","jar-recipe","tube-recipe"};
    private static final BlockPos SMELTER=new BlockPos(0,112,0), ALEMBIC=SMELTER.above(), JAR=new BlockPos(2,112,0);
    private record Display(String name,String id,BlockPos pos) {}
    private static final List<List<Display>> galleries=new ArrayList<>();
    private static final AtomicInteger saved=new AtomicInteger();
    private static CompletableFuture<Void> work;
    private static volatile CompoundTag snapshot;
    private static boolean started,setup,prepared,stopped,captureRequested,captured;
    private static int scene,phase,stableTicks,modelStates;
    private static long began;
    private static TutorialSteps previousTutorial;
    private static String bookBefore;
    private EssentiaProductionClientSmokeTest() {}
    static void snapshot(CompoundTag tag) { if(Boolean.getBoolean("thaumcraft.essentiaProductionSmokeTest"))snapshot=tag.copy(); }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.essentiaProductionSmokeTest")||stopped)return;
        Minecraft mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try {
            require(System.nanoTime()-began<420_000_000_000L,"Essentia production integrated audit timed out: scene="+scene+", phase="+phase);
            if(!started){startWorld(mc);return;}
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong production audit world");
            mc.getToasts().clear();
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!setup){setup=true;submit(mc,()->prepareWorld(mc));return;}
            if(scene==IMAGES.length){finish(mc);return;}
            if(scene==0){
                if(!(mc.level.getBlockEntity(SMELTER) instanceof SmelterBlockEntity))return;
                if(mc.player.getInventory().getItem(9).getCount()!=8||!mc.player.getInventory().getItem(10).is(Items.COAL))return;
                if(!prepared){auditModels(mc);prepared=true;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(SMELTER),Direction.NORTH,SMELTER,false));return;}
                if(!(mc.screen instanceof SmelterScreen)||!(mc.player.containerMenu instanceof SmelterMenu menu))return;
                require(menu.slots.size()==38&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),"Incorrect original smelter inventory");
            } else if(scene==1){
                if(!prepared){prepared=true;require(mc.player.containerMenu instanceof SmelterMenu,"Lost actual machine menu");
                    int id=mc.player.containerMenu.containerId;
                    mc.gameMode.handleInventoryMouseClick(id,2,0,ClickType.QUICK_MOVE,mc.player);
                    mc.gameMode.handleInventoryMouseClick(id,3,0,ClickType.QUICK_MOVE,mc.player);return;}
                if(!(mc.player.containerMenu instanceof SmelterMenu menu)||menu.burnTime()<=0||menu.cookTime()<=0)return;
                require(menu.getSlot(0).getItem().is(Items.STONE)&&menu.getSlot(1).getItem().is(Items.COAL),"Quick move sent resources to wrong machine slots");
                if(phase==0){phase=1;submit(mc,()->{
                    SmelterBlockEntity smelter=(SmelterBlockEntity)player(mc).serverLevel().getBlockEntity(SMELTER);
                    require(smelter.burnTime()>0&&smelter.getItem(0).getCount()<=8&&smelter.getItem(1).getCount()==1,"Authoritative smelter did not consume actual fuel");
                    require(player(mc).getInventory().getItem(9).isEmpty()&&player(mc).getInventory().getItem(10).isEmpty(),"Quick move did not debit player inventory");
                });return;}
            } else if(scene==2){
                if(!prepared){prepared=true;mc.player.closeContainer();mc.setScreen(null);return;}
                if(!(mc.level.getBlockEntity(JAR) instanceof EssentiaJarBlockEntity jar)||jar.amount()<2)return;
                require(jar.aspect()==Aspect.EARTH,"Network delivered the wrong aspect");
                if(phase==0){phase=1;submit(mc,()->verifyProduction(mc));return;}
                if(!(mc.screen instanceof Gallery)){mc.setScreen(new Gallery(0,"Actual fuel -> smelter -> alembic -> tubes -> jar"));return;}
            } else if(scene<7){
                if(!prepared){if(!gallerySynced(mc,scene-2))return;prepared=true;clientMutationAudit(mc);mc.setScreen(new Gallery(scene-2,"TC6 BETA26 / synchronized server devices"));return;}
            } else {
                if(snapshot==null)return;
                if(!prepared){prepared=true;openBook(mc);return;}
                require(bookBefore.equals(PlayerKnowledge.load(snapshot).save().toString()),"Viewing essentia recipes mutated server knowledge");
            }
            if(++stableTicks>=20&&!captured)captureRequested=true;
            if(captured&&saved.get()==scene+1&&stableTicks>=30){scene++;phase=stableTicks=0;prepared=captured=captureRequested=false;}
        } catch(Throwable failure){fail(mc,failure);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.essentiaProductionSmokeTest")||stopped||!captureRequested||captured)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            boolean expected=scene<2?mc.screen instanceof SmelterScreen:scene<7?mc.screen instanceof Gallery:mc.screen instanceof ThaumonomiconPageScreen;
            if(!expected)return;captured=true;captureRequested=false;
            String name="tc6-essentia-production-"+IMAGES[scene]+".png";
            File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
                if(file.isFile()&&file.length()>0){saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_SMOKE_IMAGE: {}",file.getAbsolutePath());}
                else mc.execute(()->fail(mc,new AssertionError("Missing screenshot "+name)));
            });
        } catch(Throwable failure){fail(mc,failure);}
    }
    private static void startWorld(Minecraft mc){
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        started=true;previousTutorial=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();mc.getToasts().clear();
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        var settings=new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_SMOKE_WORLD: {}",WORLD);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(0x54433613L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static ServerPlayer player(Minecraft mc){ServerPlayer player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(player!=null,"Missing actual integrated player");return player;}
    private static void submit(Minecraft mc,Runnable action){require(work==null,"Overlapping production server tasks");CompletableFuture<Void> result=new CompletableFuture<>();work=result;
        mc.getSingleplayerServer().execute(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});}
    private static BlockState state(String id){return CatalogBlocks.block(id).defaultBlockState();}
    private static void prepareWorld(Minecraft mc) throws RuntimeException {
        ServerPlayer player=player(mc);var level=player.serverLevel();player.setInvulnerable(true);
        for(BlockPos pos:BlockPos.betweenClosed(-4,111,-4,20,114,16))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(SMELTER,state("smelter_basic"));level.setBlockAndUpdate(ALEMBIC,state("alembic"));
        level.setBlockAndUpdate(new BlockPos(1,113,0),state("tube"));level.setBlockAndUpdate(new BlockPos(2,113,0),state("tube"));level.setBlockAndUpdate(JAR,state("jar_normal"));
        ItemStack filter=new ItemStack(CatalogBlocks.block("jar_normal"));filter.getOrCreateTag().putString("AspectFilter",Aspect.EARTH.getTag());((EssentiaJarBlockEntity)level.getBlockEntity(JAR)).readItem(filter);
        player.teleportTo(.5,112,3.5);player.setYRot(180);player.setXRot(20);player.getInventory().clearContent();
        player.getInventory().setItem(9,new ItemStack(Items.STONE,8));player.getInventory().setItem(10,new ItemStack(Items.COAL,2));player.inventoryMenu.broadcastChanges();
        galleries.add(List.of(new Display("Basic smelter","smelter_basic",SMELTER),new Display("Distilled output","alembic",ALEMBIC),
                new Display("Suction propagation","tube",new BlockPos(1,113,0)),new Display("Terra collection","jar_normal",JAR)));
        galleries.add(placeGallery(level,0,new String[]{"smelter_basic","smelter_thaumium","smelter_void","alembic","smelter_aux","smelter_vent","bellows","jar_void"}));
        galleries.add(placeGallery(level,1,new String[]{"tube","tube_filter","tube_restrict","tube_oneway","tube_valve","tube_buffer"}));
        galleries.add(placeGallery(level,2,new String[]{"tube_valve","tube_valve","tube_oneway","tube_buffer","tube_buffer","tube_filter"}));
        galleries.add(placeGallery(level,3,new String[]{"alembic","alembic","alembic","alembic","alembic","alembic"}));
        List<Display> controls=galleries.get(3);
        ((TubeBlockEntity)level.getBlockEntity(controls.get(1).pos)).toggleFlow();
        ((TubeBlockEntity)level.getBlockEntity(controls.get(2).pos)).setFacing(Direction.EAST);
        for(int index:List.of(3,4)){
            var buffer=(TubeBufferBlockEntity)level.getBlockEntity(controls.get(index).pos);
            for(int i=0;i<10;i++)require(buffer.addToContainer(i<5?Aspect.FIRE:Aspect.WATER,1)==0,"Buffer fixture insertion failed");
            buffer.cycleChoke(Direction.EAST);if(index==4)buffer.cycleChoke(Direction.EAST);
        }
        ((TubeFilterBlockEntity)level.getBlockEntity(controls.get(5).pos)).setFilter(Aspect.WATER);
        for(int i=0;i<6;i++){
            Display display=galleries.get(4).get(i);AlembicBlockEntity alembic=(AlembicBlockEntity)level.getBlockEntity(display.pos);
            Aspect aspect=i%2==0?Aspect.FIRE:Aspect.WATER;require(alembic.addExact(aspect,i==5?128:20+i*20),"Alembic fixture insertion failed");
            if(i>0)alembic.applyLabel(player,new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH}[i-1],aspect);
        }
        // Explicit late-game UI fixture; paid survival stage and recipe gates are exercised by server GameTests.
        try {
            var setStage=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);setStage.setAccessible(true);
            for(String key:List.of("FIRSTSTEPS","UNLOCKALCHEMY","BASEALCHEMY","METALLURGY","ALUMENTUM","ESSENTIASMELTER","WARDEDJARS","TUBES")){
                ResearchEntry entry=ResearchCatalog.get(key);require(entry!=null,"Missing canonical entry "+key);setStage.invoke(KnowledgeStore.get(player),key,entry.stages().size()+1);KnowledgeStore.recordFact(player,key);
            }
        } catch(ReflectiveOperationException exception){throw new RuntimeException(exception);}
        ResearchNetwork.sync(player);
    }
    private static List<Display> placeGallery(net.minecraft.server.level.ServerLevel level,int page,String[] ids){
        List<Display> result=new ArrayList<>();
        for(int i=0;i<ids.length;i++){
            BlockPos pos=new BlockPos(5+i%4*4,112+page*3,4+i/4*4);level.setBlockAndUpdate(pos,state(ids[i]));
            result.add(new Display(ids[i],ids[i],pos));
            if(ids[i].startsWith("tube")){level.setBlockAndUpdate(pos.west(),state("tube"));level.setBlockAndUpdate(pos.east(),state("tube"));}
        }
        return List.copyOf(result);
    }
    private static void verifyProduction(Minecraft mc){
        var level=player(mc).serverLevel();var smelter=(SmelterBlockEntity)level.getBlockEntity(SMELTER);var jar=(EssentiaJarBlockEntity)level.getBlockEntity(JAR);
        require(smelter.getItem(0).getCount()<8&&smelter.getItem(1).getCount()==1&&jar.amount()>0&&jar.amount()<=40&&jar.aspect()==Aspect.EARTH,"Actual production/payment/transport failed");
        require(jar.filter()==Aspect.EARTH,"Jar filter changed during transport");
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_REAL_FLOW: remainingStone={}, remainingCoal={}, smelter={}, alembic={}, jar={}",smelter.getItem(0).getCount(),smelter.getItem(1).getCount(),smelter.totalEssentia(),((AlembicBlockEntity)level.getBlockEntity(ALEMBIC)).amount(),jar.amount());
    }
    private static boolean gallerySynced(Minecraft mc,int page){
        for(Display d:galleries.get(page))if(mc.level.getBlockState(d.pos).getBlock()!=CatalogBlocks.block(d.id))return false;
        if(page==3){
            var valve=mc.level.getBlockEntity(galleries.get(3).get(1).pos);if(!(valve instanceof TubeBlockEntity tube)||tube.allowFlow())return false;
            for(int i:List.of(3,4)){var be=mc.level.getBlockEntity(galleries.get(3).get(i).pos);if(!(be instanceof TubeBufferBlockEntity buffer)||buffer.getAspects().visSize()!=10||buffer.choke(Direction.EAST)!=i-2)return false;}
            var filter=mc.level.getBlockEntity(galleries.get(3).get(5).pos);if(!(filter instanceof TubeFilterBlockEntity tubeFilter)||tubeFilter.filter()!=Aspect.WATER)return false;
        }
        if(page==4)for(int i=0;i<6;i++){var be=mc.level.getBlockEntity(galleries.get(4).get(i).pos);if(!(be instanceof AlembicBlockEntity alembic)||alembic.amount()!=(i==5?128:20+i*20)||i>0&&alembic.filter()==null)return false;}
        return true;
    }
    private static void clientMutationAudit(Minecraft mc){
        AlembicBlockEntity alembic=(AlembicBlockEntity)mc.level.getBlockEntity(galleries.get(4).get(0).pos);
        CompoundTag before=alembic.saveWithoutMetadata();require(!alembic.addExact(Aspect.FIRE,1)&&!alembic.take(Aspect.FIRE,1)&&!alembic.purge()&&before.equals(alembic.saveWithoutMetadata()),"Client mutated actual alembic");
        TubeBlockEntity tube=(TubeBlockEntity)mc.level.getBlockEntity(galleries.get(3).get(1).pos);before=tube.saveWithoutMetadata();tube.toggleFlow();tube.toggleSide(Direction.EAST);tube.setFacing(Direction.DOWN);
        require(tube.addEssentia(Aspect.FIRE,1,Direction.WEST)==0&&tube.takeEssentia(Aspect.FIRE,1,Direction.EAST)==0&&before.equals(tube.saveWithoutMetadata()),"Client mutated actual tube");
    }
    private static void auditModels(Minecraft mc){
        for(String id:List.of("smelter_basic","smelter_thaumium","smelter_void","alembic","smelter_aux","smelter_vent","bellows","tube","tube_filter","tube_restrict","tube_oneway","tube_valve","tube_buffer")){
            for(BlockState state:CatalogBlocks.block(id).getStateDefinition().getPossibleStates()){
                var model=mc.getBlockRenderer().getBlockModel(state);require(model!=mc.getModelManager().getMissingModel(),"Missing baked state: "+state);
                List<Direction> faces=new ArrayList<>(Arrays.asList(Direction.values()));faces.add(null);
                for(Direction face:faces)for(var quad:model.getQuads(state,face,RandomSource.create(0)))require(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),"Missing baked texture: "+state);
                modelStates++;
            }
            var model=mc.getItemRenderer().getModel(new ItemStack(CatalogBlocks.block(id)),mc.level,mc.player,0);require(model!=mc.getModelManager().getMissingModel(),"Missing machine item "+id);
        }
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_MODEL_STATES: {}",modelStates);
    }
    private static void openBook(Minecraft mc){
        PlayerKnowledge knowledge=PlayerKnowledge.load(snapshot);bookBefore=knowledge.save().toString();var browser=new ThaumonomiconScreen(knowledge,knowledge.scanCount());mc.setScreen(browser);
        String key=scene==7?"ESSENTIASMELTER":scene==8?"WARDEDJARS":"TUBES", output=scene==7?"smelter_basic":scene==8?"jar_normal":"tube_buffer";
        browser.selectForSmokeTest(key);require(mc.screen instanceof ThaumonomiconPageScreen,"Missing canonical book page "+key);
        var page=(ThaumonomiconPageScreen)mc.screen;page.showRecipeForSmokeTest(output);
        var view=page.recipesForSmokeTest().stream().filter(v->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(v.output().getItem()).getPath().equals(output)).findFirst().orElseThrow();
        require(view.unlocked(knowledge)&&!view.ingredients().isEmpty()&&view.vis()>0,"Missing original costs / live recipe "+output);
    }
    private static void finish(Minecraft mc){
        if(phase==0){phase=1;submit(mc,()->require(bookBefore.equals(KnowledgeStore.get(player(mc)).save().toString()),
                "Read-only book views changed authoritative server knowledge"));return;}
        require(saved.get()==IMAGES.length,"Missing fresh scene files");stopped=true;mc.options.tutorialStep=previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_RENDER_AUDIT_OK: {} baked states; 13 functional blocks; real BER / original item sprites / labels / valve / buffer; recipes read only",modelStates);
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_CLIENT_SMOKE_OK: {} scenes; survival menu packets, consumed stone/coal, actual tick production -> jar, S2C and client mutation checks; isolated world={}",saved.get(),WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable failure){if(stopped)return;stopped=true;if(previousTutorial!=null)mc.options.tutorialStep=previousTutorial;LogUtils.getLogger().error("THAUMCRAFT_ESSENTIA_PRODUCTION_CLIENT_SMOKE_FAILED",failure);mc.stop();}
    private static final class Gallery extends Screen {
        private final int page;
        Gallery(int page,String title){super(Component.literal(title));this.page=page;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
            Minecraft mc=Minecraft.getInstance();
            try{
                gui.fill(0,0,width,height,0xff18202b);gui.drawCenteredString(font,title,width/2,12,0xffefdbac);
                List<Display> displays=galleries.get(page);int columns=4;
                for(int i=0;i<displays.size();i++){
                    Display d=displays.get(i);BlockState state=mc.level.getBlockState(d.pos);BlockEntity be=mc.level.getBlockEntity(d.pos);
                    int px=i%columns*(width/columns)+6,py=42+i/columns*210,cw=width/columns-12;
                    gui.fill(px,py,px+cw,py+202,0xff293340);gui.flush();gui.pose().pushPose();
                    try{
                        float orientation=145;
                        if(be instanceof AlembicBlockEntity alembic){
                            if(page==0)orientation+=90; // Show the live EAST pipe nozzle instead of its hidden back side.
                            else if(alembic.filter()!=null)orientation+=switch(alembic.labelFacing()){case EAST -> 90;case SOUTH -> 180;case WEST -> -90;default -> 0;};
                        }
                        gui.pose().translate(px+cw/2,py+96,150);gui.pose().scale(105,-105,105);gui.pose().mulPose(Axis.XP.rotationDegrees(25));gui.pose().mulPose(Axis.YP.rotationDegrees(orientation));gui.pose().translate(-.5,-.4,-.5);Lighting.setupFor3DItems();
                        RenderSystem.runAsFancy(()->{mc.getBlockRenderer().renderSingleBlock(state,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);
                            if(be instanceof AlembicBlockEntity||be instanceof TubeBlockEntity||be instanceof EssentiaJarBlockEntity)require(!mc.getBlockEntityRenderDispatcher().renderItem(be,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Missing live renderer "+d.id);});
                    }finally{gui.flush();gui.pose().popPose();Lighting.setupFor3DItems();}
                    gui.renderItem(new ItemStack(CatalogBlocks.block(d.id)),px+8,py+8);gui.drawCenteredString(font,d.name,px+cw/2,py+172,0xffd4d9df);
                    String details=be instanceof AlembicBlockEntity a?(a.aspect()==null?"empty":a.aspect().getTag())+" "+a.amount()+"/128"
                            :be instanceof EssentiaJarBlockEntity j?(j.aspect()==null?"empty":j.aspect().getTag())+" "+j.amount()+"/250"
                            :be instanceof SmelterBlockEntity s?"stored="+s.totalEssentia()+" / fuel="+s.burnTime()
                            :be instanceof TubeBufferBlockEntity b?"stored="+b.getAspects().visSize()+"/10 / choke="+b.choke(Direction.EAST)
                            :be instanceof TubeBlockEntity t?"suction="+t.getSuctionAmount(null)+" / flow="+t.allowFlow():"functional attachment";
                    gui.drawCenteredString(font,details,px+cw/2,py+187,0xffa7afb8);
                }
            }catch(Throwable failure){fail(mc,failure);}
        }
    }
}
