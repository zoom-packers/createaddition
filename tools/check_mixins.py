"""Static check of Crafts & Additions' mixins (copied from ZP5's Create Fly port, tools/check_mixins.py) against a Minecraft/NeoForge jar.

For every mixin listed in a mixin config (and every mixin a config plugin can add), checks that
  - the target classes exist,
  - each injector's `method = "..."` exists in a target (by name, and by descriptor when one is given),
  - each INVOKE / INVOKE_ASSIGN / FIELD / NEW `@At` target is referenced from inside that method's bytecode,
  - @Shadow / @Accessor / @Invoker members exist.
It does not evaluate ordinals, slices or local captures, so a pass here is necessary, not sufficient.

    python tools/check_mixins.py <minecraft jar> [<more jars>...]
"""
import pathlib
import re
import struct
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONFIGS = [(ROOT / 'src/main/resources/createaddition.mixins.json', ROOT / 'src/main/java')]

# opcode -> operand byte length (excluding switches/wide); cp-referencing opcodes noted separately
OPLEN = {**{i: 0 for i in range(0, 256)}}
for op in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc):
    OPLEN[op] = 1
for op in (0x11, 0x13, 0x14, 0x84, *range(0x99, 0xa9), 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7):
    OPLEN[op] = 2
OPLEN[0x84] = 2
OPLEN[0xc5] = 3
for op in (0xb9, 0xba, 0xc8, 0xc9):
    OPLEN[op] = 4
CP_OPS = {0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xbb}


def parse_class(data):
    count = struct.unpack_from('>H', data, 8)[0]
    cp = [None] * count
    pos, i = 10, 1
    while i < count:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack_from('>H', data, pos + 1)[0]
            cp[i] = ('utf', data[pos + 3:pos + 3 + ln].decode('utf-8', 'replace'))
            pos += 3 + ln
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack_from('>H', data, pos + 1)[0])
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag,) + struct.unpack_from('>HH', data, pos + 1)
            pos += 5
        elif tag == 15:
            pos += 4
        i += 1

    def utf(k):
        return cp[k][1]

    def ref(k):
        e = cp[k]
        if e[0] in (9, 10, 11):
            nt = cp[e[2]]
            return (utf(cp[e[1]][1]), utf(nt[1]), utf(nt[2]))
        if e[0] == 7:
            return (utf(e[1]), None, None)
        return None

    _, this, sup, nif = struct.unpack_from('>HHHH', data, pos)
    pos += 8
    ifs = [utf(cp[struct.unpack_from('>H', data, pos + 2 * k)[0]][1]) for k in range(nif)]
    pos += 2 * nif
    fields, methods = {}, {}
    for member in range(2):
        n = struct.unpack_from('>H', data, pos)[0]
        pos += 2
        for _ in range(n):
            acc, ni, di, na = struct.unpack_from('>HHHH', data, pos)
            pos += 8
            refs = set()
            for _ in range(na):
                an, alen = struct.unpack_from('>HI', data, pos)
                if member == 1 and utf(an) == 'Code':
                    code_len = struct.unpack_from('>I', data, pos + 10)[0]
                    code = data[pos + 14:pos + 14 + code_len]
                    pc = 0
                    while pc < len(code):
                        op = code[pc]
                        if op in CP_OPS or op == 0xba:
                            r = ref(struct.unpack_from('>H', code, pc + 1)[0])
                            if r:
                                refs.add(r)
                        if op == 0xaa:  # tableswitch
                            p = (pc + 4) & ~3
                            lo, hi = struct.unpack_from('>ii', code, p + 4)
                            pc = p + 12 + 4 * (hi - lo + 1)
                            continue
                        if op == 0xab:  # lookupswitch
                            p = (pc + 4) & ~3
                            npairs = struct.unpack_from('>i', code, p + 4)[0]
                            pc = p + 8 + 8 * npairs
                            continue
                        if op == 0xc4:  # wide
                            pc += 6 if code[pc + 1] == 0x84 else 4
                            continue
                        pc += 1 + OPLEN.get(op, 0)
                pos += 6 + alen
            (fields if member == 0 else methods).setdefault(utf(ni), []).append((utf(di), refs))
    return utf(cp[this][1]), (utf(cp[sup][1]) if sup else None), ifs, fields, methods


classes = {}


