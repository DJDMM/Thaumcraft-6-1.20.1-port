package thaumcraft.research;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

public final class ResearchModule {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final RegistryObject<Item> THAUMONOMICON = ITEMS.register("thaumonomicon", ThaumonomiconItem::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ResearchNetwork.register();
        ResearchEvents.register();
    }

    private static final class ThaumonomiconItem extends Item {
        private ThaumonomiconItem() { super(new Item.Properties().stacksTo(1)); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                ResearchEvents.onThaumonomiconUse(serverPlayer);
                ResearchNetwork.open(serverPlayer);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("thaumcraft.research.book_tooltip"));
        }
    }
}
