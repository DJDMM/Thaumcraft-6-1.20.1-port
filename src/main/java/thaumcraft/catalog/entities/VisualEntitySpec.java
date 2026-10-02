package thaumcraft.catalog.entities;

import java.util.List;

/**
 * BETA26 registrations and neutral World-constructor dimensions, including inherited sizes.
 * Variable Thaumic Slime uses its smallest World-constructor variant (size 2).
 * Visual catalogue entries do not enable original mechanics or dynamic resizing.
 */
public record VisualEntitySpec(String id, String legacyId, boolean living,
        float width, float height, int trackingBlocks, int updateInterval,
        boolean trackVelocity, String model, String texture, int eggBase, int eggSpots) {
    public static final List<VisualEntitySpec> ALL = List.of(
        new VisualEntitySpec("cultist_portal_greater", "CultistPortalGreater", true, 1.5f, 3.0f, 64, 20, false, "portal", "textures/misc/cultist_portal.png", 6842578, 32896),
        new VisualEntitySpec("cultist_portal_lesser", "CultistPortalLesser", true, 1.5f, 3.0f, 64, 20, false, "portal", "textures/misc/cultist_portal.png", 9438728, 6316242),
        new VisualEntitySpec("flux_rift", "FluxRift", false, 2.0f, 2.0f, 64, 20, false, "rift", "minecraft:textures/entity/end_portal.png", 6842578, 8421504),
        new VisualEntitySpec("special_item", "SpecialItem", false, 0.25f, 0.25f, 64, 20, true, "item", "", 6842578, 8421504),
        new VisualEntitySpec("following_item", "FollowItem", false, 0.25f, 0.25f, 64, 20, false, "item", "", 6842578, 8421504),
        // Only the configured falling-block constructor changes this inherited size to .98 x .98.
        new VisualEntitySpec("falling_taint", "FallingTaint", false, 0.6f, 1.8f, 64, 3, true, "block", "", 6842578, 8421504),
        new VisualEntitySpec("alumentum", "Alumentum", false, 0.25f, 0.25f, 64, 20, true, "none", "", 6842578, 8421504),
        new VisualEntitySpec("golem_dart", "GolemDart", false, 0.2f, 0.2f, 64, 20, false, "dart", "minecraft:textures/entity/projectiles/arrow.png", 6842578, 8421504),
        new VisualEntitySpec("eldritch_orb", "EldritchOrb", false, 0.25f, 0.25f, 64, 20, true, "eldritch_orb", "textures/misc/particles.png", 6842578, 8421504),
        new VisualEntitySpec("bottle_taint", "BottleTaint", false, 0.25f, 0.25f, 64, 20, true, "bottle", "", 6842578, 8421504),
        new VisualEntitySpec("golem_orb", "GolemOrb", false, 0.25f, 0.25f, 64, 3, true, "golem_orb", "textures/misc/particles.png", 6842578, 8421504),
        new VisualEntitySpec("grapple", "Grapple", false, 0.1f, 0.1f, 64, 20, true, "Grappler", "textures/entity/grappler.png", 6842578, 8421504),
        new VisualEntitySpec("causality_collapser", "CausalityCollapser", false, 0.25f, 0.25f, 64, 20, true, "none", "", 6842578, 8421504),
        new VisualEntitySpec("focus_projectile", "FocusProjectile", false, 0.15f, 0.15f, 64, 20, true, "none", "", 6842578, 8421504),
        // The casting constructor later replaces this with 2 * radius x .5; .15 x .15 is transient.
        new VisualEntitySpec("focus_cloud", "FocusCloud", false, 0.6f, 1.8f, 64, 20, true, "cloud", "textures/misc/particles.png", 6842578, 8421504),
        new VisualEntitySpec("focus_mine", "Focusmine", false, 0.15f, 0.15f, 64, 20, true, "mine", "textures/entity/mine.png", 6842578, 8421504),
        new VisualEntitySpec("turret_basic", "TurretBasic", true, 0.95f, 1.25f, 64, 3, true, "Crossbow", "textures/entity/crossbow.png", 6842578, 8421504),
        new VisualEntitySpec("turret_advanced", "TurretAdvanced", true, 0.95f, 1.5f, 64, 3, true, "obj_crossbow", "textures/entity/crossbow_advanced.png", 6842578, 8421504),
        new VisualEntitySpec("arcane_bore", "ArcaneBore", true, 0.9f, 0.9f, 64, 3, true, "ArcaneBore", "textures/entity/arcanebore.png", 6842578, 8421504),
        new VisualEntitySpec("golem", "Golem", true, 0.4f, 0.9f, 64, 3, true, "obj_golem", "textures/entity/golems/mat_wood.png", 6842578, 8421504),
        new VisualEntitySpec("eldritch_warden", "EldritchWarden", true, 1.5f, 3.5f, 64, 3, true, "EldritchGuardian", "textures/entity/eldritch_warden.png", 6842578, 8421504),
        new VisualEntitySpec("eldritch_golem", "EldritchGolem", true, 1.75f, 3.5f, 64, 3, true, "EldritchGolem", "textures/entity/eldritch_golem.png", 6842578, 8947848),
        new VisualEntitySpec("cultist_leader", "CultistLeader", true, 0.75f, 2.25f, 64, 3, true, "humanoid", "textures/entity/cultist.png", 6842578, 9438728),
        new VisualEntitySpec("taintacle_giant", "TaintacleGiant", true, 1.1f, 6.0f, 96, 3, false, "Taintacle14", "textures/entity/taintacle.png", 6842578, 10618530),
        new VisualEntitySpec("brainy_zombie", "BrainyZombie", true, 0.6f, 1.95f, 64, 3, true, "zombie", "textures/entity/bzombie.png", 16761087, 32768),
        // ANGER starts at zero; neutral Giant Brainy inherits the ordinary adult zombie dimensions.
        new VisualEntitySpec("giant_brainy_zombie", "GiantBrainyZombie", true, 0.6f, 1.95f, 64, 3, true, "zombie", "textures/entity/bzombie.png", 16761087, 16384),
        new VisualEntitySpec("wisp", "Wisp", true, 0.9f, 0.9f, 64, 3, false, "wisp", "textures/misc/particles.png", 16761087, 16777215),
        new VisualEntitySpec("fire_bat", "Firebat", true, 0.5f, 0.9f, 64, 3, false, "FireBat", "textures/entity/firebat.png", 16761087, 15728640),
        new VisualEntitySpec("spell_bat", "Spellbat", true, 0.5f, 0.9f, 64, 3, false, "FireBat", "textures/entity/spellbat.png", 16761087, 15728640),
        new VisualEntitySpec("pech", "Pech", true, 0.6f, 1.8f, 64, 3, true, "Pech", "textures/entity/pech_forage.png", 16761087, 4194368),
        new VisualEntitySpec("mind_spider", "MindSpider", true, 0.7f, 0.5f, 64, 3, true, "spider", "minecraft:textures/entity/spider/spider.png", 4996656, 4473924),
        new VisualEntitySpec("eldritch_guardian", "EldritchGuardian", true, 0.8f, 2.25f, 64, 3, true, "EldritchGuardian", "textures/entity/eldritch_guardian.png", 8421504, 0),
        new VisualEntitySpec("cultist_knight", "CultistKnight", true, 0.6f, 1.8f, 64, 3, true, "humanoid", "textures/entity/cultist.png", 9438728, 128),
        new VisualEntitySpec("cultist_cleric", "CultistCleric", true, 0.6f, 1.8f, 64, 3, true, "humanoid", "textures/entity/cultist.png", 9438728, 8388608),
        new VisualEntitySpec("eldritch_crab", "EldritchCrab", true, 0.8f, 0.6f, 64, 3, true, "EldritchCrab", "textures/entity/crab.png", 8421504, 5570560),
        new VisualEntitySpec("inhabited_zombie", "InhabitedZombie", true, 0.6f, 1.95f, 64, 3, true, "zombie", "textures/entity/czombie.png", 8421504, 5570560),
        new VisualEntitySpec("thaumic_slime", "ThaumSlime", true, 1.0200001f, 1.0200001f, 64, 3, true, "slime", "textures/entity/tslime.png", 10618530, 16744703),
        new VisualEntitySpec("taint_crawler", "TaintCrawler", true, 0.5f, 0.4f, 64, 3, true, "silverfish", "textures/entity/crawler.png", 10618530, 3158064),
        new VisualEntitySpec("taintacle", "Taintacle", true, 0.8f, 3.0f, 64, 3, false, "Taintacle10", "textures/entity/taintacle.png", 10618530, 4469572),
        new VisualEntitySpec("taintacle_tiny", "TaintacleTiny", true, 0.22f, 1.0f, 64, 3, false, "Taintacle6", "textures/entity/taintacle.png", 6842578, 8421504),
        new VisualEntitySpec("taint_swarm", "TaintSwarm", true, 2.0f, 2.0f, 64, 3, false, "swarm", "textures/misc/particles.png", 10618530, 16744576),
        new VisualEntitySpec("taint_seed", "TaintSeed", true, 1.5f, 1.25f, 64, 20, false, "TaintSeed", "textures/entity/taintseed.png", 10618530, 4465237),
        new VisualEntitySpec("taint_seed_prime", "TaintSeedPrime", true, 2.0f, 2.0f, 64, 20, false, "TaintSeed", "textures/entity/taintseed.png", 10618530, 5583718));
    public static VisualEntitySpec byId(String id) {
        return ALL.stream().filter(spec -> spec.id.equals(id)).findFirst().orElseThrow();
    }
}
