// DeepSeek AI Service - Akıllı isimlendirme, çeviri ve tema üretimi

const DEEPSEEK_API_KEY = import.meta.env.VITE_DEEPSEEK_API_KEY || '';
const DEEPSEEK_API_URL = 'https://api.deepseek.com/chat/completions';

interface DeepSeekMessage {
    role: 'system' | 'user' | 'assistant';
    content: string;
}

interface DeepSeekResponse {
    choices: Array<{
        message: {
            content: string;
        };
    }>;
}

async function callDeepSeek(messages: DeepSeekMessage[], temperature: number = 0.8): Promise<string> {
    if (!DEEPSEEK_API_KEY) {
        throw new Error('DeepSeek API key bulunamadı. .env dosyasını kontrol edin.');
    }

    const response = await fetch(DEEPSEEK_API_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${DEEPSEEK_API_KEY}`
        },
        body: JSON.stringify({
            model: 'deepseek-chat',
            messages,
            temperature,
            max_tokens: 4096
        })
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`DeepSeek API hatası (${response.status}): ${errorText}`);
    }

    const data: DeepSeekResponse = await response.json();
    return data.choices[0]?.message?.content || '';
}

// ========== 1. AKILLI PAKET İSİMLENDİRME ==========

export interface PackNameSuggestion {
    name: string;
    emoji: string;
}

export async function generatePackNames(searchTerm: string, count: number = 5): Promise<PackNameSuggestion[]> {
    const messages: DeepSeekMessage[] = [
        {
            role: 'system',
            content: `You are a creative naming expert for WhatsApp sticker packs. Generate short, catchy, memorable English names for sticker packs. Rules:
- Names must be 2-4 words maximum
- Must be catchy and app-store friendly
- Include 1-2 relevant emojis at the end
- Each name should have a different style/vibe
- No generic names like "Sticker Pack" or "Collection"
- Think like a marketing expert
- Return ONLY a JSON array, no other text`
        },
        {
            role: 'user',
            content: `Generate ${count} creative sticker pack names for the theme: "${searchTerm}"

Return as JSON array: [{"name": "Pack Name Here", "emoji": "🎉✨"}]`
        }
    ];

    const result = await callDeepSeek(messages, 0.9);

    try {
        const jsonMatch = result.match(/\[[\s\S]*\]/);
        if (jsonMatch) {
            return JSON.parse(jsonMatch[0]);
        }
    } catch (e) {
        console.error('[DeepSeek] İsim parse hatası:', e, result);
    }

    // Fallback
    return [{ name: searchTerm.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' '), emoji: '✨' }];
}

// ========== 2. AKILLI ÇEVİRİ (33 DİL) ==========

export const SUPPORTED_LANGUAGES = [
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

export async function translatePackName(englishName: string): Promise<Record<string, string>> {
    const langList = SUPPORTED_LANGUAGES
        .filter(l => l.code !== 'en')
        .map(l => `${l.code} (${l.name})`)
        .join(', ');

    const messages: DeepSeekMessage[] = [
        {
            role: 'system',
            content: `You are a professional localization expert for mobile apps. Translate sticker pack names naturally - NOT word-by-word translation. The translated name should:
- Sound natural in the target language
- Keep the same vibe/feeling as the original
- Be short (2-5 words max)
- Keep any emojis from the original
- If the name is a brand-like creative name, transliterate rather than translate
- Return ONLY a JSON object, no other text`
        },
        {
            role: 'user',
            content: `Translate this sticker pack name to all these languages: "${englishName}"

Languages: ${langList}

Return as JSON object: {"tr": "Turkish translation", "zh": "Chinese translation", ...}
Include ALL language codes listed above.`
        }
    ];

    const result = await callDeepSeek(messages, 0.3);

    try {
        const jsonMatch = result.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
            const translations = JSON.parse(jsonMatch[0]);
            const finalMap: Record<string, string> = { name_en: englishName };

            for (const lang of SUPPORTED_LANGUAGES) {
                if (lang.code === 'en') continue;
                finalMap[`name_${lang.code}`] = translations[lang.code] || englishName;
            }

            return finalMap;
        }
    } catch (e) {
        console.error('[DeepSeek] Çeviri parse hatası:', e, result);
    }

    // Fallback: sadece İngilizce
    const fallback: Record<string, string> = { name_en: englishName };
    SUPPORTED_LANGUAGES.forEach(l => {
        if (l.code !== 'en') fallback[`name_${l.code}`] = englishName;
    });
    return fallback;
}

// ========== 3. TOPLU ARAMA TERİMİ ÜRETİMİ ==========

export interface ThemeSuggestion {
    searchTerm: string;
    category: string;
    description: string;
}

// Kategori ID'lerinden açıklamalı isimler (daha iyi AI sonuçları için)
const CATEGORY_DESCRIPTIONS: Record<string, string> = {
    humor: 'Humor & Comedy (funny memes, jokes, laugh reactions, comedy scenes, hilarious moments)',
    love: 'Love & Romance (hearts, couples, romantic gestures, valentine, crush, dating)',
    religious: 'Religious & Spiritual (islamic, christian, jewish, hindu, buddhist greetings, prayers)',
    entertainment: 'Entertainment & Fun (party, celebration, music, dance, joy)',
    background: 'Backgrounds & Aesthetics (aesthetic wallpapers, gradients, patterns, textures)',
    morning: 'Good Morning (sunrise greetings, coffee, breakfast, morning motivation)',
    night: 'Good Night (moon, stars, sleep, bedtime, sweet dreams)',
    birthday: 'Birthday (cake, party, balloons, gifts, celebrations, age milestones)',
    congrats: 'Congratulations (success, achievement, graduation, promotion, victory)',
    animals: 'Animals & Pets (cats, dogs, birds, wildlife, cute pets, funny animals)',
    sports: 'Sports & Fitness (football, basketball, soccer, gym, workout, champions)',
    gaming: 'Gaming & Esports (video games, consoles, streamers, gaming reactions)',
    movie: 'Movies & TV Shows (cinema, series, actors, scenes, fan reactions)',
    music: 'Music & Dance (singers, instruments, concerts, dancing, musical vibes)',
    food: 'Food & Drinks (delicious meals, coffee, desserts, cooking, restaurants)',
    emoji: 'Emoji Style (emoticons, faces, expressions, classic emoji recreations)',
    cars: 'Cars & Vehicles (automobiles, motorcycles, racing, luxury cars, speed)',
    motivation: 'Motivation & Inspiration (quotes, success, hustle, never give up)',
    cute: 'Cute & Kawaii (adorable, chibi, baby animals, sweet, wholesome)',
    text: 'Text & Typography (quotes, messages, colorful words, neon text)',
    anime: 'Anime & Manga (japanese animation, otaku, waifu, popular anime characters)',
    memes: 'Memes & Viral (internet culture, trending memes, viral moments)',
    nature: 'Nature & Scenery (landscapes, flowers, ocean, mountains, weather)',
    other: 'Miscellaneous (unique, diverse, creative, unconventional topics)'
};

export async function generateSearchTerms(
    count: number = 50,
    existingTerms: string[] = [],
    selectedCategories: string[] = []
): Promise<ThemeSuggestion[]> {
    const existingNote = existingTerms.length > 0
        ? `\n\n⚠️ ALREADY EXISTING (DO NOT generate these or anything too similar): ${existingTerms.join(', ')}`
        : '';

    let categoryNote = '';
    if (selectedCategories.length > 0) {
        const categoryDescriptions = selectedCategories
            .map(cat => CATEGORY_DESCRIPTIONS[cat] || cat)
            .join('\n- ');
        categoryNote = `\n\n🎯 FOCUS YOUR CREATIVITY STRICTLY ON THESE CATEGORIES:\n- ${categoryDescriptions}`;
    }

    const requestCount = Math.max(10, count * 2);

    const messages: DeepSeekMessage[] = [
        {
            role: 'system',
            content: `You are an elite content strategist for a massively popular, global WhatsApp sticker application. Your sole purpose is to invent highly engaging, imaginative, and highly searchable sticker pack topics.

You are interacting with Giphy and Tenor's search engines. You must generate terms that will yield visually distinct, highly expressive GIF/Sticker results.

CRITICAL DIRECTIVES:
1. MAXIMAL CREATIVE FREEDOM: Do not limit yourself. Think of every possible human emotion, internet subculture, daily struggle, universally recognizable situation, gaming moment, or abstract aesthetic. 
2. NO GEOGRAPHY/NATIONALITIES: Do NOT ever use country names, nationalities, or specific geographic regions (e.g., NEVER use "Turkish", "American", "Brazilian", "Indian", "Arabic", "African"). Stick to universal human experiences.
3. VISUAL ACTION/EMOTION: Every term must describe something visual. "Sad" is bad. "Crying loudly in bed" is great. "Happy" is bad. "Jumping with joy celebration" is great.
4. FORMAT: Exactly 2 to 4 words per term. English only.

Think about what users actually send to their friends, families, and coworkers:
- Intense reactions and dramatic emotions
- Relatable daily annoyances and victories (work, school, home)
- Trending internet humor and surreal abstract memes
- Cute, funny, or chaotic animal behaviors
- Specific social situations (flirting, ignoring, apologizing, celebrating)
- Pop culture archetypes (anime reactions, gaming rage, cinematic drama)

Return ONLY a valid JSON array. Zero markdown formatting. Zero explanation.`
        },
        {
            role: 'user',
            content: `Generate exactly ${requestCount} completely UNIQUE, visually descriptive sticker search terms.${categoryNote}${existingNote}

RULES:
- Exactly 2 to 4 words
- NO countries, NO nationalities, NO geographic locations
- Must yield great Giphy/Tenor visual results
- Every term must be conceptually distinct from the others

Return AS A RAW JSON ARRAY ONLY:
[{"searchTerm": "specific visual term", "category": "category_id", "description": "Brief description of the vibe"}]

${selectedCategories.length > 0 ? `MUST use these category IDs: ${selectedCategories.join(', ')}` : 'You may use any of these valid category IDs: humor, love, religious, entertainment, morning, night, birthday, congrats, animals, sports, gaming, movie, music, food, emoji, cars, motivation, cute, text, anime, memes, nature, other'}`
        }
    ];

    const result = await callDeepSeek(messages, 1.2); // High temperature for maximum creativity

    try {
        const jsonMatch = result.match(/\[[\s\S]*\]/);
        if (jsonMatch) {
            const parsed = JSON.parse(jsonMatch[0]);
            const seen = new Set<string>();
            const countryWords = ['turkish', 'american', 'brazilian', 'indian', 'chinese', 'japanese', 'korean', 'arabic', 'african', 'european', 'mexican', 'russian', 'thai', 'vietnamese', 'indonesian', 'pakistani', 'persian', 'filipino', 'malay', 'bengali', 'german', 'french', 'italian', 'spanish', 'portuguese', 'dutch', 'polish', 'swedish', 'norwegian', 'finnish', 'greek', 'hungarian', 'romanian', 'czech', 'ukrainian', 'hebrew', 'latino'];

            const validItems = parsed.filter((item: ThemeSuggestion) => {
                const normalized = item.searchTerm?.toLowerCase().trim();
                if (!normalized) return false;
                if (seen.has(normalized)) return false;
                if (countryWords.some(cw => normalized.includes(cw))) return false;
                if (normalized.split(' ').length < 2) return false;

                seen.add(normalized);
                return true;
            });

            return validItems.slice(0, count);
        }
    } catch (e) {
        console.error('[DeepSeek] Tema üretim parse hatası:', e, result);
    }

    return [];
}

// ========== 4. KATEGORİ OTOMATİK ATAMA ==========

const CATEGORY_MAP: Record<string, string[]> = {
    humor: ['funny', 'meme', 'lol', 'comedy', 'joke', 'laugh', 'haha', 'hilarious', 'silly', 'dank'],
    love: ['love', 'heart', 'romance', 'kiss', 'couple', 'valentine', 'romantic', 'crush', 'dating', 'relationship'],
    animals: ['cat', 'dog', 'animal', 'pet', 'puppy', 'kitten', 'bird', 'bunny', 'bear', 'fox', 'panda', 'duck'],
    reactions: ['reaction', 'mood', 'face', 'expression', 'omg', 'wow', 'yes', 'no', 'ok', 'sorry', 'thanks'],
    greetings: ['morning', 'night', 'hello', 'bye', 'welcome', 'greeting', 'hi', 'goodnight', 'good morning'],
    celebrations: ['birthday', 'party', 'celebrate', 'congratulations', 'wedding', 'anniversary', 'graduation'],
    anime: ['anime', 'manga', 'kawaii', 'chibi', 'otaku', 'waifu', 'naruto', 'dragon ball', 'one piece'],
    gaming: ['game', 'gaming', 'gamer', 'esport', 'pixel', 'retro', 'controller', 'play', 'level up'],
    food: ['food', 'eat', 'cook', 'pizza', 'burger', 'coffee', 'tea', 'cake', 'yummy', 'delicious', 'hungry'],
    sports: ['sport', 'football', 'soccer', 'basketball', 'gym', 'fitness', 'workout', 'run', 'champion'],
    entertainment: ['movie', 'film', 'music', 'dance', 'sing', 'star', 'celebrity', 'tv', 'show', 'concert'],
    lifestyle: ['fashion', 'beauty', 'travel', 'selfie', 'cool', 'aesthetic', 'vibe', 'mood', 'chill'],
    emotions: ['happy', 'sad', 'angry', 'cry', 'scared', 'surprised', 'confused', 'tired', 'bored', 'excited'],
    text: ['text', 'word', 'quote', 'message', 'chat', 'colorful', 'neon', 'typography', 'lettering'],
    seasonal: ['christmas', 'halloween', 'new year', 'easter', 'thanksgiving', 'summer', 'winter', 'spring', 'autumn'],
    memes: ['meme', 'viral', 'trending', 'internet', 'pepe', 'doge', 'based', 'bruh', 'sus']
};

export function autoDetectCategory(searchTerm: string): string {
    const lower = searchTerm.toLowerCase();

    let bestMatch = 'humor'; // default
    let bestScore = 0;

    for (const [category, keywords] of Object.entries(CATEGORY_MAP)) {
        let score = 0;
        for (const keyword of keywords) {
            if (lower.includes(keyword)) {
                score += keyword.length; // Daha uzun eşleşme = daha yüksek skor
            }
        }
        if (score > bestScore) {
            bestScore = score;
            bestMatch = category;
        }
    }

    return bestMatch;
}

// ========== 5. TEK SEFERLİK PAKET BİLGİSİ ÜRETİMİ ==========

export interface GeneratedPackInfo {
    name: string;
    emoji: string;
    translations: Record<string, string>;
    category: string;
}

export async function generateFullPackInfo(searchTerm: string): Promise<GeneratedPackInfo> {
    // 1. İsim üret
    const names = await generatePackNames(searchTerm, 3);
    const bestName = names[0] || { name: searchTerm, emoji: '✨' };
    const fullName = `${bestName.name} ${bestName.emoji}`;

    // 2. Kategori ata
    const category = autoDetectCategory(searchTerm);

    // 3. Çevir
    const translations = await translatePackName(fullName);

    return {
        name: fullName,
        emoji: bestName.emoji,
        translations,
        category
    };
}

// ========== EXPORT ==========

export const deepseekService = {
    generatePackNames,
    translatePackName,
    generateSearchTerms,
    autoDetectCategory,
    generateFullPackInfo,
    isConfigured: () => !!DEEPSEEK_API_KEY
};
