const { Router } = require('express');
const fetch = require('node-fetch');

const router = Router();

const PB_URL = process.env.PB_URL || 'http://pocketbase:8090';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PB_ADMIN_PASS = process.env.PB_ADMIN_PASS;

let pbToken = '';

let socialStatePromise = null;
let socialStateCache = null;
let socialStateExpiresAt = 0;
const SOCIAL_CACHE_TTL = 30_000;

let profilesPromise = null;
let profilesCache = null;
let profilesExpiresAt = 0;
const PROFILES_CACHE_TTL = 30_000;

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
    headers: { 'Content-Type': 'application/json', ...opts.headers, Authorization: 'Bearer ' + pbToken }
  });
  if (resp.status === 401) {
    await authenticate();
    return pbFetch(path, opts);
  }
  return resp;
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

async function safeFetchAll(collection) {
  try {
    return await fetchAllRecords(collection);
  } catch (err) {
    console.warn(`[Social] ${collection} unavailable:`, err.message);
    return [];
  }
}

async function getAllProfiles() {
  if (profilesCache && Date.now() < profilesExpiresAt) {
    return profilesCache;
  }
  if (profilesPromise) {
    return profilesPromise;
  }
  profilesPromise = safeFetchAll('user_profiles').then((profiles) => {
    profilesCache = profiles;
    profilesExpiresAt = Date.now() + PROFILES_CACHE_TTL;
    profilesPromise = null;
    return profiles;
  }).catch((err) => {
    profilesPromise = null;
    throw err;
  });
  return profilesPromise;
}

function clean(value) {
  return String(value || '').trim();
}

function lower(value) {
  return clean(value).toLowerCase();
}

function identityValues(record = {}) {
  return [
    record.id,
    record.user_id,
    record.uid,
    record.owner_id,
    record.created_by,
    record.device_id,
    record.email,
    record.user_email,
    record.publisher_email,
    record.publisher_user_id,
    record.display_name,
    record.name,
    record.publisher,
    record.publisher_name,
  ].map(lower).filter(Boolean);
}

function userMatches(record, identifiers) {
  return identityValues(record).some((value) => identifiers.has(value));
}

function normalizeProfile(profile = {}, fallback = {}) {
  return {
    id: clean(profile.id || fallback.id || fallback.user_id || fallback.uid),
    user_id: clean(profile.user_id || profile.uid || fallback.user_id || fallback.uid || profile.id || fallback.id),
    email: clean(profile.email || fallback.email),
    display_name: clean(profile.display_name || profile.name || fallback.display_name || fallback.name || fallback.publisher),
    photo_url: clean(profile.photo_url || profile.avatar_url || profile.picture || fallback.photo_url || fallback.avatar_url),
    bio: clean(profile.bio || fallback.bio),
    show_email: profile.show_email !== false,
  };
}

function profilePhoto(profile = {}, fallback = '') {
  return clean(profile.photo_url || profile.avatar_url || profile.picture || profile.photo || fallback);
}

function packName(pack, lang = '') {
  const locale = lower(lang).split(/[-_]/)[0];
  const localized = locale ? clean(pack[`name_${locale}`]) : '';
  return clean(localized || pack.name_en || pack.name || pack.name_tr || pack.title || pack.pack_name || pack.id);
}

function fileUrl(collection, recordId, value) {
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;
  return `${PB_URL}/api/files/${collection}/${recordId}/${value}`;
}

