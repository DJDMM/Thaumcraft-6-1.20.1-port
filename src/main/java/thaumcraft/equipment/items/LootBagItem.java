package thaumcraft.equipment.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.CatalogModule;
import java.util.*;

/** Original ModConfig.initLoot weights and Utils.generateLoot gear branch. */
public final class LootBagItem extends Item {
    private final int rarity;
    private record Entry(ItemStack stack,int weight) {}
    private static final Map<Integer,List<Entry>> TABLES=new java.util.concurrent.ConcurrentHashMap<>();
    public LootBagItem(CatalogModule.Spec spec) {
        super(new Properties().stacksTo(16).rarity(spec.legacyMetadata()==2 ? Rarity.RARE : spec.legacyMetadata()==1 ? Rarity.UNCOMMON : Rarity.COMMON));
        rarity=spec.legacyMetadata();
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) { tooltip.add(Component.translatable("tc.lootbag").withStyle(ChatFormatting.GRAY)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack bag=player.getItemInHand(hand);
        if(player instanceof ServerPlayer) {
            int count=8+level.random.nextInt(5);
            for(int i=0;i<count;i++) level.addFreshEntity(new ItemEntity(level,player.getX(),player.getY(),player.getZ(),generateLoot(rarity,level.random)));
            var sound=ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","coins"));
            if(sound!=null) level.playSound(null,player.blockPosition(),sound,SoundSource.PLAYERS,.75F,1F);
            bag.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(bag,level.isClientSide);
    }
    public static ItemStack generateLoot(int rarity,RandomSource random) {
        rarity=Math.max(0,Math.min(2,rarity));
        ItemStack result=ItemStack.EMPTY;
        // Original recursion retries a rare empty gear selection. Bound retries against pathological RNGs.
        for(int tries=0;tries<64 && result.isEmpty();tries++) {
            if(rarity>0 && random.nextFloat()<.025F*rarity) result=gear(rarity,random);
            else {
                List<Entry> entries=entries(rarity);
                int weight=random.nextInt(entries.stream().mapToInt(Entry::weight).sum());
                for(Entry entry:entries) { weight-=entry.weight();if(weight<0) { result=entry.stack().copy();break; } }
            }
        }
        if(result.isEmpty()) result=new ItemStack(Items.GOLD_NUGGET,rarity+1);
        if(result.is(Items.BOOK)) result=EnchantmentHelper.enchantItem(random,result,(int)(5+rarity*.75F*random.nextInt(18)),false);
        return result;
    }
    private static List<Entry> entries(int rarity) {
        return TABLES.computeIfAbsent(rarity,LootBagItem::createEntries);
    }
    private static List<Entry> createEntries(int rarity) {
        List<Entry> entries=new ArrayList<>();
        add(entries,new ItemStack(Items.GOLD_NUGGET,rarity+1),2500-250*rarity);
        add(entries,tc("salis_mundus"),3+3*rarity);
        for(Item item:new Item[]{Items.CHORUS_FRUIT,Items.COMPASS,Items.COOKIE}) add(entries,new ItemStack(item),5);
        if(rarity==0) { add(entries,pearl(7),1); }
        else if(rarity==1) { add(entries,pearl(7),3);add(entries,pearl(6),1); }
        else { add(entries,pearl(5),9);add(entries,pearl(3),3);add(entries,pearl(0),1);add(entries,new ItemStack(Items.NETHER_STAR),1); }
        add(entries,new ItemStack(Items.DIAMOND),rarity==0 ? 10 : 50);
        add(entries,new ItemStack(Items.EMERALD),rarity==0 ? 15 : 75);
        add(entries,new ItemStack(Items.GOLD_INGOT),100);add(entries,new ItemStack(Items.ENDER_PEARL),100);
        if(rarity==0) for(String id:new String[]{"baubles_amulet_mundane","baubles_ring_mundane","baubles_girdle_mundane"}) add(entries,tc(id),10);
        else {
            add(entries,tc("amulet_vis_found"),6);
            String[] ids=rarity==2 ? new String[]{"baubles_ring_apprentice"} : new String[]{"baubles_amulet_fancy","baubles_ring_fancy","baubles_girdle_fancy"};
            for(String id:ids) add(entries,tc(id),5);
        }
        add(entries,new ItemStack(Items.EXPERIENCE_BOTTLE),5<<rarity);
        add(entries,new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),rarity+1);
        add(entries,new ItemStack(Items.GOLDEN_APPLE),3+3*rarity);
        add(entries,new ItemStack(Items.BOOK),10);
        for(var entry:ForgeRegistries.POTIONS.getEntries()) {
            add(entries,PotionUtils.setPotion(new ItemStack(Items.POTION),entry.getValue()),2);
            add(entries,PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),entry.getValue()),2);
            if(rarity>0) add(entries,PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION),entry.getValue()),2);
        }
        return entries;
    }
    private static void add(List<Entry> list,ItemStack stack,int weight) { if(!stack.isEmpty()) list.add(new Entry(stack,weight)); }
    private static ItemStack tc(String id) {
        Item item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id));
        return item==null ? ItemStack.EMPTY : new ItemStack(item);
    }
    private static ItemStack pearl(int damage) { ItemStack stack=tc("primordial_pearl");stack.setDamageValue(damage);return stack; }
    private static ItemStack gear(int rarity,RandomSource random) {
        int quality=random.nextInt(2);
        for(float chance:new float[]{.2F,.15F,.1F,.095F,.095F}) if(random.nextFloat()<chance) quality++;
        int slot=random.nextInt(5);
        String[][] ids={
                {"minecraft:iron_axe","minecraft:iron_sword","minecraft:golden_axe","minecraft:golden_sword","thaumcraft:thaumium_sword","minecraft:diamond_sword","thaumcraft:void_sword"},
                {"minecraft:leather_boots","minecraft:golden_boots","minecraft:chainmail_boots","minecraft:iron_boots","thaumcraft:thaumium_boots","minecraft:diamond_boots","thaumcraft:void_boots"},
                {"minecraft:leather_leggings","minecraft:golden_leggings","minecraft:chainmail_leggings","minecraft:iron_leggings","thaumcraft:thaumium_legs","minecraft:diamond_leggings","thaumcraft:void_legs"},
                {"minecraft:leather_chestplate","minecraft:golden_chestplate","minecraft:chainmail_chestplate","minecraft:iron_chestplate","thaumcraft:thaumium_chest","minecraft:diamond_chestplate","thaumcraft:void_chest"},
                {"minecraft:leather_helmet","minecraft:golden_helmet","minecraft:chainmail_helmet","minecraft:iron_helmet","thaumcraft:thaumium_helm","minecraft:diamond_helmet","thaumcraft:void_helm"}};
        Item item=ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(ids[slot][quality]));
        if(item==null) return ItemStack.EMPTY;
        ItemStack stack=new ItemStack(item);stack.setDamageValue(random.nextInt(1+stack.getMaxDamage()/6));
        if(random.nextInt(4)<rarity) stack=EnchantmentHelper.enchantItem(random,stack,(int)(5+rarity*.75F*random.nextInt(18)),false);
        return stack;
    }
}
