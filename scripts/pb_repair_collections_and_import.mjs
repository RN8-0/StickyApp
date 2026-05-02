import { readFileSync, existsSync } from 'fs';
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
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${PB_URL}/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : {};
}

function field(name, type, extra = {}) {
  return { name, type, ...extra };
}

const adminRule = '@request.auth.id != ""';

const collections = [
  {
    name: 'messages',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: '',
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [
      field('name', 'text'), field('subject', 'text'), field('title', 'text'), field('body', 'text'),
      field('email', 'email'), field('message', 'text'), field('timestamp', 'number'), field('date', 'text'),
      field('time', 'text'), field('status', 'text'), field('source', 'text'), field('device_id', 'text'), field('user_id', 'text'),
    ],
  },
  {
    name: 'suggestions',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: '',
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [
      field('suggestion', 'text'), field('text', 'text'), field('category', 'text'), field('email', 'email'),
      field('user_id', 'text'), field('device_id', 'text'), field('timestamp', 'number'), field('date', 'text'),
      field('time', 'text'), field('status', 'text'), field('source', 'text'),
    ],
  },
  {
    name: 'notifications',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: adminRule,
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [
      field('title', 'text'), field('body', 'text'), field('message', 'text'), field('imageUrl', 'url'),
      field('image_url', 'url'), field('timestamp', 'text'), field('user_id', 'text'), field('pack_id', 'text'),
      field('from', 'text'), field('read', 'bool'), field('sent', 'bool'), field('topic', 'text'), field('data', 'json'),
    ],
  },
  {
    name: 'publisher_users',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: adminRule,
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [
      field('name', 'text'), field('email', 'email'), field('display_name', 'text'), field('avatar_url', 'url'),
      field('photo_url', 'url'), field('bio', 'text'), field('category', 'text'), field('packs_published', 'number'),
      field('total_downloads', 'number'), field('total_favorites', 'number'), field('joined_at', 'text'), field('created_at', 'text'),
      field('is_active', 'bool'),
    ],
  },
  {
    name: 'user_profiles',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: '',
    updateRule: '',
    deleteRule: adminRule,
    fields: [
      field('user_id', 'text'), field('device_id', 'text'), field('uid', 'text'), field('email', 'email'),
      field('display_name', 'text'), field('name', 'text'), field('photo_url', 'url'), field('fcm_token', 'text'),
      field('fcm_tokens', 'json'), field('notifications_enabled', 'bool'), field('push_provider', 'text'),
      field('is_premium', 'bool'), field('premium_type', 'text'), field('premium_expiry', 'number'),
      field('subscription_source', 'text'), field('subscription_history', 'json'), field('device_info', 'json'),
      field('favorite_packs', 'json'), field('total_stickers_added', 'number'), field('custom_packs_count', 'number'),
      field('packs_published', 'number'), field('total_downloads', 'number'), field('total_favorites', 'number'),
      field('ai_count', 'number'), field('ai_date', 'text'), field('created_at', 'text'), field('joined_at', 'text'), field('last_sync', 'text'),
    ],
  },
  {
    name: 'draft_stickers',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: adminRule,
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [
      field('name', 'text'), field('name_en', 'text'), field('publisher', 'text'), field('publisher_email', 'email'),
      field('publisher_user_id', 'text'), field('category', 'text'), field('is_premium', 'bool'), field('is_animated', 'bool'),
      field('is_active', 'bool'), field('is_popular', 'bool'), field('tray_image_file', 'text'), field('tray_url', 'url'),
      field('stickers', 'json'), field('sticker_data', 'json'), field('sticker_count', 'number'), field('image_data_version', 'text'),
      field('download_count', 'number'), field('view_count', 'number'), field('favorite_count', 'number'), field('whatsapp_add_count', 'number'),
      field('fake_download_base', 'number'), field('privacy_policy_website', 'url'), field('license_agreement_website', 'url'),
      field('source', 'text'), field('telegram_set_name', 'text'), field('telegram_set_title', 'text'), field('telegram_part', 'number'),
      field('telegram_total_parts', 'number'), field('batch_generated', 'bool'), field('batch_source', 'text'), field('batch_search_term', 'text'),
      field('status', 'text'), field('disabled_reason', 'text'), field('created_at', 'text'),
    ],
  },
  {
    name: 'user_submissions',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: '',
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [
      field('pack_name', 'text'), field('name', 'text'), field('publisher_name', 'text'), field('category', 'text'),
      field('user_id', 'text'), field('user_email', 'email'), field('display_name', 'text'), field('description', 'text'),
      field('status', 'text'), field('flag_reasons', 'json'), field('rejection_reason', 'text'), field('sticker_data', 'json'),
      field('stickers', 'json'), field('sticker_count', 'number'), field('sticker_pack_id', 'text'), field('source_pack_id', 'text'),
      field('is_animated', 'bool'), field('created_at', 'text'), field('processed_at', 'text'), field('note', 'text'),
    ],
  },
  {
    name: 'settings',
    type: 'base',
    listRule: adminRule,
    viewRule: adminRule,
    createRule: adminRule,
    updateRule: adminRule,
    deleteRule: adminRule,
    fields: [field('key', 'text'), field('value', 'json'), field('data', 'json')],
  },
  {
    name: 'purchased_packs',
    type: 'base',
    listRule: '@request.auth.id != ""',
    viewRule: '@request.auth.id != ""',
    createRule: '@request.auth.id != ""',
    updateRule: '@request.auth.id != ""',
    deleteRule: '@request.auth.id != ""',
    fields: [field('user_id', 'text'), field('pack_id', 'text'), field('order_id', 'text'), field('purchase_date', 'text')],
  },
];

