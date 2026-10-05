package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.*;
import thaumcraft.catalog.CatalogModule;
import thaumcraft.research.KnowledgeStore;
import java.util.*;

/** Exactly sixteen original seal types (plus the blank item), in BETA26 metadata order. */
@Mod.EventBusSubscriber(modid="thaumcraft")
public final class SealRegistry {
    public static final List<String> KEYS=List.of("thaumcraft:pickup","thaumcraft:pickup_advanced","thaumcraft:fill","thaumcraft:fill_advanced","thaumcraft:empty","thaumcraft:empty_advanced","thaumcraft:harvest","thaumcraft:butcher","thaumcraft:guard","thaumcraft:guard_advanced","thaumcraft:lumber","thaumcraft:breaker","thaumcraft:use","thaumcraft:provider","thaumcraft:stock","thaumcraft:breaker_advanced");
    private static final LinkedHashMap<String,SealBehavior> BEHAVIORS=new LinkedHashMap<>();
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"thaumcraft");
    public static final RegistryObject<MenuType<SealMenu>> MENU=MENUS.register("seal",()->IForgeMenuType.create(SealMenu::new));
    public static final RegistryObject<MenuType<SealLogisticsMenu>> LOGISTICS_MENU=MENUS.register("golem_logistics",()->IForgeMenuType.create(SealLogisticsMenu::new));
    private SealRegistry(){}
    public static void register(IEventBus bus){MENUS.register(bus);SealNetwork.register();}
    public static void registerBehavior(SealBehavior behavior){Objects.requireNonNull(behavior);if(!KEYS.contains(behavior.key())||behavior.filterSlots()<0||behavior.filterSlots()>9||behavior.toggleDefaults().size()>16||behavior.categories().isEmpty()||BEHAVIORS.putIfAbsent(behavior.key(),behavior)!=null)throw new IllegalArgumentException("Invalid/duplicate BETA26 seal "+behavior.key());}
    public static SealBehavior behavior(String key){return BEHAVIORS.get(key);}
    public static List<SealBehavior> behaviors(){return KEYS.stream().map(BEHAVIORS::get).filter(Objects::nonNull).toList();}
    public static String keyForMetadata(int metadata){return metadata>0&&metadata<=KEYS.size()?KEYS.get(metadata-1):null;}
    public static String keyForItem(String id){return id.startsWith("seal_")&&!id.equals("seal_blank")?"thaumcraft:"+id.substring(5):null;}
    public static ItemStack stack(String key){return KEYS.contains(key)?CatalogModule.stack("seal_"+key.substring(key.indexOf(':')+1)):ItemStack.EMPTY;}
    /** Original placement has no research check: acquired seal items still work. Recipes carry their own research gates. */
    public static boolean place(ServerPlayer player,SealPos position,String type,ItemStack held){
        ServerLevel level=player.serverLevel();SealBehavior behavior=behavior(type);
        if(behavior==null||held==null||held.isEmpty()||!player.isAlive()||player.isSpectator()||player.isShiftKeyDown()||!level.hasChunkAt(position.pos())||player.distanceToSqr(position.pos().getCenter())>64||!level.mayInteract(player,position.pos())||!player.mayUseItemAt(position.pos(),position.face(),held))return false;
        if(held.getItem()!=stack(type).getItem())return false;
        SealService service=SealService.get(level);if(service.seal(position)!=null)return false;ItemStack before=held.copy();
        try{
            if(!behavior.canPlace(level,position)||!ItemStack.matches(before,held)||service.seal(position)!=null)return false;
            SealData seal=new SealData(position,type,player.getUUID());
            if(!service.add(seal))return false;
            if(player.level()!=level||!player.isAlive()||!ItemStack.matches(before,held)||service.seal(position)!=seal){service.remove(position,true);return false;}
            if(!player.getAbilities().instabuild)held.shrink(1);player.getInventory().setChanged();return true;
        }catch(RuntimeException invalid){return false;}
    }
    /** Bell route consumed before golem home handling. Locked ownership also protects configuration/removal. */
    public static InteractionResult useBell(UseOnContext context){
        if(!(context.getPlayer() instanceof ServerPlayer player))return context.getLevel().isClientSide?InteractionResult.SUCCESS:InteractionResult.PASS;
        SealPos pos=new SealPos(context.getClickedPos(),context.getClickedFace());SealService service=SealService.get(player.serverLevel());SealData seal=service.seal(pos);
        if(seal!=null){
            if(!service.canConfigure(player,seal))return InteractionResult.FAIL;
            if(player.isShiftKeyDown()){if(!seal.owner().equals(player.getUUID()))return InteractionResult.FAIL;service.remove(pos,false);player.serverLevel().playSound(null,pos.pos(),SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,.5f,1);}
            else open(player,seal);
            return InteractionResult.CONSUME;
        }
        if(player.isShiftKeyDown()&&KnowledgeStore.get(player).isResearchKnown("GOLEMLOGISTICS")){openLogistics(player,context.getClickedPos(),context.getClickedFace());return InteractionResult.CONSUME;}
        return InteractionResult.PASS;
    }
    public static void open(ServerPlayer player,SealData seal){
        if(!SealService.get(player.serverLevel()).canConfigure(player,seal))return;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p)->new SealMenu(id,inventory,seal),Component.translatable("item.seal."+seal.type().substring(seal.type().indexOf(':')+1)+".name")),buffer->buffer.writeNbt(seal.save()));
    }
    public static boolean openLogistics(ServerPlayer player,BlockPos target,Direction side){
        if(player==null||!player.isAlive()||player.isSpectator()||!KnowledgeStore.get(player).isResearchKnown("GOLEMLOGISTICS")||(target!=null&&(side==null||!player.serverLevel().hasChunkAt(target)||player.distanceToSqr(target.getCenter())>64||!player.serverLevel().mayInteract(player,target))))return false;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p)->new SealLogisticsMenu(id,inventory,target,side),Component.translatable("golem.logistics")),buffer->{buffer.writeBoolean(target!=null);if(target!=null){buffer.writeBlockPos(target);buffer.writeEnum(side);}});return true;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)SealService.get(level).tick();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)SealNetwork.syncAll(player);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)SealNetwork.syncAll(player);}
    @SubscribeEvent public static void watch(net.minecraftforge.event.level.ChunkWatchEvent.Watch event){SealNetwork.syncChunk(event.getPlayer(),event.getPos());}
}
