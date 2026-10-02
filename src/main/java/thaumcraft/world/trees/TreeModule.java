package thaumcraft.world.trees;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.grower.AbstractMegaTreeGrower;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.Thaumcraft;
import thaumcraft.world.aura.AuraManager;

/** TC6 wood families; greatwood grows only from four saplings in a square. */
public final class TreeModule {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Thaumcraft.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Thaumcraft.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Thaumcraft.MOD_ID);
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD_TREE = key("greatwood_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SPIDER_GREATWOOD_TREE = key("spider_greatwood_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD_TREE = key("silverwood_tree");
    public static final RegistryObject<Block> LOG_GREATWOOD = block("log_greatwood", Log::new);
    public static final RegistryObject<Block> LOG_SILVERWOOD = block("log_silverwood", Log::new);
    public static final RegistryObject<Block> LEAVES_GREATWOOD = block("leaves_greatwood", () -> new Leaves(false));
    public static final RegistryObject<Block> LEAVES_SILVERWOOD = block("leaves_silverwood", () -> new Leaves(true));
    public static final RegistryObject<Block> SAPLING_GREATWOOD = block("sapling_greatwood", () -> new Sapling(new AbstractMegaTreeGrower() {
        @Override protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) { return null; }
        @Override protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random) { return GREATWOOD_TREE; }
    }));
    public static final RegistryObject<Block> SAPLING_SILVERWOOD = block("sapling_silverwood", () -> new Sapling(new AbstractTreeGrower() {
        @Override protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) { return SILVERWOOD_TREE; }
    }));
    public static final RegistryObject<Block> PLANK_GREATWOOD = block("plank_greatwood", Planks::new);
    public static final RegistryObject<Block> PLANK_SILVERWOOD = block("plank_silverwood", Planks::new);
    public static final RegistryObject<Block> SLAB_GREATWOOD = block("slab_greatwood", Slab::new);
    public static final RegistryObject<Block> SLAB_SILVERWOOD = block("slab_silverwood", Slab::new);
    public static final RegistryObject<Block> STAIRS_GREATWOOD = block("stairs_greatwood", () -> new Stairs(PLANK_GREATWOOD));
    public static final RegistryObject<Block> STAIRS_SILVERWOOD = block("stairs_silverwood", () -> new Stairs(PLANK_SILVERWOOD));

    static {
        FEATURES.register("greatwood_tree", GreatwoodFeature::new);
        FEATURES.register("spider_greatwood_tree", () -> new GreatwoodFeature(true));
        FEATURES.register("natural_greatwood_tree", NaturalGreatwoodFeature::new);
        FEATURES.register("natural_silverwood_tree", NaturalSilverwoodFeature::new);
        FEATURES.register("silverwood_tree", SilverwoodFeature::new);
    }

    private TreeModule() {}
    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, name));
    }
    private static RegistryObject<Block> block(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
    }

    private static class Log extends RotatedPillarBlock {
        Log() { super(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)); }
        @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 5; }
        @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 5; }
    }
    private static class Planks extends Block {
        Planks() { super(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)); }
        @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 20; }
        @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 5; }
    }
    private static class Slab extends SlabBlock {
        Slab() { super(BlockBehaviour.Properties.copy(Blocks.OAK_SLAB)); }
        @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 20; }
        @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 5; }
    }
    private static class Stairs extends StairBlock {
        Stairs(Supplier<Block> base) { super(() -> base.get().defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.OAK_STAIRS)); }
        @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 20; }
        @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 5; }
    }
    private static class Sapling extends SaplingBlock {
        Sapling(AbstractTreeGrower grower) { super(grower, BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)); }
        @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return random.nextFloat() < 0.25F; }
        @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 60; }
        @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 30; }
    }
    private static class Leaves extends LeavesBlock {
        private final boolean silverwood;
        Leaves(boolean silverwood) { super(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)); this.silverwood = silverwood; }
        @Override public boolean isRandomlyTicking(BlockState state) {
            return silverwood && !state.getValue(PERSISTENT) || super.isRandomlyTicking(state);
        }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (silverwood && !state.getValue(PERSISTENT)) {
                float missing = AuraManager.getAuraBase(level, pos) - AuraManager.getVis(level, pos);
                if (missing > 0) AuraManager.addVis(level, pos, Math.min(0.01F, missing));
            }
            super.randomTick(state, level, pos, random);
        }
        @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 60; }
        @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return state.getValue(WATERLOGGED) ? 0 : 30; }
    }
}
