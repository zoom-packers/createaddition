"""Convert Minecraft 26.2 data-pack / resource-pack JSON to the 26.3 formats.

26.3 reworked the loot/predicate stack and several data formats.  Converted, per directory:

  data/<ns>/   loot_table, item_modifier, predicate, advancement, enchantment, recipe, villager_trade,
               loot_modifiers (NeoForge global loot modifiers)
  assets/<ns>/ models (element "shade"), atlases (trim palette paths)

Worldgen is not handled here (see tools/worldgen_26_3.py).

Usage (always with -I, so nothing in the tree being converted can shadow a module):

    python -I tools/data_26_3.py [DIR ...]       convert in place, then lint (default: src/main/resources/data and
                                                 src/client/resources/assets; a DIR named "assets" is a resource pack)
    python -I tools/data_26_3.py --dry-run [DIR] only report what would change
    python -I tools/data_26_3.py --lint [DIR]    only report leftover 26.2 constructs (exit code 1 if any)
    python -I tools/data_26_3.py --check OLD.jar NEW.jar [-v N]
        convert every vanilla 26.2 file in OLD.jar and compare it with the 26.3 file of the same path in NEW.jar
        (parsed JSON; 2.0 == 2, key order ignored; 26.3 references to vanilla predicate files are inlined first),
        then check that every 26.3 vanilla file is left unchanged by the converter
    python -I tools/test_data_26_3.py [OLD.jar NEW.jar]   unit cases + idempotency + (optionally) --check

The conversion is idempotent: 26.3 files come out unchanged, so it is safe to re-run on a tree where only some files
came back from upstream in the 26.2 format.  The only value-based (not shape-based) rule is the cooking time of
blasting/smoking recipes, see recipe().  Rewritten data files keep upstream's layout (2-space indent, line endings,
datagen key order); asset files are edited textually so hand-written Blockbench layouts survive.

Format changes handled (26.2 -> 26.3):

  Loot conditions (anywhere): "condition": T -> "type": T.  A list of conditions ("conditions": [...]) became a single
    optional "condition" (one entry: itself, several: minecraft:all_of; a one-term all_of/any_of is unwrapped).
    block_state_property {block, properties} -> match_block {blocks, state}.  reference {name} -> a bare predicate id
    string (conditions are holders now).  value_check {value, range} -> int_value_check {value, test}.
  Loot functions (item modifiers): "function": T -> "type": T, their "conditions" -> "condition".  A list of
    functions ("functions": [...]) became a single optional "modifier" (one entry: itself, several: a list = the
    inline form of minecraft:sequence).  Loot tables, pools and entries: "functions" -> "modifier", "conditions" ->
    "condition".  exploration_map "destination" is a required holder set ("#tag"), default was on_treasure_maps.
  Number providers split into int providers and float providers: constant {"type": "constant", "value": v} -> v;
    ints written as ints in int slots (rolls, set_count, enchant_with_levels, ...); sum {summands} -> add {inputs};
    score is int-only and lost "scale" (-> mul); binomial is int-only, enchantment_level float-only: wrapped in
    from_int / from_float when used in the other kind of slot; untyped {min, max} -> uniform.
  Advancements: every criterion field that was a "ContextAwarePredicate" (player, entity, location, ...) is now one
    loot condition: a list of conditions is merged as above, the bare entity-predicate shorthand {...} becomes
    {"type": "minecraft:entity_properties", "entity": "this", "predicate": {...}}.  Renamed criterion fields:
    recipe_unlocked recipe -> recipes, recipe_crafted / crafter_recipe_crafted recipe_id -> recipes,
    player_generates_container_loot loot_table -> loot_tables, enter_block / slide_down_block / bee_nest_destroyed
    block -> blocks.
  Damage-type tag predicates: {"id": "t", "expected": b} -> {"id": "#t", ...}.
  Block states: {"Name", "Properties"} -> {"id", "properties"}; simple_state_provider {state} -> the state.
  Item components: minecraft:pot_decorations [back, left, right, front] -> {side: {"id": item}}.
  Villager trades: given_item_modifiers -> given_item_modifier, merchant_predicate is a condition.
  NeoForge loot modifiers: "conditions" -> "condition".
  Recipes: cooking recipes always carry "cookingtime"; smokers and blast furnaces now run twice as fast (a context
    float provider keyed on the minecraft:block/fast_cooking predicate), so smoking/blasting times are doubled
    (missing -> 200).
  Models: element "shade": false -> "shade_direction_override": "up" ("shade" is gone; face-level "shade" was never
    read and is left alone).
  Atlases: paletted_permutations palettes are loaded from textures/palettes/ now: minecraft:trims/color_palettes/
    trim_palette -> minecraft:trim_base, minecraft:trims/color_palettes/X -> minecraft:trim/X.
"""
import argparse
import copy
import json
import os
import sys
import zipfile

# ----------------------------------------------------------------------------------------------------------------
# helpers


def ns(t):
    """'foo' -> 'minecraft:foo' (identifiers in type fields)."""
    if isinstance(t, str) and ':' not in t:
        return 'minecraft:' + t
    return t