function summarizePack(pack, collection, likes, comments, lang = '') {
  const packLikes = likes.filter((item) => clean(item.pack_id) === clean(pack.id)).length;
  const packComments = comments.filter((item) => clean(item.pack_id) === clean(pack.id)).length;
  const stickerData = Array.isArray(pack.stickers) ? pack.stickers : Array.isArray(pack.sticker_data) ? pack.sticker_data : [];
  return {
    id: pack.id,
    collection,
    name: packName(pack, lang),
    name_tr: clean(pack.name_tr),
    name_en: clean(pack.name_en),
    name_es: clean(pack.name_es),
    name_zh: clean(pack.name_zh),
    name_ar: clean(pack.name_ar),
    name_hi: clean(pack.name_hi),
    name_pt: clean(pack.name_pt),
    tray_url: clean(pack.tray_url) || fileUrl(collection, pack.id, Array.isArray(pack.tray_image) ? pack.tray_image[0] : pack.tray_image || pack.tray_image_file),
    sticker_count: Number(pack.sticker_count || stickerData.length || 0),
    stickers: stickerData.slice(0, 6).map((sticker) => {
      const item = sticker && typeof sticker === 'object' ? sticker : { image_file: clean(sticker) };
      return { ...item, url: clean(item.url || item.image_url) || fileUrl(collection, pack.id, item.image_file) };
    }),
    download_count: Number(pack.download_count || 0),
    favorite_count: Number(pack.favorite_count || 0),
    view_count: Number(pack.view_count || 0),
    like_count: Number(pack.like_count || packLikes || 0),
    comment_count: Number(pack.comment_count || packComments || 0),
    engagement_score: Number(pack.engagement_score || calculateEngagementScore({ ...pack, like_count: packLikes, comment_count: packComments }) || 0),
    publisher: clean(pack.publisher),
    publisher_email: clean(pack.publisher_email || pack.email),
    publisher_user_id: clean(pack.publisher_user_id),
  };
}

function calculateEngagementScore(pack = {}) {
  const downloads = Number(pack.download_count || pack.downloads || 0);
  const favorites = Number(pack.favorite_count || pack.favorites || 0);
  const likes = Number(pack.like_count || 0);
  const comments = Number(pack.comment_count || 0);
  const views = Number(pack.view_count || pack.views || 0);
  const createdAt = Date.parse(pack.created || pack.created_at || pack.updated || 0);
  const ageHours = Number.isFinite(createdAt) && createdAt > 0 ? Math.max(1, (Date.now() - createdAt) / 36e5) : 240;
  const freshness = 120 / Math.pow(ageHours + 2, 0.35);
  const approvedCommunityBoost = clean(pack.source) === 'user_submission' || clean(pack.publisher_user_id) ? 18 : 0;
  return Math.round((downloads * 5 + favorites * 4 + likes * 6 + comments * 8 + views * 0.35 + freshness + approvedCommunityBoost) * 100) / 100;
}

async function findPackRecord(packId, preferredCollection = '') {
  const collections = [preferredCollection, 'stickers', 'premium_stickers'].filter(Boolean);
  const seen = new Set();
  for (const collection of collections) {
    if (seen.has(collection)) continue;
    seen.add(collection);
    const resp = await pbFetch(`/api/collections/${collection}/records/${packId}`);
    if (resp.ok) return { collection, record: await resp.json() };
  }
  return { collection: preferredCollection || 'stickers', record: null };
}

