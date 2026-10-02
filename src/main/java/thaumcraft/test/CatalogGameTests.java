package thaumcraft.test;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import thaumcraft.catalog.*;
import thaumcraft.catalog.blocks.*;
import thaumcraft.catalog.entities.*;
import java.util.HashSet;

/** Runtime catalogue coverage, actual block placement and visual-data persistence. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class CatalogGameTests {
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("thaumcraft",name); }
    @GameTest(template="empty") public static void everyBeta26ItemFormHasExactlyOneModernRegistration(GameTestHelper helper) {
        helper.assertTrue(CatalogModule.SPECS.size()==185,"BETA26 metadata roster changed");
        var ids=new HashSet<String>();var legacy=new HashSet<String>();
        for(var spec:CatalogModule.SPECS) {
            helper.assertTrue(ids.add(spec.id()),"Duplicate mapped item "+spec.id());legacy.add(spec.legacyItem());
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(spec.id())),"Unregistered original item form "+spec.id());
        }
        helper.assertTrue(legacy.size()==108,"Missing ConfigItems original registry entry");helper.succeed();
    }
    @GameTest(template="empty") public static void everyBeta26BlockAndOriginalItemFormIsRegistered(GameTestHelper helper) {
        helper.assertTrue(CatalogBlocks.SPECS.size()==200,"ConfigBlocks roster changed");int items=0;
        for(var spec:CatalogBlocks.SPECS) {
            helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id(spec.id())),"Missing block "+spec.id());
            if(spec.item()) {items++;helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(spec.id())),"Missing original BlockItem "+spec.id());}
        }
        helper.assertTrue(items==191,"Double slab/fluid item policy changed");
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id("thaumatorium_top")) && ForgeRegistries.BLOCKS.containsKey(id("condenser_lattice_dirty")),"Boolean constructor variant was overwritten");helper.succeed();
    }
    @GameTest(template="empty") public static void newVisualBlocksPlaceWithValidStateAndTileLifecycle(GameTestHelper helper) {
        BlockPos pos=helper.absolutePos(new BlockPos(1,2,1));
        helper.getLevel().setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        for(var entry:CatalogBlocks.ENTRIES.entrySet()) {
            var block=entry.getValue().get();
            helper.getLevel().setBlock(pos,block.defaultBlockState(),3);
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(block),"Placement failed "+entry.getKey());
            if(CatalogBlocks.special(entry.getKey())) helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof CatalogBlockEntity,"Special geometry lost tile "+entry.getKey());
            helper.getLevel().removeBlock(pos,false);
            helper.assertTrue(helper.getLevel().getBlockEntity(pos)==null,"Visual tile leaked after removal");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void allOriginalEntitiesExistAndCatalogueMobsKeepVisualOnlyPolicy(GameTestHelper helper) {
        helper.assertTrue(VisualEntitySpec.ALL.size()==43,"ConfigEntities coverage changed");
        for(var spec:VisualEntitySpec.ALL) {
            helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(id(spec.id())),"Missing original entity "+spec.id());
            Entity entity=ForgeRegistries.ENTITY_TYPES.getValue(id(spec.id())).create(helper.getLevel());
            helper.assertTrue(entity!=null,"Cannot construct entity "+spec.id());
            if(entity instanceof VisualMobEntity mob) helper.assertTrue(mob.isNoAi() && mob.isPersistenceRequired(),"Catalogue NPC unexpectedly has AI or despawning");
            helper.assertTrue(Math.abs(entity.getBbWidth()-spec.width())<.0001 && Math.abs(entity.getBbHeight()-spec.height())<.0001,"Incorrect original neutral dimensions "+spec.id());
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void golemAndPechAppearanceDataSurvivesEntitySaveLoad(GameTestHelper helper) {
        var type=VisualEntitiesModule.LIVING.get("golem").get();var source=type.create(helper.getLevel());
        CompoundTag data=new CompoundTag();data.putInt("Material",5);data.putInt("GolemHead",4);data.putInt("GolemArms",4);data.putInt("GolemLegs",3);data.putInt("GolemAddon",3);data.putInt("Color",0x445566);
        source.readAdditionalSaveData(data);CompoundTag saved=new CompoundTag();source.addAdditionalSaveData(saved);
        var loaded=type.create(helper.getLevel());loaded.readAdditionalSaveData(saved);
        helper.assertTrue(loaded.material()==5 && loaded.headPart()==4 && loaded.armsPart()==4 && loaded.legsPart()==3 && loaded.addonPart()==3 && loaded.color()==0x445566,"Golem mesh selection changed during save/load");
        helper.assertTrue(saved.getBoolean("VisualOnly"),"Catalogue lost its status");helper.succeed();
    }
    @GameTest(template="empty") public static void armourIsWearableAndPearlVisualBandsRetainDamage(GameTestHelper helper) {
        for(var spec:CatalogModule.SPECS) if(spec.sourceClass().contains("Armor") || java.util.List.of("ItemGoggles","ItemBootsTraveller","ItemCultistBoots").contains(spec.sourceClass())) {
            Item item=ForgeRegistries.ITEMS.getValue(id(spec.id()));
            helper.assertTrue(item instanceof ArmorItem,"Flat item replaced wearable armor "+spec.id());
        }
        for(int damage:new int[]{0,3,6}) {
            ItemStack stack=CatalogModule.stack("primordial_pearl");stack.setDamageValue(damage);
            helper.assertTrue(stack.getDamageValue()==damage,"Pearl visual band lost original damage");
            ItemStack loaded=ItemStack.of(stack.save(new CompoundTag()));helper.assertTrue(loaded.getDamageValue()==damage,"Pearl visual band changed during stack save/load");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void riftAppearanceAndGolemMaterialBytesKeepTheirOriginalEncoding(GameTestHelper helper) {
        var type=VisualEntitiesModule.EFFECTS.get("flux_rift").get();var source=type.create(helper.getLevel());
        CompoundTag data=new CompoundTag();data.putInt("RiftSize",80);data.putInt("RiftSeed",314);data.putBoolean("Red",true);
        source.load(data);CompoundTag saved=new CompoundTag();source.saveWithoutId(saved);
        var loaded=type.create(helper.getLevel());loaded.load(saved);
        helper.assertTrue(loaded.riftSize()==80 && loaded.riftSeed()==314 && loaded.red(),"Visual effect selection changed after save/load");
        int[] colors={5059370,16777215,13071447,15638812,5257074,1445161};
        for(int material=0;material<6;material++) {
            ItemStack stack=CatalogModule.stack("golem");stack.getOrCreateTag().putLong("props",((long)material<<56)|((long)4<<40));
            helper.assertTrue(CatalogModule.legacyGolemByte(stack.getTag().getLong("props"),0)==material && CatalogModule.golemItemColor(stack)==colors[material],"Golem material byte or tint differs from BETA26");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void placedBannerAndValveOrientationSurvivesBlockStateNbt(GameTestHelper helper) {
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.fromString("16391461-6377-4471-8faa-f797b6eddf09"),"CatalogPose"));
        player.setYRot(22.5f);var pos=helper.absolutePos(new BlockPos(1,1,1));
        helper.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        var banner=CatalogBlocks.block("banner_blue");
        for(var face:net.minecraft.core.Direction.values()) {
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),face,pos,false);
            var context=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(banner),hit);
            var state=banner.getStateForPlacement(context);
            var valve=CatalogBlocks.block("tube_valve");var valveState=valve.getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(valve),hit));
            helper.assertTrue(String.valueOf(valveState.getValue(valve.getStateDefinition().getProperty("facing"))).equals(face.getName()),"Valve handle ignored placement face "+face);
            if(face==net.minecraft.core.Direction.DOWN) {helper.assertTrue(state==null,"Banner may not attach to a ceiling in BETA26");continue;}
            var rotation=banner.getStateDefinition().getProperty("rotation");var wall=banner.getStateDefinition().getProperty("wall");
            String expected=switch(face) {case UP -> "9";case NORTH -> "8";case SOUTH -> "0";case WEST -> "4";case EAST -> "12";default -> throw new AssertionError();};
            helper.assertTrue(String.valueOf(state.getValue(rotation)).equals(expected),"Wrong source banner orientation "+face);
            helper.assertTrue(String.valueOf(state.getValue(wall)).equals(Boolean.toString(face.getAxis().isHorizontal())),"Banner wall/floor pose lost");
            var tag=net.minecraft.nbt.NbtUtils.writeBlockState(state);
            var loaded=net.minecraft.nbt.NbtUtils.readBlockState(helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK).asLookup(),tag);
            helper.assertTrue(loaded.equals(state),"Banner visual pose changed after block-state save/load");
            var target=pos.above(2);helper.getLevel().setBlock(target,state,3);
            var tile=helper.getLevel().getBlockEntity(target);
            helper.assertTrue(tile!=null,"Placed banner lost its visual block entity");
            var bounds=tile.getRenderBoundingBox();
            helper.assertTrue(bounds.minX<=target.getX() && bounds.maxX>=target.getX()+1
                    && bounds.minZ<=target.getZ() && bounds.maxZ>=target.getZ()+1
                    && bounds.minY<=target.getY()-1 && bounds.maxY>=target.getY()+2,
                    "Banner frustum bounds clip the original pole/cloth/wall pose");
        }
        helper.succeed();
    }
}
