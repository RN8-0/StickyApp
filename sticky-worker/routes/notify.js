const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const NTFY_URL = process.env.NTFY_URL || 'http://ntfy:80';
const NTFY_TOPIC = process.env.NTFY_TOPIC || 'sticky-stickers';

router.post('/', async (req, res) => {
  try {
    const { title = 'Sticky', body, imageUrl, topic } = req.body;
    if (!body) return res.status(400).json({ error: 'body required' });

    const targetTopic = topic || NTFY_TOPIC;
    const headers = { 'Title': title, 'Priority': '4', 'Tags': 'sticker' };
    if (imageUrl) headers['Attach'] = imageUrl;

    const resp = await fetch(`${NTFY_URL}/${targetTopic}`, {
      method: 'POST', headers, body
    });

    if (!resp.ok) throw new Error(`ntfy error ${resp.status}: ${await resp.text()}`);
    res.json({ success: true, topic: targetTopic });
  } catch (err) {
    console.error('[Notify]', err.message);
    res.status(500).json({ error: 'Notification error', message: err.message });
  }
});

module.exports = router;
