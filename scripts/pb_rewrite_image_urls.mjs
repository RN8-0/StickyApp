const PB_URL = process.argv[2] || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const PB_EMAIL = process.argv[3] || 'admin@rn8.local';
const PB_PASS = process.argv[4] || 'mx6I0zPE3HSaqbjlAY0p';
const OLD_URLS = [
  'http://46.225.95.201:8086',
  'https://46.225.95.201:8086',
];
const NEW_URL = 'https://sticky-images.46.225.95.201.sslip.io';

async function auth() {
  const response = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: PB_EMAIL, password: PB_PASS }),
  });
  const data = await response.json();
  if (!response.ok || !data.token) throw new Error(`PB auth failed ${response.status}: ${JSON.stringify(data)}`);
  return data.token;
}

function rewriteValue(value) {
  if (typeof value === 'string') {
    let next = value;
    for (const oldUrl of OLD_URLS) next = next.replaceAll(oldUrl, NEW_URL);
    return next;
  }
  if (Array.isArray(value)) return value.map(rewriteValue);
  if (value && typeof value === 'object') {
    const out = {};
    for (const [key, val] of Object.entries(value)) out[key] = rewriteValue(val);
    return out;
  }
  return value;
}

async function getAll(collection, token) {
  const items = [];
  let page = 1;
  while (true) {
    const response = await fetch(`${PB_URL}/api/collections/${collection}/records?page=${page}&perPage=200`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (response.status === 404) return items;
    const data = await response.json();
    if (!response.ok) throw new Error(`${collection} list failed ${response.status}: ${JSON.stringify(data)}`);
    items.push(...(data.items || []));
    if (page >= data.totalPages) break;
    page += 1;
  }
  return items;
}

async function patchRecord(collection, id, body, token) {
  const response = await fetch(`${PB_URL}/api/collections/${collection}/records/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`${collection}/${id} patch failed ${response.status}: ${JSON.stringify(data)}`);
}

async function run() {
  const token = await auth();
  const collections = ['stickers', 'premium_stickers', 'draft_stickers', 'notifications', 'user_submissions'];
  let patched = 0;

  for (const collection of collections) {
    const records = await getAll(collection, token);
    console.log(`${collection}: ${records.length} records`);
    for (const record of records) {
      const patch = {};
      for (const field of ['tray_url', 'imageUrl', 'image_url', 'url', 'stickers']) {
        if (!(field in record)) continue;
        const original = record[field];
        const rewritten = rewriteValue(original);
        if (JSON.stringify(original) !== JSON.stringify(rewritten)) patch[field] = rewritten;
      }
      if (Object.keys(patch).length) {
        await patchRecord(collection, record.id, patch, token);
        patched += 1;
      }
    }
  }

  console.log(`Patched records: ${patched}`);
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
