package thaumcraft.essentia.centrifuge;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import thaumcraft.api.aspects.*;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.*;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.world.aura.AuraManager;
import java.util.concurrent.atomic.AtomicReference;

/** Real BEs, registered tickers, original countdown/oracle, pipe transfer and save migration. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CentrifugeGameTests {
    private static final String TEMPLATE="essentia_network";
    private static final BlockPos CENTER=new BlockPos(4,2,4);
    private CentrifugeGameTests() {}
    private static CentrifugeBlockEntity centrifuge(GameTestHelper helper,BlockPos position) {
        helper.setBlock(position,CatalogBlocks.block("centrifuge"));
        return (CentrifugeBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(position));
    }
    private static void tick(GameTestHelper helper,CentrifugeBlockEntity tile,int amount) {
        for(int i=0;i<amount;i++)CentrifugeBlockEntity.tick(helper.getLevel(),tile.getBlockPos(),tile.getBlockState(),tile);
    }
    private static AlembicBlockEntity source(GameTestHelper helper,BlockPos position,Aspect type,int amount) {
        helper.setBlock(position,CatalogBlocks.block("alembic"));
        var source=(AlembicBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(position));
        source.addToContainer(type,amount); return source;
    }

    @GameTest(template=TEMPLATE)
    public static void originalTransportFacesSuctionAndPrimalRejection(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER);
        helper.assertTrue(tile.getType()==CentrifugeModule.CENTRIFUGE.get()
                &&tile.getBlockState().getRenderShape()==RenderShape.INVISIBLE
                &&tile.getBlockState().getBlock().asItem() instanceof CentrifugeBlockItem,"Centrifuge is still a catalogue anchor or lacks its builtin item renderer");
        for(Direction side:Direction.values())helper.assertTrue(tile.isConnectable(side)==(side==Direction.UP||side==Direction.DOWN)
                &&tile.canInputFrom(side)==(side==Direction.DOWN)&&tile.canOutputTo(side)==(side==Direction.UP)
                &&tile.getSuctionAmount(side)==(side==Direction.DOWN?128:0)&&tile.getSuctionType(side)==null
                &&tile.getMinimumSuction()==0,"Original transport face/suction changed");
        helper.assertTrue(tile.addEssentia(Aspect.AIR,1,Direction.DOWN)==0&&tile.input()==null,"Primal input entered the centrifuge");
        helper.assertTrue(tile.addEssentia(Aspect.MAGIC,4,Direction.NORTH)==1&&tile.input()==Aspect.MAGIC&&tile.processTicks()==39
                &&tile.getSuctionAmount(Direction.DOWN)==64,"Original low-level off-face/single-unit input contract changed");
        tile.setSuction(Aspect.FIRE,999);
        helper.assertTrue(tile.getSuctionType(Direction.DOWN)==null&&tile.getSuctionAmount(Direction.DOWN)==64,"External suction setter changed the device");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void countdownProducesOneRandomImmediateComponentAndNotBoth(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER); tile.addEssentia(Aspect.MAGIC,1,Direction.DOWN);
        tick(helper,tile,38);
        helper.assertTrue(tile.processTicks()==1&&tile.input()==Aspect.MAGIC&&tile.output()==null,"Counter completed before39 active callbacks");
        for(long seed=0;seed<16;seed++) {
            tile=new CentrifugeBlockEntity(tile.getBlockPos(),tile.getBlockState()); tile.setLevel(helper.getLevel());
            tile.addEssentia(Aspect.MAGIC,1,Direction.DOWN);
            java.util.Random oracle=new java.util.Random(seed); helper.getLevel().random.setSeed(seed);
            tick(helper,tile,39);
            helper.assertTrue(tile.input()==null&&tile.output()==Aspect.MAGIC.getComponents()[oracle.nextInt(2)]
                    &&tile.getAspects().visSize()==1,"Seeded BETA26 one-component extraction changed at seed="+seed);
            tick(helper,tile,100);
            helper.assertTrue(tile.getAspects().visSize()==1,"Idle/full output produced a second component");
        }
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void belowDrawOccursOnFifthIdleCallbackAndAlreadyConsumesCountdown(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER); var source=source(helper,CENTER.below(),Aspect.MAGIC,3);
        tick(helper,tile,4);
        helper.assertTrue(tile.input()==null&&source.amount()==3,"Input drew before fifth idle callback");
        tick(helper,tile,1);
        helper.assertTrue(tile.input()==Aspect.MAGIC&&tile.processTicks()==38&&source.amount()==2,"Draw did not debit exactly one or skip same-tick countdown");
        tick(helper,tile,37);
        helper.assertTrue(tile.output()==null&&tile.processTicks()==1,"Draw operation completed too soon");
        tick(helper,tile,1);
        helper.assertTrue(tile.input()==null&&tile.getAspects().visSize()==1&&source.amount()==2,"Original draw conversion/debit changed");
        tick(helper,tile,20);
        helper.assertTrue(source.amount()==2,"Full output pulled a second unit automatically");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void redstonePausesCountdownDrawingAndSuctionWithoutDeletingSlots(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER); var source=source(helper,CENTER.below(),Aspect.MAGIC,2);
        helper.setBlock(CENTER.east(),Blocks.REDSTONE_BLOCK);
        tick(helper,tile,30);
        helper.assertTrue(tile.getSuctionAmount(Direction.DOWN)==0&&tile.input()==null&&source.amount()==2,"Powered centrifuge still draws");
        tile.addEssentia(Aspect.MAGIC,1,Direction.DOWN); // Advisory redstone does not forbid low-level insertion.
        tick(helper,tile,30);
        helper.assertTrue(tile.processTicks()==39&&tile.input()==Aspect.MAGIC&&tile.output()==null,"Redstone failed to pause conversion");
        helper.setBlock(CENTER.east(),Blocks.AIR); tick(helper,tile,39);
        helper.assertTrue(tile.output()!=null&&tile.input()==null&&source.amount()==2,"Unpowering did not resume the same input");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void occupiedOutputWaitsButDoesNotPauseProcessingCountdown(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER);
        helper.assertTrue(tile.addToContainer(Aspect.AIR,4)==3&&tile.addEssentia(Aspect.MAGIC,1,Direction.DOWN)==1,"Two original slots were conflated");
        tick(helper,tile,39);
        helper.assertTrue(tile.output()==Aspect.AIR&&tile.input()==Aspect.MAGIC&&tile.processTicks()==0,"Full output incorrectly stopped the countdown or overwrote itself");
        helper.assertTrue(tile.takeEssentia(Aspect.AIR,1,Direction.DOWN)==0&&tile.takeEssentia(Aspect.AIR,3,Direction.UP)==3
                &&tile.output()==null,"BETA26 output side/single-slot positive-amount API changed");
        tick(helper,tile,1);
        helper.assertTrue(tile.input()==null&&tile.output()!=null,"Empty output did not immediately accept the completed input");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void containerContractAndMalformedInputsCannotMutateAmounts(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER);
        helper.assertTrue(tile.doesContainerAccept(Aspect.FIRE)&&tile.addToContainer(Aspect.AIR,2)==1
                &&tile.addToContainer(Aspect.FIRE,1)==1&&tile.containerContains(Aspect.AIR)==1
                &&tile.doesContainerContainAmount(Aspect.AIR,1)&&!tile.doesContainerContainAmount(Aspect.AIR,2)
                &&tile.doesContainerContain(new AspectList().add(Aspect.AIR,4).add(Aspect.FIRE,1)),"Original one-output aspect container contract changed");
        tile.setAspects(new AspectList().add(Aspect.FIRE,1));
        helper.assertTrue(tile.output()==Aspect.AIR&&!tile.takeFromContainer(new AspectList().add(Aspect.AIR,1)),"Inert/deprecated setters mutated output");
        helper.assertTrue(!tile.takeFromContainer(Aspect.AIR,0)&&!tile.takeFromContainer(Aspect.AIR,-4)
                &&tile.addEssentia(null,1,Direction.DOWN)==0&&tile.addEssentia(Aspect.MAGIC,0,Direction.DOWN)==0
                &&tile.addEssentia(Aspect.MAGIC,-4,Direction.DOWN)==0&&tile.input()==null&&tile.output()==Aspect.AIR
                &&tile.containerContains(null)==0,"Null/nonpositive requests invented or deleted essence");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void savingKeepsSlotsButOriginalUnsavedCountdownCompletesOnReload(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER); tile.addEssentia(Aspect.MAGIC,1,Direction.DOWN); tick(helper,tile,12);
        CompoundTag saved=tile.saveWithoutMetadata();
        helper.assertTrue(saved.getString("aspectIn").equals(Aspect.MAGIC.getTag())&&!saved.contains("process")&&!saved.contains("count")
                &&!saved.contains("rotation")&&tile.processTicks()==27,"Save invented an original transient countdown field");
        var reloaded=new CentrifugeBlockEntity(tile.getBlockPos(),tile.getBlockState()); reloaded.load(saved); reloaded.setLevel(helper.getLevel());
        helper.assertTrue(reloaded.input()==Aspect.MAGIC&&reloaded.processTicks()==0,"Reload persisted an omitted countdown");
        tick(helper,reloaded,1);
        helper.assertTrue(reloaded.output()!=null&&reloaded.input()==null,"Original immediate post-reload completion changed");
        reloaded.addEssentia(Aspect.MAGIC,1,Direction.DOWN);
        var savedBoth=reloaded.saveWithoutMetadata(); var next=new CentrifugeBlockEntity(tile.getBlockPos(),tile.getBlockState()); next.load(savedBoth);
        helper.assertTrue(next.input()==Aspect.MAGIC&&next.output()==reloaded.output()&&next.getUpdatePacket()!=null
                &&next.getUpdateTag().equals(next.getUpdatePacket().getTag()),"Both slots or update packet lost aspect tags");
        CompoundTag malformed=new CompoundTag(); malformed.putString("aspectIn","aer"); malformed.putString("aspectOut","unknown"); next.load(malformed);
        helper.assertTrue(next.input()==null&&next.output()==null,"Malformed/primal saved input remained unprocessable");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE,timeoutTicks=100)
    public static void actualServerTickerAndThreeTubesDeliverOneComponentToJar(GameTestHelper helper) {
        // Source->lower tube->centrifuge->upper tube, then side tube->jar below it.
        var tile=centrifuge(helper,CENTER); var source=source(helper,CENTER.below(2),Aspect.MAGIC,1);
        helper.setBlock(CENTER.below(),CatalogBlocks.block("tube"));
        helper.setBlock(CENTER.above(),CatalogBlocks.block("tube"));
        helper.setBlock(CENTER.above().east(),CatalogBlocks.block("tube"));
        helper.setBlock(CENTER.east(),CatalogBlocks.block("jar_normal"));
        var jar=(EssentiaJarBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER.east()));
        // No filter: either immediate component is accepted, and no client-side fixture mutation.
        helper.runAtTickTime(85,()-> {
            Aspect type=jar.getEssentiaType(Direction.UP);
            helper.assertTrue(source.amount()==0&&tile.input()==null&&tile.output()==null&&jar.getEssentiaAmount(Direction.UP)==1
                    &&(type==Aspect.MAGIC.getComponents()[0]||type==Aspect.MAGIC.getComponents()[1]),"Actual registered tickers/pipes did not carry one random component");
            helper.succeed();
        });
    }

    @GameTest(template=TEMPLATE)
    public static void destructionSpillsOnlyFinishedOutputAndReturnsEmptyBlock(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER); tile.addEssentia(Aspect.MAGIC,1,Direction.DOWN); tile.addToContainer(Aspect.AIR,1);
        float before=AuraManager.getFlux(helper.getLevel(),tile.getBlockPos());
        helper.getLevel().destroyBlock(tile.getBlockPos(),true);
        var drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(tile.getBlockPos()).inflate(1));
        helper.assertTrue(AuraManager.getFlux(helper.getLevel(),tile.getBlockPos())==before+1
                &&drops.stream().filter(e->e.getItem().is(CatalogBlocks.block("centrifuge").asItem())).mapToInt(e->e.getItem().getCount()).sum()==1
                &&drops.stream().filter(e->e.getItem().is(CatalogBlocks.block("centrifuge").asItem())).noneMatch(e->e.getItem().hasTag()),"Destruction spilled pending input, duplicated loot or saved essence in the block item");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void legacyMigrationReplacesOnlyEmptyCatalogueTileOnBoundedEnd(GameTestHelper helper) {
        var tile=centrifuge(helper,CENTER); var active=centrifuge(helper,CENTER.west()); active.addEssentia(Aspect.MAGIC,1,Direction.DOWN);
        var level=helper.getLevel(); var legacy=new CatalogBlockEntity(tile.getBlockPos(),tile.getBlockState());
        var chunk=level.getChunkAt(tile.getBlockPos()); chunk.addAndRegisterBlockEntity(legacy);
        var failure=new AtomicReference<Throwable>();
        Thread worker=new Thread(()-> { try { LegacyCentrifugeMigration.loadCentrifugeChunk(new ChunkEvent.Load(chunk,false)); }
                catch(Throwable thrown){failure.set(thrown);} },"tc6-centrifuge-chunk-worker");
        worker.start(); try { worker.join(5000); } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        helper.assertTrue(!worker.isAlive()&&failure.get()==null&&level.getBlockEntity(legacy.getBlockPos())==legacy,"Worker callback mutated server state or failed");
        float before=AuraManager.getFlux(level,tile.getBlockPos());
        for(int i=0;i<200&&level.getBlockEntity(legacy.getBlockPos())==legacy;i++)
            LegacyCentrifugeMigration.tickCentrifugeMigration(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true));
        BlockEntity replacement=level.getBlockEntity(legacy.getBlockPos());
        helper.assertTrue(replacement instanceof CentrifugeBlockEntity converted&&converted.input()==null&&converted.output()==null
                &&legacy.isRemoved()&&chunk.isUnsaved()&&level.getBlockEntity(active.getBlockPos())==active&&active.input()==Aspect.MAGIC
                &&AuraManager.getFlux(level,tile.getBlockPos())==before,"Migration replaced working contents, invented essence or lost persistence");
        CompoundTag saved=chunk.getBlockEntityNbtForSaving(legacy.getBlockPos());
        helper.assertTrue(saved!=null&&saved.getString("id").equals("thaumcraft:essentia_centrifuge")
                &&BlockEntity.loadStatic(tile.getBlockPos(),tile.getBlockState(),saved) instanceof CentrifugeBlockEntity,"Chunk save restored the visual tile ID");
        helper.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void registeredArcaneRecipeMatchesExactOriginalGridPriceAndCrystals(GameTestHelper helper) {
        var recipe=helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft","arcane/centrifuge")).orElseThrow();
        helper.assertTrue(recipe instanceof ArcaneRecipe,"Original Centrifuge recipe is not an actual arcane recipe");
        var arcane=(ArcaneRecipe)recipe; var grid=new SimpleContainer(15);
        grid.setItem(1,new ItemStack(CatalogBlocks.block("tube"))); grid.setItem(7,new ItemStack(CatalogBlocks.block("tube")));
        grid.setItem(3,CatalogModule.stack("morphic_resonator")); grid.setItem(4,new ItemStack(CatalogBlocks.block("metal_alchemical")));
        grid.setItem(5,CatalogModule.stack("mechanism_simple"));
        helper.assertTrue(arcane.vis()==100&&arcane.research().equals("CENTRIFUGE")&&arcane.crystalCost(4)==1&&arcane.crystalCost(5)==1
                &&arcane.crystalCost(0)==0&&arcane.crystalCost(1)==0&&arcane.crystalCost(2)==0&&arcane.crystalCost(3)==0
                &&arcane.matches(grid,helper.getLevel())&&arcane.getResultItem(helper.getLevel().registryAccess()).is(CatalogBlocks.block("centrifuge").asItem()),"Original recipe grid/vis/Ordo/Perditio gate changed");
        grid.setItem(1,ItemStack.EMPTY); helper.assertTrue(!arcane.matches(grid,helper.getLevel()),"Recipe accepted a missing upper tube");
        helper.succeed();
    }
}
