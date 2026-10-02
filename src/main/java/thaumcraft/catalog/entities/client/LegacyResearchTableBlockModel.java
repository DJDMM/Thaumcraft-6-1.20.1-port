package thaumcraft.catalog.entities.client;

/** BETA26 ModelResearchTable's exact three cuboids, UVs, pivots and ten-radian scroll yaw. */
public final class LegacyResearchTableBlockModel extends LegacyVisualModel {
    LegacyPart Inkwell;
    LegacyPart ScrollTube;
    LegacyPart ScrollRibbon;

    public LegacyResearchTableBlockModel() {
        textureWidth = 64;
        textureHeight = 32;
        (Inkwell = new LegacyPart(this, 0, 16)).addBox(0, 0, 0, 3, 2, 3);
        Inkwell.setRotationPoint(-6, -2, 3);
        Inkwell.mirror = true;
        setRotation(Inkwell, 0, 0, 0);
        (ScrollTube = new LegacyPart(this, 0, 0)).addBox(-8, -.5F, 0, 8, 2, 2);
        ScrollTube.setRotationPoint(-2, -2, 2);
        ScrollTube.mirror = true;
        setRotation(ScrollTube, 0, 10, 0);
        (ScrollRibbon = new LegacyPart(this, 0, 4)).addBox(-4.25F, -.275F, 0, 1, 2, 2);
        ScrollRibbon.setRotationPoint(-2, -2, 2);
        ScrollRibbon.mirror = true;
        setRotation(ScrollRibbon, 0, 10, 0);
        bake();
    }
}
