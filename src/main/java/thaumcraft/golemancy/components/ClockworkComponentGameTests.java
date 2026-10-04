package thaumcraft.golemancy.components;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.arcane.ArcaneModule;
import thaumcraft.arcane.ArcaneWorkbenchBlockEntity;
import thaumcraft.arcane.ArcaneWorkbenchMenu;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.world.WorldModule;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** Actual paid BETA26 MindClockwork result transactions; research setup is an explicit fixture. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ClockworkComponentGameTests {
    private static final BlockPos CENTER = new BlockPos(1, 2, 1);
    private ClockworkComponentGameTests() {}

    @GameTest(template = "empty")
    public static void clockworkMindNeedsEnteredStageTwoAndPaysItsOriginalCostsAtomically(GameTestHelper helper) {
        var bench = bench(helper);
        var player = player(helper, bench);
        mindInputs(bench, stack("mechanism_simple", 2), 2);
        bench.getItem(6).getOrCreateTag().putString("componentFixture", "keepOnRemainingPlate");
        setVis(helper, bench, 40);
        var menu = new ArcaneWorkbenchMenu(1, player.getInventory(), bench);
        var before = bench.saveWithoutMetadata();
        var knowledge = KnowledgeStore.get(player).save();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && knowledge.equals(KnowledgeStore.get(player).save()) && vis(helper, bench) == 40,
                "Unknown MindClockwork paid materials, knowledge, crystals or aura");
        stage(player, "MINDCLOCKWORK", 1);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 40 && bench.findRecipe(player) == null,
                "Bare started MINDCLOCKWORK bypassed the original @2 recipe predicate");
        stage(player, "MINDCLOCKWORK", 2);
        menu.broadcastChanges();
        var recipe = bench.findRecipe(player);
        helper.assertTrue(recipe != null && recipe.research().equals("MINDCLOCKWORK@2")
                        && recipe.vis() == 25 && recipe.crystalCost(1) == 1 && recipe.crystalCost(4) == 1
                        && menu.craftable(),
                "Entered stage two did not expose the original 25-vis Ignis/Ordo recipe");
        bench.removeItemNoUpdate(13);
        before = bench.saveWithoutMetadata();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 40,
                "Missing Ordo partially paid a mind");
        bench.setItem(13, CatalogModule.aspectStack("crystal_essence", Aspect.ORDER, 1));
        setVis(helper, bench, 24);
        before = bench.saveWithoutMetadata();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 24,
                "24-vis mind attempt consumed a component or crystal");
        setVis(helper, bench, 27);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(stack("mind_clockwork", 1).getItem())
                        && menu.getCarried().getCount() == 1 && !menu.getCarried().hasTag() && vis(helper, bench) == 2,
                "Mind result was not a clean metadata-zero equivalent or exact 25-vis payment");
        for (int slot : new int[]{1, 3, 4, 5, 6, 7, 8})
            helper.assertTrue(bench.getItem(slot).getCount() == 1, "Mind consumed the wrong number of physical parts");
        helper.assertTrue(bench.getItem(6).getTag().getString("componentFixture").equals("keepOnRemainingPlate")
                        && bench.getItem(10).isEmpty() && bench.getItem(13).isEmpty()
                        && KnowledgeStore.get(player).hasCraft("thaumcraft:mind_clockwork")
                        && KnowledgeStore.get(player).researchStage("MINDCLOCKWORK") == 2
                        && !KnowledgeStore.get(player).isResearchKnown("CONTROLSEALS"),
                "Mind changed remaining ingredient NBT, omitted its crafting proof or advanced research/siblings");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paidSimpleMechanismFeedsMindAndThenOriginalMnemonicMatrix(GameTestHelper helper) {
        var bench = bench(helper);
        var player = player(helper, bench);
        stage(player, "BASEARTIFICE", 1);
        stage(player, "MINDCLOCKWORK", 2);
        stage(player, "THAUMATORIUM", 1);
        setVis(helper, bench, 100);
        bench.setItem(1, stack("plate_brass", 1));
        bench.setItem(7, stack("plate_brass", 1));
        bench.setItem(3, stack("plate_iron", 1));
        bench.setItem(5, stack("plate_iron", 1));
        bench.setItem(4, new ItemStack(Items.STICK));
        bench.setItem(10, CatalogModule.aspectStack("crystal_essence", Aspect.FIRE, 1));
        bench.setItem(11, CatalogModule.aspectStack("crystal_essence", Aspect.WATER, 1));
        var menu = new ArcaneWorkbenchMenu(2, player.getInventory(), bench);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        var mechanism = menu.getCarried().copy();
        helper.assertTrue(mechanism.is(stack("mechanism_simple", 1).getItem()) && mechanism.getCount() == 1
                        && bench.isEmpty() && vis(helper, bench) == 90,
                "Original five-part simple mechanism did not actually pay 10 vis for the intermediate");
        menu.setCarried(ItemStack.EMPTY);
        mindInputs(bench, mechanism, 1);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        var mind = menu.getCarried().copy();
        helper.assertTrue(mind.is(stack("mind_clockwork", 1).getItem()) && mind.getCount() == 1
                        && bench.isEmpty() && vis(helper, bench) == 65,
                "The actual paid mechanism failed the seven-part 25-vis mind transaction");
        menu.setCarried(ItemStack.EMPTY);
        for (int slot : new int[]{0, 2, 6, 8}) bench.setItem(slot, stack("plate_iron", 1));
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, stack("amber", 1));
        bench.setItem(4, mind);
        bench.setItem(12, CatalogModule.aspectStack("crystal_essence", Aspect.EARTH, 1));
        bench.setItem(13, CatalogModule.aspectStack("crystal_essence", Aspect.ORDER, 1));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(stack("brain_box", 1).getItem()) && menu.getCarried().getCount() == 1
                        && bench.isEmpty() && vis(helper, bench) == 15
                        && KnowledgeStore.get(player).hasCraft("thaumcraft:mechanism_simple")
                        && KnowledgeStore.get(player).hasCraft("thaumcraft:mind_clockwork")
                        && KnowledgeStore.get(player).hasCraft("thaumcraft:brain_box")
                        && KnowledgeStore.get(player).researchStage("MINDCLOCKWORK") == 2
                        && KnowledgeStore.get(player).researchStage("THAUMATORIUM") == 1,
                "Real intermediate chain failed exact 10+25+50-vis/crystal payment or changed research stages");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clockworkMindRejectsPartSubstitutionsAndAcceptsLegacyPrimals(GameTestHelper helper) {
        var bench = bench(helper);
        var player = player(helper, bench);
        stage(player, "MINDCLOCKWORK", 2);
        setVis(helper, bench, 60);
        var menu = new ArcaneWorkbenchMenu(3, player.getInventory(), bench);
        mindInputs(bench, stack("mechanism_complex", 1), 1);
        var before = bench.saveWithoutMetadata();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 60 && bench.findRecipe(player) == null,
                "Complex mechanism replaced the exact original simple mechanism");
        bench.setItem(4, stack("mechanism_simple", 1));
        bench.setItem(7, new ItemStack(Items.REPEATER));
        before = bench.saveWithoutMetadata();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 60 && bench.findRecipe(player) == null,
                "Repeater replaced the original comparator");
        bench.setItem(7, new ItemStack(Items.COMPARATOR));
        bench.setItem(6, stack("plate_iron", 1));
        before = bench.saveWithoutMetadata();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 60 && bench.findRecipe(player) == null,
                "Iron replaced an original brass plate");
        bench.setItem(6, stack("plate_brass", 1));
        bench.setItem(10, new ItemStack(WorldModule.VIS_CRYSTALS.get("ignis").get(), 2));
        // Direct inventory setters are an explicit malformed-input fixture, bypassing slot placement checks.
        bench.setItem(13, CatalogModule.aspectStack("crystal_essence", Aspect.ENTROPY, 2));
        before = bench.saveWithoutMetadata();
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && before.equals(bench.saveWithoutMetadata())
                        && vis(helper, bench) == 60,
                "Perditio in the Ordo slot paid a mind or consumed the valid Ignis first");
        bench.setItem(13, new ItemStack(WorldModule.VIS_CRYSTALS.get("ordo").get(), 2));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(stack("mind_clockwork", 1).getItem())
                        && menu.getCarried().getCount() == 1 && vis(helper, bench) == 35
                        && bench.getItem(10).getCount() == 1 && bench.getItem(13).getCount() == 1,
                "Compatible legacy Ignis/Ordo crystals failed exact one-of-each 25-vis payment");
        for (int slot = 0; slot < 9; slot++)
            helper.assertTrue(bench.getItem(slot).isEmpty(), "Legacy crystal transaction left a physical part");
        helper.succeed();
    }

    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper helper) {
        helper.setBlock(CENTER, ArcaneModule.WORKBENCH.get());
        return (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }

    private static ServerPlayer player(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "clockwork_parts"));
        var pos = bench.getBlockPos();
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        return player;
    }

    private static void stage(ServerPlayer player, String key, int value) {
        try {
            var method = PlayerKnowledge.class.getDeclaredMethod("setResearchStage", String.class, int.class);
            method.setAccessible(true);
            method.invoke(KnowledgeStore.get(player), key, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static ItemStack stack(String id, int count) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("Missing registered component " + id);
        return new ItemStack(item, count);
    }

    private static void setVis(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench, float value) {
        AuraManager.drainVis(helper.getLevel(), bench.getBlockPos(), Float.MAX_VALUE, false);
        AuraManager.addVis(helper.getLevel(), bench.getBlockPos(), value);
    }

    private static float vis(GameTestHelper helper, ArcaneWorkbenchBlockEntity bench) {
        return AuraManager.getVis(helper.getLevel(), bench.getBlockPos());
    }

    private static void mindInputs(ArcaneWorkbenchBlockEntity bench, ItemStack mechanism, int count) {
        bench.clearContent();
        for (int slot : new int[]{1, 3, 5}) bench.setItem(slot, new ItemStack(Items.GLASS_PANE, count));
        bench.setItem(4, mechanism.copy());
        bench.setItem(6, stack("plate_brass", count));
        bench.setItem(8, stack("plate_brass", count));
        bench.setItem(7, new ItemStack(Items.COMPARATOR, count));
        bench.setItem(10, CatalogModule.aspectStack("crystal_essence", Aspect.FIRE, 1));
        bench.setItem(13, CatalogModule.aspectStack("crystal_essence", Aspect.ORDER, 1));
    }
}
