package thaumcraft.client.research;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.research.*;
import thaumcraft.research.book.MultiblockCatalog;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Supplied internal layout inventory plus normal owned-world book scenes and actual read packets. */
@Mod.EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class ThaumonomiconCompleteClientSmokeTest {
    private static final String WORLD="thaumcraft-thaumonomicon-complete-"+System.currentTimeMillis();
    private record Audit(ThaumonomiconPageScreen page,int chapter,int spread) {}
    private record Scene(String name,String kind,String key) {}
    private static final List<Audit> audit=new ArrayList<>();
    private static final List<Scene> scenes=new ArrayList<>();
    private static final AtomicInteger saved=new AtomicInteger();
    private static CompletableFuture<Void> work;
    private static volatile CompoundTag snapshot;
    private static boolean started,setup,stopped,auditRendered,prepared,captureRequested,captured,packetChecked;
    private static int auditIndex,sceneIndex,stableTicks,phase,entries,chapters,spreads,recipes;
    private static long began;
    private static CompoundTag gameBefore;
    private static CompoundTag lateFixture;
    private static ListTag inventoryBefore;
    private static int normalChecks, productRecipeKinds, productStructures;
    private static String productInfusion;
    private static TutorialSteps previousTutorial;
    private static ThaumonomiconScreen browser;
    private static ThaumonomiconPageScreen linkParent;

    private ThaumonomiconCompleteClientSmokeTest() {}
    static void snapshot(CompoundTag data) {
        if(Boolean.getBoolean("thaumcraft.thaumonomiconCompleteSmokeTest")) snapshot=data.copy();
    }
    private static void require(boolean value,String why) {if(!value)throw new AssertionError(why);}
    public static CompoundTag gameplayState(PlayerKnowledge knowledge) {
        CompoundTag tag=knowledge.save();tag.remove("BookRead");return tag;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.thaumonomiconCompleteSmokeTest")||stopped)return;
        Minecraft mc=Minecraft.getInstance();
        if(began==0)began=System.nanoTime();
        try {
            require(System.nanoTime()-began<600_000_000_000L,"Book audit timed out: audit="+auditIndex+", scene="+sceneIndex+", phase="+phase);
            if(!started){startWorld(mc);return;}
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            require(mc.getSingleplayerServer()!=null&&WORLD.equals(mc.getSingleplayerServer().getWorldData().getLevelName()),"Wrong book audit world");
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(!setup){setup=true;submit(mc,()->prepareWorld(mc));return;}
            if(snapshot==null||mc.player.getMainHandItem().getItem()!=ResearchModule.THAUMONOMICON.get())return;
            if(browser==null){prepareAudit(mc);return;}
            if(auditIndex<audit.size()) {
                if(!prepared){prepared=true;showAudit(mc,audit.get(auditIndex));return;}
                if(auditRendered){auditIndex++;prepared=auditRendered=false;}
                return;
            }
            if(sceneIndex==scenes.size()){finish(mc);return;}
            Scene scene=scenes.get(sceneIndex);
            if(!prepared){prepared=true;prepareScene(mc,scene);return;}
            if(scene.kind.equals("fresh")&&phase==0) {
                showFreshScene(mc,scene);phase=1;return;
            }
            if(scene.kind.equals("requirements")&&phase==0) {
                int expected=scene.name.equals("requirements-items")?1:3;
                if(PlayerKnowledge.load(snapshot).researchStage("ESSENTIASMELTER")!=expected)return;
                showRequirements(mc,scene);phase=1;return;
            }
            if(scene.kind.startsWith("ru-")&&phase==0) {
                phase=1;showRussianScene(mc,scene);return;
            }
            if(scene.kind.equals("structure")&&phase==0) {
                phase=1;((ThaumonomiconPageScreen)mc.screen).structureInputForSmokeTest(scene.key);return;
            }
            if(scene.kind.equals("read")) {
                var state=PlayerKnowledge.load(snapshot);
                if(state.hasUnreadResearch("ESSENTIASMELTER")||state.hasUnreadPage("ESSENTIASMELTER"))return;
                if(!packetChecked) {
                    packetChecked=true;
                    submit(mc,()->{
                        var actual=KnowledgeStore.get(player(mc));
                        require(!actual.hasUnreadResearch("ESSENTIASMELTER")&&!actual.hasUnreadPage("ESSENTIASMELTER"),"Read C2S/S2C did not acknowledge actual server facts");
                        require(gameBefore.equals(gameplayState(actual))&&inventoryBefore.equals(player(mc).getInventory().save(new ListTag())),"Read acknowledgments changed gameplay or inventory");
                    });return;
                }
            }
            if(!scene.kind.equals("toast"))mc.getToasts().clear();
            if(++stableTicks>=12&&!captured)captureRequested=true;
            if(captured&&saved.get()==sceneIndex+1&&stableTicks>=18) {
                if(scene.kind.equals("requirements")) {
                    // Restore only after leaving the page: a restored final chapter would correctly
                    // acknowledge completion before the subsequent unread-marker scene can inspect it.
                    mc.setScreen(browser);
                    submit(mc,()->setFixtureStage(mc,"ESSENTIASMELTER",5));
                }
                if(scene.kind.equals("fresh")) {
                    mc.setScreen(browser);
                    snapshot=null;
                    submit(mc,()->replaceFixtureKnowledge(mc,lateFixture));
                }
                sceneIndex++;stableTicks=phase=0;prepared=captured=captureRequested=false;
            }
        }catch(Throwable failure){fail(mc,failure);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("thaumcraft.thaumonomiconCompleteSmokeTest")||stopped)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(browser!=null&&auditIndex<audit.size()&&prepared&&mc.screen==audit.get(auditIndex).page) {
                auditRendered=true;spreads++;return;
            }
            if(!captureRequested||captured)return;
            captured=true;captureRequested=false;
            String name="tc6-thaumonomicon-complete-"+scenes.get(sceneIndex).name+".png";
            File file=new File(new File(mc.gameDirectory,"screenshots"),name);
            Files.deleteIfExists(file.toPath());
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
                if(file.isFile()&&file.length()>0){saved.incrementAndGet();LogUtils.getLogger().info("THAUMCRAFT_THAUMONOMICON_SMOKE_IMAGE: {}",file.getAbsolutePath());}
                else mc.execute(()->fail(mc,new AssertionError("Missing screenshot "+name)));
            });
        }catch(Throwable failure){fail(mc,failure);}
    }
    private static void startWorld(Minecraft mc) {
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
        if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        if(!mc.getLanguageManager().getSelected().equals("en_us")) {
            mc.getLanguageManager().setSelected("en_us");mc.options.languageCode="en_us";mc.options.save();
            work=mc.reloadResourcePacks();return;
        }
        started=true;previousTutorial=mc.options.tutorialStep;mc.options.tutorialStep=TutorialSteps.NONE;mc.getTutorial().stop();
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);mc.resizeDisplay();
        GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
        var settings=new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
        LogUtils.getLogger().info("THAUMCRAFT_THAUMONOMICON_SMOKE_WORLD: {}",WORLD);
        mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(0x54433614L,false,false),WorldPresets::createNormalWorldDimensions);
    }
    private static ServerPlayer player(Minecraft mc) {
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        require(player!=null,"Missing actual integrated player");return player;
    }
    private static void submit(Minecraft mc,Runnable action) {
        require(work==null,"Overlapping book audit tasks");
        CompletableFuture<Void> result=new CompletableFuture<>();work=result;
        mc.getSingleplayerServer().execute(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});
    }
    private static void prepareWorld(Minecraft mc) {
        var player=player(mc);player.setInvulnerable(true);
        for(BlockPos pos:BlockPos.betweenClosed(-3,110,-3,3,111,3))
            player.serverLevel().setBlockAndUpdate(pos,pos.getY()==110?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        player.teleportTo(.5,111,.5);
        player.getInventory().clearContent();
        player.getInventory().setItem(0,new ItemStack(ResearchModule.THAUMONOMICON.get()));
        player.getInventory().setItem(1,new ItemStack(Items.DIAMOND,3));
        player.getInventory().setItem(2,thaumcraft.alchemy.AspectCrystalItem.create(Aspect.AIR));
        player.inventoryMenu.broadcastChanges();
        var knowledge=KnowledgeStore.get(player);
        try {
            var stage=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);stage.setAccessible(true);
            var aspect=PlayerKnowledge.class.getDeclaredMethod("discoverAspect",Aspect.class);aspect.setAccessible(true);
            // Owned late-book UI fixture; this is not a claim that remaining survival branches work.
            for(ResearchEntry entry:ResearchCatalog.entries()) if(ResearchProgression.isImplemented(entry.key()))
                stage.invoke(knowledge,entry.key(),entry.stages().size()+1);
            for(Aspect value:Aspect.aspects.values())aspect.invoke(knowledge,value);
            for(String category:ResearchCategories.keys())for(KnowledgeType type:KnowledgeType.values())
                KnowledgeStore.of(player.serverLevel()).addKnowledge(player.getUUID(),type,category,type.units()*2+7);
            // Supplemental late addendum fact solely for the owned UI/Read packet fixture.
            stage.invoke(knowledge,"BELLOWS",ResearchCatalog.get("BELLOWS").stages().size()+1);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        gameBefore=gameplayState(knowledge);inventoryBefore=player.getInventory().save(new ListTag());
        lateFixture=knowledge.save();
        ResearchNetwork.sync(player);
    }
    private static void setFixtureStage(Minecraft mc,String key,int next) {
        try {
            var method=PlayerKnowledge.class.getDeclaredMethod("setResearchStage",String.class,int.class);method.setAccessible(true);
            method.invoke(KnowledgeStore.get(player(mc)),key,next);ResearchNetwork.sync(player(mc));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static ThaumonomiconPageScreen newPage(Minecraft mc,ResearchEntry entry) {
        var page=new ThaumonomiconPageScreen(browser,entry,PlayerKnowledge.load(snapshot),0);mc.setScreen(page);return page;
    }
    private static ThaumonomiconPageScreen layoutPage(Minecraft mc,ResearchEntry entry,List<ResearchEntry.Stage> fixture) {
        var page=new ThaumonomiconPageScreen(browser,entry,PlayerKnowledge.load(snapshot),0);
        page.layoutForSmokeTest(fixture);mc.setScreen(page);return page;
    }
    /** Replacement is confined to this newly created audit world and never used by production. */
    @SuppressWarnings("unchecked")
    private static void replaceFixtureKnowledge(Minecraft mc,CompoundTag data) {
        try {
            var store=KnowledgeStore.of(player(mc).serverLevel());
            var field=KnowledgeStore.class.getDeclaredField("players");field.setAccessible(true);
            ((Map<UUID,PlayerKnowledge>)field.get(store)).put(player(mc).getUUID(),PlayerKnowledge.load(data.copy()));
            store.setDirty();ResearchNetwork.sync(player(mc));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static List<ResearchEntry.Stage> suppliedChapters(ResearchEntry entry) {
        var result=new ArrayList<>(entry.stages());result.addAll(entry.addenda());return List.copyOf(result);
    }
    private static void prepareAudit(Minecraft mc) {
        browser=new ThaumonomiconScreen(PlayerKnowledge.load(snapshot),0);mc.setScreen(browser);
        Set<String> keys=new HashSet<>();
        for(ResearchEntry entry:ResearchCatalog.entries())if(!entry.supported()) {
            entries++;var page=layoutPage(mc,entry,suppliedChapters(entry));
            for(int chapter=0;chapter<page.chaptersForSmokeTest().size();chapter++) {
                chapters++;page.chapterForSmokeTest(chapter);page.auditLayoutForSmokeTest();
                for(var view:page.recipesForSmokeTest())keys.add(view.id().toString());
                for(int spread=0;spread<page.spreadCountForSmokeTest();spread++)audit.add(new Audit(page,chapter,spread));
            }
        }
        require(entries==148&&chapters==287,"Incomplete original entries/stages/addenda: "+entries+"/"+chapters);
        // Every registered display also renders, including recipes reached only by ingredient links.
        for(var definition:BookRecipeCatalog.allDefinitions()) {
            var page=layoutPage(mc,ResearchCatalog.get("FIRSTSTEPS"),
                    List.of(new ResearchEntry.Stage("",List.of(),List.of(definition.id().toString()),List.of(),0)));
            page.auditLayoutForSmokeTest();
            require(!page.recipesForSmokeTest().isEmpty(),"Unrendered registered display "+definition.id());
            for(int spread=0;spread<page.spreadCountForSmokeTest();spread++)audit.add(new Audit(page,0,spread));
        }
        recipes=BookRecipeCatalog.recipeCount();require(recipes==375,"Incomplete pinned recipe inventory");
        var seal=BookRecipeCatalog.allDefinitions().stream().filter(d->d.research().equals("SEALCOLLECT&&MINDBIOTHAUMIC")).findFirst().orElseThrow();
        var sealView=BookRecipeViews.resolve(seal.id().toString()).get(0);
        CompoundTag gate=snapshot.copy();var stages=gate.getCompound("ResearchStages");
        stages.putInt("SEALCOLLECT",ResearchCatalog.get("SEALCOLLECT").stages().size()+1);
        stages.remove("MINDBIOTHAUMIC");
        require(!sealView.unlocked(PlayerKnowledge.load(gate)),"Advanced seal ignored its second research condition");
        stages.putInt("MINDBIOTHAUMIC",ResearchCatalog.get("MINDBIOTHAUMIC").stages().size()+1);
        require(sealView.unlocked(PlayerKnowledge.load(gate)),"Advanced seal compound gate not displayed as satisfied");
        for(String key:List.of("basics","no-archive","first-stage","second-stage","ore-hidden","addendum-locked","addendum-open"))
            scenes.add(new Scene("normal-"+key,"fresh",key));
        var knowledge=PlayerKnowledge.load(snapshot);
        require(ResearchCatalog.entries().stream().filter(e->ResearchProgression.isImplemented(e.key())).count()==85,"Unexpected implemented research inventory");
        for(String category:ResearchCategories.keys()) if(ResearchCategories.categoryUnlocked(knowledge,category))
            scenes.add(new Scene("map-"+category.toLowerCase(Locale.ROOT),"map",category));
        for(String kind:List.of("crafting","arcane","crucible","infusion","infusion_enchantment","runic","salis")) {
            var selected=firstPlayable(kind,knowledge);
            // A registered late recipe is not a normal-player recipe until its chapter and gate open.
            if(selected.isEmpty()) continue;
            String key=selected.orElseThrow().id().toString();
            if(kind.equals("infusion"))productInfusion=key;
            productRecipeKinds++;scenes.add(new Scene("recipe-"+kind,"recipe",key));
        }
        require(productInfusion!=null&&productRecipeKinds>=5,"Operational early recipe families missing");
        for(var blueprint:MultiblockCatalog.all()) if(ResearchProgression.isImplemented(blueprint.research())
                && readableStructure(knowledge,blueprint.id())) {
            productStructures++;scenes.add(new Scene("structure-"+blueprint.id().getPath(),"structure",blueprint.id().toString()));
        }
        scenes.add(new Scene("structure-rotated","rotated","thaumcraft:infusionaltar"));
        scenes.add(new Scene("structure-layers","layers","thaumcraft:infusionaltar"));
        scenes.add(new Scene("requirements-items","requirements","ESSENTIASMELTER"));
        scenes.add(new Scene("requirements-knowledge-craft","requirements","ESSENTIASMELTER"));
        scenes.add(new Scene("requirements-costs","requirements","ESSENTIASMELTER"));
        scenes.add(new Scene("aspects","aspects",""));
        scenes.add(new Scene("knowledge","knowledge",""));
        scenes.add(new Scene("recipe-search","search",""));
        scenes.add(new Scene("recipe-history","history",""));
        scenes.add(new Scene("new-pages","unread","ESSENTIASMELTER"));
        scenes.add(new Scene("read-pages","read","ESSENTIASMELTER"));
        scenes.add(new Scene("notification","toast",""));
        scenes.add(new Scene("gui-scale-three","scale","INFUSION"));
        scenes.add(new Scene("ru-infusion","ru-recipe",productInfusion));
        scenes.add(new Scene("ru-knowledge","ru-knowledge",""));
        scenes.add(new Scene("ru-structure","ru-structure","thaumcraft:infusionaltar"));
        LogUtils.getLogger().info("THAUMCRAFT_THAUMONOMICON_AUDIT_PREPARED: supplied internal layout={} entries/{} chapters/{} queued spreads/{} recipes; normal product={} scenes/{} operational recipe kinds/{} accessible structures",entries,chapters,audit.size(),recipes,scenes.size(),productRecipeKinds,productStructures);
        prepared=false;
    }
    private static void showAudit(Minecraft mc,Audit item) {
        mc.setScreen(item.page);item.page.chapterForSmokeTest(item.chapter);item.page.spreadForSmokeTest(item.spread);
        item.page.auditLayoutForSmokeTest();auditRendered=false;
    }
    private static List<BookRecipeViews.View> readableRecipes(PlayerKnowledge knowledge) {
        var result=new ArrayList<BookRecipeViews.View>();
        for(var entry:ResearchCatalog.entries()) if(ResearchBookVisibility.visible(knowledge,entry,false))
            for(var chapter:ResearchBookVisibility.readableChapters(knowledge,entry,false))
                for(String raw:chapter.recipes()) for(var view:BookRecipeViews.playable(raw))
                    if(view.unlocked(knowledge)) result.add(view);
        return List.copyOf(result);
    }
    private static Optional<BookRecipeViews.View> firstPlayable(String kind,PlayerKnowledge knowledge) {
        return readableRecipes(knowledge).stream().filter(view->view.kind().equals(kind)).findFirst();
    }
    private static boolean readableStructure(PlayerKnowledge knowledge,ResourceLocation id) {
        return ResearchCatalog.entries().stream().filter(entry->ResearchBookVisibility.visible(knowledge,entry,false))
                .flatMap(entry->ResearchBookVisibility.readableChapters(knowledge,entry,false).stream())
                .flatMap(chapter->chapter.recipes().stream())
                .anyMatch(raw->MultiblockCatalog.resolve(raw).map(blueprint->blueprint.id().equals(id)).orElse(false));
    }
    private static ThaumonomiconPageScreen openPlayableRecipe(Minecraft mc,String recipe) {
        var id=ResourceLocation.parse(recipe);var knowledge=PlayerKnowledge.load(snapshot);
        for(var entry:ResearchCatalog.entries()) if(ResearchBookVisibility.visible(knowledge,entry,false))
            for(var chapter:ResearchBookVisibility.readableChapters(knowledge,entry,false))
                for(String raw:chapter.recipes())
                    if(BookRecipeViews.playable(raw).stream().anyMatch(view->view.id().equals(id)&&view.unlocked(knowledge))) {
                        browser.selectForSmokeTest(entry.key());
                        require(mc.screen instanceof ThaumonomiconPageScreen,"Playable recipe owner did not open "+entry.key());
                        var page=(ThaumonomiconPageScreen)mc.screen;
                        require(page.focusRecipe(id),"Readable recipe disappeared "+id);
                        require(page.recipesForSmokeTest().stream().noneMatch(BookRecipeViews.View::reference),"Internal pinned reference leaked into normal book");
                        return page;
                    }
        throw new AssertionError("No readable normal-player owner for "+recipe);
    }
    private static CompoundTag freshFixture(String kind) {
        var data=new PlayerKnowledge().save();
        var facts=data.getList("Research",Tag.TAG_STRING);facts.add(StringTag.valueOf("!gotthaumonomicon"));data.put("Research",facts);
        var stages=data.getCompound("ResearchStages");
        if(kind.equals("first-stage"))stages.putInt("FIRSTSTEPS",1);
        if(kind.equals("second-stage"))stages.putInt("FIRSTSTEPS",2);
        if(kind.equals("ore-hidden")) {
            stages.putInt("FIRSTSTEPS",ResearchCatalog.get("FIRSTSTEPS").stages().size()+1);
            stages.putInt("KNOWLEDGETYPES",ResearchCatalog.get("KNOWLEDGETYPES").stages().size()+1);
        }
        data.put("ResearchStages",stages);
        if(kind.startsWith("addendum-")) {
            data=lateFixture.copy();facts=data.getList("Research",Tag.TAG_STRING);
            var clean=new ListTag();
            for(var fact:facts)if(!fact.getAsString().startsWith("!ORE"))clean.add(fact.copy());
            if(kind.equals("addendum-open"))clean.add(StringTag.valueOf("!OREAMBER"));
            data.put("Research",clean);data.remove("BookRead");
        }
        return data;
    }
    private static void showFreshScene(Minecraft mc,Scene scene) {
        var knowledge=PlayerKnowledge.load(snapshot);
        browser=new ThaumonomiconScreen(knowledge,0);mc.setScreen(browser);
        if(scene.key.equals("basics")||scene.key.equals("no-archive")) {
            require(browser.categoriesForSmokeTest().equals(List.of("BASICS")),"Fresh book exposed a future category");
            require(browser.entriesForSmokeTest().stream().noneMatch(key->key.startsWith("PORT_")),"Fresh book exposed development lessons");
            if(scene.key.equals("no-archive")) {
                var categories=browser.categoriesForSmokeTest();var nodes=browser.entriesForSmokeTest();
                browser.keyPressed(GLFW.GLFW_KEY_TAB,0,0);
                browser.mouseClicked(mc.getWindow().getGuiScaledWidth()/2.0,mc.getWindow().getGuiScaledHeight()-7,0);
                require(mc.screen==browser&&categories.equals(browser.categoriesForSmokeTest())&&nodes.equals(browser.entriesForSmokeTest()),"Tab/footer disclosed a reference mode");
                browser.selectCategoryForSmokeTest("ELDRITCH");browser.selectCategoryForSmokeTest("PORT");
                require(browser.categoriesForSmokeTest().equals(List.of("BASICS")),"Closed category selection escaped initial book");
                browser.searchForSmokeTest("");
                require(browser.recipeSearchForSmokeTest().isEmpty(),"Fresh search disclosed future recipe contents");
                require(browser.searchResultsForSmokeTest().stream().allMatch(key->ResearchProgression.isImplemented(key)),"Fresh search disclosed unsupported records");
                browser.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
            }
        } else if(scene.key.equals("ore-hidden")) {
            require(!browser.entriesForSmokeTest().contains("ORE"),"Hidden ore root appeared before an ore fact");
            browser.selectForSmokeTest("ORE");require(mc.screen==browser,"Unseen hidden ore opened a page");
        } else {
            String key=scene.key.startsWith("addendum-")?"ORE":"FIRSTSTEPS";
            browser.selectForSmokeTest(key);require(mc.screen instanceof ThaumonomiconPageScreen,"Normal opened stage unavailable "+key);
            var page=(ThaumonomiconPageScreen)mc.screen;
            if(scene.key.equals("first-stage")||scene.key.equals("second-stage")) {
                int stage=scene.key.equals("first-stage")?1:2;
                require(page.chaptersForSmokeTest().equals(List.of(ResearchCatalog.get(key).stages().get(stage-1).text())),"Normal page exposed a future or past stage");
            } else {
                int expected=scene.key.equals("addendum-open")?2:1;
                require(page.chaptersForSmokeTest().size()==expected,"Ore addendum strict fact gate changed");
                if(expected==2)page.chapterForSmokeTest(1);
            }
            require(page.recipesForSmokeTest().stream().noneMatch(BookRecipeViews.View::reference),"Normal stage exposed a supplied reference recipe");
            page.auditLayoutForSmokeTest();
        }
        normalChecks++;
    }
    private static void prepareScene(Minecraft mc,Scene scene) {
        var knowledge=PlayerKnowledge.load(snapshot);
        browser=new ThaumonomiconScreen(knowledge,0);mc.setScreen(browser);
        if(scene.kind.equals("fresh")) {
            var fixture=freshFixture(scene.key);snapshot=null;
            submit(mc,()->replaceFixtureKnowledge(mc,fixture));return;
        }
        if(scene.kind.equals("map")){browser.selectCategoryForSmokeTest(scene.key);return;}
        if(scene.kind.equals("recipe")) {
            openPlayableRecipe(mc,scene.key).auditLayoutForSmokeTest();return;
        }
        if(Set.of("structure","rotated","layers","scale").contains(scene.kind)) {
            String id=scene.kind.equals("scale")?"thaumcraft:infusionaltar":scene.key;
            var blueprint=MultiblockCatalog.resolve(id).orElseThrow();var page=newPage(mc,ResearchCatalog.get(blueprint.research()));
            page.showStructureForSmokeTest(id);var view=page.structureForSmokeTest(id);
            view.rotate(45);require(view.yaw()==0,"Rotation failed");view.setRemovedTopLayers(1);
            require(view.detachedPreview().states().size()<blueprint.preview(0).states().size(),"Layer did not disappear");
            view.reset();require(view.yaw()==-45&&view.pitch()==25&&view.removedTopLayers()==0,"Reset failed");
            if(scene.kind.equals("rotated"))view.rotate(90);
            if(scene.kind.equals("layers"))view.setRemovedTopLayers(1);
            if(scene.kind.equals("scale")){mc.options.guiScale().set(3);mc.resizeDisplay();page.showStructureForSmokeTest(id);page.auditLayoutForSmokeTest();}
            return;
        }
        if(scene.kind.equals("requirements")) {
            // A real owned server fixture prevents a read response replacing a detached UI snapshot.
            submit(mc,()->setFixtureStage(mc,"ESSENTIASMELTER",scene.name.equals("requirements-items")?1:3));return;
        }
        if(scene.kind.startsWith("ru-")) {
            mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.getLanguageManager().setSelected("ru_ru");mc.options.languageCode="ru_ru";mc.options.save();
            work=mc.reloadResourcePacks();return;
        }
        if(scene.kind.equals("aspects")||scene.kind.equals("knowledge")) {
            var page=newPage(mc,ResearchCatalog.get("FIRSTSTEPS"));
            page.insertForSmokeTest(scene.kind.equals("aspects")?ThaumonomiconKnowledgeScreen.Mode.ASPECTS:ThaumonomiconKnowledgeScreen.Mode.KNOWLEDGE);
            require(((ThaumonomiconKnowledgeScreen)mc.screen).accessible(),"Known insert inaccessible");return;
        }
        if(scene.kind.equals("search")) {
            var id=ResourceLocation.parse(productInfusion);
            String needle=readableRecipes(knowledge).stream().filter(view->view.id().equals(id)).findFirst().orElseThrow().output().getHoverName().getString();
            browser.searchForSmokeTest(needle);
            require(browser.recipeSearchForSmokeTest().contains(id),"Operational recipe result search missing");return;
        }
        if(scene.kind.equals("history")) {
            linkParent=newPage(mc,ResearchCatalog.get("INFUSION"));linkParent.showStructureForSmokeTest("infusionaltar");
            int spread=linkParent.spreadForSmokeTest();
            linkParent.itemLinkForSmokeTest(BookRecipeCatalog.definitions("thaumcraft:ThaumiumIngot").get(0).output(),null);
            require(mc.screen instanceof ThaumonomiconPageScreen&&mc.screen!=linkParent,"Ingredient link did not open a recipe");
            ((ThaumonomiconPageScreen)mc.screen).onClose();
            require(mc.screen==linkParent&&linkParent.spreadForSmokeTest()==spread,"Recipe history lost the return spread");return;
        }
        if(scene.kind.equals("toast")) {
            var notices=ResearchBookNotifications.between(new PlayerKnowledge(),knowledge);
            require(!notices.isEmpty(),"Actual completed fixture produced no notification");
            mc.getToasts().clear();mc.getToasts().addToast(new ResearchBookToast(notices.get(0)));return;
        }
        if(scene.kind.equals("unread")) {
            require(knowledge.hasUnreadResearch("ESSENTIASMELTER")&&knowledge.hasUnreadPage("ESSENTIASMELTER"),"New research/page markers absent");
            browser.selectCategoryForSmokeTest("ALCHEMY");return;
        }
        if(scene.kind.equals("read")) {
            browser.selectForSmokeTest("ESSENTIASMELTER");require(mc.screen instanceof ThaumonomiconPageScreen,"Cannot open implemented smelter entry");
            var page=(ThaumonomiconPageScreen)mc.screen;require(page.chaptersForSmokeTest().size()==2,"Strict bellows addendum missing");page.chapterForSmokeTest(1);
        }
    }
    private static void showRequirements(Minecraft mc,Scene scene) {
        var knowledge=PlayerKnowledge.load(snapshot);
        browser=new ThaumonomiconScreen(knowledge,0);mc.setScreen(browser);
        browser.selectForSmokeTest("ESSENTIASMELTER");require(mc.screen instanceof ThaumonomiconPageScreen,"Actual requirement stage inaccessible");
        var page=(ThaumonomiconPageScreen)mc.screen;
        if(scene.name.equals("requirements-items")) {
            var stage=ResearchCatalog.get("ESSENTIASMELTER").stages().get(0);
            var rows=ResearchBookRequirements.rows(stage,knowledge,mc.player.getInventory());
            require(rows.stream().filter(ResearchBookRequirements.Row::met).count()==1,"Actual client inventory requirement preview wrong");
            ItemStack old=mc.player.getInventory().getItem(2).copy();mc.player.getInventory().setItem(2,ItemStack.EMPTY);
            for(int i=0;i<5;i++)page.tick();
            require(!ResearchBookRequirements.rows(stage,knowledge,mc.player.getInventory()).get(0).met(),"Inventory removal remained cached");
            mc.player.getInventory().setItem(2,old);for(int i=0;i<5;i++)page.tick();
        }
        page.auditLayoutForSmokeTest();
        page.spreadForSmokeTest(Math.max(0,page.spreadCountForSmokeTest()-(scene.name.equals("requirements-costs")?2:1)));
    }
    private static void showRussianScene(Minecraft mc,Scene scene) {
        browser=new ThaumonomiconScreen(PlayerKnowledge.load(snapshot),0);mc.setScreen(browser);
        if(scene.kind.equals("ru-recipe")) {
            openPlayableRecipe(mc,scene.key).auditLayoutForSmokeTest();
        }else if(scene.kind.equals("ru-structure")) {
            var page=newPage(mc,ResearchCatalog.get("INFUSION"));page.showStructureForSmokeTest(scene.key);page.auditLayoutForSmokeTest();
        }else newPage(mc,ResearchCatalog.get("FIRSTSTEPS")).insertForSmokeTest(ThaumonomiconKnowledgeScreen.Mode.KNOWLEDGE);
    }
    private static void finish(Minecraft mc) {
        if(phase==0){phase=1;submit(mc,()->{
            require(gameBefore.equals(gameplayState(KnowledgeStore.get(player(mc)))),"Book UI changed authoritative gameplay");
            require(inventoryBefore.equals(player(mc).getInventory().save(new ListTag())),"Book changed server inventory");
        });return;}
        require(packetChecked&&normalChecks==7&&saved.get()==scenes.size(),"Incomplete normal-book, packet or screenshot evidence");
        stopped=true;mc.options.tutorialStep=previousTutorial;
        LogUtils.getLogger().info("THAUMCRAFT_THAUMONOMICON_NORMAL_PRODUCT_OK: {} native supplied early-state scenes;85 supported records; no archive/PORT route, closed categories/hidden ore/stage and strict addendum gates; {} operational recipe kinds; {} accessible structures; native C2S/S2C read acknowledgments",normalChecks,productRecipeKinds,productStructures);
        LogUtils.getLogger().info("THAUMCRAFT_THAUMONOMICON_RENDER_AUDIT_OK: INTERNAL SUPPLIED LAYOUT: {} entries; {} original chapters; {} actual render visits / {} distinct spread requests; {} recipe displays; not player progress. Normal product: ingredient history/search, two GUI scales and native read acknowledgments",entries,chapters,spreads,audit.size(),recipes);
        LogUtils.getLogger().info("THAUMCRAFT_THAUMONOMICON_COMPLETE_CLIENT_SMOKE_OK: {} scenes; isolated owned world={}",saved.get(),WORLD);mc.stop();
    }
    private static void fail(Minecraft mc,Throwable failure) {
        if(stopped)return;stopped=true;if(previousTutorial!=null)mc.options.tutorialStep=previousTutorial;
        LogUtils.getLogger().error("THAUMCRAFT_THAUMONOMICON_COMPLETE_CLIENT_SMOKE_FAILED",failure);mc.stop();
    }
}
