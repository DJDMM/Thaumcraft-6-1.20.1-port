package thaumcraft.research;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.research.theory.TheoryCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Detached display plans only. ResearchProgression remains the authoritative payment path. */
public final class ResearchBookRequirements {
    public enum Kind { ITEM, CRAFT, RESEARCH, KNOWLEDGE, LEGACY }
    public record Row(Kind kind, String descriptor, ItemStack item, KnowledgeType type, String category,
                      int required, int available, boolean met) {
        public Row { item = item.copy(); }
        @Override public ItemStack item() { return item.copy(); }
    }
    private static final Map<String,String> DISPLAY_ALIASES = Map.ofEntries(
            Map.entry("minecraft:web","minecraft:cobweb"), Map.entry("minecraft:noteblock","minecraft:note_block"),
            Map.entry("minecraft:dye","minecraft:ink_sac"),
            Map.entry("minecraft:nether_brick","minecraft:nether_bricks"), Map.entry("minecraft:map","minecraft:filled_map"),
            Map.entry("thaumcraft:arcane_stone","thaumcraft:stone_arcane"),
            Map.entry("thaumcraft:focus_1","thaumcraft:focus_1"), Map.entry("thaumcraft:focus_2","thaumcraft:focus_2"),
            Map.entry("thaumcraft:focus_3","thaumcraft:focus_3"));
    private ResearchBookRequirements() {}

    public static List<Row> rows(ResearchEntry.Stage stage, PlayerKnowledge knowledge, Inventory inventory) {
        List<Row> result = new ArrayList<>();
        Map<Integer,Integer> reserved = new HashMap<>();
        boolean researchListed = false;
        for (String raw : stage.requirements()) {
            int separator = raw.indexOf(':');
            if (separator < 0) { result.add(new Row(Kind.LEGACY,raw,ItemStack.EMPTY,null,"",0,0,false)); continue; }
            String kind = raw.substring(0,separator).strip();
            for (String descriptor : splitValues(raw.substring(separator+1))) {
                ItemStack item = displayItem(descriptor);
                if (kind.equals("required_item")) {
                    if(item.isEmpty()) {
                        // BETA26 parseJsonOreList discards invalid/unregistered item descriptors.
                        continue;
                    }
                    int required = count(descriptor), available = 0;
                    if (inventory != null) for (int slot=0;slot<inventory.items.size();slot++) {
                        ItemStack found = inventory.getItem(slot);
                        if (!matchesItem(found,descriptor,item)) continue;
                        int take = Math.min(required-available, Math.max(0,found.getCount()-reserved.getOrDefault(slot,0)));
                        if (take > 0) { reserved.merge(slot,take,Integer::sum); available+=take; }
                        if (available>=required) break;
                    }
                    result.add(new Row(Kind.ITEM,descriptor,item,null,"",required,available,available>=required));
                } else if (kind.equals("required_craft")) {
                    // As with obtain rows, the release drops unregistered descriptors.
                    // E.g. its stale thaumcraft:metal entry must not appear as an unpaid craft.
                    if (item.isEmpty()) continue;
                    String id;
                    try { id = LegacyResearchItems.resolve(descriptor).toString(); }
                    catch (IllegalArgumentException ignored) { id = descriptor.split(";",2)[0]; }
                    id = DISPLAY_ALIASES.getOrDefault(id,id);
                    boolean met = knowledge.hasCraft(id);
                    result.add(new Row(Kind.CRAFT,descriptor,item,null,"",1,met?1:0,met));
                } else if (kind.equals("required_research")) {
                    researchListed = true;
                    result.add(researchRow(descriptor,knowledge));
                } else if (kind.equals("required_knowledge")) {
                    String[] fields=descriptor.split(";");
                    try {
                        if(fields.length!=3) throw new IllegalArgumentException();
                        KnowledgeType type=KnowledgeType.valueOf(fields[0]);
                        int required=Math.multiplyExact(Integer.parseInt(fields[2]),type.units());
                        if(required<=0 || !ResearchCategories.contains(fields[1])) throw new IllegalArgumentException();
                        int available=knowledge.rawKnowledge(type,fields[1]);
                        result.add(new Row(Kind.KNOWLEDGE,descriptor,ItemStack.EMPTY,type,fields[1],required,available,available>=required));
                    } catch(IllegalArgumentException | ArithmeticException invalid) {
                        result.add(new Row(Kind.LEGACY,descriptor,ItemStack.EMPTY,null,"",0,0,false));
                    }
                } else {
                    // e.g. original required_knowledgeauram typo is not parsed as a cost in BETA26.
                }
            }
        }
        if(!researchListed) stage.requiredResearch().forEach(key->result.add(researchRow(key,knowledge)));
        return List.copyOf(result);
    }

