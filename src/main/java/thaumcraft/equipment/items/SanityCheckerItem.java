package thaumcraft.equipment.items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.research.ResearchNetwork;

/** Read-only meter. Holding it cannot add or remove any warp. */
public final class SanityCheckerItem extends Item {
    public SanityCheckerItem() { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }
    @Override public void inventoryTick(ItemStack stack,Level level,Entity wearer,int slot,boolean selected) {
        if(wearer instanceof ServerPlayer player && player.tickCount%20==0
                && (selected || player.getOffhandItem()==stack)) ResearchNetwork.sync(player);
    }
}
