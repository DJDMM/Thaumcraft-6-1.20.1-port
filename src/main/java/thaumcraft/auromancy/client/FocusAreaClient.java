package thaumcraft.auromancy.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import thaumcraft.auromancy.*;
/** Uses Minecraft's own input event, never global desktop input. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class FocusAreaClient {
    public static final KeyMapping AREA=new KeyMapping("key.thaumcraft.focus_area",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.thaumcraft");
    @Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class AreaKeys {
        @SubscribeEvent public static void registerArea(RegisterKeyMappingsEvent event){event.register(AREA);}
    }
    @SubscribeEvent public static void input(InputEvent.Key event){
        var mc=Minecraft.getInstance();
        if(event.getAction()!=GLFW.GLFW_PRESS||mc.player==null||mc.screen!=null||mc.isPaused()||!AREA.isActiveAndMatches(InputConstants.getKey(event.getKey(),event.getScanCode())))return;
        while(AREA.consumeClick()){}
        FocusAreaNetwork.request(Screen.hasControlDown()?1:0);
    }
}