function exportJson(file) {
  const path = resolve(EXPORT_DIR, file);
  if (!existsSync(path)) return [];
  return JSON.parse(readFileSync(path, 'utf8'));
}

function cleanDoc(doc) {
  const out = {};
  for (const [key, value] of Object.entries(doc)) {
    if (key === 'id' || key.startsWith('_')) continue;
    out[key] = value;
  }
  return out;
}

async function getCollections() {
  const res = await api('GET', '/collections?perPage=500');
  return res.items || [];
}

async function ensureCollections() {
  const existing = await getCollections();
  const byName = new Map(existing.map(c => [c.name, c]));
  let created = 0;
  for (const collection of collections) {
    if (byName.has(collection.name)) {
      const current = byName.get(collection.name);
      const key = current.fields ? 'fields' : 'schema';
      const currentFields = current[key] || [];
      const existingFields = new Set(currentFields.map(f => f.name));
      const missing = collection.fields.filter(f => !existingFields.has(f.name));
      const patch = {
        listRule: collection.listRule,
        viewRule: collection.viewRule,
        createRule: collection.createRule,
        updateRule: collection.updateRule,
        deleteRule: collection.deleteRule,
      };
      if (missing.length) patch[key] = [...currentFields, ...missing];
      await api('PATCH', `/collections/${collection.name}`, patch);
      console.log(`✓ ${collection.name} checked${missing.length ? ` (+${missing.length} fields)` : ''}`);
      continue;
    }
    await api('POST', '/collections', collection);
    created++;
    console.log(`✓ ${collection.name} created`);
  }
  return created;
}

async function recordExists(collection, filter) {
  const res = await api('GET', `/collections/${collection}/records?perPage=1&filter=${encodeURIComponent(filter)}`);
  return (res.totalItems || 0) > 0;
}

