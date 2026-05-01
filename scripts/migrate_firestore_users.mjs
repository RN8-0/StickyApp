// Firestore users -> PocketBase user_profiles migration
// Usage: node scripts/migrate_firestore_users.mjs

const PB_URL = process.argv[2] || 'http://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const PB_EMAIL = process.argv[3] || 'admin@rn8.local';
const PB_PASS = process.argv[4] || 'mx6I0zPE3HSaqbjlAY0p';

import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

async function pbAuth() {
  const r = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_EMAIL, password: PB_PASS }),
  });
  const data = await r.json();
  if (!data.token) throw new Error('PB auth failed: ' + JSON.stringify(data));
  return data.token;
}

async function run() {
  console.log('Authenticating with PocketBase...');
  const token = await pbAuth();
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };

  // Load Firestore users
  const fsUsersPath = join(__dirname, '..', 'firestore_export', 'users.json');
  const fsUsers = JSON.parse(readFileSync(fsUsersPath, 'utf8'));
  const users = Array.isArray(fsUsers) ? fsUsers : Object.values(fsUsers)[0] || [];

  console.log(`Found ${users.length} Firestore users to migrate...`);

  let created = 0, skipped = 0, errors = 0;

  for (const u of users) {
    // Check if already exists in user_profiles by user_id
    const checkR = await fetch(
      `${PB_URL}/api/collections/user_profiles/records?filter=user_id%3D'${encodeURIComponent(u.id)}'`,
      { headers }
    );
    const existing = await checkR.json();
    if (existing.totalItems > 0) {
      skipped++;
      continue;
    }

    // Map Firestore fields to PB user_profiles
    const record = {
      user_id: u.id || '',
      email: u.email || '',
      display_name: u.display_name || '',
      photo_url: u.photo_url || '',
      provider: u.device_info?.platform || 'android',
      premium: u.premium === true,
      custom_packs_count: Number(u.custom_packs_count) || 0,
      total_stickers_sent: Number(u.total_stickers_added) || 0,
      language: u.device_info?.language || '',
      country: u.device_info?.country || '',
      platform: u.device_info?.platform || 'android',
      app_version: u.device_info?.app_version || '',
      created_at: u.created_at || u._createTime || '',
      updated_at: u.last_sync || u._updateTime || '',
    };

    const createR = await fetch(`${PB_URL}/api/collections/user_profiles/records`, {
      method: 'POST',
      headers,
      body: JSON.stringify(record),
    });
    const result = await createR.json();
    if (createR.status === 200 || createR.status === 201) {
      created++;
    } else {
      console.error(`  ERROR user ${u.id}: ${result.message || JSON.stringify(result)}`);
      errors++;
    }
  }

  console.log(`\nMigration complete:`);
  console.log(`  Created: ${created}`);
  console.log(`  Skipped (already exist): ${skipped}`);
  console.log(`  Errors: ${errors}`);

  // Also migrate Firestore user_profiles (publisher profiles)
  const fsProfilesPath = join(__dirname, '..', 'firestore_export', 'user_profiles.json');
  const fsProfiles = JSON.parse(readFileSync(fsProfilesPath, 'utf8'));
  const profiles = Array.isArray(fsProfiles) ? fsProfiles : Object.values(fsProfiles)[0] || [];
  console.log(`\nFirestore user_profiles (publisher): ${profiles.length}`);
  // These are publisher profiles - they're in publisher_users collection in PB
}

run().catch(e => { console.error(e); process.exit(1); });
