package thaumcraft.client.research;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCategories;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Original read-only paper inserts: five discovered aspects per page, and raw category knowledge. */
public final class ThaumonomiconKnowledgeScreen extends Screen {
    public enum Mode { ASPECTS, KNOWLEDGE }
    private static final ResourceLocation PAPER = texture("gui/paper.png");
    private static final ResourceLocation BOOK = texture("gui/gui_researchbook.png");
    private static final ResourceLocation UNKNOWN = texture("aspects/_unknown.png");
    private final Screen parent;
    private final Mode mode;
    private PlayerKnowledge knowledge;
    private int aspectPage;
    private List<Aspect> aspects = List.of();
    private List<Component> tooltip = List.of();

    public ThaumonomiconKnowledgeScreen(Screen parent, PlayerKnowledge knowledge, Mode mode) {
        super(Component.translatable(mode == Mode.ASPECTS ? "tc.aspect.name" : "tc.knowledge.name"));
        this.parent = parent;
        this.mode = mode;
        update(knowledge);
    }

    private static ResourceLocation texture(String path) { return new ResourceLocation("thaumcraft", "textures/" + path); }
    public Mode mode() { return mode; }
    public int aspectPage() { return aspectPage; }
    public List<Aspect> discoveredAspects() { return aspects; }
    public boolean accessible() {
        return knowledge.isResearchCompleteStrict(mode == Mode.ASPECTS ? "FIRSTSTEPS" : "KNOWLEDGETYPES");
    }

    public void update(PlayerKnowledge next) {
        knowledge = next;
        aspects = Aspect.aspects.values().stream().filter(knowledge::knowsAspect)
                .sorted(Comparator.comparing(Aspect::getName)).toList();
        aspectPage = Mth.clamp(aspectPage, 0, Math.max(0, (aspects.size() - 1) / 5));
    }

    public void update(PlayerKnowledge next, int scans) {
        update(next);
        // The retained book must also receive authoritative changes while an insert is in front of it.
        if (parent instanceof ThaumonomiconScreen screen) screen.update(next, scans);
        else if (parent instanceof ThaumonomiconPageScreen screen) screen.update(next, scans);
    }

    @Override protected void init() {
        if (parent.width != width || parent.height != height) parent.resize(minecraft, width, height);
    }

    private float scale() { return Math.min(1f, Math.min((width - 16) / 256f, (height - 16) / 256f)); }
    private float left() { return (width - 256 * scale()) / 2f; }
    private float top() { return (height - 256 * scale()) / 2f; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        parent.render(g, -1000, -1000, partialTick);
        g.flush();
        g.fill(0, 0, width, height, 0x99000000);
        g.flush();
        float scale = scale();
        double mx = (mouseX - left()) / scale, my = (mouseY - top()) / scale;
        tooltip = List.of();
        g.pose().pushPose();
        g.pose().translate(left(), top(), 100);
        g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, 1);
        g.blit(PAPER, 0, 0, 0, 0, 256, 256, 256, 256);
        g.flush();
        // paper.png has transparent outer margins; the content occupies its actual sheet interior.
        smallCentered(g, title.getString(), 123, 27, 142);
        if (!accessible()) centeredMessage(g, "thaumcraft.book.insert_locked", 110);
        else if (mode == Mode.ASPECTS) drawAspects(g, mx, my);
        else drawKnowledge(g, mx, my);
        g.drawString(font, "×", 201, 27, 0x5A3920, false);
        if (hit(mx, my, 196, 23, 14, 16)) tooltip = List.of(Component.translatable("gui.back"));
        g.flush();
        g.pose().popPose();
        if (!tooltip.isEmpty()) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void centeredMessage(GuiGraphics g, String key, int y) {
        var lines = font.split(Component.translatable(key), 154);
        for (var line : lines) { g.drawString(font, line, 128 - font.width(line) / 2, y, 0x5A3920, false); y += 12; }
    }

    private void drawAspects(GuiGraphics g, double mx, double my) {
        if (aspects.isEmpty()) { centeredMessage(g, "thaumcraft.book.no_aspects", 110); return; }
        int start = aspectPage * 5;
        for (int row = 0; row < 5 && start + row < aspects.size(); row++) {
            Aspect aspect = aspects.get(start + row);
            int y = 48 + row * 32;
            drawAspect(g, aspect, 55, y, 20, mx, my);
            smallCentered(g, aspect.getName(), 65, y + 22, 34);
            Aspect[] components = aspect.getComponents();
            if (components != null && components.length == 2) {
                g.drawString(font, "=", 88, y + 7, 0x777777, false);
                drawAspect(g, components[0], 115, y + 1, 18, mx, my);
                drawAspect(g, components[1], 179, y + 1, 18, mx, my);
                g.drawString(font, "+", 152, y + 7, 0x777777, false);
                if (knowledge.knowsAspect(components[0])) smallCentered(g, components[0].getName(), 124, y + 22, 44);
                if (knowledge.knowsAspect(components[1])) smallCentered(g, components[1].getName(), 188, y + 22, 40);
            } else {
                smallCentered(g, Component.translatable("tc.aspect.primal").getString(), 151, y + 7, 106);
            }
        }
        int pages = (aspects.size() + 4) / 5;
        smallCentered(g, (aspectPage + 1) + " / " + pages, 128, 224, 80);
        if (aspectPage > 0) g.blit(BOOK, 52, 225, 0, 184, 12, 8);
        if (aspectPage < pages - 1) g.blit(BOOK, 194, 225, 12, 184, 12, 8);
    }

