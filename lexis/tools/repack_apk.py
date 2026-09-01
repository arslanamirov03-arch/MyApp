#!/usr/bin/env python3
"""Replace assets/index.html inside an APK, preserving entry order and
per-entry compression method. Signing blocks are dropped; the result must be
zipaligned and re-signed with apksigner afterwards.
"""
import shutil
import sys
import zipfile

src, asset_path, dst = sys.argv[1], sys.argv[2], sys.argv[3]
new_bytes = open(asset_path, "rb").read()

with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    for info in zin.infolist():
        data = new_bytes if info.filename == "assets/index.html" else zin.read(info.filename)
        out = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        out.compress_type = info.compress_type
        out.external_attr = info.external_attr
        out.internal_attr = info.internal_attr
        out.create_system = info.create_system
        zout.writestr(out, data)

print("repacked ->", dst)
