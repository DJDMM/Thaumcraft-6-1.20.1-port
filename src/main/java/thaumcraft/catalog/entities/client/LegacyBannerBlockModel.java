package thaumcraft.catalog.entities.client;
/** Original neutral TC6 ModelBanner. */
public final class LegacyBannerBlockModel extends LegacyVisualModel {
    LegacyPart B1;
    LegacyPart B2;
    LegacyPart Beam;
    public LegacyPart Banner;
    LegacyPart Pole;
 public LegacyBannerBlockModel() {

        textureWidth = 128;
        textureHeight = 64;
        (B1 = new LegacyPart(this, 0, 29)).addBox(-5.0f, -7.5f, -1.5f, 2, 3, 3);
        B1.setRotationPoint(0.0f, 0.0f, 0.0f);
        B1.setTextureSize(128, 64);
        B1.mirror = true;
        setRotation(B1, 0.0f, 0.0f, 0.0f);
        (B2 = new LegacyPart(this, 0, 29)).addBox(3.0f, -7.5f, -1.5f, 2, 3, 3);
        B2.setRotationPoint(0.0f, 0.0f, 0.0f);
        B2.setTextureSize(128, 64);
        B2.mirror = true;
        setRotation(B2, 0.0f, 0.0f, 0.0f);
        (Beam = new LegacyPart(this, 30, 0)).addBox(-7.0f, -7.0f, -1.0f, 14, 2, 2);
        Beam.setRotationPoint(0.0f, 0.0f, 0.0f);
        Beam.setTextureSize(128, 64);
        Beam.mirror = true;
        setRotation(Beam, 0.0f, 0.0f, 0.0f);
        (Banner = new LegacyPart(this, 0, 0)).addBox(-7.0f, 0.0f, -0.5f, 14, 28, 1);
        Banner.setRotationPoint(0.0f, -5.0f, 0.0f);
        Banner.setTextureSize(128, 64);
        Banner.mirror = true;
        setRotation(Banner, 0.0f, 0.0f, 0.0f);
        (Pole = new LegacyPart(this, 62, 0)).addBox(0.0f, 0.0f, -1.0f, 2, 31, 2);
        Pole.setRotationPoint(-1.0f, -7.0f, -2.0f);
        Pole.setTextureSize(128, 64);
        Pole.mirror = true;
        setRotation(Pole, 0.0f, 0.0f, 0.0f);
    
 bake();
 }
}
