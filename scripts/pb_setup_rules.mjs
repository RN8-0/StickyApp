const PB_URL = process.argv[2] || process.env.PB_URL;
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;

if (!PB_URL) throw new Error('Usage: node scripts/pb_setup_rules.mjs <PB_URL>');
if (!ADMIN_PASSWORD) throw new Error('PB_ADMIN_PASSWORD is required.');

const ADMIN_CHECK = '@collection.admins_list.email ?= @request.auth.email';

const rules = {
  stickers: {
    listRule: '',
    viewRule: '',
    createRule: ADMIN_CHECK,
    updateRule: '',
    deleteRule: ADMIN_CHECK,
  },
  premium_stickers: {
    listRule: '',
    viewRule: '',
    createRule: ADMIN_CHECK,
    updateRule: '',
    deleteRule: ADMIN_CHECK,
  },
  messages: {
    listRule: ADMIN_CHECK,
    viewRule: ADMIN_CHECK,
    createRule: '',
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  suggestions: {
    listRule: ADMIN_CHECK,
    viewRule: ADMIN_CHECK,
    createRule: '',
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  notifications: {
    listRule: '',
    viewRule: '',
    createRule: ADMIN_CHECK,
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  content_reports: {
    listRule: ADMIN_CHECK,
    viewRule: ADMIN_CHECK,
    createRule: '@request.auth.id != ""',
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  admins_list: {
    listRule: ADMIN_CHECK,
    viewRule: ADMIN_CHECK,
    createRule: null,
    updateRule: null,
    deleteRule: null,
  },
  publisher_users: {
    listRule: '',
    viewRule: '',
    createRule: ADMIN_CHECK,
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  draft_stickers: {
    listRule: ADMIN_CHECK,
    viewRule: ADMIN_CHECK,
    createRule: ADMIN_CHECK,
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  app_settings: {
    listRule: '',
    viewRule: '',
    createRule: ADMIN_CHECK,
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  user_submissions: {
    listRule: `@request.auth.id != "" && (user_id = @request.auth.id || ${ADMIN_CHECK})`,
    viewRule: `@request.auth.id != "" && (user_id = @request.auth.id || ${ADMIN_CHECK})`,
    createRule: '@request.auth.id != ""',
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  user_profiles: {
    listRule: '',
    viewRule: '',
    createRule: '@request.auth.id != ""',
    updateRule: `@request.auth.id != "" && (user_id = @request.auth.id || ${ADMIN_CHECK})`,
    deleteRule: ADMIN_CHECK,
  },
  purchased_packs: {
    listRule: '@request.auth.id != ""',
    viewRule: '@request.auth.id != ""',
    createRule: '@request.auth.id != ""',
    updateRule: ADMIN_CHECK,
    deleteRule: ADMIN_CHECK,
  },
  users: {
    listRule: `@request.auth.id = id || ${ADMIN_CHECK}`,
    viewRule: `@request.auth.id = id || ${ADMIN_CHECK}`,
    createRule: '',
    updateRule: `@request.auth.id = id || ${ADMIN_CHECK}`,
    deleteRule: ADMIN_CHECK,
  },
};

async function authenticate() {
  const response = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  if (!response.ok) throw new Error(`Auth failed: ${response.status} ${await response.text()}`);
  return (await response.json()).token;
}

async function patchCollection(name, token) {
  const response = await fetch(`${PB_URL}/api/collections/${name}`, {
    method: 'PATCH',
    headers: { Authorization: token, 'Content-Type': 'application/json' },
    body: JSON.stringify(rules[name]),
  });
  if (!response.ok) throw new Error(`${name}: ${response.status} ${await response.text()}`);
  console.log(`OK: ${name}`);
}

async function main() {
  const token = await authenticate();
  let ok = 0;
  for (const name of Object.keys(rules)) {
    try {
      await patchCollection(name, token);
      ok += 1;
    } catch (error) {
      console.error(`FAIL: ${error.message}`);
    }
  }
  console.log(`Done: ${ok}/${Object.keys(rules).length} collections updated`);
  if (ok !== Object.keys(rules).length) process.exit(1);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});