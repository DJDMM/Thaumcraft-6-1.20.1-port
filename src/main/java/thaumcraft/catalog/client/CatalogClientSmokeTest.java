package thaumcraft.catalog.client;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import thaumcraft.catalog.*;
import thaumcraft.catalog.blocks.*;
import thaumcraft.catalog.entities.*;
import thaumcraft.catalog.entities.client.CatalogEffectGeometry;
import net.minecraft.world.phys.AABB;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in isolated rendering audit. Own fresh world and screenshots; never touches a player save. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class CatalogClientSmokeTest {
    private static final String WORLD="thaumcraft-catalog-smoke-"+System.currentTimeMillis();
    private static final AtomicInteger saved=new AtomicInteger();
    private static final int ITEM_COLUMNS=8, ITEMS_PER_PAGE=ITEM_COLUMNS*6;
    // No public shadow getter exists. Preserve the exact dispatcher state, including for living previews.
    private static final Field SHADOW_FIELD=ObfuscationReflectionHelper.findField(EntityRenderDispatcher.class,"f_114368_");
    private static TutorialSteps previousTutorial;
    private static boolean started,ready,stopped,captured;
    private static long start;
    private static int page,ticks;
    private static final List<Scene> scenes=new ArrayList<>();
    private record BlockPose(String label,BlockState state,BlockEntity tile,AABB bounds) {}
    private record Scene(String name,List<ItemStack> items,List<Entity> entities,List<String> labels,List<BlockPose> blocks) {
        Scene(String name,List<ItemStack> items,List<Entity> entities,List<String> labels) {
            this(name,items,entities,labels,List.of());
        }
    }
    private static final class ModelAudit { int states,items,quads,customItems,customBlocks; }
    private static void require(boolean valid,String why) { if(!valid) throw new AssertionError(why); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("thaumcraft.catalogSmokeTest") || stopped) return;
        Minecraft mc=Minecraft.getInstance();if(start==0) start=System.nanoTime();
        try {
            require(System.nanoTime()-start<420_000_000_000L,"Catalogue rendering timed out");
            if(!started) {
                if(!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null) return;
                started=true;
                previousTutorial=mc.options.tutorialStep;
                mc.options.tutorialStep=TutorialSteps.NONE;
                mc.getTutorial().stop();mc.getToasts().clear();
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
                GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                var settings=new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
                LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_WORLD: {}",WORLD);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(0x544336L,false,false),WorldPresets::createNormalWorldDimensions);
                return;
            }
            mc.getToasts().clear();
            if(mc.level==null || mc.player==null || mc.getOverlay()!=null) return;
            if(!ready) {
                require(mc.getSingleplayerServer()!=null && mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD),"Wrong audit world");
                inspect(mc);buildScenes(mc);ready=true;mc.setScreen(new Gallery(scenes.get(0)));ticks=0;
                LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_ROSTER: {} scenes",scenes.size());
            }
            if(++ticks==15 && !captured) {
                String filename="tc6-catalog-"+scenes.get(page).name()+".png";
                File file=new File(new File(mc.gameDirectory,"screenshots"),filename);
                // Replace only this scene's exact owned PNG, avoiding Screenshot.grab's automatic suffix.
                try { Files.deleteIfExists(file.toPath()); }
                catch(IOException e) { throw new IllegalStateException("Cannot replace catalogue screenshot "+filename,e); }
                captured=true;
                Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),message -> {
                    if(file.isFile() && file.length()>0) { saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_IMAGE: {}",filename); }
                    else mc.execute(() -> fail(mc,new AssertionError("Screenshot was not written "+filename)));
                });
            }
            if(ticks<30 || saved.get()<page+1) return;
            if(++page==scenes.size()) {
                stopped=true;restoreTutorial(mc);
                LogUtils.getLogger().info("THAUMCRAFT_CATALOG_CLIENT_SMOKE_OK: {} screenshots; {} original item forms, {} original blocks, {} original entities; baked quad sprites, custom renderer registrations, stateful block poses, all effect entities and worn armor variants audited; isolated world={}",saved.get(),CatalogModule.SPECS.size(),CatalogBlocks.SPECS.size(),VisualEntitySpec.ALL.size(),WORLD);
                mc.getConnection().getConnection().disconnect(Component.literal("Catalogue rendering complete"));mc.clearLevel(new TitleScreen());mc.stop();return;
            }
            ticks=0;captured=false;mc.setScreen(new Gallery(scenes.get(page)));
        } catch(RuntimeException|AssertionError e) { fail(mc,e); }
    }
    private static void restoreTutorial(Minecraft mc) {
        if(previousTutorial!=null) mc.options.tutorialStep=previousTutorial;
        mc.getTutorial().stop();mc.getToasts().clear();
    }
    private static void fail(Minecraft mc,Throwable e) { if(stopped)return;stopped=true;restoreTutorial(mc);LogUtils.getLogger().error("THAUMCRAFT_CATALOG_CLIENT_SMOKE_FAILED",e);mc.stop(); }
    private static void inspect(Minecraft mc) {
        ModelAudit audit=new ModelAudit();
        for(var spec:CatalogModule.SPECS) inspectItem(mc,stack(spec.id()),audit);
        for(var sample:CatalogModule.sampleStacks()) inspectItem(mc,sample,audit);
        inspectBoundaryOverrides(mc);
        for(var spec:CatalogBlocks.SPECS) {
            var block=CatalogBlocks.block(spec.id());
            require(block!=null,"Missing block registration "+spec.id());
            if(spec.item()) inspectItem(mc,new ItemStack(block),audit);
            for(var state:block.getStateDefinition().getPossibleStates()) {
                String label="block "+spec.id()+" "+state;
                if(CatalogBlocks.special(spec.id()) || state.getRenderShape()==RenderShape.ENTITYBLOCK_ANIMATED) {
                    require(block instanceof EntityBlock,"Missing entity block factory "+label);
                    var tile=((EntityBlock)block).newBlockEntity(BlockPos.ZERO,state);
                    require(tile!=null && mc.getBlockEntityRenderDispatcher().getRenderer(tile)!=null,"Missing custom block renderer "+label);
                    audit.customBlocks++;
                }
                if(state.getRenderShape()!=RenderShape.MODEL) continue;
                audit.states++;
                BakedModel model=mc.getBlockRenderer().getBlockModel(state);
                require(model!=mc.getModelManager().getMissingModel(),"Missing model "+label);
                for(RenderType layer:model.getRenderTypes(state,RandomSource.create(42),ModelData.EMPTY))
                    inspectQuads(model,state,layer,label,audit);
            }
        }
        for(var spec:VisualEntitySpec.ALL) if(!spec.texture().isEmpty()) {
            ResourceLocation texture=spec.texture().contains(":")?ResourceLocation.parse(spec.texture()):ResourceLocation.fromNamespaceAndPath("thaumcraft",spec.texture());
            require(mc.getResourceManager().getResource(texture).isPresent(),"Missing entity texture "+spec.id());
        }
        LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_MODELS: {} rendered block states, {} dynamic item samples; {} item checks, {} baked quads, {} custom item renderers, {} custom block states",
                audit.states,CatalogModule.sampleStacks().size(),audit.items,audit.quads,audit.customItems,audit.customBlocks);
    }
    private static ItemStack stack(String id) {
        Item item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id));
        require(item!=null,"Missing item registration "+id);
        return new ItemStack(item);
    }
    private static void inspectItem(Minecraft mc,ItemStack stack,ModelAudit audit) {
        String label="item "+ForgeRegistries.ITEMS.getKey(stack.getItem())+" "+stack.getTag();
        require(!stack.isEmpty(),"Empty catalogue "+label);
        BakedModel model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        require(model!=mc.getModelManager().getMissingModel(),"Missing model "+label);
        model=model.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false);
        require(model!=mc.getModelManager().getMissingModel(),"Missing GUI model "+label);
        audit.items++;
        if(model.isCustomRenderer()) {
            var extension=IClientItemExtensions.of(stack);
            require(extension!=IClientItemExtensions.DEFAULT && extension.getCustomRenderer()!=null
                    && extension.getCustomRenderer()!=mc.getItemRenderer().getBlockEntityRenderer(),
                    "builtin/entity model has no dedicated renderer "+label);
            audit.customItems++;
            return;
        }
        for(BakedModel pass:model.getRenderPasses(stack,true)) {
            require(pass!=mc.getModelManager().getMissingModel(),"Missing render pass "+label);
            // ItemRenderer.renderModelLists uses the three-argument quad lists for every render pass.
            for(Direction face:Direction.values()) inspectSprites(pass.getQuads(null,face,RandomSource.create(42)),label,audit);
            inspectSprites(pass.getQuads(null,null,RandomSource.create(42)),label,audit);
        }
    }
    private static void inspectQuads(BakedModel model,BlockState state,RenderType layer,String label,ModelAudit audit) {
        // Vanilla uses the same deterministic seed for each culled face and the unculled list.
        for(Direction face:Direction.values()) inspectSprites(model.getQuads(state,face,RandomSource.create(42),ModelData.EMPTY,layer),label,audit);
        inspectSprites(model.getQuads(state,null,RandomSource.create(42),ModelData.EMPTY,layer),label,audit);
    }
    private static void inspectSprites(List<BakedQuad> quads,String label,ModelAudit audit) {
        for(BakedQuad quad:quads) {
            require(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()),"Missing atlas sprite in "+label);
            audit.quads++;
        }
    }
    private static void inspectBoundaryOverrides(Minecraft mc) {
        for(String id:List.of("jar_normal","jar_void")) {
            Map<Integer,List<String>> levels=new HashMap<>();
            Set<Integer> amounts=new HashSet<>();
            for(ItemStack sample:CatalogModule.sampleStacks()) {
                if(!ForgeRegistries.ITEMS.getKey(sample.getItem()).getPath().equals(id)
                        || CatalogModule.containedAspect(sample)!=thaumcraft.api.aspects.Aspect.AIR) continue;
                int amount=sample.getTag().getList("Aspects",10).getCompound(0).getInt("amount");
                int level=amount==0?0:amount<=62?1:amount<=125?2:amount<=187?3:4;
                List<String> signature=itemQuadSignature(mc,sample);
                require(!signature.isEmpty(),"Empty jar override geometry "+id+" amount="+amount);
                List<String> previous=levels.putIfAbsent(level,signature);
                require(previous==null || previous.equals(signature),"Inconsistent jar fill boundary "+id+" amount="+amount);
                amounts.add(amount);
            }
            require(amounts.containsAll(List.of(0,1,62,63,125,126,187,188,250)),"Missing jar boundary samples "+id);
            require(levels.size()==5 && new HashSet<>(levels.values()).size()==5,"Jar overrides do not render five distinct fill geometries "+id);
        }
        for(String id:List.of("mirror","mirror_essentia")) {
            Set<Integer> links=new HashSet<>();
            for(ItemStack sample:CatalogModule.sampleStacks()) {
                if(!ForgeRegistries.ITEMS.getKey(sample.getItem()).getPath().equals(id)) continue;
                int linked=sample.getDamageValue();
                require(linked==0 || linked==1,"Unexpected mirror sample "+sample);
                String expected=linked==1?"thaumcraft:blocks/mirrorpaneopen":"thaumcraft:blocks/mirrorpane";
                require(itemQuadSignature(mc,sample).stream().anyMatch(quad -> quad.startsWith(expected+" ")),
                        "Wrong actual mirror pane sprite "+id+" linked="+linked);
                links.add(linked);
            }
            require(links.size()==2,"Missing linked/unlinked mirror samples "+id);
        }
        LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_OVERRIDES: both jars have five distinct baked fill geometries at all nine aer boundaries; both mirrors select linked/unlinked pane sprites");
    }
    private static List<String> itemQuadSignature(Minecraft mc,ItemStack sample) {
        BakedModel model=mc.getItemRenderer().getModel(sample,mc.level,mc.player,0)
                .applyTransform(ItemDisplayContext.GUI,new PoseStack(),false);
        List<String> signature=new ArrayList<>();
        for(BakedModel pass:model.getRenderPasses(sample,true)) {
            for(Direction face:Direction.values()) appendQuadSignature(signature,pass.getQuads(null,face,RandomSource.create(42)));
            appendQuadSignature(signature,pass.getQuads(null,null,RandomSource.create(42)));
        }
        Collections.sort(signature);
        return List.copyOf(signature);
    }
    private static void appendQuadSignature(List<String> signature,List<BakedQuad> quads) {
        for(BakedQuad quad:quads) signature.add(quad.getSprite().contents().name()+" "+Arrays.toString(quad.getVertices()));
    }
    private static void itemPages(String prefix,List<ItemStack> items) {
        for(int start=0,index=1;start<items.size();start+=ITEMS_PER_PAGE,index++) scenes.add(new Scene(prefix+"-"+index,List.copyOf(items.subList(start,Math.min(start+ITEMS_PER_PAGE,items.size()))),List.of(),List.of()));
    }
    private static void entityPages(String prefix,List<Entity> entities,List<String> labels) {
        require(entities.size()==labels.size(),"Mismatched scene labels "+prefix);
        for(int start=0,index=1;start<entities.size();start+=12,index++) scenes.add(new Scene(prefix+"-"+index,List.of(),List.copyOf(entities.subList(start,Math.min(start+12,entities.size()))),List.copyOf(labels.subList(start,Math.min(start+12,labels.size())))));
    }
    private static void blockPosePages(Minecraft mc) {
        List<BlockPose> poses=new ArrayList<>();
        BlockState banner=CatalogBlocks.block("banner_red").defaultBlockState();
        for(boolean wall:new boolean[]{false,true}) for(int rotation:new int[]{0,4,8,12}) {
            BlockState state=withState(withState(banner,"wall",Boolean.toString(wall)),"rotation",Integer.toString(rotation));
            addBlockPose(mc,poses,"banner_red "+(wall?"wall":"floor")+" r="+rotation,state);
        }
        for(Direction direction:Direction.values()) {
            BlockState state=withState(CatalogBlocks.block("tube_valve").defaultBlockState(),"facing",direction.getName());
            state=withState(withState(state,direction.getName(),"true"),direction.getOpposite().getName(),"true");
            addBlockPose(mc,poses,"tube_valve "+direction.getName(),state);
        }
        for(String id:List.of("jar_brain","centrifuge","infusion_matrix","nitor_yellow","nitor_red","nitor_blue"))
            addBlockPose(mc,poses,id,CatalogBlocks.block(id).defaultBlockState());
        for(Direction direction:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST)) {
            BlockState state=withState(CatalogBlocks.block("pattern_crafter").defaultBlockState(),"facing",direction.getName());
            addBlockPose(mc,poses,"pattern_crafter "+direction.getName(),state);
        }
        for(Direction direction:Direction.values()) {
            BlockState state=withState(CatalogBlocks.block("golem_builder").defaultBlockState(),"facing",direction.getName());
            addBlockPose(mc,poses,"golem_builder "+direction.getName(),state);
        }
        for(Direction direction:Direction.values()) {
            BlockState state=withState(CatalogBlocks.block("mirror").defaultBlockState(),"facing",direction.getName());
            addBlockPose(mc,poses,"mirror "+direction.getName(),state);
        }
        addBlockPose(mc,poses,"mirror_essentia north",withState(CatalogBlocks.block("mirror_essentia").defaultBlockState(),"facing","north"));
        for(int first=0,index=1;first<poses.size();first+=8,index++)
            scenes.add(new Scene("block-poses-"+index,List.of(),List.of(),List.of(),
                    List.copyOf(poses.subList(first,Math.min(first+8,poses.size())))));
        long models=poses.stream().filter(pose -> pose.state().getRenderShape()==RenderShape.MODEL).count();
        long tiles=poses.stream().filter(pose -> pose.tile()!=null).count();
        LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_BLOCK_POSES: {} actual states, {} MODEL draws, {} BER draws, {} pages; detached tiles in isolated world={}",
                poses.size(),models,tiles,(poses.size()+7)/8,WORLD);
    }
    private static BlockState withState(BlockState state,String name,String value) {
        Property<?> property=state.getBlock().getStateDefinition().getProperty(name);
        require(property!=null,"Missing preview state property "+name+" in "+state);
        return withStateValue(state,property,value);
    }
    private static <T extends Comparable<T>> BlockState withStateValue(BlockState state,Property<T> property,String value) {
        T parsed=property.getValue(value).orElseThrow(() -> new AssertionError("Invalid preview state "+property.getName()+"="+value));
        return state.setValue(property,parsed);
    }
    private static void addBlockPose(Minecraft mc,List<BlockPose> poses,String label,BlockState state) {
        BlockEntity tile=state.getBlock() instanceof EntityBlock factory?factory.newBlockEntity(BlockPos.ZERO,state):null;
        String id=ForgeRegistries.BLOCKS.getKey(state.getBlock()).getPath();
        require(!CatalogBlocks.special(id) || tile!=null,"Missing preview block entity "+label);
        if(tile!=null) {
            require(tile.getBlockState().equals(state),"Preview tile lost its actual state "+label);
            tile.setLevel(mc.level); // Detached anchor only: no setBlock, tile insertion or server synchronization.
            require(mc.getBlockEntityRenderDispatcher().getRenderer(tile)!=null,"Missing preview BER "+label);
        }
        require(state.getRenderShape()==RenderShape.MODEL || tile!=null,"Empty block pose "+label);
        poses.add(new BlockPose(label,state,tile,blockPoseBounds(mc,state)));
    }
    private static AABB blockPoseBounds(Minecraft mc,BlockState state) {
        var shape=state.getShape(mc.level,BlockPos.ZERO);
        AABB bounds=shape.isEmpty()?new AABB(0,0,0,1,1,1):shape.bounds();
        if(state.getRenderShape()==RenderShape.MODEL) {
            BakedModel model=mc.getBlockRenderer().getBlockModel(state);
            for(RenderType layer:model.getRenderTypes(state,RandomSource.create(42),ModelData.EMPTY)) {
                for(Direction face:Direction.values()) bounds=includeVertices(bounds,model.getQuads(state,face,RandomSource.create(42),ModelData.EMPTY,layer));
                bounds=includeVertices(bounds,model.getQuads(state,null,RandomSource.create(42),ModelData.EMPTY,layer));
            }
        }
        String id=ForgeRegistries.BLOCKS.getKey(state.getBlock()).getPath();
        if(id.startsWith("banner_")) {
            var property=state.getBlock().getStateDefinition().getProperty("wall");
            boolean wall=Boolean.parseBoolean(String.valueOf(state.getValue(property)));
            // Beam and cloth extend beyond the narrow pole collision shape. Wall cloth hangs below its anchor.
            bounds=bounds.minmax(wall?new AABB(0,-1,0,1,1,1):new AABB(0,0,0,1,2,1));
        }
        return bounds.inflate(.08);
    }
    private static AABB includeVertices(AABB bounds,List<BakedQuad> quads) {
        for(BakedQuad quad:quads) {
            int[] vertices=quad.getVertices();int stride=vertices.length/4;
            require(stride>=3,"Unexpected baked vertex format in block pose");
            for(int vertex=0;vertex<4;vertex++) {
                int offset=vertex*stride;
                double x=Float.intBitsToFloat(vertices[offset]),y=Float.intBitsToFloat(vertices[offset+1]),z=Float.intBitsToFloat(vertices[offset+2]);
                require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z),"Nonfinite block pose vertex");
                bounds=bounds.minmax(new AABB(x,y,z,x,y,z));
            }
        }
        return bounds;
    }
    private static void buildScenes(Minecraft mc) {
        List<ItemStack> items=new ArrayList<>();
        for(var spec:CatalogModule.SPECS) items.add(new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",spec.id()))));
        itemPages("items",items);itemPages("nbt-variants",CatalogModule.sampleStacks());
        items.clear();for(var spec:CatalogBlocks.SPECS) if(spec.item()) items.add(new ItemStack(CatalogBlocks.block(spec.id())));
        itemPages("blocks",items);
        blockPosePages(mc);
        List<Entity> mobs=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(var entry:VisualEntitiesModule.LIVING.entrySet()) {
            var mob=entry.getValue().get().create(mc.level);
            require(mob!=null,"Cannot create entity "+entry.getKey());
            mob.tickCount=20;
            equipCultist(mob);mobs.add(mob);labels.add(entry.getKey());
        }
        entityPages("entities",mobs,labels);mobs.clear();labels.clear();
        for(var spec:VisualEntitySpec.ALL) if(!spec.living()) {
            var type=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",spec.id()));
            require(type!=null,"Missing effect registration "+spec.id());
            Entity effect=type.create(mc.level); // Includes the already working Alumentum projectile.
            require(effect!=null,"Cannot create effect "+spec.id());
            effect.tickCount=20; // A visible animation frame, without ticking projectile/spell mechanics.
            mobs.add(effect);labels.add(spec.id());
        }
        entityPages("effects",mobs,labels);
        LogUtils.getLogger().info("THAUMCRAFT_CATALOG_SMOKE_EFFECTS: {} of {} nonliving registrations previewed",mobs.size(),VisualEntitySpec.ALL.stream().filter(spec -> !spec.living()).count());
        mobs.clear();labels.clear();
        for(int variant=0;variant<3;variant++) { var mob=VisualEntitiesModule.LIVING.get("pech").get().create(mc.level);CompoundTag tag=new CompoundTag();tag.putInt("PechType",variant);mob.readAdditionalSaveData(tag);mobs.add(mob);labels.add("pech-"+variant); }
        for(int mat=0;mat<6;mat++) {var mob=VisualEntitiesModule.GOLEM.get().create(mc.level);mob.setProps(thaumcraft.golemancy.press.GolemDesign.create(mat,mat%5,mat%5,mat%4,mat%4).orElseThrow().props());mob.setValidSpawn();mobs.add(mob);labels.add("golem-"+mat);}
        entityPages("entity-variants",mobs,labels);mobs.clear();labels.clear();
        for(String prefix:List.of("thaumium","void","cloth","fortress","void_robe","crimson_plate","crimson_robe","crimson_praetor")) {
            ArmorStand stand=armorStand(mc,prefix);
            mobs.add(stand);labels.add(prefix);
        }
        for(String id:List.of("goggles","traveller_boots","crimson_boots")) {
            Item item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",id));
            if(item instanceof ArmorItem armor) {ArmorStand stand=new ArmorStand(mc.level,0,0,0);stand.setItemSlot(armor.getEquipmentSlot(),new ItemStack(item));mobs.add(stand);labels.add(id);}
        }
        entityPages("armor",mobs,labels);
        mobs.clear();labels.clear();
        for(String prefix:List.of("cloth","void_robe")) for(int color:new int[]{0xB02E26,0x3C44AA}) {
            ArmorStand stand=armorStand(mc,prefix);
            for(ItemStack item:stand.getArmorSlots()) if(!item.isEmpty()) item.getOrCreateTagElement("display").putInt("color",color);
            mobs.add(stand);labels.add(prefix+" #"+String.format("%06X",color));
        }
        for(boolean goggles:new boolean[]{false,true}) for(int mask=-1;mask<=2;mask++) {
            ArmorStand stand=armorStand(mc,"fortress");
            ItemStack helm=stand.getItemBySlot(EquipmentSlot.HEAD);
            require(!helm.isEmpty(),"Missing fortress helmet in worn variant");
            if(goggles) helm.getOrCreateTag().putByte("goggles",(byte)1);
            if(mask>=0) helm.getOrCreateTag().putInt("mask",mask);
            mobs.add(stand);labels.add("fortress g="+(goggles?1:0)+" m="+mask);
        }
        entityPages("armor-variants",mobs,labels);
        for(Scene scene:scenes) for(Entity entity:scene.entities())
            require(mc.getEntityRenderDispatcher().getRenderer(entity)!=null,"Missing entity renderer "+ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }
    private static ArmorStand armorStand(Minecraft mc,String prefix) {
        ArmorStand stand=new ArmorStand(mc.level,0,0,0);
        for(var spec:CatalogModule.SPECS) {
            String id=spec.id();
            if(!id.startsWith(prefix+"_") || prefix.equals("void") && id.startsWith("void_robe_")) continue;
            ItemStack item=stack(id);
            if(item.getItem() instanceof ArmorItem armor) stand.setItemSlot(armor.getEquipmentSlot(),item);
        }
        return stand;
    }
    private static void equipCultist(VisualMobEntity mob) {
        String prefix=switch(mob.spec().id()) {case "cultist_knight" -> "crimson_plate";case "cultist_cleric" -> "crimson_robe";case "cultist_leader" -> "crimson_praetor";default -> "";};
        if(prefix.isEmpty()) return;
        for(String part:List.of("helm","chest","legs")) {Item item=ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("thaumcraft",prefix+"_"+part));if(item instanceof ArmorItem armor)mob.setItemSlot(armor.getEquipmentSlot(),new ItemStack(item));}
    }
    private static String variantLabel(ItemStack stack) {
        String id=ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        var aspect=CatalogModule.containedAspect(stack);
        if(id.equals("jar_normal") || id.equals("jar_void")) {
            int amount=stack.hasTag()?stack.getTag().getList("Aspects",10).stream()
                    .mapToInt(value -> ((CompoundTag)value).getInt("amount")).sum():0;
            return (aspect==null?"empty":aspect.getTag())+" "+amount+"/250";
        }
        if(id.equals("mirror") || id.equals("mirror_essentia")) return "linked="+stack.getDamageValue();
        if(aspect!=null) return aspect.getTag();
        CompoundTag tag=stack.getTag();
        if(tag==null) return stack.isDamaged()?"damage="+stack.getDamageValue():"";
        if(tag.contains("props")) return "material="+CatalogModule.legacyGolemByte(tag.getLong("props"),0);
        if(tag.contains("display",10) && tag.getCompound("display").contains("color"))
            return "#"+String.format("%06X",tag.getCompound("display").getInt("color")&0xFFFFFF);
        if(tag.contains("color")) return "#"+String.format("%06X",tag.getInt("color")&0xFFFFFF);
        if(tag.contains("focus",10)) return "focus installed";
        if(tag.contains("goggles") || tag.contains("mask"))
            return "g="+tag.getByte("goggles")+" m="+(tag.contains("mask")?tag.getInt("mask"):-1);
        if(tag.contains("loaded")) return "loaded="+tag.getByte("loaded");
        if(tag.contains("type")) return "type="+tag.getByte("type");
        return stack.isDamaged()?"damage="+stack.getDamageValue():"";
    }
    private static int previewScale(Entity entity,int cardWidth) {
        AABB fittedBounds=entityPreviewBounds(entity);
        if(fittedBounds!=null) return Math.max(1,(int)Math.min(80,
                Math.min(120/fittedBounds.getYsize(),(cardWidth-16)/fittedBounds.getXsize())));
        double modelWidth=Math.max(.6,Math.max(entity.getBoundingBox().getXsize(),entity.getBoundingBox().getZsize()));
        double modelHeight=Math.max(1,entity.getBoundingBox().getYsize());
        String id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).getPath();
        // Gallery bounds only: collision dimensions do not include wings/legs or billboard quads.
        // FireBat's audited .35 renderer scale leaves an approximately .9-block wingspan.
        if(id.equals("fire_bat") || id.equals("spell_bat")) modelWidth=Math.max(modelWidth,1.1);
        if(id.equals("mind_spider")) modelWidth=Math.max(modelWidth,1.6);
        if(id.equals("taint_seed") || id.equals("taint_seed_prime")) modelWidth=Math.max(modelWidth,2.5);
        if(id.equals("wisp")) {modelWidth=Math.max(modelWidth,1.5);modelHeight=Math.max(modelHeight,1.5);}
        if(id.equals("eldritch_orb")) {modelWidth=Math.max(modelWidth,1.8);modelHeight=Math.max(modelHeight,1.8);}
        if(id.equals("golem_orb")) {modelWidth=Math.max(modelWidth,1.4);modelHeight=Math.max(modelHeight,1.4);}
        if(entity instanceof VisualEffectEntity effect && id.equals("flux_rift")) {
            AABB bounds=riftBounds(effect);
            modelWidth=Math.max(modelWidth,bounds.getXsize());modelHeight=Math.max(modelHeight,bounds.getYsize());
        }
        if(id.equals("cultist_portal_greater") || id.equals("cultist_portal_lesser")) modelWidth=Math.max(modelWidth,3);
        return Math.max(1,(int)Math.min(80,Math.min(120/modelHeight,(cardWidth-12)/modelWidth)));
    }
    private static AABB entityPreviewBounds(Entity entity) {
        if(!(entity instanceof VisualMobEntity mob)) return null;
        return switch(mob.spec().id()) {
            // Neutral tentacle child scales and model translations extend below the entity anchor.
            // These conservative gallery envelopes cover the complete posed chains; collision sizes stay unchanged.
            case "taintacle_giant" -> new AABB(-2,-1.9,-2,2,3.1,2);
            case "taint_seed_prime" -> new AABB(-1.5,-.9,-1.5,1.5,2.5,1.5);
            case "taint_swarm" -> swarmBounds(entity.getId(),entity.tickCount);
            default -> null;
        };
    }
    private static AABB swarmBounds(int seed,int age) {
        // Match CatalogEffectGeometry.swarm's seeded centers and draw order, including each billboard's half-size.
        Random spawn=new Random(seed);
        AABB bounds=null;
        for(int insect=0;insect<30;insect++) {
            float x=(spawn.nextFloat()-spawn.nextFloat())*2;
            float y=(spawn.nextFloat()-spawn.nextFloat())*2;
            float z=(spawn.nextFloat()-spawn.nextFloat())*2;
            spawn.nextFloat();spawn.nextFloat();spawn.nextFloat(); // RGB draws occur before the next center.
            float size=new Random((long)seed*31+insect).nextFloat()*.5f+1;
            int particleAge=Math.max(0,age-insect);
            float halfSize=.1f*size*((float)Math.sin(particleAge/3f)*.25f+1);
            AABB particle=new AABB(x-halfSize,y-halfSize,z-halfSize,x+halfSize,y+halfSize,z+halfSize);
            bounds=bounds==null?particle:bounds.minmax(particle);
        }
        require(bounds!=null,"Empty swarm preview envelope");
        return bounds.inflate(.025);
    }
    private static AABB riftBounds(VisualEffectEntity effect) {
        var path=CatalogEffectGeometry.riftPath(effect.riftSeed(),effect.riftSize());
        double minX=0,minY=0,minZ=0,maxX=0,maxY=0,maxZ=0;
        for(var point:path.points()) {
            minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);
            maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);
        }
        // Include the outer polycone pass, animated radius, spine jitter and angle joins.
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ).inflate(effect.riftSize()/300.0*2.5+.2);
    }
    private static boolean originCentered(Entity entity) {
        return entityPreviewBounds(entity)!=null
                || entity instanceof VisualMobEntity mob && List.of("wisp","swarm").contains(mob.spec().model());
    }
    private static boolean shadows(EntityRenderDispatcher dispatcher) {
        try { return SHADOW_FIELD.getBoolean(dispatcher); }
        catch(IllegalAccessException e) { throw new IllegalStateException("Cannot preserve entity preview shadows",e); }
    }
    private static void renderPreview(GuiGraphics gui,Entity entity,int x,int y,int scale) {
        EntityRenderDispatcher dispatcher=Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf camera=new Quaternionf(dispatcher.cameraOrientation());
        boolean shadow=shadows(dispatcher);
        float yaw=entity.getYRot(),pitch=entity.getXRot(),oldYaw=entity.yRotO,oldPitch=entity.xRotO;
        boolean angled=entity instanceof VisualEffectEntity effect
                && List.of("dart","Grappler","mine").contains(effect.spec().model());
        try {
            if(entity instanceof LivingEntity living) {
                AABB bounds=entityPreviewBounds(entity);
                if(bounds!=null) {
                    var center=bounds.getCenter();
                    x-=(int)Math.round(center.x*scale);
                    y+=(int)Math.round(center.y*scale);
                }
                InventoryScreen.renderEntityInInventoryFollowsMouse(gui,x,y,scale,25,0,living);
                return;
            }
            if(angled) {
                entity.setYRot(35);entity.yRotO=35;
                entity.setXRot(15);entity.xRotO=15;
            }
            // InventoryScreen's pose, lighting and full-bright dispatcher path generalized to Entity.
            gui.pose().pushPose();
            try {
                gui.pose().translate(x,y,50);
                gui.pose().mulPoseMatrix(new Matrix4f().scaling(scale,scale,-scale));
                gui.pose().mulPose(Axis.ZP.rotationDegrees(180));
                if(entity instanceof VisualEffectEntity effect && effect.spec().model().equals("rift")) {
                    var center=riftBounds(effect).getCenter();
                    gui.pose().translate(-center.x,-center.y,-center.z);
                } else if(entity instanceof VisualEffectEntity effect) {
                    double center=switch(effect.spec().model()) {case "item","bottle" -> .3;case "block" -> .5;default -> 0;};
                    gui.pose().translate(0,-center,0);
                }
                Lighting.setupForEntityInInventory();
                dispatcher.overrideCameraOrientation(new Quaternionf());
                dispatcher.setRenderShadow(false);
                RenderSystem.runAsFancy(() -> dispatcher.render(entity,0,0,0,angled?35:0,0,gui.pose(),gui.bufferSource(),15728880));
                gui.flush();
            } finally { gui.pose().popPose(); }
        } finally {
            entity.setYRot(yaw);entity.yRotO=oldYaw;
            entity.setXRot(pitch);entity.xRotO=oldPitch;
            dispatcher.overrideCameraOrientation(camera);
            dispatcher.setRenderShadow(shadow);
            Lighting.setupFor3DItems();
        }
    }
    private static Quaternionf blockPoseRotation() {
        return Axis.XP.rotationDegrees(25).mul(Axis.YP.rotationDegrees(-35));
    }
    private static int blockPoseScale(BlockPose block,int cardWidth,Quaternionf rotation) {
        AABB bounds=block.bounds();var center=bounds.getCenter();
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        // Fit the projected full model, not its one-block anchor or an item GUI transform.
        for(double x:new double[]{bounds.minX,bounds.maxX}) for(double y:new double[]{bounds.minY,bounds.maxY}) for(double z:new double[]{bounds.minZ,bounds.maxZ}) {
            Vector3f corner=new Vector3f((float)(x-center.x),(float)(y-center.y),(float)(z-center.z));
            rotation.transform(corner);
            minX=Math.min(minX,corner.x);maxX=Math.max(maxX,corner.x);
            minY=Math.min(minY,corner.y);maxY=Math.max(maxY,corner.y);
        }
        return Math.max(1,(int)Math.min(88,Math.min((cardWidth-16)/Math.max(.1,maxX-minX),140/Math.max(.1,maxY-minY))));
    }
    private static void renderBlockPose(GuiGraphics gui,BlockPose block,int x,int y,int cardWidth) {
        Minecraft mc=Minecraft.getInstance();
        EntityRenderDispatcher dispatcher=mc.getEntityRenderDispatcher();
        Quaternionf camera=new Quaternionf(dispatcher.cameraOrientation());
        boolean shadow=shadows(dispatcher);
        Quaternionf rotation=blockPoseRotation();
        int scale=blockPoseScale(block,cardWidth,rotation);
        var center=block.bounds().getCenter();
        gui.flush(); // Finish card/UI batches before changing lighting and billboard orientation.
        gui.pose().pushPose();
        try {
            gui.pose().translate(x,y,150);
            gui.pose().scale(scale,-scale,scale);
            gui.pose().mulPose(rotation);
            gui.pose().translate(-center.x,-center.y,-center.z);
            Lighting.setupFor3DItems();
            dispatcher.overrideCameraOrientation(new Quaternionf(rotation).conjugate());
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> {
                if(block.state().getRenderShape()==RenderShape.MODEL)
                    mc.getBlockRenderer().renderSingleBlock(block.state(),gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY);
                if(block.tile()!=null) {
                    require(block.tile().getBlockState().equals(block.state()),"Preview BER state changed "+block.label());
                    // Verified API: false means the registered renderer actually ran; true means no renderer.
                    require(!mc.getBlockEntityRenderDispatcher().renderItem(block.tile(),gui.pose(),gui.bufferSource(),15728880,OverlayTexture.NO_OVERLAY),
                            "Dispatcher skipped preview BER "+block.label());
                }
                String id=ForgeRegistries.BLOCKS.getKey(block.state().getBlock()).getPath();
                if(id.equals("mirror") || id.equals("mirror_essentia"))
                    LevelRenderer.renderLineBox(gui.pose(),gui.bufferSource().getBuffer(RenderType.lines()),new AABB(0,0,0,1,1,1),.55f,.7f,.85f,.7f);
            });
        } finally {
            try { gui.flush(); }
            finally {
                gui.pose().popPose();
                dispatcher.overrideCameraOrientation(camera);
                dispatcher.setRenderShadow(shadow);
                Lighting.setupFor3DItems();
            }
        }
    }
    private static final class Gallery extends Screen {
        private final Scene scene;Gallery(Scene scene) { super(Component.literal("TC6 BETA26 — "+scene.name()));this.scene=scene; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics gui,int x,int y,float partial) {
            try { renderGallery(gui); }
            catch(RuntimeException|AssertionError e) { fail(Minecraft.getInstance(),e); }
        }
        private void renderGallery(GuiGraphics gui) {
            gui.fill(0,0,width,height,0xff18202b);gui.drawCenteredString(font,title,width/2,10,0xffefdbac);
            gui.drawCenteredString(font,"Визуальный каталог · механики новых объектов ещё не перенесены",width/2,height-20,0xffa7afb8);
            for(int i=0;i<scene.items.size();i++) {
                int col=i%ITEM_COLUMNS,row=i/ITEM_COLUMNS,px=col*(width/ITEM_COLUMNS)+7,py=40+row*69;
                int cardWidth=width/ITEM_COLUMNS-12;
                gui.fill(px,py,px+cardWidth,py+65,0xff293340);
                gui.pose().pushPose();gui.pose().translate(px+(cardWidth-32)/2f,py+4,0);gui.pose().scale(2,2,2);gui.renderItem(scene.items.get(i),0,0);gui.pose().popPose();
                String label=ForgeRegistries.ITEMS.getKey(scene.items.get(i).getItem()).getPath();
                String first=font.plainSubstrByWidth(label,cardWidth-4);
                gui.drawString(font,first,px+2,py+39,0xffd4d9df,false);
                if(first.length()<label.length()) gui.drawString(font,font.plainSubstrByWidth(label.substring(first.length()),cardWidth-4),px+2,py+48,0xffd4d9df,false);
                gui.drawString(font,font.plainSubstrByWidth(variantLabel(scene.items.get(i)),cardWidth-4),px+2,py+57,0xffefdbac,false);
            }
            for(int i=0;i<scene.entities.size();i++) {
                int col=i%6,row=i/6,px=col*(width/6)+6,py=40+row*205;
                int cardWidth=width/6-10;Entity entity=scene.entities.get(i);
                gui.fill(px,py,px+cardWidth,py+198,0xff293340);
                int scale=previewScale(entity,cardWidth);
                renderPreview(gui,entity,px+cardWidth/2,py+(entity instanceof LivingEntity && !originCentered(entity)?158:100),scale);
                String label=scene.labels.get(i),first=font.plainSubstrByWidth(label,cardWidth-6);
                gui.drawCenteredString(font,first,px+cardWidth/2,py+178,0xffd4d9df);
                if(first.length()<label.length()) gui.drawCenteredString(font,font.plainSubstrByWidth(label.substring(first.length()),cardWidth-6),px+cardWidth/2,py+188,0xffd4d9df);
                if(entity instanceof VisualEffectEntity effect && List.of("none","cloud").contains(effect.spec().model()))
                    gui.drawCenteredString(font,"original: no geometry",px+cardWidth/2,py+86,0xffa7afb8);
            }
            for(int i=0;i<scene.blocks().size();i++) {
                int px=i%4*(width/4)+6,py=40+i/4*205,cardWidth=width/4-12;
                BlockPose block=scene.blocks().get(i);
                gui.fill(px,py,px+cardWidth,py+198,0xff293340);
                renderBlockPose(gui,block,px+cardWidth/2,py+92,cardWidth);
                String first=font.plainSubstrByWidth(block.label(),cardWidth-6);
                gui.drawCenteredString(font,first,px+cardWidth/2,py+174,0xffd4d9df);
                if(first.length()<block.label().length()) gui.drawCenteredString(font,
                        font.plainSubstrByWidth(block.label().substring(first.length()),cardWidth-6),px+cardWidth/2,py+184,0xffd4d9df);
            }
        }
    }
}
