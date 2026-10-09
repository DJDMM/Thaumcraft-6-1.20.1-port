package thaumcraft.world.crystal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;
import thaumcraft.world.crystal.PrimalCrystalClusterBlock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** BETA26 CrystalModel's original eight mesh groups, size+1 and simultaneous rock attachments. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalBakedModel extends BakedModelWrapper<BakedModel> {
    public static final ModelProperty<Integer> SUPPORTS = new ModelProperty<>();
    public static final ModelProperty<Long> POSITION_SEED = new ModelProperty<>();
    private record Corner(Vector3f point, float u, float v) {}
    private record Face(int group, List<Corner> corners) {}
    private final List<List<List<BakedQuad>>> meshes;

    private CrystalBakedModel(BakedModel original, List<Face> faces) {
        super(original);
        var sprite = original.getParticleIcon();
        List<List<List<BakedQuad>>> result = new ArrayList<>();
        for (Direction support : Direction.values()) {
            List<List<BakedQuad>> parts = new ArrayList<>();
            for (int part = 0; part < 8; part++) parts.add(new ArrayList<>());
            for (Face face : faces) {
                int[] data = new int[32];
                for (int vertex = 0; vertex < 4; vertex++) {
                    Corner corner = face.corners.get(Math.min(vertex, face.corners.size() - 1));
                    Vector3f p = transform(corner.point, support);
                    int offset = vertex * 8;
                    data[offset] = Float.floatToRawIntBits(p.x);
                    data[offset + 1] = Float.floatToRawIntBits(p.y);
                    data[offset + 2] = Float.floatToRawIntBits(p.z);
                    data[offset + 3] = -1;
                    data[offset + 4] = Float.floatToRawIntBits(sprite.getU(corner.u * 16));
                    data[offset + 5] = Float.floatToRawIntBits(sprite.getV(corner.v * 16));
                    // Original packed-light minimum180, with the actual world's sky light.
                    data[offset + 6] = 180;
                }
                parts.get(face.group).add(new BakedQuad(data, 0, FaceBakery.calculateFacing(data), sprite, false));
            }
            result.add(parts.stream().map(List::copyOf).toList());
        }
        meshes = List.copyOf(result);
    }

    @SubscribeEvent public static void replaceModels(ModelEvent.ModifyBakingResult event) {
        List<Face> source = readOriginalMesh();
        var replaced = new IdentityHashMap<BakedModel, CrystalBakedModel>();
        event.getModels().replaceAll((id, model) -> id instanceof ModelResourceLocation variant
                && !variant.getVariant().equals("inventory") && id.getNamespace().equals("thaumcraft")
                && Set.of("crystal_aer", "crystal_ignis", "crystal_aqua", "crystal_terra", "crystal_ordo",
                        "crystal_perditio", "crystal_vitium").contains(id.getPath())
                ? replaced.computeIfAbsent(model, original -> new CrystalBakedModel(original, source)) : model);
    }

    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData existing) {
        return existing.derive().with(SUPPORTS, PrimalCrystalClusterBlock.supportMask(level, pos))
                .with(POSITION_SEED, state.getSeed(pos)).build();
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
            RandomSource random, ModelData data, @Nullable RenderType layer) {
        if (side != null || state == null || !(state.getBlock() instanceof PrimalCrystalClusterBlock)) return List.of();
        int supports = data.has(SUPPORTS) ? data.get(SUPPORTS) : 1 << state.getValue(PrimalCrystalClusterBlock.FACING).getOpposite().ordinal();
        if (supports == 63) return List.of(); // Pinned CrystalModel's all-six-enclosed guard.
        long seed = data.has(POSITION_SEED) ? data.get(POSITION_SEED) : random.nextLong();
        int count = state.getValue(PrimalCrystalClusterBlock.SIZE) + 1;
        List<BakedQuad> result = new ArrayList<>();
        for (Direction support : Direction.values()) {
            if ((supports & 1 << support.ordinal()) == 0) continue;
            int offset = switch (support) { case UP -> 0; case DOWN -> 5; case EAST -> 10;
                case WEST -> 15; case NORTH -> 20; case SOUTH -> 25; };
            List<Integer> groups = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
            Collections.shuffle(groups, new Random(seed + offset));
            for (int i = 0; i < count; i++) result.addAll(meshes.get(support.ordinal()).get(groups.get(i)));
        }
        return List.copyOf(result);
    }
    @Override public boolean useAmbientOcclusion() { return true; }

    private static Vector3f transform(Vector3f point, Direction support) {
        float x = point.x, y = point.y, z = point.z;
        return switch (support) {
            case DOWN -> new Vector3f(x, y, z);
            case UP -> new Vector3f(x, 1 - y, 1 - z);
            case NORTH -> new Vector3f(x, 1 - z, y);
            case SOUTH -> new Vector3f(1 - x, 1 - z, 1 - y);
            case WEST -> new Vector3f(y, 1 - z, 1 - x);
            case EAST -> new Vector3f(1 - y, 1 - z, x);
        };
    }
    private static List<Face> readOriginalMesh() {
        List<Vector3f> points = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        Map<String, Integer> groups = new LinkedHashMap<>();
        List<Face> faces = new ArrayList<>();
        String group = "";
        try (var reader = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().open(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/crystal.obj")), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length < 2) continue;
                switch (tokens[0]) {
                    case "v" -> points.add(new Vector3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])));
                    case "vt" -> uvs.add(new float[]{Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2])});
                    case "g" -> { group = tokens[1]; groups.computeIfAbsent(group, name -> groups.size()); }
                    case "f" -> {
                        List<Corner> corners = new ArrayList<>();
                        for (int i = 1; i < tokens.length; i++) {
                            String[] indices = tokens[i].split("/");
                            Vector3f p = points.get(Integer.parseInt(indices[0]) - 1);
                            float[] uv = uvs.get(Integer.parseInt(indices[1]) - 1);
                            corners.add(new Corner(p, uv[0], uv[1]));
                        }
                        faces.add(new Face(groups.get(group), List.copyOf(corners)));
                    }
                }
            }
        } catch (Exception exception) { throw new IllegalStateException("Cannot load pinned BETA26 crystal mesh", exception); }
        if (groups.size() != 8 || faces.size() != 96)
            throw new IllegalStateException("Pinned crystal mesh groups/faces changed: " + groups.size() + "/" + faces.size());
        return List.copyOf(faces);
    }
}
