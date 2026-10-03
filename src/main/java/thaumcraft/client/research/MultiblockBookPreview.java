package thaumcraft.client.research;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.catalog.blocks.CatalogBlock;
import thaumcraft.catalog.blocks.client.CatalogBlockRenderer;
import thaumcraft.research.book.MultiblockCatalog;
import thaumcraft.research.book.MultiblockPreview;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Interactive, read-only construction insert. Coordinates and events use the book's local space. */
public final class MultiblockBookPreview {
    public static final int PAGE_HEIGHT = 172;
    private static final int LIGHT = 15728880;
    private static final int INK = 0x382818, FADED = 0x77553B;
    private final MultiblockCatalog.Blueprint blueprint;
    private final CatalogBlockRenderer special = new CatalogBlockRenderer(null);
    private final List<Control> controls = new ArrayList<>();
    private final List<MaterialHover> materialHovers = new ArrayList<>();
    private MultiblockPreview preview;
    private float yaw = -45, pitch = 25;
    private int removedTopLayers;
    private Rect viewport;
    private boolean dragging;

    public MultiblockBookPreview(MultiblockCatalog.Blueprint blueprint) {
        this.blueprint = blueprint; preview = blueprint.preview(0);
    }
    public MultiblockCatalog.Blueprint blueprint() { return blueprint; }
    public int removedTopLayers() { return removedTopLayers; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public MultiblockPreview detachedPreview() { return preview; }
    public void reset() { yaw = -45; pitch = 25; setRemovedTopLayers(0); dragging = false; }
    public void setRemovedTopLayers(int layers) {
        removedTopLayers = Math.floorMod(layers, blueprint.height() + 1);
        preview = blueprint.preview(removedTopLayers);
    }
    public void rotate(float degrees) { yaw = Mth.wrapDegrees(yaw + degrees); }
    /** Original construction bookmark shows its detached target rather than an absent BlockItem. */
    public void renderBookmark(GuiGraphics gui,int x,int y,int width,int height) {
        MultiblockPreview before=preview;float beforeYaw=yaw,beforePitch=pitch;
        try {preview=blueprint.targetPreview();yaw=-45;pitch=25;renderStructure(gui,x,y,width,height);}
        finally {preview=before;yaw=beforeYaw;pitch=beforePitch;}
    }

    /** Title is drawn by the caller; 172 logical pixels fit a normal parchment column. */
    public void render(GuiGraphics gui, Font font, int x, int y, int width, int height, double mouseX, double mouseY) {
        controls.clear(); materialHovers.clear();
        int viewHeight = Math.max(38, height - 55);
        viewport = new Rect(x, y, width, viewHeight);
        renderStructure(gui, x, y, width, viewHeight);
        button(gui, font, x, y + viewHeight, 24, 13, "↶", tr("structure_rotate_left"), () -> rotate(-45), mouseX, mouseY);
        button(gui, font, x + width - 24, y + viewHeight, 24, 13, "↷", tr("structure_rotate_right"), () -> rotate(45), mouseX, mouseY);
        button(gui, font, x + 27, y + viewHeight, width - 54, 13,
                removedTopLayers == 0 ? tr("structure_all_layers") : tr("structure_layers", blueprint.height() - removedTopLayers, blueprint.height()),
                tr("structure_layer_hint"), () -> setRemovedTopLayers(removedTopLayers + 1), mouseX, mouseY);
        button(gui, font, x + width - 14, y, 14, 12, "×", tr("structure_reset"), this::reset, mouseX, mouseY);
        int materialsY = y + viewHeight + 17;
        List<ItemStack> stacks = blueprint.materialStacks();
        int materialsX = x + (width - stacks.size() * 20) / 2;
        for (ItemStack stack : stacks) {
            gui.fill(materialsX - 1, materialsY - 1, materialsX + 17, materialsY + 17, 0x30362116);
            gui.renderItem(stack, materialsX, materialsY);
            gui.renderItemDecorations(font, stack, materialsX, materialsY);
            materialHovers.add(new MaterialHover(new Rect(materialsX, materialsY, 16, 16), stack));
            materialsX += 20;
        }
        centered(gui, font, tr("structure_reference"), x + width / 2, y + height - 11, width, FADED);
    }

    /** Does not instantiate any world tile or call an actual Level. Even the matrix uses its neutral mesh. */
    private void renderStructure(GuiGraphics gui, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        float diameter = (float)Math.sqrt(blueprint.width() * blueprint.width() + blueprint.height() * blueprint.height()
                + blueprint.depth() * blueprint.depth());
        float scale = Math.min(width, height) / (diameter + .7f);
        gui.flush();
        gui.pose().pushPose();
        try {
            gui.pose().translate(x + width / 2f, y + height / 2f, 180);
            gui.pose().scale(scale, -scale, scale);
            gui.pose().mulPose(Axis.XP.rotationDegrees(pitch));
            gui.pose().mulPose(Axis.YP.rotationDegrees(yaw));
            gui.pose().translate(-blueprint.width() / 2f, -blueprint.height() / 2f, -blueprint.depth() / 2f);
            Lighting.setupFor3DItems();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (var entry : preview.states().entrySet()) {
                gui.pose().pushPose();
                try {
                    gui.pose().translate(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ());
                    BlockState state = entry.getValue();
                    if (state.is(Blocks.LAVA)) renderLava(gui);
                    else if (state.getBlock() instanceof thaumcraft.infusion.InfusionMatrixBlock)
                        special.renderVisual(state, preview, entry.getKey(), gui.pose(), gui.bufferSource(), LIGHT, OverlayTexture.NO_OVERLAY);
                    else {
                        var model = minecraft.getBlockRenderer().getBlockModel(state);
                        minecraft.getBlockRenderer().getModelRenderer().renderModel(gui.pose().last(),
                                gui.bufferSource().getBuffer(Sheets.cutoutBlockSheet()), state, model, 1, 1, 1, LIGHT, OverlayTexture.NO_OVERLAY);
                    }
                } finally { gui.pose().popPose(); }
            }
        } finally {
            gui.flush(); gui.pose().popPose();
            Lighting.setupFor3DItems();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        }
    }

    /** The only Material source in BETA26's six constructions is lava; render its animated atlas sprite. */
    private static void renderLava(GuiGraphics gui) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "block/lava_still"));
        VertexConsumer vertices = gui.bufferSource().getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        var pose = gui.pose().last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        face(vertices, pose, new float[][]{{0,1,0},{0,1,1},{1,1,1},{1,1,0}}, u0,u1,v0,v1, 0,1,0);
        face(vertices, pose, new float[][]{{0,0,0},{1,0,0},{1,0,1},{0,0,1}}, u0,u1,v0,v1, 0,-1,0);
        face(vertices, pose, new float[][]{{0,0,0},{0,1,0},{1,1,0},{1,0,0}}, u0,u1,v0,v1, 0,0,-1);
        face(vertices, pose, new float[][]{{1,0,1},{1,1,1},{0,1,1},{0,0,1}}, u0,u1,v0,v1, 0,0,1);
        face(vertices, pose, new float[][]{{0,0,1},{0,1,1},{0,1,0},{0,0,0}}, u0,u1,v0,v1, -1,0,0);
        face(vertices, pose, new float[][]{{1,0,0},{1,1,0},{1,1,1},{1,0,1}}, u0,u1,v0,v1, 1,0,0);
    }
    private static void face(VertexConsumer vertices, PoseStack.Pose pose, float[][] points,
                             float u0, float u1, float v0, float v1, float nx, float ny, float nz) {
        for (int i = 0; i < 4; i++) vertices.vertex(pose.pose(), points[i][0], points[i][1], points[i][2])
                .color(255,255,255,255).uv(i < 2 ? u0 : u1, i == 0 || i == 3 ? v1 : v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LIGHT).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private void button(GuiGraphics gui, Font font, int x, int y, int width, int height, String text,
                        String tooltip, Runnable action, double mouseX, double mouseY) {
        Rect bounds = new Rect(x, y, width, height);
        controls.add(new Control(bounds, action, tooltip));
        gui.fill(x, y, x + width, y + height, bounds.contains(mouseX, mouseY) ? 0x706F492A : 0x30362116);
        centered(gui, font, text, x + width / 2, y + 2, width - 3, INK);
    }
    private static void centered(GuiGraphics gui, Font font, String text, int x, int y, int width, int color) {
        float scale = Math.min(1f, width / (float)Math.max(1, font.width(text)));
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(scale, scale, 1);
        gui.drawString(font, text, -font.width(text) / 2, 0, color, false); gui.pose().popPose();
    }
    public Optional<ItemStack> hoveredMaterial(double mouseX, double mouseY) {
        return materialHovers.stream().filter(hover -> hover.bounds.contains(mouseX, mouseY)).findFirst().map(hover -> hover.stack.copy());
    }
    public Optional<String> tooltip(double mouseX, double mouseY) {
        for (Control control : controls) if (control.bounds.contains(mouseX, mouseY)) return Optional.of(control.tooltip);
        return viewport != null && viewport.contains(mouseX, mouseY) ? Optional.of(tr("structure_drag_hint")) : Optional.empty();
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (Control control : controls) if (control.bounds.contains(mouseX, mouseY)) { control.action.run(); return true; }
        if (viewport != null && viewport.contains(mouseX, mouseY)) { dragging = true; return true; }
        return false;
    }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging || button != 0) return false;
        rotate((float)deltaX * 1.5f); pitch = Mth.clamp(pitch + (float)deltaY, -80, 80); return true;
    }
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = dragging; dragging = false; return wasDragging && button == 0;
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (viewport == null || !viewport.contains(mouseX, mouseY) || amount == 0) return false;
        setRemovedTopLayers(removedTopLayers + (int)Math.signum(amount)); return true;
    }
    private static String tr(String key, Object... args) { return Component.translatable("thaumcraft.book." + key, args).getString(); }
    private record Rect(int x, int y, int width, int height) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }
    private record Control(Rect bounds, Runnable action, String tooltip) { }
    private record MaterialHover(Rect bounds, ItemStack stack) { }
}
