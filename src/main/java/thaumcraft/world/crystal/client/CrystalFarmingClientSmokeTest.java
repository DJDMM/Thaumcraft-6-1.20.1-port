package thaumcraft.world.crystal.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.alchemy.*;
import thaumcraft.api.aspects.*;
import thaumcraft.arcane.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.client.research.*;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.infusion.*;
import thaumcraft.research.*;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.aura.AuraManager;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Owned, hidden integrated client. Newly finished clusters/batteries are always actually paid. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class CrystalFarmingClientSmokeTest {
    private static final String WORLD="thaumcraft-crystal-farming-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"ore-scans","farmer-paid","infusion-formed","infusion-active",
            "infusion-output","cluster-planted","cluster-grown","battery-paid","battery-recipe",
            "battery-placed","battery-full","battery-released","cluster-models","battery-models"};
    private static final BlockPos MATRIX=new BlockPos(0,114,0), CENTRAL=MATRIX.below(2),
            CLUSTER=new BlockPos(5,112,0), BENCH=new BlockPos(-6,112,0), BATTERY=new BlockPos(-8,112,0);
    private static final String[] PRIMALS={"aer","terra","ignis","aqua","ordo","perditio"};
    private static final AtomicInteger saved=new AtomicInteger();
    private static boolean started,prepared,stopped,captureRequested,captured;
    private static int scene,phase,stable,wait,scanIndex,expectedStage=-1,ready,xpBefore,obsOne,obsTwo,modelStates;
    private static float auraBefore,growthBefore;
    private static long began;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previous;
    private CrystalFarmingClientSmokeTest(){}
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static ServerPlayer player(Minecraft mc){var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(p!=null,"Missing owned player");return p;}
    private static void submit(Minecraft mc,Runnable action){
        require(work==null,"Overlapping crystal server work");var future=new CompletableFuture<Void>();work=future;
        mc.getSingleplayerServer().execute(()->{try{action.run();future.complete(null);}catch(Throwable e){future.completeExceptionally(e);}});
    }
    private static ItemStack item(String id){var b=CatalogBlocks.block(id);require(b!=null&&b!=Blocks.AIR,"Missing block "+id);return new ItemStack(b);}
    private static IntegerProperty property(BlockState state,String name){
        return (IntegerProperty)state.getProperties().stream().filter(p->p.getName().equals(name)).findFirst().orElseThrow();
    }
    private static int value(Minecraft mc,BlockPos pos,String name){var s=mc.level.getBlockState(pos);return s.isAir()?-1:s.getValue(property(s,name));}
    private static int serverValue(Minecraft mc,BlockPos pos,String name){var s=player(mc).serverLevel().getBlockState(pos);return s.getValue(property(s,name));}
    private static InfusionMatrixBlockEntity matrix(Minecraft mc){return (InfusionMatrixBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX);}
    private static InfusionPedestalBlockEntity central(Minecraft mc){return (InfusionPedestalBlockEntity)player(mc).serverLevel().getBlockEntity(CENTRAL);}
    private static ArcaneWorkbenchBlockEntity bench(Minecraft mc){return (ArcaneWorkbenchBlockEntity)player(mc).serverLevel().getBlockEntity(BENCH);}
    private static List<EssentiaJarBlockEntity> jars(Minecraft mc){
        var out=new ArrayList<EssentiaJarBlockEntity>();
        for(int i=0;i<3;i++)out.add((EssentiaJarBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX.offset(i-1,-2,-4)));return out;
    }
    private static void held(ServerPlayer p,ItemStack stack){p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();}
    private static void fixtureStudy(ServerPlayer p,String key){try{
        var m=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);m.setAccessible(true);
        m.invoke(KnowledgeStore.get(p),key,ResearchCatalog.get(key).stages().size()+1);
    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void observations(ServerPlayer p,String category,int amount){try{
        var m=PlayerKnowledge.class.getDeclaredMethod("addKnowledge",KnowledgeType.class,String.class,int.class);m.setAccessible(true);
        m.invoke(KnowledgeStore.get(p),KnowledgeType.OBSERVATION,category,amount);
    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void close(Minecraft mc){mc.player.closeContainer();mc.setScreen(null);}
    private static void click(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),face,pos,false));}
    private static void shift(Minecraft mc,boolean down){
        mc.player.input.shiftKeyDown=down;mc.player.setShiftKeyDown(down);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
                down?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:
                        net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
    }
    private static boolean near(Minecraft mc,BlockPos pos){return mc.player.distanceToSqr(pos.getCenter().add(0,0,2))<5;}
    private static void teleport(ServerPlayer p,BlockPos pos){p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()+2.5,180,70);}
    private static void view(Minecraft mc){if(!(mc.screen instanceof Gallery g)||g.renderedScene!=scene)mc.setScreen(new Gallery());}
    private static void worldView(Minecraft mc){
        if(mc.screen!=null)close(mc);
        mc.player.setYRot(180);mc.player.setXRot(30);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.crystalFarmingSmokeTest")||stopped)return;
        var mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try{
            require(System.nanoTime()-began<900_000_000_000L,"Crystal timeout scene="+scene+" phase="+phase);
            if(!started){start(mc);return;}
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null||mc.screen instanceof ReceivingLevelScreen)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong crystal QA world");
            mc.getToasts().clear();
            if(++wait%100==0)LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_HEARTBEAT: scene={} phase={} size={} charge={} screen={}",scene,phase,
                    mc.level.getBlockState(CLUSTER).isAir()?-1:value(mc,CLUSTER,"size"),
                    mc.level.getBlockState(BATTERY).isAir()?-1:value(mc,BATTERY,"charge"),mc.screen==null?"world":mc.screen.getClass().getSimpleName());
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!prepared){prepared=true;submit(mc,()->prepare(mc));return;}
            if(captured){if(saved.get()==scene+1&&++stable>=20){scene++;phase=stable=wait=ready=0;expectedStage=-1;captured=captureRequested=false;}return;}
            if(scene==IMAGES.length){finish(mc);return;}
            if(!advance(mc))return;
            if(++stable>=12)captureRequested=true;
        }catch(Throwable error){fail(mc,error);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.crystalFarmingSmokeTest")||stopped||!captureRequested||captured)return;
        var mc=Minecraft.getInstance();try{
            captured=true;captureRequested=false;String name="tc6-crystal-farming-"+IMAGES[scene]+".png";
            var file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
                if(file.isFile()&&file.length()>0){saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_SMOKE_IMAGE: {}",file.getAbsolutePath());}
                else mc.execute(()->fail(mc,new AssertionError("Missing current crystal capture")));
            });
        }catch(Throwable error){fail(mc,error);}
    }
    private static boolean advance(Minecraft mc){
        if(scene==0)return scan(mc);
        if(scene==1||scene==7)return study(mc,scene==1?"CRYSTALFARMER":"VISBATTERY",scene==1?"BASICS":"ARTIFICE");
        if(scene==2)return formation(mc);
        if(scene==3)return infusion(mc);
        if(scene==4)return infused(mc);
        if(scene==5)return plant(mc);
        if(scene==6)return grow(mc);
        if(scene==8)return recipe(mc);
        if(scene==9)return placeBattery(mc);
        if(scene==10)return charge(mc);
        if(scene==11)return release(mc);
        view(mc);return true;
    }
    private static boolean scan(Minecraft mc){
        String[] ids={"ore_amber","ore_cinnabar","crystal_aer"},facts={"!OREAMBER","!ORECINNABAR","!ORECRYSTAL"};
        if(scanIndex<ids.length){
            if(phase==0){phase=1;submit(mc,()->{var p=player(mc);held(p,new ItemStack(ScanningModule.THAUMOMETER.get()));
                p.setItemInHand(InteractionHand.OFF_HAND,item(ids[scanIndex]));p.inventoryMenu.broadcastChanges();});return false;}
            if(phase==1){
                if(!mc.player.getMainHandItem().is(ScanningModule.THAUMOMETER.get())||!mc.player.getOffhandItem().is(item(ids[scanIndex]).getItem()))return false;
                if(++ready<15)return false;
                shift(mc,true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=2;return false;
            }
            if(!ResearchClient.golemPressKnowledge().isResearchKnown(facts[scanIndex]))return false;
            shift(mc,false);scanIndex++;phase=ready=0;return false;
        }
        if(phase==0){phase=1;submit(mc,()->{
            var p=player(mc);var k=KnowledgeStore.get(p);
            require(k.isResearchCompleteStrict("ORE")&&Arrays.stream(facts).allMatch(k::isResearchKnown),"Actual scanner failed ore root/addenda");
            held(p,new ItemStack(ResearchModule.THAUMONOMICON.get()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_SCANS: actual shift/offhand Thaumometer C2S,amber/cinnabar/Aer BlockItems -> three ore addenda and completed ORE; physical ore samples explicit fixtures");
        });return false;}
        var k=ResearchClient.golemPressKnowledge();
        if(!(mc.screen instanceof ThaumonomiconPageScreen)){
            var book=new ThaumonomiconScreen(k,k.scanCount());mc.setScreen(new ThaumonomiconPageScreen(book,ResearchCatalog.get("ORE"),k,k.scanCount()));
        }return true;
    }
    private static boolean study(Minecraft mc,String key,String second){
        if(phase==0){phase=1;close(mc);submit(mc,()->{
            var p=player(mc);held(p,new ItemStack(ResearchModule.THAUMONOMICON.get()));
            observations(p,"AUROMANCY",16);observations(p,second,16);
            if(key.equals("CRYSTALFARMER"))for(int i=0;i<6;i++)p.getInventory().setItem(10+i,AspectCrystalItem.create(Aspect.getAspect(PRIMALS[i])));
            var k=KnowledgeStore.get(p);obsOne=k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY");obsTwo=k.rawKnowledge(KnowledgeType.OBSERVATION,second);xpBefore=p.totalExperience;
            p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);
        });return false;}
        if(phase==1){
            if(!mc.player.getMainHandItem().is(ResearchModule.THAUMONOMICON.get()))return false;
            var k=ResearchClient.golemPressKnowledge();
            if(!k.isResearchCompleteStrict(key)){int stage=k.researchStage(key);if(stage==expectedStage)return false;expectedStage=stage;ResearchNetwork.requestAdvance(key,stage);return false;}
            phase=2;submit(mc,()->{
                var p=player(mc);var kServer=KnowledgeStore.get(p);
                require(kServer.isResearchCompleteStrict(key)&&kServer.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==obsOne-16
                        &&kServer.rawKnowledge(KnowledgeType.OBSERVATION,second)==obsTwo-16&&p.totalExperience==xpBefore+10,"Actual study payment/stage/XP mismatch "+key);
                if(key.equals("CRYSTALFARMER"))for(int i=0;i<6;i++)require(p.getInventory().getItem(10+i).isEmpty(),"Original six primal crystal payment omitted");
                LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_RESEARCH_PAID: {} actual book C2S16AuromancyObs+16{}Obs,10XP total; six exact primal crystals consumed only for Farmer; original parents preserved",key,second);
            });return false;
        }
        if(!(mc.screen instanceof ThaumonomiconPageScreen)){
            var k=ResearchClient.golemPressKnowledge();var book=new ThaumonomiconScreen(k,k.scanCount());mc.setScreen(new ThaumonomiconPageScreen(book,ResearchCatalog.get(key),k,k.scanCount()));
        }return true;
    }
    private static boolean formation(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->{held(player(mc),new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));teleport(player(mc),MATRIX.below(2));});return false;}
        if(phase==1){
            if(!mc.player.getMainHandItem().is(AlchemyModule.SALIS_MUNDUS.get())||!near(mc,CENTRAL))return false;
            if(++ready<3)return false;
            phase=2;click(mc,MATRIX,Direction.NORTH);return false;
        }
        if(!InfusionStability.valid(mc.level,MATRIX))return false;
        if(phase==2){phase=3;submit(mc,()->{
            require(player(mc).getMainHandItem().isEmpty(),"Native Salis formation did not pay dust");
            require(!matrix(mc).active(),"Formation also activated matrix");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_FORMATION: actual oneSalis C2S consumed; original pillar recipe formed native altar");
        });return false;}
        view(mc);return true;
    }
    private static boolean infusion(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->held(player(mc),CatalogModule.stack("caster_basic")));return false;}
        var m=(InfusionMatrixBlockEntity)mc.level.getBlockEntity(MATRIX);
        if(phase==1){if(!InfusionMatrixBlock.isCaster(mc.player.getMainHandItem())||++ready<3)return false;phase=2;click(mc,MATRIX,Direction.NORTH);return false;}
        if(phase==2){if(!m.active()||m.startup()<.95)return false;phase=3;click(mc,MATRIX,Direction.NORTH);return false;}
        if(!m.crafting()||m.getAspects().visSize()<=0||m.getAspects().visSize()>=25)return false;
        if(phase==3){phase=4;submit(mc,()->{
            require(matrix(mc).crafting()&&jars(mc).stream().mapToInt(EssentiaJarBlockEntity::amount).sum()<85,"No actual typed infusion debit");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_INFUSION_ACTIVE: actual caster C2S after actual Farmer research, ordinary matrix ticks consume Aer10/Vitreus10/Vinculum5 and two physical reagents");
        });return false;}
        view(mc);return true;
    }
    private static boolean infused(Minecraft mc){
        if(((InfusionMatrixBlockEntity)mc.level.getBlockEntity(MATRIX)).crafting())return false;
        var tile=mc.level.getBlockEntity(CENTRAL);
        if(!(tile instanceof InfusionPedestalBlockEntity p)||!p.getItem(0).is(item("crystal_aer").getItem()))return false;
        if(phase==0){phase=1;submit(mc,()->{
            require(jars(mc).stream().allMatch(j->j.amount()==20),"Crystal recipe did not pay exact25essentia");
            for(int x:new int[]{-3,3})require(((InfusionPedestalBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX.offset(x,-2,0))).isEmpty(),"Crystal reagent not paid");
            require(central(mc).getItem(0).getCount()==1,"Cluster result quantity incorrect");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_INFUSION_PAID: central exactAercrystal+oneWheatSeed+oneSalis,25essentia -> one actualcrystal_aer; alljars20,no supplied finished output");
        });return false;}
        view(mc);return true;
    }
    private static boolean plant(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->{held(player(mc),ItemStack.EMPTY);teleport(player(mc),CENTRAL);});return false;}
        if(phase==1){if(!mc.player.getMainHandItem().isEmpty()||!near(mc,CENTRAL)||++ready<3)return false;phase=2;click(mc,CENTRAL,Direction.NORTH);return false;}
        if(phase==2){
            if(!mc.player.getInventory().contains(item("crystal_aer")))return false;
            phase=3;submit(mc,()->{
                var p=player(mc);int found=-1;
                for(int i=0;i<p.getInventory().items.size();i++)if(p.getInventory().getItem(i).is(item("crystal_aer").getItem())){require(found<0,"Duplicate paid cluster");found=i;}
                require(found>=0&&central(mc).isEmpty(),"Native pedestal output not collected");
                ItemStack output=p.getInventory().getItem(found);p.getInventory().setItem(found,ItemStack.EMPTY);held(p,output);teleport(p,CLUSTER);
            });return false;
        }
        if(phase==3){
            if(!mc.player.getMainHandItem().is(item("crystal_aer").getItem())||!near(mc,CLUSTER)||++ready<6)return false;
            phase=4;click(mc,CLUSTER.below(),Direction.UP);return false;
        }
        if(mc.level.getBlockState(CLUSTER).isAir())return false;
        if(phase==4){phase=5;submit(mc,()->{
            require(player(mc).getMainHandItem().isEmpty()&&serverValue(mc,CLUSTER,"size")==0,"Native planted cluster duplicated or pregrew");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_PLACEMENT: actual paid infusion result picked up from native pedestal, ordinary BlockItem placed onstone; size0");
        });return false;}
        view(mc);return true;
    }
    private static boolean grow(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->{
            var p=player(mc);isolatedAura(p,CLUSTER,500);
            growthBefore=AuraManager.getVis(p.serverLevel(),CLUSTER);
            p.serverLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(512,p.server);
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_GROWTH_FIXTURE: randomTickSpeed512/highvis500/fullmoon/neighboursflux1000 explicit acceleration fixture; actual paid cluster natural random ticks, no direct growth/tick calls");
        });return false;}
        if(value(mc,CLUSTER,"size")!=3)return false;
        if(phase==1){phase=2;submit(mc,()->{
            var p=player(mc);p.serverLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,p.server);
            require(serverValue(mc,CLUSTER,"size")==3&&AuraManager.getVis(p.serverLevel(),CLUSTER)<=growthBefore-30,"Native random growth failed real10vis steps");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_GROWTH: real random ticks size0->3,three10vis payments; source aura={} now={}; spread ifpresent is paid extra10vis",growthBefore,AuraManager.getVis(p.serverLevel(),CLUSTER));
        });return false;}
        var state=mc.level.getBlockState(CLUSTER);var model=mc.getBlockRenderer().getBlockModel(state);
        require(model instanceof CrystalBakedModel,"Paid world cluster did not receive procedural baked model");
        var data=model.getModelData(mc.level,CLUSTER,state,net.minecraftforge.client.model.data.ModelData.EMPTY);
        int supports=thaumcraft.world.crystal.PrimalCrystalClusterBlock.supportMask(mc.level,CLUSTER);
        require(supports!=0 && supports!=63 && data.get(CrystalBakedModel.SUPPORTS)==supports
                && model.getQuads(state,null,RandomSource.create(0),data,null).size()==48*Integer.bitCount(supports),
                "Actual paid size3 world model lost rock supports or original prism faces");
        worldView(mc);return true;
    }
    private static boolean recipe(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->{
            var p=player(mc);held(p,ItemStack.EMPTY);teleport(p,BENCH);
            var level=p.serverLevel();level.setBlockAndUpdate(BENCH,ArcaneModule.WORKBENCH.get().defaultBlockState());bench(mc).clearContent();
            var r=level.getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream().filter(x->x.getResultItem(level.registryAccess()).is(item("vis_battery").getItem())).findFirst().orElseThrow();
            for(int i=0;i<r.getIngredients().size();i++){var in=r.getIngredients().get(i);if(!in.isEmpty())bench(mc).setItem(i,in.getItems()[0].copyWithCount(1));}
            for(int i=0;i<6;i++)if(r.crystalCost(i)>0)bench(mc).setItem(9+i,AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i]),r.crystalCost(i)));
            isolatedAura(p,BENCH,250);auraBefore=AuraManager.getVis(level,BENCH);
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_ARCANE_FIXTURE: actual original physical battery ingredients/crystals supplied,no finishedbattery; aura250 isolates payment");
        });return false;}
        if(phase==1){if(!mc.player.getMainHandItem().isEmpty()||!near(mc,BENCH)||++ready<3)return false;phase=2;click(mc,BENCH,Direction.UP);return false;}
        if(!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu)||!menu.getSlot(0).getItem().is(item("vis_battery").getItem())||!menu.craftable())return false;
        require(menu.requiredVis()==50,"Original battery50vis cost absent");
        for(int i=0;i<6;i++)require(menu.crystalCost(i)==2,"Original battery two-of-each-primal cost absent");
        return true;
    }
    private static boolean placeBattery(Minecraft mc){
        if(phase==0){phase=1;var menu=(ArcaneWorkbenchMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,2,ClickType.SWAP,mc.player);return false;}
        if(phase==1){if(!mc.player.getInventory().getItem(2).is(item("vis_battery").getItem()))return false;phase=2;close(mc);submit(mc,()->{
            require(bench(mc).isEmpty(),"Actual batterybench components/crystals unpaid");
            var p=player(mc);var r=p.serverLevel().getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream().filter(x->x.getResultItem(p.serverLevel().registryAccess()).is(item("vis_battery").getItem())).findFirst().orElseThrow();
            require(AuraManager.getVis(p.serverLevel(),BENCH)==auraBefore-r.vis(),"Battery arcane aura debit incorrect");
            p.getInventory().selected=2;p.inventoryMenu.broadcastChanges();teleport(p,BATTERY);
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_BATTERY_PAID: actual nativebench C2S {}vis,originalcrystals/components,one output",r.vis());
        });return false;}
        mc.player.getInventory().selected=2;
        if(phase==2){
            if(!mc.player.getMainHandItem().is(item("vis_battery").getItem())||!near(mc,BATTERY)||++ready<4)return false;
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(2));
            phase=3;click(mc,BATTERY.below(),Direction.UP);return false;
        }
        if(mc.level.getBlockState(BATTERY).isAir())return false;
        if(phase==3){phase=4;submit(mc,()->{
            require(player(mc).getInventory().getItem(2).isEmpty()&&serverValue(mc,BATTERY,"charge")==0,"Paid battery placement duplicated/invented charge");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_BATTERY_PLACED: actual paid nativeBlockItem placement,no stored energy fixture");
        });return false;}
        view(mc);return true;
    }
    private static boolean charge(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->{
            var p=player(mc);var l=p.serverLevel();
            // The already paid crystal completed its own QA. Remove loaded descendants
            // without drops in this isolated arena before the independent conservation check.
            for(BlockPos pos:BlockPos.betweenClosed(-12,110,-9,12,119,9))if(CatalogBlocks.block("crystal_aer")==l.getBlockState(pos).getBlock())l.removeBlock(pos,false);
            isolatedAura(p,BATTERY,500);auraBefore=AuraManager.getVis(l,BATTERY);
            l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(512,p.server);
        });return false;}
        if(value(mc,BATTERY,"charge")!=10)return false;
        if(phase==1){phase=2;submit(mc,()->{
            var p=player(mc);p.serverLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,p.server);
            require(serverValue(mc,BATTERY,"charge")==10&&AuraManager.getVis(p.serverLevel(),BATTERY)==auraBefore-10,"Battery native charge did not conserve ten vis");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_BATTERY_FULL: ordinary random/scheduledticks,charge0->10,exact10vis debit; capacity/comparator/light10");
        });return false;}
        worldView(mc);return true;
    }
    private static boolean release(Minecraft mc){
        if(phase==0){phase=1;close(mc);submit(mc,()->{
            var p=player(mc);auraBefore=AuraManager.getVis(p.serverLevel(),BATTERY);
            p.serverLevel().setBlockAndUpdate(BATTERY.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        });return false;}
        if(value(mc,BATTERY,"charge")!=0)return false;
        if(phase==1){phase=2;submit(mc,()->{
            var p=player(mc);require(serverValue(mc,BATTERY,"charge")==0&&AuraManager.getVis(p.serverLevel(),BATTERY)==auraBefore+10,"Powered five-tick release failed exact conservation");
            LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_BATTERY_RELEASE: actual physicalredstone native neighbor/scheduledticks release10vis,charge10->0,exactrefund; no directbattery tick/API calls");
        });return false;}
        view(mc);return true;
    }
    private static void isolatedAura(ServerPlayer p,BlockPos center,float amount){
        var l=p.serverLevel();l.setDayTime(6000);var chunk=new ChunkPos(center);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var anchor=new ChunkPos(chunk.x+dx,chunk.z+dz).getMiddleBlockPosition(112);
            AuraManager.drainVis(l,anchor,Float.MAX_VALUE,false);AuraManager.addVis(l,anchor,amount);
            AuraManager.drainFlux(l,anchor,Float.MAX_VALUE,false);AuraManager.addFlux(l,anchor,1000);
        }
    }
    private static void prepare(Minecraft mc){
        var p=player(mc);var l=p.serverLevel();p.setInvulnerable(true);
        for(BlockPos pos:BlockPos.betweenClosed(-9,111,-6,9,119,6))l.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.getChunk(x,z);
        l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,p.server);
        l.setBlockAndUpdate(MATRIX,CatalogBlocks.block("infusion_matrix").defaultBlockState());
        l.setBlockAndUpdate(CENTRAL,CatalogBlocks.block("pedestal_arcane").defaultBlockState());
        central(mc).setItem(0,AspectCrystalItem.create(Aspect.AIR));
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})for(int y:new int[]{-1,-2})l.setBlockAndUpdate(MATRIX.offset(x,y,z),CatalogBlocks.block("stone_arcane").defaultBlockState());
        for(int x:new int[]{-3,3})l.setBlockAndUpdate(MATRIX.offset(x,-2,0),CatalogBlocks.block("pedestal_arcane").defaultBlockState());
        ((InfusionPedestalBlockEntity)l.getBlockEntity(MATRIX.offset(3,-2,0))).setItem(0,new ItemStack(Items.WHEAT_SEEDS));
        ((InfusionPedestalBlockEntity)l.getBlockEntity(MATRIX.offset(-3,-2,0))).setItem(0,new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));
        Aspect[] aspects={Aspect.AIR,Aspect.CRYSTAL,Aspect.TRAP};
        for(int i=0;i<3;i++){var pos=MATRIX.offset(i-1,-2,-4);l.setBlockAndUpdate(pos,CatalogBlocks.block("jar_normal").defaultBlockState());
            require(((EssentiaJarBlockEntity)l.getBlockEntity(pos)).addExact(aspects[i],i==2?25:30),"Typed infusion source fixture failed");}
        p.getInventory().clearContent();for(String key:List.of("INFUSION","RECHARGEPEDESTAL"))fixtureStudy(p,key);
        teleport(p,CENTRAL);ResearchNetwork.sync(p);
        LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_FIXTURE: ownedisolatedworld,predecessorINFUSION/RECHARGEPEDESTAL,ore/primal/rawknowledge/ingredients/sourceessentia/arena/aura explicitfixtures; ORE/Farmer/Battery research andfinished outputs absent; nofullsurvival/multiplayer claim");
    }
    private static void start(Minecraft mc){
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        started=true;previous=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(0x54433626L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static void auditModels(Minecraft mc){
        var batteryAudit=BatteryBakedModel.verifyBake(mc);
        LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_BATTERY_LIGHTING: original min180,{} states,{} quad checks,{} actual Forge lighting merges; original geometry/inventory/higher light/sky preserved",
                batteryAudit.states(),batteryAudit.quadChecks(),batteryAudit.lightingChecks());
        for(String id:List.of("crystal_aer","crystal_ignis","crystal_aqua","crystal_terra","crystal_ordo","crystal_perditio","crystal_vitium","vis_battery")){
            var b=CatalogBlocks.block(id);
            for(var s:b.getStateDefinition().getPossibleStates()){
                var model=mc.getBlockRenderer().getBlockModel(s);require(model!=mc.getModelManager().getMissingModel(),"Missing crystal/batterymodel "+s);
                var dirs=new ArrayList<>(List.of(Direction.values()));dirs.add(null);
                for(var d:dirs)for(var q:model.getQuads(s,d,RandomSource.create(0)))require(!q.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),"Missing originalsprite "+s);
                modelStates++;
            }
        }
    }
    private static void finish(Minecraft mc){
        auditModels(mc);require(saved.get()==IMAGES.length,"Missing14currentcrystal scenes");
        stopped=true;mc.options.tutorialStep=previous;
        LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_RENDER_AUDIT_OK: {}current captures,{}bakedstates; actualbook/bench/altar/paidcluster0and3/paidbattery0and10,originalprimal+fluxandcharge gallery are explicitlyreadonly previews",saved.get(),modelStates);
        LogUtils.getLogger().info("THAUMCRAFT_CRYSTAL_FARMING_CLIENT_SMOKE_OK: 14 scenes; three actual ore scans, paid Farmer/Battery studies, actual25essentia Aer infusion/native placement/random growth, paid battery bench/native placement, ordinary charge10/release10; explicit fixtures, no direct runtime tick calls; world={}",WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable error){if(stopped)return;stopped=true;if(previous!=null)mc.options.tutorialStep=previous;LogUtils.getLogger().error("THAUMCRAFT_CRYSTAL_FARMING_CLIENT_SMOKE_FAILED",error);mc.stop();}
    private static final class Gallery extends Screen{
        final int renderedScene;
        Gallery(){super(Component.literal("Thaumcraft 6 / "+IMAGES[scene]));renderedScene=scene;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int x,int y,float partial){
            try{
                var mc=Minecraft.getInstance();g.fill(0,0,width,height,0xff18202b);g.drawCenteredString(font,title,width/2,15,0xffefdbac);g.flush();Lighting.setupFor3DItems();
                if(renderedScene==12){
                    String[] ids={"crystal_aer","crystal_ignis","crystal_aqua","crystal_terra","crystal_ordo","crystal_perditio","crystal_vitium"};
                    for(int a=0;a<7;a++)for(int size=0;size<4;size++){
                        var state=CatalogBlocks.block(ids[a]).defaultBlockState();state=state.setValue(property(state,"size"),size);
                        renderState(g,state,width/2+(a-3)*100,95+size*95,38);
                    }
                    g.drawCenteredString(font,"Aer / Ignis / Aqua / Terra / Ordo / Perditio / Vitium",width/2,height-48,0xffd4d9df);
                    g.drawCenteredString(font,"Original growth levels 0 / 1 / 2 / 3; read-only model previews",width/2,height-31,0xffd4d9df);
                }else if(renderedScene==13){
                    var state=CatalogBlocks.block("vis_battery").defaultBlockState();
                    for(int charge=0;charge<=10;charge++){
                        renderState(g,state.setValue(property(state,"charge"),charge),width/2+(charge%6-2.5)*115,140+(charge/6)*160,56);
                        g.drawCenteredString(font,Integer.toString(charge),(int)(width/2+(charge%6-2.5)*115),180+(charge/6)*160,0xffffff);
                    }
                    g.drawCenteredString(font,"Original charge0..10; read-only previews, real payments in previous scenes",width/2,height-31,0xffd4d9df);
                }else{
                    g.pose().pushPose();g.pose().translate(width/2,height/2+65,200);g.pose().scale(37,-37,37);
                    g.pose().mulPose(Axis.XP.rotationDegrees(24));g.pose().mulPose(Axis.YP.rotationDegrees(145));
                    if(renderedScene>=9){block(g,BATTERY,BATTERY);}else if(renderedScene==5||renderedScene==6){block(g,CLUSTER,CLUSTER);}
                    else for(BlockPos pos:List.of(MATRIX,CENTRAL,MATRIX.offset(-3,-2,0),MATRIX.offset(3,-2,0),MATRIX.offset(-1,-2,-4),MATRIX.offset(0,-2,-4),MATRIX.offset(1,-2,-4)))block(g,pos,MATRIX.below(2));
                    g.flush();g.pose().popPose();
                    if(renderedScene>=9)g.drawCenteredString(font,"Live battery charge: "+value(mc,BATTERY,"charge"),width/2,height-48,0xffffff);
                    else if(renderedScene==5||renderedScene==6)g.drawCenteredString(font,"Live paid cluster size: "+value(mc,CLUSTER,"size"),width/2,height-48,0xffffff);
                    else g.drawCenteredString(font,"Actual paid research / original physical ingredients / ordinary native infusion",width/2,height-48,0xffd4d9df);
                }g.flush();
            }catch(Throwable error){fail(Minecraft.getInstance(),error);}
        }
        private void renderState(GuiGraphics g,BlockState s,double x,double y,float scale){
            g.pose().pushPose();g.pose().translate(x,y,200);g.pose().scale(scale,-scale,scale);
            g.pose().mulPose(Axis.XP.rotationDegrees(24));g.pose().mulPose(Axis.YP.rotationDegrees(145));g.pose().translate(-.5,0,-.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(s,g.pose(),g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);g.flush();g.pose().popPose();
        }
        private void block(GuiGraphics g,BlockPos pos,BlockPos origin){
            var mc=Minecraft.getInstance();var s=mc.level.getBlockState(pos);if(s.isAir())return;
            g.pose().pushPose();g.pose().translate(pos.getX()-origin.getX(),pos.getY()-origin.getY(),pos.getZ()-origin.getZ());
            mc.getBlockRenderer().renderSingleBlock(s,g.pose(),g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);
            var tile=mc.level.getBlockEntity(pos);if(tile!=null&&mc.getBlockEntityRenderDispatcher().getRenderer(tile)!=null)
                require(!mc.getBlockEntityRenderDispatcher().renderItem(tile,g.pose(),g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Missing realaltar/jarrenderer");
            g.flush();g.pose().popPose();
        }
    }
}
