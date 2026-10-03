package thaumcraft.client.research;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.research.ResearchBookNotifications;

/** BETA26's 160x32 HUD artwork, icon, title placement and five-second display. */
public final class ResearchBookToast implements Toast {
    private static final ResourceLocation HUD = new ResourceLocation("thaumcraft", "textures/gui/hud.png");
    private final ResearchBookNotifications.Notice notice;
    public ResearchBookToast(ResearchBookNotifications.Notice notice) { this.notice = notice; }

    @Override public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        g.setColor(1, 1, 1, 1);
        g.blit(HUD, 0, 0, 0, 224, 160, 32);
        ResearchIconRenderer.draw(g, notice.entry(), 6, 8, 1f);
        var font = toasts.getMinecraft().font;
        String label = notice.kind() == ResearchBookNotifications.Kind.RESEARCH ? "research.complete" : "tc.research.newpage";
        Component heading = Component.translatable(label);
        float headingScale = Math.min(1f, 124f / Math.max(1, font.width(heading)));
        g.pose().pushPose();
        g.pose().translate(30, 7, 0);
        g.pose().scale(headingScale, headingScale, 1);
        g.drawString(font, heading, 0, 0, 10631665, false);
        g.pose().popPose();
        Component name = Component.translatable(notice.entry().title());
        float scale = Math.min(1f, 124f / Math.max(1, font.width(name)));
        g.pose().pushPose();
        g.pose().translate(30, 18, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, name, 0, 0, 16755465, false);
        g.pose().popPose();
        return time < 5000L * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override public Object getToken() { return notice.entry().key() + ":" + notice.kind(); }
}
