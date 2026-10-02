package thaumcraft.equipment.items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.KnowledgeStore;

public final class CatalogFoodItem extends Item {
    private final boolean brain,chunk;
    public CatalogFoodItem(CatalogModule.Spec spec) {
        super(new Properties().food(food(spec.id())));
        brain=spec.id().equals("brain");chunk=spec.id().startsWith("chunk_");
    }
    private static FoodProperties food(String id) {
        if(id.equals("brain")) return new FoodProperties.Builder().nutrition(4).saturationMod(.2F).meat().build();
        if(id.equals("triple_meat_treat")) return new FoodProperties.Builder().nutrition(6).saturationMod(.8F).meat().alwaysEat()
                .effect(() -> new MobEffectInstance(MobEffects.REGENERATION,100,0),.66F).build();
        return new FoodProperties.Builder().nutrition(1).saturationMod(.3F).build();
    }
    @Override public int getUseDuration(ItemStack stack) { return chunk ? 10 : super.getUseDuration(stack); }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity eater) {
        if(brain && eater instanceof ServerPlayer player) {
            // Original override never calls ItemFood.onFoodEaten; its configured Hunger is therefore absent.
            if(level.random.nextFloat()<.1F) KnowledgeStore.addNormalWarp(player,1);
            else KnowledgeStore.addTemporaryWarp(player,1+level.random.nextInt(3));
        }
        return super.finishUsingItem(stack,level,eater);
    }
}
