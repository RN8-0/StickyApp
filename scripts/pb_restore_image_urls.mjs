/**
 * pb_restore_image_urls.mjs
 * Firebase Storage URL'lerini (404) sticky-images URL'lerine dönüştürür
 * 
 * Firebase URL formatı: .../o/stickers%2F{packId}%2F{fileName}?alt=media
 * Hedef URL formatı:    https://sticky-images.46.225.95.201.sslip.io/stickers/{packId}/{fileName}
 */

const PB_URL = 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const IMAGES_BASE = 'https://sticky-images.46.225.95.201.sslip.io/stickers';
const ADMIN_EMAIL = 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}

let token = '';

async function api(method, path, body) {
  const headers = { Authorization: token };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${PB_URL}/api${path}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${res.status}: ${text.substring(0, 200)}`);
  return text ? JSON.parse(text) : {};
}

// Firebase Storage URL'inden pack ID ve dosya adını çıkar
// .../o/stickers%2F{packId}%2F{fileName}?...
function parseFirebaseUrl(url) {
  if (!url || !url.includes('firebasestorage.googleapis.com')) return null;
  const m = url.match(/\/o\/stickers%2F([^%]+)%2F([^?]+)/);
  if (!m) return null;
  return {
    packId: decodeURIComponent(m[1]),
    fileName: decodeURIComponent(m[2])
  };
}

function buildImageUrl(packId, fileName) {
  return `${IMAGES_BASE}/${packId}/${fileName}`;
}

async function main() {
  // Auth
  const res = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD })
  });
  const data = await res.json();
  token = data.token;
  console.log('✓ Authenticated\n');

  // Tüm sticker ve premium_sticker kayıtlarını al
  const collections = ['stickers', 'premium_stickers'];
  
  for (const col of collections) {
    console.log(`\n=== ${col} ===`);
    let allRecords = [], page = 1;
    while (true) {
      const r = await api('GET', `/collections/${col}/records?perPage=200&page=${page}`);
      allRecords.push(...r.items);
      if (page >= r.totalPages) break;
      page++;
    }
    console.log(`${allRecords.length} kayıt bulundu`);

    let fixed = 0, skipped = 0, errors = 0;

    for (const rec of allRecords) {
      const firstStickerUrl = rec.stickers?.[0]?.url || '';
      
      // Zaten sticky-images URL'si mi?
      if (firstStickerUrl.includes('sticky-images')) {
        skipped++;
        continue;
      }
      
      // Firebase URL'lerini dönüştür
      let needsUpdate = false;
      
      const newStickers = (rec.stickers || []).map(s => {
        const parsed = parseFirebaseUrl(s.url);
        if (!parsed) return s;
        needsUpdate = true;
        return {
          ...s,
          url: buildImageUrl(parsed.packId, parsed.fileName)
        };
      });

      // tray_url da dönüştür
      let newTrayUrl = rec.tray_url || '';
      const trayParsed = parseFirebaseUrl(newTrayUrl);
      if (trayParsed) {
        newTrayUrl = buildImageUrl(trayParsed.packId, trayParsed.fileName);
        needsUpdate = true;
      }

      if (!needsUpdate) {
        skipped++;
        continue;
      }

      try {
        const update = { stickers: newStickers };
        if (newTrayUrl) update.tray_url = newTrayUrl;
        await api('PATCH', `/collections/${col}/records/${rec.id}`, update);
        fixed++;
        if (fixed % 20 === 0) console.log(`  ${fixed}/${allRecords.length} işlendi...`);
      } catch (e) {
        console.error(`  HATA ${rec.name}: ${e.message}`);
        errors++;
      }
    }

    console.log(`✓ ${col}: ${fixed} düzeltildi, ${skipped} atlandı, ${errors} hata`);
  }

  // Örnek doğrulama
  console.log('\n=== Doğrulama ===');
  const sample = await api('GET', '/collections/stickers/records?perPage=3&fields=name,stickers,tray_url');
  sample.items.forEach(r => {
    const url = r.stickers?.[0]?.url || '';
    console.log(`${r.name.substring(0,30).padEnd(30)} | tray: ${r.tray_url?.includes('sticky-images') ? 'OK' : 'BAD'} | sticker: ${url.includes('sticky-images') ? 'OK' : 'BAD'}`);
    if (url) console.log(`  URL: ${url}`);
  });

  console.log('\n✅ Tamamlandı!');
}

main().catch(e => { console.error('KRITIK HATA:', e.message); process.exit(1); });
