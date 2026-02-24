#!/usr/bin/env node
/**
 * Sticky - Play Store Screenshot Generator (Fixed Version)
 */

const puppeteer = require('puppeteer');
const path = require('path');
const fs = require('fs');

const LANGUAGES = ['en', 'tr', 'de', 'ar', 'id', 'fil', 'fr', 'hi', 'ja', 'ko', 'pt', 'ru', 'th', 'vi', 'zh', 'es', 'it'];
const OUTPUT_DIR = path.join(__dirname, 'output');

// Load images as base64
function loadImageBase64(filename) {
    const filepath = path.join(__dirname, filename);
    if (fs.existsSync(filepath)) {
        const data = fs.readFileSync(filepath);
        return `data:image/png;base64,${data.toString('base64')}`;
    }
    return '';
}

// Pre-load all images
const imageCache = {};
['main_screen.png', 'detail_screen.png', 'sticker_maker.png', 'favorites.png', 'main_screen2.png', 'check.png'].forEach(img => {
    imageCache[img] = loadImageBase64(img);
});

// Parse args
const args = process.argv.slice(2);
let targetLang = null;
args.forEach(arg => {
    if (arg.startsWith('--lang=')) {
        targetLang = arg.split('=')[1];
    }
});

const languagesToProcess = targetLang ? [targetLang] : LANGUAGES;

// Screenshot data
const screenshots = [
    { gradient: 'linear-gradient(180deg, #667eea 0%, #764ba2 100%)', image: 'main_screen.png' },
    { gradient: 'linear-gradient(180deg, #f093fb 0%, #f5576c 100%)', image: 'detail_screen.png' },
    { gradient: 'linear-gradient(180deg, #4facfe 0%, #00f2fe 100%)', image: 'sticker_maker.png' },
    { gradient: 'linear-gradient(180deg, #43e97b 0%, #38f9d7 100%)', image: 'favorites.png' },
    { gradient: 'linear-gradient(180deg, #fa709a 0%, #fee140 100%)', image: 'main_screen2.png' },
    { gradient: 'linear-gradient(180deg, #30cfd0 0%, #330867 100%)', image: 'check.png' }
];

