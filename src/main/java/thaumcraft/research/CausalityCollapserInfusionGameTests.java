package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.infusion.InfusionMatrixBlockEntity;
import thaumcraft.infusion.InfusionModule;
import thaumcraft.infusion.InfusionPedestalBlockEntity;
import thaumcraft.infusion.InfusionRecipe;
import thaumcraft.infusion.InfusionRecipes;
import thaumcraft.infusion.InfusionStability;
import thaumcraft.world.rift.collapser.CausalityCollapserItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Physical recipe selection and native altar ticks establish real costs/output, not a supplied device. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CausalityCollapserInfusionGameTests {
    @GameTest(template="empty")
    public static void originalCollapserRecipeRetainsStartedGateHundredEssentiaAndEightPhysicalComponents(GameTestHelper h) {
        var p=player(h); var k=KnowledgeStore.get(p); var recipe=recipe(h); var ingredients=components();
        h.assertTrue(recipe.research().equals("RIFTCLOSER") && recipe.instability()==8 && recipe.aspects().size()==2
                && recipe.aspects().getAmount(Aspect.ELDRITCH)==50 && recipe.aspects().getAmount(Aspect.FLUX)==50
                && recipe.components(new ItemStack(Items.TNT)).size()==8,"Original RIFTCLOSER/8/50Alienis+50Vitium/eight-component contract changed");
        h.assertTrue(recipe.plan(k,new ItemStack(Items.TNT),ingredients,p.getRandom()).isEmpty(),"Unstarted RIFTCLOSER manufactured a collapser");
        startCloser(h,p);
        h.assertTrue(!k.isResearchCompleteStrict("RIFTCLOSER") && recipe.plan(k,new ItemStack(Items.TNT),ingredients,p.getRandom()).isPresent(),
                "Original bare started recipe gate was replaced with a completed-research gate");
        h.assertTrue(recipe.plan(k,new ItemStack(Items.GUNPOWDER),ingredients,p.getRandom()).isEmpty(),"Central TNT was optional");
        var wrong=new ArrayList<>(ingredients); wrong.set(5,new ItemStack(Items.REDSTONE));
        h.assertTrue(recipe.plan(k,new ItemStack(Items.TNT),wrong,p.getRandom()).isEmpty(),"Second actual redstone block could be replaced by dust");
        wrong=new ArrayList<>(ingredients); wrong.remove(6);
        h.assertTrue(recipe.plan(k,new ItemStack(Items.TNT),wrong,p.getRandom()).isEmpty(),"Second Alumentum component was omitted");
        h.assertTrue(InfusionRecipes.find(p,new ItemStack(Items.TNT),ingredients).orElseThrow().output().getItem() instanceof CausalityCollapserItem,
                "Server recipe selector still returns a visual-only collapser"); h.succeed();
    }

    @GameTest(template="empty")
    public static void bothNitorIngredientsAcceptEveryOriginalColorAndExactResonatorsRemainRequired(GameTestHelper h) {
        var p=player(h); startCloser(h,p); var recipe=recipe(h); var k=KnowledgeStore.get(p);
        for(String color:List.of("white","orange","magenta","lightblue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black")) {
            var ingredients=new ArrayList<>(components()); var nitor=item(color.equals("yellow")?"nitor":"nitor_"+color);
            h.assertTrue(!nitor.isEmpty(),"Native Nitor variant missing "+color); ingredients.set(3,nitor); ingredients.set(7,nitor.copy());
            h.assertTrue(recipe.plan(k,new ItemStack(Items.TNT),ingredients,p.getRandom()).isPresent(),"Original alternative Nitor color failed either reagent slot "+color);
        }
        for(int index:new int[]{0,4}) {
            var wrong=new ArrayList<>(components()); wrong.set(index,item(index==0?"vis_resonator":"morphic_resonator"));
            h.assertTrue(recipe.plan(k,new ItemStack(Items.TNT),wrong,p.getRandom()).isEmpty(),"One resonator type replaced the other original physical component");
        } h.succeed();
    }

    @GameTest(template="essentia_network",timeoutTicks=1200,batch="rift_infusion")
    public static void nativeAltarPaysFiftyOfBothAspectsAndAllComponentsBeforeOutputCanBeThrown(GameTestHelper h) {
        var p=player(h); startCloser(h,p); var k=KnowledgeStore.get(p);
        KnowledgeStore.addKnowledge(p,KnowledgeType.OBSERVATION,"AUROMANCY",16);
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"ALCHEMY",64);
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"AUROMANCY",32);
        result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",1),ResearchProgression.Result.COMPLETE);
        var f=altar(h,p); Map<UUID,ServerPlayer> owners=ObfuscationReflectionHelper.getPrivateValue(PlayerList.class,h.getLevel().getServer().getPlayerList(),"f_11197_");
        owners.put(p.getUUID(),p); p.setItemInHand(InteractionHand.MAIN_HAND,item("caster_basic"));
        useMatrix(h,p,f.pos()); h.assertTrue(f.matrix().active() && !f.matrix().crafting(),"Native caster block callback failed activation");
        // The ordinary ticker charges stability from sixteen physical original candle pairs.
        // No saved working plan, supplied collapser or manual matrix tick substitutes for production.
        h.runAfterDelay(90,()->{
            h.assertTrue(f.matrix().stability()==25F,"Native idle altar did not charge its real candle stability");
            useMatrix(h,p,f.pos());
            h.assertTrue(f.matrix().crafting() && f.matrix().instability()==8 && f.matrix().remainingItems()==8
                    && f.matrix().getAspects().getAmount(Aspect.ELDRITCH)==50 && f.matrix().getAspects().getAmount(Aspect.FLUX)==50,
                    "Paid native craft start changed recipe costs/reagent plan");
        });
        h.runAfterDelay(1100,()->{
            try {
                h.assertTrue(!f.matrix().crafting() && f.central().getItem(0).getItem() instanceof CausalityCollapserItem
                        && f.central().getItem(0).getCount()==1 && f.jars().stream().allMatch(jar->jar.amount()==3)
                        && f.components().stream().allMatch(at->((InfusionPedestalBlockEntity)h.getLevel().getBlockEntity(at)).isEmpty()),
                        "Native altar did not pay exactly100 essentia/eight reagents/TNT for one operational output");
                h.assertTrue(k.hasCraft("thaumcraft:causality_collapser") && p.totalExperience==10
                        && k.rawKnowledge(KnowledgeType.OBSERVATION,"AUROMANCY")==0 && k.rawKnowledge(KnowledgeType.THEORY,"ALCHEMY")==0
                        && k.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==0,"Actual output callback missed proof or repaid closer knowledge/XP");
                var output=f.central().removeItem(0,1); p.setItemInHand(InteractionHand.MAIN_HAND,output);
                output.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
                var projectiles=h.getLevel().getEntitiesOfClass(thaumcraft.world.rift.collapser.CausalityCollapserEntity.class,
                        new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(8),entity->entity.getOwner()==p);
                h.assertTrue(output.isEmpty() && projectiles.size()==1,"Actually manufactured output failed its real throwable action");
                projectiles.forEach(net.minecraft.world.entity.Entity::discard); h.succeed();
            } finally {owners.remove(p.getUUID(),p);}
        });
    }

    private record Fixture(BlockPos pos,InfusionMatrixBlockEntity matrix,InfusionPedestalBlockEntity central,
                           List<BlockPos> components,List<EssentiaJarBlockEntity> jars) {}
    private static Fixture altar(GameTestHelper h,ServerPlayer p) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(6,25,6));
        for(var at:BlockPos.betweenClosed(pos.offset(-8,-7,-8),pos.offset(8,3,8))) level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos,CatalogBlocks.block("infusion_matrix").defaultBlockState());
        level.setBlockAndUpdate(pos.below(2),CatalogBlocks.block("pedestal_arcane").defaultBlockState());
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1}) level.setBlockAndUpdate(pos.offset(x,-2,z),CatalogBlocks.block("pillar_arcane").defaultBlockState());
        var colors=List.of("white","orange","magenta","lightblue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black");
        for(int i=0;i<colors.size();i++) for(int sign:new int[]{-1,1}) {
            var at=pos.offset(sign*(2+i%6),-2,sign*(-3-i/6)); level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(at,CatalogBlocks.block("candle_"+colors.get(i)).defaultBlockState());
        }
        int[][] offsets={{3,0},{0,3},{-3,0},{0,-3},{4,2},{-4,-2},{2,4},{-2,-4}};
        var reagents=components(); var components=new ArrayList<BlockPos>();
        for(int i=0;i<reagents.size();i++) {
            var at=pos.offset(offsets[i][0],-2,offsets[i][1]); level.setBlockAndUpdate(at,CatalogBlocks.block("pedestal_arcane").defaultBlockState());
            ((InfusionPedestalBlockEntity)level.getBlockEntity(at)).setItem(0,reagents.get(i)); components.add(at);
        }
        // All four physical ingredient pairs have exactly the same count, retaining symmetry.
        h.assertTrue(Math.abs(InfusionStability.scan(level,pos).gain()-1.6F)<.00001F,"Original sixteen colored candle pairs lost their real stability contribution");
        var jars=new ArrayList<EssentiaJarBlockEntity>(); int i=0;
        for(Aspect aspect:List.of(Aspect.ELDRITCH,Aspect.FLUX)) {
            var at=pos.offset(-1+i++*2,-1,6); level.setBlockAndUpdate(at,CatalogBlocks.block("jar_normal").defaultBlockState());
            var jar=(EssentiaJarBlockEntity)level.getBlockEntity(at); h.assertTrue(jar.addExact(aspect,53),"Cannot supply physical original essentia source"); jars.add(jar);
        }
        var central=(InfusionPedestalBlockEntity)level.getBlockEntity(pos.below(2)); central.setItem(0,new ItemStack(Items.TNT));
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3.5);
        return new Fixture(pos,(InfusionMatrixBlockEntity)level.getBlockEntity(pos),central,List.copyOf(components),List.copyOf(jars));
    }
    private static void useMatrix(GameTestHelper h,ServerPlayer p,BlockPos pos) {
        var state=h.getLevel().getBlockState(pos);
        h.assertTrue(state.getBlock().use(state,h.getLevel(),pos,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction(),"Physical matrix ignored the real caster hand");
    }
    private static List<ItemStack> components() {return List.of(item("morphic_resonator"),new ItemStack(Items.REDSTONE_BLOCK),item("alumentum"),item("nitor"),item("vis_resonator"),new ItemStack(Items.REDSTONE_BLOCK),item("alumentum"),item("nitor"));}
    private static InfusionRecipe recipe(GameTestHelper h) {return (InfusionRecipe)h.getLevel().getRecipeManager().byKey(InfusionModule.id("infusion/causalitycollapser")).orElseThrow();}
    private static void startCloser(GameTestHelper h,ServerPlayer p) {
        var k=KnowledgeStore.get(p); for(String parent:List.of("FLUXRIFT","INFUSION","VISBATTERY")) k.setResearchStage(parent,ResearchCatalog.get(parent).stages().size()+1);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get())); result(h,ResearchNetwork.processAdvance(p,"RIFTCLOSER",0),ResearchProgression.Result.STARTED);
    }
    private static ServerPlayer player(GameTestHelper h) {return new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"collapser_craft"));}
    private static ItemStack item(String path) {return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft",path)));}
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result expected) {h.assertTrue(actual==expected,"Expected "+expected+", got "+actual);}
}
