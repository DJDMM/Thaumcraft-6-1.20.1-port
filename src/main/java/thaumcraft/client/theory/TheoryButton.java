package thaumcraft.client.theory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The original BETA26 image button, with text fitted to the translated label. */
final class TheoryButton extends Button {
    private static final ResourceLocation BASE = new ResourceLocation("thaumcraft", "textures/gui/gui_base.png");

    TheoryButton(int x, int y, int width, int height, Component message, OnPress pressed) {
        super(x, y, width, height, message, pressed, DEFAULT_NARRATION);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.setColor(active ? 1 : 0.68F, active ? 1 : 0.68F, active ? 1 : 0.68F, 1);
        graphics.blit(BASE, getX(), getY(), getWidth(), getHeight(), 37F, 66F, 51, 13, 256, 256);
        graphics.setColor(1, 1, 1, 1);
        if (isHoveredOrFocused() && active) graphics.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, 0x22FFFFFF);
        var font = Minecraft.getInstance().font;
        float scale = Math.min(0.9F, (getWidth() - 8F) / Math.max(1, font.width(getMessage())));
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + getWidth() / 2F, getY() + (getHeight() - font.lineHeight * scale) / 2F, 1);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, getMessage(), -font.width(getMessage()) / 2, 0, active ? 0xF2E5C5 : 0xACA898, false);
        graphics.pose().popPose();
    }
}
