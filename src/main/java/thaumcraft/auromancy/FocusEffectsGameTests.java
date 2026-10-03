package thaumcraft.auromancy;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Real world/effect/event/loot assertions; no synthetic replacement of the harvest result. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusEffectsGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "TC6Elements"));
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p);
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        p.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return p;
    }
    private static FocusGraph.Node node(String key, int power, int duration) {
        return new FocusGraph.Node(1, 0, List.of(), 0, 1, key,
                key.equals(FocusNodeRegistry.FROST) ? Map.of("power", power, "duration", duration) : Map.of("power", power));
    }
    private static Cow cow(GameTestHelper h, ServerPlayer caster) {
        Cow cow = EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setPos(caster.position().add(1, 0, 0)); return cow;
    }
    private static boolean entity(GameTestHelper h, ServerPlayer p, Cow target, String key, int power, int duration, Vec3 direction) {
        return FocusEffects.apply(h.getLevel(), p, node(key, power, duration), new EntityHitResult(target, target.position()), direction);
    }
    private static boolean block(GameTestHelper h, ServerPlayer p, BlockPos pos, String key, int power) {
        return FocusEffects.apply(h.getLevel(), p, node(key, power, 2), new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), new Vec3(0, -1, 0));
    }
    private static void aura(ServerLevel level, BlockPos pos, float value) {
        AuraManager.drainVis(level, pos, Float.MAX_VALUE, false); AuraManager.addVis(level, pos, value);
    }
    private static void near(GameTestHelper h, double got, double expected, String why) {
        h.assertTrue(Math.abs(got - expected) < .001, why + ": " + got + " != " + expected);
    }
    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos) { return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.6)); }
    private static int drops(ServerLevel level, BlockPos pos, Item item) {
        return drops(level, pos).stream().filter(drop -> drop.getItem().is(item)).mapToInt(drop -> drop.getItem().getCount()).sum();
    }
    private static void cleanDrops(ServerLevel level, BlockPos pos) {
        drops(level, pos).forEach(ItemEntity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(.6)).forEach(ExperienceOrb::discard);
    }

    @GameTest(template="empty") public static void airDamageUsesOriginalImmediateTargetAndTrueCaster(GameTestHelper h) {
        var p = player(h); AtomicInteger events = new AtomicInteger();
        Cow[] targets = {cow(h, p), cow(h, p)};
        Consumer<LivingHurtEvent> witness = event -> {
            if (event.getEntity() != targets[0] && event.getEntity() != targets[1]) return;
            h.assertTrue(event.getSource().getDirectEntity() == event.getEntity() && event.getSource().getEntity() == p,
                    "Air replaced BETA26 target/caster thrown source"); events.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, witness);
        try {
            for (int i = 0; i < targets.length; i++) {
                int power = i == 0 ? 1 : 5; float health = targets[i].getHealth();
                h.assertTrue(entity(h, p, targets[i], FocusNodeRegistry.AIR, power, 2, new Vec3(1, 0, 0)), "Air entity returned false");
                near(h, health - targets[i].getHealth(), 1 + power, "Air damage");
            }
        } finally { MinecraftForge.EVENT_BUS.unregister(witness); }
        h.assertTrue(events.get() == 2, "Air damage did not reach real Forge hurt events"); h.succeed();
    }
    @GameTest(template="empty") public static void airRejectedDamageStillKnocksBackAndHonorsResistance(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setInvulnerable(true); target.setOnGround(true);
        target.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(entity(h, p, target, FocusNodeRegistry.AIR, 3, 2, new Vec3(1, 0, 0)), "Rejected damage changed Air return");
        near(h, target.getDeltaMovement().x, 1, "Original damage*.25 normal knockback"); near(h, target.getHealth(), 10, "Invulnerable target took damage");
        var resistant = cow(h, p); resistant.setInvulnerable(true); resistant.setOnGround(true);
        resistant.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); resistant.setDeltaMovement(Vec3.ZERO);
        entity(h, p, resistant, FocusNodeRegistry.AIR, 5, 2, new Vec3(1, 0, 0));
        h.assertTrue(resistant.getDeltaMovement().lengthSqr() == 0, "Air bypassed resistance with fixed velocity"); h.succeed();
    }
    @GameTest(template="empty") public static void airWithoutTrajectoryUsesTargetYawAndNeverChangesBlocks(GameTestHelper h) {
        var p = player(h); p.setYRot(0); var target = cow(h, p); target.setYRot(90); target.setInvulnerable(true); target.setOnGround(true);
        entity(h, p, target, FocusNodeRegistry.AIR, 1, 2, null);
        near(h, target.getDeltaMovement().x, .5, "Target yaw fallback"); near(h, target.getDeltaMovement().z, 0, "Caster yaw incorrectly selected");
        BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3)); h.getLevel().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        h.assertTrue(!block(h, p, pos, FocusNodeRegistry.AIR, 5) && h.getLevel().getBlockState(pos).is(Blocks.GLASS), "Air gained a block transformation"); h.succeed();
    }
    @GameTest(template="empty") public static void frostDamageDurationAndAmplifierCrossTheOriginalPowerThreshold(GameTestHelper h) {
        var p = player(h);
        for (int power : new int[]{2, 3}) {
            var target = cow(h, p); float before = target.getHealth(); int duration = power == 2 ? 2 : 10;
            h.assertTrue(!entity(h, p, target, FocusNodeRegistry.FROST, power, duration, Vec3.ZERO), "Frost must return false after success");
            near(h, before - target.getHealth(), 3 + power, "Frost damage");
            var slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            h.assertTrue(slow != null && slow.getDuration() == duration * 20 && slow.getAmplifier() == (power == 2 ? 1 : 2), "Frost duration/Slowness II-III threshold differs");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void frostRejectedDamageStillAppliesSlowness(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setInvulnerable(true);
        h.assertTrue(!entity(h, p, target, FocusNodeRegistry.FROST, 5, 10, Vec3.ZERO), "Frost return changed");
        near(h, target.getHealth(), 10, "Invulnerable target damaged");
        h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2
                && target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == 200, "Damage acceptance gated Frost Slowness"); h.succeed();
    }
    @GameTest(template="empty") public static void frostFreezesThreeDimensionalSourcesUnderRoofWithRealScheduledIce(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos center = h.absolutePos(new BlockPos(3, 2, 3));
        BlockPos upper = center.above(), lower = center.below(), outside = center.offset(2, 1, 0), flowing = center.west(), logged = center.south();
        for (BlockPos pos : List.of(center, upper, lower, outside)) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(upper.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(flowing, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        level.setBlockAndUpdate(logged, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        h.assertTrue(!block(h, p, center, FocusNodeRegistry.FROST, 1), "Block Frost must return false");
        for (BlockPos pos : List.of(center, upper, lower)) {
            h.assertTrue(level.getBlockState(pos).is(Blocks.FROSTED_ICE), "Source sphere became a surface disk or required air/light");
            var tick = ((LevelChunkTicks<Block>)level.getChunkAt(pos).getBlockTicks()).getAll().filter(t -> t.type() == Blocks.FROSTED_ICE && t.pos().equals(pos)).findFirst();
            h.assertTrue(tick.isPresent() && tick.get().triggerTick() - level.getGameTime() >= 60
                    && tick.get().triggerTick() - level.getGameTime() <= 120, "Missing inclusive60..120 vanilla ice tick");
        }
        h.assertTrue(level.getBlockState(outside).is(Blocks.WATER) && level.getBlockState(flowing).is(Blocks.WATER)
                && level.getBlockState(flowing).getValue(LiquidBlock.LEVEL) == 1
                && level.getBlockState(logged).getValue(BlockStateProperties.WATERLOGGED), "Frost froze outside sphere/flowing/waterlogged water"); h.succeed();
    }
    @GameTest(template="empty") public static void frostSphereIsCenteredAtActualHitVector(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos center = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(center.west(2), Blocks.WATER.defaultBlockState()); level.setBlockAndUpdate(center.east(2), Blocks.WATER.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(center).add(-.5, 0, 0), Direction.WEST, center, false);
        FocusEffects.apply(level, p, node(FocusNodeRegistry.FROST, 1, 2), hit, Vec3.ZERO);
        h.assertTrue(level.getBlockState(center.west(2)).is(Blocks.FROSTED_ICE) && level.getBlockState(center.east(2)).is(Blocks.WATER), "Frost used hit block center rather than hitVec"); h.succeed();
    }
    @GameTest(template="empty") public static void frostRejectsEntityCollisionAndRollsBackCancelledPlacement(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos center = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(center, Blocks.WATER.defaultBlockState()); var target = cow(h, p); target.setPos(Vec3.atBottomCenterOf(center)); level.addFreshEntity(target);
        block(h, p, center, FocusNodeRegistry.FROST, 1);
        h.assertTrue(level.getBlockState(center).is(Blocks.WATER), "Ice intersected an entity"); target.discard();
        AtomicInteger vetoes = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> guard = event -> {
            if (event.getEntity() == p && event.getPos().equals(center)) { event.setCanceled(true); vetoes.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, guard);
        try { block(h, p, center, FocusNodeRegistry.FROST, 1); } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.assertTrue(vetoes.get() == 1 && level.getBlockState(center).is(Blocks.WATER)
                && ((LevelChunkTicks<Block>)level.getChunkAt(center).getBlockTicks()).getAll().noneMatch(t -> t.type() == Blocks.FROSTED_ICE && t.pos().equals(center)), "Cancelled Frost placement left ice/tick"); h.succeed();
    }
    @GameTest(template="empty") public static void frostHonorsAdventureAndNeverLoadsRemoteTargetChunks(GameTestHelper h) {
        var p = player(h); p.gameMode.changeGameModeForPlayer(GameType.ADVENTURE); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        h.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()); block(h, p, pos, FocusNodeRegistry.FROST, 5);
        h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.WATER), "Adventure restriction bypassed");
        BlockPos remote = new BlockPos(1_000_000, 80, 1_000_000); p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Remote test chunk was already loaded");
        block(h, p, remote, FocusNodeRegistry.FROST, 5); block(h, p, remote, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(h.getLevel());
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Elemental effect force-loaded remote chunk"); h.succeed();
    }
    @GameTest(template="empty") public static void earthEntityDamageAndRejectedDamageReturnMatchBeta26(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); float before = target.getHealth(); AtomicInteger events = new AtomicInteger();
        Consumer<LivingHurtEvent> witness = event -> {
            if (event.getEntity() != target) return;
            h.assertTrue(event.getSource().getDirectEntity() == target && event.getSource().getEntity() == p, "Earth damage source differs"); events.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(witness);
        try { h.assertTrue(entity(h, p, target, FocusNodeRegistry.EARTH, 4, 2, Vec3.ZERO), "Earth entity returned false"); }
        finally { MinecraftForge.EVENT_BUS.unregister(witness); }
        near(h, before - target.getHealth(), 8, "Earth2*power damage"); h.assertTrue(events.get() == 1, "Earth skipped actual hurt event");
        var immune = cow(h, p); immune.setInvulnerable(true);
        h.assertTrue(entity(h, p, immune, FocusNodeRegistry.EARTH, 5, 2, Vec3.ZERO), "Rejected Earth damage returned false"); near(h, immune.getHealth(), 10, "Invulnerable Earth damage"); h.succeed();
    }
    @GameTest(template="empty") public static void earthBreaksOnlyOriginalSoftThresholdAtEndPhase(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos glass = h.absolutePos(new BlockPos(3, 1, 3));
        BlockPos dirt = glass.east(), stone = glass.north(), bedrock = glass.west();
        level.setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState()); level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState()); aura(level, glass, 2);
        block(h, p, glass, FocusNodeRegistry.EARTH, 3); EarthBreakerQueue.process(level);
        h.assertTrue(level.getBlockState(glass).is(Blocks.GLASS), "Power3 incorrectly broke hardness.3 above.24");
        h.assertTrue(!block(h, p, glass, FocusNodeRegistry.EARTH, 4), "Queued Earth block must return false");
        block(h, p, dirt, FocusNodeRegistry.EARTH, 5); block(h, p, stone, FocusNodeRegistry.EARTH, 5); block(h, p, bedrock, FocusNodeRegistry.EARTH, 5);
        EarthBreakerQueue.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.START, level, () -> true));
        h.assertTrue(level.getBlockState(glass).is(Blocks.GLASS), "Earth harvested before END");
        EarthBreakerQueue.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true));
        h.assertTrue(level.isEmptyBlock(glass) && level.getBlockState(dirt).is(Blocks.DIRT) && level.getBlockState(stone).is(Blocks.STONE)
                && level.getBlockState(bedrock).is(Blocks.BEDROCK), "Earth destroyed normal dirt/stone/negative hardness");
        near(h, AuraManager.getVis(level, glass), 1.9, "Only harvested candidate extra aura charge"); h.succeed();
    }
    @GameTest(template="empty") public static void earthExtraAuraComesFromTargetChunkEvenWhenCasterHasNone(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos pos = p.blockPosition().offset(16, 0, 0);
        level.getChunkAt(pos); level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, p.blockPosition(), 0); aura(level, pos, 1);
        block(h, p, pos, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(level);
        h.assertTrue(level.isEmptyBlock(pos), "Earth consulted caster aura for target break"); near(h, AuraManager.getVis(level, pos), .9, "Target aura debit");
        near(h, AuraManager.getVis(level, p.blockPosition()), 0, "Target breaker altered caster chunk"); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); cleanDrops(level, pos); h.succeed();
    }
    @GameTest(template="empty") public static void earthNoAuraAndChangedStateDropQueueWithoutRetryOrCharge(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, .05F); block(h, p, pos, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Insufficient .1 aura still harvested"); near(h, AuraManager.getVis(level, pos), .05, "Partial breaker payment");
        aura(level, pos, 1); EarthBreakerQueue.process(level); h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Discarded no-aura break retried later");
        block(h, p, pos, FocusNodeRegistry.EARTH, 5); level.setBlockAndUpdate(pos, Blocks.GLASS_PANE.defaultBlockState()); EarthBreakerQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS_PANE), "Breaker harvested replacement block state"); near(h, AuraManager.getVis(level, pos), 1, "Changed state charged aura"); h.succeed();
    }
    @GameTest(template="empty") public static void earthCancelledForgeBreakStillChargesOriginalPointOneVis(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 1); cleanDrops(level, pos); AtomicInteger vetoes = new AtomicInteger();
        Consumer<BlockEvent.BreakEvent> guard = event -> {
            if (event.getPlayer() == p && event.getPos().equals(pos)) { event.setCanceled(true); vetoes.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, guard);
        try { block(h, p, pos, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(level); } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        h.assertTrue(vetoes.get() == 1 && level.getBlockState(pos).is(Blocks.GLASS) && drops(level, pos).isEmpty(), "Cancelled Forge break was harvested or hook skipped");
        near(h, AuraManager.getVis(level, pos), .9, "BETA26 cancelled-harvest debit omitted"); h.succeed();
    }
    @GameTest(template="empty") public static void earthUsesRealMainHandSilkLootAndEventXpWithoutCasterOrToolWear(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); pick.enchant(Enchantments.SILK_TOUCH, 1); pick.setDamageValue(31);
        ItemStack caster = CatalogModule.stack("caster_basic"); p.setItemInHand(InteractionHand.MAIN_HAND, pick); p.setItemInHand(InteractionHand.OFF_HAND, caster);
        ItemStack beforePick = pick.copy(), beforeCaster = caster.copy(); level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 1); cleanDrops(level, pos);
        AtomicInteger events = new AtomicInteger(); Consumer<BlockEvent.BreakEvent> xp = event -> {
            if (event.getPlayer() == p && event.getPos().equals(pos)) { event.setExpToDrop(7); events.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, xp);
        try { block(h, p, pos, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(level); } finally { MinecraftForge.EVENT_BUS.unregister(xp); }
        h.assertTrue(level.isEmptyBlock(pos) && drops(level, pos, Items.GLASS) == 1 && events.get() == 1, "Earth lost actual main-hand Silk Touch loot or duplicated break event");
        int experience = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(.6)).stream().mapToInt(ExperienceOrb::getValue).sum();
        h.assertTrue(experience == 7 && ItemStack.matches(pick, beforePick) && ItemStack.matches(caster, beforeCaster), "Event XP or untouched tool/caster changed");
        cleanDrops(level, pos); h.succeed();
    }
    @GameTest(template="empty") public static void earthPreservesBlockEntityLootThroughRealDecoratedPotHarvest(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(pos, Blocks.DECORATED_POT.defaultBlockState()); var pot = (DecoratedPotBlockEntity)level.getBlockEntity(pos);
        CompoundTag data = new CompoundTag(); ListTag sherds = new ListTag();
        for (String sherd : List.of("minecraft:archer_pottery_sherd", "minecraft:brick", "minecraft:brick", "minecraft:brick")) sherds.add(StringTag.valueOf(sherd));
        data.put("sherds", sherds); pot.load(data); pot.setChanged(); aura(level, pos, 1); cleanDrops(level, pos);
        block(h, p, pos, FocusNodeRegistry.EARTH, 1); EarthBreakerQueue.process(level);
        var output = drops(level, pos).stream().filter(drop -> drop.getItem().is(Items.DECORATED_POT)).toList();
        h.assertTrue(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null && output.size() == 1 && output.get(0).getItem().getCount() == 1,
                "Earth rejected a soft block entity or lost its actual loot");
        var copied = output.get(0).getItem().getOrCreateTagElement("BlockEntityTag").getList("sherds", Tag.TAG_STRING);
        h.assertTrue(copied.size() == 4 && copied.getString(0).equals("minecraft:archer_pottery_sherd"), "Earth supplied null/stale block entity to loot");
        cleanDrops(level, pos); h.succeed();
    }
    @GameTest(template="empty") public static void earthCreativeHasNoOrdinaryLootAndAdventureRejectsBeforeExtraCost(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        p.gameMode.changeGameModeForPlayer(GameType.CREATIVE); ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); pick.enchant(Enchantments.SILK_TOUCH, 1); p.setItemInHand(InteractionHand.MAIN_HAND, pick);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 1); cleanDrops(level, pos);
        block(h, p, pos, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(level);
        h.assertTrue(level.isEmptyBlock(pos) && drops(level, pos).isEmpty(), "Creative Earth produced survival loot"); near(h, AuraManager.getVis(level, pos), .9, "Creative Earth invented free extra cost");
        p.gameMode.changeGameModeForPlayer(GameType.ADVENTURE); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 1); block(h, p, pos, FocusNodeRegistry.EARTH, 5); EarthBreakerQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Adventure block restriction bypassed"); near(h, AuraManager.getVis(level, pos), 1, "Permission rejection paid extra cost"); h.succeed();
    }
    @GameTest(template="empty") public static void malformedSettingsAndOffThreadCallsNeverDamageMutateOrQueue(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var target = cow(h, p); float health = target.getHealth();
        for (String key : List.of(FocusNodeRegistry.AIR, FocusNodeRegistry.FROST, FocusNodeRegistry.EARTH))
            for (int power : new int[]{0, 6}) h.assertTrue(!entity(h, p, target, key, power, 2, Vec3.ZERO), "Out-of-range power accepted");
        for (int duration : new int[]{1, 11}) h.assertTrue(!entity(h, p, target, FocusNodeRegistry.FROST, 1, duration, Vec3.ZERO), "Out-of-range duration accepted");
        h.assertTrue(!entity(h, p, target, FocusNodeRegistry.AIR, 1, 2, new Vec3(Double.NaN, 0, 0)), "Nonfinite direction accepted");
        BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3)); level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); var state = level.getBlockState(pos); aura(level, pos, 1);
        boolean rejected = CompletableFuture.supplyAsync(() -> {
            EarthBreakerQueue.enqueue(level, p, pos, state); EarthBreakerQueue.process(level);
            return !entity(h, p, target, FocusNodeRegistry.AIR, 5, 2, Vec3.ZERO);
        }).join();
        EarthBreakerQueue.process(level);
        h.assertTrue(rejected && level.getBlockState(pos).is(Blocks.GLASS) && target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "Invalid/off-thread call changed effects or queue");
        near(h, target.getHealth(), health, "Invalid damage"); near(h, AuraManager.getVis(level, pos), 1, "Off-thread aura mutation"); h.succeed();
    }
}
