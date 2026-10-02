package thaumcraft.scanning.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The original ParticleEngine layer 0: additive, depth-tested, without depth writes. */
final class ThaumometerRenderTypes extends RenderType {
    static final RenderType SPARKLE = create("thaumcraft_scan_sparkle", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET).createCompositeState(false));

    private ThaumometerRenderTypes() {
        super("thaumcraft_scan_particles", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 1024, false, true, () -> {}, () -> {});
    }
}
