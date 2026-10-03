package thaumcraft.auromancy.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Original FXBolt texture, additive blend, depth test and no depth writes. */
final class FocusBoltRenderTypes extends RenderType {
    static final RenderType BOLT=create("thaumcraft_focus_bolt",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,4096,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/essentia.png"),false,false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET).createCompositeState(false));
    private FocusBoltRenderTypes() {
        super("thaumcraft_bolt",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,4096,false,true,()->{},()->{});
    }
}
