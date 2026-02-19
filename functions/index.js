const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { onRequest } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');
const { klipyProxy } = require('./klipyProxy');

admin.initializeApp();

// Global ayarlar (Bölge vb.)
setGlobalOptions({ region: 'us-central1' });

const GIPHY_API_KEY = 'LLWhfEaYJSNyuhTXUEnSol15YU00raps';
const KLIPY_API_KEY = 'YRtbKLSbrPqeEcPPpIpdoCqlLqZ9LlfbwQhXefbv3FQucQTjREFmkVLExf9JnI8k';



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

/**
 * Giphy API Proxy - CORS sorununu çözmek için
 * Frontend'den çağrılır, backend'den Giphy API'ye istek atar
 */
exports.giphyProxy = onRequest({ cors: true }, async (req, res) => {
    // CORS headers
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.set('Access-Control-Allow-Headers', 'Content-Type');

    // OPTIONS request için
    if (req.method === 'OPTIONS') {
        res.status(204).send('');
        return;
    }

    try {
        const { query, limit = 15, offset = 0, type = 'stickers', endpoint = 'search' } = req.query;

        if (!query && endpoint === 'search') {
            res.status(400).json({ error: 'Query parameter is required for search' });
            return;
        }

        // Giphy API endpoint oluştur
        let giphyUrl;
        if (endpoint === 'trending') {
            giphyUrl = `https://api.giphy.com/v1/${type}/trending?api_key=${GIPHY_API_KEY}&limit=${limit}&offset=${offset}`;
        } else {
            giphyUrl = `https://api.giphy.com/v1/${type}/search?api_key=${GIPHY_API_KEY}&q=${encodeURIComponent(query)}&limit=${limit}&offset=${offset}&rating=pg-13`;
        }

        console.log('[Giphy Proxy] Fetching:', giphyUrl);

        // Giphy API'ye istek at
        const response = await fetch(giphyUrl);
        
        if (!response.ok) {
            throw new Error(`Giphy API error: ${response.status}`);
        }

        const data = await response.json();
        
        console.log('[Giphy Proxy] Success:', data.data?.length || 0, 'results');
        
        // Response'u frontend'e döndür
        res.status(200).json(data);
    } catch (error) {
        console.error('[Giphy Proxy] Error:', error);
        res.status(500).json({ 
            error: 'Failed to fetch from Giphy',
            message: error.message 
        });
    }
});

// Export klipyProxy
exports.klipyProxy = klipyProxy;
