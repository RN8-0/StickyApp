const fetch = require('node-fetch');
const EventSource = require('eventsource');
const admin = require('firebase-admin');

const PB_URL = process.env.PB_URL || 'http://pocketbase:8090';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PB_ADMIN_PASS = process.env.PB_ADMIN_PASS;
const NTFY_URL = process.env.NTFY_URL || 'http://ntfy:80';
const NTFY_TOPIC = process.env.NTFY_TOPIC || 'sticky-stickers';

if (!PB_ADMIN_PASS) {
  throw new Error('PB_ADMIN_PASS is required.');
}

let authToken = '';

let fcmReady = false;
try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    admin.initializeApp({
      credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON))
    });
    fcmReady = true;
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    admin.initializeApp({ credential: admin.credential.applicationDefault() });
    fcmReady = true;
  }
  if (fcmReady) console.log('[FCM] Firebase Admin initialized');
  else console.log('[FCM] Firebase credentials not configured; ntfy fallback only');
} catch (err) {
  console.error('[FCM] init error:', err.message);
}

async function authenticate() {
  const resp = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASS })
  });
  if (!resp.ok) throw new Error(`PB auth failed: ${resp.status}`);
  const data = await resp.json();
  authToken = data.token;
  console.log('[Hooks] Authenticated with PocketBase');
  return authToken;
}

async function pbFetch(path, opts = {}) {
  if (!authToken) await authenticate();
  const resp = await fetch(`${PB_URL}${path}`, {
    ...opts,
    headers: { ...opts.headers, 'Authorization': authToken, 'Content-Type': 'application/json' }
  });
  if (resp.status === 401) {
    await authenticate();
    return pbFetch(path, opts);
  }
  return resp;
}

async function collectFcmTokens(record) {
  if (!fcmReady) return [];
  const tokens = new Set();
  const filters = [];
  const metadata = typeof record.data === 'string'
    ? (() => { try { return JSON.parse(record.data); } catch (_) { return {}; } })()
    : (record.data || {});
  const identifiers = [
    record.user_id,
    record.device_id,
    record.user_email,
    record.recipient_email,
    metadata.user_id,
    metadata.device_id,
    metadata.user_email,
  ].filter(Boolean);
  const escaped = [...new Set(identifiers)].map(value => String(value).replace(/'/g, "\\'"));
  for (const value of escaped) {
    filters.push(`user_id='${value}'`, `device_id='${value}'`, `uid='${value}'`, `id='${value}'`, `email='${value}'`);
  }

  for (const collection of ['users', 'user_profiles']) {
    const query = filters.length ? `filter=(${encodeURIComponent(filters.join(' || '))})&perPage=200` : 'perPage=500';
    try {
      const resp = await pbFetch(`/api/collections/${collection}/records?${query}`);
      const data = await resp.json();
      for (const item of data.items || []) {
        if (item.notifications_enabled === false) continue;
        if (item.fcm_token) tokens.add(item.fcm_token);
        if (Array.isArray(item.fcm_tokens)) item.fcm_tokens.forEach(t => t && tokens.add(t));
      }
    } catch (err) {
      console.error(`[FCM] token lookup failed for ${collection}:`, err.message);
    }
  }

  return Array.from(tokens);
}

async function sendFcmNotification(record, title, body) {
  const tokens = await collectFcmTokens(record);
  if (!tokens.length) {
    console.log('[FCM] No target tokens found');
    return;
  }

  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const response = await admin.messaging().sendEachForMulticast({
      tokens: batch,
      notification: {
        title,
        body,
        imageUrl: record.image_url || record.imageUrl || undefined,
      },
      data: {
        type: String(record.type || 'notification'),
        pack_id: String(record.pack_id || ''),
        notification_id: String(record.id || ''),
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'sticky_notifications',
          imageUrl: record.image_url || record.imageUrl || undefined,
        },
      },
    });
    console.log(`[FCM] Sent batch: success=${response.successCount} failure=${response.failureCount}`);
  }
}

// Translate pack name to all supported languages
const TARGET_LANGUAGES = ['tr','de','fr','es','pt','it','ru','ar','hi','ja','ko','zh','th','vi','id','fil'];

