// Complete PocketBase schema setup - creates all collections with fields and API rules
// Run: node scripts/pb_setup_complete.js

const PB_URL = process.env.PB_URL || 'http://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_RULE = "@request.auth.id != '' && @collection.admins_list.email ?= @request.auth.email";
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;

if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}

let token = '';

async function auth() {
  const r = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD })
  });
  if (!r.ok) throw new Error(`Auth failed: ${r.status}`);
  token = (await r.json()).token;
  console.log('✓ Authenticated');
}

async function api(method, path, data) {
  const h = { Authorization: token, 'Content-Type': 'application/json' };
  const opts = { method, headers: h };
  if (data) opts.body = JSON.stringify(data);
  const r = await fetch(`${PB_URL}${path}`, opts);
  const text = await r.text();
  if (!r.ok) {
    console.error(`  ERR ${r.status} ${method} ${path}: ${text.substring(0, 200)}`);
    return null;
  }
  return text ? JSON.parse(text) : {};
}

// Language translation fields
const LANGS = ['tr','de','fr','es','pt','it','ru','ar','hi','ja','ko','zh','th','vi','id','ms',
               'fil','tl','bn','fa','he','uk','pl','cs','hu','ro','nl','sv','da','fi','no','el','en','ur'];
const langFields = LANGS.map(l => ({ name: `name_${l}`, type: 'text' }));

function fileField(name, maxSelect = 1, maxSize = 5242880, mimeTypes = ['image/webp','image/png','image/gif']) {
  return { name, type: 'file', options: { maxSelect, maxSize, mimeTypes } };
}

