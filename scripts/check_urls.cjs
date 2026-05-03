const data = require('../firestore_export/stickers.json');
const first = data[0];
console.log('Pack:', first.id);
console.log('Sticker URL:', first.stickers?.[0]?.url);
console.log('tray_url:', first.tray_url);
// Test if URL works
const url = first.stickers?.[0]?.url;
if (url) {
  fetch(url, { method: 'HEAD' }).then(r => console.log('STATUS:', r.status)).catch(e => console.log('ERR:', e.message));
}
