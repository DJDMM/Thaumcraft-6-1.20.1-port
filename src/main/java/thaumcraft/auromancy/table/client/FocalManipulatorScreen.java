package thaumcraft.auromancy.table.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.focus.*;
import thaumcraft.auromancy.table.*;

import java.util.*;

/** TC6's metal frame, node sockets, original icons and one-focus inventory layout. */
public final class FocalManipulatorScreen extends AbstractContainerScreen<FocalManipulatorMenu> {
    private static final ResourceLocation FRAME = gui("gui_wandtable"), BACK = gui("gui_wandtable2"), INVENTORY = gui("gui_wandtable3"), BASE = gui("gui_base");
    private static final ResourceLocation MEDIUM = tex("textures/foci/_medium.png"), EFFECT = tex("textures/foci/_effect.png");
    private FocusGraph draft = new FocusGraph(List.of());
    private long seenRevision = -1;
    private int selected = 1, partsStart, scrollY;
    private boolean dirty, changingName;
    private EditBox name;
    private Button confirm;
    private final List<Button> settingsButtons = new ArrayList<>();

    public FocalManipulatorScreen(FocalManipulatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 231; imageHeight = 231;
    }
    private static ResourceLocation gui(String name) { return tex("textures/gui/"+name+".png"); }
    private static ResourceLocation tex(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
    @Override protected void init() {
        super.init();
        name = new EditBox(font, leftPos+30, topPos+11, 170, 12, Component.translatable("wandtable.text6"));
        name.setMaxLength(50); name.setBordered(false); name.setTextColor(0xFFFFFF);
        name.setResponder(text -> { if (!changingName && !menu.busy()) dirty = true; });
        addRenderableWidget(name);
        confirm = addRenderableWidget(Button.builder(Component.literal("✓"), button -> menu.start()).bounds(leftPos+239, topPos+14, 26, 18).build());
        refresh();
    }
    @Override protected void containerTick() {
        super.containerTick(); name.tick();
        if (!dirty && !menu.pending() && seenRevision != menu.revision()) refresh();
        boolean editing = menu.canUse() && !menu.busy() && !menu.pending() && !menu.focus().isEmpty();
        name.setEditable(editing);
        if (dirty && editing) { menu.edit(draft, name.getValue()); dirty = false; }
        var compiled = compile();
        confirm.active = editing && compiled.success() && (menu.creative() || menu.experienceLevel() >= compiled.plan().xpLevels())
                && FocalManipulatorBlockEntity.crystalDebit(menu.playerInventory(), compiled.plan().crystals()) != null;
        settingsButtons.forEach(button -> button.active = editing);
    }
    private void refresh() {
        seenRevision = menu.revision(); draft = menu.graph();
        changingName = true; name.setValue(menu.focusName()); changingName = false;
        if (FocusStacks.isFocus(menu.focus()) && draft.nodes().isEmpty() && !menu.busy()) {
            draft = rootSocket(); selected = 1; dirty = true;
        }
        scrollY = Math.max(0,Math.min(maxScroll(),scrollY));
        rebuildSettings();
    }
    private static FocusGraph rootSocket() {
        return new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),
                new FocusGraph.Node(1,0,List.of(),0,1,"",Map.of())));
    }
    private FocusCompiler.Result compile() { return FocusCompiler.compile(draft, menu.focus(), menu.knowledge()::isResearchCompleteStrict); }
    private FocusGraph.Node selectedNode() { return draft.nodes().stream().filter(node -> node.id() == selected).findFirst().orElse(null); }
    private List<FocusNodeRegistry.Definition> parts() {
        if (draft.nodes().isEmpty()) return List.of();
        var node = selectedNode(); if (node == null || node.id() == 0) return List.of();
        var parent = draft.nodes().stream().filter(current -> current.id() == node.parent()).findFirst().orElse(null);
        var supplies = parent == null ? null : FocusNodeRegistry.get(parent.key());
        if (supplies == null) return List.of();
        return FocusNodeRegistry.all().stream().filter(part -> !part.key().equals(FocusNodeRegistry.ROOT)
                && menu.knowledge().isResearchCompleteStrict(part.research())
                && supplies.supplied().containsAll(part.requiredSupply())).toList();
    }
    private void install(FocusNodeRegistry.Definition part) {
        if (menu.busy() || menu.pending() || !part.runtimeSupported() || !menu.knowledge().isResearchCompleteStrict(part.research())) return;
        var node = selectedNode(); if (node == null || node.id() == 0 || !parts().contains(part)) return;
        List<FocusGraph.Node> prefix = ancestors(node.parent());
        if (prefix.isEmpty()) return;
        int next = prefix.size(); boolean socket = !part.supplied().isEmpty();
        if (next + (socket ? 2 : 1) > FocusGraph.MAX_NODES) return;
        List<FocusGraph.Node> nodes = normalizedPrefix(prefix);
        nodes.add(new FocusGraph.Node(next,next-1,socket ? List.of(next+1) : List.of(),0,next,part.key(),part.defaultSettings()));
        if (socket) nodes.add(new FocusGraph.Node(next+1,next,List.of(),0,next+1,"",Map.of()));
        draft = new FocusGraph(nodes); selected = socket ? next+1 : next;
        scrollToSelection();
        partsStart = 0; dirty = true; rebuildSettings();
    }
    private void clearSelected() {
        if (menu.busy() || menu.pending()) return;
        var node = selectedNode(); if (node == null || node.id() == 0) return;
        var prefix = ancestors(node.parent()); if (prefix.isEmpty()) return;
        var nodes = normalizedPrefix(prefix); selected = prefix.size();
        nodes.add(new FocusGraph.Node(selected,selected-1,List.of(),0,selected,"",Map.of()));
        draft = new FocusGraph(nodes); scrollToSelection();
        dirty = true; rebuildSettings();
    }
    /** Replacement removes that node's descendants, while preserving settings on its ancestors. */
    private List<FocusGraph.Node> ancestors(int id) {
        Map<Integer,FocusGraph.Node> byId = new HashMap<>(); draft.nodes().forEach(node -> byId.put(node.id(),node));
        List<FocusGraph.Node> prefix = new ArrayList<>(); Set<Integer> seen = new HashSet<>();
        while (id >= 0) {
            var node = byId.get(id); if (node == null || !seen.add(id)) return List.of();
            prefix.add(node); id = node.parent();
        }
        Collections.reverse(prefix);
        return prefix.isEmpty() || !prefix.get(0).key().equals(FocusNodeRegistry.ROOT) ? List.of() : prefix;
    }
    private static List<FocusGraph.Node> normalizedPrefix(List<FocusGraph.Node> prefix) {
        List<FocusGraph.Node> result = new ArrayList<>();
        for (int index = 0; index < prefix.size(); index++) {
            var node = prefix.get(index);
            result.add(new FocusGraph.Node(index,index-1,List.of(index+1),0,index,node.key(),node.settings()));
        }
        return result;
    }
    private int maxScroll() { return Math.max(0,draft.nodes().stream().mapToInt(FocusGraph.Node::y).max().orElse(0)*32-128); }
    private void scrollToSelection() {
        var node = selectedNode(); scrollY = Math.max(0,Math.min(maxScroll(),node == null ? 0 : node.y()*32-96));
    }
    private void rebuildSettings() {
        for (Button button : settingsButtons) removeWidget(button);
        settingsButtons.clear();
        var node = selectedNode(); if (node == null) return;
        var definition = FocusNodeRegistry.get(node.key()); if (definition == null) return;
        int row = 0;
        for (var setting : definition.settings().values()) {
            if (setting.research() != null && !menu.knowledge().isResearchCompleteStrict(setting.research())) continue;
            int current = node.settings().getOrDefault(setting.key(), setting.defaultValue());
            int index = Math.max(0, setting.values().indexOf(current));
            int y = topPos+imageHeight-45+row++*26;
            Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> changeSetting(setting,-1)).bounds(leftPos+231,y,16,16).build());
            Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> changeSetting(setting,1)).bounds(leftPos+281,y,16,16).build());
            settingsButtons.add(previous); settingsButtons.add(next);
        }
    }
    private void changeSetting(FocusNodeRegistry.Setting setting, int direction) {
        if (menu.busy() || menu.pending()) return;
        var node = selectedNode(); if (node == null) return;
        Map<String,Integer> changed = new LinkedHashMap<>(node.settings());
        int index = setting.values().indexOf(changed.getOrDefault(setting.key(), setting.defaultValue()));
        changed.put(setting.key(), setting.values().get(Math.floorMod(index+direction, setting.values().size())));
        draft = new FocusGraph(draft.nodes().stream().map(current -> current.id() == node.id()
                ? new FocusGraph.Node(current.id(),current.parent(),current.children(),current.x(),current.y(),current.key(),changed) : current).toList());
        dirty = true;
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {}
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        graphics.blit(BACK,leftPos,topPos,0,0,231,231,256,256);
        graphics.blit(INVENTORY,leftPos-71,topPos-3,0,0,71,239,256,256);
        graphics.enableScissor(leftPos+63,topPos+31,leftPos+199,topPos+191);
        for (var node : draft.nodes()) {
            int x = leftPos+132+node.x()*24, y = topPos+48+node.y()*32-scrollY;
            if (node.parent() >= 0) graphics.blit(FRAME,x-6,y-22,54,232,12,12,256,256);
            var definition = FocusNodeRegistry.get(node.key());
            if (definition != null) drawPart(graphics,definition,x,y,24,false,true);
            else graphics.blit(FRAME,x-12,y-12,120,232,24,24,256,256);
            if (selected == node.id() && node.id() != 0) graphics.blit(FRAME,x-12,y-12,96,232,24,24,256,256);
        }
        graphics.disableScissor();
        graphics.blit(FRAME,leftPos,topPos,0,0,231,231,256,256);
        graphics.blit(BASE,leftPos+24,topPos+8,192,224,8,14,256,256);
        for (int i = 1; i < 22; i++) graphics.blit(BASE,leftPos+24+i*8,topPos+8,200,224,8,14,256,256);
        graphics.blit(BASE,leftPos+200,topPos+8,208,224,8,14,256,256);
        List<FocusNodeRegistry.Definition> parts = parts();
        partsStart = Math.max(0, Math.min(partsStart, Math.max(0,parts.size()-6)));
        for (int i = 0; i < 6 && partsStart+i < parts.size(); i++) {
            var part = parts.get(partsStart+i);
            boolean enabled = part.runtimeSupported() && menu.knowledge().isResearchCompleteStrict(part.research());
            drawPart(graphics,part,leftPos+38,topPos+43+i*25,28,
                    hovered(mouseX,mouseY,leftPos+28,topPos+33+i*25,20,20),enabled);
        }
        if (parts.size() > 6) {
            graphics.fill(leftPos+51,topPos+31,leftPos+54,topPos+180,0x70434343);
            int y = topPos+31+(int)(135F*partsStart/Math.max(1,parts.size()-6));
            graphics.fill(leftPos+51,y,leftPos+54,y+14,0xFFC6C6A0);
        }
        graphics.blit(gui("complex"),leftPos+227,topPos+34,12,12,0,0,16,16,16,16);
        graphics.blit(gui("costxp"),leftPos+227,topPos+48,12,12,0,0,16,16,16,16);
        graphics.blit(gui("costvis"),leftPos+227,topPos+62,12,12,0,0,16,16,16,16);
        var compiled = compile();
        int complexity = compiled.success() ? compiled.plan().complexity() : editorComplexity();
        int xp = (int)Math.max(1,Math.round(Math.sqrt(complexity))), capacity = FocusStacks.maxComplexity(menu.focus());
        graphics.drawString(font,complexity+"/"+capacity,leftPos+242,topPos+36,complexity > capacity ? 0xF66F68 : 0xFFDDBF);
        graphics.drawString(font,Integer.toString(xp),leftPos+242,topPos+50,menu.creative() || xp <= menu.experienceLevel() ? 0x99FF9D : 0xF66F68);
        graphics.drawString(font,Integer.toString((int)(menu.busy() ? menu.remainingVis() : complexity*10+capacity/5)),leftPos+242,topPos+64,0x55FFFF);
        graphics.drawString(font,Component.translatable("item.Focus.cost1").getString()+": "+String.format(Locale.ROOT,"%.1f",complexity/5F),leftPos+230,topPos+80,0x55FFFF);
        Map<String,Integer> crystals = editorCrystals();
        if (!crystals.isEmpty()) graphics.drawString(font,Component.translatable("wandtable.text4"),leftPos+230,topPos+94,0xFFCC55);
        int i = 0;
        for (var entry : crystals.entrySet()) {
            ItemStack crystal = AspectCrystalItem.create(Aspect.getAspect(entry.getKey()),entry.getValue());
            int x = leftPos+234+(i%5)*16, y = topPos+110+(i/5)*16;
            graphics.renderItem(crystal,x,y); graphics.renderItemDecorations(font,crystal,x,y); i++;
        }
        if (menu.busy()) graphics.drawString(font,Component.translatable("gui.thaumcraft.focal.busy"),leftPos+230,topPos+146,0xAACCFF);
        else if (!menu.status().isEmpty() && !menu.status().equals("ACCEPTED")) {
            var lines = font.split(Component.translatable("gui.thaumcraft.focal.result."+menu.status().toLowerCase(Locale.ROOT)),98);
            for (int line = 0; line < Math.min(3,lines.size()); line++) graphics.drawString(font,lines.get(line),leftPos+230,topPos+146+line*10,0xEEA0A0);
        }
        var selectedNode = selectedNode();
        var definition = selectedNode == null ? null : FocusNodeRegistry.get(selectedNode.key());
        if (definition != null) {
            int row = 0;
            for (var setting : definition.settings().values()) {
                if (setting.research()!=null && !menu.knowledge().isResearchCompleteStrict(setting.research())) continue;
                int current = selectedNode.settings().getOrDefault(setting.key(),setting.defaultValue());
                int index = Math.max(0,setting.values().indexOf(current)), y = topPos+imageHeight-45+row++*26;
                graphics.drawString(font,Component.translatable(setting.label()),leftPos+231,y-10,0xFFCC55);
                graphics.drawCenteredString(font,Component.translatable(setting.descriptions().get(index)),leftPos+264,y+4,0xFFFFFF);
            }
        }
    }
    private int editorComplexity() {
        int total = 0; Map<String,Integer> seen = new HashMap<>();
        for (var node : draft.nodes()) {
            var definition = FocusNodeRegistry.get(node.key()); if (definition == null) continue;
            total += (int)(definition.complexity(node.settings())*(.5F*(seen.merge(node.key(),1,Integer::sum)+1)));
        }
        return total;
    }
    private Map<String,Integer> editorCrystals() {
        Map<String,Integer> result = new LinkedHashMap<>();
        for (var node : draft.nodes()) {
            var definition = FocusNodeRegistry.get(node.key());
            if (definition != null && definition.aspect()!=null) result.merge(definition.aspect(),1,Integer::sum);
        }
        return result;
    }
    private void drawPart(GuiGraphics graphics, FocusNodeRegistry.Definition part, int x, int y, int size, boolean hover, boolean enabled) {
        float brightness = enabled ? 1F : .35F;
        int color = part.color(); RenderSystem.setShaderColor(((color>>16)&255)/255F*brightness,((color>>8)&255)/255F*brightness,(color&255)/255F*brightness,1);
        boolean root = part.key().equals(FocusNodeRegistry.ROOT);
        if (!root && part.type() != FocusNodeRegistry.Type.MOD) graphics.blit(part.type() == FocusNodeRegistry.Type.EFFECT ? EFFECT : MEDIUM,
                x-size/2,y-size/2,size,size,0,0,32,32,32,32);
        RenderSystem.setShaderColor(brightness,brightness,brightness,1);
        int iconSize = root ? size : size/2+(hover?2:0);
        graphics.blit(part.icon(),x-iconSize/2,y-iconSize/2,iconSize,iconSize,0,0,32,32,32,32);
        RenderSystem.setShaderColor(1,1,1,1);
        if (!enabled) graphics.drawString(font,"×",x+7,y+4,0xB86B69);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics); super.render(graphics,mouseX,mouseY,partial); renderTooltip(graphics,mouseX,mouseY);
        List<FocusNodeRegistry.Definition> parts = parts();
        for (int i = 0; i < 6 && partsStart+i < parts.size(); i++) if (hovered(mouseX,mouseY,leftPos+28,topPos+33+i*25,20,20)) {
            var definition = parts.get(partsStart+i);
            List<Component> tooltip = new ArrayList<>(); tooltip.add(Component.translatable(definition.key()+".name"));
            tooltip.add(Component.translatable(definition.key()+".text"));
            if (!menu.knowledge().isResearchCompleteStrict(definition.research())) tooltip.add(Component.translatable("gui.thaumcraft.focal.research",definition.research()));
            if (!definition.runtimeSupported()) tooltip.add(Component.translatable("gui.thaumcraft.focal.unsupported"));
            graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);
        }
    }
    private static boolean hovered(double x, double y, int left, int top, int width, int height) { return x >= left && x < left+width && y >= top && y < top+height; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!menu.busy() && !menu.pending()) {
            for (var node : draft.nodes()) if (node.id() != 0 && hovered(x,y,leftPos+63,topPos+31,136,160)
                    && hovered(x,y,leftPos+122+node.x()*24,topPos+38+node.y()*32-scrollY,20,20)) {
                selected = node.id(); partsStart = 0; rebuildSettings(); if (button == 1) clearSelected(); return true;
            }
            var parts = parts();
            for (int i = 0; i < 6 && partsStart+i < parts.size(); i++) if (hovered(x,y,leftPos+28,topPos+33+i*25,20,20)) {
                if (button == 0) install(parts.get(partsStart+i)); return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (hovered(x,y,leftPos+20,topPos+30,38,155)) { partsStart += delta > 0 ? -1 : 1; return true; }
        if (hovered(x,y,leftPos+63,topPos+31,136,160)) { scrollY = Math.max(0,Math.min(maxScroll(),scrollY+(delta > 0 ? -16 : 16))); return true; }
        return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean keyPressed(int code, int scan, int modifiers) {
        if (name.isFocused() && code != 256) return name.keyPressed(code,scan,modifiers);
        return super.keyPressed(code,scan,modifiers);
    }
    /** Real QA drives the same editor method as a palette click, without global desktop input. */
    public void selectForSmoke(int node, String key) { selected = node; install(Objects.requireNonNull(FocusNodeRegistry.get(key))); }
    public void setNameForSmoke(String value) { name.setValue(value); }
    public FocusGraph draftForSmoke() { return draft; }
}
