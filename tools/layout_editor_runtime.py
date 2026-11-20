"""Desktop theme-layout editor using the copied production preview renderer."""
import copy
import json
import os
import re
import shutil
import struct
import tempfile
from pathlib import Path, PurePosixPath

from layout_editor_preview import LANGUAGES, PAGES, ROOT, STATES, VEHICLES, Renderer, vehicle_pages
from layout_schema_validation import SchemaValidationError, validate_role

MISSING = object()


def _lookup(value, path, default=MISSING):
    try:
        for part in path:
            value = value[part]
        return value
    except (KeyError, IndexError, TypeError):
        return default


def _put(document, path, value):
    cursor = document
    for key in path[:-1]:
        cursor = cursor.setdefault(key, {})
    cursor[path[-1]] = copy.deepcopy(value)


def _remove(document, path):
    key = path[0]
    if len(path) == 1:
        document.pop(key, None)
    elif isinstance(document.get(key), dict):
        _remove(document[key], path[1:])
        if not document[key]:
            del document[key]


def _changes(before, after):
    """Edit object leaves independently; inherited arrays are replaced as a unit."""
    for key in dict.fromkeys((*before, *after)):
        old, new = before.get(key, MISSING), after.get(key, MISSING)
        if old == new:
            continue
        if isinstance(old, dict) and isinstance(new, dict):
            for path, value in _changes(old, new):
                yield (key, *path), value
        else:
            yield (key,), new


ROLES = ("colors", "fonts", "components", "shell", "f1", "overview", "gauges", "graphs", "performance", "settings", "diagnostics")
ROLE_LABELS = {
    "colors": "색상", "fonts": "글꼴", "components": "컴포넌트",
    "shell": "화면 쉘", "f1": "F1", "overview": "전체 조망", "gauges": "게이지",
    "graphs": "그래프", "performance": "퍼포먼스", "settings": "설정", "diagnostics": "진단",
}
ROLE_PAGES = {"colors": 0, "fonts": 0, "components": 0, "overview": 0, "f1": 7, "gauges": 8, "graphs": 9,
              "performance": 6, "settings": 10, "diagnostics": 1, "shell": 7}
THEMES_ROOT = Path("themes")


def _safe_json_path(value):
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or not re.fullmatch(
            r"(?:layouts/)?[a-z][a-z0-9_]{0,47}\.json", value):
        raise ValueError(f"안전하지 않은 테마 파일 경로: {value}")
    return Path(*path.parts)


def _atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + f".{os.getpid()}.tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def _deep_merge(parent, child):
    """Match ThemeCatalog's object-recursive, array-replacement inheritance."""
    merged = copy.deepcopy(parent)
    for key, value in child.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = _deep_merge(merged[key], value)
        else:
            merged[key] = copy.deepcopy(value)
    return merged


