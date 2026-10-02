package thaumcraft.api.aspects;
import java.io.Serializable;
import java.util.LinkedHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;



public class AspectList implements Serializable {
	
	public LinkedHashMap<Aspect,Integer> aspects = new LinkedHashMap<Aspect,Integer>();//aspects associated with this object

	
	public AspectList() {
	}
	
	public AspectList copy() {
		AspectList out = new AspectList();
		for (Aspect a: getAspects())
			out.add(a, getAmount(a));
		return out;
	}
	
	/**
	 * @return the amount of different aspects in this collection
	 */
	public int size() {
		return aspects.size();
	}
	
	
	/**
	 * @return the amount of total vis in this collection
	 */
	public int visSize() {
		int q = 0;
		
		for (Aspect as:aspects.keySet()) {
			q+= getAmount(as);
		}
		
		return q;
	}
	
	/**
	 * @return an array of all the aspects in this collection
	 */
	public Aspect[] getAspects() {
		return aspects.keySet().toArray(new Aspect[]{});
	}
	
		
	/**
	 * @return an array of all the aspects in this collection sorted by name
	 */
	public Aspect[] getAspectsSortedByName() {
		try {
			Aspect[] out = aspects.keySet().toArray(new Aspect[]{});
			boolean change=false;
			do {
				change=false;
				for(int a=0;a<out.length-1;a++) {
					Aspect e1 = out[a];
					Aspect e2 = out[a+1];
					if (e1!=null && e2!=null && e1.getTag().compareTo(e2.getTag())>0) {
						out[a] = e2;
						out[a+1] = e1;
						change = true;
						break;
					}
				}
			} while (change==true);
			return out;
		} catch (Exception e) {
			return getAspects();
		}
	}
	
	/**
	 * @return an array of all the aspects in this collection sorted by amount
	 */
	public Aspect[] getAspectsSortedByAmount() {
		try {
			Aspect[] out = aspects.keySet().toArray(new Aspect[]{});
			boolean change=false;
			do {
				change=false;
				for(int a=0;a<out.length-1;a++) {
					int e1 = getAmount(out[a]); 
					int e2 = getAmount(out[a+1]);
					if (e1>0 && e2>0 && e2>e1) {
						Aspect ea = out[a];
						Aspect eb = out[a+1];
						out[a] = eb;
						out[a+1] = ea;
						change = true;
						break;
					}
				}
			} while (change==true);
			return out;
		} catch (Exception e) {
			return getAspects();
		}
	}
	
	/**
	 * @param key
	 * @return the amount associated with the given aspect in this collection
	 */
	public int getAmount(Aspect key) {
		return  aspects.get(key)==null?0:aspects.get(key);
	}
	
	/**
	 * Reduces the amount of an aspect in this collection by the given amount. 
	 * @param key
	 * @param amount
	 * @return 
	 */
	public boolean reduce(Aspect key, int amount) {
		if (getAmount(key)>=amount) {
			int am = getAmount(key)-amount;
			aspects.put(key, am);
			return true;
		}
		return false;
	}
	
	/**
	 * Reduces the amount of an aspect in this collection by the given amount. 
	 * If reduced to 0 or less the aspect will be removed completely. 
	 * @param key
	 * @param amount
	 * @return
	 */
	public AspectList remove(Aspect key, int amount) {
		int am = getAmount(key)-amount;
		if (am<=0) aspects.remove(key); else
		aspects.put(key, am);
		return this;
	}
	
	/**
	 * Simply removes the aspect from the list
	 * @param key
	 * @param amount
	 * @return
	 */
	public AspectList remove(Aspect key) {
		aspects.remove(key); 
		return this;
	}
	
	/**
	 * Adds this aspect and amount to the collection. 
	 * If the aspect exists then its value will be increased by the given amount.
	 * @param aspect
	 * @param amount
	 * @return
	 */
	public AspectList add(Aspect aspect, int amount) {
		if (aspects.containsKey(aspect)) {
			int oldamount = aspects.get(aspect);
			amount+=oldamount;
		}
		aspects.put( aspect, amount );
		return this;
	}

	
	/**
	 * Adds this aspect and amount to the collection. 
	 * If the aspect exists then only the highest of the old or new amount will be used.
	 * @param aspect
	 * @param amount
	 * @return
	 */
	public AspectList merge(Aspect aspect, int amount) {
		if (aspects.containsKey(aspect)) {
			int oldamount = aspects.get(aspect);
			if (amount<oldamount) amount=oldamount;
			
		}
		aspects.put( aspect, amount );
		return this;
	}
	
	public AspectList add(AspectList in) {
		for (Aspect a:in.getAspects()) 
			add(a, in.getAmount(a));
		return this;
	}
	
	public AspectList remove(AspectList in) {
		for (Aspect a:in.getAspects()) 
			remove(a, in.getAmount(a));
		return this;
	}
	
	public AspectList merge(AspectList in) {
		for (Aspect a:in.getAspects()) 
			merge(a, in.getAmount(a));
		return this;
	}
		
	/**
	 * Reads the list of aspects from nbt
	 * @param nbttagcompound
	 * @return 
	 */
	public void readFromNBT(CompoundTag nbttagcompound)
    {
        aspects.clear();
        ListTag tlist = nbttagcompound.getList("Aspects",(byte)10);
		for (int j = 0; j < tlist.size(); j++) {
			CompoundTag rs = tlist.getCompound(j);
			if (rs.contains("key") && Aspect.getAspect(rs.getString("key")) != null) {
				add(	Aspect.getAspect(rs.getString("key")),
						rs.getInt("amount"));
			}
		}
    }
	
	public void readFromNBT(CompoundTag nbttagcompound, String label)
    {
        aspects.clear();
        ListTag tlist = nbttagcompound.getList(label,(byte)10);
		for (int j = 0; j < tlist.size(); j++) {
			CompoundTag rs = tlist.getCompound(j);
			if (rs.contains("key") && Aspect.getAspect(rs.getString("key")) != null) {
				add(	Aspect.getAspect(rs.getString("key")),
						rs.getInt("amount"));
			}
		}
    }
	
	/**
	 * Writes the list of aspects to nbt
	 * @param nbttagcompound
	 * @return 
	 */
	public void writeToNBT(CompoundTag nbttagcompound)
    {
        ListTag tlist = new ListTag();
		nbttagcompound.put("Aspects", tlist);
		for (Aspect aspect : getAspects())
			if (aspect != null) {
				CompoundTag f = new CompoundTag();
				f.putString("key", aspect.getTag());
				f.putInt("amount", getAmount(aspect));
				tlist.add(f);
			}
    }
	
	public void writeToNBT(CompoundTag nbttagcompound, String label)
    {
        ListTag tlist = new ListTag();
		nbttagcompound.put(label, tlist);
		for (Aspect aspect : getAspects())
			if (aspect != null) {
				CompoundTag f = new CompoundTag();
				f.putString("key", aspect.getTag());
				f.putInt("amount", getAmount(aspect));
				tlist.add(f);
			}
    }
	
}
