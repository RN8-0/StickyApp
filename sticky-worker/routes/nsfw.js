const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const NUDENET_URL = process.env.NUDENET_URL || 'http://nudenet:8080';

router.post('/', async (req, res) => {
  try {
    const { image_url } = req.body;
    if (!image_url) return res.status(400).json({ error: 'image_url required' });

    const imgResp = await fetch(image_url);
    if (!imgResp.ok) return res.json({ safe: null, reason: 'image_download_failed' });
    const imgBuffer = await imgResp.buffer();

    const boundary = '----NudeNetBoundary' + Date.now();
    const body = Buffer.concat([
      Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="image.webp"\r\nContent-Type: image/webp\r\n\r\n`),
      imgBuffer,
      Buffer.from(`\r\n--${boundary}--\r\n`)
    ]);

    const nudeResp = await fetch(`${NUDENET_URL}/classify`, {
      method: 'POST',
      headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
      body
    });

    if (!nudeResp.ok) return res.json({ safe: null, reason: 'nudenet_error' });
    const result = await nudeResp.json();

    const unsafeLabels = [
      'FEMALE_BREAST_EXPOSED', 'FEMALE_GENITALIA_EXPOSED',
      'MALE_GENITALIA_EXPOSED', 'BUTTOCKS_EXPOSED',
      'ANUS_EXPOSED', 'BELLY_EXPOSED'
    ];
    const flagged = [];
    if (result.prediction) {
      for (const pred of result.prediction) {
        if (unsafeLabels.includes(pred.class) && pred.score > 0.6) flagged.push(pred.class);
      }
    }

    res.json({ safe: flagged.length === 0, reason: flagged.join(', '), details: result });
  } catch (err) {
    console.error('[NSFW]', err.message);
    res.json({ safe: null, reason: 'check_error: ' + err.message });
  }
});

module.exports = router;
