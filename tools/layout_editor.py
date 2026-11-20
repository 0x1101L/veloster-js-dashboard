#!/usr/bin/env python3
"""Layout Studio for Dashboard theme and layout files."""
import argparse

from layout_editor_host import run
from layout_editor_runtime import check


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate an isolated layout edit with the production renderer")
    args = parser.parse_args()
    if args.check:
        check()
        return
    run()


if __name__ == "__main__":
    main()