def is_int_like(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and float(v).is_integer()


class Ctx:
    """Collects warnings while converting one file."""

    def __init__(self, path=''):
        self.path = path
        self.warnings = []

    def warn(self, msg):
        self.warnings.append(msg)


# ----------------------------------------------------------------------------------------------------------------
# number providers

INT_ONLY = {'minecraft:binomial', 'minecraft:score', 'minecraft:from_float', 'minecraft:floor_mod',
            'minecraft:floor_div'}
FLOAT_ONLY = {'minecraft:enchantment_level', 'minecraft:from_int', 'minecraft:ceil', 'minecraft:cos',
              'minecraft:floor', 'minecraft:length', 'minecraft:round', 'minecraft:sin', 'minecraft:sqrt',
              'minecraft:truncate'}


def num(n, kind, ctx):
    """Convert a 26.2 NumberProvider to a 26.3 ContextIntProvider (kind='int') or ContextFloatProvider ('float')."""
    if isinstance(n, bool):
        return n
    if isinstance(n, (int, float)):
        if kind == 'int':
            if is_int_like(n):
                return int(n)
            ctx.warn('non-integer constant %r in an int provider slot (truncated by the game)' % n)
            return n
        return n
    if isinstance(n, str):
        return n  # reference to a context_int_provider / context_float_provider file
    if not isinstance(n, dict):
        return n
    n = dict(n)
    t = n.get('type')
    if t is None:
        if 'min' in n or 'max' in n:
            t = 'minecraft:uniform'
        else:
            return n
    t = ns(t)
    if t == 'minecraft:constant':
        return num(n.get('value', 0), kind, ctx)
    if t == 'minecraft:uniform':
        out = {'type': t}
        for k in ('min', 'max'):
            if k in n:
                out[k] = num(n[k], kind, ctx)
        return out
    if t == 'minecraft:binomial':
        out = {'type': t, 'n': num(n['n'], 'int', ctx), 'p': num(n['p'], 'float', ctx)}
        return wrap_kind(out, 'int', kind)
    if t == 'minecraft:score':
        out = {'type': t, 'target': conv_score_target(n['target']), 'score': n['score']}
        if 'fallback' in n:
            out['fallback'] = num(n['fallback'], 'int', ctx)
        if 'scale' in n and n['scale'] not in (1, 1.0):
            # 26.2 multiplied the score by "scale"; 26.3 has no such field
            scaled = {'type': 'minecraft:mul', 'inputs': [wrap_kind(out, 'int', 'float'), num(n['scale'], 'float', ctx)]}
            return wrap_kind(scaled, 'float', kind)
        return wrap_kind(out, 'int', kind)
    if t == 'minecraft:storage':
        out = {'type': t, 'storage': n['storage'], 'path': n['path']}
        if 'fallback' in n:
            out['fallback'] = num(n['fallback'], kind, ctx)
        return out
    if t == 'minecraft:enchantment_level':
        out = {'type': t, 'amount': n['amount']}
        return wrap_kind(out, 'float', kind)
    if t in ('minecraft:sum', 'minecraft:add'):
        inputs = n.get('summands', n.get('inputs', []))
        return {'type': 'minecraft:add', 'inputs': [num(x, kind, ctx) for x in inputs]}
    # 26.3 provider types: convert children generically
    out = {'type': t}
    for k, v in n.items():
        if k == 'type':
            continue
        if k in ('input', 'left', 'right', 'base', 'exponent', 'on_true', 'on_false', 'fallback', 'min', 'max'):
            sub = kind
            if t == 'minecraft:from_int':
                sub = 'int'
            elif t == 'minecraft:from_float':
                sub = 'float'
            out[k] = num(v, sub, ctx)
        elif k == 'inputs' and isinstance(v, list):
            out[k] = [num(x, kind, ctx) for x in v]
        elif k == 'condition':
            out[k] = cond(v, ctx)
        else:
            out[k] = v
    if t in INT_ONLY:
        return wrap_kind(out, 'int', kind)
    if t in FLOAT_ONLY:
        return wrap_kind(out, 'float', kind)
    return out


def wrap_kind(provider, have, want):
    if have == want or want is None:
        return provider
    if want == 'float':
        return {'type': 'minecraft:from_int', 'input': provider}
    return {'type': 'minecraft:from_float', 'input': provider}


def conv_score_target(t):
    if isinstance(t, dict) and 'type' in t:
        t = dict(t)
        t['type'] = ns(t['type'])
        return t
    return t


def int_range(r, ctx):
    """26.2 IntRange ({min, max} of number providers, or a constant) -> 26.3 IntRangePredicate."""
    if isinstance(r, dict) and 'type' not in r and set(r) <= {'min', 'max'}:
        return {k: num(v, 'int', ctx) for k, v in r.items()}
    return num(r, 'int', ctx)


def float_range(r, ctx):
    if isinstance(r, dict) and 'type' not in r and set(r) <= {'min', 'max'}:
        return {k: num(v, 'float', ctx) for k, v in r.items()}
    return num(r, 'float', ctx)


# ----------------------------------------------------------------------------------------------------------------
# loot conditions (predicates)


def conds(lst, ctx):
    """A 26.2 list of conditions -> one 26.3 condition (or None when empty)."""
    if lst is None:
        return None
    if not isinstance(lst, list):
        return cond(lst, ctx)
    out = [cond(c, ctx) for c in lst]
    if not out:
        return None
    if len(out) == 1:
        return out[0]
    return {'type': 'minecraft:all_of', 'terms': out}


def cond(c, ctx):
    """Convert one condition (26.2 or already 26.3)."""
    if isinstance(c, str):
        return c  # reference to a predicate file
    if isinstance(c, list):
        return conds(c, ctx)
    if not isinstance(c, dict):
        return c
    c = dict(c)
    if 'condition' in c and isinstance(c['condition'], str) and 'type' not in c:
        t = ns(c.pop('condition'))
    elif 'type' in c:
        t = ns(c.pop('type'))
    else:
        return c
    out = {}
    if t == 'minecraft:reference':
        return c['name']
    if t == 'minecraft:block_state_property':
        t = 'minecraft:match_block'
        if 'block' in c:
            out['blocks'] = c.pop('block')
        if 'properties' in c:
            out['state'] = c.pop('properties')
    elif t == 'minecraft:match_block':
        out.update(block_pred(c, ctx))
        c = {}
    elif t == 'minecraft:value_check':
        t = 'minecraft:int_value_check'
        out['value'] = num(c.pop('value'), 'int', ctx)
        out['test'] = int_range(c.pop('range'), ctx)
    elif t == 'minecraft:int_value_check':
        out['value'] = num(c.pop('value'), 'int', ctx)
        out['test'] = int_range(c.pop('test'), ctx)
    elif t == 'minecraft:float_value_check':
        out['value'] = num(c.pop('value'), 'float', ctx)
        out['test'] = float_range(c.pop('test'), ctx)
    elif t in ('minecraft:any_of', 'minecraft:all_of'):
        terms = [cond(x, ctx) for x in c.pop('terms', [])]
        if len(terms) == 1 and not c:
            return terms[0]  # a one-term any_of/all_of is the term itself (26.3 datagen writes it that way)
        out['terms'] = terms
    elif t == 'minecraft:inverted':
        out['term'] = cond(c.pop('term'), ctx)
    elif t == 'minecraft:random_chance':
        out['chance'] = num(c.pop('chance'), 'float', ctx)
    elif t == 'minecraft:entity_properties':
        if 'predicate' in c:
            out['predicate'] = entity_pred(c.pop('predicate'), ctx)
    elif t == 'minecraft:entity_scores':
        out['scores'] = {k: int_range(v, ctx) for k, v in c.pop('scores', {}).items()}
    elif t == 'minecraft:match_tool':
        if 'predicate' in c:
            out['predicate'] = item_pred(c.pop('predicate'), ctx)
    elif t == 'minecraft:damage_source_properties':
        if 'predicate' in c:
            out['predicate'] = damage_source_pred(c.pop('predicate'), ctx)
    elif t == 'minecraft:location_check':
        if 'predicate' in c:
            out['predicate'] = location_pred(c.pop('predicate'), ctx)
    elif t == 'minecraft:time_check':
        if 'value' in c:
            out['value'] = int_range(c.pop('value'), ctx)
    out.update(c)
    return dict({'type': t}, **out)


# ----------------------------------------------------------------------------------------------------------------
# advancement-style predicates (item / entity / block / location / damage)


def item_pred(p, ctx):
    return p


def entity_pred(p, ctx):
    return p


def block_pred(p, ctx):
    return p


def location_pred(p, ctx):
    return p


def damage_source_pred(p, ctx):
    if not isinstance(p, dict):
        return p
    p = dict(p)
    if 'tags' in p:
        p['tags'] = [tag_pred(x) for x in p['tags']]
    for k in ('direct_entity', 'source_entity'):
        if k in p:
            p[k] = entity_pred(p[k], ctx)
    return p


def tag_pred(t):
    if isinstance(t, dict) and isinstance(t.get('id'), str) and not t['id'].startswith('#'):
        t = dict(t)
        t['id'] = '#' + t['id']
    return t


# ----------------------------------------------------------------------------------------------------------------
# loot functions (item modifiers)

FUNC_NUMBERS = {
    'minecraft:set_count': {'count': 'int'},
    'minecraft:set_damage': {'damage': 'float'},
    'minecraft:enchant_with_levels': {'levels': 'int'},
    'minecraft:enchanted_count_increase': {'count': 'float'},
    'minecraft:set_ominous_bottle_amplifier': {'amplifier': 'int'},
    'minecraft:set_random_dyes': {'number_of_dyes': 'int'},
}


def funcs(lst, ctx):
    """26.2 list of functions -> 26.3 'modifier' value (None, one function, or an inline sequence list)."""
    if lst is None:
        return None
    if not isinstance(lst, list):
        return func(lst, ctx)
    out = [func(f, ctx) for f in lst]
    if not out:
        return None
    if len(out) == 1:
        return out[0]
    return out


def func(f, ctx):
    if isinstance(f, str):
        return f  # reference to an item_modifier file
    if isinstance(f, list):
        return funcs(f, ctx)
    if not isinstance(f, dict):
        return f
    f = dict(f)
    if 'function' in f and isinstance(f['function'], str) and 'type' not in f:
        t = ns(f.pop('function'))
    elif 'type' in f:
        t = ns(f.pop('type'))
    else:
        return f
    out = {'type': t}
    c = None
    if 'conditions' in f:
        c = conds(f.pop('conditions'), ctx)
    elif 'condition' in f:
        c = cond(f.pop('condition'), ctx)
    if c is not None:
        out['condition'] = c
    for k, kind in FUNC_NUMBERS.get(t, {}).items():
        if k in f:
            out[k] = num(f.pop(k), kind, ctx)
    if t == 'minecraft:limit_count' and 'limit' in f:
        out['limit'] = int_range(f.pop('limit'), ctx)
    elif t == 'minecraft:set_attributes' and 'modifiers' in f:
        mods = []
        for m in f.pop('modifiers'):
            m = dict(m)
            if 'amount' in m:
                m['amount'] = num(m['amount'], 'float', ctx)
            mods.append(m)
        out['modifiers'] = mods
    elif t == 'minecraft:set_stew_effect' and 'effects' in f:
        effs = []
        for e in f.pop('effects'):
            e = dict(e)
            if 'duration' in e:
                e['duration'] = num(e['duration'], 'int', ctx)
            effs.append(e)
        out['effects'] = effs
    elif t == 'minecraft:sequence' and 'functions' in f:
        out['functions'] = [func(x, ctx) for x in f.pop('functions')]
    elif t == 'minecraft:filtered':
        if 'item_filter' in f:
            out['item_filter'] = item_pred(f.pop('item_filter'), ctx)
        for k in ('on_pass', 'on_fail', 'modifier'):
            if k in f:
                out[k] = funcs(f.pop(k), ctx)
    elif t == 'minecraft:modify_contents' and 'modifier' in f:
        out['modifier'] = funcs(f.pop('modifier'), ctx)
    elif t == 'minecraft:exploration_map':
        # 26.2: optional structure tag id (default on_treasure_maps); 26.3: required structure holder set
        dest = f.pop('destination', 'minecraft:on_treasure_maps')
        if isinstance(dest, str) and not dest.startswith('#'):
            dest = '#' + dest
        out['destination'] = dest
    elif t == 'minecraft:set_contents' and 'entries' in f:
        out['entries'] = [entry(e, ctx) for e in f.pop('entries')]
    out.update(f)
    return out


# ----------------------------------------------------------------------------------------------------------------
# loot tables


def move_common(src, out, ctx):
    """conditions/functions of a table, pool or entry -> condition/modifier."""
    if 'conditions' in src:
        c = conds(src.pop('conditions'), ctx)
        if c is not None:
            out['condition'] = c
    elif 'condition' in src:
        out['condition'] = cond(src.pop('condition'), ctx)
    if 'functions' in src:
        m = funcs(src.pop('functions'), ctx)
        if m is not None:
            out['modifier'] = m
    elif 'modifier' in src:
        out['modifier'] = funcs(src.pop('modifier'), ctx)


def entry(e, ctx):
    if not isinstance(e, dict):
        return e
    e = dict(e)
    t = ns(e.pop('type', None))
    out = {'type': t}
    move_common(e, out, ctx)
    if 'children' in e:
        out['children'] = [entry(x, ctx) for x in e.pop('children')]
    if t == 'minecraft:tag' and 'name' in e:
        name = e.pop('name')
        out['items'] = name if name.startswith('#') else '#' + name
    if t == 'minecraft:loot_table' and isinstance(e.get('value'), dict):
        out['value'] = loot_table(e.pop('value'), ctx)
    out.update(e)
    return out


def pool(p, ctx):
    p = dict(p)
    out = {}
    move_common(p, out, ctx)
    if 'rolls' in p:
        out['rolls'] = num(p.pop('rolls'), 'int', ctx)
    if 'bonus_rolls' in p:
        out['bonus_rolls'] = num(p.pop('bonus_rolls'), 'float', ctx)
    if 'entries' in p:
        out['entries'] = [entry(e, ctx) for e in p.pop('entries')]
    out.update(p)
    return out


def loot_table(t, ctx):
    t = dict(t)
    out = {}
    if 'type' in t:
        out['type'] = ns(t.pop('type'))
    move_common(t, out, ctx)
    if 'pools' in t:
        out['pools'] = [pool(p, ctx) for p in t.pop('pools')]
    out.update(t)
    return out


# ----------------------------------------------------------------------------------------------------------------
# advancements

# trigger -> criterion fields that are loot conditions in 26.3 (ContextAwarePredicate in 26.2); 'victims' is a list.
TRIGGER_COND_FIELDS = {
    'minecraft:any_block_use': ['location'],
    'minecraft:default_block_use': ['location'],
    'minecraft:placed_block': ['location'],
    'minecraft:item_used_on_block': ['location'],
    'minecraft:allay_drop_item_on_block': ['location'],
    'minecraft:bred_animals': ['parent', 'partner', 'child'],
    'minecraft:cured_zombie_villager': ['zombie', 'villager'],
    'minecraft:effects_changed': ['source'],
    'minecraft:fall_after_explosion': ['cause'],
    'minecraft:fishing_rod_hooked': ['entity'],
    'minecraft:player_killed_entity': ['entity'],
    'minecraft:entity_killed_player': ['entity'],
    'minecraft:kill_mob_near_sculk_catalyst': ['entity'],
    'minecraft:lightning_strike': ['lightning', 'bystander'],
    'minecraft:thrown_item_picked_up_by_entity': ['entity'],
    'minecraft:thrown_item_picked_up_by_player': ['entity'],
    'minecraft:player_hurt_entity': ['entity'],
    'minecraft:player_interacted_with_entity': ['entity'],
    'minecraft:player_sheared_equipment': ['entity'],
    'minecraft:summoned_entity': ['entity'],
    'minecraft:tame_animal': ['entity'],
    'minecraft:target_hit': ['projectile'],
    'minecraft:villager_trade': ['villager'],
}
TRIGGER_COND_LIST_FIELDS = {
    'minecraft:channeled_lightning': ['victims'],
    'minecraft:killed_by_arrow': ['victims'],
}


# trigger -> renamed criterion fields (26.2 name, 26.3 name)
TRIGGER_RENAMES = {
    'minecraft:recipe_unlocked': [('recipe', 'recipes')],
    'minecraft:recipe_crafted': [('recipe_id', 'recipes')],
    'minecraft:crafter_recipe_crafted': [('recipe_id', 'recipes')],
    'minecraft:player_generates_container_loot': [('loot_table', 'loot_tables')],
    'minecraft:enter_block': [('block', 'blocks')],
    'minecraft:slide_down_block': [('block', 'blocks')],
    'minecraft:bee_nest_destroyed': [('block', 'blocks')],
}


def context_pred(p, ctx):
    """26.2 ContextAwarePredicate (list of conditions, or entity-predicate shorthand) -> 26.3 condition."""
    if isinstance(p, list):
        return conds(p, ctx)
    if isinstance(p, dict) and 'condition' not in p:
        t = p.get('type')
        # a 26.3 condition has a condition "type"; an entity predicate may only carry an entity "type"
        if not (isinstance(t, str) and (ns(t) in CONDITION_TYPES or not ns(t).startswith('minecraft:'))):
            return {'type': 'minecraft:entity_properties', 'entity': 'this', 'predicate': entity_pred(p, ctx)}
    return cond(p, ctx)


def criterion(c, ctx):
    if not isinstance(c, dict):
        return c
    c = dict(c)
    trig = ns(c.get('trigger'))
    conds_ = c.get('conditions')
    if isinstance(conds_, dict):
        conds_ = dict(conds_)
        fields = ['player'] + TRIGGER_COND_FIELDS.get(trig, [])
        if trig.startswith('minecraft:'):
            for k in fields:
                if k in conds_:
                    v = context_pred(conds_[k], ctx)
                    if v is None:
                        del conds_[k]
                    else:
                        conds_[k] = v
            for k in TRIGGER_COND_LIST_FIELDS.get(trig, []):
                if k in conds_:
                    conds_[k] = [context_pred(x, ctx) for x in conds_[k]]
        elif 'player' in conds_:
            # modded triggers built on SimpleCriterionTrigger share the "player" field
            v = context_pred(conds_['player'], ctx)
            if v is None:
                del conds_['player']
            else:
                conds_['player'] = v
        for old, new in TRIGGER_RENAMES.get(trig, ()):
            if old in conds_ and new not in conds_:
                conds_[new] = conds_.pop(old)
        c['conditions'] = conds_
    return c


def advancement(a, ctx):
    a = dict(a)
    if isinstance(a.get('criteria'), dict):
        a['criteria'] = {k: criterion(v, ctx) for k, v in a['criteria'].items()}
    return a


# ----------------------------------------------------------------------------------------------------------------
# enchantments


def enchantment(e, ctx):
    e = copy.deepcopy(e)

    def walk(x):
        if isinstance(x, dict):
            for k, v in list(x.items()):
                if k == 'requirements':
                    x[k] = cond(v, ctx)
                else:
                    walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)

    walk(e.get('effects'))
    return e


