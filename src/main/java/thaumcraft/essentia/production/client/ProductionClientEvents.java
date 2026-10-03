package thaumcraft.essentia.production.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.essentia.production.EssentiaProductionModule;

@Mod.EventBusSubscriber(modid="thaumcraft", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ProductionClientEvents {
    private ProductionClientEvents() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> MenuScreens.register(EssentiaProductionModule.SMELTER_MENU.get(), SmelterScreen::new)); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(EssentiaProductionModule.ALEMBIC.get(), AlembicRenderer::new); }
}
