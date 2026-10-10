package thaumcraft.research;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ambient.Bat;
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
import java.util.List;

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
        List<String> facts = scanFacts(scanned);
        return facts.isEmpty() ? null : facts.get(0);
    }

    /** ConfigResearch may register multiple proofs for one specimen (FireBat).
     * Reading these predicates never grants discoveries or canonical stages. */
    public static List<String> scanFacts(@Nullable Object scanned) {
        var facts = new java.util.LinkedHashSet<>(auromancyScanFacts(scanned));
        facts.addAll(GolemancyProgressionEvents.scanFacts(scanned));
        facts.addAll(OreProgressionScans.scanFacts(scanned));
        // Both original ScanEntity registrations are tied to the working rift class,
        // never to a held catalogue item or a client-supplied entity identifier.
        if (scanned instanceof thaumcraft.world.rift.FluxRiftEntity)
            facts.addAll(List.of("f_toomuchflux", "!FluxRift"));
        facts.addAll(thaumcraft.scanning.ScanEffectFacts.facts(scanned));
        return List.copyOf(facts);
    }

    private static List<String> auromancyScanFacts(@Nullable Object scanned) {
        if (scanned instanceof ItemEntity item) scanned = item.getItem();
        if (scanned instanceof ItemStack stack) {
            if (stack.isEmpty()) return List.of();
            if (stack.is(Items.ARROW)) return List.of("f_arrow");
            if (stack.is(Items.DRAGON_BREATH)) return List.of("!DRAGONBREATH");
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && id.toString().equals("thaumcraft:pech_wand")) return List.of("!Pechwand");
            // Original ScanItem uses wildcard metadata32767: pearl/nodule/mote all qualify.
            if (id != null && id.toString().equals("thaumcraft:primordial_pearl")) return List.of("PRIMPEARL");
            if (id != null && id.toString().equals("thaumcraft:void_seed")) return List.of("f_VOIDSEED");
            return List.of();
        }
        if (scanned instanceof Bat) return List.of("f_BAT");
        if (scanned instanceof Entity entity) {
            var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            // EntityGolem.class,true includes Snow/Iron Golem and Shulker in 1.12.
            // The visual TC entity catalogue has flattened EntityOwnedConstruct's
            // hierarchy; these are its four audited concrete registered IDs.
            if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem
                    || id != null && java.util.Set.of("thaumcraft:golem", "thaumcraft:turret_basic", "thaumcraft:turret_advanced", "thaumcraft:arcane_bore").contains(id.toString()))
                return List.of("f_golem");
            if (id != null && id.toString().equals("thaumcraft:fire_bat")) return List.of("!Firebat", "f_BAT");
            String projectile = projectileFact(entity);
            if (projectile != null) return List.of(projectile);
        }
        return List.of();
    }

    /** Read-only HUD eligibility; never acquire facts from a hover or snapshot. */
    public static boolean hasUnseenScanFact(ServerPlayer player, @Nullable Object scanned) {
        return validPlayer(player) && scanFacts(scanned).stream()
                .anyMatch(fact -> !KnowledgeStore.get(player).isResearchCompleteStrict(fact));
    }

    /** Called only by the actual server-owned Thaumometer scan commit after target/range validation. */
    public static boolean recordScannedFact(ServerPlayer player, @Nullable Object scanned) {
        if (!validPlayer(player) || scanned instanceof Entity entity && (entity.isRemoved() || entity.level() != player.level())) return false;
        boolean discovered = false;
        for (String fact : scanFacts(scanned)) {
            if (fact.equals("PRIMPEARL")||fact.equals("!Firebat")||fact.equals("ORE")) {
                var state = KnowledgeStore.get(player);
                if (state.isResearchCompleteStrict(fact)) continue;
                // A scan grants this original no-cost canonical entry immediately,
                // including its ordinary five XP and persistent completion stage.
                discovered |= KnowledgeStore.recordFact(player, fact);
                var result = ResearchProgression.advance(player, fact, state.researchStage(fact));
                discovered |= result == ResearchProgression.Result.COMPLETE;
                if (result == ResearchProgression.Result.COMPLETE) ResearchNetwork.sync(player);
            } else discovered |= KnowledgeStore.recordFact(player, fact);
        }
        return discovered;
    }

    private static boolean validPlayer(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator() && player.serverLevel().getServer().isSameThread();
    }

    private static void recordFact(ServerPlayer player, String fact, String message) {
        if (!KnowledgeStore.get(player).knowsResearch(fact) && KnowledgeStore.recordFact(player, fact) && player.connection != null)
            player.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE), true);
    }
}
