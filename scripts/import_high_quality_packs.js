const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

const colors = {
    reset: "\x1b[0m",
    bright: "\x1b[1m",
    green: "\x1b[32m",
    red: "\x1b[31m",
    yellow: "\x1b[33m",
    cyan: "\x1b[36m"
};

const IMPORT_DIR = path.join(__dirname, '..', 'stickers_convert', 'output');
const SERVICE_ACCOUNT_PATH = path.join(__dirname, '..', 'functions', 'serviceAccountKey.json');
const STORAGE_BUCKET_NAME = 'sticky-dcd20.firebasestorage.app';

console.log(`${colors.bright}${colors.cyan}=== Sticly High Quality Pack Importer ===${colors.reset}\n`);

if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    console.error(`${colors.red}HATA: Service account dosyası bulunamadı!${colors.reset}`);
    process.exit(1);
}

if (!fs.existsSync(IMPORT_DIR)) {
    console.error(`${colors.red}HATA: '${IMPORT_DIR}' klasörü bulunamadı.${colors.reset}`);
    process.exit(1);
}

try {
    const serviceAccount = require(SERVICE_ACCOUNT_PATH);
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        storageBucket: STORAGE_BUCKET_NAME
    });
    console.log(`${colors.green}Firebase bağlantısı başarılı.${colors.reset}`);
} catch (e) {
    console.error(`${colors.red}Firebase başlatma hatası:${colors.reset}`, e);
    process.exit(1);
}

const db = admin.firestore();
const bucket = admin.storage().bucket();

function getPackId(name) {
    return name.toLowerCase()
        .replace(/ğ/g, 'g').replace(/ü/g, 'u').replace(/ş/g, 's')
        .replace(/ı/g, 'i').replace(/ö/g, 'o').replace(/ç/g, 'c')
        .replace(/[^a-z0-9]/g, '_')
        .replace(/_+/g, '_');
}

async function uploadFile(localPath, destinationPath) {
    try {
        await bucket.upload(localPath, {
            destination: destinationPath,
            metadata: {
                cacheControl: 'public, max-age=31536000',
            }
        });
        
        const encodedPath = encodeURIComponent(destinationPath);
        return `https://firebasestorage.googleapis.com/v0/b/${STORAGE_BUCKET_NAME}/o/${encodedPath}?alt=media`;
    } catch (e) {
        throw new Error(`Dosya yükleme hatası (${path.basename(localPath)}): ${e.message}`);
    }
}

async function processPacks() {
    const packFolders = fs.readdirSync(IMPORT_DIR).filter(f => fs.statSync(path.join(IMPORT_DIR, f)).isDirectory());

    if (packFolders.length === 0) {
        console.log(`${colors.yellow}İçeri aktarılacak paket bulunamadı.${colors.reset}`);
        return;
    }

    console.log(`${colors.bright}Toplam ${packFolders.length} paket bulundu. İşlem başlıyor...${colors.reset}\n`);

    for (const folderName of packFolders) {
        const packPath = path.join(IMPORT_DIR, folderName);
        console.log(`${colors.cyan}📂 Paket İşleniyor: ${folderName}${colors.reset}`);

        let metadata = {
            name: folderName.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()),
            publisher: 'Sticly Official',
            publisher_email: 'contact@arain.digital',
            category: 'entertainment', 
            is_premium: false,
            is_animated: true, 
            is_active: true
        };

        const packId = getPackId(metadata.name);
        
        const files = fs.readdirSync(packPath);
        const trayFile = files.find(f => f.startsWith('tray'));
        const stickerFiles = files.filter(f => 
            (f.endsWith('.webp') || f.endsWith('.png') || f.endsWith('.gif')) && 
            !f.startsWith('tray')
        ).sort();

        if (stickerFiles.length < 3) {
            console.log(`   ${colors.yellow}⚠️  Yetersiz sticker (${stickerFiles.length}/3). Atlanıyor.${colors.reset}`);
            continue;
        }

        console.log(`   📸 ${stickerFiles.length} sticker bulundu.`);

        const stickerData = [];
        let trayUrl = '';
        let trayFileName = '';

        if (trayFile) {
            const ext = path.extname(trayFile);
            trayFileName = `tray_${Date.now()}${ext}`;
            const dest = `stickers/${packId}/${trayFileName}`;
            console.log(`   ⬆️  Kapak resmi yükleniyor...`);
            trayUrl = await uploadFile(path.join(packPath, trayFile), dest);
        } else {
            console.log(`   ${colors.yellow}⚠️  Kapak resmi (tray) bulunamadı. İlk sticker kullanılacak.${colors.reset}`);
        }

        for (let i = 0; i < stickerFiles.length; i++) {
            const file = stickerFiles[i];
            const ext = path.extname(file);
            const fileName = `${Date.now()}_${i}${ext}`;
            const dest = `stickers/${packId}/${fileName}`;
            
            process.stdout.write('.'); 

            const url = await uploadFile(path.join(packPath, file), dest);
            
            stickerData.push({
                image_file: fileName,
                url: url,
                emojis: ["😀"]
            });

            if (i === 0 && !trayUrl) {
                trayUrl = url;
                trayFileName = fileName;
            }
        }
        process.stdout.write('\n');

        const collectionName = metadata.is_premium ? 'premium_stickers' : 'stickers';
        
        const firestoreData = {
            name: metadata.name,
            publisher: metadata.publisher,
            publisher_email: metadata.publisher_email,
            category: metadata.category,
            is_premium: metadata.is_premium,
            is_animated: metadata.is_animated,
            is_active: metadata.is_active,
            download_count: 0,
            fake_download_base: Math.floor(Math.random() * 7001) + 3000,
            view_count: 0,
            favorite_count: 0,
            sticker_count: stickerData.length,
            image_data_version: "1",
            stickers: stickerData,
            tray_url: trayUrl,
            tray_image_file: trayFileName,
            created_at: admin.firestore.FieldValue.serverTimestamp()
        };

        if (metadata.is_premium) {
            firestoreData.price_try = "4,99 TL";
            firestoreData.price_usd = "$0.99";
            firestoreData.price_eur = "€0.99";
        }

        const langs = ['tr', 'zh', 'es', 'ar', 'hi', 'pt'];
        langs.forEach(lang => {
            firestoreData[`name_${lang}`] = metadata.name;
        });

        await db.collection(collectionName).doc(packId).set(firestoreData);

        console.log(`   ✅ ${colors.green}Paket başarıyla oluşturuldu: ${packId}${colors.reset}\n`);
    }

    console.log(`${colors.bright}${colors.green}🎉 Tüm işlemler tamamlandı!${colors.reset}`);
}

processPacks().catch(err => {
    console.error(`\n${colors.red}KRİTİK HATA:${colors.reset}`, err);
});
