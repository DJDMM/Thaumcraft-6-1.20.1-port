package thaumcraft.infusion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/** BETA26 item/damage and exact top-level NBT requirements, independent of display ingredients. */
public final class InfusionIngredient {
    private final JsonObject data;
    private final Item item;
    private final TagKey<Item> tag;
    private final int damage;
    private final CompoundTag required;
    private final List<InfusionIngredient> alternatives;

    private InfusionIngredient(JsonObject data, int depth) {
        if (depth > 4) throw new JsonSyntaxException("Infusion ingredient alternatives are too deep");
        this.data = data.deepCopy();
        int forms = (data.has("item") ? 1 : 0) + (data.has("tag") ? 1 : 0) + (data.has("any") ? 1 : 0);
        if (forms != 1) throw new JsonSyntaxException("Infusion ingredient needs exactly one item, tag or any");
        if (data.has("any")) {
            var array = GsonHelper.getAsJsonArray(data, "any");
            if (array.isEmpty() || array.size() > 128) throw new JsonSyntaxException("Invalid infusion alternatives");
            List<InfusionIngredient> choices = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) throw new JsonSyntaxException("Infusion alternative must be an object");
                choices.add(new InfusionIngredient(element.getAsJsonObject(), depth + 1));
            }
            alternatives = List.copyOf(choices); item = null; tag = null; damage = -1; required = null;
        } else {
            alternatives = List.of();
            if (data.has("item")) {
                ResourceLocation id = location(GsonHelper.getAsString(data, "item"));
                item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new JsonSyntaxException("Unknown infusion item " + id));
                if (item == Items.AIR) throw new JsonSyntaxException("Infusion ingredient cannot be air");
                tag = null;
            } else {
                item = null; tag = TagKey.create(BuiltInRegistries.ITEM.key(), location(GsonHelper.getAsString(data, "tag")));
            }
            if (!data.has("damage") || data.get("damage").isJsonPrimitive()
                    && data.get("damage").getAsJsonPrimitive().isString()
                    && data.get("damage").getAsString().equals("any")) damage = -1;
            else {
                damage = GsonHelper.getAsInt(data, "damage");
                if (damage < 0 || damage > 32767) throw new JsonSyntaxException("Invalid legacy infusion damage");
            }
            required = data.has("nbt") ? parseNbt(GsonHelper.getAsString(data, "nbt")) : null;
        }
    }

    public static InfusionIngredient fromJson(JsonObject data) { return new InfusionIngredient(data, 0); }
    public JsonObject toJson() { return data.deepCopy(); }
    public boolean test(ItemStack actual) {
        if (actual == null || actual.isEmpty()) return false;
        if (!alternatives.isEmpty()) return alternatives.stream().anyMatch(choice -> choice.test(actual));
        if (item != null ? !actual.is(item) : !actual.is(tag)) return false;
        if (damage >= 0 && actual.getDamageValue() != damage) return false;
        if (required == null || required.isEmpty()) return true;
        CompoundTag found = actual.getTag();
        if (found == null) return false;
        for (String key : required.getAllKeys()) {
            // Unlike PartialNBTIngredient, nested compounds/lists must be exactly equal.
            Tag value = found.get(key);
            if (value == null || !required.get(key).equals(value)) return false;
        }
        return true;
    }
    public List<ItemStack> examples() {
        List<ItemStack> out = new ArrayList<>();
        if (!alternatives.isEmpty()) alternatives.forEach(choice -> out.addAll(choice.examples()));
        else if (item != null) out.add(example(item));
        else BuiltInRegistries.ITEM.getTag(tag).ifPresent(values -> values.forEach(holder -> out.add(example(holder.value()))));
        return List.copyOf(out);
    }
    public Ingredient displayIngredient() { return Ingredient.of(examples().stream()); }
    public ItemStack example() { return examples().stream().findFirst().orElse(ItemStack.EMPTY).copy(); }
    private ItemStack example(Item choice) {
        ItemStack out = new ItemStack(choice);
        if (required != null) out.setTag(required.copy());
        if (out.isDamageableItem() && damage >= 0) out.setDamageValue(damage);
        return out;
    }
    static ResourceLocation location(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new JsonSyntaxException("Invalid infusion resource location " + raw);
        return id;
    }
    static CompoundTag parseNbt(String raw) {
        try { return TagParser.parseTag(raw); }
        catch (Exception error) { throw new JsonSyntaxException("Invalid infusion NBT", error); }
    }
}
