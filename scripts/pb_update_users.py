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

# Get current users collection
req = urllib.request.Request(f"{PB}/api/collections/users", headers={"Authorization": token})
users = json.loads(urllib.request.urlopen(req).read())
fields = users.get("fields", [])
print(f"Current fields: {len(fields)}")
for f in fields:
    print(f"  - {f['name']} ({f['type']})")

# Add new fields
new_fields = [
    {"name": "is_premium", "type": "bool"},
    {"name": "premium_type", "type": "text"},
    {"name": "premium_expiry", "type": "number"},
    {"name": "subscription_source", "type": "text"},
    {"name": "device_id", "type": "text"},
    {"name": "last_sync", "type": "date"},
]

existing_names = {f["name"] for f in fields}
for nf in new_fields:
    if nf["name"] not in existing_names:
        fields.append(nf)
        print(f"  + Adding {nf['name']}")

# Update
update_req = urllib.request.Request(
    f"{PB}/api/collections/users",
    data=json.dumps({"fields": fields}).encode(),
    headers={"Authorization": token, "Content-Type": "application/json"},
    method="PATCH"
)
result = json.loads(urllib.request.urlopen(update_req).read())
print(f"Updated! New field count: {len(result.get('fields', []))}")
