package thaumcraft.catalog.armor.client;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import thaumcraft.catalog.armor.CatalogArmorItem;
import thaumcraft.catalog.entities.client.*;
import java.util.Map;
import java.util.HashMap;
public final class CatalogArmorExtension implements IClientItemExtensions {
    private final CatalogArmorItem item;
    private final Map<Boolean,LegacyArmorGeometry> geometry=new HashMap<>();
    private final Map<Boolean,HumanoidModel<?>> models=new HashMap<>();
    public CatalogArmorExtension(CatalogArmorItem item) { this.item=item; }
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original) {
        String source=item.spec().sourceClass();
        if (!java.util.List.of("ItemVoidRobeArmor","ItemFortressArmor","ItemCultistPlateArmor","ItemCultistRobeArmor","ItemCultistLeaderArmor").contains(source)) return original;
        boolean inner=slot==EquipmentSlot.LEGS;
        var mesh=geometry.computeIfAbsent(inner,k -> switch(source) {
            case "ItemFortressArmor" -> new LegacyFortressArmorArmor(k?.5f:1);
            case "ItemCultistPlateArmor" -> new LegacyKnightArmorArmor(k?.5f:1);
            case "ItemCultistLeaderArmor" -> new LegacyLeaderArmorArmor(k?.5f:1);
            default -> new LegacyRobeArmor(k?.5f:1);
        });
        mesh.configure(entity,stack);
        return models.computeIfAbsent(inner,k -> new HumanoidModel<LivingEntity>(mesh.humanoidRoot()));
    }
}
