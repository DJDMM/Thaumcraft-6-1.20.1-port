package thaumcraft.equipment.cleansing;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** TC6 ParticleEngine layer 0, whose bubbles use SRC_ALPHA/ONE, depth test and no depth writes. */
final class CleansingRenderTypes extends RenderType {
    static final RenderType BUBBLE = create("thaumcraft_cleansing_bubble", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET).createCompositeState(false));

    private CleansingRenderTypes() {
        super("thaumcraft_cleansing", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 1024, false, true, () -> {}, () -> {});
    }
}
