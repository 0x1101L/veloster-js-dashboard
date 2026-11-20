"""Interactive editor host that shares DashboardView's live Swing runtime."""
import base64
import copy
import json
import tempfile
from pathlib import Path

from dashboard_runtime import ROOT, run as run_dashboard
from layout_editor_runtime import LayoutEditorModel, MISSING, _lookup
from layout_editor_inspector import describe


def _decode(value):
    return base64.b64decode(value.encode("ascii"), validate=True).decode("utf-8")


def _encode(value):
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def _status(ok, message):
    return "LAYOUT_EDITOR_STATUS\t{}\t{}".format(1 if ok else 0, _encode(message))


def _document(model):
    serialized = json.dumps(describe(model, ROOT), ensure_ascii=False, separators=(",", ":"))
    return "LAYOUT_EDITOR_DOC\t{}\t{}\t{}\t{}\t{}".format(
        _encode(model.theme_id), _encode(model.role), _encode(model.source_label), _encode(serialized),
        int(any(theme_id == model.theme_id for theme_id, _ in model.pending_roles))
    )


def run():
    assets = ROOT / "apps/dashboard/src/0x1101L/assets"
    with tempfile.TemporaryDirectory(prefix="dashboard-layout-editor-") as folder:
        model = LayoutEditorModel(assets, Path(folder) / "assets")
        themes = model.themes()
        active = {"theme": "f1_2", "role": "overview"}

        def handle(line, inspect):
            model.inspect = inspect
            fields = line.rstrip("\r\n").split("\t")
            if len(fields) < 2 or fields[0] != "LAYOUT_EDITOR":
                return None
            command = fields[1]
            try:
                if command == "load":
                    theme_id, role = _decode(fields[2]), _decode(fields[3])
                    model.load(theme_id, role)
                    active.update(theme=theme_id, role=role)
                    return _document(model)
                if command == "set":
                    path, raw = json.loads(_decode(fields[2])), _decode(fields[3])
                    before = copy.deepcopy(model.data)
                    model.set_property(tuple(path), json.loads(raw))
                    return _document(model)
                if command == "inherit":
                    model.reset_property(tuple(json.loads(_decode(fields[2]))))
                    return _document(model)
                if command == "add":
                    path, key, raw = json.loads(_decode(fields[2])), _decode(fields[3]), _decode(fields[4])
                    before = copy.deepcopy(model.data)
                    value = json.loads(raw)
                    if _lookup(model.data, path) is MISSING:
                        if key:
                            model.set_property((*path, key), value)
                        else:
                            model.set_property(tuple(path), [value])
                    else:
                        model.add_value(path, key, value)
                        model._write_working()
                    return _document(model)
                if command == "delete":
                    path = json.loads(_decode(fields[2]))
                    before = copy.deepcopy(model.data)
                    model.delete_value(path)
                    model._write_working()
                    return _document(model)
                if command == "move":
                    path, offset = json.loads(_decode(fields[2])), int(_decode(fields[3]))
                    before = copy.deepcopy(model.data)
                    model.move_value(path, offset)
                    model._write_working()
                    return _document(model)
                if command == "save":
                    model.save(model.theme_id)
                    return _status(True, "테마 파일에 저장했습니다")
                if command == "reset":
                    theme_id = _decode(fields[2])
                    model.discard_theme_changes(theme_id)
                    if active["theme"] == theme_id:
                        model.load(active["theme"], active["role"])
                        return _document(model)
                    return _status(True, theme_id + " 초안을 원본으로 되돌렸습니다")
                return _status(False, "지원하지 않는 편집 명령: " + command)
            except Exception as error:
                if "before" in locals():
                    model.data = before
                return _status(False, str(error))

        run_dashboard(
            asset_root=model.working_assets,
            editor=True,
            editor_theme=active["theme"],
            editor_themes=themes,
            editor_handler=handle,
        )
