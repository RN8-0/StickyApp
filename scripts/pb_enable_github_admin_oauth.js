const PB_URL = process.env.PB_URL || 'http://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
const ADMIN_EMAIL = process.env.PB_ADMIN_EMAIL || 'admin@sticky.app';
const ADMIN_PASSWORD = process.env.PB_ADMIN_PASSWORD;
const GITHUB_CLIENT_ID = process.env.GITHUB_CLIENT_ID;
const GITHUB_CLIENT_SECRET = process.env.GITHUB_CLIENT_SECRET;
const GITHUB_ADMIN_EMAIL = process.env.GITHUB_ADMIN_EMAIL || '';
const ADMIN_RULE = "@request.auth.id != '' && @collection.admins_list.email ?= @request.auth.email";

if (!GITHUB_CLIENT_ID || !GITHUB_CLIENT_SECRET) {
  throw new Error('GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET are required.');
}

if (!ADMIN_PASSWORD) {
  throw new Error('PB_ADMIN_PASSWORD is required.');
}

let token = '';

async function auth() {
  const response = await fetch(`${PB_URL}/api/collections/_superusers/auth-with-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identity: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });

  if (!response.ok) {
    throw new Error(`PocketBase auth failed: ${response.status} ${await response.text()}`);
  }

  token = (await response.json()).token;
}

async function api(method, path, data) {
  const response = await fetch(`${PB_URL}${path}`, {
    method,
    headers: {
      Authorization: token,
      'Content-Type': 'application/json',
    },
    body: data ? JSON.stringify(data) : undefined,
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${method} ${path} failed: ${response.status} ${text}`);
  }

  return text ? JSON.parse(text) : {};
}

async function ensureGithubProvider() {
  const users = await api('GET', '/api/collections/users');
  const providers = Array.isArray(users.oauth2?.providers) ? [...users.oauth2.providers] : [];
  const githubProvider = {
    name: 'github',
    state: true,
    clientId: GITHUB_CLIENT_ID,
    clientSecret: GITHUB_CLIENT_SECRET,
  };

  const nextProviders = providers.filter((provider) => provider?.name !== 'github');
  nextProviders.push(githubProvider);

  await api('PATCH', '/api/collections/users', {
    oauth2: {
      enabled: true,
      mappedFields: users.oauth2?.mappedFields || {
        id: '',
        name: 'name',
        username: '',
        avatarURL: 'avatar',
      },
      providers: nextProviders,
    },
  });
}

async function patchCollectionRules() {
  const updates = {
    stickers: { createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    premium_stickers: { createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    draft_stickers: { listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    messages: { listRule: ADMIN_RULE, viewRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    suggestions: { listRule: ADMIN_RULE, viewRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    notifications: { listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    admins_list: { listRule: ADMIN_RULE, viewRule: ADMIN_RULE, createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    publisher_users: { createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    app_settings: { createRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
    user_submissions: { listRule: ADMIN_RULE, viewRule: ADMIN_RULE, updateRule: ADMIN_RULE, deleteRule: ADMIN_RULE },
  };

  for (const [name, patch] of Object.entries(updates)) {
    await api('PATCH', `/api/collections/${name}`, patch);
  }
}

async function ensureAdminEmail() {
  if (!GITHUB_ADMIN_EMAIL) {
    return;
  }

  const encodedFilter = encodeURIComponent(`email="${GITHUB_ADMIN_EMAIL}"`);
  const existing = await api('GET', `/api/collections/admins_list/records?perPage=1&filter=${encodedFilter}`);
  if ((existing.items || []).length > 0) {
    return;
  }

  await api('POST', '/api/collections/admins_list/records', { email: GITHUB_ADMIN_EMAIL });
}

async function main() {
  await auth();
  await ensureGithubProvider();
  await patchCollectionRules();
  await ensureAdminEmail();
  console.log('GitHub admin OAuth is configured.');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
