package thaumcraft.client.research;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.research.PlayerKnowledge;
import thaumcraft.research.ResearchBookNotifications;

public final class ResearchClient {
    private static Object previousConnection;
    private static PlayerKnowledge previousKnowledge;

    public static void receive(CompoundTag data, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        BookRecipeViews.receive(data);
        EarlySurvivalClientSmokeTest.snapshot(data);
        EssentiaProductionClientSmokeTest.snapshot(data);
        ThaumonomiconCompleteClientSmokeTest.snapshot(data);
        InfusionClientSmokeTest.snapshot(data);
        PlayerKnowledge knowledge = PlayerKnowledge.load(data);
        Object connection = minecraft.getConnection();
        if (connection != previousConnection) { previousConnection = connection; previousKnowledge = null; }
        for (var notice : ResearchBookNotifications.between(previousKnowledge, knowledge)) {
            minecraft.getToasts().addToast(new ResearchBookToast(notice));
        }
        previousKnowledge = knowledge;
        thaumcraft.equipment.client.SanityHud.receive(knowledge);
        int scanCount = data.getInt("ScanCount");
        if (open) minecraft.setScreen(new ThaumonomiconScreen(knowledge, scanCount));
        else if (minecraft.screen instanceof ThaumonomiconScreen screen) screen.update(knowledge, scanCount);
        else if (minecraft.screen instanceof ThaumonomiconPageScreen screen) {
            screen.update(knowledge, scanCount);
            if (data.contains("ProgressResult")) screen.requestResult(data.getString("ProgressResult"));
        }
        else if (minecraft.screen instanceof ThaumonomiconKnowledgeScreen screen) screen.update(knowledge, scanCount);
    }
}
