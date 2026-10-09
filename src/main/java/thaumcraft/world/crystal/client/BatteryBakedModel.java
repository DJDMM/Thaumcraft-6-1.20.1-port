package thaumcraft.world.crystal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.extensions.IForgeVertexConsumer;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.crystal.VisBatteryBlock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** BETA26 BlockVisBattery#getPackedLightmapCoords: block-light floor180, unchanged sky light. */
@Mod.EventBusSubscriber(modid = "thaumcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BatteryBakedModel extends BakedModelWrapper<BakedModel> {
    public static final int MINIMUM_BLOCK_LIGHT = 180;
    private final Map<BakedQuad, BakedQuad> litQuads = Collections.synchronizedMap(new IdentityHashMap<>());

    private BatteryBakedModel(BakedModel original) {
        super(original);
    }

    @SubscribeEvent
    public static void replaceModels(ModelEvent.ModifyBakingResult event) {
        var wrappers = new IdentityHashMap<BakedModel, BatteryBakedModel>();
        event.getModels().replaceAll((id, model) -> id instanceof ModelResourceLocation variant
                && id.getNamespace().equals("thaumcraft") && id.getPath().equals("vis_battery")
                && !variant.getVariant().equals("inventory") && !(model instanceof BatteryBakedModel)
                ? wrappers.computeIfAbsent(model, BatteryBakedModel::new) : model);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return lit(originalModel.getQuads(state, side, random));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType layer) {
        return lit(originalModel.getQuads(state, side, random, data, layer));
    }

    private List<BakedQuad> lit(List<BakedQuad> source) {
        if (source.isEmpty()) return source;
        var result = new ArrayList<BakedQuad>(source.size());
        for (BakedQuad quad : source) result.add(litQuads.computeIfAbsent(quad, BatteryBakedModel::raiseBlockLight));
        return List.copyOf(result);
    }

    private static BakedQuad raiseBlockLight(BakedQuad quad) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2;
            int packed = vertices[offset];
            // Forge merges baked UV2 with world light using component-wise maxima, including AO paths.
            // The high (sky-light) component and any brighter existing block light remain untouched.
            vertices[offset] = (packed & 0xFFFF0000) | Math.max(packed & 0xFFFF, MINIMUM_BLOCK_LIGHT);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    /** Read-only client verification for the integrated smoke driver; no model or world mutation. */
    public static BakeAudit verifyBake(Minecraft minecraft) {
        var battery = CatalogBlocks.block("vis_battery");
        int states = 0, quads = 0, lighting = 0;
        var sides = new ArrayList<>(List.of(Direction.values()));
        sides.add(null);
        for (BlockState state : battery.getStateDefinition().getPossibleStates()) {
            BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
            require(model instanceof BatteryBakedModel, "Battery state lacks original packed-light wrapper: " + state);
            var wrapped = (BatteryBakedModel) model;
            int stateQuads = 0;
            for (Direction side : sides) {
                for (boolean forge : List.of(false, true)) {
                    RandomSource sourceRandom = RandomSource.create(0), actualRandom = RandomSource.create(0);
                    List<BakedQuad> source = forge
                            ? wrapped.originalModel.getQuads(state, side, sourceRandom, ModelData.EMPTY, RenderType.solid())
                            : wrapped.originalModel.getQuads(state, side, sourceRandom);
                    List<BakedQuad> actual = forge
                            ? wrapped.getQuads(state, side, actualRandom, ModelData.EMPTY, RenderType.solid())
                            : wrapped.getQuads(state, side, actualRandom);
                    require(source.size() == actual.size(), "Battery wrapper changed quad count: " + state);
                    for (int i = 0; i < source.size(); i++) {
                        verifyQuad(source.get(i), actual.get(i));
                        lighting += verifyLighting(actual.get(i));
                        stateQuads++;
                    }
                }
            }
            require(stateQuads > 0, "Battery state has no baked geometry: " + state);
            quads += stateQuads;
            states++;
        }
        require(states == VisBatteryBlock.CAPACITY + 1, "Battery state count changed: " + states);
        var inventory = minecraft.getModelManager().getModel(new ModelResourceLocation("thaumcraft", "vis_battery", "inventory"));
        require(!(inventory instanceof BatteryBakedModel), "Battery inventory model received block-only lighting");
        require(inventory != minecraft.getModelManager().getMissingModel(), "Missing battery inventory model");

        // Preserve stronger baked block light and baked sky light from a resource replacement too.
        BlockState state = battery.defaultBlockState();
        var wrapped = (BatteryBakedModel) minecraft.getBlockRenderer().getBlockModel(state);
        BakedQuad template = wrapped.originalModel.getQuads(state, Direction.DOWN, RandomSource.create(0)).get(0);
        int[] data = template.getVertices().clone();
        int[] originalLights = { 0, (80 << 16) | 179, (16 << 16) | 200, (240 << 16) | 240 };
        for (int vertex = 0; vertex < 4; vertex++)
            data[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2] = originalLights[vertex];
        var original = new BakedQuad(data, template.getTintIndex(), template.getDirection(), template.getSprite(),
                template.isShade(), template.hasAmbientOcclusion());
        var transformed = raiseBlockLight(original);
        verifyQuad(original, transformed);
        lighting += verifyLighting(transformed);
        return new BakeAudit(states, quads, lighting);
    }

    public record BakeAudit(int states, int quadChecks, int lightingChecks) {}

    private static void verifyQuad(BakedQuad source, BakedQuad actual) {
        require(source.getTintIndex() == actual.getTintIndex() && source.getDirection() == actual.getDirection()
                && source.getSprite() == actual.getSprite() && source.isShade() == actual.isShade()
                && source.hasAmbientOcclusion() == actual.hasAmbientOcclusion(), "Battery quad attributes changed");
        int[] before = source.getVertices(), after = actual.getVertices();
        require(before.length == after.length && after.length == IQuadTransformer.STRIDE * 4,
                "Battery vertex format changed");
        for (int word = 0; word < after.length; word++) {
            int expected = word % IQuadTransformer.STRIDE == IQuadTransformer.UV2
                    ? (before[word] & 0xFFFF0000) | Math.max(before[word] & 0xFFFF, MINIMUM_BLOCK_LIGHT) : before[word];
            require(after[word] == expected, "Battery wrapper changed geometry/color/UV/normal or light component");
        }
    }

    private static int verifyLighting(BakedQuad quad) {
        // Exercise Forge's actual renderer merge rather than only repeating the wrapper's expression.
        IForgeVertexConsumer consumer = new IForgeVertexConsumer() {};
        int checks = 0;
        int[] environments = { 0, (16 << 16) | 32, (240 << 16) | 176, (240 << 16) | 240, (80 << 16) | 200 };
        for (int vertex = 0; vertex < 4; vertex++) {
            ByteBuffer data = ByteBuffer.allocate(IQuadTransformer.STRIDE * Integer.BYTES).order(ByteOrder.nativeOrder());
            for (int word = 0; word < IQuadTransformer.STRIDE; word++)
                data.putInt(word * Integer.BYTES, quad.getVertices()[vertex * IQuadTransformer.STRIDE + word]);
            int baked = quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2];
            for (int environment : environments) {
                int expected = Math.max(environment & 0xFFFF, baked & 0xFFFF)
                        | (Math.max((environment >>> 16) & 0xFFFF, (baked >>> 16) & 0xFFFF) << 16);
                require(consumer.applyBakedLighting(environment, data) == expected,
                        "Forge battery rendering lost block-light floor/higher environment/sky light");
                checks++;
            }
        }
        return checks;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
