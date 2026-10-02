package thaumcraft.equipment;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.aura.AuraManager;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class RunicShieldingGameTests {
    @GameTest(template="empty") public static void runicShieldConsumesAuraWaitsAfterDamageAndClearsWhenRemoved(GameTestHelper helper) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"RunicTest"));
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,2,1));player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        ItemStack armor=CatalogModule.stack("void_chest");armor.getOrCreateTag().putByte("TC.RUNIC",(byte)3);
        player.setItemSlot(EquipmentSlot.CHEST,armor);player.tickCount=20;
        AuraManager.drainVis(helper.getLevel(),pos,Float.MAX_VALUE,false);AuraManager.addVis(helper.getLevel(),pos,10);
        RunicShielding.update(player,10000);
        helper.assertTrue(player.getAbsorptionAmount()==1 && AuraManager.getVis(helper.getLevel(),pos)==9,"Initial shield charge failed to pay one vis");
        player.tickCount=21;RunicShielding.update(player,12000);
        helper.assertTrue(player.getAbsorptionAmount()==1,"Recharge accepted boundary <=nextCycle");
        RunicShielding.update(player,12001);RunicShielding.update(player,14002);
        helper.assertTrue(player.getAbsorptionAmount()==3 && AuraManager.getVis(helper.getLevel(),pos)==7,"Shield did not recharge to its rune capacity with correct aura cost");
        player.setAbsorptionAmount(0);RunicShielding.update(player,15000);RunicShielding.update(player,19000);
        helper.assertTrue(player.getAbsorptionAmount()==0,"Depleted shield ignored original 4-second wait");
        RunicShielding.update(player,19001);
        helper.assertTrue(player.getAbsorptionAmount()==1 && AuraManager.getVis(helper.getLevel(),pos)==6,"Shield failed to resume after depletion wait");
        player.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);player.tickCount=40;RunicShielding.update(player,21002);
        helper.assertTrue(player.getAbsorptionAmount()==0 && AuraManager.getVis(helper.getLevel(),pos)==6,"Unequipping runes left absorption or consumed aura");
        helper.succeed();
    }
}