def load(data):
    try:
        name, *rest = parse_class(data)
        classes.setdefault(name, rest)
    except Exception:
        pass


for jar in sys.argv[1:]:
    if pathlib.Path(jar).is_dir():
        for f in pathlib.Path(jar).rglob('*.class'):
            load(f.read_bytes())
        continue
    with zipfile.ZipFile(jar) as zf:
        for n in zf.namelist():
            if n.endswith('.class') and not n.startswith('META-INF/'):
                load(zf.read(n))
# Mixins excluded from the build (Fabric-only, other recipe viewers) are skipped
built = {p.stem for d in ('build/classes/java/main', 'build/classes/java/client') for p in (ROOT / d).rglob('*.class')}


def resolve(name, imports, wildcards):
    """Simple or dotted class name as written in source -> internal name"""
    first, _, rest = name.partition('.')
    bases = [imports[first]] if first in imports else [w + '.' + first for w in wildcards]
    for base in bases:
        full = base + ('.' + rest if rest else '')
        parts = full.split('.')
        for i in range(len(parts), 0, -1):
            cand = '/'.join(parts[:i]) + ''.join('$' + p for p in parts[i:])
            if cand in classes:
                return cand
    return name.replace('.', '/')


def hierarchy(c):
    out, todo = [], [c]
    while todo:
        x = todo.pop(0)
        if x in classes and x not in out:
            out.append(x)
            sup, ifs, _, _ = classes[x]
            todo += ([sup] if sup else []) + ifs
    return out


def find_methods(cls, spec, inherited=False):
    """spec is name or name(desc)"""
    m = re.match(r'([^(]+)(\(.*)?$', spec)
    name, desc = m.group(1), m.group(2)
    if name.startswith('L') and ';' in name:  # Lowner;name(desc)
        owner, _, name = name[1:].partition(';')
        cls = owner
    out = []
    for c in (hierarchy(cls) if inherited else [cls]):
        for d, refs in classes.get(c, [None, None, None, {}])[3].get(name, []):
            if desc is None or d == desc:
                out.append((c, d, refs))
    return out


def parse_target(t):
    if not t.startswith('L') and '.' in t.split('(')[0]:
        # owner.name(desc) form
        owner, _, rest = t.partition('(')
        owner, _, name = owner.rpartition('.')
        t = f'L{owner};{name}(' + rest
    m = re.match(r'L([^;]+);([^(:]+)(\(.*|:.*)?$', t)
    if not m:
        return None
    return m.group(1), m.group(2), (m.group(3) or '').lstrip(':') or None


def java_files(base):
    return {p.stem: p for p in base.rglob('*.java')}


