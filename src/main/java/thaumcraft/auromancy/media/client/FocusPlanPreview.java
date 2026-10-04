package thaumcraft.auromancy.media.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.auromancy.media.FocusPlanArea;

/** Read-only original architect block preview; payment and actual selection remain on the server. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class FocusPlanPreview {
    private FocusPlanPreview(){}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var minecraft=Minecraft.getInstance();var player=minecraft.player;if(player==null||minecraft.level==null||minecraft.screen!=null)return;
        var caster=FocusPlanArea.caster(player);var plan=FocusStacks.readPlan(FocusSelection.installed(caster));if(plan.isEmpty())return;
        var medium=plan.get().graph().nodes().stream().filter(node->node.key().equals("thaumcraft.PLAN")).findFirst();if(medium.isEmpty())return;
        var hits=FocusPlanArea.targets(player,player.getEyePosition().add(0,-.10000000149011612,0),player.getLookAngle(),medium.get().settings().get("method"));
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=minecraft.renderBuffers().bufferSource();var vertices=buffers.getBuffer(RenderType.lines());
        for(var hit:hits){var pos=hit.getBlockPos();LevelRenderer.renderLineBox(pose,vertices,pos.getX()-.002,pos.getY()-.002,pos.getZ()-.002,pos.getX()+1.002,pos.getY()+1.002,pos.getZ()+1.002,.65F,.35F,1,.65F);}
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
}
