const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

// Accept both GET (legacy) and POST (secure — token in body)
router.all('/', async (req, res) => {
  try {
    // Token: POST body > GET query
    const token = req.body?.token || req.query.token;
    if (!token) return res.status(400).json({ error: 'Bot token is required' });

    const method = req.body?.method || req.query.method;
    const filePath = req.body?.file_path || req.query.file_path;
    const params = req.body?.params || {};
    if (req.query && req.method === 'GET') {
      // Merge GET query params (excluding reserved keys)
      for (const [k, v] of Object.entries(req.query)) {
        if (!['token', 'method', 'file_path'].includes(k)) params[k] = v;
      }
    }

    let url;
    if (filePath) {
      url = `https://api.telegram.org/file/bot${token}/${filePath}`;
    } else if (method) {
      const qs = Object.entries(params)
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join('&');
      url = `https://api.telegram.org/bot${token}/${method}${qs ? '?' + qs : ''}`;
    } else {
      return res.status(400).json({ error: 'method or file_path required' });
    }

    const resp = await fetch(url);
    if (!resp.ok) return res.status(resp.status).send(await resp.text());

    const ct = resp.headers.get('content-type') || '';
    if (filePath || !ct.includes('application/json')) {
      const buf = await resp.arrayBuffer();
      res.set('Content-Type', ct || 'application/octet-stream');
      res.send(Buffer.from(buf));
    } else {
      res.json(await resp.json());
    }
  } catch (err) {
    console.error('[Telegram]', err.message);
    res.status(500).json({ error: 'Telegram proxy error', message: err.message });
  }
});

module.exports = router;
