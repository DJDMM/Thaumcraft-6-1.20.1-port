package thaumcraft.golemancy.press.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.golemancy.press.*;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GolemPressClientEvents {
    private GolemPressClientEvents() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {event.enqueueWork(()->MenuScreens.register(GolemPressRegistry.PRESS_MENU.get(),GolemPressScreen::new));}
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers event) {event.registerBlockEntityRenderer(GolemPressRegistry.PRESS_BE.get(),GolemPressRenderer::new);}
    public static void snapshot(GolemPressNetwork.Snapshot packet) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof GolemPressMenu menu&&menu.containerId==packet.menu())menu.acceptSnapshot(packet.revision(),packet.design(),packet.owns());
    }
}
