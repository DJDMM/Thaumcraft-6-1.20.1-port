package thaumcraft.equipment.cleansing;

import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class CleansingEvents {
    private CleansingEvents() {}
    @SubscribeEvent public static void itemExpires(ItemExpireEvent event) {
        if (event.isCanceled()) return;
        var entity = event.getEntity();
        if (!entity.level().isClientSide && entity.getItem().getItem() instanceof BathSaltsItem)
            CleansingSupport.convertBath(entity.level(), entity.blockPosition());
        // Do not cancel expiry or shrink only one salt: TC6 consumes the complete dropped stack.
    }
}
