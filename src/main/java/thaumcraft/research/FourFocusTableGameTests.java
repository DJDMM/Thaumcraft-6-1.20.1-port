package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.auromancy.focus.FocusGraph;
import thaumcraft.auromancy.focus.FocusNodeRegistry;
import thaumcraft.auromancy.focus.FocusStacks;
import thaumcraft.auromancy.table.FocalManipulatorBlockEntity;
import thaumcraft.auromancy.table.FocalManipulatorMenu;
import thaumcraft.auromancy.table.FocalManipulatorResult;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.world.aura.AuraManager;

import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Manufacturing the new original nodes through the real paid table, independently of preview compilation. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class FourFocusTableGameTests {
    private record Spell(String name, String medium, String effect, Map<String, Integer> settings,
                         String mediumAspect, String effectAspect, int complexity, int craftVis, int xp) {}
    private static final List<Spell> SPELLS = List.of(
            new Spell("Bolt Flux", FocusNodeRegistry.BOLT, FocusNodeRegistry.FLUX, Map.of("power", 1), "potentia", "vitium", 8, 83, 3),
            new Spell("Touch Heal", FocusNodeRegistry.TOUCH, FocusNodeRegistry.HEAL, Map.of("power", 1), "aversio", "victus", 6, 63, 2),
            new Spell("Touch Break", FocusNodeRegistry.TOUCH, FocusNodeRegistry.BREAK, Map.of("power", 1, "silk", 1, "fortune", 1), "aversio", "perditio", 15, 153, 4));
    private record Fixture(GameTestHelper h, BlockPos pos, FocalManipulatorBlockEntity table, ServerPlayer player) {}

    private static Fixture fixture(GameTestHelper h, Spell spell) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, CatalogBlocks.block("wand_workbench").defaultBlockState());
        var table = (FocalManipulatorBlockEntity)level.getBlockEntity(pos);
        var p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "four_focus_table"));
        p.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2.5); p.experienceLevel = 10;
        for (String research : List.of("BASEAUROMANCY", "FOCUSBOLT", "FOCUSFLUX", "FOCUSHEAL", "FOCUSBREAK")) complete(p, research);
        p.containerMenu = new FocalManipulatorMenu(1, p.getInventory(), table);
        var blank = CatalogModule.stack("focus_1"); blank.getOrCreateTag().putString("owned_marker", spell.name()); table.setItem(0, blank);
        p.getInventory().setItem(9, AspectCrystalItem.create(Aspect.getAspect(spell.mediumAspect()), 3));
        p.getInventory().setItem(10, AspectCrystalItem.create(Aspect.getAspect(spell.effectAspect()), 3));
        AuraManager.drainVis(level, pos, Float.MAX_VALUE, false); AuraManager.addVis(level, pos, 200);
        return new Fixture(h, pos, table, p);
    }
    private static void complete(ServerPlayer p, String research) {
        KnowledgeStore.get(p).setResearchStage(research, ResearchCatalog.get(research).stages().size() + 1);
    }
    private static FocusGraph graph(Spell spell) {
        return new FocusGraph(List.of(
                new FocusGraph.Node(0, -1, List.of(1), 0, 0, FocusNodeRegistry.ROOT, Map.of()),
                new FocusGraph.Node(1, 0, List.of(2), 0, 1, spell.medium(), Map.of()),
                new FocusGraph.Node(2, 1, List.of(), 0, 2, spell.effect(), spell.settings())));
    }
    private static void ticks(Fixture f, int count) {
        for (int i = 0; i < count; i++) FocalManipulatorBlockEntity.tick(f.h().getLevel(), f.pos(), f.table().getBlockState(), f.table());
    }
    private static void result(GameTestHelper h, FocalManipulatorResult actual, FocalManipulatorResult expected) {
        h.assertTrue(actual == expected, "Expected " + expected + ", got " + actual);
    }
    private static void edit(Fixture f, Spell spell) {
        result(f.h(), f.table().edit(f.player(), f.table().revision(), graph(spell).save(), spell.name()), FocalManipulatorResult.ACCEPTED);
    }
    private static void start(Fixture f) {
        result(f.h(), f.table().start(f.player(), f.table().revision()), FocalManipulatorResult.ACCEPTED);
    }
    private static void near(GameTestHelper h, float actual, float expected, String reason) {
        h.assertTrue(Math.abs(actual - expected) < .001, reason + ": " + actual + " != " + expected);
    }
    private static void paid(Fixture f, Spell spell) {
        f.h().assertTrue(f.player().experienceLevel == 10 - spell.xp()
                        && f.player().getInventory().getItem(9).getCount() == 2 && f.player().getInventory().getItem(10).getCount() == 2,
                "Original XP/one medium crystal/one effect crystal payment drift: " + spell.name());
    }
    private static void output(Fixture f, Spell spell) {
        var output = f.table().getItem(0); var plan = FocusStacks.readPlan(output).orElseThrow();
        f.h().assertTrue(!f.table().crafting() && plan.complexity() == spell.complexity()
                        && plan.graph().nodes().get(1).key().equals(spell.medium()) && plan.effect().key().equals(spell.effect())
                        && plan.effect().settings().equals(spell.settings())
                        && plan.crystals().equals(Map.of(spell.mediumAspect(), 1, spell.effectAspect(), 1))
                        && output.getTag().getString("owned_marker").equals(spell.name()) && output.getHoverName().getString().equals(spell.name()),
                "Manufactured package changed effect, medium, exact settings, aspects, name or custom input NBT: " + spell.name());
        near(f.h(), AuraManager.getVis(f.h().getLevel(), f.pos()), 200 - spell.craftVis(), "Exact manufactured aura total"); paid(f, spell);
    }

    @GameTest(template="empty") public static void newFocusNodesManufactureThroughRealTableWithExactGradualPayments(GameTestHelper h) {
        for (Spell spell : SPELLS) {
            var f = fixture(h, spell); edit(f, spell); start(f); paid(f, spell);
            near(h, f.table().remainingVis(), spell.craftVis(), "Original pending craft price");
            ticks(f, 19);
            near(h, AuraManager.getVis(h.getLevel(), f.pos()), 200, "New spell charged before twentieth tick");
            h.assertTrue(FocusStacks.readPlan(f.table().getItem(0)).isEmpty(), "New spell manufactured a package before aura payment");
            int remaining = spell.craftVis(), spent = 0;
            ticks(f, 1);
            while (remaining > 0) {
                int debit = Math.min(20, remaining); remaining -= debit; spent += debit;
                near(h, AuraManager.getVis(h.getLevel(), f.pos()), 200 - spent, "Twenty-vis gradual debit");
                near(h, f.table().remainingVis(), remaining, "Exact remaining aura");
                if (remaining > 0) {
                    h.assertTrue(f.table().crafting() && FocusStacks.readPlan(f.table().getItem(0)).isEmpty(), "Premature new-node output before full payment"); ticks(f, 20);
                }
            }
            output(f, spell); var before = f.table().getItem(0); ticks(f, 40);
            h.assertTrue(ItemStack.matches(before, f.table().getItem(0)), "Finished new-node focus was written twice"); output(f, spell);
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void paidNonFireGraphsResumeExactCadenceSettingsAndResourcesAfterSaveReload(GameTestHelper h) {
        for (Spell spell : SPELLS) {
            var f = fixture(h, spell); edit(f, spell); start(f); ticks(f, 27); var saved = f.table().saveWithoutMetadata();
            var reloaded = new FocalManipulatorBlockEntity(f.pos(), f.table().getBlockState()); reloaded.load(saved);
            h.getLevel().getChunkAt(f.pos()).addAndRegisterBlockEntity(reloaded);
            f.player().containerMenu = new FocalManipulatorMenu(2, f.player().getInventory(), reloaded);
            var resumed = new Fixture(h, f.pos(), reloaded, f.player());
            h.assertTrue(saved.equals(reloaded.saveWithoutMetadata()) && reloaded.crafting(), "New-node paid graph/cadence/input did not survive reload");
            near(h, reloaded.remainingVis(), spell.craftVis() - 20, "Paid graph remaining price after reload");
            // Paid plans are detached; losing a research capability cannot charge XP/crystals a second time.
            KnowledgeStore.get(f.player()).setResearchStage(FocusNodeRegistry.get(spell.effect()).research(), 1);
            ticks(resumed, 12); near(h, reloaded.remainingVis(), spell.craftVis() - 20, "Reload prematurely reset twenty-tick cadence");
            ticks(resumed, 1); near(h, reloaded.remainingVis(), spell.craftVis() - 40, "Reload missed the fortieth-tick debit");
            for (int i = 0; i < 12 && reloaded.crafting(); i++) ticks(resumed, 20);
            output(resumed, spell);
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void startedNewResearchCannotEditOrStartAndNeverChargesTableResources(GameTestHelper h) {
        for (Spell spell : SPELLS) {
            var f = fixture(h, spell); var research = FocusNodeRegistry.get(spell.effect()).research();
            KnowledgeStore.get(f.player()).setResearchStage(research, 1); var blank = f.table().saveWithoutMetadata();
            result(h, f.table().edit(f.player(), f.table().revision(), graph(spell).save(), spell.name()), FocalManipulatorResult.MISSING_RESEARCH);
            h.assertTrue(blank.equals(f.table().saveWithoutMetadata()), "Started-only effect lesson mutated editor");
            complete(f.player(), research); edit(f, spell); KnowledgeStore.get(f.player()).setResearchStage(research, 1);
            var edited = f.table().saveWithoutMetadata();
            result(h, f.table().start(f.player(), f.table().revision()), FocalManipulatorResult.MISSING_RESEARCH);
            if (spell.medium().equals(FocusNodeRegistry.BOLT)) {
                complete(f.player(), research); KnowledgeStore.get(f.player()).setResearchStage("FOCUSBOLT", 1);
                result(h, f.table().start(f.player(), f.table().revision()), FocalManipulatorResult.MISSING_RESEARCH);
            }
            h.assertTrue(edited.equals(f.table().saveWithoutMetadata()) && !f.table().crafting()
                            && f.player().experienceLevel == 10 && f.player().getInventory().getItem(9).getCount() == 3
                            && f.player().getInventory().getItem(10).getCount() == 3,
                    "New effect/medium research rejection consumed resources or entered paid craft");
            near(h, AuraManager.getVis(h.getLevel(), f.pos()), 200, "Research rejection debited aura");
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void newFocusManufacturingPreflightsMissingCrystalsAndXpAtomically(GameTestHelper h) {
        for (Spell spell : SPELLS) {
            var f = fixture(h, spell); edit(f, spell);
            var crystal = f.player().getInventory().getItem(10).copy(); f.player().getInventory().setItem(10, ItemStack.EMPTY);
            f.player().getInventory().offhand.set(0, crystal); var before = f.table().saveWithoutMetadata();
            result(h, f.table().start(f.player(), f.table().revision()), FocalManipulatorResult.MISSING_CRYSTALS);
            h.assertTrue(before.equals(f.table().saveWithoutMetadata()) && f.player().experienceLevel == 10
                            && f.player().getInventory().getItem(9).getCount() == 3 && f.player().getInventory().offhand.get(0).getCount() == 3,
                    "Missing main-inventory new-effect crystal partially paid or used offhand");
            f.player().getInventory().setItem(10, crystal.copy()); f.player().experienceLevel = spell.xp() - 1;
            result(h, f.table().start(f.player(), f.table().revision()), FocalManipulatorResult.MISSING_XP);
            h.assertTrue(before.equals(f.table().saveWithoutMetadata()) && f.player().experienceLevel == spell.xp() - 1
                            && f.player().getInventory().getItem(9).getCount() == 3 && f.player().getInventory().getItem(10).getCount() == 3,
                    "Missing new-node XP partially debited crystals or table state");
            near(h, AuraManager.getVis(h.getLevel(), f.pos()), 200, "Preflight failure spent aura");
        }
        h.succeed();
    }
}
