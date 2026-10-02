package thaumcraft.equipment.recharge;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.blocks.CatalogBlocks;

public final class RechargeModule {
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"thaumcraft");
    public static final RegistryObject<BlockEntityType<RechargePedestalBlockEntity>> PEDESTAL = TILES.register("recharge_pedestal",
            () -> BlockEntityType.Builder.of(RechargePedestalBlockEntity::new,CatalogBlocks.ENTRIES.get("recharge_pedestal").get()).build(null));
    private RechargeModule() {}
    public static void register(IEventBus bus) { TILES.register(bus); }
}
