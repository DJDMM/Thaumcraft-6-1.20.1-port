"""Copy original Brain/Jar sound samples and event definitions from pinned BETA26."""
from pathlib import Path
import hashlib
import json
from zipfile import ZipFile

project = Path(__file__).resolve().parents[1]
original = project.parents[1] / 'work/Thaumcraft-1.12.2-6.1.BETA26.jar'
if hashlib.sha256(original.read_bytes()).hexdigest() != '9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f':
    raise ValueError('Pinned original BETA26 JAR changed')
assets = project / 'src/main/resources/assets/thaumcraft'
with ZipFile(original) as archive:
    sounds = json.loads((assets/'sounds.json').read_text(encoding='utf-8-sig'))
    original_sounds = json.loads(archive.read('assets/thaumcraft/sounds.json'))
    for event in ['brain','jar']:
        sounds[event] = original_sounds[event]
        for index in range(1,5):
            relative = f'sounds/{event}{index}.ogg'
            target = assets/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(archive.read('assets/thaumcraft/'+relative))
    (assets/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Copied eight byte-exact Brain/Jar OGGs and two original sound events.')
