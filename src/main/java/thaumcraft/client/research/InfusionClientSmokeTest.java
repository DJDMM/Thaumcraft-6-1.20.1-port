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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.event.level.ChunkEvent;
import thaumcraft.alchemy.*;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.infusion.*;
import thaumcraft.research.*;
import thaumcraft.scanning.ScanningNetwork;
import thaumcraft.scanning.client.ThaumometerClient;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in isolated owned world: actual Salis/caster C2S, natural infusion ticks and server payment checks. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class InfusionClientSmokeTest {
    private static final String WORLD="thaumcraft-infusion-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"unformed","formed","active","crafting","complete","materials","book-matrix","book-elemental","vis-resonator"};
    private static final BlockPos MATRIX=new BlockPos(0,114,0);
    private static final List<BlockPos> displays=new CopyOnWriteArrayList<>();
    private static final AtomicInteger saved=new AtomicInteger();
    private static volatile CompoundTag snapshot;
    private static CompletableFuture<Void> work;
    private static boolean started,setup,prepared,captureRequested,captured,stopped;
    private static int scene,phase,stableTicks,modelStates;
    private static long began;
    private static TutorialSteps previousTutorial;
    private static String bookBefore;
    private InfusionClientSmokeTest() {}
    static void snapshot(CompoundTag tag){if(Boolean.getBoolean("thaumcraft.infusionSmokeTest"))snapshot=tag.copy();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.infusionSmokeTest")||stopped)return;
        var mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try{
            require(System.nanoTime()-began<420_000_000_000L,"Infusion integrated audit timed out scene="+scene+" phase="+phase);
            if(!started){startWorld(mc);return;}if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong infusion test world");mc.getToasts().clear();
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!setup){setup=true;submit(mc,()->prepareWorld(mc));return;}
            if(scene==IMAGES.length){finish(mc);return;}
            if(!(mc.level.getBlockEntity(MATRIX) instanceof InfusionMatrixBlockEntity matrix))return;
            if(scene==0){if(!(mc.level.getBlockEntity(MATRIX.below(2)) instanceof InfusionPedestalBlockEntity pedestal)||pedestal.isEmpty())return;
                if(!prepared){prepared=true;auditModels(mc);clientMutationAudit(mc);
                    // Simulate an already tracked old-save tile. Only the migration's real chunk
                    // packet can replace this client-side catalogue BE with the operational type.
                    mc.level.getChunkAt(MATRIX).addAndRegisterBlockEntity(new thaumcraft.catalog.blocks.CatalogBlockEntity(MATRIX,mc.level.getBlockState(MATRIX)));
                    submit(mc,()->{var level=player(mc).serverLevel();var chunk=level.getChunkAt(MATRIX);
                        chunk.addAndRegisterBlockEntity(new thaumcraft.catalog.blocks.CatalogBlockEntity(MATRIX,level.getBlockState(MATRIX)));
                        LegacyInfusionMigration.load(new ChunkEvent.Load(chunk,false));
                        for(int i=0;i<256&&!(level.getBlockEntity(MATRIX) instanceof InfusionMatrixBlockEntity);i++)LegacyInfusionMigration.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true));
                        require(level.getBlockEntity(MATRIX) instanceof InfusionMatrixBlockEntity,"Server legacy matrix did not migrate");});return;}
                if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());}
            else if(scene==1){
                if(!prepared){prepared=true;mc.setScreen(null);use(mc);return;}
                if(!InfusionStability.valid(mc.level,MATRIX))return;
                if(phase==0){phase=1;submit(mc,()->{var player=player(mc);require(player.getMainHandItem().getCount()==1,"Salis packet did not consume exactly one dust");require(!serverMatrix(mc).active(),"Dust activated matrix before caster click");hand(mc,CatalogModule.stack("caster_basic"));});return;}
                if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());
            }else if(scene==2){
                if(!InfusionMatrixBlock.isCaster(mc.player.getMainHandItem()))return;
                if(!prepared){prepared=true;mc.setScreen(null);use(mc);return;}if(!matrix.active()||matrix.startup()<.95F)return;
                require(!matrix.crafting(),"First caster packet skipped idle activation");if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());
            }else if(scene==3){
                if(!prepared){prepared=true;mc.setScreen(null);use(mc);return;}if(!matrix.crafting()||matrix.getAspects().visSize()==0||matrix.getAspects().visSize()>=25)return;
                if(phase==0){phase=1;submit(mc,()->{require(serverMatrix(mc).crafting(),"Second caster packet did not start craft");require(jars(mc).stream().mapToInt(EssentiaJarBlockEntity::amount).sum()<85,"Crafting animation without real source debit");});return;}
                if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());
            }else if(scene==4){
                var central=mc.level.getBlockEntity(MATRIX.below(2));if(matrix.crafting()||!(central instanceof InfusionPedestalBlockEntity p)||!p.getItem(0).is(CatalogBlocks.block("crystal_aer").asItem()))return;
                if(!prepared){prepared=true;submit(mc,()->verifyFinish(mc));return;}if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());
            }else if(scene==5){if(!prepared){prepared=true;submit(mc,()->prepareMaterials(mc));return;}
                for(BlockPos pos:displays){if(mc.level.getBlockState(pos).isAir())return;if(mc.level.getBlockEntity(pos) instanceof InfusionMatrixBlockEntity material&&material.startup()<.95F)return;}if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());
            }else if(scene==6||scene==7){if(snapshot==null)return;if(!prepared){prepared=true;openBook(mc);}
                require(mc.screen instanceof ThaumonomiconPageScreen,"Missing actual book recipe page");
            }else{
                if(!prepared){prepared=true;mc.setScreen(null);submit(mc,()->{hand(mc,CatalogModule.stack("vis_resonator"));ScanningNetwork.sendHud(player(mc));});return;}
                if(!BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath().equals("vis_resonator")||ThaumometerClient.currentSnapshot()==null)return;
                require(ThaumometerClient.currentTarget()==null&&ThaumometerClient.isVisibleForSmokeTest(),"Resonator performed scan / aura meter hidden");
            }
            if(++stableTicks>=(scene==3?60:12)&&!captured)captureRequested=true;
            if(captured&&saved.get()==scene+1&&stableTicks>=24){scene++;phase=stableTicks=0;prepared=captured=captureRequested=false;}
        }catch(Throwable error){fail(mc,error);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.infusionSmokeTest")||stopped||!captureRequested||captured)return;
        var mc=Minecraft.getInstance();try{
            if(scene<6&&!(mc.screen instanceof Gallery)||scene>=6&&scene<=7&&!(mc.screen instanceof ThaumonomiconPageScreen))return;
            captured=true;captureRequested=false;String name="tc6-infusion-"+IMAGES[scene]+".png";var file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{if(file.isFile()&&file.length()>0){saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_INFUSION_SMOKE_IMAGE: {}",file.getAbsolutePath());}
                else mc.execute(()->fail(mc,new AssertionError("Missing screenshot "+name)));});
        }catch(Throwable error){fail(mc,error);}
    }
    private static void startWorld(Minecraft mc){
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;started=true;previousTutorial=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(0x54433615L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static ServerPlayer player(Minecraft mc){var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(player!=null,"Missing integrated player");return player;}
    private static InfusionMatrixBlockEntity serverMatrix(Minecraft mc){return (InfusionMatrixBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX);}
    private static void submit(Minecraft mc,Runnable action){require(work==null,"Overlapping server QA");var result=new CompletableFuture<Void>();work=result;mc.getSingleplayerServer().execute(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});}
    private static void hand(Minecraft mc,ItemStack item){var player=player(mc);player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,item);player.inventoryMenu.broadcastChanges();}
    private static void use(Minecraft mc){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(MATRIX.getCenter(),Direction.NORTH,MATRIX,false));}
    private static void research(ServerPlayer player,String key){try{var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(player),key,ResearchCatalog.get(key).stages().size()+1);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void place(Minecraft mc,BlockPos pos,String id){player(mc).serverLevel().setBlockAndUpdate(pos,CatalogBlocks.block(id).defaultBlockState());displays.add(pos);}
    private static void prepareWorld(Minecraft mc){
        var player=player(mc);var level=player.serverLevel();player.setInvulnerable(true);
        for(BlockPos pos:BlockPos.betweenClosed(-7,111,-7,7,117,7))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        place(mc,MATRIX,"infusion_matrix");place(mc,MATRIX.below(2),"pedestal_arcane");
        ((InfusionPedestalBlockEntity)level.getBlockEntity(MATRIX.below(2))).setItem(0,AspectCrystalItem.create(Aspect.AIR));
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})for(int y:new int[]{-1,-2})place(mc,MATRIX.offset(x,y,z),"stone_arcane");
        place(mc,MATRIX.offset(3,-2,0),"pedestal_arcane");place(mc,MATRIX.offset(-3,-2,0),"pedestal_arcane");
        ((InfusionPedestalBlockEntity)level.getBlockEntity(MATRIX.offset(3,-2,0))).setItem(0,new ItemStack(Items.WHEAT_SEEDS));
        ((InfusionPedestalBlockEntity)level.getBlockEntity(MATRIX.offset(-3,-2,0))).setItem(0,new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));
        Aspect[] aspects={Aspect.AIR,Aspect.CRYSTAL,Aspect.TRAP};for(int i=0;i<3;i++){var pos=MATRIX.offset(i-1,-2,-4);place(mc,pos,"jar_normal");require(((EssentiaJarBlockEntity)level.getBlockEntity(pos)).addExact(aspects[i],i==2?25:30),"Jar fixture fill failed");}
        // Explicit owned late-recipe fixture. Ordinary players retain the closed canonical prerequisites.
        for(String key:List.of("INFUSION","CRYSTALFARMER","ELEMENTALTOOLS"))research(player,key);player.getInventory().clearContent();hand(mc,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));
        player.teleportTo(.5,112,3.5);player.setYRot(180);player.setXRot(-8);ResearchNetwork.sync(player);
    }
    private static List<EssentiaJarBlockEntity> jars(Minecraft mc){List<EssentiaJarBlockEntity> out=new ArrayList<>();for(int i=0;i<3;i++)out.add((EssentiaJarBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX.offset(i-1,-2,-4)));return out;}
    private static void verifyFinish(Minecraft mc){
        require(!serverMatrix(mc).crafting()&&jars(mc).stream().allMatch(j->j.amount()==20),"Wrong exact25-unit recipe debit");
        for(int x:new int[]{-3,3})require(((InfusionPedestalBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX.offset(x,-2,0))).isEmpty(),"Reagent pedestal was not debited");
        LogUtils.getLogger().info("THAUMCRAFT_INFUSION_REAL_FLOW: actual dust/caster packets ->25 essentia ->2 reagents ->crystal_aer; jars20/20/20");
    }
    private static void prepareMaterials(Minecraft mc){
        displays.clear();var level=player(mc).serverLevel();String[] types={"arcane","ancient","eldritch"};
        for(int i=0;i<3;i++){BlockPos pos=MATRIX.offset((i-1)*5,0,0);place(mc,pos,"infusion_matrix");place(mc,pos.below(2),"pedestal_"+types[i]);
            for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})place(mc,pos.offset(x,-2,z),"pillar_"+types[i]);
            var matrix=(InfusionMatrixBlockEntity)level.getBlockEntity(pos);var tag=matrix.saveWithoutMetadata();tag.putBoolean("active",true);tag.putFloat("stability",i==2?-30:25);matrix.load(tag);matrix.setChanged();level.sendBlockUpdated(pos,matrix.getBlockState(),matrix.getBlockState(),3);
            ((InfusionPedestalBlockEntity)level.getBlockEntity(pos.below(2))).setItem(0,CatalogModule.stack(i==0?"elemental_axe":i==1?"fortress_helm":"void_pickaxe"));}
    }
    private static void clientMutationAudit(Minecraft mc){
        var matrix=(InfusionMatrixBlockEntity)mc.level.getBlockEntity(MATRIX);var before=matrix.saveWithoutMetadata();matrix.setAspects(new AspectList().add(Aspect.AIR,999));require(matrix.addToContainer(Aspect.AIR,1)==1&&!matrix.takeFromContainer(Aspect.AIR,1)&&before.equals(matrix.saveWithoutMetadata()),"Client matrix mutated payment");
        var pedestal=(InfusionPedestalBlockEntity)mc.level.getBlockEntity(MATRIX.below(2));before=pedestal.saveWithoutMetadata();pedestal.setItem(0,new ItemStack(Items.DIAMOND));require(pedestal.removeItem(0,1).isEmpty()&&before.equals(pedestal.saveWithoutMetadata()),"Client pedestal manufactured item");
    }
    private static void auditModels(Minecraft mc){
        for(String id:List.of("pedestal_arcane","pedestal_ancient","pedestal_eldritch","stabilizer","inlay","matrix_cost","matrix_speed")){
            for(var state:CatalogBlocks.block(id).getStateDefinition().getPossibleStates()){
                var model=mc.getBlockRenderer().getBlockModel(state);require(model!=mc.getModelManager().getMissingModel(),"Missing altar model "+state);List<Direction> faces=new ArrayList<>(List.of(Direction.values()));faces.add(null);
                for(var face:faces)for(var quad:model.getQuads(state,face,RandomSource.create(0)))require(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),"Missing altar sprite "+state);modelStates++;}
        }
        for(String type:List.of("normal","ancient","eldritch"))require(mc.getResourceManager().getResource(InfusionModule.id("textures/blocks/infuser_"+type+".png")).isPresent(),"Missing original infuser texture");
        require(mc.level.getRecipeManager().getAllRecipesFor(InfusionModule.RECIPE_TYPE.get()).size()==56,"RecipeManager did not sync56 real recipes");
    }
    private static void openBook(Minecraft mc){
        var knowledge=PlayerKnowledge.load(snapshot);bookBefore=ThaumonomiconCompleteClientSmokeTest.gameplayState(knowledge).toString();var browser=new ThaumonomiconScreen(knowledge,knowledge.scanCount());mc.setScreen(browser);browser.archiveForSmokeTest(true);browser.selectForSmokeTest(scene==6?"INFUSION":"ELEMENTALTOOLS");
        require(mc.screen instanceof ThaumonomiconPageScreen,"Missing infusion book entry");var page=(ThaumonomiconPageScreen)mc.screen;String output=scene==6?"infusion_matrix":"elemental_axe";
        var selected=BookRecipeViews.resolve(scene==6?"thaumcraft:InfusionMatrix":"thaumcraft:ElementalAxe").stream().filter(v->BuiltInRegistries.ITEM.getKey(v.output().getItem()).getPath().equals(output)).findFirst().orElseThrow();
        require(page.focusRecipe(selected.id()),"Could not navigate to actual recipe chapter");page.showRecipeForSmokeTest(output);
        var view=page.recipesForSmokeTest().stream().filter(v->BuiltInRegistries.ITEM.getKey(v.output().getItem()).getPath().equals(output)).findFirst().orElseThrow();require(!view.reference()&&!view.ingredients().isEmpty(),"Book still shows archive recipe for operational altar");
    }
    private static void finish(Minecraft mc){
        if(phase==0){phase=1;submit(mc,()->require(bookBefore.equals(ThaumonomiconCompleteClientSmokeTest.gameplayState(KnowledgeStore.get(player(mc))).toString()),"Book changed authoritative gameplay knowledge"));return;}
        require(saved.get()==IMAGES.length,"Missing fresh infusion images");stopped=true;mc.options.tutorialStep=previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_INFUSION_RENDER_AUDIT_OK: {} baked states; actual matrix/pedestal BER; three original textures, startup/glow/halo; synchronized recipes and aura",modelStates);
        LogUtils.getLogger().info("THAUMCRAFT_INFUSION_CLIENT_SMOKE_OK: {} scenes; real ritual/caster packets, naturally consumed25 essentia/2 reagents, S2C/client mutation guards; isolated world={}",saved.get(),WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable failure){if(stopped)return;stopped=true;if(previousTutorial!=null)mc.options.tutorialStep=previousTutorial;LogUtils.getLogger().error("THAUMCRAFT_INFUSION_CLIENT_SMOKE_FAILED",failure);mc.stop();}
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("TC6 BETA26 / Infusion: "+IMAGES[scene]));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
            var mc=Minecraft.getInstance();try{
                gui.fill(0,0,width,height,0xFF17202B);gui.drawCenteredString(font,"TC6 BETA26 / Infusion: "+IMAGES[scene],width/2,15,0xFFEAD5A8);gui.flush();gui.pose().pushPose();
                try{gui.pose().translate(width/2.,height/2.+55,200);gui.pose().scale(43,-43,43);gui.pose().mulPose(Axis.XP.rotationDegrees(25));gui.pose().mulPose(Axis.YP.rotationDegrees(135));Lighting.setupFor3DItems();
                    for(BlockPos pos:displays){var state=mc.level.getBlockState(pos);if(state.isAir())continue;gui.pose().pushPose();gui.pose().translate(pos.getX(),pos.getY()-112,pos.getZ());
                        RenderSystem.runAsFancy(()->{mc.getBlockRenderer().renderSingleBlock(state,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);var tile=mc.level.getBlockEntity(pos);
                            if(tile instanceof InfusionMatrixBlockEntity||tile instanceof InfusionPedestalBlockEntity||tile instanceof EssentiaJarBlockEntity)require(!mc.getBlockEntityRenderDispatcher().renderItem(tile,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Missing real altar renderer");});gui.pose().popPose();}
                }finally{gui.flush();gui.pose().popPose();Lighting.setupFor3DItems();}
                var matrix=(InfusionMatrixBlockEntity)mc.level.getBlockEntity(MATRIX);gui.drawCenteredString(font,"active="+matrix.active()+" crafting="+matrix.crafting()+" stability="+matrix.stabilityName()+" remaining="+matrix.getAspects().visSize(),width/2,height-46,0xFFCDCEDF);
                gui.drawCenteredString(font,"Actual integrated-server state / original TC6 models",width/2,height-25,0xFF939CAF);
            }catch(Throwable error){fail(mc,error);}
        }
    }
}