const collections = [
  {
    name: 'stickers', type: 'base',
    fields: [
      { name: 'name', type: 'text', required: true },
      { name: 'publisher', type: 'text' },
      { name: 'publisher_email', type: 'text' },
      { name: 'category', type: 'text' },
      { name: 'is_animated', type: 'bool' },
      { name: 'is_active', type: 'bool' },
      { name: 'is_premium', type: 'bool' },
      { name: 'tray_image_file', type: 'text' },
      { name: 'tray_url', type: 'url' },
      { name: 'stickers', type: 'json' },
      { name: 'sticker_count', type: 'number' },
      { name: 'download_count', type: 'number' },
      { name: 'view_count', type: 'number' },
      { name: 'favorite_count', type: 'number' },
      { name: 'whatsapp_add_count', type: 'number' },
      { name: 'fake_download_base', type: 'number' },
      { name: 'image_data_version', type: 'text' },
      { name: 'translations', type: 'json' },
      { name: 'source', type: 'text' },
      { name: 'telegram_set_name', type: 'text' },
      { name: 'disabled_reason', type: 'text' },
      { name: 'price_usd', type: 'text' },
      { name: 'price_eur', type: 'text' },
      { name: 'price_try', type: 'text' },
      fileField('images', 99, 10485760),
      fileField('tray_image', 1, 5242880, ['image/webp','image/png']),
      ...langFields,
    ],
    listRule: '', viewRule: '', createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'premium_stickers', type: 'base',
    fields: [
      { name: 'name', type: 'text', required: true },
      { name: 'publisher', type: 'text' },
      { name: 'publisher_email', type: 'text' },
      { name: 'category', type: 'text' },
      { name: 'is_animated', type: 'bool' },
      { name: 'is_active', type: 'bool' },
      { name: 'is_premium', type: 'bool' },
      { name: 'tray_image_file', type: 'text' },
      { name: 'tray_url', type: 'url' },
      { name: 'stickers', type: 'json' },
      { name: 'sticker_count', type: 'number' },
      { name: 'download_count', type: 'number' },
      { name: 'view_count', type: 'number' },
      { name: 'favorite_count', type: 'number' },
      { name: 'whatsapp_add_count', type: 'number' },
      { name: 'fake_download_base', type: 'number' },
      { name: 'image_data_version', type: 'text' },
      { name: 'translations', type: 'json' },
      { name: 'source', type: 'text' },
      { name: 'telegram_set_name', type: 'text' },
      { name: 'disabled_reason', type: 'text' },
      { name: 'price_usd', type: 'text' },
      { name: 'price_eur', type: 'text' },
      { name: 'price_try', type: 'text' },
      fileField('images', 99, 10485760),
      fileField('tray_image', 1, 5242880, ['image/webp','image/png']),
      ...langFields,
    ],
    listRule: '', viewRule: '', createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'draft_stickers', type: 'base',
    fields: [
      { name: 'name', type: 'text' },
      { name: 'status', type: 'text' },
      { name: 'draft_data', type: 'json' },
      { name: 'stickers', type: 'json' },
      { name: 'translations', type: 'json' },
      { name: 'category', type: 'text' },
      { name: 'is_animated', type: 'bool' },
      { name: 'tray_image_file', type: 'text' },
      { name: 'tray_url', type: 'url' },
      { name: 'publisher', type: 'text' },
      { name: 'publisher_email', type: 'text' },
      { name: 'sticker_count', type: 'number' },
      ...langFields,
    ],
    listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'messages', type: 'base',
    fields: [
      { name: 'title', type: 'text' },
      { name: 'body', type: 'text' },
      { name: 'email', type: 'email' },
      { name: 'message', type: 'text' },
      { name: 'device_id', type: 'text' },
    ],
    listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: '', updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'suggestions', type: 'base',
    fields: [
      { name: 'text', type: 'text' },
      { name: 'suggestion', type: 'text' },
      { name: 'email', type: 'email' },
      { name: 'user_id', type: 'text' },
      { name: 'device_id', type: 'text' },
      { name: 'category', type: 'text' },
      { name: 'timestamp', type: 'number' },
      { name: 'date', type: 'text' },
      { name: 'time', type: 'text' },
      { name: 'status', type: 'text' },
    ],
    listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: '', updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'notifications', type: 'base',
    fields: [
      { name: 'title', type: 'text', required: true },
      { name: 'body', type: 'text' },
      { name: 'topic', type: 'text' },
      { name: 'type', type: 'text' },
      { name: 'sent_at', type: 'text' },
      { name: 'data', type: 'json' },
      { name: 'image_url', type: 'url' },
      { name: 'sent', type: 'bool' },
    ],
    listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'content_reports', type: 'base',
    fields: [
      { name: 'pack_id', type: 'text', required: true },
      { name: 'reporter_id', type: 'text' },
      { name: 'reason', type: 'text' },
      { name: 'pack_type', type: 'text' },
    ],
    listRule: null, viewRule: null, createRule: '', updateRule: null, deleteRule: null,
  },
  {
    name: 'admins_list', type: 'base',
    fields: [
      { name: 'email', type: 'email', required: true },
    ],
    listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'publisher_users', type: 'base',
    fields: [
      { name: 'name', type: 'text', required: true },
      { name: 'email', type: 'email' },
      { name: 'avatar_url', type: 'url' },
    ],
    listRule: '', viewRule: '', createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'app_settings', type: 'base',
    fields: [
      { name: 'key', type: 'text', required: true },
      { name: 'value', type: 'json' },
    ],
    listRule: '', viewRule: '', createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'user_submissions', type: 'base',
    fields: [
      { name: 'pack_name', type: 'text', required: true },
      { name: 'publisher_name', type: 'text' },
      { name: 'description', type: 'text' },
      { name: 'category', type: 'text' },
      { name: 'user_id', type: 'text' },
      { name: 'user_email', type: 'email' },
      { name: 'display_name', type: 'text' },
      { name: 'status', type: 'text' },
      { name: 'rejection_reason', type: 'text' },
      { name: 'flag_reasons', type: 'json' },
      { name: 'stickers', type: 'json' },
      { name: 'sticker_data', type: 'json' },
      { name: 'sticker_count', type: 'number' },
      { name: 'source_pack_id', type: 'text' },
      { name: 'sticker_pack_id', type: 'text' },
      { name: 'is_animated', type: 'bool' },
      { name: 'created_at', type: 'text' },
      fileField('images', 99, 5242880),
    ],
    listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: '', updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE,
  },
  {
    name: 'user_favorites', type: 'base',
    fields: [
      { name: 'user_id', type: 'text', required: true },
      { name: 'pack_id', type: 'text', required: true },
      { name: 'pack_type', type: 'text' },
    ],
    listRule: '', viewRule: '', createRule: '', updateRule: '', deleteRule: '',
  },
  {
    name: 'purchased_packs', type: 'base',
    fields: [
      { name: 'user_id', type: 'text', required: true },
      { name: 'pack_id', type: 'text', required: true },
      { name: 'order_id', type: 'text' },
      { name: 'purchase_date', type: 'date' },
    ],
    listRule: '', viewRule: '', createRule: '', updateRule: null, deleteRule: null,
  },
];

