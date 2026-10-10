package thaumcraft.world.rift;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Original optional wussMode suppresses natural rift generation, not existing rift ticks. */
public final class RiftModule {
    private static final ForgeConfigSpec.Builder BUILDER=new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue WUSS_MODE=BUILDER.comment("Original TC6 option: disable natural Flux Rift generation.").define("wussMode",false);
    public static final ForgeConfigSpec CONFIG=BUILDER.build();
    private RiftModule() {}
    public static void register() { ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,CONFIG,"thaumcraft-common.toml"); }
}
