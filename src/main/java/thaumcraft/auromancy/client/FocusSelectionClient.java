package thaumcraft.auromancy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.FocusSelectionNetwork;

@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class FocusSelectionClient {
    public static final KeyMapping CHANGE = new KeyMapping("key.thaumcraft.focus_change", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F, "key.categories.thaumcraft");
    private FocusSelectionClient() {}
    @Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(CHANGE); }
    }
    @SubscribeEvent public static void key(InputEvent.Key event) { input(InputConstants.getKey(event.getKey(),event.getScanCode()),event.getAction()); }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Post event) { input(InputConstants.Type.MOUSE.getOrCreate(event.getButton()),event.getAction()); }
    private static void input(InputConstants.Key key,int action) {
        if (action!=GLFW.GLFW_PRESS && action!=GLFW.GLFW_REPEAT) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null || mc.screen!=null || mc.isPaused() || mc.player.isSpectator() || !mc.player.isAlive()
                || !CHANGE.isActiveAndMatches(key)) return;
        var hand=FocusSelection.casterHand(mc.player);if(hand==null)return;
        if (CHANGE.getKey().equals(mc.options.keySwapOffhand.getKey())) while(mc.options.keySwapOffhand.consumeClick()){}
        boolean press=CHANGE.consumeClick();while(CHANGE.consumeClick()){}
        if (!press || action!=GLFW.GLFW_PRESS) return;
        if (mc.player.isShiftKeyDown()) FocusSelectionNetwork.request(hand,-1,mc.player.getItemInHand(hand),ItemStack.EMPTY);
        else mc.setScreen(new FocusSelectionScreen(hand));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END)while(CHANGE.consumeClick()){}
    }
}
