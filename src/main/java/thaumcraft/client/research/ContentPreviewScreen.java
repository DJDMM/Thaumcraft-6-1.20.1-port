package thaumcraft.client.research;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

/** Opt-in resource smoke test, never opened during normal gameplay. */
final class ContentPreviewScreen extends Screen {
    private final int page;
    private final List<ItemStack> items;
    ContentPreviewScreen(int page) {
        super(Component.literal("Thaumcraft content preview"));
        this.page = page;
        items = ForgeRegistries.ITEMS.getEntries().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals("thaumcraft"))
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().location().getPath()))
                .map(entry -> new ItemStack(entry.getValue())).toList();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff151321);
        graphics.drawString(font, "Thaumcraft · resource preview · " + (page + 1), 16, 12, 0xe2d5a6);
        int columns = 6, cellWidth = (width - 32) / columns;
        for (int slot = 0; slot < 36 && page * 36 + slot < items.size(); slot++) {
            int x = 16 + slot % columns * cellWidth, y = 36 + slot / columns * 48;
            graphics.fill(x, y, x + cellWidth - 4, y + 44, 0xff252132);
            ItemStack stack = items.get(page * 36 + slot);
            graphics.renderItem(stack, x + cellWidth / 2 - 10, y + 3);
            String name = ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
            graphics.drawString(font, font.plainSubstrByWidth(name, cellWidth - 8), x + 3, y + 25, 0xdddddd);
        }
    }
}
