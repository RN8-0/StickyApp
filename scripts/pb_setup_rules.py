#!/usr/bin/env python3
"""Set PocketBase API rules matching Firestore security rules."""
import json, os, urllib.request, urllib.error, sys

PB = sys.argv[1]
EMAIL = os.environ.get("PB_ADMIN_EMAIL", "admin@sticky.app")
PASSWORD = os.environ.get("PB_ADMIN_PASSWORD")

if not PASSWORD:
    raise SystemExit("PB_ADMIN_PASSWORD is required")

# Auth
auth = json.loads(urllib.request.urlopen(urllib.request.Request(
    f"{PB}/api/collections/_superusers/auth-with-password",
    data=json.dumps({"identity": EMAIL, "password": PASSWORD}).encode(),
    headers={"Content-Type":"application/json"}, method="POST"
)).read())
token = auth["token"]
print("AUTH OK")

def update_collection(name, rules):
    req = urllib.request.Request(
        f"{PB}/api/collections/{name}",
        data=json.dumps(rules).encode(),
        headers={"Authorization": token, "Content-Type": "application/json"},
        method="PATCH"
    )
    try:
        result = json.loads(urllib.request.urlopen(req).read())
        print(f"  OK: {name}")
        return True
    except urllib.error.HTTPError as e:
        print(f"  FAIL: {name} - {e.read().decode()[:200]}")
        return False

# Admin check rule helper
# In PocketBase, we use admins_list collection to check admin status
ADMIN_CHECK = '@collection.admins_list.email ?= @request.auth.email'

rules = {
    # stickers: public read, admin write, anyone can update counts
    "stickers": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN_CHECK,
        "updateRule": "",  # Allow count updates from anyone
        "deleteRule": ADMIN_CHECK,
    },
    # premium_stickers: same as stickers
    "premium_stickers": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN_CHECK,
        "updateRule": "",
        "deleteRule": ADMIN_CHECK,
    },
    # messages: anyone can create, admin read/update/delete
    "messages": {
        "listRule": ADMIN_CHECK,
        "viewRule": ADMIN_CHECK,
        "createRule": "",
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # suggestions: anyone can create, admin read/update/delete
    "suggestions": {
        "listRule": ADMIN_CHECK,
        "viewRule": ADMIN_CHECK,
        "createRule": "",
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # notifications: admin write, public read
    "notifications": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN_CHECK,
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # content_reports: auth create, admin read/update/delete
    "content_reports": {
        "listRule": ADMIN_CHECK,
        "viewRule": ADMIN_CHECK,
        "createRule": "@request.auth.id != ''",
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # admins_list: admin read only, no write via API
    "admins_list": {
        "listRule": ADMIN_CHECK,
        "viewRule": ADMIN_CHECK,
        "createRule": None,
        "updateRule": None,
        "deleteRule": None,
    },
    # publisher_users: public read, admin write
    "publisher_users": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN_CHECK,
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # draft_stickers: admin only
    "draft_stickers": {
        "listRule": ADMIN_CHECK,
        "viewRule": ADMIN_CHECK,
        "createRule": ADMIN_CHECK,
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # app_settings: public read, admin write
    "app_settings": {
        "listRule": "",
        "viewRule": "",
        "createRule": ADMIN_CHECK,
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # user_submissions: auth create own, read own+admin, update own draft+admin
    "user_submissions": {
        "listRule": "@request.auth.id != '' && (user_id = @request.auth.id || " + ADMIN_CHECK + ")",
        "viewRule": "@request.auth.id != '' && (user_id = @request.auth.id || " + ADMIN_CHECK + ")",
        "createRule": "@request.auth.id != ''",
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # user_profiles: public read, own create/update, admin all
    "user_profiles": {
        "listRule": "",
        "viewRule": "",
        "createRule": "@request.auth.id != ''",
        "updateRule": "@request.auth.id != '' && (user_id = @request.auth.id || " + ADMIN_CHECK + ")",
        "deleteRule": ADMIN_CHECK,
    },
    # purchased_packs: auth read/create, admin update/delete
    "purchased_packs": {
        "listRule": "@request.auth.id != ''",
        "viewRule": "@request.auth.id != ''",
        "createRule": "@request.auth.id != ''",
        "updateRule": ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
    # users (auth collection): own read/write, admin all
    "users": {
        "listRule": "@request.auth.id = id || " + ADMIN_CHECK,
        "viewRule": "@request.auth.id = id || " + ADMIN_CHECK,
        "createRule": "",
        "updateRule": "@request.auth.id = id || " + ADMIN_CHECK,
        "deleteRule": ADMIN_CHECK,
    },
}

ok = 0
for name, r in rules.items():
    print(f"Setting rules for '{name}'...")
    if update_collection(name, r):
        ok += 1

print(f"\nDone: {ok}/{len(rules)} collections updated")
