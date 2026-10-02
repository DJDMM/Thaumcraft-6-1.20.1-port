package thaumcraft.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.CrucibleBlockEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.arcane.ArcaneWorkbenchMenu;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchEvents;
import thaumcraft.research.ResearchModule;
import thaumcraft.research.ResearchProgression;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ResearchEventGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "research_event_test"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    @GameTest(template = "empty")
    public static void vanillaRecipePreviewDoesNotCreditButResultTakeDoes(GameTestHelper helper) {
        ServerPlayer obtained = player(helper);
        ItemStack planks = new ItemStack(Items.OAK_PLANKS, 4);
        obtained.getInventory().add(planks.copy());
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemPickupEvent(obtained,
                new ItemEntity(helper.getLevel(), obtained.getX(), obtained.getY(), obtained.getZ(), planks.copy()), planks.copy()));
        helper.assertTrue(!KnowledgeStore.get(obtained).hasCraft("minecraft:oak_planks"), "Merely obtaining planks credited a craft");

        // Forge's fake connection lets the real vanilla menu send its preview updates.
        ServerPlayer crafted = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "vanilla_craft_test"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        crafted.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        CraftingMenu menu = new CraftingMenu(1, crafted.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS) && menu.getSlot(0).getItem().getCount() == 4,
                "Vanilla oak-plank recipe did not produce its preview");
        helper.assertTrue(!KnowledgeStore.get(crafted).hasCraft("minecraft:oak_planks"), "Vanilla preview credited a craft");
        menu.clicked(0, 0, ClickType.PICKUP, crafted);
        helper.assertTrue(menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4 && menu.getSlot(1).getItem().isEmpty(),
                "Vanilla result take did not commit its recipe");
        helper.assertTrue(KnowledgeStore.get(crafted).hasCraft("minecraft:oak_planks"), "Vanilla result take did not record proof");
        helper.assertTrue(!KnowledgeStore.get(obtained).hasCraft("minecraft:oak_planks"), "Craft evidence leaked to another player");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickupsRecordOnlyActualNonemptyAcquisitions(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack crystal = new ItemStack(WorldModule.VIS_CRYSTALS.get("aer").get());
        ItemEntity original = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), crystal.copyWithCount(2));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemPickupEvent(player, original, ItemStack.EMPTY));
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("!gotcrystals"), "Zero-item pickup credited a crystal");
        helper.assertTrue(helper.getLevel().addFreshEntity(original), "Test crystal could not spawn");
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        original.playerTouch(player);
        helper.assertTrue(original.getItem().getCount() == 2 && !KnowledgeStore.get(player).knowsResearch("!gotcrystals"),
                "Refused pickup credited a crystal");
        player.getInventory().setItem(0, crystal.copyWithCount(63));
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("!gotcrystals"), "Inventory possession recorded a pickup fact");
        original.playerTouch(player);
        helper.assertTrue(original.getItem().getCount() == 1 && player.getInventory().getItem(0).getCount() == 64,
                "Partial pickup did not commit exactly one crystal");

        ServerPlayer cancelled = player(helper);
        ItemEntity blockedCrystal = new ItemEntity(helper.getLevel(), cancelled.getX(), cancelled.getY(), cancelled.getZ(), crystal.copy());
        helper.assertTrue(helper.getLevel().addFreshEntity(blockedCrystal), "Cancelled-pickup test crystal could not spawn");
        Consumer<net.minecraftforge.event.entity.player.EntityItemPickupEvent> cancelPickup = event -> {
            if (event.getItem() == blockedCrystal) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, cancelPickup);
        try {
            blockedCrystal.playerTouch(cancelled);
            helper.assertTrue(blockedCrystal.getItem().getCount() == 1 && cancelled.getInventory().countItem(crystal.getItem()) == 0,
                    "Cancelled pickup transferred a crystal");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelPickup);
            blockedCrystal.discard();
        }

        // Forge does not emit its post-event for this partial transfer. The server END
        // tick confirms the pre-event's actual item decrease and matching inventory gain.
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(KnowledgeStore.get(player).knowsResearch("!gotcrystals"), "Actual partial crystal pickup was missed");
            helper.assertTrue(!KnowledgeStore.get(cancelled).knowsResearch("!gotcrystals"), "Cancelled pickup recorded a fact");
            helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("!gotdream"), "Crystal pickup skipped the dream");
            helper.assertTrue(!KnowledgeStore.get(player).hasCraft("thaumcraft:vis_crystal_aer"), "Picking up a crystal recorded crafting proof");
            helper.assertTrue(original.getItem().getCount() == 1 && player.getInventory().getItem(0).getCount() == 64,
                    "Partial-pickup bookkeeping changed the committed transfer");
            player.getInventory().setItem(1, ItemStack.EMPTY);
            original.playerTouch(player);
            helper.assertTrue(original.isRemoved() && player.getInventory().countItem(crystal.getItem()) == 65
                            && KnowledgeStore.get(player).researchKeys().stream().filter("!gotcrystals"::equals).count() == 1,
                    "Following full pickup lost items or duplicated its one-time fact");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            ItemStack book = new ItemStack(ResearchModule.THAUMONOMICON.get());
            ItemEntity droppedBook = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), book);
            helper.assertTrue(helper.getLevel().addFreshEntity(droppedBook), "Test Thaumonomicon could not spawn");
            droppedBook.playerTouch(player);
            helper.assertTrue(KnowledgeStore.get(player).knowsResearch("!gotthaumonomicon"), "Book pickup was missed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void journalIsOncePerPlayerAndDropsWhenInventoryFull(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertTrue(!ResearchEvents.giveDreamJournal(player), "Dream occurred without a discovered crystal");
        KnowledgeStore.recordFact(player, "!gotcrystals");
        MinecraftForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, true, true));
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("!gotdream"), "An awake player received a dream");
        player.setSleepingPos(player.blockPosition());
        helper.assertTrue(player.isSleeping() && player.getSleepTimer() == 0, "Test must include an immediate early wake");
        ForgeEventFactory.onPlayerWakeup(player, true, true);
        player.clearSleepingPos();
        helper.assertTrue(KnowledgeStore.get(player).knowsResearch("!gotdream"), "Actual wake hook did not deliver a journal");
        ItemStack journal = player.getInventory().getItem(0);
        helper.assertTrue(journal.is(Items.WRITTEN_BOOK) && WrittenBookItem.makeSureTagIsValid(journal.getTag()), "Invalid written journal");
        helper.assertTrue(WrittenBookItem.getGeneration(journal) == 3 && WrittenBookItem.getPageCount(journal) == 3,
                "BETA26 journal generation/pages changed");
        helper.assertTrue(journal.getTag().getString("author").equals(player.getGameProfile().getName()), "Journal author was not personalized");
        var pages = journal.getTag().getList("pages", Tag.TAG_STRING);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(Component.Serializer.fromJson(pages.getString(i)) != null && pages.getString(i).contains("book.start." + (i + 1)),
                    "Journal page is not a valid localizable text component");
        }
        helper.assertTrue(!ResearchEvents.giveDreamJournal(player) && player.getInventory().countItem(Items.WRITTEN_BOOK) == 1,
                "Repeated wake duplicated the journal");

        ServerPlayer full = player(helper);
        KnowledgeStore.recordFact(full, "!gotcrystals");
        for (int slot = 0; slot < 36; slot++) full.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertTrue(ResearchEvents.giveDreamJournal(full), "Full inventory prevented dream delivery");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(full.blockPosition()).inflate(2), entity -> entity.getItem().is(Items.WRITTEN_BOOK));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "Full inventory lost or duplicated the journal");
        helper.assertTrue(KnowledgeStore.get(full).knowsResearch("!gotdream") && !ResearchEvents.giveDreamJournal(full),
                "Dropped journal did not persist its one-time fact");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refusedCraftAndCrucibleDissolvingNeverRecordOutputs(GameTestHelper helper) {
        ServerPlayer arcanePlayer = player(helper), alchemyPlayer = player(helper);
        BlockPos benchPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(benchPos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(benchPos);
        helper.assertTrue(bench.craft(arcanePlayer).isEmpty(), "Empty workbench created an output");
        helper.assertTrue(!KnowledgeStore.get(arcanePlayer).hasCraft("thaumcraft:thaumometer"),
                "Refused workbench craft credited a thaumometer");

        BlockPos cruciblePos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlockAndUpdate(cruciblePos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(cruciblePos);
        CompoundTag contents = new CompoundTag();
        contents.putInt("Heat", 200); contents.putInt("Water", 1000);
        new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10).writeToNBT(contents);
        crucible.load(contents);
        helper.assertTrue(crucible.consume(new ItemStack(Items.GLOWSTONE_DUST), alchemyPlayer), "Glowstone did not dissolve");
        helper.assertTrue(crucible.water() == 1000, "Locked Nitor recipe consumed craft water");
        helper.assertTrue(!KnowledgeStore.get(alchemyPlayer).hasCraft("thaumcraft:nitor"),
                "Dissolving the catalyst credited a locked Nitor output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arcanePreviewAndRefusalsDoNotCreditButCommittedCraftDoes(GameTestHelper helper) {
        ServerPlayer player = player(helper), bystander = player(helper);
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
        helper.assertTrue(ResearchProgression.advance(player, "FIRSTSTEPS", 0) == ResearchProgression.Result.STARTED,
                "Test could not start FIRSTSTEPS");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AlchemyModule.SALIS_MUNDUS.get()));
        helper.assertTrue(useDust(player, pos).consumesAction(), "Test could not transform the workbench");
        helper.assertTrue(ResearchProgression.advance(player, "FIRSTSTEPS", 1) == ResearchProgression.Result.ADVANCED,
                "Actual workbench transformation did not advance FIRSTSTEPS");
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT));
        bench.setItem(4, new ItemStack(Items.GLASS_PANE));
        for (int i = 0; i < 6; i++) bench.setItem(9 + i, new ItemStack(WorldModule.VIS_CRYSTALS.get(ArcaneModule.PRIMALS[i]).get()));
        setVis(helper, pos, 100);
        ArcaneWorkbenchMenu menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        helper.assertTrue(menu.getSlot(0).getItem().is(ScanningModule.THAUMOMETER.get()), "Thaumometer preview did not load");
        helper.assertTrue(!KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer"), "Arcane preview recorded proof");

        ItemStack missingCrystal = bench.removeItem(14, 1);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(1).getCount() == 1
                        && !KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer"),
                "Missing crystal credited an arcane craft");
        bench.setItem(14, missingCrystal);
        setVis(helper, pos, 19);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(14).getCount() == 1
                        && !KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer"),
                "Insufficient vis credited an arcane craft");
        setVis(helper, pos, 100);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && !KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer"),
                "Full inventory credited an arcane craft");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(menu.quickMoveStack(player, 0).is(ScanningModule.THAUMOMETER.get()) && bench.isEmpty()
                        && AuraManager.getVis(helper.getLevel(), pos) == 80,
                "Arcane craft did not commit its output and resources");
        helper.assertTrue(KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer")
                        && !KnowledgeStore.get(bystander).hasCraft("thaumcraft:thaumometer"),
                "Arcane craft evidence was missing or assigned to another player");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dustTransformationsCreditOnlyCommittedResults(GameTestHelper helper) {
        ServerPlayer player = player(helper), bystander = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AlchemyModule.SALIS_MUNDUS.get(), 3));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        player.setShiftKeyDown(true);
        helper.assertTrue(useDust(player, pos) == InteractionResult.PASS && helper.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                        && player.getMainHandItem().getCount() == 3 && !KnowledgeStore.get(player).hasCraft("thaumcraft:arcane_workbench"),
                "Sneaking Salis use did not pass as in TC6");
        player.setShiftKeyDown(false);
        Consumer<PlayerInteractEvent.RightClickBlock> cancelInteraction = event -> {
            if (event.getEntity() == player && event.getPos().equals(pos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancelInteraction);
        try {
            useDust(player, pos);
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                            && player.getMainHandItem().getCount() == 3 && !KnowledgeStore.get(player).hasCraft("thaumcraft:arcane_workbench"),
                    "Cancelled right-click consumed dust/table or recorded proof");
        } finally { MinecraftForge.EVENT_BUS.unregister(cancelInteraction); }
        BlockPos torchPos = pos.east();
        helper.getLevel().setBlockAndUpdate(torchPos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        Consumer<BlockEvent.EntityPlaceEvent> cancelPlace = event -> {
            if (event.getLevel() == helper.getLevel() && event.getPos().equals(pos) && event.getEntity() == player) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancelPlace);
        try {
            helper.assertTrue(useDust(player, pos) == InteractionResult.FAIL && helper.getLevel().getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                            && player.getMainHandItem().getCount() == 3 && !KnowledgeStore.get(player).hasCraft("thaumcraft:arcane_workbench")
                            && helper.getLevel().getBlockState(torchPos).is(Blocks.WALL_TORCH),
                    "Cancelled Forge placement consumed dust/table or recorded a craft");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelPlace);
        }
        helper.assertTrue(useDust(player, pos).consumesAction() && helper.getLevel().getBlockState(pos).is(ArcaneModule.WORKBENCH.get())
                        && player.getMainHandItem().getCount() == 2 && KnowledgeStore.get(player).hasCraft("thaumcraft:arcane_workbench"),
                "Workbench transformation did not record its committed result");
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        helper.assertTrue(useDust(player, pos).consumesAction() && helper.getLevel().getBlockState(pos).is(AlchemyModule.CRUCIBLE.get())
                        && player.getMainHandItem().getCount() == 1 && KnowledgeStore.get(player).hasCraft("thaumcraft:crucible"),
                "Crucible transformation did not record its committed result");
        helper.getLevel().setBlockAndUpdate(pos, Blocks.BOOKSHELF.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(torchPos, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        Consumer<EntityJoinLevelEvent> cancelBook = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity entity
                    && entity.getItem().is(ResearchModule.THAUMONOMICON.get()) && entity.blockPosition().equals(pos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancelBook);
        try {
            helper.assertTrue(useDust(player, pos) == InteractionResult.FAIL && helper.getLevel().getBlockState(pos).is(Blocks.BOOKSHELF)
                            && player.getMainHandItem().getCount() == 1 && !KnowledgeStore.get(player).hasCraft("thaumcraft:thaumonomicon")
                            && helper.getLevel().getBlockState(torchPos).is(Blocks.WALL_TORCH),
                    "Refused book spawn consumed dust/bookshelf or recorded a craft");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelBook);
        }
        helper.assertTrue(useDust(player, pos).consumesAction() && helper.getLevel().getBlockState(pos).isAir()
                        && player.getMainHandItem().isEmpty() && KnowledgeStore.get(player).hasCraft("thaumcraft:thaumonomicon"),
                "Bookshelf transformation did not record its committed result");
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("!gotthaumonomicon"), "Transformation credited book pickup before acquisition");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), entity -> entity.getItem().is(ResearchModule.THAUMONOMICON.get()));
        helper.assertTrue(drops.size() == 1, "Bookshelf did not create exactly one Thaumonomicon");
        drops.forEach(ItemEntity::discard);
        bystander.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ResearchModule.THAUMONOMICON.get()));
        bystander.getMainHandItem().use(helper.getLevel(), bystander, InteractionHand.MAIN_HAND);
        helper.assertTrue(KnowledgeStore.get(bystander).knowsResearch("!gotthaumonomicon")
                        && !KnowledgeStore.get(bystander).hasCraft("thaumcraft:thaumonomicon")
                        && !KnowledgeStore.get(bystander).hasCraft("thaumcraft:arcane_workbench"),
                "Book use did not record discovery independently of craft evidence");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledCrucibleOutputConsumesNothingAndRecordsNoCraft(GameTestHelper helper) {
        ServerPlayer player = player(helper), bystander = player(helper);
        // Existing PORT saves are intentionally valid recipe unlocks, independent of canonical stages.
        KnowledgeStore.recordFact(player, "PORT_NITOR");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        CrucibleBlockEntity crucible = prepareCrucible(helper, pos);
        ItemStack catalyst = new ItemStack(Items.GLOWSTONE_DUST);
        Consumer<EntityJoinLevelEvent> cancelOutput = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity entity
                    && entity.getItem().is(AlchemyModule.NITOR_ITEM.get()) && entity.blockPosition().equals(pos.above())) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancelOutput);
        try {
            helper.assertTrue(!crucible.consume(catalyst, player) && catalyst.getCount() == 1 && crucible.water() == 1000
                            && crucible.aspects().visSize() == 30 && !KnowledgeStore.get(player).hasCraft("thaumcraft:nitor"),
                    "Refused Nitor spawn consumed resources or recorded proof");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelOutput);
        }
        helper.assertTrue(crucible.consume(catalyst, player) && catalyst.isEmpty() && crucible.water() == 950
                        && crucible.aspects().visSize() == 0 && KnowledgeStore.get(player).hasCraft("thaumcraft:nitor")
                        && !KnowledgeStore.get(bystander).hasCraft("thaumcraft:nitor"),
                "Committed crucible output was missing, unpaid or assigned to another player");
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), entity -> entity.getItem().is(AlchemyModule.NITOR_ITEM.get()))
                .forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void thrownCrucibleCatalystCreditsItsOwnerInsteadOfNearbyPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "crucible_owner_test"));
        ServerPlayer bystander = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "crucible_nearby_test"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        owner.setPos(pos.getX() + 4, pos.getY(), pos.getZ() + 0.5);
        bystander.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addNewPlayer(owner);
        level.addNewPlayer(bystander);
        KnowledgeStore.recordFact(owner, "PORT_NITOR");
        CrucibleBlockEntity crucible = prepareCrucible(helper, pos);
        ItemEntity catalyst = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.GLOWSTONE_DUST));
        catalyst.setNoGravity(true);
        catalyst.setDeltaMovement(Vec3.ZERO);
        catalyst.setThrower(owner.getUUID());
        try {
            helper.assertTrue(level.addFreshEntity(catalyst) && catalyst.getOwner() == owner, "Test catalyst did not resolve its actual owner");
        } catch (Throwable failure) {
            level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            level.removePlayerImmediately(bystander, Entity.RemovalReason.DISCARDED);
            throw failure;
        }
        // Tick's mouth check runs on game-time multiples of five; give it an actual eligible tick.
        int delay = (int) (5 - Math.floorMod(level.getGameTime(), 5));
        helper.runAfterDelay(delay, () -> {
            try {
                CrucibleBlockEntity.tick(level, pos, level.getBlockState(pos), crucible);
                helper.assertTrue(catalyst.isRemoved() && crucible.water() == 950 && KnowledgeStore.get(owner).hasCraft("thaumcraft:nitor"),
                        "Thrown catalyst was not crafted for its owner");
                helper.assertTrue(!KnowledgeStore.get(bystander).hasCraft("thaumcraft:nitor"), "A nearby player received another player's craft proof");
                helper.succeed();
            } finally {
                catalyst.discard();
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), entity -> entity.getItem().is(AlchemyModule.NITOR_ITEM.get()))
                        .forEach(ItemEntity::discard);
                level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                level.removePlayerImmediately(bystander, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    private static InteractionResult useDust(ServerPlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void setVis(GameTestHelper helper, BlockPos pos, float vis) {
        AuraManager.drainVis(helper.getLevel(), pos, Float.MAX_VALUE, false);
        AuraManager.addVis(helper.getLevel(), pos, vis);
    }

    private static CrucibleBlockEntity prepareCrucible(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(pos);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Heat", 200);
        tag.putInt("Water", 1000);
        new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10).writeToNBT(tag);
        crucible.load(tag);
        return crucible;
    }
}
