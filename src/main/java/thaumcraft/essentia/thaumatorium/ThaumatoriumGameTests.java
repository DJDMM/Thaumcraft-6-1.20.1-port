package thaumcraft.essentia.thaumatorium;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.alchemy.*;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.production.AlembicBlockEntity;
import thaumcraft.research.*;
import java.util.*;
import java.util.function.Consumer;

/** Real working tiles, suction peers, inventories, original blueprint and modern event boundaries. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ThaumatoriumGameTests {
    private static final String TEMPLATE="essentia_network";
    private static final BlockPos CENTER=new BlockPos(4,2,4);
    private ThaumatoriumGameTests() {}
    private static ResourceLocation id(String path) {return ResourceLocation.fromNamespaceAndPath("thaumcraft",path);}
    private static Item item(String path) {return ForgeRegistries.ITEMS.getValue(id(path));}
    private static ServerPlayer player(GameTestHelper helper) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"thaumatorium_qa"));var pos=helper.absolutePos(CENTER);player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+2);return player;
    }
    private static void stage(ServerPlayer player,String key,int stage) {
        try {var setter=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);setter.setAccessible(true);setter.invoke(KnowledgeStore.get(player),key,stage);}catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
    private static void complete(ServerPlayer player,String key) {stage(player,key,ResearchCatalog.get(key).stages().size()+1);}
    private static ThaumatoriumBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(CENTER.below(2),Blocks.MAGMA_BLOCK);helper.setBlock(CENTER.below(),AlchemyModule.CRUCIBLE.get());
        helper.setBlock(CENTER,CatalogBlocks.block("thaumatorium").defaultBlockState().setValue(ThaumatoriumBlock.FACING,Direction.NORTH));
        helper.setBlock(CENTER.above(),CatalogBlocks.block("thaumatorium_top").defaultBlockState().setValue(ThaumatoriumBlock.FACING,Direction.NORTH));
        return (ThaumatoriumBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }
    private static void tick(GameTestHelper helper,ThaumatoriumBlockEntity tile,int ticks) {for(int index=0;index<ticks;index++)ThaumatoriumBlockEntity.tick(helper.getLevel(),tile.getBlockPos(),tile.getBlockState(),tile);}
    private static void selectBrass(GameTestHelper helper,ThaumatoriumBlockEntity tile,ServerPlayer player) {stage(player,"METALLURGY",1);tile.setItem(0,new ItemStack(Items.IRON_INGOT,2));helper.assertTrue(tile.toggleRecipe(player,id("brass")),"Actual known crucible recipe was not selectable");}
    private static AlembicBlockEntity source(GameTestHelper helper,BlockPos relative,Aspect aspect,int amount) {helper.setBlock(relative,CatalogBlocks.block("alembic"));var source=(AlembicBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(relative));source.addToContainer(aspect,amount);return source;}
    private static List<ItemEntity> outputs(GameTestHelper helper,ThaumatoriumBlockEntity tile,Item item) {return helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(tile.getBlockPos()).inflate(2),entity->entity.getItem().is(item));}

    @GameTest(template=TEMPLATE)
    public static void unknownAndIncompleteResearchCannotSelectButOriginalStageGateCan(GameTestHelper helper) {
        var tile=machine(helper);var player=player(helper);tile.setItem(0,new ItemStack(Items.COAL));var before=tile.saveWithoutMetadata();
        helper.assertTrue(!tile.toggleRecipe(player,id("alumentum"))&&before.equals(tile.saveWithoutMetadata()),"Unknown recipe was selected or mutated state");
        stage(player,"ALUMENTUM",1);helper.assertTrue(!tile.toggleRecipe(player,id("alumentum")),"Bare strict discovery gate accepted an unfinished research");
        complete(player,"ALUMENTUM");helper.assertTrue(tile.toggleRecipe(player,id("alumentum"))&&tile.selectedRecipes().equals(List.of(id("alumentum"))),"Completed original recipe did not select");
        tile.toggleRecipe(player,id("alumentum"));tile.setItem(0,new ItemStack(Items.IRON_INGOT));stage(player,"METALLURGY",1);
        helper.assertTrue(tile.toggleRecipe(player,id("brass")),"@1 means entered stage1, not completion");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void originalFifthTickDrawCompletesOnlyFollowingPassAndEmitsOneOutput(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));var source=source(helper,CENTER.west(),Aspect.TOOL,5);
        tick(helper,tile,4);helper.assertTrue(source.containerContains(Aspect.TOOL)==5&&tile.getAspects().visSize()==0,"Draw happened before fifth tick");
        tick(helper,tile,1);helper.assertTrue(source.containerContains(Aspect.TOOL)==4&&tile.containerContains(Aspect.TOOL)==1&&tile.getSuctionType(null)==Aspect.TOOL&&tile.getSuctionAmount(null)==128,"Fifth tick did not draw exactly one typed unit");
        tick(helper,tile,20);helper.assertTrue(source.containerContains(Aspect.TOOL)==0&&tile.containerContains(Aspect.TOOL)==5&&tile.getItem(0).getCount()==2,"Craft completed on final fill pass instead of following pass");
        tick(helper,tile,5);var output=outputs(helper,tile,item("ingot_brass"));
        helper.assertTrue(tile.getItem(0).getCount()==1&&tile.getAspects().visSize()==0&&tile.currentCraft()==-1&&output.size()==1&&output.get(0).getItem().getCount()==1&&output.get(0).getDeltaMovement().z<0,"Original consume/reset/front ejection changed");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void upperHalfSharesTypedInputInventoryAndNeverExportsMixture(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));tick(helper,tile,5);
        var top=(ThaumatoriumTopBlockEntity)helper.getLevel().getBlockEntity(tile.getBlockPos().above());
        helper.assertTrue(top.base()==tile&&top.getItem(0)==tile.getItem(0)&&top.getSuctionAmount(Direction.UP)==128,"Top did not delegate to physical base");
        helper.assertTrue(top.addEssentia(Aspect.TOOL,9,Direction.WEST)==5&&tile.containerContains(Aspect.TOOL)==5&&top.addEssentia(Aspect.FIRE,1,Direction.WEST)==0,"Top input was not bounded to selected recipe");
        for(Direction side:Direction.values())helper.assertTrue(!top.canOutputTo(side)&&top.getEssentiaAmount(side)==0&&top.takeEssentia(Aspect.TOOL,1,side)==0&&top.canInputFrom(side)==(side!=Direction.NORTH),"Original face/API contract changed");
        helper.assertTrue(top.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(()->new AssertionError("Top inventory cap")).extractItem(0,1,false).is(Items.IRON_INGOT)&&tile.getItem(0).getCount()==1,"Top hopper did not access shared catalyst slot");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void topPeerIsDrawnWhileFrontDownAndBaseUpAreSkipped(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));var allowed=source(helper,CENTER.above().east(),Aspect.TOOL,1);var front=source(helper,CENTER.north(),Aspect.TOOL,1);
        tick(helper,tile,5);helper.assertTrue(tile.containerContains(Aspect.TOOL)==1&&allowed.containerContains(Aspect.TOOL)==0&&front.containerContains(Aspect.TOOL)==1,"Two-height fill traversal ignored original exclusions");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void redstoneAtEitherHalfOrCruciblePausesCatalystAndEssentia(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));var source=source(helper,CENTER.west(),Aspect.TOOL,10);
        for(BlockPos position:List.of(CENTER.east(),CENTER.above().east(),CENTER.below().east())) {
            helper.setBlock(position,Blocks.REDSTONE_BLOCK);tick(helper,tile,10);helper.assertTrue(tile.gettingPower()&&source.containerContains(Aspect.TOOL)==10&&tile.containerContains(Aspect.TOOL)==0&&tile.getItem(0).getCount()==2,"Redstone did not pause one of three machine positions");helper.setBlock(position,Blocks.AIR);
        }
        tick(helper,tile,5);helper.assertTrue(source.containerContains(Aspect.TOOL)==9,"Unpowered machine did not resume");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void heatRefreshesEvery40TicksAndWaterIsNotConsumed(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));var crucible=(CrucibleBlockEntity)helper.getLevel().getBlockEntity(tile.getBlockPos().below());crucible.fillWater();
        helper.setBlock(CENTER.below(2),Blocks.AIR);tick(helper,tile,10);helper.assertTrue(!tile.heated()&&tile.getSuctionAmount(null)==0,"Cold machine pulled essentia");
        helper.setBlock(CENTER.below(2),Blocks.MAGMA_BLOCK);tick(helper,tile,30);helper.assertTrue(!tile.heated(),"Heat was refreshed before original40 boundary");tick(helper,tile,5);
        helper.assertTrue(tile.heated()&&tile.getSuctionType(null)==Aspect.TOOL&&crucible.water()==1000,"Fortieth refresh or water-independent automation changed");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void outputGoesIntoFrontChestAndOccupiedChestOverflowsOnce(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));helper.setBlock(CENTER.north(),Blocks.CHEST);var chest=(ChestBlockEntity)helper.getLevel().getBlockEntity(tile.getBlockPos().north());
        tick(helper,tile,5);tile.addToContainer(Aspect.TOOL,5);tick(helper,tile,5);
        helper.assertTrue(chest.getItem(0).is(item("ingot_brass"))&&chest.getItem(0).getCount()==1&&outputs(helper,tile,chest.getItem(0).getItem()).isEmpty(),"Result did not enter front inventory first");
        for(int slot=0;slot<chest.getContainerSize();slot++)chest.setItem(slot,new ItemStack(Items.STONE,64));tick(helper,tile,5);tile.addToContainer(Aspect.TOOL,5);tick(helper,tile,5);
        helper.assertTrue(tile.isEmpty()&&outputs(helper,tile,item("ingot_brass")).size()==1,"Full chest did not eject exactly one paid overflow");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void recipeCompletesWithoutOwnerOnlineAndClearsAllBufferedAspects(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));tick(helper,tile,5);tile.setAspects(new AspectList().add(Aspect.TOOL,8).add(Aspect.FIRE,17));tick(helper,tile,5);
        helper.assertTrue(tile.getItem(0).getCount()==1&&tile.getAspects().visSize()==0&&outputs(helper,tile,item("ingot_brass")).size()==1,"Offline owner blocked machine or surplus was preserved contrary to BETA26");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void brainCapacityCountsFacingAttachmentsAndTrimsParallelMetadataTogether(GameTestHelper helper) {
        var tile=machine(helper);var player=player(helper);complete(player,"BASEALCHEMY");tile.setItem(0,ThaumatoriumBlockEntity.recipe(id("vis_crystal_aer")).catalyst().getItems()[0].copy());
        helper.assertTrue(tile.toggleRecipe(player,id("vis_crystal_aer"))&&!tile.toggleRecipe(player,id("vis_crystal_ignis")),"Base capacity is not one selected recipe");
        helper.setBlock(CENTER.east(),CatalogBlocks.block("brain_box").defaultBlockState().setValue(BrainBoxBlock.FACING,Direction.WEST));tile.getUpgrades();
        // Independent release oracle: 1.12 Block.setResistance multiplies3; its getter divides5.
        var positions=List.of(CENTER,CENTER.above(),CENTER.east());float[] hardness={2,2,1},resistance={12,12,6};
        var explosion=new net.minecraft.world.level.ExplosionDamageCalculator();
        for(int index=0;index<positions.size();index++) {
            var pos=helper.absolutePos(positions.get(index));var state=helper.getLevel().getBlockState(pos);
            helper.assertTrue(!state.requiresCorrectToolForDrops()&&state.getDestroySpeed(helper.getLevel(),pos)==hardness[index]&&state.getBlock().getExplosionResistance()==resistance[index]
                    &&explosion.getBlockExplosionResistance(null,helper.getLevel(),pos,state,state.getFluidState()).orElseThrow()==resistance[index],
                    "Original hardness/effective blast resistance changed for "+state.getBlock());
        }
        helper.assertTrue(tile.maxRecipes()==3&&tile.toggleRecipe(player,id("vis_crystal_ignis"))&&tile.toggleRecipe(player,id("vis_crystal_aqua")),"Facing upgrade failed to add exactly two recipes");
        helper.setBlock(CENTER.west(),CatalogBlocks.block("brain_box").defaultBlockState().setValue(BrainBoxBlock.FACING,Direction.WEST));tile.getUpgrades();helper.assertTrue(tile.maxRecipes()==3,"Away-facing upgrade counted");
        helper.setBlock(CENTER.east(),Blocks.AIR);tile.getUpgrades();var saved=tile.saveWithoutMetadata();
        helper.assertTrue(tile.maxRecipes()==1&&tile.selectedRecipes().equals(List.of(id("vis_crystal_aer")))&&saved.getList("OutputPlayer",Tag.TAG_STRING).size()==1,"Upgrade removal misaligned selected costs/owners");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void selectingNewCatalystRecipePreservesAlreadyPaidEssentiaAndNBT(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));tick(helper,tile,5);tile.addToContainer(Aspect.TOOL,3);
        var saved=tile.saveWithoutMetadata();var restored=new ThaumatoriumBlockEntity(tile.getBlockPos(),tile.getBlockState());restored.load(saved);
        helper.assertTrue(restored.getItem(0).getCount()==2&&restored.containerContains(Aspect.TOOL)==3&&restored.selectedRecipes().equals(List.of(id("brass")))&&restored.currentCraft()==-1&&restored.getSuctionAmount(null)==0,"Saved catalyst/essentia/selection or nonpersisted active state changed");
        var player=player(helper);helper.assertTrue(tile.toggleRecipe(player,id("brass"))&&tile.containerContains(Aspect.TOOL)==3,"Deselection incorrectly discarded paid essentia");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void outputSpawnVetoPersistsPaidResultWithoutRecraftingOrSecondDebit(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));tick(helper,tile,5);tile.addToContainer(Aspect.TOOL,5);Item result=item("ingot_brass");
        Consumer<EntityJoinLevelEvent> veto=event->{if(event.getLevel()==helper.getLevel()&&event.getEntity() instanceof ItemEntity entity&&entity.getItem().is(result)&&entity.position().distanceToSqr(tile.getBlockPos().getCenter())<4)event.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(veto);
        try {tick(helper,tile,15);helper.assertTrue(tile.getItem(0).getCount()==1&&tile.getAspects().visSize()==0&&tile.pendingOutput().getCount()==1&&outputs(helper,tile,result).isEmpty(),"Canceled ejection lost/duplicated output or debited catalyst again");}
        finally {MinecraftForge.EVENT_BUS.unregister(veto);}
        var saved=tile.saveWithoutMetadata();tile.load(saved);tick(helper,tile,5);helper.assertTrue(tile.pendingOutput().isEmpty()&&tile.getItem(0).getCount()==1&&outputs(helper,tile,result).size()==1,"Paid overflow did not survive save and eject once");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void selectionRequiresPhysicalMenuRevisionAndNearLivingPlayer(GameTestHelper helper) {
        var tile=machine(helper);var player=player(helper);stage(player,"METALLURGY",1);tile.setItem(0,new ItemStack(Items.IRON_INGOT));var menu=new ThaumatoriumMenu(7,player.getInventory(),tile);
        helper.assertTrue(!menu.select(player,0,id("brass")),"Packet without actual open menu succeeded");player.containerMenu=menu;
        helper.assertTrue(!menu.select(player,1,id("brass"))&&menu.select(player,0,id("brass")),"Stale menu revision or valid original recipe handling changed");
        player.setPos(tile.getBlockPos().getX()+20,tile.getBlockPos().getY(),tile.getBlockPos().getZ());helper.assertTrue(!menu.select(player,0,id("brass")),"Distant selection packet removed recipe");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void salisFormsEitherConstructRetainsCrucibleAndForgeVetoRollsBack(GameTestHelper helper) {
        helper.setBlock(CENTER.below(),AlchemyModule.CRUCIBLE.get());var crucible=(CrucibleBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER.below()));crucible.fillWater();
        helper.setBlock(CENTER,CatalogBlocks.block("metal_alchemical"));helper.setBlock(CENTER.above(),CatalogBlocks.block("metal_alchemical"));var player=player(helper);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));
        BlockPos upper=helper.absolutePos(CENTER.above());var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(upper.getCenter(),Direction.UP,upper,false));
        helper.assertTrue(ThaumatoriumFormation.use(context)==net.minecraft.world.InteractionResult.FAIL&&player.getMainHandItem().getCount()==2,"Unknown research formed machine");stage(player,"THAUMATORIUM",1);
        Consumer<BlockEvent.EntityMultiPlaceEvent> veto=event->{if(event.getEntity()==player)event.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(veto);
        try {helper.assertTrue(ThaumatoriumFormation.use(context)==net.minecraft.world.InteractionResult.FAIL,"Canceled machine ritual committed");}finally {MinecraftForge.EVENT_BUS.unregister(veto);}
        helper.assertTrue(player.getMainHandItem().getCount()==2&&helper.getLevel().getBlockState(upper).is(CatalogBlocks.block("metal_alchemical"))&&crucible.water()==1000,"Ritual veto consumed dust/source crucible");
        helper.assertTrue(player.getMainHandItem().getItem().onItemUseFirst(player.getMainHandItem(),context).consumesAction()&&player.getMainHandItem().getCount()==1&&helper.getLevel().getBlockEntity(helper.absolutePos(CENTER)) instanceof ThaumatoriumBlockEntity&&helper.getLevel().getBlockEntity(upper) instanceof ThaumatoriumTopBlockEntity&&crucible.water()==1000,"Real Salis dispatcher failed to form original halves");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void destroyingUpperRevertsLowerAndRemovingCrucibleRevertsBothOnce(GameTestHelper helper) {
        var tile=machine(helper);tile.setItem(0,new ItemStack(Items.DIAMOND,3));var player=player(helper);player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        helper.assertTrue(player.getMainHandItem().isEmpty()&&player.hasCorrectToolForDrops(tile.getBlockState())
                &&player.gameMode.destroyBlock(tile.getBlockPos().above()),"Empty-hand survival destruction refused original top harvest");
        var diamonds=helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(tile.getBlockPos()).inflate(2),entity->entity.getItem().is(Items.DIAMOND));
        Item construct=CatalogBlocks.block("metal_alchemical").asItem();
        helper.assertTrue(helper.getLevel().getBlockState(tile.getBlockPos()).is(CatalogBlocks.block("metal_alchemical"))&&diamonds.stream().mapToInt(entity->entity.getItem().getCount()).sum()==3
                &&outputs(helper,tile,construct).stream().mapToInt(entity->entity.getItem().getCount()).sum()==1,"Top destruction lost/duplicated catalyst/block loot or left base intact");
        tile=machine(helper);tile.setItem(0,new ItemStack(Items.DIAMOND,3));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        helper.assertTrue(player.hasCorrectToolForDrops(tile.getBlockState())&&player.gameMode.destroyBlock(tile.getBlockPos()),"Non-tool stick suppressed original base harvest");
        helper.assertTrue(outputs(helper,tile,Items.DIAMOND).stream().mapToInt(entity->entity.getItem().getCount()).sum()==6
                &&outputs(helper,tile,construct).stream().mapToInt(entity->entity.getItem().getCount()).sum()==2,"Base harvest lost/duplicated catalyst or construct output");
        tile=machine(helper);helper.setBlock(CENTER.east(),CatalogBlocks.block("brain_box").defaultBlockState().setValue(BrainBoxBlock.FACING,Direction.WEST));player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        helper.assertTrue(player.gameMode.destroyBlock(tile.getBlockPos().east())&&outputs(helper,tile,CatalogBlocks.block("brain_box").asItem()).stream().mapToInt(entity->entity.getItem().getCount()).sum()==1,"Empty-hand Brain Box harvest lost/duplicated original item");
        tile=machine(helper);helper.setBlock(CENTER.below(),Blocks.AIR);
        helper.assertTrue(helper.getLevel().getBlockState(tile.getBlockPos()).is(CatalogBlocks.block("metal_alchemical"))&&helper.getLevel().getBlockState(tile.getBlockPos().above()).is(CatalogBlocks.block("metal_alchemical")),"Broken crucible left machine halves running");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void detachedUpperStopsAccessingRemovedBaseAndMalformedInputIsBounded(GameTestHelper helper) {
        var tile=machine(helper);selectBrass(helper,tile,player(helper));tick(helper,tile,5);var top=(ThaumatoriumTopBlockEntity)helper.getLevel().getBlockEntity(tile.getBlockPos().above());
        helper.assertTrue(tile.addToContainer(null,4)==4&&tile.addToContainer(Aspect.TOOL,-5)==0&&tile.addEssentia(Aspect.TOOL,1,Direction.NORTH)==0,"Malformed/off-face input mutated machine");
        CompoundTag saved=tile.saveWithoutMetadata();saved.putByte("maxrec",(byte)-1);tile.load(saved);helper.assertTrue(tile.maxRecipes()==1&&tile.getSuctionAmount(null)==0,"Corrupt capacity or transient state was trusted");
        helper.setBlock(CENTER,Blocks.AIR);helper.assertTrue(top.base()==null&&top.getItem(0).isEmpty()&&top.addEssentia(Aspect.TOOL,1,Direction.WEST)==0,"Detached upper used a stale base reference");helper.succeed();
    }
}
