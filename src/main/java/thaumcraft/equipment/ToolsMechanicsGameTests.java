package thaumcraft.equipment;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.equipment.tools.ElementalShovelItem;
import thaumcraft.equipment.tools.ToolItems;
import thaumcraft.research.KnowledgeStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actual registered tool actions, world mutations, Forge vetoes, loot and use/attack hooks. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ToolsMechanicsGameTests {
    private ToolsMechanicsGameTests() {}

    @GameTest(template = "empty")
    public static void primalAreaMiningKeepsTilesAndRespectsEveryBreakVeto(GameTestHelper helper) {
        var fixture = player(helper);
        var player = fixture.player();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        plane(helper, center, Blocks.STONE.defaultBlockState());
        BlockPos tilePos = center.east(), protectedPos = center.west().below();
        helper.getLevel().setBlockAndUpdate(tilePos, Blocks.FURNACE.defaultBlockState());
        var furnace = (FurnaceBlockEntity)helper.getLevel().getBlockEntity(tilePos);
        furnace.setItem(1, new ItemStack(Items.COAL, 4));
        ItemStack tool = tool("primal_crusher"); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        AtomicInteger centralEvents = new AtomicInteger(), vetoes = new AtomicInteger();
        Consumer<BlockEvent.BreakEvent> guard = event -> {
            if (event.getPlayer() != player) return;
            if (event.getPos().equals(center)) centralEvents.incrementAndGet();
            if (event.getPos().equals(protectedPos)) { event.setCanceled(true); vetoes.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, guard);
        // Forge returns false when onBlockStartBreak takes ownership of the action;
        // the custom harvest must still prove its actual world/loot/payment results below.
        try { helper.assertTrue(!player.gameMode.destroyBlock(center), "AOE unexpectedly fell through to a second vanilla harvest"); }
        finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        helper.assertTrue(helper.getLevel().isEmptyBlock(center) && airInPlane(helper, center) == 7,
                "Destructive mining did not remove exactly the seven permitted ordinary blocks");
        helper.assertTrue(helper.getLevel().getBlockEntity(tilePos) == furnace && furnace.getItem(1).getCount() == 4
                && helper.getLevel().getBlockState(protectedPos).is(Blocks.STONE), "AOE destroyed a tile or canceled block");
        helper.assertTrue(centralEvents.get() == 1 && vetoes.get() == 1 && tool.getDamageValue() == 7,
                "AOE repeated the central event, skipped neighbor protection, or charged durability for rejected blocks");
        helper.assertTrue(drops(helper, center, Items.COBBLESTONE) == 7, "Actual AOE loot lost or duplicated cobblestone");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void elementalDestructiveMiningAndShiftBypassUseRealSurvivalBreaks(GameTestHelper helper) {
        var player = player(helper).player();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        plane(helper, center, Blocks.DIRT.defaultBlockState());
        ItemStack tool = tool("elemental_shovel"); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        helper.assertTrue(!player.gameMode.destroyBlock(center) && airInPlane(helper, center) == 9
                && tool.getDamageValue() == 9 && drops(helper, center, Items.DIRT) == 9,
                "Registered elemental shovel did not perform actual nine-block destructive harvesting");
        plane(helper, center, Blocks.DIRT.defaultBlockState());
        player.setShiftKeyDown(true); tool.setDamageValue(0);
        helper.assertTrue(player.gameMode.destroyBlock(center) && airInPlane(helper, center) == 1 && tool.getDamageValue() == 1,
                "Shift did not restrict the actual destructive tool to the central vanilla block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void burrowingAxeHarvestsOneFarLogAndShiftReturnsToClickedLog(GameTestHelper helper) {
        var player = player(helper).player();
        BlockPos root = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int y = 0; y <= 4; y++) helper.getLevel().setBlockAndUpdate(root.above(y), Blocks.OAK_LOG.defaultBlockState());
        ItemStack axe = tool("elemental_axe"); player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        helper.assertTrue(!player.gameMode.destroyBlock(root) && helper.getLevel().isEmptyBlock(root.above(4))
                && helper.getLevel().getBlockState(root).is(Blocks.OAK_LOG), "Burrowing did not harvest the furthest log while preserving the clicked trunk");
        helper.assertTrue(axe.getDamageValue() == 1 && drops(helper, root.above(2), Items.OAK_LOG) == 1,
                "One burrowing click harvested the entire tree or failed ordinary loot/durability");
        player.setShiftKeyDown(true);
        helper.assertTrue(player.gameMode.destroyBlock(root) && helper.getLevel().isEmptyBlock(root)
                && helper.getLevel().getBlockState(root.above()).is(Blocks.OAK_LOG) && axe.getDamageValue() == 2,
                "Shift did not restore ordinary clicked-log harvesting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hoeActualUseRespectsObstructionPlacementVetoAndShiftSingleTill(GameTestHelper helper) {
        var player = player(helper).player();
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 3));
        soil(helper, center);
        BlockPos blocked = center.west(), protectedPos = center.east();
        helper.getLevel().setBlockAndUpdate(blocked.above(), Blocks.STONE.defaultBlockState());
        ItemStack hoe = tool("elemental_hoe"); player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
        AtomicInteger vetoes = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> guard = event -> {
            if (event.getEntity() == player && event.getPos().equals(protectedPos)) { event.setCanceled(true); vetoes.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, guard);
        try { use(player, InteractionHand.MAIN_HAND, center, Direction.UP); }
        finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        helper.assertTrue(farmland(helper, center) == 7 && hoe.getDamageValue() == 7 && vetoes.get() == 1
                && helper.getLevel().getBlockState(protectedPos).is(Blocks.DIRT) && helper.getLevel().getBlockState(blocked).is(Blocks.DIRT),
                "3x3 hoe paid for, or changed, an obstructed/canceled target");
        soil(helper, center); player.setShiftKeyDown(true); hoe.setDamageValue(0);
        use(player, InteractionHand.MAIN_HAND, center, Direction.UP);
        helper.assertTrue(farmland(helper, center) == 1 && helper.getLevel().getBlockState(center).is(Blocks.FARMLAND) && hoe.getDamageValue() == 1,
                "Shift hoe did not use the actual single-block vanilla tilling path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hoeBonemealActuallyGrowsCropAndPaysThreeDurability(GameTestHelper helper) {
        var player = player(helper).player();
        BlockPos crop = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.getLevel().setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
        ItemStack hoe = tool("elemental_hoe"); player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
        CompoundTag before = KnowledgeStore.get(player).save();
        use(player, InteractionHand.MAIN_HAND, crop, Direction.UP);
        helper.assertTrue(helper.getLevel().getBlockState(crop).is(Blocks.WHEAT)
                && helper.getLevel().getBlockState(crop).getValue(CropBlock.AGE) > 0 && hoe.getDamageValue() == 3,
                "Bonemeal fallback did not grow a real crop for three durability");
        helper.assertTrue(before.equals(KnowledgeStore.get(player).save()), "Tool bonemeal awarded research/warp");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shovelCopiesActualStateAndPaysCorrectHandForBothHands(GameTestHelper helper) {
        var player = player(helper).player();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 3));
        BlockState template = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        helper.getLevel().setBlockAndUpdate(origin, template);
        for (InteractionHand hand : InteractionHand.values()) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.getLevel().setBlockAndUpdate(origin.above().offset(x, 0, z), Blocks.AIR.defaultBlockState());
            player.getInventory().clearContent();
            ItemStack shovel = tool("elemental_shovel"); player.setItemInHand(hand, shovel);
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            player.setItemInHand(other, new ItemStack(Items.STICK, 3));
            player.getInventory().setItem(9, new ItemStack(Items.OAK_LOG, 9));
            use(player, hand, origin, Direction.UP);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.assertTrue(helper.getLevel().getBlockState(origin.above().offset(x, 0, z)) == template,
                    "Builder did not preserve copied log axis/state for " + hand);
            helper.assertTrue(shovel.getDamageValue() == 9 && player.getInventory().getItem(9).isEmpty() && player.getItemInHand(other).getCount() == 3,
                    "Builder charged the wrong hand, failed inventory payment or duplicated supplies for " + hand);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shovelModeChangesActualPlaneAndInventoryOrForgeDenialIsAtomic(GameTestHelper helper) {
        var player = player(helper).player();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        ItemStack shovel = tool("elemental_shovel"); player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
        ElementalShovelItem.cycleOrientation(shovel); // Mode 1: vertical along the wall normal.
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 9));
        use(player, InteractionHand.MAIN_HAND, origin, Direction.NORTH);
        for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            BlockPos pos = origin.north().offset(0, y, z);
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.STONE), "Mode 1 did not create its actual vertical plane");
            if (!pos.equals(origin)) helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(shovel.getDamageValue() == 8 && player.getInventory().getItem(9).getCount() == 1,
                "Mode 1 paid for the existing anchor instead of eight new blocks");
        ElementalShovelItem.cycleOrientation(shovel); shovel.setDamageValue(0);
        player.getInventory().setItem(9, new ItemStack(Items.DIRT, 9));
        use(player, InteractionHand.MAIN_HAND, origin, Direction.NORTH);
        helper.assertTrue(shovel.getDamageValue() == 0 && player.getInventory().getItem(9).getCount() == 9, "Missing matching supply created blocks or paid durability");
        player.getInventory().setItem(9, new ItemStack(Items.STONE, 9));
        AtomicInteger vetoes = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> guard = event -> {
            if (event.getEntity() == player) { event.setCanceled(true); vetoes.incrementAndGet(); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, guard);
        try { use(player, InteractionHand.MAIN_HAND, origin, Direction.NORTH); }
        finally { MinecraftForge.EVENT_BUS.unregister(guard); }
        helper.assertTrue(vetoes.get() == 8 && shovel.getDamageValue() == 0 && player.getInventory().getItem(9).getCount() == 9,
                "Canceled building charged supplies/durability or omitted per-position protection");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos pos = origin.north().offset(x, 0, z);
            if (!pos.equals(origin)) helper.assertTrue(helper.getLevel().isEmptyBlock(pos), "Vetoed mode 2 left a ghost block");
        }
        use(player, InteractionHand.MAIN_HAND, origin, Direction.NORTH);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.assertTrue(helper.getLevel().getBlockState(origin.north().offset(x, 0, z)).is(Blocks.STONE),
                "Mode 2 did not create its actual horizontal plane after protection was lifted");
        helper.assertTrue(shovel.getDamageValue() == 8 && player.getInventory().getItem(9).getCount() == 1, "Mode 2 did not pay for eight actual placements");
        ElementalShovelItem.cycleOrientation(shovel);
        helper.assertTrue(ElementalShovelItem.orientation(shovel) == 0, "Three modes did not wrap back to zero");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void axeHeldUseAttractsRealItemWithoutManufacturingPickup(GameTestHelper helper) {
        var player = player(helper).player();
        ItemStack axe = tool("elemental_axe"); player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        ItemStack content = new ItemStack(Items.DIAMOND, 3); content.getOrCreateTag().putString("tc6.magnetTest", UUID.randomUUID().toString());
        ItemEntity drop = new ItemEntity(helper.getLevel(), player.getX() + 2, player.getY(), player.getZ(), content);
        drop.setDeltaMovement(Vec3.ZERO); drop.setNoGravity(true); helper.getLevel().addFreshEntity(drop);
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        player.doTick();
        helper.assertTrue(player.isUsingItem() && drop.getDeltaMovement().x < 0 && Math.abs(drop.getDeltaMovement().x) <= .25
                && Math.abs(drop.getDeltaMovement().y) <= .25 && Math.abs(drop.getDeltaMovement().z) <= .25,
                "Actual held-use tick did not attract the ordinary ItemEntity with bounded velocity");
        helper.assertTrue(drop.isAlive() && ItemStack.isSameItemSameTags(drop.getItem(), content) && drop.getItem().getCount() == 3
                && !player.getInventory().contains(new ItemStack(Items.DIAMOND)) && axe.getDamageValue() == 0,
                "Magnet bypassed normal pickup, changed stack NBT/count or invented a durability cost");
        player.stopUsingItem(); drop.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swordWindMovesActualEntitiesBillsCadenceAndSendsPlayerMotion(GameTestHelper helper) {
        var fixture = player(helper); var player = fixture.player();
        ItemStack sword = tool("elemental_sword"); player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Cow mob = cow(helper, player.position().add(1, 0, 0));
        player.gameMode.useItem(player, helper.getLevel(), sword, InteractionHand.MAIN_HAND);
        player.setDeltaMovement(0, -.6, 0); player.fallDistance = 12;
        fixture.connection().packets.clear();
        sword.getItem().onUseTick(helper.getLevel(), player, sword, 72000);
        helper.assertTrue(player.getDeltaMovement().y > -.6 && player.fallDistance < 12 && sword.getDamageValue() == 1,
                "Wind did not soften actual downward movement/fall distance or bill its initial tick");
        for (int age = 1; age < 20; age++) sword.getItem().onUseTick(helper.getLevel(), player, sword, 72000 - age);
        helper.assertTrue(player.getDeltaMovement().y > 0 && player.getDeltaMovement().y <= .5 && mob.getDeltaMovement().x > 0
                && sword.getDamageValue() == 1, "Sustained wind did not lift/repel or billed more often than twenty ticks");
        sword.getItem().onUseTick(helper.getLevel(), player, sword, 71980);
        helper.assertTrue(sword.getDamageValue() == 2 && fixture.connection().packets.stream().filter(packet -> packet instanceof ClientboundSetEntityMotionPacket).count() == 21,
                "Twentieth wind tick failed durability cadence or actual server motion packets");
        player.stopUsingItem(); mob.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arcingUsesActualAttackEventAndHonorsCanceledAttack(GameTestHelper helper) {
        var player = player(helper).player();
        ItemStack sword = tool("elemental_sword"); player.setItemInHand(InteractionHand.MAIN_HAND, sword); player.doTick();
        Cow primary = cow(helper, player.position().add(0, 0, 1));
        List<Cow> others = List.of(cow(helper, player.position().add(1, 0, 1)), cow(helper, player.position().add(-1, 0, 1)), cow(helper, player.position().add(0, 0, 2)));
        Consumer<AttackEntityEvent> veto = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, veto);
        try { player.attack(primary); }
        finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        helper.assertTrue(primary.getHealth() == 100 && others.stream().allMatch(cow -> cow.getHealth() == 100), "Canceled primary attack still triggered ARCING");
        player.attack(primary);
        helper.assertTrue(primary.getHealth() < 100 && others.stream().filter(cow -> cow.getHealth() < 100).count() == 2,
                "Actual ARCING rank 2 did not hit exactly two additional mobs");
        float expected = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) / 2;
        for (Cow cow : others) if (cow.getHealth() < 100) helper.assertTrue(Math.abs(100 - cow.getHealth() - expected) < .001,
                "Additional ARCING target did not receive half the actual equipped damage");
        primary.discard(); others.forEach(Cow::discard); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soundingActualShiftUsePaysFiveAndSendsAuthoritativeTargetPacket(GameTestHelper helper) {
        var fixture = player(helper); var player = fixture.player();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 3)); helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ItemStack pick = tool("elemental_pick"); player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        CompoundTag knowledge = KnowledgeStore.get(player).save(); fixture.connection().packets.clear();
        use(player, InteractionHand.MAIN_HAND, pos, Direction.UP);
        helper.assertTrue(pick.getDamageValue() == 0 && soundingPackets(fixture).isEmpty(), "Non-sneaking pick paid or emitted SOUNDING");
        player.setShiftKeyDown(true); use(player, InteractionHand.MAIN_HAND, pos, Direction.UP);
        var packets = soundingPackets(fixture);
        helper.assertTrue(pick.getDamageValue() == 5 && packets.size() == 1 && helper.getLevel().getBlockState(pos).is(Blocks.STONE),
                "Actual SOUNDING use did not pay five once while preserving its world target");
        FriendlyByteBuf data = new FriendlyByteBuf(packets.get(0).getData().duplicate());
        helper.assertTrue(data.readUnsignedByte() == 0 && data.readResourceLocation().equals(helper.getLevel().dimension().location())
                && data.readBlockPos().equals(pos) && data.readUnsignedByte() == 2 && !data.isReadable(),
                "Real SOUNDING packet lost dimension, server position or infusion rank 2");
        helper.assertTrue(knowledge.equals(KnowledgeStore.get(player).save()), "SOUNDING awarded scan credits/research/warp");
        helper.succeed();
    }

    private static List<ClientboundCustomPayloadPacket> soundingPackets(Fixture fixture) {
        return fixture.connection().packets.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> (ClientboundCustomPayloadPacket)packet).filter(packet -> packet.getIdentifier().equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", "tools"))).toList();
    }
    private static void use(ServerPlayer player, InteractionHand hand, BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, pos, false);
        player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(hand), hand, hit);
    }
    private static void plane(GameTestHelper helper, BlockPos center, BlockState state) {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) helper.getLevel().setBlockAndUpdate(center.offset(x, y, 0), state);
    }
    private static int airInPlane(GameTestHelper helper, BlockPos center) {
        int count = 0; for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) if (helper.getLevel().isEmptyBlock(center.offset(x, y, 0))) count++; return count;
    }
    private static void soil(GameTestHelper helper, BlockPos center) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            helper.getLevel().setBlockAndUpdate(center.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(center.offset(x, 1, z), Blocks.AIR.defaultBlockState());
        }
    }
    private static int farmland(GameTestHelper helper, BlockPos center) {
        int count = 0; for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (helper.getLevel().getBlockState(center.offset(x, 0, z)).is(Blocks.FARMLAND)) count++; return count;
    }
    private static int drops(GameTestHelper helper, BlockPos center, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(3)).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static ItemStack tool(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
        if (item == null || item == Items.AIR) throw new AssertionError("Missing actual tool " + id);
        return ToolItems.initializeStack(new ItemStack(item));
    }
    private static Cow cow(GameTestHelper helper, Vec3 pos) {
        Cow cow = EntityType.COW.create(helper.getLevel()); if (cow == null) throw new AssertionError("No cow fixture");
        cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(pos); cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); cow.setHealth(100);
        helper.getLevel().addFreshEntity(cow); return cow;
    }
    private static Fixture player(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "TC6ToolTest"));
        RecordingConnection transport = new RecordingConnection();
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), transport, player);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 0)); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.setYRot(0); player.setXRot(0); player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return new Fixture(player, transport);
    }
    private record Fixture(ServerPlayer player, RecordingConnection connection) {}
    private static final class RecordingConnection extends Connection {
        final List<Packet<?>> packets = new ArrayList<>();
        RecordingConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public void send(Packet<?> packet, @Nullable PacketSendListener listener) { packets.add(packet); }
    }
}
