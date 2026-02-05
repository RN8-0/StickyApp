import { GoogleGenerativeAI } from "@google/generative-ai";

const GEMINI_KEY = "AIzaSyBLAKvxDonZ02iouHRdQm_B9JkfRFAjIaM";
const GIPHY_KEY = "92rTpVsruJZofAPNWcThnzceGzqTVLwx";

const genAI = new GoogleGenerativeAI(GEMINI_KEY);

export interface GifResult {
    id: string;
    url: string;
    preview: string;
    title: string;
    source: 'giphy' | 'tenor';
}

function cleanAiText(text: string) {
    return text.replace(/```[a-z]*\n?/gi, '').replace(/```/g, '').trim();
}

export const magicWizardService = {
    /**
     * Gemini ile daha fazla trend önerisi al
     */
    async getTopicSuggestions(): Promise<string[]> {
        try {
            const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
            const prompt = `Give me 12 fresh, creative, and trending sticker pack ideas for a WhatsApp sticker app. 
      Return only as a comma-separated list of titles in Turkish. No numbers, no extra text.
      Vary the ideas significantly. Example: Sarkastik Kediler, 3D Emojiler, Retro Oyunlar, Motivasyon Kartları`;

            const result = await model.generateContent(prompt);
            const response = await result.response;
            const text = cleanAiText(response.text());

            const cleanList = text.split(',')
                .map(s => s.replace(/^\d+[\s.)]*/, '').trim())
                .filter(s => s.length > 2);

            return cleanList.length > 0 ? cleanList : ["Sarkastik Kediler", "Synthwave Vibes", "Retro Pixel Art", "Deep Thoughts"];
        } catch (error) {
            console.error("Gemini Suggestion Error:", error);
            return ["Trend Kediler", "3D Karakterler", "Retro Oyunlar", "Günaydın Mesajları"];
        }
    },

    /**
     * Otomatik Kategori Belirle
     */
    async getAutoCategory(topicOrName: string): Promise<string> {
        try {
            const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
            const prompt = `Which category best fits this sticker pack topic: "${topicOrName}"?
            Categories: humor, love, religious, entertainment, background, morning, night, birthday, congrats, animals, sports, gaming, movie, music, food, emoji, cars, motivation, cute, text, anime, memes, nature, other.
            Return ONLY the category ID.`;

            const result = await model.generateContent(prompt);
            const response = await result.response;
            return cleanAiText(response.text()).toLowerCase();
        } catch (e) {
            return "other";
        }
    },

    async generatePackMetadata(topic: string, existingNames: string[], avoidName?: string) {
        try {
            const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });

            let excludedNames = existingNames.slice(-40);
            if (avoidName) {
                excludedNames.push(avoidName);
            }

            const prompt = `Create professional sticker pack metadata for: "${topic}".
      1. Very creative English title (max 3 words) + 1-2 cool emojis.
      2. Turkish translation for internal display.
      3. One specific and effective keyword for GIF search in English.
      4. Best category from: humor, love, religious, entertainment, background, morning, night, birthday, congrats, animals, sports, gaming, movie, music, food, emoji, cars, motivation, cute, text, anime, memes, nature, other.
      
      CRITICAL: English title MUST NOT be in: [${excludedNames.join(', ')}].
      Make the English name unique and appealing for global users.
      
      Return ONLY a JSON object:
      {
        "name": "Unique Name 🚀",
        "name_tr": "Başlık",
        "search_keyword": "...",
        "category": "..."
      }`;

            const result = await model.generateContent(prompt);
            const response = await result.response;
            const text = cleanAiText(response.text());

            const jsonStart = text.indexOf('{');
            const jsonEnd = text.lastIndexOf('}') + 1;
            if (jsonStart === -1) throw new Error("No JSON found");
            const cleanJson = text.substring(jsonStart, jsonEnd);

            return JSON.parse(cleanJson);
        } catch (error) {
            console.error("Gemini Metadata Error:", error);
            return {
                name: `${topic} ✨`,
                name_tr: topic,
                search_keyword: topic,
                category: "other"
            };
        }
    },

    /**
     * Giphy'den GIF ara
     */
    async searchGifs(query: string, page: number = 0, limit: number = 30): Promise<GifResult[]> {
        try {
            const offset = page * limit;
            // Changed from 'gifs' to 'stickers' to return transparent stickers
            const url = `https://api.giphy.com/v1/stickers/search?api_key=${GIPHY_KEY}&q=${encodeURIComponent(query)}&limit=${limit}&offset=${offset}&rating=pg-13&lang=tr`;
            const response = await fetch(url).catch(() => null);
            if (!response) return [];

            const data = await response.json();
            if (!data || !data.data) return [];

            return data.data.map((item: any) => ({
                id: item.id,
                url: item.images.fixed_height.url || item.images.original.url,
                preview: item.images.fixed_height_small.url || item.images.fixed_height.url,
                title: item.title,
                source: 'giphy' as const
            }));
        } catch (error) {
            console.error("Giphy Search Error:", error);
            return [];
        }
    }
};
