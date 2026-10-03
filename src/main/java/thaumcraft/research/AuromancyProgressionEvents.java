package thaumcraft.research;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nullable;

/** BETA26 EntityEvents and ConfigResearch scan facts; scan acquisition has no stage gate. */
@Mod.EventBusSubscriber(modid = "thaumcraft")
public final class AuromancyProgressionEvents {
    private AuromancyProgressionEvents() {}

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !validPlayer(player)) return;
        var knowledge = KnowledgeStore.get(player);
        if (event.getSource().is(DamageTypeTags.IS_FIRE) && knowledge.isResearchCompleteStrict("BASEAUROMANCY@2"))
            recordFact(player, "f_onfire", "got.onfire");
        // Immediate projectile, not its owner or the DamageType's projectile/fire tags.
        // BETA26 does not impose an extra positive-amount predicate on an already dispatched hurt event.
        if (knowledge.isResearchCompleteStrict("FOCUSPROJECTILE@2")) {
            String fact = projectileFact(event.getSource().getDirectEntity());
            if (fact != null) recordFact(player, fact, "got.projectile");
        }
    }

    /** The old EntityFireball base included WitherSkull and DragonFireball; modern Fireball no longer does. */
    @Nullable public static String projectileFact(@Nullable Entity entity) {
        if (entity instanceof AbstractArrow && !(entity instanceof ThrownTrident)) return "f_arrow";
        if (entity instanceof Fireball || entity instanceof WitherSkull || entity instanceof DragonFireball) return "f_fireball";
        if (entity instanceof LlamaSpit) return "f_spit";
        return null;
    }

    /** Pure ConfigResearch ScanEntity/ScanItem predicate. Own TC focus projectiles and snowballs do not match. */
    @Nullable public static String scanFact(@Nullable Object scanned) {
        if (scanned instanceof ItemEntity item) scanned = item.getItem();
        if (scanned instanceof ItemStack stack) return !stack.isEmpty() && stack.is(Items.ARROW) ? "f_arrow" : null;
        return scanned instanceof Entity entity ? projectileFact(entity) : null;
    }

    /** Read-only HUD eligibility; never acquire facts from a hover or snapshot. */
    public static boolean hasUnseenScanFact(ServerPlayer player, @Nullable Object scanned) {
        String fact = scanFact(scanned);
        return validPlayer(player) && fact != null && !KnowledgeStore.get(player).knowsResearch(fact);
    }

    /** Called only by the actual server-owned Thaumometer scan commit after target/range validation. */
    public static boolean recordScannedFact(ServerPlayer player, @Nullable Object scanned) {
        if (!validPlayer(player) || scanned instanceof Entity entity && (entity.isRemoved() || entity.level() != player.level())) return false;
        String fact = scanFact(scanned);
        return fact != null && KnowledgeStore.recordFact(player, fact);
    }

    private static boolean validPlayer(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator() && player.serverLevel().getServer().isSameThread();
    }

    private static void recordFact(ServerPlayer player, String fact, String message) {
        if (!KnowledgeStore.get(player).knowsResearch(fact) && KnowledgeStore.recordFact(player, fact) && player.connection != null)
            player.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE), true);
    }
}
