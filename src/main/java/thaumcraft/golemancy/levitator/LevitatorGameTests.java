package thaumcraft.golemancy.levitator;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.aura.*;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class LevitatorGameTests {
    private static final String TEMPLATE="essentia_network";
    private static final BlockPos CENTER=new BlockPos(4,2,4);
    private LevitatorGameTests() {}
    private static LevitatorBlockEntity device(GameTestHelper helper,Direction facing,int energy,int range) {
        helper.setBlock(CENTER,CatalogBlocks.block("levitator").defaultBlockState().setValue(LevitatorBlock.FACING,facing));
        var tile=(LevitatorBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
        var data=new CompoundTag();data.putByte("range",(byte)range);data.putInt("vis",energy);tile.load(data);return tile;
    }
    private static void tick(GameTestHelper helper,LevitatorBlockEntity tile,int times) {for(int i=0;i<times;i++)LevitatorBlockEntity.tick(helper.getLevel(),tile.getBlockPos(),tile.getBlockState(),tile);}
    private static Pig pig(GameTestHelper helper,LevitatorBlockEntity tile,Direction facing,double distance) {
        var pig=EntityType.PIG.create(helper.getLevel());var pos=Vec3.atCenterOf(tile.getBlockPos()).add(facing.getStepX()*distance,facing.getStepY()*distance,facing.getStepZ()*distance);
        pig.setPos(pos.x,pos.y,pos.z);pig.setNoAi(true);pig.setNoGravity(true);helper.getLevel().addFreshEntity(pig);return pig;
    }
    private static FakePlayer player(GameTestHelper helper) {return new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"levitator_qa"));}
    private static void setAura(GameTestHelper helper,BlockPos position,float amount) {
        float current=AuraManager.getVis(helper.getLevel(),position);
        AuraManager.drainVis(helper.getLevel(),position,current,false);
        AuraManager.addVis(helper.getLevel(),position,amount);
    }
    @GameTest(template=TEMPLATE)
    public static void upLiftConsumesSixteenUnitsResetsFallAndClampsMotion(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var pig=pig(helper,tile,Direction.UP,1);pig.setDeltaMovement(.8,.3,-.8);pig.fallDistance=13;
        tick(helper,tile,1);var velocity=pig.getDeltaMovement();helper.assertTrue(tile.actualRange()==1&&tile.energy()==1184&&Math.abs(velocity.x-.3499999940395355)<1e-8&&Math.abs(velocity.y-.3499999940395355)<1e-8&&Math.abs(velocity.z+.3499999940395355)<1e-8&&pig.fallDistance==0&&pig.hurtMarked,"Real upward entity acceleration/cap/debit changed");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void sneakUpDampsDescentWithoutAccelerationOrClampButStillPays(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var pig=pig(helper,tile,Direction.UP,1);pig.setShiftKeyDown(true);pig.setDeltaMovement(.8,-.4,-.8);
        tick(helper,tile,1);var motion=pig.getDeltaMovement();helper.assertTrue(Math.abs(motion.y-(-.4*.8999999761581421))<1e-8&&motion.x==.8&&motion.z==-.8&&tile.energy()==1184,"Sneak branch must only damp negative Y and still charge one cost");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void horizontalAirborneLiftDampsFallAndOffsetsGravity(GameTestHelper helper) {
        var tile=device(helper,Direction.EAST,1200,1);var pig=pig(helper,tile,Direction.EAST,1);pig.setDeltaMovement(0,-.2,0);pig.setOnGround(false);
        tick(helper,tile,1);var velocity=pig.getDeltaMovement();helper.assertTrue(Math.abs(velocity.x-.1F)<1e-8&&Math.abs(velocity.y-(-.2*.8999999761581421+.07999999821186066))<1e-8&&tile.energy()==1184,"Horizontal airborne field no longer cancels vanilla gravity");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void downFieldAcceleratesDownwardAndItemEntitiesAreLiftable(GameTestHelper helper) {
        var tile=device(helper,Direction.DOWN,1200,0);var pos=Vec3.atCenterOf(tile.getBlockPos().below());var item=new ItemEntity(helper.getLevel(),pos.x,pos.y,pos.z,new ItemStack(Items.DIAMOND));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);helper.getLevel().addFreshEntity(item);
        tick(helper,tile,1);helper.assertTrue(Math.abs(item.getDeltaMovement().y+.1F)<1e-8&&tile.energy()==1192&&!item.isRemoved(),"The four-block down beam must move an actual item and debit8");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void notPushableDecorationsDoNotConsumeAndOutsideBeamStaysStill(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var pos=Vec3.atCenterOf(tile.getBlockPos().above());var stand=new ArmorStand(helper.getLevel(),pos.x,pos.y,pos.z);stand.setNoGravity(true);helper.getLevel().addFreshEntity(stand);
        var outside=pig(helper,tile,Direction.UP,1);outside.moveTo(pos.x+2,pos.y,pos.z);outside.setDeltaMovement(Vec3.ZERO);
        tick(helper,tile,1);helper.assertTrue(tile.energy()==1200&&outside.getDeltaMovement().equals(Vec3.ZERO)&&stand.getDeltaMovement().equals(Vec3.ZERO),"Field affected a non-pushable decoration or a creature outside its one-block column");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void originalPositiveRemainderCanPayOneLiftIntoNegativeDebt(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,10,1);var pig=pig(helper,tile,Direction.UP,1);tick(helper,tile,1);
        helper.assertTrue(tile.energy()==-6&&pig.getDeltaMovement().y>0,"Original final partial-buffer lift was incorrectly refused or clamped to0");var saved=tile.saveWithoutMetadata();var restored=new LevitatorBlockEntity(tile.getBlockPos(),tile.getBlockState());restored.load(saved);
        helper.assertTrue(restored.energy()==-6&&restored.actualRange()==0&&restored.rangeIndex()==1,"Saved energy debt or transient counter/range contract changed");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void fractionalAuraRefillConvertsAt1200IncludingWhilePowered(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,0,1);helper.setBlock(CENTER.west(),Blocks.REDSTONE_BLOCK);float before=AuraManager.getVis(helper.getLevel(),tile.getBlockPos());
        try {setAura(helper,tile.getBlockPos(),.25F);tick(helper,tile,1);helper.assertTrue(!tile.getBlockState().getValue(LevitatorBlock.ENABLED)&&tile.energy()==300&&AuraManager.getVis(helper.getLevel(),tile.getBlockPos())==0,"Powered device must still convert its actual fractional local aura into300 buffer units");tick(helper,tile,1);helper.assertTrue(tile.energy()==300&&AuraManager.getVis(helper.getLevel(),tile.getBlockPos())==0,"Refill happened above original10 threshold");}finally {setAura(helper,tile.getBlockPos(),before);}
        helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void redstoneStopsActualMotionAndResumePreservesBuffer(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var pig=pig(helper,tile,Direction.UP,1);helper.setBlock(CENTER.west(),Blocks.REDSTONE_BLOCK);tick(helper,tile,1);
        helper.assertTrue(tile.energy()==1200&&pig.getDeltaMovement().equals(Vec3.ZERO),"Powered beam still moved or charged a creature");helper.setBlock(CENTER.west(),Blocks.AIR);tick(helper,tile,1);helper.assertTrue(tile.energy()==1184&&pig.getDeltaMovement().y>0&&tile.getBlockState().getValue(LevitatorBlock.ENABLED),"Signal removal did not resume the same buffer");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void progressiveScanStopsAtOpaqueObstacleAndRestartsAfterRemoval(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);helper.setBlock(CENTER.above(3),Blocks.STONE);tick(helper,tile,8);helper.assertTrue(tile.actualRange()==2,"Initial opaque obstacle should keep scan below3; BETA26 does not initialize actualRange to the configured maximum");
        helper.setBlock(CENTER.above(3),Blocks.AIR);tick(helper,tile,8);helper.assertTrue(tile.actualRange()==8,"Cleared beam did not scan to8");helper.setBlock(CENTER.above(2),Blocks.STONE);tick(helper,tile,8);helper.assertTrue(tile.actualRange()==2,"Inserted blocker should truncate to the opaque block's distance (original off-by-one)");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void rangeControlCyclesSixteenThirtyTwoFourEightAndPersists(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var player=player(helper);int[] expected={16,32,4,8};for(int range:expected) {tile.increaseRange(player);helper.assertTrue(tile.configuredRange()==range&&tile.getCost()==range*2&&tile.actualRange()==0,"Button range/cost cycle differs fromBETA26");}
        var restored=new LevitatorBlockEntity(tile.getBlockPos(),tile.getBlockState());restored.load(tile.saveWithoutMetadata());helper.assertTrue(restored.configuredRange()==8&&restored.energy()==1200&&restored.actualRange()==0,"Saved range/buffer lost or transient scan restored");
        var malformed=new CompoundTag();malformed.putByte("range",(byte)127);malformed.putInt("vis",Integer.MAX_VALUE);restored.load(malformed);helper.assertTrue(restored.configuredRange()==32&&restored.energy()==1209,"Malformed NBT was not bounded");helper.succeed();
    }
    @GameTest(template=TEMPLATE,timeoutTicks=60)
    public static void actualServerTickMovesCreatureUpward(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var pig=pig(helper,tile,Direction.UP,1);
        // NoAI also suppresses native LivingEntity travel; keep physical movement but remove wandering goals.
        pig.setNoAi(false);pig.goalSelector.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal).toList().forEach(pig.goalSelector::removeGoal);
        pig.targetSelector.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal).toList().forEach(pig.targetSelector::removeGoal);double start=pig.getY();
        helper.runAfterDelay(15,()->{helper.assertTrue(pig.getY()>start+1.5&&tile.energy()<1200&&pig.fallDistance==0,"Native server ticker did not produce actual displacement in the charged field: start="+start+",pos="+pig.position()+",motion="+pig.getDeltaMovement()+",energy="+tile.energy()+",range="+tile.actualRange());helper.succeed();});
    }
    @GameTest(template=TEMPLATE)
    public static void rearButtonActuallyCyclesButBodyClickDoesNot(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var player=player(helper);var pos=tile.getBlockPos();player.setPos(pos.getX()+.5,pos.getY()-2,pos.getZ()+.5);player.setXRot(-90);player.setYRot(0);
        var hit=new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+.0625,pos.getZ()+.5),Direction.DOWN,pos,false);
        var block=(LevitatorBlock)tile.getBlockState().getBlock();helper.assertTrue(LevitatorBlock.targetsButton(tile.getBlockState(),pos,player),"Fixture ray did not reach original small rear button");
        helper.assertTrue(block.use(tile.getBlockState(),helper.getLevel(),pos,player,InteractionHand.MAIN_HAND,hit).consumesAction()&&tile.configuredRange()==16,"Actual rear-button activation did not cycle8->16");
        player.setXRot(0);helper.assertTrue(block.use(tile.getBlockState(),helper.getLevel(),pos,player,InteractionHand.MAIN_HAND,hit)==InteractionResult.PASS&&tile.configuredRange()==16,"Body/forged button hit bypassed server's ray retrace");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void placementFacingUsesOriginalNearEyeHeightRuleAndEachButtonIsOppositeBeam(GameTestHelper helper) {
        var tile=device(helper,Direction.UP,1200,1);var player=player(helper);var pos=tile.getBlockPos();player.setYRot(0);player.setXRot(80);player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        helper.assertTrue(LevitatorBlock.placementFacing(pos,player)==Direction.UP,"Near high placer must face up independently of look pitch");player.setPos(pos.getX()+.5,pos.getY()-2,pos.getZ()+.5);helper.assertTrue(LevitatorBlock.placementFacing(pos,player)==Direction.DOWN,"Near lower eye must face down");player.setPos(pos.getX()+4,pos.getY()+5,pos.getZ()+.5);helper.assertTrue(LevitatorBlock.placementFacing(pos,player)==Direction.NORTH,"Outside2-block horizontal gate must use opposite horizontal facing");
        for(Direction face:Direction.values()) {var button=LevitatorBlock.button(face);var body=LevitatorBlock.body(face);helper.assertTrue(button.getSize()>0&&body.getSize()>0,"An audited face lost geometry");var center=button.getCenter();var delta=center.subtract(new Vec3(.5,.5,.5));helper.assertTrue(delta.dot(new Vec3(face.getStepX(),face.getStepY(),face.getStepZ()))<0,"Button was put on the beam/front face instead of the original rear face");}
        helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void originalLevitatorRecipeRequiresExactGridThirtyFiveVisAndOneAer(GameTestHelper helper) {
        var recipe=(ArcaneRecipe)helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft","arcane/levitator")).orElseThrow();var grid=new SimpleContainer(15);
        int[] wood={0,2,6,8};for(int slot:wood)grid.setItem(slot,new ItemStack(Blocks.OAK_PLANKS));grid.setItem(1,new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","plate_thaumium")))) ;for(int slot:new int[]{3,5})grid.setItem(slot,new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","plate_iron"))));grid.setItem(4,new ItemStack(CatalogBlocks.block("nitor_blue")));grid.setItem(7,new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","mechanism_simple"))));
        helper.assertTrue(recipe.matches(grid,helper.getLevel())&&recipe.vis()==35&&recipe.crystalCost(0)==1&&recipe.research().equals("LEVITATOR")&&recipe.getResultItem(helper.getLevel().registryAccess()).is(CatalogBlocks.block("levitator").asItem()),"Original dictionary grid35vis/Aer1 changed");grid.setItem(1,new ItemStack(Items.IRON_INGOT));helper.assertTrue(!recipe.matches(grid,helper.getLevel()),"Recipe accepted an iron ingot instead of thaumium plate");helper.succeed();
    }
}
