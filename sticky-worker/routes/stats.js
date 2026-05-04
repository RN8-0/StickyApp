const { Router } = require('express');
const fetch = require('node-fetch');

const router = Router();

const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'arainunger@gmail.com';
const PB_ADMIN_PASS = process.env.PB_ADMIN_PASS || 'StickyAdmin2026!';

let pbToken = '';

async function authenticate() {
  if (!PB_ADMIN_PASS) throw new Error('PB_ADMIN_PASS is required.');
  const response = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASS }),
  });
  if (!response.ok) throw new Error(`PB auth failed: ${response.status}`);
  const data = await response.json();
  pbToken = data.token;
}

async function pbFetch(path, opts = {}) {
  if (!pbToken) await authenticate();
  const response = await fetch(`${PB_URL}${path}`, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...opts.headers, Authorization: pbToken },
  });
  if (response.status === 401) {
    await authenticate();
    return pbFetch(path, opts);
  }
  return response;
}

router.post('/increment', async (req, res) => {
  try {
    const { collection, packId, field } = req.body || {};
    const delta = Number(req.body?.delta || 1);
    const allowedCollections = new Set(['stickers', 'premium_stickers']);
    const allowedFields = new Set(['view_count', 'download_count', 'favorite_count']);

    if (!allowedCollections.has(collection) || !allowedFields.has(field) || !packId || !Number.isFinite(delta)) {
      return res.status(400).json({ error: 'invalid counter request' });
    }

    const recordResponse = await pbFetch(`/api/collections/${collection}/records/${encodeURIComponent(packId)}`);
    if (!recordResponse.ok) return res.status(recordResponse.status).json({ error: await recordResponse.text() });
    const record = await recordResponse.json();
    const next = Math.max(0, Number(record[field] || 0) + delta);

    const updateResponse = await pbFetch(`/api/collections/${collection}/records/${encodeURIComponent(packId)}`, {
      method: 'PATCH',
      body: JSON.stringify({ [field]: next }),
    });
    if (!updateResponse.ok) return res.status(updateResponse.status).json({ error: await updateResponse.text() });

    res.json({ success: true, value: next });
  } catch (error) {
    console.error('[Stats increment]', error.message);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;