package thaumcraft.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import thaumcraft.alchemy.*;
import thaumcraft.api.aspects.*;
import thaumcraft.research.*;
import thaumcraft.world.aura.AuraManager;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class AlchemyGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void alumentumExplodesOnceOnImpact(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.offset(0, 0, 1), Blocks.OBSIDIAN.defaultBlockState());
        var projectile = new AlumentumProjectile(AlchemyModule.ALUMENTUM_ENTITY.get(), level);
        projectile.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.2);
        projectile.setDeltaMovement(0, 0, 0.75);
        java.util.concurrent.atomic.AtomicInteger explosions = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<net.minecraftforge.event.level.ExplosionEvent.Detonate> listener = event -> {
            if (event.getExplosion().getDirectSourceEntity() == projectile) explosions.incrementAndGet();
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        level.addFreshEntity(projectile);
        helper.runAfterDelay(20, () -> {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            helper.assertTrue(projectile.isRemoved() && explosions.get() == 1, "Alumentum did not explode exactly once on impact");
            helper.assertTrue(AlchemyModule.ALUMENTUM.get().getBurnTime(new ItemStack(AlchemyModule.ALUMENTUM.get()), net.minecraft.world.item.crafting.RecipeType.SMELTING) == 4800, "Alumentum fuel value differs from TC6");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void salisRequiresDistinctCrystalsAndReturnsTools(GameTestHelper helper) {
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        grid.setItem(0, new ItemStack(Items.FLINT)); grid.setItem(1, new ItemStack(Items.BOWL)); grid.setItem(2, new ItemStack(Items.REDSTONE));
        grid.setItem(3, new ItemStack(thaumcraft.world.WorldModule.VIS_CRYSTALS.get("aer").get()));
        grid.setItem(4, new ItemStack(thaumcraft.world.WorldModule.VIS_CRYSTALS.get("ignis").get()));
        grid.setItem(5, new ItemStack(thaumcraft.world.WorldModule.VIS_CRYSTALS.get("terra").get()));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        helper.assertTrue(recipe.assemble(grid, helper.getLevel().registryAccess()).is(AlchemyModule.SALIS_MUNDUS.get()), "Salis recipe missing");
        var remaining = recipe.getRemainingItems(grid);
        helper.assertTrue(remaining.get(0).is(Items.FLINT) && remaining.get(1).is(Items.BOWL) && remaining.get(2).isEmpty(), "Salis did not return bowl/flint correctly");
        grid.setItem(5, grid.getItem(3).copy());
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Duplicate crystal types accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleHeatCraftAndPollution(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) level.getBlockEntity(pos);
        var coldStack = new ItemStack(Items.COAL, 2);
        helper.assertTrue(!crucible.consume(coldStack, null) && coldStack.getCount() == 2, "Cold dry crucible consumed an item");
        crucible.fillWater();
        for (int i = 0; i < 151; i++) CrucibleBlockEntity.tick(level, pos, level.getBlockState(pos), crucible);
        helper.assertTrue(crucible.heat() == 151, "Heat threshold differs from TC6");
        helper.assertTrue(crucible.consume(coldStack, null) && coldStack.getCount() == 1, "Hot crucible did not dissolve exactly one coal");
        helper.assertTrue(crucible.aspects().getAmount(Aspect.FIRE) > 0, "Dissolving bypassed aspect registry");

        CompoundTag prepared = new CompoundTag();
        prepared.putInt("Heat", 200); prepared.putInt("Water", 1000);
        new AspectList().add(Aspect.TOOL, 5).writeToNBT(prepared);
        crucible.load(prepared);
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "alchemy_test"));
        ItemStack catalyst = new ItemStack(Items.IRON_INGOT, 2);
        helper.assertTrue(CrucibleRecipes.find(catalyst, crucible.aspects(), player) == null, "Locked research crafted brass");
        AspectList discoveries = new AspectList();
        for (Aspect primal : Aspect.getPrimalAspects()) discoveries.add(primal, 1);
        for (int i = 0; i < 12; i++) KnowledgeStore.recordScan(player, "test:alchemy_" + i, discoveries);
        for (int pass = 0; pass < 10; pass++) for (ResearchEntry entry : ResearchCatalog.entries()) if (entry.supported()) KnowledgeStore.discoverResearch(player, entry.key());
        helper.assertTrue(KnowledgeStore.get(player).knowsResearch("METALLURGY@1"), "Brass progression unavailable");
        helper.assertTrue(crucible.consume(catalyst, player), "Unlocked brass recipe failed");
        helper.assertTrue(catalyst.getCount() == 1 && crucible.water() == 950 && crucible.aspects().getAmount(Aspect.TOOL) == 0, "Craft resource accounting failed");
        helper.assertItemEntityPresent(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse("thaumcraft:ingot_brass")), new BlockPos(1, 1, 1), 2);

        new AspectList().add(Aspect.FIRE, 8).add(Aspect.FLUX, 2).writeToNBT(prepared);
        crucible.load(prepared);
        float before = AuraManager.getFlux(level, pos);
        crucible.empty();
        helper.assertTrue(Math.abs(AuraManager.getFlux(level, pos) - before - 4F) < 0.001F, "Emptying failed to pollute aura");
        crucible.fillWater();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(pos).isAir(), "Breaking filled crucible resurrected it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleSaveLoad(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag(); tag.putInt("Water", 650); tag.putInt("Heat", 175);
        new AspectList().add(Aspect.EARTH, 14).add(Aspect.MAGIC, 3).writeToNBT(tag);
        var original = new CrucibleBlockEntity(BlockPos.ZERO, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        original.load(tag);
        var restored = new CrucibleBlockEntity(BlockPos.ZERO, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        restored.load(original.saveWithoutMetadata());
        helper.assertTrue(restored.water() == 650 && restored.heat() == 175 && restored.aspects().getAmount(Aspect.MAGIC) == 3 && restored.aspects().visSize() == 17, "Crucible NBT lost state");
        var fluid = restored.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(fluid.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE) == 350 && restored.water() == 650, "Simulated fluid fill mutated water");
        helper.assertTrue(fluid.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0, "Crucible accepted lava as water");
        fluid.drain(50, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(restored.water() == 600, "Fluid drain lost accounting");
        helper.succeed();
    }
}
