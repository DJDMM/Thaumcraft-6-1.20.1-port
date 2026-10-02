package thaumcraft.equipment.cleansing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** In BETA26 the dropped entity expires after ten seconds; there is no right-click water shortcut. */
public final class BathSaltsItem extends Item {
    public BathSaltsItem(Properties properties) { super(properties); }
    @Override public int getEntityLifespan(ItemStack stack, Level level) { return 200; }
}
