package thaumcraft.golemancy.press;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import thaumcraft.alchemy.AlchemyModule;
import thaumcraft.catalog.blocks.CatalogBlocks;
import thaumcraft.research.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemPressFormationGameTests {
    private static final BlockPos CORNER=new BlockPos(3,2,3);
    private GolemPressFormationGameTests() {}
    private static ServerPlayer player(GameTestHelper h) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"press_formation"));p.setGameMode(GameType.SURVIVAL);
        var c=h.absolutePos(CORNER);p.setPos(c.getX()+.5,c.getY()+.5,c.getZ()+2);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));return p;
    }
    private static void stage(ServerPlayer p,int stage) {
        try {var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);method.invoke(KnowledgeStore.get(p),"MINDCLOCKWORK",stage);}catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private static List<GolemPressFormation.Part> source(GameTestHelper h,Direction facing) {
        var parts=GolemPressFormation.parts(h.absolutePos(CORNER),facing);
        for(var part:parts)h.getLevel().setBlockAndUpdate(part.pos(),part.id().equals("golem_builder")?Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP):GolemPressPlaceholderBlock.original(part.id()).defaultBlockState());return parts;
    }
    private static BlockPos part(List<GolemPressFormation.Part> parts,String id) {return parts.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow().pos();}
    private static UseOnContext context(ServerPlayer p,BlockPos clicked) {return new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(clicked.getCenter(),Direction.UP,clicked,false));}
    @GameTest(template="essentia_network")
    public static void exactAllFourBlueprintOrientationsAndWildcardCellsRetainFivePhysicalParts(GameTestHelper h) {
        var p=player(h);stage(p,1);
        for(var facing:List.of(Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST)) {
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)h.getLevel().setBlockAndUpdate(h.absolutePos(CORNER.offset(x,y,z)),Blocks.AIR.defaultBlockState());
            var parts=source(h,facing);var anchor=part(parts,"golem_builder");var cauldron=part(parts,"placeholder_cauldron");
            var clockwise=facing.getClockWise();h.assertTrue(cauldron.equals(anchor.relative(facing.getOpposite()))&&part(parts,"placeholder_table").equals(anchor.relative(clockwise))
                    &&part(parts,"placeholder_anvil").equals(cauldron.relative(clockwise))&&part(parts,"placeholder_bars").equals(anchor.above()),"Pinned rotate-right orientation changed: "+facing);
            var wildcard=cauldron.above();h.getLevel().setBlockAndUpdate(wildcard,Blocks.GOLD_BLOCK.defaultBlockState());
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AlchemyModule.SALIS_MUNDUS.get(),2));
            h.assertTrue(AlchemyModule.SALIS_MUNDUS.get().useOn(context(p,cauldron))==InteractionResult.CONSUME,"Normal Salis ritual failed "+facing);
            for(var member:parts)h.assertTrue(h.getLevel().getBlockState(member.pos()).is(CatalogBlocks.block(member.id())),"Formation lost source member "+member.id());
            h.assertTrue(h.getLevel().getBlockState(anchor).getValue(GolemPressBlock.FACING)==facing&&h.getLevel().getBlockState(wildcard).is(Blocks.GOLD_BLOCK)
                    &&p.getMainHandItem().getCount()==1&&KnowledgeStore.get(p).researchStage("MINDCLOCKWORK")==1,"Formation changed wildcard, payment, orientation or progression");
            h.getLevel().setBlockAndUpdate(anchor,Blocks.AIR.defaultBlockState());
        }h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void rejectedAndLegacyMetadataSourceVariantsPreserveOriginalMatching(GameTestHelper h) {
        var p=player(h);var parts=source(h,Direction.NORTH);BlockPos anchor=part(parts,"golem_builder"),cauldron=part(parts,"placeholder_cauldron"),anvil=part(parts,"placeholder_anvil");
        h.assertTrue(GolemPressFormation.use(context(p,anchor))==InteractionResult.FAIL&&p.getMainHandItem().getCount()==2,"Unknown research paid ritual");stage(p,2);
        for(var wrong:List.of(Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP),Blocks.PISTON.defaultBlockState(),Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP).setValue(PistonBaseBlock.EXTENDED,true))) {
            h.getLevel().setBlockAndUpdate(anchor,wrong);h.assertTrue(GolemPressFormation.match(h.getLevel(),anchor)==null&&p.getMainHandItem().getCount()==2,"Wrong piston matched/spent");}
        h.getLevel().setBlockAndUpdate(anchor,Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP));
        for(var wrong:List.of(Blocks.LAVA_CAULDRON,Blocks.POWDER_SNOW_CAULDRON)) {h.getLevel().setBlockAndUpdate(cauldron,wrong.defaultBlockState());h.assertTrue(GolemPressFormation.match(h.getLevel(),anchor)==null,"New cauldron kind impersonated original water metadata");}
        h.getLevel().setBlockAndUpdate(cauldron,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));h.getLevel().setBlockAndUpdate(anvil,Blocks.DAMAGED_ANVIL.defaultBlockState());
        h.assertTrue(GolemPressFormation.use(context(p,anvil))==InteractionResult.CONSUME,"Original anvil damage/water variants were lost by modern split IDs");
        h.getLevel().setBlockAndUpdate(anchor,Blocks.AIR.defaultBlockState());h.assertTrue(h.getLevel().getBlockState(cauldron).is(Blocks.CAULDRON)&&h.getLevel().getBlockState(anvil).is(Blocks.ANVIL),"Dismantling restored nondefault old metadata contrary to pinned behavior");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void canceledMultiblockPlacementRestoresEverySourceAndConsumesNothing(GameTestHelper h) {
        var p=player(h);stage(p,2);var parts=source(h,Direction.EAST);var anchor=part(parts,"golem_builder");
        var before=parts.stream().map(part->h.getLevel().getBlockState(part.pos())).toList();
        Consumer<BlockEvent.EntityMultiPlaceEvent> veto=e->{if(e.getEntity()==p)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(veto);
        try {h.assertTrue(GolemPressFormation.use(context(p,anchor))==InteractionResult.FAIL,"Canceled formation succeeded");}finally {MinecraftForge.EVENT_BUS.unregister(veto);}
        for(int i=0;i<parts.size();i++)h.assertTrue(h.getLevel().getBlockState(parts.get(i).pos()).equals(before.get(i)),"Canceled formation partially changed source");
        h.assertTrue(p.getMainHandItem().getCount()==2&&!(h.getLevel().getBlockEntity(anchor) instanceof GolemPressBlockEntity)&&h.getLevel().capturedBlockSnapshots.isEmpty(),"Canceled ritual paid/retained machine/snapshots");h.succeed();
    }
    @GameTest(template="essentia_network")
    public static void physicalPlaceholderBreakDropsOnceRestoresPistonAndKeepsOutputOnce(GameTestHelper h) {
        var p=player(h);stage(p,2);var parts=source(h,Direction.WEST);BlockPos anchor=part(parts,"golem_builder"),broken=part(parts,"placeholder_anvil");
        h.assertTrue(GolemPressFormation.use(context(p,anchor))==InteractionResult.CONSUME,"Formation failed");
        var tile=(GolemPressBlockEntity)h.getLevel().getBlockEntity(anchor);tile.setItem(0,thaumcraft.catalog.CatalogModule.stack("golem"));p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(p.gameMode.destroyBlock(broken),"Physical member harvest failed");
        h.assertTrue(h.getLevel().getBlockState(anchor).equals(Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.UP))&&h.getLevel().getBlockState(broken).isAir(),"Member harvest left machine or resurrected broken block");
        for(var member:parts)if(!member.pos().equals(anchor)&&!member.pos().equals(broken))h.assertTrue(h.getLevel().getBlockState(member.pos()).is(GolemPressPlaceholderBlock.original(member.id())),"Dismantling lost another member");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(anchor).inflate(3));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.ANVIL)).mapToInt(e->e.getItem().getCount()).sum()==1
                &&drops.stream().filter(e->e.getItem().is(thaumcraft.catalog.CatalogModule.stack("golem").getItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"Dismantling duplicated/lost physical member or output");h.succeed();
    }
}
