package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The operational golem API used by seals; inventories returned by carrying are detached. */
public interface SealWorker {
    Mob mob();
    UUID ownerId();
    int color();
    Set<String> traits();
    boolean withinHome(BlockPos pos);
    double moveSpeed();
    boolean inCombat();
    List<ItemStack> carrying();
    boolean canCarry(ItemStack stack,boolean partial);
    int canCarryAmount(ItemStack stack);
    ItemStack holdItem(ItemStack stack);
    ItemStack dropItem(ItemStack stack);
    void addRankXp(int amount);
    void swingArm();
}
