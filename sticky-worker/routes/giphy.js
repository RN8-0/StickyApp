const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const GIPHY_API_KEY = process.env.GIPHY_API_KEY;

router.get('/', async (req, res) => {
  try {
    const { query, limit = 15, offset = 0, type = 'stickers', endpoint = 'search' } = req.query;
    if (!query && endpoint === 'search') {
      return res.status(400).json({ error: 'Query parameter is required' });
    }

    const base = `https://api.giphy.com/v1/${type}`;
    const url = endpoint === 'trending'
      ? `${base}/trending?api_key=${GIPHY_API_KEY}&limit=${limit}&offset=${offset}`
      : `${base}/search?api_key=${GIPHY_API_KEY}&q=${encodeURIComponent(query)}&limit=${limit}&offset=${offset}&rating=pg-13`;

    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`Giphy ${resp.status}`);
    res.json(await resp.json());
  } catch (err) {
    console.error('[Giphy]', err.message);
    res.status(500).json({ error: 'Giphy proxy error', message: err.message });
  }
});

module.exports = router;
