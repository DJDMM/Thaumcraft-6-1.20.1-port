package thaumcraft.essentia.centrifuge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import thaumcraft.essentia.centrifuge.CentrifugeBlockItem;

public final class CentrifugeItemExtension implements IClientItemExtensions {
    private final BlockEntityWithoutLevelRenderer renderer=new BlockEntityWithoutLevelRenderer(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()) {
        private CentrifugeRenderer original;
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
            if(!(stack.getItem() instanceof CentrifugeBlockItem))return;
            if(original==null)original=new CentrifugeRenderer(null);
            original.renderGeometry(0,pose,buffers,light,overlay);
        }
    };
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
}
