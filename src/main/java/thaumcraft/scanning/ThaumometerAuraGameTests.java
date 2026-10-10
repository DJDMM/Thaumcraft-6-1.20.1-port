package thaumcraft.scanning;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchBookVisibility;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchProgression;
import thaumcraft.world.aura.AuraManager;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ThaumometerAuraGameTests {
    @GameTest(template = "empty")
    public static void highFluxHoverIsReadOnlyButTwentiethHeldTickStartsResearchOnce(GameTestHelper h) {
        var p = player(h); aura(p, 10, 11);
        var k = KnowledgeStore.get(p); var before = k.save(); int xp = p.totalExperience;
        for (int i=0;i<10;i++) ScanningNetwork.capture(p);
        h.assertTrue(before.equals(k.save()), "Read-only aura snapshots started FLUX");
        tick(p, 19, TickEvent.Phase.END);
        h.assertTrue(before.equals(k.save()), "FLUX started before original twenty-tick check");
        tick(p, 20, TickEvent.Phase.START);
        h.assertTrue(before.equals(k.save()), "START phase duplicated held item update");
        tick(p, 20, TickEvent.Phase.END);
        h.assertTrue(k.researchStage("FLUX")==1 && p.totalExperience==xp+5 && k.scanCount()==0
                && before.getCompound("Knowledge").equals(k.save().getCompound("Knowledge")), "Native held tick did not start only FLUX/5XP");
        h.assertTrue(ResearchBookVisibility.visible(k, ResearchCatalog.get("FLUX"), false)
                && ResearchProgression.isImplemented("FLUX") && !ResearchProgression.isComplete(k,"FLUX"),
                "Event chapter hidden or unfinished rift research advertised as complete");
        var notices=thaumcraft.research.ResearchBookNotifications.between(thaumcraft.research.PlayerKnowledge.load(before),k);
        h.assertTrue(k.hasUnreadResearch("FLUX") && notices.size()==1 && notices.get(0).entry().key().equals("FLUX")
                && notices.get(0).kind()==thaumcraft.research.ResearchBookNotifications.Kind.RESEARCH,
                "Original FLUX start lost its POPUP/RESEARCH notification");
        var once=k.save(); tick(p,40,TickEvent.Phase.END);
        h.assertTrue(once.equals(k.save()) && p.totalExperience==xp+5, "Repeated aura check paid research twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluxBaseThirdUsesStrictIntegerBoundary(GameTestHelper h) {
        var p=player(h); int base=AuraManager.getAuraBase(p.serverLevel(),p.blockPosition());
        h.assertTrue(base>3,"Aura fixture has no usable base");
        aura(p,500,base/3); tick(p,20,TickEvent.Phase.END);
        h.assertTrue(KnowledgeStore.get(p).researchStage("FLUX")==0,"Equal base/3 boundary discovered FLUX");
        AuraManager.addFlux(p.serverLevel(),p.blockPosition(),.25F); tick(p,40,TickEvent.Phase.END);
        h.assertTrue(KnowledgeStore.get(p).researchStage("FLUX")==1,"Strict integer base/3 threshold was replaced with another condition");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void resonatorDoesNotDiscoverFluxAndOffhandThaumometerDoes(GameTestHelper h) {
        var p=player(h); aura(p,10,20);
        p.setItemInHand(InteractionHand.MAIN_HAND,CatalogModule.stack("vis_resonator")); tick(p,20,TickEvent.Phase.END);
        h.assertTrue(ScanningNetwork.capture(p).flux()>0 && KnowledgeStore.get(p).researchStage("FLUX")==0,
                "Resonator measured no Flux or incorrectly started thaumometer research");
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ScanningModule.THAUMOMETER.get())); tick(p,40,TickEvent.Phase.END);
        h.assertTrue(KnowledgeStore.get(p).researchStage("FLUX")==1,"Offhand held thaumometer did not discover FLUX");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void equalVisFluxAndUnheldInventoryScannerDoNotStartResearch(GameTestHelper h) {
        var p=player(h); aura(p,10,10); tick(p,20,TickEvent.Phase.END);
        h.assertTrue(KnowledgeStore.get(p).researchStage("FLUX")==0,"Equal Flux/Vis passed strict comparison");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        p.getInventory().setItem(9,new ItemStack(ScanningModule.THAUMOMETER.get())); aura(p,0,10); tick(p,40,TickEvent.Phase.END);
        h.assertTrue(KnowledgeStore.get(p).researchStage("FLUX")==0,"Unheld inventory scanner started research");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void separateImmediateClicksScanDifferentObjectsWithoutItemCooldown(GameTestHelper h) {
        var p=player(h); p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(net.minecraft.world.item.Items.COAL));
        var scanner=(ThaumometerItem)ScanningModule.THAUMOMETER.get(); scanner.scan(p,InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(net.minecraft.world.item.Items.DIAMOND)); scanner.scan(p,InteractionHand.MAIN_HAND);
        h.assertTrue(KnowledgeStore.get(p).scanCount()==2 && !p.getCooldowns().isOnCooldown(scanner),
                "Extra item cooldown blocked a separate immediate scan");
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"ThaumAuraAudit"));
        var pos=h.absolutePos(new BlockPos(1,1,1)); p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get()));return p;
    }
    private static void aura(ServerPlayer p,float vis,float flux) {
        AuraManager.drainVis(p.serverLevel(),p.blockPosition(),Float.MAX_VALUE,false);
        AuraManager.drainFlux(p.serverLevel(),p.blockPosition(),Float.MAX_VALUE,false);
        AuraManager.addVis(p.serverLevel(),p.blockPosition(),vis); AuraManager.addFlux(p.serverLevel(),p.blockPosition(),flux);
    }
    private static void tick(ServerPlayer p,int tick,TickEvent.Phase phase) {
        p.tickCount=tick; MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(phase,p));
    }
}
