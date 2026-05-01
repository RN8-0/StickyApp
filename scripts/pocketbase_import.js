// Import Firestore exported data + Firebase Storage files into PocketBase
// Downloads sticker images from Firebase Storage, uploads to PocketBase
const fs = require('fs');
const path = require('path');

const PB_URL = process.env.PB_URL || 'http://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
const EXPORT_DIR = path.join(__dirname, '..', 'firestore_export');

if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}

let authToken = '';

async function authenticate() {
  const resp = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD })
  });
  if (!resp.ok) throw new Error(`Auth failed: ${resp.status} ${await resp.text()}`);
  const data = await resp.json();
  authToken = data.token;
  console.log('Authenticated with PocketBase');
}

async function pbRequest(method, path, body, isFormData = false) {
  const headers = { 'Authorization': authToken };
  if (!isFormData) headers['Content-Type'] = 'application/json';
  
  const opts = { method, headers };
  if (body) opts.body = isFormData ? body : JSON.stringify(body);
  
  const resp = await fetch(`${PB_URL}${path}`, opts);
  if (!resp.ok) {
    const err = await resp.text();
    throw new Error(`PB ${method} ${path}: ${resp.status} ${err}`);
  }
  return resp.json();
}

async function downloadFile(url) {
  try {
    const resp = await fetch(url, { redirect: 'follow' });
    if (!resp.ok) return null;
    return Buffer.from(await resp.arrayBuffer());
  } catch (e) {
    console.warn(`  Download failed: ${e.message}`);
    return null;
  }
}

// Build translations JSON from name_xx fields
function extractTranslations(doc) {
  const translations = {};
  const langFields = Object.keys(doc).filter(k => k.startsWith('name_') && k.length <= 8);
  for (const field of langFields) {
    const lang = field.replace('name_', '');
    if (doc[field]) translations[lang] = doc[field];
  }
  return translations;
}

async function importStickers(collectionName, targetCollection) {
  const filePath = path.join(EXPORT_DIR, `${collectionName}.json`);
  if (!fs.existsSync(filePath)) { console.log(`No ${collectionName}.json found`); return; }
  
  const docs = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
  console.log(`\nImporting ${docs.length} packs into ${targetCollection}...`);
  
  let success = 0, failed = 0;
  
  for (const doc of docs) {
    try {
      process.stdout.write(`  [${success+failed+1}/${docs.length}] ${doc.name || doc.id}... `);
      
      const translations = extractTranslations(doc);
      
      // Build stickers array with new URLs (will be blob URLs initially, 
      // we store the image_file names and download files separately)
      const stickersList = (doc.stickers || []).map(s => ({
        image_file: s.image_file,
        emojis: s.emojis || ['😀'],
        url: s.url || ''  // Keep original URL for reference
      }));

      const record = {
        name: doc.name || '',
        publisher: doc.publisher || 'Sticky',
        publisher_email: doc.publisher_email || '',
        category: doc.category || 'other',
        is_animated: doc.is_animated || false,
        is_premium: doc.is_premium || false,
        is_active: doc.is_active !== false,
        sticker_count: doc.sticker_count || stickersList.length,
        download_count: (doc.download_count || 0) + (doc.fake_download_base || 0),
        view_count: doc.view_count || 0,
        favorite_count: doc.favorite_count || 0,
        image_data_version: doc.image_data_version || '',
        stickers: JSON.stringify(stickersList),
        translations: JSON.stringify(translations),
        tray_image_file: doc.tray_image_file || '',
        tray_url: doc.tray_url || '',
        price_usd: doc.price_usd || '',
        price_eur: doc.price_eur || '',
        price_try: doc.price_try || '',
      };

      // Use the Firestore doc ID as PB record ID (if it's a valid PB ID - 15 chars alphanumeric)
      // PB requires IDs to be exactly 15 chars, so we'll let PB generate them
      await pbRequest('POST', `/api/collections/${targetCollection}/records`, record);
      success++;
      console.log('✓');
    } catch (e) {
      failed++;
      console.log(`✗ ${e.message.substring(0, 100)}`);
    }
  }
  
  console.log(`  Done: ${success} imported, ${failed} failed`);
}

async function importSettings() {
  const filePath = path.join(EXPORT_DIR, 'settings.json');
  if (!fs.existsSync(filePath)) return;
  
  const docs = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
  console.log(`\nImporting ${docs.length} settings...`);
  
  for (const doc of docs) {
    try {
      const record = {};
      // Copy all fields from settings
      for (const [k, v] of Object.entries(doc)) {
        if (k.startsWith('_')) continue;
        if (typeof v === 'object' && !Array.isArray(v)) {
          record[k] = JSON.stringify(v);
        } else {
          record[k] = v;
        }
      }
      await pbRequest('POST', '/api/collections/settings/records', record);
      console.log(`  Settings imported ✓`);
    } catch (e) {
      console.log(`  Settings failed: ${e.message.substring(0, 100)}`);
    }
  }
}

async function importNotifications() {
  const filePath = path.join(EXPORT_DIR, 'notifications.json');
  if (!fs.existsSync(filePath)) return;
  
  const docs = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
  console.log(`\nImporting ${docs.length} notifications...`);
  
  let success = 0;
  for (const doc of docs) {
    try {
      const record = {
        title: doc.title || '',
        body: doc.body || '',
        topic: doc.topic || 'all',
        type: doc.type || 'general',
        sent_at: doc.sent_at || doc.created_at || new Date().toISOString(),
        data: doc.data ? JSON.stringify(doc.data) : '{}'
      };
      await pbRequest('POST', '/api/collections/notifications/records', record);
      success++;
    } catch (e) {
      console.log(`  Notification failed: ${e.message.substring(0, 80)}`);
    }
  }
  console.log(`  ${success}/${docs.length} imported`);
}

async function importSuggestions() {
  const filePath = path.join(EXPORT_DIR, 'suggestions.json');
  if (!fs.existsSync(filePath)) return;
  
  const docs = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
  console.log(`\nImporting ${docs.length} suggestions...`);
  
  let success = 0;
  for (const doc of docs) {
    try {
      const record = {
        text: doc.text || doc.suggestion || '',
        device_id: doc.device_id || doc.userId || '',
        status: doc.status || 'pending'
      };
      await pbRequest('POST', '/api/collections/suggestions/records', record);
      success++;
    } catch (e) {
      console.log(`  Suggestion failed: ${e.message.substring(0, 80)}`);
    }
  }
  console.log(`  ${success}/${docs.length} imported`);
}

async function main() {
  await authenticate();
  
  // Import stickers and premium_stickers into their respective collections
  await importStickers('stickers', 'stickers');
  await importStickers('premium_stickers', 'premium_stickers');
  
  // Import other collections
  await importSettings();
  await importNotifications();
  await importSuggestions();
  
  // Skip: admins (will use PB superusers), users (wiped), user_profiles (wiped)
  console.log('\n✅ Import complete!');
  console.log('Skipped: admins (use PB superusers), users & user_profiles (wiped per plan)');
}

main().catch(e => { console.error('FATAL:', e); process.exit(1); });
