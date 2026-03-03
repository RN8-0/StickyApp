const { onRequest } = require('firebase-functions/v2/https');

/**
 * Telegram API Proxy - Routes requests to Telegram Bot API to avoid CORS
 * Supports both JSON API calls and file downloads
 */
exports.telegramProxy = onRequest({ cors: true }, async (req, res) => {
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.set('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.status(204).send('');
        return;
    }

    try {
        const { token, method, file_path } = req.query;

        if (!token) {
            res.status(400).json({ error: 'Bot token is required' });
            return;
        }

        let telegramUrl;

        if (file_path) {
            // File download: /file/bot{token}/{file_path}
            telegramUrl = `https://api.telegram.org/file/bot${token}/${file_path}`;
        } else if (method) {
            // API method call: /bot{token}/{method}?params
            const params = { ...req.query };
            delete params.token;
            delete params.method;
            const queryString = Object.entries(params)
                .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
                .join('&');
            telegramUrl = `https://api.telegram.org/bot${token}/${method}${queryString ? '?' + queryString : ''}`;
        } else {
            res.status(400).json({ error: 'Either method or file_path is required' });
            return;
        }

        console.log('[Telegram Proxy] Fetching:', file_path ? 'FILE' : method);

        const response = await fetch(telegramUrl);

        if (!response.ok) {
            const errorText = await response.text();
            console.error('[Telegram Proxy] Error:', response.status, errorText);
            res.status(response.status).send(errorText);
            return;
        }

        const contentType = response.headers.get('content-type') || '';

        if (file_path || !contentType.includes('application/json')) {
            // Binary file download
            const buffer = await response.arrayBuffer();
            res.set('Content-Type', contentType || 'application/octet-stream');
            res.status(200).send(Buffer.from(buffer));
        } else {
            // JSON API response
            const data = await response.json();
            res.status(200).json(data);
        }
    } catch (error) {
        console.error('[Telegram Proxy] Error:', error);
        res.status(500).json({
            error: 'Telegram proxy error',
            message: error.message
        });
    }
});
