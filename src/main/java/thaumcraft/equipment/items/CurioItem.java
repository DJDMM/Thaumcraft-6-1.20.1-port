package thaumcraft.equipment.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.*;
import java.util.List;

public final class CurioItem extends Item {
    private static final String[] CATEGORIES={"AUROMANCY","ALCHEMY","GOLEMANCY","ELDRITCH","INFUSION","ARTIFICE","ELDRITCH"};
    private final int type;
    public CurioItem(CatalogModule.Spec spec) { super(new Properties().rarity(Rarity.UNCOMMON));type=spec.legacyMetadata(); }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("item.curio.text"));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer server) {
            var sound=ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft","learn"));
            if(sound!=null) level.playSound(null,player.blockPosition(),sound,SoundSource.NEUTRAL,.5F,.4F/(level.random.nextFloat()*.4F+.8F));
            if(type==6 && KnowledgeStore.get(server).actualWarp()<=20) {
                player.sendSystemMessage(Component.translatable("fail.crimsonrites").withStyle(ChatFormatting.DARK_PURPLE));
                return InteractionResultHolder.fail(stack);
            }
            grant(server,CATEGORIES[Math.min(type,CATEGORIES.length-1)],KnowledgeType.OBSERVATION);
            grant(server,CATEGORIES[Math.min(type,CATEGORIES.length-1)],KnowledgeType.THEORY);
            if(type==3 || type==6) {
                KnowledgeStore.addNormalWarp(server,1);KnowledgeStore.addTemporaryWarp(server,5);
                if(type==6) {
                    // This event entry is deliberately unlocked by the original item, independently of research parents.
                    KnowledgeStore.completeCrimsonRites(server);
                    if(level.random.nextBoolean()) KnowledgeStore.addPermanentWarp(server,1);
                }
            }
            String[] categories=ResearchCategories.keys().toArray(String[]::new);
            grant(server,categories[level.random.nextInt(categories.length)],KnowledgeType.OBSERVATION);
            grant(server,categories[level.random.nextInt(categories.length)],KnowledgeType.THEORY);
            if(!player.getAbilities().instabuild) stack.shrink(1);
            player.sendSystemMessage(Component.translatable("tc.knowledge.gained").withStyle(ChatFormatting.DARK_PURPLE));
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    private static void grant(ServerPlayer player,String category,KnowledgeType type) {
        int units=type.units(),low=type==KnowledgeType.OBSERVATION ? units/2 : units/3,high=type==KnowledgeType.OBSERVATION ? units : units/2;
        KnowledgeStore.addKnowledge(player,type,category,low+player.getRandom().nextInt(high-low+1));
    }
}
