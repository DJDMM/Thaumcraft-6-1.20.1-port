package thaumcraft.equipment.items;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.*;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ItemMechanicsGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new GameProfile(UUID.randomUUID(),"TC6ItemTest"));
        player.connection=new ServerGamePacketListenerImpl(helper.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return player;
    }
    @GameTest(template="empty") public static void foodConsumptionChangesHungerSaturationAndConsumesOne(GameTestHelper helper) {
        var player=player(helper);
        for(String id:new String[]{"chunk_beef","chunk_chicken","chunk_pork","chunk_fish","chunk_rabbit","chunk_mutton","brain","triple_meat_treat"}) {
            ItemStack food=CatalogModule.stack(id);food.setCount(2);
            player.getFoodData().setFoodLevel(0);player.getFoodData().setSaturation(0);
            int nutrition=id.startsWith("chunk_") ? 1 : id.equals("brain") ? 4 : 6;
            float saturation=id.startsWith("chunk_") ? .6F : id.equals("brain") ? 1.6F : 9.6F;
            ItemStack remainder=food.getItem().finishUsingItem(food,helper.getLevel(),player);
            helper.assertTrue(remainder.getCount()==1,"Eating did not consume exactly one "+id);
            helper.assertTrue(player.getFoodData().getFoodLevel()==nutrition,"Wrong hunger after eating "+id);
            helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel()-Math.min(nutrition,saturation))<.0001,"Wrong saturation after eating "+id);
            helper.assertTrue(food.getUseDuration()==(id.startsWith("chunk_") ? 10 : 32),"Wrong use duration "+id);
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void brainAddsBothOriginalWarpTypesAndNoHungerPotion(GameTestHelper helper) {
        var player=player(helper);
        for(int i=0;i<256;i++) {
            ItemStack brain=CatalogModule.stack("brain");
            brain.getItem().finishUsingItem(brain,helper.getLevel(),player);
        }
        var knowledge=KnowledgeStore.get(player);
        helper.assertTrue(knowledge.normalWarp()>0 && knowledge.temporaryWarp()>0,"Brain's original normal/temporary warp branches are missing");
        helper.assertTrue(knowledge.permanentWarp()==0,"Brain created permanent warp");
        helper.assertTrue(!player.hasEffect(MobEffects.HUNGER),"Decompiler-configured hunger was applied despite original override skipping super");
        var reloaded=KnowledgeStore.load(KnowledgeStore.of(helper.getLevel()).save(new CompoundTag())).get(player.getUUID());
        helper.assertTrue(reloaded.normalWarp()==knowledge.normalWarp() && reloaded.temporaryWarp()==knowledge.temporaryWarp(),"Brain warp did not survive saved-data reload");
        helper.succeed();
    }
    @GameTest(template="empty") public static void curiosAwardRawKnowledgeAndRitesEnforcePersistentWarp(GameTestHelper helper) {
        var player=player(helper);var knowledge=KnowledgeStore.get(player);
        ItemStack curio=CatalogModule.stack("curio_arcane");curio.setCount(2);player.setItemInHand(InteractionHand.MAIN_HAND,curio);
        curio.getItem().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        int observations=0,theories=0;
        for(String category:ResearchCategories.keys()) { observations+=knowledge.rawKnowledge(KnowledgeType.OBSERVATION,category);theories+=knowledge.rawKnowledge(KnowledgeType.THEORY,category); }
        helper.assertTrue(curio.getCount()==1 && observations>=16 && observations<=32 && theories>=20 && theories<=32,"Curio consumption or original raw knowledge ranges changed");
        helper.assertTrue(knowledge.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")>=8 && knowledge.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")>=10,"Arcane curio lost its designated category rewards");
        ItemStack rites=CatalogModule.stack("curio_rites");player.setItemInHand(InteractionHand.MAIN_HAND,rites);
        KnowledgeStore.addTemporaryWarp(player,100);
        rites.getItem().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        helper.assertTrue(rites.getCount()==1 && !knowledge.isResearchCompleteStrict("CrimsonRites"),"Temporary warp incorrectly satisfied Crimson Rites");
        KnowledgeStore.addNormalWarp(player,20);
        rites.getItem().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        helper.assertTrue(rites.getCount()==1,"Crimson Rites accepted exactly20 persistent warp");
        KnowledgeStore.addPermanentWarp(player,1);
        rites.getItem().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        helper.assertTrue(rites.isEmpty() && knowledge.isResearchCompleteStrict("CrimsonRites") && knowledge.normalWarp()==21 && knowledge.temporaryWarp()==105,"Crimson Rites did not complete and apply original warp at >20");
        helper.succeed();
    }
    @GameTest(template="empty") public static void warpTypesPersistClampAndCannotReduceEachOther(GameTestHelper helper) {
        var store=new KnowledgeStore();UUID id=UUID.randomUUID();
        store.addNormalWarp(id,900);store.addPermanentWarp(id,Integer.MAX_VALUE);store.addTemporaryWarp(id,2);
        helper.assertTrue(store.get(id).warpCounter()==1002,"Counter does not sum the three original warp types");
        store.addNormalWarp(id,-501);store.addTemporaryWarp(id,-10);
        var loaded=KnowledgeStore.load(store.save(new CompoundTag())).get(id);
        helper.assertTrue(loaded.normalWarp()==0 && loaded.temporaryWarp()==0 && loaded.permanentWarp()==500 && loaded.warpCounter()==1002,"Warp reduction changed another type or refreshed event counter");
        helper.succeed();
    }
    @GameTest(template="empty") public static void pearlCraftingRemaindersCrossFormsAndFinishWithoutRepair(GameTestHelper helper) {
        ItemStack pearl=CatalogModule.stack("primordial_pearl");
        for(int i=0;i<7;i++) {
            ItemStack before=pearl.copy();ItemStack next=pearl.getCraftingRemainingItem();
            helper.assertTrue(before.getDamageValue()==i && next.getDamageValue()==i+1 && pearl.getDamageValue()==i,"Pearl remainder mutated ingredient or missed a use");
            pearl=next;
        }
        helper.assertTrue(pearl.getCraftingRemainingItem().isEmpty() && !pearl.isEnchantable() && !pearl.getItem().isRepairable(pearl),"Final pearl usage or no-repair/no-enchantment rules changed");
        helper.assertTrue(CatalogModule.stack("primordial_pearl").getMaxDamage()==8,"Pearl form lifespan changed");
        helper.succeed();
    }
    @GameTest(template="empty") public static void lootBagActualUseLifecycleProducesRewardsAndHonorsGameMode(GameTestHelper helper) {
        var player=player(helper);
        for(GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) for(String id:new String[]{"loot_bag_common","loot_bag_uncommon","loot_bag_rare"}) {
            player.gameMode.changeGameModeForPlayer(mode);
            AABB area=player.getBoundingBox().inflate(2);
            for(var entity:helper.getLevel().getEntitiesOfClass(ItemEntity.class,area)) entity.discard();
            ItemStack bag=CatalogModule.stack(id);bag.setCount(2);player.setItemInHand(InteractionHand.OFF_HAND,bag);
            player.gameMode.useItem(player,helper.getLevel(),bag,InteractionHand.OFF_HAND);
            var drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,area);
            helper.assertTrue(player.getOffhandItem().getCount()==(mode==GameType.CREATIVE ? 2 : 1) && drops.size()>=8 && drops.size()<=12,
                "Loot bag real use/vanilla creative restoration failed "+id+" mode="+mode+" drops="+drops.size());
            helper.assertTrue(drops.stream().noneMatch(entity -> entity.getItem().isEmpty()),"Loot bag spawned an empty reward");
            for(var entity:drops) entity.discard();
        }
        helper.succeed();
    }
}
