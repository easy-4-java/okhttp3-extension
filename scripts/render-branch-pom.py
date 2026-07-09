#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"

MATRIX = {
    "feature/1.0.x": {"version": "1.0.x.20260630-SNAPSHOT", "java": "1.8"},
    "feature/2.0.x": {"version": "2.0.x.20260630-SNAPSHOT", "java": "17"},
    "feature/3.0.x": {"version": "3.0.x.20260630-SNAPSHOT", "java": "21"},
    "1.0.x": {"version": "1.0.x.20260630-SNAPSHOT", "java": "1.8"},
    "2.0.x": {"version": "2.0.x.20260630-SNAPSHOT", "java": "17"},
    "3.0.x": {"version": "3.0.x.20260630-SNAPSHOT", "java": "21"},
}


def replace_once(text: str, pattern: str, repl: str) -> str:
    return re.sub(pattern, repl, text, count=1, flags=re.MULTILINE)


def main() -> int:
    if len(sys.argv) != 2 or sys.argv[1] not in MATRIX:
        print("usage: python3 scripts/render-branch-pom.py <feature/1.0.x|feature/2.0.x|feature/3.0.x>")
        return 1
    cfg = MATRIX[sys.argv[1]]
    text = POM.read_text(encoding="utf-8")
    text = replace_once(text, r"<version>.*?</version>", f"<version>{cfg['version']}</version>")
    text = replace_once(text, r"<description>.*?</description>", f"<description>OkHttp3 Extensions - independent of Spring Boot, JDK {cfg['java']} line</description>")
    text = replace_once(text, r"<java\.version>.*?</java\.version>", f"<java.version>{cfg['java']}</java.version>")
    text = replace_once(text, r"<maven\.compiler\.release>.*?</maven\.compiler\.release>", f"<maven.compiler.release>${{java.version}}</maven.compiler.release>")
    text = replace_once(text, r"<maven\.compiler\.source>.*?</maven\.compiler\.source>", f"<maven.compiler.source>${{java.version}}</maven.compiler.source>")
    text = replace_once(text, r"<maven\.compiler\.target>.*?</maven\.compiler\.target>", f"<maven.compiler.target>${{java.version}}</maven.compiler.target>")
    POM.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
