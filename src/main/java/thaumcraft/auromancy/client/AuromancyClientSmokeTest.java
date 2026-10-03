package thaumcraft.auromancy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.table.*;
import thaumcraft.auromancy.table.client.FocalManipulatorScreen;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.*;
import thaumcraft.world.aura.AuraManager;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual menu clicks, editor C2S, natural table debit, physical installation and server-owned fire target. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class AuromancyClientSmokeTest {
    private static final String WORLD="thaumcraft-auromancy-smoke-"+System.currentTimeMillis();
    private static final String[] SCENES={"empty","editor","configured","crafting","ritual","complete","selection","casting"};
    private static final BlockPos TABLE=new BlockPos(0,112,0);
    private static final AtomicInteger saved=new AtomicInteger();
    private static CompletableFuture<Void> work;
    private static TutorialSteps previousTutorial;
    private static boolean started,setup,prepared,captureRequested,captured,stopped;
    private static int scene,phase,stableTicks,cowId;
    private static long began;
    private static float craftAura,castAura;
    private AuromancyClientSmokeTest(){}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static ServerPlayer player(Minecraft mc){var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());require(p!=null,"Missing QA player");return p;}
    private static FocalManipulatorBlockEntity serverTable(Minecraft mc){return (FocalManipulatorBlockEntity)player(mc).serverLevel().getBlockEntity(TABLE);}
    private static void submit(Minecraft mc,Runnable action){require(work==null,"Overlapping QA server work");var future=new CompletableFuture<Void>();work=future;mc.getSingleplayerServer().execute(()->{try{action.run();future.complete(null);}catch(Throwable failure){future.completeExceptionally(failure);}});}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.auromancySmokeTest") || stopped)return;
        Minecraft mc=Minecraft.getInstance();if(began==0)began=System.nanoTime();
        try{
            require(System.nanoTime()-began<420_000_000_000L,"Auromancy timeout scene="+scene+" phase="+phase);
            if(!started){startWorld(mc);return;}if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
            require(mc.getSingleplayerServer()!=null && mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD),"Wrong QA world");
            mc.getToasts().clear();if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!setup){setup=true;submit(mc,()->prepare(mc));return;}
            if(scene==SCENES.length){finish(mc);return;}
            if(!(mc.level.getBlockEntity(TABLE) instanceof FocalManipulatorBlockEntity table))return;
            if(scene==0){
                if(!prepared){prepared=true;audit(mc);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(TABLE.getCenter(),Direction.NORTH,TABLE,false));return;}
                if(!(mc.screen instanceof FocalManipulatorScreen screen) || !screen.getMenu().canUse())return;
                require(screen.getMenu().focus().isEmpty(),"Empty scene contains focus");
            }else if(scene==1){
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(!prepared){prepared=true;
                    // Original left-column inventory slot: main slot9 is menu index1.
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,1,0,ClickType.PICKUP,mc.player);
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.PICKUP,mc.player);return;}
                if(screen.getMenu().focus().isEmpty() || screen.getMenu().pending() || screen.getMenu().graph().nodes().isEmpty())return;
            }else if(scene==2){
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(!prepared){prepared=true;screen.selectForSmoke(1,FocusNodeRegistry.TOUCH);screen.selectForSmoke(2,FocusNodeRegistry.FIRE);screen.setNameForSmoke("TC6 Touch Fire");return;}
                if(screen.getMenu().pending() || !FocusCompiler.compile(screen.getMenu().graph(),screen.getMenu().focus(),screen.getMenu().knowledge()::isResearchCompleteStrict).success())return;
                require(FocusStacks.readPlan(screen.getMenu().focus()).isEmpty(),"Editing manufactured spell");
            }else if(scene==3){
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(!prepared){prepared=true;screen.getMenu().start();return;}
                if(!screen.getMenu().busy() || screen.getMenu().remainingVis()>=43)return;
                if(phase==0){phase=1;submit(mc,()->{var p=player(mc);require(p.experienceLevel==8,"Table XP should debit2");require(p.getInventory().getItem(10).getCount()==1 && p.getInventory().getItem(11).getCount()==1,"Table crystal debit should be1+1");require(serverTable(mc).crafting(),"Animated without server plan");});return;}
            }else if(scene==4){
                if(!prepared){prepared=true;mc.setScreen(new Gallery());return;}
                if(!table.crafting())return;
            }else if(scene==5){
                if(table.crafting() || FocusStacks.readPlan(table.getItem(0)).isEmpty())return;
                if(!prepared){prepared=true;submit(mc,()->{var p=player(mc);require(!serverTable(mc).crafting() && FocusStacks.readPlan(serverTable(mc).getItem(0)).isPresent(),"Server did not write completed package");
                    // Regeneration is disabled only in this owned QA world by fixed zero-moon date.
                    require(Math.abs(craftAura-AuraManager.getVis(p.serverLevel(),TABLE)-43)<.01,"Wrong table total aura debit");
                    p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("caster_basic"));p.inventoryMenu.broadcastChanges();});mc.setScreen(new Gallery());return;}
            }else if(scene==6){
                if(!prepared){prepared=true;mc.setScreen(null);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(TABLE.getCenter(),Direction.NORTH,TABLE,false));return;}
                if(phase==0){if(!(mc.screen instanceof FocalManipulatorScreen screen))return;phase=1;
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.QUICK_MOVE,mc.player);return;}
                if(phase==1){int slot=focusSlot(mc.player);if(slot<0)return;phase=2;mc.setScreen(new FocusSelectionScreen(InteractionHand.MAIN_HAND));return;}
                if(!(mc.screen instanceof FocusSelectionScreen))return;
            }else if(scene==7){
                if(!prepared){prepared=true;int slot=focusSlot(mc.player);require(slot>=0,"No completed focus returned by menu");
                    FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(FocusSelection.installed(mc.player.getMainHandItem()).isEmpty())return;
                if(phase==0){phase=1;mc.setScreen(null);submit(mc,()->{var p=player(mc);p.connection.teleport(.5,112,3.5,180,0);castAura=AuraManager.getVis(p.serverLevel(),p.blockPosition());});return;}
                if(phase==1){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;phase=2;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(phase==2){phase=3;submit(mc,()->{var p=player(mc);var cow=(Cow)p.serverLevel().getEntity(cowId);require(cow!=null && cow.getHealth()==96,"Actual spell did not hurt server cow by4");
                    require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),p.blockPosition())-.8)<.01,"Actual spell did not pay .8 aura");
                    require(p.experienceLevel==8,"Casting took XP");LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_REAL_FLOW: actual menu insert/edit/start ->2XP+2crystals+43vis -> completed focus -> physical selection packet ->.8vis+4fire damage");});return;}
            }
            if(!captureRequested && !captured && ++stableTicks>=12)captureRequested=true;
            if(captured && saved.get()>scene){scene++;phase=0;stableTicks=0;prepared=false;captured=false;captureRequested=false;}
        }catch(Throwable error){fail(mc,error);}
    }
    private static int focusSlot(net.minecraft.world.entity.player.Player player){for(int i=0;i<36;i++)if(FocusStacks.readPlan(player.getInventory().getItem(i)).isPresent())return i;return -1;}
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.auromancySmokeTest") || stopped || !captureRequested || captured)return;
        var mc=Minecraft.getInstance();try{captured=true;captureRequested=false;String name="tc6-auromancy-"+SCENES[scene]+".png";
            File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{if(file.isFile() && file.length()>0){saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_SMOKE_IMAGE: {}",file.getAbsolutePath());}else mc.execute(()->fail(mc,new AssertionError("Missing screenshot")));});
        }catch(Throwable error){fail(mc,error);}
    }
    private static void startWorld(Minecraft mc){
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null)return;
        started=true;previousTutorial=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();mc.options.pauseOnLostFocus=false;
        mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(0x54433616L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static void prepare(Minecraft mc){
        var p=player(mc);var level=p.serverLevel();p.setInvulnerable(true);level.setDayTime(4*24000L+12000L);
        for(BlockPos pos:BlockPos.betweenClosed(-4,111,-4,4,116,4))level.setBlockAndUpdate(pos,pos.getY()==111?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(TABLE,CatalogBlocks.block("wand_workbench").defaultBlockState());
        p.getInventory().clearContent();p.getInventory().setItem(9,CatalogModule.stack("focus_1"));
        p.getInventory().setItem(10,AspectCrystalItem.create(Aspect.AVERSION,2));p.getInventory().setItem(11,AspectCrystalItem.create(Aspect.FIRE,2));p.experienceLevel=10;
        // Fixture isolates device behavior. Canonical progression is exercised separately in server tests.
        try{var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),"BASEAUROMANCY",ResearchCatalog.get("BASEAUROMANCY").stages().size()+1);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        AuraManager.drainVis(level,TABLE,Float.MAX_VALUE,false);AuraManager.addVis(level,TABLE,80);craftAura=80;
        // Exact payment fixture: only the owned centre chunk participates in aura diffusion.
        // This does not change production APIs or algorithms; phase4 has zero lunar regeneration.
        try{var field=thaumcraft.world.aura.AuraSavedData.class.getDeclaredField("activeChunks");field.setAccessible(true);
            @SuppressWarnings("unchecked") var active=(Set<Long>)field.get(thaumcraft.world.aura.AuraSavedData.get(level));
            active.clear();active.add(new ChunkPos(TABLE).toLong());
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        p.teleportTo(.5,112,3.5);p.setYRot(180);p.setXRot(0);p.inventoryMenu.broadcastChanges();
        Cow cow=EntityType.COW.create(level);cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);cow.setPos(.5,112,.5);level.addFreshEntity(cow);cowId=cow.getId();
    }
    private static void audit(Minecraft mc){
        var block=CatalogBlocks.block("wand_workbench");for(var state:block.getStateDefinition().getPossibleStates()){
            var model=mc.getBlockRenderer().getBlockModel(state);require(model!=mc.getModelManager().getMissingModel(),"Missing focal block model");
            for(var quad:model.getQuads(state,null,net.minecraft.util.RandomSource.create(7)))require(!quad.getSprite().contents().name().getPath().equals("missingno"),"Missing focal sprite");
        }
        FocalManipulatorBlockEntity tile=(FocalManipulatorBlockEntity)mc.level.getBlockEntity(TABLE);
        require(mc.getBlockEntityRenderDispatcher().getRenderer(tile)!=null,"Missing operational focal BER");
        tile.setItem(0,CatalogModule.stack("focus_1"));require(tile.isEmpty(),"Client changed table inventory");
        for(String id:List.of("focus_1","focus_2","focus_3","caster_basic"))require(mc.getItemRenderer().getModel(CatalogModule.stack(id),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Missing focus/caster model");
        LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_RENDER_AUDIT_OK: original focal block/BER/GUI textures and three focus tiers; client inventory guard");
    }
    private static void finish(Minecraft mc){require(saved.get()==SCENES.length,"Incomplete screenshots");stopped=true;mc.options.tutorialStep=previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_CLIENT_SMOKE_OK: {} scenes; real table/selection/casting packets and natural payments; world={}",saved.get(),WORLD);mc.stop();}
    private static void fail(Minecraft mc,Throwable error){if(stopped)return;stopped=true;if(previousTutorial!=null)mc.options.tutorialStep=previousTutorial;LogUtils.getLogger().error("THAUMCRAFT_AUROMANCY_CLIENT_SMOKE_FAILED",error);mc.stop();}
    private static final class Gallery extends Screen{
        Gallery(){super(Component.literal("TC6 Focal Manipulator"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics gui,int mx,int my,float partial){
            try{var mc=Minecraft.getInstance();gui.fill(0,0,width,height,0xFF17202B);gui.drawCenteredString(font,"TC6 BETA26 / "+SCENES[scene],width/2,20,0xE8D4AB);gui.flush();gui.pose().pushPose();
                try{gui.pose().translate(width/2.,height/2.+65,200);gui.pose().scale(130,-130,130);gui.pose().mulPose(Axis.XP.rotationDegrees(25));gui.pose().mulPose(Axis.YP.rotationDegrees(135));Lighting.setupFor3DItems();gui.pose().translate(-.5,-.5,-.5);
                    var tile=mc.level.getBlockEntity(TABLE);RenderSystem.runAsFancy(()->{mc.getBlockRenderer().renderSingleBlock(mc.level.getBlockState(TABLE),gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);require(!mc.getBlockEntityRenderDispatcher().renderItem(tile,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"Missing focal renderer");});
                }finally{gui.flush();gui.pose().popPose();Lighting.setupFor3DItems();}
                var table=(FocalManipulatorBlockEntity)mc.level.getBlockEntity(TABLE);gui.drawCenteredString(font,"crafting="+table.crafting()+" remainingVis="+table.remainingVis()+" completed="+FocusStacks.readPlan(table.getItem(0)).isPresent(),width/2,height-35,0xCDD0DE);
            }catch(Throwable error){fail(Minecraft.getInstance(),error);}
        }
    }
}
