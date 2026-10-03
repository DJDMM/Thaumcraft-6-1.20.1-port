"""Copy only the pinned BETA26 Air cast sound; preserve the other port assets."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json

PROJECT = Path(__file__).resolve().parents[1]
JAR = PROJECT.parents[1] / 'work/Thaumcraft-1.12.2-6.1.BETA26.jar'
assert hashlib.sha256(JAR.read_bytes()).hexdigest() == '9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f'
ASSETS = PROJECT / 'src/main/resources/assets/thaumcraft'
with ZipFile(JAR) as source:
    original = json.loads(source.read('assets/thaumcraft/sounds.json'))
    current = json.loads((ASSETS / 'sounds.json').read_text(encoding='utf8'))
    current['wind'] = original['wind']
    files = []
    for sound in original['wind']['sounds']:
        name = sound if isinstance(sound, str) else sound['name']
        relative = 'sounds/' + name.removeprefix('thaumcraft:') + '.ogg'
        destination = ASSETS / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(source.read('assets/thaumcraft/' + relative))
        files.append(relative)
    (ASSETS / 'sounds.json').write_text(json.dumps(current, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
print('Copied pinned Air sound:', ', '.join(files))
