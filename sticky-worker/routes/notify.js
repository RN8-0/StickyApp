const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const NTFY_URL = process.env.NTFY_URL || 'http://ntfy:80';
const NTFY_TOPIC = process.env.NTFY_TOPIC || 'sticky-stickers';
const FCM_TOPIC = 'stickers';

router.post('/', async (req, res) => {
  try {
    const { title = 'Sticky', body, imageUrl, topic } = req.body;
    if (!body) return res.status(400).json({ error: 'body required' });

    const results = { fcm: null, ntfy: null };

    // Firebase FCM topic message
    try {
      const admin = require('firebase-admin');
      const message = {
        topic: FCM_TOPIC,
        notification: { title, body },
        android: {
          notification: {
            channelId: 'sticker_updates',
            ...(imageUrl ? { imageUrl } : {}),
          },
        },
        ...(imageUrl ? { apns: { fcmOptions: { imageUrl } } } : {}),
      };
      const messageId = await admin.messaging().send(message);
      results.fcm = { success: true, messageId };
      console.log('[Notify FCM] sent to topic:', FCM_TOPIC, messageId);
    } catch (fcmErr) {
      console.error('[Notify FCM]', fcmErr.message);
      results.fcm = { success: false, error: fcmErr.message };
    }

    // ntfy as secondary channel
    try {
      const targetTopic = topic || NTFY_TOPIC;
      const headers = { 'Title': title, 'Priority': '4', 'Tags': 'sticker' };
      if (imageUrl) headers['Attach'] = imageUrl;
      const resp = await fetch(`${NTFY_URL}/${targetTopic}`, { method: 'POST', headers, body });
      results.ntfy = resp.ok ? { success: true } : { success: false, error: `ntfy ${resp.status}` };
    } catch (ntfyErr) {
      results.ntfy = { success: false, error: ntfyErr.message };
    }

    if (!results.fcm?.success && !results.ntfy?.success) {
      return res.status(500).json({ error: 'All notification channels failed', results });
    }
    res.json({ success: true, results });
  } catch (err) {
    console.error('[Notify]', err.message);
    res.status(500).json({ error: 'Notification error', message: err.message });
  }
});

module.exports = router;
