package thaumcraft.client.research;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.research.PlayerKnowledge;

public final class ResearchClient {
    public static void receive(CompoundTag data, boolean open) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerKnowledge knowledge = PlayerKnowledge.load(data);
        thaumcraft.equipment.client.SanityHud.receive(knowledge);
        int scanCount = data.getInt("ScanCount");
        if (open) minecraft.setScreen(new ThaumonomiconScreen(knowledge, scanCount));
        else if (minecraft.screen instanceof ThaumonomiconScreen screen) screen.update(knowledge, scanCount);
        else if (minecraft.screen instanceof ThaumonomiconPageScreen screen) {
            screen.update(knowledge, scanCount);
            if (data.contains("ProgressResult")) screen.requestResult(data.getString("ProgressResult"));
        }
    }
}
