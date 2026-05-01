const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const KLIPY_API_KEY = process.env.KLIPY_API_KEY || 'YRtbKLSbrPqeEcPPpIpdoCqlLqZ9LlfbwQhXefbv3FQucQTjREFmkVLExf9JnI8k';

router.get('/', async (req, res) => {
  try {
    const { query, limit = 15, endpoint = 'search', type = 'stickers' } = req.query;
    if (!query && endpoint === 'search') {
      return res.status(400).json({ error: 'Query parameter is required' });
    }

    const contentType = type === 'gifs' ? 'gifs' : 'stickers';
    const base = `https://api.klipy.com/v2/${contentType}`;
    const url = endpoint === 'trending'
      ? `${base}/trending?limit=${limit}&api_key=${KLIPY_API_KEY}`
      : `${base}/search?q=${encodeURIComponent(query)}&limit=${limit}&api_key=${KLIPY_API_KEY}`;

    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`Klipy ${resp.status}`);
    res.json(await resp.json());
  } catch (err) {
    console.error('[Klipy]', err.message);
    res.status(500).json({ error: 'Klipy proxy error', message: err.message });
  }
});

module.exports = router;
