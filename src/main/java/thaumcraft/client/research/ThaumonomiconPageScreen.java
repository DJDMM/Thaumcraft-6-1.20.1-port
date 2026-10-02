package thaumcraft.client.research;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.world.WorldModule;
import org.lwjgl.glfw.GLFW;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchEntry;
import thaumcraft.research.ResearchNetwork;
import thaumcraft.research.ResearchProgression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A paginated parchment reader. Legacy requirements are reference material, never unlock commands. */
public final class ThaumonomiconPageScreen extends Screen {
    private static final ResourceLocation BOOK = texture("gui/gui_researchbook.png");
    private static final ResourceLocation OVERLAY = texture("gui/gui_researchbook_overlay.png");
    private static final int BOOK_WIDTH = 400, BOOK_HEIGHT = 284;
    private static final int PAGE_WIDTH = 157, CONTENT_TOP = 58, CONTENT_BOTTOM = 244;
    private static final int INK = 0x382818, FADED = 0x77553B;
    private static final Pattern MARKUP = Pattern.compile("(?is)<IMG>(.*?)</IMG>|<(BR|LINE|DIV|PAGE)\\s*/?>");
    private static final Pattern ASPECT = Pattern.compile("key\\s*:\\s*['\"]([^'\"]+)['\"]");
    private final ThaumonomiconScreen browser;
    private final ResearchEntry entry;
    private final boolean archiveMode;
    private final List<ResearchEntry.Stage> chapters = new ArrayList<>();
    private final List<List<Piece>> pages = new ArrayList<>();
    private final List<Control> controls = new ArrayList<>();
    private final List<ItemHover> itemHovers = new ArrayList<>();
    private PlayerKnowledge knowledge;
    private int scans, chapter, spread, cursorY, visibleStage = -1;
    private float scale = 1, left, top;
    private boolean requestPending;
    private String resultMessage;
    private BiConsumer<String, Integer> smokeSender;

    public ThaumonomiconPageScreen(ThaumonomiconScreen browser, ResearchEntry entry,
                                  PlayerKnowledge knowledge, int scans) {
        super(Component.translatable(entry.title()));
        this.browser = browser;
        this.entry = entry;
        this.archiveMode = browser.archiveMode();
        this.knowledge = knowledge;
        this.scans = scans;
        rebuildChapters();
    }

    public void update(PlayerKnowledge knowledge, int scans) {
        this.knowledge = knowledge;
        this.scans = scans;
        browser.update(knowledge, scans);
        rebuildChapters();
        if (font != null) buildPages();
    }

    /** Only the response to an explicit request releases the pending action. Ordinary snapshots do not. */
    public void requestResult(String result) {
        requestPending = false;
        resultMessage = switch (result) {
            case "STARTED", "ADVANCED", "COMPLETE" -> null;
            case "STALE" -> "result_stale";
            case "MISSING_REQUIREMENTS" -> "requirements_missing";
            case "LOCKED" -> "result_locked";
            case "UNSUPPORTED" -> "result_unsupported";
            case "NO_BOOK" -> "result_no_book";
            default -> "result_retry";
        };
    }

    private boolean legacyLesson() {
        return !archiveMode && entry.category().equals("PORT") && entry.supported()
                && ResearchProgression.legacyLessonAvailable(knowledge, entry.key());
    }

    private boolean complete() {
        return legacyLesson() ? knowledge.knowsResearch(entry.key()) : ResearchProgression.isComplete(knowledge, entry.key());
    }

    private void rebuildChapters() {
        int stage = ResearchProgression.stage(knowledge, entry.key());
        if (stage != visibleStage) { chapter = 0; spread = 0; visibleStage = stage; }
        chapters.clear();
        if (archiveMode) {
            chapters.addAll(entry.stages());
            chapters.addAll(entry.addenda());
        } else if (legacyLesson()) {
            chapters.addAll(entry.stages());
        } else if (stage > 0 && !entry.stages().isEmpty()) {
            chapters.add(entry.stages().get(Math.min(stage, entry.stages().size()) - 1));
            if (complete()) entry.addenda().stream()
                    .filter(addendum -> addendum.requiredResearch().stream().allMatch(knowledge::isResearchCompleteStrict))
                    .forEach(chapters::add);
        }
        chapter = Math.max(0, Math.min(chapter, chapters.size() - 1));
    }

