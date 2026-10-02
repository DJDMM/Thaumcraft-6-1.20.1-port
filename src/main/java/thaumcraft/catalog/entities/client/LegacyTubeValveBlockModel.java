package thaumcraft.catalog.entities.client;
/** Original neutral TC6 ModelTubeValve. */
public final class LegacyTubeValveBlockModel extends LegacyVisualModel {
    LegacyPart ValveRod;
    LegacyPart ValveRing;
 public LegacyTubeValveBlockModel() {

        textureWidth = 64;
        textureHeight = 32;
        (ValveRod = new LegacyPart(this, 0, 10)).addBox(-1.0f, 2.0f, -1.0f, 2, 2, 2);
        ValveRod.setRotationPoint(0.0f, 0.0f, 0.0f);
        ValveRod.setTextureSize(64, 32);
        ValveRod.mirror = true;
        setRotation(ValveRod, 0.0f, 0.0f, 0.0f);
        (ValveRing = new LegacyPart(this, 0, 0)).addBox(-2.0f, 4.0f, -2.0f, 4, 1, 4);
        ValveRing.setRotationPoint(0.0f, 0.0f, 0.0f);
        ValveRing.setTextureSize(64, 32);
        ValveRing.mirror = true;
        setRotation(ValveRing, 0.0f, 0.0f, 0.0f);
    
 bake();
 }
}
