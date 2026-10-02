package thaumcraft.essentia.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.essentia.EssentiaJarBlockEntity;

/** BETA26's metadata 0/1 phials split into modern empty/filled registry entries. */
public final class EssentiaPhialItem extends Item implements IEssentiaContainerItem {
    public static final int CAPACITY = 10;
    private final boolean filled;

    public EssentiaPhialItem(CatalogModule.Spec spec) {
        super(new Properties().stacksTo(spec.stackLimit()));
        filled = spec.id().equals("phial_filled");
    }

    @Override
    public Component getName(ItemStack stack) {
        AspectList contents = getAspects(stack);
        if (filled && contents != null)
            return Component.translatable(getDescriptionId(stack), contents.getAspects()[0].getName());
        return super.getName(stack);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!filled || stack.getTag() != null || level.isClientSide || !(entity instanceof Player player)) return;
        var inventory = player.getInventory();
        int target = -1;
        if (slot >= 0 && slot < inventory.items.size() && inventory.getItem(slot) == stack) {
            target = slot;
        } else {
            // Forge's IForgeItem.onInventoryTick reduces the global compartment index
            // before calling inventoryTick: the offhand's reported slot is 0, not 40.
            // Inventory.getItem/setItem use the global index (36 main +4 armor =40).
            int offhandSlot = inventory.items.size() + inventory.armor.size();
            if (offhandSlot < inventory.getContainerSize() && inventory.getItem(offhandSlot) == stack)
                target = offhandSlot;
        }
        ItemStack empty = CatalogModule.stack("phial_empty");
        if (target < 0 || empty.isEmpty()) return;
        inventory.setItem(target, empty.copyWithCount(stack.getCount()));
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack held, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || held.isEmpty()
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof EssentiaJarBlockEntity jar))
            return InteractionResult.PASS;

        if (!filled) {
            Aspect aspect = jar.aspect();
            if (aspect == null || jar.amount() < CAPACITY) return InteractionResult.PASS;
            ItemStack result = CatalogModule.aspectStack("phial_filled", aspect, CAPACITY);
            if (result.isEmpty()) return InteractionResult.PASS;
            if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
            if (!jar.take(aspect, CAPACITY)) return InteractionResult.PASS;
            exchange(context, player, held, result);
            return InteractionResult.SUCCESS;
        }

        AspectList contents = canonicalContents(held);
        // Conservation hardening: BETA26 blindly poured base=10 even when command-edited
        // phial NBT contained less/more. Normal phials contain exactly one aspect ×10;
        // malformed contents must never create or silently destroy essentia.
        if (contents == null || contents.size() != 1 || contents.visSize() != CAPACITY)
            return InteractionResult.PASS;
        Aspect aspect = contents.getAspects()[0];
        if (contents.getAmount(aspect) != CAPACITY || !jar.canAccept(aspect, CAPACITY))
            return InteractionResult.PASS;
        ItemStack result = CatalogModule.stack("phial_empty");
        if (result.isEmpty()) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!jar.addExact(aspect, CAPACITY)) return InteractionResult.PASS;
        exchange(context, player, held, result);
        return InteractionResult.SUCCESS;
    }

    private AspectList canonicalContents(ItemStack stack) {
        if (!stack.hasTag()) return null;
        // Inspect the raw canonical entry too: the API intentionally ignores unknown
        // and nonpositive records, which must not make a malformed phial usable.
        ListTag raw = stack.getTag().getList("Aspects", Tag.TAG_COMPOUND);
        if (raw.size() != 1) return null;
        var entry = raw.getCompound(0);
        if (!entry.contains("key", Tag.TAG_STRING) || !entry.contains("amount", Tag.TAG_INT)
                || Aspect.getAspect(entry.getString("key")) == null || entry.getInt("amount") != CAPACITY)
            return null;
        return getAspects(stack);
    }

    private static void exchange(UseOnContext context, Player player, ItemStack held, ItemStack result) {
        // Original phial exchange consumes one even in creative. Check room first:
        // modern Inventory.add silently discards an uninsertable result in creative.
        // A full inventory must instead preserve the produced phial as a world drop.
        held.shrink(1);
        boolean hasRoom = player.getInventory().getFreeSlot() >= 0
                || player.getInventory().getSlotWithRemainingSpace(result) >= 0;
        if (!hasRoom || !player.getInventory().add(result)) {
            BlockPos pos = context.getClickedPos();
            context.getLevel().addFreshEntity(new ItemEntity(context.getLevel(),
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, result));
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.BOTTLE_FILL,
                SoundSource.PLAYERS, .25F, 1F);
    }
}
