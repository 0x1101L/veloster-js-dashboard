#!/usr/bin/env python3
"""Refresh checked-in Java2D previews from the current Dashboard renderers."""
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from js_ui_preview import Renderer


def render_palette(engine, output):
    source = Path(engine.classes.parent) / "PalettePreview.java"
    source.write_text(r'''package com.veloster.dashboard.view;
import android.graphics.Canvas;
import com.veloster.dashboard.theme.*;
import com.veloster.dashboard.view.DashboardPage.Content;
import com.veloster.i18n.UiText;
import java.io.*;
import java.nio.file.*;
public final class PalettePreview {
  public static void main(String[] args) throws Exception {
    final Path assets = Paths.get(args[1]);
    ThemeSpec theme = ThemeCatalog.load(new android.content.Context() {
      public android.content.res.AssetManager getAssets() {
        return new android.content.res.AssetManager() {
          public InputStream open(String path) throws IOException {
            return Files.newInputStream(assets.resolve(path));
          }
        };
      }
    }).find("material_auto_cowork");
    UiText.configure("ko", "ko");
    String[] labels = new String[ThemePalette.accentCount(theme.id)];
    int[] colors = new int[labels.length];
    for (int i=0; i<labels.length; i++) {
      labels[i] = ThemePalette.accentName(theme.id, i);
      colors[i] = ThemePalette.accentColor(theme, i);
    }
    SettingsPageRenderer.Editor editor = new SettingsPageRenderer.Editor();
    editor.swatchPage("포인트 컬러", labels, colors, 2,
                      SettingsPageRenderer.THEME_SETTINGS);
    Canvas canvas = new Canvas(800, 480);
    canvas.drawColor(theme.background);
    new SettingsPageRenderer().draw(canvas, theme, null, editor,
                                    new Content(theme, "settings", 800, 480));
    canvas.write(args[0]);
  }
}''', encoding="utf-8")
    java = engine.process.args[0]
    javac = str(Path(java).with_name("javac"))
    subprocess.run([javac, "-encoding", "UTF-8", "-cp", str(engine.classes),
                    "-d", str(engine.classes), str(source)], check=True)
    subprocess.run([
        java, "-Djava.awt.headless=true", "-cp", str(engine.classes),
        "com.veloster.dashboard.view.PalettePreview", str(output),
        str(ROOT / "apps/dashboard/src/0x1101L/assets"),
    ], check=True)


def main():
    preview_root = ROOT / "theme/theme-previews"
    material = preview_root / "material-auto"
    performance = preview_root / "performance"
    material.mkdir(parents=True, exist_ok=True)
    performance.mkdir(parents=True, exist_ok=True)
    material_cases = [
        ("js", 0, "received"),
        ("cn7", 0, "received"), ("cn7", 0, "waiting"),
        ("cn7", 10, "received"), ("cn7", 10, "waiting"),
        ("js", 10, "received"), ("js", 10, "waiting"),
        ("js", 6, "received"), ("js", 7, "received"),
        ("js", 8, "received"), ("js", 9, "received"),
    ]
    with tempfile.TemporaryDirectory(prefix="dashboard-theme-previews-") as work:
        engine = Renderer(work)
        try:
            for vehicle, page, state in material_cases:
                engine.render(material, [page], [state], ["ko"],
                              themes=("material_auto_cowork",), vehicle=vehicle)
            for language in ("ko", "en"):
                engine.render(performance, [6], ["received"], [language],
                              themes=("original",), vehicle="js")
            render_palette(engine, material / "material-auto-accent-palette.png")
        finally:
            engine.close()
    print("Refreshed 11 Material Auto dashboard captures, 2 performance captures, and 1 accent palette page.")


if __name__ == "__main__":
    main()