    private boolean actionAvailable() {
        if (archiveMode || requestPending || complete()) return false;
        if (legacyLesson()) return scans >= entry.scans() && knowledge.discoveredAspects().size() >= entry.aspects()
                && entry.parents().stream().allMatch(knowledge::knowsResearch);
        if (!ResearchProgression.isImplemented(entry.key())) return false;
        return visibleStage == 0 ? ResearchProgression.canStart(knowledge, entry.key())
                : ResearchProgression.canAdvance(knowledge, entry);
    }

    private void requestAdvance() {
        // Re-check at invocation: cached rendered controls must not submit a second click or a stale snapshot.
        if (!actionAvailable()) return;
        if (smokeSender == null && (minecraft == null || minecraft.player == null || minecraft.getConnection() == null)) {
            resultMessage = "result_no_world";
            return;
        }
        requestPending = true;
        resultMessage = null;
        int expectedStage = ResearchProgression.stage(knowledge, entry.key());
        if (smokeSender != null) smokeSender.accept(entry.key(), expectedStage);
        else if (legacyLesson()) ResearchNetwork.requestDiscover(entry.key());
        else ResearchNetwork.requestAdvance(entry.key(), expectedStage);
    }

    @Override
    protected void init() {
        scale = Math.max(0.2f, Math.min(1.25f, Math.min((width - 16f) / BOOK_WIDTH, (height - 64f) / BOOK_HEIGHT)));
        left = (width - BOOK_WIDTH * scale) / 2f;
        top = (height - BOOK_HEIGHT * scale) / 2f;
        buildPages();
    }

    private void buildPages() {
        pages.clear();
        newPage();
        if (archiveMode) {
            paragraph(tr("archive_notice"), FADED);
            divider(false);
        } else if (legacyLesson()) {
            paragraph(Component.translatable("thaumcraft.research.progress", scans, entry.scans(),
                    knowledge.discoveredAspects().size(), entry.aspects()).getString(), FADED);
            if (!entry.parents().isEmpty()) paragraph(Component.translatable("thaumcraft.research.parents",
                    String.join(", ", entry.parents().stream().map(this::researchName).toList())).getString(), FADED);
            divider(false);
        } else if (visibleStage == 0) {
            paragraph(progress("start_notice"), INK);
            if (!entry.parents().isEmpty()) paragraph(Component.translatable("thaumcraft.research.parents",
                    String.join(", ", entry.parents().stream().map(this::researchName).toList())).getString(), FADED);
        } else if (!complete()) {
            paragraph(progress("current_stage", Math.min(visibleStage, entry.stages().size()), entry.stages().size()), FADED);
            divider(false);
        }
        if (!archiveMode && !legacyLesson()) {
            paragraph(knowledgeBalance(entry.category()), FADED);
            divider(false);
        }
        if (!chapters.isEmpty()) {
            ResearchEntry.Stage stage = chapters.get(chapter);
            String text = I18n.exists(stage.text()) ? Component.translatable(stage.text()).getString() : tr("text_unavailable");
            markup(text);
            if ((archiveMode || legacyLesson() || !complete())
                    && (!stage.requirements().isEmpty() || !stage.requiredResearch().isEmpty() || stage.warp() > 0)) {
                divider(true);
                paragraph(tr(archiveMode ? "requirements_reference" : "requirements"), INK);
                for (String requirement : stage.requirements()) paragraph(requirementText(requirement), FADED);
                // Stage research is separate from the flattened legacy requirements, so avoid showing it twice.
                if (stage.requirements().stream().noneMatch(value -> value.startsWith("required_research:"))) {
                    for (String key : stage.requiredResearch()) paragraph(tr("research_requirement", researchName(key)), FADED);
                }
                if (stage.warp() > 0) paragraph(tr("warp_reference", stage.warp()), 0x754281);
                if (!archiveMode && !legacyLesson() && !complete()
                        && stage.requirements().stream().anyMatch(value -> value.startsWith("required_knowledge:")))
                    paragraph(progress("knowledge_spent"), FADED);
            }
            if (!stage.recipes().isEmpty()) {
                divider(true);
                paragraph(archiveMode ? tr("recipes_reference") : progress("recipes"), INK);
                java.util.Set<ResourceLocation> shown = new java.util.HashSet<>();
                for (String recipe : stage.recipes()) {
                    List<BookRecipeViews.View> views = BookRecipeViews.resolve(recipe);
                    if (views.isEmpty()) paragraph("• " + recipeName(recipe) + " — " + tr("recipe_unavailable"), FADED);
                    for (BookRecipeViews.View view : views) if (shown.add(view.id())) {
                        int height = view.kind().equals("salis") ? 176 : 140;
                        fit(height);
                        pages.get(pages.size() - 1).add(new RecipePiece(cursorY, view));
                        cursorY += height;
                    }
                }
            }
        }
        while (pages.size() > 1 && pages.get(pages.size() - 1).isEmpty()) pages.remove(pages.size() - 1);
        spread = Math.min(spread, (pages.size() - 1) / 2);
    }

