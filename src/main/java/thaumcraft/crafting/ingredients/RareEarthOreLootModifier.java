package thaumcraft.crafting.ingredients;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.world.WorldModule;

/** The BETA26 unconditional non-silk rare-earth harvest bonus, retaining the existing loot list. */
public final class RareEarthOreLootModifier extends LootModifier {
    public static final Codec<RareEarthOreLootModifier> CODEC = RecordCodecBuilder.create(
            instance -> codecStart(instance).apply(instance, RareEarthOreLootModifier::new));

    public RareEarthOreLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState state=context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if(state==null) return generatedLoot;
        ItemStack tool=context.getParamOrNull(LootContextParams.TOOL);
        if(tool!=null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH,tool)>0) return generatedLoot;
        double chance=chance(state);
        // Original nextFloat < double thresholds, not a Fortune multiplier or a new ore-tag rule.
        if(chance>0 && context.getRandom().nextFloat()<chance) {
            ItemStack rare=CatalogModule.stack("nugget_rareearth");
            if(!rare.isEmpty()) generatedLoot.add(rare);
        }
        return generatedLoot;
    }

    static double chance(BlockState state) {
        if(state.is(Blocks.DIAMOND_ORE)||state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return .05;
        if(state.is(Blocks.EMERALD_ORE)||state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return .075;
        if(state.is(Blocks.LAPIS_ORE)||state.is(Blocks.DEEPSLATE_LAPIS_ORE)) return .01;
        if(state.is(Blocks.COAL_ORE)||state.is(Blocks.DEEPSLATE_COAL_ORE)) return .001;
        // Lit/unlit REDSTONE_ORE is now a property of the same block.
        if(state.is(Blocks.REDSTONE_ORE)||state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) return .01;
        if(state.is(Blocks.NETHER_QUARTZ_ORE)) return .01;
        if(state.is(WorldModule.ORE_AMBER.get())||state.is(WorldModule.ORE_QUARTZ.get())) return .05;
        return 0;
    }
}