# ----------------------------------------------------------------------------------------------------------------
# recipes

COOKING_DEFAULT = {'minecraft:smelting': 200, 'minecraft:blasting': 100, 'minecraft:smoking': 100,
                   'minecraft:campfire_cooking': 600}
FAST_COOKING = {'minecraft:blasting', 'minecraft:smoking'}


def recipe(r, ctx):
    r = dict(r)
    t = ns(r.get('type'))
    if t in COOKING_DEFAULT:
        if 'cookingtime' not in r:
            v = COOKING_DEFAULT[t]
            r['cookingtime'] = v * 2 if t in FAST_COOKING else v
        elif t in FAST_COOKING:
            v = r['cookingtime']
            if v < 200:
                r['cookingtime'] = int(v) * 2
            elif v > 200:
                ctx.warn('%s cookingtime %s left as is: if this file is still 26.2, double it' % (t, v))
    return r


# ----------------------------------------------------------------------------------------------------------------
# dispatch by directory

def predicate_file(p, ctx):
    return conds(p, ctx) if isinstance(p, list) else cond(p, ctx)


def item_modifier_file(f, ctx):
    return funcs(f, ctx)


def villager_trade(v, ctx):
    v = dict(v)
    for k in ('wants', 'additional_wants'):
        if isinstance(v.get(k), dict) and 'count' in v[k]:
            v[k] = dict(v[k])
            v[k]['count'] = num(v[k]['count'], 'int', ctx)
    for k in ('max_uses', 'xp'):
        if k in v:
            v[k] = num(v[k], 'int', ctx)
    if 'reputation_discount' in v:
        v['reputation_discount'] = num(v['reputation_discount'], 'float', ctx)
    if 'merchant_predicate' in v:
        v['merchant_predicate'] = cond(v['merchant_predicate'], ctx)
    if 'given_item_modifiers' in v:
        m = funcs(v.pop('given_item_modifiers'), ctx)
        if m is not None:
            v['given_item_modifier'] = m
    elif 'given_item_modifier' in v:
        v['given_item_modifier'] = funcs(v['given_item_modifier'], ctx)
    return v


