package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.focus.FocusCompiler;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.auromancy.projectile.FocusProjectileEntity;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ElementalProgressionGameTests {
    @GameTest(template = "empty")
    public static void bothBranchesRequireCompletedBasicAuromancyAndKeepLateResearchClosed(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        state.setResearchStage("BASEAUROMANCY", 2);
        for (String key : new String[]{"FOCUSELEMENTAL", "FOCUSPROJECTILE"}) {
            helper.assertTrue(ResearchCatalog.get(key).parents().equals(List.of("BASEAUROMANCY")), "Original parent changed: " + key);
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.LOCKED);
        }
        completeBase(state);
        for (String key : new String[]{"FOCUSELEMENTAL", "FOCUSPROJECTILE"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.STARTED);
        helper.assertTrue(ResearchCatalog.entries().stream().filter(entry -> ResearchProgression.isImplemented(entry.key())).count() == 51,
                "Canonical inventory must include completed clockwork mind progression");
        for (String key : new String[]{"FOCUSBOLT", "FOCUSFLUX", "FOCUSHEAL", "FOCUSBREAK"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.LOCKED);
        for (String key : new String[]{"FOCUSCLOUD", "FOCUSADVANCED", "FOCUSGREATER"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.LOCKED);
        for (String key : new String[]{"FORTRESSMASK", "RUNICSHIELDING"})
            result(helper, ResearchNetwork.processAdvance(player, key, 0), ResearchProgression.Result.UNSUPPORTED);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSELEMENTAL", 1), ResearchProgression.Result.NO_BOOK);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothNewBranchesUseActualScansPaymentsAndDispatchedProjectileFactsWithoutStageGrants(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); completeBase(state);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSELEMENTAL", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, AspectCrystalItem.create(Aspect.AIR)); scanHeld(player, new ItemStack(Items.DIRT));
        helper.assertTrue(state.isResearchCompleteStrict("!aer") && state.isResearchCompleteStrict("!terra")
                && !state.isResearchCompleteStrict("!gelum"), "Actual scanner did not discover the selected initial aspects");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "AUROMANCY", 20);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSELEMENTAL", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        scanHeld(player, new ItemStack(Items.ICE));
        setObservation(player, 15);
        var before = state.save(); int xp = player.totalExperience;
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSELEMENTAL", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(before.equals(state.save()) && xp == player.totalExperience, "Failed elemental payment partly mutated state");
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "AUROMANCY", 4);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSELEMENTAL", 1), ResearchProgression.Result.COMPLETE);
        helper.assertTrue(state.researchStage("FOCUSELEMENTAL") == 3 && state.rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY") == 3,
                "Elemental did not pay exactly16 raw and skip only its empty conclusion");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 0), ResearchProgression.Result.STARTED);
        scanHeld(player, new ItemStack(Items.OAK_BOAT));
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "AUROMANCY", 31);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        helper.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 31, "Missing theory partly paid Projectile");
        KnowledgeStore.addKnowledge(player, KnowledgeType.THEORY, "AUROMANCY", 4);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 1), ResearchProgression.Result.ADVANCED);
        helper.assertTrue(state.researchStage("FOCUSPROJECTILE") == 2 && state.rawKnowledge(KnowledgeType.THEORY, "AUROMANCY") == 3,
                "Projectile did not pay32 theory and stop at its observed-projectile stage");
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        hurt(player, new Arrow(helper.getLevel(), 0, 0, 0), 0);
        hurt(player, new SmallFireball(EntityType.SMALL_FIREBALL, helper.getLevel()), 1);
        // Snowball must not impersonate the original llama-spit fact.
        hurt(player, new Snowball(EntityType.SNOWBALL, helper.getLevel()), 1);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 2), ResearchProgression.Result.MISSING_REQUIREMENTS);
        hurt(player, new LlamaSpit(EntityType.LLAMA_SPIT, helper.getLevel()), 1);
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 2), ResearchProgression.Result.COMPLETE);
        var loaded = PlayerKnowledge.load(state.save());
        helper.assertTrue(loaded.researchStage("FOCUSPROJECTILE") == 4 && loaded.isResearchCompleteStrict("FOCUSELEMENTAL")
                && loaded.isResearchCompleteStrict("f_arrow") && loaded.isResearchCompleteStrict("f_fireball")
                && loaded.isResearchCompleteStrict("f_spit") && !loaded.isResearchCompleteStrict("FOCUSBOLT"),
                "Original facts/conclusions did not persist or unlocked unsupported Bolt");
        var completed = state.save(); int completeXp = player.totalExperience;
        result(helper, ResearchNetwork.processAdvance(player, "FOCUSPROJECTILE", 2), ResearchProgression.Result.STALE);
        helper.assertTrue(completed.equals(state.save()) && completeXp == player.totalExperience, "Stale completion paid another stage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hurtAcquisitionUsesStageTwoImmediateSourceAndDoesNotRequirePositiveNumericAmount(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); var arrow = new Arrow(helper.getLevel(), 0, 0, 0);
        hurt(player, arrow, 1); helper.assertTrue(!state.knowsResearch("f_arrow"), "Hurt fact appeared before Projectile lesson");
        state.setResearchStage("FOCUSPROJECTILE", 1); hurt(player, arrow, 1);
        helper.assertTrue(!state.knowsResearch("f_arrow"), "Hurt fact appeared before stage2");
        state.setResearchStage("FOCUSPROJECTILE", 2);
        var pig = EntityType.PIG.create(helper.getLevel());
        var wrong = new DamageSource(player.damageSources().generic().typeHolder(), pig, arrow);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, wrong, 1));
        helper.assertTrue(!state.knowsResearch("f_arrow"), "True source impersonated the immediate projectile");
        hurt(player, arrow, 0);
        helper.assertTrue(state.knowsResearch("f_arrow") && !state.isResearchCompleteStrict("FOCUSPROJECTILE"),
                "Release event was given an invented positive-amount predicate or completed research");
        var saved = state.save(); hurt(player, arrow, 1);
        helper.assertTrue(saved.equals(state.save()) && PlayerKnowledge.load(saved).knowsResearch("f_arrow"), "Hurt fact repeated or failed save/reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalFireballAncestorsAndArrowSubclassesRemainExactInModernHierarchy(GameTestHelper helper) {
        var entities = List.of(new SmallFireball(EntityType.SMALL_FIREBALL, helper.getLevel()),
                new LargeFireball(EntityType.FIREBALL, helper.getLevel()),
                new WitherSkull(EntityType.WITHER_SKULL, helper.getLevel()),
                new DragonFireball(EntityType.DRAGON_FIREBALL, helper.getLevel()));
        for (var entity : entities) {
            helper.assertTrue("f_fireball".equals(AuromancyProgressionEvents.projectileFact(entity)), "Original EntityFireball subclass omitted: " + entity.getType());
            var player = player(helper); KnowledgeStore.get(player).setResearchStage("FOCUSPROJECTILE", 2); hurt(player, entity, 1);
            helper.assertTrue(KnowledgeStore.get(player).knowsResearch("f_fireball"), "Native immediate source failed original fireball fact");
        }
        helper.assertTrue("f_arrow".equals(AuromancyProgressionEvents.projectileFact(new SpectralArrow(EntityType.SPECTRAL_ARROW, helper.getLevel())))
                && AuromancyProgressionEvents.projectileFact(new ThrownTrident(EntityType.TRIDENT, helper.getLevel())) == null
                && AuromancyProgressionEvents.projectileFact(new Snowball(EntityType.SNOWBALL, helper.getLevel())) == null
                && AuromancyProgressionEvents.projectileFact(new ThrownEgg(EntityType.EGG, helper.getLevel())) == null,
                "Modern unrelated projectiles broadened the original fact family");
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "focus_projectile"));
        var focusProjectile = type.create(helper.getLevel());
        helper.assertTrue(focusProjectile instanceof FocusProjectileEntity
                && AuromancyProgressionEvents.projectileFact(focusProjectile) == null
                && AuromancyProgressionEvents.scanFact(focusProjectile) == null,
                "Catalogue placeholder was accepted as the real spell, or own focus projectile manufactured native facts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanningOrdinaryArrowIsUngatedAndSeparateFromPreviouslyCreditedAspectScan(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        var arrow = new ItemStack(Items.ARROW, 64); arrow.setHoverName(Component.literal("Decorated arrow"));
        arrow.getOrCreateTag().putString("OtherMod", "extra root tag");
        var aspects = AspectRegistry.getAspects(arrow);
        // A migrated/older scan can have already credited aspects without the newly supported special fact.
        StringBuilder oldKey = new StringBuilder("item:minecraft:arrow");
        for (Aspect aspect : aspects.getAspectsSortedByName())
            oldKey.append('|').append(aspect.getTag()).append('=').append(aspects.getAmount(aspect));
        KnowledgeStore.recordScan(player, oldKey.toString(), aspects);
        helper.assertTrue(!state.knowsResearch("f_arrow") && AuromancyProgressionEvents.hasUnseenScanFact(player, arrow),
                "Read-only eligibility manufactured proof");
        var credited = state.save(); int owned = arrow.getCount(); scanHeld(player, arrow);
        helper.assertTrue(state.knowsResearch("f_arrow") && arrow.getCount() == owned && state.researchStage("FOCUSPROJECTILE") == 0,
                "Actual early arrow scan consumed item, required stage2 or granted a research stage");
        helper.assertTrue(state.scanCount() == 1 && credited.getCompound("Knowledge").equals(state.save().getCompound("Knowledge")),
                "Fact acquired after an older identical aspect scan credited duplicate knowledge");
        var saved = state.save(); scanHeld(player, arrow);
        helper.assertTrue(saved.equals(state.save()), "Repeated special scan farmed knowledge or facts");
        helper.assertTrue(AuromancyProgressionEvents.scanFact(new ItemStack(Items.SPECTRAL_ARROW)) == null
                && AuromancyProgressionEvents.scanFact(new ItemStack(Items.TIPPED_ARROW)) == null
                && AuromancyProgressionEvents.scanFact(new ItemStack(Items.SNOWBALL)) == null
                && "f_arrow".equals(AuromancyProgressionEvents.scanFact(new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW)))),
                "ScanItem original ordinary arrow requirement was broadened");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroAspectNativeProjectileScansAreRealRayCommitsAndHoverNeverAwardsFacts(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player);
        var start = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(start.getX() + .5, start.getY(), start.getZ() + .5); player.setYRot(0); player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        for (Entity entity : new Entity[]{new Arrow(helper.getLevel(), 0, 0, 0), new LlamaSpit(EntityType.LLAMA_SPIT, helper.getLevel()),
                new SmallFireball(EntityType.SMALL_FIREBALL, helper.getLevel())}) {
            entity.setPos(player.getX(), player.getEyeY() - .125, player.getZ() + 2); entity.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Native scan fixture failed insertion");
            helper.assertTrue(AspectRegistry.getAspects(entity).size() == 0, "Fact-only native fixture unexpectedly has aspects");
            var before = state.save(); var snapshot = ScanningNetwork.capture(player);
            helper.assertTrue(snapshot.target() != null && snapshot.target().location().entityId() == entity.getId()
                    && snapshot.target().aspects().isEmpty() && before.equals(state.save()),
                    "Actual fact-only hover target was omitted or awarded proof");
            player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
            ScanningModule.THAUMOMETER.get().use(player.level(), player, InteractionHand.MAIN_HAND);
            String fact = AuromancyProgressionEvents.projectileFact(entity);
            helper.assertTrue(state.knowsResearch(fact) && state.scanCount() == 0 && state.researchStage("FOCUSPROJECTILE") == 0,
                    "Zero-aspect ray scan needed a fictitious aspect reward or stage grant");
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compilerSeparatesBareElementalCompletionProjectileStageAndOptionCompletion(GameTestHelper helper) {
        var player = player(helper); var state = KnowledgeStore.get(player); completeBase(state);
        state.setResearchStage("FOCUSELEMENTAL", 1);
        for (String effect : new String[]{FocusNodeRegistry.AIR, FocusNodeRegistry.FROST, FocusNodeRegistry.EARTH})
            helper.assertTrue(!compile(state, graph(FocusNodeRegistry.TOUCH, effect, 0)).success(), "Bare elemental gate accepted a merely started lesson");
        state.setResearchStage("FOCUSELEMENTAL", 3);
        for (String effect : new String[]{FocusNodeRegistry.AIR, FocusNodeRegistry.FROST, FocusNodeRegistry.EARTH})
            helper.assertTrue(compile(state, graph(FocusNodeRegistry.TOUCH, effect, 0)).success(), "Completed elemental lesson failed original default effect");
        state.setResearchStage("FOCUSPROJECTILE", 1);
        helper.assertTrue(!compile(state, graph(FocusNodeRegistry.PROJECTILE, FocusNodeRegistry.FIRE, 0)).success(), "Projectile started gate bypassed @2");
        state.setResearchStage("FOCUSPROJECTILE", 2);
        helper.assertTrue(compile(state, graph(FocusNodeRegistry.PROJECTILE, FocusNodeRegistry.FIRE, 0)).success(), "Stage2 failed original default projectile");
        var forged = compile(state, graph(FocusNodeRegistry.PROJECTILE, FocusNodeRegistry.FIRE, 1));
        helper.assertTrue(!forged.success() && forged.error().equals("missing_setting_research"), "Bouncy option bypassed completed research");
        state.setResearchStage("FOCUSPROJECTILE", 4);
        helper.assertTrue(compile(state, graph(FocusNodeRegistry.PROJECTILE, FocusNodeRegistry.FIRE, 1)).success(), "Completed projectile did not unlock its option");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "elemental_progress")) {
            @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
            @Override public void displayClientMessage(Component message, boolean actionBar) { /* Offline QA has no network connection. */ }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get())); return player;
    }
    private static void scanHeld(ServerPlayer player, ItemStack input) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, input); player.setShiftKeyDown(true);
        player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        ScanningModule.THAUMOMETER.get().use(player.level(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void hurt(ServerPlayer player, Entity projectile, float amount) {
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, player.damageSources().thrown(projectile, null), amount));
    }
    private static void completeBase(PlayerKnowledge state) { state.setResearchStage("BASEAUROMANCY", ResearchCatalog.get("BASEAUROMANCY").stages().size() + 1); }
    private static void setObservation(ServerPlayer player, int raw) {
        KnowledgeStore.addKnowledge(player, KnowledgeType.OBSERVATION, "AUROMANCY", raw - KnowledgeStore.get(player).rawKnowledge(KnowledgeType.OBSERVATION, "AUROMANCY"));
    }
    private static FocusGraph graph(String medium, String effect, int option) {
        return new FocusGraph(List.of(new FocusGraph.Node(0, -1, List.of(1), 0, 0, FocusNodeRegistry.ROOT, Map.of()),
                new FocusGraph.Node(1, 0, List.of(2), 0, 1, medium,
                        medium.equals(FocusNodeRegistry.PROJECTILE) ? Map.of("speed", 1, "option", option) : Map.of()),
                new FocusGraph.Node(2, 1, List.of(), 0, 2, effect, Map.of())));
    }
    private static FocusCompiler.Result compile(PlayerKnowledge state, FocusGraph graph) {
        return FocusCompiler.compile(graph, new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "focus_1"))), state::isResearchCompleteStrict);
    }
    private static void result(GameTestHelper helper, ResearchProgression.Result actual, ResearchProgression.Result expected) {
        helper.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }
}
