const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const NTFY_URL = process.env.NTFY_URL || 'http://ntfy:80';
const NTFY_TOPIC = process.env.NTFY_TOPIC || 'sticky-stickers';
const FCM_BROADCAST_TOPIC = 'stickers';

async function sendFcm(topic, title, body, imageUrl, data = {}) {
  const admin = require('firebase-admin');
  const message = {
    topic,
    notification: { title, body },
    android: {
      notification: {
        channelId: 'sticker_updates',
        ...(imageUrl ? { imageUrl } : {}),
      },
    },
    data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
    ...(imageUrl ? { apns: { fcmOptions: { imageUrl } } } : {}),
  };
  return admin.messaging().send(message);
}

async function sendNtfy(topic, title, body, imageUrl) {
  const headers = { 'Title': title, 'Priority': '4', 'Tags': 'sticker' };
  if (imageUrl) headers['Attach'] = imageUrl;
  const resp = await fetch(`${NTFY_URL}/${topic}`, { method: 'POST', headers, body });
  if (!resp.ok) throw new Error(`ntfy ${resp.status}`);
  return { success: true };
}

// POST /notify — broadcast to all users or specific device
router.post('/', async (req, res) => {
  try {
    const { title = 'Sticky', body, imageUrl, topic, deviceId, data = {} } = req.body;
    if (!body) return res.status(400).json({ error: 'body required' });

    const results = { fcm: null, ntfy: null };

    // FCM: send to device-specific topic if deviceId provided, else broadcast
    const fcmTopic = deviceId ? `user_${deviceId.replace(/[^a-zA-Z0-9_-]/g, '_')}` : FCM_BROADCAST_TOPIC;
    try {
      const messageId = await sendFcm(fcmTopic, title, body, imageUrl, data);
      results.fcm = { success: true, messageId, topic: fcmTopic };
      console.log('[Notify FCM] sent to topic:', fcmTopic, messageId);
    } catch (fcmErr) {
      console.error('[Notify FCM]', fcmErr.message);
      results.fcm = { success: false, error: fcmErr.message };
    }

    // ntfy: send to device-specific topic or default
    try {
      const ntfyTopic = deviceId ? `sticky_${deviceId.replace(/[^a-zA-Z0-9_-]/g, '_')}` : (topic || NTFY_TOPIC);
      results.ntfy = await sendNtfy(ntfyTopic, title, body, imageUrl);
    } catch (ntfyErr) {
      results.ntfy = { success: false, error: ntfyErr.message };
    }

    res.json({ success: true, results });
  } catch (err) {
    console.error('[Notify]', err.message);
    res.status(500).json({ error: 'Notification error', message: err.message });
  }
});

module.exports = router;
