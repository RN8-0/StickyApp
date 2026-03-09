const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const { onRequest } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');
const { klipyProxy } = require('./klipyProxy');
const { telegramProxy } = require('./telegramProxy');

admin.initializeApp();

// Global ayarlar (Bölge vb.)
setGlobalOptions({ region: 'us-central1' });

const GIPHY_API_KEY = process.env.GIPHY_API_KEY || 'LLWhfEaYJSNyuhTXUEnSol15YU00raps';
const KLIPY_API_KEY = process.env.KLIPY_API_KEY || 'YRtbKLSbrPqeEcPPpIpdoCqlLqZ9LlfbwQhXefbv3FQucQTjREFmkVLExf9JnI8k';

// Simple in-memory rate limiter
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW = 60 * 1000; // 1 minute
const RATE_LIMIT_MAX = 30; // max 30 requests per minute per IP

function checkRateLimit(ip) {
  const now = Date.now();
  const key = ip || 'unknown';
  
  if (!rateLimitMap.has(key)) {
    rateLimitMap.set(key, { count: 1, resetAt: now + RATE_LIMIT_WINDOW });
    return true;
  }
  
  const entry = rateLimitMap.get(key);
  if (now > entry.resetAt) {
    entry.count = 1;
    entry.resetAt = now + RATE_LIMIT_WINDOW;
    return true;
  }
  
  entry.count++;
  return entry.count <= RATE_LIMIT_MAX;
}

// Clean up rate limit map periodically
setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of rateLimitMap.entries()) {
    if (now > entry.resetAt + RATE_LIMIT_WINDOW) {
      rateLimitMap.delete(key);
    }
  }
}, 5 * 60 * 1000);

// Supported languages for auto-translation
const TARGET_LANGUAGES = [
  { code: 'tr', name: 'Turkish' },
  { code: 'de', name: 'German' },
  { code: 'fr', name: 'French' },
  { code: 'es', name: 'Spanish' },
  { code: 'pt', name: 'Portuguese' },
  { code: 'it', name: 'Italian' },
  { code: 'ru', name: 'Russian' },
  { code: 'ar', name: 'Arabic' },
  { code: 'hi', name: 'Hindi' },
  { code: 'ja', name: 'Japanese' },
  { code: 'ko', name: 'Korean' },
  { code: 'zh', name: 'Chinese' },
  { code: 'th', name: 'Thai' },
  { code: 'vi', name: 'Vietnamese' },
  { code: 'id', name: 'Indonesian' },
  { code: 'fil', name: 'Filipino' },
];

/**
 * Translate text using Google Translate (free endpoint)
 */
