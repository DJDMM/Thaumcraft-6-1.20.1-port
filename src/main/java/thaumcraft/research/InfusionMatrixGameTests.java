package thaumcraft.research;
import thaumcraft.infusion.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import thaumcraft.alchemy.*;
import thaumcraft.api.aspects.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.research.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class InfusionMatrixGameTests {
    private static final String TEMPLATE="essentia_production";
    private record Fixture(GameTestHelper helper, BlockPos pos, InfusionMatrixBlockEntity matrix, ServerPlayer player) {
        InfusionPedestalBlockEntity central() { return (InfusionPedestalBlockEntity)helper.getLevel().getBlockEntity(pos.below(2)); }
    }
    private static Fixture altar(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(6,8,6)); var level=h.getLevel();
        for(BlockPos p:BlockPos.betweenClosed(pos.offset(-8,-7,-8),pos.offset(8,3,8)))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos,CatalogBlocks.block("infusion_matrix").defaultBlockState());
        level.setBlockAndUpdate(pos.below(2),CatalogBlocks.block("pedestal_arcane").defaultBlockState());
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})level.setBlockAndUpdate(pos.offset(x,-2,z),CatalogBlocks.block("pillar_arcane").defaultBlockState());
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"infusion_matrix"));player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3.5);
        var matrix=(InfusionMatrixBlockEntity)level.getBlockEntity(pos);var tag=matrix.saveWithoutMetadata();tag.putFloat("stability",25);matrix.load(tag);
        return new Fixture(h,pos,matrix,player);
    }
    private static ItemStack stack(String id) { return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(InfusionModule.id(id))); }
    private static List<BlockPos> components(Fixture f,List<ItemStack> items) {
        List<BlockPos> positions=new ArrayList<>();int[][] offsets={{3,0},{-3,0},{0,3},{0,-3},{4,1},{-4,-1},{1,4},{-1,-4},{4,-1},{-4,1}};
        for(int i=0;i<items.size();i++) { var pos=f.pos.offset(offsets[i][0],-2,offsets[i][1]);f.helper.getLevel().setBlockAndUpdate(pos,CatalogBlocks.block("pedestal_arcane").defaultBlockState());
            ((InfusionPedestalBlockEntity)f.helper.getLevel().getBlockEntity(pos)).setItem(0,items.get(i));positions.add(pos); }
        return positions;
    }
    private static List<EssentiaJarBlockEntity> jars(Fixture f,AspectList costs,int extra) {
        List<EssentiaJarBlockEntity> jars=new ArrayList<>();int i=0;
        for(Aspect aspect:costs.getAspects()) { BlockPos pos=f.pos.offset(i++-3,-2,5);f.helper.getLevel().setBlockAndUpdate(pos,CatalogBlocks.block("jar_normal").defaultBlockState());
            var jar=(EssentiaJarBlockEntity)f.helper.getLevel().getBlockEntity(pos);f.helper.assertTrue(jar.addExact(aspect,costs.getAmount(aspect)+extra),"Cannot fill fixture jar");jars.add(jar); }
        return jars;
    }
    private static void ticks(Fixture f,int ticks) { for(int i=0;i<ticks;i++)InfusionMatrixBlockEntity.tick(f.helper.getLevel(),f.pos,f.matrix.getBlockState(),f.matrix); }
    private static InfusionRecipe recipe(GameTestHelper h,String id) { return (InfusionRecipe)h.getLevel().getRecipeManager().byKey(InfusionModule.id("infusion/"+id)).orElseThrow(); }
    private static Fixture crystal(GameTestHelper h) {
        Fixture f=altar(h);KnowledgeStore.get(f.player).setResearchStage("CRYSTALFARMER",1); f.central().setItem(0,AspectCrystalItem.create(Aspect.AIR));
        components(f,List.of(new ItemStack(Items.WHEAT_SEEDS),new ItemStack(AlchemyModule.SALIS_MUNDUS.get())));return f;
    }
    private static void start(Fixture f) { f.helper.assertTrue(f.matrix.useCaster(f.player)&&f.matrix.active()&&!f.matrix.crafting(),"First click did not activate");
        f.helper.assertTrue(f.matrix.useCaster(f.player)&&f.matrix.crafting(),"Second click did not start"); }
    private static void stableCandles(Fixture f) {
        String[] colors={"white","orange","magenta","lightblue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black"};
        for(int i=0;i<colors.length;i++)for(int sign:new int[]{-1,1}) {
            var at=f.pos.offset(sign*(2+i%6),-2,sign*(-3-i/6));var state=CatalogBlocks.block("candle_"+colors[i]).defaultBlockState();
            // The altar clears this layer to air. Working BETA26 candles need a real
            // upper support face; later adjacent placements otherwise drop earlier pairs.
            f.helper.assertTrue(f.helper.getLevel().getBlockState(at.below()).isAir()&&!state.canSurvive(f.helper.getLevel(),at),"Cleared candle fixture unexpectedly had support");
            f.helper.getLevel().setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
            f.helper.getLevel().setBlockAndUpdate(at,state);
        }
        for(int i=0;i<colors.length;i++)for(int sign:new int[]{-1,1}) {
            var at=f.pos.offset(sign*(2+i%6),-2,sign*(-3-i/6));var state=f.helper.getLevel().getBlockState(at);
            f.helper.assertTrue(state.is(CatalogBlocks.block("candle_"+colors[i]))&&state.canSurvive(f.helper.getLevel(),at),"Supported candle pair disappeared during fixture placement");
        }
        f.helper.assertTrue(Math.abs(InfusionStability.scan(f.helper.getLevel(),f.pos).gain()-1.6F)<.00001F,"Physical fixture lost one of its original sixteen candle pairs");
    }

    @GameTest(template=TEMPLATE) public static void realCrystalRecipeDebitsExactSourcesAndNeverDuplicatesFinish(GameTestHelper h) {
        Fixture f=crystal(h);var jars=jars(f,recipe(h,"crystalclusterair").aspects(),17);start(f);ticks(f,500);
        h.assertTrue(!f.matrix.crafting()&&f.central().getItem(0).is(CatalogBlocks.block("crystal_aer").asItem()),"Crystal did not finish");
        h.assertTrue(jars.stream().allMatch(jar->jar.amount()==17)&&f.matrix.remainingItems()==0,"Wrong exact source/reagent debit");
        var before=f.central().saveWithoutMetadata();ticks(f,150);h.assertTrue(before.equals(f.central().saveWithoutMetadata())&&jars.stream().allMatch(jar->jar.amount()==17),"Completed operation ran twice");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void saveReloadResumesPaidEssentiaWithoutRechargingOrResettingDelay(GameTestHelper h) {
        Fixture f=crystal(h);var jars=jars(f,recipe(h,"crystalclusterair").aspects(),21);start(f);ticks(f,37);
        int remaining=Arrays.stream(f.matrix.getAspects().getAspects()).mapToInt(f.matrix.getAspects()::getAmount).sum();h.assertTrue(remaining==18,"One-unit cadence changed");
        CompoundTag saved=f.matrix.saveWithoutMetadata();var replacement=new InfusionMatrixBlockEntity(f.pos,f.matrix.getBlockState());replacement.load(saved);h.getLevel().getChunkAt(f.pos).addAndRegisterBlockEntity(replacement);
        Fixture resumed=new Fixture(h,f.pos,replacement,f.player);h.assertTrue(saved.equals(replacement.saveWithoutMetadata()),"Save/load changed detached plan or cycle counter");ticks(resumed,500);
        h.assertTrue(!replacement.crafting()&&jars.stream().allMatch(jar->jar.amount()==21),"Reload recharged paid essentia");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void reagentStartupTakesSixCyclesAndMidItemSavePreservesCounter(GameTestHelper h) {
        Fixture f=crystal(h);jars(f,recipe(h,"crystalclusterair").aspects(),1);start(f);ticks(f,125);
        h.assertTrue(f.matrix.remainingItems()==2&&Arrays.stream(f.matrix.getAspects().getAspects()).allMatch(a->f.matrix.getAspects().getAmount(a)==0),"Essentia stage consumed a reagent early");
        ticks(f,20);h.assertTrue(f.matrix.remainingItems()==2,"Five-cycle reagent animation consumed early");var saved=f.matrix.saveWithoutMetadata();f.matrix.load(saved);
        ticks(f,10);h.assertTrue(f.matrix.remainingItems()==1,"Mid-reagent reload lost itemCount");ticks(f,40);h.assertTrue(!f.matrix.crafting(),"Last reagent did not finish");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void extraPedestalUnknownResearchAndChangedCentralCannotCreateOutput(GameTestHelper h) {
        Fixture f=crystal(h);components(f,List.of(new ItemStack(Items.WHEAT_SEEDS),new ItemStack(AlchemyModule.SALIS_MUNDUS.get()),new ItemStack(Items.DIRT)));
        h.assertTrue(f.matrix.useCaster(f.player)&&!f.matrix.useCaster(f.player),"Extra occupied pedestal matched recipe");
        ((InfusionPedestalBlockEntity)h.getLevel().getBlockEntity(f.pos.offset(0,-2,3))).clearContent();
        var unknown=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"unknown"));unknown.setPos(f.player.getX(),f.player.getY(),f.player.getZ());
        h.assertTrue(!f.matrix.useCaster(unknown),"Unknown research matched");jars(f,recipe(h,"crystalclusterair").aspects(),11);
        h.assertTrue(f.matrix.useCaster(f.player),"Known recipe did not start");f.central().setItem(0,new ItemStack(Items.DIRT));ticks(f,5);
        h.assertTrue(!f.matrix.crafting()&&f.central().getItem(0).is(Items.DIRT),"Invalid central manufactured result");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void invalidCentralCycleRevealsActualNearbyInstabilityAndUnlocksStabilityResearch(GameTestHelper h) {
        Fixture f=crystal(h);var level=h.getLevel();var knowledge=KnowledgeStore.get(f.player);
        knowledge.setResearchStage("INFUSION",ResearchCatalog.get("INFUSION").stages().size()+1);
        knowledge.setResearchStage("METALLURGY",3);
        f.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ResearchModule.THAUMONOMICON.get()));
        f.player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),f.player) {
            @Override public void send(Packet<?> packet) {}
        };
        try {
            // The runtime discovers nearby entities through ServerLevel's entity index, not the recipe owner UUID.
            level.addNewPlayer(f.player);
            h.assertTrue(level.getEntitiesOfClass(ServerPlayer.class,new AABB(f.pos).inflate(10)).contains(f.player),"Witness was not tracked in the real nearby entity query");
            h.assertTrue(!knowledge.isResearchKnown("!INSTABILITY")&&!ResearchProgression.canStart(knowledge,"INFUSIONSTABLE")
                    &&ResearchNetwork.processAdvance(f.player,"INFUSIONSTABLE",0)==ResearchProgression.Result.LOCKED,"Parents alone invented the instability discovery");
            jars(f,recipe(h,"crystalclusterair").aspects(),7);start(f);
            // Stay inside the ten-block discovery box but outside every random explosion's blast radius.
            f.player.setPos(f.pos.getX()+.5,f.pos.getY(),f.pos.getZ()+9.5);
            ticks(f,f.matrix.cycleDelay());
            h.assertTrue(f.matrix.crafting()&&f.matrix.stability()>=0&&!knowledge.isResearchKnown("!INSTABILITY"),"A valid stable cycle granted an instability fact");
            f.central().setItem(0,new ItemStack(Items.DIRT));
            h.assertTrue(!knowledge.isResearchKnown("!INSTABILITY"),"Changing the central inventory bypassed the real matrix cycle");
            // Invalid input forces the original instability branch irrespective of its random event type.
            ticks(f,f.matrix.cycleDelay());
            h.assertTrue(!f.matrix.crafting()&&knowledge.isResearchKnown("!INSTABILITY")
                    &&knowledge.researchStage("INFUSIONSTABLE")==0&&ResearchProgression.canStart(knowledge,"INFUSIONSTABLE"),"Actual failed infusion did not reveal the hidden research to its nearby tracked player");
            h.assertTrue(ResearchNetwork.processAdvance(f.player,"INFUSIONSTABLE",0)==ResearchProgression.Result.STARTED
                    &&knowledge.researchStage("INFUSIONSTABLE")==1&&!knowledge.isResearchCompleteStrict("INFUSIONSTABLE"),"Discovered instability could not start the book entry or skipped its physical payment");
        } finally {
            level.removePlayerImmediately(f.player,Entity.RemovalReason.DISCARDED);
            level.getEntitiesOfClass(ItemEntity.class,new AABB(f.pos).inflate(8)).forEach(ItemEntity::discard);
        }
        h.assertTrue(!level.getEntitiesOfClass(ServerPlayer.class,new AABB(f.pos).inflate(10)).contains(f.player),"Instability witness leaked into later tests");
        h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void ordinaryUpgradeTransfersFractionalWearAndTemplateEnchantments(GameTestHelper h) {
        Fixture f=altar(h);KnowledgeStore.get(f.player).setResearchStage("ELEMENTALTOOLS",1);var r=recipe(h,"elementalaxe");ItemStack central=r.displayCentral();central.setDamageValue(central.getMaxDamage()/2);central.setHoverName(net.minecraft.network.chat.Component.literal("not copied"));f.central().setItem(0,central);
        components(f,r.components(central).stream().map(InfusionIngredient::example).toList());jars(f,r.aspects(),9);
        // Symmetric stabilizers keep this test deterministic without disabling instability globally.
        for(int x:new int[]{-2,2})h.getLevel().setBlockAndUpdate(f.pos.offset(x,-2,2),CatalogBlocks.block("stabilizer").defaultBlockState());
        for(int x:new int[]{-2,2})h.getLevel().setBlockAndUpdate(f.pos.offset(x,-2,-2),CatalogBlocks.block("stabilizer").defaultBlockState());
        start(f);ticks(f,800);var result=f.central().getItem(0);
        h.assertTrue(!f.matrix.crafting()&&result.is(stack("elemental_axe").getItem())&&!result.hasCustomHoverName()
                && result.getDamageValue()==(int)(result.getMaxDamage()*((float)central.getDamageValue()/central.getMaxDamage()))
                &&EnumInfusionEnchantment.getInfusionEnchantmentLevel(result,EnumInfusionEnchantment.COLLECTOR)==1
                &&EnumInfusionEnchantment.getInfusionEnchantmentLevel(result,EnumInfusionEnchantment.BURROWING)==1,"Relative wear/template enchantments changed");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void pairsDiminishAndOccupancyPenaltyIgnoresItemIdentity(GameTestHelper h) {
        Fixture f=altar(h);for(int n=1;n<=2;n++){h.getLevel().setBlockAndUpdate(f.pos.offset(n,-2,3),Blocks.SKELETON_SKULL.defaultBlockState());h.getLevel().setBlockAndUpdate(f.pos.offset(-n,-2,-3),Blocks.ZOMBIE_HEAD.defaultBlockState());}
        f.matrix.rescan();h.assertTrue(Math.abs(f.matrix.gain()-.175F)<.00001,"Skull pairs are not normalized/diminishing");
        var positions=components(f,List.of(new ItemStack(Items.DIRT),new ItemStack(Items.DIAMOND)));f.matrix.rescan();h.assertTrue(Math.abs(f.matrix.gain()-.175F)<.00001,"Different occupied items incur penalty");
        ((InfusionPedestalBlockEntity)h.getLevel().getBlockEntity(positions.get(0))).clearContent();f.matrix.rescan();h.assertTrue(Math.abs(f.matrix.gain()-.075F)<.00001,"Occupancy mismatch missing .1 loss");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void ancientEldritchPillarsAndFourBoostsUseOriginalNumbers(GameTestHelper h) {
        Fixture f=altar(h);for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){h.getLevel().setBlockAndUpdate(f.pos.offset(x,-2,z),CatalogBlocks.block("pillar_ancient").defaultBlockState());h.getLevel().setBlockAndUpdate(f.pos.offset(x,-3,z),CatalogBlocks.block("matrix_cost").defaultBlockState());}
        f.matrix.rescan();h.assertTrue(f.matrix.cycleDelay()==6&&Math.abs(f.matrix.costMultiplier()-.82F)<.00001&&Math.abs(f.matrix.gain()+.1F)<.00001,"Ancient/cost modifiers changed");
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){h.getLevel().setBlockAndUpdate(f.pos.offset(x,-2,z),CatalogBlocks.block("pillar_eldritch").defaultBlockState());h.getLevel().setBlockAndUpdate(f.pos.offset(x,-3,z),CatalogBlocks.block("matrix_speed").defaultBlockState());}
        f.matrix.rescan();h.assertTrue(f.matrix.cycleDelay()==1&&Math.abs(f.matrix.costMultiplier()-1.09F)<.00001&&Math.abs(f.matrix.gain()-.2F)<.00001,"Eldritch/speed modifiers changed");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void dustFormsOnlyEightStonesKeepsCentralInventoryAndForgeCancellationRollsBack(GameTestHelper h) {
        Fixture f=altar(h);KnowledgeStore.get(f.player).setResearchStage("INFUSION",1);f.central().setItem(0,new ItemStack(Items.DIAMOND));
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})for(int y:new int[]{-1,-2})h.getLevel().setBlockAndUpdate(f.pos.offset(x,y,z),CatalogBlocks.block("stone_arcane").defaultBlockState());
        f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));var ctx=new UseOnContext(f.player,InteractionHand.MAIN_HAND,new BlockHitResult(f.pos.getCenter(),Direction.UP,f.pos,false));
        Consumer<BlockEvent.EntityMultiPlaceEvent> cancel=event->{if(event.getEntity()==f.player)event.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(cancel);
        try { h.assertTrue(InfusionAltarFormation.use(ctx)==net.minecraft.world.InteractionResult.FAIL,"Cancelled altar committed"); }
        finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        h.assertTrue(f.player.getMainHandItem().getCount()==2&&h.getLevel().getBlockState(f.pos.offset(-1,-2,-1)).is(CatalogBlocks.block("stone_arcane")),"Cancelled altar consumed dust/stones");
        var corner=f.pos.offset(-1,-1,-1);var cornerContext=new UseOnContext(f.player,InteractionHand.MAIN_HAND,new BlockHitResult(corner.getCenter(),Direction.UP,corner,false));
        h.assertTrue(InfusionAltarFormation.use(cornerContext).consumesAction()&&f.player.getMainHandItem().getCount()==1&&InfusionStability.valid(h.getLevel(),f.pos)
                &&f.central().getItem(0).is(Items.DIAMOND)&&!f.matrix.active(),"Dust ritual lost central contents/activated early");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void recipeNetworkRoundTripKeeps56RealModesAndRejectsNestedNBTExtras(GameTestHelper h) {
        var recipes=h.getLevel().getRecipeManager().getAllRecipesFor(InfusionModule.RECIPE_TYPE.get());h.assertTrue(recipes.size()==56,"Expected47+8+1 real recipes");
        for(var recipe:recipes) { FriendlyByteBuf buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try { var serializer=new InfusionRecipe.Serializer();serializer.toNetwork(buffer,recipe);var decoded=serializer.fromNetwork(recipe.getId(),buffer);
            h.assertTrue(decoded.research().equals(recipe.research())&&decoded.kind().equals(recipe.kind())&&decoded.getIngredients().size()==recipe.getIngredients().size(),"Infusion sync lost recipe data"); }finally{buffer.release();} }
        boolean rejected=false;
        try { new InfusionRecipe.Serializer().fromJson(InfusionModule.id("malformed-runic"),com.google.gson.JsonParser.parseString("{\"mode\":\"runic\",\"components\":[{\"item\":\"minecraft:diamond\"}]}").getAsJsonObject()); }
        catch(com.google.gson.JsonSyntaxException expected) { rejected=true; }
        h.assertTrue(rejected,"Malformed runic datapack was accepted and can crash matching");
        var required=InfusionIngredient.fromJson(com.google.gson.JsonParser.parseString("{\"item\":\"minecraft:diamond\",\"nbt\":\"{nested:{v:1}}\"}").getAsJsonObject());
        ItemStack diamond=new ItemStack(Items.DIAMOND);var tag=new CompoundTag();tag.putInt("v",1);diamond.getOrCreateTag().put("nested",tag);
        h.assertTrue(required.test(diamond),"Exact nested NBT rejected");diamond.getTag().putString("extra","allowed");h.assertTrue(required.test(diamond),"Extra top-level NBT rejected");tag.putInt("other",2);h.assertTrue(!required.test(diamond),"Nested extra passed recursive subset matcher");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void mutationChangesOnlyItsTypedTagOnCurrentCentralAndKeepsOtherNBT(GameTestHelper h) {
        Fixture f=altar(h);stableCandles(f);KnowledgeStore.get(f.player).setResearchStage("FORTRESSMASK",1);var r=recipe(h,"helmgoggles");var central=r.displayCentral();central.setDamageValue(19);central.getOrCreateTag().putString("kept","original");f.central().setItem(0,central);
        components(f,r.components(central).stream().map(InfusionIngredient::example).toList());var jars=jars(f,r.aspects(),8);start(f);
        f.central().getItem(0).getOrCreateTag().putString("afterStart","preserved");ticks(f,700);var result=f.central().getItem(0);
        h.assertTrue(!f.matrix.crafting()&&result.getDamageValue()==19&&result.getTag().contains("goggles",net.minecraft.nbt.Tag.TAG_BYTE)&&result.getTag().getByte("goggles")==1
                &&result.getTag().getString("kept").equals("original")&&result.getTag().getString("afterStart").equals("preserved")&&jars.stream().allMatch(j->j.amount()==8),"Mutation replaced current unrelated NBT/damage or wrong debit");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void runicAndEnchantmentCostsUseActualCentralNBTAndPlansAreDetached(GameTestHelper h) {
        Fixture f=altar(h);stableCandles(f);var knowledge=KnowledgeStore.get(f.player);knowledge.setResearchStage("RUNICSHIELDING",1);knowledge.setResearchStage("INFUSIONENCHANTMENT",1);
        var runic=recipe(h,"runicarmor");var central=new ItemStack(Items.LEATHER_CHESTPLATE);central.getOrCreateTag().putByte("TC.RUNIC",(byte)2);central.getTag().putString("keep","yes");
        List<ItemStack> components=List.of(new ItemStack(AlchemyModule.SALIS_MUNDUS.get()),stack("amber"),stack("amber"),stack("amber"));
        var plan=runic.plan(knowledge,central,components,net.minecraft.util.RandomSource.create(0)).orElseThrow();
        h.assertTrue(plan.aspects().getAmount(Aspect.PROTECT)==100&&plan.aspects().getAmount(Aspect.CRYSTAL)==50&&plan.instability()==6&&plan.output().getTag().getByte("TC.RUNIC")==3,"Dynamic runic formula changed");
        plan.input().getTag().putString("keep","bad");plan.output().getTag().putByte("TC.RUNIC",(byte)0);h.assertTrue(central.getTag().getString("keep").equals("yes")&&plan.output().getTag().getByte("TC.RUNIC")==3,"Plan aliases caller storage");
        var refine=recipe(h,"ierefining");ItemStack pick=new ItemStack(Items.IRON_PICKAXE);EnumInfusionEnchantment.addInfusionEnchantment(pick,EnumInfusionEnchantment.REFINING,1);EnumInfusionEnchantment.addInfusionEnchantment(pick,EnumInfusionEnchantment.SOUNDING,1);
        h.assertTrue(refine.aspects(pick).getAmount(Aspect.ORDER)==(int)(80*(2+.33F))&&refine.aspects(pick).getAmount(Aspect.EXCHANGE)==(int)(60*(2+.33F))&&refine.instability(pick)==4,"Enchantment float/truncation formula changed");
        central.getTag().putByte("TC.RUNIC",(byte)27);h.assertTrue(!runic.matchesItems(central,components),"Overflow charge starts a free runic augment");h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void waterBucketRemainderStaysOnPedestalAndOutputConsumesNoPlayerXP(GameTestHelper h) {
        Fixture f=altar(h);stableCandles(f);KnowledgeStore.get(f.player).setResearchStage("JARBRAIN",1);var r=recipe(h,"jarbrain");ItemStack central=r.displayCentral();f.central().setItem(0,central);
        List<ItemStack> inputs=r.components(central).stream().map(InfusionIngredient::example).toList();var positions=components(f,inputs);var jars=jars(f,r.aspects(),7);f.player.giveExperienceLevels(10);int xp=f.player.experienceLevel;start(f);ticks(f,800);
        h.assertTrue(!f.matrix.crafting()&&f.central().getItem(0).is(CatalogBlocks.block("jar_brain").asItem())&&f.player.experienceLevel==xp&&jars.stream().allMatch(j->j.amount()==7),"JarBrain did not finish / invented XP cost");
        for(int i=0;i<inputs.size();i++)if(inputs.get(i).is(Items.WATER_BUCKET))h.assertTrue(((InfusionPedestalBlockEntity)h.getLevel().getBlockEntity(positions.get(i))).getItem(0).is(Items.BUCKET),"Bucket remainder lost");h.succeed();
    }
}
