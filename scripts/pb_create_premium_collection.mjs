#!/usr/bin/env node
// Create the premium_stickers collection in PocketBase
const PB = process.env.PB_URL || 'http://localhost:8090';
const EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PASS = process.env.PB_ADMIN_PASSWORD;
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

async function main() {
  // 1. Authenticate as superuser
  console.log('Authenticating...');
  const auth = await api('POST', '/api/collections/_superusers/auth-with-password', {
    identity: EMAIL, password: PASS
  });
  const token = auth.token;
  console.log('Authenticated as superuser');

  // 2. Create premium_stickers collection (same schema as stickers + price fields)
  const premiumSchema = {
    name: 'premium_stickers',
    type: 'base',
    schema: [
      { name: 'name', type: 'text', required: true },
      { name: 'publisher', type: 'text' },
      { name: 'publisher_email', type: 'email' },
      { name: 'publisher_user_id', type: 'text' },
      { name: 'category', type: 'text' },
      { name: 'is_premium', type: 'bool' },
      { name: 'is_animated', type: 'bool' },
      { name: 'is_active', type: 'bool' },
      { name: 'is_popular', type: 'bool' },
      { name: 'tray_image_file', type: 'text' },
      { name: 'tray_url', type: 'url' },
      { name: 'stickers', type: 'json' },
      { name: 'sticker_data', type: 'json' },
      { name: 'sticker_count', type: 'number' },
      { name: 'image_data_version', type: 'text' },
      { name: 'download_count', type: 'number' },
      { name: 'view_count', type: 'number' },
      { name: 'favorite_count', type: 'number' },
      { name: 'whatsapp_add_count', type: 'number' },
      { name: 'fake_download_base', type: 'number' },
      { name: 'privacy_policy_website', type: 'url' },
      { name: 'license_agreement_website', type: 'url' },
      { name: 'price_try', type: 'number' },
      { name: 'price_usd', type: 'number' },
      { name: 'price_eur', type: 'number' },
      { name: 'source', type: 'text' },
      { name: 'telegram_set_name', type: 'text' },
      { name: 'telegram_set_title', type: 'text' },
      { name: 'telegram_part', type: 'number' },
      { name: 'telegram_total_parts', type: 'number' },
      { name: 'batch_generated', type: 'bool' },
      { name: 'batch_source', type: 'text' },
      { name: 'batch_search_term', type: 'text' },
      { name: 'disabled_reason', type: 'text' },
      { name: 'created_at', type: 'text' },
      {
        name: 'images', type: 'file',
        maxSelect: 99, maxSize: 10485760,
        mimeTypes: ['image/webp', 'image/png', 'image/gif'],
        protected: false
      },
      {
        name: 'tray_image', type: 'file',
        maxSelect: 1, maxSize: 5242880,
        mimeTypes: ['image/webp', 'image/png'],
        protected: false
      },
      ...LANGS.map(l => ({ name: `name_${l}`, type: 'text' })),
    ],
    listRule: '',
    viewRule: '',
    createRule: '@collection.admins_list.email ?= @request.auth.email',
    updateRule: '',
    deleteRule: '@collection.admins_list.email ?= @request.auth.email',
  };

  console.log('Creating premium_stickers collection...');
  const created = await api('POST', '/api/collections', premiumSchema, token);
  console.log(`✅ premium_stickers created! (id: ${created.id})`);

  // 3. Now run the patch script to add any missing fields
  console.log('\nRunning pb_patch_schema.mjs to verify all fields...');
  const { execSync } = await import('child_process');
  execSync('node scripts/pb_patch_schema.mjs', {
    stdio: 'inherit',
    env: { ...process.env, PB_URL: PB, PB_ADMIN_EMAIL: EMAIL, PB_ADMIN_PASSWORD: PASS }
  });
}

main().catch(err => {
  console.error('\n❌ Error:', err.message);
  process.exit(1);
});
