package thaumcraft.crafting.ingredients;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;
import java.util.UUID;

/** Original early device recipes and the missing real ore source for nugget metadata 10. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EarlyDeviceIngredientGameTests {
    private static final BlockPos CENTER=new BlockPos(1,2,1);
    private EarlyDeviceIngredientGameTests() {}

    @GameTest(template="empty")
    public static void simpleMechanismBareArtificeUsesFiveItemsAndExactlyTenVis(GameTestHelper helper) {
        var bench=bench(helper); var player=player(helper,bench);
        mechanismInputs(bench,2);
        var menu=new ArcaneWorkbenchMenu(1,player.getInventory(),bench);
        setVis(helper,bench,30);
        var before=bench.saveWithoutMetadata(); var knowledge=KnowledgeStore.get(player).save();
        menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&knowledge.equals(KnowledgeStore.get(player).save())&&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==30,
                "Locked mechanism recipe paid ingredients, crystals, aura or research");
        stage(player,"BASEARTIFICE",1); menu.broadcastChanges();
        ArcaneRecipe recipe=bench.findRecipe(player);
        helper.assertTrue(recipe!=null&&recipe.research().equals("BASEARTIFICE")&&recipe.vis()==10
                &&recipe.crystalCost(1)==1&&recipe.crystalCost(2)==1&&menu.craftable(),
                "Original BASEARTIFICE started predicate or ten-vis Ignis/Aqua recipe changed");
        setVis(helper,bench,9); before=bench.saveWithoutMetadata();
        menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==9,"Underfunded mechanism partially paid");
        setVis(helper,bench,13); menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().is(CatalogModule.stack("mechanism_simple").getItem())
                &&menu.getCarried().getCount()==1&&!menu.getCarried().hasTag()
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==3,
                "Mechanism output or exact vis payment changed");
        for(int slot:new int[]{1,3,4,5,7})helper.assertTrue(bench.getItem(slot).getCount()==1,"Mechanism paid a wrong ingredient count");
        helper.assertTrue(bench.getItem(10).isEmpty()&&bench.getItem(11).isEmpty()
                &&KnowledgeStore.get(player).researchStage("BASEARTIFICE")==1
                &&KnowledgeStore.get(player).hasCraft("thaumcraft:mechanism_simple"),
                "Mechanism omitted crystals/crafting event or completed research while manufacturing");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void complexMechanismUsesTwoActuallyCraftedSimpleMechanismsAndFiftyVis(GameTestHelper helper) {
        var bench=bench(helper); var player=player(helper,bench); stage(player,"BASEARTIFICE",1); setVis(helper,bench,73);
        mechanismInputs(bench,1); ItemStack first=bench.craft(player);
        mechanismInputs(bench,1); ItemStack second=bench.craft(player);
        helper.assertTrue(first.is(stack("mechanism_simple",1).getItem())&&second.is(first.getItem())
                &&bench.isEmpty()&&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==53,
                "Two paid simple mechanisms were not produced for the complex route");
        bench.setItem(1,first); bench.setItem(7,second);
        bench.setItem(3,stack("plate_thaumium",2)); bench.setItem(5,stack("plate_thaumium",2)); bench.setItem(4,new ItemStack(Items.PISTON,2));
        bench.setItem(10,CatalogModule.aspectStack("crystal_essence",Aspect.FIRE,1)); bench.setItem(11,CatalogModule.aspectStack("crystal_essence",Aspect.WATER,1));
        var menu=new ArcaneWorkbenchMenu(4,player.getInventory(),bench); var recipe=bench.findRecipe(player);
        helper.assertTrue(recipe!=null&&recipe.research().equals("BASEARTIFICE")&&recipe.vis()==50&&recipe.crystalCost(1)==1&&recipe.crystalCost(2)==1,
                "Complex mechanism added a Golemancy gate or changed its original50visIgnis/Aqua recipe");
        var before=bench.saveWithoutMetadata(); setVis(helper,bench,49);
        menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==49,"Complex underfunding consumed either paid simple mechanism");
        setVis(helper,bench,53); menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().is(stack("mechanism_complex",1).getItem())&&menu.getCarried().getCount()==1
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==3&&bench.getItem(1).isEmpty()&&bench.getItem(7).isEmpty()
                &&bench.getItem(3).getCount()==1&&bench.getItem(5).getCount()==1&&bench.getItem(4).getCount()==1
                &&bench.getItem(10).isEmpty()&&bench.getItem(11).isEmpty()
                &&KnowledgeStore.get(player).hasCraft("thaumcraft:mechanism_complex")&&KnowledgeStore.get(player).researchStage("BASEARTIFICE")==1,
                "Complex mechanism failed exact transaction, crafting event or nonmutating started gate");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void morphicResonatorRequiresRareEarthNotQuicksilverAndPaysPhysicalCrystals(GameTestHelper helper) {
        var bench=bench(helper); var player=player(helper,bench); stage(player,"BASEALCHEMY",1);
        resonatorInputs(bench,stack("nugget_quicksilver",1),2);
        setVis(helper,bench,80);
        var before=bench.saveWithoutMetadata();
        helper.assertTrue(bench.findRecipe(player)==null&&bench.craft(player).isEmpty()&&before.equals(bench.saveWithoutMetadata()),
                "Quicksilver replaced original nugget metadata10 (rareearth)");
        var rare=CatalogModule.stack("nugget_rareearth"); rare.setCount(2); rare.getOrCreateTag().putString("ingredientFixture","preserveRemainingStack");
        bench.setItem(4,rare); ArcaneRecipe recipe=bench.findRecipe(player);
        helper.assertTrue(recipe!=null&&recipe.research().equals("BASEALCHEMY")&&recipe.vis()==50
                &&recipe.crystalCost(0)==1&&recipe.crystalCost(1)==1,
                "Morphic resonator changed BASEALCHEMY or original Aer/Ignis50vis payment");
        bench.removeItemNoUpdate(9); before=bench.saveWithoutMetadata();
        helper.assertTrue(bench.craft(player).isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==80,"Absent Aer crystal partially paid a resonator");
        bench.setItem(9,CatalogModule.aspectStack("crystal_essence",Aspect.AIR,1));
        setVis(helper,bench,49); before=bench.saveWithoutMetadata();
        helper.assertTrue(bench.craft(player).isEmpty()&&before.equals(bench.saveWithoutMetadata())
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==49,"49vis resonator attempt partially paid");
        setVis(helper,bench,54); var menu=new ArcaneWorkbenchMenu(2,player.getInventory(),bench);
        menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().is(CatalogModule.stack("morphic_resonator").getItem())
                &&menu.getCarried().getCount()==1&&!menu.getCarried().hasTag()
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==4,"Resonator did not pay exactly50vis for a clean output");
        for(int slot:new int[]{1,3,4,5,7})helper.assertTrue(bench.getItem(slot).getCount()==1,"Resonator paid wrong ingredient count");
        helper.assertTrue(bench.getItem(4).getTag().getString("ingredientFixture").equals("preserveRemainingStack")
                &&bench.getItem(9).isEmpty()&&bench.getItem(10).isEmpty()
                &&KnowledgeStore.get(player).researchStage("BASEALCHEMY")==1
                &&KnowledgeStore.get(player).hasCraft("thaumcraft:morphic_resonator"),
                "Remaining rare-earth NBT/crystals/crafting event or nonmutating gate changed");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void rareEarthBonusMatchesPinnedNineOreThresholdsAndSilkTouchExclusion(GameTestHelper helper) {
        var modifier=new RareEarthOreLootModifier(new LootItemCondition[0]);
        BlockState[] states={Blocks.DIAMOND_ORE.defaultBlockState(),Blocks.EMERALD_ORE.defaultBlockState(),
                Blocks.LAPIS_ORE.defaultBlockState(),Blocks.COAL_ORE.defaultBlockState(),Blocks.REDSTONE_ORE.defaultBlockState(),
                Blocks.NETHER_QUARTZ_ORE.defaultBlockState(),WorldModule.ORE_AMBER.get().defaultBlockState(),
                WorldModule.ORE_QUARTZ.get().defaultBlockState(),Blocks.REDSTONE_ORE.defaultBlockState().setValue(net.minecraft.world.level.block.RedStoneOreBlock.LIT,true)};
        double[] expected={.05,.075,.01,.001,.01,.01,.05,.05,.01};
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);
        for(int index=0;index<states.length;index++) {
            helper.assertTrue(RareEarthOreLootModifier.chance(states[index])==expected[index],"Pinned ore threshold changed at "+index);
            int paidBonuses=0;
            for(long sample=1;sample<=1024;sample++) {
                long seed=sample*0x9E3779B97F4A7C15L;
                var loot=new ObjectArrayList<ItemStack>(); var baseline=new ItemStack(Items.DIAMOND,3); loot.add(baseline);
                var context=context(helper,states[index],tool,seed);
                modifier.apply(loot,context);
                boolean bonus=RandomSource.create(seed).nextFloat()<expected[index];
                if(bonus)paidBonuses++;
                helper.assertTrue(loot.size()==(bonus?2:1)&&loot.get(0)==baseline&&loot.get(0).getCount()==3
                        &&(!bonus||(loot.get(1).is(CatalogModule.stack("nugget_rareearth").getItem())&&loot.get(1).getCount()==1)),
                        "Seeded original non-Fortune rare-earth bonus replaced vanilla drops or changed amount/threshold");
            }
            helper.assertTrue(paidBonuses>0,"Seeded threshold oracle did not exercise its positive bonus branch");
        }
        var silk=tool.copy(); silk.enchant(Enchantments.SILK_TOUCH,1);
        for(BlockState state:states)for(long sample=1;sample<=64;sample++) {
            long seed=sample*0x9E3779B97F4A7C15L;
            var loot=new ObjectArrayList<ItemStack>(); loot.add(new ItemStack(state.getBlock()));
            modifier.apply(loot,context(helper,state,silk,seed));
            helper.assertTrue(loot.size()==1,"Silk Touch produced rare earth");
        }
        for(BlockState excluded:new BlockState[]{Blocks.IRON_ORE.defaultBlockState(),Blocks.COPPER_ORE.defaultBlockState(),
                Blocks.GOLD_ORE.defaultBlockState(),WorldModule.ORE_CINNABAR.get().defaultBlockState(),Blocks.STONE.defaultBlockState()}) {
            helper.assertTrue(RareEarthOreLootModifier.chance(excluded)==0,"An unsupported ore gained an invented harvest bonus");
            var context=context(helper,excluded,tool,12); modifier.apply(new ObjectArrayList<>(),context);
            helper.assertTrue(context.getRandom().nextFloat()==RandomSource.create(12).nextFloat(),"Excluded ore consumed a harvest RNG roll");
        }
        helper.assertTrue(RareEarthOreLootModifier.chance(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState())==.05
                &&RareEarthOreLootModifier.chance(Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState())==.075,
                "Modern deepslate counterparts lost original ore-family thresholds");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void registeredOreLootProvidesActualPaidResonatorAndCentrifugeIngredients(GameTestHelper helper) {
        var state=Blocks.DIAMOND_ORE.defaultBlockState(); var tool=new ItemStack(Items.DIAMOND_PICKAXE);
        var table=helper.getLevel().getServer().getLootData().getLootTable(state.getBlock().getLootTable());
        ItemStack harvested=ItemStack.EMPTY;
        for(long sample=1;sample<=256;sample++) {
            long seed=sample*0x9E3779B97F4A7C15L;
            var params=params(helper,state,tool);
            var raw=new ObjectArrayList<ItemStack>(); table.getRandomItemsRaw(new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null),raw::add);
            var drops=table.getRandomItems(params,seed);
            int rawDiamonds=raw.stream().filter(stack->stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            int diamonds=drops.stream().filter(stack->stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(rawDiamonds>0&&diamonds==rawDiamonds,"Registered global modifier changed ordinary seeded diamond loot");
            var found=drops.stream().filter(stack->stack.is(CatalogModule.stack("nugget_rareearth").getItem())).findFirst();
            if(found.isPresent()) { harvested=found.get().copy(); break; }
        }
        helper.assertTrue(!harvested.isEmpty()&&harvested.getCount()==1,"Registered datapack/codec provided no real ore rare-earth source");
        var bench=bench(helper); var player=player(helper,bench);
        stage(player,"BASEALCHEMY",1); stage(player,"BASEARTIFICE",1); stage(player,"CENTRIFUGE",1);
        setVis(helper,bench,300);
        resonatorInputs(bench,harvested,1); var resonator=bench.craft(player);
        helper.assertTrue(resonator.is(CatalogModule.stack("morphic_resonator").getItem())&&bench.isEmpty(),"Actual harvested rare earth failed its paid resonator route");
        mechanismInputs(bench,1); var mechanism=bench.craft(player);
        helper.assertTrue(mechanism.is(CatalogModule.stack("mechanism_simple").getItem())&&bench.isEmpty(),"Real plates/stick failed original mechanism route");
        bench.setItem(1,new ItemStack(CatalogBlocks.block("tube"))); bench.setItem(7,new ItemStack(CatalogBlocks.block("tube")));
        bench.setItem(3,resonator); bench.setItem(4,new ItemStack(CatalogBlocks.block("metal_alchemical"))); bench.setItem(5,mechanism);
        bench.setItem(13,CatalogModule.aspectStack("crystal_essence",Aspect.ORDER,1));
        bench.setItem(14,CatalogModule.aspectStack("crystal_essence",Aspect.ENTROPY,1));
        var menu=new ArcaneWorkbenchMenu(3,player.getInventory(),bench); menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().is(CatalogBlocks.block("centrifuge").asItem())&&bench.isEmpty()
                &&AuraManager.getVis(helper.getLevel(),bench.getBlockPos())==140,
                "Physical chain failed or 50+10+100vis/crystals/real intermediate outputs were not paid");
        helper.succeed();
    }

    private static LootParams params(GameTestHelper helper,BlockState state,ItemStack tool) {
        return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.BLOCK_STATE,state)
                .withParameter(LootContextParams.TOOL,tool).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(helper.absolutePos(CENTER)))
                .create(LootContextParamSets.BLOCK);
    }
    private static LootContext context(GameTestHelper helper,BlockState state,ItemStack tool,long seed) {
        return new LootContext.Builder(params(helper,state,tool)).withOptionalRandomSeed(seed).create(null);
    }
    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper helper) {
        helper.setBlock(CENTER,ArcaneModule.WORKBENCH.get());
        return (ArcaneWorkbenchBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }
    private static ServerPlayer player(GameTestHelper helper,ArcaneWorkbenchBlockEntity bench) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"device_ingredients"));
        var pos=bench.getBlockPos(); player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5); return player;
    }
    private static void stage(ServerPlayer player,String key,int stage) {
        try { var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);
            method.setAccessible(true); method.invoke(KnowledgeStore.get(player),key,stage);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void setVis(GameTestHelper helper,ArcaneWorkbenchBlockEntity bench,float vis) {
        AuraManager.drainVis(helper.getLevel(),bench.getBlockPos(),Float.MAX_VALUE,false); AuraManager.addVis(helper.getLevel(),bench.getBlockPos(),vis);
    }
    private static ItemStack stack(String item,int count) {
        var registered=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",item));
        if(registered==null||registered==Items.AIR)throw new IllegalStateException("Missing registered ingredient "+item);
        return new ItemStack(registered,count);
    }
    private static void mechanismInputs(ArcaneWorkbenchBlockEntity bench,int count) {
        bench.clearContent(); bench.setItem(1,stack("plate_brass",count)); bench.setItem(7,stack("plate_brass",count));
        bench.setItem(3,stack("plate_iron",count)); bench.setItem(5,stack("plate_iron",count)); bench.setItem(4,new ItemStack(Items.STICK,count));
        bench.setItem(10,CatalogModule.aspectStack("crystal_essence",Aspect.FIRE,1)); bench.setItem(11,CatalogModule.aspectStack("crystal_essence",Aspect.WATER,1));
    }
    private static void resonatorInputs(ArcaneWorkbenchBlockEntity bench,ItemStack core,int count) {
        bench.clearContent(); bench.setItem(1,new ItemStack(Items.GLASS_PANE,count)); bench.setItem(7,new ItemStack(Items.GLASS_PANE,count));
        bench.setItem(3,stack("plate_brass",count)); bench.setItem(5,stack("plate_brass",count)); var center=core.copy(); center.setCount(count); bench.setItem(4,center);
        bench.setItem(9,CatalogModule.aspectStack("crystal_essence",Aspect.AIR,1)); bench.setItem(10,CatalogModule.aspectStack("crystal_essence",Aspect.FIRE,1));
    }
}
