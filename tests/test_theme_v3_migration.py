#!/usr/bin/env python3
"""Retired schema migration check: only schema 3 is accepted by the engine."""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
from test_theme_v2_renderer import ThemeV2RendererTests

if __name__ == '__main__':
    suite = unittest.TestSuite([
        ThemeV2RendererTests('test_v1_and_v2_catalogs_and_manifests_are_rejected')
    ])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(not result.wasSuccessful())
