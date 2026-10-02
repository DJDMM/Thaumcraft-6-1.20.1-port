package thaumcraft.world.biome;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local, opt-in notes for manual playtesting. Clicking only prepares a command. */
@Mod.EventBusSubscriber(modid = "thaumcraft", value = Dist.CLIENT)
public final class GameplayHints {
    private static ClientPacketListener connection;
    private static int joinedTicks;
    private static boolean shown;

    private GameplayHints() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.gameplayHints")) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener current = minecraft.getConnection();
        if (current != connection) {
            connection = current;
            joinedTicks = 0;
            shown = false;
        }
        if (shown || current == null || minecraft.level == null || minecraft.player == null) return;
        if (++joinedTicks < 20) return;
        shown = true;
        var chat = minecraft.gui.getChat();
        chat.addMessage(Component.literal("Thaumcraft 6 — команды для проверки биомов")
                .withStyle(ChatFormatting.GOLD));
        chat.addMessage(Component.literal("Нажмите на команду, чтобы вставить её в чат. Для выполнения нужны читы.")
                .withStyle(ChatFormatting.GRAY));
        chat.addMessage(command("Найти Магический лес: ", "/locate biome thaumcraft:magical_forest"));
        chat.addMessage(Component.literal("Нажмите на координаты результата /locate, чтобы подготовить телепорт.")
                .withStyle(ChatFormatting.GRAY));
        chat.addMessage(Component.literal("Eerie и Outer Lands естественно не генерируются: /locate их не найдёт.")
                .withStyle(ChatFormatting.YELLOW));
        chat.addMessage(Component.literal("Для проверки их цвета и сохранения можно сменить биом участка вокруг себя:")
                .withStyle(ChatFormatting.GRAY));
        chat.addMessage(command("Eerie: ", "/fillbiome ~-12 ~-12 ~-12 ~12 ~12 ~12 thaumcraft:eerie"));
        chat.addMessage(command("Outer Lands: ", "/fillbiome ~-12 ~-12 ~-12 ~12 ~12 ~12 thaumcraft:eldritch"));
        chat.addMessage(Component.literal("/fillbiome меняет только биом участка; растения и деревья заново не создаёт.")
                .withStyle(ChatFormatting.GRAY));
    }

    private static Component command(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.WHITE).append(Component.literal(value)
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, value))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Вставить в чат; команда выполнится только после Enter")))));
    }
}
