"""Validate theme layout JSON against the repository's Draft 2020-12 schemas."""
import json
import re
from pathlib import Path


SCHEMA_BY_ROLE = {
    "colors": "colors.schema.json",
    "fonts": "fonts.schema.json",
    "components": "components.schema.json",
    "shell": "shell.schema.json",
    "f1": "f1.schema.json",
    "overview": "overview.schema.json",
    "gauges": "gauges.schema.json",
    "graphs": "graphs.schema.json",
    "performance": "performance.schema.json",
    "settings": "settings.schema.json",
    "diagnostics": "diagnostics.schema.json",
}
VALIDATION_KEYWORDS = {
    "$ref", "type", "const", "enum", "minimum", "maximum", "minLength", "maxLength",
    "pattern", "required", "propertyNames", "properties", "additionalProperties",
    "minItems", "maxItems", "items", "not", "oneOf",
}
ANNOTATION_KEYWORDS = {"$schema", "$defs", "description", "default", "deprecated"}


class SchemaValidationError(ValueError):
    pass


def _assert_supported_keywords(schema, path):
    if not isinstance(schema, dict):
        raise SchemaValidationError(f"{path}: schema node must be an object")
    unsupported = set(schema) - VALIDATION_KEYWORDS - ANNOTATION_KEYWORDS
    if unsupported:
        raise SchemaValidationError(f"{path}: unsupported schema keywords {sorted(unsupported)!r}")
    for keyword in ("properties", "$defs"):
        for name, child in schema.get(keyword, {}).items():
            _assert_supported_keywords(child, f"{path}/{keyword}/{name}")
    for keyword in ("items", "additionalProperties", "propertyNames", "not"):
        child = schema.get(keyword)
        if isinstance(child, dict):
            _assert_supported_keywords(child, f"{path}/{keyword}")
    for index, child in enumerate(schema.get("oneOf", ())):
        _assert_supported_keywords(child, f"{path}/oneOf/{index}")


def _resolve_pointer(document, pointer):
    value = document
    for token in pointer.lstrip("/").split("/") if pointer else ():
        token = token.replace("~1", "/").replace("~0", "~")
        value = value[int(token)] if isinstance(value, list) else value[token]
    return value


def validate(document, schema_path):
    """Raise SchemaValidationError when document violates its schema."""
    schema_path = Path(schema_path).resolve()

    def check(value, schema, path, active=()):
        if not isinstance(schema, dict):
            raise SchemaValidationError(f"{path}: schema node must be an object")
        ref = schema.get("$ref")
        if ref:
            file_name, separator, pointer = ref.partition("#")
            target_path = (schema_path.parent / file_name).resolve() if file_name else schema_path
            try:
                target_schema = json.loads(target_path.read_text(encoding="utf-8"))
                target_schema = _resolve_pointer(target_schema, pointer[1:] if pointer.startswith("/") else pointer)
            except (OSError, json.JSONDecodeError, KeyError, IndexError, ValueError) as error:
                raise SchemaValidationError(f"{path}: schema reference {ref!r} cannot be resolved: {error}") from error
            marker = (str(target_path), ref)
            if marker in active:
                raise SchemaValidationError(f"{path}: recursive schema reference {ref!r}")
            check(value, target_schema, path, active + (marker,))

        expected = schema.get("type")
        if expected:
            types = expected if isinstance(expected, list) else [expected]
            matches = {
                "object": lambda v: isinstance(v, dict),
                "array": lambda v: isinstance(v, list),
                "string": lambda v: isinstance(v, str),
                "boolean": lambda v: isinstance(v, bool),
                "integer": lambda v: isinstance(v, int) and not isinstance(v, bool),
                "number": lambda v: isinstance(v, (int, float)) and not isinstance(v, bool),
                "null": lambda v: v is None,
            }
            if not any(matches[kind](value) for kind in types):
                raise SchemaValidationError(f"{path}: expected {' or '.join(types)}")

        if "const" in schema and value != schema["const"]:
            raise SchemaValidationError(f"{path}: must equal {schema['const']!r}")
        if "enum" in schema and value not in schema["enum"]:
            raise SchemaValidationError(f"{path}: must be one of {schema['enum']!r}")
        for keyword, comparator in (("minimum", lambda a, b: a >= b), ("maximum", lambda a, b: a <= b)):
            if keyword in schema and isinstance(value, (int, float)) and not isinstance(value, bool):
                if not comparator(value, schema[keyword]):
                    raise SchemaValidationError(f"{path}: {keyword} is {schema[keyword]}")
        if isinstance(value, str):
            if len(value) < schema.get("minLength", 0):
                raise SchemaValidationError(f"{path}: string is shorter than minLength")
            if len(value) > schema.get("maxLength", float("inf")):
                raise SchemaValidationError(f"{path}: string exceeds maxLength")
            pattern = schema.get("pattern")
            if pattern and re.search(pattern, value) is None:
                raise SchemaValidationError(f"{path}: string does not match {pattern!r}")

        if isinstance(value, dict):
            missing = [key for key in schema.get("required", ()) if key not in value]
            if missing:
                raise SchemaValidationError(f"{path}: missing required properties {missing!r}")
            property_names = schema.get("propertyNames")
            if property_names:
                for key in value:
                    check(key, property_names, f"{path}.<key>", active)
            properties = schema.get("properties", {})
            for key, item in value.items():
                if key in properties:
                    check(item, properties[key], f"{path}.{key}", active)
                elif schema.get("additionalProperties") is False:
                    raise SchemaValidationError(f"{path}.{key}: additional property is not allowed")
                elif isinstance(schema.get("additionalProperties"), dict):
                    check(item, schema["additionalProperties"], f"{path}.{key}", active)
        if isinstance(value, list):
            if len(value) < schema.get("minItems", 0):
                raise SchemaValidationError(f"{path}: fewer than minItems")
            if len(value) > schema.get("maxItems", float("inf")):
                raise SchemaValidationError(f"{path}: more than maxItems")
            items = schema.get("items")
            if isinstance(items, dict):
                for index, item in enumerate(value):
                    check(item, items, f"{path}[{index}]", active)
        if "not" in schema:
            try:
                check(value, schema["not"], path, active)
            except SchemaValidationError:
                pass
            else:
                raise SchemaValidationError(f"{path}: matches a forbidden schema")
        if "oneOf" in schema:
            matches = 0
            for option in schema["oneOf"]:
                try:
                    check(value, option, path, active)
                    matches += 1
                except SchemaValidationError:
                    pass
            if matches != 1:
                raise SchemaValidationError(f"{path}: must match exactly one schema alternative")

    root_schema = json.loads(schema_path.read_text(encoding="utf-8"))
    _assert_supported_keywords(root_schema, str(schema_path))
    check(document, root_schema, "$", ())


def validate_role(document, role, repository_root):
    try:
        schema_name = SCHEMA_BY_ROLE[role]
    except KeyError as error:
        raise SchemaValidationError(f"unsupported layout role: {role}") from error
    validate(document, Path(repository_root) / "theme/schema" / schema_name)