    private void markup(String raw) {
        raw = raw.replace("\\n", "\n");
        Matcher matcher = MARKUP.matcher(raw);
        int from = 0;
        while (matcher.find()) {
            text(raw.substring(from, matcher.start()), INK);
            if (matcher.group(1) != null) addImage(matcher.group(1));
            else switch (matcher.group(2).toUpperCase(Locale.ROOT)) {
                case "BR" -> gap(7);
                case "LINE" -> divider(false);
                case "DIV" -> divider(true);
                case "PAGE" -> newPage();
                default -> { }
            }
            from = matcher.end();
        }
        text(raw.substring(from), INK);
    }

    private void text(String text, int color) {
        // Retain vanilla section-sign text formatting, but never render untranslated markup or JSON payloads.
        String clean = text.replaceAll("<[^>]*>", "").trim();
        if (clean.isBlank()) return;
        for (String line : clean.split("\n", -1)) {
            if (line.isEmpty()) gap(5);
            else for (FormattedCharSequence wrapped : font.split(Component.literal(line), PAGE_WIDTH)) {
                fit(font.lineHeight + 1);
                pages.get(pages.size() - 1).add(new TextPiece(cursorY, wrapped, color));
                cursorY += font.lineHeight + 1;
            }
        }
    }

    private void paragraph(String text, int color) { text(text, color); gap(6); }
    private void gap(int amount) { cursorY = Math.min(CONTENT_BOTTOM, cursorY + amount); }
    private void fit(int height) { if (cursorY + height > CONTENT_BOTTOM) newPage(); }
    private void newPage() { pages.add(new ArrayList<>()); cursorY = CONTENT_TOP; }

    private void divider(boolean full) {
        fit(15);
        pages.get(pages.size() - 1).add(new ImagePiece(cursorY + 3, BOOK,
                full ? 28 : 24, full ? 192 : 184, full ? 140 : 95, 6, full ? 140 : 95, 6, full));
        cursorY += 15;
    }

    private void addImage(String descriptor) {
        String[] values = descriptor.trim().split(":");
        if (values.length != 7) { paragraph(tr("image_unavailable"), FADED); return; }
        try {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(values[0], values[1]);
            int u = Integer.parseInt(values[2]), v = Integer.parseInt(values[3]);
            int w = Integer.parseInt(values[4]), h = Integer.parseInt(values[5]);
            float imageScale = Float.parseFloat(values[6]);
            if (u < 0 || v < 0 || w <= 0 || h <= 0 || u + w > 256 || v + h > 256
                    || !Float.isFinite(imageScale) || imageScale <= 0 || minecraft.getResourceManager().getResource(location).isEmpty()) {
                paragraph(tr("image_unavailable"), FADED); return;
            }
            float shrink = Math.min(1f, Math.min(PAGE_WIDTH / (w * imageScale),
                    (CONTENT_BOTTOM - CONTENT_TOP - 4f) / (h * imageScale)));
            int displayWidth = Math.max(1, Math.round(w * imageScale * shrink));
            int displayHeight = Math.max(1, Math.round(h * imageScale * shrink));
            fit(displayHeight + 4);
            pages.get(pages.size() - 1).add(new ImagePiece(cursorY, location, u, v, w, h, displayWidth, displayHeight, false));
            cursorY += displayHeight + 4;
        } catch (RuntimeException ignored) {
            paragraph(tr("image_unavailable"), FADED);
        }
    }

