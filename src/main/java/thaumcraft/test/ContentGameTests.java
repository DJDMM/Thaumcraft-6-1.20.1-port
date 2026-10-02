package thaumcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class ContentGameTests {
    @GameTest(template = "empty")
    public static void aspectsSurviveSaveLoad(GameTestHelper helper) {
        var original = new thaumcraft.api.aspects.AspectList()
                .add(thaumcraft.api.aspects.Aspect.AIR, 12)
                .add(thaumcraft.api.aspects.Aspect.MAGIC, 3);
        var tag = new net.minecraft.nbt.CompoundTag();
        original.writeToNBT(tag);
        var unknown = new net.minecraft.nbt.CompoundTag();
        unknown.putString("key", "removed_addon_aspect");
        unknown.putInt("amount", 8);
        tag.getList("Aspects", 10).add(unknown);
        var loaded = new thaumcraft.api.aspects.AspectList();
        loaded.readFromNBT(tag);
        helper.assertTrue(loaded.size() == 2 && loaded.visSize() == 15, "Aspect NBT round trip failed");
        helper.assertTrue(loaded.getAmount(thaumcraft.api.aspects.Aspect.AIR) == 12, "Aspect identity lost");
        helper.assertTrue(!loaded.reduce(thaumcraft.api.aspects.Aspect.MAGIC, 4), "Overdraw was allowed");
        helper.assertTrue(loaded.reduce(thaumcraft.api.aspects.Aspect.MAGIC, 2), "Valid reduction failed");
        loaded.copy().remove(thaumcraft.api.aspects.Aspect.AIR);
        helper.assertTrue(loaded.getAmount(thaumcraft.api.aspects.Aspect.AIR) == 12, "Copy modified original");
        helper.assertTrue(thaumcraft.api.aspects.Aspect.getPrimalAspects().size() == 6, "Primal aspect definitions changed");
        helper.succeed();
    }

    private static Item item(String name) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", name));
    }

    private static CraftingContainer grid() {
        return new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
    }

    @GameTest(template = "empty")
    public static void metalCraftingRoundTrip(GameTestHelper helper) {
        for (String metal : new String[]{"thaumium", "void", "brass"}) {
            for (String[] pair : new String[][]{{"nugget_" + metal, "ingot_" + metal}, {"ingot_" + metal, "metal_" + metal}}) {
                CraftingContainer input = grid();
                for (int i = 0; i < 9; i++) input.setItem(i, new ItemStack(item(pair[0])));
                var packed = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
                var output = packed.assemble(input, helper.getLevel().registryAccess());
                helper.assertTrue(output.is(item(pair[1])) && output.getCount() == 1, "Packing failed: " + pair[0]);
                input.clearContent();
                input.setItem(4, output);
                var unpacked = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
                var result = unpacked.assemble(input, helper.getLevel().registryAccess());
                helper.assertTrue(result.is(item(pair[0])) && result.getCount() == 9, "Unpacking failed: " + pair[1]);
                input.clearContent();
                input.setItem(0, new ItemStack(item(pair[0])));
                helper.assertTrue(!packed.matches(input, helper.getLevel()), "Packing accepts insufficient ingredients");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blocksDropAndSupportBeacons(GameTestHelper helper) {
        for (String name : new String[]{"metal_thaumium", "metal_void", "metal_brass", "stone_arcane", "stone_arcane_brick"}) {
            Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", name));
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            var state = helper.getLevel().getBlockState(pos);
            helper.assertTrue(state.is(BlockTags.BEACON_BASE_BLOCKS), "Missing beacon base tag: " + name);
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), "Missing mining tag: " + name);
            helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state), "Pickaxe cannot mine " + name);
            var drops = Block.getDrops(state, helper.getLevel(), pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1, "Incorrect drops: " + name);
        }
        helper.succeed();
    }
}
