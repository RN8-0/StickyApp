const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const PB_URL = process.env.PB_URL || 'http://pocketbase:8090';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PB_ADMIN_PASS = process.env.PB_ADMIN_PASS;

let pbToken = '';

async function authenticate() {
  if (!PB_ADMIN_PASS) throw new Error('PB_ADMIN_PASS is required.');
  const resp = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASS })
  });
  if (!resp.ok) throw new Error(`PB auth failed: ${resp.status}`);
  const data = await resp.json();
  pbToken = data.token;
}

async function pbFetch(path, opts = {}) {
  if (!pbToken) await authenticate();
  const resp = await fetch(`${PB_URL}${path}`, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...opts.headers, Authorization: pbToken }
  });
  if (resp.status === 401) {
    await authenticate();
    return pbFetch(path, opts);
  }
  return resp;
}

function sortTimestamp(value) {
  if (!value) return 0;
  if (typeof value === 'number') return value;
  if (typeof value === 'string') return Date.parse(value) || 0;
  if (value._seconds) return value._seconds * 1000;
  return 0;
}

// GET /api/users - fetch migrated users from PocketBase user_profiles (deduplicated)
router.get('/', async (req, res) => {
  try {
    const users = [];
    let page = 1;
    while (true) {
      const resp = await pbFetch(`/api/collections/user_profiles/records?perPage=200&page=${page}`);
      if (!resp.ok) throw new Error(await resp.text());
      const data = await resp.json();
      users.push(...(data.items || []));
      if (page >= data.totalPages) break;
      page++;
    }
    users.sort((a, b) => sortTimestamp(b.created_at || b.joined_at || b.created) - sortTimestamp(a.created_at || a.joined_at || a.created));

    // Deduplicate: keep newest record per unique email/device_id key
    const seen = new Map();
    const deduped = [];
    for (const u of users) {
      const key = (u.email || '').toLowerCase().trim() || u.device_id || u.id;
      if (!seen.has(key)) {
        seen.set(key, true);
        deduped.push(u);
      }
    }

    res.json({ users: deduped });
  } catch (err) {
    console.error('[Users GET]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// DELETE /api/users/duplicates - remove duplicate user_profile records, keep newest per email
router.delete('/duplicates', async (req, res) => {
  try {
    const users = [];
    let page = 1;
    while (true) {
      const resp = await pbFetch(`/api/collections/user_profiles/records?perPage=200&page=${page}`);
      if (!resp.ok) throw new Error(await resp.text());
      const data = await resp.json();
      users.push(...(data.items || []));
      if (page >= data.totalPages) break;
      page++;
    }
    users.sort((a, b) => sortTimestamp(b.created_at || b.joined_at || b.created) - sortTimestamp(a.created_at || a.joined_at || a.created));

    const seen = new Map();
    const toDelete = [];
    for (const u of users) {
      const key = (u.email || '').toLowerCase().trim() || u.device_id || u.id;
      if (!seen.has(key)) {
        seen.set(key, true);
      } else {
        toDelete.push(u.id);
      }
    }

    let deleted = 0;
    for (const id of toDelete) {
      const r = await pbFetch(`/api/collections/user_profiles/records/${id}`, { method: 'DELETE' });
      if (r.ok || r.status === 204) deleted++;
    }

    res.json({ success: true, deleted, total: users.length });
  } catch (err) {
    console.error('[Users DELETE duplicates]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// PATCH /api/users/:id - update user subscription
router.patch('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const { is_premium, premium_type, premium_expiry, subscription_source, historyItem } = req.body;

    const currentResp = await pbFetch(`/api/collections/user_profiles/records/${id}`);
    if (!currentResp.ok) throw new Error(await currentResp.text());
    const current = await currentResp.json();
    const subscriptionHistory = Array.isArray(current.subscription_history) ? current.subscription_history : [];

    const update = {
      is_premium,
      premium_type,
      premium_expiry,
      subscription_source,
      last_sync: new Date().toISOString(),
    };
    if (historyItem) update.subscription_history = [...subscriptionHistory, historyItem];

    const updateResp = await pbFetch(`/api/collections/user_profiles/records/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(update),
    });
    if (!updateResp.ok) throw new Error(await updateResp.text());
    res.json({ success: true });
  } catch (err) {
    console.error('[Users PATCH]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// DELETE /api/users/all - delete ALL user_profile records (DANGEROUS — admin use only)
router.delete('/all', async (req, res) => {
  try {
    const ids = [];
    let page = 1;
    while (true) {
      const r = await pbFetch(`/api/collections/user_profiles/records?perPage=200&page=${page}&fields=id`);
      if (!r.ok) break;
      const d = await r.json();
      ids.push(...((d.items || []).map(x => x.id)));
      if (page >= d.totalPages) break;
      page++;
    }
    let deleted = 0;
    for (const id of ids) {
      const r = await pbFetch(`/api/collections/user_profiles/records/${id}`, { method: 'DELETE' });
      if (r.ok || r.status === 204) deleted++;
    }
    res.json({ success: true, deleted, total: ids.length });
  } catch (err) {
    console.error('[Users DELETE all]', err.message);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
