package thaumcraft.research.theory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import thaumcraft.catalog.entities.client.LegacyResearchTableBlockModel;
import thaumcraft.research.theory.ResearchTableBlockEntity;
import thaumcraft.research.theory.TheoryModule;

import java.util.ArrayList;
import java.util.List;

/** Opt-in client geometry checks; no player data, GL state or world mutations. */
public final class ResearchTableRenderAudit {
    private ResearchTableRenderAudit() {}

    /** Invoke from an integrated client fixture after its synchronized table is loaded. */
    public static String verify(ResearchTableBlockEntity table) {
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(table);
        require(renderer instanceof ResearchTableRenderer, "Research table has no registered original renderer");
        ResearchTableRenderer original = (ResearchTableRenderer)renderer;
        require(new LegacyResearchTableBlockModel().cuboidCount() == 3, "Original table attachments should contain exactly three cuboids");
        ItemStack tools = new ItemStack(TheoryModule.SCRIBING_TOOLS.get());
        ItemStack exhausted = tools.copy(); exhausted.setDamageValue(100);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            require(draw(original, direction, ItemStack.EMPTY, false).vertices.isEmpty(), "Empty table displayed supplies");
            Recorder ink = draw(original, direction, tools, false);
            Recorder scroll = draw(original, direction, ItemStack.EMPTY, true);
            Recorder full = draw(original, direction, tools, true);
            require(ink.vertices.size() == 288, "Inkwell + 1/16-thick quill extrusion lost geometry");
            require(scroll.vertices.size() == 48, "Scroll tube + ribbon lost geometry");
            require(full.vertices.size() == 336, "Active table attachments lost geometry");
            require(full.vertices.equals(draw(original, direction, exhausted, true).vertices), "Exhausted tools hid original ink/quill geometry");
            for (Point point : full.vertices) require(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z), "Nonfinite table geometry");
        }
        return "research table: registered renderer; 3 original cuboids; 4 orientations; empty/ink/scroll/full states; exhausted tools visible; quill depth 1/16";
    }

    private static Recorder draw(ResearchTableRenderer renderer, Direction direction, ItemStack tools, boolean scroll) {
        Recorder recorder = new Recorder();
        PoseStack pose = new PoseStack();
        renderer.renderAttachments(direction, tools, scroll, pose, type -> recorder, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        require(pose.last().pose().equals(new PoseStack().last().pose()), "Rendering failed to restore its pose");
        return recorder;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private record Point(double x, double y, double z) {}

    private static final class Recorder implements VertexConsumer {
        private final List<Point> vertices = new ArrayList<>();
        private Point current;
        @Override public VertexConsumer vertex(double x, double y, double z) { current = new Point(x, y, z); return this; }
        @Override public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { vertices.add(current); }
        @Override public void defaultColor(int red, int green, int blue, int alpha) {}
        @Override public void unsetDefaultColor() {}
    }
}
