#!/usr/bin/env python3
"""
Restore PocketBase stickers collection from sticky-images filesystem.
Usage: python3 pb_restore_from_images.py
Runs directly on server via SSH.
"""
import json, os, sys, urllib.request, urllib.error

PB = "https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io"
EMAIL = "arainunger@gmail.com"
PASS = os.environ.get("PB_ADMIN_PASSWORD", "StickyAdmin2026!")
IMAGES_BASE = "https://sticky-images.46.225.95.201.sslip.io"
STICKERS_DIR = "/opt/sticky-images/stickers"

def api(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(f"{PB}{path}", data=body, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        return {"_error": e.code, "_msg": err[:300]}

# ─── Auth ────────────────────────────────────────────────────────────────────
print("Authenticating...")
auth = api("POST", "/api/collections/_superusers/auth-with-password",
           {"identity": EMAIL, "password": PASS})
if not auth or not auth.get("token"):
    sys.exit(f"AUTH FAILED: {auth}")
token = auth["token"]
print("AUTH OK\n")

# ─── Create stickers collection if missing ───────────────────────────────────
def ensure_collection(name):
    r = api("GET", f"/api/collections/{name}", token=token)
    if r and r.get("id"):
        print(f"  Collection '{name}' already exists.")
        return
    lang_fields = [{"name": f"name_{l}", "type": "text"} for l in
                   ["tr","de","fr","es","pt","it","ru","ar","hi","ja","ko","zh","th","vi","id","fil"]]
    schema = {
        "name": name,
        "type": "base",
        "fields": [
            {"name": "name", "type": "text", "required": True},
            {"name": "publisher", "type": "text"},
            {"name": "publisher_email", "type": "text"},
            {"name": "publisher_user_id", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "is_premium", "type": "bool"},
            {"name": "is_animated", "type": "bool"},
            {"name": "is_active", "type": "bool"},
            {"name": "is_popular", "type": "bool"},
            {"name": "tray_url", "type": "text"},
            {"name": "tray_image_file", "type": "text"},
            {"name": "stickers", "type": "json"},
            {"name": "sticker_count", "type": "number"},
            {"name": "download_count", "type": "number"},
            {"name": "view_count", "type": "number"},
            {"name": "favorite_count", "type": "number"},
            {"name": "fake_download_base", "type": "number"},
            {"name": "image_data_version", "type": "text"},
            {"name": "privacy_policy_website", "type": "text"},
            {"name": "license_agreement_website", "type": "text"},
            {"name": "batch_source", "type": "text"},
        ] + lang_fields,
        "listRule": "",
        "viewRule": "",
        "createRule": None,
        "updateRule": None,
        "deleteRule": None,
    }
    r = api("POST", "/api/collections", schema, token=token)
    if r and r.get("id"):
        print(f"  Created collection '{name}'")
    else:
        print(f"  Failed to create '{name}': {r}")

ensure_collection("stickers")
ensure_collection("premium_stickers")

# ─── List existing packs to avoid duplicates ─────────────────────────────────
existing = set()
for col in ("stickers", "premium_stickers"):
    page = 1
    while True:
        r = api("GET", f"/api/collections/{col}/records?perPage=200&page={page}&fields=name", token=token)
        if not r or not r.get("items"):
            break
        for item in r["items"]:
            existing.add(item.get("name", "").lower())
        if len(r["items"]) < 200:
            break
        page += 1

print(f"Existing packs: {len(existing)}\n")

# ─── Import packs from filesystem ────────────────────────────────────────────
pack_dirs = sorted(os.listdir(STICKERS_DIR))
created = skipped = errors = 0

for pack_dir in pack_dirs:
    pack_path = os.path.join(STICKERS_DIR, pack_dir)
    if not os.path.isdir(pack_path):
        continue

    pack_name = pack_dir.rstrip("_")  # remove trailing underscore convention
    pack_name_clean = pack_name.replace("_", " ").title()

    if pack_dir.lower() in existing or pack_name.lower() in existing:
        print(f"  SKIP (exists): {pack_dir}")
        skipped += 1
        continue

    # List sticker files sorted
    files = sorted([f for f in os.listdir(pack_path) if f.endswith(".webp")])
    if not files:
        print(f"  SKIP (empty): {pack_dir}")
        skipped += 1
        continue

    # Build sticker list
    sticker_list = []
    for f in files:
        url = f"{IMAGES_BASE}/stickers/{pack_dir}/{f}"
        sticker_list.append({"image_file": f, "url": url, "emojis": ["⭐"]})

    tray_url = f"{IMAGES_BASE}/stickers/{pack_dir}/{files[0]}"

    record = {
        "name": pack_dir,
        "publisher": "Sticky",
        "category": "other",
        "is_active": True,
        "is_animated": False,
        "is_popular": False,
        "is_premium": False,
        "tray_url": tray_url,
        "tray_image_file": files[0],
        "stickers": sticker_list,
        "sticker_count": len(sticker_list),
        "download_count": 0,
        "view_count": 0,
        "favorite_count": 0,
        "fake_download_base": 0,
        "image_data_version": "1",
    }

    r = api("POST", "/api/collections/stickers/records", record, token=token)
    if r and r.get("id"):
        print(f"  OK: {pack_dir} ({len(sticker_list)} stickers)")
        created += 1
    else:
        print(f"  ERR: {pack_dir} → {r}")
        errors += 1

print(f"\n{'='*50}")
print(f"Created: {created}  Skipped: {skipped}  Errors: {errors}")
