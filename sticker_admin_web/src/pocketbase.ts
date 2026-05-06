import PocketBase from 'pocketbase';

// PocketBase configuration
const RUNTIME_ORIGIN = typeof window !== 'undefined' ? window.location.origin : 'http://46.225.95.201:8085';
const APP_URL = import.meta.env.VITE_APP_URL || RUNTIME_ORIGIN;
const PB_PUBLIC_URL = import.meta.env.VITE_PB_PUBLIC_URL || 'https://sh3xlf9j7symlj3otlw6s8rx.46.225.95.201.sslip.io';
// Always point directly to PocketBase - do NOT fall back to the admin panel origin
const PB_URL = import.meta.env.VITE_PB_URL || PB_PUBLIC_URL;
const WORKER_URL = import.meta.env.VITE_WORKER_URL || 'https://sticky-worker.46.225.95.201.sslip.io';
const IMAGE_PROXY_URL = import.meta.env.VITE_IMAGE_PROXY_URL || 'https://sticky-images.46.225.95.201.sslip.io';
// OAuth redirect must go through PocketBase (not the admin panel URL)
const OAUTH_REDIRECT_URL = import.meta.env.VITE_OAUTH_REDIRECT_URL || `${PB_PUBLIC_URL}/api/oauth2-redirect`;

export const pb = new PocketBase(PB_URL);
pb.autoCancellation(false);

function buildOAuthUrl(url: string): string {
  const authUrl = new URL(url);
  authUrl.searchParams.set('redirect_uri', OAUTH_REDIRECT_URL);
  return authUrl.toString();
}

function openOAuthWindow(url: string): void {
  const features = 'width=1024,height=720,menubar=no,toolbar=no,location=yes,resizable=yes,scrollbars=yes,status=yes';
  const popup = window.open(url, 'sticky-admin-oauth', features);

  if (!popup) {
    window.location.assign(url);
    return;
  }

  popup.focus();
}

async function signInWithOAuthProvider(provider: string) {
  return await pb.collection('users').authWithOAuth2({
    provider,
    urlCallback: (url) => {
      openOAuthWindow(buildOAuthUrl(url));
    },
  });
}

// Auth helpers
export function isLoggedIn(): boolean {
  return pb.authStore.isValid;
}

export function currentUser() {
  return pb.authStore.record;
}

export async function signInWithGoogle() {
  return await signInWithOAuthProvider('google');
}

export async function signInWithGitHub() {
  return await signInWithOAuthProvider('github');
}

export async function signInWithEmail(email: string, password: string) {
  return await pb.collection('users').authWithPassword(email, password);
}

export async function logout() {
  pb.authStore.clear();
}

export function normalizePublicAssetUrl(url?: string | null): string {
  if (!url) return '';
  return url
    .replace(/^http:\/\/46\.225\.95\.201:8086/, IMAGE_PROXY_URL)
    .replace(/^https:\/\/46\.225\.95\.201:8086/, IMAGE_PROXY_URL);
}

// Check if current user is admin
export async function isAdmin(): Promise<boolean> {
  const user = currentUser();
  if (!user) return false;
  const email = String(user.email || '').replace(/[\\'"\n\r\t]/g, '');
  if (!email) return false;
  // Try admins_list collection first
  try {
    const admins = await pb.collection('admins_list').getFullList({ filter: `email='${email}'` });
    if (admins.length > 0) return true;
  } catch { /* collection may not exist */ }
  // Fallback: check admins collection
  try {
    const admins = await pb.collection('admins').getFullList({ filter: `id='${email}'` });
    if (admins.length > 0) return true;
  } catch { /* collection may not exist */ }
  return false;
}

// File URL helper
export function getFileUrl(collectionId: string, recordId: string, filename: string): string {
  return `${PB_URL}/api/files/${collectionId}/${recordId}/${filename}`;
}

// Upload file to a record
export async function uploadFile(
  collectionName: string,
  recordId: string,
  fieldName: string,
  file: File | Blob,
  filename?: string
): Promise<string> {
  const formData = new FormData();
  const f = file instanceof File ? file : new File([file], filename || 'file.webp');
  const uploadFieldName = fieldName === 'images' ? `${fieldName}+` : fieldName;
  formData.append(uploadFieldName, f);

  const record = await pb.collection(collectionName).update(recordId, formData);
  const uploadedFilename = record[fieldName];
  return getFileUrl(collectionName, recordId, Array.isArray(uploadedFilename) ? uploadedFilename[uploadedFilename.length - 1] : uploadedFilename);
}

// Upload a standalone file (creates a temp record, uploads file, returns URL)
export async function uploadStandaloneFile(
  collectionName: string,
  fieldName: string,
  file: File | Blob,
  additionalData?: Record<string, any>
): Promise<{ recordId: string; url: string }> {
  const formData = new FormData();
  const f = file instanceof File ? file : new File([file], 'file.webp', { type: 'image/webp' });
  formData.append(fieldName, f);
  if (additionalData) {
    for (const [k, v] of Object.entries(additionalData)) {
      formData.append(k, typeof v === 'string' ? v : JSON.stringify(v));
    }
  }

  const record = await pb.collection(collectionName).create(formData);
  const uploadedFilename = record[fieldName];
  const fname = Array.isArray(uploadedFilename) ? uploadedFilename[0] : uploadedFilename;
  return {
    recordId: record.id,
    url: getFileUrl(collectionName, record.id, fname)
  };
}

// Worker URL for proxy services
export { APP_URL, IMAGE_PROXY_URL, WORKER_URL, PB_URL, PB_PUBLIC_URL, OAUTH_REDIRECT_URL };
