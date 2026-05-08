const express = require('express');
const admin = require('firebase-admin');
const { setupPocketBaseHooks } = require('./hooks');

// Firebase Admin initialization
// Priority: FIREBASE_SERVICE_ACCOUNT env var → firebase-sa-key.json file
try {
  let serviceAccount = null;
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
  } else {
    const fs = require('fs');
    const keyPath = require('path').join(__dirname, 'firebase-sa-key.json');
    if (fs.existsSync(keyPath)) {
      serviceAccount = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
    }
  }
  if (serviceAccount) {
    if (admin.apps.length === 0) {
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    }
    console.log('[Firebase] initialized for project:', serviceAccount.project_id);
  } else {
    console.warn('[Firebase] No credentials found — FCM via Admin SDK disabled');
  }
} catch (e) {
  console.error('[Firebase] init failed:', e.message);
}
const giphyRouter = require('./routes/giphy');
const klipyRouter = require('./routes/klipy');
const telegramRouter = require('./routes/telegram');
const translateRouter = require('./routes/translate');
const nsfwRouter = require('./routes/nsfw');
const notifyRouter = require('./routes/notify');
const notificationsRouter = require('./routes/notifications');
const usersRouter = require('./routes/users');
const statsRouter = require('./routes/stats');
const socialRouter = require('./routes/social');
const aiRouter = require('./routes/ai');

const app = express();
app.use(express.json());

// CORS — restrict to known origins
const ALLOWED_ORIGINS = [
  'https://sticky-admin.46.225.95.201.sslip.io',
  'http://localhost:5173',
  'http://localhost:4173',
];
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGINS.some(o => origin.startsWith(o))) {
    res.set('Access-Control-Allow-Origin', origin);
  }
  res.set('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') return res.status(204).end();
  next();
});

// Rate limiter
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW = 60_000;
const RATE_LIMIT_MAX = 30;
const TELEGRAM_RATE_LIMIT_MAX = 900;

function createRateLimit(maxRequests, bucket = 'default') {
  return function rateLimit(req, res, next) {
  const ip = req.ip || req.headers['x-forwarded-for'] || 'unknown';
  const key = `${bucket}:${ip}`;
  const now = Date.now();
  const entry = rateLimitMap.get(key);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(key, { count: 1, resetAt: now + RATE_LIMIT_WINDOW });
    return next();
  }
  entry.count++;
  if (entry.count > maxRequests) {
    return res.status(429).json({ error: 'Too many requests' });
  }
  next();
  };
}

const rateLimit = createRateLimit(RATE_LIMIT_MAX);
const telegramRateLimit = createRateLimit(TELEGRAM_RATE_LIMIT_MAX, 'telegram');

// Simple admin auth middleware — validates PocketBase user or superuser token
async function adminAuth(req, res, next) {
  const authHeader = req.headers.authorization || '';
  const token = authHeader.replace(/^Bearer\s+/i, '').trim();
  if (!token) {
    return res.status(401).json({ error: 'Missing authorization token' });
  }
  try {
    const pbUrl = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
    const headers = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };
    // Try superusers first (admin panel tokens), then regular users
    const endpoints = [
      `${pbUrl}/api/collections/_superusers/auth-refresh`,
      `${pbUrl}/api/collections/users/auth-refresh`,
    ];
    let validated = false;
    for (const url of endpoints) {
      const resp = await fetch(url, { method: 'POST', headers });
      if (resp.ok) {
        const data = await resp.json();
        req.adminUser = data.record || data;
        validated = true;
        break;
      }
    }
    if (!validated) throw new Error('Token invalid');
    next();
  } catch {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

setInterval(() => {
  const now = Date.now();
  for (const [k, v] of rateLimitMap) {
    if (now > v.resetAt + RATE_LIMIT_WINDOW) rateLimitMap.delete(k);
  }
}, 5 * 60_000);

app.use('/api/giphy', rateLimit, giphyRouter);
app.use('/api/klipy', rateLimit, klipyRouter);
app.use('/api/telegram', telegramRateLimit, telegramRouter);
app.use('/api/translate', rateLimit, translateRouter);
app.use('/api/nsfw', rateLimit, nsfwRouter);
app.use('/api/notify', rateLimit, adminAuth, notifyRouter);
app.use('/api/notifications', rateLimit, adminAuth, notificationsRouter);
app.use('/api/users', rateLimit, adminAuth, usersRouter);
app.use('/api/stats', rateLimit, statsRouter);
app.use('/api/social', rateLimit, socialRouter);
app.use('/api/ai', rateLimit, adminAuth, aiRouter);

app.get('/health', (_, res) => res.json({ status: 'ok', ts: Date.now() }));

const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Worker] listening on :${PORT}`);
  setupPocketBaseHooks().catch(err => console.error('[Hooks] init error:', err));
});
