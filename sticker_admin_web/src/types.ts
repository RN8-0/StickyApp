export interface Sticker {
    image_file: string;
    url: string;
    emojis: string[];
}

export interface StickerPack {
    id: string;
    name: string;
    name_tr: string;
    name_zh: string;
    name_es: string;
    name_ar: string;
    name_hi: string;
    name_pt: string;
    publisher: string;
    publisher_email: string;
    privacy_policy_website: string;
    license_agreement_website: string;
    tray_image_file: string;
    tray_url: string;
    is_animated: boolean;
    is_premium: boolean;
    product_id?: string; // Google Play Console ürün etiketi (örn: "recep_ivedik")
    category: string;
    download_count: number;
    fake_download_base: number;
    view_count: number;
    favorite_count: number;
    sticker_count: number;
    image_data_version: string;
    is_active: boolean;
    stickers: Sticker[];
    created_at?: any;
}

export interface ContactMessage {
    id: string;
    name: string;
    email: string;
    subject: string;
    message: string;
    timestamp: number;
    date: string;
    time: string;
    status: 'read' | 'unread';
}

export interface StickerSuggestion {
    id: string;
    suggestion: string;
    timestamp: number;
    date: string;
    time: string;
}