async function translateText(text, targetLang) {
  if (!text || !text.trim()) return text;
  const lang = targetLang === 'fil' ? 'tl' : targetLang;
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=${lang}&dt=t&q=${encodeURIComponent(text)}`;
  const resp = await fetch(url);
  const data = await resp.json();
  return data?.[0]?.[0]?.[0] || text;
}

async function autoTranslate(record, collection) {
  if (!record.name) return;
  // Only translate if name changed or translations missing
  if (record.name_tr && record.name_es && record.name_fr) return;

  console.log(`[Hook] Translating "${record.name}" for ${collection}/${record.id}`);
  const translations = { name_en: record.name };
  await Promise.all(TARGET_LANGUAGES.map(async (lang) => {
    try {
      translations[`name_${lang}`] = await translateText(record.name, lang);
    } catch (e) { /* skip */ }
  }));

  await pbFetch(`/api/collections/${collection}/records/${record.id}`, {
    method: 'PATCH',
    body: JSON.stringify(translations)
  });
  console.log(`[Hook] Translated to ${Object.keys(translations).length} languages`);
}

// Handle new notification → send to ntfy
async function onNotification(record) {
  const title = record.title || 'Sticky';
  const body = record.body || record.message;
  if (!body) return;

  const headers = { 'Title': title, 'Priority': '4', 'Tags': 'sticker' };
  if (record.imageUrl || record.image_url) headers['Attach'] = record.imageUrl || record.image_url;

  try {
    await fetch(`${NTFY_URL}/${NTFY_TOPIC}`, { method: 'POST', headers, body });
    console.log(`[Hook] Notification sent: "${title}"`);
  } catch (err) {
    console.error('[Hook] ntfy error:', err.message);
  }

  try {
    await sendFcmNotification(record, title, body);
  } catch (err) {
    console.error('[FCM] send error:', err.message);
  }
}

// Handle content report → auto-disable pack if 3+ reports
async function onContentReport(record) {
  if (!record.pack_id) return;

  const resp = await pbFetch(`/api/collections/content_reports/records?filter=(pack_id='${record.pack_id}')&fields=id`);
  const data = await resp.json();
  const count = data.totalItems || 0;

  if (count >= 3) {
    for (const col of ['stickers', 'premium_stickers']) {
      try {
        await pbFetch(`/api/collections/${col}/records/${record.pack_id}`, {
          method: 'PATCH',
          body: JSON.stringify({ is_active: false, disabled_reason: 'multiple_reports', report_count: count })
        });
        console.log(`[Hook] Pack ${record.pack_id} disabled (${count} reports)`);
      } catch (e) { /* pack might not exist in this collection */ }
    }
  }
}

// Handle user submission → NSFW check + auto-approve
async function onUserSubmission(record) {
  const submissionId = record.id;
  console.log(`[Hook] User submission ${submissionId} left pending for admin review`);
  if (!record.status || record.status === 'processing') {
    await pbFetch(`/api/collections/user_submissions/records/${submissionId}`, {
      method: 'PATCH',
      body: JSON.stringify({ status: 'pending' })
    }).catch(err => console.error(`[Hook] Pending status update failed for ${submissionId}:`, err.message));
  }
}

// Subscribe to PocketBase SSE events
async function setupPocketBaseHooks() {
  await authenticate();
  console.log('[Hooks] Setting up SSE subscriptions...');

  const collections = [
    { name: 'stickers', handler: (r) => autoTranslate(r, 'stickers') },
    { name: 'premium_stickers', handler: (r) => autoTranslate(r, 'premium_stickers') },
    { name: 'notifications', handler: onNotification },
    { name: 'content_reports', handler: onContentReport },
    { name: 'user_submissions', handler: onUserSubmission },
  ];

  for (const col of collections) {
    subscribeToCollection(col.name, col.handler);
  }
}

function subscribeToCollection(collection, handler) {
  const url = `${PB_URL}/api/realtime`;
  const es = new EventSource(url);

  es.onopen = () => {
    console.log(`[SSE] Connected for ${collection}`);
  };

  es.addEventListener('PB_CONNECT', (e) => {
    const data = JSON.parse(e.data);
    const clientId = data.clientId;
    // Subscribe to collection events
    fetch(`${PB_URL}/api/realtime`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': authToken },
      body: JSON.stringify({ clientId, subscriptions: [collection] })
    }).then(() => {
      console.log(`[SSE] Subscribed to ${collection}`);
    }).catch(err => {
      console.error(`[SSE] Subscribe error for ${collection}:`, err.message);
    });
  });

  es.addEventListener(collection, (e) => {
    try {
      const data = JSON.parse(e.data);
      if (data.action === 'create') {
        handler(data.record).catch(err => console.error(`[SSE] Handler error:`, err.message));
      }
      // Also handle updates for translation (name changes)
      if (data.action === 'update' && (collection === 'stickers' || collection === 'premium_stickers')) {
        handler(data.record).catch(err => console.error(`[SSE] Handler error:`, err.message));
      }
    } catch (err) {
      console.error(`[SSE] Parse error for ${collection}:`, err.message);
    }
  });

  es.onerror = (err) => {
    console.error(`[SSE] Error for ${collection}, reconnecting in 5s...`);
    es.close();
    setTimeout(() => subscribeToCollection(collection, handler), 5000);
  };
}

module.exports = { setupPocketBaseHooks };
