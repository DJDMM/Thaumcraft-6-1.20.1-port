package thaumcraft.research.celestial;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public final class CelestialNoteItem extends Item {
    private final CelestialVariant variant;
    public CelestialNoteItem(CelestialVariant variant) { super(new Item.Properties()); this.variant = variant; }
    public CelestialVariant variant() { return variant; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.celestial_notes." + variant.suffix() + ".text").withStyle(ChatFormatting.AQUA));
    }
}
