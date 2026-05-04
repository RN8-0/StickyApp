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

async function fetchAllRecords(collection) {
  const records = [];
  let page = 1;
  while (true) {
    const resp = await pbFetch(`/api/collections/${collection}/records?perPage=200&page=${page}`);
    if (!resp.ok) throw new Error(await resp.text());
    const data = await resp.json();
    records.push(...(data.items || []));
    if (page >= data.totalPages) break;
    page++;
  }
  return records;
}

function userKey(user) {
  return (user.email || '').toLowerCase().trim() || user.device_id || user.user_id || user.uid || user.id;
}

function userIdentifiers(user) {
  return new Set([
    user.id,
    user.user_id,
    user.uid,
    user.device_id,
    user.email,
    user.display_name,
    user.name,
  ].map((value) => String(value || '').trim().toLowerCase()).filter(Boolean));
}

function matchesAnyUserField(record, identifiers, fields) {
  return fields
    .map((field) => String(record[field] || '').trim().toLowerCase())
    .filter(Boolean)
    .some((value) => identifiers.has(value));
}

function mergeUserProfile(profile, authUser = {}) {
  return {
    ...authUser,
    ...profile,
    email: profile.email || authUser.email || '',
    display_name: profile.display_name || profile.displayName || profile.name || authUser.display_name || authUser.name || '',
    name: profile.name || profile.display_name || authUser.name || authUser.display_name || '',
    photo_url: profile.photo_url || profile.photoURL || profile.avatar_url || profile.picture || authUser.photo_url || authUser.photoURL || authUser.avatar_url || authUser.picture || '',
    user_id: profile.user_id || profile.uid || authUser.uid || authUser.user_id || authUser.id || '',
    uid: profile.uid || profile.user_id || authUser.uid || authUser.user_id || authUser.id || '',
    created_at: profile.created_at || profile.joined_at || profile.created || authUser.created_at || authUser.created || null,
    last_sync: profile.last_sync || profile.updated || authUser.updated || null,
  };
}

// GET /api/users - fetch migrated users from PocketBase user_profiles (deduplicated)
router.get('/', async (req, res) => {
  try {
    const profiles = await fetchAllRecords('user_profiles');
    const authUsers = await fetchAllRecords('users').catch((err) => {
      console.warn('[Users GET] auth users merge skipped:', err.message);
      return [];
    });
    const [follows, comments, likes, stickerPacks, premiumPacks] = await Promise.all([
      fetchAllRecords('user_follows').catch((err) => { console.warn('[Users GET] follows skipped:', err.message); return []; }),
      fetchAllRecords('pack_comments').catch((err) => { console.warn('[Users GET] comments skipped:', err.message); return []; }),
      fetchAllRecords('pack_likes').catch((err) => { console.warn('[Users GET] likes skipped:', err.message); return []; }),
      fetchAllRecords('stickers').catch((err) => { console.warn('[Users GET] stickers skipped:', err.message); return []; }),
      fetchAllRecords('premium_stickers').catch((err) => { console.warn('[Users GET] premium stickers skipped:', err.message); return []; }),
    ]);

    const authByKey = new Map();
    for (const user of authUsers) authByKey.set(userKey(user), user);

    const users = profiles.map((profile) => mergeUserProfile(profile, authByKey.get(userKey(profile))));
    for (const authUser of authUsers) {
      const key = userKey(authUser);
      if (!users.some((profile) => userKey(profile) === key)) {
        users.push(mergeUserProfile({}, authUser));
      }
    }

    users.sort((a, b) => sortTimestamp(b.created_at || b.joined_at || b.created) - sortTimestamp(a.created_at || a.joined_at || a.created));

    // Deduplicate: keep newest record per unique email/device_id key
    const seen = new Map();
    const deduped = [];
    for (const u of users) {
      const key = userKey(u);
      if (!seen.has(key)) {
        seen.set(key, true);
        const identifiers = userIdentifiers(u);
        const publishedPacks = [...stickerPacks, ...premiumPacks].filter((pack) =>
          matchesAnyUserField(pack, identifiers, ['publisher_user_id', 'publisher_email', 'email', 'publisher', 'device_id', 'user_id'])
        );
        const userComments = comments.filter((comment) =>
          matchesAnyUserField(comment, identifiers, ['user_id', 'user_email', 'display_name'])
        );
        const followerCount = follows.filter((follow) =>
          matchesAnyUserField(follow, identifiers, ['target_id', 'target_email', 'following_id', 'following_email'])
        );
        const followingCount = follows.filter((follow) =>
          matchesAnyUserField(follow, identifiers, ['follower_id', 'follower_email'])
        );
        const publishedPackIds = new Set(publishedPacks.map((pack) => pack.id));
        const receivedLikes = likes.filter((like) => publishedPackIds.has(like.pack_id));
        deduped.push({
          ...u,
          bio: u.bio || '',
          show_email: u.show_email !== false,
          social: {
            followers: followerCount.length,
            following: followingCount.length,
            comments: userComments.length,
            likes: receivedLikes.length,
            published_packs: publishedPacks.length,
          },
          followers_list: followerCount.map((follow) => ({
            id: follow.follower_id || '',
            email: follow.follower_email || '',
            name: follow.follower_name || follow.follower_email || '',
            photo_url: follow.follower_photo || '',
          })),
          following_list: followingCount.map((follow) => ({
            id: follow.target_id || follow.following_id || '',
            email: follow.target_email || follow.following_email || '',
            name: follow.target_name || follow.following_name || follow.target_email || follow.following_email || '',
            photo_url: follow.target_photo || follow.following_photo || '',
          })),
          published_packs: publishedPacks.map((pack) => ({
            id: pack.id,
            name: pack.name || pack.name_en || pack.name_tr || pack.pack_name || pack.id,
            publisher: pack.publisher || '',
            sticker_count: Number(pack.sticker_count || (Array.isArray(pack.stickers) ? pack.stickers.length : 0) || 0),
            download_count: Number(pack.download_count || 0),
            favorite_count: Number(pack.favorite_count || 0),
            like_count: Number(pack.like_count || likes.filter((like) => like.pack_id === pack.id).length || 0),
            comment_count: Number(pack.comment_count || comments.filter((comment) => comment.pack_id === pack.id).length || 0),
            engagement_score: Number(pack.engagement_score || 0),
          })),
          recent_comments: userComments
            .slice()
            .sort((a, b) => sortTimestamp(b.created || b.created_at) - sortTimestamp(a.created || a.created_at))
            .slice(0, 10)
            .map((comment) => ({
              id: comment.id,
              pack_id: comment.pack_id,
              body: comment.body || '',
              created_at: comment.created_at || comment.created || null,
            })),
        });
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
