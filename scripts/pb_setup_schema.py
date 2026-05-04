#!/usr/bin/env python3
"""Create PocketBase collections for Sticky App migration from Firebase."""
import json, os, urllib.request, urllib.error, sys

PB = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("PB_ADMIN_EMAIL", "admin@sticky.app")
PASS = sys.argv[3] if len(sys.argv) > 3 else os.environ.get("PB_ADMIN_PASSWORD")

if not PASS:
    raise SystemExit("PB_ADMIN_PASSWORD is required")

def api(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(f"{PB}{path}", data=body, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        print(f"  ERR {e.code}: {err[:200]}")
        return None

# Authenticate
print("Authenticating...")
auth = api("POST", "/api/collections/_superusers/auth-with-password",
           {"identity": EMAIL, "password": PASS})
if not auth or not auth.get("token"):
    print("AUTH FAILED")
    sys.exit(1)
token = auth["token"]
print("AUTH OK\n")

# Helper to build file field options
def file_opts(max_select=1, max_size=5242880, mimes=None):
    if mimes is None:
        mimes = ["image/webp", "image/png", "image/gif"]
    return {"maxSelect": max_select, "maxSize": max_size, "mimeTypes": mimes}

# Language name fields
lang_fields = []
for lang in ["tr","de","fr","es","pt","it","ru","ar","hi","ja","ko","zh","th","vi","id","fil"]:
    lang_fields.append({"name": f"name_{lang}", "type": "text"})

# Collection definitions
collections = [
    {
        "name": "stickers",
        "type": "base",
        "schema": [
            {"name": "name", "type": "text", "required": True},
            {"name": "publisher", "type": "text"},
            {"name": "publisher_email", "type": "email"},
            {"name": "publisher_user_id", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "is_premium", "type": "bool"},
            {"name": "is_animated", "type": "bool"},
            {"name": "is_active", "type": "bool"},
            {"name": "is_popular", "type": "bool"},
            {"name": "tray_image_file", "type": "text"},
            {"name": "tray_url", "type": "url"},
            {"name": "stickers", "type": "json"},
            {"name": "sticker_data", "type": "json"},
            {"name": "sticker_count", "type": "number"},
            {"name": "image_data_version", "type": "text"},
            {"name": "download_count", "type": "number"},
            {"name": "view_count", "type": "number"},
            {"name": "favorite_count", "type": "number"},
            {"name": "whatsapp_add_count", "type": "number"},
            {"name": "fake_download_base", "type": "number"},
            {"name": "privacy_policy_website", "type": "url"},
            {"name": "license_agreement_website", "type": "url"},
            {"name": "batch_generated", "type": "bool"},
            {"name": "batch_source", "type": "text"},
            {"name": "batch_search_term", "type": "text"},
            {"name": "source", "type": "text"},
            {"name": "telegram_set_name", "type": "text"},
            {"name": "telegram_set_title", "type": "text"},
            {"name": "telegram_part", "type": "number"},
            {"name": "telegram_total_parts", "type": "number"},
            {"name": "created_at", "type": "text"},
            {"name": "disabled_reason", "type": "text"},
            {"name": "images", "type": "file", "options": file_opts(99, 10485760)},
            {"name": "tray_image", "type": "file", "options": file_opts(1, 5242880, ["image/webp","image/png"])},
        ] + lang_fields,
    },
    {
        "name": "premium_stickers",
        "type": "base",
        "schema": [
            {"name": "name", "type": "text", "required": True},
            {"name": "publisher", "type": "text"},
            {"name": "publisher_email", "type": "email"},
            {"name": "publisher_user_id", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "is_premium", "type": "bool"},
            {"name": "is_animated", "type": "bool"},
            {"name": "is_active", "type": "bool"},
            {"name": "is_popular", "type": "bool"},
            {"name": "tray_image_file", "type": "text"},
            {"name": "tray_url", "type": "url"},
            {"name": "stickers", "type": "json"},
            {"name": "sticker_data", "type": "json"},
            {"name": "sticker_count", "type": "number"},
            {"name": "image_data_version", "type": "text"},
            {"name": "download_count", "type": "number"},
            {"name": "view_count", "type": "number"},
            {"name": "favorite_count", "type": "number"},
            {"name": "whatsapp_add_count", "type": "number"},
            {"name": "fake_download_base", "type": "number"},
            {"name": "privacy_policy_website", "type": "url"},
            {"name": "license_agreement_website", "type": "url"},
            {"name": "price_try", "type": "number"},
            {"name": "price_usd", "type": "number"},
            {"name": "price_eur", "type": "number"},
            {"name": "source", "type": "text"},
            {"name": "telegram_set_name", "type": "text"},
            {"name": "disabled_reason", "type": "text"},
            {"name": "images", "type": "file", "options": file_opts(99, 10485760)},
            {"name": "tray_image", "type": "file", "options": file_opts(1, 5242880, ["image/webp","image/png"])},
        ] + lang_fields,
    },
    {
        "name": "messages",
        "type": "base",
        "schema": [
            {"name": "name", "type": "text"},
            {"name": "subject", "type": "text"},
            {"name": "title", "type": "text"},
            {"name": "body", "type": "text"},
            {"name": "email", "type": "email"},
            {"name": "message", "type": "text"},
            {"name": "timestamp", "type": "number"},
            {"name": "date", "type": "text"},
            {"name": "time", "type": "text"},
            {"name": "status", "type": "text"},
        ],
    },
    {
        "name": "suggestions",
        "type": "base",
        "schema": [
            {"name": "suggestion", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "email", "type": "email"},
            {"name": "user_id", "type": "text"},
            {"name": "timestamp", "type": "number"},
            {"name": "date", "type": "text"},
            {"name": "time", "type": "text"},
        ],
    },
    {
        "name": "notifications",
        "type": "base",
        "schema": [
            {"name": "title", "type": "text", "required": True},
            {"name": "body", "type": "text"},
            {"name": "message", "type": "text"},
            {"name": "user_id", "type": "text"},
            {"name": "pack_id", "type": "text"},
            {"name": "from", "type": "text"},
            {"name": "read", "type": "bool"},
            {"name": "image_url", "type": "url"},
            {"name": "sent", "type": "bool"},
        ],
    },
    {
        "name": "content_reports",
        "type": "base",
        "schema": [
            {"name": "pack_id", "type": "text", "required": True},
            {"name": "reporter_id", "type": "text"},
            {"name": "reason", "type": "text"},
            {"name": "pack_type", "type": "text"},
        ],
    },
    {
        "name": "admins_list",
        "type": "base",
        "schema": [
            {"name": "email", "type": "email", "required": True},
        ],
    },
    {
        "name": "publisher_users",
        "type": "base",
        "schema": [
            {"name": "name", "type": "text", "required": True},
            {"name": "email", "type": "email"},
            {"name": "display_name", "type": "text"},
            {"name": "avatar_url", "type": "url"},
            {"name": "bio", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "packs_published", "type": "number"},
            {"name": "total_downloads", "type": "number"},
            {"name": "created_at", "type": "date"},
            {"name": "is_active", "type": "bool"},
        ],
    },
    {
        "name": "draft_stickers",
        "type": "base",
        "schema": [
            {"name": "draft_data", "type": "json"},
            {"name": "name", "type": "text"},
            {"name": "status", "type": "text"},
            {"name": "publisher", "type": "text"},
            {"name": "publisher_email", "type": "email"},
            {"name": "publisher_user_id", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "is_premium", "type": "bool"},
            {"name": "is_animated", "type": "bool"},
            {"name": "is_active", "type": "bool"},
            {"name": "is_popular", "type": "bool"},
            {"name": "tray_image_file", "type": "text"},
            {"name": "tray_url", "type": "url"},
            {"name": "stickers", "type": "json"},
            {"name": "sticker_data", "type": "json"},
            {"name": "sticker_count", "type": "number"},
            {"name": "image_data_version", "type": "text"},
            {"name": "download_count", "type": "number"},
            {"name": "view_count", "type": "number"},
            {"name": "favorite_count", "type": "number"},
            {"name": "whatsapp_add_count", "type": "number"},
            {"name": "fake_download_base", "type": "number"},
            {"name": "privacy_policy_website", "type": "url"},
            {"name": "license_agreement_website", "type": "url"},
            {"name": "source", "type": "text"},
            {"name": "telegram_set_name", "type": "text"},
            {"name": "disabled_reason", "type": "text"},
            {"name": "images", "type": "file", "options": file_opts(99, 10485760)},
            {"name": "tray_image", "type": "file", "options": file_opts(1, 5242880, ["image/webp","image/png"])},
        ] + lang_fields,
    },
    {
        "name": "app_settings",
        "type": "base",
        "schema": [
            {"name": "key", "type": "text", "required": True},
            {"name": "value", "type": "json"},
        ],
    },
    {
        "name": "user_submissions",
        "type": "base",
        "schema": [
            {"name": "pack_name", "type": "text", "required": True},
            {"name": "publisher_name", "type": "text"},
            {"name": "category", "type": "text"},
            {"name": "user_id", "type": "text"},
            {"name": "user_email", "type": "email"},
            {"name": "display_name", "type": "text"},
            {"name": "description", "type": "text"},
            {"name": "status", "type": "text"},
            {"name": "flag_reasons", "type": "json"},
            {"name": "rejection_reason", "type": "text"},
            {"name": "sticker_data", "type": "json"},
            {"name": "stickers", "type": "json"},
            {"name": "sticker_count", "type": "number"},
            {"name": "sticker_pack_id", "type": "text"},
            {"name": "created_at", "type": "date"},
            {"name": "processed_at", "type": "date"},
            {"name": "images", "type": "file", "options": file_opts(99, 5242880)},
        ],
    },
    {
        "name": "user_profiles",
        "type": "base",
        "schema": [
            {"name": "user_id", "type": "text"},
            {"name": "device_id", "type": "text"},
            {"name": "display_name", "type": "text"},
            {"name": "email", "type": "email"},
            {"name": "photo_url", "type": "url"},
            {"name": "packs_published", "type": "number"},
            {"name": "total_downloads", "type": "number"},
            {"name": "total_favorites", "type": "number"},
            {"name": "joined_at", "type": "date"},
            {"name": "avatar", "type": "file", "options": file_opts(1, 2097152, ["image/webp","image/png","image/jpeg"])},
        ],
    },
    {
        "name": "purchased_packs",
        "type": "base",
        "schema": [
            {"name": "user_id", "type": "text", "required": True},
            {"name": "pack_id", "type": "text", "required": True},
            {"name": "order_id", "type": "text"},
            {"name": "purchase_date", "type": "date"},
        ],
    },
    {
        "name": "users",
        "type": "auth",
        "schema": [
            {"name": "uid", "type": "text"},
            {"name": "name", "type": "text"},
            {"name": "display_name", "type": "text"},
            {"name": "photo_url", "type": "url"},
            {"name": "is_premium", "type": "bool"},
            {"name": "premium_type", "type": "text"},
            {"name": "premium_expiry", "type": "number"},
            {"name": "subscription_source", "type": "text"},
            {"name": "subscription_history", "type": "json"},
            {"name": "cancelled_at", "type": "date"},
            {"name": "cancelled_reason", "type": "text"},
            {"name": "device_id", "type": "text"},
            {"name": "device_info", "type": "json"},
            {"name": "favorite_packs", "type": "json"},
            {"name": "total_stickers_added", "type": "number"},
            {"name": "custom_packs_count", "type": "number"},
            {"name": "packs_published", "type": "number"},
            {"name": "last_sync", "type": "date"},
        ],
        "options": {
            "allowEmailAuth": True,
            "allowOAuth2Auth": True,
            "allowUsernameAuth": False,
            "minPasswordLength": 8,
        },
    },
]

# Create collections
ok = 0
fail = 0
for c in collections:
    name = c["name"]
    print(f"Creating '{name}'...", end=" ")
    result = api("POST", "/api/collections", c, token)
    if result:
        print("OK")
        ok += 1
    else:
        fail += 1

print(f"\nDone: {ok} created, {fail} failed out of {len(collections)}")
