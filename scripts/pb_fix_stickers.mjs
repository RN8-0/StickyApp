/**
 * pb_fix_stickers.mjs
 * Fixes all sticker records in PocketBase:
 *  - Restores correct Firebase Storage URLs from Firestore export
 *  - Fixes categories
 *  - Fixes all name_xx translation fields
 *  - Imports missing premium_stickers (0 records → 39 records)
 * 
 * Usage:
 *   $env:PB_ADMIN_PASSWORD="..."; node scripts/pb_fix_stickers.mjs
 */

import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const PB_URL = 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}
const EXPORT_DIR = resolve(__dirname, '..', 'firestore_export');

let token = '';

async function api(method, path, body) {
  const headers = { Authorization: token };
  let reqBody;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    reqBody = JSON.stringify(body);
  }
  const res = await fetch(`${PB_URL}/api${path}`, { method, headers, body: reqBody });
  const text = await res.text();
  if (!res.ok) throw Object.assign(new Error(text.substring(0, 200)), { status: res.status });
  return text ? JSON.parse(text) : {};
}

async function auth() {
  const res = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Auth failed: ${text}`);
  token = JSON.parse(text).token;
  console.log('✓ Authenticated\n');
}

async function getAllPbRecords(collection) {
  const records = [];
  let page = 1;
  while (true) {
    const res = await api('GET', `/collections/${collection}/records?perPage=200&page=${page}`);
    records.push(...res.items);
    if (page >= res.totalPages) break;
    page++;
  }
  return records;
}

// Extract original Firestore pack ID from the broken URL format:
// https://sticky-admin.46.225.95.201.sslip.io/images/stickers/PACK_ID/filename
function extractPackIdFromUrl(url) {
  if (!url) return null;
  const m = url.match(/\/images\/stickers\/([^/]+)\//);
  return m ? m[1] : null;
}

function buildUpdatePayload(doc) {
  const payload = {};

  // All fields from doc except internal ones
  for (const [k, v] of Object.entries(doc)) {
    if (['id', 'created_at', 'updated_at', '__collections__'].includes(k)) continue;
    payload[k] = v;
  }

  // Ensure correct types
  payload.stickers = Array.isArray(doc.stickers) ? doc.stickers : [];
  payload.is_active = doc.is_active !== false;
  payload.is_animated = doc.is_animated || false;
  payload.is_premium = doc.is_premium || false;
  payload.category = doc.category || 'other';
  payload.sticker_count = Number(doc.sticker_count || doc.stickers?.length || 0);
  payload.download_count = Number(doc.download_count || 0);
  payload.fake_download_base = Number(doc.fake_download_base || 0);
  payload.view_count = Number(doc.view_count || 0);
  payload.favorite_count = Number(doc.favorite_count || 0);
  payload.tray_url = doc.tray_url || '';
  payload.tray_image_file = doc.tray_image_file || '';
  payload.image_data_version = doc.image_data_version || Date.now().toString();

  return payload;
}

async function fixCollection(jsonFile, pbCollection, isPremium) {
  console.log(`\n${'='.repeat(60)}`);
  console.log(`Processing: ${pbCollection}`);
  console.log('='.repeat(60));

  const exportData = JSON.parse(readFileSync(resolve(EXPORT_DIR, jsonFile), 'utf-8'));
  // Build lookup map: firestore_id → doc
  const exportById = {};
  exportData.forEach(doc => { exportById[doc.id] = doc; });
  console.log(`Export: ${exportData.length} records`);

  const pbRecords = await getAllPbRecords(pbCollection);
  console.log(`PocketBase: ${pbRecords.length} existing records\n`);

  let updated = 0, created = 0, failed = 0;

  // --- Update existing PB records ---
  for (const pbRec of pbRecords) {
    // Extract original Firestore ID from the broken URL
    const firstStickerUrl = pbRec.stickers?.[0]?.url || '';
    const firestoreId = extractPackIdFromUrl(firstStickerUrl) || extractPackIdFromUrl(pbRec.tray_url || '');

    const exportDoc = firestoreId ? exportById[firestoreId] : null;

    if (!exportDoc) {
      // Try matching by name as fallback
      const nameMatch = exportData.find(d => d.name && pbRec.name && d.name.trim() === pbRec.name.trim());
      if (!nameMatch) {
        console.log(`  SKIP (no match): ${pbRec.name || pbRec.id}`);
        failed++;
        continue;
      }
      Object.assign(exportDoc || {}, nameMatch); // won't work, just use nameMatch below
      const payload = buildUpdatePayload(nameMatch);
      payload.is_premium = isPremium;
      try {
        await api('PATCH', `/collections/${pbCollection}/records/${pbRec.id}`, payload);
        updated++;
        console.log(`  ✓ ${pbRec.name} (matched by name)`);
      } catch (e) {
        failed++;
        console.log(`  ✗ ${pbRec.name}: ${e.message}`);
      }
      continue;
    }

    const payload = buildUpdatePayload(exportDoc);
    payload.is_premium = isPremium;
    try {
      await api('PATCH', `/collections/${pbCollection}/records/${pbRec.id}`, payload);
      updated++;
      process.stdout.write(`  ✓ ${exportDoc.name} [category: ${payload.category}]\n`);
    } catch (e) {
      failed++;
      console.log(`  ✗ ${exportDoc.name}: ${e.message}`);
    }
    // Mark as handled
    delete exportById[exportDoc.id];
  }

  // --- Create missing records (those not yet in PB) ---
  const missing = Object.values(exportById);
  if (missing.length > 0) {
    console.log(`\n  Creating ${missing.length} missing records...`);
    for (const doc of missing) {
      const payload = buildUpdatePayload(doc);
      payload.is_premium = isPremium;
      // Don't set custom id — let PB generate one (IDs must be exactly 15 chars)
      try {
        await api('POST', `/collections/${pbCollection}/records`, payload);
        created++;
        console.log(`  ✓ Created: ${doc.name}`);
      } catch (e) {
        failed++;
        console.log(`  ✗ Failed to create ${doc.name}: ${e.message}`);
      }
    }
  }

  console.log(`\n  Summary: ${updated} updated, ${created} created, ${failed} failed`);
}

async function main() {
  await auth();
  await fixCollection('stickers.json', 'stickers', false);
  await fixCollection('premium_stickers.json', 'premium_stickers', true);
  console.log('\n✅ All done!');
}

main().catch(e => { console.error('FATAL:', e.message); process.exit(1); });
