package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelFireBat. */
public final class LegacyFireBatModel extends LegacyVisualModel {

    private LegacyPart batHead;
    private LegacyPart batBody;
    private LegacyPart batRightWing;
    private LegacyPart batLeftWing;
    private LegacyPart batOuterRightWing;
    private LegacyPart batOuterLeftWing;
    
        public LegacyFireBatModel() {

        textureWidth = 64;
        textureHeight = 64;
        (batHead = new LegacyPart(this, 0, 0)).addBox(-3.0f, -3.0f, -3.0f, 6, 6, 6);
        LegacyPart var1 = new LegacyPart(this, 24, 0);
        var1.addBox(-4.0f, -6.0f, -2.0f, 3, 4, 1);
        batHead.addChild(var1);
        LegacyPart var2 = new LegacyPart(this, 24, 0);
        var2.mirror = true;
        var2.addBox(1.0f, -6.0f, -2.0f, 3, 4, 1);
        batHead.addChild(var2);
        (batBody = new LegacyPart(this, 0, 16)).addBox(-3.0f, 4.0f, -3.0f, 6, 12, 6);
        batBody.setTextureOffset(0, 34).addBox(-5.0f, 16.0f, 0.0f, 10, 6, 1);
        (batRightWing = new LegacyPart(this, 42, 0)).addBox(-12.0f, 1.0f, 1.5f, 10, 16, 1);
        (batOuterRightWing = new LegacyPart(this, 24, 16)).setRotationPoint(-12.0f, 1.0f, 1.5f);
        batOuterRightWing.addBox(-8.0f, 1.0f, 0.0f, 8, 12, 1);
        batLeftWing = new LegacyPart(this, 42, 0);
        batLeftWing.mirror = true;
        batLeftWing.addBox(2.0f, 1.0f, 1.5f, 10, 16, 1);
        batOuterLeftWing = new LegacyPart(this, 24, 16);
        batOuterLeftWing.mirror = true;
        batOuterLeftWing.setRotationPoint(12.0f, 1.0f, 1.5f);
        batOuterLeftWing.addBox(0.0f, 1.0f, 0.0f, 8, 12, 1);
        batBody.addChild(batRightWing);
        batBody.addChild(batLeftWing);
        batRightWing.addChild(batOuterRightWing);
        batLeftWing.addChild(batOuterLeftWing);
    
        bake();
    }
}
