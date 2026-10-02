package thaumcraft.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.essentia.EssentiaJarBlockEntity;
import thaumcraft.essentia.EssentiaModule;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.world.aura.AuraManager;

import java.util.List;
import java.util.UUID;

/** Runtime storage and player interactions; catalogue registration alone is insufficient. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class EssentiaGameTests {
    private static final String[] JARS = {"jar_normal", "jar_void"};
    private static final BlockPos JAR_POS = new BlockPos(1, 2, 1);

    private EssentiaGameTests() {}

    @GameTest(template = "empty")
    public static void normalJarCapacityConservesAcceptedAndReturnedEssentia(GameTestHelper helper) {
        var jar = placeJar(helper, "jar_normal");
        helper.assertTrue(EssentiaJarBlockEntity.CAPACITY == 250 && !jar.isVoid(), "Normal jar capacity/type differs from TC6");
        helper.assertTrue(jar.addToContainer(Aspect.AIR, 247) == 0, "Initial essentia was not accepted");
        int remainder = jar.addToContainer(Aspect.AIR, 10);
        helper.assertTrue(jar.amount() == 250 && remainder == 7 && jar.amount() + remainder == 257,
                "Normal jar created or lost essentia at its capacity boundary");
        helper.assertTrue(jar.addToContainer(Aspect.FIRE, 10) == 10 && jar.aspect() == Aspect.AIR && jar.amount() == 250,
                "A mismatched aspect replaced full jar contents");
        helper.assertTrue(!jar.take(Aspect.FIRE, 1) && !jar.take(Aspect.AIR, 251) && jar.amount() == 250,
                "A rejected withdrawal changed the jar");
        helper.assertTrue(jar.take(Aspect.AIR, 10) && jar.amount() == 240 && jar.addExact(Aspect.AIR, 10) && jar.amount() == 250,
                "Exact transfer did not conserve ten units");
        helper.assertTrue(jar.takeFromContainer(Aspect.AIR, 250) && jar.amount() == 0 && jar.aspect() == null,
                "Draining an unfiltered jar retained its contents/type");
        helper.assertTrue(jar.addExact(Aspect.FIRE, 10) && jar.aspect() == Aspect.FIRE,
                "An emptied unfiltered jar could not change aspect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void voidOverflowConsumesExcessButExactTransferRejectsIt(GameTestHelper helper) {
        var jar = placeJar(helper, "jar_void");
        helper.assertTrue(jar.isVoid() && jar.addToContainer(Aspect.AIR, 247) == 0, "Void jar was not operational");
        helper.assertTrue(jar.addToContainer(Aspect.AIR, 10) == 0 && jar.amount() == 250,
                "Void jar did not consume only the overflow above 250");
        helper.assertTrue(jar.addToContainer(Aspect.AIR, 10) == 0 && jar.amount() == 250,
                "A full void jar refused its matching overflow");
        CompoundTag before = jar.saveWithoutMetadata();
        helper.assertTrue(!jar.canAccept(Aspect.AIR, 10) && !jar.addExact(Aspect.AIR, 10)
                        && before.equals(jar.saveWithoutMetadata()),
                "Exact/manual transfer overflowed a void jar");
        helper.assertTrue(jar.addToContainer(Aspect.FIRE, 10) == 10 && jar.amount() == 250 && jar.aspect() == Aspect.AIR,
                "Void overflow accepted a different aspect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedInvalidTransfersCannotCreateOrRemoveEssentia(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            helper.assertTrue(jar.addExact(Aspect.AIR, 20), "Could not prepare " + id);
            CompoundTag before = jar.saveWithoutMetadata();
            helper.assertTrue(!jar.addExact(null, 10) && !jar.addExact(Aspect.AIR, -10)
                            && !jar.addExact(Aspect.AIR, 0) && !jar.take(null, 10)
                            && !jar.take(Aspect.AIR, -10) && !jar.take(Aspect.AIR, 0),
                    "Null/nonpositive exact transfer was accepted by " + id);
            jar.addToContainer(null, 10);
            jar.addToContainer(Aspect.AIR, -10);
            jar.takeFromContainer(null, 10);
            jar.takeFromContainer(Aspect.AIR, -10);
            helper.assertTrue(before.equals(jar.saveWithoutMetadata()), "Invalid raw transfer mutated " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void labelsKeepTheFilterAfterDrainButRawInsertionRemainsSourceCompatible(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            helper.assertTrue(jar.applyLabel(player, Direction.NORTH, Aspect.AIR), "Could not label " + id);
            helper.assertTrue(jar.addExact(Aspect.AIR, 10) && jar.take(Aspect.AIR, 10) && jar.amount() == 0
                            && jar.filter() == Aspect.AIR,
                    "Draining removed the persistent filter of " + id);
            helper.assertTrue(!jar.canAccept(Aspect.FIRE, 10) && !jar.addExact(Aspect.FIRE, 10),
                    "Manual transfer ignored an empty jar's filter");
            // BETA26 addToContainer itself does not check the label. Its caller does.
            helper.assertTrue(jar.addToContainer(Aspect.FIRE, 10) == 0 && jar.amount() == 10
                            && jar.aspect() == Aspect.FIRE && jar.filter() == Aspect.AIR,
                    "Raw insertion no longer follows the original filter-agnostic API");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void suctionAndComparatorFollowFillBoundariesForBothJars(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            helper.assertTrue(jar.getMinimumSuction() == 32 && jar.getSuctionAmount(Direction.UP) == 32,
                    "Unlabelled suction differs from TC6 for " + id);
            helper.assertTrue(jar.applyLabel(player, Direction.NORTH, Aspect.AIR), "Could not prepare filtered jar");
            int filteredSuction = jar.isVoid() ? 48 : 64;
            helper.assertTrue(jar.getMinimumSuction() == filteredSuction && jar.getSuctionType(Direction.UP) == Aspect.AIR,
                    "Filtered suction minimum/type differs from TC6 for " + id);
            int[] amounts = {0, 1, 17, 18, 125, 249, 250};
            int[] signals = {0, 1, 1, 2, 8, 14, 15};
            for (int i = 0; i < amounts.length; i++) {
                if (jar.amount() > 0) helper.assertTrue(jar.take(Aspect.AIR, jar.amount()), "Could not drain comparator fixture");
                if (amounts[i] > 0) helper.assertTrue(jar.addExact(Aspect.AIR, amounts[i]), "Could not fill comparator fixture");
                var state = helper.getLevel().getBlockState(jar.getBlockPos());
                helper.assertTrue(state.hasAnalogOutputSignal()
                                && state.getAnalogOutputSignal(helper.getLevel(), jar.getBlockPos()) == signals[i],
                        "Wrong comparator output for " + id + " amount=" + amounts[i]);
                int suction = amounts[i] == 250 ? (jar.isVoid() ? 32 : 0) : filteredSuction;
                helper.assertTrue(jar.getSuctionAmount(Direction.UP) == suction,
                        "Wrong suction for " + id + " amount=" + amounts[i]);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void braceBlocksAirAccessButLeavesOnlyTopTubeConnection(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            helper.assertTrue(jar.installBrace() && jar.blocked() && !jar.installBrace(), "Brace installation is not idempotent");
            for (Direction face : Direction.values()) {
                boolean top = face == Direction.UP;
                helper.assertTrue(jar.isConnectable(face) == top && jar.canInputFrom(face) == top && jar.canOutputTo(face) == top,
                        "Brace changed the source's top-only tube access on " + face);
                if (!top) helper.assertTrue(jar.addEssentia(Aspect.AIR, 10, face) == 0 && jar.takeEssentia(Aspect.AIR, 10, face) == 0,
                        "A side tube transferred essentia through " + face);
            }
            helper.assertTrue(jar.addEssentia(Aspect.AIR, 10, Direction.UP) == 10 && jar.amount() == 10
                            && jar.getEssentiaType(Direction.UP) == Aspect.AIR && jar.getEssentiaAmount(Direction.UP) == 10
                            && jar.takeEssentia(Aspect.AIR, 10, Direction.UP) == 10 && jar.amount() == 0,
                    "Brace incorrectly disabled the upper tube transfer");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void labelFacesUseSourcePlayerYawAndFilledLabelCannotReplaceContents(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        float[] yaws = {0, 90, 180, 270};
        Direction[] expected = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < yaws.length; i++) {
            var jar = placeJar(helper, "jar_normal");
            player.setYRot(yaws[i]);
            helper.assertTrue(jar.addExact(Aspect.AIR, 31) && jar.applyLabel(player, Direction.UP, Aspect.FIRE),
                    "Could not label a nonempty jar");
            helper.assertTrue(jar.filter() == Aspect.AIR && jar.aspect() == Aspect.AIR && jar.amount() == 31
                            && jar.facing() == expected[i],
                    "Label ignored source yaw or substituted its coded aspect: yaw=" + yaws[i]);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realLabelItemConsumesOnlyOnSuccessfulApplication(GameTestHelper helper) {
        var jar = placeJar(helper, "jar_normal");
        ServerPlayer player = player(helper);
        player.setYRot(90);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack("label_blank", 2));
        useFirst(player, jar.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(player.getMainHandItem().getCount() == 2 && jar.filter() == null && jar.amount() == 0,
                "A blank label was consumed on an empty jar");
        helper.assertTrue(jar.addExact(Aspect.AIR, 31), "Could not prepare contents for a blank label");
        useFirst(player, jar.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && jar.filter() == Aspect.AIR
                        && jar.facing() == Direction.EAST && jar.amount() == 31,
                "Blank label did not copy contents and use player yaw");
        CompoundTag before = jar.saveWithoutMetadata();
        useFirst(player, jar.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && before.equals(jar.saveWithoutMetadata()),
                "Relabelling an already filtered jar consumed or changed something");
        helper.assertTrue(jar.removeLabel() && jar.take(Aspect.AIR, 31), "Could not reset label fixture");
        player.setItemInHand(InteractionHand.MAIN_HAND, aspectStack("label_filled", Aspect.FIRE, 1, 2));
        useFirst(player, jar.getBlockPos(), Direction.NORTH);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && jar.filter() == Aspect.FIRE && jar.amount() == 0,
                "A filled label did not filter an empty jar without adding essentia");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoEmptyPhialsExtractExactlyTenEachWithoutLosingInventoryItems(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            ServerPlayer player = player(helper);
            helper.assertTrue(jar.addExact(Aspect.AIR, 20), "Could not prepare extraction fixture");
            player.setItemInHand(InteractionHand.MAIN_HAND, stack("phial_empty", 2));
            useFirst(player, jar.getBlockPos(), Direction.UP);
            helper.assertTrue(jar.amount() == 10 && count(player, item("phial_empty")) == 1
                            && count(player, item("phial_filled")) == 1 && contained(player, Aspect.AIR) == 10,
                    "First extraction lost a phial or failed conservation for " + id);
            useFirst(player, jar.getBlockPos(), Direction.UP);
            helper.assertTrue(jar.amount() == 0 && count(player, item("phial_empty")) == 0
                            && count(player, item("phial_filled")) == 2 && contained(player, Aspect.AIR) == 20,
                    "Second extraction lost a phial or failed conservation for " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoFilledPhialsInsertExactlyTenEachAndReturnEmptyPhials(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            ServerPlayer player = player(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, aspectStack("phial_filled", Aspect.AIR, 10, 2));
            useFirst(player, jar.getBlockPos(), Direction.UP);
            helper.assertTrue(jar.amount() == 10 && count(player, item("phial_filled")) == 1
                            && count(player, item("phial_empty")) == 1 && jar.amount() + contained(player, Aspect.AIR) == 20,
                    "First insertion did not conserve essentia/phials for " + id);
            useFirst(player, jar.getBlockPos(), Direction.UP);
            helper.assertTrue(jar.amount() == 20 && count(player, item("phial_filled")) == 0
                            && count(player, item("phial_empty")) == 2,
                    "Second insertion did not return both empty phials for " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refusedPhialsDoNotMutateEitherInventoryOrJar(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            ServerPlayer player = player(helper);
            helper.assertTrue(jar.addExact(Aspect.AIR, 9), "Could not prepare nine-unit fixture");
            assertRefusedPhial(helper, player, jar, stack("phial_empty", 2), "less than ten units");
            assertRefusedPhial(helper, player, jar, aspectStack("phial_filled", Aspect.FIRE, 10, 2), "mismatched contents");
            assertRefusedPhial(helper, player, jar, aspectStack("phial_filled", Aspect.AIR, 9, 2), "malformed nine-unit phial");
            assertRefusedPhial(helper, player, jar, aspectStack("phial_filled", Aspect.AIR, 11, 2), "malformed eleven-unit phial");
            ItemStack mixed = stack("phial_filled", 2);
            new AspectList().add(Aspect.AIR, 5).add(Aspect.FIRE, 5).writeToNBT(mixed.getOrCreateTag());
            assertRefusedPhial(helper, player, jar, mixed, "malformed mixed-aspect phial");
            helper.assertTrue(jar.take(Aspect.AIR, 9) && jar.applyLabel(player, Direction.NORTH, Aspect.AIR), "Could not prepare empty filtered fixture");
            assertRefusedPhial(helper, player, jar, aspectStack("phial_filled", Aspect.FIRE, 10, 2), "empty jar's persistent filter");
            helper.assertTrue(jar.addExact(Aspect.AIR, 241), "Could not prepare nine-space fixture");
            assertRefusedPhial(helper, player, jar, aspectStack("phial_filled", Aspect.AIR, 10, 2), "less than ten free spaces, including void jars");
            helper.assertTrue(jar.take(Aspect.AIR, 1), "Could not prepare exact ten-space fixture");
            player.setItemInHand(InteractionHand.MAIN_HAND, aspectStack("phial_filled", Aspect.AIR, 10, 2));
            useFirst(player, jar.getBlockPos(), Direction.UP);
            helper.assertTrue(jar.amount() == 250 && count(player, item("phial_filled")) == 1
                            && count(player, item("phial_empty")) == 1,
                    "A matching phial was rejected or lost at exactly ten free spaces");
            assertRefusedPhial(helper, player, jar, aspectStack("phial_filled", Aspect.AIR, 10, 2), "a completely full jar");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDropsReturnedPhialsWithoutLossOrDuplication(GameTestHelper helper) {
        for (boolean extracting : new boolean[]{true, false}) {
            var jar = placeJar(helper, "jar_normal");
            ServerPlayer player = player(helper);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            ItemStack held = extracting ? stack("phial_empty", 2) : aspectStack("phial_filled", Aspect.AIR, 10, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            if (extracting) helper.assertTrue(jar.addExact(Aspect.AIR, 20), "Could not prepare full-inventory extraction");
            clearDrops(helper, jar.getBlockPos());
            useFirst(player, jar.getBlockPos(), Direction.UP);
            Item returned = item(extracting ? "phial_filled" : "phial_empty");
            List<ItemEntity> drops = drops(helper, jar.getBlockPos());
            helper.assertTrue(jar.amount() == 10 && player.getMainHandItem().getCount() == 1
                            && count(player, returned) == 0 && drops.size() == 1
                            && drops.get(0).getItem().is(returned) && drops.get(0).getItem().getCount() == 1,
                    "A full inventory lost/duplicated the returned phial: extraction=" + extracting);
            if (extracting) helper.assertTrue(aspects(drops.get(0).getItem()).getAmount(Aspect.AIR) == 10,
                    "The dropped filled phial lost its ten units");
            for (int slot = 1; slot < player.getInventory().items.size(); slot++) {
                ItemStack untouched = player.getInventory().getItem(slot);
                helper.assertTrue(untouched.is(Items.COBBLESTONE) && untouched.getCount() == 64,
                        "A returned phial overwrote a full inventory slot");
            }
            clearDrops(helper, jar.getBlockPos());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void labelRemovalPrecedesPurgeAndOtherFaceRetainsFilter(GameTestHelper helper) {
        var jar = placeJar(helper, "jar_normal");
        ServerPlayer player = player(helper);
        player.setYRot(0);
        helper.assertTrue(jar.addExact(Aspect.AIR, 31) && jar.applyLabel(player, Direction.SOUTH, null), "Could not prepare labelled contents");
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        float fluxBefore = AuraManager.getFlux(helper.getLevel(), jar.getBlockPos());
        clickBlock(player, jar.getBlockPos(), Direction.NORTH);
        helper.assertTrue(jar.filter() == null && jar.amount() == 31 && jar.aspect() == Aspect.AIR
                        && AuraManager.getFlux(helper.getLevel(), jar.getBlockPos()) == fluxBefore,
                "Removing the facing label purged contents or polluted the aura");
        helper.assertTrue(droppedCount(helper, jar.getBlockPos(), item("label_blank")) == 1,
                "Label removal did not drop exactly one blank label");
        helper.assertTrue(jar.applyLabel(player, Direction.UP, null), "Could not restore label for purge fixture");
        clickBlock(player, jar.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(jar.amount() == 0 && jar.filter() == Aspect.AIR
                        && !jar.canAccept(Aspect.FIRE, 10)
                        && AuraManager.getFlux(helper.getLevel(), jar.getBlockPos()) == fluxBefore + 31,
                "Other-face purge failed to retain the filter or convert exactly 31 essentia into flux");
        helper.assertTrue(droppedCount(helper, jar.getBlockPos(), item("label_blank")) == 1,
                "Other-face purge unexpectedly removed another label");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void braceInstallationPrecedesLabelRemovalAndConsumesOnlyOneBrace(GameTestHelper helper) {
        var jar = placeJar(helper, "jar_normal");
        ServerPlayer player = player(helper);
        helper.assertTrue(jar.addExact(Aspect.AIR, 31) && jar.applyLabel(player, Direction.NORTH, null), "Could not prepare brace fixture");
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack("jar_brace", 2));
        clickBlock(player, jar.getBlockPos(), jar.facing());
        helper.assertTrue(jar.blocked() && jar.filter() == Aspect.AIR && jar.amount() == 31
                        && player.getMainHandItem().getCount() == 1 && drops(helper, jar.getBlockPos()).isEmpty(),
                "Brace installation consumed two braces, removed the label, or purged contents");
        // A second normal click cannot consume a duplicate brace.
        player.setShiftKeyDown(false);
        clickBlock(player, jar.getBlockPos(), Direction.UP);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && jar.blocked() && jar.amount() == 31,
                "An already installed brace was consumed again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingAndReplacingFilledJarsKeepsEssentiaAndDropsBraceSeparately(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            ServerPlayer player = player(helper);
            helper.assertTrue(jar.addExact(Aspect.AIR, 73) && jar.applyLabel(player, Direction.NORTH, null)
                            && jar.installBrace(), "Could not prepare filled braced " + id);
            BlockPos pos = jar.getBlockPos();
            float fluxBefore = AuraManager.getFlux(helper.getLevel(), pos);
            helper.getLevel().destroyBlock(pos, true);
            List<ItemEntity> drops = drops(helper, pos);
            Item jarItem = CatalogBlocks.ENTRIES.get(id).get().asItem();
            helper.assertTrue(drops.size() == 2 && droppedCount(helper, pos, jarItem) == 1
                            && droppedCount(helper, pos, item("jar_brace")) == 1
                            && AuraManager.getFlux(helper.getLevel(), pos) == fluxBefore,
                    "Breaking " + id + " duplicated drops, lost the separate brace, or spilled essentia");
            ItemStack droppedJar = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(jarItem)).findFirst().orElseThrow().copy();
            helper.assertTrue(aspects(droppedJar).getAmount(Aspect.AIR) == 73 && aspects(droppedJar).visSize() == 73
                            && droppedJar.hasTag() && droppedJar.getTag().getString("AspectFilter").equals("aer"),
                    "The dropped " + id + " lost its content/filter NBT");
            clearDrops(helper, pos);
            player.setYRot(90);
            player.setItemInHand(InteractionHand.MAIN_HAND, droppedJar);
            player.getMainHandItem().useOn(context(player, pos.below(), Direction.UP));
            var replaced = jarAt(helper, pos);
            helper.assertTrue(replaced.amount() == 73 && replaced.aspect() == Aspect.AIR && replaced.filter() == Aspect.AIR
                            && !replaced.blocked() && replaced.facing() == Direction.EAST
                            && player.getMainHandItem().isEmpty(),
                    "Actual ItemStack placement did not restore filled/filter NBT with the brace separate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyFilteredJarItemRoundTripDoesNotRequireContentsNbt(GameTestHelper helper) {
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            ServerPlayer player = player(helper);
            helper.assertTrue(jar.applyLabel(player, Direction.NORTH, Aspect.FIRE), "Could not filter an empty jar");
            ItemStack stack = jar.asItemStack();
            helper.assertTrue(aspects(stack).visSize() == 0 && stack.hasTag()
                            && stack.getTag().getString("AspectFilter").equals("ignis"),
                    "Empty filtered item failed to preserve its independent filter NBT");
            BlockPos pos = jar.getBlockPos();
            helper.getLevel().removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.getMainHandItem().useOn(context(player, pos.below(), Direction.UP));
            var replaced = jarAt(helper, pos);
            helper.assertTrue(replaced.amount() == 0 && replaced.filter() == Aspect.FIRE
                            && !replaced.canAccept(Aspect.AIR, 10) && replaced.canAccept(Aspect.FIRE, 10),
                    "Empty filtered jar lost its filter during real item placement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredBlockEntitySaveAndUpdatePayloadPreserveAllJarState(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setYRot(270);
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            helper.assertTrue(jar.addExact(Aspect.AIR, 73) && jar.applyLabel(player, Direction.NORTH, null)
                            && jar.installBrace(), "Could not prepare persistent state for " + id);
            CompoundTag saved = jar.saveWithFullMetadata();
            BlockEntity loaded = BlockEntity.loadStatic(jar.getBlockPos(), jar.getBlockState(), saved);
            helper.assertTrue(loaded instanceof EssentiaJarBlockEntity && loaded != jar && loaded.getType() == jar.getType(),
                    "Registered BE factory could not recreate " + id + " from full metadata");
            assertSameState(helper, jar, (EssentiaJarBlockEntity) loaded, "fresh BE save/load");
            var update = new EssentiaJarBlockEntity(jar.getBlockPos(), jar.getBlockState());
            update.load(jar.getUpdateTag());
            assertSameState(helper, jar, update, "chunk update tag");
            helper.assertTrue(jar.getUpdatePacket() instanceof ClientboundBlockEntityDataPacket, "Mutation has no BE data packet");
            var packet = (ClientboundBlockEntityDataPacket) jar.getUpdatePacket();
            helper.assertTrue(packet.getPos().equals(jar.getBlockPos()) && packet.getType() == jar.getType() && packet.getTag() != null,
                    "BE packet identifies the wrong tile or has no state payload");
            var packetCopy = new EssentiaJarBlockEntity(jar.getBlockPos(), jar.getBlockState());
            packetCopy.load(packet.getTag());
            assertSameState(helper, jar, packetCopy, "BE data packet");
            helper.assertTrue(jar.take(Aspect.AIR, 73), "Could not drain persistent fixture");
            var emptyCopy = new EssentiaJarBlockEntity(jar.getBlockPos(), jar.getBlockState());
            emptyCopy.load(jar.getUpdateTag());
            assertSameState(helper, jar, emptyCopy, "empty filtered update");
            helper.assertTrue(emptyCopy.filter() == Aspect.AIR && emptyCopy.blocked(), "Empty update dropped filter/brace state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanningReadsContentsPerItemButNeverManufacturesLabelTemplateAspects(GameTestHelper helper) {
        AspectList phial = AspectRegistry.getAspects(aspectStack("phial_filled", Aspect.AIR, 10, 2));
        helper.assertTrue(phial.size() == 1 && phial.getAmount(Aspect.AIR) == 10 && phial.visSize() == 10,
                "Filled phial scan included shell aspects or multiplied contents by stack size");
        ServerPlayer player = player(helper);
        for (String id : JARS) {
            var jar = placeJar(helper, id);
            helper.assertTrue(jar.addExact(Aspect.AIR, 73) && jar.applyLabel(player, Direction.NORTH, null), "Could not prepare scan fixture");
            ItemStack contents = jar.asItemStack();
            contents.setCount(3);
            AspectList scanned = AspectRegistry.getAspects(contents);
            helper.assertTrue(scanned.size() == 1 && scanned.getAmount(Aspect.AIR) == 73 && scanned.visSize() == 73,
                    "Jar scan did not replace shell aspects with per-item contents");
            helper.assertTrue(jar.take(Aspect.AIR, 73), "Could not empty filtered scan fixture");
            helper.assertTrue(AspectRegistry.getAspects(jar.asItemStack()).visSize() == 0,
                    "Scanning an empty filtered jar manufactured its filtered essentia");
        }
        AspectList plainLabel = AspectRegistry.getAspects(stack("label_filled", 1));
        for (Aspect template : new Aspect[]{Aspect.AIR, Aspect.FIRE}) {
            AspectList codedLabel = AspectRegistry.getAspects(aspectStack("label_filled", template, 1, 1));
            helper.assertTrue(codedLabel.aspects.equals(plainLabel.aspects),
                    "Label scan manufactured its template aspect " + template.getTag());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transportPullsOneUnitEveryFiveTicksAndPreservesOriginalSuctionBranches(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (String id : JARS) {
            var jar = transportJar(helper, id);
            var source = source(helper, jar.getBlockPos().above(), 31, 32);
            tick(helper, jar, 4);
            helper.assertTrue(jar.amount() == 0 && source.amount == 10 && source.takeCalls == 0,
                    "Transport ran before its fifth tick");
            tick(helper, jar, 1);
            helper.assertTrue(jar.amount() == 1 && jar.aspect() == Aspect.AIR && source.amount == 9 && source.takeCalls == 1,
                    "Equal minimum suction was rejected or fifth-tick transport did not conserve one unit");
            tick(helper, jar, 4);
            helper.assertTrue(jar.amount() == 1 && source.amount == 9, "Transport ran between five-tick boundaries");
            tick(helper, jar, 1);
            helper.assertTrue(jar.amount() == 2 && source.amount == 8 && source.takeCalls == 2,
                    "The second five-tick boundary transferred more/less than one unit");

            jar = transportJar(helper, id);
            source = source(helper, jar.getBlockPos().above(), 31, 33);
            tick(helper, jar, 5);
            helper.assertTrue(jar.amount() == 0 && source.amount == 10 && source.takeCalls == 0,
                    "An empty unfiltered jar ignored the source's minimum suction");

            jar = transportJar(helper, id);
            source = source(helper, jar.getBlockPos().above(), 31, 1000);
            helper.assertTrue(jar.applyLabel(player, Direction.NORTH, Aspect.AIR), "Could not prepare filtered suction branch");
            tick(helper, jar, 5);
            helper.assertTrue(jar.amount() == 1 && source.amount == 9,
                    "Filtered source branch incorrectly enforced the unfiltered minimum-suction gate");

            jar = transportJar(helper, id);
            source = source(helper, jar.getBlockPos().above(), 31, 1000);
            helper.assertTrue(jar.addExact(Aspect.AIR, 1), "Could not prepare already typed suction branch");
            tick(helper, jar, 5);
            helper.assertTrue(jar.amount() == 2 && source.amount == 9,
                    "Already typed source branch incorrectly enforced the empty-jar minimum-suction gate");

            jar = transportJar(helper, id);
            source = source(helper, jar.getBlockPos().above(), 0, 0);
            helper.assertTrue(jar.addExact(Aspect.AIR, 250), "Could not prepare full transport fixture");
            tick(helper, jar, 5);
            helper.assertTrue(jar.amount() == 250 && source.amount == (jar.isVoid() ? 9 : 10),
                    "Normal/void full-jar transport did not preserve the original overflow distinction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transportRequiresSourceAboveWithEnabledOutputAndStrictlyLowerSuction(GameTestHelper helper) {
        for (String id : JARS) {
            for (Direction location : Direction.values()) {
                var jar = transportJar(helper, id);
                var source = source(helper, jar.getBlockPos().relative(location), 0, 0);
                tick(helper, jar, 5);
                boolean top = location == Direction.UP;
                helper.assertTrue(jar.amount() == (top ? 1 : 0) && source.amount == (top ? 9 : 10),
                        "A jar pulled from the wrong neighbour: " + location);
            }
            for (int suction : new int[]{32, 33}) {
                var jar = transportJar(helper, id);
                var source = source(helper, jar.getBlockPos().above(), suction, 0);
                tick(helper, jar, 5);
                helper.assertTrue(jar.amount() == 0 && source.amount == 10 && source.takeCalls == 0,
                        "A jar pulled against equal/higher neighbouring suction " + suction);
            }
            var jar = transportJar(helper, id);
            var source = source(helper, jar.getBlockPos().above(), 0, 0);
            source.output = false;
            tick(helper, jar, 5);
            helper.assertTrue(jar.amount() == 0 && source.amount == 10 && source.takeCalls == 0,
                    "Transport called a source whose downward output is disabled");
            jar = transportJar(helper, id);
            source = source(helper, jar.getBlockPos().above(), 0, 0);
            source.connected = false;
            tick(helper, jar, 5);
            helper.assertTrue(jar.amount() == 0 && source.amount == 10 && source.takeCalls == 0,
                    "Transport called a disconnected source");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void placedJarActuallyRunsItsRegisteredServerTicker(GameTestHelper helper) {
        var jar = transportJar(helper, "jar_normal");
        var source = source(helper, jar.getBlockPos().above(), 0, 0);
        helper.runAfterDelay(6, () -> helper.assertTrue(jar.amount() == 1 && source.amount == 9 && source.takeCalls == 1,
                "Placed jar's registered server ticker did not pull at the first five-tick boundary"));
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(jar.amount() == 2 && source.amount == 8 && source.takeCalls == 2,
                    "Placed jar's registered server ticker did not keep the one-per-five-tick rate");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void essentiaRecipeContracts(GameTestHelper helper) {
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        grid.setItem(0, new ItemStack(Items.BLACK_DYE));
        grid.setItem(1, new ItemStack(Items.SLIME_BALL));
        for (int slot = 2; slot < 6; slot++) grid.setItem(slot, new ItemStack(Items.PAPER));
        assertRecipeOutput(helper, grid, item("label_blank"), 4);
        grid.clearContent();
        grid.setItem(1, new ItemStack(Items.CLAY_BALL));
        for (int slot : new int[]{3, 5, 7}) grid.setItem(slot, new ItemStack(Items.GLASS));
        assertRecipeOutput(helper, grid, item("phial_empty"), 8);
        grid.clearContent();
        for (int slot : new int[]{0, 2, 6, 8}) grid.setItem(slot, stack("nugget_brass", 1));
        for (int slot : new int[]{1, 3, 5, 7}) grid.setItem(slot, new ItemStack(Items.STICK));
        assertRecipeOutput(helper, grid, item("jar_brace"), 2);
        grid.clearContent();
        grid.setItem(4, aspectStack("label_filled", Aspect.AIR, 1, 1));
        assertRecipeOutput(helper, grid, item("label_blank"), 1);
        grid.clearContent();
        ItemStack phial = aspectStack("phial_filled", Aspect.FIRE, 10, 1);
        phial.getOrCreateTag().putString("TestMarker", "retain full phial NBT");
        grid.setItem(0, stack("label_blank", 1));
        grid.setItem(1, phial);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        helper.assertTrue(recipe.getSerializer() == EssentiaModule.LABEL_RECIPE.get(), "Typed label did not select its registered NBT-aware recipe");
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(item("label_filled")) && result.getCount() == 1
                        && aspects(result).size() == 1 && aspects(result).getAmount(Aspect.FIRE) == 1,
                "Typed label recipe lost its one-unit template aspect");
        var remaining = recipe.getRemainingItems(grid);
        helper.assertTrue(remaining.get(0).isEmpty() && ItemStack.matches(remaining.get(1), phial)
                        && remaining.stream().filter(stack -> !stack.isEmpty()).count() == 1,
                "Typed label crafting did not return exactly the full original phial with its NBT");
        helper.assertTrue(ItemStack.matches(grid.getItem(1), phial), "Recipe inspection mutated its input phial");
        grid.setItem(2, new ItemStack(Items.DIRT));
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()) && recipe.assemble(grid, helper.getLevel().registryAccess()).isEmpty(),
                "Typed label recipe accepted an extra ingredient");
        grid.setItem(2, ItemStack.EMPTY);
        for (int amount : new int[]{9, 11}) {
            grid.setItem(1, aspectStack("phial_filled", Aspect.FIRE, amount, 1));
            helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Typed label recipe accepted malformed phial amount=" + amount);
        }
        ItemStack mixed = stack("phial_filled", 1);
        new AspectList().add(Aspect.AIR, 5).add(Aspect.FIRE, 5).writeToNBT(mixed.getOrCreateTag());
        grid.setItem(1, mixed);
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Typed label recipe accepted mixed-aspect phial NBT");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filledPhialWithoutNbtClearsCorrectMainAndOffhandSlotsOnly(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 13));
        ItemStack main = stack("phial_filled", 3);
        player.getInventory().setItem(5, main);
        ItemStack offhand = stack("phial_filled", 4);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        int globalOffhand = player.getInventory().items.size() + player.getInventory().armor.size();
        helper.assertTrue(globalOffhand == 40 && player.getInventory().getItem(40) == offhand,
                "Offhand fixture does not occupy global inventory slot 40");
        ItemStack emptyCompound = stack("phial_filled", 2);
        emptyCompound.setTag(new CompoundTag());
        player.getInventory().setItem(6, emptyCompound);
        helper.assertTrue(emptyCompound.getTag() != null && emptyCompound.getTag().isEmpty(), "Empty-compound fixture lost its tag");
        ItemStack canonical = aspectStack("phial_filled", Aspect.AIR, 10, 5);
        ItemStack expected = canonical.copy();
        player.getInventory().setItem(7, canonical);

        // Use the real Inventory -> ItemStack -> Forge item wrapper chain. Forge
        // converts global offhand index 40 to compartment-local slot 0 itself.
        player.getInventory().tick();
        helper.assertTrue(player.getInventory().getItem(5).is(item("phial_empty")) && player.getInventory().getItem(5).getCount() == 3,
                "Actual inventory tick did not clear the main-inventory phial with its count preserved");
        helper.assertTrue(player.getOffhandItem().is(item("phial_empty")) && player.getOffhandItem().getCount() == 4
                        && player.getInventory().getItem(40) == player.getOffhandItem(),
                "Actual inventory tick did not convert offhand local slot 0 at global slot 40 with its count preserved");
        helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE) && player.getMainHandItem().getCount() == 13,
                "Offhand cleanup overwrote the ordinary main-hand item at local slot 0");
        helper.assertTrue(player.getInventory().getItem(6) == emptyCompound && emptyCompound.is(item("phial_filled"))
                        && emptyCompound.getCount() == 2 && emptyCompound.getTag() != null && emptyCompound.getTag().isEmpty(),
                "An existing empty NBT compound was treated as missing NBT, unlike BETA26");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(7), expected)
                        && player.getInventory().getItem(7).getCount() == 5,
                "Inventory cleanup altered a canonical filled phial");
        helper.succeed();
    }

    private static void assertRecipeOutput(GameTestHelper helper, TransientCraftingContainer grid, Item expected, int count) {
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(expected) && result.getCount() == count, "Incorrect ordinary essentia recipe output/count for " + expected);
    }

    private static EssentiaJarBlockEntity transportJar(GameTestHelper helper, String id) {
        BlockPos pos = helper.absolutePos(JAR_POS);
        for (Direction direction : Direction.values()) helper.getLevel().setBlockAndUpdate(pos.relative(direction), Blocks.AIR.defaultBlockState());
        return placeJar(helper, id);
    }

    private static MockTransport source(GameTestHelper helper, BlockPos pos, int suction, int minimum) {
        var state = CatalogBlocks.ENTRIES.get("centrifuge").get().defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, state);
        var source = new MockTransport(pos, state, suction, minimum);
        helper.getLevel().setBlockEntity(source);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == source, "Runtime mock source was not installed in the level");
        return source;
    }

    private static void tick(GameTestHelper helper, EssentiaJarBlockEntity jar, int count) {
        for (int i = 0; i < count; i++) EssentiaJarBlockEntity.tick(helper.getLevel(), jar.getBlockPos(), jar.getBlockState(), jar);
    }

    /** No self ticker: only an actual jar tick may debit this source. */
    private static final class MockTransport extends BlockEntity implements IEssentiaTransport {
        private int amount = 10;
        private int takeCalls;
        private final int suction, minimum;
        private boolean connected = true, output = true;

        private MockTransport(BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int suction, int minimum) {
            super(CatalogBlocks.VISUAL_TILE.get(), pos, state);
            this.suction = suction;
            this.minimum = minimum;
        }
        @Override public boolean isConnectable(Direction face) { return connected && face == Direction.DOWN; }
        @Override public boolean canInputFrom(Direction face) { return false; }
        @Override public boolean canOutputTo(Direction face) { return output && face == Direction.DOWN; }
        @Override public void setSuction(Aspect type, int units) {}
        @Override public Aspect getSuctionType(Direction face) { return Aspect.AIR; }
        @Override public int getSuctionAmount(Direction face) { return suction; }
        @Override public int getMinimumSuction() { return minimum; }
        @Override public Aspect getEssentiaType(Direction face) { return Aspect.AIR; }
        @Override public int getEssentiaAmount(Direction face) { return amount; }
        @Override public int addEssentia(Aspect type, int units, Direction face) { return 0; }
        @Override public int takeEssentia(Aspect type, int units, Direction face) {
            takeCalls++;
            // Deliberately leave connection/output checks to the jar caller so the
            // regression test detects a missing canOutputTo/isConnectable guard.
            if (face != Direction.DOWN || type != Aspect.AIR || units <= 0 || units > amount) return 0;
            amount -= units;
            return units;
        }
    }

    private static EssentiaJarBlockEntity placeJar(GameTestHelper helper, String id) {
        BlockPos pos = helper.absolutePos(JAR_POS);
        clearDrops(helper, pos);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, CatalogBlocks.ENTRIES.get(id).get().defaultBlockState());
        return jarAt(helper, pos);
    }

    private static EssentiaJarBlockEntity jarAt(GameTestHelper helper, BlockPos pos) {
        BlockEntity tile = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(tile instanceof EssentiaJarBlockEntity, "Registered jar did not create its working block entity at " + pos);
        return (EssentiaJarBlockEntity) tile;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EssentiaTest"));
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        BlockPos pos = helper.absolutePos(JAR_POS);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setYRot(0);
        return player;
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath("thaumcraft", id);
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null || item == Items.AIR) throw new IllegalStateException("Missing registered test item " + key);
        return item;
    }

    private static ItemStack stack(String id, int count) {
        return new ItemStack(item(id), count);
    }

    private static ItemStack aspectStack(String id, Aspect aspect, int amount, int count) {
        ItemStack stack = CatalogModule.aspectStack(id, aspect, amount);
        stack.setCount(count);
        return stack;
    }

    private static AspectList aspects(ItemStack stack) {
        AspectList aspects = new AspectList();
        if (stack.hasTag()) aspects.readFromNBT(stack.getTag());
        return aspects;
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int contained(ServerPlayer player, Aspect aspect) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item("phial_filled"))) amount += aspects(stack).getAmount(aspect) * stack.getCount();
        }
        return amount;
    }

    private static UseOnContext context(ServerPlayer player, BlockPos pos, Direction face) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos, face));
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        Vec3 location = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(location, face, pos, false);
    }

    private static void useFirst(ServerPlayer player, BlockPos pos, Direction face) {
        player.getMainHandItem().onItemUseFirst(context(player, pos, face));
    }

    private static void clickBlock(ServerPlayer player, BlockPos pos, Direction face) {
        player.level().getBlockState(pos).use(player.level(), player, InteractionHand.MAIN_HAND, hit(pos, face));
    }

    private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
    }

    private static int droppedCount(GameTestHelper helper, BlockPos pos, Item item) {
        return drops(helper, pos).stream().map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void clearDrops(GameTestHelper helper, BlockPos pos) {
        drops(helper, pos).forEach(ItemEntity::discard);
    }

    private static void assertRefusedPhial(GameTestHelper helper, ServerPlayer player, EssentiaJarBlockEntity jar, ItemStack phial, String reason) {
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, phial);
        ItemStack expected = phial.copy();
        CompoundTag before = jar.saveWithoutMetadata();
        useFirst(player, jar.getBlockPos(), Direction.UP);
        helper.assertTrue(before.equals(jar.saveWithoutMetadata()) && ItemStack.matches(player.getMainHandItem(), expected)
                        && count(player, expected.getItem()) == expected.getCount()
                        && drops(helper, jar.getBlockPos()).isEmpty(),
                "Refused phial changed state/inventory for " + reason + " (void=" + jar.isVoid() + ")");
        Item other = item(expected.is(item("phial_empty")) ? "phial_filled" : "phial_empty");
        helper.assertTrue(count(player, other) == 0, "Refused phial created a return item for " + reason);
    }

    private static void assertSameState(GameTestHelper helper, EssentiaJarBlockEntity expected, EssentiaJarBlockEntity actual, String path) {
        helper.assertTrue(actual.amount() == expected.amount() && actual.aspect() == expected.aspect()
                        && actual.filter() == expected.filter() && actual.facing() == expected.facing()
                        && actual.blocked() == expected.blocked() && actual.isVoid() == expected.isVoid(),
                "Jar state changed across " + path);
    }
}
