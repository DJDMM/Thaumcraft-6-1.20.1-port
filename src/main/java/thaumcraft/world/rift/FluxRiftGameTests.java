package thaumcraft.world.rift;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.infusion.InfusionStabilizerBlockEntity;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraChunk;
import thaumcraft.world.aura.AuraManager;
import thaumcraft.world.aura.AuraSavedData;

/** Real rifts, world blocks, aura and BE tick consumers; raw aura/geometry are explicit fixtures. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FluxRiftGameTests {
    private static final String TEMPLATE="essentia_network";
    private static final BlockPos CENTER=new BlockPos(4,8,4);
    private static FluxRiftEntity rift(GameTestHelper h,int size) {
        var r=(FluxRiftEntity)VisualEntitiesModule.EFFECTS.get("flux_rift").get().create(h.getLevel());
        r.setRiftSeed(314); r.setRiftSize(size); r.setRiftStability(100); r.setPos(Vec3.atCenterOf(h.absolutePos(CENTER)));
        return r;
    }
    private static void tick(FluxRiftEntity r,int count) {
        for(int i=0;i<count && !r.isRemoved();i++) { r.tickCount=100+i; r.tick(); }
    }
    private static int seeds(GameTestHelper h,Vec3 center) {
        var seed=ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","void_seed"));
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(3),e->e.getItem().is(seed)).stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    /** Align a real seeded guide segment across an outline face; the native world ticks it. */
    private static FluxRiftEntity alignedNativeRift(GameTestHelper h,BlockPos pos,AABB outline) {
        var r=rift(h,3); Vec3 a=r.points.get(0),b=r.points.get(1),delta=b.subtract(a);
        Vec3 face=outline.getCenter();
        face=Math.abs(delta.x)>Math.abs(delta.z)
                ? new Vec3(delta.x>0 ? outline.minX : outline.maxX,face.y,face.z)
                : new Vec3(face.x,face.y,delta.z>0 ? outline.minZ : outline.maxZ);
        r.setPos(face.subtract(a.add(b).scale(.5)));
        h.assertTrue(outline.clip(r.points.get(0).add(r.position()),r.points.get(1).add(r.position())).isPresent(),
                "Actual original spine segment missed the owned outline fixture at "+pos);
        // Seed only the actual entity RNG so its first ordinary hazard tick picks
        // this real segment. No direct tick, replacement points or runtime hook.
        long seed=0;
        while(net.minecraft.util.RandomSource.create(seed).nextInt(r.points.size()-1)!=0) seed++;
        r.getRandom().setSeed(seed);
        return r;
    }
    private static BlockPos nativeSegmentHit(GameTestHelper h,FluxRiftEntity r,ClipContext.Block block,ClipContext.Fluid fluid) {
        var hit=h.getLevel().clip(new ClipContext(r.points.get(0).add(r.position()),r.points.get(1).add(r.position()),block,fluid,r));
        return hit.getType()==HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }
    @SuppressWarnings("unchecked") private static final class AuraFixture implements AutoCloseable {
        private final Map<Long,AuraChunk> chunks; private final long key; private final AuraChunk previous;
        AuraFixture(GameTestHelper h,BlockPos pos,float vis,float flux) {
            try { Field f=AuraSavedData.class.getDeclaredField("chunks"); f.setAccessible(true); chunks=(Map<Long,AuraChunk>)f.get(AuraSavedData.get(h.getLevel())); }
            catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            key=new ChunkPos(pos).toLong(); previous=chunks.put(key,new AuraChunk(500,vis,flux));
        }
        @Override public void close() { if(previous==null) chunks.remove(key); else chunks.put(key,previous); }
    }
    @GameTest(template=TEMPLATE) public static void registryNowCreatesPhysicalImmovableFireproofRift(GameTestHelper h) {
        var r=rift(h,5); Vec3 pos=r.position(); r.move(MoverType.SELF,new Vec3(3,2,1)); r.setSecondsOnFire(100);
        CompoundTag tag=new CompoundTag(); r.addAdditionalSaveData(tag);
        h.assertTrue(r.position().equals(pos) && !r.isOnFire() && r.isPickable() && !tag.getBoolean("VisualOnly"),"Rift kept catalogue-only runtime or ordinary movement/fire"); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void deterministicSpineAndSyncedBoundsChangeWithSeedAndSize(GameTestHelper h) {
        var r=rift(h,80); var before=r.points; var bounds=r.getBoundingBox();
        h.assertTrue(before.size()==56 && r.pointsWidth.size()==56 && r.pointsWidth.get(0)==0 && r.pointsWidth.get(55)==0,"Original steps or guide widths changed");
        r.setRiftSeed(315); h.assertTrue(!r.points.equals(before) && !r.getBoundingBox().equals(bounds),"Synced seed did not rebuild actual physics bounds");
        r.setRiftSeed(314); h.assertTrue(r.points.equals(before),"Spine is not reproducible from pinned seed");
        r.setRiftSize(6); h.assertTrue(r.points.size()==6 && !r.getBoundingBox().equals(bounds),"Shrinking size kept stale physical bounds"); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void persistenceRetainsReleasedStabilityTruncationAndCollapseSnapshotQuirk(GameTestHelper h) {
        var r=rift(h,16); r.setRiftStability(-12.875F); r.setCollapse(true); r.setRiftSize(4);
        CompoundTag tag=new CompoundTag(); r.addAdditionalSaveData(tag); var loaded=rift(h,5); loaded.readAdditionalSaveData(tag);
        CompoundTag after=new CompoundTag(); loaded.addAdditionalSaveData(after);
        h.assertTrue(tag.getInt("MaxSize")==16 && after.getInt("MaxSize")==4 && loaded.getRiftStability()==tag.getInt("Stability")
                && loaded.getRiftSeed()==314 && loaded.getCollapse(),"BETA26 saved float/int or load reward snapshot was silently corrected"); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void strictStabilityBandsClampAndRejectNonfiniteValues(GameTestHelper h) {
        var r=rift(h,10); float[] values={101,50,0,-.1F,-25,-101};
        FluxRiftEntity.Stability[] bands={FluxRiftEntity.Stability.VERY_STABLE,FluxRiftEntity.Stability.STABLE,FluxRiftEntity.Stability.STABLE,
                FluxRiftEntity.Stability.UNSTABLE,FluxRiftEntity.Stability.VERY_UNSTABLE,FluxRiftEntity.Stability.VERY_UNSTABLE};
        for(int i=0;i<values.length;i++) { r.setRiftStability(values[i]); h.assertTrue(r.getStability()==bands[i],"Wrong stability threshold "+values[i]); }
        r.setRiftStability(Float.NaN); h.assertTrue(r.getRiftStability()==-100,"Nonfinite malformed input poisoned geometry/effects"); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void growthConsumesUntruncatedSquareRootOnlyOnOriginalCadence(GameTestHelper h) {
        var r=rift(h,20); r.setRiftStability(50); int cadence=Math.floorMod(r.getId(),600); if(cadence==0) cadence=600;
        try(var f=new AuraFixture(h,r.blockPosition(),10,100)) {
            r.tickCount=cadence-1; r.tick(); h.assertTrue(r.getRiftSize()==20 && AuraManager.getFlux(h.getLevel(),r.blockPosition())==100,"Rift grew before original entity-ID cadence");
            r.tickCount=cadence; r.tick();
            h.assertTrue(r.getRiftSize()==21 && AuraManager.getFlux(h.getLevel(),r.blockPosition())==100-(float)Math.sqrt(40),"Growth did not pay exact sqrt(size*2) Flux");
        } h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void veryStableAndSizeOneHundredRiftsDoNotGrow(GameTestHelper h) {
        var r=rift(h,20);
        try(var f=new AuraFixture(h,r.blockPosition(),0,1000)) {
            r.tickCount=r.getId()%600; r.tick(); h.assertTrue(r.getRiftSize()==20,"Very stable rift consumed growth Flux");
            r.setRiftSize(100); r.setRiftStability(0); r.tickCount=r.getId()%600; r.tick();
            h.assertTrue(r.getRiftSize()==100 && AuraManager.getFlux(h.getLevel(),r.blockPosition())==1000,"Rift grew past released size100 cap");
        } h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void nativeCollapseReturnsOneAuraPerShrinkingTickAndExactVoidSeeds(GameTestHelper h) {
        var r=rift(h,9); Vec3 center=r.position();
        try(var f=new AuraFixture(h,r.blockPosition(),100,100)) {
            r.setCollapse(true); tick(r,8);
            h.assertTrue(r.isRemoved() && seeds(h,center)==3,"Collapse did not drop floor(sqrt(initial size)) once");
            h.assertTrue(AuraManager.getVis(h.getLevel(),r.blockPosition())+AuraManager.getFlux(h.getLevel(),r.blockPosition())==208,"Collapse returned wrong number of physical aura units");
            r.tick(); h.assertTrue(seeds(h,center)==3,"Removed rift duplicated rewards");
        } h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void repeatedCollapseUsesCurrentSizeForRewardSnapshot(GameTestHelper h) {
        var r=rift(h,16); Vec3 center=r.position(); r.setCollapse(true); r.setRiftSize(4); r.setCollapse(true);
        tick(r,3); h.assertTrue(r.isRemoved() && seeds(h,center)==2,"Repeated setCollapse incorrectly retained an earlier reward snapshot"); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void actualPearlChanceIsIndependentOfSeedCountAndKeepsOriginalDamageFourThroughSeven(GameTestHelper h) {
        // Select native RNG seeds producing a1% pearl roll, without the optional explosion.
        // Nothing overrides the reward or RNG implementation used by the real entity tick.
        for(int damage=4;damage<=7;damage++) {
            long seed=0;
            for(;;seed++) {
                var rng=net.minecraft.util.RandomSource.create(seed);
                rng.nextInt(3); rng.nextBoolean();
                if(rng.nextInt(10)!=0 && rng.nextInt(100)==0 && 4+rng.nextInt(4)==damage) break;
                if(seed>100000) throw new IllegalStateException("Cannot prepare pearl RNG fixture");
            }
            var r=rift(h,2); r.setPos(r.position().add((damage-4)*5,0,0)); r.getRandom().setSeed(seed); r.setCollapse(true); tick(r,1);
            var pearl=ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","primordial_pearl"));
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(r.position(),r.position()).inflate(2),e->e.getItem().is(pearl));
            h.assertTrue(drops.size()==1 && drops.get(0).getItem().getDamageValue()==damage && seeds(h,r.position())==1,
                    "Native1% pearl reward lost original damage variant or altered independent Void Seed reward");
            drops.forEach(Entity::discard);
        } h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void randomSpineActuallyErodesBreakableTerrainWithoutLootButPreservesBedrock(GameTestHelper h) {
        var r=rift(h,30); BlockPos origin=r.blockPosition();
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-2,-2,-2),origin.offset(2,2,2))) h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        tick(r,1); long air=BlockPos.betweenClosedStream(origin.offset(-2,-2,-2),origin.offset(2,2,2)).filter(h.getLevel()::isEmptyBlock).count();
        h.assertTrue(air==1 && h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(3)).isEmpty(),"Segment did not remove exactly one collidable block without ordinary loot");
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-2,-2,-2),origin.offset(2,2,2))) h.getLevel().setBlock(pos,Blocks.BEDROCK.defaultBlockState(),2);
        tick(r,1); h.assertTrue(BlockPos.betweenClosedStream(origin.offset(-2,-2,-2),origin.offset(2,2,2)).allMatch(pos->h.getLevel().getBlockState(pos).is(Blocks.BEDROCK)),"Negative-hardness block was erased"); h.succeed();
    }
    @GameTest(template=TEMPLATE,timeoutTicks=20) public static void nativeSpineErodesSelectableFlowerAndCrystalWithoutPhysicalCollisionOrLoot(GameTestHelper h) {
        BlockPos flower=h.absolutePos(CENTER.offset(-2,0,0)),crystal=h.absolutePos(CENTER.offset(2,0,0));
        h.getLevel().setBlock(flower.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.getLevel().setBlock(crystal.below(),Blocks.STONE.defaultBlockState(),3);
        var flowerState=Blocks.DANDELION.defaultBlockState();
        var crystalState=WorldModule.CRYSTALS.get("aer").get().defaultBlockState();
        h.getLevel().setBlock(flower,flowerState,3); h.getLevel().setBlock(crystal,crystalState,3);
        h.assertTrue(flowerState.canSurvive(h.getLevel(),flower) && crystalState.canSurvive(h.getLevel(),crystal),"Noncolliding specimens have no real native support");
        h.assertTrue(flowerState.getCollisionShape(h.getLevel(),flower).isEmpty() && crystalState.getCollisionShape(h.getLevel(),crystal).isEmpty(),"Specimens accidentally have physical collision");
        var flowerRift=alignedNativeRift(h,flower,flowerState.getShape(h.getLevel(),flower).bounds().move(flower));
        var crystalRift=alignedNativeRift(h,crystal,crystalState.getShape(h.getLevel(),crystal).bounds().move(crystal));
        h.assertTrue(flower.equals(nativeSegmentHit(h,flowerRift,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE))
                && crystal.equals(nativeSegmentHit(h,crystalRift,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE)),"Actual outline ray does not intersect the two specimens");
        h.assertTrue(nativeSegmentHit(h,flowerRift,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE)==null
                && nativeSegmentHit(h,crystalRift,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE)==null,"A collision-only ray already selects the specimen");
        h.assertTrue(h.getLevel().addFreshEntity(flowerRift) && h.getLevel().addFreshEntity(crystalRift),"Native erosion rifts did not spawn");
        h.runAfterDelay(2,()->{
            try {
                h.assertTrue(flowerRift.tickCount>0 && crystalRift.tickCount>0,"Native server never ticked the actual rifts");
                h.assertTrue(h.getLevel().isEmptyBlock(flower) && h.getLevel().isEmptyBlock(crystal),"Original selectable noncolliding flower/crystal survived actual spine erosion");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(flower).inflate(1)).isEmpty()
                        && h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(crystal).inflate(1)).isEmpty(),"Rift erosion harvested ordinary flower/crystal loot");
                h.succeed();
            } finally { flowerRift.discard(); crystalRift.discard(); }
        });
    }
    @GameTest(template=TEMPLATE,timeoutTicks=20) public static void nativeOutlineSpinePreservesIntersectedWaterAndUnbreakableBedrock(GameTestHelper h) {
        BlockPos water=h.absolutePos(CENTER.offset(-2,0,0)),bedrock=h.absolutePos(CENTER.offset(2,0,0));
        h.getLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
        h.getLevel().setBlock(bedrock,Blocks.BEDROCK.defaultBlockState(),3);
        var waterRift=alignedNativeRift(h,water,new AABB(water));
        var bedrockRift=alignedNativeRift(h,bedrock,new AABB(bedrock));
        h.assertTrue(water.equals(nativeSegmentHit(h,waterRift,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY))
                && nativeSegmentHit(h,waterRift,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE)==null,"Actual spine misses water or falsely treats it as a selectable solid");
        h.assertTrue(bedrock.equals(nativeSegmentHit(h,bedrockRift,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE))
                && h.getLevel().getBlockState(bedrock).getDestroySpeed(h.getLevel(),bedrock)<0,"Actual spine misses the negative-hardness bedrock fixture");
        h.assertTrue(h.getLevel().addFreshEntity(waterRift) && h.getLevel().addFreshEntity(bedrockRift),"Native negative erosion rifts did not spawn");
        h.runAfterDelay(2,()->{
            try {
                h.assertTrue(waterRift.tickCount>0 && bedrockRift.tickCount>0,"Native server never ticked the negative erosion rifts");
                h.assertTrue(h.getLevel().getBlockState(water).is(Blocks.WATER) && h.getLevel().getBlockState(bedrock).is(Blocks.BEDROCK),"Outline erosion removed original liquid/unbreakable negatives");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(water).inflate(1)).isEmpty()
                        && h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(bedrock).inflate(1)).isEmpty(),"Negative erosion fixtures produced ordinary loot");
                h.succeed();
            } finally { waterRift.discard(); bedrockRift.discard(); }
        });
    }
    @GameTest(template=TEMPLATE) public static void actualSpineDamagesLivingEntitiesDeletesItemsAndExemptsCreativePlayers(GameTestHelper h) {
        var r=rift(h,9); Vec3 center=r.position(); Cow cow=EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setPos(center);
        ItemEntity item=new ItemEntity(h.getLevel(),center.x,center.y,center.z,new ItemStack(Items.DIAMOND)); item.setNoGravity(true);
        var creative=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rift_creative")); creative.setGameMode(GameType.CREATIVE); creative.setPos(center);
        h.getLevel().addFreshEntity(cow); h.getLevel().addFreshEntity(item); h.getLevel().addFreshEntity(creative);
        float hp=cow.getHealth(),playerHp=creative.getHealth(); tick(r,1);
        h.assertTrue(cow.getHealth()==hp-2 && item.isRemoved() && creative.getHealth()==playerHp,"Actual spine hazard or creative exemption differs from release");
        cow.discard(); creative.discard(); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void stableCollapseWarpUsesSquaredDistanceDivisorThirtyTwo(GameTestHelper h) {
        var r=rift(h,2); r.setRiftStability(0); Vec3 center=r.position();
        var close=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rift_near")); close.setPos(center.add(2,0,0));
        var far=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rift_far")); far.setPos(center.add(6,0,0));
        h.getLevel().addFreshEntity(close); h.getLevel().addFreshEntity(far);
        r.setCollapse(true); tick(r,1);
        h.assertTrue(KnowledgeStore.get(close).normalWarp()==21 && KnowledgeStore.get(close).temporaryWarp()==21
                && KnowledgeStore.get(far).normalWarp()==0 && !close.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),"Collapse used distance/32, omitted original warp channels or added weakness to STABLE");
        close.discard(); far.discard(); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void veryUnstableCollapseFallsThroughTaintWeaknessAndBothWarpChannels(GameTestHelper h) {
        var r=rift(h,2); r.setRiftStability(-25); var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rift_unstable"));
        p.setPos(r.position().add(2,0,0)); h.getLevel().addFreshEntity(p); r.setCollapse(true); tick(r,1);
        var taint=ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","flux_taint"));
        h.assertTrue(p.hasEffect(taint) && p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)
                && KnowledgeStore.get(p).normalWarp()==21 && KnowledgeStore.get(p).temporaryWarp()==21,"VERY_UNSTABLE switch fallthrough was lost");
        h.assertTrue(p.getEffect(taint).getDuration()==2100 && p.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS).getDuration()==5240,"Released squared-distance duration changed");
        p.discard(); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void realStabilizerSpendsStoredEnergyPerRiftAndDelaysNextTreatment(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(4,2,4)); h.getLevel().setBlock(pos,CatalogBlocks.block("stabilizer").defaultBlockState(),3);
        var tile=(InfusionStabilizerBlockEntity)h.getLevel().getBlockEntity(pos); CompoundTag tag=tile.saveWithoutMetadata(); tag.putInt("energy",2); tag.putInt("ticks",4); tile.load(tag);
        var r=rift(h,5); r.setPos(Vec3.atCenterOf(pos.above(2))); r.setRiftStability(0); h.getLevel().addFreshEntity(r);
        var state=h.getLevel().getBlockState(pos); InfusionStabilizerBlockEntity.tick(h.getLevel(),pos,state,tile);
        h.assertTrue(tile.energy()==1 && r.getRiftStability()==.125F,"Stabilizer did not spend exactly one energy for .125 stability");
        for(int i=0;i<4;i++) InfusionStabilizerBlockEntity.tick(h.getLevel(),pos,state,tile);
        h.assertTrue(tile.energy()==1 && r.getRiftStability()==.125F,"Per-rift delay did not prevent early treatment");
        InfusionStabilizerBlockEntity.tick(h.getLevel(),pos,state,tile); h.assertTrue(tile.energy()==0 && r.getRiftStability()==.25F,"Original delayed fifth tick did not treat next rift");
        r.discard(); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void candidateUsesStrictLunarBaseAndUnclampedFluxProbability(GameTestHelper h) {
        h.assertTrue(!RiftGeneration.candidate(375,500,0) && RiftGeneration.candidate(375.1F,500,0)
                && !RiftGeneration.candidate(500,500,.1F) && RiftGeneration.candidate(500,500,.099F)
                && RiftGeneration.candidate(5001,500,.999F),"Original75% lunar threshold or flux/500/10 probability changed"); h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void naturalSpawnDebitsFloatSizeAndNearbyRiftBlocksAnotherSpawn(GameTestHelper h) {
        ChunkPos chunk=new ChunkPos(h.absolutePos(CENTER));
        try(var f=new AuraFixture(h,h.absolutePos(CENTER),10,101)) {
            var r=RiftGeneration.createRift(h.getLevel(),chunk);
            h.assertTrue(r!=null && r.getRiftSize()==17 && AuraManager.getFlux(h.getLevel(),r.blockPosition())==101-(float)Math.sqrt(303),"Native surface spawn did not pay untruncated sqrt(flux*3)");
            float after=AuraManager.getFlux(h.getLevel(),r.blockPosition());
            h.assertTrue(RiftGeneration.createRift(h.getLevel(),chunk)==null && AuraManager.getFlux(h.getLevel(),r.blockPosition())==after,"Nearby rift did not block another spawn without payment");
            r.discard();
        } h.succeed();
    }
    @GameTest(template=TEMPLATE) public static void insufficientFluxAndUnloadedChunkCannotCreateOrDebitRift(GameTestHelper h) {
        BlockPos pos=h.absolutePos(CENTER);
        try(var f=new AuraFixture(h,pos,10,8)) {
            h.assertTrue(RiftGeneration.createRift(h.getLevel(),new ChunkPos(pos))==null && AuraManager.getFlux(h.getLevel(),pos)==8,"Size<=5 candidate paid or spawned");
            h.assertTrue(RiftGeneration.createRift(h.getLevel(),new ChunkPos(900000,900000))==null,"Generation force-loaded an absent chunk");
        } h.succeed();
    }
}
