package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelTaintacle. */
public final class LegacyTaintacleModel extends LegacyVisualModel {

    public LegacyPart tentacle;
    public LegacyPart[] tents;
    public LegacyPart orb;
    private int length;
    private boolean seed;
    
        public LegacyTaintacleModel(int length, boolean seed) {

        tentacle = new LegacyPart(this);
        orb = new LegacyPart(this);
        this.length = 10;
        this.seed = false;
        this.seed = seed;
        int var3 = 0;
        this.length = length;
        textureHeight = 64;
        textureWidth = 64;
        (tentacle = new LegacyPart(this, 0, 0)).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8);
        tentacle.rotationPointX = 0.0f;
        tentacle.rotationPointZ = 0.0f;
        tentacle.rotationPointY = 12.0f;
        tents = new LegacyPart[length];
        for (int k = 0; k < length - 1; ++k) {
            (tents[k] = new LegacyPart(this, 0, 16)).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8);
            tents[k].rotationPointY = -8.0f;
            if (k == 0) {
                tentacle.addChild(tents[k]);
            }
            else {
                tents[k - 1].addChild(tents[k]);
            }
        }
        if (!seed) {
            (orb = new LegacyPart(this, 0, 56)).addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4);
            orb.rotationPointY = -8.0f;
            tents[length - 2].addChild(orb);
            (tents[length - 1] = new LegacyPart(this, 0, 32)).addBox(-6.0f, -6.0f, -6.0f, 12, 12, 12);
            tents[length - 1].rotationPointY = -8.0f;
            tents[length - 2].addChild(tents[length - 1]);
        }
    
        bake();
    }
}
