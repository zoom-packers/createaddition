"""List advancements whose recipe references (rewards.recipes, recipe_unlocked/recipe_crafted `recipes`) point at
recipes that do not exist. 26.3 resolves these as registry holders, so a dangling one stops the data pack loading.

    python -I tools/check_recipe_refs.py <26.3 minecraft jar>
"""
import json
import pathlib
import sys
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/data'
known = set()
for f in root.glob('*/recipe/**/*.json'):
    ns = f.relative_to(root).parts[0]
    known.add(f'{ns}:' + '/'.join(f.relative_to(root / ns / 'recipe').with_suffix('').parts))
with zipfile.ZipFile(sys.argv[1]) as zf:
    for n in zf.namelist():
        if n.startswith('data/minecraft/recipe/') and n.endswith('.json'):
            known.add('minecraft:' + n[len('data/minecraft/recipe/'):-5])


def refs(node):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == 'recipes':
                yield from ([v] if isinstance(v, str) else [x for x in v if isinstance(x, str)])
            else:
                yield from refs(v)
    elif isinstance(node, list):
        for v in node:
            yield from refs(v)


bad = 0
for f in sorted(root.glob('*/advancement/**/*.json')):
    data = json.loads(f.read_text(encoding='utf-8'))
    if 'neoforge:conditions' in data:
        continue  # only loads when the compat mod (and its generated recipe) is present
    for r in refs(data):
        if not r.startswith('#') and r not in known:
            print(f.relative_to(root), '->', r)
            bad += 1
print(bad, 'dangling recipe references')
