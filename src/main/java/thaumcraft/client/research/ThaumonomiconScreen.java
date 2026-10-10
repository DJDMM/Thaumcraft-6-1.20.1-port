package thaumcraft.client.research;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchEntry;
import thaumcraft.research.ResearchCategories;
import thaumcraft.research.ResearchProgression;
import thaumcraft.research.ResearchBookVisibility;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Original TC6 research map, restricted to the player's discoveries and current stages. */
public final class ThaumonomiconScreen extends Screen {
    private static final ResourceLocation ATLAS = texture("gui/gui_research_browser.png");
    private static final ResourceLocation STARS = texture("gui/gui_research_back_over.png");
    private static final Map<String, Integer> BACKGROUNDS = Map.of("BASICS", 1, "AUROMANCY", 2, "ALCHEMY", 3, "ARTIFICE", 4, "INFUSION", 7, "GOLEMANCY", 5, "ELDRITCH", 6);
    private final Map<String, View> views = new HashMap<>();
    private PlayerKnowledge knowledge;
    private int scans;
    private String category = "BASICS";
    private double centerX, centerY;
    private float zoom = 1;
    private boolean searching, dragging;
    private double pressX, pressY;
    private boolean moved;
    private ResearchEntry pressed, hovered;
    private EditBox searchBox;
    private String query = "";
    private int searchScroll;
    private boolean searchDirty=true;
    private List<SearchHit> cachedSearchHits=List.of();

    public ThaumonomiconScreen(PlayerKnowledge knowledge, int scans) {
        super(Component.translatable("item.thaumcraft.thaumonomicon"));
        this.knowledge = knowledge;
        this.scans = scans;
        searchDirty=true;
        resetView();
    }

    public void update(PlayerKnowledge knowledge, int scans) {
        this.knowledge = knowledge;
        this.scans = scans;
        searchDirty=true;
        if (!visibleCategories().contains(category)) changeCategory("BASICS");
    }

    private boolean complete(ResearchEntry e) {
        return ResearchProgression.isComplete(knowledge, e.key());
    }

    private List<String> visibleCategories() {
        return ResearchCategories.keys().stream()
                .filter(key -> !key.equals("PORT") && ResearchCategories.categoryUnlocked(knowledge, key)).toList();
    }

    private String viewKey() { return category; }

    @Override
    protected void init() {
        searchBox = new EditBox(font, 30, 36, Math.max(70, width - 60), 18, tr("search"));
        searchBox.setMaxLength(80);
        searchBox.setHint(tr("search"));
        searchBox.setValue(query);
        searchBox.setResponder(value -> { query = value; searchScroll = 0; searchDirty=true; });
        searchBox.setVisible(searching);
        addRenderableWidget(searchBox);
        if (searching) setFocused(searchBox);
        dragging = false;
    }

    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/" + path); }
    private static Component tr(String key, Object... args) { return Component.translatable("thaumcraft.research." + key, args); }
    private boolean canDiscover(ResearchEntry e) {
        if (!ResearchProgression.supportsProgression(e.key()) || ResearchProgression.isComplete(knowledge, e.key())) return false;
        return ResearchProgression.stage(knowledge, e.key()) == 0 ? ResearchProgression.canStart(knowledge, e.key())
                : ResearchProgression.canAdvance(knowledge, e,minecraft==null || minecraft.player==null?null:minecraft.player.getInventory());
    }
    private boolean visible(ResearchEntry e) {
        return !e.supported() && ResearchBookVisibility.visible(knowledge,e,false);
    }
    private Component categoryName(String key) { return Component.translatable("tc.research_category." + key); }
    private List<ResearchEntry> entries() {
        return ResearchCatalog.entries().stream().filter(e -> e.category().equals(category))
                .filter(this::visible).toList();
    }