async function translateText(text, targetLang) {
  if (!text || text.trim() === '') return text;
  try {
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=${targetLang}&dt=t&q=${encodeURIComponent(text)}`;
    const response = await fetch(url);
    const data = await response.json();
    if (data && data[0] && data[0][0] && data[0][0][0]) {
      return data[0][0][0];
    }
    return text;
  } catch (error) {
    console.error(`Translation error for ${targetLang}:`, error.message);
    return text;
  }
}

/**
 * Auto-translate pack name to all supported languages
 */
async function autoTranslatePackName(packName) {
  const translations = { name_en: packName };
  const promises = TARGET_LANGUAGES.map(async (lang) => {
    const googleLangCode = lang.code === 'fil' ? 'tl' : lang.code;
    try {
      const translated = await translateText(packName, googleLangCode);
      translations[`name_${lang.code}`] = translated;
    } catch (e) {
      console.error(`Failed to translate to ${lang.code}:`, e);
    }
  });
  await Promise.all(promises);
  return translations;
}

/**
 * Firestore trigger: Auto-translate sticker pack names on create/update
 * Works for both 'stickers' and 'premium_stickers' collections
 */
exports.onStickerPackWrite = onDocumentWritten('stickers/{packId}', async (event) => {
  const after = event.data?.after?.data();
  const before = event.data?.before?.data();
  if (!after) return; // Deleted

  // Only translate if name changed or no translations exist yet
  const nameChanged = !before || before.name !== after.name;
  const hasTranslations = after.name_tr && after.name_es && after.name_fr;
  
  if (!nameChanged && hasTranslations) return;
  if (!after.name) return;

  console.log(`[AutoTranslate] Translating pack "${after.name}" (${event.params.packId})`);
  
  try {
    const translations = await autoTranslatePackName(after.name);
    await event.data.after.ref.update(translations);
    console.log(`[AutoTranslate] ✓ Translated to ${Object.keys(translations).length} languages`);
  } catch (error) {
    console.error('[AutoTranslate] Error:', error);
  }
});

exports.onPremiumStickerPackWrite = onDocumentWritten('premium_stickers/{packId}', async (event) => {
  const after = event.data?.after?.data();
  const before = event.data?.before?.data();
  if (!after) return;

  const nameChanged = !before || before.name !== after.name;
  const hasTranslations = after.name_tr && after.name_es && after.name_fr;
  
  if (!nameChanged && hasTranslations) return;
  if (!after.name) return;

  console.log(`[AutoTranslate] Translating premium pack "${after.name}" (${event.params.packId})`);
  
  try {
    const translations = await autoTranslatePackName(after.name);
    await event.data.after.ref.update(translations);
    console.log(`[AutoTranslate] ✓ Translated to ${Object.keys(translations).length} languages`);
  } catch (error) {
    console.error('[AutoTranslate] Error:', error);
  }
});



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
    // Rate limiting
    const clientIp = req.ip || req.headers['x-forwarded-for'] || 'unknown';
    if (!checkRateLimit(clientIp)) {
      res.status(429).json({ error: 'Too many requests. Please try again later.' });
      return;
    }

    // CORS headers
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Allow-Methods', 'GET, OPTIONS');
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

// Export telegramProxy
exports.telegramProxy = telegramProxy;

// ========== UGC (User Generated Content) Functions ==========

/**
 * NSFW content detection using Google Cloud Vision SafeSearch
 * Returns true if content is safe, false if flagged
 */
async function checkContentSafety(imageUrl) {
  try {
    const vision = require('@google-cloud/vision');
    const client = new vision.ImageAnnotatorClient();
    
    const [result] = await client.safeSearchDetection(imageUrl);
    const safe = result.safeSearchAnnotation;
    
    if (!safe) return { safe: true, reason: '' };
    
    // LIKELY or VERY_LIKELY means content is flagged
    const flaggedCategories = [];
    if (['LIKELY', 'VERY_LIKELY'].includes(safe.adult)) flaggedCategories.push('adult');
    if (['LIKELY', 'VERY_LIKELY'].includes(safe.violence)) flaggedCategories.push('violence');
    if (['LIKELY', 'VERY_LIKELY'].includes(safe.racy)) flaggedCategories.push('racy');
    
    return {
      safe: flaggedCategories.length === 0,
      reason: flaggedCategories.join(', '),
      details: safe
    };
  } catch (error) {
    console.error('[NSFW Check] Vision API error:', error.message);
    // If Vision API fails, flag for manual review
    return { safe: false, reason: 'vision_api_error' };
  }
}

/**
 * Process user sticker submission
 * Triggered when a new document is created in user_submissions
 */
exports.onUserSubmission = onDocumentCreated('user_submissions/{submissionId}', async (event) => {
  const snap = event.data;
  if (!snap) return;
  
  const submission = snap.data();
  const submissionId = event.params.submissionId;
  
  console.log(`[UGC] New submission: ${submissionId} from user ${submission.user_id}`);
  
  // Update status to processing
  await snap.ref.update({ status: 'processing', processed_at: admin.firestore.FieldValue.serverTimestamp() });
  
  try {
    // Check all sticker images for NSFW content
    let allSafe = true;
    let flagReasons = [];
    
    if (submission.stickers && submission.stickers.length > 0) {
      for (const sticker of submission.stickers) {
        if (sticker.image_url) {
          const safety = await checkContentSafety(sticker.image_url);
          if (!safety.safe) {
            allSafe = false;
            flagReasons.push(`${sticker.name || 'sticker'}: ${safety.reason}`);
          }
        }
      }
    }
    
    if (allSafe) {
      // Auto-approve: Move to stickers collection
      const packData = {
        name: submission.pack_name,
        name_en: submission.pack_name,
        publisher: submission.publisher_name || submission.display_name || 'Community',
        publisher_email: submission.user_email || '',
        publisher_user_id: submission.user_id,
        category: submission.category || 'community',
        stickers: submission.stickers || [],
        sticker_count: (submission.stickers || []).length,
        is_active: true,
        is_premium: false,
        is_animated: false,
        download_count: 0,
        favorite_count: 0,
        view_count: 0,
        whatsapp_add_count: 0,
        source: 'user_submission',
        created_at: admin.firestore.FieldValue.serverTimestamp(),
        published_at: admin.firestore.FieldValue.serverTimestamp(),
      };
      
      // Save to stickers collection
      const stickerRef = admin.firestore().collection('stickers').doc(submissionId);
      await stickerRef.set(packData);
      
      // Update submission status
      await snap.ref.update({ 
        status: 'approved', 
        approved_at: admin.firestore.FieldValue.serverTimestamp(),
        auto_approved: true
      });
      
      // Update user profile stats
      if (submission.user_id) {
        const profileRef = admin.firestore().collection('user_profiles').doc(submission.user_id);
        await profileRef.update({
          packs_published: admin.firestore.FieldValue.increment(1),
          last_published_at: admin.firestore.FieldValue.serverTimestamp()
        }).catch(() => {});
      }
      
      console.log(`[UGC] ✓ Auto-approved submission ${submissionId}`);
    } else {
      // Flag for admin review
      await snap.ref.update({ 
        status: 'flagged', 
        flag_reasons: flagReasons,
        flagged_at: admin.firestore.FieldValue.serverTimestamp()
      });
      
      console.log(`[UGC] ⚠ Flagged submission ${submissionId}: ${flagReasons.join('; ')}`);
    }
  } catch (error) {
    console.error(`[UGC] Error processing submission ${submissionId}:`, error);
    await snap.ref.update({ 
      status: 'error', 
      error_message: error.message 
    });
  }
});

/**
 * Content report handler
 * When a user reports content, log it and flag if threshold reached
 */
exports.onContentReport = onDocumentCreated('content_reports/{reportId}', async (event) => {
  const snap = event.data;
  if (!snap) return;
  
  const report = snap.data();
  console.log(`[Report] New report for pack ${report.pack_id} by user ${report.reporter_id}`);
  
  // Count reports for this pack
  const reportsSnap = await admin.firestore()
    .collection('content_reports')
    .where('pack_id', '==', report.pack_id)
    .get();
  
  const reportCount = reportsSnap.size;
  
  // If 3+ reports, auto-disable the pack
  if (reportCount >= 3) {
    const collections = ['stickers', 'premium_stickers'];
    for (const col of collections) {
      const packRef = admin.firestore().collection(col).doc(report.pack_id);
      const packSnap = await packRef.get();
      if (packSnap.exists) {
        await packRef.update({ 
          is_active: false, 
          disabled_reason: 'multiple_reports',
          report_count: reportCount
        });
        console.log(`[Report] Pack ${report.pack_id} disabled (${reportCount} reports)`);
      }
    }
  }
});
