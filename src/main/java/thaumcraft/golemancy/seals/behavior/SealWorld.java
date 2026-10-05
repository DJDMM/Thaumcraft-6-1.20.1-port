package thaumcraft.golemancy.seals.behavior;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import thaumcraft.golemancy.seals.core.SealWorker;

import java.util.UUID;

/** Original FakeThaumcraftGolem interactions adapted to Forge's current protection hooks. */
public final class SealWorld {
    private SealWorld() {}
    private static FakePlayer actor(ServerLevel level,SealWorker golem) {
        UUID id=UUID.nameUUIDFromBytes(("ThaumcraftGolem:"+golem.mob().getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FakePlayer player=FakePlayerFactory.get(level,new GameProfile(id,"FakeThaumcraftGolem"));
        player.getInventory().clearContent();player.setGameMode(GameType.SURVIVAL);
        player.moveTo(golem.mob().getX(),golem.mob().getY(),golem.mob().getZ(),golem.mob().getYRot(),golem.mob().getXRot());
        return player;
    }
    public static boolean harvest(ServerLevel level,SealWorker golem,BlockPos pos,boolean silk) {
        if(!loaded(level,pos))return false;
        BlockState state=level.getBlockState(pos);
        if(state.isAir()||state.getDestroySpeed(level,pos)<0||state.getBlock() instanceof GameMasterBlock)return false;
        FakePlayer player=actor(level,golem);
        ItemStack tool=silk?new ItemStack(Items.DIAMOND_PICKAXE):ItemStack.EMPTY;
        if(silk)tool.enchant(Enchantments.SILK_TOUCH,1);
        player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        int xp=ForgeHooks.onBlockBreakEvent(level,GameType.SURVIVAL,player,pos);
        if(xp<0||level.getBlockState(pos)!=state||!loaded(level,pos))return false;
        var tile=level.getBlockEntity(pos);
        boolean removed=state.onDestroyedByPlayer(level,pos,player,true,level.getFluidState(pos));
        if(!removed)return false;
        level.levelEvent(player,2001,pos,Block.getId(state));state.getBlock().destroy(level,pos,state);
        state.getBlock().playerDestroy(level,player,pos,state,tile,tool);
        if(xp>0)state.getBlock().popExperience(level,pos,xp);
        player.getInventory().clearContent();return true;
    }
    public static boolean click(ServerLevel level,SealWorker golem,BlockPos pos,Direction face,ItemStack requested,boolean emptyHand,boolean sneak,boolean right) {
        if(!loaded(level,pos))return false;
        FakePlayer player=actor(level,golem);player.setShiftKeyDown(sneak);
        ItemStack borrowed=emptyHand?ItemStack.EMPTY:golem.dropItem(requested);
        if(!emptyHand&&!requested.isEmpty()&&borrowed.isEmpty())return false;
        player.setItemInHand(InteractionHand.MAIN_HAND,borrowed);
        boolean success=false;
        try {
            if(right) {
                InteractionResult result=player.gameMode.useItemOn(player,level,borrowed,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false));
                success=result.consumesAction();
            } else {
                var event=ForgeHooks.onLeftClickBlock(player,pos,face);
                if(!event.isCanceled()) {
                    BlockState state=level.getBlockState(pos);state.attack(level,pos,player);
                    // Server's interaction manager performs the ordinary destruction/callback path.
                    success=player.gameMode.destroyBlock(pos);
                }
            }
            golem.addRankXp(1);golem.swingArm();
        } finally {
            for(int i=0;i<player.getInventory().getContainerSize();i++) {
                ItemStack item=player.getInventory().removeItemNoUpdate(i);
                if(!item.isEmpty()){ItemStack rest=golem.holdItem(item);if(!rest.isEmpty())golem.mob().spawnAtLocation(rest);}
            }
            player.setShiftKeyDown(false);
        }
        return success;
    }
    public static boolean grown(ServerLevel level,BlockPos pos) {
        if(!loaded(level,pos))return false;
        BlockState state=level.getBlockState(pos);
        if(state.getBlock() instanceof CropBlock crop)return crop.isMaxAge(state);
        if(state.is(Blocks.COCOA))return state.getValue(CocoaBlock.AGE)==2;
        if(state.is(Blocks.NETHER_WART))return state.getValue(NetherWartBlock.AGE)==3;
        if(state.is(Blocks.MELON)||state.is(Blocks.PUMPKIN))return true;
        if(state.is(Blocks.SUGAR_CANE)||state.is(Blocks.CACTUS))return level.hasChunkAt(pos.below())&&level.getBlockState(pos.below()).is(state.getBlock());
        // Stems are explicitly excluded in original CropUtils; ModConfig registers the two fruits.
        return false;
    }
    public static ItemStack seed(BlockState state) {
        if(state.is(Blocks.WHEAT))return new ItemStack(Items.WHEAT_SEEDS);
        if(state.is(Blocks.CARROTS))return new ItemStack(Items.CARROT);
        if(state.is(Blocks.POTATOES))return new ItemStack(Items.POTATO);
        if(state.is(Blocks.BEETROOTS))return new ItemStack(Items.BEETROOT_SEEDS);
        if(state.is(Blocks.COCOA))return new ItemStack(Items.COCOA_BEANS);
        if(state.is(Blocks.NETHER_WART))return new ItemStack(Items.NETHER_WART);
        return ItemStack.EMPTY;
    }
    public static boolean replant(ServerLevel level,SealWorker golem,BlockPos pos,Direction support,ItemStack seed,boolean farmland) {
        if(!loaded(level,pos)||!loaded(level,pos.relative(support))||!level.isEmptyBlock(pos))return false;
        if(farmland&&(level.getBlockState(pos.below()).is(Blocks.DIRT)||level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK))) {
            // The original helper hoes with an actual diamond hoe before using the carried seed.
            FakePlayer player=actor(level,golem);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_HOE));
            player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false));
            player.getInventory().clearContent();
        }
        ItemStack paid=golem.dropItem(seed.copyWithCount(1));if(paid.isEmpty())return false;
        FakePlayer player=actor(level,golem);player.setItemInHand(InteractionHand.MAIN_HAND,paid);
        boolean success;
        try {
            success=player.gameMode.useItemOn(player,level,paid,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos.relative(support)),support.getOpposite(),pos.relative(support),false)).consumesAction();
            ItemStack rest=player.getMainHandItem();if(!rest.isEmpty()){ItemStack back=golem.holdItem(rest);if(!back.isEmpty())golem.mob().spawnAtLocation(back);}
        } finally {player.getInventory().clearContent();}
        if(success){golem.addRankXp(1);golem.swingArm();}return success;
    }
    public static void eject(ServerLevel level,BlockPos pos,ItemStack stack) {
        if(stack.isEmpty())return;ItemEntity entity=new ItemEntity(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack.copy());
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(.2,.5,.2));level.addFreshEntity(entity);
    }
    public static boolean loaded(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&level.isInWorldBounds(pos);}
    /** Same original reach=2, upward-first walk and X/Z24/Y48 bounds; avoids original static recursion. */
    public static BlockPos furthestLog(ServerLevel level,BlockPos origin) {
        BlockState root=level.getBlockState(origin);BlockPos current=origin;double distance=0;
        // The original strictly increases distance, so this finite volume is an absolute termination bound.
        for(int step=0;step<49*97*49;step++) {
            BlockPos next=null;
            search:for(int x=-2;x<=2;x++)for(int y=2;y>=-2;y--)for(int z=-2;z<=2;z++) {
                BlockPos candidate=current.offset(x,y,z);int dx=candidate.getX()-origin.getX(),dy=candidate.getY()-origin.getY(),dz=candidate.getZ()-origin.getZ();
                if(Math.abs(dx)>24||Math.abs(dy)>48||Math.abs(dz)>24)return current;
                if(!loaded(level,candidate))continue;BlockState state=level.getBlockState(candidate);
                // 1.12 damageDropped ignores log axis. Modern block identity represents species/metadata.
                if(state.getBlock()!=root.getBlock()||state.getDestroySpeed(level,candidate)<0)continue;
                double d=(double)dx*dx+(double)dy*dy+(double)dz*dz;if(d>distance){distance=d;next=candidate;break search;}
            }
            if(next==null)return current;current=next;
        }
        return current;
    }
}
