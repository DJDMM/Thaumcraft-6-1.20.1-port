package thaumcraft.auromancy.focus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Original focus package/color/srt tags, validated without trusting stored cost or execution state. */
public final class FocusStacks {
    public static final int MAX_NAME_LENGTH = 50;
    private FocusStacks() {}
    public static boolean isFocus(ItemStack stack) { return maxComplexity(stack) != 0; }
    public static int maxComplexity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("thaumcraft")) return 0;
        return switch (id.getPath()) { case "focus_1" -> 15; case "focus_2" -> 25; case "focus_3" -> 50; default -> 0; };
    }
    /** Gifted completed foci retain usability. Player research is checked only while crafting. */
    public static Optional<FocusPlan> readPlan(ItemStack stack) {
        if (!isFocus(stack) || stack.getCount() != 1 || !stack.hasTag() || !stack.getTag().contains("package", Tag.TAG_COMPOUND))
            return Optional.empty();
        try {
            CompoundTag tag = stack.getTag().getCompound("package");
            List<FocusGraph.Node> graph = new ArrayList<>();
            parseChain(tag,-1,0,0,graph,0);
            var result = FocusCompiler.compile(new FocusGraph(graph), stack, research -> true);
            return result.success() ? Optional.of(result.plan()) : Optional.empty();
        } catch (IllegalArgumentException | ClassCastException failure) { return Optional.empty(); }
    }
    private static int parseChain(CompoundTag pack,int parent,int x,int y,List<FocusGraph.Node> graph,int depth){
        if(depth>=FocusGraph.MAX_DEPTH||!pack.contains("nodes",Tag.TAG_LIST)||!(pack.get("nodes") instanceof ListTag list)
                ||list.isEmpty()||list.size()>FocusGraph.MAX_NODES||list.getElementType()!=Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("package bounds");
        int first=graph.size();
        for(int i=0;i<list.size();i++){
            CompoundTag entry=list.getCompound(i);
            if(graph.size()>=FocusGraph.MAX_NODES||!entry.contains("key",Tag.TAG_STRING)||!entry.contains("type",Tag.TAG_STRING)||entry.contains("package"))
                throw new IllegalArgumentException("package node");
            String key=entry.getString("key");var def=FocusNodeRegistry.get(key);
            if(def==null||!entry.getString("type").equals(def.type().name()))throw new IllegalArgumentException("node type");
            int id=graph.size();var settings=FocusGraph.readSettings(entry);graph.add(new FocusGraph.Node(id,parent,List.of(),x,y,key,settings));
            if(parent>=0){var old=graph.get(parent);var children=new ArrayList<>(old.children());children.add(id);
                graph.set(parent,new FocusGraph.Node(old.id(),old.parent(),children,old.x(),old.y(),old.key(),old.settings()));}
            if(FocusNodeRegistry.isSplit(key)){
                if(i!=list.size()-1||!entry.contains("packages",Tag.TAG_COMPOUND)
                        ||!(entry.getCompound("packages").get("packages") instanceof ListTag branches)||branches.size()!=2||branches.getElementType()!=Tag.TAG_COMPOUND)
                    throw new IllegalArgumentException("split branches");
                parseChain(branches.getCompound(0),id,x-1,y+1,graph,depth+1);
                parseChain(branches.getCompound(1),id,x+1,y+1,graph,depth+1);
            }else if(entry.contains("packages"))throw new IllegalArgumentException("unexpected branches");
            parent=id;y++;
        }
        return first;
    }
    /** Copies the focus, preserving unrelated metadata. A plan cannot be applied to a smaller tier. */
    public static ItemStack apply(ItemStack focus, FocusPlan plan, String name) {
        if (plan == null) throw new IllegalArgumentException("missing focus plan");
        var verified = FocusCompiler.compile(plan.graph(), focus, research -> true);
        if (!verified.success()) throw new IllegalArgumentException("invalid focus plan: " + verified.error());
        FocusPlan actual = verified.plan(); ItemStack output = focus.copy();
        CompoundTag tag = output.getOrCreateTag();
        tag.put("package", actual.packageNbt()); tag.putInt("color", actual.color()); tag.putInt("srt", actual.sortingHash());
        String clean = cleanName(name);
        if (clean.isEmpty()) output.resetHoverName(); else output.setHoverName(Component.literal(clean));
        return output;
    }
    public static String cleanName(String name) {
        if (name == null) return "";
        StringBuilder result = new StringBuilder();
        name.codePoints().filter(value -> !Character.isISOControl(value) && value != 0x00A7)
                .limit(MAX_NAME_LENGTH).forEach(result::appendCodePoint);
        return result.toString().trim();
    }
}
