package thaumcraft.golemancy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import thaumcraft.golemancy.seals.core.*;

/** Original nine-by-nine provider inventory and count/search/request controls; all delivery is server work. */
public final class SealLogisticsScreen extends AbstractContainerScreen<SealLogisticsMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_logistics.png"),BASE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_base.png");
    private EditBox search;
    private int selected=-1,amount=1,ticks;
    private ItemStack selection=ItemStack.EMPTY;
    public SealLogisticsScreen(SealLogisticsMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=215;imageHeight=215;}
    @Override protected void init(){super.init();search=new EditBox(font,leftPos+143,topPos+196,55,font.lineHeight,Component.translatable("tc.logistics.search"));search.setMaxLength(10);search.setValue(menu.search());search.setResponder(value->SealNetwork.logisticsButton(menu,22,value));addRenderableWidget(search);}
    @Override protected void renderLabels(GuiGraphics gui,int x,int y){}
    @Override protected void containerTick(){super.containerTick();search.tick();if(++ticks%20==0)SealNetwork.logisticsButton(menu,22,search.getValue());
        if(selected>=0){if(!ItemStack.isSameItemSameTags(menu.getSlot(selected).getItem(),selection)){selected=-1;for(int i=0;i<81;i++)if(ItemStack.isSameItemSameTags(menu.getSlot(i).getItem(),selection)){selected=i;break;}}
            if(selected>=0)amount=Math.max(1,Math.min(amount,menu.getSlot(selected).getItem().getCount()));}}
    public void select(int slot){if(slot>=0&&slot<81&&!menu.getSlot(slot).getItem().isEmpty()){selected=slot;selection=menu.getSlot(slot).getItem().copy();amount=Math.max(1,Math.min(amount,selection.getCount()));}}
    public void amount(int value){if(selected>=0)amount=Math.max(1,Math.min(65536,Math.min(value,menu.getSlot(selected).getItem().getCount())));}
    public void request(){if(selected>=0)SealNetwork.logisticsRequest(menu,selected,amount);}
    private void scroll(int action){SealNetwork.logisticsButton(menu,action,search.getValue());}
    @Override protected void renderBg(GuiGraphics gui,float partial,int mouseX,int mouseY){
        gui.blit(TEXTURE,leftPos,topPos,0,0,215,215);
        if(selected>=0)gui.blit(TEXTURE,leftPos+17+selected%9*19,topPos+17+selected/9*19,222,46,20,20);
        gui.blit(BASE,leftPos+195,topPos+16,60,0,10,10);gui.blit(BASE,leftPos+195,topPos+180,70,0,10,10);
        gui.fill(leftPos+196,topPos+28,leftPos+204,topPos+177,0xff30373c);int scrollY=menu.end()==0?0:Math.round(menu.start()/(float)menu.end()*139);gui.fill(leftPos+196,topPos+28+scrollY,leftPos+204,topPos+38+scrollY,0xffbfc8cf);
        if(selected>=0){gui.blit(BASE,leftPos+13,topPos+195,0,0,10,10);gui.blit(BASE,leftPos+57,topPos+195,10,0,10,10);
            gui.fill(leftPos+24,topPos+196,leftPos+56,topPos+204,0xff30373c);int max=Math.max(1,menu.getSlot(selected).getItem().getCount());int handle=max==1?0:Math.round((amount-1)/(float)(max-1)*27);gui.fill(leftPos+24+handle,topPos+196,leftPos+29+handle,topPos+204,0xffbfc8cf);
            gui.drawCenteredString(font,Integer.toString(amount),leftPos+83,topPos+196,0x333333);
            gui.blit(BASE,leftPos+96,topPos+194,37,82,40,13);}
        if(search!=null&&!search.isFocused()&&search.getValue().isEmpty())gui.drawString(font,Component.translatable("tc.logistics.search"),leftPos+146,topPos+197,0x222222,false);
    }
    @Override public void render(GuiGraphics gui,int x,int y,float partial){renderBackground(gui);super.render(gui,x,y,partial);
        for(int i=0;i<81;i++){var slot=menu.getSlot(i);if(slot.getItem().getCount()>99){String text=Integer.toString(slot.getItem().getCount());gui.pose().pushPose();gui.pose().translate(leftPos+slot.x+16,topPos+slot.y+11,301);float scale=Math.min(1,20F/font.width(text));gui.pose().scale(scale,scale,1);gui.drawString(font,text,-font.width(text),0,0xffffff,true);gui.pose().popPose();}}
        renderTooltip(gui,x,y);if(selected>=0&&isHovering(96,194,40,13,x,y))gui.renderTooltip(font,Component.translatable("tc.logistics.request"),x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0){if(isHovering(195,16,10,10,x,y)){scroll(1);return true;}if(isHovering(195,180,10,10,x,y)){scroll(0);return true;}
            if(isHovering(196,28,8,149,x,y)){scroll(100+Math.max(0,Math.min(menu.end(),(int)Math.round((y-topPos-28)/149*menu.end()))));return true;}
            if(selected>=0){if(isHovering(13,195,10,10,x,y)){amount(amount-1);return true;}if(isHovering(57,195,10,10,x,y)){amount(amount+1);return true;}if(isHovering(24,196,32,8,x,y)){amount(1+(int)((x-leftPos-24)/32*Math.max(0,menu.getSlot(selected).getItem().getCount()-1)));return true;}if(isHovering(96,194,40,13,x,y)){request();return true;}}
            for(int i=0;i<81;i++){var slot=menu.getSlot(i);if(isHovering(slot.x,slot.y,16,16,x,y)){select(i);return true;}}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){if(delta!=0){scroll(delta>0?1:0);return true;}return super.mouseScrolled(x,y,delta);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(search.keyPressed(key,scan,mods)||search.isFocused()&&key!=256)return true;return super.keyPressed(key,scan,mods);}
    @Override public boolean charTyped(char chr,int mods){return search.charTyped(chr,mods)||super.charTyped(chr,mods);}
    @Override protected boolean checkHotbarKeyPressed(int key,int scan){return false;}
}
