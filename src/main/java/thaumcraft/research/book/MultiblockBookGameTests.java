package thaumcraft.research.book;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.research.ResearchCatalog;

import java.util.Map;
import java.util.Set;

/** Canonical layout, metadata and material checks, entirely detached from the test world. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class MultiblockBookGameTests {
    private MultiblockBookGameTests() { }
    private static MultiblockCatalog.Blueprint blueprint(String path) { return MultiblockCatalog.resolve(path).orElseThrow(); }
    private static String block(MultiblockPreview preview, int x, int y, int z) {
        return BuiltInRegistries.BLOCK.getKey(preview.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
    }
    @GameTest(template = "empty") public static void bookContainsAllSixCanonicalCompoundLinks(GameTestHelper helper) {
        Set<String> expected = Set.of("infernalfurnace", "infusionaltar", "infusionaltarancient", "infusionaltareldritch", "thaumatorium", "golempress");
        helper.assertTrue(MultiblockCatalog.all().size() == 6, "BETA26 BluePrint registration count changed");
        for (var row : MultiblockCatalog.all()) {
            helper.assertTrue(expected.contains(row.id().getPath()), "Non-TC6 compound construction added");
            var entry = ResearchCatalog.get(row.research());
            helper.assertTrue(entry != null && entry.stages().stream().flatMap(stage -> stage.recipes().stream())
                    .anyMatch(link -> MultiblockCatalog.resolve(link).filter(value -> value == row).isPresent()),
                    "Construction no longer resolves from its canonical book stage: " + row.id());
            helper.assertTrue(!row.mechanicsImplemented(), "Blueprint viewing falsely enables an unported DustTrigger");
        }
        helper.assertTrue(blueprint("thaumcraft:Thaumatorium").id().getPath().equals("thaumatorium")
                        && blueprint("thaumcraft:GolemPress").id().getPath().equals("golempress"), "Original mixed-case book links lost");
        helper.assertTrue(MultiblockCatalog.resolve("thaumcraft:crucible").isEmpty(), "Ordinary device was invented as a compound registration");
        helper.succeed();
    }
    @GameTest(template = "empty") public static void infernalFurnacePreservesInternalLavaOpeningAndBars(GameTestHelper helper) {
        var row = blueprint("infernalfurnace"); var preview = row.preview(0);
        helper.assertTrue(row.width() == 3 && row.height() == 3 && row.depth() == 3 && preview.states().size() == 26,
                "Infernal furnace release dimensions or source count changed");
        helper.assertTrue(block(preview, 1, 0, 1).equals("minecraft:obsidian") && block(preview, 1, 1, 1).equals("minecraft:lava")
                && preview.getBlockState(new BlockPos(1, 2, 1)).isAir(), "Top-to-bottom BETA26 source layers were reversed incorrectly");
        var bars = preview.getBlockState(new BlockPos(2, 1, 1));
        helper.assertTrue(bars.is(Blocks.IRON_BARS) && bars.getValue(BlockStateProperties.NORTH)
                && bars.getValue(BlockStateProperties.SOUTH) && !bars.getValue(BlockStateProperties.EAST)
                && !bars.getValue(BlockStateProperties.WEST), "Bars consult world neighbours or lose the source opening");
        helper.assertTrue(preview.getFluidState(new BlockPos(1,1,1)).getType() == net.minecraft.world.level.material.Fluids.LAVA,
                "Material.LAVA source cannot be displayed by the modern preview");
        var counts = row.materials().stream().collect(java.util.stream.Collectors.toMap(material -> material.item().toString(), MultiblockCatalog.Material::count));
        helper.assertTrue(counts.equals(Map.of("minecraft:nether_bricks",12,"minecraft:obsidian",12,"minecraft:iron_bars",1,"minecraft:lava_bucket",1)),
                "Release book materials changed"); helper.succeed();
    }
    @GameTest(template = "empty") public static void threeAltarsPreserveStoneVariantsAndOriginalPedestalCharges(GameTestHelper helper) {
        String[] ids = {"infusionaltar", "infusionaltarancient", "infusionaltareldritch"};
        String[] kinds = {"arcane", "ancient", "eldritch"};
        int[] charge = {0,2,1};
        for (int i = 0; i < ids.length; i++) {
            var row = blueprint(ids[i]); var source = row.preview(0);
            String stone = "thaumcraft:stone_" + kinds[i] + (i == 2 ? "_tile" : "");
            helper.assertTrue(source.states().size() == 10 && block(source, 1, 2, 1).equals("thaumcraft:infusion_matrix")
                    && block(source, 1, 0, 1).equals("thaumcraft:pedestal_" + kinds[i]), "Wrong altar center or matrix height");
            for (int x : new int[]{0,2}) for (int z : new int[]{0,2}) for (int y : new int[]{0,1})
                helper.assertTrue(block(source,x,y,z).equals(stone), "Altar pillars require two original stones, not prebuilt pillar inputs");
            var pedestal = source.getBlockState(new BlockPos(1,0,1));
            var chargeProperty = pedestal.getBlock().getStateDefinition().getProperty("charge");
            helper.assertTrue(chargeProperty != null && pedestal.getValue(chargeProperty).toString().equals(Integer.toString(charge[i])),
                    "BETA26 pedestal metadata 0/2/1 was normalized away");
            helper.assertTrue(row.materials().get(0).count() == 8, "Source material list must contain eight stones");
        }
        helper.succeed();
    }
    @GameTest(template = "empty") public static void targetBookmarksPreserveThreeRotationsAndStoredPillarFacings(GameTestHelper helper) {
        var row = blueprint("infusionaltar"); var target = row.targetPreview();
        helper.assertTrue(target.states().size() == 6, "Assembled altar must retain four pillars, pedestal and matrix");
        int[][] positions = {{2,0},{0,0},{2,2},{0,2}};
        String[] facings = {"east","north","south","west"};
        for (int i = 0; i < 4; i++) {
            var state = target.getBlockState(new BlockPos(positions[i][0],0,positions[i][1]));
            var facing = state.getBlock().getStateDefinition().getProperty("facing");
            helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals("pillar_arcane")
                    && facing != null && state.getValue(facing).toString().equals(facings[i]),
                    "BETA26 bookmark rotation or stored facing was silently corrected");
        }
        var infernal = blueprint("infernalfurnace").targetPreview();
        helper.assertTrue(block(infernal,1,1,1).equals("thaumcraft:infernal_furnace") && infernal.states().size() == 25,
                "Infernal result must replace lava and remove bars, not alter source shell");
        helper.succeed();
    }
    @GameTest(template = "empty") public static void thaumatoriumAndGolemPressKeepActualSourceLayoutAndPistonFacing(GameTestHelper helper) {
        var thaumatorium = blueprint("Thaumatorium").preview(0);
        helper.assertTrue(thaumatorium.states().size() == 3 && block(thaumatorium,0,0,0).equals("thaumcraft:crucible")
                && block(thaumatorium,0,1,0).equals("thaumcraft:metal_alchemical")
                && block(thaumatorium,0,2,0).equals("thaumcraft:metal_alchemical"), "Thaumatorium source column is incorrect");
        var row = blueprint("GolemPress"); var press = row.preview(0);
        helper.assertTrue(row.width() == 2 && row.height() == 2 && row.depth() == 2 && press.states().size() == 5,
                "Golem press release size or sparse source layout changed");
        helper.assertTrue(block(press,0,0,0).equals("minecraft:cauldron") && block(press,0,0,1).equals("minecraft:anvil")
                && block(press,1,0,1).equals("thaumcraft:table_stone") && block(press,1,1,0).equals("minecraft:iron_bars")
                && press.getBlockState(new BlockPos(1,0,0)).getValue(BlockStateProperties.FACING) == net.minecraft.core.Direction.UP,
                "Golem press bottom row or upward piston state lost");
        helper.assertTrue(row.displayStack().orElseThrow().getItem() == BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft","golem_builder")), "Original explicit golem-builder bookmark lost");
        helper.succeed();
    }
    @GameTest(template = "empty") public static void layerAndMaterialViewsAreImmutableAndCannotMutateWorld(GameTestHelper helper) {
        var row = blueprint("infernalfurnace"); var whole = row.preview(0); var layer = row.preview(2);
        helper.assertTrue(layer.states().size() == 9 && layer.states().keySet().stream().allMatch(pos -> pos.getY() == 0)
                        && row.preview(3).states().isEmpty() && row.preview(Integer.MAX_VALUE).states().isEmpty(),
                "Layer slicing must remove top layers and permit the original empty final slice");
        helper.assertTrue(whole.states().size() == 26 && whole.getBlockState(new BlockPos(-1,0,0)).isAir()
                        && whole.getBlockState(new BlockPos(1,999,1)).isAir() && whole.getBlockEntity(new BlockPos(1,1,1)) == null,
                "Preview delegates missing coordinates or tiles to a real world");
        boolean immutable = false;
        try { whole.states().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        helper.assertTrue(immutable, "A caller can mutate the shared construction map");
        var stack = row.materialStacks().get(0); stack.setCount(1);
        helper.assertTrue(row.materialStacks().get(0).getCount() == 12, "Material icon mutation corrupts later book renders");
        helper.succeed();
    }
}