const translations = {
    en: {
        rtl: false,
        screens: [
            { badge: 'AI POWERED', title: 'Create Amazing<br>WhatsApp Stickers', subtitle: 'From your photos in seconds!' },
            { badge: '1000+ PACKS', title: 'Free Sticker<br>Packs', subtitle: 'New packs added every week!' },
            { badge: 'SMART AI', title: 'AI Background<br>Remover', subtitle: 'Perfect cutouts with one tap' },
            { badge: 'ANIMATED', title: 'GIF to Sticker<br>Converter', subtitle: 'Turn any GIF into WhatsApp sticker' },
            { badge: 'EASY', title: 'Simple 3-Step<br>Process', subtitle: 'Select → Edit → Add to WhatsApp' },
            { badge: 'PREMIUM', title: 'Unlock All<br>Features', subtitle: 'Ad-free experience & unlimited AI' }
        ],
        feature: { title: 'Sticky', tagline: 'The Ultimate<br>Sticker Maker', badge: '1000+ Free Packs' }
    },
    tr: {
        rtl: false,
        screens: [
            { badge: 'AI DESTEKLI', title: 'Harika WhatsApp<br>Stickerlar Olustur', subtitle: 'Fotograflarindan saniyeler icinde!' },
            { badge: '1000+ PAKET', title: 'Ucretsiz Sticker<br>Paketleri', subtitle: 'Her hafta yeni paketler ekleniyor!' },
            { badge: 'AKILLI AI', title: 'AI Arka Plan<br>Silici', subtitle: 'Tek dokunusla mukemmel kesim' },
            { badge: 'ANIMASYONLU', title: 'GIFten Sticker<br>Donusturucu', subtitle: 'Herhangi bir GIFi stickera cevir' },
            { badge: 'KOLAY', title: 'Basit 3 Adimli<br>Islem', subtitle: 'Sec → Duzenle → WhatsAppa Ekle' },
            { badge: 'PREMIUM', title: 'Tum Ozelliklerin<br>Kilidini Ac', subtitle: 'Reklamsiz deneyim ve sinirsiz AI' }
        ],
        feature: { title: 'Sticky', tagline: 'En Iyi Sticker<br>Yapici', badge: '1000+ Ucretsiz Paket' }
    },
    de: {
        rtl: false,
        screens: [
            { badge: 'KI-POWERED', title: 'Erstelle Tolle<br>WhatsApp Sticker', subtitle: 'Aus deinen Fotos in Sekunden!' },
            { badge: '1000+ PAKETE', title: 'Kostenlose<br>Sticker-Pakete', subtitle: 'Wochentlich neue Pakete!' },
            { badge: 'SMART KI', title: 'KI Hintergrund-<br>Entferner', subtitle: 'Perfekte Ausschnitte mit einem Tipp' },
            { badge: 'ANIMIERT', title: 'GIF zu Sticker<br>Konverter', subtitle: 'Jedes GIF in Sticker umwandeln' },
            { badge: 'EINFACH', title: 'Einfacher 3-Schritt<br>Prozess', subtitle: 'Wahlen → Bearbeiten → Hinzufugen' },
            { badge: 'PREMIUM', title: 'Alle Funktionen<br>Freischalten', subtitle: 'Werbefrei und unbegrenzte KI' }
        ],
        feature: { title: 'Sticky', tagline: 'Der Beste<br>Sticker Maker', badge: '1000+ Kostenlose Pakete' }
    },
    ar: {
        rtl: true,
        screens: [
            { badge: 'ذكاء اصطناعي', title: 'اصنع ملصقات<br>واتساب رائعة', subtitle: 'من صورك في ثواني!' },
            { badge: '+1000 حزمة', title: 'حزم ملصقات<br>مجانية', subtitle: 'حزم جديدة كل أسبوع!' },
            { badge: 'AI ذكي', title: 'مزيل الخلفية<br>بالذكاء الاصطناعي', subtitle: 'قص مثالي بلمسة واحدة' },
            { badge: 'متحرك', title: 'محول GIF<br>إلى ملصق', subtitle: 'حول أي GIF إلى ملصق' },
            { badge: 'سهل', title: 'عملية بسيطة<br>من 3 خطوات', subtitle: 'اختر ← عدل ← أضف لواتساب' },
            { badge: 'بريميوم', title: 'افتح جميع<br>المميزات', subtitle: 'بدون إعلانات و AI غير محدود' }
        ],
        feature: { title: 'Sticky', tagline: 'أفضل صانع<br>ملصقات', badge: '+1000 حزمة مجانية' }
    },
    id: {
        rtl: false,
        screens: [
            { badge: 'AI POWERED', title: 'Buat Stiker<br>WhatsApp Keren', subtitle: 'Dari foto kamu dalam detik!' },
            { badge: '1000+ PAKET', title: 'Paket Stiker<br>Gratis', subtitle: 'Paket baru setiap minggu!' },
            { badge: 'SMART AI', title: 'AI Penghapus<br>Background', subtitle: 'Potongan sempurna satu ketukan' },
            { badge: 'ANIMASI', title: 'Konverter GIF<br>ke Stiker', subtitle: 'Ubah GIF apapun jadi stiker' },
            { badge: 'MUDAH', title: 'Proses Simpel<br>3 Langkah', subtitle: 'Pilih → Edit → Tambah ke WA' },
            { badge: 'PREMIUM', title: 'Buka Semua<br>Fitur', subtitle: 'Tanpa iklan dan AI unlimited' }
        ],
        feature: { title: 'Sticky', tagline: 'Pembuat Stiker<br>Terbaik', badge: '1000+ Paket Gratis' }
    },
    fil: {
        rtl: false,
        screens: [
            { badge: 'AI POWERED', title: 'Gumawa ng Amazing<br>WhatsApp Stickers', subtitle: 'Mula sa photos mo in seconds!' },
            { badge: '1000+ PACKS', title: 'Libreng Sticker<br>Packs', subtitle: 'Bagong packs every week!' },
            { badge: 'SMART AI', title: 'AI Background<br>Remover', subtitle: 'Perfect cutouts sa isang tap' },
            { badge: 'ANIMATED', title: 'GIF to Sticker<br>Converter', subtitle: 'I-convert ang kahit anong GIF' },
            { badge: 'EASY', title: 'Simple 3-Step<br>Process', subtitle: 'Pumili → I-edit → Idagdag sa WA' },
            { badge: 'PREMIUM', title: 'Unlock Lahat ng<br>Features', subtitle: 'Walang ads at unlimited AI' }
        ],
        feature: { title: 'Sticky', tagline: 'Ang Pinakamahusay<br>na Sticker Maker', badge: '1000+ Libreng Packs' }
    },
    fr: {
        rtl: false,
        screens: [
            { badge: 'IA', title: 'Creez des Stickers<br>WhatsApp Incroyables', subtitle: 'A partir de vos photos en secondes!' },
            { badge: '1000+ PACKS', title: 'Packs de Stickers<br>Gratuits', subtitle: 'Nouveaux packs chaque semaine!' },
            { badge: 'IA SMART', title: 'Suppression du<br>Fond par IA', subtitle: 'Decoupes parfaites en un clic' },
            { badge: 'ANIME', title: 'Convertisseur<br>GIF en Sticker', subtitle: 'Convertissez nimporte quel GIF' },
            { badge: 'FACILE', title: 'Processus Simple<br>en 3 Etapes', subtitle: 'Selectionnez → Editez → Ajoutez' },
            { badge: 'PREMIUM', title: 'Debloquez Toutes<br>les Fonctionnalites', subtitle: 'Sans pub et IA illimitee' }
        ],
        feature: { title: 'Sticky', tagline: 'Le Meilleur<br>Createur de Stickers', badge: '1000+ Packs Gratuits' }
    },
    hi: {
        rtl: false,
        screens: [
            { badge: 'AI POWERED', title: 'शानदार WhatsApp<br>स्टिकर बनाएं', subtitle: 'अपनी फोटो से सेकंडों में!' },
            { badge: '1000+ पैक', title: 'फ्री स्टिकर<br>पैक', subtitle: 'हर हफ्ते नए पैक!' },
            { badge: 'स्मार्ट AI', title: 'AI बैकग्राउंड<br>रिमूवर', subtitle: 'एक टैप में परफेक्ट कटआउट' },
            { badge: 'एनिमेटेड', title: 'GIF से स्टिकर<br>कन्वर्टर', subtitle: 'किसी भी GIF को स्टिकर बनाएं' },
            { badge: 'आसान', title: 'सिंपल 3-स्टेप<br>प्रोसेस', subtitle: 'चुनें → एडिट करें → WA में जोड़ें' },
            { badge: 'प्रीमियम', title: 'सभी फीचर्स<br>अनलॉक करें', subtitle: 'एड-फ्री और अनलिमिटेड AI' }
        ],
        feature: { title: 'Sticky', tagline: 'बेस्ट स्टिकर<br>मेकर', badge: '1000+ फ्री पैक' }
    },
    ja: {
        rtl: false,
        screens: [
            { badge: 'AI搭載', title: '素敵なWhatsApp<br>スタンプを作ろう', subtitle: '写真から数秒で!' },
            { badge: '1000+パック', title: '無料スタンプ<br>パック', subtitle: '毎週新しいパック!' },
            { badge: 'スマートAI', title: 'AI背景<br>除去', subtitle: 'ワンタップで完璧な切り抜き' },
            { badge: 'アニメ', title: 'GIFからスタンプ<br>コンバーター', subtitle: 'どんなGIFもスタンプに' },
            { badge: '簡単', title: 'シンプル3ステップ<br>プロセス', subtitle: '選択 → 編集 → 追加' },
            { badge: 'プレミアム', title: '全機能を<br>アンロック', subtitle: '広告なし＆無制限AI' }
        ],
        feature: { title: 'Sticky', tagline: '最高のスタンプ<br>メーカー', badge: '1000+無料パック' }
    },
    ko: {
        rtl: false,
        screens: [
            { badge: 'AI 탑재', title: '멋진 WhatsApp<br>스티커 만들기', subtitle: '사진에서 몇 초 만에!' },
            { badge: '1000+ 팩', title: '무료 스티커<br>팩', subtitle: '매주 새로운 팩!' },
            { badge: '스마트 AI', title: 'AI 배경<br>제거', subtitle: '한 번의 탭으로 완벽한 컷아웃' },
            { badge: '애니메이션', title: 'GIF를 스티커로<br>변환', subtitle: '모든 GIF를 스티커로 변환' },
            { badge: '쉬움', title: '간단한 3단계<br>프로세스', subtitle: '선택 → 편집 → 추가' },
            { badge: '프리미엄', title: '모든 기능<br>잠금 해제', subtitle: '광고 없음 & 무제한 AI' }
        ],
        feature: { title: 'Sticky', tagline: '최고의 스티커<br>메이커', badge: '1000+ 무료 팩' }
    },
    pt: {
        rtl: false,
        screens: [
            { badge: 'IA', title: 'Crie Figurinhas<br>Incriveis', subtitle: 'Das suas fotos em segundos!' },
            { badge: '1000+ PACOTES', title: 'Pacotes de<br>Figurinhas Gratis', subtitle: 'Novos pacotes toda semana!' },
            { badge: 'IA SMART', title: 'Removedor de<br>Fundo com IA', subtitle: 'Recortes perfeitos com um toque' },
            { badge: 'ANIMADO', title: 'Conversor GIF<br>para Figurinha', subtitle: 'Converta qualquer GIF' },
            { badge: 'FACIL', title: 'Processo Simples<br>em 3 Passos', subtitle: 'Selecione → Edite → Adicione' },
            { badge: 'PREMIUM', title: 'Desbloqueie Todos<br>os Recursos', subtitle: 'Sem anuncios e IA ilimitada' }
        ],
        feature: { title: 'Sticky', tagline: 'O Melhor Criador<br>de Figurinhas', badge: '1000+ Pacotes Gratis' }
    },
    ru: {
        rtl: false,
        screens: [
            { badge: 'ИИ', title: 'Создавай Крутые<br>Стикеры WhatsApp', subtitle: 'Из твоих фото за секунды!' },
            { badge: '1000+ НАБОРОВ', title: 'Бесплатные<br>Наборы Стикеров', subtitle: 'Новые наборы каждую неделю!' },
            { badge: 'УМНЫЙ ИИ', title: 'ИИ Удаление<br>Фона', subtitle: 'Идеальная обрезка одним касанием' },
            { badge: 'АНИМАЦИЯ', title: 'Конвертер GIF<br>в Стикер', subtitle: 'Преврати любой GIF в стикер' },
            { badge: 'ЛЕГКО', title: 'Простой Процесс<br>в 3 Шага', subtitle: 'Выбери → Редактируй → Добавь' },
            { badge: 'ПРЕМИУМ', title: 'Разблокируй Все<br>Функции', subtitle: 'Без рекламы и безлимитный ИИ' }
        ],
        feature: { title: 'Sticky', tagline: 'Лучший Создатель<br>Стикеров', badge: '1000+ Бесплатных Наборов' }
    },
    th: {
        rtl: false,
        screens: [
            { badge: 'AI', title: 'สร้างสติกเกอร์<br>WhatsApp สุดเจ๋ง', subtitle: 'จากรูปของคุณในไม่กี่วินาที!' },
            { badge: '1000+ แพ็ค', title: 'แพ็คสติกเกอร์<br>ฟรี', subtitle: 'แพ็คใหม่ทุกสัปดาห์!' },
            { badge: 'AI ฉลาด', title: 'AI ลบ<br>พื้นหลัง', subtitle: 'ตัดสมบูรณ์แบบด้วยการแตะเดียว' },
            { badge: 'เคลื่อนไหว', title: 'ตัวแปลง GIF<br>เป็นสติกเกอร์', subtitle: 'แปลง GIF ใดๆ เป็นสติกเกอร์' },
            { badge: 'ง่าย', title: 'ขั้นตอนง่าย<br>3 ขั้นตอน', subtitle: 'เลือก → แก้ไข → เพิ่มลง WA' },
            { badge: 'พรีเมียม', title: 'ปลดล็อคทุก<br>ฟีเจอร์', subtitle: 'ไม่มีโฆษณา และ AI ไม่จำกัด' }
        ],
        feature: { title: 'Sticky', tagline: 'แอปสร้างสติกเกอร์<br>ที่ดีที่สุด', badge: '1000+ แพ็คฟรี' }
    },
    vi: {
        rtl: false,
        screens: [
            { badge: 'AI', title: 'Tao Sticker<br>WhatsApp Tuyet Voi', subtitle: 'Tu anh cua ban trong vai giay!' },
            { badge: '1000+ GOI', title: 'Goi Sticker<br>Mien Phi', subtitle: 'Goi moi moi tuan!' },
            { badge: 'AI THONG MINH', title: 'AI Xoa<br>Nen', subtitle: 'Cat hoan hao chi voi mot cham' },
            { badge: 'DONG', title: 'Chuyen Doi GIF<br>sang Sticker', subtitle: 'Chuyen doi bat ky GIF nao' },
            { badge: 'DE', title: 'Quy Trinh Don Gian<br>3 Buoc', subtitle: 'Chon → Chinh Sua → Them vao WA' },
            { badge: 'PREMIUM', title: 'Mo Khoa Tat Ca<br>Tinh Nang', subtitle: 'Khong quang cao va AI khong gioi han' }
        ],
        feature: { title: 'Sticky', tagline: 'Ung Dung Tao<br>Sticker Tot Nhat', badge: '1000+ Goi Mien Phi' }
    },
    zh: {
        rtl: false,
        screens: [
            { badge: 'AI驱动', title: '创建精彩的<br>WhatsApp贴纸', subtitle: '几秒钟内从您的照片创建!' },
            { badge: '1000+包', title: '免费贴纸<br>包', subtitle: '每周新增贴纸包!' },
            { badge: '智能AI', title: 'AI智能<br>抠图', subtitle: '一键完美剪切' },
            { badge: '动态', title: 'GIF转贴纸<br>转换器', subtitle: '将任何GIF转换为贴纸' },
            { badge: '简单', title: '简单3步<br>流程', subtitle: '选择 → 编辑 → 添加到WA' },
            { badge: '高级', title: '解锁所有<br>功能', subtitle: '无广告 和 无限AI' }
        ],
        feature: { title: 'Sticky', tagline: '最佳贴纸<br>制作器', badge: '1000+免费包' }
    },
    es: {
        rtl: false,
        screens: [
            { badge: 'IA', title: 'Crea Stickers<br>Increibles', subtitle: 'De tus fotos en segundos!' },
            { badge: '1000+ PAQUETES', title: 'Paquetes de<br>Stickers Gratis', subtitle: 'Nuevos paquetes cada semana!' },
            { badge: 'IA SMART', title: 'Eliminador de<br>Fondo con IA', subtitle: 'Recortes perfectos con un toque' },
            { badge: 'ANIMADO', title: 'Conversor GIF<br>a Sticker', subtitle: 'Convierte cualquier GIF' },
            { badge: 'FACIL', title: 'Proceso Simple<br>en 3 Pasos', subtitle: 'Selecciona → Edita → Anade' },
            { badge: 'PREMIUM', title: 'Desbloquea Todas<br>las Funciones', subtitle: 'Sin anuncios e IA ilimitada' }
        ],
        feature: { title: 'Sticky', tagline: 'El Mejor Creador<br>de Stickers', badge: '1000+ Paquetes Gratis' }
    },
    it: {
        rtl: false,
        screens: [
            { badge: 'IA', title: 'Crea Sticker<br>Fantastici', subtitle: 'Dalle tue foto in secondi!' },
            { badge: '1000+ PACCHETTI', title: 'Pacchetti Sticker<br>Gratis', subtitle: 'Nuovi pacchetti ogni settimana!' },
            { badge: 'IA SMART', title: 'Rimozione Sfondo<br>con IA', subtitle: 'Ritagli perfetti con un tocco' },
            { badge: 'ANIMATO', title: 'Convertitore GIF<br>in Sticker', subtitle: 'Converti qualsiasi GIF' },
            { badge: 'FACILE', title: 'Processo Semplice<br>in 3 Passi', subtitle: 'Seleziona → Modifica → Aggiungi' },
            { badge: 'PREMIUM', title: 'Sblocca Tutte<br>le Funzionalita', subtitle: 'Senza pubblicita e IA illimitata' }
        ],
        feature: { title: 'Sticky', tagline: 'Il Miglior Creatore<br>di Sticker', badge: '1000+ Pacchetti Gratis' }
    }
};

