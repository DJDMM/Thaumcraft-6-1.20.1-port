package thaumcraft.artifice.hungrychest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import thaumcraft.arcane.ArcaneRecipe;
import thaumcraft.catalog.blocks.CatalogBlocks;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class HungryChestGameTests {
    private static final String TEMPLATE="essentia_network";
    private static final BlockPos CENTER=new BlockPos(4,2,4);
    private HungryChestGameTests() {}
    private static HungryChestBlockEntity chest(GameTestHelper helper) {
        helper.setBlock(CENTER,CatalogBlocks.block("hungry_chest"));return (HungryChestBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }
    private static ItemEntity item(GameTestHelper helper,HungryChestBlockEntity chest,ItemStack stack) {
        var pos=Vec3.atCenterOf(chest.getBlockPos());var entity=new ItemEntity(helper.getLevel(),pos.x,pos.y+.4,pos.z,stack);entity.setNoGravity(true);entity.setDeltaMovement(Vec3.ZERO);helper.getLevel().addFreshEntity(entity);return entity;
    }
    private static FakePlayer player(GameTestHelper helper,HungryChestBlockEntity tile) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"hungry_chest_qa"));var pos=Vec3.atCenterOf(tile.getBlockPos());player.setPos(pos.x,pos.y,pos.z+2);return player;
    }
    @GameTest(template=TEMPLATE)
    public static void collidingItemIsInsertedIntoRealTwentySevenSlotInventory(GameTestHelper helper) {
        var chest=chest(helper);var item=item(helper,chest,new ItemStack(Items.DIAMOND,5));chest.getBlockState().entityInside(helper.getLevel(),chest.getBlockPos(),item);
        helper.assertTrue(chest.getContainerSize()==27&&chest.getItem(0).is(Items.DIAMOND)&&chest.getItem(0).getCount()==5&&item.isRemoved(),"Physical chest collision did not insert and discard exactly five diamonds");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void partialInsertionKeepsExactTaggedRemainder(GameTestHelper helper) {
        var chest=chest(helper);for(int slot=0;slot<27;slot++)chest.setItem(slot,new ItemStack(Items.COBBLESTONE,64));var source=new ItemStack(Items.DIAMOND,5);source.setHoverName(net.minecraft.network.chat.Component.literal("same tagged item"));var existing=source.copy();existing.setCount(62);chest.setItem(7,existing);var item=item(helper,chest,source);
        chest.consume(item);helper.assertTrue(!item.isRemoved()&&item.getItem().getCount()==3&&ItemStack.isSameItemSameTags(item.getItem(),source)&&chest.getItem(7).getCount()==64,"Partial insertion lost/duplicated NBT or remainder");chest.consume(item);helper.assertTrue(item.getItem().getCount()==3&&chest.getItem(7).getCount()==64,"Repeated full collision duplicated goods");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void fullChestNeverEatsItemsOrMergesDifferentTags(GameTestHelper helper) {
        var chest=chest(helper);for(int slot=0;slot<27;slot++)chest.setItem(slot,new ItemStack(Items.DIAMOND,64));var named=new ItemStack(Items.DIAMOND,3);named.setHoverName(net.minecraft.network.chat.Component.literal("keep me"));chest.getItem(0).setCount(60);var item=item(helper,chest,named);chest.consume(item);
        helper.assertTrue(!item.isRemoved()&&item.getItem().getCount()==3&&chest.getItem(0).getCount()==60&&ItemStack.isSameItemSameTags(item.getItem(),named),"Full/different-NBT chest silently consumed or merged a named item");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void adjacentChestsStayIndependentAndAllFacesExposeSingleInventory(GameTestHelper helper) {
        var chest=chest(helper);helper.setBlock(CENTER.east(),CatalogBlocks.block("hungry_chest"));var other=(HungryChestBlockEntity)helper.getLevel().getBlockEntity(chest.getBlockPos().east());chest.setItem(0,new ItemStack(Items.DIAMOND,2));other.setItem(0,new ItemStack(Items.APPLE,3));
        for(Direction face:Direction.values()) {var handler=chest.getCapability(ForgeCapabilities.ITEM_HANDLER,face).resolve().orElseThrow();helper.assertTrue(handler.getSlots()==27&&handler.getStackInSlot(0).is(Items.DIAMOND)&&handler.extractItem(0,1,true).is(Items.DIAMOND),"Adjacent chest joined or a face lost its independent27 slots");}
        helper.assertTrue(other.getItem(0).getCount()==3&&chest.getItem(0).getCount()==2,"Simulated inventory access mutated contents");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void nativeMenuOpensUnderSolidBlockAndCloseBalancesOpeners(GameTestHelper helper) {
        var chest=chest(helper);helper.setBlock(CENTER.above(),Blocks.STONE);var player=player(helper,chest);var menu=ChestMenu.threeRows(3,player.getInventory(),chest);player.containerMenu=menu;
        helper.assertTrue(menu.getRowCount()==3&&menu.getContainer()==chest&&menu.stillValid(player)&&chest.openerCount()==1,"Native3-row menu did not open under solid cover");menu.removed(player);helper.assertTrue(chest.openerCount()==0,"Menu close failed to balance openers");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void comparatorStorageAndNativeSaveKeepWholeInventory(GameTestHelper helper) {
        var chest=chest(helper);chest.setItem(0,new ItemStack(Items.DIAMOND,32));var named=new ItemStack(Items.IRON_SWORD);named.setDamageValue(37);named.setHoverName(net.minecraft.network.chat.Component.literal("persisted sword"));chest.setItem(26,named);
        int signal=chest.getBlockState().getAnalogOutputSignal(helper.getLevel(),chest.getBlockPos());var restored=new HungryChestBlockEntity(chest.getBlockPos(),chest.getBlockState());restored.load(chest.saveWithoutMetadata());
        helper.assertTrue(signal==1&&restored.getItem(0).getCount()==32&&restored.getItem(26).getDamageValue()==37&&ItemStack.isSameItemSameTags(restored.getItem(26),named),"Comparator or native27-slot NBT persistence differs");helper.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void breakingDropsContentsAndOneChestExactlyOnce(GameTestHelper helper) {
        var chest=chest(helper);chest.setItem(0,new ItemStack(Items.DIAMOND,5));var block=chest.getBlockState().getBlock();var position=chest.getBlockPos();helper.getLevel().destroyBlock(position,true);helper.getLevel().destroyBlock(position,true);
        var drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(position).inflate(1));int diamonds=drops.stream().filter(entity->entity.getItem().is(Items.DIAMOND)).mapToInt(entity->entity.getItem().getCount()).sum();int chests=drops.stream().filter(entity->entity.getItem().is(block.asItem())).mapToInt(entity->entity.getItem().getCount()).sum();
        helper.assertTrue(diamonds==5&&chests==1&&helper.getLevel().getBlockEntity(position)==null,"Chest destruction lost/duplicated stored diamonds or self drop");helper.succeed();
    }
    @GameTest(template=TEMPLATE,timeoutTicks=30)
    public static void naturalEntityTickCollisionEatsDroppedItem(GameTestHelper helper) {
        var chest=chest(helper);var item=item(helper,chest,new ItemStack(Items.APPLE,3));
        helper.runAfterDelay(3,()->{helper.assertTrue(item.isRemoved()&&chest.getItem(0).is(Items.APPLE)&&chest.getItem(0).getCount()==3,"Actual ItemEntity tick did not trigger hungry chest collision");helper.succeed();});
    }
    @GameTest(template=TEMPLATE)
    public static void originalHungryRecipeRequiresSevenGreatwoodAndWoodTrapdoor(GameTestHelper helper) {
        var recipe=(ArcaneRecipe)helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft","arcane/hungry_chest")).orElseThrow();var grid=new SimpleContainer(15);
        for(int slot:new int[]{0,2,3,5,6,7,8})grid.setItem(slot,new ItemStack(CatalogBlocks.block("plank_greatwood")));grid.setItem(1,new ItemStack(Blocks.OAK_TRAPDOOR));
        helper.assertTrue(recipe.matches(grid,helper.getLevel())&&recipe.vis()==15&&recipe.crystalCost(3)==1&&recipe.crystalCost(2)==1&&recipe.research().equals("HUNGRYCHEST"),"BETA26 HungryChest15vis/Terra1/Aqua1 grid changed");grid.setItem(1,new ItemStack(Blocks.IRON_TRAPDOOR));helper.assertTrue(!recipe.matches(grid,helper.getLevel()),"Hungry chest accepted iron trapdoor");grid.setItem(1,new ItemStack(Blocks.OAK_TRAPDOOR));grid.setItem(0,new ItemStack(Blocks.OAK_PLANKS));helper.assertTrue(!recipe.matches(grid,helper.getLevel()),"Hungry chest accepted ordinary planks instead ofGreatwood");helper.succeed();
    }
}
