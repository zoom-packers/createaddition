"""Rewrites Fabric resource conditions in data JSON to NeoForge conditions, in place.

    python tools/fabric_conditions_to_neoforge.py src/main/resources/data

fabric:load_conditions          -> neoforge:conditions
fabric:all_mods_loaded [a, b]   -> neoforge:mod_loaded a, neoforge:mod_loaded b
fabric:any_mods_loaded [a, b]   -> neoforge:or [mod_loaded a, mod_loaded b]
fabric:tags_populated [t]       -> neoforge:not neoforge:tag_empty t (same registry)
fabric:not / fabric:and / fabric:or are mapped recursively. Re-run after rebasing on upstream.
"""
import json
import os
import sys


def convert(cond):
    kind = cond["condition"]
    if kind == "fabric:all_mods_loaded":
        return [{"type": "neoforge:mod_loaded", "modid": m} for m in cond["values"]]
    if kind == "fabric:any_mods_loaded":
        return [{"type": "neoforge:or", "values": [{"type": "neoforge:mod_loaded", "modid": m} for m in cond["values"]]}]
    if kind == "fabric:tags_populated":
        registry = cond.get("registry", "minecraft:item")
        out = []
        for tag in cond["values"]:
            empty = {"type": "neoforge:tag_empty", "tag": tag.lstrip("#")}
            if registry != "minecraft:item":
                empty["registry"] = registry
            out.append({"type": "neoforge:not", "value": empty})
        return out
    if kind == "fabric:not":
        inner = convert(cond["value"])
        value = inner[0] if len(inner) == 1 else {"type": "neoforge:and", "values": inner}
        return [{"type": "neoforge:not", "value": value}]
    if kind in ("fabric:and", "fabric:or"):
        values = [c for v in cond["values"] for c in convert(v)]
        return [{"type": "neoforge:" + kind.split(":")[1], "values": values}]
    raise ValueError("unknown Fabric condition " + kind)


def main(root):
    changed = 0
    for dirpath, _, files in os.walk(root):
        for name in files:
            if not name.endswith(".json"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            if "fabric:load_conditions" not in text:
                continue
            data = json.loads(text)
            conditions = [c for cond in data.pop("fabric:load_conditions") for c in convert(cond)]
            data["neoforge:conditions"] = conditions
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            changed += 1
    print(f"converted {changed} files")


if __name__ == "__main__":
    main(sys.argv[1])
