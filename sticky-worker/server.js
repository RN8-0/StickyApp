const express = require('express');
const { setupPocketBaseHooks } = require('./hooks');
const giphyRouter = require('./routes/giphy');
const klipyRouter = require('./routes/klipy');
const telegramRouter = require('./routes/telegram');
const translateRouter = require('./routes/translate');
const nsfwRouter = require('./routes/nsfw');
const notifyRouter = require('./routes/notify');

const app = express();
app.use(express.json());

// CORS
app.use((req, res, next) => {
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') return res.status(204).end();
  next();
});

// Rate limiter
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW = 60_000;
const RATE_LIMIT_MAX = 30;

function rateLimit(req, res, next) {
  const ip = req.ip || req.headers['x-forwarded-for'] || 'unknown';
  const now = Date.now();
  const entry = rateLimitMap.get(ip);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW });
    return next();
  }
  entry.count++;
  if (entry.count > RATE_LIMIT_MAX) {
    return res.status(429).json({ error: 'Too many requests' });
  }
  next();
}

setInterval(() => {
  const now = Date.now();
  for (const [k, v] of rateLimitMap) {
    if (now > v.resetAt + RATE_LIMIT_WINDOW) rateLimitMap.delete(k);
  }
}, 5 * 60_000);

app.use('/api/giphy', rateLimit, giphyRouter);
app.use('/api/klipy', rateLimit, klipyRouter);
app.use('/api/telegram', rateLimit, telegramRouter);
app.use('/api/translate', rateLimit, translateRouter);
app.use('/api/nsfw', rateLimit, nsfwRouter);
app.use('/api/notify', notifyRouter);

app.get('/health', (_, res) => res.json({ status: 'ok', ts: Date.now() }));

const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Worker] listening on :${PORT}`);
  setupPocketBaseHooks().catch(err => console.error('[Hooks] init error:', err));
});
