package thaumcraft.world;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.Thaumcraft;

/** Client-only tint registration keeps dedicated servers free of client class references. */
@Mod.EventBusSubscriber(modid = Thaumcraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldClientEvents {
    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        WorldModule.CRYSTALS.forEach((aspect, block) -> event.register(
                (state, level, pos, tint) -> WorldModule.CRYSTAL_COLORS.get(aspect), block.get()));
        event.register((state, level, pos, tint) -> thaumcraft.api.aspects.Aspect.FLUX.getColor(),
                thaumcraft.catalog.blocks.CatalogBlocks.block("crystal_vitium"));
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        WorldModule.CRYSTALS.forEach((aspect, block) -> event.register(
                (stack, tint) -> WorldModule.CRYSTAL_COLORS.get(aspect), block.get()));
        WorldModule.VIS_CRYSTALS.forEach((aspect, item) -> event.register(
                (stack, tint) -> WorldModule.CRYSTAL_COLORS.get(aspect), item.get()));
    }
}
