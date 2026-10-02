package thaumcraft.equipment.cleansing;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchEvents;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CleansingGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new GameProfile(UUID.randomUUID(),"TC6Cleansing"));
        player.connection=new ServerGamePacketListenerImpl(helper.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var pos=helper.absolutePos(new BlockPos(2,2,2));player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);return player;
    }
    private static ItemStack soap(ServerPlayer player,int count) {
        var stack=CatalogModule.stack("sanity_soap");stack.setCount(count);player.setItemInHand(InteractionHand.MAIN_HAND,stack);return stack;
    }
    @GameTest(template="empty") public static void soapReleaseBoundaryAndCreativeConsumptionKeepPermanentWarp(GameTestHelper helper) {
        var player=player(helper);player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        KnowledgeStore.addPermanentWarp(player,49);KnowledgeStore.addNormalWarp(player,10);KnowledgeStore.addTemporaryWarp(player,17);
        var stack=soap(player,2);
        stack.getItem().releaseUsing(stack,helper.getLevel(),player,5);
        helper.assertTrue(stack.getCount()==2 && KnowledgeStore.get(player).normalWarp()==10 && KnowledgeStore.get(player).temporaryWarp()==17,
            "95 ticks incorrectly completed soap");
        stack.getItem().releaseUsing(stack,helper.getLevel(),player,4);
        helper.assertTrue(stack.getCount()==1 && KnowledgeStore.get(player).normalWarp()==9 && KnowledgeStore.get(player).temporaryWarp()==0
            && KnowledgeStore.get(player).permanentWarp()==49,"96-tick soap consumption/warp types changed");
        helper.succeed();
    }
    @GameTest(template="empty") public static void actualHandUseAutoCompletesSoapAndEarlyReleaseIsFree(GameTestHelper helper) {
        var player=player(helper);KnowledgeStore.addNormalWarp(player,4);KnowledgeStore.addTemporaryWarp(player,8);
        var stack=soap(player,2);player.gameMode.useItem(player,helper.getLevel(),stack,InteractionHand.MAIN_HAND);
        for(int i=0;i<40;i++) player.doTick();player.releaseUsingItem();
        helper.assertTrue(stack.getCount()==2 && KnowledgeStore.get(player).temporaryWarp()==8,"Early actual release spent soap");
        player.gameMode.useItem(player,helper.getLevel(),stack,InteractionHand.MAIN_HAND);
        for(int i=0;i<95;i++) player.doTick();
        helper.assertTrue(stack.getCount()==2,"Soap finished before the original >95 threshold");
        for(int i=0;i<5;i++) player.doTick();
        helper.assertTrue(stack.getCount()==1 && !player.isUsingItem() && KnowledgeStore.get(player).normalWarp()==3
            && KnowledgeStore.get(player).temporaryWarp()==0,"Actual hand-use ticks did not auto finish exactly once");
        helper.succeed();
    }
    @GameTest(template="empty") public static void boostedSoapRetainsConfirmedOriginalNormalOverdrawQuirk(GameTestHelper helper) {
        var player=player(helper);var pos=player.blockPosition();
        helper.getLevel().setBlockAndUpdate(pos,CleansingModule.block().defaultBlockState());
        player.addEffect(new MobEffectInstance(CleansingModule.WARP_WARD.get(),400));
        KnowledgeStore.addPermanentWarp(player,21);KnowledgeStore.addNormalWarp(player,5);KnowledgeStore.addTemporaryWarp(player,8);
        CleansingSupport.finishSoap(player,soap(player,1));
        helper.assertTrue(KnowledgeStore.get(player).normalWarp()==2 && KnowledgeStore.get(player).temporaryWarp()==0
            && KnowledgeStore.get(player).permanentWarp()==21,"Ward+fluid removal strength should be three");
        CleansingSupport.finishSoap(player,soap(player,1));
        helper.assertTrue(KnowledgeStore.get(player).normalWarp()==4,"BETA26 normal overdraw should add current=2, not silently clamp to zero");
        helper.succeed();
    }
    @GameTest(template="empty") public static void wardActualCollisionConsumesOnlySourceAndProtectsScheduledDecay(GameTestHelper helper) {
        var player=player(helper);var pos=player.blockPosition();var source=CleansingModule.block().defaultBlockState();
        KnowledgeStore.addPermanentWarp(player,100);KnowledgeStore.addTemporaryWarp(player,7);
        var flowing=source.setValue(LiquidBlock.LEVEL,1);helper.getLevel().setBlockAndUpdate(pos,flowing);
        flowing.entityInside(helper.getLevel(),pos,player);
        helper.assertTrue(!CleansingSupport.isProtected(player) && !helper.getLevel().getBlockState(pos).isAir(),"Flowing fluid awarded Ward");
        helper.getLevel().setBlockAndUpdate(pos,source);source.entityInside(helper.getLevel(),pos,player);
        helper.assertTrue(CleansingSupport.isProtected(player) && helper.getLevel().getBlockState(pos).isAir()
            && player.getEffect(CleansingModule.WARP_WARD.get()).getDuration()==20000,"Actual source collision lost duration or did not consume source");
        helper.getLevel().setBlockAndUpdate(pos,source);source.entityInside(helper.getLevel(),pos,player);
        helper.assertTrue(helper.getLevel().getBlockState(pos).getFluidState().isSource(),"Already protected player consumed another bath");
        helper.assertTrue(!ResearchEvents.decayTemporaryWarp(player) && KnowledgeStore.get(player).temporaryWarp()==7,"Ward did not protect scheduled temporary decay");
        player.removeEffect(CleansingModule.WARP_WARD.get());
        helper.assertTrue(ResearchEvents.decayTemporaryWarp(player) && KnowledgeStore.get(player).temporaryWarp()==6,"Removing Ward did not restore decay");
        helper.succeed();
    }
    @GameTest(template="empty") public static void actualSaltEntityExpiresWholeStackIntoSourceAtTwoHundredTicks(GameTestHelper helper) {
        var pos=helper.absolutePos(new BlockPos(2,2,2));helper.getLevel().setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState());
        var stack=CatalogModule.stack("bath_salts");stack.setCount(7);
        var salt=new ItemEntity(helper.getLevel(),pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5,stack);
        salt.setNoGravity(true);helper.getLevel().addFreshEntity(salt);
        helper.assertTrue(salt.lifespan==200,"Actual salt entity lifespan changed");
        for(int i=0;i<199;i++) { salt.setPos(pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5);salt.setDeltaMovement(Vec3.ZERO);salt.tick(); }
        helper.assertTrue(!salt.isRemoved() && helper.getLevel().getBlockState(pos).is(Blocks.WATER),"Salt expired early");
        salt.setPos(pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5);salt.setDeltaMovement(Vec3.ZERO);salt.tick();
        helper.assertTrue(salt.isRemoved() && CleansingSupport.isPurifyingFluid(helper.getLevel().getBlockState(pos))
            && helper.getLevel().getFluidState(pos).isSource(),"Forge ItemExpire lifecycle did not consume full salt stack and convert water source");
        helper.succeed();
    }
    @GameTest(template="empty") public static void registeredDispenserPlacesPureFluidAndRetainsEmptyBucket(GameTestHelper helper) {
        var pos=helper.absolutePos(new BlockPos(2,2,2));var target=pos.east();
        var state=Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,Direction.EAST);
        helper.getLevel().setBlockAndUpdate(pos,state);helper.getLevel().setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
        var inventory=(net.minecraft.world.level.block.entity.DispenserBlockEntity)helper.getLevel().getBlockEntity(pos);
        inventory.setItem(0,new ItemStack(CleansingModule.PURE_BUCKET.get()));
        state.getBlock().tick(state,helper.getLevel(),pos,helper.getLevel().random);
        helper.assertTrue(helper.getLevel().getFluidState(target).isSource() && helper.getLevel().getFluidState(target).is(CleansingModule.PURE.get())
            && inventory.getItem(0).is(Items.BUCKET),"Actual dispenser used fallback item ejection instead of registered pure-fluid transfer");
        helper.succeed();
    }
    @GameTest(template="empty") public static void bathRejectsFlowingAndWaterloggedBlocksAndBucketTransfersActualFluid(GameTestHelper helper) {
        var player=player(helper);var pos=player.blockPosition().east(2);
        helper.getLevel().setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1));
        helper.assertTrue(!CleansingSupport.convertBath(helper.getLevel(),pos),"Flowing water became a bath");
        var waterlogged=Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true);
        helper.getLevel().setBlockAndUpdate(pos,waterlogged);
        helper.assertTrue(!CleansingSupport.convertBath(helper.getLevel(),pos),"Waterlogged solid became a bath");
        helper.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        var bucket=(net.minecraft.world.item.BucketItem)CleansingModule.PURE_BUCKET.get();
        helper.assertTrue(bucket.emptyContents(player,helper.getLevel(),pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))
            && helper.getLevel().getFluidState(pos).getType()==CleansingModule.PURE.get(),"Bucket did not place actual registered source");
        var result=CleansingModule.block().pickupBlock(helper.getLevel(),pos,helper.getLevel().getBlockState(pos));
        helper.assertTrue(result.is(CleansingModule.PURE_BUCKET.get()) && helper.getLevel().getBlockState(pos).isAir(),"Source pickup did not return actual pure-fluid bucket");
        helper.succeed();
    }
}
