package thaumcraft.research;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Original ConfigResearch periodic height/statistics facts; none completes a research entry. */
@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class InfusionProgressionEvents {
    private InfusionProgressionEvents() {}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 200 == 0) checkPeriodicFacts(player);
    }

    public static void checkPeriodicFacts(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !player.serverLevel().getServer().isSameThread()) return;
        PlayerKnowledge knowledge = KnowledgeStore.get(player);
        if (knowledge.knowsResearch("UNLOCKAUROMANCY@1") && !knowledge.knowsResearch("UNLOCKAUROMANCY@2")) {
            if (player.getY() < 10) KnowledgeStore.recordFact(player, "m_deepdown");
            // Explicit 1.20 vertical-bound adaptation of BETA26's getActualHeight()*0.4.
            if (player.getY() > player.serverLevel().getMaxBuildHeight() * .4)
                KnowledgeStore.recordFact(player, "m_uphigh");
        }
        var stats = player.getStats();
        if (stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) > 160000) KnowledgeStore.recordFact(player, "m_walker");
        if (stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)) > 80000) KnowledgeStore.recordFact(player, "m_runner");
        if (stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM)) > 8000) KnowledgeStore.recordFact(player, "m_swimmer");
        if (stats.getValue(Stats.CUSTOM.get(Stats.JUMP)) > 500) KnowledgeStore.recordFact(player, "m_jumper");
    }
}