def neo_loot_modifier(m, ctx):
    """NeoForge global loot modifiers (data/*/loot_modifiers): "conditions" list -> "condition"."""
    if not isinstance(m, dict) or 'type' not in m:
        return m  # e.g. neoforge/loot_modifiers/global_loot_modifiers.json
    m = dict(m)
    out = {}
    for k, v in m.items():
        if k == 'conditions':
            c = conds(v, ctx)
            if c is not None:
                out['condition'] = c
        elif k == 'condition':
            out['condition'] = cond(v, ctx)
        else:
            out[k] = v
    return out


CONVERTERS = {
    'villager_trade': villager_trade,
    'loot_modifiers': neo_loot_modifier,
    'loot_table': loot_table,
    'advancement': advancement,
    'recipe': recipe,
    'enchantment': enchantment,
    'predicate': predicate_file,
    'item_modifier': item_modifier_file,
}


def data_type(rel):
    """'create/loot_table/blocks/x.json' -> 'loot_table' (path relative to a data/ dir)."""
    parts = rel.replace('\\', '/').split('/')
    if len(parts) < 3:
        return None
    return parts[1]


POT_SIDES = ('back', 'left', 'right', 'front')


def generic(x):
    """Shape-based rewrites that apply wherever the construct appears (any data type)."""
    if isinstance(x, list):
        return [generic(v) for v in x]
    if not isinstance(x, dict):
        return x
    # block state {"Name", "Properties"} -> {"id", "properties"}
    if 'Name' in x and isinstance(x['Name'], str) and set(x) <= {'Name', 'Properties'}:
        out = {'id': x['Name']}
        if x.get('Properties'):
            out['properties'] = x['Properties']
        return out
    # simple_state_provider {"state": S} -> the state itself (BlockStateProvider accepts an inline state)
    if ns(x.get('type')) == 'minecraft:simple_state_provider' and set(x) == {'type', 'state'}:
        return generic(x['state'])
    out = {}
    for k, v in x.items():
        if k == 'tags' and isinstance(v, list):
            # TagPredicate (damage type tags): {"id": "t", "expected": b} -> {"id": "#t", ...}
            v = [tag_pred(t) if isinstance(t, dict) and set(t) <= {'id', 'expected'} else t for t in v]
        elif k == 'minecraft:pot_decorations' and isinstance(v, list):
            # [back, left, right, front] item ids (brick = none) -> {side: {"id": item}}
            dec = {}
            for side, item in zip(POT_SIDES, v):
                if isinstance(item, str) and ns(item) != 'minecraft:brick':
                    dec[side] = {'id': item}
            v = dec
        out[k] = generic(v)
    return out


