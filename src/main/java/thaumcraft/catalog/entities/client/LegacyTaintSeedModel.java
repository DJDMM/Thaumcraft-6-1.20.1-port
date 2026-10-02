package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelTaintSeed. */
public final class LegacyTaintSeedModel extends LegacyVisualModel {

    public LegacyPart tentacle;
    public LegacyPart[] tents;
    public LegacyPart orb;
    private int length;
    
        public LegacyTaintSeedModel() {

        tentacle = new LegacyPart(this);
        orb = new LegacyPart(this);
        length = 8;
        textureHeight = 64;
        textureWidth = 64;
        (tentacle = new LegacyPart(this, 0, 0)).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8);
        tentacle.rotationPointX = 0.0f;
        tentacle.rotationPointZ = 0.0f;
        tentacle.rotationPointY = 12.0f;
        tents = new LegacyPart[length];
        for (int k = 0; k < length - 1; ++k) {
            tents[k] = new LegacyPart(this, 0, (k < length - 4) ? 16 : ((k == length - 4) ? 48 : 56));
            if (k < length - 4) {
                tents[k].addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8);
                tents[k].rotationPointY = -8.0f;
            }
            else {
                tents[k].addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4);
                tents[k].rotationPointY = ((k == length - 4) ? -8.0f : -4.0f);
            }
            if (k == 0) {
                tentacle.addChild(tents[k]);
            }
            else {
                tents[k - 1].addChild(tents[k]);
            }
        }
    
        bake();
    }
}
