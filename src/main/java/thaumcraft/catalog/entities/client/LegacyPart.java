package thaumcraft.catalog.entities.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/** Small constructor adapter preserving legacy addBox UV/mirror semantics. */
final class LegacyPart {
    float rotationPointX, rotationPointY, rotationPointZ;
    float rotateAngleX, rotateAngleY, rotateAngleZ;
    float scale = 1;
    boolean mirror;
    boolean visible = true;
    int textureWidth, textureHeight, u, v;
    LegacyPart parent;
    final List<LegacyPart> children = new ArrayList<>();
    final List<ModelPart.Cube> cubes = new ArrayList<>();
    ModelPart part;
    float ix, iy, iz;

    LegacyPart(LegacyVisualModel model) { this(model, 0, 0); }
    LegacyPart(LegacyVisualModel model, int u, int v) {
        textureWidth = model.textureWidth;
        textureHeight = model.textureHeight;
        this.u = u;
        this.v = v;
        model.nodes.add(this);
    }
    LegacyPart addBox(float x, float y, float z, int w, int h, int d) {
        cubes.add(new ModelPart.Cube(u, v, x, y, z, w, h, d, 0, 0, 0,
                mirror, textureWidth, textureHeight, EnumSet.allOf(Direction.class)));
        return this;
    }
    LegacyPart setTextureOffset(int u, int v) { this.u = u; this.v = v; return this; }
    void setTextureSize(int w, int h) { textureWidth = w; textureHeight = h; }
    void setRotationPoint(float x, float y, float z) { rotationPointX = x; rotationPointY = y; rotationPointZ = z; }
    void addChild(LegacyPart child) { children.add(child); child.parent = this; }
    ModelPart bake() {
        var parts = new LinkedHashMap<String, ModelPart>();
        for (int i = 0; i < children.size(); i++) parts.put("child_" + i, children.get(i).bake());
        part = new ModelPart(cubes, parts);
        ix = rotateAngleX; iy = rotateAngleY; iz = rotateAngleZ;
        update();
        return part;
    }
    void reset() { rotateAngleX = ix; rotateAngleY = iy; rotateAngleZ = iz; visible = true; }
    void update() {
        if (part == null) return;
        part.setPos(rotationPointX, rotationPointY, rotationPointZ);
        part.setRotation(rotateAngleX, rotateAngleY, rotateAngleZ);
        part.xScale = scale; part.yScale = scale; part.zScale = scale;
        part.visible = visible;
    }
}
