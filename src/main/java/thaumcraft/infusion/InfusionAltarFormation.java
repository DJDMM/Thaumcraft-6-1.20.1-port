package thaumcraft.infusion;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.KnowledgeStore;
import java.util.*;

/** Atomic eight-stone dust ritual; the matrix and central pedestal contents are never replaced. */
public final class InfusionAltarFormation {
    private InfusionAltarFormation() {}
    public static InteractionResult use(UseOnContext context) {
        var level = context.getLevel(); BlockPos matrix = matrixAtPart(level,context.getClickedPos());
        if (matrix == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player) || !player.mayBuild() || player.isSpectator()) return InteractionResult.FAIL;
        var knowledge = KnowledgeStore.get(player); String material = null;
        for (String type : List.of("arcane", "ancient", "eldritch")) {
            String research = type.equals("arcane") ? "INFUSION" : type.equals("ancient") ? "INFUSIONANCIENT" : "INFUSIONELDRITCH";
            if (!knowledge.isResearchKnown(research) || !level.hasChunkAt(matrix.below(2))) continue;
            var central = level.getBlockState(matrix.below(2));
            int charge = type.equals("ancient") ? 2 : type.equals("eldritch") ? 1 : 0;
            if (!central.is(CatalogBlocks.block("pedestal_" + type)) || central.getValue(InfusionPedestalBlock.CHARGE) != charge) continue;
            boolean valid = true;
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) for (int y : new int[]{-1, -2}) {
                BlockPos pos = matrix.offset(x, y, z);
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(CatalogBlocks.block("stone_" + type))) valid = false;
            }
            if (valid) { material = type; break; }
        }
        if (material == null) return InteractionResult.FAIL;
        Map<BlockPos, BlockState> replacements = new LinkedHashMap<>();
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            var pillar = CatalogBlocks.block("pillar_" + material).defaultBlockState();
            var facing = pillar.getBlock().getStateDefinition().getProperty("facing");
            // The original blueprint's four metadata values face into its paired corners.
            pillar = withValue(pillar, facing, x < 0 ? z < 0 ? "east" : "north" : z < 0 ? "south" : "west");
            replacements.put(matrix.offset(x, -2, z), pillar); replacements.put(matrix.offset(x, -1, z), Blocks.AIR.defaultBlockState());
        }
        List<BlockSnapshot> before = new ArrayList<>();
        replacements.keySet().forEach(pos -> before.add(BlockSnapshot.create(level.dimension(), level, pos)));
        boolean capturing = level.captureBlockSnapshots; int start = level.capturedBlockSnapshots.size(); boolean committed = false;
        level.captureBlockSnapshots = true;
        try {
            for (var entry : replacements.entrySet()) if (!level.setBlock(entry.getKey(), entry.getValue(), 3)) return InteractionResult.FAIL;
            if (ForgeEventFactory.onMultiBlockPlace(player, before, context.getClickedFace())) return InteractionResult.FAIL;
            committed = true; level.captureBlockSnapshots = false;
            for (BlockSnapshot snapshot : before) {
                BlockState state = level.getBlockState(snapshot.getPos()); state.onPlace(level, snapshot.getPos(), snapshot.getReplacedBlock(), false);
                level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()), snapshot.getReplacedBlock(), state, 3, 512);
            }
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
            level.playSound(null, matrix, InfusionModule.START.get(), SoundSource.BLOCKS, 1, 1);
            if (level.getBlockEntity(matrix) instanceof InfusionMatrixBlockEntity tile) tile.rescan();
            return InteractionResult.CONSUME;
        } finally {
            if (!committed) {
                boolean restoring = level.restoringBlockSnapshots; level.restoringBlockSnapshots = true;
                try { for (int i = before.size() - 1; i >= 0; i--) before.get(i).restore(true, false); }
                finally { level.restoringBlockSnapshots = restoring; }
            }
            if (level.capturedBlockSnapshots.size() > start) level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()).clear();
            level.captureBlockSnapshots = capturing;
        }
    }
    private static BlockPos matrixAtPart(net.minecraft.world.level.Level level,BlockPos clicked) {
        if(level.getBlockState(clicked).getBlock() instanceof InfusionMatrixBlock)return clicked;
        var block=level.getBlockState(clicked).getBlock();
        if(block instanceof InfusionPedestalBlock) {
            BlockPos pos=clicked.above(2);return level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof InfusionMatrixBlock?pos:null;
        }
        if(List.of("arcane","ancient","eldritch").stream().noneMatch(type->block==CatalogBlocks.block("stone_"+type)))return null;
        // The original trigger can be used on any of the eight stones, not just its anchor.
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})for(int y:new int[]{1,2}) {
            BlockPos pos=clicked.offset(x,y,z);
            if(level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof InfusionMatrixBlock)return pos;
        }
        return null;
    }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static BlockState withValue(BlockState state, net.minecraft.world.level.block.state.properties.Property property, String value) {
        return property == null ? state : (BlockState)property.getValue(value).map(found -> state.setValue(property, (Comparable)found)).orElse(state);
    }
}
