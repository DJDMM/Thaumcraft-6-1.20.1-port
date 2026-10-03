"""Copy the operational altar's additional assets from the pinned BETA26 release."""
from pathlib import Path
from zipfile import ZipFile
import hashlib, json, io
from PIL import Image

PROJECT = Path(__file__).resolve().parents[1]
JAR = PROJECT.parents[1] / 'work/Thaumcraft-1.12.2-6.1.BETA26.jar'
assert hashlib.sha256(JAR.read_bytes()).hexdigest() == '9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f'
ASSETS = PROJECT / 'src/main/resources/assets/thaumcraft'
with ZipFile(JAR) as source:
    sounds = json.loads((ASSETS / 'sounds.json').read_text(encoding='utf8'))
    original = json.loads(source.read('assets/thaumcraft/sounds.json'))
    files = ['textures/blocks/infuser_ancient.png', 'textures/blocks/infuser_eldritch.png']
    for name in ('craftfail', 'wand', 'infuser', 'infuserstart'):
        sounds[name] = original[name]
        for entry in original[name]['sounds']:
            sound = entry if isinstance(entry, str) else entry['name']
            files.append('sounds/' + sound.removeprefix('thaumcraft:') + '.ogg')
    for relative in files:
        path = ASSETS / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.read('assets/thaumcraft/' + relative))
    # Vanilla1.20 uses individual effect sprites; preserve the original18x18 pixels.
    sheet = Image.open(io.BytesIO(source.read('assets/thaumcraft/textures/misc/potions.png')))
    for name, column in (('flux_taint', 3), ('vis_exhaust', 5)):
        path = ASSETS / 'textures/mob_effect' / (name + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        sheet.crop((column*18,18,column*18+18,36)).save(path)
    (ASSETS / 'sounds.json').write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    for lang in ('en_us', 'ru_ru'):
        path = ASSETS / 'lang' / (lang + '.json')
        data = json.loads(path.read_text(encoding='utf8'))
        for line in source.read('assets/thaumcraft/lang/' + lang + '.lang').decode('utf8').splitlines():
            if '=' not in line: continue
            key, value = line.split('=', 1)
            if key.startswith('stability.') or key in ('potion.flux_taint', 'potion.vis_exhaust'):
                data[key] = value
        data['effect.thaumcraft.flux_taint'] = data['potion.flux_taint']
        data['effect.thaumcraft.vis_exhaust'] = data['potion.vis_exhaust']
        if lang == 'ru_ru':
            data.update({'stability.gain': 'прирост / цикл', 'stability.range': 'от 0 до ', 'stability.loss': 'потери / цикл',
                         'tooltip.thaumcraft.vis_resonator': 'Показывает вис и флюкс местной ауры.',
                         'tooltip.thaumcraft.caster_utility': 'Активирует устройства и алтарь наполнения. Заклинания пока не перенесены.'})
        else:
            data.update({'tooltip.thaumcraft.vis_resonator': 'Displays local aura vis and flux.',
                         'tooltip.thaumcraft.caster_utility': 'Activates devices and infusion altars. Focus casting is not yet ported.'})
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
print('Copied', len(files), 'original infusion resources and both HUD locales.')
