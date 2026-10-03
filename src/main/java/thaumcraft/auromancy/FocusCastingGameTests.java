package thaumcraft.auromancy;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import thaumcraft.auromancy.focus.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.infusion.InfusionEffects;
import thaumcraft.world.aura.AuraManager;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FocusCastingGameTests {
    private static ItemStack focus(int power,int duration){
        var compiled=FocusCompiler.compile(FocusGraph.touchFire(power,duration),CatalogModule.stack("focus_1"),ignored->true);
        if(!compiled.success())throw new AssertionError(compiled.error());
        return FocusStacks.apply(CatalogModule.stack("focus_1"),compiled.plan(),"TC6 test");
    }
    private static ServerPlayer player(GameTestHelper h){
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TC6Focus"));
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.setYRot(0);p.setXRot(0);p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);return p;
    }
    private static void ready(ServerPlayer p,int power,int duration){
        ItemStack caster=CatalogModule.stack("caster_basic");FocusSelection.setInstalled(caster,focus(power,duration));p.setItemInHand(InteractionHand.MAIN_HAND,caster);
    }
    private static void aura(ServerPlayer p,float value){AuraManager.drainVis(p.serverLevel(),p.blockPosition(),Float.MAX_VALUE,false);AuraManager.addVis(p.serverLevel(),p.blockPosition(),value);}
    private static void near(GameTestHelper h,float got,float wanted,String why){h.assertTrue(Math.abs(got-wanted)<.001F,why+": "+got+" != "+wanted);}

    @GameTest(template="empty") public static void realCastPaysFractionalAuraAndSharedCooldownEvenOnMiss(GameTestHelper h){
        var p=player(h);ready(p,1,0);aura(p,10);
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST,"Focus did not cast");
        near(h,AuraManager.getVis(p.serverLevel(),p.blockPosition()),9.2F,"Fractional .8 cast debit");
        p.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("caster_basic"));FocusSelection.setInstalled(p.getMainHandItem(),focus(1,0));
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.COOLDOWN,"Swapping casters bypassed player cooldown");
        near(h,AuraManager.getVis(p.serverLevel(),p.blockPosition()),9.2F,"Repeated use debited again");h.succeed();
    }
    @GameTest(template="empty") public static void missingAuraTakesCooldownWithoutPartialDebitAndCreativeStillPays(GameTestHelper h){
        var p=player(h);ready(p,1,0);aura(p,.5F);
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.NO_VIS && FocusCasting.onCooldown(p),"Original failed-attempt cooldown lost");
        near(h,AuraManager.getVis(p.serverLevel(),p.blockPosition()),.5F,"Insufficient aura partly drained");
        var creative=player(h);creative.gameMode.changeGameModeForPlayer(GameType.CREATIVE);ready(creative,1,0);aura(creative,2);
        h.assertTrue(FocusCasting.cast(creative,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST,"Creative focus failed");
        near(h,AuraManager.getVis(creative.serverLevel(),creative.blockPosition()),1.2F,"Creative invented free spell");h.succeed();
    }
    @GameTest(template="empty") public static void armorDiscountAndVisExhaustUseOriginalFloatPrice(GameTestHelper h){
        var p=player(h);ready(p,1,0);p.setItemSlot(EquipmentSlot.HEAD,CatalogModule.stack("goggles"));
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p);
        p.addEffect(new MobEffectInstance(InfusionEffects.VIS_EXHAUST.get(),200,1));
        near(h,FocusCasting.consumptionModifier(p),1.15F,"Five percent goggles minus twenty percent exhaustion");
        aura(p,2);h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST,"Discount spell failed");
        near(h,AuraManager.getVis(p.serverLevel(),p.blockPosition()),1.08F,"Float price changed to integer crafting price");h.succeed();
    }
    @GameTest(template="empty") public static void blankMalformedAndForgedPriceCannotCastOrDrain(GameTestHelper h){
        var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("caster_basic"));
        FocusSelection.setInstalled(p.getMainHandItem(),CatalogModule.stack("focus_1"));aura(p,5);
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.INVALID && !FocusCasting.onCooldown(p),"Blank focus became spell");
        ItemStack valid=focus(1,0);valid.getTag().getCompound("package").putInt("complexity",0);
        FocusSelection.setInstalled(p.getMainHandItem(),valid);
        h.assertTrue(FocusCasting.cast(p,InteractionHand.MAIN_HAND)==FocusCasting.Result.CAST,"Safe derived price rejected otherwise valid package");
        near(h,AuraManager.getVis(p.serverLevel(),p.blockPosition()),4.2F,"Trusted forged cost");h.succeed();
    }
    @GameTest(template="empty") public static void entityFireUsesActualDamageAndQuadraticBurnWithImmunity(GameTestHelper h){
        var p=player(h);var cow=EntityType.COW.create(h.getLevel());var blaze=EntityType.BLAZE.create(h.getLevel());
        cow.setNoAi(true);blaze.setNoAi(true);cow.setPos(p.position());blaze.setPos(p.position());
        float before=cow.getHealth();
        h.assertTrue(FocusCasting.applyFire(h.getLevel(),p,new EntityHitResult(cow,cow.position()),2,3),"Entity fire failed");
        near(h,before-cow.getHealth(),5,"3+power damage");h.assertTrue(cow.getRemainingFireTicks()==200,"Quadratic burn10 seconds lost");
        h.assertTrue(!FocusCasting.applyFire(h.getLevel(),p,new EntityHitResult(blaze,blaze.position()),2,3) && blaze.getRemainingFireTicks()<=0,"Fire immune target ignited");h.succeed();
    }
    @GameTest(template="empty") public static void blocksNeedPositiveDurationAndEmptyAdjacentFace(GameTestHelper h){
        var p=player(h);BlockPos block=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        var hit=new BlockHitResult(Vec3.atCenterOf(block).add(0,.5,0),Direction.UP,block,false);
        h.assertTrue(!FocusCasting.applyFire(h.getLevel(),p,hit,1,0) && h.getLevel().isEmptyBlock(block.above()),"Zero-duration ignited block");
        h.assertTrue(FocusCasting.applyFire(h.getLevel(),p,hit,1,1) && h.getLevel().getBlockState(block.above()).is(Blocks.FIRE),"Positive duration did not ignite empty face");
        h.assertTrue(!FocusCasting.applyFire(h.getLevel(),p,hit,1,1),"Occupied face overwritten");h.succeed();
    }
    @GameTest(template="empty") public static void touchRayRequiresLineOfSightAndUsesPlayerReach(GameTestHelper h){
        var p=player(h);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(p.getX(),p.getY(),p.getZ()+3);h.getLevel().addFreshEntity(cow);
        var target=FocusCasting.touchTarget(p);h.assertTrue(target instanceof EntityHitResult e && e.getEntity()==cow,"Reach ray missed visible entity");
        BlockPos wall=p.blockPosition().south(1);h.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        h.assertTrue(!(FocusCasting.touchTarget(p) instanceof EntityHitResult),"Touch crossed wall to entity");cow.discard();h.succeed();
    }
    @GameTest(template="empty") public static void selectionSwapsPhysicalStacksAndRejectsStalePackets(GameTestHelper h){
        var p=player(h);ready(p,1,0);ItemStack original=FocusSelection.installed(p.getMainHandItem());
        ItemStack next=focus(2,1);next.getOrCreateTag().putString("marker","detached");p.getInventory().setItem(9,next);
        ItemStack before=p.getMainHandItem().copy();
        h.assertTrue(FocusSelection.change(p,InteractionHand.MAIN_HAND,9,before,next.copy()),"Focus swap failed");
        h.assertTrue(ItemStack.matches(FocusSelection.installed(p.getMainHandItem()),next) && ItemStack.matches(p.getInventory().getItem(9),original),"Swap duplicated/lost or changed NBT");
        h.assertTrue(!FocusSelection.change(p,InteractionHand.MAIN_HAND,9,before,next.copy()),"Stale packet replayed");h.succeed();
    }
    @GameTest(template="empty") public static void fullInventoryRemovalAndBlankSelectionNeverLoseInstalledFocus(GameTestHelper h){
        var p=player(h);ready(p,1,0);for(int i=1;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        ItemStack before=p.getMainHandItem().copy();
        h.assertTrue(!FocusSelection.change(p,InteractionHand.MAIN_HAND,-1,before,ItemStack.EMPTY) && ItemStack.matches(p.getMainHandItem(),before),"Full removal discarded installed focus");
        p.getInventory().setItem(9,CatalogModule.stack("focus_1"));
        h.assertTrue(!FocusSelection.change(p,InteractionHand.MAIN_HAND,9,before,p.getInventory().getItem(9).copy()),"Blank focus installed");
        p.getInventory().setItem(9,ItemStack.EMPTY);
        h.assertTrue(FocusSelection.change(p,InteractionHand.MAIN_HAND,-1,before,ItemStack.EMPTY) && FocusSelection.installed(p.getMainHandItem()).isEmpty() && FocusStacks.readPlan(p.getInventory().getItem(9)).isPresent(),"Removal failed to return exact focus");h.succeed();
    }
    @GameTest(template="empty") public static void installedFocusReadIsDetachedTypedAndReequipTracksItemChange(GameTestHelper h){
        var caster=CatalogModule.stack("caster_basic");FocusSelection.setInstalled(caster,focus(1,0));
        var before=caster.copy();var read=FocusSelection.installed(caster);read.getOrCreateTag().getCompound("package").putInt("complexity",999);read.setHoverName(net.minecraft.network.chat.Component.literal("alias"));
        h.assertTrue(ItemStack.matches(caster,before),"Installed focus exposed caster NBT");
        var item=(CasterItem)caster.getItem();
        h.assertTrue(item.shouldCauseReequipAnimation(CatalogModule.stack("caster_basic"),new ItemStack(Items.IRON_SWORD),false),"Changing empty caster to sword suppressed animation");
        h.assertTrue(!item.shouldCauseReequipAnimation(caster,before,false),"Unchanged focus reequip");
        caster.getTag().getCompound("focus").putInt("Count",1);h.assertTrue(FocusSelection.installed(caster).isEmpty(),"Non-byte count accepted");h.succeed();
    }
    @GameTest(template="empty") public static void offhandCasterSelectionPreservesMainHandAndGiftedSpellWorks(GameTestHelper h){
        var p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));p.setItemInHand(InteractionHand.OFF_HAND,CatalogModule.stack("caster_basic"));
        p.getInventory().setItem(12,focus(1,0));
        h.assertTrue(FocusSelection.casterHand(p)==InteractionHand.OFF_HAND && FocusSelection.change(p,InteractionHand.OFF_HAND,12,p.getOffhandItem().copy(),p.getInventory().getItem(12).copy()),"Offhand focus selection failed");
        aura(p,5);h.assertTrue(FocusCasting.cast(p,InteractionHand.OFF_HAND)==FocusCasting.Result.CAST && p.getMainHandItem().is(Items.STICK),"Gifted focus required researcher or changed main hand");h.succeed();
    }
}
