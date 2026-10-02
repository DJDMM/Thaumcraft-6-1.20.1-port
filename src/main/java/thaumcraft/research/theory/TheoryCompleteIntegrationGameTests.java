package thaumcraft.research.theory;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.UUID;

/** Integration contracts that change when the entire BETA26 deck and aid registry is present. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TheoryCompleteIntegrationGameTests {
    @GameTest(template = "empty")
    public static void fullAidPacketRoundTripAndInspirationLimitRejectBeforeMutation(GameTestHelper helper) {
        helper.assertTrue(TheoryCard.ids().size() == 33 && TheoryAids.keys().size() == 14,
                "The registered BETA26 card/aid set is incomplete");
        var all = new LinkedHashSet<>(TheoryAids.keys());
        var request = new TheoryNetwork.Request(41, 0, TheoryNetwork.Action.START, -1, all);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TheoryNetwork.Request.encode(request, buffer);
            helper.assertTrue(TheoryNetwork.Request.decode(buffer).equals(request), "Fourteen aids were lost on the network");
        } finally { buffer.release(); }
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(pos, TheoryModule.TABLE.get().defaultBlockState());
        var table = (ResearchTableBlockEntity) helper.getLevel().getBlockEntity(pos);
        table.setItem(0, new ItemStack(TheoryModule.SCRIBING_TOOLS.get()));
        table.setItem(1, new ItemStack(Items.PAPER, 10));
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "full_aid_wire"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        var menu = new ResearchTableMenu(41, player.getInventory(), table);
        player.containerMenu = menu;
        var before = table.saveWithoutMetadata();
        helper.assertTrue(TheoryNetwork.process(menu, player, request.action(), request.revision(), request.cardIndex(), all)
                        == TheoryResult.INVALID && before.equals(table.saveWithoutMetadata()),
                "Too many aids consumed resources or created a non-positive starting session");
        helper.assertTrue(TheoryNetwork.process(menu, player, TheoryNetwork.Action.START, 0, -1,
                        java.util.Set.of("UNKNOWN_AID")) == TheoryResult.INVALID && before.equals(table.saveWithoutMetadata()),
                "Unknown aid mutated the table");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realTableVisualSyncRestoresInventoryAndSessionWithoutSharingMutableNbt(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(pos, TheoryModule.TABLE.get().defaultBlockState());
        var table = (ResearchTableBlockEntity) helper.getLevel().getBlockEntity(pos);
        var tools = new ItemStack(TheoryModule.SCRIBING_TOOLS.get());
        tools.setDamageValue(37);
        table.setItem(0, tools);
        table.setItem(1, new ItemStack(Items.PAPER, 17));
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "table_visual_sync"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        helper.assertTrue(table.start(player, 0, java.util.Set.of()) == TheoryResult.ACCEPTED, "Cannot start table fixture");
        var update = table.getUpdateTag();
        var restored = new ResearchTableBlockEntity(pos, table.getBlockState());
        restored.load(update);
        helper.assertTrue(restored.getItem(0).getDamageValue() == 37 && restored.getItem(1).getCount() == 17
                        && restored.session().save().equals(table.session().save()) && restored.revision() == 1,
                "Block entity sync omitted ink, paper or session paper history");
        update.getCompound("Session").putInt("PlacedCards", 999);
        restored.getItem(1).shrink(5);
        helper.assertTrue(table.session().placedCards() == 0 && table.getItem(1).getCount() == 17,
                "Detached render state mutated the real table");
        helper.assertTrue(table.getUpdatePacket() != null, "Research table has no vanilla block entity update packet");
        helper.succeed();
    }
}
