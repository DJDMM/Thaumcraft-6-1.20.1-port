package thaumcraft.golemancy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.golemancy.seals.core.*;
import java.util.*;

/** Detached dimension-local presentation. Client copies cannot schedule or pay for tasks. */
public final class SealClientState {
    private static ClientLevel world;
    private static final Map<SealPos,SealData> SEALS=new LinkedHashMap<>();
    private SealClientState(){}
    private static void select(){ClientLevel current=Minecraft.getInstance().level;if(current!=world){SEALS.clear();world=current;}}
    public static void update(CompoundTag tag,boolean remove){select();if(world==null)return;if(remove){int face=tag.getByte("face");if(face>=0&&face<6)SEALS.remove(new SealPos(net.minecraft.core.BlockPos.of(tag.getLong("pos")),net.minecraft.core.Direction.values()[face]));return;}SealData data=SealData.load(tag);if(data!=null)SEALS.put(data.position(),data);}
    public static void replace(List<CompoundTag> tags){select();SEALS.clear();if(world!=null)for(CompoundTag tag:tags)update(tag,false);}
    public static void clear(){SEALS.clear();world=null;}
    public static List<SealData> seals(){select();return SEALS.values().stream().map(SealData::copy).toList();}
}
