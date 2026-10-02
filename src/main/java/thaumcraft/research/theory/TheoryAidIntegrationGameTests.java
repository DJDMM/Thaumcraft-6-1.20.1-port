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
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class TheoryAidIntegrationGameTests {
    @GameTest(template = "empty")
    public static void threeAidsUseExactSearchBoundsAndReachTheServerMenu(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        level.setBlock(pos, TheoryModule.TABLE.get().defaultBlockState(), 3);
        var table = (ResearchTableBlockEntity) level.getBlockEntity(pos);
        level.setBlock(pos.offset(4, 1, 4), Blocks.BOOKSHELF.defaultBlockState(), 3);
        level.setBlock(pos.offset(-4, -1, -4), Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
        level.setBlock(pos.offset(0, 2, 0), Blocks.BEACON.defaultBlockState(), 3);
        helper.assertTrue(table.checkSurroundingAids().equals(Set.of(TheoryAids.BOOKSHELF, TheoryAids.ENCHANTMENT_TABLE)), "Aid search included Y=2 or missed its boundary");
        level.setBlock(pos.offset(4, 0, -4), Blocks.BEACON.defaultBlockState(), 3);
        level.setBlock(pos.offset(3, 0, -4), Blocks.BOOKSHELF.defaultBlockState(), 3);
        Set<String> all = TheoryAids.keys();
        helper.assertTrue(table.checkSurroundingAids().equals(all), "Inactive beacon or duplicate bookshelf changed aid detection");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "aid_menu"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.experienceLevel = 7;
        table.setItem(0, new ItemStack(TheoryModule.SCRIBING_TOOLS.get()));
        table.setItem(1, new ItemStack(Items.PAPER, 2));
        ResearchTableMenu menu = new ResearchTableMenu(19, player.getInventory(), table);
        player.containerMenu = menu;
        helper.assertTrue(menu.availableAids().equals(all) && menu.playerExperienceLevel() == 7, "Menu discarded aid/XP snapshot");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        TheoryNetwork.Request request = new TheoryNetwork.Request(19, 0, TheoryNetwork.Action.START, -1, all);
        try {
            TheoryNetwork.Request.encode(request, buffer);
            var decoded = TheoryNetwork.Request.decode(buffer);
            helper.assertTrue(decoded.equals(request) && TheoryNetwork.process(menu, player, decoded.action(), decoded.revision(), decoded.cardIndex(), decoded.aids()) == TheoryResult.ACCEPTED, "Three-aid packet failed");
        } finally { buffer.release(); }
        helper.assertTrue(table.session().inspiration() == 2 && table.getItem(1).getCount() == 2
                && table.getItem(0).getDamageValue() == 0, "Starting with three aids charged wrong resources");
        helper.succeed();
    }
}
