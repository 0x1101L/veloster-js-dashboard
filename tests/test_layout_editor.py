"""Tests for the isolated Dashboard layout editor model and renderer path."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys
sys.path.insert(0, str(ROOT / "tools"))
from layout_editor_runtime import LayoutEditorModel, _parse_value, _safe_json_path
from layout_schema_validation import SchemaValidationError, _assert_supported_keywords, validate_role


class LayoutEditorModelTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="layout-editor-model-")
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        self.source = self.work / "source-assets"
        shutil.copytree(ROOT / "apps/dashboard/src/0x1101L/assets", self.source)
        self.source_bytes = (self.source / "themes/f1_2/layouts/overview.json").read_bytes()
        self.model = LayoutEditorModel(self.source, self.work / "assets")

    def test_edits_are_isolated_until_save_and_saved_to_selected_theme(self):
        self.model.load("f1_2", "overview")
        path = ("overview", "grid", "itemGapX")
        self.model.set_value(path, self.model.get_value(path) + 1)
        self.model._write_working()
        self.assertNotEqual((self.model.working_assets / "themes/f1_2/layouts/overview.json").read_bytes(),
                            self.source_bytes)
        self.assertEqual((self.source / "themes/f1_2/layouts/overview.json").read_bytes(), self.source_bytes)
        self.model.save()
        self.assertEqual((self.source / "themes/f1_2/layouts/overview.json").read_bytes(),
                         (self.model.working_assets / "themes/f1_2/layouts/overview.json").read_bytes())

    def test_inherited_layout_is_materialized_as_child_override(self):
        self.model.load("f1_2_optics", "overview")
        original = self.model.get_value(("overview", "grid", "itemGapX"))
        self.model.set_value(("overview", "grid", "itemGapX"), original + 1)
        self.model._write_working()
        manifest = json.loads((self.model.working_assets / "themes/f1_2_optics/manifest.json").read_text())
        self.assertEqual(manifest["files"]["overview"], "layouts/overview.json")
        override = self.model.working_assets / "themes/f1_2_optics/layouts/overview.json"
        self.assertEqual(json.loads(override.read_text()), {"overview": {"grid": {"itemGapX": original + 1}}})
        self.assertEqual(self.model.owner(("overview", "grid", "itemGapY")), "f1_2")

    def test_reset_sidebar_width_removes_only_child_override(self):
        path = ("shell", "sideMenuBar", "width")
        self.model.load("f1_2", "shell")
        inherited_width = self.model.get_value(path)
        child_rel = Path("themes/f1_2_optics/layouts/shell.json")
        child_source = self.source / child_rel
        fixture = json.loads(child_source.read_text())
        fixture["shell"]["sideMenuBar"] = {"width": 92, "padding": {"vertical": 16}}
        content = json.dumps(fixture).encode()
        child_source.write_bytes(content)
        (self.model.working_assets / child_rel).write_bytes(content)
        self.model.source_versions[child_rel] = content
        self.model.load("f1_2_optics", "shell")
        self.assertEqual(self.model.get_value(path), 92)
        self.model.reset_property(path)
        self.assertEqual(self.model.get_value(path), inherited_width)
        self.assertEqual(self.model.owner(path), "f1_2")
        self.model.save("f1_2_optics")
        child = json.loads((self.source / "themes/f1_2_optics/layouts/shell.json").read_text())
        self.assertNotIn("width", child["shell"]["sideMenuBar"])
        self.assertEqual(child["shell"]["sideMenuBar"]["padding"], {"vertical": 16})

    def test_absent_padding_can_be_set_without_materializing_other_defaults(self):
        self.model.load("f1_2", "shell")
        path = ("shell", "contentArea", "padding", "left")
        self.assertIsNone(self.model.owner(path))
        self.model.set_property(path, 14)
        self.assertEqual(self.model.local["shell"]["contentArea"], {"padding": {"left": 14}})
        self.model.reset_property(path)
        self.assertNotIn("contentArea", self.model.local["shell"])
        self.assertFalse(self.model.dirty)

    def test_top_menu_spacing_alignment_and_clock_controls_are_editable(self):
        self.model.load("f1_2", "shell")
        self.model.set_property(("shell", "topMenuBar", "buttonMargin", "top"), 3)
        self.model.set_property(("shell", "topMenuBar", "buttonMargin", "right"), 7)
        self.model.set_property(("shell", "topMenuBar", "contentAlignment"), "end")
        self.model.set_property(("shell", "topMenuBar", "clockPosition"), "start")
        top_menu = self.model.local["shell"]["topMenuBar"]
        self.assertEqual(top_menu["buttonMargin"], {"top": 3, "right": 7})
        self.assertEqual(top_menu["contentAlignment"], "end")
        self.assertEqual(top_menu["clockPosition"], "start")
        validate_role(self.model.data, "shell", ROOT)

    def test_save_does_not_write_other_theme_drafts(self):
        path = ("shell", "sideMenuBar", "width")
        self.model.load("f1_2", "shell")
        original_width = self.model.get_value(path)
        self.model.set_property(path, 90)
        self.model.load("f1_2_optics", "shell")
        self.model.set_property(path, 72)
        self.model.save("f1_2_optics")
        parent = json.loads((self.source / "themes/f1_2/layouts/shell.json").read_text())
        child = json.loads((self.source / "themes/f1_2_optics/layouts/shell.json").read_text())
        self.assertEqual(parent["shell"]["sideMenuBar"]["width"], original_width)
        self.assertEqual(child["shell"]["sideMenuBar"]["width"], 72)
        self.assertIn(("f1_2", "shell"), self.model.pending_roles)

    def test_reset_unsaved_inherited_role_does_not_leave_a_manifest_reference(self):
        path = ("overview", "grid", "itemGapX")
        self.model.load("f1_2_optics", "overview")
        self.model.set_property(path, self.model.get_value(path) + 1)
        self.model.reset_property(path)
        manifest = self.model._manifest("f1_2_optics")[1]
        self.assertNotIn("overview", manifest["files"])
        self.assertFalse((self.model.working_assets / "themes/f1_2_optics/layouts/overview.json").exists())
        self.assertFalse(self.model.dirty)

    def test_save_preserves_external_changes_and_draft(self):
        path = ("shell", "sideMenuBar", "width")
        self.model.load("f1_2", "shell")
        self.model.set_property(path, 90)
        target = self.source / "themes/f1_2/layouts/shell.json"
        external = target.read_bytes() + b"\n"
        target.write_bytes(external)
        with self.assertRaisesRegex(ValueError, "다른 곳에서"):
            self.model.save("f1_2")
        self.assertEqual(target.read_bytes(), external)
        self.assertEqual(self.model.get_value(path), 90)
        self.assertTrue(self.model.dirty)

    def test_runtime_rejection_restores_draft_and_manifest(self):
        self.model.load("f1_2_optics", "overview")
        manifest = self.model.working_assets / "themes/f1_2_optics/manifest.json"
        before = manifest.read_bytes()
        def reject(_):
            raise ValueError("renderer rejected")
        self.model.inspect = reject
        with self.assertRaisesRegex(ValueError, "renderer rejected"):
            self.model.set_property(("overview", "grid", "itemGapX"), 12)
        self.assertEqual(manifest.read_bytes(), before)
        self.assertFalse((manifest.parent / "layouts/overview.json").exists())
        self.assertFalse(self.model.dirty)

    def test_color_font_and_component_roles_can_be_edited(self):
        for role in ("colors", "fonts", "components"):
            with self.subTest(role=role):
                self.model.load("f1_2", role)
                self.assertTrue(self.model.data)
                self.assertIn(role, self.model._manifest("f1_2")[1]["files"])
                validate_role(self.model.data, role, ROOT)

    def test_role_resolution_merges_all_inherited_documents(self):
        parent = self.model.working_assets / "themes/f1_2/components.json"
        child = self.model.working_assets / "themes/f1_2_optics/components.json"
        parent.write_text('{"material":{"style":"glass","radius":24},"mediaControls":"container"}', encoding="utf-8")
        child.write_text('{"material":{"style":"flat"}}', encoding="utf-8")
        manifest_path = self.model.working_assets / "themes/f1_2_optics/manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest.setdefault("files", {})["components"] = "components.json"
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        self.model.load("f1_2_optics", "components")
        self.assertEqual(self.model.data["material"], {"style": "flat", "radius": 24})
        self.assertEqual(self.model.data["mediaControls"], "container")

    def test_structure_operations_and_typed_value_parsing(self):
        data = {"items": [1, {"enabled": True}]}
        self.model.data = data
        self.model.add_value(("items",), "", 3)
        self.model.move_value(("items", 2), -1)
        self.assertEqual(data["items"], [1, 3, {"enabled": True}])
        self.model.delete_value(("items", 1))
        self.assertEqual(data["items"], [1, {"enabled": True}])
        self.assertIs(_parse_value("false", True), False)
        self.assertEqual(_parse_value("17", 1), 17)
        with self.assertRaises(ValueError):
            _parse_value("perhaps", True)

    def test_rejects_parent_traversal_paths(self):
        with self.assertRaises(ValueError):
            _safe_json_path("../outside.json")

    def test_schema_rejects_invalid_layout_before_writing_working_copy(self):
        self.model.load("f1_2", "overview")
        original = self.model.get_value(("overview", "grid", "itemGapX"))
        target = self.model.working_assets / "themes/f1_2/layouts/overview.json"
        original_bytes = target.read_bytes()
        self.model.set_value(("overview", "grid", "itemGapX"), 999)
        with self.assertRaises(SchemaValidationError):
            self.model._write_working()
        self.assertEqual(target.read_bytes(), original_bytes)
        self.model.set_value(("overview", "grid", "itemGapX"), original)
        self.model._write_working()
        self.assertTrue(target.is_file())

    def test_current_theme_layouts_validate_against_role_schemas(self):
        catalog = json.loads((self.source / "themes/catalog.json").read_text(encoding="utf-8"))
        checked = 0
        for theme_id in catalog["themeIds"]:
            for role in ("shell", "f1", "overview", "gauges", "graphs", "performance", "settings", "diagnostics"):
                self.model.load(theme_id, role)
                if self.model.source_label != "새 레이아웃 파일":
                    validate_role(self.model.data, role, ROOT)
                    checked += 1
        self.assertGreater(checked, 0)

    def test_each_role_schema_uses_only_supported_validation_keywords(self):
        for schema_path in sorted((ROOT / "theme/schema").glob("*.schema.json")):
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
            self.assertIsInstance(schema, dict)
            _assert_supported_keywords(schema, str(schema_path))

    def test_copied_preview_backend_stays_synchronized_with_preview_tool(self):
        preview = (ROOT / "tools/js_ui_preview.py").read_text(encoding="utf-8")
        editor = (ROOT / "tools/layout_editor_preview.py").read_text(encoding="utf-8")
        editor = editor.replace(
            '"""Renderer backend copied from the Dashboard previewer for the layout editor."""',
            '"""Live Java2D preview of the JS dashboard renderers on a desktop."""',
            1,
        )
        editor = editor.replace(
            "def render(self, folder, pages, states, languages, themes=THEMES, vehicle='js', asset_root=None):",
            "def render(self, folder, pages, states, languages, themes=THEMES, vehicle='js'):",
            1,
        )
        editor = editor.replace(
            "assets = Path(asset_root) if asset_root else ROOT / 'apps/dashboard/src/0x1101L/assets'",
            "assets = ROOT / 'apps/dashboard/src/0x1101L/assets'",
            1,
        )
        editor = editor.replace(
            "from layout_editor_runtime import run", "from dashboard_runtime import run", 1
        )
        self.assertEqual(editor, preview)


if __name__ == "__main__":
    unittest.main()
