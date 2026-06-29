#!/usr/bin/env node
/**
 * pb_repair_approved_packs.mjs
 *
 * Repairs already-approved user packs whose sticker image FILES were never migrated into the
 * `stickers` record. When the admin panel approved a submission it copied the submission's
 * `stickers[].url` verbatim (pointing at the user_submissions record), so the canonical pack URL
 * `${PB}/api/files/stickers/{packId}/{file}` 404s. In the app this shows as a blank grid + a
 * "can't add to WhatsApp" error (e.g. the "Funny kids" pack 335fstsb9uotw62).
 *
 * For each broken pack this downloads every sticker (and the tray) from its current location and
 * re-uploads it INTO the pack record, then rewrites `stickers[].url` / `tray_url` to the canonical
 * self-contained URLs. After this, the pack no longer depends on the submission record.
 *
 * Detection: a pack needs repair when the canonical URL of its first sticker 404s.
 *
 * SAFETY: dry-run by default. Pass --apply to actually upload + write. Pass --id=<recordId> to
 * repair a single pack first (recommended: test on one before a full run).
 *
 * Usage:
 *   PB_ADMIN_PASSWORD='superuser-pass' node scripts/pb_repair_approved_packs.mjs            # dry-run, all packs
 *   PB_ADMIN_PASSWORD='...' node scripts/pb_repair_approved_packs.mjs --id=335fstsb9uotw62  # dry-run, one pack
 *   PB_ADMIN_PASSWORD='...' node scripts/pb_repair_approved_packs.mjs --id=335fstsb9uotw62 --apply
 *   PB_ADMIN_PASSWORD='...' node scripts/pb_repair_approved_packs.mjs --apply               # apply to all broken packs
 */

const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
const APPLY = process.argv.includes('--apply');
const ONLY_ID = (process.argv.find((a) => a.startsWith('--id=')) || '').split('=')[1] || '';

if (!ADMIN_PASSWORD) {
  console.error('PB_ADMIN_PASSWORD env var is required.');
  process.exit(1);
}
if (typeof FormData === 'undefined' || typeof fetch === 'undefined') {
  console.error('Node 18+ is required (global fetch/FormData/Blob).');
  process.exit(1);
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

async function getJson(path, token) {
  const res = await fetch(`${PB_URL}${path}`, { headers: token ? { Authorization: token } : {} });
  const text = await res.text();
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}: ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : {};
}

async function getAllRecords(token, collectionName) {
  const records = [];
  let page = 1;
  while (true) {
    const data = await getJson(`/api/collections/${collectionName}/records?perPage=200&page=${page}`, token);
    records.push(...(data.items || []));
    if (page >= (data.totalPages || 1)) break;
    page += 1;
  }
  return records;
}

/** Same host normalization the app uses: keep the /api/files/ path, force the stable PB host. */
function toPbHost(url) {
  if (!url) return '';
  const i = url.indexOf('/api/files/');
  return i >= 0 ? PB_URL + url.slice(i) : url;
}

function canonicalUrl(recordId, file) {
  return `${PB_URL}/api/files/stickers/${recordId}/${file}`;
}

async function urlOk(url) {
  try {
    const res = await fetch(url, { method: 'GET', headers: { Range: 'bytes=0-0' } });
    return res.ok || res.status === 206;
  } catch {
    return false;
  }
}

async function fetchBlob(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`download ${res.status} for ${url}`);
  return await res.blob();
}

