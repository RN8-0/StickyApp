const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

router.get('/', async (req, res) => {
  try {
    const { token, method, file_path } = req.query;
    if (!token) return res.status(400).json({ error: 'Bot token is required' });

    let url;
    if (file_path) {
      url = `https://api.telegram.org/file/bot${token}/${file_path}`;
    } else if (method) {
      const params = { ...req.query };
      delete params.token;
      delete params.method;
      const qs = Object.entries(params).map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
      url = `https://api.telegram.org/bot${token}/${method}${qs ? '?' + qs : ''}`;
    } else {
      return res.status(400).json({ error: 'method or file_path required' });
    }

    const resp = await fetch(url);
    if (!resp.ok) return res.status(resp.status).send(await resp.text());

    const ct = resp.headers.get('content-type') || '';
    if (file_path || !ct.includes('application/json')) {
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
