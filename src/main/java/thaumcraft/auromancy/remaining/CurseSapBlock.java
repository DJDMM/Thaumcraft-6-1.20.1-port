package thaumcraft.auromancy.remaining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import java.util.Set;

/** Original invisible effectSap: contact Wither/Slow/Hunger, then natural random-tick removal. */
public final class CurseSapBlock extends AirBlock {
    private static final Set<String> ELDRITCH = Set.of("eldritch_crab", "eldritch_guardian", "eldritch_golem", "eldritch_warden",
            "inhabited_zombie", "mind_spider", "taintacle_giant");
    public CurseSapBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || living.hasEffect(MobEffects.WITHER) || eldritch(entity)) return;
        if (level instanceof ServerLevel server) CurseSapQueue.enqueue(server, living);
    }
    static boolean eldritch(Entity entity) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && id.getNamespace().equals("thaumcraft") && ELDRITCH.contains(id.getPath());
    }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos).getBlock() == this) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        float h = random.nextFloat() * .33F;
        var particle = new DustParticleOptions(new Vector3f(.3F - random.nextFloat() * .1F, 0, .5F + random.nextFloat() * .2F), 1);
        level.addParticle(particle, pos.getX() + random.nextFloat(), pos.getY() + .1515 + h / 2F, pos.getZ() + random.nextFloat(), 0, .01, 0);
        if (random.nextInt(50) == 0) {
            var sound = ForgeRegistries.SOUND_EVENTS.getValue(RemainingEffectsModule.id("jacobs"));
            if (sound != null) level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), sound,
                    net.minecraft.sounds.SoundSource.AMBIENT, .25F, 1F + (random.nextFloat() - random.nextFloat()) * .2F, false);
        }
    }
}
