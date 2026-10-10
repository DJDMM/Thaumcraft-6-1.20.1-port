package thaumcraft.world.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.auromancy.AdvancedFocusEffects;
import thaumcraft.auromancy.focus.FocusCompiler;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.auromancy.media.FocusCloudEntity;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.infusion.InfusionEffects;

import java.util.List;
import java.util.Map;

/** Weighted BETA26 events. An unavailable taint ecology slot stays a no-op, never rerolls. */
public final class RiftEvents {
    public enum Outcome { APPLIED, NO_TARGET, BLOCKED, UNSUPPORTED }
    public record Entry(int event, int weight, int stabilityCost, boolean nearTaintAllowed) {}
    public static final List<Entry> EVENTS = List.of(new Entry(0,50,5,true), new Entry(1,10,0,false),
            new Entry(2,20,10,true), new Entry(3,20,10,true), new Entry(4,1,0,true));
    private RiftEvents() {}

    public static void execute(FluxRiftEntity rift) {
        if (!valid(rift)) return;
        execute(rift, choose(rift.getRandom().nextDouble()));
    }
    static int choose(double randomFraction) {
        if (!Double.isFinite(randomFraction) || randomFraction < 0 || randomFraction >= 1)
            throw new IllegalArgumentException("rift event random fraction");
        double threshold = randomFraction * 101;
        int cumulative = 0;
        for (Entry event : EVENTS) if ((cumulative += event.weight()) >= threshold) return event.event();
        throw new IllegalStateException("rift event weight");
    }
    /** Package-scoped selection permits deterministic native event tests, not a client request. */
    static Outcome execute(FluxRiftEntity rift, int event) {
        if (!valid(rift) || event < 0 || event >= EVENTS.size()) return Outcome.BLOCKED;
        ServerLevel server = (ServerLevel)rift.level();
        boolean didit = false;
        switch (event) {
            case 0 -> {
                var registered = VisualEntitiesModule.LIVING.get("wisp").get().create(server);
                if (!(registered instanceof WispEntity wisp)) return Outcome.UNSUPPORTED;
                wisp.setPos(rift.getX() + rift.getRandom().nextGaussian() * 5,
                        rift.getY() + rift.getRandom().nextGaussian() * 5,
                        rift.getZ() + rift.getRandom().nextGaussian() * 5);
                if (server.random.nextInt(5) == 0) wisp.setAspectType("vitium");
                if (wisp.canSpawnFromRift() && server.addFreshEntity(wisp)) didit = true;
                else return Outcome.BLOCKED;
            }
            case 1 -> {
                // Prime seed needs registered seed-area lifecycle, fibres/block conversion,
                // flux saturation, tentacles and taint immunity. The old NoAI catalogue
                // has none of these; retain its original weighted slot without spawning it,
                // discarding the real rift or polluting aura as if ecology existed.
                return Outcome.UNSUPPORTED;
            }
            case 2 -> {
                for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                        rift.getBoundingBox().inflate(16), e -> e.isAlive() && !e.isRemoved())) {
                    didit = true;
                    if (target instanceof Player player)
                        player.displayClientMessage(Component.translatable("tc.fluxevent.2").withStyle(
                                net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
                    MobEffectInstance infection = new MobEffectInstance(InfusionEffects.INFECTIOUS_VIS_EXHAUST.get(),3000,2);
                    infection.getCurativeItems().clear();
                    target.addEffect(infection);
                }
                if (!didit) return Outcome.NO_TARGET;
            }
            case 3 -> {
                Player closest = server.getNearestPlayer(rift,16);
                if (!(closest instanceof ServerPlayer target)) return Outcome.NO_TARGET;
                int radius = Mth.nextInt(rift.getRandom(),1,3);
                int rawDuration = Mth.nextInt(rift.getRandom(),Math.min(rift.getRiftSize()/2,30),Math.min(rift.getRiftSize(),120));
                int duration = cloudDurationSetting(rawDuration);
                FocusGraph graph = new FocusGraph(List.of(
                        new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),
                        new FocusGraph.Node(1,0,List.of(2),0,1,FocusNodeRegistry.CLOUD,Map.of("radius",radius,"duration",duration)),
                        new FocusGraph.Node(2,1,List.of(),0,2,FocusNodeRegistry.FLUX,Map.of("power",1))));
                var compiled = FocusCompiler.compile(graph,CatalogModule.stack("focus_3"),ignored -> true);
                if (!compiled.success()) return Outcome.BLOCKED;
                Vec3 source = target.getEyePosition().add(0,-.10000000149011612,0);
                // Free world event: the original engine cast boolean bypasses the caster's
                // item payment/research. Only this fixed graph is minted as a continuation.
                if (!FocusCloudEntity.spawn(target,compiled.plan(),2,source,radius,duration,.5F,0)) return Outcome.BLOCKED;
                AdvancedFocusEffects.playCastSound(server,target,FocusNodeRegistry.FLUX);
                // Original case3 never sets didit, even after a successful cast.
                return Outcome.APPLIED;
            }
            case 4 -> { rift.setCollapse(true); return Outcome.APPLIED; }
        }
        if (didit) rift.setRiftStability(rift.getRiftStability() + EVENTS.get(event).stabilityCost());
        return Outcome.APPLIED;
    }
    /** NodeSetting.setValue exhausts its spinner to30 when a sampled value is outside5..30. */
    static int cloudDurationSetting(int sampled) { return sampled >= 5 && sampled <= 30 ? sampled : 30; }
    private static boolean valid(FluxRiftEntity rift) {
        return rift != null && !rift.isRemoved() && rift.level() instanceof ServerLevel server
                && server.getServer().isSameThread() && loaded(server,rift.position());
    }
    static boolean loaded(ServerLevel server, Vec3 point) {
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return false;
        BlockPos pos = BlockPos.containing(point);
        return !server.isOutsideBuildHeight(pos) && server.getWorldBorder().isWithinBounds(pos)
                && server.getChunkSource().getChunkNow(pos.getX() >> 4,pos.getZ() >> 4) != null;
    }
    static boolean loaded(ServerLevel server, AABB bounds) {
        if (!(loaded(server,new Vec3(bounds.minX,bounds.minY,bounds.minZ))
                && loaded(server,new Vec3(bounds.maxX,bounds.maxY,bounds.maxZ))
                && loaded(server,new Vec3(bounds.minX,bounds.maxY,bounds.maxZ))
                && loaded(server,new Vec3(bounds.maxX,bounds.minY,bounds.minZ)))) return false;
        int x0=Mth.floor(bounds.minX)>>4, x1=Mth.floor(bounds.maxX)>>4;
        int z0=Mth.floor(bounds.minZ)>>4, z1=Mth.floor(bounds.maxZ)>>4;
        if (x1-x0>16 || z1-z0>16) return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)
            if(server.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
}
