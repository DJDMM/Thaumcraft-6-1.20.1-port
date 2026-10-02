package thaumcraft.scanning;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayer;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.UUID;

/** Read-only HUD capture and wire metadata must never become a second discovery path. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ThaumometerHudGameTests {
    private ThaumometerHudGameTests() {}

    @GameTest(template = "empty")
    public static void hoveringUnknownTargetChangesNeitherKnowledgeNorCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL));
        CompoundTag before = KnowledgeStore.get(player).save();
        for (int i = 0; i < 10; i++) {
            var packet = ScanningNetwork.capture(player);
            helper.assertTrue(packet.target() != null && !packet.target().scanned() && !packet.target().aspects().isEmpty(),
                    "Original hover composition was missing or the unknown target was reported as scanned");
            var expected = AspectRegistry.getAspects(new ItemStack(Items.COAL));
            for (var aspect : packet.target().aspects()) helper.assertTrue(
                    expected.getAmount(thaumcraft.api.aspects.Aspect.getAspect(aspect.tag())) == aspect.amount(),
                    "Unknown target hover did not use the authoritative per-item composition");
            helper.assertTrue(!player.getCooldowns().isOnCooldown(ScanningModule.THAUMOMETER.get()), "Hover started scan cooldown");
        }
        helper.assertTrue(before.equals(KnowledgeStore.get(player).save()), "Hover granted aspects, scans, observations or research");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualScanPublishesKnownCompositionWithoutHoverGivingFurtherCredit(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL, 64));
        ((ThaumometerItem) ScanningModule.THAUMOMETER.get()).scan(player, InteractionHand.MAIN_HAND);
        var knowledge = KnowledgeStore.get(player);
        helper.assertTrue(knowledge.scanCount() == 1, "Actual scan did not credit its server-owned target");
        CompoundTag before = knowledge.save();
        var snapshot = ScanningNetwork.capture(player);
        helper.assertTrue(snapshot.target() != null && snapshot.target().scanned(), "HUD failed to reflect an actually scanned target");
        var expected = AspectRegistry.getAspects(new ItemStack(Items.COAL));
        helper.assertTrue(snapshot.target().aspects().size() == expected.size(), "Known target lost its aspect entries");
        for (var amount : snapshot.target().aspects()) {
            helper.assertTrue(expected.getAmount(thaumcraft.api.aspects.Aspect.getAspect(amount.tag())) == amount.amount(),
                    "HUD returned incorrect per-item composition for coal");
        }
        player.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        for (int i = 0; i < 10; i++) ScanningNetwork.capture(player);
        helper.assertTrue(before.equals(knowledge.save()) && !player.getCooldowns().isOnCooldown(ScanningModule.THAUMOMETER.get()),
                "Hovering a known target awarded further knowledge or applied scan cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void auraSnapshotReadsCurrentPlayerChunkAndNeverDrainsIt(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL));
        BlockPos current = player.blockPosition();
        float visBefore = AuraManager.getVis(helper.getLevel(), current);
        float fluxBefore = AuraManager.getFlux(helper.getLevel(), current);
        int base = AuraManager.getAuraBase(helper.getLevel(), current);
        // Place a different amount in another loaded chunk; aiming/held targets
        // cannot change which chunk supplies the gauge.
        BlockPos other = current.offset(32, 0, 0);
        helper.getLevel().getChunkAt(other);
        AuraManager.addFlux(helper.getLevel(), other, 123);
        var packet = ScanningNetwork.capture(player);
        helper.assertTrue(packet.dimension().equals(helper.getLevel().dimension().location())
                        && packet.tick() == helper.getLevel().getGameTime() && packet.base() == base
                        && packet.vis() == visBefore && packet.flux() == fluxBefore,
                "Aura packet read another chunk/dimension or changed its values");
        helper.assertTrue(!new ChunkPos(current).equals(new ChunkPos(other))
                        && AuraManager.getVis(helper.getLevel(), current) == visBefore
                        && AuraManager.getFlux(helper.getLevel(), current) == fluxBefore,
                "HUD capture drained or polluted the current player's aura");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heldHandAndOppositeStackSelectionWorkInBothHands(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COAL));
        helper.assertTrue(ThaumometerItem.heldHand(player) == InteractionHand.MAIN_HAND
                        && ThaumometerItem.locateTarget(player, InteractionHand.MAIN_HAND).kind() == ThaumometerItem.TargetKind.HELD_ITEM
                        && ThaumometerItem.findTarget(player, InteractionHand.MAIN_HAND).key().startsWith("item:minecraft:coal"),
                "Main-hand scanner ignored the opposite server inventory stack");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.REDSTONE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        helper.assertTrue(ThaumometerItem.heldHand(player) == InteractionHand.OFF_HAND
                        && ThaumometerItem.locateTarget(player, InteractionHand.OFF_HAND).kind() == ThaumometerItem.TargetKind.HELD_ITEM
                        && ThaumometerItem.findTarget(player, InteractionHand.OFF_HAND).key().startsWith("item:minecraft:redstone"),
                "Offhand scanner did not select the opposite main-hand stack");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        helper.assertTrue(ThaumometerItem.heldHand(player) == InteractionHand.MAIN_HAND, "Two scanners changed deterministic main-hand preference");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(ThaumometerItem.heldHand(player) == null && ScanningNetwork.capture(player).target() == null,
                "HUD retained a target with no held scanner");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void serverRayMetadataRespectsObstructionNearestEntityAndRange(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(false);
        BlockPos start = player.blockPosition();
        for (int z = 1; z <= 11; z++) helper.getLevel().setBlockAndUpdate(start.above().south(z), Blocks.AIR.defaultBlockState());
        ItemEntity entity = new ItemEntity(helper.getLevel(), start.getX() + .5, player.getEyeY() - .1, start.getZ() + 4.5, new ItemStack(Items.COAL));
        entity.setNoGravity(true);
        helper.getLevel().addFreshEntity(entity);
        BlockPos wall = start.above().south(2);
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        var obstructed = ThaumometerItem.locateTarget(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(obstructed != null && obstructed.kind() == ThaumometerItem.TargetKind.BLOCK
                        && wall.equals(obstructed.blockPos()) && obstructed.face() == Direction.NORTH,
                "Server metadata selected an entity behind the blocking face");
        helper.assertTrue(ThaumometerItem.findTarget(player, InteractionHand.MAIN_HAND).location().equals(obstructed),
                "HUD geometry and actual scan resolved different obstructed targets");
        helper.getLevel().removeBlock(wall, false);
        var visible = ThaumometerItem.locateTarget(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(visible != null && visible.kind() == ThaumometerItem.TargetKind.ENTITY && visible.entityId() == entity.getId(),
                "Server metadata did not select the nearest unobstructed entity");
        helper.assertTrue(ThaumometerItem.findTarget(player, InteractionHand.MAIN_HAND).location().equals(visible),
                "HUD metadata and actual scan resolved different unobstructed entities");
        entity.setPos(start.getX() + .5, player.getEyeY() - .1, start.getZ() + ThaumometerItem.SCAN_RANGE + 2);
        helper.assertTrue(ThaumometerItem.locateTarget(player, InteractionHand.MAIN_HAND) == null,
                "Server hover ray acquired an entity beyond the scan range");
        entity.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotAndScanResultWireRoundTripsPreserveBoundedPayload(GameTestHelper helper) {
        var location = new ThaumometerItem.TargetLocation(ThaumometerItem.TargetKind.BLOCK, -1,
                new BlockPos(3, 125, 9), Direction.NORTH, new Vec3(3.5, 125.25, 9));
        var target = new ScanningNetwork.Target(location, List.of(new ScanningNetwork.AspectAmount("aer", 10)), true, Component.literal("Coal test"));
        var packet = new ScanningNetwork.Snapshot(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), 1234, 300, 174.5f, 82.25f, target);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ScanningNetwork.Snapshot.encode(packet, buffer);
            var copy = ScanningNetwork.Snapshot.decode(buffer);
            helper.assertTrue(copy.equals(packet) && buffer.readableBytes() == 0, "Snapshot wire round-trip lost values or left bytes unread");
            var result = new ScanningNetwork.ScanResult(packet.dimension(), target, true);
            ScanningNetwork.ScanResult.encode(result, buffer);
            helper.assertTrue(ScanningNetwork.ScanResult.decode(buffer).equals(result) && buffer.readableBytes() == 0,
                    "Scan-result wire round-trip lost discovery status/location/aspects");
            var empty = new ScanningNetwork.Snapshot(packet.dimension(), 1235, 0, 0, 0, null);
            ScanningNetwork.Snapshot.encode(empty, buffer);
            helper.assertTrue(ScanningNetwork.Snapshot.decode(buffer).equals(empty), "Empty target failed snapshot round-trip");
            assertRejected(helper, () -> new ScanningNetwork.AspectAmount("unknown_aspect", 10), "unknown aspect tag");
            assertRejected(helper, () -> new ScanningNetwork.AspectAmount("aer", -1), "negative amount");
            assertRejected(helper, () -> new ScanningNetwork.Snapshot(packet.dimension(), 0, 300, Float.NaN, 0, null), "nonfinite aura");
            assertRejected(helper, () -> new ScanningNetwork.Target(location,
                    List.of(new ScanningNetwork.AspectAmount("aer", 10), new ScanningNetwork.AspectAmount("aer", 20)), true, Component.empty()),
                    "duplicate aspect entries");
            for (int count : new int[]{-1, 129}) {
                buffer.clear();
                buffer.writeEnum(ThaumometerItem.TargetKind.HELD_ITEM);
                buffer.writeVarInt(-1);
                buffer.writeEnum(Direction.UP);
                buffer.writeDouble(0); buffer.writeDouble(1); buffer.writeDouble(0);
                buffer.writeBoolean(false);
                buffer.writeComponent(Component.empty());
                buffer.writeVarInt(count);
                assertRejected(helper, () -> ScanningNetwork.Target.decode(buffer), "out-of-bounds decoded aspect count=" + count);
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, Runnable attempt, String field) {
        boolean rejected = false;
        try { attempt.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Network contract accepted " + field);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ThaumHudTest"));
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 0));
        player.setPos(start.getX() + .5, start.getY(), start.getZ() + .5);
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        return player;
    }
}
