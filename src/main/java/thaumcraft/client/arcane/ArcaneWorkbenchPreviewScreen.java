package thaumcraft.client.arcane;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Passive development screenshot wrapper. AbstractContainerScreen.tick() is final and
 * dereferences LocalPlayer, so a title-screen preview must never tick the real menu screen.
 * init/render are the same paths as in game; interaction and networking are not exercised.
 */
public final class ArcaneWorkbenchPreviewScreen extends Screen {
    private final ArcaneWorkbenchScreen contents;
    ArcaneWorkbenchPreviewScreen(ArcaneWorkbenchScreen contents) {
        super(Component.literal("Arcane workbench layout preview"));
        this.contents = contents;
    }
    @Override protected void init() { contents.init(minecraft, width, height); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        contents.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(new TitleScreen()); }
}
