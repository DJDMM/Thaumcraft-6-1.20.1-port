package thaumcraft.client.research;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.aspects.*;
import thaumcraft.alchemy.*;
import thaumcraft.arcane.*;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.*;
import thaumcraft.essentia.production.client.SmelterScreen;
import thaumcraft.essentia.transport.*;
import thaumcraft.essentia.thaumatorium.*;
import thaumcraft.essentia.thaumatorium.client.ThaumatoriumScreen;
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
            "six-tubes","tube-controls","alembic-labels","smelter-recipe","jar-recipe","tube-recipe","centrifuge","centrifuge-recipe",
            "thaumatorium-formation","thaumatorium-empty","thaumatorium-selected","thaumatorium-progress-paused",
            "thaumatorium-chest-output","thaumatorium-live-render","thaumium-smelter-recipe","thaumatorium-blueprint","stabilizer-recipe","inlay-recipe","clockwork-mind-recipe","hedge-glowstone-recipe","hedge-lava-recipe"};
    private static final BlockPos SMELTER=new BlockPos(0,112,0), ALEMBIC=SMELTER.above(), JAR=new BlockPos(2,112,0);
    private static final BlockPos THAUMATORIUM=new BlockPos(-12,113,0), THAUMATORIUM_CHEST=THAUMATORIUM.south(), THAUMATORIUM_POWER=THAUMATORIUM.above(2);
    private static final List<BlockPos> THAUMATORIUM_SOURCES=List.of(THAUMATORIUM.west(),THAUMATORIUM.east(),THAUMATORIUM.north());
    private static final ResourceLocation ALUMENTUM=ResourceLocation.fromNamespaceAndPath("thaumcraft","alumentum");
    private static final BlockPos BRAIN_WORKBENCH=new BlockPos(-10,111,3);
    private static final Vec3 THAUMATORIUM_CAMERA=new Vec3(-11.5,111,3.5);
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
    private static CrucibleBlockEntity thaumatoriumCrucible;
    private EssentiaProductionClientSmokeTest() {}
    static void snapshot(CompoundTag tag) { if(Boolean.getBoolean("thaumcraft.essentiaProductionSmokeTest"))snapshot=tag.copy(); }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.essentiaProductionSmokeTest")||stopped)return;
        Minecraft mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try {
            require(System.nanoTime()-began<420_000_000_000L,"Essentia production integrated audit timed out: scene="+scene+", phase="+phase);
            if(!started){startWorld(mc);return;}
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null
                    ||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong production audit world");
            mc.getToasts().clear();
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!setup){setup=true;submit(mc,()->prepareWorld(mc));return;}
            if(scene==IMAGES.length){finish(mc);return;}
            // A saved frame may outlive a transient processing state. Advance from that frame before rechecking it.
            if(captured){if(saved.get()==scene+1&&++stableTicks>=30){scene++;phase=stableTicks=0;prepared=captured=captureRequested=false;}return;}
            if(scene==0){
                if(!(mc.level.getBlockEntity(SMELTER) instanceof SmelterBlockEntity))return;
                if(!prepared&&mc.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(.5,112,3.5))>.25)return;
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
            } else if(scene==10){
                if(!prepared){prepared=true;submit(mc,()->{
                    var level=player(mc).serverLevel();var displays=galleries.get(5);
                    var paused=(thaumcraft.essentia.centrifuge.CentrifugeBlockEntity)level.getBlockEntity(displays.get(0).pos);
                    paused.addEssentia(Aspect.MAGIC,1,Direction.DOWN);level.setBlockAndUpdate(displays.get(0).pos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                    var running=(thaumcraft.essentia.centrifuge.CentrifugeBlockEntity)level.getBlockEntity(displays.get(1).pos);
                    running.addEssentia(Aspect.MAGIC,1,Direction.DOWN);
                    var output=(thaumcraft.essentia.centrifuge.CentrifugeBlockEntity)level.getBlockEntity(displays.get(2).pos);
                    output.addEssentia(Aspect.MAGIC,1,Direction.DOWN);
                    for(int i=0;i<39;i++)thaumcraft.essentia.centrifuge.CentrifugeBlockEntity.tick(level,output.getBlockPos(),output.getBlockState(),output);
                    require(output.output()==Aspect.AIR||output.output()==Aspect.ENERGY,"Centrifuge did not split Praecantatio into one original component");
                });return;}
                if(!gallerySynced(mc,5))return;
                if(phase==0){phase=1;LogUtils.getLogger().info("THAUMCRAFT_CENTRIFUGE_CLIENT_FLOW: actual server conversion and client received working BE/input/output/redstone; original rotor animates");}
                if(!(mc.screen instanceof Gallery gallery&&gallery.page==5)){mc.setScreen(new Gallery(5,"TC6 centrifuge: redstone pause / processing / one component"));return;}
            } else if(scene>=12&&scene<=17){
                if(!thaumatoriumScene(mc))return;
            } else {
                if(snapshot==null)return;
                if(!prepared){prepared=true;openBook(mc);return;}
                require(bookBefore.equals(ThaumonomiconCompleteClientSmokeTest.gameplayState(PlayerKnowledge.load(snapshot)).toString()),"Viewing essentia recipes mutated server knowledge");
            }
            if(++stableTicks>=20&&!captured)captureRequested=true;
        } catch(Throwable failure){fail(mc,failure);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.essentiaProductionSmokeTest")||stopped||!captureRequested||captured)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            boolean expected=scene<2?mc.screen instanceof SmelterScreen:scene<7||scene==10||scene==12||scene==17?mc.screen instanceof Gallery
                    :scene>=13&&scene<=15?mc.screen instanceof ThaumatoriumScreen:scene==16?mc.screen instanceof ContainerScreen:mc.screen instanceof ThaumonomiconPageScreen;
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
    private static void closeMenu(Minecraft mc){mc.player.closeContainer();mc.setScreen(null);}
    private static void useBlock(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false));}
    private static ThaumatoriumBlockEntity thaumatorium(Minecraft mc){
        var tile=player(mc).serverLevel().getBlockEntity(THAUMATORIUM);require(tile instanceof ThaumatoriumBlockEntity,"Missing authoritative formed thaumatorium");return (ThaumatoriumBlockEntity)tile;
    }
    private static void tickThaumatorium(Minecraft mc,int ticks){var tile=thaumatorium(mc);for(int i=0;i<ticks;i++)ThaumatoriumBlockEntity.tick(tile.getLevel(),THAUMATORIUM,tile.getBlockState(),tile);}
    private static int sourceAmount(Minecraft mc){return THAUMATORIUM_SOURCES.stream().mapToInt(pos->((AlembicBlockEntity)player(mc).serverLevel().getBlockEntity(pos)).amount()).sum();}
    private static int clientSourceAmount(Minecraft mc){
        int total=0;for(var pos:THAUMATORIUM_SOURCES){if(!(mc.level.getBlockEntity(pos) instanceof AlembicBlockEntity tile))return -1;total+=tile.amount();}return total;
    }
    private static void prepareThaumatorium(Minecraft mc){
        var player=player(mc);var level=player.serverLevel();
        // Separate footing and room keep this physical interaction fixture away from the original producer.
        for(var pos:BlockPos.betweenClosed(-17,110,-5,-7,117,6))level.setBlockAndUpdate(pos,pos.getY()==110?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(THAUMATORIUM.below(2),AlchemyModule.NITOR.get().defaultBlockState());
        level.setBlockAndUpdate(THAUMATORIUM.below(),AlchemyModule.CRUCIBLE.get().defaultBlockState());
        thaumatoriumCrucible=(CrucibleBlockEntity)level.getBlockEntity(THAUMATORIUM.below());
        level.setBlockAndUpdate(THAUMATORIUM,state("metal_alchemical"));level.setBlockAndUpdate(THAUMATORIUM.above(),state("metal_alchemical"));
        for(var pos:THAUMATORIUM_SOURCES)level.setBlockAndUpdate(pos,state("alembic"));
        player.getInventory().clearContent();player.getInventory().selected=0;
        player.getInventory().setItem(0,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));player.getInventory().setItem(9,new ItemStack(Items.COAL,2));
        player.inventoryMenu.broadcastChanges();player.connection.teleport(THAUMATORIUM_CAMERA.x,THAUMATORIUM_CAMERA.y,THAUMATORIUM_CAMERA.z,180,5);
        galleries.add(List.of(new Display("Real dust formation","thaumatorium",THAUMATORIUM),new Display("Potentia source","alembic",THAUMATORIUM_SOURCES.get(0)),
                new Display("Ignis source","alembic",THAUMATORIUM_SOURCES.get(1)),new Display("Perditio source","alembic",THAUMATORIUM_SOURCES.get(2))));
    }
    private static boolean thaumatoriumScene(Minecraft mc){
        if(scene==12){
            if(!prepared){prepared=true;closeMenu(mc);submit(mc,()->prepareThaumatorium(mc));return false;}
            if(phase==0){
                if(mc.player.position().distanceToSqr(THAUMATORIUM_CAMERA)>.25||!mc.player.getMainHandItem().is(AlchemyModule.SALIS_MUNDUS.get())||mc.player.getMainHandItem().getCount()!=2
                        ||!mc.level.getBlockState(THAUMATORIUM).is(CatalogBlocks.block("metal_alchemical"))||!mc.level.getBlockState(THAUMATORIUM.above()).is(CatalogBlocks.block("metal_alchemical")))return false;
                phase=1;useBlock(mc,THAUMATORIUM);return false;
            }
            if(!(mc.level.getBlockEntity(THAUMATORIUM) instanceof ThaumatoriumBlockEntity)||!(mc.level.getBlockEntity(THAUMATORIUM.above()) instanceof ThaumatoriumTopBlockEntity)
                    ||mc.player.getMainHandItem().getCount()!=1)return false;
            if(phase==1){phase=2;submit(mc,()->{
                var player=player(mc);var tile=thaumatorium(mc);var level=player.serverLevel();
                require(tile.facing()==Direction.SOUTH&&level.getBlockEntity(THAUMATORIUM.above()) instanceof ThaumatoriumTopBlockEntity top&&top.base()==tile
                        &&level.getBlockEntity(THAUMATORIUM.below())==thaumatoriumCrucible&&player.getMainHandItem().getCount()==1
                        &&KnowledgeStore.get(player).hasCraft("thaumcraft:thaumatorium"),"Real C2S dust formation did not retain the crucible or debit exactly one dust");
                level.setBlockAndUpdate(THAUMATORIUM_CHEST,Blocks.CHEST.defaultBlockState());
                require(tile.checkHeat(),"Original yellow nitor did not heat the machine");
                LogUtils.getLogger().info("THAUMCRAFT_THAUMATORIUM_CLIENT_FORMATION: actual Salis C2S; two constructs replaced, crucible retained, one dust paid, facing SOUTH, real Nitor heat");
            });return false;}
            if(!gallerySynced(mc,6))return false;
            if(!(mc.screen instanceof Gallery gallery&&gallery.page==6)){mc.setScreen(new Gallery(6,"TC6 Thaumatorium / real Salis formation and three empty transport peers"));return false;}
            return true;
        }
        if(scene==13){
            if(!prepared){prepared=true;closeMenu(mc);useBlock(mc,THAUMATORIUM);return false;}
            if(!(mc.screen instanceof ThaumatoriumScreen)||!(mc.player.containerMenu instanceof ThaumatoriumMenu menu))return false;
            if(menu.revision()<1)return false;
            require(menu.position().equals(THAUMATORIUM)&&menu.slots.size()==37&&menu.getSlot(0).getItem().isEmpty()&&menu.recipes().isEmpty()
                    &&menu.stored().visSize()==0&&menu.capacity()==1,"Empty real thaumatorium menu does not match its original one catalyst inventory");
            return true;
        }
        if(scene==14){
            require(mc.screen instanceof ThaumatoriumScreen&&mc.player.containerMenu instanceof ThaumatoriumMenu,"Lost real thaumatorium menu");
            var menu=(ThaumatoriumMenu)mc.player.containerMenu;
            if(!prepared){if(!mc.player.getInventory().getItem(9).is(Items.COAL)||mc.player.getInventory().getItem(9).getCount()!=2)return false;
                prepared=true;mc.gameMode.handleInventoryMouseClick(menu.containerId,1,0,ClickType.QUICK_MOVE,mc.player);return false;}
            if(!menu.getSlot(0).getItem().is(Items.COAL)||menu.getSlot(0).getItem().getCount()!=2)return false;
            var selected=menu.recipes().stream().filter(view->view.id().equals(ALUMENTUM)).findFirst();if(selected.isEmpty())return false;
            require(selected.get().cost().getAmount(Aspect.ENERGY)==10&&selected.get().cost().getAmount(Aspect.FIRE)==10&&selected.get().cost().getAmount(Aspect.ENTROPY)==5
                    &&selected.get().output().is(AlchemyModule.ALUMENTUM.get())&&selected.get().output().getCount()==1,"Original alumentum recipe cost/output changed");
            if(phase==0){phase=1;ThaumatoriumNetwork.select(menu,ALUMENTUM);return false;}
            if(!selected.get().selected())return false;
            if(phase==1){phase=2;submit(mc,()->{
                var player=player(mc);var tile=thaumatorium(mc);
                require(player.containerMenu instanceof ThaumatoriumMenu serverMenu&&serverMenu.position().equals(THAUMATORIUM)&&tile.selectedRecipes().equals(List.of(ALUMENTUM))
                        &&tile.getItem(0).getCount()==2&&player.getInventory().getItem(9).isEmpty()&&tile.getAspects().visSize()==0,"Actual revision-checked selection or coal quick move failed");
            });return false;}
            require(menu.stored().visSize()==0,"Empty source fixture unexpectedly generated free essentia");return true;
        }
        if(scene==15){
            if(!prepared){prepared=true;submit(mc,()->{
                var level=player(mc).serverLevel();var tile=thaumatorium(mc);Aspect[] aspects={Aspect.ENERGY,Aspect.FIRE,Aspect.ENTROPY};int[] amounts={20,20,10};
                for(int i=0;i<aspects.length;i++)require(((AlembicBlockEntity)level.getBlockEntity(THAUMATORIUM_SOURCES.get(i))).addExact(aspects[i],amounts[i]),"Explicit typed source fixture failed");
                // Tick the real typed transport path and stop one unit before completion; no buffer setter is used.
                for(int i=0;i<200&&tile.getAspects().visSize()<24;i++)tickThaumatorium(mc,1);
                require(tile.getAspects().visSize()==24&&sourceAmount(mc)==26&&tile.getItem(0).getCount()==2,"Thaumatorium did not draw exactly24 typed units from real peers");
                level.setBlockAndUpdate(THAUMATORIUM_POWER,Blocks.REDSTONE_BLOCK.defaultBlockState());
                var before=tile.getAspects();tickThaumatorium(mc,15);
                require(tile.gettingPower()&&before.aspects.equals(tile.getAspects().aspects)&&sourceAmount(mc)==26&&tile.getItem(0).getCount()==2,"Redstone did not preserve real processing buffer and catalyst");
                ((ThaumatoriumMenu)player(mc).containerMenu).broadcastChanges();
                LogUtils.getLogger().info("THAUMCRAFT_THAUMATORIUM_CLIENT_PARTIAL: real fifth-tick typed draw24/25; peers26/50; coal2; redstone freezes paid buffer for capture");
            });return false;}
            if(!(mc.screen instanceof ThaumatoriumScreen)||!(mc.player.containerMenu instanceof ThaumatoriumMenu menu)||menu.stored().visSize()!=24||clientSourceAmount(mc)!=26
                    ||!(mc.level.getBlockEntity(THAUMATORIUM) instanceof ThaumatoriumBlockEntity tile)||tile.getAspects().visSize()!=24||!mc.level.getBlockState(THAUMATORIUM_POWER).is(Blocks.REDSTONE_BLOCK))return false;
            require(menu.recipes().stream().anyMatch(view->view.id().equals(ALUMENTUM)&&view.selected())&&menu.getSlot(0).getItem().getCount()==2,"Paused progress snapshot lost selected recipe/catalyst");return true;
        }
        if(scene==16){
            if(!prepared){prepared=true;closeMenu(mc);submit(mc,()->{
                var level=player(mc).serverLevel();var tile=thaumatorium(mc);var chest=(ChestBlockEntity)level.getBlockEntity(THAUMATORIUM_CHEST);
                level.setBlockAndUpdate(THAUMATORIUM_POWER,Blocks.AIR.defaultBlockState());
                for(int i=0;i<30&&chest.isEmpty();i++)tickThaumatorium(mc,1);
                level.setBlockAndUpdate(THAUMATORIUM_POWER,Blocks.REDSTONE_BLOCK.defaultBlockState());
                require(chest.getItem(0).is(AlchemyModule.ALUMENTUM.get())&&chest.getItem(0).getCount()==1&&tile.getItem(0).is(Items.COAL)&&tile.getItem(0).getCount()==1
                        &&tile.getAspects().visSize()==0&&sourceAmount(mc)==25&&tile.pendingOutput().isEmpty(),"Original25 essentia/one coal payment or single front-chest output failed");
                require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(THAUMATORIUM).inflate(3),entity->entity.getItem().is(AlchemyModule.ALUMENTUM.get())).isEmpty(),"Inventory output also ejected a duplicate");
                LogUtils.getLogger().info("THAUMCRAFT_THAUMATORIUM_CLIENT_FLOW: real C2S formation/menu/coal/selection; three typed peers50->25; buffer24->25->0; coal2->1; actual front chest receives one alumentum; redstone pause preserved");
            });return false;}
            if(!(mc.level.getBlockEntity(THAUMATORIUM) instanceof ThaumatoriumBlockEntity tile)||tile.getItem(0).getCount()!=1||tile.getAspects().visSize()!=0||clientSourceAmount(mc)!=25)return false;
            if(phase==0){phase=1;useBlock(mc,THAUMATORIUM_CHEST);return false;}
            if(!(mc.screen instanceof ContainerScreen))return false;
            require(mc.player.containerMenu.getSlot(0).getItem().is(AlchemyModule.ALUMENTUM.get())&&mc.player.containerMenu.getSlot(0).getItem().getCount()==1,"Real chest menu did not synchronize the paid output");return true;
        }
        if(scene==17)return paidBrainScene(mc);
        throw new AssertionError("Unknown thaumatorium scene "+scene);
    }
    private static ItemStack registeredItem(String path) {
        var id=ResourceLocation.fromNamespaceAndPath("thaumcraft",path);var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        require(item!=Items.AIR,"Missing registered original component "+id);return new ItemStack(item);
    }
    private static ArcaneWorkbenchBlockEntity brainWorkbench(Minecraft mc) {
        var tile=player(mc).serverLevel().getBlockEntity(BRAIN_WORKBENCH);require(tile instanceof ArcaneWorkbenchBlockEntity,"Missing physical Brain Box crafting bench");return (ArcaneWorkbenchBlockEntity)tile;
    }
    private static void prepareBrainCraft(Minecraft mc) {
        var player=player(mc);var level=player.serverLevel();var knowledge=KnowledgeStore.get(player);
        require(knowledge.researchStage("MINDCLOCKWORK")==2&&!knowledge.isResearchCompleteStrict("MINDCLOCKWORK")
                &&knowledge.isResearchCompleteStrict("HEDGEALCHEMY")&&!knowledge.isResearchKnown("CONTROLSEALS"),"Brain craft fixture bypassed the bounded original mind stage");
        level.setBlockAndUpdate(BRAIN_WORKBENCH,ArcaneModule.WORKBENCH.get().defaultBlockState());
        var bench=brainWorkbench(mc);player.getInventory().clearContent();player.getInventory().selected=0;
        // Component/research/aura fixtures are explicit; neither intermediate nor Brain Box output is supplied.
        bench.setItem(1,new ItemStack(Items.GLASS_PANE));bench.setItem(3,new ItemStack(Items.GLASS_PANE));bench.setItem(5,new ItemStack(Items.GLASS_PANE));
        bench.setItem(4,registeredItem("mechanism_simple"));bench.setItem(6,registeredItem("plate_brass"));bench.setItem(8,registeredItem("plate_brass"));bench.setItem(7,new ItemStack(Items.COMPARATOR));
        bench.setItem(10,CatalogModule.aspectStack("crystal_essence",Aspect.FIRE,1));bench.setItem(13,CatalogModule.aspectStack("crystal_essence",Aspect.ORDER,1));
        AuraManager.drainVis(level,BRAIN_WORKBENCH,Float.MAX_VALUE,false);AuraManager.addVis(level,BRAIN_WORKBENCH,100);
        player.inventoryMenu.broadcastChanges();
        LogUtils.getLogger().info("THAUMCRAFT_CLOCKWORK_CLIENT_FIXTURE: completed Hedge/Golem unlock parents; Mind entered2 only, unpaid32+32 theories; press chapter/seals closed; actual workbench with glass3/brass2/simple1/comparator1, Ignis1/Ordo1 and100 aura supplied explicitly; no mind/Brain output supplied");
    }
    private static boolean paidBrainScene(Minecraft mc) {
        if(!prepared){prepared=true;closeMenu(mc);submit(mc,()->prepareBrainCraft(mc));phase=1;return false;}
        if(phase==1){
            if(!mc.level.getBlockState(BRAIN_WORKBENCH).is(ArcaneModule.WORKBENCH.get())||!mc.player.getMainHandItem().isEmpty())return false;
            phase=2;useBlock(mc,BRAIN_WORKBENCH);return false;
        }
        if(phase==2){
            if(!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu)||!menu.getSlot(0).getItem().is(registeredItem("mind_clockwork").getItem())||!menu.craftable())return false;
            require(menu.requiredVis()==25&&menu.crystalCost(1)==1&&menu.crystalCost(4)==1,"Mind recipe changed original25vis/Ignis1/Ordo1 payment");
            mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.SWAP,mc.player);phase=3;return false;
        }
        if(phase==3){
            if(!mc.player.getMainHandItem().is(registeredItem("mind_clockwork").getItem()))return false;
            phase=4;submit(mc,()->{
                var player=player(mc);var bench=brainWorkbench(mc);var knowledge=KnowledgeStore.get(player);
                require(bench.isEmpty()&&player.getMainHandItem().is(registeredItem("mind_clockwork").getItem())&&player.getMainHandItem().getCount()==1
                        &&knowledge.hasCraft("thaumcraft:mind_clockwork")&&AuraManager.getVis(player.serverLevel(),BRAIN_WORKBENCH)<90,"Real C2S mind result did not consume its components/crystals/vis");
                for(int slot:new int[]{0,2,6,8})bench.setItem(slot,registeredItem("plate_iron"));
                for(int slot:new int[]{1,3,5,7})bench.setItem(slot,registeredItem("amber"));
                bench.setItem(12,CatalogModule.aspectStack("crystal_essence",Aspect.EARTH,1));bench.setItem(13,CatalogModule.aspectStack("crystal_essence",Aspect.ORDER,1));
                require(bench.getItem(4).isEmpty(),"Mnemonic fixture injected a replacement mind");player.containerMenu.broadcastChanges();
                LogUtils.getLogger().info("THAUMCRAFT_CLOCKWORK_CLIENT_MIND: real result-slot SWAP C2S created one mind at entered2, consumed entire original grid/Ignis1/Ordo1; paid recipe25vis, observed remainingAura={}",AuraManager.getVis(player.serverLevel(),BRAIN_WORKBENCH));
            });return false;
        }
        if(phase==4){
            if(!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu)||!menu.getSlot(1).getItem().is(registeredItem("plate_iron").getItem())
                    ||!menu.getSlot(2).getItem().is(registeredItem("amber").getItem())||!menu.getSlot(5).getItem().isEmpty())return false;
            // Move the exact crafted stack, through ordinary menu C2S, into the matrix center.
            mc.gameMode.handleInventoryMouseClick(menu.containerId,43,0,ClickType.PICKUP,mc.player);
            mc.gameMode.handleInventoryMouseClick(menu.containerId,5,0,ClickType.PICKUP,mc.player);phase=5;return false;
        }
        if(phase==5){
            if(!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu)||!menu.getSlot(0).getItem().is(CatalogBlocks.block("brain_box").asItem())||!menu.craftable())return false;
            require(menu.requiredVis()==50&&menu.crystalCost(3)==1&&menu.crystalCost(4)==1&&menu.getCarried().isEmpty()
                    &&mc.player.getMainHandItem().isEmpty(),"Mnemonic original payment or real crafted center transfer changed");
            mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.SWAP,mc.player);phase=6;return false;
        }
        if(phase==6){
            if(!mc.player.getMainHandItem().is(CatalogBlocks.block("brain_box").asItem()))return false;
            phase=7;submit(mc,()->{
                var player=player(mc);var knowledge=KnowledgeStore.get(player);
                require(brainWorkbench(mc).isEmpty()&&player.getMainHandItem().is(CatalogBlocks.block("brain_box").asItem())&&player.getMainHandItem().getCount()==1
                        &&knowledge.hasCraft("thaumcraft:mind_clockwork")&&knowledge.hasCraft("thaumcraft:brain_box")&&knowledge.researchStage("MINDCLOCKWORK")==2
                        &&!knowledge.isResearchCompleteStrict("MINDCLOCKWORK")&&!knowledge.isResearchKnown("CONTROLSEALS")&&AuraManager.getVis(player.serverLevel(),BRAIN_WORKBENCH)<50,
                        "Actual crafted mind -> Brain Box chain failed payment, provenance or bounded research");
                LogUtils.getLogger().info("THAUMCRAFT_CLOCKWORK_CLIENT_BRAIN: real original25+50vis recipe chain via result/center C2S; actual crafted mind consumed, plates4/amber4/Terra1/Ordo1 paid; one Brain Box, bounded Mind2 and no seals; observed remainingAura={}",AuraManager.getVis(player.serverLevel(),BRAIN_WORKBENCH));
            });closeMenu(mc);mc.options.keyShift.setDown(true);return false;
        }
        if(phase==7){
            if(!mc.player.isShiftKeyDown()||mc.screen!=null)return false;
            // Only this isolated client's internal key state is used; no native/global input.
            BlockPos host=THAUMATORIUM.above();
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(host).add(-.5,0,0),Direction.WEST,host,false));
            mc.options.keyShift.setDown(false);phase=8;return false;
        }
        if(phase==8){
            var brain=THAUMATORIUM.above().west();
            if(!mc.level.getBlockState(brain).is(CatalogBlocks.block("brain_box"))||!mc.player.getMainHandItem().isEmpty())return false;
            phase=9;submit(mc,()->{
                var player=player(mc);var level=player.serverLevel();var tile=thaumatorium(mc);
                require(level.getBlockState(brain).getValue(BrainBoxBlock.FACING)==Direction.EAST&&player.getMainHandItem().isEmpty(),"Physical crafted Brain Box placement did not pay/follow the real clicked face");
                tile.getUpgrades();require(tile.maxRecipes()==3&&tile.selectedRecipes().equals(List.of(ALUMENTUM)),"Actual crafted facing Brain Box did not add exactly two program slots");
                galleries.add(List.of(new Display("Live selected output","thaumatorium",THAUMATORIUM),new Display("Paid mind -> matrix / +2 slots","brain_box",brain),
                        new Display("Potentia remaining","alembic",THAUMATORIUM_SOURCES.get(0)),new Display("Ignis remaining","alembic",THAUMATORIUM_SOURCES.get(1))));
                LogUtils.getLogger().info("THAUMCRAFT_CLOCKWORK_CLIENT_PLACEMENT: actual crafted Brain Box placed through owned-client C2S, one item consumed, original EAST attachment yields capacity3");
            });return false;
        }
        if(!gallerySynced(mc,7)||!(mc.level.getBlockEntity(THAUMATORIUM) instanceof ThaumatoriumBlockEntity tile)||tile.maxRecipes()!=3
                ||!tile.cyclingOutput(mc.level.getGameTime()).is(AlchemyModule.ALUMENTUM.get())||clientSourceAmount(mc)!=25)return false;
        if(phase==9){phase=10;var before=tile.saveWithoutMetadata();tile.setAspects(new AspectList().add(Aspect.FIRE,99));
            require(tile.addToContainer(Aspect.FIRE,1)==1&&!tile.takeFromContainer(Aspect.FIRE,1)&&before.equals(tile.saveWithoutMetadata()),"Client mutated synced thaumatorium essentia");
            LogUtils.getLogger().info("THAUMCRAFT_THAUMATORIUM_CLIENT_RENDER: synchronized formed BE cycles real selected alumentum; native original model/actually crafted and placed Brain capacity3; client mutation rejected");}
        if(!(mc.screen instanceof Gallery gallery&&gallery.page==7)){mc.setScreen(new Gallery(7,"TC6 Thaumatorium / paid clockwork mind -> Brain Box and synchronized result preview"));return false;}
        return true;
    }
    private static void prepareWorld(Minecraft mc) throws RuntimeException {
        ServerPlayer player=player(mc);var level=player.serverLevel();player.setInvulnerable(true);
        for(BlockPos pos:BlockPos.betweenClosed(-4,111,-4,20,114,16))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(SMELTER,state("smelter_basic"));level.setBlockAndUpdate(ALEMBIC,state("alembic"));
        level.setBlockAndUpdate(new BlockPos(1,113,0),state("tube"));level.setBlockAndUpdate(new BlockPos(2,113,0),state("tube"));level.setBlockAndUpdate(JAR,state("jar_normal"));
        ItemStack filter=new ItemStack(CatalogBlocks.block("jar_normal"));filter.getOrCreateTag().putString("AspectFilter",Aspect.EARTH.getTag());((EssentiaJarBlockEntity)level.getBlockEntity(JAR)).readItem(filter);
        player.connection.teleport(.5,112,3.5,180,20);player.getInventory().clearContent();
        player.getInventory().setItem(9,new ItemStack(Items.STONE,8));player.getInventory().setItem(10,new ItemStack(Items.COAL,2));player.inventoryMenu.broadcastChanges();
        galleries.add(List.of(new Display("Basic smelter","smelter_basic",SMELTER),new Display("Distilled output","alembic",ALEMBIC),
                new Display("Suction propagation","tube",new BlockPos(1,113,0)),new Display("Terra collection","jar_normal",JAR)));
        galleries.add(placeGallery(level,0,new String[]{"smelter_basic","smelter_thaumium","smelter_void","alembic","smelter_aux","smelter_vent","bellows","jar_void"}));
        galleries.add(placeGallery(level,1,new String[]{"tube","tube_filter","tube_restrict","tube_oneway","tube_valve","tube_buffer"}));
        galleries.add(placeGallery(level,2,new String[]{"tube_valve","tube_valve","tube_oneway","tube_buffer","tube_buffer","tube_filter"}));
        galleries.add(placeGallery(level,3,new String[]{"alembic","alembic","alembic","alembic","alembic","alembic"}));
        galleries.add(placeGallery(level,4,new String[]{"centrifuge","centrifuge","centrifuge"}));
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
            for(String key:List.of("FIRSTSTEPS","UNLOCKALCHEMY","BASEALCHEMY","METALLURGY","ALUMENTUM","ESSENTIASMELTER","WARDEDJARS","TUBES","CENTRIFUGE","ESSENTIASMELTERTHAUMIUM","THAUMATORIUM","UNLOCKINFUSION","BASEINFUSION","INFUSION","INFUSIONSTABLE","UNLOCKARTIFICE","UNLOCKAUROMANCY","HEDGEALCHEMY","UNLOCKGOLEMANCY","BASEGOLEMANCY","MATSTUDWOOD")){
                ResearchEntry entry=ResearchCatalog.get(key);require(entry!=null,"Missing canonical entry "+key);setStage.invoke(KnowledgeStore.get(player),key,entry.stages().size()+1);KnowledgeStore.recordFact(player,key);
            }
            require(Boolean.TRUE.equals(setStage.invoke(KnowledgeStore.get(player),"MINDCLOCKWORK",2)),"Could not fixture only entered clockwork stage2");
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
        if(page==5){
            var displays=galleries.get(5);
            if(!(mc.level.getBlockEntity(displays.get(0).pos) instanceof thaumcraft.essentia.centrifuge.CentrifugeBlockEntity paused)
                    ||paused.input()!=Aspect.MAGIC||!mc.level.hasNeighborSignal(displays.get(0).pos))return false;
            if(!(mc.level.getBlockEntity(displays.get(1).pos) instanceof thaumcraft.essentia.centrifuge.CentrifugeBlockEntity running)
                    ||running.rotationDegrees()<=0)return false;
            if(!(mc.level.getBlockEntity(displays.get(2).pos) instanceof thaumcraft.essentia.centrifuge.CentrifugeBlockEntity output)
                    ||output.output()!=Aspect.AIR&&output.output()!=Aspect.ENERGY)return false;
        }
        return true;
    }
    private static void clientMutationAudit(Minecraft mc){
        AlembicBlockEntity alembic=(AlembicBlockEntity)mc.level.getBlockEntity(galleries.get(4).get(0).pos);
        CompoundTag before=alembic.saveWithoutMetadata();require(!alembic.addExact(Aspect.FIRE,1)&&!alembic.take(Aspect.FIRE,1)&&!alembic.purge()&&before.equals(alembic.saveWithoutMetadata()),"Client mutated actual alembic");
        TubeBlockEntity tube=(TubeBlockEntity)mc.level.getBlockEntity(galleries.get(3).get(1).pos);before=tube.saveWithoutMetadata();tube.toggleFlow();tube.toggleSide(Direction.EAST);tube.setFacing(Direction.DOWN);
        require(tube.addEssentia(Aspect.FIRE,1,Direction.WEST)==0&&tube.takeEssentia(Aspect.FIRE,1,Direction.EAST)==0&&before.equals(tube.saveWithoutMetadata()),"Client mutated actual tube");
    }
    private static void auditModels(Minecraft mc){
        for(String id:List.of("smelter_basic","smelter_thaumium","smelter_void","alembic","smelter_aux","smelter_vent","bellows","tube","tube_filter","tube_restrict","tube_oneway","tube_valve","tube_buffer","centrifuge","thaumatorium","thaumatorium_top","brain_box")){
            for(BlockState state:CatalogBlocks.block(id).getStateDefinition().getPossibleStates()){
                var model=mc.getBlockRenderer().getBlockModel(state);require(model!=mc.getModelManager().getMissingModel(),"Missing baked state: "+state);
                List<Direction> faces=new ArrayList<>(Arrays.asList(Direction.values()));faces.add(null);
                for(Direction face:faces)for(var quad:model.getQuads(state,face,RandomSource.create(0)))require(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),"Missing baked texture: "+state);
                modelStates++;
            }
            if(CatalogBlocks.block(id).asItem()!=Items.AIR){var model=mc.getItemRenderer().getModel(new ItemStack(CatalogBlocks.block(id)),mc.level,mc.player,0);require(model!=mc.getModelManager().getMissingModel(),"Missing machine item "+id);}
        }
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_MODEL_STATES: {}",modelStates);
    }
    private static void openBook(Minecraft mc){
        PlayerKnowledge knowledge=PlayerKnowledge.load(snapshot);bookBefore=ThaumonomiconCompleteClientSmokeTest.gameplayState(knowledge).toString();var browser=new ThaumonomiconScreen(knowledge,knowledge.scanCount());mc.setScreen(browser);
        String key=switch(scene){case 7->"ESSENTIASMELTER";case 8->"WARDEDJARS";case 11->"CENTRIFUGE";case 18->"ESSENTIASMELTERTHAUMIUM";case 19->"THAUMATORIUM";case 20,21->"INFUSIONSTABLE";case 22->"MINDCLOCKWORK";case 23,24->"HEDGEALCHEMY";default->"TUBES";};
        String output=switch(scene){case 7->"smelter_basic";case 8->"jar_normal";case 11->"centrifuge";case 18->"smelter_thaumium";case 20->"stabilizer";case 21->"inlay";case 22->"mind_clockwork";case 23->"glowstone_dust";case 24->"lava_bucket";default->"tube_buffer";};
        browser.selectForSmokeTest(key);require(mc.screen instanceof ThaumonomiconPageScreen,"Missing canonical book page "+key);
        var page=(ThaumonomiconPageScreen)mc.screen;
        if(scene==19){page.showStructureForSmokeTest("thaumcraft:Thaumatorium");var structure=page.structureForSmokeTest("thaumcraft:Thaumatorium");
            require(structure!=null&&!structure.detachedPreview().states().isEmpty(),"Missing detached Thaumatorium construction");return;}
        page.showRecipeForSmokeTest(output);
        var view=page.recipesForSmokeTest().stream().filter(v->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(v.output().getItem()).getPath().equals(output)).findFirst().orElseThrow();
        require(view.unlocked(knowledge)&&!view.reference()&&!view.ingredients().isEmpty(),"Missing unlocked live recipe "+output);
        if(scene>=23){
            require(knowledge.isResearchCompleteStrict("HEDGEALCHEMY")&&page.chaptersForSmokeTest().equals(List.of("research.HEDGEALCHEMY.stage.4"))
                    &&view.kind().equals("crucible")&&view.vis()==0&&view.aspects().visSize()>0&&!page.availableForSmokeTest(),"Completed Hedge spread lost original current/final-stage or paid essentia recipe");
            LogUtils.getLogger().info("THAUMCRAFT_HEDGE_CLIENT_BOOK: completed original final stage4; live synchronized {} catalyst/essentia/output, read only",output);
        } else {
            require(view.vis()>0,"Missing original arcane vis cost "+output);
            if(scene==22){
                require(knowledge.researchStage("MINDCLOCKWORK")==2&&!knowledge.isResearchCompleteStrict("MINDCLOCKWORK")&&!knowledge.isResearchKnown("CONTROLSEALS")
                        &&page.chaptersForSmokeTest().equals(List.of("research.MINDCLOCKWORK.stage.2"))&&!page.availableForSmokeTest()
                        &&view.research().equals("MINDCLOCKWORK@2")&&view.vis()==25&&view.crystals()[1]==1&&view.crystals()[4]==1,
                        "Clockwork current-stage recipe bypassed original gate, cost or unpaid theory requirement");
                require(page.recipesForSmokeTest().stream().allMatch(recipe->recipe.output().is(registeredItem("mind_clockwork").getItem())),"Current-stage mind book exposed later press/seal recipes before theory payment");
                LogUtils.getLogger().info("THAUMCRAFT_CLOCKWORK_CLIENT_BOOK: entered stage2 only; original live25vis/Ignis1/Ordo1 recipe; unpaid32+32 theories; no later press chapter/seals or enabled completion action; read only");
            }
        }
    }
    private static void finish(Minecraft mc){
        if(phase==0){phase=1;submit(mc,()->require(bookBefore.equals(ThaumonomiconCompleteClientSmokeTest.gameplayState(KnowledgeStore.get(player(mc))).toString()),
                "Read-only book views changed authoritative server knowledge"));return;}
        require(saved.get()==IMAGES.length,"Missing fresh scene files");mc.options.keyShift.setDown(false);stopped=true;mc.options.tutorialStep=previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_RENDER_AUDIT_OK: {} baked states; 17 functional blocks; real BER / original item sprites / labels / valve / buffer / centrifuge / thaumatorium; recipes and blueprint read only",modelStates);
        LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_PRODUCTION_CLIENT_SMOKE_OK: {} scenes; survival menu packets, consumed stone/coal/dust, actual tick production -> jar and typed peers -> thaumatorium -> chest, actual paid clockwork mind -> Brain Box C2S chain, bounded Mind2/Hedge final book, S2C and client mutation checks; isolated world={}",saved.get(),WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable failure){if(stopped)return;mc.options.keyShift.setDown(false);stopped=true;if(previousTutorial!=null)mc.options.tutorialStep=previousTutorial;LogUtils.getLogger().error("THAUMCRAFT_ESSENTIA_PRODUCTION_CLIENT_SMOKE_FAILED",failure);mc.stop();}
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
                        boolean tall=be instanceof ThaumatoriumBlockEntity;
                        if(be instanceof ThaumatoriumBlockEntity machine)orientation+=switch(machine.facing()){case EAST->90;case SOUTH->180;case WEST->-90;default->0;};
                        float modelScale=tall?70:105;
                        gui.pose().translate(px+cw/2,py+(tall?85:96),150);gui.pose().scale(modelScale,-modelScale,modelScale);gui.pose().mulPose(Axis.XP.rotationDegrees(25));gui.pose().mulPose(Axis.YP.rotationDegrees(orientation));gui.pose().translate(-.5,tall?-.95:-.4,-.5);Lighting.setupFor3DItems();
                        RenderSystem.runAsFancy(()->{mc.getBlockRenderer().renderSingleBlock(state,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);
                            if(be instanceof AlembicBlockEntity||be instanceof TubeBlockEntity||be instanceof EssentiaJarBlockEntity||be instanceof thaumcraft.essentia.centrifuge.CentrifugeBlockEntity||be instanceof ThaumatoriumBlockEntity)require(!mc.getBlockEntityRenderDispatcher().renderItem(be,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Missing live renderer "+d.id);});
                    }finally{gui.flush();gui.pose().popPose();Lighting.setupFor3DItems();}
                    gui.renderItem(new ItemStack(CatalogBlocks.block(d.id)),px+8,py+8);gui.drawCenteredString(font,d.name,px+cw/2,py+172,0xffd4d9df);
                    String details=be instanceof AlembicBlockEntity a?(a.aspect()==null?"empty":a.aspect().getTag())+" "+a.amount()+"/128"
                            :be instanceof EssentiaJarBlockEntity j?(j.aspect()==null?"empty":j.aspect().getTag())+" "+j.amount()+"/250"
                            :be instanceof SmelterBlockEntity s?"stored="+s.totalEssentia()+" / fuel="+s.burnTime()
                            :be instanceof TubeBufferBlockEntity b?"stored="+b.getAspects().visSize()+"/10 / choke="+b.choke(Direction.EAST)
                            :be instanceof TubeBlockEntity t?"suction="+t.getSuctionAmount(null)+" / flow="+t.allowFlow()
                            :be instanceof thaumcraft.essentia.centrifuge.CentrifugeBlockEntity c?"in="+(c.input()==null?"empty":c.input().getTag())+" / out="+(c.output()==null?"empty":c.output().getTag())
                            :be instanceof ThaumatoriumBlockEntity t?"selected="+t.selectedRecipes().size()+" / slots="+t.maxRecipes()+" / buffered="+t.getAspects().visSize():"functional attachment";
                    gui.drawCenteredString(font,details,px+cw/2,py+187,0xffa7afb8);
                }
            }catch(Throwable failure){fail(mc,failure);}
        }
    }
}
