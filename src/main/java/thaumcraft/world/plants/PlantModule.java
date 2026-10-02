package thaumcraft.world.plants;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thaumcraft.Thaumcraft;

/** The three BETA26 plants and the forest's ambient grass. */
public final class PlantModule {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Thaumcraft.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Thaumcraft.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Thaumcraft.MOD_ID);
    public static final RegistryObject<Block> SHIMMERLEAF = plant("shimmerleaf", Kind.SHIMMERLEAF);
    public static final RegistryObject<Block> CINDERPEARL = plant("cinderpearl", Kind.CINDERPEARL);
    public static final RegistryObject<Block> VISHROOM = plant("vishroom", Kind.VISHROOM);
    public static final RegistryObject<Block> GRASS_AMBIENT = block("grass_ambient", AmbientGrass::new);
    static {
        FEATURES.register("forest_flora", ForestFloraFeature::new);
        FEATURES.register("cinderpearl_patch", CinderpearlFeature::new);
    }
    private PlantModule() {}
    private static RegistryObject<Block> plant(String name, Kind kind) { return block(name, () -> new Plant(kind)); }
    private static RegistryObject<Block> block(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); FEATURES.register(bus); }
    private enum Kind { SHIMMERLEAF, CINDERPEARL, VISHROOM }

    private static final class Plant extends BushBlock {
        private final Kind kind;
        Plant(Kind kind) {
            super(BlockBehaviour.Properties.copy(Blocks.DANDELION).lightLevel(state -> kind == Kind.CINDERPEARL ? 7 : 6)
                    .offsetType(kind == Kind.VISHROOM ? BlockBehaviour.OffsetType.NONE : BlockBehaviour.OffsetType.XZ));
            this.kind = kind;
        }
        @Override protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
            if (kind == Kind.CINDERPEARL) return soil.is(BlockTags.SAND) || soil.is(Blocks.DIRT)
                    || soil.is(Blocks.TERRACOTTA) || soil.is(BlockTags.TERRACOTTA);
            if (kind == Kind.SHIMMERLEAF) return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(GRASS_AMBIENT.get());
            return super.mayPlaceOn(soil, level, pos) || soil.is(GRASS_AMBIENT.get());
        }
        @Override public net.minecraftforge.common.PlantType getPlantType(BlockGetter level, BlockPos pos) {
            return kind == Kind.CINDERPEARL ? net.minecraftforge.common.PlantType.DESERT
                    : kind == Kind.VISHROOM ? net.minecraftforge.common.PlantType.CAVE : net.minecraftforge.common.PlantType.PLAINS;
        }
        @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (kind == Kind.VISHROOM && !level.isClientSide && entity instanceof LivingEntity living && level.random.nextInt(5) == 0)
                living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (kind == Kind.CINDERPEARL) {
                if (random.nextBoolean()) {
                    double x = pos.getX() + .5 + (random.nextFloat() - random.nextFloat()) * .1;
                    double y = pos.getY() + .6 + (random.nextFloat() - random.nextFloat()) * .1;
                    double z = pos.getZ() + .5 + (random.nextFloat() - random.nextFloat()) * .1;
                    level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
                    level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
                }
            } else if (random.nextInt(3) == 0) {
                // Modern vanilla particles stand in for the unported FXDispatcher motes.
                level.addParticle(kind == Kind.SHIMMERLEAF ? ParticleTypes.END_ROD : ParticleTypes.WITCH,
                        pos.getX() + .5 + random.nextGaussian() * .1, pos.getY() + .4,
                        pos.getZ() + .5 + random.nextGaussian() * .1, 0, .001, 0);
            }
        }
    }
    private static final class AmbientGrass extends GrassBlock {
        AmbientGrass() { super(BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK)); }
        @Override public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
            // Ordinary grass spreads around the ambient anchor, as in TC6, not more anchors.
            Blocks.GRASS_BLOCK.randomTick(state, level, pos, random);
        }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (level.getMaxLocalRawBrightness(pos.above()) >= 4 || random.nextInt(3) != 0) return;
            int x = pos.getX() + random.nextInt(17) - 8;
            int z = pos.getZ() + random.nextInt(17) - 8;
            BlockPos spot = new BlockPos(x, pos.getY() + 5, z);
            for (int i = 0; i < 10 && spot.getY() > level.getMinBuildHeight(); i++, spot = spot.below()) {
                if (level.getBlockState(spot).is(Blocks.GRASS_BLOCK)) {
                    level.addParticle(ParticleTypes.END_ROD, x + .5, spot.getY() + 1.1, z + .5, 0, -.01, 0);
                    break;
                }
            }
        }
    }
}
