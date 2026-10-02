package thaumcraft.catalog.entities.client;

/** Cuboids/UV/pivots extracted from pinned BETA26 ModelEldritchCrab. */
public final class LegacyEldritchCrabModel extends LegacyVisualModel {

    LegacyPart TailHelm;
    LegacyPart TailBare;
    LegacyPart RFLeg1;
    LegacyPart RClaw1;
    LegacyPart Head1;
    LegacyPart RClaw0;
    LegacyPart RClaw2;
    LegacyPart LClaw2;
    LegacyPart LClaw1;
    LegacyPart RArm;
    LegacyPart Torso;
    LegacyPart RRLeg1;
    LegacyPart Head0;
    LegacyPart LRLeg1;
    LegacyPart LFLeg1;
    LegacyPart RRLeg0;
    LegacyPart RFLeg0;
    LegacyPart LFLeg0;
    LegacyPart LRLeg0;
    LegacyPart LClaw0;
    LegacyPart LArm;
    
        public LegacyEldritchCrabModel() {

        textureWidth = 128;
        textureHeight = 64;
        (TailHelm = new LegacyPart(this, 0, 0)).addBox(-4.5f, -4.5f, -0.4f, 9, 9, 9);
        TailHelm.setRotationPoint(0.0f, 18.0f, 0.0f);
        setRotation(TailHelm, 0.1047198f, 0.0f, 0.0f);
        (TailBare = new LegacyPart(this, 64, 0)).addBox(-4.0f, -4.0f, -0.4f, 8, 8, 8);
        TailBare.setRotationPoint(0.0f, 18.0f, 0.0f);
        setRotation(TailBare, 0.1047198f, 0.0f, 0.0f);
        (RClaw1 = new LegacyPart(this, 0, 47)).addBox(-2.0f, -1.0f, -5.066667f, 4, 3, 5);
        RClaw1.setRotationPoint(-6.0f, 15.5f, -10.0f);
        (Head1 = new LegacyPart(this, 0, 38)).addBox(-2.0f, -1.5f, -9.066667f, 4, 4, 1);
        Head1.setRotationPoint(0.0f, 18.0f, 0.0f);
        (RClaw0 = new LegacyPart(this, 0, 55)).addBox(-2.0f, -2.5f, -3.066667f, 4, 5, 3);
        RClaw0.setRotationPoint(-6.0f, 17.0f, -7.0f);
        (RClaw2 = new LegacyPart(this, 14, 54)).addBox(-1.5f, -1.0f, -4.066667f, 3, 2, 5);
        RClaw2.setRotationPoint(-6.0f, 18.5f, -10.0f);
        setRotation(RClaw2, 0.3141593f, 0.0f, 0.0f);
        (RArm = new LegacyPart(this, 44, 4)).addBox(-1.0f, -1.0f, -5.066667f, 2, 2, 6);
        RArm.setRotationPoint(-3.0f, 17.0f, -4.0f);
        setRotation(RArm, 0.0f, 0.7504916f, 0.0f);
        (LClaw2 = new LegacyPart(this, 14, 54)).addBox(-1.5f, -1.0f, -4.066667f, 3, 2, 5);
        LClaw2.setRotationPoint(6.0f, 18.5f, -10.0f);
        setRotation(LClaw2, 0.3141593f, 0.0f, 0.0f);
        LClaw1 = new LegacyPart(this, 0, 47);
        LClaw1.mirror = true;
        LClaw1.addBox(-2.0f, -1.0f, -5.066667f, 4, 3, 5);
        LClaw1.setRotationPoint(6.0f, 15.5f, -10.0f);
        LClaw0 = new LegacyPart(this, 0, 55);
        LClaw0.mirror = true;
        LClaw0.addBox(-2.0f, -2.5f, -3.066667f, 4, 5, 3);
        LClaw0.setRotationPoint(6.0f, 17.0f, -7.0f);
        (LArm = new LegacyPart(this, 44, 4)).addBox(-1.0f, -1.0f, -4.066667f, 2, 2, 6);
        LArm.setRotationPoint(4.0f, 17.0f, -5.0f);
        setRotation(LArm, 0.0f, -0.7504916f, 0.0f);
        (Torso = new LegacyPart(this, 0, 18)).addBox(-3.5f, -3.5f, -6.066667f, 7, 7, 6);
        Torso.setRotationPoint(0.0f, 18.0f, 0.0f);
        setRotation(Torso, 0.0523599f, 0.0f, 0.0f);
        (Head0 = new LegacyPart(this, 0, 31)).addBox(-2.5f, -2.0f, -8.066667f, 5, 5, 2);
        Head0.setRotationPoint(0.0f, 18.0f, 0.0f);
        (RRLeg1 = new LegacyPart(this, 36, 4)).addBox(-4.5f, 1.0f, -0.9f, 2, 5, 2);
        RRLeg1.setRotationPoint(-4.0f, 20.0f, -1.5f);
        (RFLeg1 = new LegacyPart(this, 36, 4)).addBox(-5.0f, 1.0f, -1.066667f, 2, 5, 2);
        RFLeg1.setRotationPoint(-4.0f, 20.0f, -3.5f);
        (LRLeg1 = new LegacyPart(this, 36, 4)).addBox(2.5f, 1.0f, -0.9f, 2, 5, 2);
        LRLeg1.setRotationPoint(4.0f, 20.0f, -1.5f);
        (LFLeg1 = new LegacyPart(this, 36, 4)).addBox(3.0f, 1.0f, -1.066667f, 2, 5, 2);
        LFLeg1.setRotationPoint(4.0f, 20.0f, -3.5f);
        (RRLeg0 = new LegacyPart(this, 36, 0)).addBox(-4.5f, -1.0f, -0.9f, 6, 2, 2);
        RRLeg0.setRotationPoint(-4.0f, 20.0f, -1.5f);
        (RFLeg0 = new LegacyPart(this, 36, 0)).addBox(-5.0f, -1.0f, -1.066667f, 6, 2, 2);
        RFLeg0.setRotationPoint(-4.0f, 20.0f, -3.5f);
        (LFLeg0 = new LegacyPart(this, 36, 0)).addBox(-1.0f, -1.0f, -1.066667f, 6, 2, 2);
        LFLeg0.setRotationPoint(4.0f, 20.0f, -3.5f);
        (LRLeg0 = new LegacyPart(this, 36, 0)).addBox(-1.5f, -1.0f, -0.9f, 6, 2, 2);
        LRLeg0.setRotationPoint(4.0f, 20.0f, -1.5f);
    
        bake();
    }
}
