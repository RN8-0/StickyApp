// Ücretsiz çeviri sistemi - Google Translate API (resmi olmayan) + Fallback

export const TARGET_LANGUAGES = [
    { code: 'en', name: 'English', flag: '🇬🇧' },
    { code: 'tr', name: 'Turkish', flag: '🇹🇷' },
    { code: 'zh', name: 'Chinese', flag: '🇨🇳' },
    { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    { code: 'ar', name: 'Arabic', flag: '🇸🇦' },
    { code: 'hi', name: 'Hindi', flag: '🇮🇳' },
    { code: 'pt', name: 'Portuguese', flag: '🇵🇹' },
    { code: 'fr', name: 'French', flag: '🇫🇷' },
    { code: 'de', name: 'German', flag: '🇩🇪' },
    { code: 'it', name: 'Italian', flag: '🇮🇹' },
    { code: 'ru', name: 'Russian', flag: '🇷🇺' },
    { code: 'ja', name: 'Japanese', flag: '🇯🇵' },
    { code: 'ko', name: 'Korean', flag: '🇰🇷' },
    { code: 'id', name: 'Indonesian', flag: '🇮🇩' },
    { code: 'th', name: 'Thai', flag: '🇹🇭' },
    { code: 'vi', name: 'Vietnamese', flag: '🇻🇳' },
    { code: 'nl', name: 'Dutch', flag: '🇳🇱' },
    { code: 'pl', name: 'Polish', flag: '🇵🇱' },
    { code: 'sv', name: 'Swedish', flag: '🇸🇪' },
    { code: 'da', name: 'Danish', flag: '🇩🇰' },
    { code: 'no', name: 'Norwegian', flag: '🇳🇴' },
    { code: 'fi', name: 'Finnish', flag: '🇫🇮' },
    { code: 'cs', name: 'Czech', flag: '🇨🇿' },
    { code: 'hu', name: 'Hungarian', flag: '🇭🇺' },
    { code: 'ro', name: 'Romanian', flag: '🇷🇴' },
    { code: 'el', name: 'Greek', flag: '🇬🇷' },
    { code: 'bn', name: 'Bengali', flag: '🇧🇩' },
    { code: 'ur', name: 'Urdu', flag: '🇵🇰' },
    { code: 'fa', name: 'Persian', flag: '🇮🇷' },
    { code: 'he', name: 'Hebrew', flag: '🇮🇱' },
    { code: 'uk', name: 'Ukrainian', flag: '🇺🇦' },
    { code: 'ms', name: 'Malay', flag: '🇲🇾' },
    { code: 'tl', name: 'Filipino', flag: '🇵🇭' }
];

// Google Translate API (ücretsiz, resmi olmayan endpoint)
const translateSingle = async (text: string, targetLang: string): Promise<string> => {
    try {
        const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=${targetLang}&dt=t&q=${encodeURIComponent(text)}`;
        const response = await fetch(url);
        const data = await response.json();

        // Google Translate yanıt formatı: [[["çeviri","original",...]]]
        if (data && data[0] && data[0][0] && data[0][0][0]) {
            return data[0][0][0];
        }
        return text;
    } catch (error) {
        console.error(`Çeviri hatası (${targetLang}):`, error);
        return text;
    }
};

export const translateTextAllLanguages = async (text: string): Promise<{ [key: string]: string }> => {
    console.log("🌍 Çoklu dil çevirisi başlatılıyor:", text);

    const finalMap: { [key: string]: string } = {};

    // İngilizce zaten mevcut
    finalMap['name_en'] = text;

    // Diğer dilleri paralel çevir (İngilizce hariç)
    const otherLanguages = TARGET_LANGUAGES.filter(l => l.code !== 'en');

    const translations = await Promise.all(
        otherLanguages.map(async (lang) => {
            const translated = await translateSingle(text, lang.code);
            return { code: lang.code, translation: translated };
        })
    );

    translations.forEach(({ code, translation }) => {
        finalMap[`name_${code}`] = translation;
    });

    console.log("✅ Çeviri tamamlandı:", Object.keys(finalMap).length, "dil");
    return finalMap;
};