function generateScreenshotHTML(lang, screenIndex) {
    const data = translations[lang];
    const screen = data.screens[screenIndex];
    const sc = screenshots[screenIndex];
    const isRtl = data.rtl;

    return `
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&family=Noto+Sans+Arabic:wght@600;700;800&family=Noto+Sans+JP:wght@600;700;800&family=Noto+Sans+KR:wght@600;700;800&family=Noto+Sans+SC:wght@600;700;800&family=Noto+Sans+Thai:wght@600;700;800&display=swap" rel="stylesheet">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                width: 1080px;
                height: 1920px;
                font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
                background: ${sc.gradient};
                overflow: hidden;
                ${isRtl ? 'direction: rtl;' : ''}
            }
            ${lang === 'ar' ? "body { font-family: 'Noto Sans Arabic', 'Geeza Pro', 'Arabic Typesetting', -apple-system, BlinkMacSystemFont, system-ui, sans-serif; }" : ''}
            ${lang === 'ja' ? "body { font-family: 'Noto Sans JP', 'Hiragino Sans', 'Yu Gothic', system-ui, sans-serif; }" : ''}
            ${lang === 'ko' ? "body { font-family: 'Noto Sans KR', 'Apple SD Gothic Neo', 'Malgun Gothic', system-ui, sans-serif; }" : ''}
            ${lang === 'zh' ? "body { font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif; }" : ''}
            ${lang === 'th' ? "body { font-family: 'Noto Sans Thai', 'Thonburi', system-ui, sans-serif; }" : ''}
            ${lang === 'hi' ? "body { font-family: 'Noto Sans Devanagari', 'Kohinoor Devanagari', system-ui, sans-serif; }" : ''}

            .header {
                padding: 120px 80px 60px;
                text-align: center;
                color: white;
            }
            .badge {
                display: inline-block;
                background: rgba(255,255,255,0.25);
                padding: 20px 40px;
                border-radius: 50px;
                font-size: 32px;
                font-weight: 700;
                margin-bottom: 50px;
                letter-spacing: 2px;
            }
            h1 {
                font-size: 82px;
                font-weight: 900;
                line-height: 1.1;
                margin-bottom: 30px;
                text-shadow: 0 4px 30px rgba(0,0,0,0.3);
            }
            .subtitle {
                font-size: 40px;
                font-weight: 500;
                opacity: 0.95;
            }
            .phone {
                position: absolute;
                bottom: -80px;
                left: 50%;
                transform: translateX(-50%);
                width: 800px;
            }
            .phone-body {
                background: #1a1a1a;
                border-radius: 70px;
                padding: 18px;
                box-shadow: 0 60px 120px rgba(0,0,0,0.5);
            }
            .phone-notch {
                position: absolute;
                top: 18px;
                left: 50%;
                transform: translateX(-50%);
                width: 220px;
                height: 50px;
                background: #1a1a1a;
                border-radius: 0 0 30px 30px;
                z-index: 10;
            }
            .phone-screen {
                border-radius: 55px;
                overflow: hidden;
                background: #000;
            }
            .phone-screen img {
                width: 100%;
                display: block;
            }
        </style>
    </head>
    <body>
        <div class="header">
            <div class="badge">${screen.badge}</div>
            <h1>${screen.title}</h1>
            <div class="subtitle">${screen.subtitle}</div>
        </div>
        <div class="phone">
            <div class="phone-body">
                <div class="phone-notch"></div>
                <div class="phone-screen">
                    <img src="${imageCache[sc.image]}" alt="Screenshot">
                </div>
            </div>
        </div>
    </body>
    </html>`;
}

