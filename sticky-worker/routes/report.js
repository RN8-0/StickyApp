const express = require('express');
const router = express.Router();
const NTFY_URL = process.env.NTFY_URL || 'http://ntfy:80';
const NTFY_TOPIC = process.env.NTFY_TOPIC || 'sticky-stickers';
const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const PB_ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'arainunger@gmail.com';
const PB_ADMIN_PASS = process.env.PB_ADMIN_PASS;

let authToken = '';
let authExpiry = 0;

async function pbAuth() {
  if (authToken && Date.now() < authExpiry) return authToken;
  const resp = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_ADMIN_EMAIL, password: PB_ADMIN_PASS })
  });
  if (!resp.ok) throw new Error(`PB auth failed: ${resp.status}`);
  const data = await resp.json();
  authToken = data.token;
  authExpiry = Date.now() + 600_000; // 10 min cache
  return authToken;
}

/**
 * POST /api/report — Accept content reports from the Android app
 */
router.post('/', async (req, res) => {
  try {
    const { reason, prompt, style, pack_id, pack_name, pack_image, timestamp } = req.body;
    const timeStr = timestamp ? new Date(timestamp).toISOString() : new Date().toISOString();
    const subject = pack_name ? `[Report] ${pack_name}` : (prompt ? '[Report] AI Generated Content' : '[Report] Content Flagged');
    const bodyText = pack_name
      ? `Pack: ${pack_name} (${pack_id || 'N/A'})\nReason: ${reason}\nImage: ${pack_image || 'N/A'}\nTime: ${timeStr}`
      : `Reason: ${reason}\nPrompt: ${(prompt || '').substring(0, 120)}\nStyle: ${style || 'N/A'}\nTime: ${timeStr}`;

    console.log('[Report]', bodyText);

    // Store in PocketBase suggestions collection → admin panel Messages → Suggestions
    if (PB_ADMIN_PASS) {
      try {
        const token = await pbAuth();
        await fetch(`${PB_URL}/api/collections/suggestions/records`, {
          method: 'POST',
          headers: { 'Authorization': token, 'Content-Type': 'application/json' },
          body: JSON.stringify({
            subject,
            message: bodyText,
            suggestion: bodyText,
            text: bodyText,
            category: 'ai_report',
            status: 'new'
          })
        });
        console.log('[Report] stored in suggestions');
      } catch (dbErr) {
        console.error('[Report] DB failed:', dbErr.message);
      }
    } else {
      console.warn('[Report] PB_ADMIN_PASS not set - skipping DB storage');
    }

    // ntfy push notification
    try {
      await fetch(`${NTFY_URL}/${NTFY_TOPIC}`, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: `🚩 ${subject}\n${bodyText}`
      });
    } catch (_) {}

    res.json({ success: true });
  } catch (e) {
    console.error('[Report] error:', e.message);
    res.status(500).json({ error: 'Failed to process report' });
  }
});

module.exports = router;
