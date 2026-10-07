package thaumcraft.golemancy.press;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static thaumcraft.golemancy.press.GolemDesign.Category;
import static thaumcraft.golemancy.press.GolemDesign.Trait;

/** Property assertions use pinned BETA26 constants; completed Mind is an explicit QA fixture. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemDesignGameTests {
    private GolemDesignGameTests() {}

    @GameTest(template = "empty")
    public static void golemDesignWoodDefaultHasOriginalDetachedComponentsAndStats(GameTestHelper helper) {
        var design = GolemDesign.fromLong(0).orElseThrow();
        helper.assertTrue(design.material().key().equals("WOOD") && design.head().key().equals("BASIC")
                        && design.arms().key().equals("BASIC") && design.legs().key().equals("WALKER")
                        && design.addon().key().equals("NONE") && design.rank() == 0 && design.props() == 0,
                "Default props no longer selects the original wood/basic/walker/none design");
        assertComponents(helper, design.components(), List.of(
                stack("thaumcraft:plank_greatwood", 3), stack("thaumcraft:mechanism_simple", 2),
                stack("thaumcraft:mind_clockwork", 1)));
        helper.assertTrue(design.traits().equals(Set.of(Trait.LIGHT)) && design.essentiaCost() == 8
                        && design.health() == 16 && design.armor() == 2 && design.attackDamage() == 0
                        && Math.abs(design.moveSpeed() - 1.2F) < .00001F,
                "Default light wood changed its eight Machina, sixteen health, armor two or noncombat speed");
        helper.assertTrue(design.requiredResearch().equals(Set.of("MATSTUDWOOD", "MINDCLOCKWORK")),
                "Default design added an invented research requirement or lost original strict Mind");
        var detached = design.components();
        detached.get(0).setCount(40);
        detached.get(1).getOrCreateTag().putBoolean("ExternalMutation", true);
        assertComponents(helper, design.components(), List.of(
                stack("thaumcraft:plank_greatwood", 3), stack("thaumcraft:mechanism_simple", 2),
                stack("thaumcraft:mind_clockwork", 1)));
        helper.assertTrue(design.essentiaCost() == 8,
                "External stack/count/tag mutation poisoned future component or cost previews");
        boolean immutable = false;
        try { design.traits().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        helper.assertTrue(immutable && design.hasTrait(Trait.LIGHT), "Caller mutated cached trait definitions");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void golemDesignReadsBigEndianBytesAndRejectsMalformedOrInflatedCrafts(GameTestHelper helper) {
        long payload = 0x05040302010A0000L;
        var decoded = GolemDesign.parse(payload).orElseThrow();
        helper.assertTrue(decoded.material().id() == 5 && decoded.head().id() == 4 && decoded.arms().id() == 3
                        && decoded.legs().id() == 2 && decoded.addon().id() == 1 && decoded.rank() == 10
                        && decoded.toLong() == ByteBuffer.wrap(new byte[]{5, 4, 3, 2, 1, 10, 0, 0}).getLong(),
                "Properties no longer use the original big-endian byte order or preserve valid rank");
        int[] invalid = {6, 5, 5, 4, 4, 11, 1, 1};
        for (int index = 0; index < invalid.length; index++) {
            long bad = (long)invalid[index] << (56 - index * 8);
            helper.assertTrue(GolemDesign.parse(bad).isEmpty(), "Invalid property byte accepted at index " + index);
            if (index < 6)
                helper.assertTrue(GolemDesign.parse(255L << (56 - index * 8)).isEmpty(),
                        "Signed negative byte escaped bounded property validation at index " + index);
        }
        helper.assertTrue(GolemDesign.parse(-1L).isEmpty() && GolemDesign.parse(Long.MIN_VALUE).isEmpty()
                        && GolemDesign.create(-1, 0, 0, 0, 0).isEmpty()
                        && GolemDesign.create(0, 256, 0, 0, 0).isEmpty(),
                "Sentinel or oversized selector value wrapped into a valid default design");
        helper.assertTrue(!GolemDesign.parse(1L << 16).orElseThrow().canManufacture(completedBase()),
                "Fresh manufacture accepted a pre-ranked golem and invented free experience");
        for (int material = 0; material < 6; material++)
            for (int head = 0; head < 5; head++)
                for (int arms = 0; arms < 5; arms++)
                    for (int legs = 0; legs < 4; legs++)
                        for (int addon = 0; addon < 4; addon++) {
                            long props = ByteBuffer.wrap(new byte[]{(byte)material, (byte)head, (byte)arms,
                                    (byte)legs, (byte)addon, 0, 0, 0}).getLong();
                            helper.assertTrue(GolemDesign.parse(props).isPresent()
                                            && GolemDesign.create(material, head, arms, legs, addon).orElseThrow().props() == props,
                                    "Valid audited selector combination failed byte round trip");
                        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void golemDesignRetainsAllSixMaterialMetadataAndOriginalStatistics(GameTestHelper helper) {
        String[] bases = {"thaumcraft:plank_greatwood", "thaumcraft:plate_iron", "minecraft:terracotta",
                "thaumcraft:plate_brass", "thaumcraft:plate_thaumium", "thaumcraft:plate_void"};
        int[] health = {16, 30, 20, 26, 34, 30};
        int[] armor = {2, 8, 4, 6, 10, 6};
        int[] damage = {1, 3, 2, 3, 4, 4};
        int[] color = {5059370, 16777215, 13071447, 15638812, 5257074, 1445161};
        int[] cost = {8, 12, 8, 8, 12, 8};
        for (int material = 0; material < 6; material++) {
            var design = GolemDesign.create(material, 0, 0, 0, 0).orElseThrow();
            assertComponents(helper, design.components(), List.of(stack(bases[material], 3),
                    stack("thaumcraft:mechanism_simple", 2), stack("thaumcraft:mind_clockwork", 1)));
            helper.assertTrue(design.health() == health[material] && design.armor() == armor[material]
                            && design.material().damage() == damage[material] && design.attackDamage() == 0
                            && design.material().itemColor() == color[material] && design.essentiaCost() == cost[material],
                    "An original material profile or flattened metadata form changed: " + material);
        }
        helper.assertTrue(GolemDesign.choices(Category.MATERIAL).size() == 6
                        && GolemDesign.choices(Category.HEAD).size() == 5
                        && GolemDesign.choices(Category.ARMS).size() == 5
                        && GolemDesign.choices(Category.LEGS).size() == 4
                        && GolemDesign.choices(Category.ADDON).size() == 4,
                "The complete original reference selector arrays were truncated");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void golemDesignTraitCancellationAndFragileStatsFollowPinnedBytecode(GameTestHelper helper) {
        var fragile = GolemDesign.create(0, 1, 0, 0, 0).orElseThrow();
        helper.assertTrue(fragile.traits().equals(Set.of(Trait.LIGHT, Trait.SMART, Trait.FRAGILE))
                        && fragile.health() == 12 && fragile.armor() == 1,
                "Fragile stats copied the decompiler's integer-cast-zero error instead of multiply then truncate");
        var cancelled = GolemDesign.create(0, 1, 0, 0, 1).orElseThrow();
        helper.assertTrue(cancelled.traits().equals(Set.of(Trait.SMART)) && cancelled.health() == 16
                        && cancelled.armor() == 2 && cancelled.moveSpeed() == 1 && cancelled.essentiaCost() == 12,
                "Opposing Fragile/Armored or Light/Heavy traits used last-wins instead of original cancellation");
        var heavyArmor = GolemDesign.create(1, 0, 0, 0, 1).orElseThrow();
        helper.assertTrue(heavyArmor.hasTrait(Trait.ARMORED) && heavyArmor.hasTrait(Trait.HEAVY)
                        && heavyArmor.armor() == 12 && heavyArmor.health() == 30,
                "Unopposed armored iron lost its original 1.5 armor multiplier");
        var fighter = GolemDesign.create(1, 0, 2, 0, 0).orElseThrow();
        helper.assertTrue(fighter.attackDamage() == 4.5 && fighter.hasTrait(Trait.BRUTAL),
                "Original iron claw Fighter/Brutal damage changed");
        var ranked = GolemDesign.parse(fighter.props() | (10L << 16)).orElseThrow();
        helper.assertTrue(ranked.attackDamage() == 7 && ranked.health() == 40,
                "Read-only valid rank lost original health and quarter-damage scaling");
        helper.assertTrue(GolemDesign.create(0, 0, 3, 0, 0).orElseThrow().attackDamage() == 0,
                "Brutal without Fighter invented an actual combat attack");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void golemDesignMergesRepeatedPartsBeforeReservationAndKeepsDifferentPlateMetadata(GameTestHelper helper) {
        var brass = GolemDesign.create(3, 2, 1, 2, 1).orElseThrow();
        assertComponents(helper, brass.components(), List.of(stack("thaumcraft:plate_brass", 10),
                stack("thaumcraft:mechanism_simple", 4), stack("minecraft:flint", 4),
                stack("thaumcraft:mind_biothaumic", 1), stack("minecraft:white_wool", 1)));
        helper.assertTrue(brass.traits().equals(Set.of(Trait.SMART, Trait.DEFT, Trait.CLIMBER))
                        && brass.essentiaCost() == 26,
                "Repeated base/mechanism or direct brass plate was double-reserved or priced before exact merging");
        Set<ResourceLocation> ids = new HashSet<>();
        for (ItemStack component : brass.components())
            helper.assertTrue(ids.add(BuiltInRegistries.ITEM.getKey(component.getItem())),
                    "Equivalent component templates survived as multiple reservation rows");
        var iron = GolemDesign.create(1, 2, 0, 0, 0).orElseThrow();
        assertComponents(helper, iron.components(), List.of(stack("thaumcraft:plate_iron", 4),
                stack("thaumcraft:mechanism_simple", 2), stack("thaumcraft:mind_biothaumic", 1),
                stack("thaumcraft:plate_brass", 1), stack("minecraft:white_wool", 1)));
        var rollerHauler = GolemDesign.create(0, 0, 0, 1, 3).orElseThrow();
        assertComponents(helper, rollerHauler.components(), List.of(stack("thaumcraft:plank_greatwood", 2),
                stack("thaumcraft:mechanism_simple", 2), stack("minecraft:bowl", 2),
                stack("minecraft:leather", 2), stack("thaumcraft:mind_clockwork", 1), stack("minecraft:chest", 1)));
        helper.assertTrue(rollerHauler.essentiaCost() == 16
                        && Math.abs(rollerHauler.moveSpeed() - 1.45F) < .00001F,
                "Roller/Hauler lost merged two-leather cost or original wheeled speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void golemDesignRequiresStrictCanonicalPartsAndKeepsUnportedProfilesLocked(GameTestHelper helper) {
        var design = GolemDesign.parse(0).orElseThrow();
        for (int mindStage : new int[]{0, 1, 2, 3}) {
            var knowledge = knowledge(Map.of("MATSTUDWOOD", 2, "MINDCLOCKWORK", mindStage));
            helper.assertTrue(!design.canManufacture(knowledge) && GolemDesign.choices(Category.HEAD, knowledge).isEmpty()
                            && GolemDesign.choices(Category.ARMS, knowledge).isEmpty()
                            && GolemDesign.choices(Category.LEGS, knowledge).isEmpty()
                            && GolemDesign.choices(Category.ADDON, knowledge).isEmpty(),
                    "Started or partial Mind bypassed original strict manufacture choice gates at stage " + mindStage);
        }
        var completed = completedBase();
        helper.assertTrue(design.canManufacture(completed) && design.accessible(completed)
                        && GolemDesign.choices(Category.MATERIAL, completed).size() == 1
                        && GolemDesign.choices(Category.HEAD, completed).size() == 1
                        && GolemDesign.choices(Category.ARMS, completed).size() == 1
                        && GolemDesign.choices(Category.LEGS, completed).size() == 2
                        && GolemDesign.choices(Category.ADDON, completed).size() == 2,
                "Explicit completed-Mind fixture failed original wood/basic/walker/roller/none/hauler choices");
        helper.assertTrue(!design.canManufacture(knowledge(Map.of("MINDCLOCKWORK", 4))),
                "Completed Mind bypassed the wooden material's separate canonical completion");
        CompoundTag allStages = new CompoundTag();
        for (Category category : Category.values())
            for (var part : GolemDesign.choices(category))
                for (String research : part.research())
                    allStages.putInt(research, ResearchCatalog.get(research).stages().size() + 1);
        CompoundTag forged = new CompoundTag();
        forged.putInt("Version", 2);
        forged.put("ResearchStages", allStages);
        var forgedLate = PlayerKnowledge.load(forged);
        helper.assertTrue(GolemDesign.create(1, 0, 0, 0, 0).orElseThrow().canManufacture(forgedLate)
                        && GolemDesign.create(0, 1, 0, 0, 0).orElseThrow().canManufacture(forgedLate)
                        && GolemDesign.create(0, 0, 2, 0, 0).orElseThrow().canManufacture(forgedLate)
                        && !GolemDesign.create(5, 0, 0, 0, 0).orElseThrow().canManufacture(forgedLate)
                        && GolemDesign.create(0, 0, 0, 3, 0).orElseThrow().canManufacture(forgedLate),
                "Completed supported parts/Flyer or the separate Void support gate changed");
        CompoundTag aliases = new CompoundTag();
        aliases.putInt("Version", 2);
        ListTag research = new ListTag();
        for (String alias : List.of("PORT_BRASS", "PORT_THAUMIUM", "MINDCLOCKWORK", "MATSTUDWOOD"))
            research.add(StringTag.valueOf(alias));
        aliases.put("Research", research);
        helper.assertTrue(!design.canManufacture(PlayerKnowledge.load(aliases)),
                "Legacy aliases or bare discovery markers replaced completed canonical part research");
        helper.succeed();
    }

    private static PlayerKnowledge completedBase() {
        return knowledge(Map.of("MATSTUDWOOD", 2, "MINDCLOCKWORK", 4));
    }
    private static PlayerKnowledge knowledge(Map<String, Integer> stages) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        CompoundTag serializedStages = new CompoundTag();
        stages.forEach(serializedStages::putInt);
        root.put("ResearchStages", serializedStages);
        return PlayerKnowledge.load(root);
    }
    private static ItemStack stack(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count);
    }
    private static void assertComponents(GameTestHelper helper, List<ItemStack> actual, List<ItemStack> expected) {
        helper.assertTrue(actual.size() == expected.size(), "Original component order/merge row count changed");
        for (int index = 0; index < expected.size(); index++) {
            ItemStack a = actual.get(index), e = expected.get(index);
            helper.assertTrue(!a.isEmpty() && ItemStack.isSameItemSameTags(a, e) && a.getCount() == e.getCount(),
                    "Original exact merged component differs at row " + index + ": " + a + " / " + e);
        }
    }
}
