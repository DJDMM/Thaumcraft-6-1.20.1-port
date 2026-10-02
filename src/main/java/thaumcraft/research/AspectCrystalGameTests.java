package thaumcraft.research;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.AspectCrystalItem;
import thaumcraft.alchemy.CrucibleBlockEntity;
import thaumcraft.alchemy.CrucibleRecipes;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.scanning.AspectRegistry;
import thaumcraft.world.aura.AuraManager;

import java.util.UUID;

/** The real aspect item, reloadable recipe, player knowledge and crucible output/accounting. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class AspectCrystalGameTests {
    private AspectCrystalGameTests() {}

    @GameTest(template = "empty")
    public static void allAspectCrystalsRetainOriginalNbtAndScanPerItem(GameTestHelper helper) {
        for (Aspect aspect : Aspect.aspects.values()) {
            ItemStack stack = AspectCrystalItem.create(aspect, 32);
            helper.assertTrue(stack.getItem() instanceof IEssentiaContainerItem && stack.getCount() == 32,
                    "Crystal factory did not produce the original aspect container");
            var tag = stack.getTag().getList("Aspects", 10).getCompound(0);
            helper.assertTrue(tag.getString("key").equals(aspect.getTag()) && tag.getInt("amount") == 1,
                    "Crystal lost the BETA26 Aspects/key/amount data");
            AspectList scan = AspectRegistry.getAspects(stack);
            helper.assertTrue(scan.size() == 1 && scan.getAmount(aspect) == 1,
                    "NBT crystal scan used a generic recipe composition or multiplied stack count");
            var name = (TranslatableContents) stack.getHoverName().getContents();
            helper.assertTrue(name.getKey().equals("item.thaumcraft.crystal_essence")
                    && name.getArgs().length == 1 && name.getArgs()[0].equals(aspect.getName()),
                    "Original crystal display name did not include its contained aspect");
            ItemStack restored = ItemStack.of(stack.save(new CompoundTag()));
            helper.assertTrue(ItemStack.isSameItemSameTags(stack, restored), "Crystal save/load changed its aspect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rawCrystalsInitializeOnlyWhenTheyHaveNoCompound(GameTestHelper helper) {
        ItemStack raw = CatalogModule.stack("crystal_essence");
        raw.getItem().inventoryTick(raw, helper.getLevel(), player(helper), 0, false);
        var contents = ((IEssentiaContainerItem) raw.getItem()).getAspects(raw);
        helper.assertTrue(contents != null && contents.size() == 1 && contents.visSize() == 1,
                "Untagged inventory crystal did not receive one random original aspect");
        ItemStack emptyCompound = CatalogModule.stack("crystal_essence");
        emptyCompound.setTag(new CompoundTag());
        emptyCompound.getItem().inventoryTick(emptyCompound, helper.getLevel(), player(helper), 0, false);
        helper.assertTrue(emptyCompound.getTag().isEmpty(), "Existing empty compound was silently initialized");
        ItemStack crafted = CatalogModule.stack("crystal_essence");
        crafted.getItem().onCraftedBy(crafted, helper.getLevel(), player(helper));
        helper.assertTrue(((IEssentiaContainerItem) crafted.getItem()).getAspects(crafted).visSize() == 1,
                "Untagged crafted crystal did not receive its BETA26 fallback aspect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyAspectRecipeProducesARealTaggedCrystal(GameTestHelper helper) {
        var player = player(helper);
        complete(player, "BASEALCHEMY");
        long count = CrucibleRecipes.all().stream().filter(r -> r.id().getPath().startsWith("vis_crystal_")).count();
        helper.assertTrue(count == Aspect.aspects.size(), "Missing a registered BETA26 crystal recipe");
        for (Aspect aspect : Aspect.aspects.values()) {
            var recipe = CrucibleRecipes.find(CatalogModule.stack("nugget_quartz"), new AspectList().add(aspect, 2), player);
            helper.assertTrue(recipe != null && recipe.research().equals("BASEALCHEMY")
                    && recipe.cost().visSize() == 2 && recipe.cost().getAmount(aspect) == 2,
                    "Wrong original aspect crystal catalyst/cost/gate");
            helper.assertTrue(AspectRegistry.getAspects(recipe.output()).getAmount(aspect) == 1,
                    "Reloaded crystal recipe output has no aspect NBT");
            helper.assertTrue(CrucibleRecipes.find(new ItemStack(Items.QUARTZ), new AspectList().add(aspect, 2), player) == null,
                    "Whole quartz accepted in place of a quartz sliver");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleCrystallizesTwoUnitsAndAllowsTheLastWaterRemainder(GameTestHelper helper) {
        var player = player(helper);
        complete(player, "BASEALCHEMY");
        var crucible = crucible(helper, 1000, new AspectList().add(Aspect.LIFE, 5));
        ItemStack input = CatalogModule.stack("nugget_quartz"); input.setCount(3);
        helper.assertTrue(crucible.consume(input, player) && input.getCount() == 2
                && crucible.water() == 950 && crucible.aspects().getAmount(Aspect.LIFE) == 3,
                "Crystal craft failed atomic sliver/two-aspect/50 mB consumption");
        var results = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(crucible.getBlockPos()).inflate(2),
                item -> item.getPersistentData().getBoolean("thaumcraft_crucible_output"));
        helper.assertTrue(results.size() == 1 && results.get(0).getItem().getCount() == 1
                && AspectRegistry.getAspects(results.get(0).getItem()).getAmount(Aspect.LIFE) == 1,
                "Crucible emitted a generic/incorrect crystal or reabsorbed its output");
        helper.assertTrue(KnowledgeStore.get(player).hasCraft("thaumcraft:crystal_essence"), "Crystallization did not record real craft evidence");
        prepare(crucible, 1, new AspectList().add(Aspect.LIFE, 2));
        helper.assertTrue(crucible.consume(input, player) && crucible.water() == 0
                && input.getCount() == 1 && crucible.aspects().visSize() == 0,
                "BETA26 last craft with 1–49 mB was denied or produced negative water");
        helper.assertTrue(!crucible.consume(input, player) && input.getCount() == 1,
                "Dry crucible accepted another item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crystalCraftNeedsResearchAndTwoUnitsWithoutGrantingAnUnlock(GameTestHelper helper) {
        var player = player(helper);
        var crucible = crucible(helper, 1000, new AspectList().add(Aspect.LIFE, 2));
        ItemStack input = CatalogModule.stack("nugget_quartz");
        helper.assertTrue(CrucibleRecipes.find(input, crucible.aspects(), player) == null, "Fresh player can crystallize without BASEALCHEMY");
        crucible.consume(input, player); // An unmatched catalyst may legitimately dissolve into its own aspects.
        helper.assertTrue(crucible.water() == 1000 && crucible.aspects().getAmount(Aspect.LIFE) == 2
                && !KnowledgeStore.get(player).hasCraft("thaumcraft:crystal_essence")
                && !KnowledgeStore.get(player).knowsResearch("BASEALCHEMY"),
                "Failed crystallization consumed recipe costs, wrote proof or granted research");
        complete(player, "BASEALCHEMY");
        prepare(crucible, 1000, new AspectList().add(Aspect.LIFE, 1));
        input = CatalogModule.stack("nugget_quartz");
        helper.assertTrue(CrucibleRecipes.find(input, crucible.aspects(), player) == null, "One aspect unit sufficed for crystallization");
        crucible.consume(input, player);
        helper.assertTrue(crucible.water() == 1000 && crucible.aspects().getAmount(Aspect.LIFE) == 1
                && !KnowledgeStore.get(player).hasCraft("thaumcraft:crystal_essence"), "Insufficient-aspect craft spent water or emitted crystal");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(crucible.getBlockPos()).inflate(2),
                item -> item.getItem().getItem() instanceof AspectCrystalItem).isEmpty(), "Denied craft still spawned a crystal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boilingCrucibleDissolvesActualCrystalContents(GameTestHelper helper) {
        var crucible = crucible(helper, 1000, new AspectList().add(Aspect.LIFE, 1));
        ItemStack input = AspectCrystalItem.create(Aspect.MAGIC, 4);
        helper.assertTrue(crucible.consume(input, null) && input.getCount() == 3 && crucible.water() == 1000
                && crucible.aspects().getAmount(Aspect.LIFE) == 1 && crucible.aspects().getAmount(Aspect.MAGIC) == 1,
                "Crystal dissolution ignored contained NBT, multiplied stack or spent recipe water");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overflowAndIdleSpillIndependentlyOnTheSameTick(GameTestHelper helper) {
        var crucible = crucible(helper, 1000, new AspectList().add(Aspect.FLUX, 502));
        CompoundTag tag = crucible.saveWithoutMetadata(); tag.putInt("Idle", 99); crucible.load(tag);
        var pos = crucible.getBlockPos();
        AuraManager.getVis(helper.getLevel(), pos); // Materialize aura before measuring.
        float before = AuraManager.getFlux(helper.getLevel(), pos);
        CrucibleBlockEntity.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), crucible);
        helper.assertTrue(crucible.aspects().getAmount(Aspect.FLUX) == 500
                && Math.abs(AuraManager.getFlux(helper.getLevel(), pos) - before - 2) < 0.001,
                "Overflow suppressed the independent 100-tick spill or Vitium pollution differed from BETA26");
        for (int i = 0; i < 99; i++) CrucibleBlockEntity.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), crucible);
        helper.assertTrue(crucible.aspects().getAmount(Aspect.FLUX) == 500, "Idle decay occurred before 100 ticks");
        CrucibleBlockEntity.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), crucible);
        helper.assertTrue(crucible.aspects().getAmount(Aspect.FLUX) == 499, "Idle decay failed after overflow ended");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void competingMetallurgyRecipesChooseTheHighestAspectCost(GameTestHelper helper) {
        var player = player(helper);
        KnowledgeStore.get(player).setResearchStage("METALLURGY", 1);
        AspectList available = new AspectList().add(Aspect.TOOL, 5).add(Aspect.MAGIC, 5).add(Aspect.EARTH, 5);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        var first = CrucibleRecipes.find(iron, available, player);
        helper.assertTrue(first != null && first.output().is(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "ingot_brass"))),
                "Stage one does not open brass independently of thaumium");
        KnowledgeStore.get(player).setResearchStage("METALLURGY", 2);
        var selected = CrucibleRecipes.find(iron, available, player);
        helper.assertTrue(selected != null && selected.cost().visSize() == 10
                && selected.output().is(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft", "ingot_thaumium"))),
                "Resource sorting selected five-cost brass before the original ten-cost thaumium preference");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quartzProducesNineSliversInTheRealCraftingManager(GameTestHelper helper) {
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        grid.setItem(7, new ItemStack(Items.QUARTZ));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        ItemStack output = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(recipe.getId().equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", "quartztonuggets"))
                && output.is(CatalogModule.stack("nugget_quartz").getItem()) && output.getCount() == 9,
                "One quartz does not produce the original nine slivers at a movable one-slot recipe");
        grid.setItem(0, new ItemStack(Items.QUARTZ));
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Quartz recipe accepted extra ingredients");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "CrystalTest"));
    }
    private static void complete(ServerPlayer player, String key) {
        KnowledgeStore.get(player).setResearchStage(key, ResearchCatalog.get(key).stages().size() + 1);
    }
    private static CrucibleBlockEntity crucible(GameTestHelper helper, int water, AspectList aspects) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var result = (CrucibleBlockEntity) helper.getLevel().getBlockEntity(pos);
        prepare(result, water, aspects);
        return result;
    }
    private static void prepare(CrucibleBlockEntity crucible, int water, AspectList aspects) {
        var data = new CompoundTag(); data.putInt("Water", water); data.putInt("Heat", 200); data.putInt("Idle", -250);
        aspects.writeToNBT(data); crucible.load(data);
    }
}
