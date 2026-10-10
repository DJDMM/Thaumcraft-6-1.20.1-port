package thaumcraft.scanning.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.scanning.ThaumometerItem;

/** First-person grip requested for the port; uses the player's actual skin and sleeves. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class ThaumometerHandRenderer {
    public record RenderSample(InteractionHand hand, HumanoidArm arm, boolean twoHanded,
                               int skinArms, int armMask, long sequence) {}

    private static RenderSample mainSample, offSample;
    private static long renders, armRenders, itemRenders;

    private ThaumometerHandRenderer() {}

    public static RenderSample lastSample(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainSample : offSample;
    }

    public static long renderCount() { return renders; }
    public static long armRenderCount() { return armRenders; }
    public static long itemRenderCount() { return itemRenders; }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson()
                || mc.options.hideGui || player.isSpectator() || player.isScoping()) return;

        InteractionHand hand = event.getHand();
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack displayed = event.getItemStack();
        if (!(displayed.getItem() instanceof ThaumometerItem)) {
            // Vanilla otherwise adds its empty main arm to an offhand two-handed grip.
            if (displayed.isEmpty() && player.getItemInHand(hand).isEmpty()
                    && player.getItemInHand(other).getItem() instanceof ThaumometerItem) {
                event.setCanceled(true);
            }
            return;
        }

        event.setCanceled(true);
        boolean twoHanded = player.getItemInHand(other).isEmpty();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float swing = Mth.clamp(event.getSwingProgress(), 0.0F, 1.0F);
        float swingRoot = Mth.sqrt(swing);
        float centreX = twoHanded ? 0.0F : side * 0.49F;
        float centreY = twoHanded ? -0.04F : -0.18F;
        float centreZ = twoHanded ? -0.92F : -0.98F;
        float scale = twoHanded ? 0.38F : 0.29F;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        int skinArms = 0, armMask = 0;
        pose.pushPose();
        try {
            pose.translate(side * -0.12F * Mth.sin(swingRoot * Mth.PI),
                    0.10F * Mth.sin(swing * Mth.PI) - event.getEquipProgress() * 0.65F,
                    -0.16F * Mth.sin(swingRoot * Mth.PI));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -8.0F * Mth.sin(swingRoot * Mth.PI)));
            if (!player.isInvisible()) {
                PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
                if (twoHanded) {
                    renderArm(pose, buffers, light, player, renderer, HumanoidArm.LEFT,
                            -0.40F, centreY - 0.23F, centreZ - 0.02F);
                    renderArm(pose, buffers, light, player, renderer, HumanoidArm.RIGHT,
                            0.40F, centreY - 0.23F, centreZ - 0.02F);
                    skinArms = 2;
                    armMask = 3;
                } else {
                    renderArm(pose, buffers, light, player, renderer, arm,
                            centreX + side * 0.31F, centreY - 0.24F, centreZ - 0.02F);
                    skinArms = 1;
                    armMask = arm == HumanoidArm.LEFT ? 1 : 2;
                }
            }
            pose.pushPose();
            try {
                pose.translate(centreX, centreY, centreZ);
                // The original OBJ lies in XZ. NONE keeps GUI/third-person transforms intact.
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.scale(scale, scale, scale);
                // ItemRenderer centres block models at .5; this OBJ is already centred at zero.
                pose.translate(0.5F, 0.5F, 0.5F);
                mc.getItemRenderer().renderStatic(player, displayed, ItemDisplayContext.NONE, false,
                        pose, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
                itemRenders++;
            } finally {
                pose.popPose();
            }
        } finally {
            pose.popPose();
        }
        RenderSample sample = new RenderSample(hand, arm, twoHanded, skinArms, armMask, ++renders);
        if (hand == InteractionHand.MAIN_HAND) mainSample = sample;
        else offSample = sample;
    }

    private static void renderArm(PoseStack pose, MultiBufferSource buffers, int light,
                                  AbstractClientPlayer player, PlayerRenderer renderer, HumanoidArm arm,
                                  float wristX, float wristY, float wristZ) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float modelWristX = "slim".equals(player.getModelName()) ? 5.5F / 16.0F : 6.0F / 16.0F;
        pose.pushPose();
        try {
            pose.translate(wristX, wristY, wristZ);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 35.0F));
            pose.mulPose(Axis.XP.rotationDegrees(-50.0F));
            pose.translate(side * modelWristX, -0.75F, 0.0F);
            if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(pose, buffers, light, player);
            else renderer.renderLeftHand(pose, buffers, light, player);
            armRenders++;
        } finally {
            pose.popPose();
        }
    }
}
