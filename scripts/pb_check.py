#!/usr/bin/env python3
"""Check admins_list collection schema."""
import json, os, urllib.request, sys

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

req = urllib.request.Request(f"{PB}/api/collections/admins_list",
    headers={"Authorization": token})
col = json.loads(urllib.request.urlopen(req).read())
print(json.dumps(col, indent=2))
