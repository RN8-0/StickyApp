/**
 * Bu script tüm mevcut çıkartma paketlerini günceller:
 * 1. fake_download_base ekler (3000-10000 arası rastgele)
 * 2. Premium paketlere doğru fiyatları yazar (4,99 TL / $0.99 / €0.99)
 *
 * Kullanım: node scripts/update_firestore_packs.js
 */

const admin = require('firebase-admin');
const path = require('path');

// Firebase Admin SDK'yı başlat (service account kullan)
const serviceAccountPath = path.join(__dirname, '..', 'functions', 'serviceAccountKey.json');

try {
    const serviceAccount = require(serviceAccountPath);
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
} catch (e) {
    console.log('Service account bulunamadı, varsayılan credentials kullanılıyor...');
    admin.initializeApp();
}

const db = admin.firestore();

async function updateAllPacks() {
    console.log('Tüm paketler güncelleniyor...\n');

    const collections = ['stickers', 'premium_stickers'];
    let totalUpdated = 0;

    for (const collectionName of collections) {
        console.log(`\n📦 ${collectionName} koleksiyonu işleniyor...`);

        const snapshot = await db.collection(collectionName).get();
        console.log(`   ${snapshot.size} paket bulundu`);

        for (const doc of snapshot.docs) {
            const data = doc.data();
            const updates = {};

            // 1. fake_download_base ekle (yoksa veya 0 ise)
            if (!data.fake_download_base || data.fake_download_base === 0) {
                updates.fake_download_base = Math.floor(Math.random() * 7001) + 3000; // 3000-10000
            }

            // 2. Premium paketlere doğru fiyatları yaz
            if (collectionName === 'premium_stickers') {
                updates.price_try = '4,99 TL';
                updates.price_usd = '$0.99';
                updates.price_eur = '€0.99';
            }

            // Güncelleme varsa uygula
            if (Object.keys(updates).length > 0) {
                await doc.ref.update(updates);
                console.log(`   ✅ ${doc.id}: fake_base=${updates.fake_download_base || data.fake_download_base}, price=${updates.price_try || '-'}`);
                totalUpdated++;
            } else {
                console.log(`   ⏭️  ${doc.id}: Zaten güncel`);
            }
        }
    }

    console.log(`\n🎉 Toplam ${totalUpdated} paket güncellendi!`);
    process.exit(0);
}

updateAllPacks().catch(err => {
    console.error('Hata:', err);
    process.exit(1);
});
