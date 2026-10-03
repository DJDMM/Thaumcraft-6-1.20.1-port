package thaumcraft.auromancy.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import thaumcraft.auromancy.FocusBoltNetwork;
import java.util.*;

/** FXBolt's original three-tick seeded path and nested five-sided tubes, using the modern vertex API. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class FocusBoltClient {
    private static final List<Bolt> BOLTS=new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(4096));
    private static ClientLevel world;
    private static long received,rendered;
    private FocusBoltClient() {}
    private static boolean select(ClientLevel level) {
        if(world!=level){BOLTS.clear();world=level;received=rendered=0;}return level!=null;
    }
    public static void receive(FocusBoltNetwork.Zap p) {
        var level=Minecraft.getInstance().level;
        if(!select(level)||!level.dimension().location().equals(p.dimension())||!FocusBoltNetwork.valid(p.source(),p.target(),p.width()))return;
        if(BOLTS.size()>=512)BOLTS.remove(0);
        BOLTS.add(new Bolt(p,level.random.nextInt(50)*(float)Math.PI,level.random.nextInt(1000)));received++;
    }
    public static long receivedCount(){return received;}
    public static long renderedCount(){return rendered;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        var mc=Minecraft.getInstance();if(e.phase!=TickEvent.Phase.END||!select(mc.level)||mc.isPaused())return;
        BOLTS.removeIf(b->++b.age>3);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||BOLTS.isEmpty()||world!=Minecraft.getInstance().level)return;
        var vertices=BUFFERS.getBuffer(FocusBoltRenderTypes.BOLT);var eye=e.getCamera().getPosition();
        for(var bolt:BOLTS){bolt.draw(vertices,e.getPoseStack().last().pose(),eye,e.getPartialTick());rendered++;}
        BUFFERS.endBatch();
    }
    private static final class Bolt {
        final FocusBoltNetwork.Zap p;final float phase;final int seed;int age;
        Bolt(FocusBoltNetwork.Zap p,float phase,int seed){this.p=p;this.phase=phase;this.seed=seed;}
        void draw(VertexConsumer vertices,Matrix4f pose,Vec3 eye,float partial) {
            Vec3 delta=p.target().subtract(p.source());float length=(float)(delta.length()*Math.PI);int steps=(int)length;
            // Original GLE rendering requires more than two path points.
            if(steps<3)return;
            Random rng=new Random(seed);List<Vec3> points=new ArrayList<>();List<Float> widths=new ArrayList<>();
            points.add(Vec3.ZERO);widths.add(p.width());float amplitude=(age+partial)/10F;
            for(int a=1;a<steps-1;a++) {
                float dist=a*(length/steps)+phase;
                points.add(delta.scale(a/(double)steps).add(Mth.sin(dist/4)*amplitude+(rng.nextFloat()-rng.nextFloat())*.1F,
                        Mth.sin(dist/3)*amplitude+(rng.nextFloat()-rng.nextFloat())*.1F,
                        Mth.sin(dist/2)*amplitude+(rng.nextFloat()-rng.nextFloat())*.1F));
                widths.add((rng.nextInt(4)==0?1-age*.25F:1)*p.width());
            }
            points.add(delta);widths.add(p.width());float alpha=Mth.clamp(1-age/3F,.1F,1);
            // GLE join tessellation is replaced by continuous indexed five-sided rings.
            for(float scale:new float[]{.1F,.1F/3})for(int i=0;i<points.size()-1;i++) {
                Vec3 tangent=points.get(i+1).subtract(points.get(i)).normalize();
                Vec3 axis=tangent.cross(Math.abs(tangent.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
                Vec3 other=tangent.cross(axis).normalize();
                Vec3 start=p.source().add(points.get(i)).subtract(eye),end=p.source().add(points.get(i+1)).subtract(eye);
                for(int side=0;side<5;side++) {
                    Vec3 r0=axis.scale(Math.cos(side*Math.PI*2/5)).add(other.scale(Math.sin(side*Math.PI*2/5)));
                    Vec3 r1=axis.scale(Math.cos((side+1)*Math.PI*2/5)).add(other.scale(Math.sin((side+1)*Math.PI*2/5)));
                    vertex(vertices,pose,start.add(r0.scale(widths.get(i)*scale)),side/5F,i/(float)steps,alpha);
                    vertex(vertices,pose,start.add(r1.scale(widths.get(i)*scale)),(side+1)/5F,i/(float)steps,alpha);
                    vertex(vertices,pose,end.add(r1.scale(widths.get(i+1)*scale)),(side+1)/5F,(i+1)/(float)steps,alpha);
                    vertex(vertices,pose,end.add(r0.scale(widths.get(i+1)*scale)),side/5F,(i+1)/(float)steps,alpha);
                }
            }
        }
        void vertex(VertexConsumer v,Matrix4f pose,Vec3 point,float u,float t,float alpha) {
            v.vertex(pose,(float)point.x,(float)point.y,(float)point.z)
                    .color((p.color()>>16&255)/255F,(p.color()>>8&255)/255F,(p.color()&255)/255F,alpha).uv(u,t).uv2(15728880).endVertex();
        }
    }
}
