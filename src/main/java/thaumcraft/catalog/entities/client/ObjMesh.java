package thaumcraft.catalog.entities.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Original Wavefront faces, groups and UVs, drawn through the modern entity pipeline. */
public final class ObjMesh {
    private record Corner(Vector3f pos,float u,float v,Vector3f normal) {}
    private record Face(String group,List<Corner> corners) {}
    private final List<Face> faces=new ArrayList<>();
    private static final Map<String,ObjMesh> CACHE=new HashMap<>();
    public static ObjMesh get(String name) { return CACHE.computeIfAbsent("obj/"+name,key->new ObjMesh(key,false)); }
    /** Original block renderer cancels the OBJ loader's V flip with its texture matrix. */
    public static ObjMesh getBlock(String name) { return CACHE.computeIfAbsent("block/"+name,key->new ObjMesh(key,true)); }
    public static void clear() { CACHE.clear(); }
    private ObjMesh(String name,boolean originalBlockTextureMatrix) {
        List<Vector3f> points=new ArrayList<>(),normals=new ArrayList<>();List<float[]> uvs=new ArrayList<>();String group="default";
        try (var reader=new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager()
                .open(ResourceLocation.fromNamespaceAndPath("thaumcraft","models/"+name+".obj")),StandardCharsets.UTF_8))) {
            for(String line;(line=reader.readLine())!=null;) {
                String[] parts=line.trim().split("\\s+");
                if(parts.length<2) continue;
                switch(parts[0]) {
                    case "v" -> points.add(new Vector3f(Float.parseFloat(parts[1]),Float.parseFloat(parts[2]),Float.parseFloat(parts[3])));
                    case "vn" -> normals.add(new Vector3f(Float.parseFloat(parts[1]),Float.parseFloat(parts[2]),Float.parseFloat(parts[3])));
                    case "vt" -> uvs.add(new float[]{Float.parseFloat(parts[1]),originalBlockTextureMatrix?Float.parseFloat(parts[2]):1-Float.parseFloat(parts[2])});
                    case "g","o" -> group=parts[1];
                    case "f" -> {
                        List<Corner> corners=new ArrayList<>();
                        for(int i=1;i<parts.length;i++) {
                            String[] indices=parts[i].split("/",-1);
                            Vector3f p=points.get(index(indices[0],points.size()));
                            float[] uv=indices.length>1 && !indices[1].isEmpty()?uvs.get(index(indices[1],uvs.size())):new float[]{0,0};
                            Vector3f normal=indices.length>2 && !indices[2].isEmpty()?normals.get(index(indices[2],normals.size())):new Vector3f(0,1,0);
                            corners.add(new Corner(p,uv[0],uv[1],normal));
                        }
                        if(corners.size()>=3) faces.add(new Face(group,List.copyOf(corners)));
                    }
                }
            }
        } catch(Exception e) { throw new IllegalStateException("Cannot load pinned mesh "+name,e); }
    }
    private static int index(String value,int size) { int n=Integer.parseInt(value);return n<0?size+n:n-1; }
    public void render(PoseStack pose,MultiBufferSource buffers,ResourceLocation material,ResourceLocation detail,int light,String... groups) {
        renderTinted(pose,buffers,material,detail,light,0xffffffff,groups);
    }
    public Set<String> groups() { Set<String> names=new LinkedHashSet<>();faces.forEach(f->names.add(f.group));return Collections.unmodifiableSet(names); }
    public void renderTinted(PoseStack pose,MultiBufferSource buffers,ResourceLocation material,ResourceLocation detail,int light,int tint,String... groups) {
        renderStyled(pose,buffers,material,detail,light,tint,RenderType::entityCutoutNoCull,groups);
    }
    public void renderStyled(PoseStack pose,MultiBufferSource buffers,ResourceLocation material,ResourceLocation detail,int light,int tint,java.util.function.Function<ResourceLocation,RenderType> layer,String... groups) {
        Set<String> included=Set.of(groups);
        for(var face:faces) {
            if(!included.isEmpty() && !included.contains(face.group)) continue;
            ResourceLocation tex=detail!=null && !face.group.startsWith("bm")?detail:material;
            VertexConsumer vertices=buffers.getBuffer(layer.apply(tex));
            var c=face.corners;
            if(c.size()==4) for(var corner:c) vertex(vertices,pose.last(),corner,light,tint);
            else for(int i=1;i<c.size()-1;i++) {
                vertex(vertices,pose.last(),c.get(0),light,tint);vertex(vertices,pose.last(),c.get(i),light,tint);
                vertex(vertices,pose.last(),c.get(i+1),light,tint);vertex(vertices,pose.last(),c.get(i+1),light,tint);
            }
        }
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose pose,Corner c,int light,int tint) {
        v.vertex(pose.pose(),c.pos.x,c.pos.y,c.pos.z).color((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).uv(c.u,c.v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),c.normal.x,c.normal.y,c.normal.z).endVertex();
    }
    public int faceCount() { return faces.size(); }
}