    private List<ResearchEntry> searchResults() {
        return searchHits().stream().map(SearchHit::entry).filter(java.util.Objects::nonNull).distinct().toList();
    }
    private List<SearchHit> searchHits() {
        if(!searchDirty) return cachedSearchHits;
        String needle = query.toLowerCase(Locale.ROOT).strip();
        List<SearchHit> hits=new java.util.ArrayList<>();
        // Empty-query ordering keeps research nodes first; real queries also find categories/results.
        for(ResearchEntry e:ResearchCatalog.entries()) if(visible(e)
                && Component.translatable(e.title()).getString().toLowerCase(Locale.ROOT).contains(needle))
            hits.add(new SearchHit(e,null,null,Component.translatable(e.title()).getString(),null));
        for(String key:visibleCategories()) if(!needle.isEmpty() && categoryName(key).getString().toLowerCase(Locale.ROOT).contains(needle))
            hits.add(new SearchHit(null,key,null,categoryName(key).getString(),null));
        java.util.Set<ResourceLocation> recipes=new java.util.HashSet<>();
        for(ResearchEntry e:ResearchCatalog.entries()) if(visible(e))
            for(var stage:ResearchBookVisibility.readableChapters(knowledge,e,false))
                for(String raw:stage.recipes()) for(var view:BookRecipeViews.playable(raw))
                    if(!view.output().isEmpty() && view.output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                            && recipes.add(view.id()))
                        hits.add(new SearchHit(e,null,view.id(),view.output().getHoverName().getString(),view.output().copy()));
        cachedSearchHits=List.copyOf(hits);searchDirty=false;
        return cachedSearchHits;
    }

    private void rememberView() { views.put(viewKey(), new View(centerX, centerY, zoom)); }

    private void changeCategory(String next) {
        if (!visibleCategories().contains(next)) return;
        rememberView();
        category = next;
        View view = views.get(viewKey());
        if (view == null) resetView();
        else { centerX = view.x(); centerY = view.y(); zoom = view.zoom(); }
        if (searching) toggleSearch();
    }

    private void resetView() {
        List<ResearchEntry> nodes = entries();
        centerX = (nodes.stream().mapToInt(ResearchEntry::column).min().orElse(0)
                + nodes.stream().mapToInt(ResearchEntry::column).max().orElse(0)) * 12.0;
        centerY = (nodes.stream().mapToInt(ResearchEntry::row).min().orElse(0)
                + nodes.stream().mapToInt(ResearchEntry::row).max().orElse(0)) * 12.0;
        zoom = 1;
    }

    private void clampView() {
        List<ResearchEntry> nodes = entries();
        centerX = Mth.clamp(centerX, nodes.stream().mapToInt(ResearchEntry::column).min().orElse(0) * 24 - 64,
                nodes.stream().mapToInt(ResearchEntry::column).max().orElse(0) * 24 + 64);
        centerY = Mth.clamp(centerY, nodes.stream().mapToInt(ResearchEntry::row).min().orElse(0) * 24 - 64,
                nodes.stream().mapToInt(ResearchEntry::row).max().orElse(0) * 24 + 64);
    }

    private int tabY(int index) { return 32 + index * Math.min(24, Math.max(17, (height - 68) / 6)); }
    private boolean inMap(double x, double y) { return x >= 20 && x < width - 20 && y >= 20 && y < height - 20; }
    private int nodeX(ResearchEntry e) { return (int) Math.round(width / 2.0 + (e.column() * 24 - centerX) / zoom); }
    private int nodeY(ResearchEntry e) { return (int) Math.round(height / 2.0 + (e.row() * 24 - centerY) / zoom); }
    private ResearchEntry at(double x, double y) {
        if (!inMap(x, y)) return null;
        double radius = 12 / zoom;
        for (ResearchEntry e : entries()) if (Math.abs(x - nodeX(e)) <= radius && Math.abs(y - nodeY(e)) <= radius) return e;
        return null;
    }

    private void open(ResearchEntry e) {
        if (!visible(e) || (ResearchProgression.stage(knowledge, e.key()) == 0
                && !ResearchProgression.canStart(knowledge, e.key()))) return;
        rememberView();
        dragging = false;
        minecraft.setScreen(new ThaumonomiconPageScreen(this, e, knowledge, scans));
    }

