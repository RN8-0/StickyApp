const PB_URL = process.env.PB_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'arainunger@gmail.com';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
const DRY_RUN = process.argv.includes('--dry-run');

if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}

const exactNames = new Map(Object.entries({
  'amongusguys': 'Among Us Guys',
  'animatedemojies': 'Animated Emojis',
  'ashqyat': 'Ashqyat',
  'babyyoda': 'Baby Yoda',
  'baclpackgiirl': 'Backpack Girl',
  'bandorimygo': 'Bandori MyGO',
  'beasttamerstickers': 'Beast Tamer',
  'boysclub': 'Boys Club',
  'cupiman': 'Cupi Man',
  'cutiemadeline': 'Cutie Madeline',
  'deadpoolemotions': 'Deadpool Emotions',
  'deepturkish': 'Deep Turkish',
  'dhcjjowjdj': 'Reaction Pack',
  'durexpack': 'Durex Pack',
  'elonmusk': 'Elon Musk',
  'elonmusk ru': 'Elon Musk',
  'elonmuskpdg': 'Elon Musk',
  'fakebeast': 'Fake Beast',
  'figurinhaswa4': 'Figurinhas WA',
  'friendlydeath': 'Friendly Death',
  'fuuxuddyiivviv': 'Reaction Pack',
  'genshinarabiastickers': 'Genshin Arabia',
  'gumballearth928b': 'Gumball Earth',
  'gumloveis': 'Love Is',
  'birdofparadise': 'Bird Of Paradise',
  'coloredcats': 'Colored Cats',
  'donutthedog': 'Donut The Dog',
  'hallowen text collection': 'Halloween Text Collection',
  'hackers1998': 'Hackers 1998',
  'love mehot': 'Love Me Hot',
  'mrerdogan': 'Mr Erdogan',
  'mrpanda': 'Mr Panda',
  'utyaduck': 'Utya Duck',
  'futball120': 'Football',
  'tmeedustickerskiss': 'Kiss Stickers',
  'iron man': 'Iron Man',
}));

const categoryRules = [
  ['love', ['love', 'kiss', 'hug', 'romantic', 'romance', 'valentine', 'heart', 'sweet tinky', 'sweet babies']],
  ['birthday', ['birthday', 'cake', 'balloon']],
  ['morning', ['morning', 'good morning', 'coffee', 'sunrise']],
  ['religious', ['ramadan', 'eid', 'islam', 'muslim', 'prayer', 'allah', 'christmas', 'easter', 'diwali']],
  ['sports', ['football', 'futball', 'soccer', 'basketball', 'sport', 'gym', 'workout', 'tennis', 'goal', 'messi', 'ronaldo', 'ronny7']],
  ['animals', ['animal', 'cat', 'dog', 'bear', 'bunny', 'rabbit', 'duck', 'bird', 'panda', 'monkey', 'gorilla', 'badger', 'orangoutang', 'rottweiler', 'shiba', 'sheep', 'pet', 'kitty', 'ladycat', 'donutthedog', 'milk mocha']],
  ['night', ['night', 'good night', 'moon', 'sleep', 'dream']],
  ['anime', ['anime', 'manga', 'manhwa', 'genshin', 'honkai', 'bandori', 'mygo', 'monogatari', 'kusuriya', 'chibi', 'kawaii', 'pokemon', 'pikachu']],
  ['gaming', ['among us', 'amongus', 'game', 'gaming', 'minecraft', 'fortnite', 'roblox', 'starman', 'herophine']],
  ['movie', ['movie', 'film', 'cinema', 'marvel', 'iron man', 'deadpool', 'breaking bad', 'better call saul', 'minions', 'mr bean', 'sherlock', 'titanic', 'winter is coming', 'star wars', 'baby yoda', 'yoda', 'rick', 'wubba', 'gumball', 'walker', 'the end of the fucking world', 'ice age', 'oppenheimer', 'regularshow', 'regular show', 'sheldoncooper', 'sheldon cooper', 'walter white', 'wednesday', 'tvseries', 'turkishtvseries', 'zimorodok', 'kung fu', 'office']],
  ['music', ['music', 'song', 'dance', 'singer', 'concert', 'beat']],
  ['food', ['food', 'pizza', 'burger', 'coffee', 'cake', 'donut', 'chef']],
  ['emoji', ['emoji', 'emojies', 'emojis', 'emoticon', 'smiley']],
  ['text', ['text', 'quote', 'word', 'loud text', 'street texts', 'hallowen text', 'typography', 'turkce']],
  ['memes', ['meme', 'reaction', 'side eye', 'crying', 'head shake', 'mood', 'vibes', 'peoplememes', 'doh my doodles', 'elon', 'trump', 'putin', 'president', 'kim', 'northkorea', 'supremeleader', 'ishowspeed', 'speed', 'hackers', 'telegram pack']],
  ['cute', ['cute', 'cutie', 'baby', 'sweet', 'adorable']],
  ['nature', ['nature', 'flower', 'moon', 'forest', 'winter', 'snow', 'ocean', 'mountain', 'sky']],
  ['motivation', ['motivation', 'success', 'quote', 'hustle']],
  ['cars', ['car', 'cars', 'auto', 'racing', 'motorcycle']],
  ['humor', ['funny', 'humor', 'comedy', 'lol', 'haha', 'joke', 'recep', 'wild ride']],
  ['entertainment', ['party', 'celebration', 'fun', 'doodle', 'whimsy', 'mashup']],
];

