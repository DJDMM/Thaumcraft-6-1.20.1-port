package thaumcraft.golemancy.jar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import thaumcraft.golemancy.jar.BrainJarBlock;

public final class BrainJarItemExtension implements IClientItemExtensions {
    private final BlockEntityWithoutLevelRenderer renderer = new BlockEntityWithoutLevelRenderer(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()) {
        private BrainJarRenderer contents;
        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof BrainJarBlock)) return;
            if (contents == null) contents = new BrainJarRenderer(null);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(item.getBlock().defaultBlockState(), pose, buffers, light, overlay);
            contents.renderContents(0, 0, pose, buffers, light, overlay);
        }
    };
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
}
