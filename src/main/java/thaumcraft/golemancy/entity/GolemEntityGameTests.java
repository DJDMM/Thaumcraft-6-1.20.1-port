package thaumcraft.golemancy.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.golemancy.press.GolemDesign;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import java.util.*;
import java.util.function.Consumer;

/** Physical deployment/ownership and original properties; research and source stacks are fixtures. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemEntityGameTests {
    private static final String TEMPLATE = "essentia_network";
    private static final BlockPos FLOOR = new BlockPos(4, 1, 4);
    private GolemEntityGameTests() {}
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "golem_qa"));
        player.setGameMode(GameType.SURVIVAL); BlockPos pos = h.absolutePos(FLOOR);
        player.setPos(pos.getX() + 2.5, pos.getY() + 1, pos.getZ() + .5); return player;
    }
    private static ThaumcraftGolemEntity candidate(GameTestHelper h, long props) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "golem"));
        ThaumcraftGolemEntity golem = (ThaumcraftGolemEntity)type.create(h.getLevel());
        golem.setProps(props); golem.setValidSpawn(); golem.setPos(h.absoluteVec(new Vec3(4.5, 2, 4.5))); golem.setHome(golem.blockPosition()); return golem;
    }
    private static UseOnContext context(FakePlayer player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false));
    }
    private static void setResearch(ServerPlayer player, String key, int value) {
        try { var method = PlayerKnowledge.class.getDeclaredMethod("setResearchStage", String.class, int.class); method.setAccessible(true); method.invoke(KnowledgeStore.get(player), key, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    @GameTest(template = TEMPLATE)
    public static void originalPlacerCreatesOwnedHomeGolemAndPaysOnce(GameTestHelper h) {
        h.setBlock(FLOOR, Blocks.STONE); FakePlayer player = player(h);
        ItemStack stack = CatalogModule.stack("golem"); stack.setCount(2); stack.getOrCreateTag().putLong("props", 1L << 32); stack.getOrCreateTag().putInt("xp", 731);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.assertTrue(stack.getItem() instanceof GolemPlacerItem, "Existing placer registry is still visual-only");
        h.assertTrue(stack.onItemUseFirst(context(player, h.absolutePos(FLOOR))).consumesAction(), "Physical placer failed");
        List<ThaumcraftGolemEntity> golems = h.getLevel().getEntitiesOfClass(ThaumcraftGolemEntity.class, new AABB(h.absolutePos(FLOOR)).inflate(1));
        h.assertTrue(golems.size() == 1 && stack.getCount() == 1, "Placement did not create/debit exactly one golem");
        ThaumcraftGolemEntity golem = golems.get(0);
        h.assertTrue(golem.isOwner(player) && golem.props() == 1L << 32 && golem.rankXp() == 731 && golem.homePosition().equals(h.absolutePos(FLOOR.above())) && !golem.isNoAi(), "Placement lost original owner/home/props/xp or left NoAI");
        h.assertTrue(golem.getMaxHealth() == 16 && golem.getHealth() == 10 && golem.getArmorValue() == 2, "Original wood initial10/max16/armor2 changed");
        golem.discard(); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void invalidCollisionAndForgeCanceledDeploymentKeepStack(GameTestHelper h) {
        h.setBlock(FLOOR, Blocks.STONE); FakePlayer player = player(h); ItemStack stack = CatalogModule.stack("golem"); stack.setCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getOrCreateTag().putLong("props", 6L << 56);
        h.assertTrue(!stack.onItemUseFirst(context(player, h.absolutePos(FLOOR))).consumesAction() && stack.getCount() == 2, "Malformed props deployed");
        stack.getOrCreateTag().putLong("props", 0); h.setBlock(FLOOR.above(), Blocks.STONE);
        h.assertTrue(!stack.onItemUseFirst(context(player, h.absolutePos(FLOOR))).consumesAction() && stack.getCount() == 2, "Blocked spawn paid");
        h.setBlock(FLOOR.above(), Blocks.AIR);
        Consumer<EntityJoinLevelEvent> cancel = event -> { if (event.getEntity() instanceof ThaumcraftGolemEntity golem && golem.isOwner(player)) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, cancel);
        try { h.assertTrue(!stack.onItemUseFirst(context(player, h.absolutePos(FLOOR))).consumesAction() && stack.getCount() == 2, "Forge-canceled spawn debited placer"); }
        finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void ownerPickupDropsOnePropsItemAndCarriedWithoutReplay(GameTestHelper h) {
        FakePlayer owner = player(h), stranger = player(h); long props = 3L << 24;
        ThaumcraftGolemEntity golem = candidate(h, props); golem.setOwnerId(owner.getUUID()); golem.setRankXp(73); h.getLevel().addFreshEntity(golem);
        golem.holdItem(new ItemStack(Items.APPLE, 7)); golem.holdItem(new ItemStack(Items.DIAMOND, 3));
        h.assertTrue(!golem.collect(stranger, InteractionHand.MAIN_HAND) && !golem.isRemoved(), "Stranger collected owned golem");
        owner.setShiftKeyDown(true); h.assertTrue(golem.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction() && golem.isRemoved(), "Original sneak-owner interaction did not collect");
        h.assertTrue(!golem.collect(owner, InteractionHand.MAIN_HAND), "Removed pickup replayed");
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(1));
        h.assertTrue(drops.stream().filter(drop -> drop.getItem().is(CatalogModule.stack("golem").getItem())).count() == 1, "Pickup produced duplicate/missing placer");
        ItemStack placer = drops.stream().map(ItemEntity::getItem).filter(item -> item.is(CatalogModule.stack("golem").getItem())).findFirst().orElseThrow();
        h.assertTrue(placer.getTag().getLong("props") == props && placer.getTag().getInt("xp") == 73 && drops.stream().filter(drop -> drop.getItem().is(Items.APPLE)).mapToInt(drop -> drop.getItem().getCount()).sum() == 7 && drops.stream().filter(drop -> drop.getItem().is(Items.DIAMOND)).mapToInt(drop -> drop.getItem().getCount()).sum() == 3, "Pickup lost original props/xp or carried payment");
        drops.forEach(Entity::discard); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void carryingMergesExactTagsHaulerCompactsAndReturnsDetachedStacks(GameTestHelper h) {
        ThaumcraftGolemEntity golem = candidate(h, 3L << 24);
        ItemStack first = new ItemStack(Items.APPLE, 60); first.getOrCreateTag().putInt("A", 1);
        h.assertTrue(golem.holdItem(first).isEmpty() && first.getCount() == 60, "Hold mutated caller template");
        ItemStack more = first.copyWithCount(9); h.assertTrue(golem.holdItem(more).isEmpty() && golem.getCarrying().get(0).getCount() == 64 && golem.getCarrying().get(1).getCount() == 5, "Original hauler merge failed");
        ItemStack detached = golem.getCarrying().get(0); detached.setCount(1); h.assertTrue(golem.getMainHandItem().getCount() == 64, "Carrying exposed live entity stack");
        ItemStack extracted = golem.dropItem(first.copyWithCount(64)); h.assertTrue(extracted.getCount() == 64 && golem.getMainHandItem().getCount() == 5 && golem.getOffhandItem().isEmpty(), "HAULER did not compact second hand");
        ItemStack different = first.copyWithCount(4); different.getOrCreateTag().putInt("A", 2);
        h.assertTrue(golem.holdItem(different).isEmpty() && golem.getCarrying().get(0).getCount() == 5 && golem.getCarrying().get(1).getTag().getInt("A") == 2, "Different tags incorrectly merged");
        h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void persistenceRetainsOwnedHomeFlagsHandsAndRejectsVisualPromotion(GameTestHelper h) {
        ThaumcraftGolemEntity golem = candidate(h, 3L << 24); UUID owner = UUID.randomUUID(); golem.setOwnerId(owner); golem.setRankXp(81); golem.setGolemColor((byte)16);
        golem.holdItem(new ItemStack(Items.APPLE, 8)); golem.holdItem(new ItemStack(Items.DIAMOND, 2));
        CompoundTag saved = new CompoundTag(); golem.addAdditionalSaveData(saved);
        ThaumcraftGolemEntity restored = candidate(h, 0); restored.readAdditionalSaveData(saved);
        h.assertTrue(restored.isValidSpawn() && owner.equals(restored.getOwnerId()) && restored.props() == 3L << 24 && restored.rankXp() == 81 && restored.homePosition().equals(golem.homePosition()) && restored.getGolemColor() == 16 && restored.getMainHandItem().getCount() == 8 && restored.getOffhandItem().getCount() == 2 && !restored.isNoAi(), "Restored construct changed paid/save state");
        golem.setFollowingOwner(true); CompoundTag following = new CompoundTag(); golem.addAdditionalSaveData(following); restored.readAdditionalSaveData(following);
        h.assertTrue(restored.isFollowingOwner() && restored.hasHome() && restored.homePosition().equals(golem.homePosition()), "Pinned follow attribute rebuild lost stored home");
        saved.putBoolean("VisualOnly", true); restored.readAdditionalSaveData(saved); h.assertTrue(!restored.isValidSpawn(), "Visual catalogue save became a working owned worker"); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void originalSmartRankAwardAdvancesOnceAndStopsAtTen(GameTestHelper h) {
        ThaumcraftGolemEntity golem = candidate(h, 1L << 48); int initialHealth = (int)golem.getMaxHealth();
        golem.addRankXp(5_001); h.assertTrue(golem.design().rank() == 1 && golem.rankXp() == 4_001 && golem.getMaxHealth() == initialHealth, "Original one-rank award/rebuild timing changed");
        golem.addRankXp(1); h.assertTrue(golem.design().rank() == 2 && golem.rankXp() == 2, "Original quadratic threshold/carryover changed");
        golem.setProps((1L << 48) | (10L << 16)); int xp = golem.rankXp(); golem.addRankXp(Integer.MAX_VALUE);
        h.assertTrue(golem.design().rank() == 10 && golem.rankXp() == xp, "Rank cap exceeded");
        golem.setProps(0); golem.addRankXp(1_000); h.assertTrue(golem.rankXp() == xp && golem.design().rank() == 0, "Non-SMART received experience"); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void bellRequiresStartedDirectAndDyesKeepOriginalMapping(GameTestHelper h) {
        FakePlayer player = player(h); ThaumcraftGolemEntity golem = candidate(h, 0); golem.setOwnerId(player.getUUID()); h.getLevel().addFreshEntity(golem);
        player.setItemInHand(InteractionHand.MAIN_HAND, CatalogModule.stack("golem_bell"));
        golem.mobInteract(player, InteractionHand.MAIN_HAND); h.assertTrue(!golem.isFollowingOwner(), "Bell bypassed GOLEMDIRECT");
        setResearch(player, "GOLEMDIRECT", 1); golem.mobInteract(player, InteractionHand.MAIN_HAND); h.assertTrue(golem.isFollowingOwner() && golem.hasHome(), "Original bare started GOLEMDIRECT did not follow/reapply stored home");
        golem.mobInteract(player, InteractionHand.MAIN_HAND); h.assertTrue(!golem.isFollowingOwner() && golem.hasHome(), "Second bell did not anchor current position");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLACK_DYE, 2)); golem.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(golem.getGolemColor() == 1 && player.getMainHandItem().getCount() == 1, "Original black metadata0/color1 changed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_DYE, 2)); golem.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(golem.getGolemColor() == 16 && player.getMainHandItem().getCount() == 1, "Original white metadata15/color16 changed"); golem.discard(); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void traitsApplyDamageImmunitiesNavigationAndOriginalHealingPeriods(GameTestHelper h) {
        ThaumcraftGolemEntity iron = candidate(h, 1L << 56);
        h.assertTrue(iron.getMaxHealth() == 30 && iron.getArmorValue() == 8 && iron.getGolemMoveSpeed() == .825F && iron.getNavigation() instanceof GroundPathNavigation, "IRON stats changed");
        h.assertTrue(!iron.hurt(h.getLevel().damageSources().inFire(), 5) && !iron.hurt(h.getLevel().damageSources().cactus(), 5), "Original fireproof/cactus immunity missing");
        iron.setHealth(15); iron.invulnerableTime = 0; iron.hurt(h.getLevel().damageSources().explosion(null, null), 30);
        h.assertTrue(iron.getHealth() > 0 && iron.getHealth() < 15, "BLASTPROOF did not bound actual explosion damage");
        ThaumcraftGolemEntity climber = candidate(h, 2L << 32); h.assertTrue(climber.getNavigation() instanceof WallClimberNavigation && !climber.causeFallDamage(50, 1, h.getLevel().damageSources().fall()), "CLIMBER navigation/fall changed");
        ThaumcraftGolemEntity flyer = candidate(h, 3L << 32); h.assertTrue(flyer.getNavigation() instanceof FlyingPathNavigation && flyer.isNoGravity() && !flyer.causeFallDamage(50, 1, h.getLevel().damageSources().fall()), "FLYER navigation/fall changed");
        // ServerLevel advances tickCount before entity.tick; this direct fixture must do so explicitly.
        ThaumcraftGolemEntity repaired = candidate(h, 5L << 56); repaired.setHealth(7); repaired.tickCount = 39; repaired.tick(); h.assertTrue(repaired.getHealth() == 7, "REPAIR healed before its fortieth tick");
        repaired.tickCount = 40; repaired.tick(); h.assertTrue(repaired.getHealth() == 8, "REPAIR did not heal every40ticks");
        ThaumcraftGolemEntity ordinary = candidate(h, 0); ordinary.setHealth(7); ordinary.tickCount = 99; ordinary.tick(); h.assertTrue(ordinary.getHealth() == 7, "Ordinary golem healed before its hundredth tick");
        ordinary.tickCount = 100; ordinary.tick(); h.assertTrue(ordinary.getHealth() == 8, "Ordinary golem did not heal every100ticks"); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void fighterCombatAndDartUseRealDamageProjectileAndOwnerAlliance(GameTestHelper h) {
        ThaumcraftGolemEntity fighter = candidate(h, (1L << 56) | (1L << 48) | (2L << 40)); UUID owner = UUID.randomUUID(); fighter.setOwnerId(owner);
        var target = EntityType.ZOMBIE.create(h.getLevel()); target.setPos(fighter.getX() + 1, fighter.getY(), fighter.getZ()); target.setHealth(1);
        h.assertTrue(fighter.doHurtTarget(target) && !target.isAlive() && fighter.rankXp() == 8, "Actual FIGHTER kill/SMART8XP failed");
        ThaumcraftGolemEntity friend = candidate(h, 0); friend.setOwnerId(owner); fighter.setTarget(friend); h.assertTrue(fighter.getTarget() == null && !fighter.doHurtTarget(friend), "Combat targeted same owner construct");
        ThaumcraftGolemEntity ranged = candidate(h, (1L << 56) | (4L << 40)); var distant = EntityType.ZOMBIE.create(h.getLevel()); distant.setPos(ranged.getX() + 3, ranged.getY(), ranged.getZ());
        ranged.performRangedAttack(distant, .5F);
        var darts = h.getLevel().getEntitiesOfClass(GolemDartEntity.class, ranged.getBoundingBox().inflate(2));
        h.assertTrue(darts.size() == 1 && darts.get(0).getOwner() == ranged && darts.get(0).getDeltaMovement().length() > 1.5 && darts.get(0).getBaseDamage() > 0, "RANGED did not spawn real original dart"); darts.forEach(Entity::discard);
        h.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(1), drop -> drop.getItem().is(Items.ROTTEN_FLESH)).forEach(Entity::discard); h.succeed();
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void stationaryWorkerActuallyPathsHomeWithActiveNativeAI(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) { h.setBlock(new BlockPos(x, 1, z), Blocks.STONE); h.setBlock(new BlockPos(x, 2, z), Blocks.AIR); h.setBlock(new BlockPos(x, 3, z), Blocks.AIR); }
        // Explicit QA arena loading; runtime navigation itself never requests an absent chunk.
        BlockPos arena = h.absolutePos(new BlockPos(4, 2, 4));
        for (int x = (arena.getX() - 64) >> 4; x <= (arena.getX() + 64) >> 4; x++)
            for (int z = (arena.getZ() - 64) >> 4; z <= (arena.getZ() + 64) >> 4; z++) h.getLevel().getChunk(x, z);
        ThaumcraftGolemEntity golem = candidate(h, 0); golem.setPos(h.absoluteVec(new Vec3(1.5, 2, 1.5))); golem.setHome(golem.blockPosition()); golem.setOnGround(true);
        // This navigation fixture isolates the native home goal; real seal goals are checked by their task suite.
        golem.goalSelector.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                .filter(goal -> goal instanceof thaumcraft.golemancy.seals.core.SealTaskGoal).toList().forEach(golem.goalSelector::removeGoal);
        BlockPos destination=h.absolutePos(new BlockPos(7,2,7));double initial=golem.distanceToSqr(Vec3.atCenterOf(destination));h.getLevel().addFreshEntity(golem);
        // Complete original first-spawn recovery at the actual deployment home before assigning a
        // different home. Otherwise this fixture tests recovery teleportation, not the home goal.
        h.runAfterDelay(2,()->golem.setHome(destination));
        boolean[] activePath={false};int[] moving={0};Vec3[] previous={golem.position()};
        h.onEachTick(()->{if(golem.isRemoved())return;activePath[0]|=!golem.getNavigation().isDone();if(golem.position().distanceToSqr(previous[0])>1e-6)moving[0]++;previous[0]=golem.position();});
        h.runAfterDelay(140, () -> {try{h.assertTrue(golem.isAlive() && !golem.isNoAi() && activePath[0] && moving[0]>=10 && golem.distanceToSqr(Vec3.atCenterOf(golem.homePosition())) < initial / 2,
                "Actual native home navigation never moved the worker: pos="+golem.position()+",home="+golem.homePosition()+",path="+activePath[0]+",moving="+moving[0]);h.succeed();}finally{golem.discard();}});
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void bellDirectedWorkerActuallyFollowsIndexedOwnerAroundWall(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) {
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            h.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            h.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
        }
        // The direct diagonal is blocked; a real ground path must go around a wall end.
        for (int z = 0; z <= 6; z++) {
            h.setBlock(new BlockPos(4, 2, z), Blocks.STONE);
            h.setBlock(new BlockPos(4, 3, z), Blocks.STONE);
        }
        BlockPos arena = h.absolutePos(new BlockPos(4, 2, 4));
        // Explicit fixture loading supplies the complete native navigation region; runtime does not force chunks.
        for (int x = (arena.getX() - 64) >> 4; x <= (arena.getX() + 64) >> 4; x++)
            for (int z = (arena.getZ() - 64) >> 4; z <= (arena.getZ() + 64) >> 4; z++) h.getLevel().getChunk(x, z);
        FakePlayer owner = player(h);
        owner.setPos(h.absoluteVec(new Vec3(2.5, 2, .5)));
        ThaumcraftGolemEntity golem = candidate(h, 0);
        golem.setPos(h.absoluteVec(new Vec3(.5, 2, .5))); golem.setHome(golem.blockPosition()); golem.setOnGround(true);
        golem.setOwnerId(owner.getUUID());
        Runnable cleanup = () -> { golem.discard(); h.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED); };
        try {
            // Unlike the interaction-only fixture, this owner is in the world's real UUID/player index.
            h.getLevel().addNewPlayer(owner);
            h.assertTrue(h.getLevel().addFreshEntity(golem) && golem.getOwnerEntity() == owner, "Follow fixture did not resolve its actual tracked owner");
            owner.setItemInHand(InteractionHand.MAIN_HAND, CatalogModule.stack("golem_bell"));
            setResearch(owner, "GOLEMDIRECT", 1);
            h.assertTrue(golem.mobInteract(owner, InteractionHand.MAIN_HAND).consumesAction() && golem.isFollowingOwner() && !golem.isNoAi(), "Actual owner bell did not enable native follow AI");
            owner.setPos(h.absoluteVec(new Vec3(8.5, 2, 8.5)));
            double initial = golem.distanceToSqr(owner);
            // 10 <= distance < 12 triggers follow and makes the original teleport fallback ineligible.
            h.assertTrue(initial >= 100 && initial < 144, "Follow fixture fell outside the physical-navigation-only distance band");
            Vec3[] previous = {golem.position()};
            int[] movementTicks = {0}; boolean[] activePath = {false}, crossedOpening = {false};
            h.onEachTick(() -> {
                if (golem.isRemoved()) return;
                try {
                    Vec3 before = h.relativeVec(previous[0]), current = h.relativeVec(golem.position());
                    double step = golem.position().distanceToSqr(previous[0]);
                    h.assertTrue(step < 1, "Follow test moved by teleport instead of actual native navigation");
                    if (step > 1e-6) movementTicks[0]++;
                    activePath[0] |= golem.getNavigation().getPath() != null && !golem.getNavigation().isDone();
                    crossedOpening[0] |= before.x < 4.5 && current.x >= 4.5 && (current.z > 6.5 || current.z < 0);
                    previous[0] = golem.position();
                } catch (Throwable failure) { cleanup.run(); throw failure; }
            });
            // ServerLevel ticks the entity, goal selector, native path and move control; the test never calls tick/moveTo.
            h.runAfterDelay(160, () -> {
                try {
                    h.assertTrue(golem.isAlive() && golem.getOwnerEntity() == owner && golem.isFollowingOwner() && !golem.hasTask(), "Follow lost owner/mode or executed a stationary seal task");
                    h.assertTrue(activePath[0] && movementTicks[0] >= 15 && crossedOpening[0] && golem.distanceToSqr(owner) < 9,
                            "Native follow did not walk around the wall to its indexed owner: path=" + activePath[0] + ", movingTicks=" + movementTicks[0] + ", opening=" + crossedOpening[0] + ", distanceSq=" + golem.distanceToSqr(owner));
                    h.succeed();
                } finally { cleanup.run(); }
            });
        } catch (Throwable failure) { cleanup.run(); throw failure; }
    }
    @GameTest(template = TEMPLATE)
    public static void canceledPickupRollsBackCarriedAndBlocksReentrantCollection(GameTestHelper h) {
        FakePlayer owner = player(h); ThaumcraftGolemEntity golem = candidate(h, 0); golem.setOwnerId(owner.getUUID()); golem.holdItem(new ItemStack(Items.APPLE, 7)); h.getLevel().addFreshEntity(golem);
        boolean[] replayRejected = {false};
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getLevel() == h.getLevel() && event.getEntity() instanceof ItemEntity item && item.getItem().is(CatalogModule.stack("golem").getItem()) && item.blockPosition().equals(golem.blockPosition())) {
                replayRejected[0] = !golem.collect(owner, InteractionHand.MAIN_HAND); event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, cancel);
        try { h.assertTrue(!golem.collect(owner, InteractionHand.MAIN_HAND) && replayRejected[0] && golem.isAlive() && golem.getMainHandItem().getCount() == 7, "Canceled/reentrant pickup duplicated or lost paid entity/carry"); }
        finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(1), item -> item.getItem().is(Items.APPLE) || item.getItem().is(CatalogModule.stack("golem").getItem())).isEmpty(), "Canceled pickup retained a spawned partial output"); golem.discard(); h.succeed();
    }
    @GameTest(template = TEMPLATE)
    public static void originalOrbSteersBySquaredDistanceBouncesAndDealsTypedDamage(GameTestHelper h) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "golem_orb"));
        h.assertTrue(type.create(h.getLevel()) instanceof GolemOrbEntity, "Orb ID is still a visual effect");
        GolemOrbEntity orb = (GolemOrbEntity)type.create(h.getLevel());
        ThaumcraftGolemEntity owner = candidate(h, (1L << 56) | (2L << 40)); orb.setOwner(owner); orb.setPos(owner.getX(), owner.getY() + 1, owner.getZ());
        var target = EntityType.ZOMBIE.create(h.getLevel()); target.setPos(orb.getX() + 4, orb.getY(), orb.getZ()); target.setNoAi(true); h.getLevel().addFreshEntity(target); orb.setTarget(target); orb.tick();
        h.assertTrue(Math.abs(orb.getDeltaMovement().x - .05) < 1e-6, "Orb normalized distance rather than original squared-distance steering");
        owner.setYRot(90); h.assertTrue(orb.hurt(h.getLevel().damageSources().mobAttack(owner), 1) && Math.abs(orb.getDeltaMovement().length() - .9) < 1e-6, "Orb did not redirect toward attacker's look at.9");
        target.setHealth(20); float before = target.getHealth(); orb.onHit(new EntityHitResult(target));
        h.assertTrue(orb.isRemoved() && target.getHealth() < before, "Orb entity impact did not apply real indirect magic damage");
        GolemOrbEntity saved = (GolemOrbEntity)type.create(h.getLevel()); saved.setRed(true); saved.setTarget(target); CompoundTag tag = new CompoundTag(); saved.addAdditionalSaveData(tag); saved.readAdditionalSaveData(tag);
        h.assertTrue(!saved.red() && saved.target() == null, "Orb reload invented persisted target/red absent in original NBT"); target.discard(); h.succeed();
    }
}