function normalizeText(value) {
  return String(value || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[._-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function toTitleCase(value) {
  return value.split(/\s+/).map((word) => {
    if (/^(3d|wa|tv|ai)$/i.test(word)) return word.toUpperCase();
    if (/^mr$/i.test(word)) return 'Mr';
    return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
  }).join(' ');
}

function cleanName(record) {
  const raw = record.name_en || record.name || record.name_tr || record.telegram_set_title || record.telegram_set_name || record.batch_search_term || 'Sticker Pack';
  let value = String(raw)
    .replace(/[._-]+/g, ' ')
    .replace(/\s+by\s+\w+bot\b.*$/i, '')
    .replace(/\s+\d+\s+m[ml][a-z0-9]+$/i, '')
    .replace(/\s+m[ml][a-z0-9]+$/i, '')
    .replace(/\s+/g, ' ')
    .trim();

  const partMatch = String(raw).match(/\s+(\d+)\s+m[ml][a-z0-9]+$/i);
  const partNumber = partMatch?.[1] || '';
  const key = normalizeText(value).replace(/\s+\d+$/, '');
  value = exactNames.get(key) || value;
  value = toTitleCase(value);

  if (partNumber && !new RegExp(`\\b${partNumber}$`).test(value)) {
    value = `${value} ${partNumber}`;
  }

  return value || 'Sticker Pack';
}

function classify(record, cleanedName) {
  const haystack = normalizeText([
    cleanedName,
    record.name,
    record.name_en,
    record.name_tr,
    record.telegram_set_name,
    record.telegram_set_title,
    record.batch_search_term,
    record.description,
  ].filter(Boolean).join(' '));

  for (const [category, keywords] of categoryRules) {
    if (keywords.some((keyword) => haystack.includes(normalizeText(keyword)))) return category;
  }
  return record.category && record.category !== 'entertainment' ? record.category : 'other';
}

async function pbFetch(path, options = {}, token) {
  const response = await fetch(`${PB_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', Authorization: token, ...(options.headers || {}) },
  });
  if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
  return response.status === 204 ? null : response.json();
}

async function authenticate() {
  const response = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  if (!response.ok) throw new Error(`Auth failed: ${response.status} ${await response.text()}`);
  return (await response.json()).token;
}

async function getAllRecords(token) {
  const records = [];
  let page = 1;
  while (true) {
    const data = await pbFetch(`/api/collections/stickers/records?perPage=200&page=${page}`, {}, token);
    records.push(...(data.items || []));
    if (page >= data.totalPages) break;
    page += 1;
  }
  return records;
}

async function main() {
  const token = await authenticate();
  const records = await getAllRecords(token);
  let changed = 0;
  const categoryCounts = new Map();

  for (const record of records) {
    const name = cleanName(record);
    const category = classify(record, name);
    categoryCounts.set(category, (categoryCounts.get(category) || 0) + 1);

    const patch = {};
    if (record.name !== name) patch.name = name;
    if (record.name_en !== name) patch.name_en = name;
    if (record.name_tr !== name) patch.name_tr = name;
    if (record.category !== category) patch.category = category;

    if (Object.keys(patch).length === 0) continue;
    changed += 1;
    console.log(`${DRY_RUN ? 'DRY ' : ''}${record.id}: ${record.name} -> ${name} [${record.category || 'empty'} -> ${category}]`);
    if (!DRY_RUN) {
      await pbFetch(`/api/collections/stickers/records/${record.id}`, { method: 'PATCH', body: JSON.stringify(patch) }, token);
    }
  }

  console.log(`Changed ${changed}/${records.length} records.`);
  console.log(Object.fromEntries([...categoryCounts.entries()].sort((a, b) => a[0].localeCompare(b[0]))));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});