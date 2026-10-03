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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Break delay, real crack packets, protection, actual loot/XP and detached queue lifetimes. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusBreakGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "TC6Break"));
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p);
        var pos = h.absolutePos(new BlockPos(1, 1, 1)); p.setPos(Vec3.atBottomCenterOf(pos));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return p;
    }
    private static FocusGraph.Node node(int power, int silk, int fortune) {
        return new FocusGraph.Node(1, 0, List.of(), 0, 1, FocusBreakEffect.KEY,
                Map.of("power", power, "silk", silk, "fortune", fortune));
    }
    private static boolean apply(ServerLevel level, ServerPlayer p, BlockPos pos, int power, int silk, int fortune) {
        return FocusBreakEffect.apply(level, p, node(power, silk, fortune),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), Vec3.ZERO);
    }
    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); }
    private static BlockPos target(GameTestHelper h) { return h.absolutePos(new BlockPos(3, 1, 3)); }
    private static void aura(ServerLevel level, BlockPos pos, float value) {
        AuraManager.drainVis(level, pos, Float.MAX_VALUE, false); AuraManager.addVis(level, pos, value);
    }
    private static void near(GameTestHelper h, float actual, float expected, String reason) {
        h.assertTrue(Math.abs(actual - expected) < .001, reason + ": " + actual + " != " + expected);
    }
    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos) { return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.6)); }
    private static int drops(ServerLevel level, BlockPos pos, Item item) {
        return drops(level, pos).stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }
    private static void clean(ServerLevel level, BlockPos pos) {
        drops(level, pos).forEach(ItemEntity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(.6)).forEach(ExperienceOrb::discard);
    }
    private static void process(ServerLevel level, int ticks) { for (int i = 0; i < ticks; i++) FocusBreakQueue.process(level); }

    @GameTest(template="empty") public static void breakFirstTargetHasZeroInitialDelayButHardnessNeedsRepeatedEndTicks(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        h.assertTrue(apply(level, p, pos, 1, 0, 0), "Break refused a valid target");
        FocusBreakQueue.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.START, level, () -> true));
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Break harvested on START");
        process(level, 12); h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "sqrt(1.5*100) durability was reduced to instant/12 ticks");
        near(h, AuraManager.getVis(level, pos), 4, "Break charged before completion");
        FocusBreakQueue.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true));
        h.assertTrue(level.isEmptyBlock(pos) && drops(level, pos, Items.COBBLESTONE) == 1, "Thirteenth END failed real always-drop stone harvest");
        near(h, AuraManager.getVis(level, pos), 3.75F, "Final Break target charge"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakTargetOrdinalDelayAndProgressSendRealCrackPackets(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        List<ClientboundBlockDestructionPacket> cracks = new ArrayList<>();
        p.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), p) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundBlockDestructionPacket crack && crack.getPos().equals(pos)) cracks.add(crack);
            }
        };
        // Forge exposes an unmodifiable playersView. Temporarily add this packet witness to
        // the real backing list, using the verified SRG field mapping; no login/world input.
        List<ServerPlayer> observers = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class,
                level.getServer().getPlayerList(), "f_11196_");
        observers.add(p);
        try {
            FocusBreakEffect.apply(level, p, node(1, 0, 0), hit(pos), Vec3.ZERO, 1F, 1);
            process(level, 2); level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState()); process(level, 2);
            h.assertTrue(cracks.isEmpty() && level.getBlockState(pos).is(Blocks.OAK_LOG), "Positive4 delay checked/harvested its temporarily changed target too soon");
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            FocusBreakQueue.process(level);
            h.assertTrue(cracks.size() == 1 && cracks.get(0).getProgress() == 0 && cracks.get(0).getId() == pos.hashCode(), "Missing original pre-subtraction stage0/position id packet");
            process(level, 11); h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Delayed target completed before17 END steps");
            FocusBreakQueue.process(level);
            h.assertTrue(level.isEmptyBlock(pos) && cracks.get(cracks.size() - 1).getProgress() == -1
                    && cracks.stream().anyMatch(c -> c.getProgress() >= 8), "Progressive cracks failed to advance/clear after harvest");
        } finally { observers.remove(p); }
        clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakFinalPowerAndStrengthKeepFloatingDurabilityAndZeroHardness(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        FocusBreakEffect.apply(level, p, node(5, 0, 0), hit(pos), Vec3.ZERO, .5F, 0);
        process(level, 4); h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "finalPower.5 ignored");
        FocusBreakQueue.process(level); h.assertTrue(level.isEmptyBlock(pos), "2.5 strength did not finish sqrt150 at5 ticks"); clean(level, pos);
        level.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState()); apply(level, p, pos, 1, 0, 0); FocusBreakQueue.process(level);
        h.assertTrue(level.isEmptyBlock(pos), "Zero hardness produced an endless/NaN breaker"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakSilkOverridesPlaceholderWhileRetainingFortuneAndNeverWearingHeldStacks(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); pick.enchant(Enchantments.BLOCK_FORTUNE, 3); pick.setDamageValue(42);
        pick.getOrCreateTag().putString("tc6_test_marker", "must remain"); ItemStack caster = CatalogModule.stack("caster_basic");
        p.setItemInHand(InteractionHand.MAIN_HAND, pick); p.setItemInHand(InteractionHand.OFF_HAND, caster);
        var beforePick = pick.copy(); var beforeCaster = caster.copy();
        var fake = FocusBreakQueue.lootTool(p, true, 1);
        h.assertTrue(fake.is(CatalogModule.stack("enchanted_placeholder").getItem())
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, fake) == 1
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, fake) == 3
                && !fake.getOrCreateTag().contains("tc6_test_marker"), "Silk override lost placeholder/main-hand enchantment priority or copied unrelated tool NBT");
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        apply(level, p, pos, 5, 1, 1); process(level, 2);
        h.assertTrue(level.isEmptyBlock(pos) && drops(level, pos, Items.GLASS) == 1, "Silk option failed vanilla Silk Touch loot");
        h.assertTrue(ItemStack.matches(pick, beforePick) && ItemStack.matches(caster, beforeCaster), "Break mutated/wore actual held stacks");
        near(h, AuraManager.getVis(level, pos), 3.4F, "Configured fortune1+silk charge must be.6, independent of higher held fortune"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakLowerFortunePreservesRealToolAndHigherFortuneSwitchesPlaceholder(GameTestHelper h) {
        var p = player(h); ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE); tool.enchant(Enchantments.BLOCK_FORTUNE, 3);
        tool.getOrCreateTag().putString("tc6_context", "real tool"); p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var lower = FocusBreakQueue.lootTool(p, false, 2);
        h.assertTrue(ItemStack.matches(tool, lower), "Lower fortune unexpectedly replaced the real main-hand/NBT loot context");
        var higher = FocusBreakQueue.lootTool(p, false, 4);
        h.assertTrue(higher.is(CatalogModule.stack("enchanted_placeholder").getItem())
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, higher) == 4
                && !higher.getOrCreateTag().contains("tc6_context"), "FortuneIV failed original conditional placeholder override"); h.succeed();
    }

    @GameTest(template="empty") public static void breakAllFortuneLevelsReachTheRealLootPipeline(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, CatalogModule.stack("caster_basic"));
        for (int fortune = 0; fortune <= 4; fortune++) {
            level.setBlockAndUpdate(pos, Blocks.LAPIS_ORE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
            apply(level, p, pos, 5, 0, fortune); process(level, 4);
            int count = drops(level, pos, Items.LAPIS_LAZULI);
            h.assertTrue(level.isEmptyBlock(pos) && count >= 4 && count <= 9 * (fortune + 1), "Fortune" + fortune + " failed original always-drop native ore loot");
            near(h, AuraManager.getVis(level, pos), 4F - .25F - fortune * .1F, "Fortune extra vis");
        }
        clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakUsesCompletionMainHandAndRealForgeXpEvenWithSilkOverride(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        apply(level, p, pos, 5, 1, 0); process(level, 2);
        ItemStack completion = new ItemStack(Items.DIAMOND_PICKAXE); completion.enchant(Enchantments.BLOCK_FORTUNE, 3);
        completion.setDamageValue(19); p.setItemInHand(InteractionHand.MAIN_HAND, completion); var before = completion.copy();
        AtomicInteger events = new AtomicInteger(); Consumer<BlockEvent.BreakEvent> xp = event -> {
            if (event.getPlayer() == p && event.getPos().equals(pos)) {
                h.assertTrue(ItemStack.matches(event.getPlayer().getMainHandItem(), before), "Forge event saw temporary/frozen main-hand tool");
                event.setExpToDrop(7); events.incrementAndGet();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, xp);
        try { FocusBreakQueue.process(level); } finally { MinecraftForge.EVENT_BUS.unregister(xp); }
        int actualXp = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(.6)).stream().mapToInt(ExperienceOrb::getValue).sum();
        h.assertTrue(events.get() == 1 && actualXp == 7 && drops(level, pos, Items.STONE) == 1
                && ItemStack.matches(completion, before), "Silk override lost event XP, completion loot, or changed actual tool"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakPreservesActualBlockEntityNbtInLoot(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.DECORATED_POT.defaultBlockState()); var pot = (DecoratedPotBlockEntity)level.getBlockEntity(pos);
        CompoundTag tag = new CompoundTag(); ListTag sherds = new ListTag();
        for (String sherd : List.of("minecraft:archer_pottery_sherd", "minecraft:brick", "minecraft:brick", "minecraft:brick")) sherds.add(StringTag.valueOf(sherd));
        tag.put("sherds", sherds); pot.load(tag); pot.setChanged(); aura(level, pos, 4); clean(level, pos);
        apply(level, p, pos, 5, 0, 0); process(level, 2);
        var actual = drops(level, pos).stream().filter(e -> e.getItem().is(Items.DECORATED_POT)).toList();
        h.assertTrue(level.isEmptyBlock(pos) && actual.size() == 1 && actual.get(0).getItem().getCount() == 1, "Break rejected/lost/duplicated block-entity loot");
        var copied = actual.get(0).getItem().getOrCreateTagElement("BlockEntityTag").getList("sherds", Tag.TAG_STRING);
        h.assertTrue(copied.size() == 4 && copied.getString(0).equals("minecraft:archer_pottery_sherd"), "Stale/null block entity lost actual loot NBT"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakForgeCancellationChargesOnceWithoutLootOrRetry(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        AtomicInteger events = new AtomicInteger(); Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPlayer() == p && event.getPos().equals(pos)) { event.setCanceled(true); events.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, veto);
        try { apply(level, p, pos, 5, 1, 4); process(level, 6); } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(events.get() == 1 && level.getBlockState(pos).is(Blocks.GLASS) && drops(level, pos).isEmpty(), "Forge veto was ignored/retried");
        near(h, AuraManager.getVis(level, pos), 3.1F, "Cancelled original Break target charge.9"); process(level, 6);
        h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Vetoed breaker resurrected later"); h.succeed();
    }

    @GameTest(template="empty") public static void breakEventReplacementDoesNotHarvestTheNewStateButStillPays(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        Consumer<BlockEvent.BreakEvent> replacement = event -> {
            if (event.getPlayer() == p && event.getPos().equals(pos)) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, replacement);
        try { apply(level, p, pos, 5, 0, 0); process(level, 2); } finally { MinecraftForge.EVENT_BUS.unregister(replacement); }
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && drops(level, pos).isEmpty(), "Break harvested replacement installed by protection callback");
        near(h, AuraManager.getVis(level, pos), 3.75F, "Original post-harvest-call charge omitted after changed state"); h.succeed();
    }

    @GameTest(template="empty") public static void breakStateReplacementBeforeCompletionDiscardsProgressWithoutCharge(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        apply(level, p, pos, 1, 0, 0); process(level, 4); level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState()); process(level, 20);
        h.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG) && drops(level, pos).isEmpty(), "Stale Break operation destroyed replacement state");
        near(h, AuraManager.getVis(level, pos), 4, "Stale operation paid extra vis"); h.succeed();
    }

    @GameTest(template="empty") public static void breakInsufficientAuraDiscardsRatherThanPausingOrPartiallyCharging(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4);
        apply(level, p, pos, 1, 1, 4); process(level, 4); aura(level, pos, .8F); FocusBreakQueue.process(level);
        near(h, AuraManager.getVis(level, pos), .8F, "Insufficient.9 charge partially debited"); aura(level, pos, 4); process(level, 20);
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "No-aura operation resumed after refill");
        near(h, AuraManager.getVis(level, pos), 4, "Discarded Break altered refill"); h.succeed();
    }

    @GameTest(template="empty") public static void breakAdditionalCostUsesTargetChunkAndAppliesInCreativeWithoutLoot(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = p.blockPosition().offset(16, 0, 0);
        level.getChunkAt(pos); level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); clean(level, pos);
        aura(level, p.blockPosition(), 0); aura(level, pos, 4); p.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        apply(level, p, pos, 5, 1, 4); process(level, 2);
        h.assertTrue(level.isEmptyBlock(pos) && drops(level, pos).isEmpty(), "Creative Break failed native removal/no ordinary loot");
        near(h, AuraManager.getVis(level, pos), 3.1F, "Creative did not pay target chunk.9");
        near(h, AuraManager.getVis(level, p.blockPosition()), 0, "Extra cost came from caster chunk"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakDuplicateTargetsOnlyHarvestAndDebitOnce(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); aura(level, pos, 4); clean(level, pos);
        apply(level, p, pos, 5, 0, 0); apply(level, p, pos, 5, 0, 0); process(level, 6);
        h.assertTrue(level.isEmptyBlock(pos) && drops(level, pos, Items.COBBLESTONE) == 1, "Duplicate delayed targets duplicated native loot");
        near(h, AuraManager.getVis(level, pos), 3.75F, "Duplicate target debited twice"); clean(level, pos); h.succeed();
    }

    @GameTest(template="empty") public static void breakEventCreatedWorkIsDetachedAndRecursiveProcessingCannotHarvestItEarly(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        // Keep both independent loot positions in one aura chunk regardless of batch origin.
        var second = pos.offset((pos.getX() & 15) <= 12 ? 3 : -3, 0, 0);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); level.setBlockAndUpdate(second, Blocks.GLASS.defaultBlockState());
        aura(level, pos, 4); clean(level, pos); clean(level, second);
        Consumer<BlockEvent.BreakEvent> reentrant = event -> {
            if (event.getPlayer() == p && event.getPos().equals(pos)) {
                apply(level, p, second, 5, 1, 0); FocusBreakQueue.process(level);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, reentrant);
        try { apply(level, p, pos, 5, 1, 0); process(level, 2); } finally { MinecraftForge.EVENT_BUS.unregister(reentrant); }
        h.assertTrue(level.isEmptyBlock(pos) && level.getBlockState(second).is(Blocks.GLASS), "Event-created work processed inside detached/recursive END");
        process(level, 2);
        h.assertTrue(level.isEmptyBlock(second) && drops(level, pos, Items.GLASS) == 1 && drops(level, second, Items.GLASS) == 1,
                "Deferred Break was lost/duplicated"); near(h, AuraManager.getVis(level, pos), 3, "Two silk breaks extra cost");
        clean(level, pos); clean(level, second); h.succeed();
    }

    @GameTest(template="empty") public static void breakAdventureAndDeadCasterRejectBeforeExtraPayment(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 4);
        p.gameMode.changeGameModeForPlayer(GameType.ADVENTURE); apply(level, p, pos, 5, 0, 0); process(level, 4);
        h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Break bypassed Adventure restriction");
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); apply(level, p, pos, 5, 0, 0); p.setHealth(0); process(level, 4);
        h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Pending Break outlived caster"); near(h, AuraManager.getVis(level, pos), 4, "Rejected owner/protection paid extra vis"); h.succeed();
    }

    @GameTest(template="empty") public static void breakUnloadedTargetsBedrockAndEntityHitsNeverGainMutationOrDamage(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        var cow = EntityType.COW.create(level); cow.setPos(p.position().add(1, 0, 0)); float health = cow.getHealth();
        h.assertTrue(FocusBreakEffect.apply(level, p, node(5, 1, 4), new EntityHitResult(cow), Vec3.ZERO), "Original non-block true return lost");
        near(h, cow.getHealth(), health, "Break gained entity damage");
        level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState()); aura(level, pos, 4); apply(level, p, pos, 5, 1, 4); process(level, 20);
        h.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK), "Break destroyed negative hardness"); near(h, AuraManager.getVis(level, pos), 4, "Bedrock paid extra cost");
        var remote = new BlockPos(1_000_000, 80, 1_000_000); apply(level, p, remote, 5, 1, 4); process(level, 20);
        h.assertTrue(level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "Break force-loaded target chunk"); h.succeed();
    }

    @GameTest(template="empty") public static void breakWorldUnloadClearsDelayedAndActiveWorkWithoutResurrection(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h); var second = pos.east();
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(second, Blocks.STONE.defaultBlockState()); aura(level, pos, 4);
        apply(level, p, pos, 1, 0, 0);
        FocusBreakEffect.apply(level, p, node(1, 0, 0), hit(second), Vec3.ZERO, 1F, 5); process(level, 2);
        FocusBreakQueue.unload(new LevelEvent.Unload(level)); process(level, 100);
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && level.getBlockState(second).is(Blocks.STONE), "Unload retained active/delayed runnable and resurrected harvest");
        near(h, AuraManager.getVis(level, pos), 4, "Dropped transient queue charged vis"); h.succeed();
    }

    @GameTest(template="empty") public static void breakMalformedSettingsNonfiniteAndOffThreadCallsAreRejected(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var pos = target(h);
        level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()); aura(level, pos, 4);
        for (int power : new int[]{0, 6}) h.assertTrue(!apply(level, p, pos, power, 0, 0), "Bad power accepted");
        for (int silk : new int[]{-1, 2}) h.assertTrue(!apply(level, p, pos, 1, silk, 0), "Bad silk accepted");
        for (int fortune : new int[]{-1, 5}) h.assertTrue(!apply(level, p, pos, 1, 0, fortune), "Bad fortune accepted");
        var extra = new FocusGraph.Node(1, 0, List.of(), 0, 1, FocusBreakEffect.KEY, Map.of("power", 1, "injected", 1));
        h.assertTrue(!FocusBreakEffect.apply(level, p, extra, hit(pos), Vec3.ZERO), "Unknown setting accepted");
        var nullSettings = new java.util.LinkedHashMap<String, Integer>(); nullSettings.put("power", null);
        var nullNode = new FocusGraph.Node(1, 0, List.of(), 0, 1, FocusBreakEffect.KEY, nullSettings);
        h.assertTrue(!FocusBreakEffect.apply(level, p, nullNode, hit(pos), Vec3.ZERO), "Null setting threw or queued work");
        h.assertTrue(!FocusBreakEffect.apply(level, p, node(1, 0, 0), hit(pos), new Vec3(Double.NaN, 0, 0))
                && !FocusBreakEffect.apply(level, p, node(1, 0, 0), hit(pos), Vec3.ZERO, Float.NaN, 0), "Nonfinite strength/trajectory accepted");
        h.assertTrue(CompletableFuture.supplyAsync(() -> !apply(level, p, pos, 5, 1, 4)).join(), "Off-thread effect queued state");
        process(level, 20); h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS), "Rejected payload still caused harvest");
        near(h, AuraManager.getVis(level, pos), 4, "Rejected payload paid extra cost"); h.succeed();
    }
}
