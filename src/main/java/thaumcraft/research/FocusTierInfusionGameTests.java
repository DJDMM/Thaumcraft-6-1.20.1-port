package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.airborne.AirborneEssentiaManager;
import thaumcraft.infusion.*;
import thaumcraft.scanning.ScanningModule;

import java.util.*;

/** Physical 25/50-capacity upgrades through the real matrix; old parent completion is an explicit fixture. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class FocusTierInfusionGameTests {
    @GameTest(template="empty")
    public static void advancedAndGreaterRequireStrictParentsAndOwningTheNewFocusNeverCreatesCraftEvidence(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"BASEAUROMANCY"); state.setResearchStage("INFUSION",1);
        result(h,ResearchNetwork.processAdvance(p,"FOCUSADVANCED",0),ResearchProgression.Result.LOCKED);
        complete(state,"INFUSION"); result(h,ResearchNetwork.processAdvance(p,"FOCUSADVANCED",0),ResearchProgression.Result.STARTED);
        var advanced=recipe(h,"focus_2");
        h.assertTrue(advanced.research().equals("FOCUSADVANCED@1") && advanced.unlocked(state) && advanced.instability()==3
                        && advanced.aspects().getAmount(thaumcraft.api.aspects.Aspect.MAGIC)==25 && advanced.aspects().getAmount(thaumcraft.api.aspects.Aspect.ORDER)==50,
                "Advanced upgrade changed original stage gate, instability or essentia");
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"AUROMANCY",35); p.getInventory().setItem(4,CatalogModule.stack("focus_2"));
        missing(h,p,"FOCUSADVANCED"); h.assertTrue(!state.hasCraft("thaumcraft:focus_2"),"Possession manufactured Advanced craft proof");
        complete(state,"FOCUSADVANCED");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSGREATER",0),ResearchProgression.Result.LOCKED);
        scanPearl(p,7); result(h,ResearchNetwork.processAdvance(p,"FOCUSGREATER",0),ResearchProgression.Result.STARTED);
        var greater=recipe(h,"focus_3");
        h.assertTrue(greater.research().equals("FOCUSGREATER@1") && greater.unlocked(state) && greater.instability()==5
                        && greater.aspects().getAmount(thaumcraft.api.aspects.Aspect.MAGIC)==25 && greater.aspects().getAmount(thaumcraft.api.aspects.Aspect.ORDER)==50
                        && greater.aspects().getAmount(thaumcraft.api.aspects.Aspect.VOID)==100,
                "Greater upgrade changed original stage gate, instability or essentia");
        p.getInventory().setItem(5,CatalogModule.stack("focus_3"));
        missing(h,p,"FOCUSGREATER"); h.assertTrue(!state.hasCraft("thaumcraft:focus_3"),"Possession manufactured Greater craft proof");
        h.succeed();
    }

    @GameTest(template="essentia_production")
    public static void actualAdvancedInfusionRecordsCraftPaysExactEssentiaAndCompletesItsResearch(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"BASEAUROMANCY"); complete(state,"INFUSION");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSADVANCED",0),ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        ItemStack input=CatalogModule.stack("focus_1"); input.getOrCreateTag().putString("oldPackage","replace rather than mutate");
        input.setHoverName(Component.literal("Old Lesser Focus"));
        var f=altar(h,p,"focus_2",input,0); craft(h,f,false);
        ItemStack output=f.central().getItem(0);
        h.assertTrue(output.is(CatalogModule.stack("focus_2").getItem()) && FocusStacks.maxComplexity(output)==25
                        && !output.hasCustomHoverName() && (output.getTag()==null || !output.getTag().contains("oldPackage"))
                        && state.hasCraft("thaumcraft:focus_2") && !state.hasCraft("thaumcraft:focus_3"),
                "Actual matrix failed25capacity craft proof or improperly copied old focus metadata");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSADVANCED",1),ResearchProgression.Result.COMPLETE);
        var saved=state.save(); int xp=p.totalExperience;
        h.assertTrue(state.researchStage("FOCUSADVANCED")==3 && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3
                        && PlayerKnowledge.load(saved).isResearchCompleteStrict("FOCUSADVANCED"),"Advanced changed32raw cost or completion save");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSADVANCED",1),ResearchProgression.Result.STALE);
        h.assertTrue(saved.equals(state.save()) && xp==p.totalExperience,"Advanced replay paid again"); h.succeed();
    }

    @GameTest(template="essentia_production")
    public static void actualGreaterInfusionConsumesMoteResumesPaidSaveAndKeepsFireProofRequired(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); complete(state,"BASEAUROMANCY"); complete(state,"FOCUSADVANCED");
        scanPearl(p,7); result(h,ResearchNetwork.processAdvance(p,"FOCUSGREATER",0),ResearchProgression.Result.STARTED);
        KnowledgeStore.addKnowledge(p,KnowledgeType.THEORY,"AUROMANCY",35);
        var f=altar(h,p,"focus_3",CatalogModule.stack("focus_2"),7); craft(h,f,true);
        h.assertTrue(f.central().getItem(0).is(CatalogModule.stack("focus_3").getItem()) && FocusStacks.maxComplexity(f.central().getItem(0))==50
                        && state.hasCraft("thaumcraft:focus_3") && !state.isResearchCompleteStrict("f_onfire"),
                "Greater manufacture omitted50capacity proof or invented a fire injury");
        missing(h,p,"FOCUSGREATER");
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(p,p.damageSources().onFire(),1));
        h.assertTrue(state.isResearchCompleteStrict("f_onfire"),"Original stage2 fire source hook failed Greater requirement");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSGREATER",1),ResearchProgression.Result.COMPLETE);
        var saved=state.save(); int xp=p.totalExperience;
        h.assertTrue(state.researchStage("FOCUSGREATER")==3 && state.rawKnowledge(KnowledgeType.THEORY,"AUROMANCY")==3
                        && PlayerKnowledge.load(saved).isResearchCompleteStrict("FOCUSGREATER"),"Greater changed32raw cost or persistent completion");
        result(h,ResearchNetwork.processAdvance(p,"FOCUSGREATER",1),ResearchProgression.Result.STALE);
        h.assertTrue(saved.equals(state.save()) && xp==p.totalExperience,"Greater replay paid again"); h.succeed();
    }

    @GameTest(template="empty")
    public static void tierRecipesRequireExactPhysicalComponentsAndAcceptEveryReleasedPearlState(GameTestHelper h) {
        var p=player(h); var state=KnowledgeStore.get(p); var advanced=recipe(h,"focus_2"); var greater=recipe(h,"focus_3");
        List<ItemStack> a=List.of(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver"))),new ItemStack(Items.DIAMOND),new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver"))),new ItemStack(Items.ENDER_PEARL));
        h.assertTrue(advanced.plan(state,CatalogModule.stack("focus_1"),a,p.getRandom()).isEmpty(),"Unknown Advanced recipe manufactured a tier upgrade");
        state.setResearchStage("FOCUSADVANCED",1);
        h.assertTrue(advanced.plan(state,CatalogModule.stack("focus_1"),a,p.getRandom()).isPresent(),"Original Advanced@1 gate required full completion and deadlocked its craft");
        List<ItemStack> wrong=List.of(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver"))),new ItemStack(Items.DIAMOND),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.ENDER_PEARL));
        h.assertTrue(advanced.plan(state,CatalogModule.stack("focus_1"),wrong,p.getRandom()).isEmpty(),"Missing second quicksilver was accepted");
        state.setResearchStage("FOCUSGREATER",1);
        for(int wear:new int[]{0,3,7}) {
            var pearl=CatalogModule.stack("primordial_pearl"); pearl.setDamageValue(wear);
            List<ItemStack> g=List.of(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver"))),pearl,new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver"))),new ItemStack(Items.NETHER_STAR));
            var plan=greater.plan(state,CatalogModule.stack("focus_2"),g,p.getRandom());
            h.assertTrue(plan.isPresent() && plan.get().aspects().getAmount(thaumcraft.api.aspects.Aspect.VOID)==100,
                    "Released wildcard pearl state failed Greater upgrade: "+wear);
            h.assertTrue(greater.plan(state,CatalogModule.stack("focus_1"),g,p.getRandom()).isEmpty(),"Greater recipe skipped its physical Advanced central focus");
        }
        h.succeed();
    }

    private record Fixture(GameTestHelper h,ServerPlayer p,BlockPos pos,InfusionMatrixBlockEntity matrix,List<BlockPos> components,List<EssentiaJarBlockEntity> jars) {
        InfusionPedestalBlockEntity central() { return (InfusionPedestalBlockEntity)h.getLevel().getBlockEntity(pos.below(2)); }
    }
    private static Fixture altar(GameTestHelper h,ServerPlayer p,String key,ItemStack input,int pearlDamage) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(6,8,6));
        for(var at:BlockPos.betweenClosed(pos.offset(-8,-7,-8),pos.offset(8,3,8))) level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos,CatalogBlocks.block("infusion_matrix").defaultBlockState());
        level.setBlockAndUpdate(pos.below(2),CatalogBlocks.block("pedestal_arcane").defaultBlockState());
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1}) level.setBlockAndUpdate(pos.offset(x,-2,z),CatalogBlocks.block("pillar_arcane").defaultBlockState());
        String[] colors={"white","orange","magenta","lightblue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black"};
        for(int i=0;i<colors.length;i++) for(int sign:new int[]{-1,1}) {
            var at=pos.offset(sign*(2+i%6),-2,sign*(-3-i/6));var state=CatalogBlocks.block("candle_"+colors[i]).defaultBlockState();
            // Working BETA26 candles cannot float in the altar's cleared air layer.
            h.assertTrue(level.getBlockState(at.below()).isAir()&&!state.canSurvive(level,at),"Cleared tier candle fixture unexpectedly had support");
            level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(at,state);
        }
        for(int i=0;i<colors.length;i++) for(int sign:new int[]{-1,1}) {
            var at=pos.offset(sign*(2+i%6),-2,sign*(-3-i/6));var state=level.getBlockState(at);
            h.assertTrue(state.is(CatalogBlocks.block("candle_"+colors[i]))&&state.canSurvive(level,at),"Supported tier candle pair disappeared during fixture placement");
        }
        h.assertTrue(Math.abs(thaumcraft.infusion.InfusionStability.scan(level,pos).gain()-1.6F)<.00001F,"Physical tier fixture lost one of its original sixteen candle pairs");
        List<ItemStack> items=new ArrayList<>(); items.add(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver"))));
        if(key.equals("focus_2")) items.add(new ItemStack(Items.DIAMOND)); else { var pearl=CatalogModule.stack("primordial_pearl"); pearl.setDamageValue(pearlDamage); items.add(pearl); }
        items.add(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(InfusionModule.id("quicksilver")))); items.add(new ItemStack(key.equals("focus_2")?Items.ENDER_PEARL:Items.NETHER_STAR));
        int[][] offsets={{3,0},{0,3},{-3,0},{0,-3}}; List<BlockPos> components=new ArrayList<>();
        for(int i=0;i<items.size();i++) { var at=pos.offset(offsets[i][0],-2,offsets[i][1]); level.setBlockAndUpdate(at,CatalogBlocks.block("pedestal_arcane").defaultBlockState());
            ((InfusionPedestalBlockEntity)level.getBlockEntity(at)).setItem(0,items.get(i)); components.add(at); }
        var cost=recipe(h,key).aspects(); List<EssentiaJarBlockEntity> jars=new ArrayList<>(); int index=0;
        for(var aspect:cost.getAspects()) { var at=pos.offset(index++-1,-1,6); level.setBlockAndUpdate(at,CatalogBlocks.block("jar_normal").defaultBlockState());
            var jar=(EssentiaJarBlockEntity)level.getBlockEntity(at); h.assertTrue(jar.addExact(aspect,cost.getAmount(aspect)+3),"Cannot fill real focus-upgrade jar"); jars.add(jar); }
        var matrix=(InfusionMatrixBlockEntity)level.getBlockEntity(pos); var tag=matrix.saveWithoutMetadata(); tag.putFloat("stability",25); matrix.load(tag);
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3.5); var f=new Fixture(h,p,pos,matrix,List.copyOf(components),List.copyOf(jars)); f.central().setItem(0,input);
        return f;
    }
    private static void craft(GameTestHelper h,Fixture f,boolean saveReload) {
        // Verified mapping from build/createMcpToSrg/output.tsrg. Register only the
        // real UUID lookup for the matrix's owner callback, without a login/world input.
        Map<UUID,ServerPlayer> byUUID=ObfuscationReflectionHelper.getPrivateValue(PlayerList.class,h.getLevel().getServer().getPlayerList(),"f_11197_");
        byUUID.put(f.p.getUUID(),f.p);
        try {
            AirborneEssentiaManager.forgetConsumer(f.matrix);
            h.assertTrue(f.matrix.useCaster(f.p) && f.matrix.active() && !f.matrix.crafting(),"First actual caster use failed altar activation");
            h.assertTrue(f.matrix.useCaster(f.p) && f.matrix.crafting(),"Second actual caster use failed focus-tier start");
            InfusionMatrixBlockEntity tile=f.matrix;
            if(saveReload) {
                ticks(f,tile,37); var saved=tile.saveWithoutMetadata();
                h.assertTrue(saved.hasUUID("owner") && tile.crafting(),"Greater paid plan did not retain its real owner");
                var replacement=new InfusionMatrixBlockEntity(f.pos,tile.getBlockState()); replacement.load(saved);
                h.getLevel().getChunkAt(f.pos).addAndRegisterBlockEntity(replacement);
                h.assertTrue(saved.equals(replacement.saveWithoutMetadata()),"Paid tier-plan save changed costs, input or counters"); tile=replacement;
            }
            ticks(f,tile,2500);
            h.assertTrue(!tile.crafting() && f.jars.stream().allMatch(j->j.amount()==3)
                            && f.components.stream().allMatch(at->((InfusionPedestalBlockEntity)h.getLevel().getBlockEntity(at)).getItem(0).isEmpty()),
                    "Focus upgrade did not finish, repaid essentia after save or failed physical reagent consumption");
        } finally { byUUID.remove(f.p.getUUID(),f.p); }
    }
    private static void ticks(Fixture f,InfusionMatrixBlockEntity tile,int count) {
        for(int i=0;i<count;i++) InfusionMatrixBlockEntity.tick(f.h.getLevel(),f.pos,tile.getBlockState(),tile);
    }
    private static InfusionRecipe recipe(GameTestHelper h,String key) { return (InfusionRecipe)h.getLevel().getRecipeManager().byKey(InfusionModule.id("infusion/"+key)).orElseThrow(); }
    private static ServerPlayer player(GameTestHelper h) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"focus_tier")) {
            @Override protected ItemCooldowns createItemCooldowns() { return new ItemCooldowns(); }
            @Override public void displayClientMessage(Component message,boolean actionBar) { }
        }; p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get())); return p;
    }
    private static void complete(PlayerKnowledge state,String key) { state.setResearchStage(key,ResearchCatalog.get(key).stages().size()+1); }
    private static void scanPearl(ServerPlayer p,int damage) {
        var pearl=CatalogModule.stack("primordial_pearl"); pearl.setDamageValue(damage);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ScanningModule.THAUMOMETER.get())); p.setItemInHand(InteractionHand.OFF_HAND,pearl); p.setShiftKeyDown(true);
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get()); ScanningModule.THAUMOMETER.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
    }
    private static void missing(GameTestHelper h,ServerPlayer p,String key) {
        var before=KnowledgeStore.get(p).save(); int xp=p.totalExperience; var items=new net.minecraft.nbt.ListTag(); p.getInventory().save(items);
        result(h,ResearchNetwork.processAdvance(p,key,1),ResearchProgression.Result.MISSING_REQUIREMENTS);
        var after=new net.minecraft.nbt.ListTag(); p.getInventory().save(after);
        h.assertTrue(before.equals(KnowledgeStore.get(p).save()) && xp==p.totalExperience && items.equals(after),"Missing focus-tier condition partly paid research");
    }
    private static void result(GameTestHelper h,ResearchProgression.Result actual,ResearchProgression.Result wanted) { h.assertTrue(actual==wanted,"Expected "+wanted+", got "+actual); }
}
