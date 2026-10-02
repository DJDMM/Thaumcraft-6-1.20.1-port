package thaumcraft.essentia.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.world.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real server placement -> client packet state -> BER audit, in a fresh owned world. */
@Mod.EventBusSubscriber(modid="thaumcraft", value=Dist.CLIENT)
public final class EssentiaClientSmokeTest {
    private static final String WORLD="thaumcraft-essentia-smoke-"+System.currentTimeMillis();
    private record Case(String name,boolean voidJar,Aspect aspect,int amount,Aspect filter,Direction face,boolean brace) {}
    private static final List<Case> CASES=List.of(
            new Case("empty",false,null,0,null,Direction.NORTH,false),
            new Case("aer 1/250",false,Aspect.AIR,1,null,Direction.NORTH,false),
            new Case("aer 62/250",false,Aspect.AIR,62,null,Direction.NORTH,false),
            new Case("aer 125/250",false,Aspect.AIR,125,null,Direction.NORTH,false),
            new Case("ignis 188/250",false,Aspect.FIRE,188,null,Direction.NORTH,false),
            new Case("ignis 250/250",false,Aspect.FIRE,250,null,Direction.NORTH,false),
            new Case("void aqua 250/250",true,Aspect.WATER,250,null,Direction.NORTH,false),
            new Case("void empty / terra filter",true,null,0,Aspect.EARTH,Direction.NORTH,false),
            new Case("north / ignis 100",false,Aspect.FIRE,100,Aspect.FIRE,Direction.NORTH,false),
            new Case("east / aqua 100",false,Aspect.WATER,100,Aspect.WATER,Direction.EAST,false),
            new Case("south / terra 100",false,Aspect.EARTH,100,Aspect.EARTH,Direction.SOUTH,false),
            new Case("west / aer 100",false,Aspect.AIR,100,Aspect.AIR,Direction.WEST,false),
            new Case("brace / aer 250",false,Aspect.AIR,250,Aspect.AIR,Direction.NORTH,true),
            new Case("void brace / ignis 250",true,Aspect.FIRE,250,Aspect.FIRE,Direction.EAST,true),
            new Case("empty labelled / aqua",false,null,0,Aspect.WATER,Direction.SOUTH,false),
            new Case("brace / terra 1",false,Aspect.EARTH,1,Aspect.EARTH,Direction.WEST,true));
    private static final List<BlockPos> positions=new ArrayList<>();
    private static final AtomicInteger saved=new AtomicInteger();
    private static boolean started,placed,ready,stopped,captured;
    private static int ticks,page;
    private static long start;
    private static TutorialSteps tutorial;
    private static void require(boolean value,String why) { if(!value)throw new AssertionError(why); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.essentiaSmokeTest") || stopped)return;
        Minecraft mc=Minecraft.getInstance();if(start==0)start=System.nanoTime();
        try {
            require(System.nanoTime()-start<300_000_000_000L,"Essentia client audit timed out");
            if(!started) {
                if(mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;
                }
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                started=true;tutorial=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;
                mc.getTutorial().stop();mc.getToasts().clear();mc.options.pauseOnLostFocus=false;
                mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
                GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                var settings=new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_SMOKE_WORLD: {}",WORLD);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(0x544336L,false,false),WorldPresets::createNormalWorldDimensions);return;
            }
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            mc.getToasts().clear();
            require(mc.getSingleplayerServer()!=null&&mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD),"Wrong essentia audit world");
            if(!placed) {
                placed=true;BlockPos base=mc.player.blockPosition().offset(-4,3,-4);
                for(int i=0;i<CASES.size();i++)positions.add(base.offset(i%4,0,i/4));
                mc.getSingleplayerServer().execute(() -> {
                    var level=mc.getSingleplayerServer().overworld();
                    for(int i=0;i<CASES.size();i++) {
                        Case c=CASES.get(i);BlockPos pos=positions.get(i);
                        var state=CatalogBlocks.block(c.voidJar?"jar_void":"jar_normal").defaultBlockState();
                        level.setBlockAndUpdate(pos,state);
                        var jar=(EssentiaJarBlockEntity)level.getBlockEntity(pos);CompoundTag tag=new CompoundTag();
                        if(c.aspect!=null)tag.putString("Aspect",c.aspect.getTag());
                        if(c.filter!=null)tag.putString("AspectFilter",c.filter.getTag());
                        tag.putShort("Amount",(short)c.amount);tag.putByte("facing",(byte)c.face.get3DDataValue());tag.putBoolean("blocked",c.brace);
                        jar.load(tag);jar.setChanged();level.sendBlockUpdated(pos,state,state,3);
                    }
                });return;
            }
            if(!ready) {
                for(int i=0;i<CASES.size();i++) {
                    if(!(mc.level.getBlockEntity(positions.get(i)) instanceof EssentiaJarBlockEntity jar))return;
                    Case c=CASES.get(i);
                    if(jar.amount()!=c.amount||jar.aspect()!=c.aspect||jar.filter()!=c.filter||jar.facing()!=c.face||jar.blocked()!=c.brace)return;
                }
                var jar=(EssentiaJarBlockEntity)mc.level.getBlockEntity(positions.get(8));
                CompoundTag before=jar.saveWithoutMetadata();
                require(!jar.addExact(Aspect.FIRE,10)&&!jar.take(Aspect.FIRE,10)&&!jar.purge()&&!jar.removeLabel()&&!jar.installBrace(),"Client mutated a jar");
                mc.player.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("phial_empty"));
                var held=mc.player.getMainHandItem();int count=held.getCount();
                var context=new UseOnContext(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(jar.getBlockPos()),Direction.NORTH,jar.getBlockPos(),false));
                held.getItem().onItemUseFirst(held,context);
                require(count==held.getCount()&&before.equals(jar.saveWithoutMetadata()),"Client phial changed NBT or inventory");
                mc.player.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("label_blank"));
                held=mc.player.getMainHandItem();held.getItem().onItemUseFirst(held,context);
                require(held.getCount()==1&&before.equals(jar.saveWithoutMetadata()),"Client label changed NBT or inventory");
                ready=true;ticks=0;mc.setScreen(new Gallery());
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_SMOKE_SYNC: {} placed jars; exact client NBT/filters/facing/braces; client mutations rejected",CASES.size());
            }
            if(++ticks==20&&!captured) {
                captured=true;String name="tc6-essentia-"+(page==0?"levels":"labels-braces")+".png";
                File file=new File(new File(mc.gameDirectory,"screenshots"),name);Files.deleteIfExists(file.toPath());
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> {
                    if(file.isFile()&&file.length()>0)saved.incrementAndGet();
                    else mc.execute(() -> fail(mc,new AssertionError("Missing screenshot "+name)));
                });
            }
            if(ticks<40||saved.get()<page+1)return;
            if(++page==2) {
                stopped=true;mc.options.tutorialStep=tutorial;
                LogUtils.getLogger().info("THAUMCRAFT_ESSENTIA_CLIENT_SMOKE_OK: 16 synchronized world jars; 2 screenshots; client storage and item mutation checks passed; isolated world={}",WORLD);
                mc.getConnection().getConnection().disconnect(Component.literal("Essentia audit complete"));mc.clearLevel(new TitleScreen());mc.stop();return;
            }
            ticks=0;captured=false;
        } catch(Exception|AssertionError failure) { fail(mc,failure); }
    }
    private static void fail(Minecraft mc,Throwable failure) {
        if(stopped)return;stopped=true;if(tutorial!=null)mc.options.tutorialStep=tutorial;
        LogUtils.getLogger().error("THAUMCRAFT_ESSENTIA_CLIENT_SMOKE_FAILED",failure);mc.stop();
    }
    private static final class Gallery extends Screen {
        Gallery() { super(Component.literal("TC6 BETA26 — Essentia jars / live server state")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial) {
            Minecraft mc=Minecraft.getInstance();
            try {
                gui.fill(0,0,width,height,0xff18202b);gui.drawCenteredString(font,title,width/2,12,0xffefdbac);
                for(int i=0;i<8;i++) {
                    int index=page*8+i,px=i%4*(width/4)+6,py=42+i/4*210,cw=width/4-12;
                    Case c=CASES.get(index);var jar=(EssentiaJarBlockEntity)mc.level.getBlockEntity(positions.get(index));
                    gui.fill(px,py,px+cw,py+202,0xff293340);gui.flush();
                    gui.pose().pushPose();
                    try {
                        gui.pose().translate(px+cw/2,py+96,150);gui.pose().scale(115,-115,115);
                        gui.pose().mulPose(Axis.XP.rotationDegrees(25));
                        float orient=switch(c.face) {case EAST -> 90;case SOUTH -> 180;case WEST -> -90;default -> 0;};
                        gui.pose().mulPose(Axis.YP.rotationDegrees(145+orient));gui.pose().translate(-.5,-.4,-.5);
                        Lighting.setupFor3DItems();
                        RenderSystem.runAsFancy(() -> {
                            mc.getBlockRenderer().renderSingleBlock(jar.getBlockState(),gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);
                            require(!mc.getBlockEntityRenderDispatcher().renderItem(jar,gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),"No live jar renderer");
                        });
                    } finally { gui.flush();gui.pose().popPose();Lighting.setupFor3DItems(); }
                    gui.drawCenteredString(font,c.name,px+cw/2,py+173,0xffd4d9df);
                    gui.drawCenteredString(font,"suction="+jar.getSuctionAmount(Direction.UP)+" / min="+jar.getMinimumSuction(),px+cw/2,py+187,0xffa7afb8);
                }
            } catch(RuntimeException|AssertionError failure) { fail(mc,failure); }
        }
    }
}
