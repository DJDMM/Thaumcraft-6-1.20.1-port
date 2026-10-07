package thaumcraft.artifice.hungrychest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class HungryChestItemRenderer extends BlockEntityWithoutLevelRenderer {
    private HungryChestRenderer renderer;
    public HungryChestItemRenderer() {super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(renderer==null)renderer=new HungryChestRenderer(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CHEST));renderer.draw(0,0,pose,buffers,light,overlay);
    }
    public static final class Extension implements IClientItemExtensions {
        private BlockEntityWithoutLevelRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {if(renderer==null)renderer=new HungryChestItemRenderer();return renderer;}
    }
}
