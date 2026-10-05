package thaumcraft.golemancy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import thaumcraft.golemancy.press.GolemDesign;
import thaumcraft.golemancy.seals.core.*;
import java.util.*;

/** Original 176x232 wax seal menu, radial tabs and centered ghost filters. */
public final class SealScreen extends AbstractContainerScreen<SealMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_base.png");
    private record Control(int action,int x,int y,int w,int h,int u,int v,Component caption,Component detail,boolean selected){}
    private final List<Control> controls=new ArrayList<>();
    private static final Map<String,String> TOGGLE_NAMES=Map.ofEntries(Map.entry("pmeta","meta"),Map.entry("pnbt","nbt"),Map.entry("pore","ore"),Map.entry("pmod","mod"),Map.entry("pcycle","cycle"),Map.entry("pleave","leave"),Map.entry("pexist","exist"),Map.entry("psilk","silk"),Map.entry("pmob","mob"),Map.entry("panimal","animal"),Map.entry("pplayer","player"),Map.entry("pleft","left"),Map.entry("pempty","empty"),Map.entry("pemptyhand","emptyhand"),Map.entry("psneak","sneak"),Map.entry("ppro","provision.wl"),Map.entry("psing","single"),Map.entry("prep","replant"));
    public SealScreen(SealMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=232;}
    public void action(int action){SealNetwork.button(menu,action);}
    @Override protected void renderLabels(GuiGraphics gui,int x,int y){}
    private void control(int action,int x,int y,int w,int h,int u,int v,Component caption,boolean selected){controls.add(new Control(action,x,y,w,h,u,v,caption,Component.empty(),selected));}
    private void minus(int action,int x,int y){control(action,x,y,10,10,0,0,Component.literal("−"),false);}
    private void plus(int action,int x,int y){control(action,x,y,10,10,10,0,Component.literal("+"),false);}
    private void controls(){
        controls.clear();var seal=menu.seal();var cats=menu.categories();float slice=60F/cats.size(),start=-180+(cats.size()-1)*slice/2;slice=Math.max(12,Math.min(24,slice));
        for(int i=0;i<cats.size();i++){int cat=cats.get(i),x=88+(int)(Math.cos(Math.toRadians(start-i*slice))*86)-8,y=72+(int)(Math.sin(Math.toRadians(start-i*slice))*86)-8;
            if(cats.size()>1)control(i,x,y,16,16,cat*16,120,Component.translatable("button.category."+cat),menu.category()==cat);}
        int rsx=88+(int)(Math.cos(Math.toRadians(start-cats.size()*slice))*86)-8,rsy=72+(int)(Math.sin(Math.toRadians(start-cats.size()*slice))*86)-8;
        control(seal.redstone()?28:27,rsx,rsy,16,16,seal.redstone()?64:80,136,Component.translatable(seal.redstone()?"golem.prop.redon":"golem.prop.redoff"),seal.redstone());
        switch(menu.category()){
            case 0->{minus(80,69,55);plus(81,97,55);minus(82,94,76);plus(83,117,76);control(seal.locked()?26:25,56,72,16,16,seal.locked()?32:48,136,Component.translatable(seal.locked()?"golem.prop.lock":"golem.prop.unlock"),seal.locked());}
            case 1->{int size=menu.filterSlots(),sy=16+(size-1)/3*12;control(seal.blacklist()?21:20,80,72+(size-1)/3*24-sy+27,16,16,seal.blacklist()?0:16,136,Component.translatable(seal.blacklist()?"button.bl":"button.wl"),true);}
            case 2->{minus(90,69,47);plus(91,97,47);minus(92,69,72);plus(93,97,72);minus(94,69,97);plus(95,97,97);}
            case 3->{int count=seal.toggles().size(),step=count<4?8:count<6?7:count<9?6:5,h=(count-1)*step,w=12;
                for(String key:seal.toggles().keySet())w=Math.max(w,(12+Math.min(100,font.width(toggleName(key))))/2);
                int i=0;for(var entry:seal.toggles().entrySet()){Component name=toggleName(entry.getKey());int action=(entry.getValue()?60:30)+i;control(action,88-w-2,72-7-h+i*step*2,12,12,entry.getValue()?18:2,18,name,entry.getValue());i++;}}
            default->{}
        }
    }
    private Component toggleName(String key){String caption=key.equals("ppro")&&menu.seal().type().equals("thaumcraft:harvest")?"provision":TOGGLE_NAMES.getOrDefault(key,key.startsWith("p")?key.substring(1):key);return Component.translatable("golem.prop."+caption);}
    @Override protected void renderBg(GuiGraphics gui,float partial,int mouseX,int mouseY){
        var seal=menu.seal();int category=menu.category();controls();
        gui.blit(TEXTURE,leftPos+8,topPos-8,96,0,160,160);gui.blit(TEXTURE,leftPos,topPos+143,0,167,176,89);
        gui.drawCenteredString(font,Component.translatable("button.category."+category),leftPos+88,topPos+8,0xffffff);
        switch(category){
            case 0->{gui.drawCenteredString(font,Component.translatable("golem.prop.priority"),leftPos+88,topPos+44,12299007);gui.drawCenteredString(font,Integer.toString(seal.priority()),leftPos+88,topPos+56,0xffffff);
                gui.blit(TEXTURE,leftPos+105,topPos+75,2,18,12,12);
                if(seal.color()>0){int color=DyeColor.byId(16-seal.color()).getTextColor();gui.setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);gui.blit(TEXTURE,leftPos+108,topPos+78,74,31,6,6);gui.setColor(1,1,1,1);}
                if(minecraft.player!=null&&seal.owner().equals(minecraft.player.getUUID()))gui.drawCenteredString(font,Component.translatable("golem.prop.owner"),leftPos+88,topPos+104,12299007);
                if(isHovering(93,75,36,12,mouseX,mouseY)){Component color=seal.color()==0?Component.translatable("golem.prop.colorall"):Component.translatable("golem.prop.color",Component.translatable("color.minecraft."+DyeColor.byId(16-seal.color()).getName()));gui.drawCenteredString(font,color,leftPos+111,topPos+89,0xffffff);}}
            case 1->{int size=menu.filterSlots(),sx=16+(size-1)%3*12,sy=16+(size-1)/3*12;for(int i=0;i<size;i++)gui.blit(TEXTURE,leftPos+88+i%3*24-sx,topPos+72+i/3*24-sy,0,56,32,32);}
            case 2->{String[] axes={"y","x","z"};int[] values={seal.area().getY(),seal.area().getX(),seal.area().getZ()};for(int i=0;i<3;i++){int y=48+i*24;gui.drawCenteredString(font,Component.translatable("button.caption."+axes[i]),leftPos+88,topPos+y-9,14540253);gui.drawCenteredString(font,Integer.toString(values[i]),leftPos+88,topPos+y,0xffffff);}}
            case 4->{gui.drawCenteredString(font,Component.translatable("button.caption.required"),leftPos+88,topPos+46,14540253);gui.drawCenteredString(font,Component.translatable("button.caption.forbidden"),leftPos+88,topPos+78,14540253);
                SealBehavior behavior=SealRegistry.behavior(seal.type());if(behavior!=null){traits(gui,behavior.requiredTraits(),64);traits(gui,behavior.forbiddenTraits(),96);}}
            default->{}
        }
        for(Control control:controls){boolean hover=isHovering(control.x,control.y,control.w,control.h,mouseX,mouseY);float tint=control.selected||hover?1:.7F;gui.setColor(tint,tint,tint,1);gui.blit(TEXTURE,leftPos+control.x,topPos+control.y,control.u,control.v,control.w,control.h);gui.setColor(1,1,1,1);
            if(category==3&&control.action>=30&&control.action<76)gui.drawString(font,control.caption,leftPos+control.x+14,topPos+control.y+2,0xffffff,true);}
    }
    private void traits(GuiGraphics gui,Set<String> names,int y){int i=0;for(String name:names){try{var trait=GolemDesign.Trait.valueOf(name.toUpperCase(Locale.ROOT));gui.blit(trait.icon(),leftPos+80+i*18-(names.size()-1)*9,topPos+y-8,0,0,16,16,16,16);}catch(IllegalArgumentException ignored){}i++;}}
    @Override public void render(GuiGraphics gui,int x,int y,float partial){
        renderBackground(gui);super.render(gui,x,y,partial);var seal=menu.seal();SealBehavior behavior=SealRegistry.behavior(seal.type());
        if(menu.category()==1&&behavior!=null&&behavior.hasStacksizeLimiters()&&!seal.blacklist())for(int i=0;i<menu.filterSlots();i++){var slot=menu.getSlot(i);if(!slot.getItem().isEmpty()){String size=seal.filterSize(i)==0?"§6*":Integer.toString(seal.filterSize(i));gui.pose().pushPose();gui.pose().translate(0,0,201);gui.drawString(font,size,leftPos+slot.x+17-font.width(size),topPos+slot.y+9,0xffffff,true);gui.pose().popPose();}}
        renderTooltip(gui,x,y);
        for(Control control:controls)if(isHovering(control.x,control.y,control.w,control.h,x,y)){gui.renderTooltip(font,control.caption,x,y);return;}
        if(menu.category()==4&&behavior!=null){if(traitTooltip(gui,behavior.requiredTraits(),64,x,y))return;traitTooltip(gui,behavior.forbiddenTraits(),96,x,y);}
    }
    private boolean traitTooltip(GuiGraphics gui,Set<String> names,int y,int mouseX,int mouseY){int i=0;for(String name:names){if(isHovering(80+i*18-(names.size()-1)*9,y-8,16,16,mouseX,mouseY))try{var trait=GolemDesign.Trait.valueOf(name.toUpperCase(Locale.ROOT));gui.renderTooltip(font,List.of(Component.translatable(trait.nameKey()),Component.translatable(trait.descriptionKey())),Optional.empty(),mouseX,mouseY);return true;}catch(IllegalArgumentException ignored){}i++;}return false;}
    @Override public boolean mouseClicked(double x,double y,int button){
        controls();if(button==0)for(Control control:controls)if(isHovering(control.x,control.y,control.w,control.h,x,y)){action(control.action);return true;}
        if(menu.category()==1&&(button==0||button==1))for(int i=0;i<menu.filterSlots();i++){var slot=menu.getSlot(i);if(isHovering(slot.x,slot.y,16,16,x,y)){SealNetwork.ghost(menu,i,button,hasShiftDown());return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override protected boolean checkHotbarKeyPressed(int key,int scan){return false;}
}
