package thaumcraft.catalog.entities.client;
/** BETA26 ModelCube(0), used by the eight neutral infusion-matrix pieces. */
public final class LegacyInfusionCubeBlockModel extends LegacyVisualModel {
    private final LegacyPart cube;
    public LegacyInfusionCubeBlockModel() { this(0); }
    public LegacyInfusionCubeBlockModel(int textureOffset) {
        textureWidth=64;textureHeight=64;
        cube=new LegacyPart(this,0,textureOffset);
        cube.addBox(-8,-8,-8,16,16,16);
        cube.setRotationPoint(0,0,0);
        bake();
    }
}