function generateFeatureHTML(lang) {
    const data = translations[lang];
    const f = data.feature;
    const isRtl = data.rtl;

    return `
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&family=Noto+Sans+Arabic:wght@600;700;800&family=Noto+Sans+JP:wght@600;700;800&family=Noto+Sans+KR:wght@600;700;800&family=Noto+Sans+SC:wght@600;700;800&family=Noto+Sans+Thai:wght@600;700;800&display=swap" rel="stylesheet">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                width: 1024px;
                height: 500px;
                font-family: 'Inter', sans-serif;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                overflow: hidden;
                display: flex;
                align-items: center;
                padding: 50px 60px;
                color: white;
                ${isRtl ? 'direction: rtl; flex-direction: row-reverse;' : ''}
            }
            ${lang === 'ar' ? "body { font-family: 'Noto Sans Arabic', 'Inter', sans-serif; }" : ''}
            ${lang === 'ja' ? "body { font-family: 'Noto Sans JP', 'Inter', sans-serif; }" : ''}
            ${lang === 'ko' ? "body { font-family: 'Noto Sans KR', 'Inter', sans-serif; }" : ''}
            ${lang === 'zh' ? "body { font-family: 'Noto Sans SC', 'Inter', sans-serif; }" : ''}
            ${lang === 'th' ? "body { font-family: 'Noto Sans Thai', 'Inter', sans-serif; }" : ''}

            .content {
                flex: 1;
                ${isRtl ? 'text-align: right;' : ''}
            }
            h1 {
                font-size: 90px;
                font-weight: 900;
                margin-bottom: 10px;
            }
            .tagline {
                font-size: 38px;
                font-weight: 600;
                opacity: 0.95;
                margin-bottom: 25px;
                line-height: 1.2;
            }
            .badge {
                display: inline-block;
                background: rgba(255,255,255,0.25);
                padding: 15px 35px;
                border-radius: 50px;
                font-size: 24px;
                font-weight: 700;
            }
            .phones {
                display: flex;
                gap: 15px;
                ${isRtl ? 'flex-direction: row-reverse;' : ''}
            }
            .mini-phone {
                width: 160px;
                background: #1a1a1a;
                border-radius: 22px;
                padding: 6px;
                box-shadow: 0 25px 50px rgba(0,0,0,0.4);
            }
            .mini-phone:first-child {
                transform: rotate(-8deg) translateY(15px);
            }
            .mini-phone:last-child {
                transform: rotate(8deg) translateY(-15px);
            }
            .mini-phone img {
                width: 100%;
                border-radius: 17px;
            }
        </style>
    </head>
    <body>
        <div class="content">
            <h1>${f.title}</h1>
            <div class="tagline">${f.tagline}</div>
            <div class="badge">${f.badge}</div>
        </div>
        <div class="phones">
            <div class="mini-phone">
                <img src="${imageCache['main_screen.png']}" alt="App">
            </div>
            <div class="mini-phone">
                <img src="${imageCache['sticker_maker.png']}" alt="App">
            </div>
        </div>
    </body>
    </html>`;
}

