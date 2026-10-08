package thaumcraft.essentia.transfuser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.airborne.AirborneEssentiaManager;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.essentia.transport.TubeBlockEntity;
import thaumcraft.essentia.transport.TubeBufferBlockEntity;

/** Registered blocks and real stored units; refusal/reentry peers retain ordinary tube NBT. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EssentiaTransfuserGameTests {
    private static final String TEMPLATE = "essentia_production";
    private static final BlockPos CENTER = new BlockPos(6, 4, 6);
    private EssentiaTransfuserGameTests() {}

    private static EssentiaTransfuserBlockEntity device(GameTestHelper h, boolean filling, Direction front) {
        var pos = h.absolutePos(CENTER);
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var state = CatalogBlocks.block(filling ? "essentia_input" : "essentia_output").defaultBlockState()
                .setValue(EssentiaTransfuserBlock.FACING, front);
        h.getLevel().setBlockAndUpdate(pos, state);
        h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof EssentiaTransfuserBlockEntity, "Device retained a catalogue block/entity");
        var result = (EssentiaTransfuserBlockEntity) h.getLevel().getBlockEntity(pos);
        AirborneEssentiaManager.forgetConsumer(result);
        return result;
    }
    private static void tick(GameTestHelper h, EssentiaTransfuserBlockEntity tile, int count) {
        for (int i = 0; i < count; i++) EssentiaTransfuserBlockEntity.tick(h.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }
    private static EssentiaJarBlockEntity jar(GameTestHelper h, BlockPos pos, Aspect type, int amount) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, CatalogBlocks.block("jar_normal").defaultBlockState());
        var jar = (EssentiaJarBlockEntity) h.getLevel().getBlockEntity(pos);
        if (amount > 0) h.assertTrue(jar.addExact(type, amount), "Jar fixture refused its stored units");
        return jar;
    }
    private static TubeBlockEntity tube(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, CatalogBlocks.block("tube").defaultBlockState());
        return (TubeBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static Peer peer(GameTestHelper h, EssentiaTransfuserBlockEntity tile, int amount, boolean demand) {
        var pos = tile.getBlockPos().relative(tile.facing().getOpposite());
        var original = tube(h, pos);
        var peer = new Peer(pos, original.getBlockState(), tile, amount, demand);
        h.getLevel().getChunkAt(pos).addAndRegisterBlockEntity(peer);
        return peer;
    }

    @GameTest(template = TEMPLATE)
    public static void originalPortsAndStatelessApiForAllSixFacings(GameTestHelper h) {
        for (boolean filling : new boolean[]{true, false}) {
            var tile = device(h, filling, Direction.NORTH);
            for (Direction front : Direction.values()) {
                h.getLevel().setBlock(tile.getBlockPos(), tile.getBlockState().setValue(EssentiaTransfuserBlock.FACING, front), 3);
                for (Direction face : Direction.values()) {
                    boolean rear = face == front.getOpposite();
                    h.assertTrue(tile.isConnectable(face) == rear && tile.canInputFrom(face) == (filling && rear)
                            && tile.canOutputTo(face) == (!filling && rear), "Unexpected port on " + filling + "/" + front + "/" + face);
                    h.assertTrue(tile.getEssentiaAmount(face) == 0 && tile.getEssentiaType(face) == null
                            && tile.getSuctionAmount(face) == (filling ? 128 : 0) && tile.getSuctionType(face) == null,
                            "Invented public storage/suction type");
                }
                tile.setSuction(Aspect.AIR, 512);
                h.assertTrue(tile.addEssentia(Aspect.AIR, 7, front) == 7 && tile.addEssentia(null, 3, null) == 3
                        && tile.addEssentia(Aspect.AIR, 0, null) == 0 && tile.addEssentia(Aspect.AIR, -1, null) == 0
                        && tile.takeEssentia(Aspect.AIR, 1, front.getOpposite()) == 0 && tile.getMinimumSuction() == 0
                        && !tile.isConnectable(null) && tile.getEssentiaAmount(null) == 0, "Original echo API or positive-unit hardening changed");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void originalShapeSupportHardnessAndSixFaceCycle(GameTestHelper h) {
        var tile = device(h, true, Direction.DOWN);
        var block = (EssentiaTransfuserBlock) tile.getBlockState().getBlock();
        var state = tile.getBlockState();
        h.assertTrue(block.defaultBlockState().getValue(EssentiaTransfuserBlock.FACING) == Direction.DOWN, "Ignored BETA26 withProperty call became a different default");
        for (Direction expected : Direction.values()) {
            h.assertTrue(state.getValue(EssentiaTransfuserBlock.FACING) == expected, "Six-face rotation changed EnumFacing order");
            var body = block.getShape(state, h.getLevel(), tile.getBlockPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()).bounds();
            double coordinate = switch (expected.getAxis()) {
                case X -> expected.getStepX() > 0 ? body.minX : body.maxX;
                case Y -> expected.getStepY() > 0 ? body.minY : body.maxY;
                case Z -> expected.getStepZ() > 0 ? body.minZ : body.maxZ;
            };
            h.assertTrue(coordinate == (expected.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1),
                    "Body no longer attaches to the rear face for " + expected);
            for (Direction face : Direction.values()) h.assertTrue(!state.isFaceSturdy(h.getLevel(), tile.getBlockPos(), face), "Original UNDEFINED support became sturdy");
            state = EssentiaTransfuserBlock.cycleFacing(state);
        }
        h.assertTrue(state.getValue(EssentiaTransfuserBlock.FACING) == Direction.DOWN
                        && state.getDestroySpeed(h.getLevel(), tile.getBlockPos()) == 1 && block.getExplosionResistance() == 6
                        && !state.requiresCorrectToolForDrops(), "Original physical properties/hand harvesting changed");
        var north = state.setValue(EssentiaTransfuserBlock.FACING, Direction.NORTH);
        h.assertTrue(block.rotate(north, Rotation.CLOCKWISE_90).getValue(EssentiaTransfuserBlock.FACING) == Direction.EAST
                        && block.mirror(north, Mirror.LEFT_RIGHT).getValue(EssentiaTransfuserBlock.FACING) == Direction.SOUTH,
                "Structure rotation/mirroring lost device front");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fillingTakesOneRealAlembicUnitOnlyOnFifthTick(GameTestHelper h) {
        var tile = device(h, true, Direction.UP);
        var rear = tile.getBlockPos().below();
        h.getLevel().setBlockAndUpdate(rear, CatalogBlocks.block("alembic").defaultBlockState());
        var source = (AlembicBlockEntity) h.getLevel().getBlockEntity(rear);
        h.assertTrue(source.addExact(Aspect.AIR, 3), "Alembic fixture failed");
        var target = jar(h, tile.getBlockPos().above(2), null, 0);
        tick(h, tile, 4);
        h.assertTrue(source.amount() == 3 && target.amount() == 0, "Pump acted before its five-tick interval");
        tick(h, tile, 1);
        h.assertTrue(source.amount() == 2 && target.aspect() == Aspect.AIR && target.amount() == 1, "Filling did not conserve one actual source unit");
        tick(h, tile, 5);
        h.assertTrue(source.amount() == 1 && target.amount() == 2 && tile.getEssentiaAmount(null) == 0, "Filling invented a buffer, lost or doubled the next unit");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptyingCreditsRealDemandJarAfterFifthTick(GameTestHelper h) {
        var tile = device(h, false, Direction.UP);
        var target = jar(h, tile.getBlockPos().below(), null, 0);
        var label = target.saveWithoutMetadata(); label.putString("AspectFilter", Aspect.AIR.getTag()); target.load(label);
        var source = jar(h, tile.getBlockPos().above(2), Aspect.AIR, 3);
        tick(h, tile, 4);
        h.assertTrue(source.amount() == 3 && target.amount() == 0, "Emptying acted before its fifth tick");
        tick(h, tile, 1);
        h.assertTrue(source.amount() == 2 && target.amount() == 1 && target.aspect() == Aspect.AIR, "Emptying failed its typed one-unit transfer");
        tick(h, tile, 5);
        h.assertTrue(source.amount() == 1 && target.amount() == 2, "Confirmed drain lost or doubled a unit");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bothTransfersWorkWithRealTubePeersInAllSixDirections(GameTestHelper h) {
        for (boolean filling : new boolean[]{true, false}) for (Direction front : Direction.values()) {
            var tile = device(h, filling, front);
            var peerPos = tile.getBlockPos().relative(front.getOpposite());
            var storagePos = tile.getBlockPos().relative(front, 2);
            var peer = tube(h, peerPos);
            var storage = jar(h, storagePos, filling ? null : Aspect.AIR, filling ? 0 : 2);
            if (filling) h.assertTrue(peer.addEssentia(Aspect.AIR, 1, front) == 1, "Real rear tube refused unit");
            else peer.setSuction(Aspect.AIR, 64);
            tick(h, tile, 5);
            h.assertTrue(peer.getEssentiaAmount(front) == (filling ? 0 : 1) && storage.amount() == 1,
                    "Directional physical transfer failed for " + filling + "/" + front);
            h.getLevel().setBlockAndUpdate(peerPos, Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(storagePos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fillingRequiresLowSuctionAndAnOpenRealRearTube(GameTestHelper h) {
        var tile = device(h, true, Direction.UP);
        var peer = tube(h, tile.getBlockPos().below());
        h.assertTrue(peer.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Tube source fixture failed");
        var storage = jar(h, tile.getBlockPos().above(2), null, 0);
        peer.setSuction(Aspect.AIR, 128); tick(h, tile, 5);
        peer.setSuction(Aspect.AIR, 129); tick(h, tile, 5);
        peer.setSuction(Aspect.AIR, 127); peer.toggleSide(Direction.UP); tick(h, tile, 5);
        h.assertTrue(peer.getEssentiaAmount(Direction.UP) == 1 && storage.amount() == 0, "High suction or closed rear tube was debited");
        peer.toggleSide(Direction.UP); tick(h, tile, 5);
        h.assertTrue(peer.getEssentiaAmount(Direction.UP) == 0 && storage.amount() == 1, "Valid suction127/native rear tube failed");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptyingRequiresTypedPositiveDemandAndRearInput(GameTestHelper h) {
        var tile = device(h, false, Direction.UP);
        var peer = tube(h, tile.getBlockPos().below());
        var source = jar(h, tile.getBlockPos().above(2), Aspect.AIR, 3);
        peer.setSuction(Aspect.AIR, 0); tick(h, tile, 5);
        peer.setSuction(null, 64); tick(h, tile, 5);
        peer.setSuction(Aspect.AIR, 64); peer.toggleSide(Direction.UP); tick(h, tile, 5);
        h.assertTrue(source.amount() == 3 && peer.getEssentiaAmount(Direction.UP) == 0, "Untyped/nonpositive/closed demand drained a jar");
        peer.toggleSide(Direction.UP); tick(h, tile, 5);
        h.assertTrue(source.amount() == 2 && peer.getEssentiaAmount(Direction.UP) == 1, "Valid demand did not resume its exact transfer");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noAirDestinationLeavesPhysicalRearSourceUntouched(GameTestHelper h) {
        var tile = device(h, true, Direction.UP);
        var source = tube(h, tile.getBlockPos().below());
        h.assertTrue(source.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Real source refused its fixture unit");
        var wrong = jar(h, tile.getBlockPos().above(2), Aspect.FIRE, 2);
        tick(h, tile, 5);
        h.assertTrue(source.getEssentiaAmount(Direction.UP) == 1 && wrong.amount() == 2 && wrong.aspect() == Aspect.FIRE,
                "Unavailable air destination consumed the source or overwrote its aspect");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void refusedSourceCannotCreateEssentiaInAirJar(GameTestHelper h) {
        var tile = device(h, true, Direction.UP);
        var rear = tile.getBlockPos().below();
        h.getLevel().setBlockAndUpdate(rear, CatalogBlocks.block("tube_buffer").defaultBlockState());
        var source = (TubeBufferBlockEntity) h.getLevel().getBlockEntity(rear);
        h.assertTrue(source.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Native buffer source fixture failed");
        var competitor = tube(h, rear.west()); competitor.setSuction(Aspect.AIR, 256);
        var target = jar(h, tile.getBlockPos().above(2), null, 0);
        tick(h, tile, 5);
        h.assertTrue(source.getEssentiaAmount(Direction.UP) == 1 && target.amount() == 0,
                "Rejected real rear debit created air essence");
        h.getLevel().setBlockAndUpdate(competitor.getBlockPos(), Blocks.AIR.defaultBlockState()); tick(h, tile, 5);
        h.assertTrue(source.getEssentiaAmount(Direction.UP) == 0 && target.amount() == 1,
                "Refused source prevented the next valid transaction or leaked a reservation");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void refusedDemandCannotConsumeAirJarEssentia(GameTestHelper h) {
        var tile = device(h, false, Direction.UP);
        var target = tube(h, tile.getBlockPos().below());
        h.assertTrue(target.addEssentia(Aspect.FIRE, 1, Direction.UP) == 1, "Full native target fixture failed");
        target.setSuction(Aspect.AIR, 64);
        var source = jar(h, tile.getBlockPos().above(2), Aspect.AIR, 3);
        tick(h, tile, 5);
        h.assertTrue(source.amount() == 3 && target.getEssentiaAmount(Direction.UP) == 1 && target.getEssentiaType(Direction.UP) == Aspect.FIRE,
                "Refused rear target still consumed an air unit");
        h.assertTrue(target.takeEssentia(Aspect.FIRE, 1, Direction.UP) == 1, "Could not clear the native blocked target");
        tick(h, tile, 5);
        h.assertTrue(source.amount() == 2 && target.getEssentiaAmount(Direction.UP) == 1 && target.getEssentiaType(Direction.UP) == Aspect.AIR,
                "Failed confirmation leaked state or a retry unit");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void uncontractedPeerAndQueryReentryCannotPumpOrAdvanceTickCounter(GameTestHelper h) {
        for (boolean filling : new boolean[]{true, false}) {
            var tile = device(h, filling, Direction.UP);
            var peer = peer(h, tile, filling ? 1 : 0, !filling); peer.reenter = true;
            var storage = jar(h, tile.getBlockPos().above(2), filling ? null : Aspect.AIR, filling ? 0 : 3);
            tick(h, tile, 5);
            h.assertTrue(peer.units() == (filling ? 1 : 0) && storage.amount() == (filling ? 0 : 3)
                    && peer.takeCalls == 0 && peer.addCalls == 0, "Uncontracted callback peer entered a paid native transfer");
            var nativePeer = tube(h, peer.getBlockPos());
            if (filling) h.assertTrue(nativePeer.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Native replacement source fixture failed");
            else nativePeer.setSuction(Aspect.AIR, 64);
            tick(h, tile, 4);
            h.assertTrue(storage.amount() == (filling ? 0 : 3), "Query reentry changed the five-tick cadence");
            tick(h, tile, 1);
            h.assertTrue(nativePeer.getEssentiaAmount(Direction.UP) == (filling ? 0 : 1) && storage.amount() == (filling ? 1 : 2),
                    "Next ordinary pump lost its one-unit cadence");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void reloadDiscardsTransientCountAndForgedStoredEssence(GameTestHelper h) {
        var tile = device(h, true, Direction.UP);
        var source = tube(h, tile.getBlockPos().below());
        h.assertTrue(source.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Native source fixture failed");
        var target = jar(h, tile.getBlockPos().above(2), null, 0);
        tick(h, tile, 4);
        CompoundTag tag = tile.saveWithoutMetadata();
        tag.putInt("count", 4); tag.putInt("Amount", 250); tag.putString("Aspect", Aspect.AIR.getTag());
        tile.load(tag);
        tick(h, tile, 4);
        h.assertTrue(source.getEssentiaAmount(Direction.UP) == 1 && target.amount() == 0 && !tile.saveWithoutMetadata().contains("count")
                        && !tile.saveWithoutMetadata().contains("Amount") && tile.getEssentiaAmount(null) == 0,
                "A reload restored forged storage/count or executed an early transfer");
        tick(h, tile, 1);
        h.assertTrue(source.getEssentiaAmount(Direction.UP) == 0 && target.amount() == 1, "Reload lost the next normal five-tick transfer");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void redstoneDoesNotDisableEitherOriginalDevice(GameTestHelper h) {
        for (boolean filling : new boolean[]{true, false}) {
            var tile = device(h, filling, Direction.UP);
            var peer = tube(h, tile.getBlockPos().below());
            if (filling) h.assertTrue(peer.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Native source fixture failed");
            else peer.setSuction(Aspect.AIR, 64);
            var storage = jar(h, tile.getBlockPos().above(2), filling ? null : Aspect.AIR, filling ? 0 : 3);
            h.getLevel().setBlockAndUpdate(tile.getBlockPos().west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(h.getLevel().hasNeighborSignal(tile.getBlockPos()), "Powered-device fixture was not powered");
            tick(h, tile, 5);
            h.assertTrue(peer.getEssentiaAmount(Direction.UP) == (filling ? 0 : 1) && storage.amount() == (filling ? 1 : 2),
                    "An invented redstone gate suppressed the original pump");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void removedOrDetachedConsumerCannotTickItsRetainedRearPeer(GameTestHelper h) {
        var tile = device(h, true, Direction.UP);
        var source = tube(h, tile.getBlockPos().below());
        h.assertTrue(source.addEssentia(Aspect.AIR, 1, Direction.UP) == 1, "Native source fixture failed");
        var target = jar(h, tile.getBlockPos().above(2), null, 0);
        h.getLevel().setBlockAndUpdate(tile.getBlockPos(), Blocks.AIR.defaultBlockState());
        tick(h, tile, 10);
        h.assertTrue(source.getEssentiaAmount(Direction.UP) == 1 && target.amount() == 0, "Detached consumer pumped using a stale block entity reference");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void legacyMissingTilesAcquireLoadedTickerWithoutChangingFacingOrWorkingTile(GameTestHelper h) {
        var tile = device(h, true, Direction.WEST);
        var pos = tile.getBlockPos();
        var state = tile.getBlockState();
        var chunk = h.getLevel().getChunkAt(pos);
        chunk.removeBlockEntity(pos);
        h.assertTrue(chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null, "Missing-anchor fixture recreated its tile prematurely");
        LegacyTransfuserMigration.repair(h.getLevel(), chunk);
        var restored = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        h.assertTrue(restored instanceof EssentiaTransfuserBlockEntity && restored != tile
                        && chunk.getBlockState(pos).equals(state) && ((EssentiaTransfuserBlockEntity) restored).facing() == Direction.WEST,
                "Loaded migration changed the original block/facing or left a missing ticker");
        LegacyTransfuserMigration.repair(h.getLevel(), chunk);
        h.assertTrue(chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == restored, "Migration replaced an already operational tile");
        h.succeed();
    }

    private static final class Peer extends TubeBlockEntity {
        private final EssentiaTransfuserBlockEntity consumer;
        private final boolean demand;
        boolean reenter;
        int suction, takeCalls, addCalls;
        Peer(BlockPos pos, net.minecraft.world.level.block.state.BlockState state, EssentiaTransfuserBlockEntity consumer, int amount, boolean demand) {
            super(pos, state);
            this.consumer = consumer; this.demand = demand;
            essentiaAmount = amount; essentiaType = amount > 0 ? Aspect.AIR : null;
            suction = demand ? 64 : 0;
        }
        int units() { return essentiaAmount; }
        @Override public boolean isConnectable(Direction face) { reenter(); return face == consumer.facing(); }
        @Override public boolean canInputFrom(Direction face) { return demand && face == consumer.facing(); }
        @Override public boolean canOutputTo(Direction face) { return !demand && face == consumer.facing(); }
        @Override public int getSuctionAmount(Direction face) { return suction; }
        @Override public Aspect getSuctionType(Direction face) { return demand ? Aspect.AIR : null; }
        @Override public int getMinimumSuction() { return 0; }
        private void reenter() {
            if (!reenter) return;
            for (int i = 0; i < 5; i++) EssentiaTransfuserBlockEntity.tick(level, consumer.getBlockPos(), consumer.getBlockState(), consumer);
        }
        @Override public int takeEssentia(Aspect type, int amount, Direction face) {
            takeCalls++; reenter();
            return super.takeEssentia(type, amount, face);
        }
        @Override public int addEssentia(Aspect type, int amount, Direction face) {
            addCalls++; reenter();
            return super.addEssentia(type, amount, face);
        }
    }
}
