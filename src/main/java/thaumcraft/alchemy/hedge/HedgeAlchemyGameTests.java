package thaumcraft.alchemy.hedge;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.CrucibleBlockEntity;
import thaumcraft.alchemy.CrucibleRecipes;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.scanning.AspectRegistry;

import java.util.List;
import java.util.UUID;

/** Real paid manufacture for all three recipe tiers; fixtures enter stages without completing research. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class HedgeAlchemyGameTests {
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);
    private static final String[] COLORS = {"white", "orange", "magenta", "lightblue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final Item[] DYES = {Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE, Items.LIGHT_BLUE_DYE, Items.YELLOW_DYE,
            Items.LIME_DYE, Items.PINK_DYE, Items.GRAY_DYE, Items.LIGHT_GRAY_DYE, Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
            Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE, Items.BLACK_DYE};
    private HedgeAlchemyGameTests() {}

    @GameTest(template = "empty")
    public static void tenOriginalRecipeCostsPreservePostAspectsAndPositiveDifferences(GameTestHelper helper) {
        String[] ids = {"tallow", "leather", "hedge_gunpowder", "hedge_slime", "hedge_glowstone", "hedge_dye", "hedge_clay", "hedge_string", "hedge_web", "hedge_lava"};
        AspectList[] costs = {cost(Aspect.FIRE, 1), cost(Aspect.AIR, 3, Aspect.BEAST, 3),
                cost(Aspect.FIRE, 10, Aspect.ENTROPY, 10, Aspect.ALCHEMY, 5), cost(Aspect.WATER, 5, Aspect.LIFE, 5, Aspect.ALCHEMY, 1),
                cost(Aspect.SENSES, 5, Aspect.LIGHT, 10), cost(Aspect.WATER, 2, Aspect.BEAST, 2), cost(Aspect.WATER, 5),
                cost(Aspect.BEAST, 5, Aspect.CRAFT, 1), cost(Aspect.TRAP, 5), cost(Aspect.FIRE, 15, Aspect.EARTH, 5)};
        for (int index = 0; index < ids.length; index++) {
            var recipe = recipe(ids[index]);
            sameAspects(helper, recipe.cost(), costs[index], "Pinned cost changed for " + ids[index]);
            helper.assertTrue(recipe.research().equals("HEDGEALCHEMY@" + (index < 2 ? 1 : index < 6 ? 2 : 3)), "Recipe stage was weakened: " + ids[index]);
            helper.assertTrue((recipe.aspectCost() != null) == (index >= 2 && index < 9), "postAspects formula missing or invented: " + ids[index]);
        }
        // The complete output/catalyst lists deliberately have unrelated aspects and smaller entries.
        var string = recipe("hedge_string");
        sameAspects(helper, string.cost(), AspectRegistry.getAspects(new ItemStack(Items.STRING))
                .remove(AspectRegistry.getAspects(new ItemStack(Items.WHEAT))), "String must subtract wheat, not add it");
        sameAspects(helper, recipe("hedge_web").cost(), AspectRegistry.getAspects(new ItemStack(Items.COBWEB))
                .remove(AspectRegistry.getAspects(new ItemStack(Items.STRING))), "Web must subtract the entire string list");
        var reverse = new CrucibleRecipes.AspectCost(new ItemStack(Items.WHEAT), new ItemStack(Items.STRING));
        sameAspects(helper, reverse.resolve(), cost(Aspect.PLANT, 5, Aspect.LIFE, 5), "Absent catalyst aspects produced negative payments");
        var returned = string.cost(); returned.add(Aspect.FIRE, 200);
        sameAspects(helper, string.cost(), costs[7], "A caller mutated a registered dynamic recipe cost");
        helper.assertTrue(!recipe("hedge_dye").matchesCatalyst(new ItemStack(Items.BLACK_DYE))
                && recipe("hedge_dye").matchesCatalyst(new ItemStack(Items.INK_SAC)), "Original dye metadata0 became arbitrary black dye");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fleshChoosesHigherCostLeatherThenPaysSeparateTallowWithoutCompletingResearch(GameTestHelper helper) {
        var player = player(helper); stage(player, 1);
        var crucible = prepare(helper, cost(Aspect.FIRE, 2, Aspect.AIR, 3, Aspect.BEAST, 3, Aspect.EARTH, 7), 1000);
        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 3);
        helper.assertTrue(CrucibleRecipes.find(flesh, crucible.aspects(), player).id().getPath().equals("leather"), "One-ignis tallow displaced higher-cost leather");
        helper.assertTrue(crucible.consume(flesh, player), "Canonical entered stage1 did not permit leather");
        takeOutput(helper, crucible, Items.LEATHER, 1);
        helper.assertTrue(flesh.getCount() == 2 && crucible.water() == 950, "Leather paid a wrong flesh/water count");
        sameAspects(helper, crucible.aspects(), cost(Aspect.FIRE, 2, Aspect.EARTH, 7), "Leather charged unrelated ignis/terra");
        helper.assertTrue(crucible.consume(flesh, player), "Paid remaining mixture did not allow tallow");
        takeOutput(helper, crucible, item("tallow"), 1);
        sameAspects(helper, crucible.aspects(), cost(Aspect.FIRE, 1, Aspect.EARTH, 7), "Tallow charged more than one ignis");
        helper.assertTrue(flesh.getCount() == 1 && crucible.water() == 900 && KnowledgeStore.get(player).researchStage("HEDGEALCHEMY") == 1
                && KnowledgeStore.get(player).hasCraft("thaumcraft:tallow") && KnowledgeStore.get(player).hasCraft("minecraft:leather"),
                "Committed stage1 crafts omitted proof, paid too much, or completed the canonical entry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldTallowProfileRetainsOnlyTierOneAndCannotBypassNewCanonicalStages(GameTestHelper helper) {
        var player = player(helper);
        KnowledgeStore.recordFact(player, "PORT_TALLOW");
        helper.assertTrue(KnowledgeStore.get(player).knowsResearch("HEDGEALCHEMY@1")
                && !KnowledgeStore.get(player).knowsResearch("HEDGEALCHEMY@2") && !KnowledgeStore.get(player).isResearchCompleteStrict("HEDGEALCHEMY"),
                "Old lesson compatibility completed research or unlocked duplication");
        var crucible = prepare(helper, cost(Aspect.FIRE, 1), 1000);
        helper.assertTrue(crucible.consume(new ItemStack(Items.ROTTEN_FLESH), player), "Existing tallow profiles lost their recipe");
        takeOutput(helper, crucible, item("tallow"), 1);
        helper.assertTrue(KnowledgeStore.get(player).researchStage("HEDGEALCHEMY") == 0, "Compatibility set a canonical stage");
        var gunpowder = recipe("hedge_gunpowder");
        helper.assertTrue(!gunpowder.matches(new ItemStack(Items.GUNPOWDER), gunpowder.cost(), player), "Old lesson unlocked stage2 duplication");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allFourDuplicationsConsumeOneCatalystAndExactFullItemAspects(GameTestHelper helper) {
        var player = player(helper); stage(player, 2);
        String[] recipes = {"hedge_gunpowder", "hedge_slime", "hedge_glowstone", "hedge_dye"};
        Item[] items = {Items.GUNPOWDER, Items.SLIME_BALL, Items.GLOWSTONE_DUST, Items.INK_SAC};
        for (int index = 0; index < recipes.length; index++) {
            var recipe = recipe(recipes[index]);
            AspectList input = recipe.cost().add(Aspect.ORDER, 9);
            var crucible = prepare(helper, input, 1000);
            ItemStack catalyst = new ItemStack(items[index], 2); catalyst.getOrCreateTag().putString("keep", "remaining catalyst");
            helper.assertTrue(!KnowledgeStore.get(player).hasCraft("minecraft:" + BuiltInRegistries.ITEM.getKey(items[index]).getPath()), "Fixture awarded craft proof before manufacture");
            helper.assertTrue(crucible.consume(catalyst, player), "Paid duplication failed: " + recipes[index]);
            ItemStack result = takeOutput(helper, crucible, items[index], 2);
            sameAspects(helper, crucible.aspects(), cost(Aspect.ORDER, 9), "Duplication charged a wrong aspect: " + recipes[index]);
            helper.assertTrue(catalyst.getCount() == 1 && catalyst.getTag().getString("keep").equals("remaining catalyst")
                    && !result.hasTag() && crucible.water() == 950 && KnowledgeStore.get(player).hasCraft(BuiltInRegistries.ITEM.getKey(items[index]).toString()),
                    "Duplication changed NBT, paid a wrong catalyst/water count or omitted committed craft proof");
        }
        helper.assertTrue(KnowledgeStore.get(player).researchStage("HEDGEALCHEMY") == 2, "Duplication itself advanced research");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clayAndWheatStringWebChainPaysOnlyPositiveAspectDifferences(GameTestHelper helper) {
        var player = player(helper); stage(player, 3);
        var crucible = prepare(helper, cost(Aspect.WATER, 5, Aspect.ORDER, 2), 1000);
        var dirt = new ItemStack(Items.DIRT, 2);
        helper.assertTrue(crucible.consume(dirt, player), "Five-aqua dirt-to-clay transformation failed");
        takeOutput(helper, crucible, Items.CLAY_BALL, 1);
        sameAspects(helper, crucible.aspects(), cost(Aspect.ORDER, 2), "Clay also charged dirt's terra");
        helper.assertTrue(dirt.getCount() == 1 && crucible.water() == 950, "Clay transformation paid wrong counts");
        crucible = prepare(helper, cost(Aspect.BEAST, 5, Aspect.CRAFT, 1, Aspect.ORDER, 2), 1000);
        var wheat = new ItemStack(Items.WHEAT, 2);
        helper.assertTrue(crucible.consume(wheat, player), "Wheat-to-string transformation demanded removed herba/victus");
        ItemStack actualString = takeOutput(helper, crucible, Items.STRING, 1);
        sameAspects(helper, crucible.aspects(), cost(Aspect.ORDER, 2), "String transformation paid wrong aspects");
        helper.assertTrue(wheat.getCount() == 1 && crucible.water() == 950, "String transformation paid wrong counts");
        crucible = prepare(helper, cost(Aspect.TRAP, 5, Aspect.ORDER, 2), 1000);
        helper.assertTrue(crucible.consume(actualString, player), "Actual crafted string did not make cobweb for five vinculum");
        takeOutput(helper, crucible, Items.COBWEB, 1);
        sameAspects(helper, crucible.aspects(), cost(Aspect.ORDER, 2), "Cobweb added/charged string's removed beast/craft aspects");
        helper.assertTrue(actualString.isEmpty() && crucible.water() == 950 && KnowledgeStore.get(player).hasCraft("minecraft:clay_ball")
                && KnowledgeStore.get(player).hasCraft("minecraft:string") && KnowledgeStore.get(player).hasCraft("minecraft:cobweb"),
                "Transformations omitted paid physical input or committed modern-ID craft facts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaUsesOneEmptyBucketFifteenIgnisFiveTerraAndLastMillibucket(GameTestHelper helper) {
        var player = player(helper); stage(player, 3);
        var crucible = prepare(helper, cost(Aspect.FIRE, 15, Aspect.EARTH, 5, Aspect.WATER, 2), 1);
        var buckets = new ItemStack(Items.BUCKET, 2);
        helper.assertTrue(crucible.consume(buckets, player), "BETA26 last-water lava craft was denied");
        var lava = takeOutput(helper, crucible, Items.LAVA_BUCKET, 1);
        sameAspects(helper, crucible.aspects(), cost(Aspect.WATER, 2), "Lava changed original fixed ignis/terra payment");
        helper.assertTrue(lava.getCount() == 1 && buckets.getCount() == 1 && crucible.water() == 0 && outputs(helper, crucible).isEmpty()
                && KnowledgeStore.get(player).hasCraft("minecraft:lava_bucket"), "Lava created an extra empty bucket/output or omitted the real craft fact");
        var before = crucible.saveWithoutMetadata();
        helper.assertTrue(!crucible.consume(buckets, player) && before.equals(crucible.saveWithoutMetadata()) && buckets.getCount() == 1,
                "Waterless crucible accepted or partially paid a second catalyst");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedOrUnderfundedDuplicationDissolvesCatalystWithoutOutputWaterDebitOrProof(GameTestHelper helper) {
        var recipe = recipe("hedge_gunpowder");
        for (boolean locked : new boolean[]{true, false}) {
            var player = player(helper); if (!locked) stage(player, 2);
            AspectList mixture = cost(Aspect.FIRE, 10, Aspect.ENTROPY, locked ? 10 : 9, Aspect.ALCHEMY, 5);
            var crucible = prepare(helper, mixture, 1000); ItemStack catalyst = new ItemStack(Items.GUNPOWDER, 2);
            helper.assertTrue(!recipe.matches(catalyst, mixture, player), "Locked/underfunded duplication matched");
            helper.assertTrue(crucible.consume(catalyst, player), "Rejected recipe must retain original ordinary dissolution behavior");
            sameAspects(helper, crucible.aspects(), mixture.copy().add(AspectRegistry.getAspects(new ItemStack(Items.GUNPOWDER))),
                    "Failed recipe charged manufactured-output aspects instead of dissolving exactly one input");
            helper.assertTrue(catalyst.getCount() == 1 && crucible.water() == 1000 && outputs(helper, crucible).isEmpty()
                    && !KnowledgeStore.get(player).hasCraft("minecraft:gunpowder"), "Rejected recipe manufactured output, debited water or awarded proof");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void candlesUseRealUngatedCraftingMenuWithThreeOutputsAndAllSixteenBySixteenDyeRoutes(GameTestHelper helper) {
        var player = player(helper); var pos = helper.absolutePos(CENTER);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        var menu = new CraftingMenu(41, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        menu.getSlot(1).set(new ItemStack(Items.STRING, 2)); menu.getSlot(4).set(new ItemStack(item("tallow"), 2));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "One tallow made an invented two-item candle recipe");
        menu.getSlot(7).set(new ItemStack(item("tallow"), 2));
        helper.assertTrue(menu.getSlot(0).getItem().is(candle("white").asItem()) && menu.getSlot(0).getItem().getCount() == 3
                && !KnowledgeStore.get(player).hasCraft("thaumcraft:candle_white"), "Original three-candle preview or proof changed");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(candle("white").asItem()) && menu.getCarried().getCount() == 3
                && menu.getSlot(1).getItem().getCount() == 1 && menu.getSlot(4).getItem().getCount() == 1 && menu.getSlot(7).getItem().getCount() == 1
                && KnowledgeStore.get(player).hasCraft("thaumcraft:candle_white") && KnowledgeStore.get(player).researchStage("HEDGEALCHEMY") == 0,
                "Ordinary candle manufacture did not pay string/two tallow or gained an invented research gate");
        clear(menu);
        for (int dye = 0; dye < COLORS.length; dye++) for (String source : COLORS) {
            menu.getSlot(2).set(new ItemStack(DYES[dye], 2)); menu.getSlot(8).set(new ItemStack(candle(source), 2));
            helper.assertTrue(menu.getSlot(0).getItem().is(candle(COLORS[dye]).asItem()), "Any-color candle ingredient failed: " + source + " -> " + COLORS[dye]);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(candle(COLORS[dye]).asItem()) && menu.getCarried().getCount() == 1
                    && menu.getSlot(2).getItem().getCount() == 1 && menu.getSlot(8).getItem().getCount() == 1,
                    "Candle recolor did not pay exactly one dye/candle: " + source + " -> " + COLORS[dye]);
            clear(menu);
        }
        Item[] legacyDyes = {Items.BONE_MEAL, Items.LAPIS_LAZULI, Items.COCOA_BEANS, Items.INK_SAC};
        String[] legacyColors = {"white", "blue", "brown", "black"};
        for (int index = 0; index < legacyDyes.length; index++) {
            menu.getSlot(1).set(new ItemStack(legacyDyes[index])); menu.getSlot(9).set(new ItemStack(candle("red")));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(candle(legacyColors[index]).asItem()) && !menu.getSlot(1).hasItem() && !menu.getSlot(9).hasItem(),
                    "Original dye item lost its paid candle route: " + legacyDyes[index]); clear(menu);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allColoredCandlesRetainOriginalLightOutlineNoCollisionAndOneDropOnLostSupport(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(CENTER);
        for (String color : COLORS) {
            Block candle = candle(color); var state = candle.defaultBlockState();
            helper.assertTrue(candle instanceof TallowCandleBlock && state.getLightEmission(level, pos) == 14
                    && Math.abs(state.getDestroySpeed(level, pos) - .1F) < .0001F && candle.getExplosionResistance() == 1.5F && !state.requiresCorrectToolForDrops()
                    && state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty(), "Original candle physical API changed: " + color);
            helper.assertTrue(candle.asItem().getClass() == BlockItem.class, "Working candle retained a visual-only catalogue item: " + color);
            AABB bounds = state.getShape(level, pos).bounds();
            helper.assertTrue(bounds.equals(new AABB(.375, 0, .375, .625, .5, .625)), "Candle selection outline changed: " + color);
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(state.canSurvive(level, pos), "Candle rejected a solid upper face");
            level.setBlockAndUpdate(pos, state);
            level.removeBlock(pos.below(), false);
            helper.assertTrue(level.getBlockState(pos).isAir(), "Candle floated after losing support: " + color);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.5));
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(candle.asItem()) && drops.get(0).getItem().getCount() == 1,
                    "Support loss failed the exact single candle drop: " + color);
            drops.get(0).discard();
            helper.assertTrue(!state.canSurvive(level, pos), "Air supplied an invented candle support");
        }
        helper.succeed();
    }

    private static void clear(CraftingMenu menu) { menu.setCarried(ItemStack.EMPTY); for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(ItemStack.EMPTY); }
    private static Block candle(String color) { return CatalogBlocks.block("candle_" + color); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft", id)); }
    private static CrucibleRecipes.Entry recipe(String id) { return CrucibleRecipes.all().stream().filter(entry -> entry.id().getPath().equals(id)).findFirst().orElseThrow(); }
    private static ServerPlayer player(GameTestHelper helper) { return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hedge_alchemy")); }
    private static void stage(ServerPlayer player, int stage) {
        try { var method = PlayerKnowledge.class.getDeclaredMethod("setResearchStage", String.class, int.class); method.setAccessible(true); method.invoke(KnowledgeStore.get(player), "HEDGEALCHEMY", stage); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static CrucibleBlockEntity prepare(GameTestHelper helper, AspectList mixture, int water) {
        var level = helper.getLevel(); var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) level.getBlockEntity(pos);
        CompoundTag tag = new CompoundTag(); tag.putInt("Water", water); tag.putInt("Heat", 200); tag.putInt("Idle", -250); mixture.writeToNBT(tag); crucible.load(tag);
        return crucible;
    }
    private static List<ItemEntity> outputs(GameTestHelper helper, CrucibleBlockEntity crucible) {
        var pos = crucible.getBlockPos();
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos.getX() + .35, pos.getY() + 1, pos.getZ() + .35,
                pos.getX() + .65, pos.getY() + 1.4, pos.getZ() + .65));
    }
    private static ItemStack takeOutput(GameTestHelper helper, CrucibleBlockEntity crucible, Item item, int count) {
        var outputs = outputs(helper, crucible);
        helper.assertTrue(outputs.size() == 1 && outputs.get(0).getItem().is(item) && outputs.get(0).getItem().getCount() == count
                && outputs.get(0).getPersistentData().getBoolean("thaumcraft_crucible_output"), "Expected one protected physical output: " + item + " x" + count);
        ItemStack result = outputs.get(0).getItem().copy(); outputs.get(0).discard(); return result;
    }
    private static AspectList cost(Object... entries) { AspectList result = new AspectList(); for (int index = 0; index < entries.length; index += 2) result.add((Aspect) entries[index], (Integer) entries[index + 1]); return result; }
    private static void sameAspects(GameTestHelper helper, AspectList actual, AspectList expected, String message) { helper.assertTrue(actual.aspects.equals(expected.aspects), message + ": " + actual.aspects + " != " + expected.aspects); }
}
