package thaumcraft.research.theory;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.catalog.entities.VisualEntitiesModule;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchCatalog;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Original aid identities, search volumes, finite pools, and the missing empty-phial recipe. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class FullTheoryAidGameTests {
    @GameTest(template = "theory_aids")
    public static void allTwelveBlockAidsRespectTheirVolumeAndOriginalDormantDefinition(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(6, 3, 6));
        Set<String> expected = new LinkedHashSet<>();
        int index = 0;
        for (String aid : TheoryAids.keys()) {
            var block = TheoryAids.block(aid);
            if (block == null) continue;
            BlockPos pos = table.offset(index % 9 - 4, index / 9 - 1, index % 2 == 0 ? -4 : 4);
            // No adjacent-device or beacon power validation belongs to research assistance.
            level.setBlock(pos, block.defaultBlockState(), 2);
            expected.add(aid);
            for (var state : block.getStateDefinition().getPossibleStates())
                helper.assertTrue(TheoryAids.matches(aid, state), "Aid acquired a state/metadata condition: " + aid);
            index++;
        }
        helper.assertTrue(expected.size() == 12 && TheoryAids.find(level, table).equals(expected),
                "Not all twelve original block aids were recognized independently of device operation");
        helper.assertTrue(TheoryAids.keys().size() == 14 && TheoryAids.block(TheoryAids.BASIC_ELDRITCH) == null
                        && !TheoryAids.find(level, table).contains(TheoryAids.BASIC_ELDRITCH),
                "The uninitialized BETA26 Eldritch aid was mapped onto an invented block");
        level.setBlock(table.offset(4, 1, 4), Blocks.BOOKSHELF.defaultBlockState(), 2);
        helper.assertTrue(TheoryAids.find(level, table).equals(expected), "Duplicate aid increased the assistance set");
        for (BlockPos pos : BlockPos.betweenClosed(table.offset(-4, -1, -4), table.offset(4, 1, 4)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(table.offset(4, 1, 4), Blocks.BOOKSHELF.defaultBlockState(), 2);
        level.setBlock(table.offset(-4, -1, -4), Blocks.BEACON.defaultBlockState(), 2);
        level.setBlock(table.offset(5, 0, 0), Blocks.ENCHANTING_TABLE.defaultBlockState(), 2);
        level.setBlock(table.offset(0, 2, 0), TheoryAids.block(TheoryAids.BASIC_INFUSION).defaultBlockState(), 2);
        helper.assertTrue(TheoryAids.find(level, table).equals(Set.of(TheoryAids.BOOKSHELF, TheoryAids.BEACON)),
                "Inclusive corners were missed or x=5/y=2 blocks entered the original search box");
        helper.succeed();
    }

    @GameTest(template = "theory_aids")
    public static void crimsonAidUsesExactEntityTypeAndFiveBlockAabbIntersection(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(6, 3, 6));
        Vec3 centre = Vec3.atCenterOf(table);
        var greater = VisualEntitiesModule.LIVING.get("cultist_portal_greater").get().create(level);
        var unrelated = VisualEntitiesModule.LIVING.get("eldritch_guardian").get().create(level);
        greater.setPos(centre.x, centre.y, centre.z); unrelated.setPos(centre.x, centre.y, centre.z);
        level.addFreshEntity(greater); level.addFreshEntity(unrelated);
        helper.assertTrue(!TheoryAids.matches(TheoryAids.PORTAL_CRIMSON, greater)
                        && !TheoryAids.matches(TheoryAids.PORTAL_CRIMSON, unrelated)
                        && !TheoryAids.find(level, table).contains(TheoryAids.PORTAL_CRIMSON),
                "Generic catalogue classes let the greater portal or unrelated mob qualify");
        var lesser = VisualEntitiesModule.LIVING.get("cultist_portal_lesser").get().create(level);
        lesser.setPos(centre.x + 4.9, centre.y, centre.z + 4.9);
        level.addFreshEntity(lesser);
        helper.assertTrue(lesser.distanceToSqr(centre) > 25 && TheoryAids.find(level, table).contains(TheoryAids.PORTAL_CRIMSON),
                "Aid entity range became a sphere; BETA26 accepts this AABB corner");
        lesser.setPos(centre.x + 5.7, centre.y, centre.z);
        helper.assertTrue(TheoryAids.find(level, table).contains(TheoryAids.PORTAL_CRIMSON),
                "Aid detection checked the entity centre instead of its box intersection");
        lesser.setPos(centre.x + 5.76, centre.y, centre.z);
        helper.assertTrue(!TheoryAids.find(level, table).contains(TheoryAids.PORTAL_CRIMSON),
                "The lesser portal's box beyond the five-block AABB still qualified");
        lesser.discard(); greater.discard(); unrelated.discard();
        helper.succeed();
    }

    @GameTest(template = "theory_aids")
    public static void aidDetectionDoesNotGenerateOrLoadDistantChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos distant = new BlockPos(2_000_000, 80, 2_000_000);
        helper.assertTrue(!level.hasChunkAt(distant), "Distant aid-search fixture unexpectedly has a loaded chunk");
        helper.assertTrue(TheoryAids.find(level, distant).isEmpty(), "An unloaded distant aid search produced assistance");
        for (BlockPos pos : BlockPos.betweenClosed(distant.offset(-4, -1, -4), distant.offset(4, 1, 4)))
            helper.assertTrue(!level.hasChunkAt(pos), "Aid search force-loaded a distant chunk");
        helper.succeed();
    }

    @GameTest(template = "theory_aids")
    public static void allReachableAidPoolsPayThirteenInspirationAndSurviveSavingWithExactWeights(GameTestHelper helper) {
        CompoundTag knowledgeTag = new PlayerKnowledge().save();
        CompoundTag stages = new CompoundTag();
        for (var entry : ResearchCatalog.entries()) if (!entry.supported()) stages.putInt(entry.key(), entry.stages().size() + 1);
        knowledgeTag.put("ResearchStages", stages);
        PlayerKnowledge knowledge = PlayerKnowledge.load(knowledgeTag);
        Set<String> reachable = new LinkedHashSet<>(TheoryAids.keys());
        reachable.remove(TheoryAids.BASIC_ELDRITCH);
        TheorySession session = TheorySession.create(UUID.randomUUID(), knowledge, reachable, RandomSource.create(26));
        helper.assertTrue(session.inspirationStart() == 14 && session.inspiration() == 1,
                "Thirteen distinct aid types did not each cost one original inspiration");
        var pool = session.save().getList("AidCards", Tag.TAG_STRING);
        Map<String, Long> expectedRepeated = Map.of("study", 3L, "notation", 2L, "portal", 3L);
        helper.assertTrue(pool.size() == 28, "All thirteen reachable assistance pools should contain 28 finite tokens");
        for (var entry : expectedRepeated.entrySet()) {
            long count = pool.stream().filter(tag -> entry.getKey().equals(tag.getAsString())).count();
            helper.assertTrue(count == entry.getValue(), "Original assistance weight changed for " + entry.getKey());
        }
        TheorySession restored = TheorySession.load(session.save());
        helper.assertTrue(restored != null && restored.save().equals(session.save()), "New aid selection/pool did not survive NBT reload");
        helper.assertTrue(TheoryAids.cards(TheoryAids.BASIC_ELDRITCH).equals(java.util.List.of("realization", "revelation", "truth"))
                        && !TheoryCard.ids().contains("truth") && !TheoryCard.ids().contains("dragon_egg")
                        && TheoryCard.initialize("truth", 1, true, session, knowledge) == null,
                "Unregistered Truth or DragonEgg became an active BETA26 card");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyPhialScribingToolsCraftThroughVanillaMenuAndRejectFilledPhial(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "phial_scribe"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        level.setBlock(pos, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        CraftingMenu menu = new CraftingMenu(61, player.getInventory(), ContainerLevelAccess.create(level, pos));
        player.containerMenu = menu;
        menu.getSlot(1).set(CatalogModule.aspectStack("phial_filled", Aspect.FIRE, 10));
        menu.getSlot(2).set(new ItemStack(Items.FEATHER));
        menu.getSlot(3).set(new ItemStack(Items.BLACK_DYE));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Filled phial replaced the original metadata-zero empty phial ingredient");
        menu.getSlot(1).set(CatalogModule.stack("phial_empty"));
        ItemStack preview = menu.getSlot(0).getItem();
        helper.assertTrue(preview.is(TheoryModule.SCRIBING_TOOLS.get()) && preview.getDamageValue() == 0
                        && preview.getMaxDamage() == 100 && !KnowledgeStore.get(player).hasCraft("thaumcraft:scribing_tools"),
                "Empty-phial recipe preview failed or credited the research craft before pickup");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(TheoryModule.SCRIBING_TOOLS.get())
                        && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty()
                        && KnowledgeStore.get(player).hasCraft("thaumcraft:scribing_tools"),
                "Empty phial, feather and ink did not commit exactly one new scribing tool/craft proof");
        helper.succeed();
    }
}
