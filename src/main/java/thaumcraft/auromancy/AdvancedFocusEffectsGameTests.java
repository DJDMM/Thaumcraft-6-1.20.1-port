package thaumcraft.auromancy;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.auromancy.focus.FocusCompiler;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Live health, Forge damage/heal events, source tags, invalid calls and real caster payment. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class AdvancedFocusEffectsGameTests {
    private static final String FLUX = "thaumcraft.FLUX", HEAL = "thaumcraft.HEAL";
    private static ServerPlayer player(GameTestHelper h) {
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "TC6FluxHeal"));
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p);
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        p.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        p.setYRot(0); p.setXRot(0); p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return p;
    }
    private static Cow cow(GameTestHelper h, ServerPlayer p) {
        Cow target = EntityType.COW.create(h.getLevel()); target.setNoAi(true); target.setPos(p.position().add(1, 0, 0)); return target;
    }
    private static Zombie zombie(GameTestHelper h, ServerPlayer p) {
        Zombie target = EntityType.ZOMBIE.create(h.getLevel()); target.setNoAi(true); target.setPos(p.position().add(1, 0, 0)); return target;
    }
    private static FocusGraph.Node node(String key, int power) { return node(key, Map.of("power", power)); }
    private static FocusGraph.Node node(String key, Map<String, Integer> settings) {
        return new FocusGraph.Node(1, 0, List.of(), 0, 1, key, settings);
    }
    private static boolean apply(GameTestHelper h, ServerPlayer p, Entity target, String key, int power) {
        return AdvancedFocusEffects.apply(h.getLevel(), p, node(key, power), new EntityHitResult(target, target.position()), Vec3.ZERO);
    }
    private static void near(GameTestHelper h, double got, double expected, String why) {
        h.assertTrue(Math.abs(got - expected) < .001, why + ": " + got + " != " + expected);
    }
    private static void magicSource(GameTestHelper h, LivingHurtEvent event, Entity direct, ServerPlayer caster) {
        h.assertTrue(event.getSource().is(DamageTypes.INDIRECT_MAGIC), "Effect did not use indirect magic");
        h.assertTrue(event.getSource().getDirectEntity() == direct && event.getSource().getEntity() == caster,
                "Immediate/true BETA26 sources changed");
        h.assertTrue(event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                && event.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO)
                && !event.getSource().is(DamageTypeTags.IS_PROJECTILE), "Original magic/bypass/nonprojectile semantics lost");
    }

    @GameTest(template="empty") public static void fluxLowAndHighPowerDamageUsesHitEntityAndTrueCaster(GameTestHelper h) {
        var p = player(h); Cow[] targets = {cow(h, p), cow(h, p)}; AtomicInteger events = new AtomicInteger();
        Consumer<LivingHurtEvent> witness = event -> {
            if (event.getEntity() == targets[0] || event.getEntity() == targets[1]) {
                magicSource(h, event, event.getEntity(), p); events.incrementAndGet();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, witness);
        try {
            for (int i = 0; i < targets.length; i++) {
                int power = i == 0 ? 1 : 5; float before = targets[i].getHealth();
                h.assertTrue(!apply(h, p, targets[i], FLUX, power), "Successful Flux must return false");
                near(h, before - targets[i].getHealth(), 3 + power, "Flux damage formula");
            }
        } finally { MinecraftForge.EVENT_BUS.unregister(witness); }
        h.assertTrue(events.get() == 2, "Flux did not reach real Forge hurt events"); h.succeed();
    }
    @GameTest(template="empty") public static void fluxBypassesActualWornArmor(GameTestHelper h) {
        var p = player(h); var target = cow(h, p);
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        target.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        target.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        target.tick(); // Vanilla installs equipment attribute modifiers during its real tick.
        h.assertTrue(target.getArmorValue() > 0, "Armor fixture has no real armor");
        float before = target.getHealth(); apply(h, p, target, FLUX, 5);
        near(h, before - target.getHealth(), 8, "Flux was reduced by ordinary armor"); h.succeed();
    }
    @GameTest(template="empty") public static void rejectedFluxDamageDoesNotChangeFalseReturnOrAddDebuffs(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setInvulnerable(true);
        h.assertTrue(!apply(h, p, target, FLUX, 5), "Rejected Flux changed its return");
        near(h, target.getHealth(), target.getMaxHealth(), "Invulnerable target damaged");
        h.assertTrue(target.getActiveEffects().isEmpty() && target.getRemainingFireTicks() <= 0, "Flux gained an invented debuff/fire effect"); h.succeed();
    }
    @GameTest(template="empty") public static void absentPowerRetainsOriginalDefaultOneForBothEffects(GameTestHelper h) {
        var p = player(h); var damaged = cow(h, p); var healed = cow(h, p); healed.setHealth(2);
        AdvancedFocusEffects.apply(h.getLevel(), p, node(FLUX, Map.of()), new EntityHitResult(damaged, damaged.position()), null);
        AdvancedFocusEffects.apply(h.getLevel(), p, node(HEAL, Map.of()), new EntityHitResult(healed, healed.position()), null);
        near(h, damaged.getHealth(), 6, "Default Flux power differs"); near(h, healed.getHealth(), 3, "Default Heal power differs"); h.succeed();
    }
    @GameTest(template="empty") public static void fluxAlsoDamagesNonlivingTargetsThroughTheirOwnHurtApi(GameTestHelper h) {
        var p = player(h); var item = new ItemEntity(h.getLevel(), p.getX() + 1, p.getY(), p.getZ(), new ItemStack(Items.COBBLESTONE));
        h.assertTrue(!apply(h, p, item, FLUX, 5) && item.isRemoved(), "Flux was incorrectly restricted to LivingEntity"); h.succeed();
    }
    @GameTest(template="empty") public static void healingLowAndHighPowerUsesHealthApiAndCapsAtMaximum(GameTestHelper h) {
        var p = player(h);
        for (int power : new int[]{1, 5}) {
            var target = cow(h, p); target.setHealth(2);
            h.assertTrue(!apply(h, p, target, HEAL, power), "Successful Heal must return false");
            near(h, target.getHealth(), 2 + power, "Heal original amount");
        }
        var full = cow(h, p); full.setHealth(full.getMaxHealth() - .5F); apply(h, p, full, HEAL, 5);
        near(h, full.getHealth(), full.getMaxHealth(), "Heal exceeded max health"); h.succeed();
    }
    @GameTest(template="empty") public static void healingHonorsRealForgeAmountChangesAndCancellation(GameTestHelper h) {
        var p = player(h); Cow changed = cow(h, p), cancelled = cow(h, p); changed.setHealth(2); cancelled.setHealth(2);
        AtomicInteger events = new AtomicInteger(); Consumer<LivingHealEvent> listener = event -> {
            if (event.getEntity() == changed) { near(h, event.getAmount(), 5, "Original amount before Forge adjustment"); event.setAmount(2); events.incrementAndGet(); }
            if (event.getEntity() == cancelled) { event.setCanceled(true); events.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, listener);
        try { apply(h, p, changed, HEAL, 5); apply(h, p, cancelled, HEAL, 5); }
        finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        near(h, changed.getHealth(), 4, "Forge adjusted heal ignored"); near(h, cancelled.getHealth(), 2, "Cancelled heal still applied");
        h.assertTrue(events.get() == 2, "Effect bypassed LivingEntity.heal events"); h.succeed();
    }
    @GameTest(template="empty") public static void undeadHealDamageUsesCasterAsBothSourcesAndBypassesArmor(GameTestHelper h) {
        var p = player(h); Zombie[] targets = {zombie(h, p), zombie(h, p)}; AtomicInteger events = new AtomicInteger();
        Consumer<LivingHurtEvent> listener = event -> {
            if (event.getEntity() == targets[0] || event.getEntity() == targets[1]) { magicSource(h, event, p, p); events.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, listener);
        try {
            for (int i = 0; i < targets.length; i++) {
                int power = i == 0 ? 1 : 5;
                targets[i].setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE)); targets[i].tick();
                h.assertTrue(targets[i].getArmorValue() >= 8, "Undead worn armor did not install attributes"); float before = targets[i].getHealth();
                h.assertTrue(!apply(h, p, targets[i], HEAL, power), "Undead Heal must return false");
                near(h, before - targets[i].getHealth(), power * 1.5F, "Undead magic damage formula");
            }
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(events.get() == 2, "Undead damage did not reach hurt events"); h.succeed();
    }
    @GameTest(template="empty") public static void invulnerableUndeadDoesNotReceiveHealingWhenDamageIsRefused(GameTestHelper h) {
        var p = player(h); var target = zombie(h, p); target.setHealth(10); target.setInvulnerable(true);
        h.assertTrue(!apply(h, p, target, HEAL, 5), "Refused undead damage changed return");
        near(h, target.getHealth(), 10, "Undead was healed or bypassed invulnerability"); h.succeed();
    }
    @GameTest(template="empty") public static void healingDoesNotResurrectDeadLivingTargets(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setHealth(0);
        h.assertTrue(!apply(h, p, target, HEAL, 5), "Dead heal changed return");
        near(h, target.getHealth(), 0, "Dead target resurrected"); h.succeed();
    }
    @GameTest(template="empty") public static void healingIgnoresNonlivingTargetsWithoutDestroyingThem(GameTestHelper h) {
        var p = player(h); var item = new ItemEntity(h.getLevel(), p.getX() + 1, p.getY(), p.getZ(), new ItemStack(Items.COBBLESTONE));
        h.assertTrue(!apply(h, p, item, HEAL, 5) && !item.isRemoved() && item.getItem().getCount() == 1,
                "Heal hurt or transformed nonliving entity"); h.succeed();
    }
    @GameTest(template="empty") public static void blockHitsDoNotChangeBlocksOrConsumeExtraAura(GameTestHelper h) {
        var p = player(h); BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3)); h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        AuraManager.drainVis(h.getLevel(), pos, Float.MAX_VALUE, false); AuraManager.addVis(h.getLevel(), pos, 10);
        for (String key : List.of(FLUX, HEAL)) h.assertTrue(!AdvancedFocusEffects.apply(h.getLevel(), p, node(key, 5),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), null), "Block effect returned true");
        h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE), "Flux/Heal gained an invented block mutation");
        near(h, AuraManager.getVis(h.getLevel(), pos), 10, "Effect charged another spell/target cost"); h.succeed();
    }
    @GameTest(template="empty") public static void forgedSettingsAndNullPowerNeverDamageOrHeal(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setHealth(2); var hit = new EntityHitResult(target, target.position());
        Map<String, Integer> nullPower = new HashMap<>(); nullPower.put("power", null);
        for (String key : List.of(FLUX, HEAL)) for (Map<String, Integer> settings : List.of(Map.of("power", 0), Map.of("power", 6),
                Map.of("power", 1, "duration", 2), nullPower)) {
            h.assertTrue(!AdvancedFocusEffects.apply(h.getLevel(), p, node(key, settings), hit, null), "Forged effect settings accepted");
            near(h, target.getHealth(), 2, "Rejected settings mutated health");
        }
        h.assertTrue(!AdvancedFocusEffects.apply(h.getLevel(), p, node("thaumcraft.CURSE", 5), hit, null), "Unknown runtime key accepted");
        near(h, target.getHealth(), 2, "Unknown effect mutated health"); h.succeed();
    }
    @GameTest(template="empty") public static void missingMissAndNonfiniteArgumentsHaveNoHealthEffect(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setHealth(2); var hit = new EntityHitResult(target, target.position());
        var effect = node(HEAL, 5);
        h.assertTrue(!AdvancedFocusEffects.apply(null, p, effect, hit, null)
                && !AdvancedFocusEffects.apply(h.getLevel(), null, effect, hit, null)
                && !AdvancedFocusEffects.apply(h.getLevel(), p, null, hit, null)
                && !AdvancedFocusEffects.apply(h.getLevel(), p, effect, null, null), "Missing effect argument accepted");
        h.assertTrue(!AdvancedFocusEffects.apply(h.getLevel(), p, effect, hit, new Vec3(Double.NaN, 0, 0))
                && !AdvancedFocusEffects.apply(h.getLevel(), p, effect, new EntityHitResult(target, new Vec3(Double.POSITIVE_INFINITY, 0, 0)), null)
                && !AdvancedFocusEffects.apply(h.getLevel(), p, effect, BlockHitResult.miss(p.position(), Direction.UP, p.blockPosition()), null),
                "Nonfinite/miss effect accepted");
        near(h, target.getHealth(), 2, "Invalid argument changed health"); h.succeed();
    }
    @GameTest(template="empty") public static void removedForeignUnloadedTargetsAndInvalidCastersAreRejected(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setHealth(2); var hit = new EntityHitResult(target, target.position());
        p.gameMode.changeGameModeForPlayer(GameType.SPECTATOR); AdvancedFocusEffects.apply(h.getLevel(), p, node(HEAL, 5), hit, null);
        near(h, target.getHealth(), 2, "Spectator healed"); p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        p.setHealth(0); AdvancedFocusEffects.apply(h.getLevel(), p, node(HEAL, 5), hit, null); near(h, target.getHealth(), 2, "Dead caster healed"); p.setHealth(20);
        var foreignLevel = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(foreignLevel != null, "Nether fixture absent");
        var foreign = EntityType.COW.create(foreignLevel); foreign.setHealth(2); foreign.setPos(p.position());
        AdvancedFocusEffects.apply(h.getLevel(), p, node(HEAL, 5), new EntityHitResult(foreign, foreign.position()), null);
        near(h, foreign.getHealth(), 2, "Cross-world target healed");
        var remote = cow(h, p); remote.setHealth(2); remote.setPos(30_000_000, 80, 30_000_000);
        AdvancedFocusEffects.apply(h.getLevel(), p, node(HEAL, 5), new EntityHitResult(remote, remote.position()), null);
        near(h, remote.getHealth(), 2, "Unloaded/out-of-border target healed");
        target.discard(); AdvancedFocusEffects.apply(h.getLevel(), p, node(HEAL, 5), hit, null);
        near(h, target.getHealth(), 2, "Removed target healed"); h.succeed();
    }
    @GameTest(template="empty") public static void offThreadExecutionIsRejectedWithoutDamageOrHealing(GameTestHelper h) {
        var p = player(h); var target = cow(h, p); target.setHealth(2);
        boolean rejected = CompletableFuture.supplyAsync(() -> {
            boolean flux = apply(h, p, target, FLUX, 5), heal = apply(h, p, target, HEAL, 5);
            AdvancedFocusEffects.playCastSound(h.getLevel(), p, FLUX); return !flux && !heal;
        }).join();
        h.assertTrue(rejected, "Off-thread effects accepted"); near(h, target.getHealth(), 2, "Off-thread effect changed health"); h.succeed();
    }

    private static void ready(ServerPlayer p, String medium, String effect, int power) {
        var nodes = medium == null ? List.of(
                new FocusGraph.Node(0, -1, List.of(1), 0, 0, FocusNodeRegistry.ROOT, Map.of()),
                new FocusGraph.Node(1, 0, List.of(), 0, 1, effect, Map.of("power", power))) : List.of(
                new FocusGraph.Node(0, -1, List.of(1), 0, 0, FocusNodeRegistry.ROOT, Map.of()),
                new FocusGraph.Node(1, 0, List.of(2), 0, 1, medium, Map.of()),
                new FocusGraph.Node(2, 1, List.of(), 0, 2, effect, Map.of("power", power)));
        var compiled = FocusCompiler.compile(new FocusGraph(nodes), CatalogModule.stack("focus_1"), ignored -> true);
        if (!compiled.success()) throw new AssertionError(compiled.error());
        ItemStack caster = CatalogModule.stack("caster_basic");
        FocusSelection.setInstalled(caster, FocusStacks.apply(CatalogModule.stack("focus_1"), compiled.plan(), "BETA26 Flux/Heal test"));
        p.setItemInHand(InteractionHand.MAIN_HAND, caster);
        AuraManager.drainVis(p.serverLevel(), p.blockPosition(), Float.MAX_VALUE, false); AuraManager.addVis(p.serverLevel(), p.blockPosition(), 10);
    }
    @GameTest(template="empty") public static void realSelfHealCastPaysVisAndCooldownDespiteFalseEffectReturn(GameTestHelper h) {
        var p = player(h); p.setHealth(10); ready(p, null, HEAL, 2);
        h.assertTrue(FocusCasting.cast(p, InteractionHand.MAIN_HAND) == FocusCasting.Result.CAST, "Self Heal cast failed");
        near(h, p.getHealth(), 12, "ROOT self target not healed"); near(h, AuraManager.getVis(h.getLevel(), p.blockPosition()), 8.4, "Self Heal vis price");
        h.assertTrue(FocusCasting.cast(p, InteractionHand.MAIN_HAND) == FocusCasting.Result.COOLDOWN, "False effect return cleared caster cooldown");
        near(h, p.getHealth(), 12, "Cooldown healed twice"); near(h, AuraManager.getVis(h.getLevel(), p.blockPosition()), 8.4, "Cooldown paid twice"); h.succeed();
    }
    @GameTest(template="empty") public static void realTouchFluxCastPaysExactlyOnceDespiteFalseEffectReturn(GameTestHelper h) {
        var p = player(h); ready(p, FocusNodeRegistry.TOUCH, FLUX, 1);
        var target = cow(h, p); target.setPos(p.getX(), p.getY(), p.getZ() + 3); h.getLevel().addFreshEntity(target);
        h.assertTrue(FocusCasting.cast(p, InteractionHand.MAIN_HAND) == FocusCasting.Result.CAST, "Touch Flux cast failed");
        near(h, target.getHealth(), 6, "Touch Flux target not damaged"); near(h, AuraManager.getVis(h.getLevel(), p.blockPosition()), 9, "Touch Flux vis price");
        h.assertTrue(FocusCasting.cast(p, InteractionHand.MAIN_HAND) == FocusCasting.Result.COOLDOWN, "False Flux return discarded cooldown");
        near(h, AuraManager.getVis(h.getLevel(), p.blockPosition()), 9, "Cooldown charged again"); target.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void realTouchHealRayHealsOtherEntityAndPaysOneSpellPrice(GameTestHelper h) {
        var p = player(h); ready(p, FocusNodeRegistry.TOUCH, HEAL, 1);
        var target = cow(h, p); target.setHealth(2); target.setPos(p.getX(), p.getY(), p.getZ() + 3); h.getLevel().addFreshEntity(target);
        h.assertTrue(FocusCasting.touchTarget(p) instanceof EntityHitResult hit && hit.getEntity() == target, "Real touch ray did not select fixture");
        h.assertTrue(FocusCasting.cast(p, InteractionHand.MAIN_HAND) == FocusCasting.Result.CAST, "Touch Heal cast failed");
        near(h, target.getHealth(), 3, "Real touch target did not heal"); near(h, p.getHealth(), 20, "Touch Heal changed caster health");
        near(h, AuraManager.getVis(h.getLevel(), p.blockPosition()), 8.8, "Touch Heal did not pay exactly complexity6*.2"); target.discard(); h.succeed();
    }
}
