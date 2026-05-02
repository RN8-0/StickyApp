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

// GET /api/users - fetch migrated users from PocketBase user_profiles
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
    res.json({ users });
  } catch (err) {
    console.error('[Users GET]', err.message);
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

module.exports = router;
