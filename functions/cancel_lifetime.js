const admin = require('firebase-admin');

// Application Default Credentials kullan (firebase login ile)
admin.initializeApp({
    projectId: 'sticky-dcd20'
});

const db = admin.firestore();

async function cancelLifetimeSubscriptions() {
    try {
        const usersRef = db.collection('users');

        // premium_type = "lifetime" olan kullanıcıları bul
        const lifetimeUsers = await usersRef.where('premium_type', '==', 'lifetime').get();

        if (lifetimeUsers.empty) {
            console.log('No lifetime subscriptions found');

            // is_premium = true olanları da kontrol et
            console.log('\nChecking all premium users...');
            const premiumUsers = await usersRef.where('is_premium', '==', true).get();
            premiumUsers.forEach(doc => {
                console.log(`User: ${doc.id}, Data:`, doc.data());
            });
            return;
        }

        console.log(`Found ${lifetimeUsers.size} lifetime subscription(s)`);

        for (const doc of lifetimeUsers.docs) {
            console.log(`Cancelling lifetime subscription for user: ${doc.id}`);
            console.log('Current data:', doc.data());

            await doc.ref.update({
                is_premium: false,
                premium_type: 'none',
                premium_expiry: 0,
                cancelled_at: admin.firestore.FieldValue.serverTimestamp(),
                cancelled_reason: 'lifetime_subscription_removed'
            });

            console.log(`✓ Cancelled subscription for user: ${doc.id}`);
        }

        console.log('\nDone!');
    } catch (error) {
        console.error('Error:', error);
    } finally {
        process.exit(0);
    }
}

cancelLifetimeSubscriptions();