async function importRecords(collection, docs, mapper) {
  let created = 0, skipped = 0, failed = 0;
  for (const doc of docs) {
    const record = mapper(doc);
    const unique = record.__unique;
    delete record.__unique;
    try {
      if (unique && await recordExists(collection, unique)) {
        skipped++;
        continue;
      }
      await api('POST', `/collections/${collection}/records`, record);
      created++;
    } catch (e) {
      failed++;
      console.warn(`  ${collection} failed (${doc.id || doc.email || doc.suggestion || doc.title || 'record'}): ${e.message.slice(0, 160)}`);
    }
  }
  console.log(`✓ ${collection}: ${created} created, ${skipped} skipped, ${failed} failed`);
}

async function importData() {
  await importRecords('suggestions', exportJson('suggestions.json'), doc => ({
    ...cleanDoc(doc),
    suggestion: doc.suggestion || doc.text || '',
    text: doc.text || doc.suggestion || '',
    status: doc.status || 'pending',
    source: 'firestore_export',
    __unique: `timestamp=${Number(doc.timestamp || 0)} && suggestion='${String(doc.suggestion || doc.text || '').replace(/'/g, "\\'")}'`,
  }));

  await importRecords('notifications', exportJson('notifications.json'), doc => ({
    ...cleanDoc(doc),
    image_url: doc.image_url || doc.imageUrl || '',
    imageUrl: doc.imageUrl || doc.image_url || '',
    sent: doc.sent !== false,
    topic: doc.topic || 'all',
    __unique: `timestamp='${String(doc.timestamp || '').replace(/'/g, "\\'")}' && body='${String(doc.body || '').replace(/'/g, "\\'")}'`,
  }));

  await importRecords('user_profiles', exportJson('users.json'), doc => ({
    ...cleanDoc(doc),
    user_id: doc.id || doc.user_id || doc.uid || '',
    uid: doc.id || doc.uid || '',
    name: doc.name || doc.display_name || '',
    display_name: doc.display_name || doc.name || '',
    is_premium: doc.is_premium === true,
    premium_type: doc.premium_type || 'none',
    premium_expiry: Number(doc.premium_expiry || 0),
    subscription_source: doc.subscription_source || 'none',
    created_at: doc.created_at || doc._createTime || '',
    last_sync: doc.last_sync || doc._updateTime || '',
    __unique: `uid='${String(doc.id || '').replace(/'/g, "\\'")}'`,
  }));

  await importRecords('publisher_users', exportJson('user_profiles.json'), doc => ({
    ...cleanDoc(doc),
    name: doc.display_name || doc.name || doc.email || 'Publisher',
    display_name: doc.display_name || doc.name || '',
    avatar_url: doc.avatar_url || doc.photo_url || '',
    photo_url: doc.photo_url || doc.avatar_url || '',
    is_active: doc.is_active !== false,
    created_at: doc.joined_at || doc._createTime || '',
    __unique: `email='${String(doc.email || '').replace(/'/g, "\\'")}'`,
  }));

  await importRecords('settings', exportJson('settings.json'), doc => ({
    key: doc.id || doc.key || 'settings',
    value: cleanDoc(doc),
    data: cleanDoc(doc),
    __unique: `key='${String(doc.id || doc.key || 'settings').replace(/'/g, "\\'")}'`,
  }));
}

async function verify() {
  const names = ['messages', 'suggestions', 'notifications', 'publisher_users', 'user_profiles', 'draft_stickers', 'user_submissions', 'settings', 'purchased_packs', 'stickers', 'premium_stickers'];
  console.log('\nVerification:');
  for (const name of names) {
    const res = await api('GET', `/collections/${name}/records?perPage=1`);
    console.log(`  ${name}: ${res.totalItems}`);
  }
}

async function main() {
  const auth = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  const authText = await auth.text();
  if (!auth.ok) throw new Error(`Auth failed: ${authText}`);
  token = JSON.parse(authText).token;
  console.log('✓ Authenticated');

  await ensureCollections();
  console.log('\nImporting Firestore export data...');
  await importData();
  await verify();
  console.log('\n✅ PocketBase collections repaired and export data imported.');
}

main().catch(e => {
  console.error('FATAL:', e.message);
  process.exit(1);
});