    private void toggleSearch() {
        searching = !searching;
        searchBox.setVisible(searching);
        searchBox.setFocused(searching);
        setFocused(searching ? searchBox : null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xFF120F13);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.enableScissor(18, 18, width - 18, height - 18);
        ResourceLocation back = texture("gui/gui_research_back_" + BACKGROUNDS.get(category) + ".jpg");
        // TC6 uses a 256-unit texture period for its 1024px backgrounds.
        float u = (float) (centerX / 2 - width * zoom / 4);
        float v = (float) (centerY / 2 - height * zoom / 4);
        g.blit(back, 18, 18, width - 36, height - 36, u, v, (int) ((width - 36) * zoom), (int) ((height - 36) * zoom), 256, 256);
        g.blit(STARS, 18, 18, width - 36, height - 36, u * 1.3f, v * 1.3f, (int) ((width - 36) * zoom), (int) ((height - 36) * zoom), 256, 256);
        hovered = null;
        if (searching) renderSearch(g, mouseX, mouseY);
        else renderGraph(g, mouseX, mouseY);
        g.disableScissor();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderFrame(g);
        List<String> categories = visibleCategories();
        for (int i = 0; i < categories.size(); i++) renderTab(g, categories.get(i), 0, tabY(i), mouseX, mouseY);
        if(knowledge.isResearchCompleteStrict("FIRSTSTEPS")) insertTab(g,35,76,"tc.aspect.name");
        if(knowledge.isResearchCompleteStrict("KNOWLEDGETYPES")) insertTab(g,57,44,"tc.knowledge.name");
        g.blit(ATLAS, 0, height - 18, 160, 16, 16, 16);
        String label = categoryName(category).getString();
        g.fill(width / 2 - font.width(label) / 2 - 7, 2, width / 2 + font.width(label) / 2 + 7, 15, 0xBB171010);
        g.drawCenteredString(font, label, width / 2, 4, 0xE4C993);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            Component state = complete(hovered) ? tr("complete")
                    : Component.translatable(canDiscover(hovered) ? "thaumcraft.progress.ready" : "thaumcraft.progress.requirements_missing");
            java.util.List<Component> tooltip=new java.util.ArrayList<>();
            tooltip.add(Component.translatable(hovered.title()));tooltip.add(state);
            if(knowledge.hasUnreadResearch(hovered.key())) tooltip.add(Component.translatable("tc.research.newresearch"));
            if(knowledge.hasUnreadPage(hovered.key())) tooltip.add(Component.translatable("tc.research.newpage"));
            {
                int stage=knowledge.researchStage(hovered.key());
                if(stage>0 && !complete(hovered)) tooltip.add(Component.translatable("thaumcraft.book.stage",stage,hovered.stages().size()));
                for(String parent: hovered.parents()) if(!knowledge.isResearchCompleteStrict(parent)) {
                    ResearchEntry related=ResearchCatalog.get(ResearchCatalog.graphParentKey(parent));
                    tooltip.add(Component.translatable("thaumcraft.book.research_requirement",related==null?
                            Component.translatable("research."+parent.replaceFirst("^[!~]","")+".text").getString():Component.translatable(related.title()).getString()));
                }
            }
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        } else if (mouseX <= 18 && mouseY >= height - 20) {
            g.renderTooltip(font, tr("search_hint"), mouseX, mouseY);
        } else {
            for (int i = 0; i < categories.size(); i++) if (hit(mouseX, mouseY, 0, tabY(i), 18, 18))
                g.renderTooltip(font, categoryName(categories.get(i)), mouseX, mouseY);
            if(hit(mouseX,mouseY,width-25,35,25,16)&&knowledge.isResearchCompleteStrict("FIRSTSTEPS"))
                g.renderTooltip(font,Component.translatable("tc.aspect.name"),mouseX,mouseY);
            if(hit(mouseX,mouseY,width-25,57,25,16)&&knowledge.isResearchCompleteStrict("KNOWLEDGETYPES"))
                g.renderTooltip(font,Component.translatable("tc.knowledge.name"),mouseX,mouseY);
        }
    }

    private void renderGraph(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(width / 2.0, height / 2.0, 0);
        g.pose().scale(1 / zoom, 1 / zoom, 1);
        g.pose().translate(-centerX, -centerY, 0);
        List<ResearchEntry> nodes = entries();
        for (ResearchEntry e : nodes) {
            for (String raw : e.parents()) {
                ResearchEntry parent = ResearchCatalog.get(ResearchCatalog.graphParentKey(raw));
                if (raw.startsWith("~") || parent == null || !nodes.contains(parent) || parent.siblings().contains(e.key())) continue;
                float brightness = complete(parent) ? 0.7f : 0.35f;
                g.setColor(brightness, brightness, brightness, 1);
                connection(g, e, parent, true, e.hasMeta("REVERSE"));
            }
            for (String raw : e.siblings()) {
                ResearchEntry sibling = ResearchCatalog.get(ResearchCatalog.graphParentKey(raw));
                if (raw.startsWith("~") || sibling == null || !nodes.contains(sibling)) continue;
                g.setColor(0.4f, 0.4f, 0.5f, 1);
                connection(g, sibling, e, false, e.hasMeta("REVERSE"));
            }
        }
        g.setColor(1, 1, 1, 1);
        hovered = at(mouseX, mouseY);
        for (ResearchEntry e : nodes) {
            int x = e.column() * 24, y = e.row() * 24;
            boolean complete = complete(e);
            boolean available = canDiscover(e);
            float brightness = complete || available || ResearchProgression.stage(knowledge, e.key()) > 0 ? 1 : 0.4f;
            if (hovered == e) {
                g.fill(x - 17, y - 17, x + 17, y + 17, 0x354FDAC5);
                brightness = 1;
            }
            g.setColor(brightness, brightness, brightness, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int atlasX = e.hasMeta("ROUND") ? 144 : e.hasMeta("HEX") ? 112 : 80;
            int atlasY = e.hasMeta("HIDDEN") ? 80 : 48;
            g.blit(ATLAS, x - 16, y - 16, atlasX, atlasY, 32, 32);
            if (e.hasMeta("SPIKY")) g.blit(ATLAS, x - 16, y - 16, 176, atlasY, 32, 32);
            g.setColor(1, 1, 1, 1);
            ResearchIconRenderer.draw(g, e, x - 8, y - 8, brightness);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (complete) { g.setColor(0.6f, 1, 0.6f, 1); g.blit(ATLAS, x + 6, y - 17, 224, 16, 16, 16); }
            else if (available) g.blit(ATLAS, x + 6, y - 17, 176, 16, 16, 16);
            g.setColor(1, 1, 1, 1);
            if(knowledge.hasUnreadResearch(e.key())) g.blit(ATLAS,x-18,y-18,8,8,176,16,32,32,256,256);
            if(knowledge.hasUnreadPage(e.key())) g.blit(ATLAS,x-18,y-8,8,8,208,16,32,32,256,256);
        }
        g.pose().popPose();
    }

    /** TC6's original rounded, orthogonal connectors, using the same atlas tiles. */
    private void connection(GuiGraphics g, ResearchEntry child, ResearchEntry parent, boolean arrow, boolean reverse) {
        int x = child.column(), y = child.row(), x2 = parent.column(), y2 = parent.row();
        int dx = Math.abs(x - x2), dy = Math.abs(y - y2);
        int sx = Integer.compare(reverse ? x : x2, reverse ? x2 : x);
        int sy = Integer.compare(reverse ? y : y2, reverse ? y2 : y);
        int xx = (reverse ? x2 : x) * 24 - 12, yy = (reverse ? y2 : y) * 24 - 12;
        if (dx == 0 && dy == 0) return;
        boolean big = dx > 1 && dy > 1;
        if (arrow) {
            int ax = reverse ? x * 24 - 16 : xx - 4;
            int ay = reverse ? y * 24 - 16 : yy - 4;
            int uv = reverse ? sx < 0 ? 160 : sx > 0 ? 128 : sy > 0 ? 64 : 96
                    : sy < 0 ? 64 : sy > 0 ? 96 : sx > 0 ? 160 : 128;
            g.blit(ATLAS, ax, ay, uv, 112, 32, 32);
        }
        int vertical = 1;
        while (vertical < dy - (big ? 1 : 0)) {
            g.blit(ATLAS, xx, yy + sy * 24 * vertical, 0, 228, 24, 24);
            vertical++;
        }
        if (dx > 0 && dy > 0) {
            if (big) {
                int uv = sy > 0 ? sx < 0 ? 0 : 48 : sx < 0 ? 96 : 144;
                g.blit(ATLAS, xx + (sx < 0 ? -24 : 0), yy + sy * 24 * vertical + (sy < 0 ? -24 : 0), uv, 180, 48, 48);
            } else {
                int uv = sy > 0 ? sx < 0 ? 48 : 72 : sx < 0 ? 96 : 120;
                g.blit(ATLAS, xx, yy + sy * 24 * vertical, uv, 228, 24, 24);
            }
        }
        int horizontalY = dy == 0 ? yy : yy + sy * 24 * (vertical + (big ? 1 : 0));
        for (int h = big ? 2 : 1; h < dx; h++) g.blit(ATLAS, xx + sx * 24 * h, horizontalY, 24, 228, 24, 24);
    }

    private void renderFrame(GuiGraphics g) {
        for (int x = 16; x < width - 16; x += 64) {
            int length = Math.min(64, width - 16 - x);
            g.blit(ATLAS, x, -2, 48, 13, length, 22);
            g.blit(ATLAS, x, height - 20, 48, 13, length, 22);
        }
        for (int y = 16; y < height - 16; y += 64) {
            int length = Math.min(64, height - 16 - y);
            g.blit(ATLAS, -2, y, 13, 48, 22, length);
            g.blit(ATLAS, width - 20, y, 13, 48, 22, length);
        }
        g.blit(ATLAS, -2, -2, 13, 13, 22, 22);
        g.blit(ATLAS, width - 20, -2, 13, 13, 22, 22);
        g.blit(ATLAS, -2, height - 20, 13, 13, 22, 22);
        g.blit(ATLAS, width - 20, height - 20, 13, 13, 22, 22);
    }

    private void renderTab(GuiGraphics g, String key, int x, int y, int mx, int my) {
        boolean active = category.equals(key), hover = hit(mx, my, x, y, 18, 18);
        if (active || hover) g.fill(x, y - 2, x + 18, y + 19, active ? 0xCC755C36 : 0x885D4D38);
        ResourceLocation icon = switch (key) {
            case "BASICS" -> texture("items/thaumonomicon_cheat.png");
            default -> texture("research/cat_" + key.toLowerCase(Locale.ROOT) + ".png");
        };
        ResearchIconRenderer.drawTexture(g, icon, x + 1, y, 16);
        {
            var categoryEntries=ResearchCatalog.entries().stream().filter(e->e.category().equals(key)).toList();
            if(categoryEntries.stream().anyMatch(e->knowledge.hasUnreadResearch(e.key()))) g.blit(ATLAS,x+10,y-2,8,8,176,16,32,32,256,256);
            if(categoryEntries.stream().anyMatch(e->knowledge.hasUnreadPage(e.key()))) g.blit(ATLAS,x+10,y+8,8,8,208,16,32,32,256,256);
        }
    }
    private void insertTab(GuiGraphics g,int y,int u,String title) {
        g.blit(texture("gui/gui_researchbook.png"),width-25,y,u,232,25,16);
    }

    private void renderSearch(GuiGraphics g, int mx, int my) {
        g.fill(23, 23, width - 23, height - 23, 0xE91B1720);
        List<SearchHit> results = searchHits();
        int capacity = Math.max(1, (height - 85) / 24);
        searchScroll = Mth.clamp(searchScroll, 0, Math.max(0, results.size() - capacity));
        for (int i = 0; i < capacity && i + searchScroll < results.size(); i++) {
            SearchHit result=results.get(i+searchScroll);
            ResearchEntry e = result.entry();
            int y = 62 + i * 24;
            if (hit(mx, my, 29, y - 2, width - 58, 24)) { g.fill(29, y - 2, width - 29, y + 22, 0x80594666); hovered = e; }
            if(result.output()!=null) ResearchIconRenderer.drawStack(g,result.output(),32,y);
            else if(e!=null) ResearchIconRenderer.draw(g,e,32,y,1);
            else renderTab(g,result.category(),31,y,mx,my);
            g.drawString(font,font.plainSubstrByWidth(result.label(),width-88),56,y,0xEFDEB8,false);
            Component detail=result.recipe()==null?categoryName(e==null?result.category():e.category())
                    :Component.translatable("thaumcraft.book.recipe_search_owner",Component.translatable(e.title()));
            g.drawString(font,font.plainSubstrByWidth(detail.getString(),width-88),56,y+11,0xA099AF,false);
        }
        if (results.isEmpty()) g.drawCenteredString(font, tr("no_results"), width / 2, 75, 0xC0B6C8);
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1 && searching) { toggleSearch(); return true; }
        if (button == 0) {
            if(hit(mx,my,width-25,35,25,16)&&knowledge.isResearchCompleteStrict("FIRSTSTEPS")) {
                minecraft.setScreen(new ThaumonomiconKnowledgeScreen(this,knowledge,ThaumonomiconKnowledgeScreen.Mode.ASPECTS));return true;
            }
            if(hit(mx,my,width-25,57,25,16)&&knowledge.isResearchCompleteStrict("KNOWLEDGETYPES")) {
                minecraft.setScreen(new ThaumonomiconKnowledgeScreen(this,knowledge,ThaumonomiconKnowledgeScreen.Mode.KNOWLEDGE));return true;
            }
            List<String> categories = visibleCategories();
            for (int i = 0; i < categories.size(); i++) if (hit(mx, my, 0, tabY(i), 18, 18)) { changeCategory(categories.get(i)); return true; }
            if (hit(mx, my, 0, height - 20, 18, 20)) { toggleSearch(); return true; }
            if (searching) {
                if (super.mouseClicked(mx, my, button)) return true;
                int row = (int) ((my - 60) / 24);
                int index = row + searchScroll;
                int capacity = Math.max(1, (height - 85) / 24);
                List<SearchHit> results = searchHits();
                if (my >= 60 && row < capacity && my < height - 23 && mx >= 29 && mx < width - 29 && index >= 0 && index < results.size()) {
                    SearchHit result=results.get(index);
                    if(result.entry()==null) {changeCategory(result.category());return true;}
                    ResearchEntry e = result.entry();
                    changeCategory(e.category());
                    centerX = e.column() * 24; centerY = e.row() * 24;
                    open(e);
                    if(result.recipe()!=null && minecraft.screen instanceof ThaumonomiconPageScreen page)
                        page.focusRecipe(result.recipe());
                    return true;
                }
            } else if (inMap(mx, my)) {
                pressed = at(mx, my); dragging = true; moved = false; pressX = mx; pressY = my;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && dragging && !searching) {
            if (Math.hypot(mx - pressX, my - pressY) > 3) moved = true;
            if (moved) { centerX -= dx * zoom; centerY -= dy * zoom; clampView(); }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            if (!moved && pressed != null && pressed == at(mx, my)) open(pressed);
            pressed = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!inMap(mx, my)) return super.mouseScrolled(mx, my, amount);
        if (searching) searchScroll = Math.max(0, searchScroll - (int) Math.signum(amount) * 3);
        else {
            float next = Mth.clamp(zoom - (float) Math.signum(amount) * 0.25f, 1, 2);
            centerX += (mx - width / 2.0) * (zoom - next);
            centerY += (my - height / 2.0) * (zoom - next);
            zoom = next;
            clampView();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && searching) { toggleSearch(); return true; }
        if (key == GLFW.GLFW_KEY_F && (!searching || hasControlDown())) { toggleSearch(); return true; }
        if (!searching) {
            if (key == GLFW.GLFW_KEY_HOME) { resetView(); return true; }
            if (key == GLFW.GLFW_KEY_LEFT) centerX -= 24 * zoom;
            else if (key == GLFW.GLFW_KEY_RIGHT) centerX += 24 * zoom;
            else if (key == GLFW.GLFW_KEY_UP) centerY -= 24 * zoom;
            else if (key == GLFW.GLFW_KEY_DOWN) centerY += 24 * zoom;
            else return super.keyPressed(key, scanCode, modifiers);
            clampView(); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    void selectCategoryForSmokeTest(String key) { changeCategory(key); }
    List<String> categoriesForSmokeTest() { return visibleCategories(); }
    List<String> entriesForSmokeTest() { return entries().stream().map(ResearchEntry::key).toList(); }
    List<String> searchResultsForSmokeTest() { return searchResults().stream().map(ResearchEntry::key).toList(); }
    void selectForSmokeTest(String key) { ResearchEntry e = ResearchCatalog.get(key); if (e != null) { changeCategory(e.category()); open(e); } }
    void clickForSmokeTest(String key) {
        ResearchEntry e = ResearchCatalog.get(key);
        int x = nodeX(e), y = nodeY(e);
        mouseClicked(x, y, 0); mouseReleased(x, y, 0);
    }
    void searchForSmokeTest(String value) { if (!searching) toggleSearch(); searchBox.setValue(value); }
    String stateForSmokeTest() { return "progression:" + category + ":" + centerX + ":" + centerY + ":" + zoom; }
    List<ResourceLocation> recipeSearchForSmokeTest() {return searchHits().stream().map(SearchHit::recipe).filter(java.util.Objects::nonNull).toList();}
    @Override public boolean isPauseScreen() { return false; }
    private record View(double x, double y, float zoom) {}
    private record SearchHit(ResearchEntry entry,String category,ResourceLocation recipe,String label,net.minecraft.world.item.ItemStack output) {}
}

