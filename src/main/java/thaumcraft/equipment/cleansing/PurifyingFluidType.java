package thaumcraft.equipment.cleansing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import java.util.function.Consumer;

/** Original FluidPure water sprites, ARGB tint, light and rarity. The material is not vanilla water. */
public final class PurifyingFluidType extends FluidType {
    public static final int COLOR = 2013252778;

    public PurifyingFluidType() {
        super(Properties.create().descriptionId("block.thaumcraft.purifying_fluid")
                .lightLevel(5).rarity(Rarity.RARE).density(1000).viscosity(1000)
                .motionScale(0).canPushEntity(false)
                .canConvertToSource(false).canHydrate(false).canSwim(false).canDrown(false)
                .canExtinguish(false).supportsBoating(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
    }

    @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override public int getTintColor() { return COLOR; }
            @Override public ResourceLocation getStillTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
            }
            @Override public ResourceLocation getFlowingTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");
            }
        });
    }
}
