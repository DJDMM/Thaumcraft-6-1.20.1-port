package thaumcraft.client.theory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** A world-free wrapper: the container screen's final tick requires a LocalPlayer. */
final class ResearchTablePreviewScreen extends Screen {
    final ResearchTableScreen contents;
    private int[] pointer;

    ResearchTablePreviewScreen(ResearchTableScreen contents) {
        super(Component.literal("Research table layout preview"));
        this.contents = contents;
    }

    @Override protected void init() { contents.init(minecraft, width, height); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        contents.render(graphics, pointer == null ? mouseX : pointer[0], pointer == null ? mouseY : pointer[1], partialTick);
    }
    void hoverForSmokeTest(boolean requiredItem) { pointer = contents.hoverForSmokeTest(requiredItem); }
    @Override public boolean mouseClicked(double x, double y, int button) { return contents.mouseClicked(x, y, button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return contents.mouseReleased(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return contents.mouseDragged(x, y, button, dx, dy); }
    @Override public void onClose() { minecraft.setScreen(new TitleScreen()); }
}
