package thaumcraft.scanning;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.KnowledgeType;
import thaumcraft.research.ResearchCategories;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Original generic identity, ungated Observation and read-only inventory traversal. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ScanningMechanicsGameTests {
    private ScanningMechanicsGameTests() {}

    @GameTest(template = "empty")
    public static void ordinarySpecimensNeedNoAspectOrResearchPrerequisites(GameTestHelper h) {
        var p = player(h);
        var state = KnowledgeStore.get(p);
        h.assertTrue(state.discoveredAspects().isEmpty() && !state.isResearchKnown("FIRSTSTEPS")
                && !state.isResearchKnown("BASEALCHEMY"), "Fresh scanner fixture already knows prerequisites");
        Map<String, Integer> expected = new LinkedHashMap<>();
        java.util.Set<Aspect> discovered = new java.util.HashSet<>();
        for (String category : ResearchCategories.keys()) expected.put(category, 0);
        for (ItemStack specimen : java.util.List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIRT),
                new ItemStack(Items.OAK_LOG), new ItemStack(Items.COAL), new ItemStack(Items.WHEAT))) {
            AspectList aspects = AspectRegistry.getAspects(specimen);
            h.assertTrue(ThaumometerScanning.scanObject(p, specimen), "Ordinary world specimen required earlier aspect discovery: " + specimen);
            expected.replaceAll((category, amount) -> amount + ResearchCategories.observationGain(category, aspects));
            addAspectBonuses(expected, discovered, aspects);
        }
        var cow = EntityType.COW.create(h.getLevel());
        h.assertTrue(cow != null && ThaumometerScanning.scanObject(p, cow), "Fresh player cannot scan a vanilla animal");
        AspectList cowAspects = AspectRegistry.getAspects(cow);
        expected.replaceAll((category, amount) -> amount + ResearchCategories.observationGain(category, cowAspects));
        addAspectBonuses(expected, discovered, cowAspects);
        for (String category : ResearchCategories.keys()) h.assertTrue(
                state.rawKnowledge(KnowledgeType.OBSERVATION, category) == expected.get(category)
                        && state.rawKnowledge(KnowledgeType.THEORY, category) == 0,
                "Ordinary scans changed category weighting or granted Theory: " + category);
        h.assertTrue(!state.isResearchKnown("FIRSTSTEPS") && !state.isResearchKnown("BASEALCHEMY"),
                "World scanning freely completed paid book research");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantmentCompositionCannotGrantAnotherGenericObservation(GameTestHelper h) {
        var p = player(h);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        h.assertTrue(ThaumometerScanning.scanObject(p, sword), "First sword scan failed");
        var knowledge = KnowledgeStore.get(p);
        Map<String, Integer> before = observations(p);
        var knownAspects = new java.util.HashSet<>(knowledge.discoveredAspects());
        sword.enchant(Enchantments.SHARPNESS, 3);
        h.assertTrue(AspectRegistry.getAspects(sword).getAmount(Aspect.MAGIC) > 0,
                "Meaningful enchanted composition fixture did not change");
        ThaumometerScanning.scanObject(p, sword);
        int newAspects = (int) knowledge.discoveredAspects().stream().filter(tag -> !knownAspects.contains(tag)).count();
        h.assertTrue(knowledge.scanCount() == 1 && newAspects > 0 && withAspectBonuses(before, newAspects).equals(observations(p)),
                "Enchanting an already scanned item repeated generic Observation rather than only new-aspect bonuses");
        before = observations(p);
        sword.setDamageValue(75);
        sword.setHoverName(net.minecraft.network.chat.Component.literal("Renamed enchanted sword"));
        ThaumometerScanning.scanObject(p, sword);
        h.assertTrue(knowledge.scanCount() == 1 && before.equals(observations(p)),
                "Damage or display NBT changed the original generic identity");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void taggedAspectCrystalsShareOneOriginalGenericIdentity(GameTestHelper h) {
        var p = player(h);
        ItemStack aer = AspectCrystalItem.create(Aspect.AIR, 1), aqua = AspectCrystalItem.create(Aspect.WATER, 1);
        h.assertTrue(aer.getItem() == aqua.getItem() && ThaumometerScanning.scanObject(p, aer),
                "Original crystal_essence first scan fixture failed");
        Map<String, Integer> before = observations(p);
        h.assertTrue(ThaumometerScanning.scanObject(p, aqua), "New Aqua aspect was suppressed by its known crystal_essence identity");
        h.assertTrue(KnowledgeStore.get(p).scanCount() == 1 && withAspectBonuses(before, 1).equals(observations(p)),
                "A different tagged aspect repeated the crystal's generic reward or lost its three one-unit aspect bonuses");
        var after = KnowledgeStore.get(p).save();
        h.assertTrue(!ThaumometerScanning.scanObject(p, aqua) && after.equals(KnowledgeStore.get(p).save()),
                "Repeated tagged aspect crystal duplicated its per-aspect bonus");
        h.assertTrue(!KnowledgeStore.get(p).isResearchKnown("ORE") && !KnowledgeStore.get(p).isResearchKnown("!ORECRYSTAL"),
                "Loose aspect crystals forged the physical cluster ore proof");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedStackAndDroppedItemShareOneRewardWithoutConsumption(GameTestHelper h) {
        var p = player(h);
        ItemStack stack = new ItemStack(Items.COAL, 64);
        CompoundTag contents = stack.save(new CompoundTag());
        h.assertTrue(ThaumometerScanning.scanObject(p, stack), "First stack scan failed");
        CompoundTag before = KnowledgeStore.get(p).save();
        var dropped = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), stack.copy());
        h.assertTrue(!ThaumometerScanning.scanObject(p, dropped) && !ThaumometerScanning.scanObject(p, new ItemStack(Items.COAL)),
                "Dropped/held forms or different stack count repeated the generic reward");
        h.assertTrue(before.equals(KnowledgeStore.get(p).save()) && contents.equals(stack.save(new CompoundTag()))
                        && contents.equals(dropped.getItem().save(new CompoundTag())),
                "Repeat scan altered knowledge or consumed its specimen");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void removedEntityCannotSupplyGenericOrSpecialScanProof(GameTestHelper h) {
        var p = player(h);
        var entity = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.ARROW));
        entity.discard();
        CompoundTag before = KnowledgeStore.get(p).save();
        h.assertTrue(!ThaumometerScanning.scanObject(p, entity) && before.equals(KnowledgeStore.get(p).save()),
                "Removed dropped arrow supplied generic knowledge or f_arrow");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void actualChestClickScansContentsWhileHoverIsReadOnly(GameTestHelper h) {
        var p = player(h);
        BlockPos relative = new BlockPos(4, 3, 5), absolute = h.absolutePos(relative);
        h.setBlock(relative, Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(absolute);
        chest.setItem(3, new ItemStack(Items.COAL, 37));
        chest.setItem(20, new ItemStack(Items.ARROW, 12));
        CompoundTag inventoryBefore = chest.saveWithoutMetadata();
        aim(p, Vec3.atCenterOf(absolute));
        CompoundTag knowledgeBefore = KnowledgeStore.get(p).save();
        var hover = ScanningNetwork.capture(p);
        h.assertTrue(hover.target() != null && absolute.equals(hover.target().location().blockPos())
                        && knowledgeBefore.equals(KnowledgeStore.get(p).save()),
                "Chest hover missed its server target or scanned inventory without a click");
        ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        var state = KnowledgeStore.get(p);
        h.assertTrue(state.hasScanned(ThaumometerItem.itemTarget(new ItemStack(Items.CHEST), Vec3.ZERO).key())
                        && state.hasScanned(ThaumometerItem.itemTarget(new ItemStack(Items.COAL), Vec3.ZERO).key())
                        && state.hasScanned(ThaumometerItem.itemTarget(new ItemStack(Items.ARROW), Vec3.ZERO).key())
                        && state.isResearchCompleteStrict("f_arrow"),
                "Actual chest click omitted the block, contents or independent arrow scan proof");
        h.assertTrue(inventoryBefore.equals(chest.saveWithoutMetadata()), "Chest scanning changed its real inventory");
        CompoundTag after = state.save();
        p.getCooldowns().removeCooldown(ScanningModule.THAUMOMETER.get());
        ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(after.equals(state.save()) && inventoryBefore.equals(chest.saveWithoutMetadata()),
                "Repeated physical chest scan changed rewards or contents");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void physicalChestUpCapabilityIsReadOnlyAndSkipsEmptySlots(GameTestHelper h) {
        var p = player(h);
        BlockPos relative = new BlockPos(4, 3, 5), absolute = h.absolutePos(relative);
        h.setBlock(relative, Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(absolute);
        chest.setItem(2, new ItemStack(Items.COAL, 64));
        chest.setItem(25, new ItemStack(Items.IRON_INGOT, 4));
        CompoundTag inventoryBefore = chest.saveWithoutMetadata();
        var result = ThaumometerScanning.scanContents(p, absolute);
        h.assertTrue(result.scanned() == 2 && result.changed() && !result.capped()
                        && KnowledgeStore.get(p).isResearchCompleteStrict("f_MATIRON"),
                "Native UP item capability did not scan the two nonempty slots and iron proof");
        h.assertTrue(inventoryBefore.equals(chest.saveWithoutMetadata()), "Read-only inventory traversal consumed or rewrote chest contents");
        CompoundTag after = KnowledgeStore.get(p).save();
        result = ThaumometerScanning.scanContents(p, absolute);
        h.assertTrue(result.scanned() == 2 && !result.changed() && !result.capped()
                        && after.equals(KnowledgeStore.get(p).save()),
                "Known contents changed the nonempty count or repeated rewards");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void actualDoubleChestClickReadsFarHalfWithoutChangingEitherInventory(GameTestHelper h) {
        var p = player(h);
        BlockPos near = h.absolutePos(new BlockPos(4, 3, 5)), far = near.east();
        var nearState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        var farState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        h.getLevel().setBlock(near, nearState, 2);
        h.getLevel().setBlock(far, farState, 2);
        h.assertTrue(h.getLevel().getBlockState(near).equals(nearState)
                        && h.getLevel().getBlockState(far).equals(farState)
                        && near.relative(ChestBlock.getConnectedDirection(nearState)).equals(far),
                "Actual connected double-chest fixture did not retain its matching halves");
        var first = (ChestBlockEntity) h.getLevel().getBlockEntity(near);
        var second = (ChestBlockEntity) h.getLevel().getBlockEntity(far);
        first.setItem(8, new ItemStack(Items.COAL, 19));
        second.setItem(24, new ItemStack(Items.ARROW, 23));
        var nativeHandler = first.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve();
        h.assertTrue(nativeHandler.isPresent() && nativeHandler.get().getSlots() == 54,
                "Native Forge chest capability did not expose the actual combined inventory");
        CompoundTag firstBefore = first.saveWithoutMetadata(), secondBefore = second.saveWithoutMetadata();
        aim(p, Vec3.atCenterOf(near));
        CompoundTag knowledgeBefore = KnowledgeStore.get(p).save();
        var hover = ScanningNetwork.capture(p);
        h.assertTrue(hover.target() != null && near.equals(hover.target().location().blockPos())
                        && knowledgeBefore.equals(KnowledgeStore.get(p).save()),
                "Double-chest hover selected the wrong half or granted inventory knowledge");
        ScanningModule.THAUMOMETER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(KnowledgeStore.get(p).isResearchCompleteStrict("f_arrow")
                        && KnowledgeStore.get(p).hasScanned(ThaumometerItem.itemTarget(new ItemStack(Items.COAL), Vec3.ZERO).key())
                        && KnowledgeStore.get(p).hasScanned(ThaumometerItem.itemTarget(new ItemStack(Items.ARROW), Vec3.ZERO).key()),
                "Scanning the near half omitted the actual far-half arrow or its independent proof");
        h.assertTrue(firstBefore.equals(first.saveWithoutMetadata()) && secondBefore.equals(second.saveWithoutMetadata()),
                "Double-chest scanning extracted or rewrote either half's contents");
        CompoundTag after = KnowledgeStore.get(p).save();
        var result = ThaumometerScanning.scanContents(p, near);
        h.assertTrue(result.scanned() == 2 && !result.changed() && !result.capped()
                        && after.equals(KnowledgeStore.get(p).save())
                        && firstBefore.equals(first.saveWithoutMetadata()) && secondBefore.equals(second.saveWithoutMetadata()),
                "Repeated combined-container scan changed rewards or either inventory");
        h.succeed();
    }

    @GameTest(template = "essentia_network")
    public static void missingCapabilityFallsBackToOnlyVanillaUpSidedSlots(GameTestHelper h) {
        var p = player(h);
        BlockPos at = h.absolutePos(new BlockPos(4, 3, 5));
        h.getLevel().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
        var chest = new NoCapabilitySidedChest(at, h.getLevel().getBlockState(at));
        h.getLevel().setBlockEntity(chest);
        chest.setItem(0, new ItemStack(Items.COAL, 9));
        chest.setItem(2, new ItemStack(Items.ARROW, 7));
        chest.setItem(6, new ItemStack(Items.IRON_INGOT, 5));
        h.assertTrue(h.getLevel().getBlockEntity(at) == chest
                        && !chest.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
                "Missing-capability vanilla sided-inventory fixture was not established");
        CompoundTag before = chest.saveWithoutMetadata();
        var result = ThaumometerScanning.scanContents(p, at);
        h.assertTrue(result.scanned() == 2 && result.changed() && !result.capped()
                        && KnowledgeStore.get(p).isResearchCompleteStrict("f_arrow")
                        && KnowledgeStore.get(p).isResearchCompleteStrict("f_MATIRON")
                        && !KnowledgeStore.get(p).hasScanned(ThaumometerItem.itemTarget(new ItemStack(Items.COAL), Vec3.ZERO).key())
                        && before.equals(chest.saveWithoutMetadata()),
                "Original vanilla fallback scanned another face, lost accessible slots or changed inventory");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryLimitCountsNonemptySlotsIncludingRepeatedKnownItems(GameTestHelper h) {
        var p = player(h);
        var handler = new ReadOnlyHandler(203);
        for (int i = 0; i < 100; i++) handler.setStackInSlot(i * 2 + 1, new ItemStack(Items.COAL, 64));
        handler.setStackInSlot(201, new ItemStack(Items.ARROW));
        CompoundTag inventoryBefore = handler.serializeNBT();
        var result = ThaumometerScanning.scanContents(p, handler);
        h.assertTrue(result.scanned() == 100 && result.changed() && result.capped() && handler.reads == 200,
                "Limit counted physical/unique slots or read beyond the first 100 nonempty slots");
        h.assertTrue(KnowledgeStore.get(p).scanCount() == 1 && !KnowledgeStore.get(p).isResearchKnown("f_arrow")
                        && inventoryBefore.equals(handler.serializeNBT()),
                "Repeated contents farmed rewards, slot 101 was scanned or traversal altered inventory");
        CompoundTag knowledgeBefore = KnowledgeStore.get(p).save();
        handler.reads = 0;
        result = ThaumometerScanning.scanContents(p, handler);
        h.assertTrue(result.scanned() == 100 && !result.changed() && result.capped() && handler.reads == 200
                        && knowledgeBefore.equals(KnowledgeStore.get(p).save())
                        && inventoryBefore.equals(handler.serializeNBT()),
                "Already-known contents bypassed the original 100 nonempty limit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hundredthSlotRetainsItsIndependentProofBeforeLimitStopsTraversal(GameTestHelper h) {
        var p = player(h);
        var handler = new ReadOnlyHandler(101);
        for (int i = 0; i < 99; i++) handler.setStackInSlot(i, new ItemStack(Items.COAL));
        handler.setStackInSlot(99, new ItemStack(Items.ARROW));
        handler.setStackInSlot(100, new ItemStack(Items.DRAGON_BREATH));
        var result = ThaumometerScanning.scanContents(p, handler);
        h.assertTrue(result.scanned() == 100 && result.changed() && result.capped()
                        && KnowledgeStore.get(p).isResearchCompleteStrict("f_arrow")
                        && !KnowledgeStore.get(p).isResearchKnown("!DRAGONBREATH"),
                "100th nonempty slot was skipped or 101st slot escaped the cap");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void knownGenericContentsStillAcquireUnseenIndependentProof(GameTestHelper h) {
        var p = player(h);
        ItemStack arrow = new ItemStack(Items.ARROW);
        var generic = ThaumometerItem.itemTarget(arrow, Vec3.ZERO);
        h.assertTrue(KnowledgeStore.recordScan(p, generic.key(), generic.aspects())
                        && !KnowledgeStore.get(p).isResearchKnown("f_arrow"),
                "Known generic/unseen special-proof fixture was not established");
        Map<String, Integer> before = observations(p);
        var handler = new ReadOnlyHandler(3);
        handler.setStackInSlot(2, arrow);
        var result = ThaumometerScanning.scanContents(p, handler);
        h.assertTrue(result.scanned() == 1 && result.changed() && !result.capped()
                        && KnowledgeStore.get(p).isResearchCompleteStrict("f_arrow")
                        && KnowledgeStore.get(p).scanCount() == 1 && before.equals(observations(p)),
                "Generic uniqueness suppressed an unseen special proof or credited Observation twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyAndNoninventoryTargetsCannotCreateRewards(GameTestHelper h) {
        var p = player(h);
        CompoundTag before = KnowledgeStore.get(p).save();
        var empty = ThaumometerScanning.scanContents(p, new ReadOnlyHandler(120));
        BlockPos stone = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var block = ThaumometerScanning.scanContents(p, stone);
        h.assertTrue(empty.scanned() == 0 && !empty.changed() && !empty.capped()
                        && block.scanned() == 0 && !block.changed() && !block.capped()
                        && !ThaumometerScanning.scanObject(p, ItemStack.EMPTY)
                        && !ThaumometerScanning.scanObject(p, null)
                        && before.equals(KnowledgeStore.get(p).save()),
                "Empty/no-capability scan fabricated Observation or special proofs");
        h.succeed();
    }

    private static Map<String, Integer> observations(ServerPlayer p) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String category : ResearchCategories.keys()) result.put(category,
                KnowledgeStore.get(p).rawKnowledge(KnowledgeType.OBSERVATION, category));
        return result;
    }

    private static Map<String, Integer> withAspectBonuses(Map<String, Integer> before, int amount) {
        Map<String, Integer> result = new LinkedHashMap<>(before);
        for (String category : java.util.List.of("BASICS", "AUROMANCY", "ALCHEMY"))
            result.put(category, result.get(category) + amount);
        return result;
    }

    private static void addAspectBonuses(Map<String, Integer> rewards, java.util.Set<Aspect> discovered, AspectList aspects) {
        for (Aspect aspect : aspects.getAspects()) if (aspects.getAmount(aspect) > 0 && discovered.add(aspect))
            for (String category : java.util.List.of("BASICS", "AUROMANCY", "ALCHEMY"))
                rewards.put(category, rewards.get(category) + 1);
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "tc6_scan_mechanics"));
        BlockPos start = h.absolutePos(new BlockPos(4, 2, 2));
        p.setPos(start.getX() + .5, start.getY(), start.getZ() + .5);
        p.setYRot(0); p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ScanningModule.THAUMOMETER.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        return p;
    }

    private static void aim(ServerPlayer p, Vec3 target) {
        Vec3 offset = target.subtract(p.getEyePosition());
        p.setYRot((float) -Math.toDegrees(Math.atan2(offset.x, offset.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
    }

    private static final class ReadOnlyHandler extends ItemStackHandler {
        private int reads;
        private ReadOnlyHandler(int slots) { super(slots); }
        @Override public ItemStack getStackInSlot(int slot) { reads++; return super.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            throw new AssertionError("Scanning attempted to insert into an inventory");
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            throw new AssertionError("Scanning attempted to extract from an inventory");
        }
    }

    /** Explicit integration fixture: a vanilla inventory that exposes no Forge capability. */
    private static final class NoCapabilitySidedChest extends ChestBlockEntity implements net.minecraft.world.WorldlyContainer {
        private NoCapabilitySidedChest(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { super(pos, state); }
        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> capability, Direction side) {
            return net.minecraftforge.common.util.LazyOptional.empty();
        }
        @Override public int[] getSlotsForFace(Direction side) { return side == Direction.UP ? new int[]{2, 6} : new int[]{0}; }
        @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
        @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    }
}
