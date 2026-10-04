package thaumcraft.essentia.thaumatorium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.essentia.thaumatorium.*;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ThaumatoriumClientEvents {
    private ThaumatoriumClientEvents() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {event.enqueueWork(()->MenuScreens.register(ThaumatoriumModule.MENU.get(),ThaumatoriumScreen::new));}
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers event) {event.registerBlockEntityRenderer(ThaumatoriumModule.BASE.get(),ThaumatoriumRenderer::new);}
    public static void snapshot(ThaumatoriumNetwork.Snapshot packet) {
        var player=Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof ThaumatoriumMenu menu&&menu.containerId==packet.menu())menu.acceptSnapshot(packet.revision(),packet.capacity(),packet.recipes(),packet.stored());
    }
}
