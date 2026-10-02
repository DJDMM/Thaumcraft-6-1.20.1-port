package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.arcane.ArcaneWorkbenchMenu;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.scanning.ScanningModule;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** Compatibility at actual paid crafting and acquisition boundaries, rather than NBT parsing alone. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CrystalInteropGameTests {
    private CrystalInteropGameTests() {}

    @GameTest(template = "empty")
    public static void containedPrimalsShiftIntoSlotsAndPayForThaumometer(GameTestHelper helper) {
        var player = player(helper);
        KnowledgeStore.get(player).setResearchStage("FIRSTSTEPS", 2);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, ArcaneModule.WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(Items.GOLD_INGOT));
        bench.setItem(4, new ItemStack(Items.GLASS_PANE));
        AuraManager.drainVis(helper.getLevel(), pos, Float.MAX_VALUE, false);
        AuraManager.addVis(helper.getLevel(), pos, 100);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        for (int i = 0; i < 6; i++) {
            ItemStack crystal = AspectCrystalItem.create(Aspect.getAspect(ArcaneModule.PRIMALS[i]), 2);
            helper.assertTrue(bench.canPlaceItem(9 + i, crystal) && menu.getSlot(10 + i).mayPlace(crystal),
                    "Original contained primal could not enter its bench/menu slot");
            helper.assertTrue(!menu.getSlot(10 + ((i + 1) % 6)).mayPlace(crystal), "Primal entered the wrong crystal slot");
            player.getInventory().setItem(9 + i, crystal);
            menu.quickMoveStack(player, ArcaneWorkbenchMenu.PLAYER_START + i);
            helper.assertTrue(bench.getItem(9 + i).getCount() == 2 && player.getInventory().getItem(9 + i).isEmpty(),
                    "Original contained primal was not shift-routed to its slot");
        }
        ItemStack savedAir = bench.getItem(9).copy();
        ItemStack compound = AspectCrystalItem.create(Aspect.MAGIC);
        ItemStack malformed = CatalogModule.stack("crystal_essence");
        ItemStack multi = AspectCrystalItem.create(Aspect.AIR);
        ((IEssentiaContainerItem) multi.getItem()).setAspects(multi, new AspectList().add(Aspect.AIR, 1).add(Aspect.FIRE, 1));
        for (ItemStack bad : new ItemStack[]{compound, malformed, multi}) {
            for (int i = 0; i < 6; i++) helper.assertTrue(!bench.canPlaceItem(9 + i, bad)
                    && !menu.getSlot(10 + i).mayPlace(bad), "Non-primal/malformed crystal entered a primal slot");
            // Even a direct inventory setter cannot bypass validation at payment time.
            bench.setItem(9, bad);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && bench.getItem(1).getCount() == 1
                    && AuraManager.getVis(helper.getLevel(), pos) == 100, "Bad contained crystal paid an arcane cost");
        }
        bench.setItem(9, savedAir);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ScanningModule.THAUMOMETER.get())
                && KnowledgeStore.get(player).hasCraft("thaumcraft:thaumometer")
                && AuraManager.getVis(helper.getLevel(), pos) == 80, "Contained primals did not pay a real thaumometer craft");
        for (int i = 0; i < 6; i++) helper.assertTrue(bench.getItem(9 + i).getCount() == 1
                && AspectCrystalItem.matchesPrimal(bench.getItem(9 + i), ArcaneModule.PRIMALS[i]),
                "Craft changed original NBT or consumed the wrong primal count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void salisMixesOriginalAndLegacyCrystalsButRejectsDuplicateAspectTags(GameTestHelper helper) {
        var player = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        menu.getSlot(1).set(new ItemStack(Items.FLINT));
        menu.getSlot(2).set(new ItemStack(Items.BOWL));
        menu.getSlot(3).set(new ItemStack(Items.REDSTONE));
        menu.getSlot(4).set(AspectCrystalItem.create(Aspect.AIR));
        menu.getSlot(5).set(AspectCrystalItem.create(Aspect.FIRE));
        menu.getSlot(6).set(new ItemStack(WorldModule.VIS_CRYSTALS.get("aer").get()));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "New and legacy Aer counted as distinct Salis aspects");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(3).hasItem(), "Rejected Salis spent redstone");
        ItemStack multi = AspectCrystalItem.create(Aspect.EARTH);
        ((IEssentiaContainerItem) multi.getItem()).setAspects(multi, new AspectList().add(Aspect.EARTH, 1).add(Aspect.WATER, 1));
        menu.getSlot(6).set(multi);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Malformed multi-aspect crystal entered Salis recipe");
        menu.getSlot(6).set(new ItemStack(WorldModule.VIS_CRYSTALS.get("terra").get()));
        helper.assertTrue(menu.getSlot(0).getItem().is(AlchemyModule.SALIS_MUNDUS.get()), "Mixed-format original Salis did not match");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(AlchemyModule.SALIS_MUNDUS.get())
                && KnowledgeStore.get(player).hasCraft("thaumcraft:salis_mundus"), "Salis menu take did not commit a craft");
        helper.assertTrue(menu.getSlot(1).getItem().is(Items.FLINT) && menu.getSlot(2).getItem().is(Items.BOWL),
                "Salis lost original flint/bowl remainders");
        for (int slot = 3; slot <= 9; slot++) helper.assertTrue(!menu.getSlot(slot).hasItem(), "Salis left a consumed ingredient");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(3).set(new ItemStack(Items.REDSTONE));
        menu.getSlot(4).set(AspectCrystalItem.create(Aspect.AIR));
        menu.getSlot(5).set(AspectCrystalItem.create(Aspect.FIRE));
        // BETA26 RecipeMagicDust has no isPrimal condition: any three distinct single aspects work.
        menu.getSlot(6).set(AspectCrystalItem.create(Aspect.MAGIC));
        helper.assertTrue(menu.getSlot(0).getItem().is(AlchemyModule.SALIS_MUNDUS.get()),
                "Salis incorrectly rejected a valid single compound-aspect crystal from BETA26");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(AlchemyModule.SALIS_MUNDUS.get()), "Compound-aspect Salis did not commit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualOriginalCrystalAcquisitionStartsDreamsAndIgnoresInventoryPossession(GameTestHelper helper) {
        var player = player(helper);
        ItemStack crystal = AspectCrystalItem.create(Aspect.MAGIC);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(0, crystal.copyWithCount(63));
        helper.assertTrue(!KnowledgeStore.get(player).knowsResearch("!gotcrystals"), "Possession alone discovered a crystal");
        var dropped = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), crystal.copyWithCount(2));
        helper.assertTrue(helper.getLevel().addFreshEntity(dropped), "Original crystal failed to spawn");
        dropped.playerTouch(player);
        helper.assertTrue(dropped.getItem().getCount() == 1 && player.getInventory().getItem(0).getCount() == 64,
                "Actual original crystal partial pickup failed");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(KnowledgeStore.get(player).knowsResearch("!gotcrystals")
                    && !KnowledgeStore.get(player).hasCraft("thaumcraft:crystal_essence"),
                    "Original crystal acquisition was missed or confused with crafting");
            player.getInventory().setItem(1, ItemStack.EMPTY);
            helper.assertTrue(ResearchEvents.giveDreamJournal(player)
                    && KnowledgeStore.get(player).knowsResearch("!gotdream"), "Acquisition did not enable Strange Dreams");
            helper.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "crystal_interop"));
        // Vanilla result previews send a packet. This transport opens no socket and preserves
        // actual ServerPlayer crafting/acquisition hooks without using Forge's FakePlayer.
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return player;
    }
}
