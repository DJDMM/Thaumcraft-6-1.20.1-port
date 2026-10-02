package thaumcraft.catalog.entities.client;
/** Original neutral TC6 ModelCentrifuge. */
public final class LegacyCentrifugeBlockModel extends LegacyVisualModel {
    LegacyPart Crossbar;
    LegacyPart Dingus1;
    LegacyPart Dingus2;
    LegacyPart Core;
    LegacyPart Top;
    LegacyPart Bottom;
 public LegacyCentrifugeBlockModel() {

        textureWidth = 64;
        textureHeight = 32;
        (Crossbar = new LegacyPart(this, 16, 0)).addBox(-4.0f, -1.0f, -1.0f, 8, 2, 2);
        Crossbar.setRotationPoint(0.0f, 0.0f, 0.0f);
        Crossbar.setTextureSize(64, 32);
        Crossbar.mirror = true;
        setRotation(Crossbar, 0.0f, 0.0f, 0.0f);
        (Dingus1 = new LegacyPart(this, 0, 16)).addBox(4.0f, -3.0f, -2.0f, 4, 6, 4);
        Dingus1.setRotationPoint(0.0f, 0.0f, 0.0f);
        Dingus1.setTextureSize(64, 32);
        Dingus1.mirror = true;
        setRotation(Dingus1, 0.0f, 0.0f, 0.0f);
        (Dingus2 = new LegacyPart(this, 0, 16)).addBox(-8.0f, -3.0f, -2.0f, 4, 6, 4);
        Dingus2.setRotationPoint(0.0f, 0.0f, 0.0f);
        Dingus2.setTextureSize(64, 32);
        Dingus2.mirror = true;
        setRotation(Dingus2, 0.0f, 0.0f, 0.0f);
        (Core = new LegacyPart(this, 0, 0)).addBox(-1.5f, -4.0f, -1.5f, 3, 8, 3);
        Core.setRotationPoint(0.0f, 0.0f, 0.0f);
        Core.setTextureSize(64, 32);
        Core.mirror = true;
        setRotation(Core, 0.0f, 0.0f, 0.0f);
        (Top = new LegacyPart(this, 20, 16)).addBox(-4.0f, -8.0f, -4.0f, 8, 4, 8);
        Top.setRotationPoint(0.0f, 0.0f, 0.0f);
        Top.setTextureSize(64, 32);
        Top.mirror = true;
        setRotation(Top, 0.0f, 0.0f, 0.0f);
        (Bottom = new LegacyPart(this, 20, 16)).addBox(-4.0f, 4.0f, -4.0f, 8, 4, 8);
        Bottom.setRotationPoint(0.0f, 0.0f, 0.0f);
        Bottom.setTextureSize(64, 32);
        Bottom.mirror = true;
        setRotation(Bottom, 0.0f, 0.0f, 0.0f);
    
 bake();
 }
}
