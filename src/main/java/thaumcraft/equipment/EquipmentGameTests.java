package thaumcraft.equipment;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.items.IGoggles;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.equipment.armor.FortressArmorSupport;
import thaumcraft.equipment.recharge.RechargeModule;
import thaumcraft.equipment.recharge.RechargePedestalBlockEntity;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchProgression;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** Runtime consumers of registered equipment, rather than a second registry mapping. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    private EquipmentGameTests() {}

    @GameTest(template = "empty")
    public static void wornThaumiumActuallyAbsorbsDamageAndUsesDurability(GameTestHelper helper) {
        TestPlayer player = player(helper);
        equip(player, "thaumium", true);
        player.doTick();
        helper.assertTrue(player.getArmorValue() == 15 && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 4,
                "Worn thaumium did not install its actual armor/toughness attributes");
        float before = player.getHealth();
        DamageSource hit = helper.getLevel().damageSources().mobAttack(cow(helper));
        helper.assertTrue(!hit.is(DamageTypeTags.BYPASSES_ARMOR), "Physical control source bypasses armor");
        player.receiveDamage(hit, 10);
        float expected = CombatRules.getDamageAfterAbsorb(10, 15, 4);
        near(helper, before - player.getHealth(), expected, "Worn thaumium health loss");
        for (ItemStack armor : player.getArmorSlots()) helper.assertTrue(armor.getDamageValue() == 2, "Actual damage did not wear thaumium armor");
        helper.assertTrue(stack("thaumium_chest").getItem().isValidRepairItem(stack("thaumium_chest"), stack("ingot_thaumium"))
                && !stack("thaumium_chest").getItem().isValidRepairItem(stack("thaumium_chest"), stack("ingot_void")),
                "Thaumium repair ingredient changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fortressSetNbtUpdatesWornAttributesAndRemovalClearsThem(GameTestHelper helper) {
        TestPlayer player = player(helper);
        equip(player, "fortress", false);
        player.doTick();
        helper.assertTrue(player.getArmorValue() == 19 && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 12,
                "Unmasked fortress set did not combine material and original first-piece bonuses");
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        helmet.getOrCreateTag().putInt("mask", 0);
        helmet.getOrCreateTag().putBoolean("goggles", false); // Original checks key presence, not its boolean value.
        player.doTick();
        helper.assertTrue(player.getArmorValue() == 22 && FortressArmorSupport.armorDisplay(player) == 2,
                "Mask zero was treated as absent or NBT change did not update the actual worn set");
        helper.assertTrue(((IGoggles) helmet.getItem()).showIngamePopups(helmet, player)
                && GearSupport.getTotalVisDiscount(player) == 0, "Fortress goggles lost revealing or invented a vis discount");
        CompoundTag knowledge = KnowledgeStore.get(player).save();
        for (int seed = 0; seed < 8; seed++) {
            int severity = FortressArmorSupport.reduceWarpEventSeverity(player, 30, net.minecraft.util.RandomSource.create(seed));
            helper.assertTrue(severity >= 25 && severity <= 28, "Grinning mask changed the original 2..5 severity reduction");
        }
        helper.assertTrue(knowledge.equals(KnowledgeStore.get(player).save()), "Wearing a mask directly credited or removed persisted knowledge/warp");
        clearArmor(player);
        player.doTick();
        helper.assertTrue(player.getArmorValue() == 0 && player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 0,
                "Removing the fortress set retained transient protection bonuses");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fortressDamagePathDistinguishesPhysicalFireAndUnblockableMagic(GameTestHelper helper) {
        TestPlayer physical = player(helper), fire = player(helper), magic = player(helper);
        for (TestPlayer player : new TestPlayer[]{physical, fire, magic}) { equip(player, "fortress", false); player.doTick(); }
        DamageSource hit = helper.getLevel().damageSources().mobAttack(cow(helper));
        helper.assertTrue(!hit.is(DamageTypeTags.BYPASSES_ARMOR), "Physical control source bypasses armor");
        physical.receiveDamage(hit, 10);
        fire.receiveDamage(helper.getLevel().damageSources().inFire(), 10);
        magic.receiveDamage(helper.getLevel().damageSources().magic(), 10);
        near(helper, 100 - physical.getHealth(), CombatRules.getDamageAfterAbsorb(10 * (1 - 16 / 25F), 19, 12), "Fortress physical absorption plus vanilla residual pass");
        near(helper, 100 - fire.getHealth(), CombatRules.getDamageAfterAbsorb(10 * (1 - 16 / 20F), 19, 12), "Fortress fire priority path");
        near(helper, 100 - magic.getHealth(), 10, "Original default handleUnblockableDamage=false");
        for (ItemStack armor : magic.getArmorSlots()) helper.assertTrue(armor.getDamageValue() == 0, "Unblockable magic wore fortress armor");
        helper.assertTrue(fire.getHealth() > physical.getHealth(), "Fortress fire and ordinary protection collapsed into one path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fortressMasksRunThroughActualHurtEvents(GameTestHelper helper) {
        TestPlayer victim = player(helper);
        ItemStack angry = stack("fortress_helm"); angry.getOrCreateTag().putInt("mask", 1);
        victim.setItemSlot(EquipmentSlot.HEAD, angry); victim.doTick();
        Cow attacker = cow(helper);
        victim.receiveDamage(helper.getLevel().damageSources().mobAttack(attacker), 12); // Probability >1: deterministic original boundary.
        helper.assertTrue(attacker.hasEffect(MobEffects.WITHER) && attacker.getEffect(MobEffects.WITHER).getDuration() == 80,
                "Angry Ghost's real hurt hook did not give the attacker Wither 80");
        TestPlayer leecher = player(helper);
        ItemStack sipping = stack("fortress_helm"); sipping.getOrCreateTag().putInt("mask", 2);
        leecher.setItemSlot(EquipmentSlot.HEAD, sipping); leecher.doTick(); leecher.setHealth(80);
        Cow target = cow(helper);
        helper.assertTrue(target.hurt(helper.getLevel().damageSources().playerAttack(leecher), 12), "Control target rejected actual incoming damage");
        near(helper, leecher.getHealth(), 81, "Sipping Fiend's real hurt hook healed one HP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredToolsActuallyMineAndInstallMainHandWeaponAttributes(GameTestHelper helper) {
        TestPlayer player = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack pick = stack("void_pick"); player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
        helper.assertTrue(pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) && pick.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 8,
                "Void pick does not harvest diamond-tier blocks at its original speed");
        helper.assertTrue(player.gameMode.destroyBlock(pos) && helper.getLevel().isEmptyBlock(pos), "Actual survival mining failed");
        int obsidian = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).stream()
                .map(ItemEntity::getItem).filter(item -> item.is(Items.OBSIDIAN)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(obsidian == 1 && pick.getDamageValue() == 1, "Mining lost/duplicated the actual block drop or skipped durability");
        String[] weapons = {"thaumium_sword", "void_sword", "crimson_blade", "thaumium_axe", "void_axe", "thaumium_hoe", "void_hoe"};
        double[] damage = {6.5, 7, 7.5, 9, 9, 1, 1};
        double[] speed = {1.6, 1.6, 1.6, 1, 1, 3.5, 4};
        for (int i = 0; i < weapons.length; i++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack(weapons[i])); player.doTick();
            near(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage[i], "Actual main-hand damage " + weapons[i]);
            near(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), speed[i], "Actual main-hand speed " + weapons[i]);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.doTick();
        near(helper, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 1, "Weapon removal retained an attack modifier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryRepairUsesBothOriginalWornHooksAndSingleCarriedHook(GameTestHelper helper) {
        TestPlayer player = player(helper);
        ItemStack sword = damaged("void_sword"), pick = damaged("void_pick"), helm = damaged("void_helm"), robe = damaged("void_robe_chest"), plain = damaged("thaumium_pick");
        player.getInventory().setItem(0, sword); player.getInventory().setItem(11, pick); player.getInventory().setItem(32, plain);
        player.setItemSlot(EquipmentSlot.HEAD, helm); player.setItemSlot(EquipmentSlot.CHEST, robe);
        player.tickCount = 19; player.getInventory().tick();
        for (ItemStack stack : new ItemStack[]{sword, pick, helm, robe, plain}) helper.assertTrue(stack.getDamageValue() == 10, "Equipment repaired before the 20-tick boundary");
        player.tickCount = 20; player.getInventory().tick();
        for (ItemStack stack : new ItemStack[]{sword, pick}) helper.assertTrue(stack.getDamageValue() == 9, "Carried void gear did not repair exactly one point");
        for (ItemStack stack : new ItemStack[]{helm, robe}) helper.assertTrue(stack.getDamageValue() == 8,
                "Worn void armor did not run both original hooks: damage=" + stack.getDamageValue());
        helper.assertTrue(plain.getDamageValue() == 10, "Plain thaumium acquired void self-repair");
        Cow target = cow(helper);
        sword.getItem().hurtEnemy(sword, target, player);
        helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS) && target.getEffect(MobEffects.WEAKNESS).getDuration() == 60, "Void sword lost Weakness 60");
        Cow crimsonTarget = cow(helper);
        ItemStack crimson = stack("crimson_blade"); crimson.getItem().hurtEnemy(crimson, crimsonTarget, player);
        helper.assertTrue(crimsonTarget.getEffect(MobEffects.WEAKNESS).getDuration() == 60
                && crimsonTarget.getEffect(MobEffects.HUNGER).getDuration() == 120 && crimson.getDamageValue() == 1,
                "Crimson Blade did not apply both actual hit effects/durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargeCapacityConsumptionAndItemNbtRemainServerOwned(GameTestHelper helper) {
        TestPlayer player = player(helper);
        ItemStack boots = stack("traveller_boots");
        CompoundTag knowledge = KnowledgeStore.get(player).save();
        helper.assertTrue(RechargeSupport.getCharge(boots) == 0 && RechargeSupport.addCharge(boots, player, 1000) == 240,
                "Boot charge did not stop at its original capacity");
        helper.assertTrue(!RechargeSupport.consumeCharge(boots, player, 241) && !RechargeSupport.consumeCharge(boots, player, -1)
                && RechargeSupport.getCharge(boots) == 240, "Failed/negative consumption manufactured or removed charge");
        helper.assertTrue(RechargeSupport.consumeCharge(boots, player, 7) && RechargeSupport.getCharge(boots) == 233,
                "Successful charge consumption did not spend exactly seven");
        boots.setDamageValue(17); boots.getOrCreateTag().putInt("energy", 42);
        ItemStack reloaded = ItemStack.of(boots.save(new CompoundTag()));
        helper.assertTrue(reloaded.is(item("traveller_boots")) && RechargeSupport.getCharge(reloaded) == 233
                && reloaded.getDamageValue() == 17 && reloaded.getTag().getInt("energy") == 42,
                "Item serialization conflated charge, billing energy and durability");
        helper.assertTrue(knowledge.equals(KnowledgeStore.get(player).save()), "Equipment charge directly awarded research/observations/warp");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void auraRechargeRejectsPlainItemsAndPreservesOriginalFractionTruncation(GameTestHelper helper) {
        TestPlayer player = player(helper);
        BlockPos pos = player.blockPosition(); setVis(helper, pos, 10);
        ItemStack plain = new ItemStack(Items.STICK);
        helper.assertTrue(RechargeSupport.rechargeFromAura(helper.getLevel(), plain, pos, player, 5) == 0
                && RechargeSupport.rechargeFromAura(helper.getLevel(), ItemStack.EMPTY, pos, player, 5) == 0
                && AuraManager.getVis(helper.getLevel(), pos) == 10 && !plain.hasTag(), "Non-chargeable input drained aura or acquired charge NBT");
        ItemStack boots = stack("traveller_boots");
        helper.assertTrue(RechargeSupport.rechargeFromAura(helper.getLevel(), boots, pos, player, -1) == 0
                && AuraManager.getVis(helper.getLevel(), pos) == 10, "Negative recharge changed the aura");
        setVis(helper, pos, .75F);
        helper.assertTrue(RechargeSupport.rechargeFromAura(helper.getLevel(), boots, pos, null, 5) == 0
                && RechargeSupport.getCharge(boots) == 0 && AuraManager.getVis(helper.getLevel(), pos) == 0,
                "BETA26 drain-then-truncate fractional recharge semantics changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPedestalTickerConservesWholeVisAndPersistsFilledSlot(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = CatalogBlocks.ENTRIES.get("recharge_pedestal").get();
        var state = block.defaultBlockState(); helper.getLevel().setBlockAndUpdate(pos, state);
        var pedestal = (RechargePedestalBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(pedestal != null && pedestal.getType() == RechargeModule.PEDESTAL.get(), "Placed recharge pedestal used a visual-only tile");
        var ticker = ((EntityBlock) block).getTicker(helper.getLevel(), state, RechargeModule.PEDESTAL.get());
        helper.assertTrue(ticker != null, "Registered pedestal has no actual server ticker");
        ItemStack boots = stack("traveller_boots"); pedestal.setItem(0, boots); setVis(helper, pos, 50);
        ticker.tick(helper.getLevel(), pos, state, pedestal);
        helper.assertTrue(RechargeSupport.getCharge(boots) == 5 && AuraManager.getVis(helper.getLevel(), pos) == 45, "First pedestal tick did not exchange five vis for five charge");
        for (int i = 0; i < 9; i++) ticker.tick(helper.getLevel(), pos, state, pedestal);
        helper.assertTrue(RechargeSupport.getCharge(boots) == 5 && AuraManager.getVis(helper.getLevel(), pos) == 45, "Pedestal charged more frequently than ten ticks");
        ticker.tick(helper.getLevel(), pos, state, pedestal);
        helper.assertTrue(RechargeSupport.getCharge(boots) == 10 && AuraManager.getVis(helper.getLevel(), pos) == 40, "Second recharge missed the tenth-tick boundary");
        RechargeSupport.addCharge(boots, null, 229);
        for (int i = 0; i < 10; i++) ticker.tick(helper.getLevel(), pos, state, pedestal);
        helper.assertTrue(RechargeSupport.getCharge(boots) == 240 && AuraManager.getVis(helper.getLevel(), pos) == 39, "Near-full pedestal lost aura through overflow");
        for (int i = 0; i < 10; i++) ticker.tick(helper.getLevel(), pos, state, pedestal);
        helper.assertTrue(AuraManager.getVis(helper.getLevel(), pos) == 39, "Full charge continued draining the aura");
        var saved = pedestal.saveWithFullMetadata();
        var loaded = (RechargePedestalBlockEntity) BlockEntity.loadStatic(pos, state, saved);
        helper.assertTrue(loaded != null && RechargeSupport.getCharge(loaded.getItem(0)) == 240, "Registered factory reload lost pedestal charge");
        loaded.load(pedestal.getUpdatePacket().getTag());
        helper.assertTrue(ItemStack.isSameItemSameTags(loaded.getItem(0), boots), "Actual BE update packet lost charge/inventory NBT");
        var inventory = pedestal.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.STICK), false).is(Items.STICK), "Pedestal automation accepted an ordinary item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pedestalActualOffhandInteractionAndBreakKeepChargedNbt(GameTestHelper helper) {
        TestPlayer player = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = CatalogBlocks.ENTRIES.get("recharge_pedestal").get(); var state = block.defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, state);
        var pedestal = (RechargePedestalBlockEntity) helper.getLevel().getBlockEntity(pos);
        ItemStack boots = stack("traveller_boots"); RechargeSupport.addCharge(boots, player, 38); boots.getOrCreateTag().putInt("energy", 19);
        String marker = UUID.randomUUID().toString(); boots.getOrCreateTag().putString("tc6.pedestalTest", marker);
        ItemStack expected = boots.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 3)); player.setItemInHand(InteractionHand.OFF_HAND, boots);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        state.use(helper.getLevel(), player, InteractionHand.OFF_HAND, hit);
        helper.assertTrue(player.getOffhandItem().isEmpty() && player.getMainHandItem().getCount() == 3
                && ItemStack.isSameItemSameTags(pedestal.getItem(0), expected), "Offhand pedestal insertion consumed the main stack or lost NBT");
        helper.getLevel().destroyBlock(pos, true);
        var nearbyDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(item("traveller_boots"))).toList();
        var drops = nearbyDrops.stream().filter(stack -> stack.hasTag() && marker.equals(stack.getTag().getString("tc6.pedestalTest"))).toList();
        helper.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1 && ItemStack.isSameItemSameTags(drops.get(0), expected),
                "Breaking actual pedestal duplicated or erased the charged inventory stack: own=" + drops + ", nearby=" + nearbyDrops + ", expected=" + expected.getTag());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wornVisDiscountPaysActualArcaneCraftAndStillRejectsInsufficientAura(GameTestHelper helper) {
        TestPlayer player = player(helper);
        player.setItemSlot(EquipmentSlot.HEAD, stack("goggles"));
        player.setItemSlot(EquipmentSlot.CHEST, stack("cloth_chest")); player.setItemSlot(EquipmentSlot.LEGS, stack("cloth_legs")); player.setItemSlot(EquipmentSlot.FEET, stack("cloth_boots"));
        near(helper, GearSupport.getTotalVisDiscount(player), .13, "Actual equipped vis discount");
        BlockPos pos = player.blockPosition(); helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT));
        bench.setItem(4, new ItemStack(Items.GLASS_PANE));
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, new ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get()));
        KnowledgeStore.recordFact(player, "!gotthaumonomicon"); ResearchProgression.advance(player, "FIRSTSTEPS", 0);
        KnowledgeStore.recordCraft(player, new ItemStack(ArcaneModule.WORKBENCH_ITEM.get())); ResearchProgression.advance(player, "FIRSTSTEPS", 1);
        var recipe = bench.findRecipe(player);
        helper.assertTrue(recipe != null && ArcaneWorkbenchBlockEntity.visCost(player, recipe) == 17,
                "Shared preview/validation/commit price lost multiply-then-truncate discount semantics");
        setVis(helper, pos, 16.9F);
        helper.assertTrue(bench.craft(player).isEmpty() && bench.getItem(1).getCount() == 1 && AuraManager.getVis(helper.getLevel(), pos) == 16.9F,
                "Discounted craft accepted insufficient aura or consumed inputs before validation");
        setVis(helper, pos, 17);
        helper.assertTrue(bench.craft(player).is(item("thaumometer")) && bench.isEmpty() && AuraManager.getVis(helper.getLevel(), pos) == 0,
                "Actual arcane craft did not pay int(20*(1-.13))=17 and consume its original ingredients/crystals");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void travellerBillingMovementJumpAndStepRestorationUseActualWornHooks(GameTestHelper helper) {
        TestPlayer player = player(helper);
        ItemStack boots = stack("traveller_boots"); player.setItemSlot(EquipmentSlot.FEET, boots); RechargeSupport.addCharge(boots, player, 2);
        player.tickCount = 19; player.getInventory().tick();
        helper.assertTrue(RechargeSupport.getCharge(boots) == 2, "Traveller boots billed before twenty ticks");
        player.tickCount = 20; player.getInventory().tick();
        helper.assertTrue(RechargeSupport.getCharge(boots) == 1 && boots.getTag().getInt("energy") == 60, "Actual worn tick did not spend one charge and start the 60-second billing counter");
        player.tickCount = 40; player.getInventory().tick();
        helper.assertTrue(RechargeSupport.getCharge(boots) == 1 && boots.getTag().getInt("energy") == 59, "Boots spent charge while their energy billing counter was still positive");
        float normalStep = player.maxUpStep(); player.zza = 1; player.setOnGround(true); player.setDeltaMovement(Vec3.ZERO); player.tickCount = 41;
        player.getInventory().tick();
        near(helper, player.getDeltaMovement().horizontalDistance(), .05, "Charged forward boots movement");
        near(helper, player.maxUpStep(), 1, "Traveller step height");
        player.setDeltaMovement(Vec3.ZERO); player.jump();
        near(helper, player.getDeltaMovement().y, .42 + .2750000059604645, "Actual LivingJumpEvent boost");
        player.zza = 0; player.setShiftKeyDown(true); player.doTick();
        near(helper, player.maxUpStep(), normalStep, "Sneaking did not restore the previous step height");
        player.setShiftKeyDown(false); player.zza = 1; player.getAbilities().flying = true; player.setDeltaMovement(Vec3.ZERO);
        player.getInventory().tick(); near(helper, player.getDeltaMovement().horizontalDistance(), 0, "Flying boots invented forward movement");
        player.getAbilities().flying = false; player.zza = 0;
        float before = player.getHealth(); player.receiveDamage(helper.getLevel().damageSources().fall(), 6);
        near(helper, before - player.getHealth(), 2, "Traveller fall reduction damage/2-1");
        helper.succeed();
    }

    private static void near(GameTestHelper helper, double actual, double expected, String subject) {
        helper.assertTrue(Math.abs(actual - expected) < .001, subject + ": expected=" + expected + ", actual=" + actual);
    }
    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
        if (item == null || item == Items.AIR) throw new AssertionError("Missing registered equipment item " + id);
        return item;
    }
    private static ItemStack stack(String id) { return new ItemStack(item(id)); }
    private static ItemStack damaged(String id) { ItemStack stack = stack(id); stack.setDamageValue(10); return stack; }
    private static void setVis(GameTestHelper helper, BlockPos pos, float amount) {
        AuraManager.drainVis(helper.getLevel(), pos, Float.MAX_VALUE, false); AuraManager.addVis(helper.getLevel(), pos, amount);
    }
    private static void equip(TestPlayer player, String prefix, boolean boots) {
        player.setItemSlot(EquipmentSlot.HEAD, stack(prefix + "_helm")); player.setItemSlot(EquipmentSlot.CHEST, stack(prefix + "_chest"));
        player.setItemSlot(EquipmentSlot.LEGS, stack(prefix + "_legs")); if (boots) player.setItemSlot(EquipmentSlot.FEET, stack(prefix + "_boots"));
    }
    private static void clearArmor(TestPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) player.setItemSlot(slot, ItemStack.EMPTY);
    }
    private static Cow cow(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel()); if (cow == null) throw new AssertionError("Cow fixture missing");
        cow.setNoAi(true); return cow;
    }
    private static TestPlayer player(GameTestHelper helper) {
        TestPlayer player = new TestPlayer(helper.getLevel()); BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); player.setYRot(0); player.setXRot(0);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); player.setHealth(100);
        return player;
    }
    private static final class TestPlayer extends ServerPlayer {
        TestPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "TC6Equipment"));
            // No socket is opened. Packets queue on this unconnected transport while actual player hooks run.
            connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), this);
        }
        // Enter actual Player damage/armor/durability and Forge hurt/damage hooks, bypassing only
        // ServerPlayer's initial login immunity. No damage-calculation method is overridden.
        void receiveDamage(DamageSource source, float amount) { super.actuallyHurt(source, amount); }
        void jump() { super.jumpFromGround(); }
    }
}
