package thaumcraft.catalog.blocks;

import net.minecraft.core.*;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Visual state properties only; devices do not execute their future TC6 mechanics. */
public final class CatalogBlock extends Block implements EntityBlock {
    private static final ThreadLocal<CatalogBlocks.Spec> CONSTRUCTING = new ThreadLocal<>();
    private final CatalogBlocks.Spec spec;
    public static CatalogBlock create(CatalogBlocks.Spec spec, Properties properties) {
        CONSTRUCTING.set(spec);
        try { return new CatalogBlock(spec,properties); } finally { CONSTRUCTING.remove(); }
    }
    private CatalogBlock(CatalogBlocks.Spec spec,Properties properties) {
        super(properties); this.spec=spec;
        BlockState state=defaultBlockState();
        for (var property:stateDefinition.getProperties()) state=set(state,property,spec.defaults().get(property.getName()));
        registerDefaultState(state);
    }
    public String catalogId() { return spec.id(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        var spec=Objects.requireNonNull(CONSTRUCTING.get(),"Missing catalogue state schema");
        spec.properties().forEach((name,values) -> builder.add(new VisualProperty(name,values)));
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value) {
        return property.getValue(value).map(v -> state.setValue(property,v)).orElse(state);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state=defaultBlockState();
        if(spec.id().startsWith("banner_")) {
            if(context.getClickedFace()==Direction.DOWN) return null;
            boolean wall=context.getClickedFace().getAxis().isHorizontal();
            int rotation=wall ? switch(context.getClickedFace()) {case NORTH -> 8;case WEST -> 4;case EAST -> 12;default -> 0;}
                    : Mth.floor(((context.getPlayer()==null ? 0 : context.getPlayer().getYRot())+180)*16/360+.5)&15;
            var rotationProperty=stateDefinition.getProperty("rotation");
            var wallProperty=stateDefinition.getProperty("wall");
            if(rotationProperty!=null)state=set(state,rotationProperty,Integer.toString(rotation));
            if(wallProperty!=null)state=set(state,wallProperty,Boolean.toString(wall));
            return state;
        }
        var facing=stateDefinition.getProperty("facing");
        if (facing!=null) {
            String direction=facing.getValue(context.getClickedFace().getName()).isPresent() && facing.getPossibleValues().size()==6
                    ? context.getClickedFace().getName() : context.getHorizontalDirection().getOpposite().getName();
            state=set(state,facing,direction);
        }
        var axis=stateDefinition.getProperty("axis");
        if (axis!=null) state=set(state,axis,context.getClickedFace().getAxis().getName());
        for (Direction direction:Direction.values()) {
            var property=stateDefinition.getProperty(direction.getName());
            if (property!=null && property.getValue("true").isPresent()) {
                var neighbor=context.getLevel().getBlockState(context.getClickedPos().relative(direction)).getBlock();
                boolean attached=neighbor instanceof CatalogBlock;
                if (spec.id().equals("taint_fibre")) attached=direction==Direction.DOWN;
                state=set(state,property,Boolean.toString(attached));
            }
        }
        return state;
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) {
        var property=stateDefinition.getProperty(direction.getName());
        if (property!=null && property.getValue("true").isPresent() && (spec.id().startsWith("tube") || spec.id().startsWith("condenser_lattice")))
            return set(state,property,Boolean.toString(neighbor.getBlock() instanceof CatalogBlock));
        return state;
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        String id=spec.id();
        if (id.startsWith("jar_")) return Block.box(3,0,3,13,14,13);
        if (id.startsWith("tube")) return Block.box(5,0,5,11,16,11);
        if (id.startsWith("candle_")) return Block.box(5,0,5,11,8,11);
        if (id.startsWith("banner_")) {
            var wall=stateDefinition.getProperty("wall");
            var rotation=stateDefinition.getProperty("rotation");
            if(wall==null || !Boolean.parseBoolean(String.valueOf(state.getValue(wall))))
                return Block.box(5.28,0,5.28,10.56,32,10.56);
            int direction=rotation==null ? 0 : Integer.parseInt(String.valueOf(state.getValue(rotation)));
            return switch(direction) {
                case 0 -> Block.box(0,-16,0,16,16,4);
                case 8 -> Block.box(0,-16,12,16,16,16);
                case 12 -> Block.box(0,-16,0,4,16,16);
                case 4 -> Block.box(12,-16,0,16,16,16);
                default -> Block.box(0,-16,0,16,16,16);
            };
        }
        if (id.startsWith("nitor_")) return Block.box(4,4,4,12,12,12);
        if (id.startsWith("pedestal_")) return Block.box(2,0,2,14,16,14);
        if (id.equals("inlay") || id.equals("taint_fibre")) return Block.box(0,0,0,16,1,16);
        return super.getShape(state,level,pos,context);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return CatalogBlocks.special(spec.id()) ? new CatalogBlockEntity(pos,state) : null; }
    @Override public RenderShape getRenderShape(BlockState state) { return spec.id().startsWith("banner_") || spec.id().startsWith("nitor_") || List.of("centrifuge","infusion_matrix","thaumatorium_top").contains(spec.id()) ? RenderShape.INVISIBLE : RenderShape.MODEL; }
    private static final class VisualProperty extends Property<String> {
        private final List<String> values;
        VisualProperty(String name,List<String> values) { super(name,String.class); this.values=List.copyOf(values); }
        @Override public Collection<String> getPossibleValues() { return values; }
        @Override public Optional<String> getValue(String value) { return values.contains(value)?Optional.of(value):Optional.empty(); }
        @Override public String getName(String value) { return value; }
        @Override public boolean equals(Object o) { return this==o || o instanceof VisualProperty p && super.equals(o) && values.equals(p.values); }
        @Override public int generateHashCode() { return 31*super.generateHashCode()+values.hashCode(); }
    }
}
