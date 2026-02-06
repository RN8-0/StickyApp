const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { onRequest } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');

admin.initializeApp();

// Global ayarlar (Bölge vb.)
setGlobalOptions({ region: 'us-central1' });



/**
 * Manuel olarak gönderilen bildirimleri yakalar
 */
exports.onManualNotificationCreated = onDocumentCreated('notifications/{notifId}', async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data();
    const title = data.title || 'Sticky';
    const body = data.body;
    const imageUrl = data.imageUrl || '';

    if (!body) return;

    const message = {
        topic: 'stickers',
        notification: {
            title: title,
            body: body
        },
        data: {
            title: title,
            body: body,
            imageUrl: imageUrl,
            type: 'manual'
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
        console.log('Successfully sent manual message:', response);
        return response;
    } catch (error) {
        console.error('Error sending manual message:', error);
    }
});

