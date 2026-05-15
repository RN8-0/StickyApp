#!/usr/bin/env node
/**
 * pb_fix_premium_schema.mjs
 *
 * Fixes the two PocketBase-side bugs behind the admin panel issues:
 *
 *  ISSUE 1 — premium packs publish without a name / cover.
 *    The `premium_stickers` collection was created with an incomplete schema:
 *    it is MISSING `name`, `name_en`, `category`, `publisher`, `is_active`,
 *    `is_animated`, `tray_url`, `tray_image_file`, the counters and the price
 *    fields. PocketBase silently DROPS any value sent for a field that does
 *    not exist, so publishing/editing a premium pack loses its name + cover,
 *    and the Android app falls back to showing the raw record id ("vzcps...").
 *    Fix: add every missing field so writes actually stick.
 *
 *  ISSUE 2 — "Recently Added" never updates.
 *    The Android app reads each pack's date from the built-in `created`
 *    field (StickerRepository.kt -> json.optString("created")). Neither
 *    `stickers` nor `premium_stickers` has a `created` field, so every pack
 *    parses to date 0 and the recency sort is a no-op.
 *    Fix: add an autodate `created` field to both collections. New packs get
 *    a real timestamp on creation; existing rows are backfilled by PocketBase.
 *
 * After the schema is fixed the script also BACKFILLS the already-broken
 * premium records (name / cover / is_active / category).
 *
 * Usage:
 *   PB_ADMIN_PASSWORD='your-superuser-password' node scripts/pb_fix_premium_schema.mjs
 *   # optional: PB_URL, PB_ADMIN_EMAIL overrides
 *   # add --dry-run to preview without writing
 */

const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
const DRY_RUN = process.argv.includes('--dry-run');

if (!ADMIN_PASSWORD) {
  console.error('PB_ADMIN_PASSWORD env var is required.');
  process.exit(1);
}

// ---------------------------------------------------------------------------
// Fields the premium_stickers collection MUST have to match `stickers`.
// Only the ones that are missing are added — adding is idempotent.
// `created` is the autodate field that powers the app's "Recently Added".
// ---------------------------------------------------------------------------
const TEXT = (name) => ({ name, type: 'text' });
const BOOL = (name) => ({ name, type: 'bool' });
const NUM = (name) => ({ name, type: 'number' });

const CREATED_FIELD = { name: 'created', type: 'autodate', onCreate: true, onUpdate: false };

const PREMIUM_REQUIRED_FIELDS = [
  TEXT('name'),
  TEXT('name_en'),
  TEXT('category'),
  TEXT('publisher'),
  TEXT('publisher_photo_url'),
  BOOL('is_active'),
  BOOL('is_animated'),
  // tray_url kept as plain text (not `url`) so proxy / non-canonical URLs are
  // never rejected by validation — that rejection is the exact class of bug
  // this script exists to kill.
  TEXT('tray_url'),
  TEXT('tray_image_file'),
  NUM('download_count'),
  NUM('view_count'),
  NUM('favorite_count'),
  NUM('like_count'),
  NUM('comment_count'),
  NUM('engagement_score'),
  NUM('fake_download_base'),
  TEXT('product_id'),
  // price fields as text: the Android app reads them with optString()
  TEXT('price_try'),
  TEXT('price_usd'),
  TEXT('price_eur'),
  CREATED_FIELD,
];

// stickers collection only needs the `created` field for Recently Added.
const STICKERS_REQUIRED_FIELDS = [CREATED_FIELD];

// ---------------------------------------------------------------------------

