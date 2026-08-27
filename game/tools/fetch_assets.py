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
#
# 1k, not 2k. At the tiling scales used here the difference is invisible on a
# phone, and it roughly halves both the download and the texture memory — which
# buys back what the palace and the garden cost.
TEXTURES = {
    "parquet":      ("herringbone_parquet", "1k"),
    "planks":       ("plank_flooring_02", "1k"),
    "wallpaper":    ("decrepit_wallpaper", "1k"),
    "plaster":      ("plastered_wall_02", "1k"),
    "ceiling":      ("white_stucco", "1k"),
    "tiles_kitchen": ("floor_tiles_06", "1k"),
    "tiles_bath":   ("marble_tiles", "1k"),
    "wood_dark":    ("dark_planks", "1k"),
    "brick":        ("brick_wall_006", "1k"),
    "carpet":       ("dirty_carpet", "1k"),
    "concrete":     ("concrete_floor_worn_001", "1k"),
    "attic_wood":   ("old_planks_02", "1k"),
    "chitin":       ("bark_brown_02", "1k"),
    "leather":      ("brown_leather", "1k"),
    # palace
    "marble":       ("marble_01", "1k"),
    "sandstone":    ("large_sandstone_blocks", "1k"),
    "roof_slate":   ("red_slate_roof_tiles_01", "1k"),
    "mosaic":       ("marble_mosaic_tiles", "1k"),
    # garden
    "grass":        ("leafy_grass", "1k"),
    "path":         ("stone_pathway", "1k"),
    "cobble":       ("cobblestone_floor_02", "1k"),
    "gravel":       ("gravel_floor_02", "1k"),
    "foliage":      ("forest_leaves_02", "1k"),
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
    # --- palace ---
    "Chandelier_02", "Chandelier_03", "lantern_chandelier_01",
    "GothicCabinet_01", "GothicCommode_01", "gothic_coffee_table", "gothic_statue",
    "chinese_sofa", "chinese_console_table", "chinese_screen_panels",
    "marble_bust_01", "brass_vase_02", "brass_candleholders", "fancy_picture_frame_02",
    # --- garden: planting ---
    # No Poly Haven trees: even at 1k textures a single pine is 914 MB of leaf
    # cards and dense geometry. The garden's trees are built procedurally in
    # scripts/garden.gd instead — a trunk with branches and foliage clusters,
    # which at night reads fine and costs almost nothing.
    "shrub_01", "shrub_02", "shrub_03", "fern_02", "grass_medium_01",
    "flower_gazania", "flower_ursinia", "dandelion_01", "calathea_orbifolia_01",
    # --- garden: furniture, lights, ornament ---
    "street_lamp_01", "street_lamp_02", "wooden_lantern_01",
    "painted_wooden_bench", "outdoor_table_chair_set_01", "wooden_picnic_table",
    "planter_box_01", "planter_pot_clay", "garden_gnome", "stone_fire_pit",
    "horse_statue_01", "concrete_cat_statue", "boulder_01", "rock_07",
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
