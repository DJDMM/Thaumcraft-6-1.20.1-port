package thaumcraft.client.arcane;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchMenu;
import thaumcraft.world.WorldModule;

public final class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
    public ArcaneWorkbenchScreen(ArcaneWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 230; imageHeight = 225; inventoryLabelX = 35; inventoryLabelY = 131; titleLabelX = 12; titleLabelY = 10;
    }

    /** Development-only layout preview: no world, player, server or network interaction. */
    public static net.minecraft.client.gui.screens.Screen createSmokePreview() {
        if (!Boolean.getBoolean("thaumcraft.clientSmokeTest")) throw new IllegalStateException("Smoke preview is disabled");
        Inventory inventory = new Inventory(null);
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ArcaneWorkbenchMenu menu;
        try {
            buffer.writeBlockPos(net.minecraft.core.BlockPos.ZERO);
            menu = new ArcaneWorkbenchMenu(0, inventory, buffer);
        } finally { buffer.release(); }
        for (int slot : new int[]{2, 4, 6, 8}) menu.getSlot(slot).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 4));
        menu.getSlot(5).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_PANE, 4));
        for (int i = 0; i < 6; i++) {
            menu.getSlot(10 + i).set(new net.minecraft.world.item.ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get(), 8));
            menu.setData(4 + i, 1);
        }
        menu.getSlot(0).set(new net.minecraft.world.item.ItemStack(thaumcraft.scanning.ScanningModule.THAUMOMETER.get()));
        menu.setData(0, 132); menu.setData(1, 20); menu.setData(2, 1); menu.setData(3, 1);
        inventory.setItem(0, new net.minecraft.world.item.ItemStack(thaumcraft.alchemy.AlchemyModule.SALIS_MUNDUS.get(), 3));
        inventory.setItem(1, new net.minecraft.world.item.ItemStack(ArcaneModule.WORKBENCH_ITEM.get()));
        return new ArcaneWorkbenchPreviewScreen(new ArcaneWorkbenchScreen(menu, inventory, Component.translatable("container.thaumcraft.arcane_workbench")));
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF3B293B);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFE2D5AF);
        graphics.fill(x + 5, y + 26, x + imageWidth - 5, y + 128, 0xFFCABE9A);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF6D5D48);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFFAC9C7A);
            graphics.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0xFFBCAE8C);
        }
        int arrow = menu.craftable() ? 0xFF795A9B : 0xFF978C78;
        graphics.fill(x + 114, y + 58, x + 153, y + 64, arrow);
        graphics.fill(x + 145, y + 53, x + 150, y + 69, arrow);
        graphics.fill(x + 150, y + 56, x + 155, y + 66, arrow);
        for (int i = 0; i < 6; i++) {
            int color = 0xFF000000 | WorldModule.CRYSTAL_COLORS.get(ArcaneModule.PRIMALS[i]);
            graphics.fill(x + 44 + i * 18, y + 104, x + 60 + i * 18, y + 106, color);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x342E38, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x514535, false);
        Component vis = menu.hasRecipe() ? Component.translatable("gui.thaumcraft.arcane.vis", menu.requiredVis(), menu.availableVis())
                : Component.translatable("gui.thaumcraft.arcane.aura", menu.availableVis());
        graphics.drawString(font, vis, 35, 88, menu.hasRecipe() && menu.availableVis() < menu.requiredVis() ? 0x9B342D : 0x4D3B65, false);
        graphics.drawString(font, Component.translatable("gui.thaumcraft.arcane.crystals"), 35, 96, 0x514535, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        for (int i = 0; i < 6; i++) {
            Slot slot = menu.slots.get(10 + i);
            if (!slot.hasItem() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                Component name = WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get().getDescription();
                graphics.renderTooltip(font, Component.translatable("gui.thaumcraft.arcane.crystal_cost", name, menu.crystalCost(i)), mouseX, mouseY);
            }
        }
        if (menu.hasRecipe() && !menu.craftable() && isHovering(174, 53, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.thaumcraft.arcane.missing"), mouseX, mouseY);
        }
    }
}
