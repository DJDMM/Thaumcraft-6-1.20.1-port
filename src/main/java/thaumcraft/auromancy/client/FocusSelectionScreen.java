package thaumcraft.auromancy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import thaumcraft.auromancy.FocusSelection;
import thaumcraft.auromancy.FocusSelectionNetwork;
import thaumcraft.auromancy.focus.FocusStacks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** TC6 F/Shift-F inventory selection, adapted to a cursor radial screen and bounded pages. */
public final class FocusSelectionScreen extends Screen {
    private record Choice(int slot,ItemStack stack) {}
    private final InteractionHand hand;
    private ItemStack caster=ItemStack.EMPTY;
    private final List<Choice> choices=new ArrayList<>();
    private int page, hovered=-2;
    public FocusSelectionScreen(InteractionHand hand) { super(Component.translatable("gui.thaumcraft.focus.select"));this.hand=hand; }
    @Override protected void init() {
        choices.clear();caster=minecraft.player.getItemInHand(hand).copy();
        for(int i=0;i<36;i++){
            ItemStack focus=minecraft.player.getInventory().getItem(i);
            if(focus.getCount()==1 && FocusStacks.readPlan(focus).isPresent())choices.add(new Choice(i,focus.copy()));
        }
        choices.sort(Comparator.comparing(c->c.stack.getHoverName().getString()));
        page=Math.min(page,Math.max(0,(choices.size()-1)/8));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){ if(minecraft.player==null || !ItemStack.matches(caster,minecraft.player.getItemInHand(hand)))onClose(); }
    private int count(){return Math.min(8,choices.size()-page*8);}
    private int x(int index){return width/2+(int)Math.round(Math.cos(-Math.PI/2+Math.PI*2*index/Math.max(1,count()))*64)-12;}
    private int y(int index){return height/2+(int)Math.round(Math.sin(-Math.PI/2+Math.PI*2*index/Math.max(1,count()))*64)-12;}
    @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
        gui.fill(0,0,width,height,0x9910101D);
        gui.drawCenteredString(font,title,width/2,height/2-108,0xDDC899);
        hovered=-2;
        for(int i=0;i<count();i++){
            int cx=x(i),cy=y(i);boolean over=mouseX>=cx && mouseX<cx+24 && mouseY>=cy && mouseY<cy+24;
            if(over)hovered=page*8+i;
            gui.fill(cx-2,cy-2,cx+26,cy+26,over?0xFFC6A56B:0xFF56416A);
            gui.fill(cx,cy,cx+24,cy+24,0xED21182E);
            gui.renderItem(choices.get(page*8+i).stack,cx+4,cy+4);
        }
        int cx=width/2,cy=height/2;
        boolean remove=mouseX>=cx-22 && mouseX<cx+22 && mouseY>=cy-13 && mouseY<cy+13;
        if(remove)hovered=-1;
        gui.fill(cx-22,cy-13,cx+22,cy+13,remove?0xFFE4CAA0:0xFF6F597F);
        gui.drawCenteredString(font,Component.translatable("gui.thaumcraft.focus.remove"),cx,cy-4,remove?0x32213F:0xFFFFFF);
        if(choices.isEmpty())gui.drawCenteredString(font,Component.translatable("gui.thaumcraft.focus.none"),cx,cy+92,0xDDC899);
        else gui.drawCenteredString(font,(page+1)+" / "+((choices.size()+7)/8),cx,cy+92,0xDDC899);
        gui.drawCenteredString(font,Component.translatable("gui.thaumcraft.focus.hint"),cx,cy+106,0xAFA3B6);
        if(hovered>=0)gui.renderTooltip(font,choices.get(hovered).stack,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        hovered=-2;
        for(int i=0;i<count();i++)if(mx>=x(i)&&mx<x(i)+24&&my>=y(i)&&my<y(i)+24)hovered=page*8+i;
        if(mx>=width/2-22&&mx<width/2+22&&my>=height/2-13&&my<height/2+13)hovered=-1;
        if(hovered<-1)return super.mouseClicked(mx,my,button);
        if(hovered==-1)FocusSelectionNetwork.request(hand,-1,caster,ItemStack.EMPTY);
        else {var choice=choices.get(hovered);FocusSelectionNetwork.request(hand,choice.slot,caster,choice.stack);}
        onClose();return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(choices.size()>8)page=Math.floorMod(page+(delta<0?1:-1),(choices.size()+7)/8);return true;
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){
        if(FocusSelectionClient.CHANGE.matches(key,scan)){onClose();return true;}
        return super.keyReleased(key,scan,modifiers);
    }
}
