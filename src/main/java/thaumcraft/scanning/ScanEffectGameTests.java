package thaumcraft.scanning;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.infusion.InfusionEffects;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.ResearchCategories;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Independent registered facts do not make an already scanned specimen pay generic Observation again. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ScanEffectGameTests {
    private ScanEffectGameTests() {}

    @GameTest(template = "empty")
    public static void renamedVanillaEnchantmentsRetainOriginalResearchKeys(GameTestHelper h) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
        helmet.enchant(Enchantments.FIRE_PROTECTION, 3);
        helmet.enchant(Enchantments.RESPIRATION, 1);
        helmet.enchant(Enchantments.AQUA_AFFINITY, 1);
        h.assertTrue(Set.copyOf(ScanEffectFacts.facts(helmet)).equals(Set.of("!enchantment.protect.all",
                        "!enchantment.protect.fire", "!enchantment.oxygen", "!enchantment.waterWorker")),
                "Vanilla registry renames lost the original enchantment discovery identifiers");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void storedBooksAndLevelChangesIdentifyEnchantmentsOnce(GameTestHelper h) {
        ItemStack low = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 1));
        ItemStack high = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 5));
        h.assertTrue(ScanEffectFacts.facts(low).equals(List.of("!enchantment.damage.all"))
                        && ScanEffectFacts.facts(low).equals(ScanEffectFacts.facts(high)),
                "Stored enchantments were missed or their level invented distinct research facts");
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 4);
        sword.enchant(Enchantments.FIRE_ASPECT, 2);
        h.assertTrue(Set.copyOf(ScanEffectFacts.facts(sword)).equals(Set.of("!enchantment.damage.all", "!enchantment.fire")),
                "One specimen omitted a second registered enchantment handler");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryPotionFormsIdentifyTheEffectRegardlessOfDurationAndStrength(GameTestHelper h) {
        for (var item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
            ItemStack low = PotionUtils.setPotion(new ItemStack(item), Potions.SWIFTNESS);
            ItemStack strong = PotionUtils.setPotion(new ItemStack(item), Potions.STRONG_SWIFTNESS);
            ItemStack longer = PotionUtils.setPotion(new ItemStack(item), Potions.LONG_SWIFTNESS);
            h.assertTrue(ScanEffectFacts.facts(low).equals(List.of("!effect.moveSpeed"))
                            && ScanEffectFacts.facts(low).equals(ScanEffectFacts.facts(strong))
                            && ScanEffectFacts.facts(low).equals(ScanEffectFacts.facts(longer)),
                    "Potion form, duration or amplifier changed its original research key: " + item);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customPotionMixturesRunAllMatchingHandlersWithoutDuplicatingAnEffect(GameTestHelper h) {
        ItemStack mixture = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.SWIFTNESS);
        PotionUtils.setCustomEffects(mixture, List.of(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2),
                new MobEffectInstance(MobEffects.POISON, 80), new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60)));
        CompoundTag before = mixture.save(new CompoundTag());
        h.assertTrue(Set.copyOf(ScanEffectFacts.facts(mixture)).equals(Set.of("!effect.moveSpeed", "!effect.poison", "!effect.digSlowDown"))
                        && ScanEffectFacts.facts(mixture).size() == 3 && before.equals(mixture.save(new CompoundTag())),
                "Custom potion effects were skipped, duplicated, renamed or changed by examining the stack");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void droppedItemsUseTheSameRegisteredEffectFactsAsTheirStacks(GameTestHelper h) {
        ItemStack sample = PotionUtils.setPotion(new ItemStack(Items.POTION, 7), Potions.HEALING);
        ItemEntity dropped = new ItemEntity(h.getLevel(), 0, 0, 0, sample.copy());
        CompoundTag before = dropped.getItem().save(new CompoundTag());
        h.assertTrue(ScanEffectFacts.facts(dropped).equals(List.of("!effect.heal"))
                        && ScanEffectFacts.facts(dropped).equals(ScanEffectFacts.facts(sample))
                        && before.equals(dropped.getItem().save(new CompoundTag())),
                "Dropped item acquired an entity-specific effect key or was consumed by the predicate");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void livingEffectPredicatesAreIndependentOfAmplifierAndNeverWriteKnowledge(GameTestHelper h) {
        ServerPlayer p = player(h);
        Cow cow = new Cow(EntityType.COW, h.getLevel());
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 80));
        cow.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 2));
        CompoundTag before = KnowledgeStore.get(p).save();
        for (int i = 0; i < 20; i++) h.assertTrue(Set.copyOf(ScanEffectFacts.facts(cow)).equals(Set.of("!effect.poison", "!effect.glowing")),
                "Living effects did not use the original separate potion handlers");
        cow.removeEffect(MobEffects.POISON);
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 240, 3));
        h.assertTrue(ScanEffectFacts.facts(cow).size() == 2 && before.equals(KnowledgeStore.get(p).save())
                        && cow.getEffect(MobEffects.POISON).getDuration() == 240 && cow.getEffect(MobEffects.POISON).getAmplifier() == 3,
                "Amplifier/duration created research or examining effects altered them");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeThaumcraftEffectsRetainTheirReleasedPotionNames(GameTestHelper h) {
        Cow cow = new Cow(EntityType.COW, h.getLevel());
        cow.addEffect(new MobEffectInstance(InfusionEffects.FLUX_TAINT.get(), 60));
        cow.addEffect(new MobEffectInstance(InfusionEffects.VIS_EXHAUST.get(), 100, 1));
        h.assertTrue(Set.copyOf(ScanEffectFacts.facts(cow)).equals(Set.of("!potion.flux_taint", "!potion.vis_exhaust")),
                "Ported Thaumcraft effect registry IDs replaced their original research keys");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void modernRegistryEntriesUseDocumentedNativeKeyExtension(GameTestHelper h) {
        ItemStack leggings = new ItemStack(Items.DIAMOND_LEGGINGS);
        leggings.enchant(Enchantments.SWIFT_SNEAK, 1);
        ItemStack modern = PotionUtils.setCustomEffects(new ItemStack(Items.POTION), List.of(new MobEffectInstance(MobEffects.DARKNESS, 80)));
        h.assertTrue(ScanEffectFacts.facts(leggings).equals(List.of("!" + Enchantments.SWIFT_SNEAK.getDescriptionId()))
                        && ScanEffectFacts.facts(modern).equals(List.of("!" + MobEffects.DARKNESS.getDescriptionId())),
                "Registry-wide compatibility invented a legacy key for a post-1.12 effect");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyAndOrdinarySpecimensDoNotInventEffectFacts(GameTestHelper h) {
        h.assertTrue(ScanEffectFacts.facts(null).isEmpty() && ScanEffectFacts.facts(ItemStack.EMPTY).isEmpty()
                        && ScanEffectFacts.facts(new ItemStack(Items.COAL)).isEmpty()
                        && ScanEffectFacts.facts(Blocks.CHEST.defaultBlockState()).isEmpty()
                        && ScanEffectFacts.facts(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER)).isEmpty(),
                "Ordinary specimen acquired a fabricated potion or enchantment discovery");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newEnchantmentFactOnCreditedItemDoesNotRepayObservation(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack first = new ItemStack(Items.IRON_PICKAXE);
        first.enchant(Enchantments.BLOCK_EFFICIENCY, 1);
        scanHeld(p, first);
        var knowledge = KnowledgeStore.get(p);
        h.assertTrue(knowledge.isResearchKnown("!enchantment.digging"), "Actual item scan omitted its enchantment fact");
        Map<String, Integer> observations = observations(p);
        int count = knowledge.scanCount();
        ItemStack changed = first.copy();
        changed.enchant(Enchantments.BINDING_CURSE, 1);
        p.setItemInHand(InteractionHand.OFF_HAND, changed);
        CompoundTag before = knowledge.save();
        var hover = ScanningNetwork.capture(p);
        h.assertTrue(hover.target() != null && !hover.target().scanned() && before.equals(knowledge.save()),
                "New enchantment hover was not eligible or wrote its research fact");
        scanHeld(p, changed);
        h.assertTrue(knowledge.isResearchKnown("!enchantment.binding_curse") && knowledge.scanCount() == count
                        && observations.equals(observations(p)),
                "Separate enchantment discovery repaid generic Observation or duplicated specimen identity");
        before = knowledge.save();
        ItemStack stronger = new ItemStack(Items.IRON_PICKAXE);
        stronger.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
        stronger.enchant(Enchantments.BINDING_CURSE, 1);
        scanHeld(p, stronger);
        h.assertTrue(before.equals(knowledge.save()), "Enchantment levels repaid observations or duplicated facts");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void effectAppliedAfterEntityScanCreatesOnlyItsNewFactAndHoverRemainsReadOnly(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setShiftKeyDown(false);
        Cow cow = new Cow(EntityType.COW, h.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(p.getX(), p.getEyeY() - .125, p.getZ() + 2);
        h.assertTrue(h.getLevel().addFreshEntity(cow), "Cannot add native scanner target");
        try {
            scan(p);
            var knowledge = KnowledgeStore.get(p);
            int count = knowledge.scanCount();
            Map<String, Integer> observations = observations(p);
            cow.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 2));
            CompoundTag before = knowledge.save();
            var hover = ScanningNetwork.capture(p);
            h.assertTrue(hover.target() != null && hover.target().location().entityId() == cow.getId()
                            && !hover.target().scanned() && before.equals(knowledge.save()),
                    "New living effect hover missed eligibility or committed its fact");
            scan(p);
            h.assertTrue(knowledge.isResearchKnown("!effect.poison") && knowledge.scanCount() == count
                            && observations.equals(observations(p)) && cow.getEffect(MobEffects.POISON).getAmplifier() == 2,
                    "Living effect fact repeated generic observations or consumed the actual effect");
            before = knowledge.save();
            scan(p);
            h.assertTrue(before.equals(knowledge.save()), "Repeated living effect scan was not deduplicated");
        } finally { cow.discard(); }
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ScanEffects"));
        BlockPos start = h.absolutePos(new BlockPos(1, 2, 1));
        p.setPos(start.getX() + .5, start.getY(), start.getZ() + .5);
        p.setYRot(0);
        p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        return p;
    }

    private static void scanHeld(ServerPlayer p, ItemStack stack) {
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.OFF_HAND, stack);
        scan(p);
    }

    private static void scan(ServerPlayer p) {
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
    }

    private static Map<String, Integer> observations(ServerPlayer p) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String category : ResearchCategories.keys()) result.put(category, KnowledgeStore.get(p).rawKnowledge(KnowledgeType.OBSERVATION, category));
        return result;
    }
}
