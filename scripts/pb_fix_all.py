#!/usr/bin/env python3
"""Fix PocketBase collections - add missing fields and set API rules."""
import json, os, urllib.request, urllib.error, sys

PB = sys.argv[1]
EMAIL = os.environ.get("PB_ADMIN_EMAIL", "admin@sticky.app")
PASSWORD = os.environ.get("PB_ADMIN_PASSWORD")

if not PASSWORD:
    raise SystemExit("PB_ADMIN_PASSWORD is required")

auth = json.loads(urllib.request.urlopen(urllib.request.Request(
    f"{PB}/api/collections/_superusers/auth-with-password",
    data=json.dumps({"identity": EMAIL, "password": PASSWORD}).encode(),
    headers={"Content-Type":"application/json"}, method="POST"
)).read())
token = auth["token"]
print("AUTH OK")

def api(method, path, data=None):
    headers = {"Authorization": token, "Content-Type": "application/json"}
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(f"{PB}{path}", data=body, headers=headers, method=method)
    try:
        return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        print(f"  ERR {e.code} {path}: {err[:300]}")
        return None

def get_collection(name):
    return api("GET", f"/api/collections/{name}")

def add_fields(name, new_fields):
    col = get_collection(name)
    if not col:
        print(f"  Collection {name} not found!")
        return False
    existing = col.get("fields", [])
    existing_names = {f["name"] for f in existing}
    added = 0
    for nf in new_fields:
        if nf["name"] not in existing_names:
            existing.append(nf)
            added += 1
    if added > 0:
        result = api("PATCH", f"/api/collections/{name}", {"fields": existing})
        if result:
            print(f"  {name}: added {added} fields")
            return True
        return False
    else:
        print(f"  {name}: all fields exist")
        return True

# File field helper
def file_field(name, max_select=1, max_size=5242880, mimes=None):
    if mimes is None:
        mimes = ["image/webp", "image/png", "image/gif"]
    return {"name": name, "type": "file", "options": {"maxSelect": max_select, "maxSize": max_size, "mimeTypes": mimes}}

# Language fields
lang_fields = []
for lang in ["tr","de","fr","es","pt","it","ru","ar","hi","ja","ko","zh","th","vi","id","fil"]:
    lang_fields.append({"name": f"name_{lang}", "type": "text"})

# ============ FIX FIELDS ============
print("\n=== FIXING FIELDS ===")

collections_fields = {
    "stickers": [
        {"name": "name", "type": "text", "required": True},
        {"name": "publisher", "type": "text"},
        {"name": "category", "type": "text"},
        {"name": "is_animated", "type": "bool"},
        {"name": "is_active", "type": "bool"},
        {"name": "tray_image_file", "type": "text"},
        {"name": "tray_url", "type": "url"},
        {"name": "sticker_data", "type": "json"},
        {"name": "download_count", "type": "number"},
        {"name": "view_count", "type": "number"},
        {"name": "favorite_count", "type": "number"},
        {"name": "whatsapp_add_count", "type": "number"},
        {"name": "fake_download_base", "type": "number"},
        {"name": "source", "type": "text"},
        {"name": "telegram_set_name", "type": "text"},
        {"name": "disabled_reason", "type": "text"},
        file_field("images", 99, 10485760),
        file_field("tray_image", 1, 5242880, ["image/webp","image/png"]),
    ] + lang_fields,
    "premium_stickers": [
        {"name": "name", "type": "text", "required": True},
        {"name": "publisher", "type": "text"},
        {"name": "category", "type": "text"},
        {"name": "is_animated", "type": "bool"},
        {"name": "is_active", "type": "bool"},
        {"name": "tray_image_file", "type": "text"},
        {"name": "tray_url", "type": "url"},
        {"name": "sticker_data", "type": "json"},
        {"name": "download_count", "type": "number"},
        {"name": "view_count", "type": "number"},
        {"name": "favorite_count", "type": "number"},
        {"name": "whatsapp_add_count", "type": "number"},
        {"name": "fake_download_base", "type": "number"},
        {"name": "price_try", "type": "number"},
        {"name": "price_usd", "type": "number"},
        {"name": "price_eur", "type": "number"},
        {"name": "source", "type": "text"},
        {"name": "telegram_set_name", "type": "text"},
        {"name": "disabled_reason", "type": "text"},
        file_field("images", 99, 10485760),
        file_field("tray_image", 1, 5242880, ["image/webp","image/png"]),
    ] + lang_fields,
    "messages": [
        {"name": "title", "type": "text"},
        {"name": "body", "type": "text"},
        {"name": "email", "type": "email"},
        {"name": "message", "type": "text"},
    ],
    "suggestions": [
        {"name": "suggestion", "type": "text"},
        {"name": "email", "type": "email"},
        {"name": "user_id", "type": "text"},
    ],
    "notifications": [
        {"name": "title", "type": "text", "required": True},
        {"name": "body", "type": "text"},
        {"name": "image_url", "type": "url"},
        {"name": "sent", "type": "bool"},
    ],
    "content_reports": [
        {"name": "pack_id", "type": "text", "required": True},
        {"name": "reporter_id", "type": "text"},
        {"name": "reason", "type": "text"},
        {"name": "pack_type", "type": "text"},
    ],
    "admins_list": [
        {"name": "email", "type": "email", "required": True},
    ],
    "publisher_users": [
        {"name": "name", "type": "text", "required": True},
        {"name": "email", "type": "email"},
        {"name": "avatar_url", "type": "url"},
    ],
    "draft_stickers": [
        {"name": "draft_data", "type": "json"},
        {"name": "name", "type": "text"},
        {"name": "status", "type": "text"},
    ],
    "app_settings": [
        {"name": "key", "type": "text", "required": True},
        {"name": "value", "type": "json"},
    ],
    "user_submissions": [
        {"name": "pack_name", "type": "text", "required": True},
        {"name": "publisher_name", "type": "text"},
        {"name": "category", "type": "text"},
        {"name": "user_id", "type": "text"},
        {"name": "user_email", "type": "email"},
        {"name": "display_name", "type": "text"},
        {"name": "status", "type": "text"},
        {"name": "flag_reasons", "type": "json"},
        {"name": "sticker_data", "type": "json"},
        file_field("images", 99, 5242880),
    ],
    "user_profiles": [
        {"name": "user_id", "type": "text"},
        {"name": "display_name", "type": "text"},
        {"name": "packs_published", "type": "number"},
        file_field("avatar", 1, 2097152, ["image/webp","image/png","image/jpeg"]),
    ],
    "purchased_packs": [
        {"name": "user_id", "type": "text", "required": True},
        {"name": "pack_id", "type": "text", "required": True},
        {"name": "order_id", "type": "text"},
        {"name": "purchase_date", "type": "date"},
    ],
}

