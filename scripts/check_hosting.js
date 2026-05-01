const https = require('https');

function fetchUrl(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: data }));
    }).on('error', reject);
  });
}

(async () => {
  const r1 = await fetchUrl('https://sticky-dcd20.web.app/');
  const title1 = r1.body.match(/<title[^>]*>([^<]*)<\/title>/i)?.[1];
  console.log('sticky-dcd20.web.app - Status:', r1.status, '| Title:', title1);

  const r2 = await fetchUrl('https://sticky-privacy-legal.web.app/');
  const title2 = r2.body.match(/<title[^>]*>([^<]*)<\/title>/i)?.[1];
  console.log('sticky-privacy-legal.web.app - Status:', r2.status, '| Title:', title2);
  console.log('Privacy preview:', r2.body.slice(0, 500));
})();
