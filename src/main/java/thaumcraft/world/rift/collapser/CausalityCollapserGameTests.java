package thaumcraft.world.rift.collapser;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.world.rift.FluxRiftEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CausalityCollapserGameTests {
    @GameTest(template="essentia_network")
    public static void actualThrowConsumesOneAndRetainsOriginalLaunchWithoutResearchOrCooldown(GameTestHelper h) {
        var p=player(h); var held=CatalogModule.stack("causality_collapser"); held.setCount(3); p.setItemInHand(InteractionHand.MAIN_HAND,held);
        var knowledge=KnowledgeStore.get(p).save(); var item=held.getItem();
        h.assertTrue(item instanceof CausalityCollapserItem && held.getMaxStackSize()==16 && !held.isDamageableItem(),"Original collapser item16/no-durability contract changed");
        h.assertTrue(item.use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult().consumesAction() && held.getCount()==2,"First real throw failed its exact one-item payment");
        var first=owned(h,p); h.assertTrue(first.size()==1 && first.get(0).getOwner()==p,"Throw did not create an owner-bound working projectile");
        var motion=first.get(0).getDeltaMovement();
        h.assertTrue(motion.length()>.70 && motion.length()<.90 && motion.y>.015 && motion.z>.70,
                "Actual speed.8/inaccuracy2/pitch-5 launch changed: "+motion);
        h.assertTrue(first.get(0).position().distanceToSqr(new Vec3(p.getX(),p.getEyeY()-.1,p.getZ()))<1e-9,"Projectile lost its native eye launch origin");
        item.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(owned(h,p).size()==2 && held.getCount()==1 && !p.getCooldowns().isOnCooldown(item)
                && knowledge.equals(KnowledgeStore.get(p).save()),"Immediate second throw added a cooldown/research grant or duplicate payment");
        owned(h,p).forEach(Entity::discard); h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void creativeOffhandLaunchIsFreeAndProjectileSaveKeepsOwnerAndMomentum(GameTestHelper h) {
        var p=player(h); p.getAbilities().instabuild=true; var held=CatalogModule.stack("causality_collapser"); held.setCount(2);
        p.setItemInHand(InteractionHand.OFF_HAND,held); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));
        held.getItem().use(h.getLevel(),p,InteractionHand.OFF_HAND); var projectile=owned(h,p).get(0);
        h.assertTrue(held.getCount()==2 && p.getMainHandItem().is(Items.STONE),"Creative/offhand throw consumed or replaced the wrong hand");
        var saved=new CompoundTag(); projectile.saveWithoutId(saved);
        var loaded=VisualEntitiesModule.CAUSALITY_COLLAPSER.get().create(h.getLevel()); loaded.load(saved);
        var restored=new CompoundTag(); loaded.saveWithoutId(restored);
        h.assertTrue(saved.getUUID("Owner").equals(p.getUUID()) && restored.getUUID("Owner").equals(p.getUUID())
                && projectile.getDeltaMovement().distanceToSqr(loaded.getDeltaMovement())<1e-9
                && projectile.position().distanceToSqr(loaded.position())<1e-9,"Native projectile owner/position/momentum failed save/load");
        projectile.discard(); h.succeed();
    }

    @GameTest(template="essentia_network")
    public static void rejectedSpawnAndWrongHandOrSpectatorCannotDebitOrCreateProjectiles(GameTestHelper h) {
        var p=player(h); var held=CatalogModule.stack("causality_collapser"); held.setCount(2); p.setItemInHand(InteractionHand.MAIN_HAND,held);
        Consumer<EntityJoinLevelEvent> veto=event->{if(event.getLevel()==h.getLevel() && event.getEntity() instanceof CausalityCollapserEntity c && c.getOwner()==p) event.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(veto);
        try {h.assertTrue(held.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND).getResult()==net.minecraft.world.InteractionResult.FAIL
                && held.getCount()==2 && owned(h,p).isEmpty(),"Forge spawn rejection consumed a collapser");}
        finally {MinecraftForge.EVENT_BUS.unregister(veto);}
        var item=held.getItem(); p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STONE)); item.use(h.getLevel(),p,InteractionHand.OFF_HAND);
        ((AuditPlayer)p).spectator=true; item.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(held.getCount()==2 && owned(h,p).isEmpty(),"Invalid hand/spectator action created or paid a projectile"); h.succeed();
    }

    @GameTest(template="empty")
    public static void releasedProjectileOverrideForcesSpeedPointEightEvenWhenCallerRequestsAnotherVelocity(GameTestHelper h) {
        var projectile=VisualEntitiesModule.CAUSALITY_COLLAPSER.get().create(h.getLevel());
        projectile.shoot(0,0,1,5,0);
        h.assertTrue(Math.abs(projectile.getDeltaMovement().z-.8F)<1e-8 && projectile.getDeltaMovement().x==0 && projectile.getDeltaMovement().y==0,
                "Original shoot override accepted a caller's different velocity"); h.succeed();
    }

    @GameTest(template="essentia_network",timeoutTicks=80)
    public static void nativeCollisionExplodesAtStrengthTwoAndRemovesItsPaidProjectileOnce(GameTestHelper h) {
        var p=player(h); var level=h.getLevel(); var wall=h.absolutePos(new BlockPos(6,5,7));
        for(var at:BlockPos.betweenClosed(wall.offset(-4,-4,0),wall.offset(4,4,0))) level.setBlockAndUpdate(at,Blocks.BEDROCK.defaultBlockState());
        var held=CatalogModule.stack("causality_collapser"); p.setItemInHand(InteractionHand.MAIN_HAND,held);
        held.getItem().use(level,p,InteractionHand.MAIN_HAND); var projectile=owned(h,p).get(0);
        var explosions=new AtomicInteger(); var position=new AtomicReference<Vec3>();
        Consumer<ExplosionEvent.Start> witness=event->{
            if(event.getExplosion().getDirectSourceEntity()!=projectile) return;
            explosions.incrementAndGet(); position.set(event.getExplosion().getPosition());
            Float radius=ObfuscationReflectionHelper.getPrivateValue(Explosion.class,event.getExplosion(),"f_46017_");
            h.assertTrue(radius!=null && radius==2F && event.getExplosion().interactsWithBlocks(),"Native impact explosion changed its strength2/block-damage contract");
        };
        MinecraftForge.EVENT_BUS.addListener(witness);
        h.runAfterDelay(15,()->{
            try {
                h.assertTrue(projectile.isRemoved() && explosions.get()==1 && position.get()!=null,"Native flight failed to hit/explode/remove once");
                h.assertTrue(held.isEmpty(),"Paid native impact restored the consumed item"); h.succeed();
            } finally {MinecraftForge.EVENT_BUS.unregister(witness); projectile.discard();}
        });
    }

    @GameTest(template="essentia_network")
    public static void actualImpactCallbackUsesTheReleasedCubeAndRemovedProjectileCannotCollapseAgain(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(6,5,6)).getCenter();
        var inside=rift(h,at); var corner=rift(h,at.add(2.5,0,2.5)); var outside=rift(h,at.add(20,0,0));
        var projectile=VisualEntitiesModule.CAUSALITY_COLLAPSER.get().create(h.getLevel()); projectile.setPos(at);
        h.assertTrue(h.getLevel().addFreshEntity(projectile),"Cannot insert real impact callback projectile");
        var hit=new net.minecraft.world.phys.BlockHitResult(at,net.minecraft.core.Direction.UP,BlockPos.containing(at),false);
        var explosions=new AtomicInteger(); Consumer<ExplosionEvent.Start> witness=event->{if(event.getExplosion().getDirectSourceEntity()==projectile)explosions.incrementAndGet();};
        MinecraftForge.EVENT_BUS.addListener(witness);
        try {
            h.assertTrue(projectile.position().distanceToSqr(corner.position())>9,"Corner fixture does not distinguish original cube from a spherical center-distance filter");
            projectile.onHit(hit);
            h.assertTrue(inside.getCollapse() && corner.getCollapse() && !outside.getCollapse() && projectile.isRemoved() && explosions.get()==1,
                    "Impact skipped a cube corner, collapsed a distant rift or failed removal/explosion");
            inside.setCollapse(false); corner.setCollapse(false); projectile.onHit(hit);
            h.assertTrue(!inside.getCollapse() && !corner.getCollapse() && explosions.get()==1,"Removed projectile replayed its explosion/collapse");
        } finally {MinecraftForge.EVENT_BUS.unregister(witness); inside.discard(); corner.discard(); outside.discard(); projectile.discard();}
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p=new AuditPlayer(h); var at=h.absolutePos(new BlockPos(6,5,1)); p.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
        p.setYRot(0); p.setXRot(0); p.setDeltaMovement(Vec3.ZERO); return p;
    }
    private static List<CausalityCollapserEntity> owned(GameTestHelper h,ServerPlayer p) {
        return h.getLevel().getEntitiesOfClass(CausalityCollapserEntity.class,new AABB(p.blockPosition()).inflate(32),c->c.getOwner()==p);
    }
    private static FluxRiftEntity rift(GameTestHelper h,Vec3 at) {
        var rift=(FluxRiftEntity)VisualEntitiesModule.EFFECTS.get("flux_rift").get().create(h.getLevel());
        rift.setPos(at); rift.setRiftSeed(314); rift.setRiftSize(40); rift.setRiftStability(100); h.assertTrue(h.getLevel().addFreshEntity(rift),"Cannot insert actual impact rift fixture"); return rift;
    }
    private static final class AuditPlayer extends FakePlayer {
        boolean spectator;
        AuditPlayer(GameTestHelper h) {super(h.getLevel(),new GameProfile(UUID.randomUUID(),"collapser_use"));}
        @Override public boolean isSpectator() {return spectator;}
    }
}
