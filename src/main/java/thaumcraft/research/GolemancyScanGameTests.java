package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.scanning.ScanningNetwork;
import thaumcraft.scanning.ThaumometerItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Actual server Thaumometer use and Forge death drops; scan facts are never supplied as fixtures. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemancyScanGameTests {
    private GolemancyScanGameTests() {}
    private static ItemStack registered(String id) { var item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id)); if(item==null||item==Items.AIR)throw new AssertionError("Missing physical fixture item "+id);return new ItemStack(item); }
    private static final List<String> MATERIAL_STUDIES = List.of("MATSTUDIRON", "MATSTUDCLAY", "MATSTUDBRASS", "MATSTUDTHAUMIUM");

    @GameTest(template = "essentia_network")
    public static void realHeldScansAcquireEveryOriginalOreDictionaryMaterialForm(GameTestHelper h) {
        for (var stack : List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_BLOCK), registered("plate_iron"))) {
            var p = player(h); scanHeld(h, p, stack, "f_MATIRON"); noFreeMaterialStudy(h, p);
        }
        for (String metal : List.of("brass", "thaumium")) {
            for (String form : List.of("ingot_", "metal_", "plate_")) {
                var p = player(h);
                ItemStack stack = form.equals("metal_") ? new ItemStack(CatalogBlocks.block(form + metal)) : registered(form + metal);
                scanHeld(h, p, stack, metal.equals("brass") ? "f_MATBRASS" : "f_MATTHAUMIUM"); noFreeMaterialStudy(h, p);
            }
        }
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void actualBlockScansAcquireClayAllTerracottaMetalOresAndDispenserFacts(GameTestHelper h) {
        var p = player(h);
        scanBlock(h, p, Blocks.CLAY.defaultBlockState(), "f_MATCLAY");
        for (var block : List.of(Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
                Blocks.MAGENTA_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
                Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA,
                Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA,
                Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA)) {
            scanBlock(h, player(h), block.defaultBlockState(), "f_MATCLAY");
        }
        scanBlock(h, p, Blocks.IRON_ORE.defaultBlockState(), "f_MATIRON");
        scanBlock(h, player(h), Blocks.IRON_BLOCK.defaultBlockState(), "f_MATIRON");
        // Flattened Forge ore tags are the modern counterpart of oreIron.
        scanBlock(h, player(h), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), "f_MATIRON");
        scanBlock(h, p, CatalogBlocks.block("metal_brass").defaultBlockState(), "f_MATBRASS");
        scanBlock(h, p, CatalogBlocks.block("metal_thaumium").defaultBlockState(), "f_MATTHAUMIUM");
        scanBlock(h, p, Blocks.DISPENSER.defaultBlockState(), "f_DISPENSER");
        h.assertTrue(!KnowledgeStore.get(p).isResearchKnown("GOLEMCOMBATADV"), "Dispenser scan freely unlocked the paid combat part");
        noFreeMaterialStudy(h, p); h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void heldClayBrainDispenserAndStrictNegativeFamiliesUseRealThaumometer(GameTestHelper h) {
        var p = player(h);
        scanHeld(h, p, new ItemStack(Items.CLAY_BALL), "f_MATCLAY");
        scanHeld(h, p, CatalogModule.stack("brain"), "f_BRAIN");
        scanHeld(h, p, new ItemStack(Items.DISPENSER), "f_DISPENSER");
        for (var specimen : List.of(new ItemStack(Items.IRON_NUGGET), new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_PICKAXE),
                registered("nugget_brass"), registered("nugget_thaumium"), new ItemStack(Items.TERRACOTTA),
                new ItemStack(Items.CLAY), new ItemStack(Items.DROPPER), new ItemStack(Items.ZOMBIE_SPAWN_EGG), new ItemStack(Items.SPIDER_SPAWN_EGG))) {
            var negative = player(h); useHeld(h, negative, specimen);
            for (String fact : List.of("f_MATIRON", "f_MATCLAY", "f_MATBRASS", "f_MATTHAUMIUM", "f_BRAIN", "f_DISPENSER", "f_SPIDER"))
                h.assertTrue(!KnowledgeStore.get(negative).isResearchKnown(fact), "Unrelated held specimen substituted for " + fact + ": " + specimen);
        }
        for (var block : List.of(Blocks.MUD, Blocks.WHITE_GLAZED_TERRACOTTA, Blocks.BRICKS, Blocks.DROPPER)) {
            var negative = player(h); useBlock(h, negative, block.defaultBlockState());
            h.assertTrue(!KnowledgeStore.get(negative).isResearchKnown("f_MATCLAY") && !KnowledgeStore.get(negative).isResearchKnown("f_DISPENSER"),
                    "Modern visual relative substituted for original clay/dispenser scan: " + block);
        }
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void actualEntityScansIncludeSpiderSubclassesAndBothBrainyZombies(GameTestHelper h) {
        for (var type : List.of(EntityType.SPIDER, EntityType.CAVE_SPIDER)) {
            var p = player(h); scanEntity(h, p, type.create(h.getLevel()), "f_SPIDER");
            h.assertTrue(!KnowledgeStore.get(p).isResearchKnown("GOLEMCLIMBER"), "Spider scan freely granted climber research");
        }
        for (String id : List.of("brainy_zombie", "giant_brainy_zombie")) {
            var p = player(h);
            var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
            h.assertTrue(type != null, "Missing original Brainy subclass " + id);
            scanEntity(h, p, type.create(h.getLevel()), "f_BRAIN");
            h.assertTrue(!KnowledgeStore.get(p).isResearchKnown("MINDBIOTHAUMIC"), "Brainy scan skipped the paid mind lesson");
        }
        var negative = player(h); useEntity(h, negative, EntityType.ZOMBIE.create(h.getLevel()));
        h.assertTrue(!KnowledgeStore.get(negative).isResearchKnown("f_BRAIN"), "Ordinary zombie body substituted for a brain/Brainy specimen");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void allFourMaterialStudiesAdvanceFromActualScansWithOriginalStageParents(GameTestHelper h) {
        var p = player(h); var state = KnowledgeStore.get(p);
        // Only predecessor research is fixture data; every material proof below is a real scan.
        complete(state, "MATSTUDWOOD");
        for (String key : List.of("MATSTUDIRON", "MATSTUDCLAY")) {
            result(h, ResearchNetwork.processAdvance(p, key, 0), ResearchProgression.Result.STARTED);
            result(h, ResearchNetwork.processAdvance(p, key, 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
            scanHeld(h, p, new ItemStack(key.equals("MATSTUDIRON") ? Items.IRON_INGOT : Items.CLAY_BALL), key.equals("MATSTUDIRON") ? "f_MATIRON" : "f_MATCLAY");
            result(h, ResearchNetwork.processAdvance(p, key, 1), ResearchProgression.Result.COMPLETE);
        }
        state.setResearchStage("METALLURGY", 1);
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDBRASS", 0), ResearchProgression.Result.LOCKED);
        scanHeld(h, p, registered("ingot_brass"), "f_MATBRASS");
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDBRASS", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 2);
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDBRASS", 0), ResearchProgression.Result.STARTED);
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDBRASS", 1), ResearchProgression.Result.COMPLETE);
        scanHeld(h, p, registered("ingot_thaumium"), "f_MATTHAUMIUM");
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDTHAUMIUM", 0), ResearchProgression.Result.LOCKED);
        state.setResearchStage("METALLURGY", 3);
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDTHAUMIUM", 0), ResearchProgression.Result.STARTED);
        result(h, ResearchNetwork.processAdvance(p, "MATSTUDTHAUMIUM", 1), ResearchProgression.Result.COMPLETE);
        var loaded = PlayerKnowledge.load(state.save());
        for (String key : MATERIAL_STUDIES) h.assertTrue(loaded.isResearchCompleteStrict(key), "Real scan/payment lost after save: " + key);
        h.assertTrue(!loaded.isResearchKnown("MATSTUDVOID"), "Real material scans opened the unsupported late parent"); h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void biothaumicMindConsumesItsTheoryObservationOnlyAfterRealBrainScan(GameTestHelper h) {
        var p = player(h); var state = KnowledgeStore.get(p); complete(state, "MINDCLOCKWORK"); complete(state, "INFUSION");
        result(h, ResearchNetwork.processAdvance(p, "MINDBIOTHAUMIC", 0), ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(p, KnowledgeType.THEORY, "GOLEMANCY", 32);
        KnowledgeStore.addKnowledge(p, KnowledgeType.OBSERVATION, "ARTIFICE", 16);
        result(h, ResearchNetwork.processAdvance(p, "MINDBIOTHAUMIC", 1), ResearchProgression.Result.MISSING_REQUIREMENTS);
        scanHeld(h, p, CatalogModule.stack("brain"), "f_BRAIN");
        int theory = state.rawKnowledge(KnowledgeType.THEORY, "GOLEMANCY"), observation = state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE");
        result(h, ResearchNetwork.processAdvance(p, "MINDBIOTHAUMIC", 1), ResearchProgression.Result.COMPLETE);
        h.assertTrue(state.rawKnowledge(KnowledgeType.THEORY, "GOLEMANCY") == theory - 32
                && state.rawKnowledge(KnowledgeType.OBSERVATION, "ARTIFICE") == observation - 16
                && state.permanentWarp() == 2 && state.normalWarp() == 1, "Actual Brain scan bypassed or changed the original payment/warp");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void realPlayerZombieKillProducesBrainThatCanBeScannedAsItsActualDroppedEntity(GameTestHelper h) {
        var p = player(h); var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.setPos(p.getX(), p.getY(), p.getZ() + 3); zombie.setNoAi(true); zombie.setNoGravity(true); zombie.setHealth(1);
        h.assertTrue(h.getLevel().addFreshEntity(zombie), "Cannot insert natural Brain source");
        boolean[] witnessed = {false};
        Consumer<LivingDropsEvent> seed = event -> {
            if (event.getEntity() != zombie) return;
            witnessed[0] = event.isRecentlyHit();
            // Deterministic RNG fixture for this one death; the real registered subscriber,
            // vanilla death capture/spawn and original 10% predicate still execute.
            h.getLevel().random.setSeed(seedForRoll(0));
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, seed);
        try { h.assertTrue(zombie.hurt(p.damageSources().playerAttack(p), 100), "Real player attack failed"); }
        finally { MinecraftForge.EVENT_BUS.unregister(seed); }
        h.assertTrue(!zombie.isAlive() && witnessed[0], "Natural loot did not execute a real recently-hit death event");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(zombie.blockPosition()).inflate(2),
                item -> item.getItem().is(CatalogModule.stack("brain").getItem()));
        h.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "Ordinary zombie death did not spawn exactly one original Brain");
        ItemEntity brain = drops.get(0); zombie.discard();
        useExistingEntity(h, p, brain);
        h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict("f_BRAIN") && brain.getItem().getCount() == 1,
                "Actual naturally dropped Brain could not be scanned or scanning consumed it");
        brain.discard(); h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void registeredBrainDropHookRetainsExactRecentHitLootingAndBrainySubclassBoundaries(GameTestHelper h) {
        for (var type : List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN)) {
            var entity = type.create(h.getLevel());
            h.assertTrue(brainDrops(h, entity, 0, 0, true) == 1 && brainDrops(h, entity, 1, 0, true) == 0
                    && brainDrops(h, entity, 1, 1, true) == 1 && brainDrops(h, entity, 9, 0, false) == 0,
                    "Original ordinary EntityZombie recent-hit/roll-looting boundary changed: " + type);
        }
        h.assertTrue(brainDrops(h, EntityType.DROWNED.create(h.getLevel()), 0, 0, true) == 0,
                "Post-1.12 Drowned received an invented original Brain source");
        for (String id : List.of("brainy_zombie", "giant_brainy_zombie")) {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
            var entity = (net.minecraft.world.entity.LivingEntity) type.create(h.getLevel());
            h.assertTrue(brainDrops(h, entity, 4, 0, false) == 1 && brainDrops(h, entity, 5, 0, true) == 0
                    && brainDrops(h, entity, 5, 1, false) == 1, "Original Brainy <=4/no recent-hit branch changed: " + id);
        }
        h.succeed();
    }

    private static int brainDrops(GameTestHelper h, net.minecraft.world.entity.LivingEntity entity, int roll, int looting, boolean recentlyHit) {
        h.getLevel().random.setSeed(seedForRoll(roll));
        var event = new LivingDropsEvent(entity, entity.damageSources().generic(), new ArrayList<>(), looting, recentlyHit);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getDrops().stream().filter(item -> item.getItem().is(CatalogModule.stack("brain").getItem())).mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static long seedForRoll(int roll) {
        for (long seed = 0; seed < 1000; seed++) if (RandomSource.create(seed).nextInt(10) == roll) return seed;
        throw new IllegalStateException("No deterministic RNG fixture for roll " + roll);
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "golem_scans"));
        var at = h.absolutePos(new BlockPos(4, 2, 2)); p.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
        p.setYRot(0); p.setXRot(0); book(p); return p;
    }
    private static void book(ServerPlayer p) { p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get())); }
    private static void scanHeld(GameTestHelper h, ServerPlayer p, ItemStack specimen, String fact) {
        useHeld(h, p, specimen); h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict(fact), "Actual held scan omitted " + fact + ": " + specimen);
    }
    private static void useHeld(GameTestHelper h, ServerPlayer p, ItemStack specimen) {
        ItemStack before = specimen.copy(); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, specimen); p.setShiftKeyDown(true);
        var knowledge = KnowledgeStore.get(p).save(); var hover = ScanningNetwork.capture(p);
        h.assertTrue(knowledge.equals(KnowledgeStore.get(p).save()), "Held HUD granted a scan fact");
        if (!GolemancyProgressionEvents.scanFacts(specimen).isEmpty()) h.assertTrue(hover.target() != null
                && hover.target().location().kind() == ThaumometerItem.TargetKind.HELD_ITEM && !hover.target().scanned(), "Held fact specimen was not offered for real scan");
        use(p); h.assertTrue(ItemStack.matches(before, specimen), "Scanning consumed/changed the held specimen");
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); book(p);
    }
    private static void scanBlock(GameTestHelper h, ServerPlayer p, BlockState state, String fact) {
        useBlock(h, p, state); h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict(fact), "Actual block scan omitted " + fact + ": " + state);
    }
    private static void useBlock(GameTestHelper h, ServerPlayer p, BlockState state) {
        BlockPos relative = new BlockPos(4, 3, 5), at = h.absolutePos(relative); h.setBlock(relative, state);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get())); aim(p, Vec3.atCenterOf(at));
        var before = KnowledgeStore.get(p).save(); var hover = ScanningNetwork.capture(p);
        h.assertTrue(before.equals(KnowledgeStore.get(p).save()), "Block HUD granted a scan fact");
        if (!GolemancyProgressionEvents.scanFacts(state).isEmpty()) h.assertTrue(hover.target() != null
                && hover.target().location().kind() == ThaumometerItem.TargetKind.BLOCK && at.equals(hover.target().location().blockPos()), "Real block geometry missed the fact specimen");
        use(p); h.assertTrue(h.getBlockState(relative).equals(state), "Scanning changed the physical block"); h.setBlock(relative, Blocks.AIR); book(p);
    }
    private static void scanEntity(GameTestHelper h, ServerPlayer p, Entity entity, String fact) {
        useEntity(h, p, entity); h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict(fact), "Actual entity scan omitted " + fact + ": " + entity.getType());
    }
    private static void useEntity(GameTestHelper h, ServerPlayer p, Entity entity) {
        entity.setPos(p.getX(), p.getY(), p.getZ() + 3); entity.setNoGravity(true); if (entity instanceof Mob mob) mob.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(entity), "Cannot insert real scan specimen"); useExistingEntity(h, p, entity); entity.discard();
    }
    private static void useExistingEntity(GameTestHelper h, ServerPlayer p, Entity entity) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get())); aim(p, entity.getBoundingBox().getCenter());
        var before = KnowledgeStore.get(p).save(); var hover = ScanningNetwork.capture(p);
        h.assertTrue(hover.target() != null && hover.target().location().entityId() == entity.getId()
                && before.equals(KnowledgeStore.get(p).save()), "Read-only HUD missed or acquired the actual entity scan");
        use(p); book(p);
    }
    private static void aim(ServerPlayer p, Vec3 target) {
        Vec3 offset = target.subtract(p.getEyePosition());
        p.setYRot((float) -Math.toDegrees(Math.atan2(offset.x, offset.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
    }
    private static void use(ServerPlayer p) {
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get()); ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
    }
    private static void noFreeMaterialStudy(GameTestHelper h, ServerPlayer p) {
        for (String key : MATERIAL_STUDIES) h.assertTrue(!KnowledgeStore.get(p).isResearchKnown(key), "Scan skipped the original material study book action: " + key);
    }
    private static void complete(PlayerKnowledge state, String key) { state.setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1); }
    private static void result(GameTestHelper h, ResearchProgression.Result actual, ResearchProgression.Result expected) { h.assertTrue(actual == expected, "Expected " + expected + ", got " + actual); }
}
