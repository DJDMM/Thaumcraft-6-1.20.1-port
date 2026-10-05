package thaumcraft.golemancy.seals.core;

import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An instance is shared immutable behavior; all mutable per-seal state lives in SealData.runtime(). */
public interface SealBehavior {
    String key();
    default int filterSlots() {return 0;}
    default boolean hasStacksizeLimiters() {return false;}
    default boolean hasArea() {return false;}
    default List<Integer> categories() {return List.of(0,4);}
    default Map<String,Boolean> toggleDefaults() {return Map.of();}
    default Set<String> requiredTraits() {return Set.of();}
    default Set<String> forbiddenTraits() {return Set.of();}
    boolean canPlace(ServerLevel level,SealPos position);
    void tick(ServerLevel level,SealData seal,SealService service);
    boolean canPerform(ServerLevel level,SealData seal,SealWorker golem,SealTask task);
    default void onStarted(ServerLevel level,SealData seal,SealWorker golem,SealTask task) {}
    boolean onCompletion(ServerLevel level,SealData seal,SealWorker golem,SealTask task);
    default void onSuspension(ServerLevel level,SealData seal,SealTask task) {}
    default void onRemoval(ServerLevel level,SealData seal) {}
}
