package thaumcraft.world.rift;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.entities.VisualMobEntity;

import java.util.Set;

/** BETA26 PotionFluxTaint runtime, shared by matrix harm and real rift collapse. */
public final class FluxTaintEffect extends MobEffect {
    private static final Set<String> NATIVE_TAINTED_TYPES = Set.of("thaumic_slime","taint_crawler",
            "taint_swarm","taintacle","taintacle_tiny","taintacle_giant","taint_seed","taint_seed_prime");
    private static final Set<String> NATIVE_UNDEAD_CATALOG_TYPES = Set.of("brainy_zombie","giant_brainy_zombie","inhabited_zombie");
    public FluxTaintEffect() { super(MobEffectCategory.HARMFUL,0x800080); }
    @Override public String getDescriptionId() { return "potion.flux_taint"; }
    @Override public boolean isDurationEffectTick(int duration,int amplifier) {
        // Released Java shifts use their ordinary five-bit distance mask; do not replace
        // large tiers with a clamp. Native common tiers keep40/20/10/5/2/1 cadence.
        int interval=40>>amplifier;
        return interval>0 ? duration%interval==0 : true;
    }
    @Override public void applyEffectTick(LivingEntity target,int amplifier) {
        if(target.level().isClientSide || !target.isAlive() || target.isRemoved()
                || target.getServer()==null || !target.getServer().isSameThread())return;
        if(isNativeTainted(target))target.heal(1);
        else if(!isOriginalUndead(target))target.hurt(TaintDamage.source(target),1);
        // Original Champion modifier13 also heals. That actual attribute/champion system
        // is not ported; arbitrary persistent NBT must not impersonate it.
    }
    public static boolean isNativeTainted(LivingEntity target) {
        if(!(target instanceof VisualMobEntity))return false;
        ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return id!=null && id.getNamespace().equals("thaumcraft") && NATIVE_TAINTED_TYPES.contains(id.getPath());
    }
    private static boolean isOriginalUndead(LivingEntity target) {
        if(target.getMobType()==MobType.UNDEAD)return true;
        if(!(target instanceof VisualMobEntity))return false;
        ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return id!=null && id.getNamespace().equals("thaumcraft") && NATIVE_UNDEAD_CATALOG_TYPES.contains(id.getPath());
    }
}
