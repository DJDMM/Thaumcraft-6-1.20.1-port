package thaumcraft.auromancy.media.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.auromancy.media.FocusPlanArea;
import thaumcraft.auromancy.remaining.FocusBlockPicker;

/** Small read-only area/selected-block caster HUD, alongside the original G controls. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FocusCasterHud {
    private FocusCasterHud(){}
    @SubscribeEvent public static void register(RegisterGuiOverlaysEvent event){
        event.registerAboveAll("focus_architect",(gui,graphics,partial,width,height)->{
            var minecraft=Minecraft.getInstance();var player=minecraft.player;
            if(player==null||minecraft.screen!=null||minecraft.options.hideGui)return;
            var caster=FocusPlanArea.caster(player);var plan=FocusStacks.readPlan(FocusSelection.installed(caster));if(plan.isEmpty())return;
            int x=width-170,y=height-64;
            if(plan.get().graph().nodes().stream().anyMatch(node->node.key().equals("thaumcraft.PLAN"))){
                int rx=FocusPlanArea.radius(caster,"x"),ry=FocusPlanArea.radius(caster,"y"),rz=FocusPlanArea.radius(caster,"z");
                String axis=switch(FocusPlanArea.dimension(caster)){case 1->"X";case 2->"Z";case 3->"Y";default->"XYZ";};
                graphics.fill(x-4,y-4,width-6,y+27,0xA0181024);
                graphics.drawString(minecraft.font,Component.translatable("thaumcraft.focus.plan.area",rx*2+1,ry*2+1,rz*2+1),x,y,0xDDCCFF,true);
                graphics.drawString(minecraft.font,Component.translatable("thaumcraft.focus.plan.controls",axis),x,y+12,0xC4B4D5,true);y-=32;
            }
            if(FocusBlockPicker.isPicker(caster)){
                var picked=FocusBlockPicker.picked(caster);graphics.fill(x-4,y-4,width-6,y+21,0xA0181024);
                if(!picked.isEmpty()){graphics.renderItem(picked,x,y);graphics.drawString(minecraft.font,picked.getHoverName(),x+20,y+4,0xDDCCFF,true);}
                else graphics.drawString(minecraft.font,Component.translatable("thaumcraft.focus.exchange.pick"),x,y+4,0xDDCCFF,true);
            }
        });
    }
}