def convert(typ, obj, ctx):
    fn = CONVERTERS.get(typ)
    if fn is None:
        return obj
    return generic(fn(copy.deepcopy(obj), ctx))


# ----------------------------------------------------------------------------------------------------------------
# comparison helpers


def norm(x):
    if isinstance(x, dict):
        return {k: norm(v) for k, v in x.items()}
    if isinstance(x, list):
        return [norm(v) for v in x]
    if isinstance(x, float) and x.is_integer():
        return int(x)
    return x


def inline_refs(x, refs, depth=0):
    """Replace strings naming a known predicate (refs: id -> json) by the predicate itself."""
    if depth > 20:
        return x
    if isinstance(x, dict):
        return {k: inline_refs(v, refs, depth + 1) for k, v in x.items()}
    if isinstance(x, list):
        return [inline_refs(v, refs, depth + 1) for v in x]
    if isinstance(x, str) and x in refs:
        return inline_refs(refs[x], refs, depth + 1)
    return x


def diff(a, b, path, out):
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a:
                out.append((path + '/' + k, '<missing>', b[k]))
            elif k not in b:
                out.append((path + '/' + k, a[k], '<missing>'))
            else:
                diff(a[k], b[k], path + '/' + k, out)
    elif isinstance(a, list) and isinstance(b, list) and len(a) == len(b):
        for i, (x, y) in enumerate(zip(a, b)):
            diff(x, y, '%s[%d]' % (path, i), out)
    elif a != b:
        out.append((path, a, b))


