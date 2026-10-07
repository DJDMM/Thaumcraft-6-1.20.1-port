package thaumcraft.golemancy.jar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

/** Original placed/item ID remains jar_brain; working tiles have a separate save ID. */
public final class BrainJarModule {
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "thaumcraft");
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<BlockEntityType<BrainJarBlockEntity>> BRAIN_JAR = TILES.register("brain_jar",
            () -> BlockEntityType.Builder.of(BrainJarBlockEntity::new, CatalogBlocks.block("jar_brain")).build(null));
    public static final RegistryObject<SoundEvent> JAR = sound("jar");
    public static final RegistryObject<SoundEvent> BRAIN = sound("brain");
    private BrainJarModule() {}
    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", name)));
    }
    public static boolean handlesBlock(String id) { return id.equals("jar_brain"); }
    public static Block createBlock() {
        // BlockTCTile resistance20 -> effective12; BlockJar overrides hardness to .3 only.
        // Blocks register before sounds: resolve the sound only when it is played.
        var sound = new SoundType(.5F, 1, SoundEvents.BOTTLE_FILL, SoundEvents.BOTTLE_FILL,
                SoundEvents.BOTTLE_FILL, SoundEvents.BOTTLE_FILL, SoundEvents.BOTTLE_FILL) {
            @Override public SoundEvent getBreakSound() { return JAR.get(); }
            @Override public SoundEvent getStepSound() { return JAR.get(); }
            @Override public SoundEvent getPlaceSound() { return JAR.get(); }
            @Override public SoundEvent getHitSound() { return JAR.get(); }
            @Override public SoundEvent getFallSound() { return JAR.get(); }
        };
        return new BrainJarBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).strength(.3F, 12).sound(sound).noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));
    }
    public static void register(IEventBus bus) { TILES.register(bus); SOUNDS.register(bus); }
}
