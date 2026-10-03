"""Read-only BETA26 book catalogue. No generated row is a registered gameplay recipe.

Inputs are the pinned decompilation, official JAR and the port's explicit item roster.
The deliberately small expression parser rejects unknown Java instead of guessing costs.
"""
from pathlib import Path
import copy, hashlib, json, re, zipfile

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parents[1]
SRC = ROOT / 'work/thaumcraft6-reference/src/main/java'
RES = PROJECT / 'src/main/resources'
JAR = ROOT / 'work/Thaumcraft-1.12.2-6.1.BETA26.jar'
PIN = '9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f'
assert hashlib.sha256(JAR.read_bytes()).hexdigest() == PIN
CLASSES = {p.stem: p for p in SRC.rglob('*.java')}
CODE = CLASSES['ConfigRecipes'].read_text(encoding='utf8')
ITEMS = json.loads((RES/'assets/thaumcraft/catalog/items.json').read_text(encoding='utf8'))['items']
BLOCKS = json.loads((RES/'assets/thaumcraft/catalog/blocks.json').read_text(encoding='utf8'))['blocks']
ITEM_LOOKUP = {(r['legacy_item'], r['legacy_metadata']): r['id'] for r in ITEMS}
FIELDS = {}
for field, cls, args in re.findall(r'iForgeRegistry\.register\(\(ItemsTC\.(\w+) = new (\w+)\((.*?)\)\)\);', CLASSES['ConfigItems'].read_text()):
    ss = re.findall(r'"([a-z0-9_]+)"', args)
    if ss: base = ss[0]
    else:
        code = CLASSES[cls].read_text()
        m = re.search(r'super\("([a-z0-9_]+)"', code) or re.search(r'setRegistryName\("([a-z0-9_]+)"', code)
        if not m: raise ValueError((field, cls))
        base = m[1]
    FIELDS['ItemsTC.'+field] = base
for line in CLASSES['ConfigBlocks'].read_text().splitlines():
    m = re.search(r'BlocksTC\.(\w+) = registerBlock\(new ([\w.]+)\((.*)', line)
    if not m: continue
    field, cls, args = m.groups()
    ss = re.findall(r'"([a-z0-9_]+)"', args)
    if ss: base = ss[0]
    elif cls == 'BlockRailPowered': base = 'activator_rail'
    else:
        rows = [r for r in BLOCKS if r['source_class'] == cls.split('.')[0]]
        if cls == 'BlockThaumatorium': base = 'thaumatorium_top' if args.startswith('true') else 'thaumatorium'
        elif cls == 'BlockCondenserLattice': base = 'condenser_lattice_dirty' if args.startswith('true') else 'condenser_lattice'
        elif len(rows) == 1: base = rows[0]['id']
        else: continue
    FIELDS['BlocksTC.'+field] = base
FIELDS.update({'BlocksTC.activatorRail':'activator_rail','BlocksTC.slabArcaneStone':'slab_arcane_stone','BlocksTC.slabArcaneBrick':'slab_arcane_brick','BlocksTC.slabAncient':'slab_ancient','BlocksTC.slabEldritch':'slab_eldritch','BlocksTC.slabGreatwood':'slab_greatwood','BlocksTC.slabSilverwood':'slab_silverwood'})
COLORS = ['white','orange','magenta','lightblue','yellow','lime','pink','gray','silver','cyan','purple','blue','brown','green','red','black']
MODERN_COLORS = ['light_blue' if x == 'lightblue' else 'light_gray' if x == 'silver' else x for x in COLORS]
DYES = ['ink_sac','red_dye','green_dye','cocoa_beans','lapis_lazuli','purple_dye','cyan_dye','light_gray_dye','gray_dye','pink_dye','lime_dye','yellow_dye','light_blue_dye','magenta_dye','orange_dye','bone_meal']
ASPECT_CODE = re.sub(r'//[^\n]*|/\*.*?\*/', '', CLASSES['Aspect'].read_text(), flags=re.S)
ASPECTS = dict(re.findall(r'(\w+)\s*=\s*new Aspect\("([^"]+)"', ASPECT_CODE))
assert len(ASPECTS) == 37, 'BETA26 contains exactly37registeredaspects'
ENV = {'ConfigRecipes.defaultGroup': ''}
for name, args in re.findall(r'ResourceLocation (\w+) = new ResourceLocation\(([^;]+)\);', CODE):
    strings = re.findall(r'"([^"]*)"', args)
    ENV[name] = ':'.join(strings).lower()

