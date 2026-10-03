package thaumcraft.essentia.production;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** Full 13×10×13 isolation contains tall columns, sideways attachments and all queried drops. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ProductionGameTests {
    private static final BlockPos MACHINE = new BlockPos(6,1,6);
    private ProductionGameTests() {}

    @GameTest(template="essentia_production")
    public static void allSmelterTiersHaveActualInventoriesAndOriginalIntervals(GameTestHelper helper) {
        String[] ids={"smelter_basic","smelter_thaumium","smelter_void"}; int[] intervals={15,10,15};
        for (int tier=0;tier<ids.length;tier++) {
            var smelter=smelter(helper,ids[tier],new BlockPos(3+tier*3,1,6));
            helper.assertTrue(smelter.tier()==tier && smelter.getContainerSize()==2 && smelter.distillationInterval()==intervals[tier]
                    && smelter.getBlockState().getValue(SmelterBlock.FACING)==Direction.NORTH && !smelter.getBlockState().getValue(SmelterBlock.ENABLED),"Wrong actual smelter tier/state/interval");
        }
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void rawInputReservationUsesFull256CapacityBeforeLoss(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE);
        smelter.setItem(0,new ItemStack(Items.DIRT));
        helper.assertTrue(AspectRegistry.getAspects(smelter.getItem(0)).getAmount(Aspect.EARTH)==5,"Original dirt tags changed");
        smelter.setStoredAspects(new AspectList().add(Aspect.AIR,251));
        helper.assertTrue(smelter.canSmelt() && smelter.smeltTime()==10,"A 251+5 exact capacity input was rejected");
        smelter.setStoredAspects(new AspectList().add(Aspect.AIR,252));
        smelter.setItem(1,new ItemStack(Items.COAL,2));
        tick(helper,smelter,15);
        helper.assertTrue(!smelter.canSmelt() && smelter.totalEssentia()==252 && smelter.getItem(0).getCount()==1
                && smelter.getItem(1).getCount()==2 && smelter.burnTime()==0,"Capacity rejection consumed fuel or input");
        smelter.setStoredAspects(new AspectList()); smelter.setItem(0,ItemStack.EMPTY);
        tick(helper,smelter,5);
        helper.assertTrue(smelter.getItem(1).getCount()==2 && smelter.burnTime()==0,"Fuel was consumed with no input");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void beta26PerUnitLossAndFluxPenaltyMatchSeededReleaseNumbers(GameTestHelper helper) {
        int[] normal={78,88,91},flux={47,53,59};
        for (int tier=0;tier<3;tier++) {
            var ordinary=SmelterRules.rollLoss(new AspectList().add(Aspect.AIR,100),tier,RandomSource.create(0));
            var corrupted=SmelterRules.rollLoss(new AspectList().add(Aspect.FLUX,100),tier,RandomSource.create(0));
            helper.assertTrue(ordinary.retained().getAmount(Aspect.AIR)==normal[tier] && ordinary.lost()==100-normal[tier],"Ordinary loss differs from BETA26 seeded oracle tier="+tier);
            helper.assertTrue(corrupted.retained().getAmount(Aspect.FLUX)==flux[tier] && corrupted.lost()==100-flux[tier],"Vitium 0.66 efficiency factor differs from BETA26 tier="+tier);
        }
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void actualFuelCookingAndAlumentumSpeedRemainSeparate(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE);
        smelter.setItem(0,new ItemStack(Items.DIRT)); smelter.setItem(1,new ItemStack(AlchemyModule.ALUMENTUM.get(),2));
        float before=AuraManager.getFlux(helper.getLevel(),smelter.getBlockPos());
        tick(helper,smelter,10);
        helper.assertTrue(smelter.getItem(0).isEmpty() && smelter.getItem(1).getCount()==1 && smelter.currentBurnTime()==4800
                && smelter.burnTime()==4791 && smelter.speedBoost() && smelter.distillationInterval()==12 && smelter.cookTime()==0,"Actual alumentum/fuel/cooking state differs from BETA26");
        helper.assertTrue(smelter.totalEssentia()+Math.round(AuraManager.getFlux(helper.getLevel(),smelter.getBlockPos())-before)==5,"Actual smelting lost or created input aspects");
        helper.assertTrue(SmelterRules.interval(1,true)==8 && SmelterRules.interval(2,true)==12,"Boost must use interval*0.8, including void slower than thaumium");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void bellowsRequireOrientationEnabledStateAndExcludeFront(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE);
        smelter.setItem(0,CatalogModule.aspectStack("phial_filled",Aspect.AIR,100));
        for (Direction side:Direction.Plane.HORIZONTAL) helper.getLevel().setBlock(smelter.getBlockPos().relative(side),CatalogBlocks.block("bellows").defaultBlockState().setValue(SmelterBellowsBlock.FACING,side.getOpposite()),3);
        smelter.checkNeighbours(); helper.assertTrue(smelter.canSmelt() && smelter.smeltTime()==125,"Three non-front bellows must reduce cook time by37.5%");
        BlockPos east=smelter.getBlockPos().east();
        helper.getLevel().setBlock(east,helper.getLevel().getBlockState(east).setValue(SmelterBellowsBlock.FACING,Direction.EAST),3);
        smelter.checkNeighbours(); helper.assertTrue(smelter.canSmelt() && smelter.smeltTime()==150,"Outward bellows incorrectly accelerated smelting");
        BlockPos west=smelter.getBlockPos().west();
        helper.getLevel().setBlock(west,helper.getLevel().getBlockState(west).setValue(SmelterBellowsBlock.ENABLED,false),3);
        smelter.checkNeighbours(); helper.assertTrue(smelter.canSmelt() && smelter.smeltTime()==175,"Disabled bellows incorrectly accelerated smelting");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void columnFillsExistingAspectBeforeEmptyAndStopsAtGap(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE); var lower=alembic(helper,MACHINE.above()); var upper=alembic(helper,MACHINE.above(2));
        upper.addToContainer(Aspect.AIR,7);
        helper.assertTrue(AlembicBlockEntity.processColumn(helper.getLevel(),smelter.getBlockPos(),Aspect.AIR) && lower.amount()==0 && upper.amount()==8,"Empty lower alembic took priority over matching upper contents");
        helper.assertTrue(AlembicBlockEntity.processColumn(helper.getLevel(),smelter.getBlockPos(),Aspect.FIRE) && lower.aspect()==Aspect.FIRE && lower.amount()==1,"Second pass did not use first empty alembic");
        helper.getLevel().setBlock(upper.getBlockPos(),Blocks.AIR.defaultBlockState(),3); var beyond=alembic(helper,MACHINE.above(3));
        helper.assertTrue(!AlembicBlockEntity.processColumn(helper.getLevel(),smelter.getBlockPos(),Aspect.WATER) && beyond.amount()==0,"Column crossed a non-alembic gap");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void fiveFullAlembicsAndReleaseSixthReceiverRespect128Each(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE);
        for (int height=1;height<=5;height++) {
            var alembic=alembic(helper,MACHINE.above(height)); helper.assertTrue(alembic.addToContainer(Aspect.AIR,130)==2 && alembic.amount()==128,"Alembic capacity differs from128");
        }
        helper.assertTrue(!AlembicBlockEntity.processColumn(helper.getLevel(),smelter.getBlockPos(),Aspect.AIR),"All five full receivers accepted overflow");
        var sixth=alembic(helper,MACHINE.above(6));
        helper.assertTrue(AlembicBlockEntity.processColumn(helper.getLevel(),smelter.getBlockPos(),Aspect.AIR) && sixth.amount()==1,"BETA26 bytecode has no five-height cap; sixth contiguous receiver was rejected");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void pumpsAddIndependentOutputsWhileDistillationNeedsNoBurningFuel(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE); smelter.setStoredAspects(new AspectList().add(Aspect.AIR,8));
        var main=alembic(helper,MACHINE.above()); var output=alembic(helper,MACHINE.east().above());
        helper.getLevel().setBlock(smelter.getBlockPos().east(),CatalogBlocks.block("smelter_aux").defaultBlockState().setValue(SmelterAttachmentBlock.FACING,Direction.WEST),3);
        tick(helper,smelter,15);
        helper.assertTrue(smelter.burnTime()==0 && main.amount()==1 && output.amount()==1 && smelter.totalEssentia()==6,"Main/pump did not independently extract one without burning fuel");
        helper.getLevel().setBlock(smelter.getBlockPos().east(),helper.getLevel().getBlockState(smelter.getBlockPos().east()).setValue(SmelterAttachmentBlock.FACING,Direction.EAST),3);
        tick(helper,smelter,15);
        helper.assertTrue(main.amount()==2 && output.amount()==1 && smelter.totalEssentia()==5,"Outward pump extracted essentia");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void ventsPreventOneThirdOfLossPerWorkingSideAndExcludeFront(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE); BlockPos pos=smelter.getBlockPos();
        smelter.setItem(0,CatalogModule.aspectStack("phial_filled",Aspect.AIR,100));
        helper.getLevel().setBlock(pos.south(),CatalogBlocks.block("smelter_vent").defaultBlockState().setValue(SmelterAttachmentBlock.FACING,Direction.NORTH),3);
        helper.getLevel().setBlock(pos.north(),CatalogBlocks.block("smelter_vent").defaultBlockState().setValue(SmelterAttachmentBlock.FACING,Direction.SOUTH),3);
        java.util.Random oracle=new java.util.Random(0); int lost=0,captured=0;
        for(int i=0;i<100;i++)if(oracle.nextFloat()>.8F)lost++;
        for(int i=0;i<lost;i++)if(oracle.nextFloat()<.333D)captured++;
        float before=AuraManager.getFlux(helper.getLevel(),pos); helper.getLevel().random.setSeed(0);
        helper.assertTrue(smelter.smeltItem() && smelter.totalEssentia()==78 && Math.round(AuraManager.getFlux(helper.getLevel(),pos)-before)==lost-captured,"Vent used extra front trials, wrong probability or lost-unit loop");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void alembicApiEnforcesFilterButRemainsOutputOnlyAndAdvisoryAcceptanceIsBroad(GameTestHelper helper) {
        var alembic=alembic(helper,MACHINE); ServerPlayer player=player(helper);
        helper.assertTrue(alembic.applyLabel(player,Direction.EAST,Aspect.AIR) && alembic.doesContainerAccept(Aspect.FIRE),"Original filter/advisory acceptance contract changed");
        helper.assertTrue(alembic.addToContainer(Aspect.FIRE,10)==10 && alembic.addToContainer(Aspect.AIR,130)==2 && alembic.amount()==128,"Alembic filter/capacity was bypassed");
        for(Direction side:Direction.values()) {
            boolean output=side!=Direction.DOWN && side!=Direction.EAST;
            helper.assertTrue(alembic.canOutputTo(side)==output && alembic.isConnectable(side)==output && !alembic.canInputFrom(side)
                    && alembic.getSuctionAmount(side)==0 && alembic.getMinimumSuction()==0 && alembic.addEssentia(Aspect.AIR,1,side)==0,"Wrong original transport faces/suction");
        }
        helper.assertTrue(alembic.takeEssentia(Aspect.AIR,10,Direction.EAST)==0 && alembic.takeEssentia(Aspect.AIR,10,Direction.NORTH)==10
                && alembic.amount()==118 && !alembic.takeFromContainer(Aspect.AIR,-1),"Invalid/label-side extraction mutated contents");
        alembic.setAspects(new AspectList().add(Aspect.FIRE,1)); helper.assertTrue(alembic.amount()==118 && alembic.aspect()==Aspect.AIR,"Original inert setter changed contents");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void machineStateNbtAndUpdatePacketPreserveTimersTypesInventoryAndFilter(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_void",MACHINE); smelter.setItem(0,new ItemStack(Items.DIRT,2)); smelter.setItem(1,new ItemStack(AlchemyModule.ALUMENTUM.get(),2)); tick(helper,smelter,3);
        smelter.setStoredAspects(new AspectList().add(Aspect.AIR,3).add(Aspect.FIRE,7));
        var loaded=new SmelterBlockEntity(smelter.getBlockPos(),smelter.getBlockState()); loaded.load(smelter.saveWithoutMetadata());
        helper.assertTrue(loaded.burnTime()==smelter.burnTime() && loaded.cookTime()==smelter.cookTime() && loaded.smeltTime()==smelter.smeltTime()
                && loaded.speedBoost() && loaded.totalEssentia()==10 && loaded.storedAspects().getAmount(Aspect.FIRE)==7
                && loaded.getItem(0).getCount()==2 && loaded.getItem(1).getCount()==1,"Smelter saved state changed after reload");
        helper.assertTrue(smelter.getUpdatePacket()!=null && smelter.getUpdateTag().equals(smelter.getUpdatePacket().getTag()),"Smelter client packet/tag differs");
        var alembic=alembic(helper,MACHINE.above()); alembic.addToContainer(Aspect.WATER,74); alembic.applyLabel(player(helper),Direction.WEST,null);
        var second=new AlembicBlockEntity(alembic.getBlockPos(),alembic.getBlockState()); second.load(alembic.saveWithoutMetadata());
        helper.assertTrue(second.amount()==74 && second.aspect()==Aspect.WATER && second.filter()==Aspect.WATER && second.labelFacing()==Direction.WEST
                && alembic.getUpdatePacket()!=null && alembic.getUpdateTag().equals(alembic.getUpdatePacket().getTag()),"Alembic save/client sync lost label, type or amount");
        CompoundTag malformed=new CompoundTag(); malformed.putString("aspect","aer"); malformed.putInt("amount",10000); malformed.putString("AspectFilter","aer"); malformed.putByte("facing",(byte)127); second.load(malformed);
        helper.assertTrue(second.amount()==128 && second.labelFacing()==Direction.DOWN,"Malformed alembic NBT was not bounded");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void actualAutomationUsesSideInputBottomFuelNoTopAndCapabilitiesRevive(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE);
        var top=smelter.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(IllegalStateException::new);
        var bottom=smelter.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow(IllegalStateException::new);
        var side=smelter.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.NORTH).orElseThrow(IllegalStateException::new);
        helper.assertTrue(top.getSlots()==0 && bottom.getSlots()==1 && side.getSlots()==1,"Original automation slot maps changed");
        helper.assertTrue(bottom.insertItem(0,new ItemStack(Items.DIRT),false).getCount()==1 && bottom.insertItem(0,new ItemStack(Items.COAL,2),false).isEmpty()
                && side.insertItem(0,new ItemStack(Items.DIRT,3),false).isEmpty() && smelter.getItem(0).getCount()==3 && smelter.getItem(1).getCount()==2,"Sided item automation bypassed slot validation");
        helper.assertTrue(bottom.extractItem(0,1,false).is(Items.COAL) && side.extractItem(0,1,false).is(Items.DIRT),"Original extraction access changed");
        var old=smelter.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN); smelter.invalidateCaps(); helper.assertTrue(!old.isPresent(),"Removed device retained capability");
        smelter.reviveCaps(); helper.assertTrue(smelter.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).isPresent(),"Revived device lost automation");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void originalManualPhialAndLabelActionsDoNotAllowAlembicRefill(GameTestHelper helper) {
        var alembic=alembic(helper,MACHINE); var player=player(helper); alembic.addToContainer(Aspect.AIR,21);
        player.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("phial_empty")); click(helper,player,alembic,Direction.NORTH);
        helper.assertTrue(alembic.amount()==11 && phialAmount(player,Aspect.AIR)==10,"Alembic phial extraction did not conserve10");
        player.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.aspectStack("phial_filled",Aspect.AIR,10)); click(helper,player,alembic,Direction.NORTH);
        helper.assertTrue(alembic.amount()==11 && player.getMainHandItem().getCount()==1,"Filled phial incorrectly poured into alembic");
        player.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("label_blank")); click(helper,player,alembic,Direction.EAST);
        helper.assertTrue(alembic.filter()==Aspect.AIR && alembic.labelFacing()==Direction.EAST && player.getMainHandItem().isEmpty(),"Label ignored clicked face/existing aspect or was not consumed");
        float flux=AuraManager.getFlux(helper.getLevel(),alembic.getBlockPos()); player.setShiftKeyDown(true); click(helper,player,alembic,Direction.NORTH);
        helper.assertTrue(alembic.amount()==0 && alembic.aspect()==null && alembic.filter()==Aspect.AIR
                && Math.round(AuraManager.getFlux(helper.getLevel(),alembic.getBlockPos())-flux)==11,"Purge removed filter or failed exact pollution");
        click(helper,player,alembic,Direction.EAST);
        helper.assertTrue(alembic.filter()==null && alembic.labelFacing()==Direction.DOWN && dropped(helper,alembic.getBlockPos(),CatalogModule.stack("label_blank").getItem())==1,"Label removal did not return one blank label");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void breakingDevicesDropsEmptyBlocksInventoryAndPollutesStoredEssentiaExactlyOnce(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE); BlockPos pos=smelter.getBlockPos();
        smelter.setStoredAspects(new AspectList().add(Aspect.AIR,21).add(Aspect.FIRE,9)); smelter.setItem(0,new ItemStack(Items.DIRT,3)); smelter.setItem(1,new ItemStack(Items.COAL,2));
        float before=AuraManager.getFlux(helper.getLevel(),pos); helper.getLevel().destroyBlock(pos,true);
        helper.assertTrue(Math.round(AuraManager.getFlux(helper.getLevel(),pos)-before)==30 && dropped(helper,pos,Items.DIRT)==3 && dropped(helper,pos,Items.COAL)==2
                && dropped(helper,pos,CatalogBlocks.block("smelter_basic").asItem())==1,"Smelter break lost items or wrong pollution");
        helper.getLevel().destroyBlock(pos,true); helper.assertTrue(Math.round(AuraManager.getFlux(helper.getLevel(),pos)-before)==30,"Repeated break duplicated flux");
        var alembic=alembic(helper,new BlockPos(6,1,10)); alembic.addToContainer(Aspect.AIR,32); alembic.applyLabel(player(helper),Direction.SOUTH,null);
        before=AuraManager.getFlux(helper.getLevel(),alembic.getBlockPos()); helper.getLevel().destroyBlock(alembic.getBlockPos(),true);
        helper.assertTrue(Math.round(AuraManager.getFlux(helper.getLevel(),alembic.getBlockPos())-before)==32 && dropped(helper,alembic.getBlockPos(),CatalogBlocks.block("alembic").asItem())==1,"Alembic break didn't spill32/drop empty block");
        var items=helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(alembic.getBlockPos()).inflate(1.5));
        helper.assertTrue(items.stream().filter(i->i.getItem().is(CatalogBlocks.block("alembic").asItem())).allMatch(i->!i.getItem().hasTag()),"Alembic break retained already-polluted contents/filter in item");
        helper.succeed();
    }

    @GameTest(template="essentia_production")
    public static void menuShiftTransferPrefersFuelAndServerChecksActualInputAspects(GameTestHelper helper) {
        var smelter=smelter(helper,"smelter_basic",MACHINE); var player=player(helper);
        player.getInventory().setItem(9,new ItemStack(Items.COAL,7)); player.getInventory().setItem(10,new ItemStack(Items.DIRT,5));
        var menu=new SmelterMenu(4,player.getInventory(),smelter,smelter.menuData());
        helper.assertTrue(menu.slots.size()==38 && menu.stillValid(player) && !menu.quickMoveStack(player,2).isEmpty() && smelter.getItem(1).getCount()==7
                && smelter.getItem(0).isEmpty(),"Shift transfer put coal in input before fuel");
        helper.assertTrue(!menu.quickMoveStack(player,3).isEmpty() && smelter.getItem(0).getCount()==5 && !menu.slots.get(0).mayPlace(new ItemStack(Items.BARRIER)),"Server menu input rejected aspects/accepted untagged item");
        helper.assertTrue(menu.scaledEssentia(48)==0 && menu.smeltTime()==100 && !menu.quickMoveStack(player,0).isEmpty() && smelter.getItem(0).isEmpty(),"Menu machine→player transfer/data state wrong");
        helper.succeed();
    }

    private static SmelterBlockEntity smelter(GameTestHelper helper,String id,BlockPos pos) { helper.setBlock(pos,CatalogBlocks.block(id)); return (SmelterBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos)); }
    private static AlembicBlockEntity alembic(GameTestHelper helper,BlockPos pos) { helper.setBlock(pos,CatalogBlocks.block("alembic")); return (AlembicBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos)); }
    private static void tick(GameTestHelper helper,SmelterBlockEntity smelter,int count) { for(int i=0;i<count;i++)SmelterBlockEntity.tick(helper.getLevel(),smelter.getBlockPos(),smelter.getBlockState(),smelter); }
    private static ServerPlayer player(GameTestHelper helper) { var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"ProductionTest")); BlockPos pos=helper.absolutePos(MACHINE); player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); player.getInventory().clearContent(); player.getInventory().selected=0; return player; }
    private static void click(GameTestHelper helper,ServerPlayer player,AlembicBlockEntity alembic,Direction face) { BlockPos pos=alembic.getBlockPos(); helper.getLevel().getBlockState(pos).use(helper.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false)); }
    private static int dropped(GameTestHelper helper,BlockPos pos,Item item) { return helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5)).stream().map(ItemEntity::getItem).filter(i->i.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static int phialAmount(ServerPlayer player,Aspect aspect) { int total=0; for(int i=0;i<player.getInventory().getContainerSize();i++) {ItemStack stack=player.getInventory().getItem(i); if(stack.is(CatalogModule.stack("phial_filled").getItem()) && stack.getItem() instanceof IEssentiaContainerItem container) { var list=container.getAspects(stack); if(list!=null)total+=list.getAmount(aspect)*stack.getCount(); }} return total; }
}
