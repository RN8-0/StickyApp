/**
 * pb_import_all.mjs — Clean import of Firestore export into PocketBase
 * 
 * Usage:
 *   PB_ADMIN_PASSWORD=yourpassword node scripts/pb_import_all.mjs
 *
 * Optional env vars:
 *   PB_URL (default: https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io)
 *   PB_ADMIN_EMAIL (default: arainunger@gmail.com)
 *   SKIP_EXISTING=1  → skip records that already exist instead of updating
 */

import { readFileSync, existsSync } from 'fs';
import { resolve, join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
const EXPORT_DIR = resolve(__dirname, '..', 'firestore_export');
const SKIP_EXISTING = process.env.SKIP_EXISTING === '1';

if (!ADMIN_PASSWORD) {
  console.error('Error: PB_ADMIN_PASSWORD env var required');
  console.error('Usage: $env:PB_ADMIN_PASSWORD="yourpassword"; node scripts/pb_import_all.mjs');
  process.exit(1);
}

let authToken = '';

async function api(method, path, body) {
  const headers = { Authorization: authToken };
  let reqBody;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    reqBody = JSON.stringify(body);
  }
  const res = await fetch(`${PB_URL}/api${path}`, { method, headers, body: reqBody });
  const text = await res.text();
  if (!res.ok) throw Object.assign(new Error(text), { status: res.status });
  return text ? JSON.parse(text) : {};
}

async function authenticate() {
  console.log(`Authenticating as ${ADMIN_EMAIL}...`);
  const res = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Auth failed: ${res.status} ${text}`);
  authToken = JSON.parse(text).token;
  console.log('✓ Authenticated\n');
}

async function getExistingIds(collection) {
  const ids = new Set();
  let page = 1;
  while (true) {
    const res = await api('GET', `/collections/${collection}/records?perPage=200&page=${page}&fields=id`);
    res.items.forEach(r => ids.add(r.id));
    if (page >= res.totalPages) break;
    page++;
  }
  return ids;
}

function buildRecord(doc, isPremium) {
  // Collect all translation fields (name_xx)
  const record = {};
  for (const [k, v] of Object.entries(doc)) {
    if (k === 'id' || k === 'created_at' || k === 'updated_at') continue;
    if (k === 'stickers') {
      // Store stickers as proper JSON array (not stringified)
      record.stickers = Array.isArray(v) ? v : [];
      continue;
    }
    record[k] = v;
  }

  // Ensure required fields
  record.id = doc.id;
  record.is_premium = isPremium;
  record.is_active = doc.is_active !== false;
  record.category = doc.category || 'other';
  record.sticker_count = Number(doc.sticker_count || doc.stickers?.length || 0);
  record.download_count = Number(doc.download_count || 0);
  record.fake_download_base = Number(doc.fake_download_base || 0);
  record.view_count = Number(doc.view_count || 0);
  record.favorite_count = Number(doc.favorite_count || 0);
  record.is_animated = doc.is_animated || false;
  record.stickers = Array.isArray(doc.stickers) ? doc.stickers : [];

  return record;
}

async function importCollection(jsonFile, pbCollection, isPremium) {
  const filePath = join(EXPORT_DIR, jsonFile);
  if (!existsSync(filePath)) { console.log(`Skipping ${jsonFile} (not found)`); return; }

  const docs = JSON.parse(readFileSync(filePath, 'utf-8'));
  console.log(`Importing ${docs.length} records into ${pbCollection}...`);

  const existingIds = await getExistingIds(pbCollection);
  console.log(`  Found ${existingIds.size} existing records`);

  let created = 0, updated = 0, failed = 0;

  for (let i = 0; i < docs.length; i++) {
    const doc = docs[i];
    const label = `[${i + 1}/${docs.length}] ${doc.name || doc.id}`;
    try {
      const record = buildRecord(doc, isPremium);
      const exists = existingIds.has(doc.id);

      if (exists && SKIP_EXISTING) {
        process.stdout.write(`  ${label} → skipped\n`);
        continue;
      }

      if (exists) {
        await api('PATCH', `/collections/${pbCollection}/records/${doc.id}`, record);
        updated++;
        process.stdout.write(`  ${label} → updated ✓\n`);
      } else {
        await api('POST', `/collections/${pbCollection}/records`, record);
        created++;
        process.stdout.write(`  ${label} → created ✓\n`);
      }
    } catch (e) {
      failed++;
      const msg = e.message.length > 120 ? e.message.substring(0, 120) + '...' : e.message;
      process.stdout.write(`  ${label} → ✗ ${msg}\n`);
    }
  }

  console.log(`\n  ✓ ${pbCollection}: ${created} created, ${updated} updated, ${failed} failed\n`);
}

async function main() {
  await authenticate();
  await importCollection('stickers.json', 'stickers', false);
  await importCollection('premium_stickers.json', 'premium_stickers', true);
  console.log('✅ Import complete!');
}

main().catch(e => { console.error('FATAL:', e.message); process.exit(1); });