    private void drawAspect(GuiGraphics g, Aspect aspect, int x, int y, int size, double mx, double my) {
        boolean known = knowledge.knowsAspect(aspect);
        int color = known ? aspect.getColor() : 0xCCCCCC;
        g.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
        ResearchIconRenderer.drawTexture(g, known ? aspect.getImage() : UNKNOWN, x, y, size);
        g.setColor(1, 1, 1, 1);
        if (hit(mx, my, x, y, size, size)) tooltip = known
                ? List.of(Component.literal(aspect.getName()), Component.literal(aspect.getLocalizedDescription()))
                : List.of(Component.translatable("thaumcraft.book.unknown_aspect"));
    }

    private void drawKnowledge(GuiGraphics g, double mx, double my) {
        List<String> categories = ResearchCategories.keys().stream().filter(category ->
                knowledge.rawKnowledge(KnowledgeType.OBSERVATION, category) > 0
                        || knowledge.rawKnowledge(KnowledgeType.THEORY, category) > 0).toList();
        if (categories.isEmpty()) { centeredMessage(g, "thaumcraft.book.no_knowledge", 110); return; }
        ResearchIconRenderer.drawTexture(g, texture("research/knowledge_observation.png"), 155, 40, 16);
        ResearchIconRenderer.drawTexture(g, texture("research/knowledge_theory.png"), 188, 40, 16);
        if (hit(mx, my, 154, 39, 18, 18)) tooltip = List.of(Component.translatable("tc.type.observation"));
        if (hit(mx, my, 187, 39, 18, 18)) tooltip = List.of(Component.translatable("tc.type.theory"));
        int row = 0;
        for (String category : categories) {
            int y = 61 + row++ * 21;
            ResourceLocation icon = category.equals("BASICS") ? texture("items/thaumonomicon_cheat.png")
                    : texture("research/cat_" + category.toLowerCase(Locale.ROOT) + ".png");
            ResearchIconRenderer.drawTexture(g, icon, 50, y, 16);
            smallCentered(g, Component.translatable("tc.research_category." + category).getString(), 109, y + 5, 78);
            knowledgeCell(g, category, KnowledgeType.OBSERVATION, 155, y, mx, my);
            knowledgeCell(g, category, KnowledgeType.THEORY, 188, y, mx, my);
        }
        smallCentered(g, Component.translatable("thaumcraft.book.knowledge_fraction").getString(), 128, 224, 160);
    }

    private void knowledgeCell(GuiGraphics g, String category, KnowledgeType type, int x, int y, double mx, double my) {
        int raw = knowledge.rawKnowledge(type, category), full = raw / type.units(), remainder = raw % type.units();
        ResearchIconRenderer.drawTexture(g, texture("research/knowledge_" + type.name().toLowerCase(Locale.ROOT) + ".png"), x, y, 16);
        ResourceLocation icon = category.equals("BASICS") ? texture("items/thaumonomicon_cheat.png")
                : texture("research/cat_" + category.toLowerCase(Locale.ROOT) + ".png");
        ResearchIconRenderer.drawTexture(g, icon, x + 4, y + 4, 11);
        String amount = Integer.toString(full);
        float numberScale = Math.min(1f, 16f / Math.max(1, font.width(amount)));
        g.pose().pushPose();
        g.pose().translate(x + 16, y + 8, 0);
        g.pose().scale(numberScale, numberScale, 1);
        g.drawString(font, amount, -font.width(amount), 0, 0xFFFFFF, true);
        g.pose().popPose();
        g.blit(BOOK, x, y + 18, 0, 234, 16, 2);
        int progress = remainder * 16 / type.units();
        if (progress > 0) g.blit(BOOK, x, y + 18, 0, 232, progress, 2);
        if (hit(mx, my, x, y, 20, 21)) tooltip = List.of(
                Component.translatable("tc.type." + type.name().toLowerCase(Locale.ROOT))
                        .append(": ").append(Component.translatable("tc.research_category." + category)),
                Component.translatable("thaumcraft.book.knowledge_balance", full, remainder, type.units()));
    }

    private void smallCentered(GuiGraphics g, String text, int x, int y, int availableWidth) {
        float scale = Math.min(.8f, availableWidth / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, 0x504030, false);
        g.pose().popPose();
    }

    public void changeAspectPage(int direction) {
        if (mode == Mode.ASPECTS && accessible()) aspectPage = Mth.clamp(aspectPage + direction, 0, Math.max(0, (aspects.size() - 1) / 5));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double mx = (x - left()) / scale(), my = (y - top()) / scale();
        if (button == 1 || !hit(mx, my, 36, 14, 184, 228) || hit(mx, my, 196, 23, 14, 16)) { onClose(); return true; }
        if (button == 0 && mode == Mode.ASPECTS) {
            if (hit(mx, my, 48, 219, 20, 18)) { changeAspectPage(-1); return true; }
            if (hit(mx, my, 188, 219, 20, 18)) { changeAspectPage(1); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        changeAspectPage(delta < 0 ? 1 : -1); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) { changeAspectPage(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) { changeAspectPage(1); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private static boolean hit(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
}
