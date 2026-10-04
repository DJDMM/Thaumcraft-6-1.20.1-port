package thaumcraft.auromancy.remaining;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.focus.FocusCompiler;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Real TC6 effect callbacks, terrain/contact lifetime, physical exchanges and restored passage states. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class RemainingFocusEffectsGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "TC6RemainFX"));
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return p;
    }
    private static BlockPos pos(GameTestHelper h) { return h.absolutePos(new BlockPos(3, 2, 3)); }
    private static FocusGraph.Node node(String key, Map<String, Integer> settings) {
        return new FocusGraph.Node(2, 1, List.of(), 0, 2, key, settings);
    }
    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); }
    private static Cow cow(GameTestHelper h, ServerPlayer p) {
        Cow target = EntityType.COW.create(h.getLevel()); target.setNoAi(true); target.setPos(p.position().add(1, 0, 0)); return target;
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < .001, why + ": " + actual + " != " + expected);
    }
    private static void aura(ServerLevel level, BlockPos pos, float value) {
        AuraManager.drainVis(level, pos, Float.MAX_VALUE, false); AuraManager.addVis(level, pos, value);
    }
    private static int count(ServerPlayer p, Item item) {
        return p.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void clean(ServerLevel level, BlockPos pos) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.8)).forEach(ItemEntity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(.8)).forEach(ExperienceOrb::discard);
    }
    private static void queue(ServerLevel level, ServerPlayer p, BlockPos pos, Item item, boolean silk, int fortune) {
        FocusExchangeQueue.enqueue(level, p, pos, level.getBlockState(pos), new ItemStack(item), silk, fortune);
    }
    private static void exchangeCaster(ServerPlayer p) {
        var focus = CatalogModule.stack("focus_1");
        var graph = new FocusGraph(List.of(new FocusGraph.Node(0,-1,List.of(1),0,0,FocusNodeRegistry.ROOT,Map.of()),
                new FocusGraph.Node(1,0,List.of(2),0,1,FocusNodeRegistry.TOUCH,Map.of()),
                node(FocusNodeRegistry.EXCHANGE,Map.of("silk",0,"fortune",0))));
        var result = FocusCompiler.compile(graph, focus, research -> true);
        if (!result.success()) throw new IllegalStateException("Exchange fixture could not compile: " + result.error());
        var caster = CatalogModule.stack("caster_basic"); FocusSelection.setInstalled(caster, FocusStacks.apply(focus, result.plan(), "Exchange fixture"));
        p.setItemInHand(InteractionHand.MAIN_HAND, caster);
    }
    private static boolean curse(GameTestHelper h, ServerPlayer p, Cow cow, int power, int duration, float finalPower) {
        return RemainingFocusEffects.apply(h.getLevel(), p, node(FocusNodeRegistry.CURSE,Map.of("power",power,"duration",duration)),
                new EntityHitResult(cow, cow.position()), new Vec3(1,0,0), finalPower,0);
    }
    private static boolean rift(GameTestHelper h, ServerPlayer p, BlockPos pos, Direction side, int depth, int duration, float power) {
        return RemainingFocusEffects.apply(h.getLevel(), p, node(FocusNodeRegistry.RIFT,Map.of("depth",depth,"duration",duration)),
                new BlockHitResult(Vec3.atCenterOf(pos),side,pos,false),new Vec3(0,0,1),power,0);
    }
    private static void plane(ServerLevel level, BlockPos center, Direction.Axis axis, BlockState state) {
        for (int u=-1;u<=1;u++) for (int v=-1;v<=1;v++) {
            var pos = switch(axis) { case X -> center.offset(0,u,v); case Y -> center.offset(u,0,v); case Z -> center.offset(u,v,0); };
            level.setBlockAndUpdate(pos,state);
        }
    }
    private static RiftHoleBlockEntity memory(GameTestHelper h, BlockPos pos) {
        var be = h.getLevel().getBlockEntity(pos); h.assertTrue(be instanceof RiftHoleBlockEntity,"No operational hole memory at " + pos);
        return (RiftHoleBlockEntity)be;
    }
    private static void tick(GameTestHelper h, BlockPos pos) {
        var be = memory(h,pos); RiftHoleBlockEntity.tick(h.getLevel(),pos,h.getLevel().getBlockState(pos),be);
    }

    @GameTest(template="empty") public static void curseScaledMagicHasOriginalTargetAndCasterSource(GameTestHelper h) {
        var p=player(h); var target=cow(h,p); var observed=new AtomicInteger();
        Consumer<LivingHurtEvent> witness=event->{ if(event.getEntity()==target) {
            h.assertTrue(event.getSource().getDirectEntity()==target && event.getSource().getEntity()==p,"Curse replaced original target/caster magic source");
            near(h,event.getAmount(),3,"(1+power)*finalPower"); observed.incrementAndGet();
        }};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,witness);
        try { h.assertTrue(!curse(h,p,target,5,3,.5F),"Curse changed original false return"); }
        finally { MinecraftForge.EVENT_BUS.unregister(witness); }
        h.assertTrue(observed.get()==1,"Curse failed real Forge damage callback"); near(h,target.getHealth(),7,"Curse actual health");
        var poison=target.getEffect(MobEffects.POISON); h.assertTrue(poison!=null && poison.getDuration()==60 && poison.getAmplifier()==1,"Scaled poison duration/amplifier"); h.succeed();
    }
    @GameTest(template="empty") public static void curseDamageVetoStillAppliesOriginalAilments(GameTestHelper h) {
        var p=player(h); var target=cow(h,p); target.setInvulnerable(true);
        curse(h,p,target,4,10,1F); near(h,target.getHealth(),10,"Invulnerable target health");
        h.assertTrue(target.getEffect(MobEffects.POISON).getAmplifier()==2 && target.getEffect(MobEffects.POISON).getDuration()==200,"Curse gated poison on accepted damage"); h.succeed();
    }
    @GameTest(template="empty") public static void curseRandomThresholdDropsOnlyAfterSuccessfulPriorDebuff(GameTestHelper h) {
        var p=player(h); var level=h.getLevel();
        for(int seed=0;seed<24;seed++) {
            var target=cow(h,p); target.setInvulnerable(true); var random=RandomSource.create(seed); float chance=.85F;
            var effects=List.of(MobEffects.MOVEMENT_SLOWDOWN,MobEffects.WEAKNESS,MobEffects.DIG_SLOWDOWN,MobEffects.HUNGER,MobEffects.UNLUCK);
            boolean[] expected=new boolean[5]; for(int i=0;i<5;i++) { expected[i]=random.nextFloat()<chance; if(expected[i] && i<4)chance-=.15F; }
            level.random.setSeed(seed); curse(h,p,target,3,4,.6F);
            for(int i=0;i<5;i++) {
                var effect=target.getEffect(effects.get(i)); h.assertTrue((effect!=null)==expected[i],"Curse probability changed at seed " + seed + " index " + i);
                if(effect!=null) { h.assertTrue(effect.getAmplifier()==0,"Curse rounded instead of truncating scaled amplifier");
                    h.assertTrue(effect.getDuration()==(i<2?80:i==2?160:240),"Curse omitted original doubled/tripled durations"); }
            }
        } h.succeed();
    }
    @GameTest(template="empty") public static void curseGroundSphereCreatesFunctionalInvisibleSap(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h);
        plane(level,center,Direction.Axis.Y,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(center.offset(2,0,2),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(center.offset(2,1,2),Blocks.AIR.defaultBlockState());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlockAndUpdate(center.offset(x,1,z),Blocks.AIR.defaultBlockState());
        h.assertTrue(!RemainingFocusEffects.apply(level,p,node(FocusNodeRegistry.CURSE,Map.of("power",1,"duration",1)),hit(center),Vec3.ZERO,1F,0),"Ground Curse changed false return");
        var sap=level.getBlockState(center.above()); h.assertTrue(sap.getBlock() instanceof CurseSapBlock && sap.isAir() && sap.getCollisionShape(level,center.above()).isEmpty(),"Sap is a solid visual placeholder");
        h.assertTrue(!level.getBlockState(center.offset(2,1,2)).is(CatalogBlocks.block("effect_sap")),"Curse ignored 3D spherical radius"); h.succeed();
    }
    @GameTest(template="empty") public static void curseSapContactDefersThenAppliesThreeExactAmbientDebuffs(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var target=cow(h,p); var sap=CatalogBlocks.block("effect_sap"); var pos=pos(h);
        sap.entityInside(sap.defaultBlockState(),level,pos,target); h.assertTrue(!target.hasEffect(MobEffects.WITHER),"Sap lost original END callback delay");
        CurseSapQueue.process(level);
        h.assertTrue(target.getEffect(MobEffects.WITHER).getDuration()==40 && target.getEffect(MobEffects.WITHER).getAmplifier()==0,"Sap Wither");
        h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==1 && target.getEffect(MobEffects.HUNGER).getAmplifier()==1,"Sap Slow/Hunger levels");
        h.assertTrue(target.getEffect(MobEffects.WITHER).isAmbient() && target.getEffect(MobEffects.WITHER).isVisible(),"Sap original ambient/particle flags"); h.succeed();
    }
    @GameTest(template="empty") public static void curseSapDoesNotRefreshWitherAndExemptsOriginalEldritchRoster(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var target=cow(h,p); var sap=CatalogBlocks.block("effect_sap");
        target.addEffect(new MobEffectInstance(MobEffects.WITHER,7,0)); sap.entityInside(sap.defaultBlockState(),level,pos(h),target); CurseSapQueue.process(level);
        h.assertTrue(target.getEffect(MobEffects.WITHER).getDuration()==7 && !target.hasEffect(MobEffects.HUNGER),"Existing Wither did not suppress contact runnable");
        for(var id:List.of("eldritch_crab","eldritch_guardian","eldritch_golem","eldritch_warden","inhabited_zombie","mind_spider","taintacle_giant")) {
            var eldritch=VisualEntitiesModule.LIVING.get(id).get().create(level); sap.entityInside(sap.defaultBlockState(),level,pos(h),eldritch); CurseSapQueue.process(level);
            h.assertTrue(!eldritch.hasEffect(MobEffects.WITHER),"Original IEldritchMob sap exemption missing: " + id);
        } h.succeed();
    }
    @GameTest(template="empty") public static void curseSapActualRandomTickRemovesItWithoutLoot(GameTestHelper h) {
        var level=h.getLevel(); var pos=pos(h); clean(level,pos); var sap=CatalogBlocks.block("effect_sap"); level.setBlockAndUpdate(pos,sap.defaultBlockState());
        sap.randomTick(sap.defaultBlockState(),level,pos,level.random); h.assertTrue(level.getBlockState(pos).is(Blocks.AIR),"Sap random decay absent");
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(.8)).isEmpty(),"Sap dropped an item"); h.succeed();
    }
    @GameTest(template="empty") public static void cursePlacementVetoPreservesTerrainAndAir(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        Consumer<BlockEvent.EntityPlaceEvent> veto=event->{if(event.getEntity()==p)event.setCanceled(true);}; MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,veto);
        try { RemainingFocusEffects.apply(level,p,node(FocusNodeRegistry.CURSE,Map.of("power",1,"duration",1)),hit(pos),Vec3.ZERO,1,0); }
        finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && level.getBlockState(pos.above()).is(Blocks.AIR),"Cancelled Curse placement changed terrain"); h.succeed();
    }

    @GameTest(template="empty") public static void pickerSneakSelectionPersistsOriginalPickedTagWithoutCosts(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); exchangeCaster(p); p.setShiftKeyDown(true); level.setBlockAndUpdate(pos,Blocks.BLUE_WOOL.defaultBlockState()); aura(level,pos,5);
        h.assertTrue(FocusBlockPicker.pick(p,InteractionHand.MAIN_HAND,pos),"Sneak picker did not select a non-BE block");
        h.assertTrue(FocusBlockPicker.picked(p.getMainHandItem()).is(Items.BLUE_WOOL) && p.getMainHandItem().getTag().contains("picked",10),"Original picked NBT missing");
        near(h,AuraManager.getVis(level,pos),5,"Picking charged aura"); h.assertTrue(level.getBlockState(pos).is(Blocks.BLUE_WOOL) && count(p,Items.BLUE_WOOL)==0,"Picking created/consumed/replaced physical block"); h.succeed();
    }
    @GameTest(template="empty") public static void pickerRejectsBlockEntitiesAndNonSneakingUse(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); exchangeCaster(p); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        h.assertTrue(!FocusBlockPicker.pick(p,InteractionHand.MAIN_HAND,pos),"Non-sneaking picked a block"); p.setShiftKeyDown(true); level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
        h.assertTrue(!FocusBlockPicker.pick(p,InteractionHand.MAIN_HAND,pos) && !p.getMainHandItem().getTag().contains("picked"),"Picker captured block-entity data"); h.succeed();
    }
    @GameTest(template="empty") public static void pickedItemDisappearsFromAccessorWhenExchangeFocusRemoved(GameTestHelper h) {
        var p=player(h); exchangeCaster(p); p.getMainHandItem().getOrCreateTag().put("picked",new ItemStack(Items.DIRT).save(new CompoundTag()));
        h.assertTrue(FocusBlockPicker.picked(p.getMainHandItem()).is(Items.DIRT),"Valid picked item unavailable"); FocusSelection.setInstalled(p.getMainHandItem(),ItemStack.EMPTY);
        h.assertTrue(FocusBlockPicker.picked(p.getMainHandItem()).isEmpty() && p.getMainHandItem().getTag().contains("picked"),"Picker ignored installed focus or destroyed original remembered tag"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangePaysOneMainInventoryBlockAndPicksUpRealStoneLoot(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); clean(level,pos); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,5);
        p.getInventory().setItem(5,new ItemStack(Items.DIRT,3)); queue(level,p,pos,Items.DIRT,false,0);
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"Exchange executed before END"); FocusExchangeQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT) && count(p,Items.DIRT)==2 && count(p,Items.COBBLESTONE)==1,"Exchange lacked physical consumption and original pickup");
        near(h,AuraManager.getVis(level,pos),4.75,"Exchange target .25 vis"); h.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class,new AABB(pos).inflate(.8)).isEmpty(),"Exchange invented break XP"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeSilkReturnsGlassAndChargesOriginalExtraVis(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.GLASS.defaultBlockState()); aura(level,pos,5); p.getInventory().setItem(7,new ItemStack(Items.DIRT));
        queue(level,p,pos,Items.DIRT,true,0); FocusExchangeQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT) && count(p,Items.GLASS)==1,"Exchange Silk Touch lost glass"); near(h,AuraManager.getVis(level,pos),4.5,"Exchange Silk cost"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeFortuneUsesModernLootWithoutToolWearOrExperience(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); clean(level,pos); level.setBlockAndUpdate(pos,Blocks.DIAMOND_ORE.defaultBlockState()); aura(level,pos,5);
        var tool=new ItemStack(Items.DIAMOND_PICKAXE); tool.setDamageValue(13); p.setItemInHand(InteractionHand.MAIN_HAND,tool); p.getInventory().setItem(8,new ItemStack(Items.DIRT));
        queue(level,p,pos,Items.DIRT,false,4); FocusExchangeQueue.process(level);
        h.assertTrue(count(p,Items.DIAMOND)>=1 && count(p,Items.DIAMOND)<=5 && tool.getDamageValue()==13,"Exchange Fortune IV loot/wear");
        near(h,AuraManager.getVis(level,pos),4.35,"Exchange Fortune IV target vis"); h.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class,new AABB(pos).inflate(.8)).isEmpty(),"Exchange invented XP"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeRequiresExactMainInventoryItemTagsAndExcludesOffhand(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,5);
        var named=new ItemStack(Items.DIRT); named.setHoverName(net.minecraft.network.chat.Component.literal("Different")); p.getInventory().setItem(4,named); p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.DIRT));
        queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"Exchange accepted wrong NBT/offhand item"); near(h,AuraManager.getVis(level,pos),5,"Failed inventory cost"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeCancelledPlacementDoesNotConsumeBlockLootOrVis(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,5); p.getInventory().setItem(5,new ItemStack(Items.DIRT));
        Consumer<BlockEvent.EntityPlaceEvent> veto=event->{if(event.getEntity()==p)event.setCanceled(true);}; MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,veto);
        try { queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && count(p,Items.DIRT)==1 && count(p,Items.COBBLESTONE)==0,"Cancelled Exchange mutated block/inventory"); near(h,AuraManager.getVis(level,pos),5,"Cancelled Exchange charged aura"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangePlacementCallbackStateChangeIsPreservedWithoutPayment(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,5); p.getInventory().setItem(5,new ItemStack(Items.DIRT));
        Consumer<BlockEvent.EntityPlaceEvent> mutate=event->{if(event.getEntity()==p)level.setBlockAndUpdate(pos,Blocks.GOLD_BLOCK.defaultBlockState());}; MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,mutate);
        try { queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); } finally { MinecraftForge.EVENT_BUS.unregister(mutate); }
        h.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK) && count(p,Items.DIRT)==1 && count(p,Items.COBBLESTONE)==0,"Exchange overwrote another placement callback"); near(h,AuraManager.getVis(level,pos),5,"Stale callback Exchange aura"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeInsufficientTargetAuraIsDiscardedWithoutPartialPayment(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,.2F); p.getInventory().setItem(6,new ItemStack(Items.DIRT));
        queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); aura(level,pos,5); FocusExchangeQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && count(p,Items.DIRT)==1,"Exchange paused invalid work instead of discarding"); near(h,AuraManager.getVis(level,pos),5,"Discarded Exchange cost"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeCreativeSkipsTargetDebitLootAndInventoryButStillNeedsAura(GameTestHelper h) {
        var p=player(h); p.gameMode.changeGameModeForPlayer(GameType.CREATIVE); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,.2F);
        queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"Creative bypassed original target aura availability");
        aura(level,pos,5); queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT) && count(p,Items.COBBLESTONE)==0,"Creative swap/loot behavior"); near(h,AuraManager.getVis(level,pos),5,"Creative target debit"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeSameBlockAndUnbreakableTargetsNeverConsume(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); p.getInventory().setItem(5,new ItemStack(Items.DIRT,2)); aura(level,pos,5); level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());
        queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); level.setBlockAndUpdate(pos,Blocks.BEDROCK.defaultBlockState()); queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK) && count(p,Items.DIRT)==2,"Same-block/unbreakable exchange consumed"); near(h,AuraManager.getVis(level,pos),5,"Refused target cost"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeQueueDetachesCallbackWorkUntilNextEnd(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); var other=pos.east(); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(other,Blocks.STONE.defaultBlockState()); aura(level,pos,5); p.getInventory().setItem(5,new ItemStack(Items.DIRT,2));
        Consumer<BlockEvent.EntityPlaceEvent> reenter=event->{ if(event.getEntity()==p && event.getPos().equals(pos)) { queue(level,p,other,Items.DIRT,false,0); FocusExchangeQueue.process(level); } }; MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,reenter);
        try { queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.process(level); h.assertTrue(level.getBlockState(other).is(Blocks.STONE),"Exchange callback reentered detached batch"); }
        finally { MinecraftForge.EVENT_BUS.unregister(reenter); }
        FocusExchangeQueue.process(level); h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT) && level.getBlockState(other).is(Blocks.DIRT) && count(p,Items.DIRT)==0,"Detached follow-up exchange vanished/duplicated"); h.succeed();
    }
    @GameTest(template="empty") public static void exchangeUnloadClearsPendingPhysicalWork(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var pos=pos(h); level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); aura(level,pos,5); p.getInventory().setItem(5,new ItemStack(Items.DIRT));
        queue(level,p,pos,Items.DIRT,false,0); FocusExchangeQueue.unload(new LevelEvent.Unload(level)); FocusExchangeQueue.process(level);
        h.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && count(p,Items.DIRT)==1,"Unload retained exchange work"); h.succeed();
    }

    @GameTest(template="empty") public static void riftPropagatesThreeByThreePlanesThenOneAirExit(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h); plane(level,center,Direction.Axis.Z,Blocks.STONE.defaultBlockState()); plane(level,center.south(),Direction.Axis.Z,Blocks.STONE.defaultBlockState()); plane(level,center.south(2),Direction.Axis.Z,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(center.south(3),Blocks.AIR.defaultBlockState());
        h.assertTrue(rift(h,p,center,Direction.NORTH,8,2,1),"Rift refused wall"); h.assertTrue(memory(h,center).remainingSegments()==3,"Rift omitted original distance+1 air terminal");
        h.assertTrue(level.getBlockState(center.east()).is(Blocks.STONE),"Rift created side planes synchronously"); tick(h,center);
        h.assertTrue(level.getBlockState(center.east()).getBlock() instanceof RiftHoleBlock && level.getBlockState(center.south()).getBlock() instanceof RiftHoleBlock,"First BE tick lacked plane/next segment");
        tick(h,center.south()); h.assertTrue(level.getBlockState(center.south().east()).getBlock() instanceof RiftHoleBlock && level.getBlockState(center.south(2)).getBlock() instanceof RiftHoleBlock,"Second passage plane/exit missing");
        h.assertTrue(memory(h,center.south(2)).remainingSegments()==1 && memory(h,center.south(2)).previousState().isAir(),"Air exit is not count-one original memory");
        tick(h,center.south(2)); h.assertTrue(level.getBlockState(center.south(3)).isAir() && !(level.getBlockState(center.south(3)).getBlock() instanceof RiftHoleBlock),"Air exit kept tunneling"); h.succeed();
    }
    @GameTest(template="empty") public static void riftDepthIsScaledAndOriginalByteCountPreserved(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h); for(int z=0;z<6;z++)level.setBlockAndUpdate(center.south(z),Blocks.STONE.defaultBlockState());
        rift(h,p,center,Direction.NORTH,8,3,.25F); h.assertTrue(memory(h,center).remainingSegments()==3 && memory(h,center).maximum()==60,"Rift ignored scaled depth or seconds*20"); h.succeed();
    }
    @GameTest(template="empty") public static void riftThreeAxesHaveOriginalPerpendicularPlane(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h);
        for(var face:List.of(Direction.NORTH,Direction.WEST,Direction.UP)) {
            plane(level,center,face.getAxis(),Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(center.relative(face.getOpposite()),Blocks.STONE.defaultBlockState());
            h.assertTrue(RiftPassage.create(level,p,center,face,(byte)2,40),"Rift creation axis " + face); tick(h,center);
            BlockPos diagonal=switch(face.getAxis()) {case X -> center.offset(0,1,1); case Y -> center.offset(1,0,1); case Z -> center.offset(1,1,0);};
            h.assertTrue(level.getBlockState(diagonal).getBlock() instanceof RiftHoleBlock,"Rift plane rotation missing " + face);
            for(var mutable:BlockPos.betweenClosed(center.offset(-2,-2,-2),center.offset(2,2,2)))if(level.getBlockEntity(mutable) instanceof RiftHoleBlockEntity memory)level.setBlockAndUpdate(mutable.immutable(),memory.previousState());
        } h.succeed();
    }
    @GameTest(template="empty") public static void riftRefusesBlockEntitiesAndOriginalBlacklistWithoutRemovingContents(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h);
        for(var block:List.of(Blocks.CHEST,Blocks.BEDROCK,Blocks.WHITE_BED,Blocks.PISTON,Blocks.STICKY_PISTON,Blocks.PISTON_HEAD,Blocks.OAK_DOOR,Blocks.IRON_DOOR,CatalogBlocks.block("infernal_furnace"))) {
            level.setBlockAndUpdate(center,block.defaultBlockState()); h.assertTrue(!RiftPassage.create(level,p,center,Direction.NORTH,(byte)2,40),"Rift blacklisted/BE block accepted " + block);
            h.assertTrue(level.getBlockState(center).is(block),"Rift refused block was removed");
        } h.succeed();
    }
    @GameTest(template="empty") public static void riftRefusesReplaceableWaterButAcceptsTerminalAir(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h); level.setBlockAndUpdate(center,Blocks.WATER.defaultBlockState());
        h.assertTrue(!RiftPassage.create(level,p,center,null,(byte)1,40),"Rift ignored original canPlaceBlockAt water refusal");
        level.setBlockAndUpdate(center,Blocks.AIR.defaultBlockState()); h.assertTrue(RiftPassage.create(level,p,center,null,(byte)1,40),"Rift refused original terminal air");
        h.assertTrue(memory(h,center).previousState().is(Blocks.AIR),"Rift air memory"); h.succeed();
    }
    @GameTest(template="empty") public static void riftSaveLoadRestoresExactModernBlockPropertiesAtOriginalDuration(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h); var stairs=Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.WEST).setValue(BlockStateProperties.HALF,Half.TOP);
        level.setBlockAndUpdate(center,stairs); h.assertTrue(RiftPassage.create(level,p,center,null,(byte)1,40),"Rift refused stairs");
        for(int i=0;i<17;i++)tick(h,center); var tag=memory(h,center).saveWithoutMetadata(); var restored=new RiftHoleBlockEntity(center,level.getBlockState(center)); restored.load(tag); level.setBlockEntity(restored);
        h.assertTrue(restored.countdown()==17 && restored.maximum()==40 && restored.previousState()==stairs,"Rift NBT lost state/countdown");
        for(int i=0;i<22;i++)tick(h,center); h.assertTrue(level.getBlockState(center).getBlock() instanceof RiftHoleBlock,"Rift restored before tick40"); tick(h,center);
        h.assertTrue(level.getBlockState(center)==stairs && level.getBlockEntity(center)==null,"Rift failed exact timed restoration"); h.succeed();
    }
    @GameTest(template="essentia_network") public static void riftRealWorldTicksRestoreWholePassage(GameTestHelper h) {
        // Keep both 3x3 walls and the terminal inside this 9x5x9 template's ticking region.
        // The 3x3x3 empty template leaves the old z=5 terminal outside its chunk tickets.
        var p=player(h); var level=h.getLevel(); var center=h.absolutePos(new BlockPos(4,2,3)); plane(level,center,Direction.Axis.Z,Blocks.STONE.defaultBlockState()); plane(level,center.south(),Direction.Axis.Z,Blocks.STONE.defaultBlockState()); plane(level,center.south(2),Direction.Axis.Z,Blocks.AIR.defaultBlockState());
        h.assertTrue(rift(h,p,center,Direction.NORTH,8,2,1),"Real-world Rift refused wall");
        h.runAtTickTime(6,()->{
            for(int z=0;z<2;z++)for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++) {
                var wall=memory(h,center.offset(u,v,z));
                h.assertTrue(wall.previousState().is(Blocks.STONE) && wall.maximum()==40 && wall.countdown()>0 && wall.countdown()<=6,"Real BE world ticks did not activate the complete 40-tick wall passage");
            }
            var terminal=memory(h,center.south(2));
            h.assertTrue(terminal.previousState().is(Blocks.AIR) && terminal.remainingSegments()==1 && terminal.maximum()==40 && terminal.countdown()>0 && terminal.countdown()<=6,"Real BE world ticks did not activate the original 40-tick air terminal");
        });
        h.runAtTickTime(48,()->{for(int z=0;z<2;z++)for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)h.assertTrue(level.getBlockState(center.offset(u,v,z)).is(Blocks.STONE),"Real passage failed to restore wall");
            h.assertTrue(level.getBlockState(center.south(2)).is(Blocks.AIR),"Original air terminal did not restore"); h.succeed();});
    }
    @GameTest(template="empty") public static void riftCancelledPlacementLeavesOriginalBlockAndNoMemory(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h); level.setBlockAndUpdate(center,Blocks.STONE.defaultBlockState());
        Consumer<BlockEvent.EntityPlaceEvent> veto=event->{if(event.getEntity()==p)event.setCanceled(true);}; MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,veto);
        try { h.assertTrue(!RiftPassage.create(level,p,center,Direction.NORTH,(byte)2,40),"Rift ignored Forge veto"); } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(level.getBlockState(center).is(Blocks.STONE) && level.getBlockEntity(center)==null,"Cancelled Rift changed block/memory"); h.succeed();
    }
    @GameTest(template="empty") public static void riftStopsChainAtContainerWithoutDestroyingItsBlockEntity(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var center=pos(h); level.setBlockAndUpdate(center,Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(center.south(),Blocks.CHEST.defaultBlockState()); var chest=level.getBlockEntity(center.south());
        rift(h,p,center,Direction.NORTH,8,2,1); tick(h,center); h.assertTrue(level.getBlockState(center.south()).is(Blocks.CHEST) && level.getBlockEntity(center.south())==chest && memory(h,center).remainingSegments()==0,"Rift overwrote container or kept failed chain"); h.succeed();
    }
    @GameTest(template="empty") public static void malformedRemainingEffectsNeverDamageOrSchedule(GameTestHelper h) {
        var p=player(h); var target=cow(h,p);
        for(float power:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,17})h.assertTrue(!curse(h,p,target,1,1,power),"Malformed finalPower executed Curse");
        h.assertTrue(!curse(h,p,target,0,1,1) && !curse(h,p,target,1,11,1),"Malformed Curse settings executed"); near(h,target.getHealth(),10,"Malformed effects health");
        var pos=pos(h); h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); h.assertTrue(!rift(h,p,pos,Direction.NORTH,9,2,1) && !rift(h,p,pos,Direction.NORTH,8,11,1),"Malformed Rift executed"); h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Malformed Rift removed block"); h.succeed();
    }
}
