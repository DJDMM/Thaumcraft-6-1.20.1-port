package thaumcraft.auromancy.table.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.auromancy.table.*;

@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FocalManipulatorClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(FocalManipulatorModule.MENU.get(), FocalManipulatorScreen::new));
    }
    public static void receive(int id, CompoundTag state) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof FocalManipulatorMenu menu && menu.containerId == id) menu.setState(state);
    }
}
