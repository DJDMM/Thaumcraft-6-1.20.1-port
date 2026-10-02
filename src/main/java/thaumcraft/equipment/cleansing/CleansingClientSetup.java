package thaumcraft.equipment.cleansing;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CleansingClientSetup {
    private CleansingClientSetup() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CleansingModule.PURE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(CleansingModule.FLOWING_PURE.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register(new DynamicFluidContainerModel.Colors(), CleansingModule.PURE_BUCKET.get());
    }
}
