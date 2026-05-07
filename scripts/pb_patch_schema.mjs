#!/usr/bin/env node
// Node.js port of pb_patch_schema.py
const PB   = process.argv[2] || process.env.PB_URL || 'http://localhost:8090';
const EMAIL = process.argv[3] || process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PASS  = process.argv[4] || process.env.PB_ADMIN_PASSWORD;
if (!PASS) { console.error('PB_ADMIN_PASSWORD is required'); process.exit(1); }

async function api(method, path, data, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = token;
  const res = await fetch(`${PB}${path}`, {
    method, headers, body: data ? JSON.stringify(data) : undefined
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status}: ${text.slice(0,400)}`);
  return text ? JSON.parse(text) : {};
}

const LANGS = ['tr','de','fr','es','pt','it','ru','ar','hi','ja','ko','zh','th','vi','id','fil'];
const langFields = LANGS.map(l => ({ name: `name_${l}`, type: 'text' }));
const fileField = (name, maxSelect = 1, maxSize = 5242880, mimeTypes = ['image/webp','image/png','image/gif']) => ({
  name,
  type: 'file',
  maxSelect,
  maxSize,
  mimeTypes,
  thumbs: [],
  protected: false,
});

const packFields = [
  { name:'publisher_email',         type:'email'  },
  { name:'publisher_user_id',       type:'text'   },
  { name:'is_premium',              type:'bool'   },
  { name:'is_popular',              type:'bool'   },
  { name:'stickers',                type:'json'   },
  { name:'sticker_count',           type:'number' },
  { name:'image_data_version',      type:'text'   },
  { name:'privacy_policy_website',  type:'url'    },
  { name:'license_agreement_website',type:'url'   },
  { name:'batch_generated',         type:'bool'   },
  { name:'batch_source',            type:'text'   },
  { name:'batch_search_term',       type:'text'   },
  { name:'source',                  type:'text'   },
  { name:'telegram_set_name',       type:'text'   },
  { name:'telegram_set_title',      type:'text'   },
  { name:'telegram_part',           type:'number' },
  { name:'telegram_total_parts',    type:'number' },
  { name:'created_at',              type:'text'   },
  fileField('images', 99, 10485760),
  fileField('tray_image', 1, 5242880, ['image/webp','image/png']),
];

const PATCHES = {
  stickers:        [...packFields, ...langFields],
  premium_stickers:[...packFields, ...langFields],
  draft_stickers: [
    { name:'publisher',               type:'text'   },
    { name:'publisher_email',         type:'email'  },
    { name:'publisher_user_id',       type:'text'   },
    { name:'name',                    type:'text'   },
    { name:'status',                  type:'text'   },
    { name:'draft_data',              type:'json'   },
    { name:'category',                type:'text'   },
    { name:'is_premium',              type:'bool'   },
    { name:'is_animated',             type:'bool'   },
    { name:'is_active',               type:'bool'   },
    { name:'is_popular',              type:'bool'   },
    { name:'tray_image_file',         type:'text'   },
    { name:'tray_url',                type:'url'    },
    { name:'stickers',                type:'json'   },
    { name:'sticker_data',            type:'json'   },
    { name:'sticker_count',           type:'number' },
    { name:'image_data_version',      type:'text'   },
    { name:'download_count',          type:'number' },
    { name:'view_count',              type:'number' },
    { name:'favorite_count',          type:'number' },
    { name:'whatsapp_add_count',      type:'number' },
    { name:'fake_download_base',      type:'number' },
    { name:'privacy_policy_website',  type:'url'    },
    { name:'license_agreement_website',type:'url'   },
    { name:'batch_generated',         type:'bool'   },
    { name:'batch_source',            type:'text'   },
    { name:'batch_search_term',       type:'text'   },
    { name:'source',                  type:'text'   },
    { name:'telegram_set_name',       type:'text'   },
    { name:'telegram_set_title',      type:'text'   },
    { name:'telegram_part',           type:'number' },
    { name:'telegram_total_parts',    type:'number' },
    { name:'created_at',              type:'text'   },
    { name:'disabled_reason',         type:'text'   },
    fileField('images', 99, 10485760),
    fileField('tray_image', 1, 5242880, ['image/webp','image/png']),
    ...langFields,
  ],
  messages: [
    { name:'name',      type:'text'   },
    { name:'subject',   type:'text'   },
    { name:'timestamp', type:'number' },
    { name:'date',      type:'text'   },
    { name:'time',      type:'text'   },
    { name:'status',    type:'text'   },
  ],
  suggestions: [
    { name:'category',  type:'text'   },
    { name:'timestamp', type:'number' },
    { name:'date',      type:'text'   },
    { name:'time',      type:'text'   },
  ],
  notifications: [
    { name:'message',   type:'text'   },
    { name:'user_id',   type:'text'   },
    { name:'pack_id',   type:'text'   },
    { name:'from',      type:'text'   },
    { name:'read',      type:'bool'   },
  ],
  publisher_users: [
    { name:'display_name',    type:'text'   },
    { name:'bio',             type:'text'   },
    { name:'category',        type:'text'   },
    { name:'packs_published', type:'number' },
    { name:'total_downloads', type:'number' },
    { name:'created_at',      type:'date'   },
    { name:'is_active',       type:'bool'   },
  ],
  user_submissions: [
    { name:'user_email',        type:'email'  },
    { name:'description',      type:'text'   },
    { name:'rejection_reason', type:'text'   },
    { name:'stickers',         type:'json'   },
    { name:'sticker_data',     type:'json'   },
    { name:'sticker_count',    type:'number' },
    { name:'sticker_pack_id',  type:'text'   },
    { name:'source_pack_id',   type:'text'   },
    { name:'is_animated',      type:'bool'   },
    { name:'created_at',       type:'date'   },
    { name:'processed_at',     type:'date'   },
    fileField('images', 99, 5242880),
  ],
  user_profiles: [
    { name:'user_id',          type:'text'   },
    { name:'uid',              type:'text'   },
    { name:'device_id',        type:'text'   },
    { name:'name',             type:'text'   },
    { name:'display_name',     type:'text'   },
    { name:'email',            type:'email'  },
    { name:'photo_url',        type:'url'    },
    { name:'provider',         type:'text'   },
    { name:'platform',         type:'text'   },
    { name:'app_version',      type:'text'   },
    { name:'fcm_token',        type:'text'   },
    { name:'fcm_tokens',       type:'json'   },
    { name:'notifications_enabled', type:'bool' },
    { name:'push_provider',    type:'text'   },
    { name:'is_premium',       type:'bool'   },
    { name:'premium_type',     type:'text'   },
    { name:'premium_expiry',   type:'number' },
    { name:'premium_expires_at', type:'date' },
    { name:'last_sync',        type:'date'   },
    { name:'cancelled_at',     type:'date'   },
    { name:'cancelled_reason', type:'text'   },
    { name:'subscription_source', type:'text' },
    { name:'subscription_history', type:'json' },
    { name:'device_info',      type:'json'   },
    { name:'favorite_packs',   type:'json'   },
    { name:'total_stickers_added', type:'number' },
    { name:'custom_packs_count', type:'number' },
    { name:'packs_published',  type:'number' },
    { name:'total_downloads',  type:'number' },
    { name:'total_favorites',  type:'number' },
    { name:'created_at',       type:'date'   },
    { name:'joined_at',        type:'date'   },
  ],
  users: [
    { name:'device_id',             type:'text'   },
    { name:'uid',                   type:'text'   },
    { name:'name',                  type:'text'   },
    { name:'display_name',          type:'text'   },
    { name:'photo_url',             type:'url'    },
    { name:'fcm_token',             type:'text'   },
    { name:'fcm_tokens',            type:'json'   },
    { name:'notifications_enabled', type:'bool'   },
    { name:'push_provider',         type:'text'   },
    { name:'subscription_history',  type:'json'   },
    { name:'cancelled_at',          type:'date'   },
    { name:'cancelled_reason',      type:'text'   },
    { name:'device_info',           type:'json'   },
    { name:'favorite_packs',        type:'json'   },
    { name:'total_stickers_added',  type:'number' },
    { name:'custom_packs_count',    type:'number' },
    { name:'packs_published',       type:'number' },
  ],
  admin_logs: [
    { name:'admin_email', type:'email'  },
    { name:'action',      type:'text'   },
    { name:'detail',      type:'text'   },
    { name:'pack_id',     type:'text'   },
    { name:'timestamp',   type:'text'   },
  ],
};

async function patchCollection(token, name, wantedFields) {
  let collection;
  try {
    collection = await api('GET', `/api/collections/${name}`, null, token);
  } catch (e) {
    console.log(`SKIP ${name}: ${e.message}`);
    return false;
  }
  const key = collection.fields ? 'fields' : 'schema';
  const current = collection[key] || [];
  const existing = new Set(current.map(f => f.name));
  const missing = wantedFields.filter(f => !existing.has(f.name));
  if (!missing.length) {
    console.log(`OK   ${name}: no missing fields`);
    return true;
  }
  await api('PATCH', `/api/collections/${name}`, { [key]: [...current, ...missing] }, token);
  console.log(`OK   ${name}: added [${missing.map(f=>f.name).join(', ')}]`);
  return true;
}

(async () => {
  const auth = await api('POST', '/api/collections/_superusers/auth-with-password', { identity: EMAIL, password: PASS });
  const token = auth.token;
  if (!token) { console.error('PocketBase auth failed'); process.exit(1); }
  console.log('Authenticated as superuser');

  let ok = 0;
  for (const [name, fields] of Object.entries(PATCHES)) {
    if (await patchCollection(token, name, fields)) ok++;
  }
  console.log(`\nDone: patched/checked ${ok}/${Object.keys(PATCHES).length} collections`);
})().catch(e => { console.error(e.message); process.exit(1); });
