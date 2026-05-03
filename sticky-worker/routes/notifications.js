const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const PB_URL = process.env.PB_URL || 'http://pocketbase:8090';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PB_ADMIN_PASS = process.env.PB_ADMIN_PASS || process.env.PB_ADMIN_PASSWORD;

let pbToken = '';

async function authenticate() {
  if (!PB_ADMIN_PASS) {
    console.warn('[Notifications] PB_ADMIN_PASS not set');
    return;
  }
  // Try new endpoint first (PB >= 0.20)
  let resp = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASS }),
  });
  if (!resp.ok) {
    // Fallback to legacy
    resp = await fetch(`${PB_URL}/api/admins/auth-with-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASS }),
    });
  }
  if (!resp.ok) {
    console.error('[Notifications] PB auth failed:', resp.status, await resp.text());
    throw new Error(`PB auth failed: ${resp.status}`);
  }
  const data = await resp.json();
  pbToken = data.token;
  console.log('[Notifications] Authenticated, token length:', pbToken.length);
}

async function pbFetch(path, opts = {}) {
  if (!pbToken) await authenticate();
  let resp = await fetch(`${PB_URL}${path}`, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...opts.headers, Authorization: pbToken },
  });
  if (resp.status === 401) {
    await authenticate();
    resp = await fetch(`${PB_URL}${path}`, {
      ...opts,
      headers: { 'Content-Type': 'application/json', ...opts.headers, Authorization: pbToken },
    });
  }
  return resp;
}

// GET /api/notifications/_debug — diagnostic endpoint
router.get('/_debug', async (req, res) => {
  try {
    const userId = req.query.userId || req.query.deviceId;
    const noFilterUrl = `/api/collections/notifications/records?perPage=1`;
    const filterUrl = userId
      ? `/api/collections/notifications/records?filter=${encodeURIComponent(`user_id='${String(userId).replace(/'/g, "\\'")}'`)}&perPage=5`
      : null;

    const r1 = await pbFetch(noFilterUrl);
    const t1 = await r1.text();
    let b1; try { b1 = JSON.parse(t1); } catch { b1 = t1; }

    let filterResult = null;
    if (filterUrl) {
      const r2 = await pbFetch(filterUrl);
      const t2 = await r2.text();
      let b2; try { b2 = JSON.parse(t2); } catch { b2 = t2; }
      filterResult = { url: PB_URL + filterUrl, status: r2.status, body: b2 };
    }

    res.json({
      pb_url: PB_URL,
      pb_admin_email: PB_ADMIN_EMAIL,
      has_password: !!PB_ADMIN_PASS,
      token_length: pbToken ? pbToken.length : 0,
      no_filter: { status: r1.status, total: b1.totalItems },
      filter_result: filterResult,
    });
  } catch (e) {
    res.json({ error: e.message, pb_url: PB_URL, has_password: !!PB_ADMIN_PASS });
  }
});

// GET /api/notifications?userId=...&email=...&deviceId=...
router.get('/', async (req, res) => {
  try {
    const { userId, email, deviceId } = req.query;
    const ids = [userId, email, deviceId].filter(v => v && String(v).trim());
    if (ids.length === 0) return res.json([]);

    // Include broadcast notifications (user_id='broadcast') so all users see admin announcements
    const filter = '(' + ids.map(v => `user_id='${String(v).replace(/'/g, "\\'")}'`).join(' || ') + ' || user_id=\'broadcast\')';
    const url = `/api/collections/notifications/records?filter=${encodeURIComponent(filter)}&sort=-timestamp&perPage=100`;
    const pbRes = await pbFetch(url);
    if (!pbRes.ok) {
      const errText = await pbRes.text();
      console.error('[Notifications GET] PB error:', pbRes.status, errText);
      return res.json([]);
    }
    const data = await pbRes.json();
    console.log('[Notifications GET]', { ids, filter, url: PB_URL + url, total: data.totalItems, returned: (data.items || []).length });
    res.json(data.items || []);
  } catch (e) {
    console.error('[Notifications GET] error:', e.message, e.stack);
    res.json([]);
  }
});

// POST /api/notifications — create in-app notification record
router.post('/', async (req, res) => {
  try {
    const { title, body, message, user_id, type, from, timestamp, image_url, imageUrl, pack_id, data } = req.body;
    if (!user_id || (!body && !message)) {
      return res.status(400).json({ error: 'user_id and body required' });
    }
    const payload = {
      title: title || 'Sticky',
      body: body || message || '',
      message: body || message || '',
      user_id: String(user_id),
      from: from || 'admin',
      read: false,
      sent: false,
      timestamp: timestamp || new Date().toISOString(),
      ...(image_url || imageUrl ? { image_url: image_url || imageUrl, imageUrl: image_url || imageUrl } : {}),
      ...(pack_id ? { pack_id } : {}),
      ...(type ? { topic: type } : {}),
      ...(data ? { data } : {}),
    };
    const pbRes = await pbFetch('/api/collections/notifications/records', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    const respData = await pbRes.json();
    if (!pbRes.ok) {
      console.error('[Notifications POST] PB error:', pbRes.status, respData);
      return res.status(500).json({ success: false, error: respData });
    }
    res.json({ success: true, id: respData.id });
  } catch (e) {
    console.error('[Notifications POST] error:', e.message);
    res.status(500).json({ success: false, error: e.message });
  }
});

// PATCH /api/notifications/:id — mark as read
router.patch('/:id', async (req, res) => {
  try {
    const pbRes = await pbFetch(`/api/collections/notifications/records/${req.params.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ read: true }),
    });
    res.json({ success: pbRes.ok });
  } catch (e) {
    console.error('[Notifications PATCH] error:', e.message);
    res.json({ success: false });
  }
});

// DELETE /api/notifications — clear all for user
router.delete('/', async (req, res) => {
  try {
    const { userId, email, deviceId } = req.query;
    const ids = [userId, email, deviceId].filter(v => v && String(v).trim());
    if (ids.length === 0) return res.json({ success: false });

    const filter = ids.map(v => `user_id='${String(v).replace(/'/g, "\\'")}'`).join(' || ');
    const listUrl = `/api/collections/notifications/records?filter=${encodeURIComponent(filter)}&perPage=200&fields=id`;
    const listRes = await pbFetch(listUrl);
    if (!listRes.ok) return res.json({ success: false });
    const listData = await listRes.json();
    const recordIds = (listData.items || []).map(r => r.id);

    let deleted = 0;
    for (const id of recordIds) {
      const r = await pbFetch(`/api/collections/notifications/records/${id}`, { method: 'DELETE' });
      if (r.ok || r.status === 204) deleted++;
    }
    res.json({ success: true, deleted });
  } catch (e) {
    console.error('[Notifications DELETE] error:', e.message);
    res.json({ success: false });
  }
});

module.exports = router;
