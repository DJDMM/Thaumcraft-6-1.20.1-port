package thaumcraft.golemancy.seals.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Persisted original seal settings; task caches and scan counters deliberately do not survive saves. */
public final class SealData {
    private final SealPos position;
    private final String type;
    private final UUID owner;
    private int priority,color,revision;
    private boolean locked,redstone,blacklist=true;
    private BlockPos area=new BlockPos(1,1,1);
    private final List<ItemStack> filters=new ArrayList<>();
    private final List<Integer> filterSizes=new ArrayList<>();
    private final LinkedHashMap<String,Boolean> toggles=new LinkedHashMap<>();
    private final CompoundTag runtime=new CompoundTag();
    public SealData(SealPos position,String type,UUID owner) {
        this.position=Objects.requireNonNull(position);this.type=Objects.requireNonNull(type);this.owner=Objects.requireNonNull(owner);
        SealBehavior behavior=SealRegistry.behavior(type);
        if(behavior!=null) {
            for(int i=0;i<behavior.filterSlots();i++){filters.add(ItemStack.EMPTY);filterSizes.add(0);}
            toggles.putAll(behavior.toggleDefaults());
            if(behavior.hasArea())area=new BlockPos(position.face().getStepX()==0?3:1,position.face().getStepY()==0?3:1,position.face().getStepZ()==0?3:1);
        }
    }
    public SealPos position(){return position;}
    public String type(){return type;}
    public UUID owner(){return owner;}
    public int priority(){return priority;}
    public void priority(int value){priority=Math.max(-5,Math.min(5,value));}
    public int color(){return color;}
    public void color(int value){color=Math.max(0,Math.min(16,value));}
    public boolean locked(){return locked;}
    public void locked(boolean value){locked=value;}
    public boolean redstone(){return redstone;}
    public void redstone(boolean value){redstone=value;}
    public BlockPos area(){return area;}
    public void area(BlockPos value){area=new BlockPos(bound(value.getX()),bound(value.getY()),bound(value.getZ()));}
    private static int bound(int value){return Math.max(1,Math.min(8,value));}
    public int revision(){return revision;}
    public void changed(){revision=revision==Integer.MAX_VALUE?0:revision+1;}
    public boolean blacklist(){return blacklist;}
    public void blacklist(boolean value){blacklist=value;}
    public List<ItemStack> filters(){return filters.stream().map(ItemStack::copy).toList();}
    public ItemStack filter(int slot){return slot>=0&&slot<filters.size()?filters.get(slot).copy():ItemStack.EMPTY;}
    public void filter(int slot,ItemStack stack){if(slot>=0&&slot<filters.size()){ItemStack copy=stack.copy();if(!copy.isEmpty())copy.setCount(1);filters.set(slot,copy);}}
    public List<Integer> filterSizes(){return List.copyOf(filterSizes);}
    public int filterSize(int slot){return slot>=0&&slot<filterSizes.size()?filterSizes.get(slot):0;}
    public void filterSize(int slot,int value){if(slot>=0&&slot<filterSizes.size())filterSizes.set(slot,Math.max(0,Math.min(Integer.MAX_VALUE-64,value)));}
    public Map<String,Boolean> toggles(){return Collections.unmodifiableMap(new LinkedHashMap<>(toggles));}
    public boolean toggle(String key){return toggles.getOrDefault(key,false);}
    public void toggle(String key,boolean value){if(toggles.containsKey(key))toggles.put(key,value);}
    public CompoundTag runtime(){return runtime;}
    public boolean matches(ItemStack stack) {
        if(stack==null||stack.isEmpty())return false;
        for(ItemStack filter:filters)if(!filter.isEmpty()&&matchesFilter(filter,stack))return !blacklist;
        return blacklist;
    }
    public boolean matchesFilter(ItemStack filter,ItemStack stack) {
        if(filter==null||stack==null||filter.isEmpty()||stack.isEmpty())return false;
        if(toggle("pmod"))return ForgeRegistries.ITEMS.getKey(filter.getItem()).getNamespace().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace());
        // Modern Forge item tags replace the removed OreDictionary. Only forge/c item groups participate.
        if(toggle("pore")) {
            Set<ResourceLocation> names=new HashSet<>();
            filter.getTags().map(t->t.location()).filter(SealData::oreTag).forEach(names::add);
            CompoundTag candidate=stack.hasTag()?stack.getTag().copy():null;if(candidate!=null){candidate.remove("Damage");if(candidate.isEmpty())candidate=null;}
            if(candidate==null&&stack.getTags().anyMatch(t->names.contains(t.location())))return true;
        }
        LegacyForm first=legacy(filter),second=legacy(stack);
        if(!first.item().equals(second.item()))return false;
        if(toggles.getOrDefault("pmeta",true)&&first.metadata()!=32767&&second.metadata()!=32767&&first.metadata()!=second.metadata())return false;
        if(!toggles.getOrDefault("pnbt",true))return true;
        // Legacy damage was metadata rather than an NBT member; compare other tags independently.
        CompoundTag a=filter.hasTag()?filter.getTag().copy():null,b=stack.hasTag()?stack.getTag().copy():null;
        if(a!=null){a.remove("Damage");if(a.isEmpty())a=null;}if(b!=null){b.remove("Damage");if(b.isEmpty())b=null;}
        return Objects.equals(a,b);
    }
    private static boolean oreTag(ResourceLocation key){
        if(!key.getNamespace().equals("forge")&&!key.getNamespace().equals("c"))return false;
        String path=key.getPath();int slash=path.indexOf('/');if(slash<0)return false;
        return Set.of("ingots","nuggets","dusts","gems","ores","storage_blocks","rods","plates","raw_materials").contains(path.substring(0,slash));
    }
    private record LegacyForm(String item,int metadata){}
    private static final class Forms {
        static final Map<String,thaumcraft.catalog.CatalogModule.Spec> ITEMS=new HashMap<>();
        static {for(var spec:thaumcraft.catalog.CatalogModule.SPECS)ITEMS.put(spec.id(),spec);}
    }
    private static LegacyForm legacy(ItemStack stack){
        ResourceLocation key=ForgeRegistries.ITEMS.getKey(stack.getItem());String path=key.getPath();
        var spec=key.getNamespace().equals("thaumcraft")?Forms.ITEMS.get(path):null;
        if(spec!=null)return new LegacyForm("thaumcraft:"+spec.legacyItem(),stack.isDamageableItem()?stack.getDamageValue():spec.legacyMetadata());
        if(key.getNamespace().equals("minecraft")){
            for(net.minecraft.world.item.DyeColor color:net.minecraft.world.item.DyeColor.values()){
                String prefix=color.getName()+"_";if(!path.startsWith(prefix))continue;String family=path.substring(prefix.length());
                if(Set.of("wool","carpet","stained_glass","stained_glass_pane","terracotta","concrete","concrete_powder","dye").contains(family))return new LegacyForm("minecraft:"+family,family.equals("dye")?15-color.getId():color.getId());
            }
            if(path.equals("ink_sac"))return new LegacyForm("minecraft:dye",0);
            if(path.equals("bone_meal"))return new LegacyForm("minecraft:dye",15);
            if(path.equals("lapis_lazuli"))return new LegacyForm("minecraft:dye",4);
            if(path.equals("cocoa_beans"))return new LegacyForm("minecraft:dye",3);
            List<String> woods=List.of("oak","spruce","birch","jungle","acacia","dark_oak");
            for(int i=0;i<woods.size();i++)if(path.equals(woods.get(i)+"_planks"))return new LegacyForm("minecraft:planks",i);
        }
        return new LegacyForm(key.toString(),stack.getDamageValue());
    }
    public BlockPos posInArea(int count) {
        int fx=position.face().getStepX(),fy=position.face().getStepY(),fz=position.face().getStepZ();
        int xx=1+(area.getX()-1)*(fx==0?2:1),yy=1+(area.getY()-1)*(fy==0?2:1),zz=1+(area.getZ()-1)*(fz==0?2:1);
        int qx=fx==0?1:fx,qy=fy==0?1:fy,qz=fz==0?1:fz;
        int y=qy*((count/zz)/xx)%yy+fy,x=qx*(count/zz)%xx+fx,z=qz*count%zz+fz;
        return position.pos().offset(x-(fx==0?xx/2:0),y-(fy==0?yy/2:0),z-(fz==0?zz/2:0));
    }
    public int areaVolume(){int fx=position.face().getStepX(),fy=position.face().getStepY(),fz=position.face().getStepZ();return (1+(area.getX()-1)*(fx==0?2:1))*(1+(area.getY()-1)*(fy==0?2:1))*(1+(area.getZ()-1)*(fz==0?2:1));}
    public AABB bounds() {
        int fx=position.face().getStepX(),fy=position.face().getStepY(),fz=position.face().getStepZ();
        return new AABB(position.pos()).move(fx,fy,fz).expandTowards(fx==0?0:(area.getX()-1)*fx,fy==0?0:(area.getY()-1)*fy,fz==0?0:(area.getZ()-1)*fz).inflate(fx==0?area.getX()-1:0,fy==0?area.getY()-1:0,fz==0?area.getZ()-1:0);
    }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag();tag.putLong("pos",position.pos().asLong());tag.putByte("face",(byte)position.face().ordinal());tag.putString("type",type);tag.putUUID("Owner",owner);
        tag.putString("owner",owner.toString());tag.putByte("priority",(byte)priority);tag.putByte("color",(byte)color);tag.putBoolean("locked",locked);tag.putBoolean("redstone",redstone);tag.putLong("area",area.asLong());tag.putBoolean("bl",blacklist);tag.putInt("revision",revision);
        ListTag items=new ListTag(),sizes=new ListTag();for(int i=0;i<filters.size();i++){
            if(!filters.get(i).isEmpty()){CompoundTag entry=new CompoundTag();entry.putByte("Slot",(byte)i);filters.get(i).save(entry);items.add(entry);}
            if(filterSizes.get(i)!=0){CompoundTag entry=new CompoundTag();entry.putByte("Slot",(byte)i);entry.putInt("Size",filterSizes.get(i));sizes.add(entry);}
        }tag.put("Items",items);tag.put("Sizes",sizes);toggles.forEach(tag::putBoolean);thaumcraft.golemancy.seals.behavior.SealBehaviors.writeCustom(this,tag);return tag;
    }
    public static SealData load(CompoundTag tag) {
        int face=tag.getByte("face");String type=tag.getString("type");if(face<0||face>=6||SealRegistry.behavior(type)==null)return null;
        UUID owner;try{owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):UUID.fromString(tag.getString("owner"));}catch(IllegalArgumentException invalid){return null;}
        SealData out=new SealData(new SealPos(BlockPos.of(tag.getLong("pos")),net.minecraft.core.Direction.values()[face]),type,owner);
        out.priority(tag.getByte("priority"));out.color(tag.getByte("color"));out.locked(tag.getBoolean("locked"));out.redstone(tag.getBoolean("redstone"));out.blacklist(tag.getBoolean("bl"));
        if(tag.contains("area",Tag.TAG_LONG))out.area(BlockPos.of(tag.getLong("area")));out.revision=Math.max(0,tag.getInt("revision"));
        ListTag items=tag.getList("Items",Tag.TAG_COMPOUND);for(int i=0;i<items.size();i++){CompoundTag entry=items.getCompound(i);out.filter(entry.getByte("Slot")&255,ItemStack.of(entry));}
        ListTag sizes=tag.getList("Sizes",Tag.TAG_COMPOUND);for(int i=0;i<sizes.size();i++){CompoundTag entry=sizes.getCompound(i);out.filterSize(entry.getByte("Slot")&255,entry.getInt("Size"));}
        for(String key:out.toggles.keySet())if(tag.contains(key,Tag.TAG_BYTE))out.toggle(key,tag.getBoolean(key));thaumcraft.golemancy.seals.behavior.SealBehaviors.readCustom(out,tag);return out;
    }
    public SealData copy(){return load(save());}
}
