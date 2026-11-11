"""Generates bedrock/ (Bedrock pack + Geyser item mappings) from resourcepack/. Usage: bedrock.py <repo>"""
import json, sys, glob, os, uuid
import numpy as np
from PIL import Image

repo = sys.argv[1]
java = f"{repo}/resourcepack/assets/dm"
out = f"{repo}/bedrock"
ICON, SS = 32, 8  # icon size in px, supersampling
BASE = {"rifle": "iron_hoe", "smg": "golden_hoe", "shotgun": "iron_shovel", "sniper": "blaze_rod", "pistol": "stick",
        "knife": "iron_sword", "grenade_he": "fire_charge", "grenade_flash": "snowball", "grenade_smoke": "firework_star"}
NAME = {"rifle": "Rifle", "smg": "SMG", "shotgun": "Shotgun", "sniper": "Sniper", "pistol": "Pistol", "knife": "Knife",
        "grenade_he": "HE Grenade", "grenade_flash": "Flashbang", "grenade_smoke": "Smoke"}

def rot(axis, deg):
    c, s = np.cos(np.radians(deg)), np.sin(np.radians(deg))
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]

# corners as TL, BL, BR, TR seen from outside; f/t index: 0 = from, 1 = to
FACES = {"north": ([(1,1,0),(1,0,0),(0,0,0),(0,1,0)], .8), "south": ([(0,1,1),(0,0,1),(1,0,1),(1,1,1)], .8),
         "west": ([(0,1,0),(0,0,0),(0,0,1),(0,1,1)], .6), "east": ([(1,1,1),(1,0,1),(1,0,0),(1,1,0)], .6),
         "up": ([(0,1,0),(0,1,1),(1,1,1),(1,1,0)], 1), "down": ([(0,0,1),(0,0,0),(1,0,0),(1,0,1)], .5)}

def quads(model):
    tex = {k: np.asarray(Image.open(f"{java}/textures/{v.split(':')[1]}.png").convert("RGBA")) for k, v in model["textures"].items()}
    g = model["display"]["gui"]["rotation"]
    view = rot("x", g[0]) @ rot("y", g[1]) @ rot("z", g[2])
    for e in model["elements"]:
        ft = (e["from"], e["to"])
        r = e.get("rotation")
        for name, face in e["faces"].items():
            corners, shade = FACES[name]
            pts = np.array([[ft[c[i]][i] for i in range(3)] for c in corners], float)
            if r and r["angle"]:
                o = np.array(r["origin"], float)
                pts = (pts - o) @ rot(r["axis"], r["angle"]).T + o
            pts = (pts - 8) @ view.T
            u1, v1, u2, v2 = face["uv"]
            uv = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
            k = face.get("rotation", 0) // 90
            uv = uv[k:] + uv[:k]
            yield pts, np.array(uv) / 16, tex[face["texture"][1:]], shade

def render(model):
    qs = list(quads(model))
    allp = np.concatenate([q[0] for q in qs])
    lo, hi = allp[:, :2].min(0), allp[:, :2].max(0)
    n = ICON * SS
    scale = (n - 2 * SS) / (hi - lo).max()
    off = (n - (hi - lo) * scale) / 2
    img, depth = np.zeros((n, n, 4)), np.full((n, n), -1e9)
    ys, xs = np.mgrid[0:n, 0:n] + .5
    for pts, uv, tex, shade in qs:
        p = (pts[:, :2] - lo) * scale + off
        p[:, 1] = n - p[:, 1]  # screen y points down
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = p[list(tri)]
            det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(det) < 1e-9: continue
            w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / det
            w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / det
            w2 = 1 - w0 - w1
            z = w0 * pts[tri[0], 2] + w1 * pts[tri[1], 2] + w2 * pts[tri[2], 2]
            hit = (w0 >= 0) & (w1 >= 0) & (w2 >= 0) & (z > depth)
            t = w0[..., None] * uv[tri[0]] + w1[..., None] * uv[tri[1]] + w2[..., None] * uv[tri[2]]
            h, w = tex.shape[:2]
            px = tex[np.clip((t[..., 1] * h).astype(int), 0, h - 1), np.clip((t[..., 0] * w).astype(int), 0, w - 1)] / 255
            hit &= px[..., 3] > .5
            img[hit] = px[hit] * [shade, shade, shade, 1]
            depth[hit] = z[hit]
    small = img.reshape(ICON, SS, ICON, SS, 4)
    alpha = small[..., 3].mean((1, 3))
    rgb = (small[..., :3] * small[..., 3:]).sum((1, 3)) / np.maximum(small[..., 3:].sum((1, 3)), 1e-9)
    res = np.dstack([rgb, alpha > .4]) * 255
    return Image.fromarray(res.astype(np.uint8), "RGBA")

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(data, open(path, "w"), indent=2)

ids = sorted(BASE)
for i in ids:
    os.makedirs(f"{out}/pack/textures/items/dm", exist_ok=True)
    icon = render(json.load(open(f"{java}/models/item/{i}.json")))
    # Bedrock first person holds a handheld icon so that its top-left points forward and its bottom-left points
    # down: the gui render (muzzle top-left, grip bottom-left) is already the right way round. Do not mirror it.
    icon.save(f"{out}/pack/textures/items/dm/{i}.png")

# Bedrock clients cache a pack by uuid + version, so the version must change whenever the content does.
import zlib
content = b"".join(open(f, "rb").read() for f in sorted(glob.glob(f"{out}/pack/textures/items/dm/*.png") + glob.glob(f"{java}/sounds/*.ogg")))
version = [1, 0, zlib.crc32(content) % 60000]  # Bedrock version parts are 16-bit
ns = uuid.UUID("6f0e7c0e-5d2f-4c55-9b55-3a8c5d1f9a10")  # fixed, so pack UUIDs are stable across regenerations
write(f"{out}/pack/manifest.json", {"format_version": 2,
      "header": {"name": "Deathmatch weapons", "description": "Weapon icons and sounds", "uuid": str(uuid.uuid5(ns, "header")),
                 "version": version, "min_engine_version": [1, 21, 0]},
      "modules": [{"type": "resources", "uuid": str(uuid.uuid5(ns, "resources")), "version": version}]})
write(f"{out}/pack/textures/item_texture.json", {"resource_pack_name": "dm", "texture_name": "atlas.items",
      "texture_data": {f"dm_{i}": {"textures": f"textures/items/dm/{i}"} for i in ids}})
sounds = json.load(open(f"{java}/sounds.json"))
write(f"{out}/pack/sounds/sound_definitions.json", {"format_version": "1.14.0", "sound_definitions": {
      f"dm:{k}": {"category": "player", "max_distance": v["sounds"][0]["attenuation_distance"],
                  "sounds": [f"sounds/dm/{k}"]} for k, v in sounds.items()}})
write(f"{out}/mappings.json", {"format_version": 2, "items": {f"minecraft:{BASE[i]}": [
      {"type": "definition", "model": f"dm:{i}", "bedrock_identifier": f"dm:{i}", "display_name": NAME[i],
       "bedrock_options": {"icon": f"dm_{i}", "display_handheld": not i.startswith("grenade")}}] for i in ids}})
