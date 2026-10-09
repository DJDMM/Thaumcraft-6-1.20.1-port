package thaumcraft.scanning;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Server-authoritative scanning. No client-to-server discovery packet exists. */
public final class ScanningModule {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "thaumcraft");
    public static final RegistryObject<Item> THAUMOMETER = ITEMS.register("thaumometer", ThaumometerItem::new);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "thaumcraft");
    public static final RegistryObject<SoundEvent> SCAN_SOUND = SOUNDS.register("scan",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("thaumcraft", "scan")));

    private ScanningModule() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ScanningNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(ScanningModule::reload);
        MinecraftForge.EVENT_BUS.addListener(ScanningModule::interact);
        MinecraftForge.EVENT_BUS.addListener(ScanningModule::interactSpecific);
        MinecraftForge.EVENT_BUS.addListener(ScanningModule::interactBlock);
        MinecraftForge.EVENT_BUS.addListener(ScanningModule::tick);
    }

    private static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 5 == 0 && player.isAlive() && !player.isSpectator()
                && ThaumometerItem.auraHand(player) != null) {
            // Target snapshots retain the port's five-tick cadence. Only the original
            // held thaumometer (not a resonator/caster or a read-only capture) discovers FLUX.
            if (player.tickCount % 20 == 0 && ThaumometerItem.heldHand(player) != null) {
                var level = player.serverLevel();
                var pos = player.blockPosition();
                if (thaumcraft.world.aura.AuraManager.getFlux(level, pos) > thaumcraft.world.aura.AuraManager.getVis(level, pos)
                        || thaumcraft.world.aura.AuraManager.getFlux(level, pos) > thaumcraft.world.aura.AuraManager.getAuraBase(level, pos) / 3)
                    thaumcraft.research.KnowledgeStore.startFluxResearch(player);
            }
            ScanningNetwork.sendHud(player);
        }
    }

    private static void reload(AddReloadListenerEvent event) {
        event.addListener(new AspectRegistry.Loader(event.getServerResources().getRecipeManager(), event.getRegistryAccess()));
    }

    // Handle these before the target's normal interaction (opening a chest, riding a horse,
    // rotating a frame, etc.). The packet's supplied target is deliberately not used.
    private static void interact(PlayerInteractEvent.EntityInteract event) { scan(event); }
    private static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) { scan(event); }
    private static void interactBlock(PlayerInteractEvent.RightClickBlock event) { scan(event); }

    private static void scan(PlayerInteractEvent event) {
        if (event.getItemStack().getItem() instanceof ThaumometerItem scanner) {
            scanner.scan(event.getEntity(), event.getHand());
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }
}