report = []
for config, src in CONFIGS:
    text = config.read_text(encoding='utf-8')
    package = re.search(r'"package"\s*:\s*"([^"]+)"', text).group(1)
    names = re.findall(r'"([A-Z]\w*)"', text)
    plugin = re.search(r'"plugin"\s*:\s*"([^"]+)"', text)
    if plugin:
        pf = src / (plugin.group(1).replace('.', '/') + '.java')
        ptext = re.sub(r'if \(false\) \{.*?\} else \{', '', pf.read_text(encoding='utf-8'), flags=re.S)
        names += re.findall(r'mixins\.add\("(\w+)"\)', ptext)
    for mixin in sorted(set(names) - {'JAVA_25'}):
        if mixin not in built:
            continue
        path = src / package.replace('.', '/') / f'{mixin}.java'
        if not path.exists():
            report.append(f'{mixin}: source missing (excluded from the build?)')
            continue
        code = path.read_text(encoding='utf-8')
        imports = dict((m.group(2), m.group(1)) for m in re.finditer(r'^import ([\w.]+\.(\w+));', code, re.M))
        wildcards = re.findall(r'^import ([\w.]+)\.\*;', code, re.M) + [package]
        mm = re.search(r'@Mixin\((?:value\s*=\s*)?\{?([^)}]*)\}?', code)
        targets = []
        for t in re.findall(r'([\w.$]+)\.class', mm.group(1)):
            targets.append(resolve(t, imports, wildcards))
        targets += [s.replace('.', '/') for s in re.findall(r'targets\s*=\s*\{?\s*"([^"]+)"', mm.group(0))]
        problems = []
        for t in targets:
            if t not in classes:
                problems.append(f'target class {t} not found')
        live = [t for t in targets if t in classes]
        if not live:
            report.append(f'{mixin}: ' + '; '.join(problems or ['no target']))
            continue
        # injector annotations: grab balanced annotation text
        for am in re.finditer(r'@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|WrapOperation|WrapWithCondition|ModifyExpressionValue|ModifyReturnValue|ModifyReceiver|WrapMethod|Definition)\s*\(', code):
            depth, i = 1, am.end()
            while depth and i < len(code):
                depth += {'(': 1, ')': -1}.get(code[i], 0)
                i += 1
            ann = code[am.start():i]
            methods_spec = re.findall(r'method\s*=\s*\{?\s*((?:"[^"]*"\s*,?\s*)+)', ann)
            specs = re.findall(r'"([^"]+)"', methods_spec[0]) if methods_spec else []
            found = []
            for spec in specs:
                hits = [h for t in live for h in find_methods(t, spec)]
                if not hits:
                    problems.append(f'@{am.group(1)} method {spec} not found')
                elif '(' not in spec and len({d for _, d, _ in hits}) > 1:
                    # NeoForge often adds an overload and turns the vanilla one into a delegate; a name-only
                    # spec then matches both, and the handler signature fits at most one of them
                    problems.append(f'@{am.group(1)} method {spec} matches {len(hits)} overloads: ' + ', '.join(sorted(d for _, d, _ in hits)))
                found += hits
            for at in re.finditer(r'@At\(([^@]*?)\)(?=\s*[,)}])', ann):
                body = at.group(1)
                v = re.search(r'value\s*=\s*"(\w+)"', body) or re.match(r'\s*"(\w+)"', body)
                tgt = re.search(r'target\s*=\s*"([^"]+)"', body)
                if not v or not tgt or v.group(1) not in ('INVOKE', 'INVOKE_ASSIGN', 'FIELD', 'NEW'):
                    continue
                t = tgt.group(1)
                if v.group(1) == 'NEW':
                    # NEW targets are either the class or a constructor descriptor returning it
                    owner = t.rsplit(')', 1)[1] if t.startswith('(') else t
                    owner = owner[1:-1] if owner.startswith('L') and owner.endswith(';') else owner
                    ok = any((owner, None, None) in refs or any(r[0] == owner and r[1] == '<init>' for r in refs) for _, _, refs in found)
                else:
                    pt = parse_target(t)
                    if not pt:
                        continue
                    ok = any(any(r[0] == pt[0] and r[1] == pt[1] and (pt[2] is None or r[2] == pt[2]) for r in refs)
                             or any(r[1] == pt[1] and (pt[2] is None or r[2] == pt[2]) for r in refs if r[0] != pt[0] and pt[0] in hierarchy(r[0]))
                             for _, _, refs in found)
                if found and not ok:
                    problems.append(f'@{am.group(1)} {specs[0] if specs else "?"}: @At({v.group(1)}) target {t} not in method')
        for sm in re.finditer(r'@Shadow[^;{]*?\s(?:abstract\s+)?(?:static\s+)?(?:final\s+)?[\w<>\[\]?,. ]+?\s+(\w+)\s*([(;=])', code):
            name = sm.group(1)
            is_method = sm.group(2) == '('
            ok = False
            for t in live:
                for c in hierarchy(t):
                    _, _, fields, methods = classes[c]
                    if (methods if is_method else fields).get(name):
                        ok = True
            if not ok:
                problems.append(f'@Shadow {"method" if is_method else "field"} {name} not found')
        for kind, am in [(k, m) for k in ('Accessor', 'Invoker') for m in re.finditer(r'@' + k + r'(\("([^"]+)"\))?[^;{]*?\s(\w+)\s*\(', code)]:
            name = am.group(2)
            if not name:
                meth = am.group(3)
                name = re.sub(r'^(get|set|is|invoke|call|create)', '', meth)
                name = name[:1].lower() + name[1:]
            ok = any((classes[c][3] if kind == 'Invoker' else classes[c][2]).get(name) for t in live for c in hierarchy(t))
            if kind == 'Invoker' and name == 'new' or name == '<init>':
                ok = True
            if not ok:
                problems.append(f'@{kind} {name} not found')
        if problems:
            report.append(f'{mixin}: ' + '; '.join(dict.fromkeys(problems)))
print('\n'.join(report))
print(f'{len(report)} mixins with problems')
