// Firestore → JSON export using gcloud access token + REST API
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'sticky-dcd20';
const BASE_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

function getToken() {
  return execSync('gcloud auth print-access-token', { encoding: 'utf-8' }).trim();
}

function parseFirestoreValue(val) {
  if (!val) return null;
  if ('stringValue' in val) return val.stringValue;
  if ('integerValue' in val) return parseInt(val.integerValue);
  if ('doubleValue' in val) return val.doubleValue;
  if ('booleanValue' in val) return val.booleanValue;
  if ('nullValue' in val) return null;
  if ('timestampValue' in val) return val.timestampValue;
  if ('arrayValue' in val) return (val.arrayValue.values || []).map(parseFirestoreValue);
  if ('mapValue' in val) {
    const obj = {};
    for (const [k, v] of Object.entries(val.mapValue.fields || {})) {
      obj[k] = parseFirestoreValue(v);
    }
    return obj;
  }
  if ('geoPointValue' in val) return val.geoPointValue;
  if ('referenceValue' in val) return val.referenceValue;
  if ('bytesValue' in val) return val.bytesValue;
  return val;
}

function parseDocument(doc) {
  const id = doc.name.split('/').pop();
  const data = {};
  for (const [k, v] of Object.entries(doc.fields || {})) {
    data[k] = parseFirestoreValue(v);
  }
  return { id, ...data, _createTime: doc.createTime, _updateTime: doc.updateTime };
}

async function fetchCollection(collectionId, token) {
  const docs = [];
  let pageToken = null;

  while (true) {
    let url = `${BASE_URL}/${collectionId}?pageSize=300`;
    if (pageToken) url += `&pageToken=${pageToken}`;

    const resp = await fetch(url, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!resp.ok) {
      const err = await resp.text();
      console.error(`  Error fetching ${collectionId}: ${resp.status} ${err}`);
      break;
    }

    const data = await resp.json();
    if (data.documents) {
      for (const doc of data.documents) {
        docs.push(parseDocument(doc));
      }
    }

    if (data.nextPageToken) {
      pageToken = data.nextPageToken;
    } else {
      break;
    }
  }

  return docs;
}

async function main() {
  const token = getToken();
  const outDir = path.join(__dirname, '..', 'firestore_export');
  fs.mkdirSync(outDir, { recursive: true });

  const collections = ['admins', 'notifications', 'premium_stickers', 'settings',
                        'stickers', 'suggestions', 'user_profiles', 'users'];

  for (const col of collections) {
    process.stdout.write(`Exporting ${col}... `);
    const docs = await fetchCollection(col, token);
    const outFile = path.join(outDir, `${col}.json`);
    fs.writeFileSync(outFile, JSON.stringify(docs, null, 2));
    console.log(`${docs.length} documents`);
  }

  console.log(`\nAll exported to ${outDir}`);
}

main().catch(e => { console.error(e); process.exit(1); });