for name, fields in collections_fields.items():
    add_fields(name, fields)

# ============ SET API RULES ============
print("\n=== SETTING API RULES ===")

ADMIN = '@collection.admins_list.email ?= @request.auth.email'

rules = {
    "stickers": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN,
        "updateRule": "",
        "deleteRule": ADMIN,
    },
    "premium_stickers": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN,
        "updateRule": "",
        "deleteRule": ADMIN,
    },
    "messages": {
        "listRule": ADMIN,
        "viewRule": ADMIN,
        "createRule": "",
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "suggestions": {
        "listRule": ADMIN,
        "viewRule": ADMIN,
        "createRule": "",
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "notifications": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN,
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "content_reports": {
        "listRule": ADMIN,
        "viewRule": ADMIN,
        "createRule": "@request.auth.id != ''",
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "admins_list": {
        "listRule": ADMIN,
        "viewRule": ADMIN,
        "createRule": None,
        "updateRule": None,
        "deleteRule": None,
    },
    "publisher_users": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN,
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "draft_stickers": {
        "listRule": ADMIN,
        "viewRule": ADMIN,
        "createRule": ADMIN,
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "app_settings": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN,
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "user_submissions": {
        "listRule": f"@request.auth.id != '' && (user_id = @request.auth.id || {ADMIN})",
        "viewRule": f"@request.auth.id != '' && (user_id = @request.auth.id || {ADMIN})",
        "createRule": "@request.auth.id != ''",
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "user_profiles": {
        "listRule": "",
        "viewRule": "",
        "createRule": "@request.auth.id != ''",
        "updateRule": f"@request.auth.id != '' && (user_id = @request.auth.id || {ADMIN})",
        "deleteRule": ADMIN,
    },
    "purchased_packs": {
        "listRule": "@request.auth.id != ''",
        "viewRule": "@request.auth.id != ''",
        "createRule": "@request.auth.id != ''",
        "updateRule": ADMIN,
        "deleteRule": ADMIN,
    },
    "users": {
        "listRule": f"@request.auth.id = id || {ADMIN}",
        "viewRule": f"@request.auth.id = id || {ADMIN}",
        "createRule": "",
        "updateRule": f"@request.auth.id = id || {ADMIN}",
        "deleteRule": ADMIN,
    },
}

ok = 0
for name, r in rules.items():
    result = api("PATCH", f"/api/collections/{name}", r)
    if result:
        print(f"  OK: {name}")
        ok += 1
    else:
        print(f"  FAIL: {name}")

print(f"\nDone: {ok}/{len(rules)} rules set")