def split(s):
    out, pos, stack, quote, escape = [], 0, [], None, False
    for i, c in enumerate(s):
        if quote:
            if escape: escape = False
            elif c == '\\': escape = True
            elif c == quote: quote = None
        elif c in "\"'": quote = c
        elif c in '({[': stack.append(c)
        elif c in ')}]': stack.pop()
        elif c == ',' and not stack: out.append(s[pos:i].strip()); pos = i+1
    out.append(s[pos:].strip())
    return out

def call(s, prefix):
    assert s.startswith(prefix+'('), s
    depth, q = 0, None
    start = len(prefix)+1
    for i in range(start-1, len(s)):
        c=s[i]
        if q:
            if c == q and s[i-1] != '\\': q=None
        elif c in "\"'": q=c
        elif c=='(': depth+=1
        elif c==')':
            depth-=1
            if depth==0: return split(s[start:i]), s[i+1:]
    raise ValueError(s)

def stack(item, count=1, nbt=''):
    row={'item':item}
    if count!=1: row['count']=count
    if nbt: row['nbt']=nbt
    return row

def field_stack(field, meta=0, count=1):
    if field.startswith(('ItemsTC.','BlocksTC.')):
        base=FIELDS[field]
        if field.startswith('ItemsTC.'):
            if meta==32767:
                variants=[stack('thaumcraft:'+r['id']) for r in ITEMS if r['legacy_item']==base]
                if len(variants)>1:return {'alternatives':variants}
                meta=0
            modern=ITEM_LOOKUP.get((base,meta),base)
        else: modern=base
        # The previous early prototype owns the yellow nitor's modern registry ID.
        if modern=='nitor_yellow': modern='nitor'
        return stack('thaumcraft:'+modern,count)
    name=field.split('.')[-1].lower()
    aliases={'nether_brick':'nether_bricks','reeds':'sugar_cane','web':'cobweb','stone_slab':'smooth_stone_slab','wooden_button':'oak_button','trapdoor':'oak_trapdoor','potionitem':'potion','map':'filled_map','skull':'skeleton_skull','lit_pumpkin':'jack_o_lantern','golden_rail':'powered_rail'}
    if name=='dye': name=DYES[0 if meta==32767 else meta]
    if name=='wool':
        if meta==32767:return {'alternatives':[stack('minecraft:'+c+'_wool') for c in MODERN_COLORS]}
        name=MODERN_COLORS[meta]+'_wool'
    if name=='fish':
        names=['cod','salmon','tropical_fish','pufferfish']
        if meta==32767:return {'alternatives':[stack('minecraft:'+n) for n in names]}
        name=names[meta]
    if name=='coal' and meta==32767:return {'alternatives':[stack('minecraft:coal'),stack('minecraft:charcoal')]}
    if name=='coal' and meta==1:name='charcoal'
    if name=='skull':name=['skeleton_skull','wither_skeleton_skull','zombie_head','player_head','creeper_head','dragon_head'][meta]
    return stack('minecraft:'+aliases.get(name,name),count)

def aspect_list(expr):
    result={}
    for method, name, value in re.findall(r'\.(add|merge)\(Aspect\.(\w+),\s*(\d+)\)',expr):
        tag=ASPECTS[name]; n=int(value)
        result[tag]=result.get(tag,0)+n if method=='add' else max(result.get(tag,0),n)
    if not result and 'new ItemStack' in expr:
        args,_=call(expr,'new AspectList')
        source=ev(args[0]); key=source['item']
        lookup={'minecraft:gunpowder':'tag/gunpowder','minecraft:slime_ball':'tag/slimeball','minecraft:glowstone_dust':'tag/dustglowstone','minecraft:ink_sac':'item/minecraft_ink_sac','minecraft:clay_ball':'item/minecraft_clay_ball','minecraft:string':'tag/string','minecraft:cobweb':'item/minecraft_cobweb'}
        result=json.loads((RES/'data/thaumcraft/aspects'/(lookup[key]+'.json')).read_text())['aspects']
        if '.remove(' in expr:
            other=re.search(r'\.remove\(new AspectList\((.*)\)\)\s*$',expr)[1]
            other=ev(other)['item']; lookup2={'minecraft:dirt':'tag/dirt','minecraft:wheat':'tag/cropwheat','minecraft:string':'tag/string'}
            remove=json.loads((RES/'data/thaumcraft/aspects'/(lookup2[other]+'.json')).read_text())['aspects']
            result={k:max(0,v-remove.get(k,0)) for k,v in result.items()};result={k:v for k,v in result.items() if v}
    return result