def read_jar(path):
    """{ 'minecraft/loot_table/...json': obj } for every data json in the jar (incl. built-in datapacks)."""
    out = {}
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if not n.endswith('.json') or not n.startswith('data/'):
                continue
            rel = n[len('data/'):]
            # vanilla feature packs: data/minecraft/datapacks/<pack>/data/<ns>/<type>/...
            if '/datapacks/' in rel:
                pack, _, inner = rel.partition('/datapacks/')[2].partition('/data/')
                rel = inner
                key = 'datapacks/' + pack + ':' + rel
            else:
                key = rel
            try:
                out[key] = json.loads(z.read(n).decode('utf-8'))
            except ValueError:
                pass
    return out


def check(old_jar, new_jar, verbose):
    old = read_jar(old_jar)
    new = read_jar(new_jar)
    refs = {}
    for key, obj in new.items():
        rel = key.split(':', 1)[1] if key.startswith('datapacks/') else key
        parts = rel.split('/')
        if len(parts) > 2 and parts[1] == 'predicate':
            refs[parts[0] + ':' + '/'.join(parts[2:])[:-5]] = obj
    stats = {}
    samples = {}
    for key in sorted(old):
        rel = key.split(':', 1)[1] if key.startswith('datapacks/') else key
        typ = data_type(rel)
        if typ not in CONVERTERS or key not in new:
            continue
        ctx = Ctx(key)
        try:
            got = norm(convert(typ, old[key], ctx))
        except Exception as ex:  # noqa: BLE001 - report and carry on
            got = {'<exception>': repr(ex)}
        want = norm(inline_refs(new[key], refs))
        got = norm(inline_refs(got, refs))
        st = stats.setdefault(typ, [0, 0, 0, 0])
        st[2] += 1
        if norm(old[key]) == want:
            st[1] += 1  # unchanged between versions
        if got == want:
            st[0] += 1
        else:
            d = []
            diff(got, want, '', d)
            samples.setdefault(typ, []).append((key, d))
    # every 26.3 file must come out of the converter unchanged (makes re-running on a converted tree safe)
    stable = {}
    for key in sorted(new):
        rel = key.split(':', 1)[1] if key.startswith('datapacks/') else key
        typ = data_type(rel)
        if typ not in CONVERTERS:
            continue
        st = stable.setdefault(typ, [0, 0, []])
        st[1] += 1
        try:
            again = norm(convert(typ, new[key], Ctx(key)))
        except Exception as ex:  # noqa: BLE001
            again = {'<exception>': repr(ex)}
        if again == norm(new[key]):
            st[0] += 1
        else:
            d = []
            diff(again, norm(new[key]), '', d)
            st[2].append((key, d))
    print('26.2 -> 26.3 vanilla round trip (same path in both jars):')
    print('%-15s %8s %8s %8s %9s' % ('type', 'files', 'match', 'differ', 'unchanged'))
    for typ, (ok, same, total, _) in sorted(stats.items()):
        print('%-15s %8d %8d %8d %9d  %5.1f%%' % (typ, total, ok, total - ok, same, 100.0 * ok / total))
    print('\n26.3 vanilla files left unchanged by the converter (idempotency):')
    for typ, (ok, total, bad) in sorted(stable.items()):
        print('%-15s %8d of %8d' % (typ, ok, total))
        for key, d in bad[:verbose]:
            print('  ' + key)
            for path, a, b in d[:4]:
                print('    %s\n      got:  %s\n      want: %s' % (path, json.dumps(a)[:240], json.dumps(b)[:240]))
    for typ, lst in sorted(samples.items()):
        print('\n== %s: %d mismatches' % (typ, len(lst)))
        for key, d in lst[:verbose]:
            print('  ' + key)
            for path, a, b in d[:6]:
                print('    %s\n      got:  %s\n      want: %s' % (path, json.dumps(a)[:240], json.dumps(b)[:240]))


# ----------------------------------------------------------------------------------------------------------------
# in-place conversion

SKIP_NAMESPACES = {'fabric', 'trinkets'}
KEY_PRIORITY = {'neoforge:conditions': 0, 'fabric:load_conditions': 0, 'type': 1, 'parent': 2}


