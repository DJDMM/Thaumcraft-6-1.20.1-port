"""Reconstruct the 56 real BETA26 recipes, preserving exact/wildcard damage and mutation modes.

Uses the pinned source expression parser, never promotes the finite book/FAKE displays to gameplay.
"""
from pathlib import Path
import copy, json, re

parser = Path(__file__).with_name('extract_book_recipes.py')
ns = {'__file__': str(parser)}
# Load its pinned expressions/environment without rewriting the independently validated book catalogue.
exec(compile(parser.read_text(encoding='utf8').split('# Explicit original loops.')[0], str(parser), 'exec'), ns)
ev, call, code = ns['ev'], ns['call'], ns['CODE']
target = ns['RES'] / 'data/thaumcraft/recipes/infusion'
target.mkdir(parents=True, exist_ok=True)

def ore(name):
    special = {'nitor':'thaumcraft:nitor','leather':'forge:leather','stickWood':'forge:rods/wooden'}
    if name in special: return special[name]
    for prefix in ('ingot','nugget','plate','gem','dust'):
        if name.startswith(prefix): return 'forge:'+prefix+'s/'+re.sub(r'([a-z])([A-Z])',r'\1_\2',name[len(prefix):]).lower()
    raise ValueError('Unknown infusion ore '+name)

def convert(row, damage='any'):
    if isinstance(row, str): return {'tag':ore(row)}
    if 'alternatives' in row: return {'any':[convert(x,damage) for x in row['alternatives']]}
    out = {key:copy.deepcopy(row[key]) for key in ('item','nbt') if key in row}
    out['damage'] = damage
    assert 'item' in out, row
    return out

def ingredient(expr):
    raw = ev(expr)
    if isinstance(raw,str): return convert(raw)
    damage = 0
    if expr.startswith('Ingredient.fromItem('): damage = 'any'
    elif expr.startswith('Ingredient.fromStacks('): damage = 'any'
    elif expr.startswith('new ItemStack('):
        args,_ = call(expr,'new ItemStack')
        if len(args)>2 and args[2] == '32767': damage = 'any'
        # Metadata maps into distinct modern IDs; only primordial pearl has dynamic modern Damage.
        elif len(args)>2 and 'primordialPearl' in args[0]: damage = int(args[2])
    return convert(raw,damage)

written = {}
for line in code.splitlines():
    s=line.strip()
    if not s.startswith('ThaumcraftApi.addInfusionCraftingRecipe('): continue
    args,_ = call(s,'ThaumcraftApi.addInfusionCraftingRecipe')
    ident=ev(args[0]); expr=args[1]
    if not expr.startswith('new InfusionRecipe('): continue
    a,_ = call(expr,'new InfusionRecipe')
    output=ev(a[1]); row={'type':'thaumcraft:infusion','research':ev(a[0]),'instability':ev(a[2]),'aspects':ev(a[3]),
        'central':ingredient(a[4]),'components':[ingredient(x) for x in a[5:]]}
    if isinstance(output,list):
        label,(kind,value)=output; suffix={'byte':'b','int':'','short':'s'}[kind]
        row.update(mode='mutate',mutation='{'+json.dumps(label)+':'+str(value)+suffix+'}')
    else: row.update(mode='replace',result=output)
    written[ident]=row

assert len(written)==47, len(written)
for entry in ns['ROWS']:
    if entry['kind'] != 'infusion_enchantment': continue
    real=entry['id'].removesuffix('fake'); name=real.split(':')[1][2:].upper(); base=ns['ENV']['IE'+name]
    written[real]={'type':'thaumcraft:infusion','mode':'enchantment','enchantment':name,'research':'INFUSIONENCHANTMENT',
        'instability':4,'aspects':base['aspects'],'components':[convert(x,0) if 'ore' not in x else {'tag':ore(x['ore'])} for x in base['components']],
        'display_central':entry['ingredients'][0]}
written['thaumcraft:runicarmor']={'type':'thaumcraft:infusion','mode':'runic','research':'RUNICSHIELDING','instability':5,
    'components':[{'item':'thaumcraft:salis_mundus','damage':0},{'tag':'forge:gems/amber'}],
    'display_central':{'item':'minecraft:leather_chestplate'}}
assert len(written)==56 and not any('fake' in key for key in written)
for ident,row in written.items():
    (target/(ident.split(':')[1]+'.json')).write_text(json.dumps(row,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('56 real BETA26 infusion recipes generated: 47 ordinary + 8 enchantments + 1 dynamic runic')
