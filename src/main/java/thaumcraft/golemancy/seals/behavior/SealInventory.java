package thaumcraft.golemancy.seals.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import thaumcraft.golemancy.seals.core.SealPos;
import thaumcraft.golemancy.seals.core.SealWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/** Sided capability payment, with detached snapshots and rollback of hostile callbacks. */
public final class SealInventory {
    private SealInventory() {}
    private static final Set<Object> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    public static IItemHandler handler(ServerLevel level, SealPos at) {
        if (!level.hasChunkAt(at.pos())) return null;
        var tile = level.getBlockEntity(at.pos());
        return tile == null ? null : tile.getCapability(ForgeCapabilities.ITEM_HANDLER, at.face()).orElse(null);
    }
    public static int count(IItemHandler handler, Predicate<ItemStack> matches) {
        if (handler == null || handler.getSlots() > 256) return 0;
        long count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i).copy();
            if (!stack.isEmpty() && matches.test(stack)) count += stack.getCount();
        }
        return (int)Math.min(Integer.MAX_VALUE, count);
    }
    public static ItemStack first(IItemHandler handler, Predicate<ItemStack> matches, boolean leaveOne) {
        if (handler == null || handler.getSlots() > 256) return ItemStack.EMPTY;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i).copy();
            if (!candidate.isEmpty() && matches.test(candidate)
                    && (!leaveOne || count(handler, s -> ItemStack.isSameItemSameTags(candidate,s)) > 1)
                    && !handler.extractItem(i,1,true).isEmpty()) return candidate;
        }
        return ItemStack.EMPTY;
    }
    public static int room(IItemHandler handler, ItemStack offered) {
        if (!(handler instanceof IItemHandlerModifiable mutable) || handler.getSlots()>256 || offered.isEmpty()) return 0;
        var before=snapshot(handler);
        ItemStack remaining = offered.copy();
        try{for (int i=0;i<handler.getSlots()&&!remaining.isEmpty();i++)remaining=handler.insertItem(i,remaining,true);}
        catch(RuntimeException rejected){restore(mutable,before);return 0;}
        if(!same(handler,before)){restore(mutable,before);return 0;}
        return Math.max(0,offered.getCount()-remaining.getCount());
    }
    /** Extract one exact physical stack family, never a loose-NBT equivalent into a changed template. */
    public static int take(ServerLevel level, SealPos at, SealWorker golem, ItemStack template, int requested) {
        IItemHandler endpoint = handler(level,at);
        if (!(endpoint instanceof IItemHandlerModifiable mutable) || endpoint.getSlots()>256 || template.isEmpty()) return 0;
        if(!ACTIVE.add(endpoint))return 0;
        try{return takeLocked(level,at,golem,template,requested,endpoint,mutable);}finally{ACTIVE.remove(endpoint);}
    }
    private static int takeLocked(ServerLevel level,SealPos at,SealWorker golem,ItemStack template,int requested,IItemHandler endpoint,IItemHandlerModifiable mutable) {
        int remaining = Math.min(Math.max(0,requested),golem.canCarryAmount(template));
        if (remaining<=0) return 0;
        var before=snapshot(endpoint);
        var planned=copy(before);
        int total=0;
        try{for(int i=0;i<planned.size()&&remaining>0;i++) {
                ItemStack stack=planned.get(i);
                if(!ItemStack.isSameItemSameTags(template,stack))continue;
                ItemStack simulated=endpoint.extractItem(i,Math.min(remaining,stack.getCount()),true);
                if(simulated.isEmpty()||!ItemStack.isSameItemSameTags(template,simulated))continue;
                int n=Math.min(remaining,Math.min(stack.getCount(),simulated.getCount()));
                stack.shrink(n);remaining-=n;total+=n;
            }}catch(RuntimeException rejected){restore(mutable,before);return 0;}
        if(total==0||handler(level,at)!=endpoint||!same(endpoint,before)){if(!same(endpoint,before))restore(mutable,before);return 0;}
        try {
            for(int i=0;i<before.size();i++) {
                int n=before.get(i).getCount()-planned.get(i).getCount();
                if(n<=0)continue;
                ItemStack extracted=endpoint.extractItem(i,n,false);
                if(extracted.getCount()!=n||!ItemStack.isSameItemSameTags(template,extracted)
                        ||handler(level,at)!=endpoint||!sameExcept(endpoint,planned,before,i)) {
                    restore(mutable,before);return 0;
                }
            }
            if(!same(endpoint,planned)){restore(mutable,before);return 0;}
            ItemStack offered=template.copyWithCount(total);
            ItemStack back=golem.holdItem(offered);
            int retained=total-back.getCount();
            if(!back.isEmpty()) {
                // Reentrant/custom workers cannot destroy a paid remainder.
                for(int i=0;i<before.size()&&!back.isEmpty();i++)back=endpoint.insertItem(i,back,false);
                if(!back.isEmpty())golem.mob().spawnAtLocation(back);
            }
            return retained;
        } catch(RuntimeException rejected) {restore(mutable,before);return 0;}
    }
    public static int deliver(ServerLevel level, SealPos at, SealWorker golem, ItemStack template, int requested) {
        IItemHandler endpoint=handler(level,at);
        if(!(endpoint instanceof IItemHandlerModifiable mutable)||endpoint.getSlots()>256||template.isEmpty())return 0;
        if(!ACTIVE.add(endpoint))return 0;
        try{return deliverLocked(level,at,golem,template,requested,endpoint,mutable);}finally{ACTIVE.remove(endpoint);}
    }
    private static int deliverLocked(ServerLevel level,SealPos at,SealWorker golem,ItemStack template,int requested,IItemHandler endpoint,IItemHandlerModifiable mutable) {
        ItemStack offered=template.copyWithCount(Math.min(template.getCount(),Math.max(0,requested)));
        var before=snapshot(endpoint);
        int accepted;
        try{accepted=room(endpoint,offered);}catch(RuntimeException rejected){restore(mutable,before);return 0;}
        if(!same(endpoint,before)){restore(mutable,before);return 0;}
        if(accepted<=0||handler(level,at)!=endpoint)return 0;
        ItemStack paid=golem.dropItem(offered.copyWithCount(accepted));
        if(paid.isEmpty())return 0;
        try {
            ItemStack rest=paid.copy();
            for(int i=0;i<endpoint.getSlots()&&!rest.isEmpty();i++)rest=endpoint.insertItem(i,rest,false);
            if(handler(level,at)!=endpoint){restore(mutable,before);returnBack(golem,paid);return 0;}
            int added=paid.getCount()-rest.getCount();
            // Only the offered stack family may change. Item handlers are arbitrary callbacks.
            if(!validInsertion(before,snapshot(endpoint),paid,added)){restore(mutable,before);returnBack(golem,paid);return 0;}
            returnBack(golem,rest);return added;
        } catch(RuntimeException rejected){restore(mutable,before);returnBack(golem,paid);return 0;}
    }
    private static boolean validInsertion(List<ItemStack> before,List<ItemStack> after,ItemStack offered,int added) {
        if(before.size()!=after.size()||added<0)return false;
        long delta=0;
        for(int i=0;i<before.size();i++) {
            ItemStack a=before.get(i),b=after.get(i);
            if(ItemStack.matches(a,b))continue;
            if(!b.isEmpty()&&!ItemStack.isSameItemSameTags(offered,b))return false;
            if(!a.isEmpty()&&!ItemStack.isSameItemSameTags(offered,a))return false;
            if(b.getCount()<a.getCount())return false;
            delta+=b.getCount()-a.getCount();
        }
        return delta==added;
    }
    private static void returnBack(SealWorker golem,ItemStack stack){if(!stack.isEmpty()){ItemStack rest=golem.holdItem(stack);if(!rest.isEmpty())golem.mob().spawnAtLocation(rest);}}
    private static List<ItemStack> snapshot(IItemHandler handler){List<ItemStack> out=new ArrayList<>();for(int i=0;i<handler.getSlots();i++)out.add(handler.getStackInSlot(i).copy());return out;}
    private static List<ItemStack> copy(List<ItemStack> source){return source.stream().map(ItemStack::copy).toList();}
    private static boolean same(IItemHandler handler,List<ItemStack> state){if(handler.getSlots()!=state.size())return false;for(int i=0;i<state.size();i++)if(!ItemStack.matches(handler.getStackInSlot(i),state.get(i)))return false;return true;}
    private static boolean sameExcept(IItemHandler handler,List<ItemStack> after,List<ItemStack> before,int last){if(handler.getSlots()!=before.size())return false;for(int i=0;i<before.size();i++)if(!ItemStack.matches(handler.getStackInSlot(i),i<=last?after.get(i):before.get(i)))return false;return true;}
    private static void restore(IItemHandlerModifiable handler,List<ItemStack> snapshot){for(int i=snapshot.size()-1;i>=0;i--)handler.setStackInSlot(i,snapshot.get(i).copy());}
}