/** Uploads one file into the record's multi-file field and returns the stored filename. */
async function uploadFile(token, recordId, field, blob, filename) {
  const form = new FormData();
  form.append(`${field}+`, new File([blob], filename, { type: blob.type || 'image/webp' }));
  const res = await fetch(`${PB_URL}/api/collections/stickers/records/${recordId}`, {
    method: 'PATCH',
    headers: { Authorization: token },
    body: form,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`upload ${field} -> ${res.status}: ${text.slice(0, 300)}`);
  const rec = JSON.parse(text);
  const arr = rec[field] || [];
  return Array.isArray(arr) ? arr[arr.length - 1] : arr;
}

async function patchRecord(token, recordId, data) {
  const res = await fetch(`${PB_URL}/api/collections/stickers/records/${recordId}`, {
    method: 'PATCH',
    headers: { Authorization: token, 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`patch -> ${res.status}: ${text.slice(0, 300)}`);
  return JSON.parse(text);
}

function safeName(name, fallback) {
  const clean = String(name || fallback).split('/').pop().replace(/[^a-zA-Z0-9._-]/g, '_') || fallback;
  return clean.includes('.') ? clean : `${clean}.webp`;
}

async function repairPack(token, rec) {
  const stickers = Array.isArray(rec.stickers) ? rec.stickers : [];
  if (stickers.length === 0) {
    console.log(`SKIP ${rec.id} "${rec.name}" — no stickers array`);
    return { repaired: false };
  }

  // Detection: is the first sticker already self-contained?
  const first = stickers[0];
  const firstCanonical = canonicalUrl(rec.id, first.image_file || '');
  if (first.image_file && (await urlOk(firstCanonical))) {
    console.log(`OK   ${rec.id} "${rec.name}" — already self-contained`);
    return { repaired: false };
  }

  // Only repair fragile PocketBase-file-API packs (submission-path). Packs served from the
  // sticky-images CDN (no /api/files/) are permanent and must NOT be migrated to PB.
  if (!String(first.url || '').includes('/api/files/')) {
    console.log(`SKIP ${rec.id} "${rec.name}" — CDN/permanent source (not /api/files)`);
    return { repaired: false };
  }

  console.log(`FIX  ${rec.id} "${rec.name}" — ${stickers.length} stickers to migrate`);
  if (!APPLY) {
    // Verify sources are reachable so a real run would succeed.
    let reachable = 0;
    for (const s of stickers) {
      if (await urlOk(toPbHost(s.url) || canonicalUrl(rec.id, s.image_file))) reachable += 1;
    }
    console.log(`     DRY-RUN: ${reachable}/${stickers.length} source files reachable (would migrate)`);
    return { repaired: false, wouldRepair: true };
  }

  const migrated = [];
  for (let i = 0; i < stickers.length; i++) {
    const s = stickers[i];
    const name = safeName(s.image_file, `sticker_${i + 1}.webp`);
    const src = toPbHost(s.url) || canonicalUrl(rec.id, s.image_file);
    try {
      const blob = await fetchBlob(src);
      const stored = await uploadFile(token, rec.id, 'images', blob, name);
      migrated.push({
        image_file: stored,
        url: canonicalUrl(rec.id, stored),
        emojis: Array.isArray(s.emojis) && s.emojis.length ? s.emojis : ['⭐'],
      });
      process.stdout.write('.');
    } catch (e) {
      console.log(`\n     ! sticker ${i + 1} failed: ${e.message}`);
    }
  }
  console.log('');

  if (migrated.length < Math.min(9, stickers.length)) {
    console.log(`     ABORT ${rec.id}: only ${migrated.length}/${stickers.length} migrated — leaving record untouched`);
    return { repaired: false };
  }

  // Tray from the first migrated sticker.
  let trayFile = '';
  try {
    const trayName = safeName(migrated[0].image_file, 'tray.webp');
    const blob = await fetchBlob(migrated[0].url);
    trayFile = await uploadFile(token, rec.id, 'tray_image', blob, trayName);
  } catch (e) {
    console.log(`     tray upload failed: ${e.message}`);
  }

  await patchRecord(token, rec.id, {
    stickers: migrated,
    sticker_count: migrated.length,
    tray_url: trayFile ? canonicalUrl(rec.id, trayFile) : migrated[0].url,
    tray_image_file: trayFile,
    image_data_version: Date.now().toString(),
  });
  console.log(`     DONE ${rec.id}: ${migrated.length} stickers now self-contained`);
  return { repaired: true };
}

async function main() {
  console.log(`PB: ${PB_URL}`);
  console.log(APPLY ? 'MODE: APPLY (will upload + write)' : 'MODE: DRY-RUN (no writes)');
  const token = await authenticate();

  let records;
  if (ONLY_ID) {
    records = [await getJson(`/api/collections/stickers/records/${ONLY_ID}`, token)];
  } else {
    records = await getAllRecords(token, 'stickers');
  }
  console.log(`Scanning ${records.length} pack(s)...\n`);

  let broken = 0, repaired = 0;
  for (const rec of records) {
    try {
      const r = await repairPack(token, rec);
      if (r.wouldRepair) broken += 1;
      if (r.repaired) { broken += 1; repaired += 1; }
    } catch (e) {
      console.log(`ERR  ${rec.id}: ${e.message}`);
    }
  }

  console.log(`\nSummary: ${broken} pack(s) need(ed) repair${APPLY ? `, ${repaired} repaired` : ' (dry-run)'}.`);
  if (!APPLY && broken > 0) console.log('Re-run with --apply to migrate the files.');
}

main().catch((e) => { console.error(e); process.exit(1); });