class LayoutEditorModel:
    """Edits an isolated asset copy; disk files change only after save()."""

    def __init__(self, source_assets, working_assets):
        self.source_assets = Path(source_assets).resolve()
        self.working_assets = Path(working_assets).resolve()
        if not self.working_assets.exists():
            shutil.copytree(self.source_assets, self.working_assets)
        self.theme_id = None
        self.role = None
        self.data = {}
        self.target_rel = None
        self.manifest_rel = None
        self.source_label = ""
        self._baseline = {}
        self.local = {}
        self.parents = []
        self.inspect = None
        self.effective = {}
        self.pending_roles = {}
        self.source_versions = {path.relative_to(self.working_assets): path.read_bytes()
                                for path in (self.working_assets / THEMES_ROOT).rglob("*.json")}

    def themes(self):
        catalog = json.loads((self.working_assets / "themes/catalog.json").read_text(encoding="utf-8"))
        return tuple(catalog["themeIds"])

    def _manifest(self, theme_id):
        if not re.fullmatch(r"[a-z][a-z0-9_]{0,47}", theme_id):
            raise ValueError("잘못된 테마 ID입니다")
        relative = THEMES_ROOT / theme_id / "manifest.json"
        value = json.loads((self.working_assets / relative).read_text(encoding="utf-8"))
        if value.get("schemaVersion") != 3 or value.get("id") != theme_id:
            raise ValueError(f"테마 manifest 규격이 올바르지 않습니다: {theme_id}")
        return relative, value

    def _resolve(self, theme_id, role):
        cursor, seen, chain = theme_id, set(), []
        first_manifest_rel, first_manifest = self._manifest(theme_id)
        own_file = first_manifest.get("files", {}).get(role)
        default_file = f"{role}.json" if role in ("colors", "fonts", "components") else f"layouts/{role}.json"
        target_rel = _safe_json_path(own_file) if own_file else _safe_json_path(default_file)
        while cursor:
            if cursor in seen or len(seen) >= 16:
                raise ValueError("테마 상속 순환 또는 깊이 초과")
            seen.add(cursor)
            manifest_rel, manifest = self._manifest(cursor)
            file_name = manifest.get("files", {}).get(role)
            if file_name:
                chain.append((manifest_rel.parent / _safe_json_path(file_name), cursor))
            cursor = manifest.get("extends")
        return chain, target_rel, first_manifest_rel

    def load(self, theme_id, role):
        if role not in ROLES:
            raise ValueError(f"지원하지 않는 레이아웃 역할: {role}")
        effective = self.inspect(theme_id) if self.inspect else {}
        resolved_chain, target, manifest_rel = self._resolve(theme_id, role)
        self.theme_id, self.role = theme_id, role
        self.target_rel, self.manifest_rel = target, manifest_rel
        self.parents, self.local = [], {}
        for relative, owner in reversed(resolved_chain):
            value = json.loads((self.working_assets / relative).read_text(encoding="utf-8"))
            if owner == theme_id:
                self.local = value
            else:
                self.parents.append((owner, value))
        self._sync_data()
        self.source_label = " → ".join(owner for _, owner in reversed(resolved_chain)) or "새 레이아웃 파일"
        self.effective = effective
        return self.data

    def _merged(self, local):
        data = {}
        for _, parent in self.parents:
            data = _deep_merge(data, parent)
        return _deep_merge(data, local)

    def _sync_data(self):
        self.data = self._merged(self.local)
        self._baseline = copy.deepcopy(self.data)

    @property
    def dirty(self):
        return bool(self.pending_roles) or self.data != self._baseline

    def owner(self, path):
        for owner, document in [(self.theme_id, self.local), *reversed(self.parents)]:
            # Arrays replace the parent list, including keys missing from its items.
            for length in range(1, len(path) + 1):
                value = _lookup(document, path[:length])
                if isinstance(value, list) or (length == len(path) and value is not MISSING):
                    return owner
        return None

    def discard_theme_changes(self, theme_id):
        relative = THEMES_ROOT / theme_id
        source = self.source_assets / relative
        working = self.working_assets / relative
        if not source.is_dir():
            raise ValueError("원본 테마 디렉터리를 찾지 못했습니다")
        if working.exists():
            shutil.rmtree(working)
        shutil.copytree(source, working)
        self.source_versions = {path: version for path, version in self.source_versions.items()
                                if not path.is_relative_to(relative)}
        self.source_versions.update((path.relative_to(self.working_assets), path.read_bytes())
                                    for path in working.rglob("*.json"))
        self.pending_roles = {key: value for key, value in self.pending_roles.items() if key[0] != theme_id}

    def set_value(self, path, value):
        current = self.data
        for part in path[:-1]:
            current = current[part]
        current[path[-1]] = value

    def get_value(self, path):
        value = self.data
        for part in path:
            value = value[part]
        return value

    def add_value(self, parent_path, key, value):
        parent = self.get_value(parent_path) if parent_path else self.data
        if isinstance(parent, dict):
            if not key or key in parent:
                raise ValueError("새 속성 이름을 입력하세요. 기존 이름은 추가할 수 없습니다")
            parent[key] = value
        elif isinstance(parent, list):
            parent.append(value)
        else:
            raise ValueError("객체 또는 배열 노드를 선택하세요")

    def delete_value(self, path):
        if not path:
            raise ValueError("레이아웃 루트는 삭제할 수 없습니다")
        parent = self.get_value(path[:-1])
        del parent[path[-1]]

    def move_value(self, path, offset):
        if not path or not isinstance(path[-1], int):
            raise ValueError("순서 변경은 배열 항목에서만 사용할 수 있습니다")
        parent = self.get_value(path[:-1])
        index, target = path[-1], path[-1] + offset
        if not isinstance(parent, list) or target < 0 or target >= len(parent):
            raise ValueError("배열 경계를 벗어났습니다")
        parent[index], parent[target] = parent[target], parent[index]

    def set_property(self, path, value):
        if any(isinstance(key, int) for key in path):
            self.set_value(path, value)
            self._write_working()
            return
        local = copy.deepcopy(self.local)
        # An inherited scalar frame padding is valid shorthand. Changing one axis
        # expands it to the required object, retaining the other effective axis.
        frame = ("shell", "frame", "padding")
        if path[:len(frame)] == frame and len(path) > len(frame):
            fallback = _lookup(self.data, frame)
            base = _lookup(self._merged({}), frame)
            if not isinstance(fallback, dict) or not isinstance(base, dict):
                if not isinstance(_lookup(local, frame), dict):
                    axes = _lookup(self.effective, frame, {"horizontal": 0, "vertical": 0})
                    _put(local, frame, axes)
        gap = ("shell", "frame", "gap")
        if path[:len(gap)] == gap and len(path) > len(gap) and _lookup(self.data, gap) is MISSING:
            _put(local, gap, _lookup(self.effective, gap, {"horizontal": 0, "vertical": 0}))
        _put(local, path, value)
        self._commit_draft(local)

    def reset_property(self, path):
        if _lookup(self.local, path) is MISSING:
            return
        local = copy.deepcopy(self.local)
        if any(isinstance(key, int) for key in path):
            raise ValueError("배열은 전체를 선택해 상속값으로 되돌릴 수 있습니다")
        frame = ("shell", "frame", "padding")
        if path[:len(frame)] == frame and len(path) > len(frame):
            parent = _lookup(self._merged({}), frame)
            # A scalar parent cannot complete the required two-axis object.
            if not isinstance(parent, dict):
                path = frame
        if path[:2] == ("shell", "frame") and path[2:3] == ("gap",) and len(path) > 3:
            if _lookup(self._merged({}), path) is MISSING:
                path = path[:3]
        _remove(local, path)
        self._commit_draft(local)

    def _write_working(self):
        validate_role(self.data, self.role, ROOT)
        local = copy.deepcopy(self.local)
        for path, value in _changes(self._baseline, self.data):
            if value is MISSING:
                _remove(local, path)
            else:
                _put(local, path, value)
        self._commit_draft(local)

    def _commit_draft(self, local):
        validate_role(self._merged(local), self.role, ROOT)
        if local == self.local:
            self._sync_data()
            return
        manifest_path = self.working_assets / self.manifest_rel
        target = self.working_assets / THEMES_ROOT / self.theme_id / self.target_rel
        manifest_bytes = manifest_path.read_bytes()
        previous = target.read_bytes() if target.exists() else None
        manifest = json.loads(manifest_bytes)
        source_manifest = json.loads((self.source_assets / self.manifest_rel).read_text(encoding="utf-8"))
        source_name = source_manifest.get("files", {}).get(self.role)
        saved_file = self.source_assets / THEMES_ROOT / self.theme_id / self.target_rel
        saved = json.loads(saved_file.read_text(encoding="utf-8")) if source_name else {}
        files = manifest.setdefault("files", {})
        keep_file = bool(local) or source_name is not None
        if keep_file:
            if not source_name and saved_file.exists():
                raise ValueError("등록되지 않은 테마 파일이 있어 덮어쓰지 않았습니다")
            files[self.role] = self.target_rel.as_posix()
        else:
            files.pop(self.role, None)
        try:
            if keep_file:
                _atomic_json(target, local)
            else:
                target.unlink(missing_ok=True)
            if json.loads(manifest_bytes) != manifest:
                _atomic_json(manifest_path, manifest)
            effective = self.inspect(self.theme_id) if self.inspect else {}
        except Exception:
            if previous is None:
                target.unlink(missing_ok=True)
            else:
                target.write_bytes(previous)
            manifest_path.write_bytes(manifest_bytes)
            raise
        self.effective = effective
        self.local = local
        self._sync_data()
        key = (self.theme_id, self.role)
        if saved == local:
            self.pending_roles.pop(key, None)
        else:
            self.pending_roles[key] = (self.target_rel, self.manifest_rel)
        owners = [owner for owner, _ in self.parents]
        self.source_label = " → ".join([*owners, *([self.theme_id] if keep_file else [])]) or "새 레이아웃 파일"

    def save(self, theme_id=None):
        if self.data != self._baseline:
            self._write_working()
        pending = {key: value for key, value in self.pending_roles.items() if theme_id is None or key[0] == theme_id}
        for (theme_id, _role), (target, manifest_rel) in pending.items():
            if target.is_absolute() or ".." in target.parts:
                raise ValueError("테마 경로가 작업 디렉터리 밖을 가리킵니다")
            source_theme = self.working_assets / THEMES_ROOT / theme_id
            destination_theme = self.source_assets / THEMES_ROOT / theme_id
            for relative in (target, Path("manifest.json")):
                source = source_theme / relative
                destination = destination_theme / relative
                if not source.is_file() or not destination.resolve().is_relative_to(self.source_assets):
                    raise ValueError("저장 경로 검증에 실패했습니다")
                identity = destination.relative_to(self.source_assets)
                version = destination.read_bytes() if destination.exists() else None
                if version != self.source_versions.get(identity):
                    raise ValueError("다른 곳에서 " + str(identity) + " 파일을 변경했습니다. 초안을 보존하고 저장을 중단했습니다.")
        for (theme_id, _role), (target, _manifest_rel) in pending.items():
            source_theme = self.working_assets / THEMES_ROOT / theme_id
            destination_theme = self.source_assets / THEMES_ROOT / theme_id
            for relative in (target, Path("manifest.json")):
                source = source_theme / relative
                destination = destination_theme / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                temporary = destination.with_name(destination.name + f".{os.getpid()}.tmp")
                shutil.copyfile(source, temporary)
                os.replace(temporary, destination)
                self.source_versions[destination.relative_to(self.source_assets)] = destination.read_bytes()
        for key in pending:
            del self.pending_roles[key]


