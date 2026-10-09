package thaumcraft.scanning;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** BETA26 ScanEnchantment/ScanPotion: independent facts with no Observation reward.
 * Original vanilla translation keys survive the 1.20 registry/translation renames.
 * ConfigResearch enumerated the whole registry, including addons; modern/addon
 * entries use their native description key as an explicit compatibility extension. */
public final class ScanEffectFacts {
    private static final Map<String, String> ENCHANTMENT_NAMES = Map.ofEntries(
            Map.entry("protection", "protect.all"), Map.entry("fire_protection", "protect.fire"),
            Map.entry("feather_falling", "protect.fall"), Map.entry("blast_protection", "protect.explosion"),
            Map.entry("projectile_protection", "protect.projectile"), Map.entry("respiration", "oxygen"),
            Map.entry("aqua_affinity", "waterWorker"), Map.entry("thorns", "thorns"),
            Map.entry("depth_strider", "waterWalker"), Map.entry("frost_walker", "frostWalker"),
            Map.entry("binding_curse", "binding_curse"), Map.entry("sharpness", "damage.all"),
            Map.entry("smite", "damage.undead"), Map.entry("bane_of_arthropods", "damage.arthropods"),
            Map.entry("knockback", "knockback"), Map.entry("fire_aspect", "fire"),
            Map.entry("looting", "lootBonus"), Map.entry("sweeping", "sweeping"),
            Map.entry("efficiency", "digging"), Map.entry("silk_touch", "untouching"),
            Map.entry("unbreaking", "durability"), Map.entry("fortune", "lootBonusDigger"),
            Map.entry("power", "arrowDamage"), Map.entry("punch", "arrowKnockback"),
            Map.entry("flame", "arrowFire"), Map.entry("infinity", "arrowInfinite"),
            Map.entry("luck_of_the_sea", "lootBonusFishing"), Map.entry("lure", "fishingSpeed"),
            Map.entry("mending", "mending"), Map.entry("vanishing_curse", "vanishing_curse"));
    private static final Map<String, String> EFFECT_NAMES = Map.ofEntries(
            Map.entry("speed", "moveSpeed"), Map.entry("slowness", "moveSlowdown"),
            Map.entry("haste", "digSpeed"), Map.entry("mining_fatigue", "digSlowDown"),
            Map.entry("strength", "damageBoost"), Map.entry("instant_health", "heal"),
            Map.entry("instant_damage", "harm"), Map.entry("jump_boost", "jump"),
            Map.entry("nausea", "confusion"), Map.entry("regeneration", "regeneration"),
            Map.entry("resistance", "resistance"), Map.entry("fire_resistance", "fireResistance"),
            Map.entry("water_breathing", "waterBreathing"), Map.entry("invisibility", "invisibility"),
            Map.entry("blindness", "blindness"), Map.entry("night_vision", "nightVision"),
            Map.entry("hunger", "hunger"), Map.entry("weakness", "weakness"),
            Map.entry("poison", "poison"), Map.entry("wither", "wither"),
            Map.entry("health_boost", "healthBoost"), Map.entry("absorption", "absorption"),
            Map.entry("saturation", "saturation"), Map.entry("glowing", "glowing"),
            Map.entry("levitation", "levitation"), Map.entry("luck", "luck"), Map.entry("unluck", "unluck"));
    private static final Map<String, String> TC_EFFECT_NAMES = Map.ofEntries(
            Map.entry("flux_taint", "flux_taint"), Map.entry("vis_exhaust", "vis_exhaust"),
            Map.entry("infectious_vis_exhaust", "infvisexhaust"), Map.entry("unnatural_hunger", "unhunger"),
            Map.entry("warp_ward", "warpward"), Map.entry("death_gaze", "deathgaze"),
            Map.entry("blurred_vision", "blurred"), Map.entry("sun_scorned", "sunscorned"),
            Map.entry("thaumarhia", "thaumarhia"));

    private ScanEffectFacts() {}

    /** Pure predicate: examining a specimen never writes player data or alters its effects. */
    public static List<String> facts(@Nullable Object specimen) {
        if (specimen instanceof ItemEntity item) specimen = item.getItem();
        if (specimen instanceof BlockState state) specimen = new ItemStack(state.getBlock());
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        if (specimen instanceof LivingEntity living) {
            for (var instance : living.getActiveEffects()) facts.add(effectKey(instance.getEffect()));
        } else if (specimen instanceof ItemStack stack && !stack.isEmpty()) {
            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet())
                facts.add(enchantmentKey(enchantment));
            for (var instance : PotionUtils.getMobEffects(stack)) facts.add(effectKey(instance.getEffect()));
        }
        return List.copyOf(facts);
    }

    public static String enchantmentKey(Enchantment enchantment) {
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        String old = id != null && id.getNamespace().equals("minecraft") ? ENCHANTMENT_NAMES.get(id.getPath()) : null;
        return "!" + (old == null ? enchantment.getDescriptionId() : "enchantment." + old);
    }

    public static String effectKey(MobEffect effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        String old = id != null && id.getNamespace().equals("minecraft") ? EFFECT_NAMES.get(id.getPath()) : null;
        if (old != null) return "!effect." + old;
        old = id != null && id.getNamespace().equals("thaumcraft") ? TC_EFFECT_NAMES.get(id.getPath()) : null;
        return "!" + (old == null ? effect.getDescriptionId() : "potion." + old);
    }
}
