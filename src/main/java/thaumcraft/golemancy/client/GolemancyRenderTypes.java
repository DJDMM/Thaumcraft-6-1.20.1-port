package thaumcraft.golemancy.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Ethereal seal overlays: translucent, no depth writes; sneak previews intentionally see through walls. */
final class GolemancyRenderTypes extends RenderType {
    private record Key(ResourceLocation texture,boolean through){}
    private static final Map<Key,RenderType> CACHE=new HashMap<>();
    private static final Map<Key,RenderType> GOLEMS=new HashMap<>();
    static RenderType golem(ResourceLocation texture,boolean through){return GOLEMS.computeIfAbsent(new Key(texture,through),key->
            create("thaumcraft_golem_translucent"+(through?"_through":""),DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,
                    CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(through?NO_DEPTH_TEST:LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
    static final RenderType ORB=create("thaumcraft_golem_orb",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,1024,false,true,
            CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/misc/particles.png"),false,false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    static RenderType seal(ResourceLocation texture,boolean through){return CACHE.computeIfAbsent(new Key(texture,through),key->
            create("thaumcraft_seal"+(through?"_through":""),DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,4096,false,true,
                    CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                            .setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(through?NO_DEPTH_TEST:LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                            .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));}
    private GolemancyRenderTypes(){super("thaumcraft_golemancy",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,4096,false,true,()->{},()->{});}
}
