package thaumcraft.catalog.entities.client;
/** Original neutral TC6 ModelBrain. */
public final class LegacyBrainBlockModel extends LegacyVisualModel {
    LegacyPart Shape1;
    LegacyPart Shape2;
    LegacyPart Shape3;
 public LegacyBrainBlockModel() {

        textureWidth = 128;
        textureHeight = 64;
        (Shape1 = new LegacyPart(this, 0, 0)).addBox(0.0f, 0.0f, 0.0f, 12, 10, 16);
        Shape1.setRotationPoint(-6.0f, 8.0f, -8.0f);
        Shape1.setTextureSize(128, 64);
        Shape1.mirror = true;
        setRotation(Shape1, 0.0f, 0.0f, 0.0f);
        (Shape2 = new LegacyPart(this, 64, 0)).addBox(0.0f, 0.0f, 0.0f, 8, 3, 7);
        Shape2.setRotationPoint(-4.0f, 18.0f, 0.0f);
        Shape2.setTextureSize(128, 64);
        Shape2.mirror = true;
        setRotation(Shape2, 0.0f, 0.0f, 0.0f);
        (Shape3 = new LegacyPart(this, 0, 32)).addBox(0.0f, 0.0f, 0.0f, 2, 6, 2);
        Shape3.setRotationPoint(-1.0f, 18.0f, -2.0f);
        Shape3.setTextureSize(128, 64);
        Shape3.mirror = true;
        setRotation(Shape3, 0.4089647f, 0.0f, 0.0f);
    
 bake();
 }
}
