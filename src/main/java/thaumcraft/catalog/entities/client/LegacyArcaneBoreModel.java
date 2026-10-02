package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelArcaneBore. */
public final class LegacyArcaneBoreModel extends LegacyVisualModel {

    LegacyPart crystal;
    LegacyPart leg2;
    LegacyPart tripod;
    LegacyPart leg3;
    LegacyPart leg4;
    LegacyPart leg1;
    LegacyPart magbase;
    LegacyPart base;
    LegacyPart domebase;
    LegacyPart dome;
    LegacyPart tip;
    
        public LegacyArcaneBoreModel() {

        textureWidth = 64;
        textureHeight = 32;
        (leg2 = new LegacyPart(this, 20, 10)).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2);
        leg2.setRotationPoint(0.0f, 12.0f, 0.0f);
        leg2.setTextureSize(64, 32);
        setRotation(leg2, 0.5235988f, 1.570796f, 0.0f);
        (tripod = new LegacyPart(this, 13, 0)).addBox(-1.5f, 0.0f, -1.5f, 3, 2, 3);
        tripod.setRotationPoint(0.0f, 12.0f, 0.0f);
        tripod.setTextureSize(64, 32);
        setRotation(tripod, 0.0f, 0.0f, 0.0f);
        (leg3 = new LegacyPart(this, 20, 10)).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2);
        leg3.setRotationPoint(0.0f, 12.0f, 0.0f);
        leg3.setTextureSize(64, 32);
        setRotation(leg3, 0.5235988f, 3.141593f, 0.0f);
        (leg4 = new LegacyPart(this, 20, 10)).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2);
        leg4.setRotationPoint(0.0f, 12.0f, 0.0f);
        leg4.setTextureSize(64, 32);
        setRotation(leg4, 0.5235988f, 4.712389f, 0.0f);
        (leg1 = new LegacyPart(this, 20, 10)).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2);
        leg1.setRotationPoint(0.0f, 12.0f, 0.0f);
        leg1.setTextureSize(64, 32);
        setRotation(leg1, 0.5235988f, 0.0f, 0.0f);
        (base = new LegacyPart(this, 32, 0)).addBox(-3.0f, -6.0f, -3.0f, 6, 6, 6);
        base.setRotationPoint(0.0f, 13.0f, 0.0f);
        base.setTextureSize(64, 32);
        setRotation(base, 0.0f, 0.0f, 0.0f);
        (crystal = new LegacyPart(this, 32, 25)).addBox(-1.0f, -4.0f, 5.0f, 2, 2, 2);
        crystal.setRotationPoint(0.0f, 0.0f, 0.0f);
        crystal.setTextureSize(64, 32);
        setRotation(crystal, 0.0f, 0.0f, 0.0f);
        (domebase = new LegacyPart(this, 32, 19)).addBox(-2.0f, -5.0f, 3.0f, 4, 4, 1);
        domebase.setRotationPoint(0.0f, 0.0f, 0.0f);
        domebase.setTextureSize(64, 32);
        setRotation(domebase, 0.0f, 0.0f, 0.0f);
        (dome = new LegacyPart(this, 44, 16)).addBox(-2.0f, -5.0f, 4.0f, 4, 4, 4);
        dome.setRotationPoint(0.0f, 0.0f, 0.0f);
        dome.setTextureSize(64, 32);
        setRotation(dome, 0.0f, 0.0f, 0.0f);
        (magbase = new LegacyPart(this, 0, 18)).addBox(-1.0f, -4.0f, -6.0f, 2, 2, 3);
        magbase.setRotationPoint(0.0f, 0.0f, 0.0f);
        magbase.setTextureSize(64, 32);
        magbase.mirror = true;
        setRotation(magbase, 0.0f, 0.0f, 0.0f);
        (tip = new LegacyPart(this, 0, 9)).addBox(-1.5f, 0.0f, -1.5f, 3, 3, 3);
        tip.setRotationPoint(0.0f, -3.0f, -6.0f);
        tip.setTextureSize(64, 32);
        tip.mirror = true;
        setRotation(tip, -1.570796f, 0.0f, 0.0f);
        base.addChild(crystal);
        base.addChild(dome);
        base.addChild(domebase);
        base.addChild(magbase);
        base.addChild(tip);
    
        bake();
    }
}
