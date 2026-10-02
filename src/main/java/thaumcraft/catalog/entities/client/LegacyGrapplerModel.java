package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelGrappler. */
public final class LegacyGrapplerModel extends LegacyVisualModel {

    LegacyPart core;
    LegacyPart prong1;
    LegacyPart prong2;
    LegacyPart prong3;
    
        public LegacyGrapplerModel() {

        textureWidth = 64;
        textureHeight = 32;
        (core = new LegacyPart(this, 0, 0)).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3);
        core.setRotationPoint(0.0f, 0.0f, 0.0f);
        core.setTextureSize(textureWidth, textureHeight);
        setRotation(core, 0.0f, 0.0f, 0.0f);
        (prong1 = new LegacyPart(this, 0, 10)).addBox(-0.5f, -0.5f, -2.5f, 1, 1, 5);
        prong1.setRotationPoint(0.0f, 0.0f, 0.0f);
        prong1.setTextureSize(textureWidth, textureHeight);
        setRotation(prong1, 0.0f, 0.0f, 0.0f);
        (prong2 = new LegacyPart(this, 0, 10)).addBox(-0.5f, -0.5f, -2.5f, 1, 1, 5);
        prong2.setRotationPoint(0.0f, 0.0f, 0.0f);
        prong2.setTextureSize(textureWidth, textureHeight);
        setRotation(prong2, 0.0f, 1.5707964f, 0.0f);
        (prong3 = new LegacyPart(this, 0, 10)).addBox(-0.5f, -0.5f, -2.5f, 1, 1, 5);
        prong3.setRotationPoint(0.0f, 0.0f, 0.0f);
        prong3.setTextureSize(textureWidth, textureHeight);
        setRotation(prong3, 1.5707964f, 1.5707964f, 0.0f);
    
        bake();
    }
}