async function api(method, path, data, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = token;
  const res = await fetch(`${PB_URL}${path}`, {
    method,
    headers,
    body: data ? JSON.stringify(data) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : {};
}

async function authenticate() {
  const res = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  if (!res.ok) throw new Error(`Auth failed: ${res.status} ${await res.text()}`);
  return (await res.json()).token;
}

async function patchSchema(token, collectionName, wantedFields) {
  const collection = await api('GET', `/api/collections/${collectionName}`, null, token);
  const key = collection.fields ? 'fields' : 'schema';
  const current = collection[key] || [];
  const existing = new Set(current.map((f) => f.name));
  const missing = wantedFields.filter((f) => !existing.has(f.name));

  if (missing.length === 0) {
    console.log(`OK   ${collectionName}: schema already complete`);
    return;
  }
  console.log(`FIX  ${collectionName}: adding [${missing.map((f) => f.name).join(', ')}]`);
  if (DRY_RUN) return;
  await api('PATCH', `/api/collections/${collectionName}`, { [key]: [...current, ...missing] }, token);
  console.log(`     ${collectionName}: ${missing.length} field(s) added`);
}

function titleCaseFromSlug(raw) {
  return String(raw || '')
    .replace(/[._-]+/g, ' ')
    // split camelCase / PascalCase: "StoryOfLove" -> "Story Of Love"
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .map((w) => (w ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(' ')
    .trim();
}

async function getAllRecords(token, collectionName) {
  const records = [];
  let page = 1;
  while (true) {
    const data = await api(
      'GET',
      `/api/collections/${collectionName}/records?perPage=200&page=${page}`,
      null,
      token,
    );
    records.push(...(data.items || []));
    if (page >= (data.totalPages || 1)) break;
    page += 1;
  }
  return records;
}

async function backfillPremiumRecords(token) {
  const records = await getAllRecords(token, 'premium_stickers');
  console.log(`\nBackfilling ${records.length} premium_stickers record(s)...`);
  let fixed = 0;

  for (const r of records) {
    const patch = {};

    // name + name_en — derive a human name when missing
    const currentName = String(r.name || '').trim();
    if (!currentName) {
      const derived =
        titleCaseFromSlug(r.telegram_set_title) ||
        titleCaseFromSlug(r.name_tr) ||
        titleCaseFromSlug(r.batch_search_term) ||
        titleCaseFromSlug(r.telegram_set_name) ||
        `Premium ${String(r.id).slice(-4).toUpperCase()}`;
      patch.name = derived;
    }
    const finalName = patch.name || currentName;
    if (finalName && !String(r.name_en || '').trim()) patch.name_en = finalName;

    // cover image — promote first sticker if tray is missing
    if (!String(r.tray_url || '').trim()) {
      const first = Array.isArray(r.stickers) ? r.stickers[0] : null;
      if (first?.url) patch.tray_url = first.url;
      if (first?.image_file && !String(r.tray_image_file || '').trim()) {
        patch.tray_image_file = first.image_file;
      }
    }

    // visibility + category
    if (r.is_active !== true) patch.is_active = true;
    if (!String(r.category || '').trim()) patch.category = 'other';
    if (!String(r.publisher || '').trim()) patch.publisher = 'Sticky';

    if (Object.keys(patch).length === 0) {
      console.log(`  - ${r.id}: ok`);
      continue;
    }
    fixed += 1;
    console.log(`  ${DRY_RUN ? 'DRY ' : 'FIX '}${r.id}: ${JSON.stringify(patch)}`);
    if (!DRY_RUN) {
      await api('PATCH', `/api/collections/premium_stickers/records/${r.id}`, patch, token);
    }
  }
  console.log(`Backfill done: ${fixed}/${records.length} record(s) repaired.`);
}

(async () => {
  console.log(`PocketBase: ${PB_URL}`);
  console.log(DRY_RUN ? '(dry-run — no writes)\n' : '');
  const token = await authenticate();
  console.log('Authenticated as superuser\n');

  console.log('--- Schema ---');
  await patchSchema(token, 'premium_stickers', PREMIUM_REQUIRED_FIELDS);
  await patchSchema(token, 'stickers', STICKERS_REQUIRED_FIELDS);

  await backfillPremiumRecords(token);

  console.log('\nAll done.');
  if (DRY_RUN) console.log('Re-run without --dry-run to apply.');
})().catch((err) => {
  console.error('\nERROR:', err.message);
  process.exit(1);
});
