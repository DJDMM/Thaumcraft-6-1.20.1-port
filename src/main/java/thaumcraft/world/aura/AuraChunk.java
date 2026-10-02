package thaumcraft.world.aura;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Mutable only inside the server-side aura store. Amounts remain finite and nonnegative. */
public final class AuraChunk {
    private final int base;
    private float vis;
    private float flux;

    public AuraChunk(int base, float vis, float flux) {
        this.base = Mth.clamp(base, 0, 500);
        this.vis = sanitize(vis);
        this.flux = sanitize(flux);
    }

    private static float sanitize(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0F, 32766.0F) : 0.0F;
    }

    public int getBase() { return base; }
    public float getVis() { return vis; }
    public float getFlux() { return flux; }
    void setVis(float value) { vis = sanitize(value); }
    void setFlux(float value) { flux = sanitize(value); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Base", base);
        tag.putFloat("Vis", vis);
        tag.putFloat("Flux", flux);
        return tag;
    }

    public static AuraChunk load(CompoundTag tag) {
        return new AuraChunk(tag.getInt("Base"), tag.getFloat("Vis"), tag.getFloat("Flux"));
    }
}