def datagen_order(x):
    """Key order of Mojang's datagen (and of upstream's files): "type", "parent" first, then alphabetical."""
    if isinstance(x, dict):
        return {k: datagen_order(x[k]) for k in sorted(x, key=lambda k: (KEY_PRIORITY.get(k, 9), k))}
    if isinstance(x, list):
        return [datagen_order(v) for v in x]
    return x


def convert_tree(root, dry_run):
    changed = {}
    seen = {}
    warnings = []
    for dirpath, _, files in os.walk(root):
        for f in sorted(files):
            if not f.endswith('.json'):
                continue
            p = os.path.join(dirpath, f)
            rel = os.path.relpath(p, root).replace(os.sep, '/')
            if rel.split('/')[0] in SKIP_NAMESPACES:
                continue
            typ = data_type(rel)
            if typ not in CONVERTERS:
                continue
            with open(p, 'rb') as fh:
                raw = fh.read()
            text = raw.decode('utf-8')
            obj = json.loads(text)
            ctx = Ctx(rel)
            new = convert(typ, obj, ctx)
            seen[typ] = seen.get(typ, 0) + 1
            for w in ctx.warnings:
                warnings.append('%s: %s' % (rel, w))
            if new == obj:
                continue
            changed[typ] = changed.get(typ, 0) + 1
            if not dry_run:
                nl = '\r\n' if b'\r\n' in raw else '\n'
                out = json.dumps(datagen_order(new), indent=2, ensure_ascii=False).replace('\n', nl)
                if raw.endswith(b'\n'):
                    out += nl
                with open(p, 'wb') as fh:
                    fh.write(out.encode('utf-8'))
    print('%s: %s' % (root, 'would change' if dry_run else 'changed'))
    for typ in sorted(seen):
        print('  %-15s %5d of %5d files' % (typ, changed.get(typ, 0), seen[typ]))
    for w in warnings:
        print('  warning: ' + w)
    return changed


CONDITION_TYPES = {ns(t) for t in (
    'inverted any_of all_of random_chance random_chance_with_enchanted_bonus entity_properties killed_by_player '
    'entity_scores match_block match_tool table_bonus survives_explosion damage_source_properties location_check '
    'weather_check time_check int_value_check float_value_check enchantment_active_check environment_attribute_check '
    'block_state_property reference value_check').split()} | {'neoforge:loot_table_id', 'neoforge:can_item_perform_ability'}
LEGACY_TYPES = {'minecraft:block_state_property', 'minecraft:reference', 'minecraft:value_check', 'minecraft:sum',
                'minecraft:constant'}


def lint_obj(x, path, typ, out):
    """Report constructs that only exist in the 26.2 formats."""
    if isinstance(x, list):
        for i, v in enumerate(x):
            lint_obj(v, '%s[%d]' % (path, i), typ, out)
        return
    if not isinstance(x, dict):
        return
    if isinstance(x.get('condition'), str) and 'type' not in x and ns(x['condition']) in CONDITION_TYPES:
        out.append('%s: "condition" used as a type discriminator' % path)
    if isinstance(x.get('function'), str) and 'type' not in x and typ != 'test_instance':
        out.append('%s: "function" used as a type discriminator' % path)
    if 'functions' in x and ns(x.get('type')) != 'minecraft:sequence':
        out.append('%s: "functions" list (now "modifier")' % path)
    if 'conditions' in x and not (typ == 'advancement' and 'trigger' in x):
        out.append('%s: "conditions" list (now "condition")' % path)
    if isinstance(x.get('type'), str) and ns(x['type']) in LEGACY_TYPES:
        out.append('%s: removed type %s' % (path, x['type']))
    if 'Name' in x and set(x) <= {'Name', 'Properties'}:
        out.append('%s: {"Name", "Properties"} block state' % path)
    if typ == 'advancement' and 'trigger' in x and isinstance(x.get('conditions'), dict):
        for k, v in x['conditions'].items():
            if k in ('player',) + tuple(TRIGGER_COND_FIELDS.get(ns(x['trigger']), ())) and isinstance(v, list):
                out.append('%s/conditions/%s: list of conditions (now one condition)' % (path, k))
        for old, new in TRIGGER_RENAMES.get(ns(x['trigger']), ()):
            if old in x['conditions']:
                out.append('%s/conditions/%s: renamed to %s' % (path, old, new))
    if typ == 'loot_table' and 'rolls' in x and isinstance(x['rolls'], float) and not x['rolls'].is_integer():
        out.append('%s/rolls: non-integer rolls' % path)
    for k, v in x.items():
        lint_obj(v, path + '/' + k, typ, out)


def lint_tree(root):
    problems = 0
    for dirpath, _, files in os.walk(root):
        for f in sorted(files):
            if not f.endswith('.json'):
                continue
            p = os.path.join(dirpath, f)
            rel = os.path.relpath(p, root).replace(os.sep, '/')
            if rel.split('/')[0] in SKIP_NAMESPACES:
                continue
            typ = data_type(rel)
            try:
                with open(p, encoding='utf-8') as fh:
                    obj = json.load(fh)
            except ValueError as ex:
                print('%s: invalid JSON: %s' % (rel, ex))
                problems += 1
                continue
            if typ not in CONVERTERS:
                continue
            out = []
            lint_obj(obj, '', typ, out)
            for o in out:
                print('%s: %s' % (rel, o))
            problems += len(out)
    print('%s: %d legacy constructs found' % (root, problems))
    return problems


# ----------------------------------------------------------------------------------------------------------------
# resource-pack assets (assets/<ns>/models, assets/<ns>/atlases)
#
# Asset files are often hand-written (Blockbench) with their own layout, so they are edited textually, never
# re-serialised.


