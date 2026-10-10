package thaumcraft.world.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.infusion.InfusionEffects;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Native effect ticking, damage registry/tag routing and original taint/undead branches. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FluxTaintGameTests {
    private FluxTaintGameTests() {}
    private static FluxTaintEffect effect() { return (FluxTaintEffect)InfusionEffects.FLUX_TAINT.get(); }
    private static Cow cow(GameTestHelper h) {
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        cow.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,1,1))));return cow;
    }
    @GameTest(template="empty") public static void fluxTaintCadenceMatchesOriginalJavaShiftIncludingHighAmplifierMask(GameTestHelper h) {
        for(int amp : new int[]{0,1,2,3,4,5,6,30,31,32,33,255})for(int duration=1;duration<=80;duration++){
            int interval=40>>amp;
            h.assertTrue(effect().isDurationEffectTick(duration,amp)==(interval<=0||duration%interval==0),"Original40>>amplifier cadence changed at tier"+amp+"/duration"+duration);
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void ordinaryNativePoisonTickDamagesOneOnlyAtTheOriginalDurationBoundary(GameTestHelper h) {
        var cow=cow(h);cow.addEffect(new MobEffectInstance(effect(),80,0));
        try {
            cow.tick();h.assertTrue(cow.getHealth()==99&&cow.getEffect(effect()).getDuration()==79,"Native starting80tick duration did not apply original1taint damage");
            for(int i=0;i<39;i++)cow.tick();
            h.assertTrue(cow.getHealth()==99&&cow.getEffect(effect()).getDuration()==40,"Taint applied before the next40-duration boundary");
            cow.tick();h.assertTrue(cow.getHealth()==98&&cow.getEffect(effect()).getDuration()==39,"Native second40boundary did not deal exactly1damage");
        } finally { cow.discard(); }h.succeed();
    }
    @GameTest(template="empty") public static void taintUsesItsOwnNativeDamageTypeArmorBypassMagicResistanceAndNoAttacker(GameTestHelper h) {
        var cow=cow(h);var captured=new AtomicReference<DamageSource>();
        Consumer<LivingHurtEvent> listener=e->{if(e.getEntity()==cow)captured.set(e.getSource());};
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            effect().applyEffectTick(cow,0);var source=captured.get();
            h.assertTrue(cow.getHealth()==99&&source!=null&&source.is(TaintDamage.TYPE)&&source.getMsgId().equals("taint")
                    && source.is(DamageTypeTags.BYPASSES_ARMOR)&&source.is(DamageTypeTags.BYPASSES_SHIELD)
                    && source.is(DamageTypeTags.WITCH_RESISTANT_TO)&&source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
                    &&!source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                    && source.getEntity()==null&&source.getDirectEntity()==null&&source.getFoodExhaustion()==0,"Taint was routed through generic magic or lost released damage flags/source identity");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener);cow.discard(); }h.succeed();
    }
    @GameTest(template="empty") public static void originalTaintBypassesActualArmorButOrdinaryResistanceStillProtects(GameTestHelper h) {
        var armored=cow(h);var resistant=cow(h);
        armored.getAttribute(Attributes.ARMOR).setBaseValue(20);armored.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        resistant.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));
        try {
            h.assertTrue(armored.getArmorValue()==20,"Physical armor control is missing");
            effect().applyEffectTick(armored,0);effect().applyEffectTick(resistant,0);
            h.assertTrue(armored.getHealth()==99&&Math.abs(resistant.getHealth()-99.2F)<.0001,"Taint lost original armor bypass or ordinary20percent Resistance handling");
        } finally { armored.discard();resistant.discard(); }h.succeed();
    }
    @GameTest(template="empty") public static void nativeUndeadAndOriginalZombieCatalogueTypesAreImmune(GameTestHelper h) {
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setNoAi(true);float start=zombie.getHealth();
        try { effect().applyEffectTick(zombie,0);h.assertTrue(zombie.getHealth()==start,"Original undead immunity damaged a native Zombie"); }
        finally { zombie.discard(); }
        for(String id : List.of("brainy_zombie","giant_brainy_zombie","inhabited_zombie")){
            var mob=VisualEntitiesModule.LIVING.get(id).get().create(h.getLevel());float before=mob.getHealth();
            try { effect().applyEffectTick(mob,0);h.assertTrue(mob.getHealth()==before,"Original undead class identity lost at catalogue adapter"+id); }
            finally { mob.discard(); }
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void allEightCurrentNativeTaintedClassesHealOneAndClampAtMaximum(GameTestHelper h) {
        for(String id : List.of("thaumic_slime","taint_crawler","taint_swarm","taintacle","taintacle_tiny","taintacle_giant","taint_seed","taint_seed_prime")){
            var mob=VisualEntitiesModule.LIVING.get(id).get().create(h.getLevel());mob.setHealth(10);
            try {
                h.assertTrue(FluxTaintEffect.isNativeTainted(mob),"Original ITaintedMob identity missing for"+id);
                effect().applyEffectTick(mob,0);h.assertTrue(mob.getHealth()==11,"Original tainted class did not heal exactly1: "+id);
                mob.setHealth(mob.getMaxHealth());effect().applyEffectTick(mob,5);h.assertTrue(mob.getHealth()==mob.getMaxHealth(),"Taint healing exceeded native maximum: "+id);
            } finally { mob.discard(); }
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void forgedTaintOrChampionNbtCannotTurnAnOrdinaryTargetIntoAHealedChampion(GameTestHelper h) {
        var cow=cow(h);cow.getPersistentData().putBoolean("Tainted",true);cow.getPersistentData().putInt("Champion",13);
        cow.getPersistentData().putInt("champion_mod",13);cow.getPersistentData().putString("EntityType","thaumcraft:taint_seed");
        try { h.assertTrue(!FluxTaintEffect.isNativeTainted(cow),"Persistent NBT substituted for an original native entity class");
            effect().applyEffectTick(cow,0);h.assertTrue(cow.getHealth()==99,"Unported champion attribute was invented from arbitrary NBT"); }
        finally { cow.discard(); }h.succeed();
    }
    @GameTest(template="empty") public static void fluxTaintHasOriginalMilkCureAndItsNativeInstanceCanActuallyBeCured(GameTestHelper h) {
        var cow=cow(h);cow.addEffect(new MobEffectInstance(effect(),100,0));
        try { var infection=cow.getEffect(effect());
            h.assertTrue(infection!=null&&!infection.getCurativeItems().isEmpty()&&infection.getCurativeItems().stream().anyMatch(s->s.is(Items.MILK_BUCKET)),"Original taint effect lost milk as a cure");
            cow.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
            h.assertTrue(!cow.hasEffect(effect())&&cow.getHealth()==100,"Native milk cure left taint active or dealt damage"); }
        finally { cow.discard(); }h.succeed();
    }
    @GameTest(template="empty") public static void oneHealthOrdinaryCreatureCanDieFromOriginalTaintWhileRemovedTargetsAreIgnored(GameTestHelper h) {
        var cow=cow(h);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1);cow.setHealth(1);
        var lootBox=cow.getBoundingBox().inflate(2);
        var existingLoot=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,lootBox)
                .stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        try { effect().applyEffectTick(cow,0);h.assertTrue(!cow.isAlive(),"Invented minimum-health exemption prevented original taint death"); }
        finally { cow.discard();h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,lootBox)
                .stream().filter(item->!existingLoot.contains(item.getUUID())).forEach(net.minecraft.world.entity.Entity::discard); }
        var removed=cow(h);removed.setHealth(70);removed.discard();effect().applyEffectTick(removed,0);
        h.assertTrue(removed.getHealth()==70,"Removed target still received a taint callback");h.succeed();
    }
    @GameTest(template="empty") public static void actualTaintEffectSaveRetainsAmplifierDurationAndNormalCure(GameTestHelper h) {
        var cow=cow(h);cow.addEffect(new MobEffectInstance(effect(),321,3));var saved=cow.saveWithoutId(new CompoundTag());
        var restored=EntityType.COW.create(h.getLevel());restored.load(saved);
        try { var taint=restored.getEffect(effect());
            h.assertTrue(taint!=null&&taint.getDuration()==321&&taint.getAmplifier()==3&&!taint.getCurativeItems().isEmpty(),"Native effect persistence changed taint cadence or cure"); }
        finally { cow.discard();restored.discard(); }h.succeed();
    }
}