async function updatePackCounters(packId, preferredCollection = '') {
  const [{ collection, record }, likes, comments] = await Promise.all([
    findPackRecord(packId, preferredCollection),
    safeFetchAll('pack_likes'),
    safeFetchAll('pack_comments'),
  ]);
  const likeCount = likes.filter((item) => clean(item.pack_id) === packId).length;
  const commentCount = comments.filter((item) => clean(item.pack_id) === packId).length;
  const engagementScore = calculateEngagementScore({ ...(record || {}), like_count: likeCount, comment_count: commentCount });
  if (record) {
    const resp = await pbFetch(`/api/collections/${collection}/records/${record.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ like_count: likeCount, comment_count: commentCount, engagement_score: engagementScore })
    });
    if (!resp.ok) console.warn('[Social counters] pack update failed:', await resp.text());
  }
  return { like_count: likeCount, comment_count: commentCount, engagement_score: engagementScore };
}

function viewerMatches(record, viewerId, viewerEmail) {
  return (viewerId && lower(record.user_id) === lower(viewerId)) || (viewerEmail && lower(record.user_email) === lower(viewerEmail));
}

async function commentsWithState(packId, viewerId = '', viewerEmail = '') {
  const [comments, commentLikes, replies] = await Promise.all([
    safeFetchAll('pack_comments'),
    safeFetchAll('comment_likes'),
    safeFetchAll('comment_replies'),
  ]);
  return comments
    .filter((comment) => clean(comment.pack_id) === packId)
    .sort((a, b) => Date.parse(b.created || b.created_at || 0) - Date.parse(a.created || a.created_at || 0))
    .map((comment) => {
      const likes = commentLikes.filter((like) => clean(like.comment_id) === clean(comment.id));
      return {
        ...comment,
        like_count: Number(comment.like_count || likes.length || 0),
        liked: likes.some((like) => viewerMatches(like, viewerId, viewerEmail)),
        replies: replies
          .filter((reply) => clean(reply.comment_id) === clean(comment.id))
          .sort((a, b) => Date.parse(a.created || a.created_at || 0) - Date.parse(b.created || b.created_at || 0))
          .map((reply) => {
            const replyLikes = commentLikes.filter((like) => clean(like.comment_id) === clean(reply.id));
            return {
              ...reply,
              like_count: replyLikes.length,
              liked: replyLikes.some((like) => viewerMatches(like, viewerId, viewerEmail)),
            };
          }),
      };
    });
}

async function loadSocialState() {
  if (socialStateCache && Date.now() < socialStateExpiresAt) {
    return socialStateCache;
  }
  if (socialStatePromise) {
    return socialStatePromise;
  }
  socialStatePromise = Promise.all([
    safeFetchAll('user_profiles'),
    safeFetchAll('user_follows'),
    safeFetchAll('pack_likes'),
    safeFetchAll('pack_comments'),
    safeFetchAll('stickers'),
    safeFetchAll('premium_stickers'),
  ]).then(([profiles, follows, likes, comments, stickerPacks, premiumPacks]) => {
    socialStateCache = { profiles, follows, likes, comments, stickerPacks, premiumPacks };
    socialStateExpiresAt = Date.now() + SOCIAL_CACHE_TTL;
    socialStatePromise = null;
    return socialStateCache;
  }).catch((err) => {
    socialStatePromise = null;
    throw err;
  });
  return socialStatePromise;
}

function unique(values) {
  return [...new Set(values.map(clean).filter(Boolean))];
}

function sameIdentity(a = '', b = '') {
  return lower(a) && lower(a) === lower(b);
}

function identitySet(values = []) {
  return new Set(values.map(lower).filter(Boolean));
}

function intersects(a, b) {
  for (const value of a) if (b.has(value)) return true;
  return false;
}

async function expandIdentityKeys(values = []) {
  const keys = identitySet(values);
  if (keys.size === 0) return keys;
  const profiles = await getAllProfiles();
  profiles
    .filter((profile) => userMatches(profile, keys))
    .forEach((profile) => identityValues(profile).forEach((value) => keys.add(value)));
  return keys;
}

async function packOwnerIdentityKeys(pack = {}) {
  const ownerKeys = await expandIdentityKeys([
    pack.publisher_user_id,
    pack.publisher_email,
    pack.email,
    pack.publisher,
    pack.publisher_name,
    pack.user_id,
    pack.user_email,
    pack.owner_id,
    pack.created_by,
    pack.device_id,
  ]);
  const submissions = await safeFetchAll('user_submissions');
  submissions
    .filter((submission) => [submission.sticker_pack_id, submission.source_pack_id, submission.pack_id].map(clean).includes(clean(pack.id)))
    .forEach((submission) => identityValues(submission).forEach((value) => ownerKeys.add(value)));
  return ownerKeys;
}

async function createNotification(userId, payload) {
  if (!clean(userId)) return;
  const resp = await pbFetch('/api/collections/notifications/records', {
    method: 'POST',
    body: JSON.stringify({
      title: payload.title || 'Sticky',
      body: payload.body || '',
      message: payload.body || '',
      user_id: clean(userId),
      from: payload.from || payload.actor_name || 'Sticky',
      read: false,
      sent: false,
      timestamp: new Date().toISOString(),
      topic: payload.topic || 'general',
      ...(payload.pack_id ? { pack_id: payload.pack_id } : {}),
      ...(payload.image_url ? { image_url: payload.image_url, imageUrl: payload.image_url } : {}),
      data: payload.data || {},
    }),
  });
  if (!resp.ok) console.warn('[Social notification] create failed:', await resp.text());
}

async function notifyMany(recipients, payload, actor = {}) {
  const actorValues = await expandIdentityKeys([actor.id, actor.email, actor.name, actor.device_id]);
  const filtered = unique(recipients).filter((recipient) => !actorValues.has(lower(recipient)));
  await Promise.all(filtered.map((recipient) => createNotification(recipient, payload).catch((err) => console.warn('[Social notification]', err.message))));
}

async function hasFollowNotification(followerId, followerEmail, targetKeys = []) {
  const notifications = await safeFetchAll('notifications');
  const targets = identitySet(targetKeys);
  return notifications.some((notification) => {
    if (clean(notification.topic) !== 'social_follow') return false;
    if (!targets.has(lower(notification.user_id))) return false;
    const data = typeof notification.data === 'object' && notification.data ? notification.data : {};
    return sameIdentity(data.actor_id, followerId) || sameIdentity(data.actor_email, followerEmail);
  });
}

async function profileRecipientKeys(targetId, targetEmail) {
  const identifiers = new Set([targetId, targetEmail].map(lower).filter(Boolean));
  const profiles = await getAllProfiles();
  const profile = profiles.find((item) => userMatches(item, identifiers));
  return unique([targetId, targetEmail, profile?.user_id, profile?.uid, profile?.email, profile?.device_id]);
}

async function packRecipientKeys(pack) {
  const identifiers = new Set([pack.publisher_user_id, pack.publisher_email, pack.email, pack.publisher].map(lower).filter(Boolean));
  const profiles = await getAllProfiles();
  const profile = profiles.find((item) => userMatches(item, identifiers));
  return unique([pack.publisher_user_id, pack.publisher_email, pack.email, pack.user_id, pack.user_email, pack.device_id, profile?.user_id, profile?.uid, profile?.email, profile?.device_id]);
}

async function deleteRecordsWhere(collection, predicate) {
  const records = await safeFetchAll(collection);
  let deleted = 0;
  for (const record of records.filter(predicate)) {
    const resp = await pbFetch(`/api/collections/${collection}/records/${record.id}`, { method: 'DELETE' });
    if (resp.ok || resp.status === 204) deleted++;
    else console.warn(`[Social delete] ${collection}/${record.id} failed:`, await resp.text());
  }
  return deleted;
}

router.get('/profile', async (req, res) => {
  try {
    const publisherId = clean(req.query.publisherId);
    const publisherEmail = clean(req.query.email || req.query.publisherEmail);
    const publisherName = clean(req.query.publisherName);
    const publisherPhoto = clean(req.query.publisherPhoto);
    const viewerId = clean(req.query.viewerId);
    const viewerEmail = clean(req.query.viewerEmail);
    const lang = clean(req.query.lang);

    const identifiers = new Set([publisherId, publisherEmail, publisherName].map(lower).filter(Boolean));
    const viewerIdentifiers = new Set([viewerId, viewerEmail].map(lower).filter(Boolean));
    const state = await loadSocialState();

    const matchedProfile = state.profiles.find((profile) => userMatches(profile, identifiers));
    const profile = normalizeProfile(matchedProfile, {
      id: publisherId,
      user_id: publisherId,
      email: publisherEmail,
      display_name: publisherName,
      photo_url: publisherPhoto,
    });

    [profile.id, profile.user_id, profile.email, profile.display_name].map(lower).filter(Boolean).forEach((value) => identifiers.add(value));

    const rawPacks = [
      ...state.stickerPacks.map((pack) => ({ pack, collection: 'stickers' })),
      ...state.premiumPacks.map((pack) => ({ pack, collection: 'premium_stickers' })),
    ].filter(({ pack }) => {
      if (pack.is_active === false) return false;
      return userMatches(pack, identifiers);
    });

    const packs = rawPacks
      .map(({ pack, collection }) => summarizePack(pack, collection, state.likes, state.comments, lang))
      .sort((a, b) => (b.download_count + b.favorite_count + b.like_count) - (a.download_count + a.favorite_count + a.like_count));

    const followers = state.follows.filter((follow) => {
      const values = [follow.target_id, follow.target_email, follow.following_id, follow.following_email].map(lower).filter(Boolean);
      return values.some((value) => identifiers.has(value));
    });
    const following = state.follows.filter((follow) => {
      const values = [follow.follower_id, follow.follower_email].map(lower).filter(Boolean);
      return values.some((value) => identifiers.has(value));
    });
    const isFollowing = followers.some((follow) => {
      const values = [follow.follower_id, follow.follower_email].map(lower).filter(Boolean);
      return values.some((value) => viewerIdentifiers.has(value));
    });

    const profileFor = (...values) => {
      const keys = identitySet(values);
      return state.profiles.find((item) => userMatches(item, keys));
    };

    res.json({
      profile,
      packs,
      stats: {
        packs: packs.length,
        downloads: packs.reduce((sum, pack) => sum + pack.download_count, 0),
        favorites: packs.reduce((sum, pack) => sum + pack.favorite_count, 0),
        likes: packs.reduce((sum, pack) => sum + pack.like_count, 0),
        comments: packs.reduce((sum, pack) => sum + pack.comment_count, 0),
        followers: followers.length,
        following: following.length,
      },
      followers: followers.map((follow) => {
        const followerProfile = profileFor(follow.follower_id, follow.follower_email, follow.follower_name);
        const photo = clean(follow.follower_photo) || profilePhoto(followerProfile);
        return { id: follow.follower_id, email: follow.follower_email, name: follow.follower_name || followerProfile?.display_name || followerProfile?.name, photo_url: photo, photo, avatar_url: photo };
      }),
      following: following.map((follow) => {
        const targetProfile = profileFor(follow.target_id || follow.following_id, follow.target_email || follow.following_email, follow.target_name || follow.following_name);
        const photo = clean(follow.target_photo || follow.following_photo) || profilePhoto(targetProfile);
        return { id: follow.target_id || follow.following_id, email: follow.target_email || follow.following_email, name: follow.target_name || follow.following_name || targetProfile?.display_name || targetProfile?.name, photo_url: photo, photo, avatar_url: photo };
      }),
      is_following: isFollowing,
    });
  } catch (err) {
    console.error('[Social profile]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/follow', async (req, res) => {
  try {
    const followerId = clean(req.body.follower_id);
    const followerEmail = clean(req.body.follower_email);
    const targetId = clean(req.body.target_id);
    const targetEmail = clean(req.body.target_email);
    if ((!followerId && !followerEmail) || (!targetId && !targetEmail)) {
      return res.status(400).json({ error: 'Missing follower or target.' });
    }
    const followerKeys = identitySet([followerId, followerEmail]);
    const targetKeys = identitySet([targetId, targetEmail]);
    if ([...followerKeys].some((value) => targetKeys.has(value))) {
      return res.json({ following: false, self: true });
    }
    const follows = await safeFetchAll('user_follows');
    const existing = follows.find((follow) =>
      (lower(follow.follower_id) === lower(followerId) || lower(follow.follower_email) === lower(followerEmail)) &&
      (lower(follow.target_id || follow.following_id) === lower(targetId) || lower(follow.target_email || follow.following_email) === lower(targetEmail))
    );
    if (existing) {
      const resp = await pbFetch(`/api/collections/user_follows/records/${existing.id}`, { method: 'DELETE' });
      if (!resp.ok && resp.status !== 204) throw new Error(await resp.text());
      return res.json({ following: false });
    }
    const payload = {
      follower_id: followerId,
      follower_email: followerEmail,
      follower_name: clean(req.body.follower_name),
      follower_photo: clean(req.body.follower_photo),
      target_id: targetId,
      target_email: targetEmail,
      target_name: clean(req.body.target_name),
      target_photo: clean(req.body.target_photo),
      created_at: new Date().toISOString(),
    };
    const resp = await pbFetch('/api/collections/user_follows/records', { method: 'POST', body: JSON.stringify(payload) });
    if (!resp.ok) throw new Error(await resp.text());
    const recipients = await profileRecipientKeys(targetId, targetEmail);
    const alreadyNotified = await hasFollowNotification(followerId, followerEmail, recipients);
    if (!alreadyNotified) {
      await notifyMany(recipients, {
        topic: 'social_follow',
        title: 'New follower',
        body: `${payload.follower_name || payload.follower_email || 'Someone'} started following you.`,
        image_url: payload.follower_photo,
        actor_name: payload.follower_name,
        data: {
          actor_id: followerId,
          actor_email: followerEmail,
          actor_name: payload.follower_name,
          actor_photo: payload.follower_photo,
        },
      }, { id: followerId, email: followerEmail, name: payload.follower_name });
    }
    res.json({ following: true });
  } catch (err) {
    console.error('[Social follow]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.get('/comments', async (req, res) => {
  try {
    const packId = clean(req.query.packId);
    const comments = await commentsWithState(packId, clean(req.query.viewerId), clean(req.query.viewerEmail));
    res.json({ comments });
  } catch (err) {
    console.error('[Social comments]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.get('/pack', async (req, res) => {
  try {
    const packId = clean(req.query.packId);
    const viewerId = clean(req.query.viewerId);
    const viewerEmail = clean(req.query.viewerEmail);
    if (!packId) return res.status(400).json({ error: 'Missing pack.' });
    const [likes, comments] = await Promise.all([safeFetchAll('pack_likes'), safeFetchAll('pack_comments')]);
    const liked = likes.some((like) => clean(like.pack_id) === packId && viewerMatches(like, viewerId, viewerEmail));
    const counters = await updatePackCounters(packId, clean(req.query.collection));
    res.json({ liked, ...counters, comments: comments.filter((comment) => clean(comment.pack_id) === packId).length });
  } catch (err) {
    console.error('[Social pack]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/comments', async (req, res) => {
  try {
    const body = clean(req.body.body);
    const packId = clean(req.body.pack_id);
    if (!packId || !body) return res.status(400).json({ error: 'Missing pack or comment.' });
    const payload = {
      pack_id: packId,
      collection: clean(req.body.collection || 'stickers'),
      user_id: clean(req.body.user_id),
      user_email: clean(req.body.user_email),
      display_name: clean(req.body.display_name),
      photo_url: clean(req.body.photo_url),
      body: body.slice(0, 500),
      like_count: 0,
      created_at: new Date().toISOString(),
    };
    const resp = await pbFetch('/api/collections/pack_comments/records', { method: 'POST', body: JSON.stringify(payload) });
    if (!resp.ok) throw new Error(await resp.text());
    const comment = await resp.json();
    const counters = await updatePackCounters(packId, payload.collection);
    res.json({ comment, ...counters });
  } catch (err) {
    console.error('[Social add comment]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/like', async (req, res) => {
  try {
    const packId = clean(req.body.pack_id);
    const userId = clean(req.body.user_id);
    const userEmail = clean(req.body.user_email);
    const actorDeviceId = clean(req.body.device_id);
    if (!packId || (!userId && !userEmail)) return res.status(400).json({ error: 'Missing pack or user.' });
    const likes = await safeFetchAll('pack_likes');
    const existing = likes.find((like) => clean(like.pack_id) === packId &&
      (lower(like.user_id) === lower(userId) || lower(like.user_email) === lower(userEmail))
    );
    if (existing) {
      const resp = await pbFetch(`/api/collections/pack_likes/records/${existing.id}`, { method: 'DELETE' });
      if (!resp.ok && resp.status !== 204) throw new Error(await resp.text());
      const counters = await updatePackCounters(packId, clean(existing.collection || req.body.collection || 'stickers'));
      return res.json({ liked: false, ...counters });
    }
    const payload = {
      pack_id: packId,
      collection: clean(req.body.collection || 'stickers'),
      user_id: userId,
      user_email: userEmail,
      display_name: clean(req.body.display_name),
      created_at: new Date().toISOString(),
    };
    const resp = await pbFetch('/api/collections/pack_likes/records', { method: 'POST', body: JSON.stringify(payload) });
    if (!resp.ok) throw new Error(await resp.text());
    const counters = await updatePackCounters(packId, payload.collection);
    const { record } = await findPackRecord(packId, payload.collection);
    if (record) {
      const actorKeys = await expandIdentityKeys([userId, userEmail, payload.display_name, actorDeviceId]);
      const ownerKeys = await packOwnerIdentityKeys(record);
      const isSelfLike = intersects(actorKeys, ownerKeys);
      if (!isSelfLike) {
        const recipients = await packRecipientKeys(record);
        await notifyMany(recipients, {
          topic: 'pack_like',
          title: 'New pack like',
          body: `${payload.display_name || payload.user_email || 'Someone'} liked your "${packName(record)}" pack.`,
          pack_id: packId,
          image_url: clean(record.publisher_photo_url),
          actor_name: payload.display_name,
          data: {
            actor_id: userId,
            actor_email: userEmail,
            actor_name: payload.display_name,
            actor_photo: clean(req.body.photo_url),
            pack_id: packId,
            pack_name: packName(record),
          },
        }, { id: userId, email: userEmail, name: payload.display_name, device_id: actorDeviceId });
      } else {
        return res.json({ liked: true, self_like: true, ...counters });
      }
    }
    res.json({ liked: true, ...counters });
  } catch (err) {
    console.error('[Social like]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/pack/delete', async (req, res) => {
  try {
    const packId = clean(req.body.pack_id);
    const submissionId = clean(req.body.submission_id);
    const userId = clean(req.body.user_id);
    const userEmail = clean(req.body.user_email);
    if (!packId && !submissionId) return res.status(400).json({ error: 'Missing pack or submission.' });

    let targetPackId = packId;
    let packRecord = null;
    let packCollection = 'stickers';
    if (targetPackId) {
      const found = await findPackRecord(targetPackId, clean(req.body.collection || 'stickers'));
      packRecord = found.record;
      packCollection = found.collection;
    }

    if (!targetPackId && submissionId) {
      const submissionResp = await pbFetch(`/api/collections/user_submissions/records/${submissionId}`);
      if (submissionResp.ok) {
        const submission = await submissionResp.json();
        targetPackId = clean(submission.sticker_pack_id);
      }
    }

    const actorIdentifiers = new Set([userId, userEmail].map(lower).filter(Boolean));
    const isAdmin = req.body.admin === true || req.body.admin === 'true';
    if (packRecord && actorIdentifiers.size && !userMatches(packRecord, actorIdentifiers) && !isAdmin) {
      return res.status(403).json({ error: 'You can only delete your own pack.' });
    }

    const comments = targetPackId ? await safeFetchAll('pack_comments') : [];
    const commentIds = new Set(comments.filter((comment) => clean(comment.pack_id) === targetPackId).map((comment) => clean(comment.id)));
    const deleted = {};
    if (targetPackId) {
      deleted.comment_likes = await deleteRecordsWhere('comment_likes', (item) => clean(item.pack_id) === targetPackId || commentIds.has(clean(item.comment_id)));
      deleted.comment_replies = await deleteRecordsWhere('comment_replies', (item) => clean(item.pack_id) === targetPackId || commentIds.has(clean(item.comment_id)));
      deleted.pack_comments = await deleteRecordsWhere('pack_comments', (item) => clean(item.pack_id) === targetPackId);
      deleted.pack_likes = await deleteRecordsWhere('pack_likes', (item) => clean(item.pack_id) === targetPackId);
      deleted.notifications = await deleteRecordsWhere('notifications', (item) => clean(item.pack_id) === targetPackId);
      const packResp = await pbFetch(`/api/collections/${packCollection}/records/${targetPackId}`, { method: 'DELETE' });
      deleted.pack = packResp.ok || packResp.status === 204 ? 1 : 0;
    }
    if (submissionId) {
      const submissionResp = await pbFetch(`/api/collections/user_submissions/records/${submissionId}`, { method: 'DELETE' });
      deleted.submission = submissionResp.ok || submissionResp.status === 204 ? 1 : 0;
    } else if (targetPackId) {
      deleted.submissions = await deleteRecordsWhere('user_submissions', (item) => clean(item.sticker_pack_id) === targetPackId);
    }

    res.json({ success: true, deleted });
  } catch (err) {
    console.error('[Social pack delete]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/comments/like', async (req, res) => {
  try {
    const commentId = clean(req.body.comment_id);
    const packId = clean(req.body.pack_id);
    const userId = clean(req.body.user_id);
    const userEmail = clean(req.body.user_email);
    if (!commentId || (!userId && !userEmail)) return res.status(400).json({ error: 'Missing comment or user.' });
    const likes = await safeFetchAll('comment_likes');
    const existing = likes.find((like) => clean(like.comment_id) === commentId && viewerMatches(like, userId, userEmail));
    let liked = true;
    if (existing) {
      const resp = await pbFetch(`/api/collections/comment_likes/records/${existing.id}`, { method: 'DELETE' });
      if (!resp.ok && resp.status !== 204) throw new Error(await resp.text());
      liked = false;
    } else {
      const resp = await pbFetch('/api/collections/comment_likes/records', {
        method: 'POST',
        body: JSON.stringify({ comment_id: commentId, pack_id: packId, user_id: userId, user_email: userEmail, display_name: clean(req.body.display_name), created_at: new Date().toISOString() })
      });
      if (!resp.ok) throw new Error(await resp.text());
    }
    const nextLikes = await safeFetchAll('comment_likes');
    const likeCount = nextLikes.filter((like) => clean(like.comment_id) === commentId).length;
    const resp = await pbFetch(`/api/collections/pack_comments/records/${commentId}`, { method: 'PATCH', body: JSON.stringify({ like_count: likeCount }) });
    if (!resp.ok) console.warn('[Social comment like] count update failed:', await resp.text());
    res.json({ liked, like_count: likeCount });
  } catch (err) {
    console.error('[Social comment like]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/comments/reply', async (req, res) => {
  try {
    const commentId = clean(req.body.comment_id);
    const packId = clean(req.body.pack_id);
    const body = clean(req.body.body);
    if (!commentId || !packId || !body) return res.status(400).json({ error: 'Missing comment, pack or reply.' });
    const payload = {
      comment_id: commentId,
      pack_id: packId,
      user_id: clean(req.body.user_id),
      user_email: clean(req.body.user_email),
      display_name: clean(req.body.display_name),
      photo_url: clean(req.body.photo_url),
      body: body.slice(0, 500),
      created_at: new Date().toISOString(),
    };
    const resp = await pbFetch('/api/collections/comment_replies/records', { method: 'POST', body: JSON.stringify(payload) });
    if (!resp.ok) throw new Error(await resp.text());
    await updatePackCounters(packId, clean(req.body.collection || 'stickers'));
    res.json({ reply: await resp.json() });
  } catch (err) {
    console.error('[Social comment reply]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.post('/comments/reply/like', async (req, res) => {
  try {
    const replyId = clean(req.body.reply_id);
    const packId = clean(req.body.pack_id);
    const userId = clean(req.body.user_id);
    const userEmail = clean(req.body.user_email);
    if (!replyId || (!userId && !userEmail)) return res.status(400).json({ error: 'Missing reply or user.' });
    const likes = await safeFetchAll('comment_likes');
    const existing = likes.find((like) => clean(like.comment_id) === replyId && viewerMatches(like, userId, userEmail));
    let liked = true;
    if (existing) {
      const resp = await pbFetch(`/api/collections/comment_likes/records/${existing.id}`, { method: 'DELETE' });
      if (!resp.ok && resp.status !== 204) throw new Error(await resp.text());
      liked = false;
    } else {
      const resp = await pbFetch('/api/collections/comment_likes/records', {
        method: 'POST',
        body: JSON.stringify({ comment_id: replyId, pack_id: packId, user_id: userId, user_email: userEmail, display_name: clean(req.body.display_name), created_at: new Date().toISOString() })
      });
      if (!resp.ok) throw new Error(await resp.text());
    }
    const nextLikes = await safeFetchAll('comment_likes');
    const likeCount = nextLikes.filter((like) => clean(like.comment_id) === replyId).length;
    res.json({ liked, like_count: likeCount });
  } catch (err) {
    console.error('[Social reply like]', err.message);
    res.status(500).json({ error: err.message });
  }
});

router.patch('/profile', async (req, res) => {
  try {
    const id = clean(req.body.id);
    const email = clean(req.body.email);
    const userId = clean(req.body.user_id);
    const profiles = await safeFetchAll('user_profiles');
    const profile = profiles.find((item) => clean(item.id) === id || lower(item.email) === lower(email) || lower(item.user_id) === lower(userId) || lower(item.uid) === lower(userId));
    const update = {
      display_name: clean(req.body.display_name),
      name: clean(req.body.display_name),
      bio: clean(req.body.bio).slice(0, 280),
      show_email: req.body.show_email !== false,
      photo_url: clean(req.body.photo_url),
      email,
      user_id: userId,
      last_sync: new Date().toISOString(),
    };
    Object.keys(update).forEach((key) => update[key] === '' && delete update[key]);
    let resp;
    if (profile) {
      resp = await pbFetch(`/api/collections/user_profiles/records/${profile.id}`, { method: 'PATCH', body: JSON.stringify(update) });
    } else {
      resp = await pbFetch('/api/collections/user_profiles/records', { method: 'POST', body: JSON.stringify({ ...update, created_at: new Date().toISOString() }) });
    }
    if (!resp.ok) throw new Error(await resp.text());
    res.json({ profile: await resp.json() });
  } catch (err) {
    console.error('[Social profile update]', err.message);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;