    private String requirementText(String raw) {
        int colon = raw.indexOf(':');
        if (colon < 0) return tr("other_requirement");
        String kind = raw.substring(0, colon).trim();
        List<String> values = splitValues(raw.substring(colon + 1).trim());
        if (kind.startsWith("required_knowledge")) return String.join("; ", values.stream().map(value -> {
            String[] fields = value.split(";");
            if (fields.length < 2) return tr("knowledge_requirement");
            String type = fields[0].equals("THEORY") ? tr("theory") : tr("observation");
            String category = fields.length > 2 ? Component.translatable("tc.research_category." + fields[1]).getString() : "";
            if (!archiveMode && !legacyLesson() && fields.length == 3) {
                try {
                    KnowledgeType knowledgeType = KnowledgeType.valueOf(fields[0]);
                    int rawCost = Math.multiplyExact(Integer.parseInt(fields[2]), knowledgeType.units());
                    return progress("knowledge_cost", type, category, fields[2], rawCost,
                            knowledge.rawKnowledge(knowledgeType, fields[1]));
                } catch (IllegalArgumentException | ArithmeticException ignored) { }
            }
            return tr("knowledge", type, category, fields[fields.length - 1]);
        }).toList());
        return switch (kind) {
            case "required_item" -> tr("items_requirement", String.join(", ", values.stream().map(this::itemName).toList()));
            case "required_craft" -> tr("craft_requirement", String.join(", ", values.stream().map(value -> {
                String label = itemName(value);
                if (archiveMode || legacyLesson()) return label;
                return progress(knowledge.hasCraft(BookRecipeViews.modernCraftId(value)) ? "craft_recorded" : "craft_missing", label);
            }).toList()));
            case "required_research" -> tr("research_requirement", String.join(", ", values.stream().map(this::researchName).toList()));
            default -> tr("other_requirement");
        };
    }

    private String knowledgeBalance(String category) {
        int raw = knowledge.rawKnowledge(KnowledgeType.OBSERVATION, category);
        return progress("observation_balance", Component.translatable("tc.research_category." + category).getString(),
                knowledge.completedKnowledge(KnowledgeType.OBSERVATION, category), raw % KnowledgeType.OBSERVATION.units(),
                KnowledgeType.OBSERVATION.units(), raw);
    }

    private String chapterLabel() {
        if (chapters.isEmpty()) return progress("not_started");
        if (archiveMode || legacyLesson()) return chapter < entry.stages().size()
                ? tr("stage", chapter + 1, Math.max(1, entry.stages().size()))
                : tr("addendum", chapter - entry.stages().size() + 1, entry.addenda().size());
        if (chapter == 0) return tr("stage", Math.min(visibleStage, entry.stages().size()), entry.stages().size());
        return tr("addendum", entry.addenda().indexOf(chapters.get(chapter)) + 1, entry.addenda().size());
    }

