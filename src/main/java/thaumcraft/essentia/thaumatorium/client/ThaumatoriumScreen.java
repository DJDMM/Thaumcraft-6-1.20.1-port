package thaumcraft.essentia.thaumatorium.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.essentia.thaumatorium.*;
import java.util.List;

/** Release 175x216 texture, catalyst, six output grid, paging and eight aspect progress bars. */
public final class ThaumatoriumScreen extends AbstractContainerScreen<ThaumatoriumMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("thaumcraft","textures/gui/gui_thaumatorium.png");
    private int page;
    public ThaumatoriumScreen(ThaumatoriumMenu menu,Inventory inventory,Component title) {super(menu,inventory,title);imageWidth=175;imageHeight=216;}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y) {}
    private List<ThaumatoriumMenu.RecipeView> visible() {page=Math.max(0,Math.min(page,Math.max(0,(menu.recipes().size()-5)/2)));return menu.recipes().stream().skip(page*2L).limit(6).toList();}
    @Override public void render(GuiGraphics graphics,int x,int y,float partialTicks) {
        renderBackground(graphics);super.render(graphics,x,y,partialTicks);renderTooltip(graphics,x,y);
        var entries=visible();for(int index=0;index<entries.size();index++)if(isHovering(48+index%2*16,56+index/2*16,16,16,x,y))graphics.renderTooltip(font,entries.get(index).output(),x,y);
    }
    @Override protected void renderBg(GuiGraphics graphics,float partialTicks,int x,int y) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);var entries=visible();
        if(menu.recipes().size()>6) {if(page>0)graphics.blit(TEXTURE,leftPos+82,topPos+56,176,56,8,11);if(page<menu.recipes().size()/2f-3)graphics.blit(TEXTURE,leftPos+82,topPos+93,176,93,8,11);}
        long time=System.currentTimeMillis();var selected=menu.recipes().stream().filter(ThaumatoriumMenu.RecipeView::selected).toList();
        for(int index=0;index<entries.size();index++) {
            var entry=entries.get(index);int px=leftPos+48+index%2*16,py=topPos+56+index/2*16;
            if(entry.selected()) {graphics.setColor(1,1,1,.7f);graphics.blit(TEXTURE,px,py,176,8,16,16);graphics.setColor(1,1,1,1);}
            graphics.renderItem(entry.output(),px,py);graphics.renderItemDecorations(font,entry.output(),px,py);
        }
        if(menu.capacity()>1) {graphics.pose().pushPose();graphics.pose().translate(leftPos+64,topPos+48,0);graphics.pose().scale(.5f,.5f,1);graphics.drawCenteredString(font,selected.size()+"/"+menu.capacity(),0,0,0xffffff);graphics.pose().popPose();}
        if(selected.isEmpty())return;
        var recipe=selected.get((int)(time/1000%selected.size()));var stored=menu.stored();int index=0;
        for(var aspect:recipe.cost().getAspectsSortedByName()) {
            if(index>=8)break;int column=index%2,row=index/2,px=leftPos+96+column*16,py=topPos+24+row*20;
            graphics.blit(aspect.getImage(),px,py,0,0,16,16,16,16);
            graphics.pose().pushPose();graphics.pose().translate(px+14,py+10,0);graphics.pose().scale(.5f,.5f,1);graphics.drawString(font,Integer.toString(recipe.cost().getAmount(aspect)),0,0,0xffffff,true);graphics.pose().popPose();
            graphics.blit(TEXTURE,px+2,py+16,176,4,12,3);int filled=Math.min(12,Math.max(0,stored.getAmount(aspect)*12/Math.max(1,recipe.cost().getAmount(aspect))));
            int color=aspect.getColor();graphics.setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);graphics.blit(TEXTURE,px+2,py+16,176,0,filled,3);graphics.setColor(1,1,1,1);index++;
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0) {
            var entries=visible();for(int index=0;index<entries.size();index++)if(isHovering(48+index%2*16,56+index/2*16,16,16,x,y)) {ThaumatoriumNetwork.select(menu,entries.get(index).id());return true;}
            if(menu.recipes().size()>6) {
                if(page>0&&isHovering(82,56,8,11,x,y)) {page--;return true;}
                if(page<menu.recipes().size()/2f-3&&isHovering(82,93,8,11,x,y)) {page++;return true;}
            }
        }
        return super.mouseClicked(x,y,button);
    }
}
