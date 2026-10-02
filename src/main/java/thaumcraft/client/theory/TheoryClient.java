package thaumcraft.client.theory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.research.theory.ResearchTableMenu;
import thaumcraft.research.theory.TheoryModule;

@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TheoryClient {
    private TheoryClient() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(TheoryModule.MENU.get(), ResearchTableScreen::new));
    }

    /** Menu identifiers prevent late replies from affecting a subsequently opened table. */
    public static void receive(int menuId, CompoundTag state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof ResearchTableMenu menu
                && menu.containerId == menuId) menu.setState(state);
    }
}
