package thaumcraft.infusion;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.blocks.*;
import thaumcraft.world.aura.AuraManager;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class InfusionProtectionGameTests {
    @GameTest(template="empty") public static void unreadyChunksCannotStarveALoadedMigrationOrCauseForcedLoads(GameTestHelper h) {
        var level=h.getLevel();var queue=new thaumcraft.world.LoadedChunkMigrationQueue();
        for(int i=0;i<64;i++)queue.load(level,new net.minecraft.world.level.chunk.LevelChunk(level,new net.minecraft.world.level.ChunkPos(145000+i,145000)));
        var loaded=level.getChunkAt(h.absolutePos(new BlockPos(1,1,1)));queue.load(level,loaded);
        var repaired=new java.util.concurrent.atomic.AtomicInteger();queue.process(level,(world,chunk)->repaired.incrementAndGet());
        h.assertTrue(repaired.get()==0,"First migration callback exceeded64 coordinates");queue.process(level,(world,chunk)->{h.assertTrue(chunk==loaded,"Migration loaded an unready chunk");repaired.incrementAndGet();});
        h.assertTrue(repaired.get()==1,"Unready coordinates starved an already loaded chunk");
        queue.load(level,loaded);queue.unload(level,loaded.getPos());queue.load(level,loaded);
        queue.process(level,(world,chunk)->repaired.incrementAndGet());queue.process(level,(world,chunk)->repaired.incrementAndGet());
        h.assertTrue(repaired.get()==2,"Stale unloaded token repaired the same chunk twice");
        for(int i=0;i<64;i++)h.assertTrue(!level.hasChunkAt(new BlockPos((145000+i)*16,70,145000*16)),"Queue forced a chunk load");h.succeed();
    }
    @GameTest(template="essentia_production") public static void chargeDecaysThroughInlayAndPedestalsAndDisconnectionRemovesProtection(GameTestHelper h) {
        var level=h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(2,2,2));
        for(int x=0;x<=7;x++){level.setBlockAndUpdate(origin.offset(x,-1,0),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(origin.offset(x,0,0),Blocks.AIR.defaultBlockState());}
        level.setBlockAndUpdate(origin,CatalogBlocks.block("stabilizer").defaultBlockState());
        var source=(InfusionStabilizerBlockEntity)level.getBlockEntity(origin);var tag=new CompoundTag();tag.putInt("energy",15);source.load(tag);
        for(int x=1;x<=6;x++)level.setBlockAndUpdate(origin.offset(x,0,0),CatalogBlocks.block(x==3||x==6?"pedestal_arcane":"inlay").defaultBlockState());
        var pedestal=(InfusionPedestalBlockEntity)level.getBlockEntity(origin.offset(6,0,0));pedestal.setItem(0,new ItemStack(Items.DIAMOND));
        settle(h,origin);
        for(int x=1;x<=6;x++)h.assertTrue(level.getBlockState(origin.offset(x,0,0)).getValue(InfusionInlayBlock.CHARGE)==16-x,"Wrong charge decay at"+x);
        h.assertTrue(InfusionInlayBlock.find(level,pedestal.getBlockPos())==source&&source.mitigate(10)&&source.energy()==5,"Connected source did not absorb exact charge");
        settle(h,origin);h.assertTrue(level.getBlockState(pedestal.getBlockPos()).getValue(InfusionInlayBlock.CHARGE)==0,"Overloaded source kept remote pedestal protected");
        tag.putInt("energy",15);source.load(tag);settle(h,origin);level.setBlockAndUpdate(origin.offset(2,0,0),Blocks.AIR.defaultBlockState());settle(h,origin);
        h.assertTrue(InfusionInlayBlock.find(level,pedestal.getBlockPos())==null&&level.getBlockState(pedestal.getBlockPos()).getValue(InfusionInlayBlock.CHARGE)==0
                &&pedestal.getItem(0).is(Items.DIAMOND),"Broken wiring retained protection or lost inventory");h.succeed();
    }
    private static void settle(GameTestHelper h,BlockPos origin){for(int pass=0;pass<32;pass++)for(int x=1;x<=6;x++)InfusionInlayBlock.updateCharge(h.getLevel(),origin.offset(x,0,0));}
    @GameTest(template="empty") public static void stabilizerChargesOncePerSecondPollutesAndRejectsOverdraft(GameTestHelper h) {
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(1,1,1));level.setBlockAndUpdate(pos,CatalogBlocks.block("stabilizer").defaultBlockState());
        var tile=(InfusionStabilizerBlockEntity)level.getBlockEntity(pos);float flux=AuraManager.getFlux(level,pos);
        for(int i=0;i<19;i++)InfusionStabilizerBlockEntity.tick(level,pos,tile.getBlockState(),tile);
        h.assertTrue(tile.energy()==0,"Stabilizer charged before20 ticks");InfusionStabilizerBlockEntity.tick(level,pos,tile.getBlockState(),tile);
        h.assertTrue(tile.energy()==1&&Math.abs(AuraManager.getFlux(level,pos)-flux-.25F)<.0001,"Original charging pollution changed");
        for(int i=0;i<400;i++)InfusionStabilizerBlockEntity.tick(level,pos,tile.getBlockState(),tile);
        h.assertTrue(tile.energy()==15&&!tile.mitigate(16)&&!tile.mitigate(0)&&tile.mitigate(6)&&tile.energy()==9,"Capacity/overdraft changed");
        var copy=new InfusionStabilizerBlockEntity(pos,tile.getBlockState());copy.load(tile.saveWithoutMetadata());h.assertTrue(copy.energy()==9,"Stabilizer energy lost on reload");h.succeed();
    }
    @GameTest(template="essentia_production") public static void equalChargeRejectedBranchDoesNotHideAnAlternateIncreasingPath(GameTestHelper h) {
        var level=h.getLevel();BlockPos start=h.absolutePos(new BlockPos(5,2,5));
        BlockPos[] positions={start,start.south(),start.west(),start.west().south()};int[] charges={1,10,2,3};
        for(var pos:positions){level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,CatalogBlocks.block("inlay").defaultBlockState());}
        BlockPos sourcePos=start.west(2).south();level.setBlockAndUpdate(sourcePos,CatalogBlocks.block("stabilizer").defaultBlockState());
        var source=(InfusionStabilizerBlockEntity)level.getBlockEntity(sourcePos);var tag=new CompoundTag();tag.putInt("energy",10);source.load(tag);
        for(int i=0;i<positions.length;i++)level.setBlock(positions[i],level.getBlockState(positions[i]).setValue(InfusionInlayBlock.CHARGE,charges[i]),2);
        h.assertTrue(InfusionInlayBlock.find(level,start)==source,"Rejected first branch poisoned a valid alternate route");h.succeed();
    }
    @GameTest(template="empty") public static void legacyMatrixMigrationRunsOnlyAtServerEndAndCreatesAnInactiveEmptyPlan(GameTestHelper h) {
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(1,1,1));var state=CatalogBlocks.block("infusion_matrix").defaultBlockState();level.setBlockAndUpdate(pos,state);
        var chunk=level.getChunkAt(pos);var legacy=new CatalogBlockEntity(pos,state);chunk.addAndRegisterBlockEntity(legacy);
        LegacyInfusionMigration.load(new ChunkEvent.Load(chunk,false));LegacyInfusionMigration.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.START,level,()->true));
        h.assertTrue(level.getBlockEntity(pos)==legacy,"Migration mutated during START");
        for(int i=0;i<256&&!(level.getBlockEntity(pos) instanceof InfusionMatrixBlockEntity);i++)LegacyInfusionMigration.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true));
        h.assertTrue(level.getBlockEntity(pos) instanceof InfusionMatrixBlockEntity,"Legacy matrix did not upgrade");var matrix=(InfusionMatrixBlockEntity)level.getBlockEntity(pos);
        h.assertTrue(!matrix.active()&&!matrix.crafting()&&matrix.remainingItems()==0&&matrix.getAspects().size()==0,"Migration invented active operation/payment");h.succeed();
    }
}
