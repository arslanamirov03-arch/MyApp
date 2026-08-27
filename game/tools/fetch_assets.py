#!/usr/bin/env python3
"""Download the CC0 asset set (Poly Haven) used by the game.

Assets are not committed to git: they are ~250 MB of third-party CC0 binaries.
Run this once before opening/exporting the Godot project:

    python3 game/tools/fetch_assets.py

Everything downloaded here is CC0 (public domain) from polyhaven.com.
"""

import json
import os
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = "https://api.polyhaven.com"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
ROOT = os.path.normpath(ROOT)

# name -> (polyhaven slug, resolution)
TEXTURES = {
    "parquet":      ("herringbone_parquet", "2k"),
    "planks":       ("plank_flooring_02", "2k"),
    "wallpaper":    ("decrepit_wallpaper", "2k"),
    "plaster":      ("plastered_wall_02", "2k"),
    "ceiling":      ("white_stucco", "2k"),
    "tiles_kitchen": ("floor_tiles_06", "2k"),
    "tiles_bath":   ("marble_tiles", "2k"),
    "wood_dark":    ("dark_planks", "2k"),
    "brick":        ("brick_wall_006", "2k"),
    "carpet":       ("dirty_carpet", "2k"),
    "concrete":     ("concrete_floor_worn_001", "2k"),
    "attic_wood":   ("old_planks_02", "2k"),
    "chitin":       ("bark_brown_02", "2k"),
    "leather":      ("brown_leather", "2k"),
}

TEX_MAPS = ["Diffuse", "nor_gl", "Rough", "AO", "arm", "Displacement"]

# Poly Haven models, all at 1k textures to keep the APK sane.
MODELS = [
    # living room
    "Sofa_01", "ArmChair_01", "CoffeeTable_01", "Television_01",
    "wooden_bookshelf_worn", "vintage_grandfather_clock_01", "potted_plant_01",
    "fancy_picture_frame_01", "throw_pillows_01", "Chandelier_01",
    # kitchen
    "electric_stove", "WoodenTable_01", "WoodenChair_01", "wicker_basket_01",
    "brass_pot_01", "wine_bottles_01", "ceramic_vase_01", "vintage_electric_kettle",
    # bedrooms
    "GothicBed_01", "ClassicNightstand_01", "vintage_cabinet_01",
    "desk_lamp_arm_01", "vintage_suitcase", "old_bed_frame",
    # hall / corridor / bathroom
    "ornate_mirror_01", "hanging_picture_frame_01", "wooden_ladder", "wall_clock",
    # attic / basement clutter
    "wooden_crate_01", "cardboard_box_01", "old_military_crate", "metal_toolbox",
    "wooden_barrels_01", "vintage_radio_transceiver", "vintage_suitcase",
    # lamps
    "caged_hanging_light", "industrial_wall_lamp", "vintage_oil_lamp",
    "modern_ceiling_lamp_01",
    # small physics props the spider can knock around
    "alarm_clock_01", "brass_goblets", "plastic_crate_01", "boombox",
    "wooden_bowl_01", "food_apple_01", "book_encyclopedia_set_01",
]


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "spiderhouse-build/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return 0
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "spiderhouse-build/1.0"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=180) as r, open(dest, "wb") as f:
                data = r.read()
                f.write(data)
            return len(data)
        except Exception as exc:  # noqa: BLE001 - network retry
            if attempt == 3:
                print(f"  !! failed {url}: {exc}")
                return 0
    return 0


def fetch_texture(item):
    name, (slug, res) = item
    try:
        files = get_json(f"{API}/files/{slug}")
    except Exception as exc:  # noqa: BLE001
        print(f"  !! api {slug}: {exc}")
        return 0
    total = 0
    for m in TEX_MAPS:
        node = files.get(m, {}).get(res, {})
        entry = node.get("jpg") or node.get("png")
        if not entry:
            continue
        total += download(entry["url"], os.path.join(ROOT, "textures", name, f"{m}.jpg"))
    print(f"  tex {name:14s} <- {slug} ({total/1e6:.1f} MB)")
    return total


def fetch_model(slug):
    try:
        files = get_json(f"{API}/files/{slug}")
    except Exception as exc:  # noqa: BLE001
        print(f"  !! api {slug}: {exc}")
        return 0
    gltf = files.get("gltf", {})
    node = gltf.get("1k") or gltf.get("2k")
    if not node or "gltf" not in node:
        print(f"  !! no gltf for {slug}")
        return 0
    entry = node["gltf"]
    base = os.path.join(ROOT, "models", slug)
    total = download(entry["url"], os.path.join(base, f"{slug}.gltf"))
    for rel, sub in entry.get("include", {}).items():
        total += download(sub["url"], os.path.join(base, rel))
    print(f"  mdl {slug:32s} ({total/1e6:.1f} MB)")
    return total


def main():
    os.makedirs(ROOT, exist_ok=True)
    print(f"Downloading CC0 assets into {ROOT}")
    total = 0
    with ThreadPoolExecutor(max_workers=8) as pool:
        for got in pool.map(fetch_texture, TEXTURES.items()):
            total += got
    with ThreadPoolExecutor(max_workers=8) as pool:
        for got in pool.map(fetch_model, sorted(set(MODELS))):
            total += got
    print(f"\nDone. {total/1e6:.1f} MB downloaded.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
