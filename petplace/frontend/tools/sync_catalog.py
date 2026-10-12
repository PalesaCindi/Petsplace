#!/usr/bin/env python3
"""Rebuilds frontend/js/catalog-data.js from frontend/data/catalog.json.
Run this after editing catalog.json:  python tools/sync_catalog.py"""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent / "frontend"
data = json.loads((root / "data" / "catalog.json").read_text(encoding="utf-8"))
out = (
    "/* Generated from data/catalog.json by tools/sync_catalog.py - do not edit by hand.\n"
    "   Lets the breeds, shop and puppies pages work even when opened without a server. */\n"
    "window.PETPLACE_CATALOG = " + json.dumps(data, indent=2, ensure_ascii=False) + ";\n"
)
(root / "js" / "catalog-data.js").write_text(out, encoding="utf-8")
print("wrote frontend/js/catalog-data.js")
