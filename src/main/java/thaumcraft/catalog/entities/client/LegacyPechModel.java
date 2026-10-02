package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelPech. */
public final class LegacyPechModel extends LegacyVisualModel {

    LegacyPart Jowls;
    LegacyPart LowerPack;
    LegacyPart UpperPack;
    
        LegacyPart bipedBody, bipedRightLeg, bipedLeftLeg, bipedHead, bipedRightArm, bipedLeftArm;
    public LegacyPechModel() {

        textureWidth = 128;
        textureHeight = 64;
        (bipedBody = new LegacyPart(this, 34, 12)).addBox(-3.0f, 0.0f, 0.0f, 6, 10, 6);
        bipedBody.setRotationPoint(0.0f, 9.0f, -3.0f);
        bipedBody.setTextureSize(128, 64);
        bipedBody.mirror = true;
        setRotation(bipedBody, 0.3129957f, 0.0f, 0.0f);
        bipedRightLeg = new LegacyPart(this, 35, 1);
        bipedRightLeg.mirror = true;
        bipedRightLeg.addBox(-2.9f, 0.0f, 0.0f, 3, 6, 3);
        bipedRightLeg.setRotationPoint(0.0f, 18.0f, 0.0f);
        bipedRightLeg.setTextureSize(128, 64);
        bipedRightLeg.mirror = true;
        setRotation(bipedRightLeg, 0.0f, 0.0f, 0.0f);
        bipedRightLeg.mirror = false;
        (bipedLeftLeg = new LegacyPart(this, 35, 1)).addBox(-0.1f, 0.0f, 0.0f, 3, 6, 3);
        bipedLeftLeg.setRotationPoint(0.0f, 18.0f, 0.0f);
        bipedLeftLeg.setTextureSize(128, 64);
        bipedLeftLeg.mirror = true;
        setRotation(bipedLeftLeg, 0.0f, 0.0f, 0.0f);
        (bipedHead = new LegacyPart(this, 2, 11)).addBox(-3.5f, -5.0f, -5.0f, 7, 5, 5);
        bipedHead.setRotationPoint(0.0f, 8.0f, 0.0f);
        bipedHead.setTextureSize(128, 64);
        bipedHead.mirror = true;
        setRotation(bipedHead, 0.0f, 0.0f, 0.0f);
        (Jowls = new LegacyPart(this, 1, 21)).addBox(-4.0f, -1.0f, -6.0f, 8, 3, 5);
        Jowls.setRotationPoint(0.0f, 8.0f, 0.0f);
        Jowls.setTextureSize(128, 64);
        Jowls.mirror = true;
        setRotation(Jowls, 0.0f, 0.0f, 0.0f);
        (LowerPack = new LegacyPart(this, 0, 0)).addBox(-5.0f, 0.0f, 0.0f, 10, 5, 5);
        LowerPack.setRotationPoint(0.0f, 10.0f, 3.5f);
        LowerPack.setTextureSize(128, 64);
        LowerPack.mirror = true;
        setRotation(LowerPack, 0.3013602f, 0.0f, 0.0f);
        (UpperPack = new LegacyPart(this, 64, 1)).addBox(-7.5f, -14.0f, 0.0f, 15, 14, 11);
        UpperPack.setRotationPoint(0.0f, 10.0f, 3.0f);
        UpperPack.setTextureSize(128, 64);
        UpperPack.mirror = true;
        setRotation(UpperPack, 0.4537856f, 0.0f, 0.0f);
        bipedRightArm = new LegacyPart(this, 52, 2);
        bipedRightArm.mirror = true;
        bipedRightArm.addBox(-2.0f, 0.0f, -1.0f, 2, 6, 2);
        bipedRightArm.setRotationPoint(-3.0f, 10.0f, -1.0f);
        bipedRightArm.setTextureSize(128, 64);
        bipedRightArm.mirror = true;
        setRotation(bipedRightArm, 0.0f, 0.0f, 0.0f);
        bipedRightArm.mirror = false;
        (bipedLeftArm = new LegacyPart(this, 52, 2)).addBox(0.0f, 0.0f, -1.0f, 2, 6, 2);
        bipedLeftArm.setRotationPoint(3.0f, 10.0f, -1.0f);
        bipedLeftArm.setTextureSize(128, 64);
        bipedLeftArm.mirror = true;
        setRotation(bipedLeftArm, 0.0f, 0.0f, 0.0f);
    
        bake();
    }
}
