/**
 * Firebase Cloud Functions for StickyApp
 * Deploy with: firebase deploy --only functions
 */
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');

admin.initializeApp();

// ====================== CALLABLE FUNCTIONS ======================

/**
 * Kullanıcı profili güncelleme - PocketBase'den gelen kullanıcı bilgilerini Firestore'a yazar
 */
exports.updateUserProfile = onCall(async (request) => {
  const { uid, displayName, photoUrl, email } = request.data;
  if (!uid) throw new HttpsError('invalid-argument', 'uid is required');

  try {
    await admin.auth().updateUser(uid, {
      ...(displayName && { displayName }),
      ...(photoUrl && { photoURL: photoUrl }),
      ...(email && { email }),
    });
    return { success: true };
  } catch (error) {
    throw new HttpsError('internal', error.message);
  }
});

/**
 * Sticker paketi bildirimini tetikle - yeni paket eklendiğinde tüm kullanıcılara FCM gönderir
 */
exports.notifyNewPack = onCall(async (request) => {
  const { packId, packName, topic = 'new_stickers' } = request.data;
  if (!packId) throw new HttpsError('invalid-argument', 'packId is required');

  const message = {
    notification: {
      title: 'Yeni Sticker Paketi! 🎉',
      body: packName ? `${packName} paketi eklendi!` : 'Yeni bir sticker paketi eklendi!',
    },
    data: { packId, packName: packName || '', type: 'new_pack' },
    topic,
    android: {
      priority: 'high',
      notification: {
        channelId: 'sticky_notifications',
        icon: 'ic_notification_sticky',
        color: '#FF7043',
      },
    },
  };

  try {
    const response = await admin.messaging().send(message);
    return { success: true, messageId: response };
  } catch (error) {
    throw new HttpsError('internal', error.message);
  }
});

/**
 * Premium kullanıcı FCM token'ını topic'e kaydet
 */
exports.subscribeToTopic = onCall(async (request) => {
  const { fcmToken, topic } = request.data;
  if (!fcmToken || !topic) throw new HttpsError('invalid-argument', 'fcmToken and topic are required');

  try {
    await admin.messaging().subscribeToTopic(fcmToken, topic);
    return { success: true };
  } catch (error) {
    throw new HttpsError('internal', error.message);
  }
});

/**
 * Premium kullanıcı FCM token'ını topic'ten çıkar
 */
exports.unsubscribeFromTopic = onCall(async (request) => {
  const { fcmToken, topic } = request.data;
  if (!fcmToken || !topic) throw new HttpsError('invalid-argument', 'fcmToken and topic are required');

  try {
    await admin.messaging().unsubscribeFromTopic(fcmToken, topic);
    return { success: true };
  } catch (error) {
    throw new HttpsError('internal', error.message);
  }
});

// ====================== FIRESTORE TRIGGERS ======================

/**
 * Yeni sticker paketi Firestore'a yazıldığında FCM ile bildirim gönder
 */
exports.onNewPackCreated = onDocumentCreated('sticker_packs/{packId}', async (event) => {
  const pack = event.data.data();
  if (!pack) return;

  const message = {
    notification: {
      title: 'Yeni Sticker Paketi! 🎉',
      body: `${pack.name || 'Yeni bir paket'} eklendi!`,
    },
    data: { packId: event.params.packId, name: pack.name || '', type: 'new_pack' },
    topic: 'new_stickers',
    android: {
      priority: 'high',
      notification: {
        channelId: 'sticky_notifications',
      },
    },
  };

  try {
    await admin.messaging().send(message);
  } catch (error) {
    console.error('FCM send failed:', error.message);
  }
});

/**
 * Sticker paketi güncellendiğinde cache invalidasyonu için bildirim gönder
 */
exports.onPackUpdated = onDocumentUpdated('sticker_packs/{packId}', async (event) => {
  const after = event.data.after.data();
  if (!after) return;

  const message = {
    data: {
      packId: event.params.packId,
      type: 'pack_updated',
      version: after.version || '1',
      timestamp: Date.now().toString(),
    },
    topic: 'pack_updates',
    android: { priority: 'normal' },
  };

  try {
    await admin.messaging().send(message);
  } catch (error) {
    console.error('FCM update notification failed:', error.message);
  }
});