#!/usr/bin/env python3
"""Patch existing PocketBase collections with fields required by the migrated Sticky app."""
import json
import os
import sys
import urllib.error
import urllib.request

PB = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("PB_URL", "http://localhost:8090")
EMAIL = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("PB_ADMIN_EMAIL", "admin@sticky.app")
PASS = sys.argv[3] if len(sys.argv) > 3 else os.environ.get("PB_ADMIN_PASSWORD")

if not PASS:
    raise SystemExit("PB_ADMIN_PASSWORD is required")


def api(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(f"{PB}{path}", data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            text = resp.read().decode()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        raise RuntimeError(f"{method} {path} failed: {exc.code} {detail[:500]}") from exc


def file_opts(max_select=1, max_size=5242880, mimes=None):
    return {"maxSelect": max_select, "maxSize": max_size, "mimeTypes": mimes or ["image/webp", "image/png", "image/gif"]}


def field(name, typ, **extra):
    data = {"name": name, "type": typ}
    data.update(extra)
    return data


LANG_FIELDS = [field(f"name_{lang}", "text") for lang in ["tr", "de", "fr", "es", "pt", "it", "ru", "ar", "hi", "ja", "ko", "zh", "th", "vi", "id", "fil"]]

PACK_FIELDS = [
    field("publisher_email", "email"),
    field("publisher_user_id", "text"),
    field("is_premium", "bool"),
    field("is_popular", "bool"),
    field("stickers", "json"),
    field("sticker_count", "number"),
    field("image_data_version", "text"),
    field("privacy_policy_website", "url"),
    field("license_agreement_website", "url"),
]

PATCHES = {
    "stickers": PACK_FIELDS + LANG_FIELDS,
    "premium_stickers": PACK_FIELDS + LANG_FIELDS,
    "draft_stickers": [
        field("publisher", "text"), field("publisher_email", "email"), field("publisher_user_id", "text"),
        field("category", "text"), field("is_premium", "bool"), field("is_animated", "bool"),
        field("is_active", "bool"), field("is_popular", "bool"), field("tray_image_file", "text"),
        field("tray_url", "url"), field("stickers", "json"), field("sticker_data", "json"),
        field("sticker_count", "number"), field("image_data_version", "text"), field("download_count", "number"),
        field("view_count", "number"), field("favorite_count", "number"), field("whatsapp_add_count", "number"),
        field("fake_download_base", "number"), field("privacy_policy_website", "url"),
        field("license_agreement_website", "url"), field("source", "text"), field("telegram_set_name", "text"),
        field("disabled_reason", "text"), field("images", "file", options=file_opts(99, 10485760)),
        field("tray_image", "file", options=file_opts(1, 5242880, ["image/webp", "image/png"])),
    ] + LANG_FIELDS,
    "messages": [
        field("name", "text"), field("subject", "text"), field("timestamp", "number"),
        field("date", "text"), field("time", "text"), field("status", "text"),
    ],
    "suggestions": [
        field("category", "text"), field("timestamp", "number"), field("date", "text"), field("time", "text"),
    ],
    "notifications": [
        field("message", "text"), field("user_id", "text"), field("pack_id", "text"),
        field("from", "text"), field("read", "bool"),
    ],
    "publisher_users": [
        field("display_name", "text"), field("bio", "text"), field("category", "text"),
        field("packs_published", "number"), field("total_downloads", "number"),
        field("created_at", "date"), field("is_active", "bool"),
    ],
    "user_submissions": [
        field("user_email", "email"),
        field("description", "text"), field("rejection_reason", "text"), field("stickers", "json"),
        field("sticker_data", "json"), field("sticker_count", "number"), field("sticker_pack_id", "text"),
        field("source_pack_id", "text"), field("is_animated", "bool"),
        field("created_at", "date"), field("processed_at", "date"), field("images", "file", options=file_opts(99, 5242880)),
    ],
    "user_profiles": [
        field("device_id", "text"), field("email", "email"), field("photo_url", "url"),
        field("packs_published", "number"), field("total_downloads", "number"), field("total_favorites", "number"), field("joined_at", "date"),
    ],
    "users": [
        field("uid", "text"), field("name", "text"), field("display_name", "text"), field("photo_url", "url"),
        field("subscription_history", "json"), field("cancelled_at", "date"), field("cancelled_reason", "text"),
        field("device_info", "json"), field("favorite_packs", "json"), field("total_stickers_added", "number"),
        field("custom_packs_count", "number"), field("packs_published", "number"),
    ],
}


def schema_key(collection):
    if "schema" in collection:
        return "schema"
    if "fields" in collection:
        return "fields"
    return "schema"


def patch_collection(token, name, wanted_fields):
    try:
        collection = api("GET", f"/api/collections/{name}", token=token)
    except RuntimeError as exc:
        print(f"SKIP {name}: {exc}")
        return False

    key = schema_key(collection)
    current = collection.get(key) or []
    existing = {item.get("name") for item in current}
    missing = [item for item in wanted_fields if item["name"] not in existing]

    if not missing:
        print(f"OK   {name}: no missing fields")
        return True

    next_schema = current + missing
    api("PATCH", f"/api/collections/{name}", {key: next_schema}, token=token)
    print(f"OK   {name}: added {', '.join(item['name'] for item in missing)}")
    return True


def main():
    auth = api("POST", "/api/collections/_superusers/auth-with-password", {"identity": EMAIL, "password": PASS})
    token = auth.get("token")
    if not token:
        raise SystemExit("PocketBase auth failed")

    ok = 0
    for name, fields in PATCHES.items():
        if patch_collection(token, name, fields):
            ok += 1
    print(f"Done: patched/checked {ok}/{len(PATCHES)} collections")


if __name__ == "__main__":
    main()
