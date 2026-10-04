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
    private static final String[] SCENES={"empty","editor","configured","crafting","ritual","complete","selection","casting",
            "projectile-editor","projectile-crafting","projectile-flight","frost-hit","frost-water","air-hit","earth-break",
            "bolt-heal-editor","bolt-heal-crafting","bolt-heal-result","flux-hit","break-progress","break-harvest","split-editor","split-crafting","split-complete","curse-hit","exchange-pick","exchange-swap","rift-passage","cloud-pulse","mine-armed","spellbat-flight","scatter-projectiles","plan-preview"};
    private static final BlockPos TABLE=new BlockPos(0,112,0);
    private static final AtomicInteger saved=new AtomicInteger();
    private static final AtomicInteger beamSaved=new AtomicInteger();
    private static CompletableFuture<Void> work;
    private static TutorialSteps previousTutorial;
    private static boolean started,setup,prepared,captureRequested,captured,stopped,impactReady;
    private static int scene,phase,stableTicks,cowId;
    private static long began;
    private static float craftAura,castAura;
    private static long boltReceived,boltRendered;
    private static boolean beamCaptured;
    private static volatile boolean legacyReady;
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
            // A transient projectile can disappear after Screenshot.grab finishes. Advance
            // before reevaluating that scene's live-entity predicate on the following tick.
            if(captured&&saved.get()>scene){scene++;phase=0;stableTicks=0;prepared=false;captured=false;captureRequested=false;}
            if(scene==SCENES.length){finish(mc);return;}
            if(!(mc.level.getBlockEntity(TABLE) instanceof FocalManipulatorBlockEntity table))return;
            if(scene==0){
                if(!prepared&&mc.player.position().distanceToSqr(new Vec3(.5,112,3.5))>.25)return;
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
            }else if(scene==8){
                if(!prepared){prepared=true;mc.setScreen(null);submit(mc,()->{
                    var p=player(mc);var level=p.serverLevel();
                    p.getInventory().setItem(9,CatalogModule.stack("focus_1"));
                    p.getInventory().setItem(10,AspectCrystalItem.create(Aspect.MOTION,2));p.getInventory().setItem(11,AspectCrystalItem.create(Aspect.COLD,2));p.experienceLevel=10;
                    grant(p,"FOCUSELEMENTAL");grant(p,"FOCUSPROJECTILE");p.inventoryMenu.broadcastChanges();
                    AuraManager.drainVis(level,TABLE,Float.MAX_VALUE,false);AuraManager.addVis(level,TABLE,200);craftAura=200;
                    var chunk=level.getChunkAt(TABLE);chunk.addAndRegisterBlockEntity(new thaumcraft.catalog.blocks.CatalogBlockEntity(TABLE,level.getBlockState(TABLE)));
                    thaumcraft.auromancy.table.LegacyFocalMigration.load(new net.minecraftforge.event.level.ChunkEvent.Load(chunk,false));
                });return;}
                if(phase==0){if(!legacyReady){submit(mc,()->{var be=player(mc).serverLevel().getBlockEntity(TABLE);legacyReady=be instanceof FocalManipulatorBlockEntity migrated&&migrated.isEmpty()&&!migrated.crafting();});return;}
                    phase=1;submit(mc,()->require(serverTable(mc).isEmpty()&&!serverTable(mc).crafting(),"Legacy catalogue table did not migrate empty"));
                    mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(TABLE.getCenter(),Direction.NORTH,TABLE,false));return;}
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(phase==1){if(!screen.getMenu().canUse())return;phase=2;mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,1,0,ClickType.PICKUP,mc.player);mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.PICKUP,mc.player);return;}
                if(phase==2){if(screen.getMenu().pending()||screen.getMenu().focus().isEmpty()||screen.getMenu().graph().nodes().isEmpty())return;
                    phase=3;screen.selectForSmoke(1,FocusNodeRegistry.PROJECTILE);screen.selectForSmoke(2,FocusNodeRegistry.FROST);screen.setNameForSmoke("TC6 Projectile Frost");return;}
                var compiled=FocusCompiler.compile(screen.getMenu().graph(),screen.getMenu().focus(),screen.getMenu().knowledge()::isResearchCompleteStrict);
                if(screen.getMenu().pending()||!compiled.success()||!compiled.plan().effect().key().equals(FocusNodeRegistry.FROST))return;
                require(compiled.plan().complexity()==8&&compiled.plan().craftVis()==83,"Wrong Projectile Frost editor cost");
            }else if(scene==9){
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(!prepared){prepared=true;screen.getMenu().start();return;}
                if(!screen.getMenu().busy()||screen.getMenu().remainingVis()>=83)return;
            }else if(scene==10){
                if(table.crafting()||FocusStacks.readPlan(table.getItem(0)).isEmpty()&&phase==0)return;
                if(phase==0){if(!(mc.screen instanceof FocalManipulatorScreen screen))return;phase=1;
                    submit(mc,()->{var p=player(mc);require(p.experienceLevel==7&&p.getInventory().getItem(10).getCount()==1&&p.getInventory().getItem(11).getCount()==1,"Projectile table materials/XP");require(Math.abs(craftAura-AuraManager.getVis(p.serverLevel(),TABLE)-83)<.01,"Projectile table aura");});
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.QUICK_MOVE,mc.player);return;}
                if(phase==1){int slot=focusSlot(mc.player,FocusNodeRegistry.FROST);if(slot<0)return;phase=2;mc.setScreen(null);FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(FocusStacks.readPlan(FocusSelection.installed(mc.player.getMainHandItem())).map(p->!p.effect().key().equals(FocusNodeRegistry.FROST)).orElse(true))return;
                if(phase==2){phase=3;submit(mc,()->{var p=player(mc);var cow=(Cow)p.serverLevel().getEntity(cowId);cow.clearFire();cow.removeAllEffects();cow.setHealth(100);cow.setPos(.5,112,-2.5);cow.setDeltaMovement(Vec3.ZERO);
                    for(int z=-5;z<=4;z++)p.serverLevel().setBlockAndUpdate(new BlockPos(0,111,z),Blocks.STONE.defaultBlockState());
                    p.connection.teleport(.5,112,3.5,180,0);castAura=AuraManager.getVis(p.serverLevel(),TABLE);});return;}
                if(phase==3){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;phase=4;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                var moving=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                        .filter(e->e instanceof thaumcraft.auromancy.projectile.FocusProjectileEntity)
                        .map(e->(thaumcraft.auromancy.projectile.FocusProjectileEntity)e).findFirst();
                if(moving.isEmpty())return;
                var projectile=moving.get();require(projectile.effectKey().equals(FocusNodeRegistry.FROST)
                        && projectile.color()==FocusStacks.readPlan(FocusSelection.installed(mc.player.getMainHandItem())).orElseThrow().color()
                        && projectile.ownerEntityId()==mc.player.getId()&&projectile.special()==0,"Projectile spawn/synced appearance differs from paid plan");
                require(mc.getEntityRenderDispatcher().getRenderer(projectile) instanceof thaumcraft.auromancy.projectile.FocusProjectileRenderer,"Wrong projectile renderer");
                if(phase==4){phase=5;LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_PROJECTILE_RENDER_AUDIT_OK: native spawn packet, owner, option, Frost color/effect and original mote renderer");}
            }else if(scene==11){
                if(!impactReady){submit(mc,()->{var p=player(mc);var cow=(Cow)p.serverLevel().getEntity(cowId);impactReady=cow.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                    if(impactReady){require(cow.getHealth()==96&&cow.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==1,"Projectile Frost impact damage/slowness");require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),TABLE)-1.6)<.01,"Projectile impact paid twice");require(p.experienceLevel==7,"Projectile casting spent XP");LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_PROJECTILE_FLOW: actual table83vis/3XP/motus+gelum -> physical installation -> moving native entity ->4damage/SlownessII -> single1.6vis; legacy table repaired");}});return;}
            }else if(scene==12){
                if(!prepared){prepared=true;submit(mc,()->{var p=player(mc);var level=p.serverLevel();var water=new BlockPos(4,112,4);
                    // Contained source pool and a real camera footing keep this visual fixture above the ice.
                    for(BlockPos pos:BlockPos.betweenClosed(2,111,2,6,111,6))level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
                    for(BlockPos pos:BlockPos.betweenClosed(2,112,2,6,112,6))if(pos.getX()==2||pos.getX()==6||pos.getZ()==2||pos.getZ()==6)level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
                    for(BlockPos pos:BlockPos.betweenClosed(3,112,3,5,112,5)){level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState());}
                    FocusEffects.apply(level,p,new FocusGraph.Node(1,0,List.of(),0,1,FocusNodeRegistry.FROST,Map.of("power",1,"duration",2)),new BlockHitResult(water.getCenter().add(0,.5,0),Direction.UP,water,false),new Vec3(0,-1,0));
                    require(level.getBlockState(water).is(Blocks.FROSTED_ICE),"Frost did not freeze source water");level.setBlockAndUpdate(new BlockPos(8,114,8),Blocks.STONE.defaultBlockState());p.setDeltaMovement(Vec3.ZERO);p.connection.teleport(8.5,115,8.5,135,32);});return;}
            }else if(scene==13){
                if(!prepared){prepared=true;submit(mc,()->prepareEffect(mc,FocusNodeRegistry.AIR,5));return;}
                if(!installedEffect(mc,FocusNodeRegistry.AIR)){int slot=focusSlot(mc.player,FocusNodeRegistry.AIR);if(slot>=0)FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(phase==0){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);var cow=(Cow)p.serverLevel().getEntity(cowId);require(cow.getHealth()==94&&cow.getDeltaMovement().horizontalDistanceSqr()>0,"Air damage/knockback");require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),TABLE)-2.4)<.01,"Air cast cost");});return;}
            }else if(scene==14){
                if(!prepared){prepared=true;submit(mc,()->{prepareEffect(mc,FocusNodeRegistry.EARTH,5);var p=player(mc);p.serverLevel().getEntity(cowId).setPos(20,112,20);p.serverLevel().setBlockAndUpdate(new BlockPos(0,113,0),Blocks.GLASS.defaultBlockState());});return;}
                if(!installedEffect(mc,FocusNodeRegistry.EARTH)){int slot=focusSlot(mc.player,FocusNodeRegistry.EARTH);if(slot>=0)FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(phase==0){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(!mc.level.isEmptyBlock(new BlockPos(0,113,0)))return;
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);require(p.serverLevel().isEmptyBlock(new BlockPos(0,113,0)),"Earth client-only break");require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),TABLE)-3.5)<.01,"Earth cast+target extra .1vis");LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_ELEMENTAL_FLOW: source water frosted/scheduled; actual Air cast6damage/knockback/2.4vis; actual Earth cast glass harvest/3.4+.1vis; Air/Earth completed focus inputs are explicit gifted QA fixtures");});return;}
            }else if(scene==15){
                if(phase==0){phase=1;submit(mc,()->{
                    var p=player(mc);var level=p.serverLevel();grant(p,"FOCUSBOLT");grant(p,"FOCUSHEAL");
                    serverTable(mc).setItem(0,ItemStack.EMPTY);p.getInventory().clearContent();p.getInventory().selected=0;
                    p.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("caster_basic"));
                    p.getInventory().setItem(9,CatalogModule.stack("focus_1"));
                    p.getInventory().setItem(10,AspectCrystalItem.create(Aspect.ENERGY,2));p.getInventory().setItem(11,AspectCrystalItem.create(Aspect.LIFE,2));
                    p.experienceLevel=10;AuraManager.drainVis(level,TABLE,Float.MAX_VALUE,false);AuraManager.addVis(level,TABLE,300);craftAura=300;
                    level.setBlockAndUpdate(new BlockPos(0,113,0),Blocks.AIR.defaultBlockState());
                    var cow=(Cow)level.getEntity(cowId);cow.clearFire();cow.removeAllEffects();cow.invulnerableTime=0;cow.setHealth(60);cow.setPos(.5,112,-8.5);cow.setDeltaMovement(Vec3.ZERO);
                    level.setBlockAndUpdate(new BlockPos(0,111,-9),Blocks.STONE.defaultBlockState());
                    p.connection.teleport(.5,112,3.5,180,0);p.inventoryMenu.broadcastChanges();});mc.setScreen(null);return;}
                if(phase==1){phase=2;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(TABLE.getCenter(),Direction.NORTH,TABLE,false));return;}
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(phase==2){phase=3;mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,1,0,ClickType.PICKUP,mc.player);
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.PICKUP,mc.player);return;}
                if(phase==3){if(screen.getMenu().pending()||screen.getMenu().focus().isEmpty()||screen.getMenu().graph().nodes().isEmpty())return;
                    phase=4;screen.selectForSmoke(1,FocusNodeRegistry.BOLT);screen.selectForSmoke(2,FocusNodeRegistry.HEAL);screen.setNameForSmoke("TC6 Bolt Heal");return;}
                var compiled=FocusCompiler.compile(screen.getMenu().graph(),screen.getMenu().focus(),screen.getMenu().knowledge()::isResearchCompleteStrict);
                if(screen.getMenu().pending()||!compiled.success()||!compiled.plan().effect().key().equals(FocusNodeRegistry.HEAL))return;
                require(compiled.plan().complexity()==9&&compiled.plan().craftVis()==93,"Wrong Bolt Heal paid editor plan");
            }else if(scene==16){
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(!prepared){prepared=true;screen.getMenu().start();return;}
                if(!screen.getMenu().busy()||screen.getMenu().remainingVis()>=93)return;
            }else if(scene==17){
                if(phase==0){if(table.crafting()||FocusStacks.readPlan(table.getItem(0)).isEmpty())return;
                    if(!(mc.screen instanceof FocalManipulatorScreen screen))return;phase=1;
                    submit(mc,()->{var p=player(mc);require(p.experienceLevel==7&&p.getInventory().getItem(10).getCount()==1&&p.getInventory().getItem(11).getCount()==1,"Bolt Heal table XP/crystals");
                        require(Math.abs(craftAura-AuraManager.getVis(p.serverLevel(),TABLE)-93)<.01,"Bolt Heal total table aura");castAura=AuraManager.getVis(p.serverLevel(),TABLE);});
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.QUICK_MOVE,mc.player);return;}
                if(phase==1){int slot=focusSlot(mc.player,FocusNodeRegistry.HEAL);if(slot<0)return;phase=2;mc.setScreen(null);
                    FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(!installedEffect(mc,FocusNodeRegistry.HEAL))return;
                if(phase==2){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;
                    boltReceived=FocusBoltClient.receivedCount();boltRendered=FocusBoltClient.renderedCount();phase=3;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(phase==3){phase=4;submit(mc,()->{var p=player(mc);var cow=(Cow)p.serverLevel().getEntity(cowId);
                    require(cow.getHealth()==61,"Real Bolt Heal failed beyond Touch reach");require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),TABLE)-1.8)<.01,"Bolt Heal single cast debit");});return;}
                if(FocusBoltClient.receivedCount()<=boltReceived||FocusBoltClient.renderedCount()<=boltRendered||beamSaved.get()!=1)return;
            }else if(scene==18){
                if(!prepared){prepared=true;submit(mc,()->prepareEffect(mc,FocusNodeRegistry.FLUX,3));return;}
                if(!installedEffect(mc,FocusNodeRegistry.FLUX)){int slot=focusSlot(mc.player,FocusNodeRegistry.FLUX);if(slot>=0)FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(phase==0){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);require(((Cow)p.serverLevel().getEntity(cowId)).getHealth()==94,"Flux damage6");require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),TABLE)-2.2)<.01,"Flux single debit2.2");});return;}
            }else if(scene==19){
                if(!prepared){prepared=true;submit(mc,()->{
                    prepareEffect(mc,FocusNodeRegistry.BREAK,1);var p=player(mc);var blank=CatalogModule.stack("focus_1");
                    var graph=ElementalFocusGraphGameTests.graph(FocusNodeRegistry.TOUCH,FocusNodeRegistry.BREAK,Map.of(),Map.of("power",1,"silk",1,"fortune",0));
                    p.getInventory().setItem(9,FocusStacks.apply(blank,FocusCompiler.compile(graph,blank,k->true).plan(),"TC6 Break Silk"));p.inventoryMenu.broadcastChanges();
                    p.serverLevel().getEntity(cowId).setPos(20,112,20);p.serverLevel().setBlockAndUpdate(new BlockPos(0,113,0),Blocks.STONE.defaultBlockState());});return;}
                if(!installedEffect(mc,FocusNodeRegistry.BREAK)){int slot=focusSlot(mc.player,FocusNodeRegistry.BREAK);if(slot>=0)FocusSelectionNetwork.request(InteractionHand.MAIN_HAND,slot,mc.player.getMainHandItem(),mc.player.getInventory().getItem(slot));return;}
                if(phase==0){if(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-180))>1)return;phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);require(p.serverLevel().getBlockState(new BlockPos(0,113,0)).is(Blocks.STONE),"Break skipped original delay");
                    require(Math.abs(castAura-AuraManager.getVis(p.serverLevel(),TABLE)-1.8)<.01,"Break target debit occurred before harvest");});return;}
            }else if(scene==20){
                if(!mc.level.isEmptyBlock(new BlockPos(0,113,0)))return;
                if(!prepared){prepared=true;submit(mc,()->{var p=player(mc);var level=p.serverLevel();var pos=new BlockPos(0,113,0);
                    require(level.isEmptyBlock(pos),"Break client-only block removal");require(Math.abs(castAura-AuraManager.getVis(level,TABLE)-2.3)<.01,"Break cast1.8+Silk target0.5vis");
                    require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(.75)).stream().anyMatch(drop->drop.getItem().is(Items.STONE)),"Break Silk did not produce stone");
                    LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_FOUR_FLOW: real Bolt Heal table93vis/3XP/Potentia+Victus and physical install/use packet heal1 at12blocks/cast1.8vis; client received/rendered original Bolt; gifted Flux cast6damage/2.2vis; gifted delayed Break Silk real stone loot/1.8+.5vis");});return;}
            }
            if(scene==21){
                if(!prepared){prepared=true;submit(mc,()->{
                    var p=player(mc);grant(p,"FOCUSSPLIT");grant(p,"FOCUSFLUX");
                    serverTable(mc).setItem(0,ItemStack.EMPTY);p.getInventory().setItem(9,CatalogModule.stack("focus_2"));
                    p.getInventory().setItem(10,AspectCrystalItem.create(Aspect.AVERSION,2));p.getInventory().setItem(11,AspectCrystalItem.create(Aspect.FIRE,2));p.getInventory().setItem(12,AspectCrystalItem.create(Aspect.FLUX,2));p.experienceLevel=20;
                    AuraManager.addVis(p.serverLevel(),TABLE,250);p.connection.teleport(.5,112,3.5,180,0);p.inventoryMenu.broadcastChanges();});mc.setScreen(null);return;}
                if(!(mc.screen instanceof FocalManipulatorScreen screen)){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(TABLE.getCenter(),Direction.NORTH,TABLE,false));return;}
                if(phase==0){phase=1;mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,1,0,ClickType.PICKUP,mc.player);mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,ClickType.PICKUP,mc.player);return;}
                if(screen.getMenu().pending()||screen.getMenu().focus().isEmpty()||screen.getMenu().graph().nodes().isEmpty())return;
                if(phase==1){phase=2;screen.selectForSmoke(1,FocusNodeRegistry.TOUCH);screen.selectForSmoke(2,FocusNodeRegistry.SPLITTARGET);screen.selectForSmoke(3,FocusNodeRegistry.FIRE);screen.selectForSmoke(4,FocusNodeRegistry.FLUX);screen.setNameForSmoke("TC6 Split Fire + Flux");return;}
                var compiled=FocusCompiler.compile(screen.getMenu().graph(),screen.getMenu().focus(),screen.getMenu().knowledge()::isResearchCompleteStrict);
                if(!compiled.success())return;require(compiled.plan().effects().size()==2&&compiled.plan().complexity()==11,"Editor lost sibling branch or costs");
            }else if(scene==22){
                if(!(mc.screen instanceof FocalManipulatorScreen screen))return;
                if(!prepared){prepared=true;screen.getMenu().start();return;}if(!screen.getMenu().busy())return;
            }else if(scene==23){
                if(table.crafting()||FocusStacks.readPlan(table.getItem(0)).isEmpty())return;
                if(!prepared){prepared=true;submit(mc,()->{var plan=FocusStacks.readPlan(serverTable(mc).getItem(0)).orElseThrow();require(plan.effects().size()==2&&plan.maxComplexity()==25,"Table lost completed Advanced branched package");});return;}
            }else if(scene==24){
                if(!prepared){prepared=true;mc.setScreen(null);submit(mc,()->setupNewSpell(mc,ElementalFocusGraphGameTests.graph(FocusNodeRegistry.TOUCH,FocusNodeRegistry.CURSE,Map.of(),Map.of())));return;}
                if(!installedEffect(mc,FocusNodeRegistry.CURSE))return;
                if(phase==0){phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(phase==1){phase=2;submit(mc,()->{var cow=(Cow)player(mc).serverLevel().getEntity(cowId);require(cow.hasEffect(net.minecraft.world.effect.MobEffects.POISON)&&cow.getHealth()<=98,"Curse missing actual damage/poison");});return;}
            }else if(scene==25){
                if(!prepared){prepared=true;submit(mc,()->{setupNewSpell(mc,ElementalFocusGraphGameTests.graph(FocusNodeRegistry.TOUCH,FocusNodeRegistry.EXCHANGE,Map.of(),Map.of()));var p=player(mc);p.serverLevel().getEntity(cowId).setPos(20,112,20);p.serverLevel().setBlockAndUpdate(new BlockPos(0,113,0),Blocks.STONE.defaultBlockState());p.setShiftKeyDown(true);p.getInventory().setItem(14,new ItemStack(Items.STONE,8));p.inventoryMenu.broadcastChanges();});return;}
                if(!installedEffect(mc,FocusNodeRegistry.EXCHANGE))return;
                if(phase==0){phase=1;mc.player.setShiftKeyDown(true);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,113.5,1),Direction.SOUTH,new BlockPos(0,113,0),false));return;}
                if(thaumcraft.auromancy.remaining.FocusBlockPicker.picked(mc.player.getMainHandItem()).isEmpty())return;
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);require(thaumcraft.auromancy.remaining.FocusBlockPicker.picked(p.getMainHandItem()).is(Items.STONE),"Server did not store original picked block");p.setShiftKeyDown(false);p.serverLevel().setBlockAndUpdate(new BlockPos(0,113,0),Blocks.DIRT.defaultBlockState());});mc.player.setShiftKeyDown(false);return;}
            }else if(scene==26){
                if(!prepared){prepared=true;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(!mc.level.getBlockState(new BlockPos(0,113,0)).is(Blocks.STONE))return;
                if(phase==0){phase=1;submit(mc,()->{var p=player(mc);require(p.serverLevel().getBlockState(new BlockPos(0,113,0)).is(Blocks.STONE)&&p.getInventory().getItem(14).getCount()==7,"Exchange did not pay real inventory block");});return;}
            }else if(scene==27){
                if(!prepared){prepared=true;submit(mc,()->{setupNewSpell(mc,ElementalFocusGraphGameTests.graph(FocusNodeRegistry.TOUCH,FocusNodeRegistry.RIFT,Map.of(),Map.of("depth",8,"duration",10)));var p=player(mc);p.serverLevel().getEntity(cowId).setPos(20,112,20);for(BlockPos pos:BlockPos.betweenClosed(-1,112,-3,1,114,0))if(!pos.equals(TABLE))p.serverLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());});return;}
                if(!installedEffect(mc,FocusNodeRegistry.RIFT))return;
                if(phase==0){phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(!(mc.level.getBlockEntity(new BlockPos(0,113,0)) instanceof thaumcraft.auromancy.remaining.RiftHoleBlockEntity))return;
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);require(p.serverLevel().getBlockEntity(new BlockPos(0,113,0)) instanceof thaumcraft.auromancy.remaining.RiftHoleBlockEntity,"Rift passage client-only");});return;}
            }else if(scene==28||scene==29||scene==30||scene==31||scene==32){
                String medium=switch(scene){case 28->FocusNodeRegistry.CLOUD;case 29->FocusNodeRegistry.MINE;case 30->FocusNodeRegistry.SPELLBAT;case 31->FocusNodeRegistry.SCATTER;default->FocusNodeRegistry.PLAN;};
                if(!prepared){prepared=true;submit(mc,()->{
                    var p=player(mc);p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
                    // Media may hurt their own caster in BETA26; this isolated renderer fixture uses creative invulnerability.
                    for(BlockPos pos:BlockPos.betweenClosed(-1,112,-3,1,114,0))if(!pos.equals(TABLE))p.serverLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                    FocusGraph graph;
                    if(scene==31)graph=new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),new FocusGraph.Node(1,0,List.of(2),0,1,FocusNodeRegistry.SCATTER,Map.of("forks",4,"cone",30)),new FocusGraph.Node(2,1,List.of(3),0,2,FocusNodeRegistry.PROJECTILE,Map.of("speed",1,"option",0)),new FocusGraph.Node(3,2,List.of(),0,3,FocusNodeRegistry.FLUX,Map.of())));
                    else if(scene==32)graph=ElementalFocusGraphGameTests.graph(medium,FocusNodeRegistry.BREAK,Map.of("method",1),Map.of());
                    else graph=ElementalFocusGraphGameTests.graph(medium,FocusNodeRegistry.FLUX,Map.of(),Map.of());
                    setupNewSpell(mc,graph);var cow=(Cow)p.serverLevel().getEntity(cowId);
                    if(scene==28){cow.setPos(.5,113,2.5);p.getInventory().setChanged();}
                    if(scene==29||scene==30||scene==31)cow.setPos(20,112,20);
                    if(scene==32){cow.setPos(20,112,20);for(BlockPos pos:BlockPos.betweenClosed(-1,113,0,1,114,0))p.serverLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
                });return;}
                var clientPlan=FocusStacks.readPlan(FocusSelection.installed(mc.player.getMainHandItem()));
                if(clientPlan.isEmpty()||clientPlan.get().graph().nodes().stream().noneMatch(n->n.key().equals(medium)))return;
                if(scene==32){if(phase==0){phase=1;FocusAreaNetwork.request(0);return;}
                    if(phase==1){if(++stableTicks<10)return;phase=2;submit(mc,()->{var p=player(mc);require(thaumcraft.auromancy.media.FocusPlanArea.radius(p.getMainHandItem(),"x")==0,"Plan G packet did not update the authoritative physical caster");LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_PLAN_CYCLE: server radii0, client awaiting original caster NBT");});return;}
                    if(thaumcraft.auromancy.media.FocusPlanArea.radius(mc.player.getMainHandItem(),"x")!=0)return;}
                else if(phase==0){phase=1;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);return;}
                if(scene==28&&mc.level.getEntitiesOfClass(thaumcraft.auromancy.media.FocusCloudEntity.class,mc.player.getBoundingBox().inflate(8)).isEmpty())return;
                if(scene==29&&mc.level.getEntitiesOfClass(thaumcraft.auromancy.media.FocusMineEntity.class,mc.player.getBoundingBox().inflate(8)).stream().noneMatch(thaumcraft.auromancy.media.FocusMineEntity::armed))return;
                if(scene==30&&mc.level.getEntitiesOfClass(thaumcraft.auromancy.media.SpellBatEntity.class,mc.player.getBoundingBox().inflate(8)).isEmpty())return;
                if(scene==31&&mc.level.getEntitiesOfClass(thaumcraft.auromancy.projectile.FocusProjectileEntity.class,mc.player.getBoundingBox().inflate(8)).size()<4)return;
                if((scene==29||scene==30)&&phase>=2&&mc.player.getZ()<7)return;
                if(phase==1){phase=2;submit(mc,()->{var p=player(mc);var plan=FocusStacks.readPlan(FocusSelection.installed(p.getMainHandItem())).orElseThrow();
                    if(scene!=32){float debit=castAura-AuraManager.getVis(p.serverLevel(),TABLE);require(Math.abs(debit-plan.castVis())<.01,"Medium "+medium+" debit="+debit+" expected="+plan.castVis()+" before="+castAura+" after="+AuraManager.getVis(p.serverLevel(),TABLE));}
                    LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_REMAINING_SCENE: {} actual paid packet / cloned tree / server power",medium);
                    if(scene==29||scene==30){
                        // Stand behind the real cast so the target/previous spell cannot hide its model.
                        p.serverLevel().setBlockAndUpdate(new BlockPos(0,111,7),Blocks.STONE.defaultBlockState());
                        p.connection.teleport(.5,112,7.5,180,scene==29?18:0);
                    }
                });return;}
            }
            if(!captureRequested && !captured && ++stableTicks>=(scene==10||scene==31?1:scene==19?6:12))captureRequested=true;
        }catch(Throwable error){fail(mc,error);}
    }
    private static int focusSlot(net.minecraft.world.entity.player.Player player){for(int i=0;i<36;i++)if(FocusStacks.readPlan(player.getInventory().getItem(i)).isPresent())return i;return -1;}
    private static int focusSlot(net.minecraft.world.entity.player.Player player,String effect){for(int i=0;i<36;i++)if(FocusStacks.readPlan(player.getInventory().getItem(i)).map(p->p.effect().key().equals(effect)).orElse(false))return i;return -1;}
    private static boolean installedEffect(Minecraft mc,String key){return FocusStacks.readPlan(FocusSelection.installed(mc.player.getMainHandItem())).map(p->p.effect().key().equals(key)).orElse(false);}
    private static void grant(ServerPlayer p,String key){try{var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),key,ResearchCatalog.get(key).stages().size()+1);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void prepareEffect(Minecraft mc,String key,int power){
        var p=player(mc);var level=p.serverLevel();var blank=CatalogModule.stack(key.equals(FocusNodeRegistry.EARTH)?"focus_2":"focus_1");
        var graph=new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),
                new FocusGraph.Node(1,0,List.of(2),0,1,FocusNodeRegistry.TOUCH,Map.of()),
                new FocusGraph.Node(2,1,List.of(),0,2,key,Map.of("power",power))));
        // Gift fixtures isolate each runtime effect; paid table crafting is exercised above and by server tests.
        p.getInventory().setItem(9,FocusStacks.apply(blank,FocusCompiler.compile(graph,blank,k->true).plan(),"TC6 "+key));p.inventoryMenu.broadcastChanges();
        var cow=(Cow)level.getEntity(cowId);cow.clearFire();cow.removeAllEffects();cow.invulnerableTime=0;cow.setHealth(100);cow.setPos(.5,112,.5);cow.setDeltaMovement(Vec3.ZERO);
        p.connection.teleport(.5,112,3.5,180,0);castAura=AuraManager.getVis(level,TABLE);
    }
    private static void setupNewSpell(Minecraft mc,FocusGraph graph){
        var p=player(mc);var level=p.serverLevel();var blank=CatalogModule.stack("focus_3");var compiled=FocusCompiler.compile(graph,blank,k->true);require(compiled.success(),"New fixture "+compiled.error());
        serverTable(mc).setItem(0,ItemStack.EMPTY);
        for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,p.getBoundingBox().inflate(32)))
            if(e instanceof thaumcraft.auromancy.media.FocusCloudEntity||e instanceof thaumcraft.auromancy.media.FocusMineEntity
                    ||e instanceof thaumcraft.auromancy.media.SpellBatEntity||e instanceof thaumcraft.auromancy.projectile.FocusProjectileEntity)e.discard();
        var wand=CatalogModule.stack("caster_basic");FocusSelection.setInstalled(wand,FocusStacks.apply(blank,compiled.plan(),"TC6 "+graph.nodes().get(1).key()));p.setItemInHand(InteractionHand.MAIN_HAND,wand);
        var cow=(Cow)level.getEntity(cowId);cow.clearFire();cow.removeAllEffects();cow.invulnerableTime=0;cow.setHealth(100);cow.setPos(.5,112,.5);cow.setDeltaMovement(Vec3.ZERO);
        p.connection.teleport(.5,112,3.5,180,0);p.setShiftKeyDown(false);p.inventoryMenu.broadcastChanges();
        // Keep the owned payment fixture below the lunar over-cap pollution threshold:
        // successive gifted spells must not accumulate 100 vis each while a mine arms.
        AuraManager.drainVis(level,TABLE,Float.MAX_VALUE,false);AuraManager.addVis(level,TABLE,100);castAura=100;
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.auromancySmokeTest") || stopped)return;
        if(scene==17&&phase>=3&&!beamCaptured&&FocusBoltClient.renderedCount()>boltRendered){
            beamCaptured=true;var mc=Minecraft.getInstance();try{
                String name="tc6-auromancy-bolt-beam.png";File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{if(file.isFile()&&file.length()>0){beamSaved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_AUROMANCY_BOLT_IMAGE: {}",file.getAbsolutePath());}else mc.execute(()->fail(mc,new AssertionError("Missing transient Bolt screenshot")));});
            }catch(Throwable error){fail(mc,error);}return;
        }
        if(!captureRequested||captured)return;
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
        p.connection.teleport(.5,112,3.5,180,0);p.inventoryMenu.broadcastChanges();
        Cow cow=EntityType.COW.create(level);cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);cow.setPos(.5,112,.5);level.addFreshEntity(cow);cowId=cow.getId();
    }
    private static void audit(Minecraft mc){
        require(mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/essentia.png")).isPresent(),"Missing original Bolt beam texture");
        var block=CatalogBlocks.block("wand_workbench");for(var state:block.getStateDefinition().getPossibleStates()){
            var model=mc.getBlockRenderer().getBlockModel(state);require(model!=mc.getModelManager().getMissingModel(),"Missing focal block model");
            for(var quad:model.getQuads(state,null,net.minecraft.util.RandomSource.create(7)))require(!quad.getSprite().contents().name().getPath().equals("missingno"),"Missing focal sprite");
        }
        FocalManipulatorBlockEntity tile=(FocalManipulatorBlockEntity)mc.level.getBlockEntity(TABLE);
        require(mc.getBlockEntityRenderDispatcher().getRenderer(tile)!=null,"Missing operational focal BER");
        tile.setItem(0,CatalogModule.stack("focus_1"));require(tile.isEmpty(),"Client changed table inventory");
        for(String id:List.of("focus_1","focus_2","focus_3","caster_basic"))require(mc.getItemRenderer().getModel(CatalogModule.stack(id),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Missing focus/caster model");
        for(var type:List.of(thaumcraft.auromancy.media.FocusMediaModule.CLOUD.get(),thaumcraft.auromancy.media.FocusMediaModule.MINE.get(),thaumcraft.auromancy.media.FocusMediaModule.SPELL_BAT.get()))require(mc.getEntityRenderDispatcher().getRenderer(type.create(mc.level))!=null,"Missing working spell entity renderer");
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
