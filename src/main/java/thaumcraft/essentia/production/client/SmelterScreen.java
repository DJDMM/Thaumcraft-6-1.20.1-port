package thaumcraft.essentia.production.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.essentia.production.SmelterMenu;

/** Original BETA26 176×166 texture, fuel flame, cooking strip and mixed-essentia gauge. */
public final class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_smelter.png");
    public SmelterScreen(SmelterMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 176; imageHeight = 166; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTicks); renderTooltip(graphics, mouseX, mouseY);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {} // Original GUI has no title labels.
    @Override protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int flame = menu.scaledBurn(20);
        if (flame > 0) graphics.blit(TEXTURE, leftPos+80, topPos+26+20-flame, 176, 20-flame, 16, flame);
        int cooked = menu.scaledCook(46);
        if (cooked > 0) graphics.blit(TEXTURE, leftPos+106, topPos+13+46-cooked, 216, 46-cooked, 9, cooked);
        int essentia = menu.scaledEssentia(48);
        if (essentia > 0) graphics.blit(TEXTURE, leftPos+61, topPos+12+48-essentia, 200, 48-essentia, 8, essentia);
        graphics.blit(TEXTURE, leftPos+60, topPos+8, 232, 0, 10, 55);
    }
}
