// Delete all Firebase Auth users
// Usage: node scripts/delete_fb_auth_users.mjs
import { readFileSync } from 'fs';
import { execSync } from 'child_process';

const data = JSON.parse(readFileSync('C:/Users/RN8/Desktop/StickyApp-main/fb_auth_check.json', 'utf8'));
const users = data.users || [];
console.log(`Found ${users.length} Firebase Auth users to delete...`);

// Delete in batches via firebase CLI - but easiest is to use admin SDK or gcloud
// Since we have firebase CLI, let's use it to batch delete
let deleted = 0;
for (const user of users) {
  try {
    execSync(`firebase auth:users:delete "${user.localId}" --project sticky-dcd20 --force 2>&1`, { encoding: 'utf8', stdio: 'pipe' });
    deleted++;
    if (deleted % 10 === 0) process.stdout.write(`Deleted ${deleted}/${users.length}\r`);
  } catch (e) {
    console.log(`SKIP ${user.localId}: ${e.message?.slice(0, 80)}`);
  }
}
console.log(`\nDone: ${deleted} users deleted.`);
