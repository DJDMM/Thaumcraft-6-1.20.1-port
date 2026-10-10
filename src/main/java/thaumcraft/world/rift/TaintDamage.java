package thaumcraft.world.rift;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** Native damage registry/tags translate original setDamageBypassesArmor().setMagicDamage(). */
public final class TaintDamage {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("thaumcraft","taint"));
    private TaintDamage() {}
    public static DamageSource source(LivingEntity target) {
        return new DamageSource(target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE));
    }
}