def ev(s):
    s=s.strip()
    if s in ENV:return copy.deepcopy(ENV[s])
    if s=='null':return None
    if re.fullmatch(r'-?\d+',s):return int(s)
    if s.startswith('"') and s.endswith('"'):
        return json.loads(s)
    if s.startswith("'") and s.endswith("'"):return ('char',s[1:-1])
    if s.startswith('new ResourceLocation('):
        a,_=call(s,'new ResourceLocation');return ':'.join(ev(x) for x in a).lower()
    if s.startswith('new Object[]'):
        return [ev(x) for x in split(s[s.index('{')+1:s.rindex('}')])]
    if s.startswith('new AspectList('):return aspect_list(s)
    if s.startswith('new ItemStack('):
        a,_=call(s,'new ItemStack');field=a[0]
        if field in ENV:
            out=ev(field); out['count']=int(a[1]) if len(a)>1 else 1;return out
        color=re.fullmatch(r'BlocksTC\.(nitor|candles|banners)\.get\(EnumDyeColor\.(\w+)\)',field)
        if color:
            pre={'nitor':'nitor','candles':'candle','banners':'banner'}[color[1]]
            ident=pre+'_'+color[2].lower();ident='nitor' if ident=='nitor_yellow' else ident
            return stack('thaumcraft:'+ident,int(a[1]) if len(a)>1 else 1)
        return field_stack(field,int(a[2]) if len(a)>2 else 0,int(a[1]) if len(a)>1 else 1)
    if s.startswith('ThaumcraftApiHelper.makeCrystal('):
        a,_=call(s,'ThaumcraftApiHelper.makeCrystal');tag=ASPECTS[a[0].split('.')[-1]]
        return stack('thaumcraft:crystal_essence',nbt="{Aspects:[{key:'"+tag+"',amount:1}]}")
    if s.startswith('ItemPhial.makeFilledPhial('):
        a,_=call(s,'ItemPhial.makeFilledPhial');tag=ASPECTS[a[0].split('.')[-1]]
        return stack('thaumcraft:phial_filled',nbt="{Aspects:[{key:'"+tag+"',amount:10}]}")
    if s.startswith('GolemHelper.getSealStack('):
        a,_=call(s,'GolemHelper.getSealStack');return stack('thaumcraft:seal_'+ev(a[0]).split(':')[1])
    if s.startswith('new IngredientNBTTC('):a,_=call(s,'new IngredientNBTTC');return ev(a[0])
    if s.startswith('Ingredient.fromItem('):a,_=call(s,'Ingredient.fromItem');return ev(a[0])
    if s=='Ingredient.fromStacks(nitorStacks)':return {'alternatives':[stack('thaumcraft:nitor' if c=='yellow' else 'thaumcraft:nitor_'+c) for c in COLORS]}
    if s.startswith('FluidUtil.getFilledBucket('):return stack('minecraft:bucket',nbt='{Fluid:{FluidName:"liquid_death",Amount:1000}}')
    if s.startswith('ConfigItems.') and s.endswith('_CRYSTAL'):return ev('ThaumcraftApiHelper.makeCrystal(Aspect.'+s[12:-8]+')')
    if s.startswith(('Items.','Blocks.','ItemsTC.','BlocksTC.')):return field_stack(s)
    if s.startswith('new NBTTag'):
        a,_=call(s,s[:s.index('(')]);value=a[0].replace('(byte)','').replace('(short)','');return (s[len('new NBTTag'):s.index('(')].lower(),ev(value))
    raise ValueError('Unknown expression: '+s)

def ingredient(x):
    if isinstance(x,str):return {'ore':x}
    if x is None:return {}
    return x

def pattern(args):
    if len(args)==1 and isinstance(args[0],list):args=args[0]
    rows=[]
    while args and isinstance(args[0],str):rows.append(args.pop(0))
    assert rows and len({len(x) for x in rows})==1,rows
    keys={}
    while args:
        key=args.pop(0);value=args.pop(0); assert isinstance(key,tuple) and key[0]=='char'
        keys[key[1]]=ingredient(value)
    return [keys.get(c,{}) for row in rows for c in row],len(rows[0]),len(rows)

