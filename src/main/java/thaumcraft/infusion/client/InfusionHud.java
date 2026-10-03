package thaumcraft.infusion.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.equipment.armor.GogglesArmorSupport;
import thaumcraft.infusion.*;
import java.text.DecimalFormat;

/** Read-only, tracked block-entity state; goggles expose the release's stability and remaining aspects. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class InfusionHud {
    private static final DecimalFormat VALUE = new DecimalFormat("#.##");
    private InfusionHud() {}
    @SubscribeEvent public static void register(RegisterGuiOverlaysEvent event) { event.registerAboveAll("infusion", (overlay, gui, partial, width, height) -> render(gui,width,height)); }
    private static void render(GuiGraphics gui, int width, int height) {
        var mc=Minecraft.getInstance(); if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui||!GogglesArmorSupport.hasGoggles(mc.player)
                || !(mc.hitResult instanceof BlockHitResult hit)) return;
        int x=width/2+12,y=height/2+12;
        if(mc.level.getBlockEntity(hit.getBlockPos()) instanceof InfusionMatrixBlockEntity matrix) {
            gui.drawString(mc.font,Component.translatable("stability."+matrix.stabilityName()),x,y,0xE0C8FF,true);
            gui.drawString(mc.font,VALUE.format(matrix.gain())+" "+Component.translatable("stability.gain").getString(),x,y+11,0xBBBBBB,true);
            if(matrix.instability()!=0)gui.drawString(mc.font,Component.translatable("stability.range").getString()+VALUE.format(matrix.instability()/ (matrix.stability()>12.5F?5F:matrix.stability()>=0?6F:matrix.stability()>-25?7F:8F))
                    +" "+Component.translatable("stability.loss").getString(),x,y+22,0xBBBBBB,true);
            var aspects=matrix.getAspects(); int column=0;
            for(var aspect:aspects.getAspects()) if(aspects.getAmount(aspect)>0) {
                int color=aspect.getColor(); gui.setColor(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,1);
                gui.blit(aspect.getImage(),x+column*24,y+37,0,0,16,16,16,16);gui.setColor(1,1,1,1);
                gui.drawString(mc.font,Integer.toString(aspects.getAmount(aspect)),x+column*24,y+54,0xFFFFFF,true);column++;
            }
        } else if(mc.level.getBlockEntity(hit.getBlockPos()) instanceof InfusionStabilizerBlockEntity stabilizer)
            gui.drawString(mc.font,stabilizer.energy()+" / 15",x,y,0xE0C8FF,true);
    }
}
