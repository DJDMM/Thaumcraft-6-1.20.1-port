"""Validate local model/texture references and translations without Minecraft."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'src/main/resources'
assets = root / 'assets/thaumcraft'
documents = {p: json.loads(p.read_text(encoding='utf-8-sig')) for p in root.rglob('*.json')}
errors = []
for path, data in documents.items():
    if 'models' in path.parts and isinstance(data, dict):
        parent = data.get('parent', '')
        if parent.startswith('thaumcraft:'):
            target = assets / 'models' / (parent.split(':', 1)[1] + '.json')
            if not target.is_file(): errors.append(f'{path}: missing parent {parent}')
        for override in data.get('overrides', []):
            ref=override.get('model','')
            if ref.startswith('thaumcraft:') and not (assets/'models'/(ref.split(':',1)[1]+'.json')).is_file():
                errors.append(f'{path}: missing override model {ref}')
        if data.get('loader')=='forge:obj' and data.get('model','').startswith('thaumcraft:'):
            mesh=assets/data['model'].split(':',1)[1]
            if not mesh.is_file():errors.append(f'{path}: missing OBJ {mesh}')
        for texture in data.get('textures', {}).values():
            if texture.startswith('thaumcraft:'):
                target = assets / 'textures' / (texture.split(':', 1)[1] + '.png')
                if not target.is_file(): errors.append(f'{path}: missing texture {texture}')
                directory = texture.split(':', 1)[1].split('/')[0]
                if directory in ('items', 'blocks'):
                    atlas = documents.get(root/'assets/minecraft/atlases/blocks.json', {})
                    if not any(source.get('type') == 'minecraft:directory' and source.get('source') == directory and source.get('prefix') == directory+'/' for source in atlas.get('sources', [])):
                        errors.append(f'{path}: legacy texture directory {directory} is not included in the modern block atlas')
    if 'blockstates' in path.parts:
        variants = list(data.get('variants', {}).values()) + [part['apply'] for part in data.get('multipart', [])]
        for variant in variants:
            for entry in variant if isinstance(variant, list) else [variant]:
                if entry['model'].startswith('thaumcraft:'):
                    model = entry['model'].split(':', 1)[1]
                    if not (assets/'models'/f'{model}.json').is_file(): errors.append(f'Missing block model: {model}')
auxiliary_item_models = set()
for path, model in documents.items():
    if 'models' not in path.parts or not isinstance(model, dict): continue
    parent = model.get('parent', '')
    if parent.startswith('thaumcraft:item/'): auxiliary_item_models.add(parent.split('/')[-1])
    for override in model.get('overrides', []):
        ref = override.get('model', '')
        if ref.startswith('thaumcraft:item/'): auxiliary_item_models.add(ref.split('/')[-1])
catalog_items = {row['id'] for row in documents.get(assets/'catalog/items.json', {}).get('items', [])}
for locale in ('en_us', 'ru_ru'):
    names = documents[assets/'lang'/f'{locale}.json']
    for model in (assets/'models/item').glob('*.json'):
        if model.stem in auxiliary_item_models and model.stem not in catalog_items: continue
        kind = 'block' if (assets/'blockstates'/model.name).exists() else 'item'
        key = f'{kind}.thaumcraft.{model.stem}'
        if not names.get(key): errors.append(f'{locale}: missing {key}')
    for biome in (root/'data/thaumcraft/worldgen/biome').glob('*.json'):
        if not names.get(f'biome.thaumcraft.{biome.stem}'):
            errors.append(f'{locale}: missing biome.thaumcraft.{biome.stem}')

# Missing dynamic-registry references prevent world creation, even when the
# models and translations are valid. Check mod-owned biome/feature links too.
def check_worldgen_reference(location, kind, context):
    if isinstance(location, str) and location.startswith('thaumcraft:'):
        target = root/'data/thaumcraft/worldgen'/kind/(location.split(':', 1)[1] + '.json')
        if target not in documents: errors.append(f'{context}: missing {kind} {location}')

for path, data in documents.items():
    if 'worldgen' not in path.parts or not isinstance(data, dict): continue
    if path.parent.name == 'biome':
        for step in data.get('features', []):
            for location in step: check_worldgen_reference(location, 'placed_feature', path)
    elif path.parent.name == 'placed_feature':
        check_worldgen_reference(data.get('feature'), 'configured_feature', path)

# Thaumonomicon uses standalone textures as well as registered item models.
# A missing texture here can silently hide a category tab instead of showing
# Minecraft's missing-texture checkerboard, so validate it explicitly.
def check_book_texture(location, context):
    if not isinstance(location, str) or not re.fullmatch(r'[a-z0-9_.-]+:textures/[a-z0-9_./-]+\.(?:png|jpg)', location):
        errors.append(f'{context}: invalid texture location {location!r}')
        return
    namespace, relative = location.split(':', 1)
    if not (root / 'assets' / namespace / relative).is_file():
        errors.append(f'{context}: missing texture {location}')


def check_dimensions(value, context):
    for dimension in ('width', 'height'):
        number = value.get(dimension)
        if type(number) is not int or number <= 0:
            errors.append(f'{context}: {dimension} must be a positive integer')


icon_manifest_path = assets / 'research/icon_textures.json'
icon_manifest = documents.get(icon_manifest_path, {})
icon_mappings = icon_manifest.get('icons', {})
texture_sizes = icon_manifest.get('textures', {})
if not icon_mappings or not texture_sizes:
    errors.append('Thaumonomicon: missing or empty icon_textures.json manifest')
for location, size in texture_sizes.items():
    check_book_texture(location, 'Thaumonomicon texture manifest')
    check_dimensions(size, location)
for raw, icon in icon_mappings.items():
    for field in ('texture', 'background'):
        location = icon.get(field)
        if location:
            check_book_texture(location, f'Icon {raw}')
            if location not in texture_sizes:
                errors.append(f'Icon {raw}: no dimensions for {location}')
    if icon.get('texture'):
        check_dimensions(icon, f'Icon {raw}')
    if not icon.get('texture') and not icon.get('item'):
        errors.append(f'Icon {raw}: mapping has neither texture nor item')
    if ':textures/' in raw and icon.get('texture') != raw:
        errors.append(f'Icon {raw}: direct texture mapping does not preserve the original location')
    if raw.startswith('focus:') and (icon.get('kind') != 'focus' or not icon.get('texture')):
        errors.append(f'Icon {raw}: focus mapping requires its texture and kind')
    if not raw.startswith('focus:') and ':textures/' not in raw:
        if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', icon.get('item', '')):
            errors.append(f'Icon {raw}: invalid or missing item mapping')
catalog_path = assets / 'research/catalog.json'
if catalog_path not in documents:
    errors.append('Thaumonomicon: missing research catalog')
for entry in documents.get(catalog_path, []):
    for raw in entry.get('icons', []):
        if raw not in icon_mappings:
            errors.append(f'Research {entry["key"]}: no mapping for icon {raw}')

required_gui = [f'gui_research_back_{index}.jpg' for index in range(1, 8)] + [
    'gui_research_back_over.png', 'gui_research_browser.png', 'gui_research_table.png',
    'gui_researchbook.png', 'gui_researchbook_overlay.png',
]
for filename in required_gui:
    check_book_texture(f'thaumcraft:textures/gui/{filename}', 'Thaumonomicon GUI')
basics_icon = 'thaumcraft:textures/items/thaumonomicon_cheat.png'
check_book_texture(basics_icon, 'Thaumonomicon BASICS tab')
if basics_icon not in texture_sizes:
    errors.append('Thaumonomicon BASICS tab: missing icon dimensions')
image_refs = set()
for locale in ('en_us', 'ru_ru'):
    for key, value in documents[assets / 'lang' / f'{locale}.json'].items():
        if not isinstance(value, str):
            continue
        for location in re.findall(r'<IMG>([\w.-]+:[^:<>]+)', value):
            check_book_texture(location, f'{locale}:{key}')
            image_refs.add(location)
# Original BlockJar/BlockTranslucent select TRANSLUCENT. Default solid hides
# interior essence/brain despite valid textures and distinct baked geometry.
translucent_models = ['jar_brain_item', 'empty'] + [
    f'{jar}{suffix}' for jar in ('jar_normal', 'jar_void')
    for suffix in ('', '_25', '_50', '_75', '_100')
]
for name in translucent_models:
    path = assets / 'models/block' / f'{name}.json'
    if documents.get(path, {}).get('render_type') != 'minecraft:translucent':
        errors.append(f'{path}: lost original TRANSLUCENT layer')
for block in ('jar_normal', 'jar_void', 'jar_brain', 'amber_block', 'amber_brick', 'empty'):
    for path in (assets / 'models/catalog' / block).glob('*.json'):
        if documents[path].get('render_type') != 'minecraft:translucent':
            errors.append(f'{path}: state wrapper lost original TRANSLUCENT layer')
if documents.get(assets / 'models/block/golem_builder_neutral.json', {}).get('flip_v') is not False:
    errors.append('Placed golem builder must combine the original loader and texture-matrix V flips')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'Validated {len(documents)} JSON files, local model/texture references and both translations.')
print(f'Thaumonomicon: {len(icon_mappings)} icon mappings, {len(texture_sizes)} sized textures, '
      f'{len(required_gui)} GUI textures, BASICS tab and {len(image_refs)} IMG references.')
