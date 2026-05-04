#!/usr/bin/env node

const PB = process.argv[2] || process.env.PB_URL || 'http://localhost:8090';
const EMAIL = process.argv[3] || process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const PASS = process.argv[4] || process.env.PB_ADMIN_PASSWORD || process.env.PB_ADMIN_PASS;

if (!PASS) {
  console.error('PB_ADMIN_PASSWORD is required');
  process.exit(1);
}

async function api(method, path, data, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = token;
  const res = await fetch(`${PB}${path}`, { method, headers, body: data ? JSON.stringify(data) : undefined });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 400)}`);
  return text ? JSON.parse(text) : {};
}

const publicRules = { listRule: '', viewRule: '', createRule: '', updateRule: null, deleteRule: null };
const socialCollections = [
  {
    name: 'user_follows',
    fields: [
      { name: 'follower_id', type: 'text' },
      { name: 'follower_email', type: 'email' },
      { name: 'follower_name', type: 'text' },
      { name: 'follower_photo', type: 'url' },
      { name: 'target_id', type: 'text' },
      { name: 'target_email', type: 'email' },
      { name: 'target_name', type: 'text' },
      { name: 'target_photo', type: 'url' },
      { name: 'created_at', type: 'date' },
    ],
    ...publicRules,
  },
  {
    name: 'pack_likes',
    fields: [
      { name: 'pack_id', type: 'text', required: true },
      { name: 'collection', type: 'text' },
      { name: 'user_id', type: 'text' },
      { name: 'user_email', type: 'email' },
      { name: 'display_name', type: 'text' },
      { name: 'created_at', type: 'date' },
    ],
    ...publicRules,
  },
  {
    name: 'pack_comments',
    fields: [
      { name: 'pack_id', type: 'text', required: true },
      { name: 'collection', type: 'text' },
      { name: 'user_id', type: 'text' },
      { name: 'user_email', type: 'email' },
      { name: 'display_name', type: 'text' },
      { name: 'photo_url', type: 'url' },
      { name: 'body', type: 'text', required: true },
      { name: 'like_count', type: 'number' },
      { name: 'created_at', type: 'date' },
    ],
    ...publicRules,
  },
  {
    name: 'comment_likes',
    fields: [
      { name: 'comment_id', type: 'text', required: true },
      { name: 'pack_id', type: 'text' },
      { name: 'user_id', type: 'text' },
      { name: 'user_email', type: 'email' },
      { name: 'display_name', type: 'text' },
      { name: 'created_at', type: 'date' },
    ],
    ...publicRules,
  },
  {
    name: 'comment_replies',
    fields: [
      { name: 'comment_id', type: 'text', required: true },
      { name: 'pack_id', type: 'text', required: true },
      { name: 'user_id', type: 'text' },
      { name: 'user_email', type: 'email' },
      { name: 'display_name', type: 'text' },
      { name: 'photo_url', type: 'url' },
      { name: 'body', type: 'text', required: true },
      { name: 'created_at', type: 'date' },
    ],
    ...publicRules,
  },
];

async function ensureCollection(token, def) {
  let collection = null;
  try {
    collection = await api('GET', `/api/collections/${def.name}`, null, token);
  } catch (_) {
    await api('POST', '/api/collections', { type: 'base', ...def }, token);
    console.log(`Created ${def.name}`);
    return;
  }
  const key = collection.fields ? 'fields' : 'schema';
  const current = collection[key] || [];
  const existing = new Set(current.map((field) => field.name));
  const missing = def.fields.filter((field) => !existing.has(field.name));
  if (missing.length) {
    await api('PATCH', `/api/collections/${def.name}`, { [key]: [...current, ...missing] }, token);
    console.log(`Patched ${def.name}: ${missing.map((field) => field.name).join(', ')}`);
  } else {
    console.log(`OK ${def.name}`);
  }
}

async function patchUserProfiles(token) {
  const collection = await api('GET', '/api/collections/user_profiles', null, token);
  const key = collection.fields ? 'fields' : 'schema';
  const current = collection[key] || [];
  const existing = new Set(current.map((field) => field.name));
  const wanted = [
    { name: 'bio', type: 'text' },
    { name: 'show_email', type: 'bool' },
  ].filter((field) => !existing.has(field.name));
  if (wanted.length) {
    await api('PATCH', '/api/collections/user_profiles', { [key]: [...current, ...wanted] }, token);
    console.log(`Patched user_profiles: ${wanted.map((field) => field.name).join(', ')}`);
  }
}

async function patchPackCollection(token, name) {
  const collection = await api('GET', `/api/collections/${name}`, null, token);
  const key = collection.fields ? 'fields' : 'schema';
  const current = collection[key] || [];
  const existing = new Set(current.map((field) => field.name));
  const wanted = [
    { name: 'like_count', type: 'number' },
    { name: 'comment_count', type: 'number' },
    { name: 'engagement_score', type: 'number' },
    { name: 'source', type: 'text' },
    { name: 'publisher_user_id', type: 'text' },
    { name: 'publisher_photo_url', type: 'url' },
  ].filter((field) => !existing.has(field.name));
  if (wanted.length) {
    await api('PATCH', `/api/collections/${name}`, { [key]: [...current, ...wanted] }, token);
    console.log(`Patched ${name}: ${wanted.map((field) => field.name).join(', ')}`);
  } else {
    console.log(`OK ${name}`);
  }
}

async function patchNotifications(token) {
  const collection = await api('GET', '/api/collections/notifications', null, token);
  const key = collection.fields ? 'fields' : 'schema';
  const current = collection[key] || [];
  const existing = new Set(current.map((field) => field.name));
  const wanted = [
    { name: 'topic', type: 'text' },
    { name: 'pack_id', type: 'text' },
    { name: 'image_url', type: 'url' },
    { name: 'imageUrl', type: 'url' },
    { name: 'data', type: 'json' },
  ].filter((field) => !existing.has(field.name));
  if (wanted.length) {
    await api('PATCH', '/api/collections/notifications', { [key]: [...current, ...wanted] }, token);
    console.log(`Patched notifications: ${wanted.map((field) => field.name).join(', ')}`);
  } else {
    console.log('OK notifications');
  }
}

const auth = await api('POST', '/api/collections/_superusers/auth-with-password', { identity: EMAIL, password: PASS });
for (const collection of socialCollections) await ensureCollection(auth.token, collection);
await patchUserProfiles(auth.token);
await patchPackCollection(auth.token, 'stickers');
await patchPackCollection(auth.token, 'premium_stickers');
await patchNotifications(auth.token);