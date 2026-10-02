package thaumcraft.catalog;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;

/** Original JSON predicates and tint layers, independent of future item mechanics. */
@Mod.EventBusSubscriber(modid = Thaumcraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CatalogItemClientEvents {
    private CatalogItemClientEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            register("primordial_pearl", "type", (stack, level, entity, seed) -> stack.getDamageValue() < 3 ? 0 : stack.getDamageValue() < 6 ? 1 : 2);
            register("verdant_charm", "type", (stack, level, entity, seed) -> stack.hasTag() ? stack.getTag().getByte("type") : 0);
            register("grapple_gun", "type", (stack, level, entity, seed) -> stack.hasTag() && stack.getTag().getByte("loaded") == 1 ? 1 : 0);
            register("caster_basic", "focus", (stack, level, entity, seed) -> stack.hasTag()
                    && stack.getTag().contains("focus", 10)
                    && !ItemStack.of(stack.getTag().getCompound("focus")).isEmpty() ? 1 : 0);
            for (String id : new String[]{"jar_normal", "jar_void"}) {
                registerBlockItem(id, "fill", (stack, level, entity, seed) -> CatalogModule.jarFillLevel(stack));
            }
            for (String id : new String[]{"mirror", "mirror_essentia"}) {
                registerBlockItem(id, "linked", (stack, level, entity, seed) -> stack.getDamageValue() == 1 ? 1 : 0);
            }
        });
    }

    // BETA26's type predicate has value 2; a ClampedItemPropertyFunction would
    // truncate it to 1 and hide the mote/third charm appearances.
    private static void register(String id, String property, net.minecraft.client.renderer.item.ItemPropertyFunction getter) {
        var entry = CatalogModule.ENTRIES.get(id);
        if (entry != null) ItemProperties.register(entry.get(), ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, property), getter);
    }

    private static void registerBlockItem(String id, String property, net.minecraft.client.renderer.item.ItemPropertyFunction getter) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, id));
        if (item != null && item != net.minecraft.world.item.Items.AIR)
            ItemProperties.register(item, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MOD_ID, property), getter);
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item event) {
        thaumcraft.catalog.blocks.CatalogBlocks.ENTRIES.forEach((id,block) -> {
            if(id.startsWith("candle_"))event.register((stack,tint) -> tint==0?thaumcraft.catalog.blocks.client.CatalogBlockRenderer.color(id):-1,block.get());
            if(id.equals("jar_normal") || id.equals("jar_void")) event.register((stack,tint) -> {
                Aspect aspect = CatalogModule.containedAspect(stack);
                return tint == 1 && aspect != null ? aspect.getColor() : -1;
            },block.get());
        });
        for (var entry : CatalogModule.ENTRIES.entrySet()) {
            String id = entry.getKey();
            if (id.equals("crystal_essence") || id.equals("phial_filled") || id.equals("label_filled")) {
                event.register((stack, tint) -> {
                    Aspect aspect = CatalogModule.containedAspect(stack);
                    return aspect != null && (id.equals("crystal_essence") || tint == 1) ? aspect.getColor() : -1;
                }, entry.getValue().get());
            } else if (java.util.List.of("focus_1", "focus_2", "focus_3").contains(id)) {
                event.register((stack, tint) -> stack.hasTag() && stack.getTag().contains("color") ? stack.getTag().getInt("color") : -1, entry.getValue().get());
            } else if (id.equals("golem")) {
                event.register((stack, tint) -> CatalogModule.golemItemColor(stack), entry.getValue().get());
            } else if (id.equals("caster_basic")) {
                event.register((stack, tint) -> {
                    if (tint > 0 && stack.hasTag() && stack.getTag().contains("focus", 10)) {
                        var focus = ItemStack.of(stack.getTag().getCompound("focus"));
                        if (focus.hasTag() && focus.getTag().contains("color")) return focus.getTag().getInt("color");
                    }
                    return -1;
                }, entry.getValue().get());
            } else if (id.startsWith("cloth_") || id.startsWith("void_robe_")) {
                event.register((stack, tint) -> tint == 0 ? clothColor(stack) : -1, entry.getValue().get());
            }
        }
    }

    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event) {
        thaumcraft.catalog.blocks.CatalogBlocks.ENTRIES.forEach((id,block) -> {
            if(id.startsWith("candle_"))event.register((state,world,pos,tint) -> tint==0?thaumcraft.catalog.blocks.client.CatalogBlockRenderer.color(id):-1,block.get());
        });
    }

    private static int clothColor(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().getCompound("display").contains("color"))
            return stack.getTag().getCompound("display").getInt("color");
        // Both ItemRobeArmor and ItemVoidRobeArmor use 6961280 in the BETA26 JAR.
        return 6961280;
    }
}
