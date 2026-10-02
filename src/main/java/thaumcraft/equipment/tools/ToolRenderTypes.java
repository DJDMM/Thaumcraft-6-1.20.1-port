package thaumcraft.equipment.tools;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Original ParticleEngine ore-ripple layers 2 (additive) and 3 (normal alpha), both without depth writes/test. */
final class ToolRenderTypes extends RenderType {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png");
    static final RenderType ADDITIVE = type("thaumcraft_ore_ripple_additive", true);
    static final RenderType ALPHA = type("thaumcraft_ore_ripple_alpha", false);
    private static RenderType type(String name, boolean additive) {
        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                        .setTextureState(new TextureStateShard(ATLAS, false, false))
                        .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }
    private ToolRenderTypes() {
        super("thaumcraft_ore_ripple", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096,
                false, true, () -> {}, () -> {});
    }
}
