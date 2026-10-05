"""Copy audited BETA26 seal/logistics art and interface strings from the pinned JAR."""
import hashlib
import json
import zipfile
from pathlib import Path

project = Path(__file__).resolve().parents[1]
jar = project.parents[1] / 'work/Thaumcraft-1.12.2-6.1.BETA26.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest() == '9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f'
assets = project / 'src/main/resources/assets/thaumcraft'
with zipfile.ZipFile(jar) as source:
    for relative in ('textures/gui/gui_logistics.png', 'textures/misc/frame_corner.png', 'textures/misc/seal_area.png', 'sounds/shock1.ogg', 'sounds/shock2.ogg'):
        target = assets / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read('assets/thaumcraft/' + relative))
    sound_file = assets / 'sounds.json'
    sounds = json.loads(sound_file.read_text(encoding='utf-8-sig'))
    sounds['shock'] = json.loads(source.read('assets/thaumcraft/sounds.json'))['shock']
    sound_file.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for locale in ('en_us', 'ru_ru'):
        target = assets / 'lang' / (locale + '.json')
        strings = json.loads(target.read_text(encoding='utf-8-sig'))
        count = 0
        for line in source.read('assets/thaumcraft/lang/' + locale + '.lang').decode('utf-8-sig').splitlines():
            if '=' not in line or line.lstrip().startswith('#'):
                continue
            key, value = line.split('=', 1)
            if key.startswith(('button.', 'golem.prop.', 'logistics.', 'tc.logistics.')) or key in ('tc.notowned', 'golem.follow', 'golem.stay', 'golem.logistics'):
                strings[key] = value
                count += 1
        strings['thaumcraft.golemancy.seal'] = 'Control Seal' if locale == 'en_us' else 'Управляющая печать'
        strings['thaumcraft.golemancy.logistics'] = 'Golem Logistics' if locale == 'en_us' else 'Логистика големов'
        strings['thaumcraft.golemancy.search'] = 'Search' if locale == 'en_us' else 'Поиск'
        strings['thaumcraft.golemancy.request'] = 'Request delivery' if locale == 'en_us' else 'Запросить доставку'
        strings['thaumcraft.golemancy.count'] = 'Amount' if locale == 'en_us' else 'Количество'
        target.write_text(json.dumps(strings, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(locale, count, 'pinned interface strings')
