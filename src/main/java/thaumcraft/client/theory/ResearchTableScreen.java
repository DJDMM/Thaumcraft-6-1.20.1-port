package thaumcraft.client.theory;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import thaumcraft.research.theory.TheoryAids;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.ResearchCategories;
import thaumcraft.research.theory.ResearchTableMenu;
import thaumcraft.research.theory.TheoryCard;
import thaumcraft.research.theory.TheoryModule;
import thaumcraft.research.theory.TheoryNetwork;
import thaumcraft.research.theory.TheorySession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** BETA26 desk, parchment offers and category percentages, driven only by server snapshots. */
public final class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final ResourceLocation DESK = texture("gui/gui_research_table.png");
    private static final ResourceLocation PAPER = texture("gui/paper.png");
    private static final ResourceLocation GILDED = texture("gui/papergilded.png");
    private static final ResourceLocation BASE = texture("gui/gui_base.png");
    private static final int LIGHT = 0xEADCB9, INK = 0x3F2D1E;
    private final Set<String> selectedAids = new HashSet<>();
    private static final List<String> AID_KEYS = List.copyOf(TheoryAids.keys());
    private final TheoryAidButton[] aidButtons = new TheoryAidButton[AID_KEYS.size()];
    private final TheoryCardAnimation animation = new TheoryCardAnimation();
    private final Map<String, Float> displayedTotals = new LinkedHashMap<>();
    private long lastFrame;
    private float frameSeconds;
    private int resolvingAnimations, drawnAnimations;
    private final TheoryButton[] choose = new TheoryButton[3];
    private TheoryButton start, draw, bonus, finish, scrap;
    private RequestSink previewSender;
    private boolean previewPending;

    @FunctionalInterface interface RequestSink {
        void send(int menuId, long revision, TheoryNetwork.Action action, int cardIndex, Set<String> aids);
    }

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // The desk remains 255x255; its original external category column also needs room.
        imageWidth = 385;
        imageHeight = 255;
    }

    @Override protected void init() {
        super.init();
        start = button(68, 159, 120, 17, "start", b -> submit(TheoryNetwork.Action.START, -1));
        draw = button(10, 163, 79, 14, "draw", b -> submit(TheoryNetwork.Action.DRAW, -1));
        bonus = button(89, 163, 79, 14, "draw_bonus", b -> submit(TheoryNetwork.Action.DRAW_BONUS, -1));
        scrap = button(168, 163, 77, 14, "scrap", b -> submit(TheoryNetwork.Action.SCRAP, -1));
        finish = button(53, 144, 150, 18, "finish", b -> submit(TheoryNetwork.Action.FINISH, -1));
        for (int i = 0; i < aidButtons.length; i++) {
            final String key = AID_KEYS.get(i);
            int columns = Math.min(6, Math.max(1, AID_KEYS.size()));
            int x = 128 - columns * 10 + i % columns * 20;
            int y = 99 + i / columns * 20;
            aidButtons[i] = addRenderableWidget(new TheoryAidButton(leftPos + x, topPos + y, aidIcon(key),
                    tr("aid." + key.toLowerCase(Locale.ROOT)), b -> {
                if (pending() || !menu.canUse() || menu.session() != null || !menu.availableAids().contains(key)) return;
                if (!selectedAids.remove(key) && selectedAids.size() + 1 < menu.inspirationAvailable()) selectedAids.add(key);
            }));
        }
        for (int i = 0; i < choose.length; i++) {
            final int index = i;
            choose[i] = button(0, 145, 72, 13, "select", b -> submit(TheoryNetwork.Action.SELECT, index));
        }
        updateControls();
    }

    private TheoryButton button(int x, int y, int width, int height, String key, Button.OnPress press) {
        return addRenderableWidget(new TheoryButton(leftPos + x, topPos + y, width, height, tr(key), press));
    }

    private boolean inkAvailable() {
        ItemStack ink = menu.getSlot(0).getItem();
        return ink.is(TheoryModule.SCRIBING_TOOLS.get()) && ink.getDamageValue() < ink.getMaxDamage();
    }

    private boolean paperAvailable() { return menu.getSlot(1).getItem().is(Items.PAPER); }
    private boolean pending() { return menu.requestPending() || previewPending; }

    private boolean canRequest(TheoryNetwork.Action action, int index) {
        if (pending() || !menu.canUse() || previewSender == null && animation.busy()) return false;
        TheorySession session = menu.session();
        return switch (action) {
            case START -> session == null && inkAvailable() && paperAvailable() && menu.inspirationAvailable() > selectedAids.size();
            case DRAW -> session != null && !session.complete() && session.choices().isEmpty() && paperAvailable();
            case DRAW_BONUS -> session != null && !session.complete() && session.choices().isEmpty() && paperAvailable() && session.bonusDraws() > 0;
            case SELECT -> {
                if (session == null || session.complete() || !inkAvailable() || index < 0 || index >= session.choices().size()) yield false;
                yield session.canSelect(menu.playerKnowledge(), menu.playerInventory(), menu.playerExperienceLevel(), index);
            }
            case FINISH -> session != null && session.complete();
            case SCRAP -> session != null && !session.complete();
        };
    }

    private void submit(TheoryNetwork.Action action, int index) {
        if (!canRequest(action, index)) return;
        if (action == TheoryNetwork.Action.START || action == TheoryNetwork.Action.SCRAP || action == TheoryNetwork.Action.FINISH) playPaper("clack", .4F);
        Set<String> aids = action == TheoryNetwork.Action.START ? Set.copyOf(selectedAids) : Set.of();
        if (previewSender != null) {
            previewPending = true;
            previewSender.send(menu.containerId, menu.revision(), action, index, aids);
        } else menu.request(action, index, aids);
        updateControls();
    }

    private void updateControls() {
        if (start == null) return;
        TheorySession session = menu.session();
        selectedAids.retainAll(menu.availableAids());
        boolean idle = session == null, complete = session != null && session.complete();
        start.visible = idle;
        draw.visible = bonus.visible = scrap.visible = !idle && !complete;
        finish.visible = complete && !animation.busy();
        start.active = canRequest(TheoryNetwork.Action.START, -1);
        draw.active = canRequest(TheoryNetwork.Action.DRAW, -1);
        bonus.active = canRequest(TheoryNetwork.Action.DRAW_BONUS, -1);
        scrap.active = canRequest(TheoryNetwork.Action.SCRAP, -1);
        finish.active = canRequest(TheoryNetwork.Action.FINISH, -1);
        int aidColumns = Math.min(6, Math.max(1, menu.availableAids().size()));
        int visibleAid = 0;
        for (int i = 0; i < aidButtons.length; i++) {
            String key = AID_KEYS.get(i);
            boolean selected = selectedAids.contains(key);
            aidButtons[i].visible = idle && menu.availableAids().contains(key);
            if (aidButtons[i].visible) {
                aidButtons[i].setX(leftPos + 128 - aidColumns * 10 + visibleAid % aidColumns * 20);
                aidButtons[i].setY(topPos + 99 + visibleAid / aidColumns * 20);
                visibleAid++;
            }
            aidButtons[i].active = !pending() && menu.canUse() && (selected || selectedAids.size() + 1 < menu.inspirationAvailable());
            aidButtons[i].selected = selected;
        }
        for (int i = 0; i < choose.length; i++) {
            choose[i].visible = session != null && !complete && !animation.busy() && i < session.choices().size();
            choose[i].active = canRequest(TheoryNetwork.Action.SELECT, i);
            int count = session == null ? 0 : session.choices().size();
            choose[i].setX(leftPos + cardX(i, count) + 7);
            choose[i].setWidth(cardWidth(count) - 14);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(DESK, leftPos, topPos, 0, 0, 255, 255, 256, 256);
        text(graphics, title, 44, 8, 170, 0.8F, LIGHT, true);
        TheorySession session = menu.session();
        int maximum = session == null ? menu.inspirationAvailable() : session.inspirationStart();
        int inspiration = session == null ? maximum - selectedAids.size() : session.inspiration();
        text(graphics, tr("inspiration", inspiration, maximum), 44, 23, 170, 0.85F, LIGHT, true);
        for (int i = 0; i < maximum; i++) {
            graphics.blit(BASE, leftPos + 128 - maximum * 6 + i * 12, topPos + 34, 10, 10,
                    inspiration > i ? 32F : 48F, 96F, 16, 16, 256, 256);
        }
        if (session == null) renderIdle(graphics);
        else if (!animation.cards().isEmpty() && previewSender == null) {
            for (int i = 0; i < animation.cards().size(); i++) renderAnimatedCard(graphics, animation.cards().get(i), i, animation.cards().size());
        } else if (session.complete()) renderComplete(graphics);
        else if (!session.choices().isEmpty()) {
            for (int i = 0; i < session.choices().size(); i++) renderCard(graphics, session.choices().get(i), i, session.choices().size());
        } else renderDraw(graphics, session);
        renderProgress(graphics, session);
        Component message = statusMessage();
        if (message != null) text(graphics, message, 8, 178, 238, 0.65F, pending() ? LIGHT : 0xFFBC9C, true);
    }

    private void renderIdle(GuiGraphics graphics) {
        parchment(graphics, PAPER, 43, 47, 172, 131);
        paragraph(graphics, tr("idle"), 60, 57, 141, 28, 0.72F, INK);
        text(graphics, tr("aids"), 57, 87, 144, 0.72F, INK, true);
        if (menu.availableAids().isEmpty()) text(graphics, tr("aid_none"), 57, 117, 144, 0.7F, INK, true);
    }

    private void renderDraw(GuiGraphics graphics, TheorySession session) {
        parchment(graphics, PAPER, 25, 51, 95, 105);
        parchment(graphics, PAPER, 28, 49, 95, 105);
        text(graphics, Component.literal("?"), 38, 78, 76, 3F, INK, true);
        text(graphics, tr("draw_hint"), 36, 128, 79, 0.65F, INK, true);
        if (session.lastCard() != null) {
            parchment(graphics, session.lastCard().fromAid() ? GILDED : PAPER, 139, 51, 91, 106);
            paragraph(graphics, session.lastCard().title(), 151, 66, 67, 30, 0.72F, INK);
            paragraph(graphics, session.lastCard().description(), 151, 95, 67, 44, 0.65F, INK);
        }
        renderBonus(graphics, session);
    }

    private void renderComplete(GuiGraphics graphics) {
        parchment(graphics, GILDED, 37, 47, 182, 116);
        paragraph(graphics, tr("finish_hint"), 57, 65, 141, 37, 0.85F, INK);
        paragraph(graphics, tr("finish_rules"), 57, 106, 141, 32, 0.67F, INK);
    }

    private void renderCard(GuiGraphics graphics, TheoryCard card, int index, int count) {
        int x = cardX(index, count), width = cardWidth(count);
        float scale = count > 2 ? 0.64F : 0.77F;
        parchment(graphics, card.fromAid() ? GILDED : PAPER, x, 44, width, 117);
        paragraph(graphics, card.title(), x + 8, 53, width - 16, 21, scale, INK);
        boolean resolving = previewSender == null && animation.phase() == TheoryCardAnimation.Phase.RESOLVE;
        boolean items = !resolving && !card.requiredItems().isEmpty();
        boolean xp = !resolving && (card.requiredLevels() > 0 || card.id().equals("dark_whispers"));
        paragraph(graphics, card.description(), x + 8, 76, width - 16, items || xp ? 30 : 42, scale, INK);
        // Paid offers are detached animation data. Current depleted supplies and XP
        // must not turn them into new requirements or a misleading zero-cost offer.
        if (resolving) return;
        if (items) renderRequiredItems(graphics, card, x, width);
        if (card.requiredLevels() > 0 && !card.id().equals("spellbinding")) text(graphics, tr("levels", card.requiredLevels(), menu.playerExperienceLevel()), x + 7, 112, width - 14, 0.68F,
                menu.playerExperienceLevel() < card.requiredLevels() ? 0xA5372B : 0x437632, true);
        text(graphics, tr(card.cost() < 0 ? "refund" : "cost", Math.abs(card.cost())), x + 7, items ? 129 : 124, width - 14, 0.64F, 0x563A21, true);
        text(graphics, cardInkCost(card), x + 7, items ? 137 : 134, width - 14, .60F,
                !inkAvailable() ? 0xA5372B : 0x6F573F, true);
        if (card.id().equals("dark_whispers")) text(graphics, tr("levels_all", menu.playerExperienceLevel()), x + 7, 112, width - 14, .6F, 0xA5372B, true);
        if (card.id().equals("spellbinding")) text(graphics, tr("levels_upto", 5, menu.playerExperienceLevel()), x + 7, 112, width - 14, .6F, menu.playerExperienceLevel() == 0 ? 0xA5372B : 0x437632, true);
    }

    private Component cardInkCost(TheoryCard card) {
        if (card.tablePaperExtra() == 0 && card.tableInkExtra() == 0) return tr("ink_cost");
        ItemStack tools = menu.getSlot(0).getItem();
        return tr("scripting_cost", Math.min(1 + card.tableInkExtra(), tools.getMaxDamage() - tools.getDamageValue()),
                Math.min(menu.getSlot(1).getItem().getCount(), card.tablePaperExtra()));
    }

    private void renderRequiredItems(GuiGraphics graphics, TheoryCard card, int x, int width) {
        List<TheoryCard.RequiredItem> requirements = card.requiredItems();
        for (int i = 0; i < requirements.size(); i++) {
            var required = requirements.get(i);
            ItemStack wanted = required.stack();
            int itemX = requiredItemX(x, width, requirements.size(), i);
            long owned = requiredCount(wanted);
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + itemX, topPos + 108, 4);
            graphics.pose().scale(0.75F, 0.75F, 1);
            graphics.renderItem(wanted, 0, 0);
            graphics.pose().popPose();
            if (required.consumed()) text(graphics, Component.literal("!").withStyle(ChatFormatting.BOLD), itemX + 10, 106 + Math.round((float)Math.sin((System.nanoTime() / 100_000_000D + i) / 2)), 5, 0.78F, 0xD6A723, true);
            text(graphics, Component.literal(Math.min(owned, wanted.getCount()) + "/" + wanted.getCount()), itemX - 2, 121, 19, 0.58F,
                    owned < wanted.getCount() ? 0xA5372B : 0x437632, true);
        }
    }

    private long requiredCount(ItemStack wanted) {
        return menu.playerInventory().items.stream().filter(found -> TheoryCard.matchesRequirement(found, wanted)).mapToLong(ItemStack::getCount).sum();
    }
    private static int requiredItemX(int cardX, int width, int count, int index) { return cardX + width / 2 - count * 12 + index * 24 + 4; }

    private void renderProgress(GuiGraphics graphics, TheorySession session) {
        parchment(graphics, PAPER, 257, 0, 128, 255);
        text(graphics, tr("progress"), 271, 17, 99, 0.85F, INK, true);
        int y = 32, rank = 0;
        if (session == null || session.totals().isEmpty()) {
            paragraph(graphics, tr("empty_progress"), 272, y, 96, 28, 0.78F, INK);
        } else {
            for (var reward : session.rewards().entrySet()) {
                String category = reward.getKey();
                categoryIcon(graphics, category, 269, y, 13);
                text(graphics, categoryName(category), 285, y, 85, 0.7F, session.blocked().contains(category) ? 0x887A66 : INK, false);
                int percentage = Math.round(displayedTotals.getOrDefault(category, (float)session.totals().get(category)));
                text(graphics, tr("progress_row", percentage + "%", reward.getValue()), 285, y + 9, 85, 0.68F,
                        rank > session.penaltyStart() ? 0x8D613B : 0x3D6E33, false);
                graphics.fill(leftPos + 285, topPos + y + 17, leftPos + 370, topPos + y + 20, 0x443F2D1E);
                int fill = Math.round(85 * Math.min(percentage, 100) / 100F);
                graphics.fill(leftPos + 285, topPos + y + 17, leftPos + 285 + fill, topPos + y + 20,
                        session.blocked().contains(category) ? 0xAA887A66 : rank > session.penaltyStart() ? 0xAA8D613B : 0xAA3D6E33);
                if (percentage < session.totals().get(category) && fill > 0) {
                    int phase = (int)(System.nanoTime() / 80_000_000L % 5);
                    graphics.fill(leftPos + 284 + fill, topPos + y + 16 - phase % 3, leftPos + 286 + fill, topPos + y + 18 - phase % 3, 0xFFDDB44E);
                }
                y += 24;
                rank++;
            }
        }
        graphics.fill(leftPos + 272, topPos + 202, leftPos + 370, topPos + 203, 0x665A4126);
        text(graphics, tr("knowledge"), 272, 208, 96, 0.76F, INK, true);
        long total = ResearchCategories.keys().stream().mapToLong(category -> menu.playerKnowledge().rawKnowledge(KnowledgeType.THEORY, category)).sum();
        text(graphics, Component.literal(total + " / 32"), 272, 223, 96, 0.85F, INK, true);
    }

    private Component statusMessage() {
        if (pending()) return tr("wait");
        if (!menu.canUse()) return tr("unavailable");
        TheorySession session = menu.session();
        if (session == null || !session.complete()) {
            if (!inkAvailable()) return tr("no_ink");
            if (!paperAvailable() && (session == null || session.choices().isEmpty())) return tr("no_paper");
        }
        String result = menu.status();
        if (result != null && !result.isBlank() && !result.equals("ACCEPTED")) return tr("result." + result.toLowerCase(Locale.ROOT));
        return null;
    }

    private void parchment(GuiGraphics graphics, ResourceLocation resource, int x, int y, int width, int height) {
        // Both original papers have a 26px transparent margin. Map the measured paper
        // bounds to the layout rectangle so content padding refers to the visible page.
        graphics.blit(resource, leftPos + x, topPos + y, width, height, 26F, 7F, 204, 240, 256, 256);
    }

    private void text(GuiGraphics graphics, Component message, int x, int y, int width, float wantedScale, int color, boolean centered) {
        float scale = Math.min(wantedScale, width / (float) Math.max(1, font.width(message)));
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + x + (centered ? width / 2F : 0), topPos + y, 1);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, message, centered ? -font.width(message) / 2 : 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void paragraph(GuiGraphics graphics, Component message, int x, int y, int width, int height, float scale, int color) {
        List<FormattedCharSequence> lines = font.split(message, (int) (width / scale));
        int maximum = Math.max(1, (int) (height / (font.lineHeight * scale)));
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + x, topPos + y, 1);
        graphics.pose().scale(scale, scale, 1);
        for (int i = 0; i < Math.min(lines.size(), maximum); i++) {
            if (i == maximum - 1 && lines.size() > maximum) graphics.drawString(font, "…", 0, i * font.lineHeight, color, false);
            else graphics.drawString(font, lines.get(i), 0, i * font.lineHeight, color, false);
        }
        graphics.pose().popPose();
    }

    private void categoryIcon(GuiGraphics graphics, String category, int x, int y, int size) {
        ResourceLocation resource = category.equals("BASICS") ? texture("items/thaumonomicon_cheat.png")
                : texture("research/cat_" + category.toLowerCase(Locale.ROOT) + ".png");
        graphics.blit(resource, leftPos + x, topPos + y, size, size, 0F, 0F, 256, 256, 256, 256);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (smokePointer != null) { mouseX = smokePointer[0]; mouseY = smokePointer[1]; }
        renderBackground(graphics);
        updateAnimation(mouseX, mouseY);
        updateControls();
        float scale = layoutScale();
        int adjustedX = (int) localX(mouseX), adjustedY = (int) localY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(width * (1 - scale) / 2F, height * (1 - scale) / 2F, 0);
        graphics.pose().scale(scale, scale, 1);
        super.render(graphics, adjustedX, adjustedY, partialTick);
        renderTooltip(graphics, adjustedX, adjustedY);
        renderHints(graphics, adjustedX, adjustedY);
        graphics.pose().popPose();
    }

    private void renderHints(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isHovering(16, 15, 16, 16, mouseX, mouseY) && !menu.getSlot(0).hasItem()) tooltip(graphics, List.of(tr("ink_slot")), mouseX, mouseY);
        else if (isHovering(224, 16, 16, 16, mouseX, mouseY) && !menu.getSlot(1).hasItem()) tooltip(graphics, List.of(tr("paper_slot")), mouseX, mouseY);
        else if (draw.visible && draw.isHovered() || bonus.visible && bonus.isHovered()) tooltip(graphics, List.of(tr("draw_cost")), mouseX, mouseY);
        for (int i = 0; i < aidButtons.length; i++) if (aidButtons[i].visible && aidButtons[i].isHovered()) {
            List<Component> lines = new ArrayList<>(List.of(tr("aid." + AID_KEYS.get(i).toLowerCase(Locale.ROOT)), tr("aid_inspiration")));
            for (String card : TheoryAids.cards(AID_KEYS.get(i)).stream().distinct().toList()) {
                String translation = switch (card) { case "dark_whispers" -> "darkwhisper"; case "glyphs" -> "glyph"; case "mind_over_matter" -> "mindmatter"; default -> card; };
                lines.add(Component.translatable("card." + translation + ".name", tr(card.equals("channel") ? "aid_any_aspect" : "aid_any_category")));
            }
            tooltip(graphics, lines, mouseX, mouseY);
        }
        TheorySession session = menu.session();
        if (session != null && (previewSender != null || !animation.busy())) for (int i = 0; i < session.choices().size(); i++) {
            TheoryCard card = session.choices().get(i);
            List<TheoryCard.RequiredItem> items = card.requiredItems();
            for (int item = 0; item < items.size(); item++) {
                int x = requiredItemX(cardX(i, session.choices().size()), cardWidth(session.choices().size()), items.size(), item);
                if (!isHovering(x - 2, 106, 18, 21, mouseX, mouseY)) continue;
                var required = items.get(item);
                ItemStack wanted = required.stack();
                List<Component> lines = new ArrayList<>(wanted.getTooltipLines(minecraft.player, TooltipFlag.NORMAL));
                lines.add(tr(required.consumed() ? "consumed" : "required", wanted.getCount()).copy().withStyle(required.consumed() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                lines.add(tr("owned", requiredCount(wanted), wanted.getCount()));
                if (!card.hasRequiredItems(menu.playerInventory())) lines.add(tr("card_requirements_missing").copy().withStyle(ChatFormatting.RED));
                tooltip(graphics, lines, mouseX, mouseY);
                return;
            }
            if (!isHovering(cardX(i, session.choices().size()), 44, cardWidth(session.choices().size()), 98, mouseX, mouseY)) continue;
            List<Component> lines = new ArrayList<>(List.of(card.title(), card.description(), tr(card.cost() < 0 ? "refund" : "cost", Math.abs(card.cost())), cardInkCost(card)));
            if (card.requiredLevels() > 0) lines.add(tr("levels", card.requiredLevels(), menu.playerExperienceLevel()));
            if (card.id().equals("dark_whispers")) lines.add(tr("levels_all", menu.playerExperienceLevel()));
            if (card.id().equals("spellbinding")) lines.add(tr("levels_upto", 5, menu.playerExperienceLevel()));
            if (!canRequest(TheoryNetwork.Action.SELECT, i) && !pending()) lines.add(tr("card_requirements_missing").copy().withStyle(ChatFormatting.RED));
            for (var required : items) {
                lines.addAll(required.stack().getTooltipLines(minecraft.player, TooltipFlag.NORMAL));
                lines.add(tr(required.consumed() ? "consumed" : "required", required.stack().getCount()).copy().withStyle(required.consumed() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
            if (card.id().equals("analyze")) lines.add(tr("analyze_cost", categoryName(card.category())));
            if (card.fromAid()) lines.add(tr("aid_card"));
            tooltip(graphics, lines, mouseX, mouseY);
        }
    }

    private void tooltip(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
        int wrap = Math.min(220, (int) (width / layoutScale()) - 24);
        List<FormattedCharSequence> wrapped = lines.stream().flatMap(line -> font.split(line, wrap).stream()).toList();
        graphics.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    private float layoutScale() { return Math.min(1F, Math.min((width - 12F) / imageWidth, (height - 12F) / imageHeight)); }
    private double localX(double x) { float scale = layoutScale(); return (x - width * (1 - scale) / 2F) / scale; }
    private double localY(double y) { float scale = layoutScale(); return (y - height * (1 - scale) / 2F) / scale; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        updateControls();
        double lx = localX(x), ly = localY(y);
        if (button == 0 && super.mouseClicked(lx, ly, button)) return true;
        TheorySession session = menu.session();
        if (button == 0 && session != null && !session.complete() && canRequest(TheoryNetwork.Action.DRAW, -1)
                && isHovering(25, 51, 95, 105, lx, ly)) {
            submit(session.bonusDraws() > 0 ? TheoryNetwork.Action.DRAW_BONUS : TheoryNetwork.Action.DRAW, -1); return true;
        }
        if (button == 0 && session != null && !session.choices().isEmpty()) {
            int count = session.choices().size();
            for (int i = 0; i < count; i++) if (isHovering(cardX(i, count), 44, cardWidth(count), 98, lx, ly)
                    && canRequest(TheoryNetwork.Action.SELECT, i)) { submit(TheoryNetwork.Action.SELECT, i); return true; }
        }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(localX(x), localY(y), button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(localX(x), localY(y), button, dx / layoutScale(), dy / layoutScale()); }

    private static int cardWidth(int count) { return count > 2 ? 80 : 116; }
    private static int cardX(int index, int count) { return count > 2 ? 7 + index * 81 : count == 1 ? 69 : 10 + index * 120; }
    private static ResourceLocation texture(String path) { return new ResourceLocation("thaumcraft", "textures/" + path); }
    private static Component categoryName(String category) { return Component.translatable("tc.research_category." + category); }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.thaumcraft.theory." + key, args); }

    void pointerForSmokeTest(int[] point) { requireSmokeTest(); smokePointer = point; }
    private int[] smokePointer;

    private float updateAnimation(int mouseX, int mouseY) {
        long now = System.nanoTime();
        boolean initial = lastFrame == 0;
        frameSeconds = lastFrame == 0 ? 0 : Math.min(0.05F, (now - lastFrame) / 1_000_000_000F); lastFrame = now;
        if (previewSender == null) {
            var before = animation.phase();
            animation.sync(menu.session());
            if (animation.phase() == TheoryCardAnimation.Phase.DEAL && before != animation.phase()) { drawnAnimations++; playPaper("page", 1); }
            if (animation.phase() == TheoryCardAnimation.Phase.RESOLVE && before != animation.phase()) { resolvingAnimations++; playPaper("pageturn", 1); }
            var advancing = animation.phase();
            animation.advance(frameSeconds, (float)(localX(mouseX) - leftPos), (float)(localY(mouseY) - topPos));
            if (advancing == TheoryCardAnimation.Phase.RESOLVE && animation.phase() == TheoryCardAnimation.Phase.NONE) playPaper("write", .3F);
        }
        var totals = menu.session() == null ? Map.<String, Integer>of() : menu.session().totals();
        displayedTotals.keySet().retainAll(totals.keySet());
        totals.forEach((key, target) -> {
            float current = displayedTotals.getOrDefault(key, initial ? (float)target : 0F);
            float step = frameSeconds * 20;
            displayedTotals.put(key, current < target ? Math.min(target, current + step) : Math.max(target, current - step));
        });
        return frameSeconds;
    }

    private void renderAnimatedCard(GuiGraphics graphics, TheoryCard card, int index, int count) {
        float deal = animation.deal(index), resolve = animation.resolution(), hover = animation.hover(index);
        int x = cardX(index, count), width = cardWidth(count);
        float targetX = x + width / 2F, targetY = 102.5F;
        float cx = Mth.lerp(deal, 65F, targetX), cy = Mth.lerp(deal, 104F, targetY) - hover * 5;
        float size = Mth.lerp(deal, .78F, 1F) + hover * .035F;
        float alpha = 1;
        if (animation.phase() == TheoryCardAnimation.Phase.RESOLVE) {
            if (animation.selected(index)) { cx = Mth.lerp(resolve, targetX, 184.5F); cy = Mth.lerp(resolve, cy, 104F); size *= Mth.lerp(resolve, 1, .78F); }
            else alpha = 1 - resolve;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + cx, topPos + cy, 10 + index);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float)(new java.util.Random(card.seed()).nextGaussian() * 4) * (1 - deal + resolve)));
        graphics.pose().scale(size, size, 1);
        graphics.pose().translate(-(leftPos + targetX), -(topPos + targetY), 0);
        graphics.setColor(1, 1, 1, alpha);
        renderCard(graphics, card, index, count);
        graphics.setColor(1, 1, 1, 1);
        graphics.pose().popPose();
    }

    private void renderBonus(GuiGraphics graphics, TheorySession session) {
        int visible = Math.min(8, session.bonusDraws());
        for (int i = 0; i < visible; i++) graphics.blit(BASE, leftPos + 13 + i * 2, topPos + 147 + i / 2, 12, 12, 64F, 96F, 16, 16, 256, 256);
        text(graphics, tr("bonus", session.bonusDraws()), 38, 151, 97, .63F, LIGHT, false);
    }

    private void playPaper(String key, float volume) {
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", key)), 1, volume));
    }

    private static ItemStack aidIcon(String key) {
        var block = TheoryAids.block(key);
        if (block != null && block.asItem() != Items.AIR) return new ItemStack(block);
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("nether")) return new ItemStack(Items.OBSIDIAN);
        if (lower.contains("end")) return new ItemStack(Items.ENDER_EYE);
        if (lower.contains("crimson")) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft", "cultist_portal_lesser_spawn_egg"));
            return new ItemStack(item == Items.AIR ? Items.WRITTEN_BOOK : item);
        }
        return new ItemStack(Items.WRITTEN_BOOK);
    }
    int resolvingAnimationsForSmokeTest() { requireSmokeTest(); return resolvingAnimations; }
    int drawnAnimationsForSmokeTest() { requireSmokeTest(); return drawnAnimations; }
    boolean animationBusyForSmokeTest() { requireSmokeTest(); return animation.busy(); }
    int aidIconsForSmokeTest() { requireSmokeTest(); return (int)java.util.Arrays.stream(aidButtons).filter(b -> b.visible).count(); }
    boolean hoverRaisedForSmokeTest(int index) { requireSmokeTest(); return animation.hover(index) > .2F; }
    int[] cardPointForSmokeTest(int index) { requireSmokeTest(); return realPointForSmokeTest(leftPos + cardX(index, menu.session().choices().size()) + 20, topPos + 86); }

    void senderForSmokeTest(RequestSink sink) {
        requireSmokeTest();
        previewSender = sink;
    }
    void applyForSmokeTest(CompoundTag state) {
        requireSmokeTest();
        menu.setState(state);
        if (state.contains("Result")) previewPending = false;
        updateControls();
    }
    void actionForSmokeTest(TheoryNetwork.Action action, int index) { requireSmokeTest(); submit(action, index); }
    boolean clickActionForSmokeTest(TheoryNetwork.Action action, int index) {
        requireSmokeTest();
        updateControls();
        TheoryButton control = switch (action) {
            case START -> start;
            case DRAW -> draw;
            case DRAW_BONUS -> bonus;
            case FINISH -> finish;
            case SCRAP -> scrap;
            case SELECT -> choose[index];
        };
        if (!control.visible || !control.active) return false;
        float scale = layoutScale();
        double x = width * (1 - scale) / 2F + (control.getX() + control.getWidth() / 2D) * scale;
        double y = height * (1 - scale) / 2F + (control.getY() + control.getHeight() / 2D) * scale;
        return mouseClicked(x, y, 0);
    }
    boolean clickAidForSmokeTest(String key) {
        requireSmokeTest(); updateControls();
        int index = AID_KEYS.indexOf(key);
        if (index < 0 || !aidButtons[index].visible || !aidButtons[index].active) return false;
        TheoryAidButton control = aidButtons[index];
        int[] point = realPointForSmokeTest(control.getX() + control.getWidth() / 2, control.getY() + control.getHeight() / 2);
        return mouseClicked(point[0], point[1], 0);
    }
    int[] hoverForSmokeTest(boolean requiredItem) {
        requireSmokeTest();
        int count = menu.session().choices().size();
        int x = requiredItem ? requiredItemX(cardX(0, count), cardWidth(count), menu.session().choices().get(0).requiredItems().size(), 0) + 6 : cardX(0, count) + 20;
        return realPointForSmokeTest(leftPos + x, topPos + (requiredItem ? 114 : 86));
    }
    private int[] realPointForSmokeTest(int x, int y) {
        float scale = layoutScale();
        return new int[]{Math.round(width * (1 - scale) / 2F + x * scale), Math.round(height * (1 - scale) / 2F + y * scale)};
    }
    Set<String> selectedAidsForSmokeTest() { return Set.copyOf(selectedAids); }
    boolean availableForSmokeTest(TheoryNetwork.Action action, int index) { return canRequest(action, index); }
    boolean pendingForSmokeTest() { return pending(); }
    boolean layoutFitsForSmokeTest() { return imageWidth * layoutScale() <= width - 11 && imageHeight * layoutScale() <= height - 11; }
    boolean slotHitboxesForSmokeTest() {
        float scale = layoutScale();
        for (var slot : menu.slots) {
            double x = width * (1 - scale) / 2F + (leftPos + slot.x + 8) * scale;
            double y = height * (1 - scale) / 2F + (topPos + slot.y + 8) * scale;
            if (!isHovering(slot.x, slot.y, 16, 16, localX(x), localY(y))) return false;
        }
        return true;
    }
    private static void requireSmokeTest() { if (!Boolean.getBoolean("thaumcraft.theorySmokeTest") && !Boolean.getBoolean("thaumcraft.theoryCompleteSmokeTest")) throw new IllegalStateException("Theory smoke preview is disabled"); }
}