async function main() {
  await auth();

  // Get existing collections
  const existing = await api('GET', '/api/collections?perPage=100');
  const existingNames = new Set((existing?.items || []).map(c => c.name));

  for (const col of collections) {
    if (existingNames.has(col.name)) {
      console.log(`  ${col.name}: already exists, updating fields...`);
      // Get current collection
      const current = await api('GET', `/api/collections/${col.name}`);
      if (current) {
        const currentFieldNames = new Set((current.fields || []).map(f => f.name));
        const newFields = col.fields.filter(f => !currentFieldNames.has(f.name));
        if (newFields.length > 0) {
          const allFields = [...current.fields, ...newFields];
          await api('PATCH', `/api/collections/${col.name}`, { fields: allFields });
          console.log(`    Added ${newFields.length} new fields`);
        }
        // Update rules
        const rules = {};
        for (const r of ['listRule','viewRule','createRule','updateRule','deleteRule']) {
          if (r in col) rules[r] = col[r];
        }
        await api('PATCH', `/api/collections/${col.name}`, rules);
      }
      continue;
    }

    process.stdout.write(`  Creating ${col.name}... `);
    const data = {
      name: col.name,
      type: col.type || 'base',
      fields: col.fields,
      listRule: col.listRule,
      viewRule: col.viewRule,
      createRule: col.createRule,
      updateRule: col.updateRule,
      deleteRule: col.deleteRule,
    };
    const result = await api('POST', '/api/collections', data);
    console.log(result ? '✓' : '✗');
  }

  // Update users collection with extra fields
  const usersCol = await api('GET', '/api/collections/users');
  if (usersCol) {
    const currentFields = new Set(usersCol.fields.map(f => f.name));
    const extraFields = [
      { name: 'device_id', type: 'text' },
      { name: 'premium', type: 'bool' },
      { name: 'is_premium', type: 'bool' },
      { name: 'premium_type', type: 'text' },
      { name: 'premium_expiry', type: 'number' },
      { name: 'display_name', type: 'text' },
      { name: 'packs_published', type: 'number' },
      { name: 'favorites', type: 'json' },
      { name: 'favorite_packs', type: 'json' },
      { name: 'total_stickers_added', type: 'number' },
      { name: 'custom_packs_count', type: 'number' },
      { name: 'photo_url', type: 'text' },
      { name: 'total_favorites', type: 'number' },
      fileField('avatar', 1, 2097152, ['image/webp','image/png','image/jpeg']),
    ].filter(f => !currentFields.has(f.name));
    
    if (extraFields.length > 0) {
      await api('PATCH', '/api/collections/users', { 
        fields: [...usersCol.fields, ...extraFields],
        listRule: '', viewRule: '', createRule: '', updateRule: '', deleteRule: '',
      });
      console.log(`  users: added ${extraFields.length} extra fields`);
    }
  }

  console.log('\n✅ Schema setup complete!');
}

main().catch(e => { console.error('FATAL:', e); process.exit(1); });
