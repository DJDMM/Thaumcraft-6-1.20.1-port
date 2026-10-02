package thaumcraft.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.aura.AuraManager;
import java.util.Map;
import java.util.WeakHashMap;

/** Original TC.RUNIC shield consumer: one absorption per vis, 2s recharge and 4s depletion wait. */
@Mod.EventBusSubscriber(modid="thaumcraft")
public final class RunicShielding {
    private static final Map<ServerPlayer,State> STATES=new WeakHashMap<>();
    private RunicShielding() {}
    private static final class State { int max,lastCharge;long nextCycle; }
    public static int getRunicCharge(ItemStack stack) {
        return stack.isEmpty() || !stack.hasTag() ? 0 : Math.max(0,stack.getTag().getByte("TC.RUNIC"));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase==TickEvent.Phase.END && event.player instanceof ServerPlayer player) update(player,System.currentTimeMillis());
    }
    /** Time is injectable for deterministic acceptance; production retains BETA26 wall-clock intervals. */
    public static void update(ServerPlayer player,long time) {
        if(!player.isAlive() || player.isSpectator()) return;
        State state=STATES.computeIfAbsent(player,key->new State());
        if(player.tickCount%20==0) {
            int max=0;for(ItemStack stack:player.getArmorSlots()) max+=getRunicCharge(stack);
            if(state.max>max) player.setAbsorptionAmount(Math.max(0,player.getAbsorptionAmount()-(state.max-max)));
            state.max=max;
            if(max==0) { STATES.remove(player);return; }
        }
        if(state.max==0) return;
        int charge=(int)player.getAbsorptionAmount();
        if(charge==0 && state.lastCharge>0) { state.nextCycle=time+4000;state.lastCharge=0; }
        if(charge>=state.max || state.nextCycle>=time) return;
        var level=player.serverLevel();var pos=player.blockPosition();
        if(KnowledgeStore.get(player).isResearchCompleteStrict("AURAPRESERVE")
                && AuraManager.getVis(level,pos)/AuraManager.getAuraBase(level,pos)<.1F) return;
        if(AuraManager.getVis(level,pos)<1) return;
        if(AuraManager.drainVis(level,pos,1,false)!=1) return;
        state.nextCycle=time+2000;state.lastCharge=charge+1;player.setAbsorptionAmount(charge+1);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { STATES.remove(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { STATES.remove(event.getOriginal());STATES.remove(event.getEntity()); }
}
