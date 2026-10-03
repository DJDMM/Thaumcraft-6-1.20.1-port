package thaumcraft.auromancy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import thaumcraft.auromancy.focus.FocusPlan;

/** BETA26 instant sixteen-block Touch subclass; the continuation remains in the already paid package. */
public final class FocusBoltMedium {
    public static final double RANGE=16;
    private FocusBoltMedium() {}
    static FocusCasting.Touch trace(ServerPlayer caster, FocusPlan plan, Vec3 source, Vec3 direction) {
        var result=FocusCasting.traceRay(caster,source,direction,RANGE);
        FocusBoltNetwork.send(caster.serverLevel(),source,result.trajectory(),plan.color(),.66F);
        return result;
    }
}
