const { Router } = require('express');
const router = Router();

function db() {
  return require('firebase-admin').firestore();
}

// GET /api/users - fetch all users from Firestore via Admin SDK (no auth rules)
router.get('/', async (req, res) => {
  try {
    const snapshot = await db().collection('users').get();
    const users = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
    users.sort((a, b) => {
      const aT = a.created_at?._seconds || a.created_at || 0;
      const bT = b.created_at?._seconds || b.created_at || 0;
      return bT - aT;
    });
    res.json({ users });
  } catch (err) {
    console.error('[Users GET]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// PATCH /api/users/:id - update user subscription
router.patch('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const { is_premium, premium_type, premium_expiry, subscription_source, historyItem } = req.body;
    const admin = require('firebase-admin');
    const FieldValue = admin.firestore.FieldValue;

    const update = {
      is_premium,
      premium_type,
      premium_expiry,
      subscription_source,
      last_sync: FieldValue.serverTimestamp(),
    };
    if (historyItem) update.subscription_history = FieldValue.arrayUnion(historyItem);

    await db().collection('users').doc(id).update(update);
    res.json({ success: true });
  } catch (err) {
    console.error('[Users PATCH]', err.message);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