ROWS=[]; GROUPS={}; FAIL=[]
def put(row):
    if 'group' in row and row['group']:GROUPS.setdefault(row['group'],[]).append(row['id'])
    ROWS.append(row)

def parse_recipe(expr,key,line,group=''):
    if expr in ENV:expr=ENV[expr]
    if not isinstance(expr,str):raise ValueError(expr)
    typ=expr[len('new '):expr.index('(')]
    a,tail=call(expr,'new '+typ)
    data={'id':key,'source_line':line,'kind':'','research':'','vis':0,'aspects':{},'crystals':{},'width':0,'height':0,'shapeless':False,'instability':0,'xp':0}
    if typ in ('ShapedArcaneRecipe','ShapelessArcaneRecipe','ShapedArcaneVoidJar'):
        a=[ev(x) for x in a];group,research,vis,crystals,out=a[:5];a=a[5:]
        data.update(kind='arcane',research=research,vis=vis,crystals=crystals or {},output=out)
        if typ=='ShapelessArcaneRecipe':
            a=a[0] if len(a)==1 and isinstance(a[0],list) else a
            data.update(ingredients=[ingredient(x) for x in a],width=min(3,len(a)),height=(len(a)+2)//3,shapeless=True)
        else:
            inputs,w,h=pattern(a);data.update(ingredients=inputs,width=w,height=h)
        if typ=='ShapedArcaneVoidJar':data['note']='preserves_jar'
    elif typ=='CrucibleRecipe':
        a=[ev(x) for x in a];research,out,catalyst,aspects=a
        data.update(kind='crucible',research=research,output=out,ingredients=[ingredient(catalyst)],aspects=aspects,width=1,height=1)
        m=re.search(r'\.setGroup\(([^)]+)\)',tail)
        if m:group=ev(m[1])
    elif typ=='InfusionRecipe':
        a=[ev(x) for x in a];research,out,instability,aspects,central=a[:5];a=a[5:]
        if isinstance(out,list):
            out=copy.deepcopy(central);name,nbt=out['item'],out.get('nbt','{}')

            # InfusionRecipe object[] mutates the central stack with one typed tag.
            pair=ev(call(expr,'new '+typ)[0][1]);tagname,(tagtype,value)=pair
            suffix={'byte':'b','int':'','short':'s','string':''}[tagtype]
            rendered=json.dumps(value) if isinstance(value,str) else str(value)+suffix
            out['nbt']='{'+json.dumps(tagname)+':'+rendered+'}'
        data.update(kind='infusion',research=research,output=out,ingredients=[ingredient(central)]+[ingredient(x) for x in a],aspects=aspects,instability=instability)
    elif typ=='ShapelessOreRecipe':
        a=[ev(x) for x in a];group,out=a[:2];a=a[2:];data.update(kind='crafting',output=out,ingredients=[ingredient(x) for x in a],width=min(3,len(a)),height=(len(a)+2)//3,shapeless=True)
        if key=='thaumcraft:salismundusfake':data['kind']='salis';data['note']='different_crystals'
        if key=='thaumcraft:triplemeattreatfake':data['note']='different_meat'
    elif typ=='InfusionEnchantmentRecipe':
        base=ENV[a[0]];central=ev(a[1]);out=copy.deepcopy(central)
        eid=base['enchantment'];out['nbt']="{infench:[{id:"+str(eid)+"s,lvl:1s}]}"
        data.update(kind='infusion_enchantment',research='INFUSIONENCHANTMENT',output=out,ingredients=[central]+copy.deepcopy(base['components']),aspects=base['aspects'],instability=4,note='enchantment_dynamic')
    else:raise ValueError('Unknown recipe class '+typ)
    if key=='thaumcraft:liquiddeath':data['note']='universal_bucket'
    if key in ('thaumcraft:metal_purification_copper','thaumcraft:metal_purification_tin','thaumcraft:metal_purification_silver','thaumcraft:metal_purification_lead'):data['note']='conditional_ore'
    data['group']=group or ''; return data

for n,line in enumerate(CODE.splitlines(),1):
    s=line.strip()
    m=re.fullmatch(r'ItemStack (\w+) = (new ItemStack\(.*\));',s)
    if m:
        try:ENV[m[1]]=ev(m[2])
        except (ValueError,KeyError):pass
    m=re.fullmatch(r'(\w+)\.setTagInfo\("([^"]+)", (.*)\);',s)
    if m and m[1] in ENV and '(byte)a' not in m[3]:
        typ,value=ev(m[3]);suffix={'byte':'b','int':'','string':''}[typ];v=json.dumps(value) if isinstance(value,str) else str(value)+suffix
        ENV[m[1]]['nbt']='{'+json.dumps(m[2])+':'+v+'}'
    m=re.fullmatch(r'EnumInfusionEnchantment\.addInfusionEnchantment\((\w+), EnumInfusionEnchantment\.(\w+), (\d+)\);',s)
    if m:
        names=['COLLECTOR','DESTRUCTIVE','BURROWING','SOUNDING','REFINING','ARCING','ESSENCE','VISBATTERY','VISCHARGE','SWIFT','AGILE','INFESTED','LAMPLIGHT']
        entry='{id:'+str(names.index(m[2]))+'s,lvl:'+m[3]+'s}'
        old=ENV[m[1]].get('nbt','{infench:[]}');ENV[m[1]]['nbt']=old[:-2]+(',' if old[-3]!= '[' else '')+entry+']}'
    m=re.fullmatch(r'InfusionEnchantmentRecipe (\w+) = new InfusionEnchantmentRecipe\((.*)\);',s)
    if m:
        a=split(m[2]);names=['COLLECTOR','DESTRUCTIVE','BURROWING','SOUNDING','REFINING','ARCING','ESSENCE','VISBATTERY','VISCHARGE','SWIFT','AGILE','INFESTED','LAMPLIGHT']
        ENV[m[1]]={'enchantment':names.index(a[0].split('.')[-1]),'aspects':ev(a[1]),'components':[ingredient(ev(x)) for x in a[2:]]}
    if re.match(r'ThaumcraftApi\.add(?:Crucible|InfusionCrafting|ArcaneCrafting|FakeCrafting)Recipe\(',s):
        method=s[:s.index('(')];a,_=call(s,method)
        if '+ a)' in a[0] or '+ d.' in a[0] or 'aspect.getTag()' in a[0] or a[1]=='ra' or a[1].startswith('IE') and not a[1].startswith('new '):continue
        try:put(parse_recipe(a[1],ev(a[0]),n))
        except (ValueError,KeyError,AssertionError) as e:FAIL.append((n,str(e)))
    if re.match(r'(?:oreDictRecipe|shapelessOreDictRecipe|GameRegistry.addShapedRecipe)\(',s):
        method=s[:s.index('(')];a,_=call(s,method)
        if '+' in a[0]:continue
        try:
            key=ev(a[0]);key=key if ':' in key else 'thaumcraft:'+key.lower();group=ev(a[1]);out=ev(a[2]);params=[ev(x) for x in a[3:]]
            row={'id':key,'source_line':n,'kind':'crafting','output':out,'group':group,'research':'','vis':0,'crystals':{},'aspects':{},'instability':0,'xp':0,'shapeless':method=='shapelessOreDictRecipe'}
            if row['shapeless']:
                params=params[0] if len(params)==1 and isinstance(params[0],list) else params;row.update(ingredients=[ingredient(x) for x in params],width=min(3,len(params)),height=(len(params)+2)//3)
            else:inputs,w,h=pattern(params);row.update(ingredients=inputs,width=w,height=h)
            put(row)
        except (ValueError,KeyError,AssertionError) as e:FAIL.append((n,str(e)))

# Explicit original loops. They are finite display variants, never runtime recipes.
for aspect in ASPECTS:
    tag=ASPECTS[aspect]
    row=parse_recipe('new CrucibleRecipe("BASEALCHEMY", ThaumcraftApiHelper.makeCrystal(Aspect.'+aspect+'), "nuggetQuartz", new AspectList().add(Aspect.'+aspect+', 2))','thaumcraft:vis_crystal_'+tag,119,'thaumcraft:viscrystalgroup');put(row)
    put({'id':'thaumcraft:label_'+tag,'source_line':442,'kind':'crafting','output':stack('thaumcraft:label_filled',nbt="{Aspects:[{key:'"+tag+"',amount:1}]}"),'ingredients':[stack('thaumcraft:label_blank'),stack('thaumcraft:phial_filled',nbt="{Aspects:[{key:'"+tag+"',amount:10}]}")],'width':2,'height':1,'research':'','vis':0,'aspects':{},'crystals':{},'shapeless':True,'instability':0,'xp':0,'group':'thaumcraft:jarlabels','note':'returns_phial'})
for i,color in enumerate(COLORS):
    dye={'ore':'dye'+''.join(x.title() for x in MODERN_COLORS[i].split('_'))}
    put({'id':'thaumcraft:nitordye'+color,'source_line':126,'kind':'crafting','output':stack('thaumcraft:nitor' if color=='yellow' else 'thaumcraft:nitor_'+color),'ingredients':[dye,{'ore':'nitor'}],'width':2,'height':1,'research':'','vis':0,'aspects':{},'crystals':{},'shapeless':True,'instability':0,'xp':0,'group':'thaumcraft:nitorgroup'})
    put({'id':'thaumcraft:banner'+color,'source_line':217,'kind':'arcane','output':stack('thaumcraft:banner_'+color),'ingredients':[stack('minecraft:'+MODERN_COLORS[i]+'_wool'),{'ore':'stickWood'},stack('minecraft:'+MODERN_COLORS[i]+'_wool'),{'ore':'stickWood'},stack('minecraft:'+MODERN_COLORS[i]+'_wool'),{'ore':'slabWood'}],'width':2,'height':3,'research':'BASEINFUSION','vis':10,'aspects':{},'crystals':{},'shapeless':False,'instability':0,'xp':0,'group':'thaumcraft:banners'})
    put({'id':'thaumcraft:tallowcandle'+color,'source_line':473,'kind':'crafting','output':stack('thaumcraft:candle_'+color),'ingredients':[dye,{'alternatives':[stack('thaumcraft:candle_'+c) for c in COLORS]}],'width':2,'height':1,'research':'','vis':0,'aspects':{},'crystals':{},'shapeless':True,'instability':0,'xp':0,'group':'thaumcraft:tallowcandles'})
for level in range(3):
    central=stack('thaumcraft:baubles_ring_mundane',nbt='{ "TC.RUNIC":'+str(level)+'b}' if level else '')
    out=stack('thaumcraft:baubles_ring_mundane',nbt='{ "TC.RUNIC":'+str(level+1)+'b}')
    vis=20+20*2**level
    put({'id':'thaumcraft:runicarmorfake'+str(level),'source_line':289,'kind':'runic','output':out,'ingredients':[central,stack('thaumcraft:salis_mundus')]+[{'ore':'gemAmber'}]*(level+1),'width':0,'height':0,'research':'RUNICSHIELDING','vis':0,'aspects':{'praemunio':vis,'vitreus':vis//2,'potentia':vis//2},'crystals':{},'shapeless':False,'instability':5+level//2,'xp':0,'group':'','note':'runic_dynamic'})

if FAIL:
    print('\n'.join(f'{n}: {e}' for n,e in FAIL));raise SystemExit('Unparsed original recipe expression')

KEYS=set()
def visit(x):
    if isinstance(x,dict):
        for k,v in x.items():
            if k=='recipes':KEYS.update(v)
            else:visit(v)
    elif isinstance(x,list):
        for y in x:visit(y)
with zipfile.ZipFile(JAR) as z:
    for name in z.namelist():
        if name.startswith('assets/thaumcraft/research/') and name.endswith('.json'):visit(json.loads(z.read(name)))
index={r['id']:r for r in ROWS}
assert len(index)==len(ROWS), 'Duplicate original recipe'
BLUEPRINTS=['infernalfurnace','infusionaltar','infusionaltarancient','infusionaltareldritch','thaumatorium','golempress']
STATUS={}
for original in sorted(KEYS):
    key=original.lower()
    if key in index:status='recipe'
    elif key in GROUPS:status='group'
    elif key.split(':')[1] in BLUEPRINTS:status='blueprint'
    else:status='original_unregistered'
    STATUS[key]={'original':original,'status':status}
data={'schema':1,'baseline':'Thaumcraft 6.1.BETA26','jar_sha256':PIN,'source_commit':'954022bb777b7546281fb36df8522f0ba6b43f81','source_file':'thaumcraft/common/config/ConfigRecipes.java','source_sha256':hashlib.sha256(CODE.encode()).hexdigest(),'reference_only':True,'referenced_keys':STATUS,'groups':GROUPS,'recipes':ROWS}
target=RES/'assets/thaumcraft/research/book_recipes.json'
target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(f'{len(KEYS)} original references; {len(ROWS)} display recipes; {len(GROUPS)} groups')
print('Original unregistered: '+', '.join(k for k,v in STATUS.items() if v['status']=='original_unregistered'))
