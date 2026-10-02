package thaumcraft.catalog.blocks.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import thaumcraft.catalog.blocks.*;
public final class CatalogBlockItemExtension implements IClientItemExtensions {
    private final BlockEntityWithoutLevelRenderer renderer=new BlockEntityWithoutLevelRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()) {
        private CatalogBlockRenderer visual;
        @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
            if(!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof CatalogBlock block)) return;
            if(visual==null) visual=new CatalogBlockRenderer(null);
            pose.pushPose();
            if(block.catalogId().startsWith("banner_")) {pose.translate(.25,0,.25);pose.scale(.5f,.5f,.5f);}
            if(!block.catalogId().equals("centrifuge"))
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(),pose,buffers,light,overlay);
            visual.renderVisual(block.defaultBlockState(),null,null,pose,buffers,light,overlay);
            pose.popPose();
        }
    };
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
}
