const PB_URL = 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}

const fields = [
  { name: 'publisher', type: 'text' },
  { name: 'publisher_name', type: 'text' },
  { name: 'name_en', type: 'text' },
  { name: 'source', type: 'text' },
  { name: 'telegram_set_name', type: 'text' },
  { name: 'telegram_set_title', type: 'text' },
  { name: 'telegram_part', type: 'number' },
  { name: 'telegram_total_parts', type: 'number' },
  { name: 'batch_generated', type: 'bool' },
  { name: 'batch_source', type: 'text' },
  { name: 'batch_search_term', type: 'text' },
  { name: 'disabled_reason', type: 'text' },
  { name: 'created_at', type: 'text' },
  { name: 'price_try', type: 'number' },
  { name: 'price_usd', type: 'number' },
  { name: 'price_eur', type: 'number' },
];

async function main() {
  const auth = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  const { token } = await auth.json();
  const headers = { Authorization: token, 'Content-Type': 'application/json' };

  for (const name of ['stickers', 'premium_stickers', 'draft_stickers']) {
    const collection = await (await fetch(`${PB_URL}/api/collections/${name}`, { headers })).json();
    const key = collection.fields ? 'fields' : 'schema';
    const current = collection[key] || [];
    const existing = new Set(current.map(f => f.name));
    const missing = fields.filter(f => !existing.has(f.name));
    if (!missing.length) {
      console.log(`${name}: ok`);
      continue;
    }
    const res = await fetch(`${PB_URL}/api/collections/${name}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({ [key]: [...current, ...missing] }),
    });
    if (!res.ok) throw new Error(`${name}: ${res.status} ${await res.text()}`);
    console.log(`${name}: added ${missing.map(f => f.name).join(', ')}`);
  }
}

main().catch(e => { console.error(e.message); process.exit(1); });
