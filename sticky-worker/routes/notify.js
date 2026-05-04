const { Router } = require('express');
const fetch = require('node-fetch');
const router = Router();

const NTFY_URL = process.env.NTFY_URL || 'http://ntfy:80';
const NTFY_TOPIC = process.env.NTFY_TOPIC || 'sticky-stickers';
const FCM_BROADCAST_TOPIC = 'stickers';
const FCM_SERVER_KEY = process.env.FIREBASE_SERVER_KEY;

async function sendFcmViaAdminSdk(topic, title, body, imageUrl, data = {}) {
  const admin = require('firebase-admin');
  if (!admin.apps.length) throw new Error('Firebase Admin not initialized — set FIREBASE_SERVICE_ACCOUNT env var');
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

async function sendFcmViaLegacyKey(topic, title, body, imageUrl, data = {}) {
  const payload = {
    to: `/topics/${topic}`,
    notification: { title, body, ...(imageUrl ? { image: imageUrl } : {}) },
    data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
    android: { notification: { channel_id: 'sticker_updates' } },
  };
  const resp = await fetch('https://fcm.googleapis.com/fcm/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `key=${FCM_SERVER_KEY}` },
    body: JSON.stringify(payload),
  });
  const result = await resp.json();
  if (!resp.ok || result.failure) throw new Error(result.error || `FCM legacy error: ${JSON.stringify(result)}`);
  return result.message_id || 'sent';
}

async function sendFcm(topic, title, body, imageUrl, data = {}) {
  const admin = require('firebase-admin');
  if (admin.apps.length) return sendFcmViaAdminSdk(topic, title, body, imageUrl, data);
  if (FCM_SERVER_KEY) return sendFcmViaLegacyKey(topic, title, body, imageUrl, data);
  throw new Error('No FCM credentials configured. Set FIREBASE_SERVICE_ACCOUNT or FIREBASE_SERVER_KEY env var.');
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

    const fcmTopic = deviceId ? `user_${deviceId.replace(/[^a-zA-Z0-9_-]/g, '_')}` : FCM_BROADCAST_TOPIC;
    try {
      const messageId = await sendFcm(fcmTopic, title, body, imageUrl, data);
      results.fcm = { success: true, messageId, topic: fcmTopic };
      console.log('[Notify FCM] sent to topic:', fcmTopic, messageId);
    } catch (fcmErr) {
      console.error('[Notify FCM]', fcmErr.message);
      results.fcm = { success: false, error: fcmErr.message };
    }

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
