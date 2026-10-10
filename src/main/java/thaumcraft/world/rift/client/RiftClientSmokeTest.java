package thaumcraft.world.rift.client;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.client.research.ResearchClient;
import thaumcraft.client.research.ThaumonomiconPageScreen;
import thaumcraft.client.research.ThaumonomiconScreen;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.infusion.InfusionMatrixBlock;
import thaumcraft.infusion.InfusionMatrixBlockEntity;
import thaumcraft.infusion.InfusionPedestalBlockEntity;
import thaumcraft.infusion.InfusionRecipes;
import thaumcraft.infusion.InfusionStabilizerBlockEntity;
import thaumcraft.infusion.InfusionStability;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchModule;
import thaumcraft.research.ResearchNetwork;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ThaumometerItem;
import thaumcraft.scanning.client.ThaumometerClient;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.rift.FluxRiftEntity;
import thaumcraft.world.rift.RiftGeometry;

/** Owned hidden client: native generation, paid research/infusion, real throw and reward pickup. */
@Mod.EventBusSubscriber(modid="thaumcraft", value=Dist.CLIENT)
public final class RiftClientSmokeTest {
    private static final String WORLD="thaumcraft-rift-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"natural-unknown","scanned-rift","flux-rift-study","closer-paid",
            "infusion-active","infusion-output","physical-stabilizer","collapsing","rewards-picked-up"};
    private static final BlockPos SOURCE=new BlockPos(8,160,8), MATRIX=new BlockPos(48,164,8), CENTRAL=MATRIX.below(2);
    private static final int[][] REAGENT_OFFSETS={{-3,0},{3,0},{0,-3},{0,3},{-3,-3},{3,3},{-3,3},{3,-3}};
    private static final AtomicInteger SAVED=new AtomicInteger();
    private static boolean started,prepared,stopped,captured,captureRequested;
    private static volatile boolean generationReady;
    private static volatile int riftId=-1, initialSize;
    private static volatile MinecraftServer ownedServer;
    private static volatile float beforeGeneration, afterGeneration;
    private static int scene,phase,stable,wait,expectedStage=-1,obsBefore,alchemyBefore,auromancyBefore,xpBefore;
    private static int stabilizerAge, stabilizerEnergy, rewardCount;
    private static long began;
    private static volatile BlockPos stabilizerPos;
    private static volatile Vec3 rewardCenter;
    private static volatile String matrixDiagnostic="not yet observed";
    private static CompoundTag hoverState;
    private static float stabilizerFlux;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previous;

    private RiftClientSmokeTest() {}
    private static void require(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
    private static ServerPlayer player(Minecraft mc) {
        var player=ownedServer.getPlayerList().getPlayer(mc.player.getUUID());
        require(player!=null,"Missing owned rift QA player");return player;
    }
    private static FluxRiftEntity rift(ServerLevel level) {
        require(level.getEntity(riftId) instanceof FluxRiftEntity,"Natural rift missing before collapse");
        return (FluxRiftEntity)level.getEntity(riftId);
    }
    private static void submit(Minecraft mc,Runnable action) {
        require(work==null,"Overlapping Rift server work");var result=new CompletableFuture<Void>();work=result;
        ownedServer.execute(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});
    }
    private static void held(ServerPlayer player,ItemStack stack) {
        player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.inventoryMenu.broadcastChanges();
    }
    private static void fixtureKnowledge(ServerPlayer player,String key) {
        try { var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);
            method.invoke(KnowledgeStore.get(player),key,ResearchCatalog.get(key).stages().size()+1);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void fixtureRaw(ServerPlayer player,KnowledgeType type,String category,int amount) {
        try { var method=PlayerKnowledge.class.getDeclaredMethod("addKnowledge",KnowledgeType.class,String.class,int.class);method.setAccessible(true);
            method.invoke(KnowledgeStore.get(player),type,category,amount);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static float nearbyFlux(ServerLevel level) {
        // Loaded-only total prevents ordinary diffusion between neighbouring test
        // chunks from being mistaken for another device's payment or pollution.
        float amount=0;
        for(int x=-6;x<=8;x++)for(int z=-6;z<=6;z++)if(level.getChunkSource().getChunkNow(x,z)!=null)
            amount+=AuraManager.getFlux(level,new ChunkPos(x,z).getMiddleBlockPosition(160));
        return amount;
    }
    private static boolean own(TickEvent.LevelTickEvent event) {
        return Boolean.getBoolean("thaumcraft.riftSmokeTest") && !stopped && generationReady
                && event.phase==TickEvent.Phase.END && event.level instanceof ServerLevel level
                && level.getServer()==ownedServer && level.dimension()==net.minecraft.world.level.Level.OVERWORLD
                && level.getGameTime()%20==0 && riftId<0;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeNaturalGeneration(TickEvent.LevelTickEvent event) {
        if(own(event))beforeGeneration=nearbyFlux((ServerLevel)event.level);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void afterNaturalGeneration(TickEvent.LevelTickEvent event) {
        if(!own(event))return;
        ServerLevel level=(ServerLevel)event.level;
        var rifts=level.getEntitiesOfClass(FluxRiftEntity.class,new AABB(0,150,0,16,180,16));
        if(rifts.isEmpty())return;
        try {
            require(rifts.size()==1,"Multiple natural rifts for one chunk fixture");
            var rift=rifts.get(0);afterGeneration=nearbyFlux(level);
            double price=Math.sqrt(5000F*3F); // Original diffusion transfers exactly one of the initial5001 Flux.
            require(Math.abs(beforeGeneration-5001)<.001 && Math.abs(afterGeneration-(beforeGeneration-(float)price))<.002,
                    "Native natural spawn did not pay exact fractional sqrt after conserved one-unit diffusion");
            require(rift.getRiftSize()==(int)price && !rift.getCollapse(),"Natural size was supplied/clamped or already collapsing");
            initialSize=rift.getRiftSize();rewardCenter=rift.position();riftId=rift.getId();
            // Explicit post-spawn fixture protects the long paid research/infusion audit from unrelated weighted events.
            rift.setRiftStability(100);
            for(Direction direction:Direction.Plane.HORIZONTAL)AuraManager.drainFlux(level,SOURCE.relative(direction,16),Float.MAX_VALUE,false);
            AuraManager.drainFlux(level,SOURCE,Float.MAX_VALUE,false);AuraManager.addFlux(level,SOURCE,30);
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_NATURAL_PAYMENT_OK: native aura END tick,initialFlux5001,one-unit diffusion,integerSize={},floatPrice={},totalFlux{}->{}; post-spawn stability100/flux30 are explicit containment fixtures",initialSize,(float)price,beforeGeneration,afterGeneration);
        } catch(Throwable error) {
            ownedServer.execute(()->Minecraft.getInstance().execute(()->fail(Minecraft.getInstance(),error)));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void matrixDiagnostics(TickEvent.LevelTickEvent event) {
        if(!Boolean.getBoolean("thaumcraft.riftSmokeTest") || stopped || !generationReady
                || event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getServer()!=ownedServer || level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                || level.getGameTime()%100!=0 || !level.hasChunkAt(MATRIX))return;
        if(level.getBlockEntity(MATRIX) instanceof InfusionMatrixBlockEntity tile) {
            var tag=tile.saveWithoutMetadata();
            matrixDiagnostic="active="+tile.active()+", crafting="+tile.crafting()+", needed="+tile.getAspects().visSize()
                    +", stability="+tile.stability()+", owner="+(tag.hasUUID("owner")?tag.getUUID("owner"):"none")
                    +", jars="+(jar(level,0).amount()+jar(level,1).amount());
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.riftSmokeTest") || stopped)return;
        Minecraft mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try {
            require(System.nanoTime()-began<900_000_000_000L,"Rift client timeout scene="+scene+" phase="+phase);
            if(!started){start(mc);return;}
            if(mc.level==null || mc.player==null || mc.getOverlay()!=null || mc.screen instanceof ReceivingLevelScreen)return;
            require(mc.getSingleplayerServer()!=null && WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong owned Rift QA world");
            if(ownedServer==null)ownedServer=mc.getSingleplayerServer();mc.getToasts().clear();
            if(++wait%100==0)LogUtils.getLogger().info("THAUMCRAFT_RIFT_HEARTBEAT: scene={}, phase={}, rift={}, screen={}, matrix [{}]",scene,phase,riftId,mc.screen==null?"world":mc.screen.getClass().getSimpleName(),matrixDiagnostic);
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!prepared){prepared=true;submit(mc,()->prepare(mc));return;}
            if(captured){if(SAVED.get()==scene+1 && ++stable>=12){scene++;phase=stable=wait=0;expectedStage=-1;captured=captureRequested=false;}return;}
            if(scene==IMAGES.length){finish(mc);return;}
            if(!advance(mc))return;
            if(++stable>=12)captureRequested=true;
        } catch(Throwable error) { fail(mc,error); }
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.riftSmokeTest") || stopped || !captureRequested || captured)return;
        var mc=Minecraft.getInstance();try {
            captured=true;captureRequested=false;String name="tc6-rift-"+IMAGES[scene]+".png";
            File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
                if(file.isFile() && file.length()>0){SAVED.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_RIFT_SMOKE_IMAGE: {}",file.getAbsolutePath());}
                else mc.execute(()->fail(mc,new AssertionError("Missing current Rift capture "+name)));
            });
        }catch(Throwable error){fail(mc,error);}
    }
    private static boolean advance(Minecraft mc) {
        return switch(scene) {
            case 0 -> natural(mc);
            case 1 -> scan(mc);
            case 2 -> study(mc,"FLUX",false) && studyAfterFlux(mc);
            case 3 -> study(mc,"RIFTCLOSER",true);
            case 4 -> infusion(mc);
            case 5 -> output(mc);
            case 6 -> stabilize(mc);
            case 7 -> collapse(mc);
            case 8 -> rewards(mc);
            default -> false;
        };
    }
    private static void close(Minecraft mc) { if(mc.screen!=null){mc.player.closeContainer();mc.setScreen(null);} }
    private static Vec3 scanAim(FluxRiftEntity rift) {
        var box=rift.getBoundingBox();return new Vec3((box.minX+box.maxX)/2,Math.min(box.maxY-.05,Math.max(161.6,(box.minY+box.maxY)/2)),(box.minZ+box.maxZ)/2);
    }
    private static void teleportToRift(ServerPlayer player) {
        Vec3 aim=scanAim(rift(player.serverLevel()));Vec3 feet=aim.add(0,2,-6);
        double dy=aim.y-(feet.y+player.getEyeHeight());float pitch=(float)-Math.toDegrees(Math.atan2(dy,6));
        player.connection.teleport(feet.x,feet.y,feet.z,0,pitch);
    }
    private static void worldView(Minecraft mc) { close(mc); }
    private static boolean aimedAtRift() {
        var snapshot=ThaumometerClient.currentSnapshot();return snapshot!=null && snapshot.target()!=null
                && snapshot.target().location().kind()==ThaumometerItem.TargetKind.ENTITY && snapshot.target().location().entityId()==riftId;
    }
    private static boolean natural(Minecraft mc) {
        if(riftId<0 || !(mc.level.getEntity(riftId) instanceof FluxRiftEntity))return false;
        if(phase==0){phase=1;worldView(mc);submit(mc,()->teleportToRift(player(mc)));return false;}
        var client=ResearchClient.golemPressKnowledge();if(!aimedAtRift() || client.researchStage("FLUX")!=1)return false;
        if(phase==1){phase=2;submit(mc,()->{
            var p=player(mc);var knowledge=KnowledgeStore.get(p);
            require(knowledge.isResearchKnown("f_toomuchflux") && !knowledge.isResearchKnown("!FluxRift"),"Spawn invented scan fact or failed actual nearby Flux event");
            require(knowledge.researchStage("FLUX")==1 && p.totalExperience==5,"Native scanner discovery did not pay exactly5XP once");
            require(knowledge.scanCount()==0,"Natural generation/hover paid a generic scan");hoverState=knowledge.save();
        });return false;}
        if(phase==2 && stable>=10){phase=3;submit(mc,()->{
            require(hoverState.equals(KnowledgeStore.get(player(mc)).save()),"Read-only natural-rift hover wrote knowledge");
            var actual=rift(player(mc).serverLevel());var path=RiftGeometry.path(actual.getRiftSeed(),actual.getRiftSize());
            require(actual.points.equals(path.points()) && actual.pointsWidth.equals(path.widths()),"Server physics spine disagrees with shared renderer path");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_HOVER_OK: actual nearbyf_toomuchflux/nativeFLUX@1/+5XP; no!FluxRift or object scan/Observation from hover; seed{} size{} shared geometry",actual.getRiftSeed(),actual.getRiftSize());
        });return false;}
        return true;
    }
    private static boolean scan(Minecraft mc) {
        worldView(mc);
        if(phase==0){if(!aimedAtRift())return false;phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return false;}
        if(!ResearchClient.golemPressKnowledge().isResearchKnown("!FluxRift") || !aimedAtRift())return false;
        if(phase==1){phase=2;submit(mc,()->{
            var p=player(mc);require(KnowledgeStore.get(p).isResearchKnown("!FluxRift") && KnowledgeStore.get(p).isResearchKnown("f_toomuchflux"),"Actual live entity scan failed both original facts");
            require(p.totalExperience==5,"Rift scan repaid original native Flux discovery XP");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_ACTUAL_SCAN_OK: ordinary client air-use C2S/server reconstructed live target; both originalfacts,FLUX stage1,XP5");
        });return false;}return true;
    }
    private static boolean study(Minecraft mc,String key,boolean paid) {
        if(phase==0){phase=1;close(mc);submit(mc,()->{
            var p=player(mc);held(p,new ItemStack(ResearchModule.THAUMONOMICON.get()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            if(paid){fixtureRaw(p,KnowledgeType.OBSERVATION,"AUROMANCY",16);fixtureRaw(p,KnowledgeType.THEORY,"ALCHEMY",64);fixtureRaw(p,KnowledgeType.THEORY,"AUROMANCY",32);}
            var k=KnowledgeStore.get(p);obsBefore=k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY");alchemyBefore=k.rawKnowledge(KnowledgeType.THEORY,"ALCHEMY");auromancyBefore=k.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY");xpBefore=p.totalExperience;
            p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);
        });return false;}
        var k=ResearchClient.golemPressKnowledge();
        if(phase==1){
            if(!mc.player.getMainHandItem().is(ResearchModule.THAUMONOMICON.get()))return false;
            if(!k.isResearchCompleteStrict(key)){int current=k.researchStage(key);if(current==expectedStage)return false;expectedStage=current;ResearchNetwork.requestAdvance(key,current);return false;}
            phase=2;submit(mc,()->{
                var p=player(mc);var server=KnowledgeStore.get(p);require(server.isResearchCompleteStrict(key),"Server study incomplete "+key);
                require(server.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==obsBefore-(paid?16:0)
                        && server.rawKnowledge(KnowledgeType.THEORY,"ALCHEMY")==alchemyBefore-(paid?64:0)
                        && server.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==auromancyBefore-(paid?32:0),"Original study cost mismatch "+key);
                require(p.totalExperience==xpBefore+(paid?10:5),"Original research XP mismatch "+key);
                LogUtils.getLogger().info("THAUMCRAFT_RIFT_RESEARCH_PAID: {} realbookC2S,rawObservation{},AlchemyTheory{},AuromancyTheory{},XP{}",key,paid?16:0,paid?64:0,paid?32:0,paid?10:5);
            });return false;
        }
        if(scene==3 && !(mc.screen instanceof ThaumonomiconPageScreen))book(mc,key);
        return true;
    }
    private static boolean studyAfterFlux(Minecraft mc) {
        // Phase3..5 uses the same real protocol for the one-empty-stage FLUXRIFT study.
        if(phase==2){phase=3;expectedStage=-1;return false;}
        var k=ResearchClient.golemPressKnowledge();
        if(phase==3){
            if(!k.isResearchCompleteStrict("FLUXRIFT")){int current=k.researchStage("FLUXRIFT");if(current==expectedStage)return false;expectedStage=current;ResearchNetwork.requestAdvance("FLUXRIFT",current);return false;}
            phase=4;submit(mc,()->{var p=player(mc);require(KnowledgeStore.get(p).isResearchCompleteStrict("FLUXRIFT") && p.totalExperience==xpBefore+10,"Original empty FLUXRIFT completion or XP mismatch");
                LogUtils.getLogger().info("THAUMCRAFT_RIFT_FLUXRIFT_COMPLETE: FLUXfinalchapter+emptyFLUXRIFT,actual!FluxRift,5+5XP; no supplied target-study stages");});return false;
        }
        if(!(mc.screen instanceof ThaumonomiconPageScreen))book(mc,"FLUXRIFT");return true;
    }
    private static void book(Minecraft mc,String key) {
        var k=ResearchClient.golemPressKnowledge();var browser=new ThaumonomiconScreen(k,k.scanCount());mc.setScreen(new ThaumonomiconPageScreen(browser,ResearchCatalog.get(key),k,k.scanCount()));
    }
    private static InfusionMatrixBlockEntity matrix(ServerLevel level) { return (InfusionMatrixBlockEntity)level.getBlockEntity(MATRIX); }
    private static InfusionPedestalBlockEntity central(ServerLevel level) { return (InfusionPedestalBlockEntity)level.getBlockEntity(CENTRAL); }
    private static EssentiaJarBlockEntity jar(ServerLevel level,int index) { return (EssentiaJarBlockEntity)level.getBlockEntity(MATRIX.offset(index*2-1,-2,-5)); }
    private static void click(Minecraft mc,BlockPos pos) { mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.NORTH,pos,false)); }
    private static boolean infusion(Minecraft mc) {
        require(wait<2400,"Native infusion did not begin debit within 120 seconds; phase="+phase+", "+matrixDiagnostic);
        if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);held(p,CatalogModule.stack("caster_basic"));p.connection.teleport(MATRIX.getX()+.5,MATRIX.getY()-2,MATRIX.getZ()+4.5,180,-12);});return false;}
        var tile=mc.level.getBlockEntity(MATRIX);if(!(tile instanceof InfusionMatrixBlockEntity matrix))return false;
        if(phase==1){if(!InfusionMatrixBlock.isCaster(mc.player.getMainHandItem()) || mc.player.distanceToSqr(MATRIX.getCenter())>40 || ++stable<8)return false;phase=2;stable=0;click(mc,MATRIX);return false;}
        if(phase==2){if(!matrix.active() || matrix.startup()<.95F || matrix.stability()<12.5)return false;phase=3;submit(mc,()->{
            var p=player(mc);var level=p.serverLevel();var input=central(level).getItem(0);
            var actual=InfusionStability.scan(level,MATRIX).pedestals().stream()
                    .map(pos->(InfusionPedestalBlockEntity)level.getBlockEntity(pos)).filter(tileValue->!tileValue.isEmpty())
                    .map(tileValue->tileValue.getItem(0).copy()).toList();
            require(input.is(Items.TNT) && input.getCount()==1 && actual.size()==8
                    && actual.stream().allMatch(stack->stack.getCount()==1),"Incorrect physical collapser inputs before C2S start: "+input+" / "+actual);
            var selected=InfusionRecipes.find(p,input,actual);
            require(selected.isPresent(),"No real infusion recipe for paid RIFTCLOSER and physical inputs; bounded fail before second C2S click: "+actual);
            var plan=selected.orElseThrow();
            require(plan.id().toString().equals("thaumcraft:infusion/causalitycollapser")
                    && plan.output().is(CatalogModule.stack("causality_collapser").getItem()) && plan.output().getCount()==1
                    && plan.aspects().getAmount(Aspect.ELDRITCH)==50 && plan.aspects().getAmount(Aspect.FLUX)==50
                    && plan.aspects().visSize()==100,"Unexpected actual infusion plan "+plan.id());
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_INFUSION_PLAN_READY: {} selected by real server recipe matcher from TNT and 8 physical inputs, Alienis50/Vitium50; preview has not paid or started crafting",plan.id());
        });return false;}
        if(phase==3){phase=4;click(mc,MATRIX);return false;}
        if(!matrix.crafting() || matrix.getAspects().visSize()<=0 || matrix.getAspects().visSize()>=100)return false;
        if(phase==4){phase=5;submit(mc,()->{var level=player(mc).serverLevel();require(matrix(level).crafting() && jar(level,0).amount()+jar(level,1).amount()<140,"No native infusion source debit");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_INFUSION_ACTIVE: actualcasteractivation/start,ordinarynativecycles,Alienis50+Vitium50,TNTand8pedestalcomponents; recipe output absent beforefinish");});return false;}
        return true;
    }
    private static boolean output(Minecraft mc) {
        worldView(mc);var tile=mc.level.getBlockEntity(CENTRAL);if(!(tile instanceof InfusionPedestalBlockEntity output))return false;
        if(!output.getItem(0).is(CatalogModule.stack("causality_collapser").getItem()))return false;
        if(phase==0){phase=1;submit(mc,()->{var p=player(mc);var level=p.serverLevel();p.connection.teleport(MATRIX.getX()+.5,MATRIX.getY()-2,MATRIX.getZ()+4.5,180,12);require(!matrix(level).crafting() && central(level).getItem(0).getCount()==1,"Duplicate/not-finished native collapser output");
            require(jar(level,0).amount()==20 && jar(level,1).amount()==20,"Infusion did not pay exact100typedessentia");
            for(var offset:REAGENT_OFFSETS)require(((InfusionPedestalBlockEntity)level.getBlockEntity(MATRIX.offset(offset[0],-2,offset[1]))).isEmpty(),"Unpaid original collapser component");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_INFUSION_PAID: nativeTNTcentral+8components+100typedessentia->onecollapser; bothjars70->20; no supplied finishedcollapser");
        });return false;}return true;
    }
    private static boolean stabilize(Minecraft mc) {
        if(phase==0){phase=1;worldView(mc);submit(mc,()->{
            var p=player(mc);var level=p.serverLevel();var rift=rift(level);
            stabilizerPos=new BlockPos((int)Math.ceil(rift.getBoundingBox().maxX)+3,162,(int)Math.floor(rift.getZ()));
            level.setBlockAndUpdate(stabilizerPos,CatalogBlocks.block("stabilizer").defaultBlockState());
            var stabilizer=(InfusionStabilizerBlockEntity)level.getBlockEntity(stabilizerPos);CompoundTag tag=stabilizer.saveWithoutMetadata();tag.putInt("energy",8);tag.putInt("ticks",0);stabilizer.load(tag);
            level.sendBlockUpdated(stabilizerPos,stabilizer.getBlockState(),stabilizer.getBlockState(),3);
            rift.setRiftStability(0);stabilizerAge=rift.tickCount;stabilizerEnergy=8;stabilizerFlux=nearbyFlux(level);
            held(p,new ItemStack(ScanningModule.THAUMOMETER.get()));teleportToRift(p);
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_STABILIZER_FIXTURE: actualphysicalblock,energy8/ticks0/riftstability0 explicit initialfixture; no directticker/treatment call");
        });return false;}
        if(!(mc.level.getBlockEntity(stabilizerPos) instanceof InfusionStabilizerBlockEntity))return false;
        if(phase==1 && ++stable>=65){phase=2;stable=0;submit(mc,()->{
            var p=player(mc);var level=p.serverLevel();var rift=rift(level);var tile=(InfusionStabilizerBlockEntity)level.getBlockEntity(stabilizerPos);
            int ticks=tile.saveWithoutMetadata().getInt("ticks"),charged=ticks/20,spent=stabilizerEnergy+charged-tile.energy();
            int drift=rift.tickCount/120-stabilizerAge/120;
            require(spent>0 && Math.abs(rift.getRiftStability()-(spent*.125F-drift*.2F))<.001,"Real stabilizer paid stability/conservation mismatch");
            require(Math.abs(nearbyFlux(level)-stabilizerFlux-charged*.25F)<.01,"Native stabilizer selfcharge pollution mismatch");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_STABILIZER_PAID: {}ordinaryticks,{}charged/{}spent/{}energy;+.125each-minus{}.2drift;flux+.25eachcharge",ticks,charged,spent,tile.energy(),drift);
        });return false;}
        return phase>=2;
    }
    private static boolean collapse(Minecraft mc) {
        if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);var level=p.serverLevel();level.removeBlock(stabilizerPos,false);rift(level).setRiftStability(100);
            held(p,ItemStack.EMPTY);p.connection.teleport(CENTRAL.getX()+.5,CENTRAL.getY(),CENTRAL.getZ()+2.5,180,15);});return false;}
        if(phase==1){if(!mc.player.getMainHandItem().isEmpty() || mc.player.distanceToSqr(CENTRAL.getCenter())>16 || ++stable<8)return false;phase=2;stable=0;click(mc,CENTRAL);return false;}
        if(phase==2){if(!mc.player.getInventory().contains(CatalogModule.stack("causality_collapser")))return false;phase=3;submit(mc,()->{
            var p=player(mc);require(central(p.serverLevel()).isEmpty(),"Native pedestal output collection failed");
            int found=-1;for(int slot=0;slot<p.getInventory().items.size();slot++)if(p.getInventory().getItem(slot).is(CatalogModule.stack("causality_collapser").getItem())){require(found<0,"Multiple paid collapsers");found=slot;}
            require(found>=0,"Paid output not in actual player inventory");var paid=p.getInventory().getItem(found);require(paid.getCount()==1,"Wrong paid output count");p.getInventory().setItem(found,ItemStack.EMPTY);held(p,paid);teleportToRift(p);
            rewardCount=(int)Math.sqrt(rift(p.serverLevel()).getRiftSize());
        });return false;}
        if(phase==3){if(!mc.player.getMainHandItem().is(CatalogModule.stack("causality_collapser").getItem()) || mc.player.distanceToSqr(rewardCenter)>100 || ++stable<10)return false;phase=4;stable=0;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return false;}
        if(!(mc.level.getEntity(riftId) instanceof FluxRiftEntity rift) || !rift.getCollapse() || rift.getRiftSize()>=initialSize)return false;
        if(phase==4){phase=5;submit(mc,()->{var p=player(mc);require(p.getMainHandItem().isEmpty(),"Actual native throw did not pay one survival item");require(rift(p.serverLevel()).getCollapse(),"Actual projectile impact did not collapse live rift");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_THROW_OK: paidphysicalpedestaloutput/nativepickup/actualC2Ssurvivalthrow,oneitemconsumed,ordinaryprojectilecollision,riftcollapse; stability100 containmentfixture");});return false;}
        return true;
    }
    private static int seeds(ServerPlayer player) { int count=0;for(ItemStack stack:player.getInventory().items)if(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals("thaumcraft:void_seed"))count+=stack.getCount();return count; }
    private static boolean rewards(Minecraft mc) {
        worldView(mc);if(mc.level.getEntity(riftId)!=null)return false;
        if(phase==0){phase=1;submit(mc,()->{
            var p=player(mc);var drops=p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(rewardCenter,rewardCenter).inflate(12));
            int count=drops.stream().filter(e->ForgeRegistries.ITEMS.getKey(e.getItem().getItem()).toString().equals("thaumcraft:void_seed")).mapToInt(e->e.getItem().getCount()).sum();
            require(count+seeds(p)==rewardCount,"Actual collapse Void Seed reward count mismatch");
            for(var drop:drops)if(drop.getItem().is(CatalogModule.stack("primordial_pearl").getItem()))require(drop.getItem().getDamageValue()>=4 && drop.getItem().getDamageValue()<=7,"Wrong original Pearl reward damage");
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_COLLAPSE_REWARDS_OK: nativecollapse completed,{}actualVoidSeeds,Pearlifrolled damage4..7; no scripteddrop/output",rewardCount);
        });return false;}
        if(phase==1){if(++stable<15)return false;stable=0;submit(mc,()->{
            var p=player(mc);if(seeds(p)<rewardCount){var drops=p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(rewardCenter,rewardCenter).inflate(12),e->e.isAlive() && ForgeRegistries.ITEMS.getKey(e.getItem().getItem()).toString().equals("thaumcraft:void_seed"));
                require(!drops.isEmpty(),"Actual reward lost before native pickup");var next=drops.get(0);p.connection.teleport(next.getX(),161,next.getZ(),0,15);
            }
        });
            if(mc.player.getInventory().items.stream().filter(s->ForgeRegistries.ITEMS.getKey(s.getItem()).toString().equals("thaumcraft:void_seed")).mapToInt(ItemStack::getCount).sum()<rewardCount)return false;
            phase=2;return false;
        }
        if(phase==2){phase=3;submit(mc,()->{
            var p=player(mc);require(seeds(p)==rewardCount,"Final native pickup count mismatch");var k=KnowledgeStore.get(p);
            require(k.isResearchCompleteStrict("FLUX") && k.isResearchCompleteStrict("FLUXRIFT") && k.isResearchCompleteStrict("RIFTCLOSER") && !k.isResearchKnown("BASEELDRITCH") && !k.isResearchKnown("f_VOIDSEED"),"Loot pickup invented late Eldritch/scan progression");
            int found=-1;for(int slot=0;slot<p.getInventory().items.size();slot++)if(ForgeRegistries.ITEMS.getKey(p.getInventory().getItem(slot).getItem()).toString().equals("thaumcraft:void_seed")){found=slot;break;}
            require(found>=0,"Missing real reward stack");var paid=p.getInventory().getItem(found);p.getInventory().setItem(found,ItemStack.EMPTY);held(p,paid);
            LogUtils.getLogger().info("THAUMCRAFT_RIFT_NATIVE_REWARD_PICKUP_OK: {}seeds actuallypickedup viaordinaryItemEntitycollision; no directplayerTouch/inventorygift,threeoriginalstudiescomplete,lateEldritchgateandf_VOIDSEEDnotinvented",rewardCount);
        });return false;}return true;
    }
    private static void prepare(Minecraft mc) {
        var p=player(mc);var level=p.serverLevel();level.setDayTime(6000);
        for(int x=-1;x<=4;x++)for(int z=-1;z<=2;z++)level.getChunk(x,z); // Named loaded arena fixture, never production force loading.
        for(int x=-16;x<=72;x++)for(int z=-16;z<=32;z++){
            level.setBlockAndUpdate(new BlockPos(x,160,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=161;y<=170;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        for(int x=-1;x<=4;x++)for(int z=-1;z<=2;z++){
            BlockPos pos=new ChunkPos(x,z).getMiddleBlockPosition(160);AuraManager.drainVis(level,pos,Float.MAX_VALUE,false);AuraManager.drainFlux(level,pos,Float.MAX_VALUE,false);
        }
        level.setBlockAndUpdate(MATRIX,CatalogBlocks.block("infusion_matrix").defaultBlockState());level.setBlockAndUpdate(CENTRAL,CatalogBlocks.block("pedestal_arcane").defaultBlockState());central(level).setItem(0,new ItemStack(Items.TNT));
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})level.setBlockAndUpdate(MATRIX.offset(x,-2,z),CatalogBlocks.block("pillar_arcane").defaultBlockState());
        ItemStack[] reagents={CatalogModule.stack("morphic_resonator"),new ItemStack(Items.REDSTONE_BLOCK),new ItemStack(thaumcraft.alchemy.AlchemyModule.ALUMENTUM.get()),new ItemStack(thaumcraft.alchemy.AlchemyModule.NITOR_ITEM.get()),CatalogModule.stack("vis_resonator"),new ItemStack(Items.REDSTONE_BLOCK),new ItemStack(thaumcraft.alchemy.AlchemyModule.ALUMENTUM.get()),new ItemStack(thaumcraft.alchemy.AlchemyModule.NITOR_ITEM.get())};
        for(int i=0;i<REAGENT_OFFSETS.length;i++){var offset=REAGENT_OFFSETS[i];var pos=MATRIX.offset(offset[0],-2,offset[1]);level.setBlockAndUpdate(pos,CatalogBlocks.block("pedestal_arcane").defaultBlockState());((InfusionPedestalBlockEntity)level.getBlockEntity(pos)).setItem(0,reagents[i]);}
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(Math.abs(x)+Math.abs(z)>5)level.setBlockAndUpdate(MATRIX.offset(x,-3,z),Blocks.DRAGON_HEAD.defaultBlockState());
        for(var color:net.minecraft.world.item.DyeColor.values()){
            int x=4+color.getId()%4,z=1+color.getId()/4;
            level.setBlockAndUpdate(MATRIX.offset(x,-3,z),CatalogBlocks.block("candle_"+color.getName()).defaultBlockState());
            level.setBlockAndUpdate(MATRIX.offset(-x,-3,-z),CatalogBlocks.block("candle_"+color.getName()).defaultBlockState());
        }
        for(int i=0;i<2;i++){level.setBlockAndUpdate(MATRIX.offset(i*2-1,-2,-5),CatalogBlocks.block("jar_normal").defaultBlockState());require(jar(level,i).addExact(i==0?Aspect.ELDRITCH:Aspect.FLUX,70),"Typed original collapser essentia fixture");}
        p.getInventory().clearContent();fixtureKnowledge(p,"INFUSION");fixtureKnowledge(p,"VISBATTERY");
        p.getAbilities().invulnerable=true;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.getAbilities().instabuild=false;p.onUpdateAbilities();
        held(p,new ItemStack(ScanningModule.THAUMOMETER.get()));p.connection.teleport(8,164,0,0,15);ResearchNetwork.sync(p);
        AuraManager.addFlux(level,SOURCE,5001);generationReady=true;
        LogUtils.getLogger().info("THAUMCRAFT_RIFT_FIXTURES: ownedisolatedworld/bedrockloadedarena/INFUSION+VISBATTERY/rawknowledgelater/TNT+8components+140typedessentia/symmetricskulls+16candlepairs/caster/scanner/book explicitfixtures; naturalgenerationonlyfromFlux5001/nativeprobability,finishedrift/collapser absent; nofullsurvival/multiplayer claim");
    }
    private static void start(Minecraft mc) {
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null)return;
        started=true;previous=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();mc.options.pauseOnLostFocus=false;
        mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;mc.resizeDisplay();
        GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(0x54433627L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static void finish(Minecraft mc) {
        require(SAVED.get()==IMAGES.length,"Missing9current Rift screenshots");stopped=true;mc.options.tutorialStep=previous;
        LogUtils.getLogger().info("THAUMCRAFT_RIFT_RENDER_AUDIT_OK: 9currentnativeworld/bookcaptures,sharedseededspine,actualstability/collapse and paidaltaroutput; no substitutedgalleryrift");
        LogUtils.getLogger().info("THAUMCRAFT_RIFT_CLIENT_SMOKE_OK: 9 scenes; nativevariableprobabilitygeneration/fractionalFluxpayment,actualscan/FLUX+FLUXRIFT+RIFTCLOSERC2Sstudies,paid100essentiainfusion,physicalstabilizer/nativepaidprojectile/collapse/{}seedspickup; explicitfixtures,no directruntimetickers; world={}",rewardCount,WORLD);
        mc.getConnection().getConnection().disconnect(Component.literal("Rift audit complete"));mc.clearLevel(new TitleScreen());mc.stop();
    }
    private static void fail(Minecraft mc,Throwable error) { if(stopped)return;stopped=true;if(previous!=null)mc.options.tutorialStep=previous;LogUtils.getLogger().error("THAUMCRAFT_RIFT_CLIENT_SMOKE_FAILED",error);mc.stop(); }
}