async function generateScreenshots() {
    console.log('🚀 Starting Sticky Screenshot Generator (Fixed)...\n');

    if (!fs.existsSync(OUTPUT_DIR)) {
        fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    }

    const browser = await puppeteer.launch({
        headless: 'new',
        args: ['--no-sandbox', '--disable-setuid-sandbox', '--font-render-hinting=none']
    });

    for (const lang of languagesToProcess) {
        console.log(`\n📱 Processing: ${lang.toUpperCase()}`);

        const langDir = path.join(OUTPUT_DIR, lang);
        if (!fs.existsSync(langDir)) {
            fs.mkdirSync(langDir, { recursive: true });
        }

        // Generate 6 screenshots
        for (let i = 0; i < 6; i++) {
            const page = await browser.newPage();
            await page.setViewport({ width: 1080, height: 1920, deviceScaleFactor: 1 });

            const html = generateScreenshotHTML(lang, i);
            await page.setContent(html, { waitUntil: 'networkidle0', timeout: 30000 });

            // Wait for fonts to load - especially important for Arabic, CJK, Thai
            await page.evaluate(() => document.fonts.ready);

            // Extra wait for non-Latin scripts
            const extraWaitLangs = ['ar', 'ja', 'ko', 'zh', 'th', 'hi'];
            if (extraWaitLangs.includes(lang)) {
                await new Promise(r => setTimeout(r, 2000));
            } else {
                await new Promise(r => setTimeout(r, 500));
            }

            await page.screenshot({
                path: path.join(langDir, `screenshot_${i + 1}_${lang}.png`),
                type: 'png',
                clip: { x: 0, y: 0, width: 1080, height: 1920 }
            });

            await page.close();
            console.log(`   ✅ screenshot_${i + 1}_${lang}.png`);
        }

        // Generate Feature Graphic
        const featurePage = await browser.newPage();
        await featurePage.setViewport({ width: 1024, height: 500, deviceScaleFactor: 1 });

        const featureHtml = generateFeatureHTML(lang);
        await featurePage.setContent(featureHtml, { waitUntil: 'networkidle0', timeout: 30000 });

        // Wait for fonts to load
        await featurePage.evaluate(() => document.fonts.ready);

        // Extra wait for non-Latin scripts
        const extraWaitLangs = ['ar', 'ja', 'ko', 'zh', 'th', 'hi'];
        if (extraWaitLangs.includes(lang)) {
            await new Promise(r => setTimeout(r, 2000));
        } else {
            await new Promise(r => setTimeout(r, 500));
        }

        await featurePage.screenshot({
            path: path.join(langDir, `feature_graphic_${lang}.png`),
            type: 'png',
            clip: { x: 0, y: 0, width: 1024, height: 500 }
        });

        await featurePage.close();
        console.log(`   ✅ feature_graphic_${lang}.png`);
    }

    await browser.close();

    console.log('\n' + '='.repeat(50));
    console.log('🎉 All screenshots generated successfully!');
    console.log(`📁 Output: ${OUTPUT_DIR}`);
    console.log('='.repeat(50));
}

generateScreenshots().catch(err => {
    console.error('❌ Error:', err.message);
    process.exit(1);
});
