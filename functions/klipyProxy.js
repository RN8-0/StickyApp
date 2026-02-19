const { onRequest } = require('firebase-functions/v2/https');
const fetch = require('node-fetch');

const KLIPY_API_KEY = 'YRtbKLSbrPqeEcPPpIpdoCqlLqZ9LlfbwQhXefbv3FQucQTjREFmkVLExf9JnI8k';

/**
 * Klipy API Proxy - CORS sorununu çözmek için
 * Frontend'den çağrılır, backend'den Klipy API'ye istek atar
 */
exports.klipyProxy = onRequest({ cors: true }, async (req, res) => {
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
        const { query, limit = 15, endpoint = 'search', type = 'stickers' } = req.query;

        if (!query && endpoint === 'search') {
            res.status(400).json({ error: 'Query parameter is required for search' });
            return;
        }

        // type: 'stickers' veya 'gifs' — Klipy'de ayrı endpoint'ler
        const contentType = type === 'gifs' ? 'gifs' : 'stickers';

        // Klipy API endpoint oluştur
        let klipyUrl;
        if (endpoint === 'trending') {
            klipyUrl = `https://api.klipy.com/v2/${contentType}/trending?limit=${limit}&api_key=${KLIPY_API_KEY}`;
        } else {
            klipyUrl = `https://api.klipy.com/v2/${contentType}/search?q=${encodeURIComponent(query)}&limit=${limit}&api_key=${KLIPY_API_KEY}`;
        }

        console.log('[Klipy Proxy] Fetching:', klipyUrl);

        // Klipy API'ye istek at
        const response = await fetch(klipyUrl);
        
        if (!response.ok) {
            throw new Error(`Klipy API error: ${response.status}`);
        }

        const data = await response.json();
        
        console.log('[Klipy Proxy] Success:', data.data?.length || 0, 'results');
        
        // Response'u frontend'e döndür
        res.status(200).json(data);
    } catch (error) {
        console.error('[Klipy Proxy] Error:', error);
        res.status(500).json({ 
            error: 'Failed to fetch from Klipy',
            message: error.message 
        });
    }
});
