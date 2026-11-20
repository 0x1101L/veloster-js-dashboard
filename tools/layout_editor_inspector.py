"""Describe the editable properties of a role without writing defaults into its JSON."""
import json

from layout_editor_runtime import MISSING, _lookup
from layout_schema_validation import SCHEMA_BY_ROLE, _resolve_pointer


def describe(model, root):
    directory = root / "theme/schema"
    schema_path = directory / SCHEMA_BY_ROLE[model.role]
    cache = {}

    def read(path):
        if path not in cache:
            cache[path] = json.loads(path.read_text(encoding="utf-8"))
        return cache[path]

    def resolve(schema, file):
        while "$ref" in schema:
            name, _, pointer = schema["$ref"].partition("#")
            file = directory / name if name else file
            schema = _resolve_pointer(read(file), pointer)
        return schema, file

    def node(schema, file, path, stored=MISSING):
        schema, file = resolve(schema, file)
        actual = _lookup(model.effective, path)
        if "oneOf" in schema:
            choices = [resolve(option, file) for option in schema["oneOf"]]
            # A scalar frame padding is exposed as the two axes actually used by the renderer.
            sample = actual if actual is not MISSING else stored
            for option, target in choices:
                if ((isinstance(sample, dict) and option.get("type") == "object")
                        or (isinstance(sample, list) and option.get("type") == "array")
                        or (type(sample) is int and option.get("type") in ("integer", "number"))
                        or (isinstance(sample, str) and option.get("type") == "string")):
                    schema, file = option, target
                    break
        props = schema.get("properties", {})
        owner = model.owner(path) if path else None
        if not owner and path[:3] == ("shell", "frame", "padding") and len(path) == 4:
            if isinstance(_lookup(model.data, path[:3]), int):
                owner = model.owner(path[:3])
        if not owner and path[:1] == ("shell",) and len(path) == 4 and path[2] == "padding":
            alias = "vertical" if path[-1] in ("top", "bottom") else "horizontal"
            if path[-1] in ("top", "bottom", "left", "right") and path[1] != "sideMenuBar":
                owner = model.owner((*path[:-1], alias))
        local = _lookup(model.local, path) is not MISSING
        value = stored if stored is not MISSING else actual
        kind = schema.get("type", "")
        if not kind and isinstance(value, dict):
            kind = "object"
        if not kind and isinstance(value, list):
            kind = "array"
        result = {"path": path, "kind": kind, "present": value is not MISSING,
                  "local": local, "stored": stored is not MISSING,
                  "owner": owner, "children": []}
        if value is not MISSING and kind not in ("object", "array"):
            result["value"] = value
        result["resettable"] = local and not any(isinstance(part, int) for part in path)
        if path[:2] == ("shell", "frame") and path[2:3] in (("padding",), ("gap",)) and len(path) == 4:
            result["resettable"] &= _lookup(model._merged({}), path) is not MISSING
        if "const" in schema:
            result["fixed"] = True
        for key in ("enum", "minimum", "maximum", "description"):
            if key in schema:
                result[key] = schema[key]
        if model.theme_id == "original" and (
                (model.role == "overview" and path[:1] in (("cardDefaults",), ("cardTypes",)))
                or (model.role == "shell" and path[:2] in (("shell", "sideMenuBar"), ("shell", "navigationPosition")))):
            result["fixed"] = True
        if kind == "object":
            entries = stored if isinstance(stored, dict) else {}
            keys = (*props, *(key for key in entries if key not in props))
            for key in keys:
                child_path = (*path, key)
                # These keys are accepted by the parser but do not control the production view.
                if child_path in (("shell", "topMenuBar", "buttonHeight"),
                                  ("shell", "sideMenuBar", "presentation"),
                                  ("shell", "bottomMediaBar", "itemGap")):
                    continue
                child_schema = props.get(key, schema.get("additionalProperties", {}))
                result["children"].append(node(child_schema if isinstance(child_schema, dict) else {},
                                                file, child_path, entries.get(key, MISSING)))
        elif kind == "array" and isinstance(stored, list):
            for index, item in enumerate(stored):
                result["children"].append(node(schema.get("items", {}), file, (*path, index), item))
        if model.role == "overview" and len(path) == 4 and path[0] == "cardTypes" and path[2] == "padding" and not owner:
            if _lookup(model.data, ("cardDefaults",)) is not MISSING or _lookup(model.data, ("cardTypes",)) is not MISSING:
                result["defaultLabel"] = "공통 카드 패딩"
        return result

    return node(read(schema_path), schema_path, (), model.data)
