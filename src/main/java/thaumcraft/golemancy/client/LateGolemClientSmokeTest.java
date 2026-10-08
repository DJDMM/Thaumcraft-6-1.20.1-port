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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.*;
import thaumcraft.artifice.hungrychest.HungryChestBlockEntity;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.client.research.*;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.golemancy.entity.ThaumcraftGolemEntity;
import thaumcraft.golemancy.jar.BrainJarBlockEntity;
import thaumcraft.golemancy.levitator.*;
import thaumcraft.golemancy.press.*;
import thaumcraft.golemancy.press.client.GolemPressScreen;
import thaumcraft.golemancy.seals.core.*;
import thaumcraft.infusion.*;
import thaumcraft.research.*;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.aura.AuraManager;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Hidden owned client. New outputs are produced by actual C2S payments, never supplied as fixtures. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class LateGolemClientSmokeTest {
    private static final String WORLD="thaumcraft-late-golem-smoke-"+System.currentTimeMillis();
    private static final String[] IMAGES={"paid-research","hungry-chest-recipe","hungry-chest-absorbed","levitator-recipe","levitator-lifting","jar-infusion-active","jar-infusion-output","jar-xp-stored","jar-xp-released","flyer-ready-menu","flyer-paid-progress","flyer-output","flyer-deployed","flyer-elevated-pickup","flyer-delivered"};
    private static final BlockPos BENCH=new BlockPos(-7,112,0),CHEST=new BlockPos(-4,112,0),LIFT=new BlockPos(-4,112,4),JAR=new BlockPos(-4,112,-4),MATRIX=new BlockPos(6,114,-6),PRESS=new BlockPos(0,112,4),SOURCE=PRESS.below();
    private static final BlockPos DEPLOY=new BlockPos(1,111,0),PICKUP=new BlockPos(4,119,0);
    private static final SealPos COLLECT=new SealPos(PICKUP,Direction.UP),STORE=new SealPos(CHEST,Direction.NORTH);
    private static final long DESIGN=3L<<32;
    private static final String[] KEYS={"HUNGRYCHEST","LEVITATOR","JARBRAIN","GOLEMFLYER"};
    private static final AtomicInteger saved=new AtomicInteger();
    private static final List<BlockPos> altar=new ArrayList<>(),essentia=new ArrayList<>(),reagents=new ArrayList<>();
    private static boolean started,prepared,stopped,captureRequested,captured;
    private static int scene,phase,stable,wait,researchIndex,researchExpected=-1,golemId,batId,liftedId,heartbeatTicks,hungryPolls;
    private static int obsGolemBefore,obsArtificeBefore,theoryBefore,warpBefore,normalWarpBefore,jarReleaseAttempts,jarReleasePlayerXp,jarSlot;
    private static float liftAuraBefore,arcaneAuraBefore;
    private static long began;
    private static CompletableFuture<Void> work;
    private static TutorialSteps previous;
    private LateGolemClientSmokeTest() {}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static ServerPlayer player(Minecraft mc){var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(p!=null,"Missing integrated player");return p;}
    private static void submit(Minecraft mc,Runnable task){require(work==null,"Overlapping late-golem server tasks");var future=new CompletableFuture<Void>();work=future;mc.getSingleplayerServer().execute(()->{try{task.run();future.complete(null);}catch(Throwable e){future.completeExceptionally(e);}});}
    private static void stage(ServerPlayer p,String key){try{var m=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);m.setAccessible(true);m.invoke(KnowledgeStore.get(p),key,ResearchCatalog.get(key).stages().size()+1);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void knowledge(ServerPlayer p,KnowledgeType type,String category,int raw){try{var m=PlayerKnowledge.class.getDeclaredMethod("addKnowledge",KnowledgeType.class,String.class,int.class);m.setAccessible(true);m.invoke(KnowledgeStore.get(p),type,category,raw);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static ItemStack item(String id){var v=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id));require(v!=null&&v!=Items.AIR,"Missing item "+id);return new ItemStack(v);}
    private static void held(ServerPlayer p,ItemStack stack){p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();}
    private static void close(Minecraft mc){mc.player.closeContainer();mc.setScreen(null);}
    private static void click(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),face,pos,false));}
    private static void shift(Minecraft mc,boolean value){mc.player.input.shiftKeyDown=value;mc.player.setShiftKeyDown(value);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,value?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));}
    private static void view(Minecraft mc){if(!(mc.screen instanceof Gallery gallery)||gallery.renderedScene!=scene)mc.setScreen(new Gallery());}
    private static ArcaneWorkbenchBlockEntity bench(Minecraft mc){return (ArcaneWorkbenchBlockEntity)player(mc).serverLevel().getBlockEntity(BENCH);}
    private static GolemPressBlockEntity press(Minecraft mc){return (GolemPressBlockEntity)player(mc).serverLevel().getBlockEntity(PRESS);}
    private static InfusionMatrixBlockEntity matrix(Minecraft mc){return (InfusionMatrixBlockEntity)player(mc).serverLevel().getBlockEntity(MATRIX);}
    private static ThaumcraftGolemEntity golem(Minecraft mc){var e=player(mc).serverLevel().getEntity(golemId);require(e instanceof ThaumcraftGolemEntity,"Missing paid Flyer entity");return (ThaumcraftGolemEntity)e;}
    private static ThaumcraftGolemEntity clientGolem(Minecraft mc){var e=mc.level.getEntity(golemId);return e instanceof ThaumcraftGolemEntity g?g:null;}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.lateGolemSmokeTest")||stopped)return;
        var mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try{
            require(System.nanoTime()-began<900_000_000_000L,"Late golem timeout scene="+scene+" phase="+phase);
            if(!started){start(mc);return;}if(mc.level==null||mc.player==null||mc.getOverlay()!=null||mc.screen instanceof ReceivingLevelScreen)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong late golem world");mc.getToasts().clear();
            if(++heartbeatTicks%100==0){var chest=mc.level.getBlockEntity(CHEST);LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_HEARTBEAT: scene={} phase={} pos={} screen={} menu={} selected={} hand={} hotbar2={} hotbar3={} hotbar4={} chestType={} chestFirst={}",scene,phase,mc.player.position(),mc.screen==null?"none":mc.screen.getClass().getSimpleName(),mc.player.containerMenu.getClass().getSimpleName(),mc.player.getInventory().selected,mc.player.getMainHandItem(),mc.player.getInventory().getItem(2),mc.player.getInventory().getItem(3),mc.player.getInventory().getItem(4),chest==null?"none":chest.getClass().getSimpleName(),chest instanceof HungryChestBlockEntity hungry?hungry.getItem(0):ItemStack.EMPTY);}
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!prepared){prepared=true;submit(mc,()->prepare(mc));return;}
            if(captured){if(saved.get()==scene+1&&++stable>=20){scene++;phase=stable=wait=0;captured=captureRequested=false;}return;}
            if(scene==IMAGES.length){finish(mc);return;}
            if(!advance(mc))return;
            if(++stable>=15&&!captured)captureRequested=true;
        }catch(Throwable e){fail(mc,e);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.lateGolemSmokeTest")||stopped||!captureRequested||captured)return;
        var mc=Minecraft.getInstance();try{require(scene<IMAGES.length,"Invalid late golem capture");captured=true;captureRequested=false;
            String name="tc6-late-golem-"+IMAGES[scene]+".png";var file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{if(file.isFile()&&file.length()>0){saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_SMOKE_IMAGE: {}",file.getAbsolutePath());}else mc.execute(()->fail(mc,new AssertionError("Missing late golem image")));});
        }catch(Throwable e){fail(mc,e);}
    }

    private static boolean advance(Minecraft mc){
        if(phase>0&&Set.of(1,3,5,9,13,14).contains(scene))mc.player.getInventory().selected=0;
        if(scene==0)return research(mc);
        if(scene==1){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);p.connection.teleport(-6.5,112,2.5,180,20);p.getInventory().selected=0;held(p,ItemStack.EMPTY);prepareArcane(mc,"hungry_chest",1);});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().isEmpty()||!mc.level.getBlockState(BENCH).is(ArcaneModule.WORKBENCH.get()))return false;phase=2;click(mc,BENCH,Direction.UP);return false;}
            if(!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu)||!menu.getSlot(0).getItem().is(item("hungry_chest").getItem())||!menu.craftable())return false;
            require(menu.requiredVis()==15&&menu.crystalCost(2)==1&&menu.crystalCost(3)==1,"Hungry Chest original15vis/Terra1/Aqua1 missing");return true;
        }
        if(scene==2){
            if(phase==0){phase=1;var menu=(ArcaneWorkbenchMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,2,ClickType.SWAP,mc.player);return false;}
            if(phase==1){if(!mc.player.getInventory().getItem(2).is(item("hungry_chest").getItem()))return false;phase=2;close(mc);submit(mc,()->{require(bench(mc).isEmpty(),"Hungry Chest result did not pay components/crystals");require(AuraManager.getVis(player(mc).serverLevel(),BENCH)<arcaneAuraBefore-10,"Hungry Chest did not pay aura");player(mc).getInventory().selected=2;player(mc).connection.teleport(-3.5,112,2.5,180,30);player(mc).inventoryMenu.broadcastChanges();});return false;}
            mc.player.getInventory().selected=2;
            if(phase==2){if(!mc.player.getMainHandItem().is(item("hungry_chest").getItem()))return false;phase=3;click(mc,CHEST.below(),Direction.UP);return false;}
            if(phase==3){if(!(mc.level.getBlockEntity(CHEST) instanceof HungryChestBlockEntity))return false;phase=4;submit(mc,()->{var p=player(mc);require(p.getInventory().getItem(2).isEmpty(),"Hungry Chest placement did not consume crafted item");
                // Drop above the .875-high collision shape. Embedding the entity inside that
                // shape invokes vanilla ItemEntity.noPhysics push-out and skips entityInside.
                var d=new ItemEntity(p.serverLevel(),CHEST.getX()+.5,CHEST.getY()+1.1,CHEST.getZ()+.5,new ItemStack(Items.APPLE,3));d.setDeltaMovement(Vec3.ZERO);d.setNoPickUpDelay();require(p.serverLevel().addFreshEntity(d),"Hungry contact fixture missing");held(p,ItemStack.EMPTY);});return false;}
            if(phase==4){phase=5;submit(mc,()->{var p=player(mc);var c=(HungryChestBlockEntity)p.serverLevel().getBlockEntity(CHEST);if(++hungryPolls%100==0){int apples=0;for(int i=0;i<27;i++)if(c.getItem(i).is(Items.APPLE))apples+=c.getItem(i).getCount();var drops=p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(CHEST).inflate(8),e->e.getItem().is(Items.APPLE));LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_HUNGRY_WAIT: polls={} serverChestApples={} drops={}",hungryPolls,apples,drops.stream().map(e->"pos="+e.position()+",motion="+e.getDeltaMovement()+",noPhysics="+e.noPhysics+",alive="+e.isAlive()+",removed="+e.isRemoved()+",stack="+e.getItem()).toList());}if(c.getItem(0).is(Items.APPLE)&&c.getItem(0).getCount()==3){phase=6;require(p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(CHEST).inflate(1),e->e.getItem().is(Items.APPLE)).isEmpty(),"Hungry absorption duplicated contact entity");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_HUNGRY: actual15vis recipe C2S, placement, native entityInside absorption of3apples; no chest output supplied");}});return false;}
            if(phase==5){phase=4;return false;}if(phase==6){phase=7;click(mc,CHEST,Direction.NORTH);return false;}
            if(!(mc.player.containerMenu instanceof ChestMenu menu)||menu.getRowCount()!=3||menu.getSlot(0).getItem().getCount()!=3)return false;return true;
        }
        if(scene==3){
            if(phase==0){phase=1;close(mc);submit(mc,()->{player(mc).connection.teleport(-6.5,112,2.5,180,20);held(player(mc),ItemStack.EMPTY);prepareArcane(mc,"levitator",2);});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().isEmpty())return false;phase=2;click(mc,BENCH,Direction.UP);return false;}
            if(!(mc.player.containerMenu instanceof ArcaneWorkbenchMenu menu)||!menu.getSlot(0).getItem().is(item("levitator").getItem())||!menu.craftable())return false;
            require(menu.requiredVis()==35&&menu.crystalCost(0)==1,"Levitator original35vis/Aer1 missing");return true;
        }
        if(scene==4){
            if(phase==0){phase=1;var menu=(ArcaneWorkbenchMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,3,ClickType.SWAP,mc.player);return false;}
            if(phase==1){if(mc.player.getInventory().getItem(3).getCount()!=1)return false;phase=2;var menu=(ArcaneWorkbenchMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,3,ClickType.SWAP,mc.player);return false;}
            if(phase==2){if(mc.player.getInventory().getItem(3).getCount()!=2)return false;phase=3;close(mc);submit(mc,()->{var p=player(mc);require(bench(mc).isEmpty(),"Two Levitators did not consume entire doubled grid and Aer2");require(AuraManager.getVis(p.serverLevel(),BENCH)<arcaneAuraBefore-60,"Two35vis outputs did not pay aura");p.getInventory().selected=3;p.serverLevel().setBlockAndUpdate(LIFT.east().above(),Blocks.STONE.defaultBlockState());p.connection.teleport(LIFT.getX()+1.5,LIFT.getY()+2,LIFT.getZ()+.5,180,80);p.inventoryMenu.broadcastChanges();// Fixed moon phase and equal neighboring aura are explicit fixtures: a native
                // lunar/diffusion update must not hide the one-vis debit during this short check.
                p.serverLevel().setDayTime(4L*24000+6000);var chunk=new net.minecraft.world.level.ChunkPos(LIFT);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var anchor=new net.minecraft.world.level.ChunkPos(chunk.x+dx,chunk.z+dz).getMiddleBlockPosition(112);AuraManager.drainVis(p.serverLevel(),anchor,Float.MAX_VALUE,false);AuraManager.addVis(p.serverLevel(),anchor,100);}
                liftAuraBefore=AuraManager.getVis(p.serverLevel(),LIFT);});return false;}
            mc.player.getInventory().selected=3;
            if(phase==3){phase=4;mc.player.setXRot(80);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(mc.player.getYRot(),80,mc.player.onGround()));click(mc,LIFT.below(),Direction.UP);return false;}
            if(phase==4){if(!(mc.level.getBlockEntity(LIFT) instanceof LevitatorBlockEntity))return false;phase=5;submit(mc,()->{var p=player(mc);require(p.getInventory().getItem(3).getCount()==1&&p.serverLevel().getBlockState(LIFT).getValue(LevitatorBlock.FACING)==Direction.UP,"Placed actual Levitator did not retain paid Flyer component or face UP");var d=new ItemEntity(p.serverLevel(),LIFT.getX()+.5,LIFT.getY()+1.2,LIFT.getZ()+.5,new ItemStack(Items.FEATHER));d.setPickUpDelay(32767);require(p.serverLevel().addFreshEntity(d),"Lift fixture missing");liftedId=d.getId();});return false;}
            if(phase==5){phase=6;submit(mc,()->{var p=player(mc);var e=p.serverLevel().getEntity(liftedId);var tile=(LevitatorBlockEntity)p.serverLevel().getBlockEntity(LIFT);if(e!=null&&e.getY()>LIFT.getY()+2&&tile.energy()>0&&tile.energy()<1200){phase=7;require(AuraManager.getVis(p.serverLevel(),LIFT)<liftAuraBefore,"Device lift did not debit actual aura: before="+liftAuraBefore+",after="+AuraManager.getVis(p.serverLevel(),LIFT)+",energy="+tile.energy());LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_LEVITATOR: two real35vis/Aer1 crafts; one placed UP, physical feather lifted by native ticks; energy={} aura={}",tile.energy(),AuraManager.getVis(p.serverLevel(),LIFT));}});return false;}
            if(phase==6){phase=5;return false;}if(phase<7)return false;view(mc);return true;
        }
        if(scene==5){
            if(phase==0){phase=1;close(mc);submit(mc,()->prepareInfusion(mc));return false;}
            if(!(mc.level.getBlockEntity(MATRIX) instanceof InfusionMatrixBlockEntity tile))return false;
            if(phase==1){if(!mc.player.getMainHandItem().is(item("caster_basic").getItem()))return false;phase=2;click(mc,MATRIX,Direction.NORTH);return false;}
            if(phase==2){if(!tile.active()||tile.startup()<.95F)return false;phase=3;submit(mc,()->{var data=matrix(mc).saveWithoutMetadata();data.putFloat("stability",25);matrix(mc).load(data);matrix(mc).setChanged();player(mc).serverLevel().sendBlockUpdated(MATRIX,matrix(mc).getBlockState(),matrix(mc).getBlockState(),3);});return false;}
            if(phase==3){phase=4;click(mc,MATRIX,Direction.NORTH);return false;}if(!tile.crafting()||tile.getAspects().visSize()==0||tile.getAspects().visSize()>=75)return false;view(mc);return true;
        }
        if(scene==6){
            if(!(mc.level.getBlockEntity(MATRIX.below(2)) instanceof InfusionPedestalBlockEntity center)||!center.getItem(0).is(item("jar_brain").getItem()))return false;
            if(phase==0){phase=1;submit(mc,()->{require(!matrix(mc).crafting(),"Jar output while payment active");for(BlockPos pos:essentia)require(((EssentiaJarBlockEntity)player(mc).serverLevel().getBlockEntity(pos)).amount()==0,"Jar infusion did not pay25of each aspect");for(int i=0;i<reagents.size();i++){var p=(InfusionPedestalBlockEntity)player(mc).serverLevel().getBlockEntity(reagents.get(i));require(i==2?p.getItem(0).is(Items.BUCKET)&&p.getItem(0).getCount()==1:p.isEmpty(),"Jar reagent/container remainder wrong at"+i);}LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_JAR_INFUSION: actual caster C2S,75real essentia,brain/spider-eye2/water-bucket,normaljar replacement; bucket retained; no brainjar supplied");});return false;}view(mc);return true;
        }
        if(scene==7){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);held(p,ItemStack.EMPTY);p.connection.teleport(MATRIX.getX()+.5,112,MATRIX.getZ()+2.5,180,10);});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().isEmpty())return false;phase=2;click(mc,MATRIX.below(2),Direction.NORTH);return false;}
            if(phase==2){phase=3;submit(mc,()->{var p=player(mc);if(count(p,item("jar_brain").getItem())==1){phase=4;int slot=find(p,item("jar_brain").getItem());require(slot>=0&&slot<9,"Actual infusion jar not picked up into free hotbar");jarSlot=slot;p.getInventory().selected=slot;p.connection.teleport(JAR.getX()+.5,112,JAR.getZ()+2.5,180,35);p.inventoryMenu.broadcastChanges();}});return false;}
            if(phase==3){phase=2;return false;}
            if(phase==4){mc.player.getInventory().selected=jarSlot;if(!mc.player.getMainHandItem().is(item("jar_brain").getItem()))return false;phase=5;click(mc,JAR.below(),Direction.UP);return false;}
            if(phase==5){if(!(mc.level.getBlockEntity(JAR) instanceof BrainJarBlockEntity))return false;phase=6;submit(mc,()->{var p=player(mc);require(count(p,item("jar_brain").getItem())==0,"Brain jar placement left duplicate paid item");var xp=new ExperienceOrb(p.serverLevel(),JAR.getX()+.5,JAR.getY()+.5,JAR.getZ()+.5,20);require(p.serverLevel().addFreshEntity(xp),"Small XP source fixture missing");held(p,ItemStack.EMPTY);});return false;}
            if(!(mc.level.getBlockEntity(JAR) instanceof BrainJarBlockEntity jar)||jar.xp()!=20)return false;
            if(phase==6){phase=7;submit(mc,()->{require(((BrainJarBlockEntity)player(mc).serverLevel().getBlockEntity(JAR)).xp()==20,"Client jar display did not match actual captured XP");require(player(mc).serverLevel().getEntitiesOfClass(ExperienceOrb.class,new AABB(JAR).inflate(1)).isEmpty(),"XP capture duplicated source orb");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_JAR_CAPTURE: placed actual infused output captured20XP by native ticks and synchronized live brain model");});return false;}view(mc);return true;
        }
        if(scene==8){
            if(phase==0){phase=10;close(mc);submit(mc,()->jarReleasePlayerXp=player(mc).totalExperience);return false;}
            if(phase==10){phase=1;click(mc,JAR,Direction.NORTH);jarReleaseAttempts++;return false;}
            if(!(mc.level.getBlockEntity(JAR) instanceof BrainJarBlockEntity jar))return false;
            if(jar.xp()==20){if(++wait>12){require(jarReleaseAttempts<20,"Twenty C2S jar activations released no XP");phase=0;wait=0;}return false;}
            if(phase==1){phase=2;submit(mc,()->{var p=player(mc);var current=(BrainJarBlockEntity)p.serverLevel().getBlockEntity(JAR);int amount=20-current.xp();require(amount>0&&amount<=20&&current.eatDelay()>0,"Actual jar release did not debit XP/apply eat delay");long loose=p.serverLevel().getEntitiesOfClass(ExperienceOrb.class,new AABB(JAR).inflate(8)).stream().mapToLong(BrainJarBlockEntity::totalOrbXp).sum();int collected=p.totalExperience-jarReleasePlayerXp;require(collected>=0&&loose+collected==amount,"XP release did not conserve world-orb plus actual player XP");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_JAR_RELEASE: actual ungated C2S click released{}XP, exact world+player conservation,40tick recapture delay",amount);});return false;}view(mc);return true;
        }
        if(scene==9){
            if(phase==0){phase=1;close(mc);submit(mc,()->preparePress(mc));return false;}
            if(phase==1){if(!mc.player.getMainHandItem().is(AlchemyModule.SALIS_MUNDUS.get()))return false;phase=2;click(mc,PRESS,Direction.UP);return false;}
            if(phase==2){if(!(mc.level.getBlockEntity(PRESS) instanceof GolemPressBlockEntity))return false;phase=3;submit(mc,()->{require(player(mc).getMainHandItem().isEmpty(),"Press ritual did not pay one Salis");held(player(mc),ItemStack.EMPTY);});return false;}
            if(phase==3){phase=4;click(mc,PRESS,Direction.NORTH);return false;}
            if(!(mc.screen instanceof GolemPressScreen screen)||!(mc.player.containerMenu instanceof GolemPressMenu menu))return false;
            if(phase==4){if(screen.design()==null)return false;phase=5;screen.selectForSmokeTest(DESIGN);return false;}
            if(menu.checkedDesignId()!=DESIGN||!screen.canCreate())return false;require(screen.design().legs().id()==3&&screen.design().canManufacture(ResearchClient.golemPressKnowledge()),"Actual Flyer part gate/design missing");return true;
        }
        if(scene==10){
            if(!(mc.screen instanceof GolemPressScreen screen)||!(mc.player.containerMenu instanceof GolemPressMenu menu))return false;
            if(phase==0){phase=1;screen.create();return false;}int cost=GolemDesign.parse(DESIGN).orElseThrow().essentiaCost();if(menu.cost()!=cost)return false;
            if(phase==1){phase=2;submit(mc,()->{var p=player(mc);require(press(mc).golemId()==DESIGN&&press(mc).cost()==cost&&press(mc).getItem(0).isEmpty(),"Actual Flyer manufacture did not create paid pending job");require(count(p,item("levitator").getItem())==0,"Factory did not consume the remaining actual crafted Levitator");for(var component:GolemDesign.parse(DESIGN).orElseThrow().components())require(count(p,component.getItem())==0,"Factory component remained after real C2S start: "+component);require(!GolemPressNetwork.process(p,new GolemPressNetwork.Request(p.containerMenu.containerId,0,DESIGN,true)),"Replay started second Flyer");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FLYER_PAID: actual strictGOLEMFLYER selection+C2S, paid crafted Levitator and exact components,{}Machina pending,replay rejected",cost);});return false;}
            if(!(mc.level.getBlockEntity(PRESS) instanceof GolemPressBlockEntity p)||p.pressAngle()<90)return false;return true;
        }
        if(scene==11){
            if(phase==0){phase=1;submit(mc,()->{var level=player(mc).serverLevel();level.setBlockAndUpdate(SOURCE,CatalogBlocks.block("alembic").defaultBlockState());require(((AlembicBlockEntity)level.getBlockEntity(SOURCE)).addExact(Aspect.MECHANISM,GolemDesign.parse(DESIGN).orElseThrow().essentiaCost()),"Machina source fixture failed");});return false;}
            if(!(mc.player.containerMenu instanceof GolemPressMenu menu)||menu.getSlot(0).getItem().isEmpty()||menu.cost()!=0)return false;
            require(menu.getSlot(0).getItem().getCount()==1&&menu.getSlot(0).getItem().getTag().getLong("props")==DESIGN,"Flyer press output incorrect");
            if(phase==1){phase=2;submit(mc,()->{require(((AlembicBlockEntity)player(mc).serverLevel().getBlockEntity(SOURCE)).amount()==0&&press(mc).golemId()==-1,"Actual Flyer output did not pay exact Machina/retire job");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FLYER_OUTPUT: all actual fifth-tick Machina draws paid, one original props12884901888 output; none supplied");});return false;}return true;
        }
        if(scene==12){
            if(phase==0){phase=1;var menu=(GolemPressMenu)mc.player.containerMenu;mc.gameMode.handleInventoryMouseClick(menu.containerId,0,4,ClickType.SWAP,mc.player);return false;}
            if(phase==1){if(!mc.player.getInventory().getItem(4).is(item("golem").getItem()))return false;phase=2;close(mc);submit(mc,()->{player(mc).getInventory().selected=4;player(mc).connection.teleport(1.5,112,2.5,180,30);player(mc).inventoryMenu.broadcastChanges();});return false;}
            mc.player.getInventory().selected=4;
            if(phase==2){if(mc.player.getMainHandItem().isEmpty())return false;require(mc.player.getMainHandItem().getTag().getLong("props")==DESIGN,"Deployment used replacement Flyer");phase=3;click(mc,DEPLOY,Direction.UP);return false;}
            if(phase==3){if(!mc.player.getMainHandItem().isEmpty())return false;phase=4;submit(mc,()->{var p=player(mc);var workers=p.serverLevel().getEntitiesOfClass(ThaumcraftGolemEntity.class,new AABB(-2,111,-3,8,124,4));require(workers.size()==1,"Flyer deployment missing/duplicate");var g=workers.get(0);golemId=g.getId();require(g.props()==DESIGN&&g.isOwner(p)&&g.isValidSpawn()&&!g.isNoAi()&&g.isNoGravity()&&g.homePosition().equals(DEPLOY.above()),"Actual Flyer deployment lost operational state");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FLYER_DEPLOYMENT: actual paid factory output consumed by C2S placement; owned native flight worker,originalprops");});return false;}if(clientGolem(mc)==null)return false;view(mc);return true;
        }
        if(scene==13){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);p.serverLevel().setBlockAndUpdate(PICKUP,Blocks.STONE.defaultBlockState());p.serverLevel().setBlockAndUpdate(PICKUP.south(2),Blocks.STONE.defaultBlockState());p.connection.teleport(4.5,120,2.5,180,30);held(p,item("seal_pickup"));});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().is(item("seal_pickup").getItem()))return false;phase=2;click(mc,PICKUP,Direction.UP);return false;}
            if(phase==2){if(!mc.player.getMainHandItem().isEmpty())return false;phase=3;submit(mc,()->{var p=player(mc);var seal=SealService.get(p.serverLevel()).seal(COLLECT);require(seal!=null,"Actual elevated Collect seal absent");var d=new ItemEntity(p.serverLevel(),4.5,120.1,.5,new ItemStack(Items.DIAMOND,3));d.setDeltaMovement(Vec3.ZERO);d.setNoPickUpDelay();require(p.serverLevel().addFreshEntity(d),"Elevated diamond fixture missing");p.connection.teleport(4.5,112,3.5,180,20);});return false;}
            var g=clientGolem(mc);if(g==null||!g.getMainHandItem().is(Items.DIAMOND)||g.getMainHandItem().getCount()!=3||g.getY()<PICKUP.getY()-2)return false;
            if(phase==3){phase=4;submit(mc,()->{var p=player(mc);require(golem(mc).getY()>PICKUP.getY()-2&&golem(mc).getMainHandItem().getCount()==3,"Flyer did not physically rise to Collect task");require(p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(PICKUP).inflate(2),e->e.getItem().is(Items.DIAMOND)).isEmpty(),"Elevated collection duplicated drop");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FLYER_COLLECT: actual native flight to physical elevated seal,three ground diamonds moved into real hand; no remote transfer");});return false;}view(mc);return true;
        }
        if(scene==14){
            if(phase==0){phase=1;close(mc);submit(mc,()->{var p=player(mc);p.connection.teleport(-3.5,112,2.5,180,20);held(p,item("seal_fill"));});return false;}
            if(phase==1){if(!mc.player.getMainHandItem().is(item("seal_fill").getItem()))return false;phase=2;click(mc,CHEST,Direction.NORTH);return false;}
            if(phase==2){if(!mc.player.getMainHandItem().isEmpty())return false;phase=3;submit(mc,()->require(SealService.get(player(mc).serverLevel()).seal(STORE)!=null,"Actual Hungry Store seal missing"));return false;}
            if(phase==3){phase=4;submit(mc,()->{var p=player(mc);var c=(HungryChestBlockEntity)p.serverLevel().getBlockEntity(CHEST);int diamonds=0;for(int i=0;i<27;i++)if(c.getItem(i).is(Items.DIAMOND))diamonds+=c.getItem(i).getCount();if(diamonds==3){phase=5;require(golem(mc).carrying().stream().allMatch(ItemStack::isEmpty),"Flyer delivery duplicated hand cargo");held(p,ItemStack.EMPTY);LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FLYER_DELIVERY: actual flight back down,Store capability delivered3diamonds to the previously crafted Hungry Chest; cargo cleared");}});return false;}
            if(phase==4){phase=3;return false;}if(phase==5){phase=6;click(mc,CHEST,Direction.NORTH);return false;}
            if(!(mc.player.containerMenu instanceof ChestMenu menu))return false;int diamonds=0;for(int i=0;i<27;i++)if(menu.getSlot(i).getItem().is(Items.DIAMOND))diamonds+=menu.getSlot(i).getItem().getCount();return diamonds==3;
        }
        return false;
    }

    private static boolean research(Minecraft mc){
        if(phase==0){phase=1;submit(mc,()->{var p=player(mc);held(p,new ItemStack(ScanningModule.THAUMOMETER.get()));p.setItemInHand(InteractionHand.OFF_HAND,item("brain"));p.inventoryMenu.broadcastChanges();});return false;}
        if(phase==1){if(!mc.player.getMainHandItem().is(ScanningModule.THAUMOMETER.get())||!mc.player.getOffhandItem().is(item("brain").getItem()))return false;shift(mc,true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=2;wait=0;return false;}
        if(phase==2){if(++wait<12)return false;submit(mc,()->{var p=player(mc);if(KnowledgeStore.get(p).knowsResearch("f_BRAIN")){phase=3;p.setItemInHand(InteractionHand.OFF_HAND,AspectCrystalItem.create(Aspect.FLIGHT));p.inventoryMenu.broadcastChanges();}else phase=1;});return false;}
        if(phase==3){if(AspectCrystalItem.crystalAspect(mc.player.getOffhandItem())!=Aspect.FLIGHT)return false;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=4;wait=0;return false;}
        if(phase==4){if(++wait<12)return false;submit(mc,()->{var p=player(mc);if(KnowledgeStore.get(p).knowsResearch("!volatus")){phase=5;p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.connection.teleport(.5,112,-1.5,0,0);var bat=EntityType.BAT.create(p.serverLevel());bat.setPos(.5,113.3,1.5);bat.setNoAi(true);bat.setNoGravity(true);bat.setResting(false);require(p.serverLevel().addFreshEntity(bat),"Real Bat scan fixture absent");batId=bat.getId();p.inventoryMenu.broadcastChanges();}else phase=3;});return false;}
        if(phase==5){shift(mc,false);if(!mc.player.getOffhandItem().isEmpty()||mc.player.distanceToSqr(.5,112,-1.5)>1)return false;mc.player.setYRot(0);mc.player.setXRot(0);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(0,0,mc.player.onGround()));mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=6;wait=0;return false;}
        if(phase==6){if(++wait<12)return false;submit(mc,()->{var p=player(mc);var k=KnowledgeStore.get(p);if(k.knowsResearch("f_FLY")){phase=7;var bat=p.serverLevel().getEntity(batId);if(bat!=null)bat.discard();held(p,new ItemStack(ResearchModule.THAUMONOMICON.get()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getInventory().setItem(9,new ItemStack(Items.CHEST));p.getInventory().setItem(10,new ItemStack(Items.HOPPER));p.getInventory().setItem(11,item("brain"));knowledge(p,KnowledgeType.OBSERVATION,"GOLEMANCY",32);knowledge(p,KnowledgeType.OBSERVATION,"ARTIFICE",16);knowledge(p,KnowledgeType.THEORY,"GOLEMANCY",64);obsGolemBefore=k.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY");obsArtificeBefore=k.rawKnowledge(KnowledgeType.OBSERVATION,"ARTIFICE");theoryBefore=k.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY");warpBefore=k.permanentWarp();normalWarpBefore=k.normalWarp();p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_SCANS: real held-brain/flight-crystal and trackedBat C2S scans supplied f_BRAIN,!volatus,f_FLY; no direct new facts seeded");}else phase=5;});return false;}
        if(phase==7){
            if(!mc.player.getMainHandItem().is(ResearchModule.THAUMONOMICON.get()))return false;
            var k=ResearchClient.golemPressKnowledge();
            if(researchIndex<KEYS.length){String key=KEYS[researchIndex];if(k.isResearchCompleteStrict(key)){researchIndex++;researchExpected=-1;return false;}int current=k.researchStage(key);if(current==researchExpected)return false;researchExpected=current;ResearchNetwork.requestAdvance(key,current);return false;}
            phase=8;submit(mc,()->{var p=player(mc);var state=KnowledgeStore.get(p);for(String key:KEYS)require(state.isResearchCompleteStrict(key),"New research did not actually complete: "+key);require(state.rawKnowledge(KnowledgeType.OBSERVATION,"GOLEMANCY")==obsGolemBefore-32&&state.rawKnowledge(KnowledgeType.OBSERVATION,"ARTIFICE")==obsArtificeBefore-16&&state.rawKnowledge(KnowledgeType.THEORY,"GOLEMANCY")==theoryBefore-64,"Four original research costs not actually debited");require(p.getInventory().getItem(9).isEmpty()&&p.getInventory().getItem(10).isEmpty()&&p.getInventory().getItem(11).isEmpty()&&state.permanentWarp()==warpBefore+2&&state.normalWarp()==normalWarpBefore+1,"Chest/hopper/brain or original3warp split payment wrong");LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_RESEARCH_PAID: four actual C2S entries,32GolemancyObs+16ArtificeObs+64GolemancyTheory,chest/hopper/brain debits,2permanent+1normalwarp; preceding studies explicitfixtures");});return false;
        }
        if(!(mc.screen instanceof ThaumonomiconPageScreen)){var k=ResearchClient.golemPressKnowledge();var b=new ThaumonomiconScreen(k,k.scanCount());mc.setScreen(new ThaumonomiconPageScreen(b,ResearchCatalog.get("GOLEMFLYER"),k,k.scanCount()));}
        return true;
    }

    private static void prepareArcane(Minecraft mc,String output,int count){
        var p=player(mc);var level=p.serverLevel();level.setBlockAndUpdate(BENCH,ArcaneModule.WORKBENCH.get().defaultBlockState());var b=bench(mc);b.clearContent();
        var recipe=level.getRecipeManager().getAllRecipesFor(ArcaneModule.RECIPE_TYPE.get()).stream().filter(r->r.getResultItem(level.registryAccess()).is(item(output).getItem())).findFirst().orElseThrow();
        require(recipe.gridWidth()==3&&recipe.gridHeight()==3,"Unexpected audited arcane grid");for(int i=0;i<9;i++){var ingredient=recipe.getIngredients().get(i);if(!ingredient.isEmpty()){require(ingredient.getItems().length>0,"Empty tag in real recipe");b.setItem(i,ingredient.getItems()[0].copyWithCount(count));}}
        for(int i=0;i<6;i++)if(recipe.crystalCost(i)>0)b.setItem(9+i,AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i]),recipe.crystalCost(i)*count));
        AuraManager.drainVis(level,BENCH,Float.MAX_VALUE,false);AuraManager.addVis(level,BENCH,180);arcaneAuraBefore=AuraManager.getVis(level,BENCH);p.containerMenu.broadcastChanges();
        LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_ARCANE_FIXTURE: {} complete original input grids/crystals,aura180, no{}output supplied",count,output);
    }
    private static void prepareInfusion(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();altar.clear();essentia.clear();reagents.clear();
        put(level,MATRIX,"infusion_matrix");put(level,MATRIX.below(2),"pedestal_arcane");for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})put(level,MATRIX.offset(x,-2,z),"pillar_arcane");
        ((InfusionPedestalBlockEntity)level.getBlockEntity(MATRIX.below(2))).setItem(0,item("jar_normal"));
        List<ItemStack> inputs=List.of(item("brain"),new ItemStack(Items.SPIDER_EYE),new ItemStack(Items.WATER_BUCKET),new ItemStack(Items.SPIDER_EYE));
        List<BlockPos> positions=List.of(MATRIX.offset(3,-2,0),MATRIX.offset(0,-2,3),MATRIX.offset(-3,-2,0),MATRIX.offset(0,-2,-3));
        for(int i=0;i<4;i++){put(level,positions.get(i),"pedestal_arcane");reagents.add(positions.get(i));((InfusionPedestalBlockEntity)level.getBlockEntity(positions.get(i))).setItem(0,inputs.get(i));}
        Aspect[] types={Aspect.MIND,Aspect.SENSES,Aspect.UNDEAD};for(int i=0;i<3;i++){BlockPos pos=MATRIX.offset(i-1,-2,-4);put(level,pos,"jar_normal");essentia.add(pos);require(((EssentiaJarBlockEntity)level.getBlockEntity(pos)).addExact(types[i],25),"Jar recipe25unit essentia fixture failed");}
        // A completed altar program, stability and symmetric skull pairs are explicit QA fixtures.
        for(int dx:new int[]{2,3,4})for(int dz:new int[]{-2,2})for(int sign:new int[]{-1,1})level.setBlockAndUpdate(MATRIX.offset(sign*dx,-3,dz),Blocks.SKELETON_SKULL.defaultBlockState());
        String[] colors={"white","orange","magenta","light_blue","yellow","lime","pink","gray"};int color=0;
        for(int dx:new int[]{2,3,4,5})for(int dz:new int[]{-4,4}){String id="candle_"+colors[color++];for(int sign:new int[]{-1,1})level.setBlockAndUpdate(MATRIX.offset(sign*dx,-2,sign*dz),CatalogBlocks.block(id).defaultBlockState());}
        held(p,item("caster_basic"));p.connection.teleport(MATRIX.getX()+.5,112,MATRIX.getZ()+3.5,180,-12);p.inventoryMenu.broadcastChanges();
        LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_INFUSION_FIXTURE: existing physical altar,normaljar central,brain/eyes2/waterbucket,Cognitio25/Sensus25/Exanimis25; no brainjar output supplied");
    }
    private static void put(net.minecraft.server.level.ServerLevel level,BlockPos pos,String id){level.setBlockAndUpdate(pos,CatalogBlocks.block(id).defaultBlockState());altar.add(pos);}
    private static void preparePress(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();require(count(p,item("levitator").getItem())==1,"Paid Levitator component lost before manufacture");
        for(var part:GolemPressFormation.parts(PRESS,Direction.NORTH))level.setBlockAndUpdate(part.pos(),part.id().equals("golem_builder")?Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP):GolemPressPlaceholderBlock.original(part.id()).defaultBlockState());
        int slot=9;for(var component:GolemDesign.parse(DESIGN).orElseThrow().components())if(!component.is(item("levitator").getItem()))p.getInventory().setItem(slot++,component.copy());
        held(p,new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));p.connection.teleport(.5,112,7.5,180,20);p.inventoryMenu.broadcastChanges();
        LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FACTORY_FIXTURE: preexisting Mind/wood studies,base/mechanism/head/brass/slime components and Salis prepared; Flyer Levitator is actual paid bench output, no finished golem supplied");
    }
    private static int count(ServerPlayer p,Item item){int n=0;for(var stack:p.getInventory().items)if(stack.is(item))n+=stack.getCount();return n;}
    private static int find(ServerPlayer p,Item item){for(int i=0;i<p.getInventory().items.size();i++)if(p.getInventory().getItem(i).is(item))return i;return -1;}

    private static void prepare(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();p.setInvulnerable(true);p.connection.teleport(.5,112,-1.5,0,0);
        for(var pos:BlockPos.betweenClosed(-10,111,-12,12,124,10))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        // Native navigation's region is loaded explicitly by fixture setup, never by operational path code.
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.getChunk(x,z);
        p.getInventory().clearContent();for(String key:List.of("BASEARTIFICE","METALLURGY","BASEGOLEMANCY","WARDEDJARS","INFUSION","GOLEMCLIMBER","MINDCLOCKWORK","MATSTUDWOOD","SEALCOLLECT","SEALSTORE"))stage(p,key);
        p.inventoryMenu.broadcastChanges();ResearchNetwork.sync(p);
        LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_FIXTURE: owned isolated world; completed predecessor studies and arena only; four new entries,f_FLY and all finished new outputs absent");
    }
    private static void start(Minecraft mc){
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        started=true;previous=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(0x54433624L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static void finish(Minecraft mc){
        require(saved.get()==IMAGES.length,"Missing fifteen fresh late-golem captures");stopped=true;mc.options.tutorialStep=previous;
        LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_RENDER_AUDIT_OK: real native menus and synchronized original HungryChest,Levitator,BrainJar,infusion,press and paid Flyer models; no supplied finished outputs; screenshots={}",saved.get());
        LogUtils.getLogger().info("THAUMCRAFT_LATE_GOLEM_CLIENT_SMOKE_OK: 15 scenes; actual four research C2S,real scans,paid HungryChest/Levitator bench,75essentia JarBrain with liveXP capture/release,actual press Flyer and elevated seal flight/delivery; explicit predecessor/input/aura/altar fixtures; world={}",WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable e){if(stopped)return;stopped=true;if(previous!=null)mc.options.tutorialStep=previous;LogUtils.getLogger().error("THAUMCRAFT_LATE_GOLEM_CLIENT_SMOKE_FAILED",e);mc.stop();}

    private static final class Gallery extends Screen {
        private final int renderedScene;
        Gallery(){super(Component.literal("Thaumcraft 6 / "+IMAGES[scene]));renderedScene=scene;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics gui,int x,int y,float partial){
            var mc=Minecraft.getInstance();try{
                gui.fill(0,0,width,height,0xff18202b);gui.drawCenteredString(font,title,width/2,15,0xffefdbac);gui.flush();Lighting.setupFor3DItems();
                if(renderedScene==4){drawBlock(gui,LIFT,width/2,height/2+140,125,partial);var entity=mc.level.getEntity(liftedId);if(entity!=null){gui.pose().pushPose();gui.pose().translate(width/2,height/2+105,200);gui.pose().scale(60,-60,60);mc.getEntityRenderDispatcher().render(entity,0,Math.min(4,entity.getY()-LIFT.getY()),0,0,partial,gui.pose(),gui.bufferSource(),15728880);gui.flush();gui.pose().popPose();}}
                else if(renderedScene==5||renderedScene==6){gui.pose().pushPose();gui.pose().translate(width/2,height/2+105,200);gui.pose().scale(43,-43,43);gui.pose().mulPose(Axis.XP.rotationDegrees(24));gui.pose().mulPose(Axis.YP.rotationDegrees(145));for(BlockPos pos:altar){gui.pose().pushPose();gui.pose().translate(pos.getX()-MATRIX.getX(),pos.getY()-112,pos.getZ()-MATRIX.getZ());block(gui,pos,partial);gui.pose().popPose();}gui.flush();gui.pose().popPose();}
                else if(renderedScene==7||renderedScene==8){drawBlock(gui,JAR,width/2,height/2+95,210,partial);if(mc.level.getBlockEntity(JAR) instanceof BrainJarBlockEntity jar)gui.drawCenteredString(font,"Actual stored XP: "+jar.xp()+" / "+BrainJarBlockEntity.CAPACITY,width/2,height-65,0xffffff);}
                else if(renderedScene>=12){var golem=clientGolem(mc);if(golem!=null){gui.pose().pushPose();gui.pose().translate(width/2,height/2+105,200);gui.pose().scale(220,-220,220);gui.pose().mulPose(Axis.XP.rotationDegrees(18));gui.pose().mulPose(Axis.YP.rotationDegrees(150));mc.getEntityRenderDispatcher().render(golem,0,0,0,0,partial,gui.pose(),gui.bufferSource(),15728880);gui.flush();gui.pose().popPose();gui.drawCenteredString(font,"Paid original FLYER legs / actual y="+String.format(Locale.ROOT,"%.2f",golem.getY())+" / carried="+golem.getMainHandItem().getCount(),width/2,height-65,0xffffff);}}
                gui.flush();gui.drawCenteredString(font,"Actual integrated-server output / original TC6 models and native ticks",width/2,height-25,0xffd4d9df);
            }catch(Throwable e){fail(mc,e);}
        }
        private static void drawBlock(GuiGraphics gui,BlockPos pos,int x,int y,float scale,float partial){gui.pose().pushPose();gui.pose().translate(x,y,200);gui.pose().scale(scale,-scale,scale);gui.pose().mulPose(Axis.XP.rotationDegrees(24));gui.pose().mulPose(Axis.YP.rotationDegrees(145));gui.pose().translate(-.5,0,-.5);block(gui,pos,partial);gui.flush();gui.pose().popPose();}
        private static void block(GuiGraphics gui,BlockPos pos,float partial){var mc=Minecraft.getInstance();var state=mc.level.getBlockState(pos);require(!state.isAir(),"Missing real gallery block"+pos);if(state.getRenderShape()==RenderShape.MODEL)mc.getBlockRenderer().renderSingleBlock(state,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);BlockEntity tile=mc.level.getBlockEntity(pos);if(tile!=null&&mc.getBlockEntityRenderDispatcher().getRenderer(tile)!=null)require(!mc.getBlockEntityRenderDispatcher().renderItem(tile,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Missing live block entity renderer"+pos);}
    }
}
