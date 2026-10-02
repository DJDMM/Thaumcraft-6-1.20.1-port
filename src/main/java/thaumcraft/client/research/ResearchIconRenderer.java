package thaumcraft.client.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import thaumcraft.Thaumcraft;
import thaumcraft.research.ResearchEntry;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Legacy book artwork is display data; it never registers unavailable content. */
@Mod.EventBusSubscriber(modid = Thaumcraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ResearchIconRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MANIFEST = new ResourceLocation("thaumcraft", "research/icon_textures.json");
    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static final Map<ResourceLocation, Size> SIZES = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> AVAILABLE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static boolean loaded;

    private ResearchIconRenderer() {}

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ResearchIconRenderer::reload);
    }

    private static void reload(ResourceManager manager) {
        ICONS.clear();
        SIZES.clear();
        AVAILABLE.clear();
        WARNED.clear();
        loaded = true;
        try (Reader reader = manager.getResourceOrThrow(MANIFEST).openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.getAsJsonObject("textures").entrySet().forEach(entry -> {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                JsonObject value = entry.getValue().getAsJsonObject();
                if (id != null) SIZES.put(id, new Size(GsonHelper.getAsInt(value, "width", 16),
                        GsonHelper.getAsInt(value, "height", 16)));
            });
            root.getAsJsonObject("icons").entrySet().forEach(entry -> {
                JsonObject value = entry.getValue().getAsJsonObject();
                ICONS.put(entry.getKey(), new Icon(location(value, "texture"), location(value, "item"),
                        location(value, "background"), GsonHelper.getAsInt(value, "color", 0xffffff),
                        GsonHelper.getAsBoolean(value, "animate", false),
                        "focus".equals(GsonHelper.getAsString(value, "kind", ""))));
            });
        } catch (Exception error) {
            LOGGER.warn("Could not load Thaumonomicon icon manifest", error);
        }
    }

    private static ResourceLocation location(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull()
                ? ResourceLocation.tryParse(value.get(key).getAsString()) : null;
    }

    private static void ensureLoaded() {
        if (!loaded) reload(Minecraft.getInstance().getResourceManager());
    }

    /** Draws a node's icon at its 16-pixel content origin, cycling once per second. */
    public static void draw(GuiGraphics graphics, ResearchEntry entry, int x, int y, float brightness) {
        ensureLoaded();
        float light = Mth.clamp(brightness, 0.0f, 1.0f);
        int count = entry.icons().size();
        int first = count == 0 ? 0 : (int) (System.currentTimeMillis() / 1000L % count);
        try {
            for (int offset = 0; offset < count; offset++) {
                String raw = entry.icons().get((first + offset) % count);
                Icon icon = ICONS.get(raw);
                if (icon == null) {
                    ResourceLocation id = ResourceLocation.tryParse(raw.split(";", 2)[0]);
                    icon = raw.contains(":textures/") ? new Icon(id, null, null, 0xffffff, true, false)
                            : new Icon(null, id, null, 0xffffff, false, false);
                }
                Item item = icon.item == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(icon.item).orElse(Items.AIR);
                if (item != Items.AIR) {
                    graphics.setColor(light, light, light, 1.0f);
                    graphics.renderItem(new ItemStack(item), x, y);
                    return;
                }
                if (available(icon.texture)) {
                    if (icon.focus && available(icon.background)) {
                        graphics.setColor(((icon.color >> 16) & 255) / 255f * light,
                                ((icon.color >> 8) & 255) / 255f * light,
                                (icon.color & 255) / 255f * light, 1f);
                        blit(graphics, icon.background, x - 3, y - 3, 22, false);
                    }
                    graphics.setColor(light, light, light, 1.0f);
                    int size = icon.focus ? (icon.background == null ? 24 : 12) : 16;
                    int inset = (16 - size) / 2;
                    blit(graphics, icon.texture, x + inset, y + inset, size, icon.animate);
                    return;
                }
            }
            if (WARNED.add(entry.key())) LOGGER.warn("No available Thaumonomicon icon for {}: {}", entry.key(), entry.icons());
            graphics.setColor(light, light, light, 1.0f);
            graphics.renderItem(new ItemStack(Items.BOOK), x, y);
        } finally {
            graphics.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** Full image, scaled to a square; used by category tabs. Preserves caller tint. */
    public static void drawTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        ensureLoaded();
        if (available(texture)) blit(graphics, texture, x, y, size, false);
    }

    private static boolean available(ResourceLocation texture) {
        return texture != null && AVAILABLE.computeIfAbsent(texture,
                key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, boolean animate) {
        // ResourceLocation blit does not enable blending itself in 1.20.1.
        // Item rendering / buffered fills can change that state between icons.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Size source = SIZES.getOrDefault(texture, new Size(16, 16));
        int frameWidth = source.width, frameHeight = source.height, u = 0, v = 0;
        if (animate && source.height > source.width && source.height % source.width == 0) {
            frameHeight = source.width;
            v = (int) (System.currentTimeMillis() / 150L % (source.height / source.width)) * frameHeight;
        } else if (animate && source.width > source.height && source.width % source.height == 0) {
            frameWidth = source.height;
            u = (int) (System.currentTimeMillis() / 150L % (source.width / source.height)) * frameWidth;
        }
        graphics.blit(texture, x, y, size, size, (float) u, (float) v, frameWidth, frameHeight, source.width, source.height);
    }

    private record Size(int width, int height) {}
    private record Icon(ResourceLocation texture, ResourceLocation item, ResourceLocation background,
                        int color, boolean animate, boolean focus) {}
}