def scan_scalars(text):
    """Yield (path, key_start, value_end, raw_value) for every scalar member of every JSON object in text.

    path is the list of keys / list indices leading to the member (its own key last)."""
    i, n = 0, len(text)
    stack = []  # frames: [kind, key_or_index, key_start]
    expect_key = False
    while i < n:
        ch = text[i]
        if ch in ' \t\r\n,:':
            if ch == ',' and stack:
                if stack[-1][0] == 'arr':
                    stack[-1][1] += 1
                else:
                    expect_key = True
            i += 1
            continue
        if ch == '{':
            stack.append(['obj', None, None])
            expect_key = True
            i += 1
            continue
        if ch == '[':
            stack.append(['arr', 0, None])
            i += 1
            continue
        if ch in '}]':
            stack.pop()
            i += 1
            continue
        start = i
        if ch == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == '\\' else 1
            i += 1
            token = text[start:i]
            if stack and stack[-1][0] == 'obj' and expect_key:
                stack[-1][1] = json.loads(token)
                stack[-1][2] = start
                expect_key = False
                continue
        else:
            while i < n and text[i] not in ' \t\r\n,:]}':
                i += 1
            token = text[start:i]
        if stack and stack[-1][0] == 'obj':
            yield [f[1] for f in stack], stack[-1][2], i, token


def fix_model_text(text):
    """Element-level "shade": false -> "shade_direction_override": "up" (26.3 removed "shade")."""
    edits = []
    for path, ks, ve, raw in scan_scalars(text):
        if len(path) >= 3 and path[-1] == 'shade' and path[-3] == 'elements' and isinstance(path[-2], int):
            if raw == 'false':
                edits.append((ks, ve, '"shade_direction_override": "up"'))
            # "shade": true was the default; 26.3 ignores the unknown key, which means the same
    for ks, ve, rep in reversed(edits):
        text = text[:ks] + rep + text[ve:]
    return text, len(edits)


def palette_id(v):
    """26.2 trim palette ids (textures/trims/color_palettes/*) -> 26.3 palette ids (textures/palettes/*)."""
    nsp, _, path = v.rpartition(':')
    if path == 'trims/color_palettes/trim_palette':
        path = 'trim_base'
    elif path.startswith('trims/color_palettes/'):
        path = 'trim/' + path[len('trims/color_palettes/'):]
    return (nsp + ':' if nsp else '') + path


def fix_atlas_text(text):
    """paletted_permutations: palette_key / permutations now name textures/palettes/* images."""
    obj = json.loads(text)
    olds = set()
    for src in obj.get('sources', []):
        if isinstance(src, dict) and ns(src.get('type')) == 'minecraft:paletted_permutations':
            if isinstance(src.get('palette_key'), str):
                olds.add(src['palette_key'])
            for v in (src.get('permutations') or {}).values():
                if isinstance(v, str):
                    olds.add(v)
    count = 0
    for old in sorted(olds):
        new = palette_id(old)
        if new != old:
            count += text.count(json.dumps(old))
            text = text.replace(json.dumps(old), json.dumps(new))
    return text, count


def convert_assets_tree(root, dry_run, lint_only=False):
    changed = {}
    seen = {}
    problems = 0
    for dirpath, _, files in os.walk(root):
        for f in sorted(files):
            if not f.endswith('.json'):
                continue
            p = os.path.join(dirpath, f)
            rel = os.path.relpath(p, root).replace(os.sep, '/')
            parts = rel.split('/')
            if len(parts) < 3 or parts[0] in SKIP_NAMESPACES or parts[1] not in ('models', 'atlases'):
                continue
            with open(p, 'rb') as fh:
                raw = fh.read()
            text = raw.decode('utf-8-sig')
            try:
                json.loads(text)
            except ValueError as ex:
                print('  warning: %s: not strict JSON (%s), skipped' % (rel, ex))
                continue
            kind = parts[1]
            seen[kind] = seen.get(kind, 0) + 1
            new, count = (fix_model_text if kind == 'models' else fix_atlas_text)(text)
            if not count:
                continue
            if lint_only:
                print('%s: %d 26.2 construct(s) (%s)' % (rel, count, 'element "shade"' if kind == 'models' else 'trim palette path'))
                problems += count
                continue
            changed[kind] = changed.get(kind, 0) + 1
            if not dry_run:
                bom = b'\xef\xbb\xbf' if raw.startswith(b'\xef\xbb\xbf') else b''
                with open(p, 'wb') as fh:
                    fh.write(bom + new.encode('utf-8'))
    if lint_only:
        print('%s: %d legacy constructs found' % (root, problems))
        return problems
    print('%s: %s' % (root, 'would change' if dry_run else 'changed'))
    for kind in sorted(seen):
        print('  %-15s %5d of %5d files' % (kind, changed.get(kind, 0), seen[kind]))
    return 0


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('dirs', nargs='*')
    ap.add_argument('--check', nargs=2, metavar=('OLD_JAR', 'NEW_JAR'))
    ap.add_argument('-v', '--verbose', type=int, default=5, help='mismatches to print per type (--check)')
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--lint', action='store_true', help='only report leftover 26.2 constructs in DIR')
    args = ap.parse_args(argv)
    if args.check:
        check(args.check[0], args.check[1], args.verbose)
        return 0
    here = os.path.dirname(os.path.abspath(__file__))
    dirs = args.dirs or [os.path.join(here, '..', 'src', 'main', 'resources', 'data'),
                         os.path.join(here, '..', 'src', 'client', 'resources', 'assets')]
    bad = 0
    for d in dirs:
        d = os.path.normpath(d)
        if os.path.basename(d) == 'assets':
            if not args.lint:
                convert_assets_tree(d, args.dry_run)
            if args.lint or not args.dry_run:
                bad += convert_assets_tree(d, True, lint_only=True)
            continue
        if not args.lint:
            convert_tree(d, args.dry_run)
        if args.lint or not args.dry_run:
            bad += lint_tree(d)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
