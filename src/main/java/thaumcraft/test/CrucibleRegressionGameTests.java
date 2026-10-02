package thaumcraft.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.alchemy.CrucibleBlock;
import thaumcraft.alchemy.CrucibleBlockEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.research.KnowledgeStore;
import thaumcraft.research.ResearchCatalog;
import thaumcraft.research.ResearchEntry;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class CrucibleRegressionGameTests {
    @GameTest(template = "empty")
    public static void fillingFullHotCrucibleDoesNotDissolveTheBucket(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        CrucibleBlockEntity crucible = prepare(helper, pos, new AspectList().add(Aspect.FIRE, 2));
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "refill_test"));
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        ((CrucibleBlock) AlchemyModule.CRUCIBLE.get()).use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET) && player.getMainHandItem().getCount() == 1,
                "Refilling a full crucible consumed the water bucket");
        helper.assertTrue(crucible.water() == 1000 && crucible.aspects().visSize() == 2 && crucible.aspects().getAmount(Aspect.FIRE) == 2,
                "A refused refill dissolved the bucket into the crucible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void craftedOutputIsNotDissolvedAfterFiveSeconds(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        CrucibleBlockEntity crucible = prepare(helper, pos, new AspectList().add(Aspect.TOOL, 5));
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "output_test"));
        AspectList discoveries = new AspectList();
        for (Aspect primal : Aspect.getPrimalAspects()) discoveries.add(primal, 1);
        for (int i = 0; i < 12; i++) KnowledgeStore.recordScan(player, "test:output_" + i, discoveries);
        for (int pass = 0; pass < 10; pass++) {
            for (ResearchEntry entry : ResearchCatalog.entries()) if (entry.supported()) KnowledgeStore.discoverResearch(player, entry.key());
        }
        helper.assertTrue(KnowledgeStore.get(player).knowsResearch("METALLURGY@1"), "Test failed to unlock brass research");
        helper.assertTrue(crucible.consume(new ItemStack(Items.IRON_INGOT), player), "Brass recipe failed");
        var nearby = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        helper.assertTrue(nearby.size() == 1, "Expected exactly one crafted output");
        ItemEntity output = nearby.get(0);
        helper.assertTrue(thaumcraft.scanning.AspectRegistry.getAspects(output.getItem()).visSize() > 0,
                "Regression test needs a result that could otherwise dissolve");
        ItemStack expected = output.getItem().copy();
        // Force the result back into the crucible mouth; permanent protection must
        // survive beyond the previous 100-tick grace period independently of physics.
        output.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        output.setDeltaMovement(Vec3.ZERO);
        helper.runAfterDelay(125, () -> {
            helper.assertTrue(!output.isRemoved() && ItemStack.matches(output.getItem(), expected),
                    "The crucible swallowed its own crafted output after the old grace period");
            helper.assertTrue(crucible.water() == 950 && crucible.aspects().visSize() == 0,
                    "Output protection failed and added result aspects back to the crucible");
            output.discard();
            helper.succeed();
        });
    }

    private static CrucibleBlockEntity prepare(GameTestHelper helper, BlockPos pos, AspectList aspects) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos, AlchemyModule.CRUCIBLE.get().defaultBlockState());
        var crucible = (CrucibleBlockEntity) level.getBlockEntity(pos);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Heat", 200);
        tag.putInt("Water", 1000);
        aspects.writeToNBT(tag);
        crucible.load(tag);
        return crucible;
    }
}
