// Fix PocketBase sticker records:
// 1. Add tray_url field to collections
// 2. Remove tray_*.png from stickers array
// 3. Set tray_image_file and tray_url correctly
import https from 'https';
import { execSync } from 'child_process';

const PB_URL = 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const PB_EMAIL = 'arainunger@gmail.com';
const PB_PASS = 'StickyAdmin2026!';
const IMAGES_BASE = 'https://sticky-admin.46.225.95.201.sslip.io/images/stickers';
const SSH = 'ssh -i ~/.ssh/hetzner_compair -o StrictHostKeyChecking=no -o IdentitiesOnly=yes root@46.225.95.201';
const STICKERS_DIR = '/opt/sticky-images/stickers';

function req(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const url = new URL(PB_URL + path);
    const data = body ? JSON.stringify(body) : null;
    const r = https.request({
      hostname: url.hostname, port: 443, path: url.pathname + url.search,
      method, headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {})
      }
    }, res => {
      let b = ''; res.on('data', d => b += d);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(b) }); }
        catch { resolve({ status: res.statusCode, body: b }); }
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function main() {
  // Login
  const loginRes = await req('POST', '/api/collections/_superusers/auth-with-password', { identity: PB_EMAIL, password: PB_PASS });
  const token = loginRes.body.token;
  if (!token) { console.error('Login failed'); process.exit(1); }
  console.log('Logged in');

  // Add tray_url field to both collections if missing
  for (const col of ['stickers', 'premium_stickers']) {
    const colRes = await req('GET', '/api/collections/' + col, null, token);
    const fields = colRes.body.fields || [];
    if (!fields.find(f => f.name === 'tray_url')) {
      await req('PATCH', '/api/collections/' + col, {
        fields: [...fields, { name: 'tray_url', type: 'text' }]
      }, token);
      console.log('Added tray_url field to', col);
    }
  }

  // Get all records from both collections
  for (const col of ['stickers', 'premium_stickers']) {
    let page = 1;
    let totalPages = 1;
    let updated = 0, skipped = 0;

    while (page <= totalPages) {
      const res = await req('GET', `/api/collections/${col}/records?perPage=50&page=${page}`, null, token);
      const { items, totalPages: tp } = res.body;
      totalPages = tp || 1;

      for (const record of (items || [])) {
        const stickers = record.stickers || [];
        const traySticker = stickers.find(s => s.image_file && s.image_file.startsWith('tray_'));
        const realStickers = stickers.filter(s => !s.image_file?.startsWith('tray_'));

        // Determine tray file from server
        let trayFile = traySticker?.image_file || '';
        let trayUrl = traySticker?.url || '';

        // If no tray in stickers, try to find on server using pack name
        if (!trayFile) {
          // Try to get pack folder from existing sticker url
          const firstUrl = realStickers[0]?.url || '';
          const folderMatch = firstUrl.match(/\/images\/stickers\/([^/]+)\//);
          if (folderMatch) {
            const folder = folderMatch[1];
            try {
              const files = execSync(`${SSH} "ls ${STICKERS_DIR}/${folder}/ 2>/dev/null | grep '^tray_'"`).toString().trim();
              if (files) {
                trayFile = files.split('\n')[0];
                trayUrl = `${IMAGES_BASE}/${folder}/${trayFile}`;
              }
            } catch {}
          }
        }

        // Skip if nothing changed
        if (realStickers.length === stickers.length && record.tray_url) {
          skipped++;
          continue;
        }

        // Update record
        const updateData = {
          stickers: realStickers,
          sticker_count: realStickers.length,
          tray_image_file: trayFile || (realStickers[0]?.image_file || ''),
          tray_url: trayUrl || (realStickers[0]?.url || ''),
        };

        const updateRes = await req('PATCH', `/api/collections/${col}/records/${record.id}`, updateData, token);
        if (updateRes.status === 200) {
          updated++;
          if (updated % 30 === 0) console.log(`${col}: ${updated} updated...`);
        } else {
          console.error('Error updating', record.id, JSON.stringify(updateRes.body).substring(0, 100));
        }
      }
      page++;
    }
    console.log(`${col}: ${updated} updated, ${skipped} skipped`);
  }
  console.log('Done!');
}

main().catch(console.error);
