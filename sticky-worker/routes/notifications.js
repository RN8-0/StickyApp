const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL;
const PB_ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD || process.env.PB_ADMIN_PASS;

let pbAdminToken = null;
let pbTokenExpiry = 0;

async function getPbAdminToken() {
  if (pbAdminToken && Date.now() < pbTokenExpiry) return pbAdminToken;
  if (!PB_ADMIN_EMAIL || !PB_ADMIN_PASSWORD) return null;
  try {
    const res = await fetch(`${PB_URL}/api/admins/auth-with-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASSWORD }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    pbAdminToken = data.token;
    pbTokenExpiry = Date.now() + 50 * 60 * 1000; // 50 minutes
    return pbAdminToken;
  } catch (e) {
    console.error('[Notifications] PB admin auth failed:', e.message);
    return null;
  }
}

// GET /api/notifications?userId=...&email=...&deviceId=...
router.get('/', async (req, res) => {
  try {
    const { userId, email, deviceId } = req.query;
    const filters = [userId, email, deviceId]
      .filter(v => v && v.trim())
      .map(v => `user_id='${v.replace(/'/g, "\\'")}'`)
      .join(' || ');

    if (!filters) return res.json([]);

    const token = await getPbAdminToken();
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = token;

    const url = `${PB_URL}/api/collections/notifications/records?filter=${encodeURIComponent(filters)}&sort=-created&perPage=100`;
    const pbRes = await fetch(url, { headers });
    if (!pbRes.ok) {
      const errText = await pbRes.text();
      console.error('[Notifications] PB error:', pbRes.status, errText);
      return res.json([]);
    }
    const data = await pbRes.json();
    res.json(data.items || []);
  } catch (e) {
    console.error('[Notifications] GET error:', e.message);
    res.json([]);
  }
});

// PATCH /api/notifications/:id — mark as read
router.patch('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const token = await getPbAdminToken();
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = token;

    const pbRes = await fetch(`${PB_URL}/api/collections/notifications/records/${id}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({ read: true }),
    });
    res.json({ success: pbRes.ok });
  } catch (e) {
    console.error('[Notifications] PATCH error:', e.message);
    res.json({ success: false });
  }
});

// DELETE /api/notifications — clear all for user
router.delete('/', async (req, res) => {
  try {
    const { userId, email, deviceId } = req.query;
    const filters = [userId, email, deviceId]
      .filter(v => v && v.trim())
      .map(v => `user_id='${v.replace(/'/g, "\\'")}'`)
      .join(' || ');

    if (!filters) return res.json({ success: false });

    const token = await getPbAdminToken();
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = token;

    // Fetch IDs first, then delete
    const listUrl = `${PB_URL}/api/collections/notifications/records?filter=${encodeURIComponent(filters)}&perPage=200&fields=id`;
    const listRes = await fetch(listUrl, { headers });
    if (!listRes.ok) return res.json({ success: false });
    const listData = await listRes.json();
    const ids = (listData.items || []).map((r) => r.id);

    await Promise.all(ids.map(id =>
      fetch(`${PB_URL}/api/collections/notifications/records/${id}`, {
        method: 'DELETE',
        headers,
      }).catch(() => {})
    ));

    res.json({ success: true, deleted: ids.length });
  } catch (e) {
    console.error('[Notifications] DELETE error:', e.message);
    res.json({ success: false });
  }
});

module.exports = router;
