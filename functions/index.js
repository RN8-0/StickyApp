const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');

admin.initializeApp();

// Global ayarlar (Bölge vb.)
setGlobalOptions({ region: 'us-central1' });

/**
 * Normal sticker paketleri için bildirim gönderir
 */
exports.onPackCreated = onDocumentCreated('stickers/{packId}', async (event) => {
    const snap = event.data;
    if (!snap) {
        console.log('No data associated with the event');
        return;
    }

    const newValue = snap.data();
    const packName = newValue.name;
    const imageUrl = newValue.tray_url || '';

    const message = {
        topic: 'stickers',
        notification: {
            title: 'Yeni Sticker Paketi!',
            body: `${packName} paketi şimdi yayında. Hemen göz at!`
        },
        data: {
            title: 'Yeni Stickerlar Geldi!',
            body: `${packName} paketi sticker kütüphanesine eklendi.`,
            imageUrl: imageUrl,
            packId: event.params.packId,
            type: 'new_pack'
        },
        android: {
            priority: 'high',
            notification: {
                sound: 'default',
                channelId: 'sticky_notifications'
            }
        }
    };

    try {
        const response = await admin.messaging().send(message);
        console.log('Successfully sent message:', response);
        return response;
    } catch (error) {
        console.error('Error sending message:', error);
    }
});

/**
 * Premium sticker paketleri için bildirim gönderir
 */
exports.onPremiumPackCreated = onDocumentCreated('premium_stickers/{packId}', async (event) => {
    const snap = event.data;
    if (!snap) {
        console.log('No data associated with the event');
        return;
    }

    const newValue = snap.data();
    const packName = newValue.name;
    const imageUrl = newValue.tray_url || '';

    const message = {
        topic: 'stickers',
        notification: {
            title: '🔥 Yeni Premium Paket!',
            body: `${packName} özel paketi yayında. Kaçırma!`
        },
        data: {
            title: 'Elite Stickerlar Geldi!',
            body: `${packName} premium paketi kütüphaneye eklendi.`,
            imageUrl: imageUrl,
            packId: event.params.packId,
            type: 'premium_pack'
        },
        android: {
            priority: 'high',
            notification: {
                sound: 'default',
                channelId: 'sticky_notifications'
            }
        }
    };

    try {
        const response = await admin.messaging().send(message);
        console.log('Successfully sent premium message:', response);
        return response;
    } catch (error) {
        console.error('Error sending message:', error);
    }
});
