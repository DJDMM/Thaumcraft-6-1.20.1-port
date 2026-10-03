package thaumcraft.infusion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Server selection and the separate original in-progress crafting comparison. */
public final class InfusionRecipes {
    private InfusionRecipes() {}

    public static Optional<InfusionRecipe.Plan> find(ServerPlayer player, ItemStack central, List<ItemStack> actualComponents) {
        if (player == null || central == null || central.isEmpty() || actualComponents == null) return Optional.empty();
        // BETA26's HashMap order is not a useful stable tie-breaker; datapack IDs make this deterministic.
        List<InfusionRecipe> recipes = new ArrayList<>(player.serverLevel().getRecipeManager().getAllRecipesFor(InfusionModule.RECIPE_TYPE.get()));
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        PlayerKnowledge knowledge = KnowledgeStore.get(player);
        for (InfusionRecipe recipe : recipes) {
            Optional<InfusionRecipe.Plan> plan = recipe.plan(knowledge, central, actualComponents, player.getRandom());
            if (plan.isPresent()) return plan;
        }
        return Optional.empty();
    }

    /** Matches the original helper's tag asymmetry; stack counts never affect the comparison. */
    public static boolean sameForCrafting(ItemStack actual, ItemStack expected) {
        if (actual == null || expected == null || actual.isEmpty() || expected.isEmpty() || !actual.is(expected.getItem())) return false;
        if (expected.getDamageValue() != 32767 && actual.getDamageValue() != expected.getDamageValue()) return false;
        CompoundTag found = legacyTags(actual), wanted = legacyTags(expected);
        if (found == null || wanted == null) return true;
        for (String key : wanted.getAllKeys()) {
            if (!found.contains(key) || !found.get(key).toString().equals(wanted.get(key).toString())) return false;
        }
        return true;
    }
    private static CompoundTag legacyTags(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag out = stack.getTag().copy();
        // 1.12 stored durability outside item NBT. 1.20 stores it under Damage.
        if (stack.isDamageableItem()) out.remove("Damage");
        return out.isEmpty() ? null : out;
    }

    public static boolean known(PlayerKnowledge knowledge, String research) {
        if (research.contains("&&")) {
            for (String key : research.split("&&")) if (!known(knowledge, key)) return false;
            return true;
        }
        if (research.contains("||")) {
            for (String key : research.split("\\|\\|")) if (known(knowledge, key)) return true;
            return false;
        }
        return knowledge.isResearchKnown(research);
    }

    /** Augmenting paths avoid the greedy broad-ingredient/precise-ingredient trap. */
    static boolean matchesComponents(List<InfusionIngredient> required, List<ItemStack> actual) {
        if (required.size() != actual.size() || actual.stream().anyMatch(stack -> stack == null || stack.isEmpty())) return false;
        int[] assigned = new int[actual.size()];
        java.util.Arrays.fill(assigned, -1);
        for (int recipe = 0; recipe < required.size(); recipe++)
            if (!assign(recipe, required, actual, assigned, new boolean[actual.size()])) return false;
        return true;
    }
    private static boolean assign(int recipe, List<InfusionIngredient> required, List<ItemStack> actual, int[] assigned, boolean[] visited) {
        for (int slot = 0; slot < actual.size(); slot++) if (!visited[slot] && required.get(recipe).test(actual.get(slot))) {
            visited[slot] = true;
            if (assigned[slot] < 0 || assign(assigned[slot], required, actual, assigned, visited)) {
                assigned[slot] = recipe;
                return true;
            }
        }
        return false;
    }
}
