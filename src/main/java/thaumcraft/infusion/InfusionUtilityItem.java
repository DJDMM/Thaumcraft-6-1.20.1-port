package thaumcraft.infusion;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.catalog.*;
import java.util.List;
/** The gauntlet's block callbacks and the Vis Resonator's read-only aura HUD are operational. */
public final class InfusionUtilityItem extends CatalogItem {
    private final boolean resonator;
    public InfusionUtilityItem(CatalogModule.Spec spec) { super(spec); resonator = spec.id().equals("vis_resonator"); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(resonator ? "tooltip.thaumcraft.vis_resonator" : "tooltip.thaumcraft.caster_utility"));
    }
}