    private static Row researchRow(String key,PlayerKnowledge knowledge) {
        boolean met=knowledge.isResearchCompleteStrict(key);
        return new Row(Kind.RESEARCH,key,ItemStack.EMPTY,null,"",1,met?1:0,met);
    }
    private static int count(String descriptor) {
        try { String[] fields=descriptor.split(";",4); return fields.length>1?Math.max(1,Integer.parseInt(fields[1])):1; }
        catch(NumberFormatException invalid) { return 1; }
    }
    public static ItemStack displayItem(String descriptor) {
        if(descriptor.startsWith("oredict:chest")) return new ItemStack(Items.CHEST,count(descriptor));
        String[] fields=descriptor.split(";",4);
        String id=fields[0];
        try { id=LegacyResearchItems.resolve(descriptor).toString(); }
        catch(IllegalArgumentException ignored) {
            if(id.equals("minecraft:dye")) id=fields.length>2&&fields[2].equals("15")?"minecraft:bone_meal":"minecraft:ink_sac";
        }
        if(id.equals("minecraft:dye")) id="minecraft:ink_sac";
        ResourceLocation location=ResourceLocation.tryParse(DISPLAY_ALIASES.getOrDefault(id,id));
        ItemStack result=location==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(location),count(descriptor));
        if(!result.isEmpty() && fields.length>3) try { result.setTag(TagParser.parseTag(fields[3])); }
        catch(Exception ignored) { return ItemStack.EMPTY; }
        return result;
    }
    /** Shared physical requirement predicate; the server pays the same dictionary group shown by the book. */
    public static boolean matchesItem(ItemStack found,String descriptor,ItemStack template) {
        if(found.isEmpty()) return false;
        // Forge 1.12's "chest" includes wooden, trapped AND Ender chests.
        if(descriptor.split(";",2)[0].equals("oredict:chest")) return found.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("forge","chests"))) || found.is(Items.CHEST) || found.is(Items.TRAPPED_CHEST) || found.is(Items.ENDER_CHEST);
        if(descriptor.startsWith("thaumcraft:enchanted_placeholder;")) {
            try {
                String[] fields=descriptor.split(";",4);
                var enchantments=TagParser.parseTag(fields[3]).getList("ench",10);
                if(enchantments.isEmpty()) return false;
                var actual=net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(found);
                for(int i=0;i<enchantments.size();i++) {
                    var requested=enchantments.getCompound(i);
                    var enchantment=switch(requested.getShort("id")) {
                        case 0 -> net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION;
                        case 16 -> net.minecraft.world.item.enchantment.Enchantments.SHARPNESS;
                        case 33 -> net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH;
                        case 35 -> net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE;
                        default -> null;
                    };
                    if(enchantment==null || actual.getOrDefault(enchantment,0)<requested.getShort("lvl")) return false;
                }
                return true;
            } catch(Exception invalid) {return false;}
        }
        return !template.isEmpty() && TheoryCard.matchesRequirement(found,template);
    }
    public static List<String> splitValues(String raw) {
        List<String> values=new ArrayList<>(); int depth=0,start=0; char quote=0; boolean escaped=false;
        for(int i=0;i<raw.length();i++) {
            char c=raw.charAt(i);
            if(quote!=0) { if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c==quote) quote=0; continue; }
            if(c=='\'' || c=='"') quote=c;
            else if(c=='{' || c=='[') depth++;
            else if(c=='}' || c==']') depth--;
            else if(c==',' && depth==0) {values.add(raw.substring(start,i).strip());start=i+1;}
        }
        if(start<raw.length()) values.add(raw.substring(start).strip());
        return List.copyOf(values);
    }
}
