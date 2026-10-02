package thaumcraft.research;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import thaumcraft.alchemy.AspectCrystalItem;

import java.util.HashMap;
import java.util.Map;

/** Evidence is recorded by committed server actions, never by recipe previews or inventory scans. */
public final class ResearchEvents {
    private static final Map<ItemEntity, PendingPickup> PENDING_PICKUPS = new HashMap<>();

    private ResearchEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ResearchEvents::onPickup);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ResearchEvents::beforePickup);
        MinecraftForge.EVENT_BUS.addListener(ResearchEvents::afterServerTick);
        MinecraftForge.EVENT_BUS.addListener(ResearchEvents::onCrafted);
        MinecraftForge.EVENT_BUS.addListener(ResearchEvents::onWakeUp);
        MinecraftForge.EVENT_BUS.addListener(ResearchEvents::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount > 0 && player.tickCount % 2000 == 0) decayTemporaryWarp(player);
    }

    /** Scheduled temporary decay shares the original Ward guard; random warp events still require porting. */
    public static boolean decayTemporaryWarp(ServerPlayer player) {
        if (thaumcraft.equipment.cleansing.CleansingSupport.isProtected(player)) return false;
        return KnowledgeStore.addTemporaryWarp(player, -1);
    }

    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        // Forge 47.4.10 omits this post-event when Inventory.add fills a stack partially
        // and its final insertion attempt fails. The pre-event snapshot handles that case.
        PENDING_PICKUPS.remove(event.getOriginalEntity());
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getStack().isEmpty()) return;
        acquired(player, event.getStack());
    }

    private static void beforePickup(EntityItemPickupEvent event) {
        ItemEntity entity = event.getItem();
        // Finalize the previous touch before a second player can change this item's count.
        PendingPickup previous = PENDING_PICKUPS.remove(entity);
        if (previous != null) previous.finish();
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || !discoveryItem(stack)) return;
        PENDING_PICKUPS.put(entity, new PendingPickup(player, entity, stack.copy(), matchingInventoryCount(player, stack)));
    }

    private static void afterServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var iterator = PENDING_PICKUPS.values().iterator();
        while (iterator.hasNext()) {
            PendingPickup pickup = iterator.next();
            if (pickup.player().serverLevel().getServer() != event.getServer()) continue;
            iterator.remove();
            pickup.finish();
        }
    }

    private record PendingPickup(ServerPlayer player, ItemEntity entity, ItemStack before, int ownedBefore) {
        void finish() {
            ItemStack remaining = entity.getItem();
            if (!remaining.isEmpty() && !ItemStack.isSameItemSameTags(before, remaining)) return;
            int removed = before.getCount() - remaining.getCount();
            int gained = matchingInventoryCount(player, before) - ownedBefore;
            // Require both sides of this attempted transfer. Possession alone, a cancelled
            // touch, or disappearance of a dropped item cannot establish an acquisition.
            if (removed > 0 && gained > 0) acquired(player, before.copyWithCount(Math.min(removed, gained)));
        }
    }

    private static int matchingInventoryCount(ServerPlayer player, ItemStack wanted) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameTags(wanted, stack)) count += stack.getCount();
        }
        return count;
    }

    private static boolean discoveryItem(ItemStack stack) {
        return stack.is(ResearchModule.THAUMONOMICON.get())
                || AspectCrystalItem.isCrystal(stack);
    }

    private static void acquired(ServerPlayer player, ItemStack stack) {
        if (AspectCrystalItem.isCrystal(stack)) {
            if (KnowledgeStore.recordFact(player, "!gotcrystals")) {
                message(player, "got.crystals");
            }
        }
        if (stack.is(ResearchModule.THAUMONOMICON.get())) onThaumonomiconUse(player);
    }

    public static void onThaumonomiconUse(ServerPlayer player) {
        KnowledgeStore.recordFact(player, "!gotthaumonomicon");
    }

    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recordCraft(player, event.getCrafting());
    }

    /** Used by devices and block transformations which do not use a vanilla crafting result slot. */
    public static void recordCraft(ServerPlayer player, ItemStack output) {
        if (player != null && output != null && !output.isEmpty()) KnowledgeStore.recordCraft(player, output);
    }

    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // A partial acquisition earlier in this tick already committed before waking.
        var iterator = PENDING_PICKUPS.values().iterator();
        while (iterator.hasNext()) {
            PendingPickup pickup = iterator.next();
            if (pickup.player() != player) continue;
            iterator.remove();
            pickup.finish();
        }
        // In Forge 1.20.1 the hook runs before stopSleeping resets this state. TC6 also
        // grants the dream after an early wake, including a wake in the same tick.
        if (player.isSleeping()) giveDreamJournal(player);
    }

    /** Returns true only when this player's one-time journal was successfully delivered. */
    public static boolean giveDreamJournal(ServerPlayer player) {
        PlayerKnowledge knowledge = KnowledgeStore.get(player);
        if (!knowledge.knowsResearch("!gotcrystals") || knowledge.knowsResearch("!gotdream")) return false;
        ItemStack journal = createDreamJournal(player);
        if (!player.getInventory().add(journal)) {
            ItemEntity drop = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), journal);
            drop.setDefaultPickUpDelay();
            if (!player.serverLevel().addFreshEntity(drop)) return false;
        }
        KnowledgeStore.recordFact(player, "!gotdream");
        message(player, "got.dream");
        return true;
    }

    public static ItemStack createDreamJournal(ServerPlayer player) {
        ItemStack journal = new ItemStack(Items.WRITTEN_BOOK);
        var tag = journal.getOrCreateTag();
        tag.putInt("generation", 3);
        tag.putString("title", "Strange Dreams");
        tag.putString("author", player.getGameProfile().getName());
        ListTag pages = new ListTag();
        // The official BETA26 startBook contains book.start.1, .2 AND .3.
        for (int page = 1; page <= 3; page++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.start." + page))));
        }
        tag.put("pages", pages);
        journal.setHoverName(Component.translatable("book.start.title").withStyle(style -> style.withItalic(false)));
        return journal;
    }

    private static void message(ServerPlayer player, String key) {
        if (player.connection != null) player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE));
    }

}
