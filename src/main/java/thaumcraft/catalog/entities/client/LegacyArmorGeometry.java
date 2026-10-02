package thaumcraft.catalog.entities.client;
import net.minecraft.client.model.geom.ModelPart;
import java.util.*;
/** Constructor host for original armor geometry; animation uses modern humanoid poses. */
public abstract class LegacyArmorGeometry extends LegacyVisualModel {
    protected final LegacyPart bipedHead,bipedHeadwear,bipedBody,bipedRightArm,bipedLeftArm,bipedRightLeg,bipedLeftLeg;
    protected LegacyArmorGeometry(float inflation) {
        textureWidth=128;textureHeight=64;
        bipedHead=new LegacyPart(this,0,0);bipedHeadwear=new LegacyPart(this,32,0);
        bipedBody=new LegacyPart(this,16,16);bipedRightArm=new LegacyPart(this,40,16);bipedLeftArm=new LegacyPart(this,40,16);
        bipedRightLeg=new LegacyPart(this,0,16);bipedLeftLeg=new LegacyPart(this,0,16);
        bipedRightArm.setRotationPoint(-5,2,0);bipedLeftArm.setRotationPoint(5,2,0);
        bipedRightLeg.setRotationPoint(-1.9f,12,0);bipedLeftLeg.setRotationPoint(1.9f,12,0);
    }
    public ModelPart humanoidRoot() {
        return new ModelPart(List.of(),Map.of("head",bipedHead.part,"hat",bipedHeadwear.part,"body",bipedBody.part,
                "right_arm",bipedRightArm.part,"left_arm",bipedLeftArm.part,"right_leg",bipedRightLeg.part,"left_leg",bipedLeftLeg.part));
    }
    public void configure(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.item.ItemStack stack) {
        if (this instanceof LegacyFortressArmorArmor fortress) {
            int pieces=0;
            for(var worn:entity.getArmorSlots()) if (worn.getItem() instanceof thaumcraft.catalog.armor.CatalogArmorItem armor && armor.spec().sourceClass().equals("ItemFortressArmor")) pieces++;
            fortress.Goggles.part.visible=stack.hasTag() && stack.getTag().contains("goggles");
            for(int i=0;i<3;i++) fortress.Mask[i].part.visible=stack.hasTag() && stack.getTag().contains("mask") && stack.getTag().getInt("mask")==i;
            for(var part:List.of(fortress.Scroll,fortress.OrnamentL,fortress.OrnamentL2,fortress.OrnamentR,fortress.OrnamentR2,fortress.Gemornament,fortress.Gem)) part.part.visible=pieces>=3;
            for(var part:List.of(fortress.Book,fortress.flapL,fortress.flapR)) part.part.visible=pieces>=2;
        }
    }
}
