package thaumcraft.golemancy.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.golemancy.seals.core.SealRegistry;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GolemancyClientEvents {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{MenuScreens.register(SealRegistry.MENU.get(),SealScreen::new);MenuScreens.register(SealRegistry.LOGISTICS_MENU.get(),SealLogisticsScreen::new);});}
    private GolemancyClientEvents(){}
}
