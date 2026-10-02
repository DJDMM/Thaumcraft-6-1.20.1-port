package thaumcraft.equipment.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.equipment.items.SanityCheckerItem;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.scanning.ThaumometerItem;

/** Original HudHandler sanity meter UVs, colours and 100-warp normalization. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class SanityHud {
    private static final ResourceLocation HUD=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/hud.png");
    private static LocalPlayer owner;
    private static int permanent,normal,temporary;
    private SanityHud() {}
    public static void receive(PlayerKnowledge knowledge) {
        var player=Minecraft.getInstance().player;
        if(player==null) return;
        owner=player;permanent=knowledge.permanentWarp();normal=knowledge.normalWarp();temporary=knowledge.temporaryWarp();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { owner=null;permanent=normal=temporary=0; }
    public static int thaumometerOffset() {
        var player=Minecraft.getInstance().player;
        return player!=null && player.getMainHandItem().getItem() instanceof SanityCheckerItem ? 75 : 0;
    }
    private static void render(GuiGraphics graphics) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null || owner!=player || mc.level==null || mc.options.hideGui || mc.screen!=null || mc.isPaused()
                || !player.isAlive() || player.isSpectator()) return;
        boolean main=player.getMainHandItem().getItem() instanceof SanityCheckerItem;
        if(!main && !(player.getOffhandItem().getItem() instanceof SanityCheckerItem)) return;
        int offset=!main && player.getMainHandItem().getItem() instanceof ThaumometerItem ? 80 : 0;
        graphics.pose().pushPose();graphics.pose().translate(0,offset,0);
        graphics.blit(HUD,1,1,152,0,20,76);
        float total=permanent+normal+temporary,mod=total>100 ? 100F/total : 1;
        int gap=(int)((100-Math.min(100,total))/100F*48),temp=(int)(temporary/100F*48*mod),norm=(int)(normal/100F*48*mod);
        if(temporary>0) { graphics.setColor(1,.5F,1,1);graphics.blit(HUD,7,21+gap,200,gap,8,temp+gap); }
        if(normal>0) { graphics.setColor(.75F,0,.75F,1);graphics.blit(HUD,7,21+temp+gap,200,temp+gap,8,temp+norm+gap); }
        if(permanent>0) { graphics.setColor(.5F,0,.5F,1);graphics.blit(HUD,7,21+temp+norm+gap,200,temp+norm+gap,8,48); }
        graphics.setColor(1,1,1,1);graphics.blit(HUD,1,1,176,0,20,76);
        if(total>=100) {
            graphics.pose().pushPose();graphics.pose().scale(.75F,.75F,1);
            graphics.pose().translate(player.getRandom().nextInt(2),player.getRandom().nextInt(2),0);
            graphics.blit(HUD,3,3,216,0,20,16);graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }
    @Mod.EventBusSubscriber(modid="thaumcraft",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent event) { event.registerAboveAll("sanity_meter",(gui,graphics,partial,width,height)->render(graphics)); }
    }
}