    private static List<String> splitValues(String raw) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) { result.add(raw.substring(start, i).trim()); start = i + 1; }
        }
        if (start < raw.length()) result.add(raw.substring(start).trim());
        return result;
    }

    private String itemName(String descriptor) {
        String[] fields = descriptor.split(";", 4);
        String id = BookRecipeViews.modernCraftId(descriptor);
        int count = 1;
        try { if (fields.length > 1) count = Integer.parseInt(fields[1]); } catch (NumberFormatException ignored) { }
        Matcher aspect = ASPECT.matcher(descriptor);
        String label;
        if (id.endsWith(":enchanted_placeholder")) {
            label = enchantedRequirement(fields);
        } else if (aspect.find()) {
            Aspect found = Aspect.getAspect(aspect.group(1));
            label = tr(id.endsWith("phial") ? "aspect_phial" : "aspect_crystal", found == null ? aspect.group(1) : found.getName());
        } else {
            ResourceLocation location = ResourceLocation.tryParse(id);
            label = location == null ? tr("legacy_item") : localizedItem(location);
            if (id.equals(fields[0]) && fields.length > 2 && !fields[2].equals("0")) label = tr("item_variant", label, fields[2]);
        }
        return count > 1 ? tr("item_count", count, label) : label;
    }

    private String enchantedRequirement(String[] fields) {
        List<String> enchantments = new ArrayList<>();
        if (fields.length > 3) {
            try {
                var list = TagParser.parseTag(fields[3]).getList("ench", 10);
                for (int i = 0; i < list.size(); i++) {
                    var tag = list.getCompound(i);
                    int id = tag.getShort("id"), level = tag.getShort("lvl");
                    String key = switch (id) {
                        case 0 -> "protection";
                        case 16 -> "sharpness";
                        case 33 -> "silk_touch";
                        case 35 -> "fortune";
                        default -> null;
                    };
                    String name = key == null ? tr("legacy_enchantment", id)
                            : Component.translatable("enchantment.minecraft." + key).getString();
                    String levelName = level >= 1 && level <= 10
                            ? Component.translatable("enchantment.level." + level).getString() : Integer.toString(level);
                    enchantments.add(tr("enchantment_minimum", name, levelName));
                }
            } catch (Exception ignored) {
                return tr("enchanted_item_unknown");
            }
        }
        return enchantments.isEmpty() ? tr("enchanted_item_unknown")
                : tr("enchanted_item", String.join(", ", enchantments));
    }

    private String localizedItem(ResourceLocation id) {
        var item = BuiltInRegistries.ITEM.get(id);
        if (item != Items.AIR) return item.getDescription().getString();
        for (String key : List.of("item." + id.getNamespace() + "." + id.getPath(),
                "block." + id.getNamespace() + "." + id.getPath(), "item." + id.getPath() + ".name",
                "tile." + id.getPath() + ".name")) {
            if (I18n.exists(key)) return Component.translatable(key).getString();
        }
        return humanName(id.getPath());
    }

    private String recipeName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (location != null) {
            if (location.getNamespace().equals("thaumcraft") && location.getPath().equals("salismundusfake"))
                return Component.translatable("item.thaumcraft.salis_mundus").getString();
            if (location.getNamespace().equals("thaumcraft") && location.getPath().equals("tablewood"))
                return Component.translatable("block.thaumcraft.table_wood").getString();
            if (location.getNamespace().equals("thaumcraft") && location.getPath().equals("inkwell"))
                return Component.translatable("item.thaumcraft.scribing_tools").getString();
            String label = localizedItem(location);
            if (!label.equals(humanName(location.getPath()))) return label;
        }
        String path = id.substring(id.indexOf(':') + 1);
        String researchKey = path.replace("_", "").toUpperCase(Locale.ROOT);
        ResearchEntry match = ResearchCatalog.get(researchKey);
        return match == null ? humanName(path) : Component.translatable(match.title()).getString();
    }

    private String researchName(String raw) {
        String key = raw.replaceFirst("^[!~]", "");
        int stage = key.indexOf('@');
        if (stage >= 0) key = key.substring(0, stage);
        ResearchEntry match = ResearchCatalog.get(key);
        if (match != null) return Component.translatable(match.title()).getString();
        Aspect aspect = Aspect.getAspect(key);
        if (aspect != null) return tr("aspect_requirement", aspect.getName());
        if (I18n.exists("research." + key + ".title")) return Component.translatable("research." + key + ".title").getString();
        if (I18n.exists("research." + key + ".text")) return Component.translatable("research." + key + ".text").getString();
        return tr("discovery_requirement");
    }

    private static String humanName(String key) {
        String clean = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('/', ' ');
        return clean.isBlank() ? "?" : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        controls.clear();
        itemHovers.clear();
        double mx = (mouseX - left) / scale, my = (mouseY - top) / scale;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(BOOK, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, 0, 0, 256, 181, 256, 256);
        centered(graphics, title.getString(), 105, 26, PAGE_WIDTH, INK);
        atlas(graphics, BOOK, 62, 18, 95, 4, 24, 184, 95, 4);
        atlas(graphics, BOOK, 62, 40, 95, 4, 24, 184, 95, 4);
        centered(graphics, chapterLabel(), 295, 28, PAGE_WIDTH, FADED);
        if (!chapters.isEmpty() && chapters.get(chapter).warp() > 0) atlas(graphics, OVERLAY, 287, 40, 12, 12, 20, 3, 16, 16);
        drawPage(graphics, spread * 2, 29);
        drawPage(graphics, spread * 2 + 1, 216);
        String status = archiveMode ? tr("archive") : complete() ? progress("complete")
                : legacyLesson() ? tr("practice") : progress(visibleStage == 0 ? "not_started" : "in_progress");
        centered(graphics, status, BOOK_WIDTH / 2, -16, BOOK_WIDTH - 70, 0xE8D7B3);
        if (spread > 0) arrow(graphics, 22, 262, false, mx, my, () -> spread--, tr("previous_page"));
        if ((spread + 1) * 2 < pages.size()) arrow(graphics, 363, 262, true, mx, my, () -> spread++, tr("next_page"));
        if (chapters.size() > 1) {
            button(graphics, 224, 8, 18, 13, "‹", chapter > 0, mx, my, () -> changeChapter(-1), tr("previous_stage"));
            button(graphics, 346, 8, 18, 13, "›", chapter + 1 < chapters.size(), mx, my, () -> changeChapter(1), tr("next_stage"));
        }
        button(graphics, 8, 292, 116, 17, tr("back"), true, mx, my, this::onClose, tr("back_hint"));
        if (!archiveMode && !complete() && (legacyLesson() || ResearchProgression.isImplemented(entry.key()))) {
            String label = requestPending ? tr("pending") : legacyLesson() ? tr("discover")
                    : progress(visibleStage == 0 ? "start" : "advance");
            String hint = resultMessage == null ? progress("server_check") : progress(resultMessage);
            button(graphics, 170, 292, 220, 17, label, actionAvailable(), mx, my, this::requestAdvance, hint);
        } else centered(graphics, tr("page_count", spread + 1, Math.max(1, (pages.size() + 1) / 2)), 285, 295, 200, 0xD5C199);
        if (resultMessage != null) centered(graphics, progress(resultMessage), BOOK_WIDTH / 2, 316, BOOK_WIDTH - 16, 0xE8BA8E);
        graphics.pose().popPose();
        for (ItemHover hover : itemHovers) if (hover.contains(mx, my)) {
            graphics.renderTooltip(font, hover.stack(), mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (Control control : controls) if (control.contains(mx, my)) {
            graphics.renderTooltip(font, Component.literal(control.tooltip), mouseX, mouseY); break;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPage(GuiGraphics graphics, int index, int x) {
        if (index >= pages.size()) return;
        for (Piece piece : pages.get(index)) {
            if (piece instanceof TextPiece text) graphics.drawString(font, text.text, x, text.y, text.color, false);
            else if (piece instanceof ImagePiece img) atlas(graphics, img.texture,
                    x + (img.leftAligned ? 0 : (PAGE_WIDTH - img.width) / 2), img.y, img.width, img.height,
                    img.u, img.v, img.sourceWidth, img.sourceHeight);
            else if (piece instanceof RecipePiece recipe) drawRecipe(graphics, x, recipe.y, recipe.view);
        }
        centered(graphics, Integer.toString(index + 1), x + PAGE_WIDTH / 2, 263, PAGE_WIDTH, FADED);
    }

    private void drawRecipe(GuiGraphics graphics, int x, int y, BookRecipeViews.View view) {
        centered(graphics, view.output().getHoverName().getString(), x + PAGE_WIDTH / 2, y + 2, PAGE_WIDTH, INK);
        centered(graphics, tr("recipe_" + view.kind()), x + PAGE_WIDTH / 2, y + 14, PAGE_WIDTH, FADED);
        if (view.kind().equals("crucible")) {
            ingredientSlot(graphics, BookRecipeViews.displayIngredient(view.ingredients().get(0)), x + 27, y + 49);
        } else {
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
                int index = row * view.width() + column;
                ItemStack ingredient = column < view.width() && row < view.height() && index < view.ingredients().size()
                        ? BookRecipeViews.displayIngredient(view.ingredients().get(index)) : ItemStack.EMPTY;
                ingredientSlot(graphics, ingredient, x + 10 + column * 20, y + 30 + row * 20);
            }
        }
        graphics.drawString(font, "→", x + 84, y + 53, INK, false);
        ingredientSlot(graphics, view.output(), x + 112, y + 49);
        if (view.kind().equals("arcane")) {
            centered(graphics, tr("recipe_vis", view.vis()), x + PAGE_WIDTH / 2, y + 94, PAGE_WIDTH, 0x654575);
            int count = 0;
            for (int value : view.crystals()) if (value > 0) count++;
            int crystalX = x + (PAGE_WIDTH - count * 23) / 2;
            for (int i = 0; i < 6; i++) if (view.crystals()[i] > 0) {
                ItemStack crystal = new ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get(), view.crystals()[i]);
                graphics.renderItem(crystal, crystalX, y + 107);
                graphics.renderItemDecorations(font, crystal, crystalX, y + 107);
                itemHovers.add(new ItemHover(crystalX, y + 107, crystal));
                crystalX += 23;
            }
            if (count == 0) centered(graphics, tr("recipe_no_crystals"), x + PAGE_WIDTH / 2, y + 109, PAGE_WIDTH, FADED);
        } else if (view.kind().equals("crucible")) {
            Aspect[] aspects = view.aspects().getAspects();
            int aspectX = x + (PAGE_WIDTH - aspects.length * 31) / 2;
            for (Aspect aspect : aspects) {
                graphics.blit(aspect.getImage(), aspectX, y + 97, 0, 0, 16, 16, 16, 16);
                graphics.drawString(font, Integer.toString(view.aspects().getAmount(aspect)), aspectX + 17, y + 101, INK, false);
                controls.add(new Control(aspectX, y + 97, 30, 16, () -> {}, aspect.getName()));
                aspectX += 31;
            }
        } else if (view.kind().equals("salis")) {
            int noteY = recipeNote(graphics, tr("recipe_salis_distinct"), x, y + 94);
            recipeNote(graphics, tr("recipe_salis_return"), x, noteY + 4);
        }
        boolean unlocked = view.unlocked(knowledge);
        int statusY = y + (view.kind().equals("salis") ? 161 : 127);
        centered(graphics, tr(unlocked ? "recipe_unlocked" : "recipe_locked"),
                x + PAGE_WIDTH / 2, statusY, PAGE_WIDTH, unlocked ? 0x3C633D : 0x8F3A35);
        if (!view.research().isEmpty()) controls.add(new Control(x, statusY - 3, PAGE_WIDTH, 14, () -> {},
                tr("research_requirement", researchName(view.research()))));
    }

    private int recipeNote(GuiGraphics graphics, String text, int x, int y) {
        for (FormattedCharSequence line : font.split(Component.literal(text), PAGE_WIDTH)) {
            graphics.drawString(font, line, x, y, FADED, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    private void ingredientSlot(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x704B3623);
        graphics.fill(x, y, x + 16, y + 16, 0x30FFF0BB);
        if (stack.isEmpty()) return;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        itemHovers.add(new ItemHover(x, y, stack));
    }

    private void centered(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        float textScale = Math.min(1f, maxWidth / (float)Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(textScale, textScale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void arrow(GuiGraphics graphics, int x, int y, boolean forward, double mx, double my, Runnable action, String tooltip) {
        Control c = new Control(x - 4, y - 5, 24, 20, action, tooltip);
        controls.add(c);
        atlas(graphics, BOOK, x, y + (c.contains(mx, my) ? -1 : 0), 16, 11, forward ? 12 : 0, 184, 12, 8);
    }

    private void button(GuiGraphics graphics, int x, int y, int w, int h, String label, boolean active,
                        double mx, double my, Runnable action, String tooltip) {
        Control c = new Control(x, y, w, h, active ? action : () -> { }, tooltip);
        controls.add(c);
        boolean hovered = c.contains(mx, my);
        graphics.fill(x, y, x + w, y + h, active && hovered ? 0xB0895D38 : 0x70362116);
        graphics.fill(x, y + h - 1, x + w, y + h, active ? 0xA0BB9261 : 0x605D4D3A);
        centered(graphics, label, x + w / 2, y + (h - font.lineHeight) / 2, w - 8, active ? 0xF1DCAD : 0x98836D);
    }

    private void changeChapter(int amount) {
        if (chapters.isEmpty()) return;
        int next = Math.max(0, Math.min(chapters.size() - 1, chapter + amount));
        if (next != chapter) { chapter = next; spread = 0; buildPages(); }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) { onClose(); return true; }
        if (button == 0) for (Control control : controls) {
            if (control.contains((mouseX - left) / scale, (mouseY - top) / scale)) { control.action.run(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_LEFT) { spread = Math.max(0, spread - 1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { spread = Math.min((pages.size() - 1) / 2, spread + 1); return true; }
        if (key == GLFW.GLFW_KEY_UP) { changeChapter(-1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { changeChapter(1); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        spread = Math.max(0, Math.min((pages.size() - 1) / 2, spread - (int)Math.signum(amount)));
        return true;
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(browser); }
    @Override public boolean isPauseScreen() { return false; }

    void senderForSmokeTest(BiConsumer<String, Integer> sender) {
        if (!Boolean.getBoolean("thaumcraft.clientSmokeTest")) throw new IllegalStateException("Smoke sender requires explicit opt-in");
        smokeSender = sender;
    }
    void clickActionForSmokeTest() { mouseClicked(left + 280 * scale, top + 300 * scale, 0); }
    boolean pendingForSmokeTest() { return requestPending; }
    boolean availableForSmokeTest() { return actionAvailable(); }
    List<String> chaptersForSmokeTest() { return chapters.stream().map(ResearchEntry.Stage::text).toList(); }
    String chapterLabelForSmokeTest() { return chapterLabel(); }
    String costForSmokeTest() { return chapters.isEmpty() ? "" : String.join(" ", chapters.get(chapter).requirements().stream().map(this::requirementText).toList()); }
    int spreadForSmokeTest() { return spread; }
    int spreadCountForSmokeTest() { return Math.max(1, (pages.size() + 1) / 2); }
    List<BookRecipeViews.View> recipesForSmokeTest() {
        return pages.stream().flatMap(List::stream).filter(piece -> piece instanceof RecipePiece)
                .map(piece -> ((RecipePiece)piece).view()).toList();
    }
    void showRecipeForSmokeTest(String output) {
        for (int i = 0; i < pages.size(); i++) for (Piece piece : pages.get(i))
            if (piece instanceof RecipePiece recipe && BuiltInRegistries.ITEM.getKey(recipe.view.output().getItem()).getPath().equals(output)) {
                spread = i / 2;
                return;
            }
        throw new AssertionError("No rendered recipe for " + output + " in " + entry.key());
    }

    private static String tr(String key, Object... args) { return Component.translatable("thaumcraft.book." + key, args).getString(); }
    private static String progress(String key, Object... args) { return Component.translatable("thaumcraft.progress." + key, args).getString(); }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/" + path); }
    private static void atlas(GuiGraphics g, ResourceLocation resource, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // TC6 markup uses normalized 256px coordinates, including PNG atlases stored at 512px.
        g.blit(resource, x, y, w, h, (float)u, (float)v, sw, sh, 256, 256);
    }
    private interface Piece { }
    private record TextPiece(int y, FormattedCharSequence text, int color) implements Piece { }
    private record ImagePiece(int y, ResourceLocation texture, int u, int v, int sourceWidth, int sourceHeight,
                              int width, int height, boolean leftAligned) implements Piece { }
    private record RecipePiece(int y, BookRecipeViews.View view) implements Piece { }
    private record ItemHover(int x, int y, ItemStack stack) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + 16 && my >= y && my < y + 16; }
    }
    private record Control(int x, int y, int width, int height, Runnable action, String tooltip) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }
}