def _parse_value(raw, previous):
    if isinstance(previous, bool):
        if raw.strip().lower() not in ("true", "false"):
            raise ValueError("true 또는 false를 입력하세요")
        return raw.strip().lower() == "true"
    if isinstance(previous, int) and not isinstance(previous, bool):
        return int(raw.strip())
    if isinstance(previous, float):
        return float(raw.strip())
    if previous is None:
        return json.loads(raw)
    if isinstance(previous, str):
        return raw
    raise ValueError("이 항목은 값 편집기에서 수정할 수 없습니다")


def check():
    from js_ui_preview import Renderer as PreviewRenderer
    with tempfile.TemporaryDirectory(prefix="dashboard-layout-editor-check-") as folder:
        work = Path(folder)
        model = LayoutEditorModel(ROOT / "apps/dashboard/src/0x1101L/assets", work / "assets")
        model.load("f1_2", "overview")
        path = ("overview", "grid", "itemGapX")
        original = model.get_value(path)
        original_bytes = (model.source_assets / "themes/f1_2/layouts/overview.json").read_bytes()
        source_preview_dir = work / "source-preview"
        before_dir = work / "before"
        (work / "preview").mkdir()
        (work / "previewer-classes").mkdir()
        previewer = PreviewRenderer(work / "previewer-classes")
        renderer = Renderer(work)
        try:
            themes = model.themes()
            states, languages = list(STATES), list(LANGUAGES)
            source_preview_dir.mkdir()
            before_dir.mkdir()
            compared = 0
            before_image = None
            expected = 0
            for vehicle, spec in VEHICLES.items():
                pages = vehicle_pages(vehicle)
                expected += len(pages) * len(states) * len(languages) * len(themes)
                for page in pages:
                    for state in states:
                        for language in languages:
                            for theme_id in themes:
                                previewer.render(source_preview_dir, [page], [state], [language],
                                                 themes=[theme_id], vehicle=vehicle)
                                renderer.render(before_dir, [page], [state], [language],
                                                themes=[theme_id], vehicle=vehicle,
                                                asset_root=model.working_assets)
                                name = f"{vehicle}-{page}-{state}-{language}-{theme_id}.png"
                                preview_image = (source_preview_dir / name).read_bytes()
                                editor_image = (before_dir / name).read_bytes()
                                if preview_image != editor_image:
                                    # The live dashboard clock can tick between the paired renders.
                                    previewer.render(source_preview_dir, [page], [state], [language],
                                                     themes=[theme_id], vehicle=vehicle)
                                    renderer.render(before_dir, [page], [state], [language],
                                                    themes=[theme_id], vehicle=vehicle,
                                                    asset_root=model.working_assets)
                                    preview_image = (source_preview_dir / name).read_bytes()
                                    editor_image = (before_dir / name).read_bytes()
                                    if preview_image != editor_image:
                                        raise AssertionError(f"previewer/editor render mismatch: {name}")
                                dimensions = struct.unpack(">II", editor_image[16:24])
                                if dimensions != spec["resolution"]:
                                    raise AssertionError(f"{name}: rendered {dimensions}, expected {spec['resolution']}")
                                if name == "js-0-received-ko-f1_2.png":
                                    before_image = editor_image
                                compared += 1
            if compared != expected:
                raise AssertionError("previewer/editor render matrix is incomplete")
            model.set_value(path, original + 1)
            model._write_working()
            renderer.render(work / "preview", [0], ["received"], ["ko"],
                            themes=["f1_2"], vehicle="js", asset_root=model.working_assets)
            after_image = next((work / "preview").glob("*.png")).read_bytes()
            loaded = LayoutEditorModel(model.working_assets, work / "second-copy")
            loaded.load("f1_2", "overview")
            if loaded.get_value(path) != original + 1:
                raise AssertionError("edited property did not reach the renderer asset copy")
            if before_image == after_image:
                raise AssertionError("layout edit did not change the production renderer output")
            model.set_value(path, 999)
            try:
                model._write_working()
            except SchemaValidationError:
                pass
            else:
                raise AssertionError("schema-invalid layout value reached the renderer asset copy")
            if (model.source_assets / "themes/f1_2/layouts/overview.json").read_bytes() != original_bytes:
                raise AssertionError("check modified project source assets")
            print(f"PASS {compared} previewer/editor renders, live renderer edit, invalid-value rejection and source isolation")
        finally:
            previewer.close()
            renderer.close()
