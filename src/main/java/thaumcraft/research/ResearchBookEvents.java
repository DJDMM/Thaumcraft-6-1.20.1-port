package thaumcraft.research;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.Thaumcraft;

/** Seed the client's delta baseline before in-session discoveries, also on a dedicated server. */
@Mod.EventBusSubscriber(modid = Thaumcraft.MOD_ID)
public final class ResearchBookEvents {
    private ResearchBookEvents() {}

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ResearchNetwork.sync(player);
    }
}
