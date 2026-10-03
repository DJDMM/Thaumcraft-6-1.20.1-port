package thaumcraft.world;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.function.BiConsumer;

/** Worker-safe coordinates, fair bounded server callbacks, and no chunk promotion/loading. */
public final class LoadedChunkMigrationQueue {
    private static final class Pending {
        final long coordinate; int waits;
        Pending(long coordinate){this.coordinate=coordinate;}
    }
    private static final class State {
        final ConcurrentHashMap<Long,Pending> pending=new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Pending> order=new ConcurrentLinkedQueue<>();
    }
    private final ConcurrentHashMap<ServerLevel,State> levels=new ConcurrentHashMap<>();
    public void load(ServerLevel level,LevelChunk chunk){
        State state=levels.computeIfAbsent(level,ignored->new State());var node=new Pending(chunk.getPos().toLong());
        if(state.pending.putIfAbsent(node.coordinate,node)==null)state.order.offer(node);
    }
    public void unload(ServerLevel level,ChunkPos pos){var state=levels.get(level);if(state!=null)state.pending.remove(pos.toLong());}
    public void stop(MinecraftServer server){levels.keySet().removeIf(level->level.getServer()==server);}
    public void process(ServerLevel level,BiConsumer<ServerLevel,LevelChunk> repair){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Chunk migration outside server thread");
        var state=levels.get(level);if(state==null)return;
        // Detach before requeueing so even concurrent loads cannot make a coordinate receive
        // two attempts in one END callback. Unready chunks move behind queued loaded chunks.
        var batch=new ArrayList<Pending>(64);
        for(int i=0;i<64;i++){var node=state.order.poll();if(node==null)break;batch.add(node);}
        for(Pending node:batch){
            if(state.pending.get(node.coordinate)!=node)continue;
            var pos=new ChunkPos(node.coordinate);var chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);
            if(chunk==null){
                if(node.waits++>=200)state.pending.remove(node.coordinate,node);
                else state.order.offer(node);
            }else if(state.pending.remove(node.coordinate,node))repair.accept(level,chunk);
        }
    }
}
