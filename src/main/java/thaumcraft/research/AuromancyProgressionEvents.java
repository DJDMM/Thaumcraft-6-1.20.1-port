package thaumcraft.research;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** BETA26 EntityEvents: the stage-two lesson observes a fire damage source. */
@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class AuromancyProgressionEvents {
    private AuromancyProgressionEvents() {}

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || !player.serverLevel().getServer().isSameThread() || !event.getSource().is(DamageTypeTags.IS_FIRE)) return;
        var knowledge = KnowledgeStore.get(player);
        if (knowledge.isResearchCompleteStrict("BASEAUROMANCY@2")
                && !knowledge.isResearchCompleteStrict("f_onfire") && KnowledgeStore.recordFact(player, "f_onfire")
                && player.connection != null)
            player.displayClientMessage(Component.translatable("got.onfire").withStyle(ChatFormatting.DARK_PURPLE), true);
    }
}
