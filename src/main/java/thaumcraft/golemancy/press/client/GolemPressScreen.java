package thaumcraft.golemancy.press.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.research.ResearchClient;
import thaumcraft.golemancy.press.*;
import java.util.*;

/** Original 208x224 texture, selector/trait/component positions, output slot and progress strip. */
public final class GolemPressScreen extends AbstractContainerScreen<GolemPressMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_golembuilder.png");
    private static final ResourceLocation ARROWS=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_base.png");
    private final EnumMap<GolemDesign.Category,List<GolemDesign.Part>> choices=new EnumMap<>(GolemDesign.Category.class);
    private final EnumMap<GolemDesign.Category,Integer> indexes=new EnumMap<>(GolemDesign.Category.class);
    private GolemDesign design;
    private int ticks;
    private String lastKnowledge="";
    public GolemPressScreen(GolemPressMenu menu,Inventory inventory,Component title) {super(menu,inventory,title);imageWidth=208;imageHeight=224;}
    @Override protected void init() {super.init();refreshChoices();}
    public GolemDesign design() {return design;}
    public boolean canCreate() {return design!=null&&menu.cost()==0&&menu.checkedDesignId()==design.props()&&menu.owns().size()==design.components().size()&&menu.owns().stream().allMatch(Boolean::booleanValue);}
    public void selectForSmokeTest(long id) {
        var selected=GolemDesign.parse(id).orElseThrow();for(var category:GolemDesign.Category.values()) {var list=choices.getOrDefault(category,List.of());int index=list.indexOf(selected.part(category));if(index<0)throw new IllegalArgumentException("Locked selector");indexes.put(category,index);}gather();
    }
    public void create() {if(canCreate())GolemPressNetwork.request(menu,design.props(),true);}
    private void refreshChoices() {
        var knowledge=ResearchClient.golemPressKnowledge();String signature=knowledge.save().toString();if(signature.equals(lastKnowledge)&&design!=null)return;lastKnowledge=signature;
        for(var category:GolemDesign.Category.values()) {var available=GolemDesign.choices(category,knowledge);choices.put(category,available);indexes.put(category,Math.min(indexes.getOrDefault(category,0),Math.max(0,available.size()-1)));}gather();
    }
    private void gather() {
        if(choices.values().stream().anyMatch(List::isEmpty)) {design=null;return;}
        int[] ids=new int[5];for(var category:GolemDesign.Category.values())ids[category.byteIndex()]=choices.get(category).get(indexes.get(category)).id();
        design=GolemDesign.create(ids[0],ids[1],ids[2],ids[3],ids[4]).orElseThrow();GolemPressNetwork.request(menu,design.props(),false);
    }
    @Override protected void containerTick() {super.containerTick();if(++ticks%10==0) {refreshChoices();if(design!=null)GolemPressNetwork.request(menu,design.props(),false);}}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y) {}
    private static int[] position(GolemDesign.Category category) {return switch(category) {case MATERIAL->new int[]{16,16};case HEAD->new int[]{112,16};case ARMS->new int[]{112,40};case LEGS->new int[]{112,64};case ADDON->new int[]{16,64};};}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        g.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);
        for(var category:GolemDesign.Category.values()) {int[] p=position(category);g.blit(TEXTURE,leftPos+p[0]-4,topPos+p[1]-4,228,124,24,24);}
        if(design==null) {g.pose().pushPose();g.pose().translate(leftPos+104,topPos+54,0);g.pose().scale(.65f,.65f,1);g.drawCenteredString(font,Component.translatable("thaumcraft.golem_press.locked"),0,0,0xeeeeee);g.pose().popPose();return;}
        for(var category:GolemDesign.Category.values()) {int[] p=position(category);var part=design.part(category);
            if(!part.key().equals("NONE")) {int color=part.itemColor();g.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);g.blit(part.icon(),leftPos+p[0],topPos+p[1],0,0,16,16,16,16);g.setColor(1,1,1,1);}
            if(choices.get(category).size()>1) {g.blit(ARROWS,leftPos+p[0]-11,topPos+p[1]+3,20,0,10,10);g.blit(ARROWS,leftPos+p[0]+17,topPos+p[1]+3,30,0,10,10);}}
        var traits=new ArrayList<>(design.traits());int yy=traits.size()<=4?(traits.size()-1)%4*8:24,xx=(traits.size()-1)/4%4*8;
        for(int i=0;i<traits.size();i++)g.blit(traits.get(i).icon(),leftPos+64+i/4*16-xx,topPos+40+i%4*16-yy,0,0,16,16,16,16);
        g.blit(Aspect.MECHANISM.getImage(),leftPos+144,topPos+16,0,0,16,16,16,16);
        g.drawString(font,Integer.toString(design.essentiaCost()),leftPos+162-font.width(Integer.toString(design.essentiaCost())),topPos+24,0xffffff,false);
        var components=design.components();var owns=menu.owns();
        for(int i=0;i<components.size();i++) {int index=i+1,px=leftPos+144+index/4*16,py=topPos+16+index%4*16;
            g.renderItem(components.get(i),px,py);g.renderItemDecorations(font,components.get(i),px,py);
            if(menu.checkedDesignId()!=design.props()||owns.size()<=i||!owns.get(i)) {g.setColor(1,1,1,.5f);g.blit(TEXTURE,px,py,240,0,16,16);g.setColor(1,1,1,1);}}
        if(menu.cost()>0&&menu.maxCost()>0)g.blit(TEXTURE,leftPos+145,topPos+89,209,89,Math.max(0,Math.min(46,(int)(46f*(1-menu.cost()/(float)menu.maxCost())))),6);
        g.drawCenteredString(font,Float.toString(design.health()/2f),leftPos+48,topPos+108,0xffffff);g.drawCenteredString(font,Float.toString(design.armor()/2f),leftPos+72,topPos+108,0xffffff);g.drawCenteredString(font,Float.toString((float)(design.attackDamage()/2)),leftPos+97,topPos+108,0xffffff);
        g.blit(TEXTURE,leftPos+120,topPos+104,216,64,24,16);if(!canCreate())g.blit(TEXTURE,leftPos+120,topPos+104,216,40,24,16);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);if(design==null)return;
        for(var category:GolemDesign.Category.values()) {int[] p=position(category);var part=design.part(category);if(!part.key().equals("NONE")&&isHovering(p[0],p[1],16,16,x,y)) {g.renderTooltip(font,List.of(Component.translatable(part.nameKey()),Component.translatable(part.descriptionKey())),Optional.empty(),x,y);return;}}
        var traits=new ArrayList<>(design.traits());int yy=traits.size()<=4?(traits.size()-1)%4*8:24,xx=(traits.size()-1)/4%4*8;
        for(int i=0;i<traits.size();i++)if(isHovering(64+i/4*16-xx,40+i%4*16-yy,16,16,x,y)) {var trait=traits.get(i);g.renderTooltip(font,List.of(Component.translatable(trait.nameKey()),Component.translatable(trait.descriptionKey())),Optional.empty(),x,y);return;}
        var components=design.components();for(int i=0;i<components.size();i++)if(isHovering(144+(i+1)/4*16,16+(i+1)%4*16,16,16,x,y))g.renderTooltip(font,components.get(i),x,y);
        if(isHovering(120,104,24,16,x,y))g.renderTooltip(font,Component.translatable("thaumcraft.golem_press.craft"),x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0&&design!=null&&menu.cost()==0) {
            for(var category:GolemDesign.Category.values()) {var list=choices.get(category);if(list.size()<2)continue;int[] p=position(category);
                if(isHovering(p[0]-11,p[1]+3,10,10,x,y)||isHovering(p[0]+17,p[1]+3,10,10,x,y)) {int delta=x-leftPos<p[0]?-1:1;indexes.put(category,Math.floorMod(indexes.get(category)+delta,list.size()));gather();return true;}}
            if(isHovering(120,104,24,16,x,y)) {create();return true;}
        }return super.mouseClicked(x,y,button);
    }
}
