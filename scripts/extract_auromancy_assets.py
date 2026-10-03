"""Pinned TC6 BETA26 focus UI strings, textures and focus-change sound."""
from pathlib import Path
from zipfile import ZipFile
import hashlib, json

PROJECT = Path(__file__).resolve().parents[1]
JAR = PROJECT.parents[1] / 'work/Thaumcraft-1.12.2-6.1.BETA26.jar'
assert hashlib.sha256(JAR.read_bytes()).hexdigest() == '9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f'
ASSETS = PROJECT / 'src/main/resources/assets/thaumcraft'
with ZipFile(JAR) as source:
    sounds = json.loads((ASSETS / 'sounds.json').read_text(encoding='utf8'))
    original = json.loads(source.read('assets/thaumcraft/sounds.json'))
    sounds['ticks'] = original['ticks']
    files = ['textures/gui/' + name + '.png' for name in ('gui_wandtable', 'gui_wandtable2', 'gui_wandtable3', 'gui_base', 'complex', 'costxp', 'costvis')]
    files += ['sounds/' + (s if isinstance(s,str) else s['name']).removeprefix('thaumcraft:') + '.ogg' for s in original['ticks']['sounds']]
    for relative in files:
        path = ASSETS / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.read('assets/thaumcraft/' + relative))
    (ASSETS / 'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for locale in ('en_us','ru_ru'):
        path = ASSETS / 'lang' / (locale + '.json')
        data = json.loads(path.read_text(encoding='utf8'))
        for line in source.read('assets/thaumcraft/lang/' + locale + '.lang').decode('utf-8-sig').splitlines():
            if '=' not in line: continue
            key,value=line.split('=',1)
            if key.startswith(('focus.','focuspart.','wandtable.','item.Focus.','thaumcraft.','key.categories.thaumcraft')) or key == 'tc.vis.cost':
                data[key]=value
        if locale=='ru_ru':
            data.update({
                'key.thaumcraft.focus_change':'Сменить фокус кастера',
                'gui.thaumcraft.focus.select':'Фокусы кастера',
                'gui.thaumcraft.focus.remove':'Снять',
                'gui.thaumcraft.focus.none':'В инвентаре нет готовых фокусов',
                'gui.thaumcraft.focus.hint':'ЛКМ — выбрать; колесо — страница; Shift + F — снять',
                'tooltip.thaumcraft.caster':'ПКМ — заклинание или управление устройством. F — выбор фокуса.',
                'tooltip.thaumcraft.focus.capacity':'Предел сложности: %s',
                'tooltip.thaumcraft.focus.blank':'Настройте фокус на столе обработки фокусов.',
                'tooltip.thaumcraft.focus.price':'%s вис за применение; перезарядка %s с'
            })
        else:
            data.update({
                'key.thaumcraft.focus_change':'Change Caster Focus',
                'gui.thaumcraft.focus.select':'Caster Foci',
                'gui.thaumcraft.focus.remove':'Remove',
                'gui.thaumcraft.focus.none':'No completed foci in your inventory',
                'gui.thaumcraft.focus.hint':'Click to select; scroll for pages; Shift + F to remove',
                'tooltip.thaumcraft.caster':'Right-click to cast or control a device. F selects a focus.',
                'tooltip.thaumcraft.focus.capacity':'Complexity limit: %s',
                'tooltip.thaumcraft.focus.blank':'Configure this focus at a Focal Manipulator.',
                'tooltip.thaumcraft.focus.price':'%s vis per cast; cooldown %s s'
            })
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        statuses = {
            'en_us': {'busy':'Drawing vis from the aura…','research':'Research required: %s','unsupported':'This focus node is not implemented yet.',
                'accepted':'Accepted','locked':'Table is unavailable','stale':'Table changed; try again','invalid':'Invalid focus graph','missing_focus':'Insert a focus',
                'missing_research':'Research is incomplete','missing_crystals':'Required crystals are missing','missing_xp':'Not enough levels','overflow':'Table must be replaced'},
            'ru_ru': {'busy':'Поглощение вис из ауры…','research':'Требуется исследование: %s','unsupported':'Этот узел фокуса пока не реализован.',
                'accepted':'Принято','locked':'Стол недоступен','stale':'Состояние стола изменилось','invalid':'Недопустимая схема фокуса','missing_focus':'Вставьте фокус',
                'missing_research':'Исследование не завершено','missing_crystals':'Не хватает кристаллов','missing_xp':'Не хватает уровней опыта','overflow':'Замените стол'}
        }[locale]
        for key,value in statuses.items():
            if key in ('busy','research','unsupported'): data['gui.thaumcraft.focal.'+key]=value
            data['gui.thaumcraft.focal.result.'+key]=value
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Copied',len(files),'pinned focus UI/sound assets and two focus locales.')
